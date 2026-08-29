import sqlite3
import os
import secrets
import string
from contextlib import contextmanager
from werkzeug.security import generate_password_hash

# Where the SQLite file lives. Defaults to the project directory, which is right
# for local dev. On a host with ephemeral container storage (Fly, Render, …) point
# BUCKETS_DB_PATH at a mounted volume, or every deploy wipes the database.
DB_PATH = os.environ.get('BUCKETS_DB_PATH') or os.path.join(
    os.path.dirname(__file__), 'buckets.db'
)

# Initial user seeded on first run. There is no signup page, so this is the
# account you use to get in the first time; everyone else is added with
# `manage_users.py add`.
#
# The password is deliberately NOT given a hardcoded default: a fixed one would
# be published in the repo, letting a stranger who finds a hosted instance claim
# the account by setting a password on it. Unset → generate one and print it to
# the console at seed time.
DEFAULT_USER_NAME  = os.environ.get('BUCKETS_DEFAULT_NAME', 'demo')
DEFAULT_USER_EMAIL = os.environ.get('BUCKETS_DEFAULT_EMAIL', 'demo@example.com')
DEFAULT_USER_PASS  = os.environ.get('BUCKETS_DEFAULT_PASSWORD')


def gen_temp_password(length=14):
    """A readable but strong temporary password (letters + digits)."""
    alphabet = string.ascii_letters + string.digits
    return ''.join(secrets.choice(alphabet) for _ in range(length))


def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    # SQLite locks the whole file for writes. Without a busy timeout a request
    # that collides with another writer fails instantly with "database is
    # locked"; this waits its turn instead.
    conn.execute("PRAGMA busy_timeout = 5000")
    return conn


@contextmanager
def db_conn():
    """Context manager — always closes the connection, even on exception."""
    conn = get_db()
    try:
        yield conn
    finally:
        conn.close()


def init_db(seed=True):
    """Create/migrate the schema, and (unless seed=False) seed the initial user.

    Pass seed=False when you only need the schema to exist — `manage_users.py`
    does, so that running it never conjures a default user as a side effect.
    """
    conn = get_db()
    cur = conn.cursor()

    # Temporarily disable FK enforcement so we can migrate freely
    cur.execute("PRAGMA foreign_keys = OFF")

    cur.executescript("""
        CREATE TABLE IF NOT EXISTS users (
            id                   INTEGER PRIMARY KEY AUTOINCREMENT,
            name                 TEXT NOT NULL,
            email                TEXT NOT NULL UNIQUE,
            passwd               TEXT NOT NULL,
            must_change_password INTEGER NOT NULL DEFAULT 0,
            intro_seen           INTEGER NOT NULL DEFAULT 0
        );

        CREATE TABLE IF NOT EXISTS accounts (
            id      INTEGER PRIMARY KEY AUTOINCREMENT,
            name    TEXT NOT NULL,
            user_id INTEGER NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id)
        );

        CREATE TABLE IF NOT EXISTS buckets (
            id            INTEGER PRIMARY KEY AUTOINCREMENT,
            name          TEXT NOT NULL,
            budget        REAL NOT NULL DEFAULT 0,
            acct_id       INTEGER NOT NULL,
            refill_factor REAL NOT NULL DEFAULT 1.0,
            FOREIGN KEY (acct_id) REFERENCES accounts(id)
        );

        CREATE TABLE IF NOT EXISTS transactions (
            id        INTEGER PRIMARY KEY AUTOINCREMENT,
            tx_date   TEXT NOT NULL,
            bucket_id INTEGER NOT NULL,
            amount    REAL NOT NULL,
            note      TEXT DEFAULT '',
            posted    INTEGER NOT NULL DEFAULT 0,
            deleted   INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (bucket_id) REFERENCES buckets(id)
        );

        CREATE TABLE IF NOT EXISTS upcoming_expenses (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            bucket_id   INTEGER NOT NULL,
            description TEXT NOT NULL,
            amount      REAL NOT NULL DEFAULT 0,
            due_date    TEXT DEFAULT '',
            notes       TEXT DEFAULT '',
            FOREIGN KEY (bucket_id) REFERENCES buckets(id)
        );

        CREATE TABLE IF NOT EXISTS iou_requests (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            requester_id    INTEGER NOT NULL,
            payee_id        INTEGER NOT NULL,
            amount          REAL    NOT NULL,
            description     TEXT    NOT NULL DEFAULT '',
            due_date        TEXT    NOT NULL DEFAULT '',
            notes           TEXT    NOT NULL DEFAULT '',
            status          TEXT    NOT NULL DEFAULT 'pending',
            linked_tx_id    INTEGER DEFAULT NULL,
            created_at      TEXT    NOT NULL,
            updated_at      TEXT    NOT NULL,
            FOREIGN KEY (requester_id) REFERENCES users(id),
            FOREIGN KEY (payee_id)     REFERENCES users(id),
            FOREIGN KEY (linked_tx_id) REFERENCES transactions(id) ON DELETE SET NULL,
            CHECK (requester_id != payee_id),
            CHECK (status IN ('pending','linked','settled','cancelled'))
        );
    """)

    # ── Schema migration: add email/passwd columns to older DBs ──────────────
    cols = [r[1] for r in cur.execute("PRAGMA table_info(users)").fetchall()]
    if 'email' not in cols:
        cur.execute("ALTER TABLE users ADD COLUMN email TEXT NOT NULL DEFAULT ''")
    if 'passwd' not in cols:
        cur.execute("ALTER TABLE users ADD COLUMN passwd TEXT NOT NULL DEFAULT ''")
    if 'must_change_password' not in cols:
        cur.execute("ALTER TABLE users ADD COLUMN must_change_password INTEGER NOT NULL DEFAULT 0")

    # ── Schema migration: add intro_seen to users ────────────────────────────
    # Anyone who already has an account has been using the app, so backfill
    # them as "seen". Only accounts created from here on get the intro tour.
    if 'intro_seen' not in cols:
        cur.execute("ALTER TABLE users ADD COLUMN intro_seen INTEGER NOT NULL DEFAULT 0")
        cur.execute("UPDATE users SET intro_seen=1")

    # ── Schema migration: add is_settlement to buckets ───────────────────────
    bkt_cols = [r[1] for r in cur.execute("PRAGMA table_info(buckets)").fetchall()]
    if 'is_settlement' not in bkt_cols:
        cur.execute(
            "ALTER TABLE buckets ADD COLUMN is_settlement INTEGER NOT NULL DEFAULT 0"
        )
        conn.commit()

    # ── Schema migration: add archived to buckets ────────────────────────────
    if 'archived' not in bkt_cols:
        cur.execute(
            "ALTER TABLE buckets ADD COLUMN archived INTEGER NOT NULL DEFAULT 0"
        )
        conn.commit()

    # ── Schema migration: add linked_tx_id / deleted to transactions ─────────
    tx_cols = [r[1] for r in cur.execute("PRAGMA table_info(transactions)").fetchall()]
    if 'deleted' not in tx_cols:
        cur.execute(
            "ALTER TABLE transactions ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0"
        )
        conn.commit()

    if 'linked_tx_id' not in tx_cols:
        cur.execute(
            "ALTER TABLE transactions ADD COLUMN linked_tx_id INTEGER REFERENCES transactions(id) ON DELETE SET NULL"
        )
        # Best-effort: link existing transfer pairs by matching note + date + amount
        cur.execute("""
            UPDATE transactions SET linked_tx_id = (
                SELECT t2.id FROM transactions t2
                WHERE t2.note = 'Transfer from bucket #' || transactions.bucket_id
                  AND t2.tx_date = transactions.tx_date
                  AND t2.amount = -transactions.amount
                LIMIT 1
            )
            WHERE note LIKE 'Transfer to bucket #%' AND linked_tx_id IS NULL
        """)
        cur.execute("""
            UPDATE transactions SET linked_tx_id = (
                SELECT t2.id FROM transactions t2
                WHERE t2.note = 'Transfer to bucket #' || transactions.bucket_id
                  AND t2.tx_date = transactions.tx_date
                  AND t2.amount = -transactions.amount
                LIMIT 1
            )
            WHERE note LIKE 'Transfer from bucket #%' AND linked_tx_id IS NULL
        """)

    conn.commit()

    if not seed:
        cur.execute("PRAGMA foreign_keys = ON")
        conn.commit()
        conn.close()
        return

    # ── Seed default user if not present ──────────────────────────────────────
    default_user = cur.execute(
        "SELECT id FROM users WHERE LOWER(email)=?", (DEFAULT_USER_EMAIL.lower(),)
    ).fetchone()

    if default_user is None:
        passwd = DEFAULT_USER_PASS or gen_temp_password()
        # Always force a change on first login, so even a password that leaked
        # via the console or shell history is single-use.
        try:
            cur.execute(
                "INSERT INTO users (name, email, passwd, must_change_password) VALUES (?,?,?,1)",
                (DEFAULT_USER_NAME, DEFAULT_USER_EMAIL, generate_password_hash(passwd)),
            )
            conn.commit()
        except sqlite3.IntegrityError:
            # Another gunicorn worker seeded it first — app.py runs init_db() at
            # import, so every worker races here on a fresh database.
            conn.rollback()
        else:
            if not DEFAULT_USER_PASS:
                print("\n" + "=" * 70)
                print("  Seeded the initial Buckets user.")
                print(f"    Email:    {DEFAULT_USER_EMAIL}")
                print(f"    Password: {passwd}")
                print("  Shown once, right now. It must be changed at first login.")
                print("  Set BUCKETS_DEFAULT_PASSWORD before first run to choose your own.")
                print("=" * 70 + "\n", flush=True)

        default_user = cur.execute(
            "SELECT id FROM users WHERE LOWER(email)=?", (DEFAULT_USER_EMAIL.lower(),)
        ).fetchone()

    default_user_id = default_user[0]

    # ── Migrate orphaned accounts to the default user ────────────────────────
    # Accounts whose user_id references a non-existent user row
    orphaned = cur.execute(
        """SELECT a.id FROM accounts a
           LEFT JOIN users u ON u.id = a.user_id
           WHERE u.id IS NULL""",
    ).fetchall()

    if orphaned:
        ids = [r[0] for r in orphaned]
        cur.execute(
            "UPDATE accounts SET user_id=? WHERE id IN ({})".format(
                ",".join("?" * len(ids))
            ),
            [default_user_id] + ids,
        )
        conn.commit()
        print(f"[init_db] Migrated {len(ids)} orphaned account(s) to user '{DEFAULT_USER_NAME}'.")

    cur.execute("PRAGMA foreign_keys = ON")
    conn.commit()

    # ── Create Settlement bucket for any account that doesn't have one ────────
    accounts_no_settlement = cur.execute("""
        SELECT a.id FROM accounts a
        WHERE NOT EXISTS (
            SELECT 1 FROM buckets b WHERE b.acct_id = a.id AND b.is_settlement = 1
        )
    """).fetchall()
    if accounts_no_settlement:
        for row in accounts_no_settlement:
            cur.execute(
                "INSERT INTO buckets (name, budget, acct_id, refill_factor, is_settlement)"
                " VALUES ('Settlement', 0, ?, 1.0, 1)",
                (row[0],),
            )
        conn.commit()
        print(f"[init_db] Created Settlement bucket for {len(accounts_no_settlement)} account(s).")

    # ── Seed starter account + buckets for the default user if none exist ────
    acct_count = cur.execute(
        "SELECT COUNT(*) FROM accounts WHERE user_id=?", (default_user_id,)
    ).fetchone()[0]

    if acct_count == 0:
        _seed_starter_data(cur, default_user_id)
        conn.commit()
        print(f"[init_db] Seeded starter account and buckets for '{DEFAULT_USER_NAME}'.")

    conn.close()


def _seed_starter_data(cur, user_id):
    """Create a default Checking account with common buckets and sample transactions."""
    from datetime import date, timedelta

    cur.execute(
        "INSERT INTO accounts (name, user_id) VALUES (?,?)",
        ('Checking', user_id),
    )
    acct_id = cur.lastrowid

    # Settlement bucket (receives all deposits, funds refills)
    cur.execute(
        "INSERT INTO buckets (name, budget, acct_id, refill_factor, is_settlement)"
        " VALUES ('Settlement', 0, ?, 1.0, 1)",
        (acct_id,),
    )
    settlement_id = cur.lastrowid

    # Buckets: name, budget, refill_factor
    buckets_def = [
        ('Groceries',      500.00, 1.0),
        ('Rent',          1500.00, 1.0),
        ('Utilities',      200.00, 1.0),
        ('Entertainment',  150.00, 1.0),
        ('Savings',        300.00, 1.0),
    ]
    bucket_ids = {}
    for name, budget, rf in buckets_def:
        cur.execute(
            "INSERT INTO buckets (name, budget, acct_id, refill_factor) VALUES (?,?,?,?)",
            (name, budget, acct_id, rf),
        )
        bucket_ids[name] = cur.lastrowid

    # Sample transactions for current month
    today = date.today()
    def iso(delta=0):
        return (today - timedelta(days=delta)).isoformat()

    sample_tx = [
        # Refills (positive)
        (iso(28), 'Groceries',      500.00,  'Refill from Settlement', 0),
        (iso(28), 'Rent',          1500.00,  'Refill from Settlement', 1),
        (iso(28), 'Utilities',      200.00,  'Refill from Settlement', 1),
        (iso(28), 'Entertainment',  150.00,  'Refill from Settlement', 0),
        (iso(28), 'Savings',        300.00,  'Refill from Settlement', 1),
        # Spending (negative)
        (iso(20), 'Groceries',      -62.50,  'Whole Foods',      1),
        (iso(18), 'Groceries',      -38.75,  'Trader Joe\'s',    1),
        (iso(15), 'Utilities',      -95.00,  'Electric bill',    1),
        (iso(14), 'Entertainment',  -14.99,  'Netflix',          1),
        (iso(12), 'Groceries',      -45.20,  'Costco run',       0),
        (iso(10), 'Entertainment',  -55.00,  'Dinner out',       0),
        (iso(7),  'Utilities',      -42.00,  'Internet bill',    0),
        (iso(5),  'Groceries',      -29.10,  'Safeway',          0),
        (iso(3),  'Entertainment',  -12.00,  'Movie tickets',    0),
        (iso(1),  'Savings',       -100.00,  'Emergency fund',   0),
    ]

    # Seed an initial deposit into Settlement covering total budgets
    cur.execute(
        "INSERT INTO transactions (tx_date, bucket_id, amount, note, posted) VALUES (?,?,?,?,?)",
        (iso(30), settlement_id, 2650.00, 'Opening deposit', 1),
    )
    # Offsetting debits from Settlement for each refill above
    for bucket_name, amount in [
        ('Groceries', 500.00), ('Rent', 1500.00), ('Utilities', 200.00),
        ('Entertainment', 150.00), ('Savings', 300.00),
    ]:
        cur.execute(
            "INSERT INTO transactions (tx_date, bucket_id, amount, note, posted) VALUES (?,?,?,?,?)",
            (iso(28), settlement_id, -amount, f'Refill to {bucket_name}', 1),
        )

    for tx_date, bucket_name, amount, note, posted in sample_tx:
        cur.execute(
            "INSERT INTO transactions (tx_date, bucket_id, amount, note, posted) VALUES (?,?,?,?,?)",
            (tx_date, bucket_ids[bucket_name], amount, note, posted),
        )
