import csv
import io
import os
import secrets
from datetime import date
from functools import wraps

from flask import (Flask, jsonify, redirect, render_template,
                   request, make_response, send_from_directory, session,
                   url_for)
from werkzeug.security import check_password_hash, generate_password_hash

from database import db_conn, init_db

app = Flask(__name__)

# Session cookies are signed with this key. It MUST stay stable across restarts
# (a new key logs everyone out) and secret. Set BUCKETS_SECRET_KEY in the VM's
# environment in production; the random fallback is dev-only.
app.secret_key = os.environ.get('BUCKETS_SECRET_KEY')
if not app.secret_key:
    print("[WARN] BUCKETS_SECRET_KEY not set — using a random key. "
          "Sessions will reset on restart. Set it in production.")
    app.secret_key = secrets.token_hex(32)

app.config.update(
    SESSION_COOKIE_HTTPONLY=True,
    SESSION_COOKIE_SAMESITE='Lax',
    # Only send the cookie over HTTPS. Enable in production (behind TLS/Cloudflare)
    # via BUCKETS_SECURE_COOKIES=1; leave off for plain-HTTP local dev.
    SESSION_COOKIE_SECURE=os.environ.get('BUCKETS_SECURE_COOKIES', '').lower()
        in ('1', 'true', 'yes'),
)


# Shown on the sign-in page so an invitee has somewhere to write. Kept in the
# environment rather than the template: it's the one address that changes
# without the code changing, and it stays out of a public repo.
ADMIN_EMAIL = os.environ.get('BUCKETS_ADMIN_EMAIL', '').strip()


# ---------------------------------------------------------------------------
# Auth helpers
# ---------------------------------------------------------------------------

def login_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if 'user_id' not in session:
            if request.path.startswith('/api/'):
                return jsonify({'error': 'Unauthorized'}), 401
            return redirect(url_for('login'))
        return f(*args, **kwargs)
    return decorated


def current_user_id():
    return session['user_id']


# ---------------------------------------------------------------------------
# Auth routes
# ---------------------------------------------------------------------------

@app.route('/login', methods=['GET', 'POST'])
def login():
    if 'user_id' in session:
        return redirect(url_for('index'))

    error = None
    if request.method == 'POST':
        email  = (request.form.get('email') or '').strip().lower()
        passwd = request.form.get('password') or ''
        try:
            with db_conn() as conn:
                user = conn.execute(
                    "SELECT id, name, email, passwd, must_change_password "
                    "FROM users WHERE LOWER(email)=?",
                    (email,)
                ).fetchone()
            if user and check_password_hash(user['passwd'], passwd):
                session.clear()
                session['user_id']    = user['id']
                session['user_name']  = user['name']
                session['user_email'] = user['email']
                session['must_change_password'] = bool(user['must_change_password'])
                return redirect(url_for('index'))
            error = 'Invalid email or password.'
        except Exception as e:
            error = f'Login failed: {e}'

    return render_template('login.html', error=error, admin_email=ADMIN_EMAIL)


# Signup is intentionally disabled — this is an invite-only deployment.
# Accounts are provisioned from the VM with `python manage_users.py add …`,
# which also handles password resets. There is no public self-service path.


@app.route('/change-password')
@login_required
def change_password_page():
    """Standalone page used to force a password change on first login."""
    return render_template(
        'change_password.html',
        forced=bool(session.get('must_change_password')),
        user_name=session.get('user_name', ''),
    )


@app.before_request
def enforce_password_change():
    """While a user's password change is pending, confine them to that page."""
    if not session.get('must_change_password'):
        return
    # Endpoints that must stay reachable so the user can actually change it.
    if request.endpoint in ('change_password_page', 'change_password', 'logout', 'static'):
        return
    if request.path.startswith('/api/'):
        return jsonify({'error': 'Password change required'}), 403
    return redirect(url_for('change_password_page'))


@app.route('/logout')
def logout():
    session.clear()
    return redirect(url_for('login'))


# ---------------------------------------------------------------------------
# Main page
# ---------------------------------------------------------------------------

MEDIA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'media')
INTRO_VIDEO = 'buckets-intro.mp4'


@app.route('/')
@login_required
def index():
    with db_conn() as conn:
        row = conn.execute(
            "SELECT intro_seen FROM users WHERE id=?", (current_user_id(),)
        ).fetchone()
    return render_template('index.html',
                           user_name=session.get('user_name', ''),
                           user_email=session.get('user_email', ''),
                           show_intro=not (row and row['intro_seen']))


@app.route('/media/intro.mp4')
def intro_video():
    """The walkthrough recording — deliberately public, so the sign-in page can
    show it to invitees who don't have a password yet. It's a tour of the demo
    account, not of anyone's real balances. conditional=True keeps Range
    requests working, so the player can seek."""
    return send_from_directory(MEDIA_DIR, INTRO_VIDEO,
                               mimetype='video/mp4', conditional=True)


@app.route('/api/intro-seen', methods=['POST'])
@login_required
def intro_seen():
    """Remember that this user has watched the tour, so it stops auto-opening."""
    try:
        with db_conn() as conn:
            conn.execute("UPDATE users SET intro_seen=1 WHERE id=?",
                         (current_user_id(),))
            conn.commit()
        return jsonify({'ok': True})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


# ---------------------------------------------------------------------------
# Change password
# ---------------------------------------------------------------------------

@app.route('/api/change-password', methods=['POST'])
@login_required
def change_password():
    data    = request.json
    current = data.get('current_password') or ''
    new_pwd = data.get('new_password') or ''
    confirm = data.get('confirm_password') or ''

    if not current or not new_pwd or not confirm:
        return jsonify({'error': 'All fields are required.'}), 400
    if len(new_pwd) < 10:
        return jsonify({'error': 'New password must be at least 10 characters.'}), 400
    if new_pwd != confirm:
        return jsonify({'error': 'New passwords do not match.'}), 400

    try:
        with db_conn() as conn:
            user = conn.execute(
                "SELECT passwd FROM users WHERE id=?", (current_user_id(),)
            ).fetchone()
            if not user or not check_password_hash(user['passwd'], current):
                return jsonify({'error': 'Current password is incorrect.'}), 400
            conn.execute(
                "UPDATE users SET passwd=?, must_change_password=0 WHERE id=?",
                (generate_password_hash(new_pwd), current_user_id()),
            )
            conn.commit()
        session['must_change_password'] = False
        return jsonify({'ok': True})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _fmt(value):
    try:
        return round(float(value), 2)
    except (TypeError, ValueError):
        return 0.0


def _account_balance(conn, acct_id):
    row = conn.execute(
        """SELECT COALESCE(SUM(t.amount), 0) AS bal
           FROM transactions t
           JOIN buckets b ON b.id = t.bucket_id
           WHERE b.acct_id = ? AND t.deleted=0""",
        (acct_id,),
    ).fetchone()
    return _fmt(row['bal'])


def _bucket_stats(conn, bucket_id):
    first_of_month = date.today().replace(day=1).isoformat()

    balance = conn.execute(
        "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE bucket_id=? AND deleted=0",
        (bucket_id,),
    ).fetchone()[0]

    refill_mtd = conn.execute(
        """SELECT COALESCE(SUM(amount),0)
           FROM transactions
           WHERE bucket_id=? AND amount>=0 AND tx_date>=? AND deleted=0""",
        (bucket_id, first_of_month),
    ).fetchone()[0]

    prev_balance = conn.execute(
        """SELECT COALESCE(SUM(amount),0)
           FROM transactions
           WHERE bucket_id=? AND tx_date<? AND deleted=0""",
        (bucket_id, first_of_month),
    ).fetchone()[0]

    return _fmt(balance), _fmt(refill_mtd), _fmt(prev_balance)


# ---------------------------------------------------------------------------
# Accounts API
# ---------------------------------------------------------------------------

@app.route('/api/accounts')
@login_required
def get_accounts():
    try:
        with db_conn() as conn:
            rows = conn.execute(
                "SELECT id, name FROM accounts WHERE user_id=? ORDER BY name",
                (current_user_id(),),
            ).fetchall()
            accounts = [
                {'id': r['id'], 'name': r['name'],
                 'balance': _account_balance(conn, r['id'])}
                for r in rows
            ]
        return jsonify(accounts)
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/accounts', methods=['POST'])
@login_required
def create_account():
    name = (request.json.get('name') or '').strip()
    if not name:
        return jsonify({'error': 'Name is required'}), 400
    try:
        with db_conn() as conn:
            cur = conn.execute(
                "INSERT INTO accounts (name, user_id) VALUES (?,?)",
                (name, current_user_id()),
            )
            acct_id = cur.lastrowid
            # Every account needs a Settlement bucket — it funds all refills.
            conn.execute(
                "INSERT INTO buckets (name, budget, acct_id, refill_factor, is_settlement)"
                " VALUES ('Settlement', 0, ?, 1.0, 1)",
                (acct_id,),
            )
            conn.commit()
        return jsonify({'id': acct_id, 'name': name, 'balance': 0.0}), 201
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/accounts/<int:acct_id>', methods=['PUT'])
@login_required
def update_account(acct_id):
    name = (request.json.get('name') or '').strip()
    if not name:
        return jsonify({'error': 'Name is required'}), 400
    try:
        with db_conn() as conn:
            conn.execute(
                "UPDATE accounts SET name=? WHERE id=? AND user_id=?",
                (name, acct_id, current_user_id()),
            )
            conn.commit()
            balance = _account_balance(conn, acct_id)
        return jsonify({'id': acct_id, 'name': name, 'balance': balance})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/accounts/<int:acct_id>', methods=['DELETE'])
@login_required
def delete_account(acct_id):
    try:
        with db_conn() as conn:
            cnt = conn.execute(
                "SELECT COUNT(*) FROM buckets WHERE acct_id=?", (acct_id,)
            ).fetchone()[0]
            if cnt > 0:
                return jsonify({'error': 'Cannot delete account with buckets'}), 400
            conn.execute(
                "DELETE FROM accounts WHERE id=? AND user_id=?",
                (acct_id, current_user_id()),
            )
            conn.commit()
        return jsonify({'ok': True})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


# ---------------------------------------------------------------------------
# Buckets API
# ---------------------------------------------------------------------------

@app.route('/api/buckets')
@login_required
def get_buckets():
    acct_id = request.args.get('acct_id', type=int)
    include_archived = request.args.get('include_archived', type=int)
    if not acct_id:
        return jsonify([])
    try:
        with db_conn() as conn:
            archived_filter = "" if include_archived else " AND archived=0"
            rows = conn.execute(
                """SELECT id, name, budget, acct_id, refill_factor, is_settlement, archived
                   FROM buckets WHERE acct_id=?""" + archived_filter + " ORDER BY name",
                (acct_id,),
            ).fetchall()
            buckets = []
            for r in rows:
                balance, refill_mtd, prev_balance = _bucket_stats(conn, r['id'])
                buckets.append({
                    'id': r['id'],
                    'name': r['name'],
                    'budget': _fmt(r['budget']),
                    'acct_id': r['acct_id'],
                    'refill_factor': _fmt(r['refill_factor']),
                    'is_settlement': bool(r['is_settlement']),
                    'archived': bool(r['archived']),
                    'balance': balance,
                    'refill_mtd': refill_mtd,
                    'prev_balance': prev_balance,
                })
        return jsonify(buckets)
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/buckets', methods=['POST'])
@login_required
def create_bucket():
    data          = request.json
    name          = (data.get('name') or '').strip()
    budget        = _fmt(data.get('budget', 0))
    acct_id       = data.get('acct_id')
    refill_factor = _fmt(data.get('refill_factor', 1.0))
    if not name or not acct_id:
        return jsonify({'error': 'Name and account are required'}), 400
    try:
        with db_conn() as conn:
            cur = conn.execute(
                "INSERT INTO buckets (name, budget, acct_id, refill_factor) VALUES (?,?,?,?)",
                (name, budget, acct_id, refill_factor),
            )
            conn.commit()
            bkt_id = cur.lastrowid
        return jsonify({
            'id': bkt_id, 'name': name, 'budget': budget,
            'acct_id': acct_id, 'refill_factor': refill_factor,
            'balance': 0.0, 'refill_mtd': 0.0, 'prev_balance': 0.0,
        }), 201
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/buckets/<int:bkt_id>', methods=['PUT'])
@login_required
def update_bucket(bkt_id):
    data          = request.json
    name          = (data.get('name') or '').strip()
    budget        = _fmt(data.get('budget', 0))
    refill_factor = _fmt(data.get('refill_factor', 1.0))
    if not name:
        return jsonify({'error': 'Name is required'}), 400
    try:
        with db_conn() as conn:
            conn.execute(
                "UPDATE buckets SET name=?, budget=?, refill_factor=? WHERE id=?",
                (name, budget, refill_factor, bkt_id),
            )
            conn.commit()
            row = conn.execute(
                "SELECT acct_id FROM buckets WHERE id=?", (bkt_id,)
            ).fetchone()
            balance, refill_mtd, prev_balance = _bucket_stats(conn, bkt_id)
        return jsonify({
            'id': bkt_id, 'name': name, 'budget': budget,
            'refill_factor': refill_factor, 'acct_id': row['acct_id'],
            'balance': balance, 'refill_mtd': refill_mtd, 'prev_balance': prev_balance,
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/buckets/<int:bkt_id>', methods=['DELETE'])
@login_required
def delete_bucket(bkt_id):
    try:
        with db_conn() as conn:
            cnt = conn.execute(
                "SELECT COUNT(*) FROM transactions WHERE bucket_id=?", (bkt_id,)
            ).fetchone()[0]
            if cnt > 0:
                return jsonify({'error': 'Cannot delete bucket with transactions'}), 400
            conn.execute("DELETE FROM buckets WHERE id=?", (bkt_id,))
            conn.commit()
        return jsonify({'ok': True})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/buckets/<int:bkt_id>/archive', methods=['POST'])
@login_required
def archive_bucket(bkt_id):
    try:
        with db_conn() as conn:
            row = conn.execute(
                """SELECT b.id, b.is_settlement FROM buckets b
                   JOIN accounts a ON a.id = b.acct_id
                   WHERE b.id=? AND a.user_id=?""",
                (bkt_id, current_user_id()),
            ).fetchone()
            if not row:
                return jsonify({'error': 'Bucket not found'}), 404
            if row['is_settlement']:
                return jsonify({'error': 'The Settlement bucket cannot be archived'}), 400
            balance = _fmt(conn.execute(
                "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE bucket_id=? AND deleted=0",
                (bkt_id,),
            ).fetchone()[0])
            if balance != 0:
                return jsonify({'error': f'Empty the bucket before archiving (balance ${balance:.2f})'}), 400
            conn.execute("UPDATE buckets SET archived=1 WHERE id=?", (bkt_id,))
            conn.commit()
        return jsonify({'ok': True})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/buckets/<int:bkt_id>/unarchive', methods=['POST'])
@login_required
def unarchive_bucket(bkt_id):
    try:
        with db_conn() as conn:
            row = conn.execute(
                """SELECT b.id FROM buckets b
                   JOIN accounts a ON a.id = b.acct_id
                   WHERE b.id=? AND a.user_id=?""",
                (bkt_id, current_user_id()),
            ).fetchone()
            if not row:
                return jsonify({'error': 'Bucket not found'}), 404
            conn.execute("UPDATE buckets SET archived=0 WHERE id=?", (bkt_id,))
            conn.commit()
        return jsonify({'ok': True})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/buckets/<int:bkt_id>/transactions')
@login_required
def get_bucket_transactions(bkt_id):
    try:
        with db_conn() as conn:
            bucket = conn.execute(
                """SELECT b.id, b.name, b.budget, b.refill_factor, b.is_settlement
                   FROM buckets b
                   JOIN accounts a ON a.id = b.acct_id
                   WHERE b.id=? AND a.user_id=?""",
                (bkt_id, current_user_id()),
            ).fetchone()
            if not bucket:
                return jsonify({'error': 'Bucket not found'}), 404

            rows = conn.execute(
                """SELECT id, tx_date, amount, note, posted, linked_tx_id
                   FROM transactions WHERE bucket_id=? AND deleted=0
                   ORDER BY tx_date ASC, id ASC""",
                (bkt_id,),
            ).fetchall()

            running = 0.0
            transactions = []
            for r in rows:
                running = round(running + _fmt(r['amount']), 2)
                transactions.append({
                    'id':           r['id'],
                    'tx_date':      r['tx_date'],
                    'amount':       _fmt(r['amount']),
                    'note':         r['note'] or '',
                    'posted':       bool(r['posted']),
                    'running':      running,
                    'linked_tx_id': r['linked_tx_id'],
                })

        return jsonify({
            'bucket': {
                'id':            bucket['id'],
                'name':          bucket['name'],
                'budget':        _fmt(bucket['budget']),
                'refill_factor': _fmt(bucket['refill_factor']),
                'balance':       running,
                'is_settlement': bool(bucket['is_settlement']),
            },
            'transactions': transactions,
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/buckets/<int:bkt_id>/refill', methods=['POST'])
@login_required
def refill_bucket(bkt_id):
    try:
        with db_conn() as conn:
            row = conn.execute(
                "SELECT b.budget, b.refill_factor, b.acct_id, b.archived FROM buckets b WHERE b.id=?", (bkt_id,)
            ).fetchone()
            if not row:
                return jsonify({'error': 'Bucket not found'}), 404
            if row['archived']:
                return jsonify({'error': 'Cannot refill an archived bucket'}), 400
            settlement = conn.execute(
                "SELECT id FROM buckets WHERE acct_id=? AND is_settlement=1", (row['acct_id'],)
            ).fetchone()
            amount = _fmt(row['budget'] * row['refill_factor'])
            if settlement:
                s_balance = _fmt(conn.execute(
                    "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE bucket_id=? AND deleted=0",
                    (settlement['id'],)
                ).fetchone()[0])
                if s_balance - amount < 0:
                    return jsonify({'error': f'Insufficient Settlement balance (${s_balance:.2f}) to refill ${amount:.2f}'}), 400
            today = date.today().isoformat()
            conn.execute(
                "INSERT INTO transactions (tx_date, bucket_id, amount, note, posted) VALUES (?,?,?,?,0)",
                (today, bkt_id, amount, 'Refill'),
            )
            if settlement:
                conn.execute(
                    "INSERT INTO transactions (tx_date, bucket_id, amount, note, posted) VALUES (?,?,?,?,0)",
                    (today, settlement['id'], -amount, 'Refill'),
                )
            conn.commit()
        return jsonify({'ok': True, 'amount': amount})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/buckets/refill-all', methods=['POST'])
@login_required
def refill_all_buckets():
    acct_id = request.json.get('acct_id')
    if not acct_id:
        return jsonify({'error': 'acct_id required'}), 400
    try:
        with db_conn() as conn:
            settlement = conn.execute(
                "SELECT id FROM buckets WHERE acct_id=? AND is_settlement=1", (acct_id,)
            ).fetchone()
            buckets = conn.execute(
                "SELECT id, budget, refill_factor FROM buckets WHERE acct_id=? AND budget > 0 AND is_settlement=0 AND archived=0",
                (acct_id,)
            ).fetchall()
            total = _fmt(sum(_fmt(b['budget'] * b['refill_factor']) for b in buckets))
            if settlement and total > 0:
                s_balance = _fmt(conn.execute(
                    "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE bucket_id=? AND deleted=0",
                    (settlement['id'],)
                ).fetchone()[0])
                if s_balance - total < 0:
                    return jsonify({'error': f'Insufficient Settlement balance (${s_balance:.2f}) to refill ${total:.2f}'}), 400
            today = date.today().isoformat()
            count = 0
            for b in buckets:
                amount = _fmt(b['budget'] * b['refill_factor'])
                conn.execute(
                    "INSERT INTO transactions (tx_date, bucket_id, amount, note, posted) VALUES (?,?,?,?,0)",
                    (today, b['id'], amount, 'Refill'),
                )
                count += 1
            if settlement and total > 0:
                conn.execute(
                    "INSERT INTO transactions (tx_date, bucket_id, amount, note, posted) VALUES (?,?,?,?,0)",
                    (today, settlement['id'], -total, 'Refill'),
                )
            conn.commit()
        return jsonify({'ok': True, 'count': count})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/buckets/reset', methods=['POST'])
@login_required
def reset_buckets():
    acct_id = request.json.get('acct_id')
    if not acct_id:
        return jsonify({'error': 'acct_id required'}), 400
    try:
        with db_conn() as conn:
            settlement = conn.execute(
                "SELECT id FROM buckets WHERE acct_id=? AND is_settlement=1", (acct_id,)
            ).fetchone()
            if not settlement:
                return jsonify({'error': 'No Settlement bucket for this account'}), 400
            settlement_id = settlement['id']
            buckets = conn.execute(
                "SELECT id FROM buckets WHERE acct_id=? AND is_settlement=0 AND archived=0", (acct_id,)
            ).fetchall()
            today = date.today().isoformat()
            swept = 0.0
            for b in buckets:
                balance = conn.execute(
                    "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE bucket_id=? AND deleted=0",
                    (b['id'],),
                ).fetchone()[0]
                if balance > 0:
                    conn.execute(
                        "INSERT INTO transactions (tx_date, bucket_id, amount, note, posted)"
                        " VALUES (?,?,?,?,0)",
                        (today, b['id'], -balance, 'Reset'),
                    )
                    swept += balance
            if swept != 0:
                conn.execute(
                    "INSERT INTO transactions (tx_date, bucket_id, amount, note, posted)"
                    " VALUES (?,?,?,?,0)",
                    (today, settlement_id, _fmt(swept), 'Reset sweep'),
                )
            conn.commit()
        return jsonify({'ok': True})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/transfers', methods=['POST'])
@login_required
def transfer():
    data    = request.json
    from_id = data.get('from_bucket_id')
    to_id   = data.get('to_bucket_id')
    amount  = _fmt(data.get('amount', 0))
    note    = (data.get('note') or '').strip()
    tx_date = (data.get('tx_date') or '').strip() or date.today().isoformat()
    if not from_id or not to_id or amount <= 0:
        return jsonify({'error': 'from_bucket_id, to_bucket_id, and positive amount required'}), 400
    if from_id == to_id:
        return jsonify({'error': 'Cannot transfer to the same bucket'}), 400
    try:
        with db_conn() as conn:
            archived = conn.execute(
                "SELECT COUNT(*) FROM buckets WHERE id IN (?,?) AND archived=1", (from_id, to_id)
            ).fetchone()[0]
            if archived:
                return jsonify({'error': 'Cannot transfer to or from an archived bucket'}), 400
            from_balance = _fmt(conn.execute(
                "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE bucket_id=? AND deleted=0", (from_id,)
            ).fetchone()[0])
            if from_balance - amount < 0:
                return jsonify({'error': f'Insufficient balance (${from_balance:.2f}) — overdraft not allowed'}), 400
            cur1 = conn.execute(
                "INSERT INTO transactions (tx_date, bucket_id, amount, note, posted) VALUES (?,?,?,?,1)",
                (tx_date, from_id, -amount, note or f'Transfer to bucket #{to_id}'),
            )
            id1 = cur1.lastrowid
            cur2 = conn.execute(
                "INSERT INTO transactions (tx_date, bucket_id, amount, note, posted) VALUES (?,?,?,?,1)",
                (tx_date, to_id, amount, note or f'Transfer from bucket #{from_id}'),
            )
            id2 = cur2.lastrowid
            conn.execute("UPDATE transactions SET linked_tx_id=? WHERE id=?", (id2, id1))
            conn.execute("UPDATE transactions SET linked_tx_id=? WHERE id=?", (id1, id2))
            conn.commit()
        return jsonify({'ok': True})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/account-transfers', methods=['POST'])
@login_required
def account_transfer():
    data      = request.json
    from_acct = data.get('from_acct_id')
    to_acct   = data.get('to_acct_id')
    amount    = _fmt(data.get('amount', 0))
    note      = (data.get('note') or '').strip()
    tx_date   = (data.get('tx_date') or '').strip() or date.today().isoformat()
    if not from_acct or not to_acct or amount <= 0:
        return jsonify({'error': 'from_acct_id, to_acct_id, and positive amount required'}), 400
    if from_acct == to_acct:
        return jsonify({'error': 'Cannot transfer to the same account'}), 400
    try:
        with db_conn() as conn:
            # Resolve the Settlement bucket for each account, verifying ownership.
            def settlement_for(acct_id):
                return conn.execute(
                    """SELECT b.id FROM buckets b
                       JOIN accounts a ON a.id = b.acct_id
                       WHERE b.acct_id=? AND b.is_settlement=1 AND a.user_id=?""",
                    (acct_id, current_user_id()),
                ).fetchone()

            from_row = settlement_for(from_acct)
            to_row   = settlement_for(to_acct)
            if not from_row or not to_row:
                return jsonify({'error': 'Account not found'}), 404
            from_settlement = from_row['id']
            to_settlement   = to_row['id']

            from_balance = _fmt(conn.execute(
                "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE bucket_id=? AND deleted=0",
                (from_settlement,),
            ).fetchone()[0])
            if from_balance - amount < 0:
                return jsonify({'error': f'Insufficient Settlement balance (${from_balance:.2f}) — overdraft not allowed'}), 400

            cur1 = conn.execute(
                "INSERT INTO transactions (tx_date, bucket_id, amount, note, posted) VALUES (?,?,?,?,1)",
                (tx_date, from_settlement, -amount, note or f'Transfer to account #{to_acct}'),
            )
            id1 = cur1.lastrowid
            cur2 = conn.execute(
                "INSERT INTO transactions (tx_date, bucket_id, amount, note, posted) VALUES (?,?,?,?,1)",
                (tx_date, to_settlement, amount, note or f'Transfer from account #{from_acct}'),
            )
            id2 = cur2.lastrowid
            conn.execute("UPDATE transactions SET linked_tx_id=? WHERE id=?", (id2, id1))
            conn.execute("UPDATE transactions SET linked_tx_id=? WHERE id=?", (id1, id2))
            conn.commit()
        return jsonify({'ok': True})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


# ---------------------------------------------------------------------------
# Upcoming Expenses API
# ---------------------------------------------------------------------------

@app.route('/api/buckets/<int:bkt_id>/upcoming')
@login_required
def get_upcoming(bkt_id):
    try:
        with db_conn() as conn:
            bucket = conn.execute(
                """SELECT b.id FROM buckets b
                   JOIN accounts a ON a.id = b.acct_id
                   WHERE b.id=? AND a.user_id=?""",
                (bkt_id, current_user_id()),
            ).fetchone()
            if not bucket:
                return jsonify({'error': 'Bucket not found'}), 404
            rows = conn.execute(
                """SELECT id, description, amount, due_date, notes
                   FROM upcoming_expenses WHERE bucket_id=?
                   ORDER BY due_date ASC, id ASC""",
                (bkt_id,),
            ).fetchall()
            items = [
                {
                    'id':          r['id'],
                    'description': r['description'],
                    'amount':      _fmt(r['amount']),
                    'due_date':    r['due_date'] or '',
                    'notes':       r['notes'] or '',
                }
                for r in rows
            ]
        return jsonify(items)
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/buckets/<int:bkt_id>/upcoming', methods=['POST'])
@login_required
def create_upcoming(bkt_id):
    data        = request.json
    description = (data.get('description') or '').strip()
    amount      = _fmt(data.get('amount', 0))
    due_date    = (data.get('due_date') or '').strip()
    notes       = (data.get('notes') or '').strip()
    if not description:
        return jsonify({'error': 'Description is required'}), 400
    try:
        with db_conn() as conn:
            cur = conn.execute(
                "INSERT INTO upcoming_expenses (bucket_id, description, amount, due_date, notes) VALUES (?,?,?,?,?)",
                (bkt_id, description, amount, due_date, notes),
            )
            conn.commit()
            item_id = cur.lastrowid
        return jsonify({
            'id': item_id, 'description': description, 'amount': amount,
            'due_date': due_date, 'notes': notes,
        }), 201
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/upcoming/<int:item_id>', methods=['PUT'])
@login_required
def update_upcoming(item_id):
    data        = request.json
    description = (data.get('description') or '').strip()
    amount      = _fmt(data.get('amount', 0))
    due_date    = (data.get('due_date') or '').strip()
    notes       = (data.get('notes') or '').strip()
    if not description:
        return jsonify({'error': 'Description is required'}), 400
    try:
        with db_conn() as conn:
            conn.execute(
                "UPDATE upcoming_expenses SET description=?, amount=?, due_date=?, notes=? WHERE id=?",
                (description, amount, due_date, notes, item_id),
            )
            conn.commit()
        return jsonify({
            'id': item_id, 'description': description, 'amount': amount,
            'due_date': due_date, 'notes': notes,
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/upcoming/<int:item_id>', methods=['DELETE'])
@login_required
def delete_upcoming(item_id):
    try:
        with db_conn() as conn:
            conn.execute("DELETE FROM upcoming_expenses WHERE id=?", (item_id,))
            conn.commit()
        return jsonify({'ok': True})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


# ---------------------------------------------------------------------------
# Transactions API
# ---------------------------------------------------------------------------

@app.route('/api/transactions')
@login_required
def get_transactions():
    acct_id  = request.args.get('acct_id', type=int)
    page     = request.args.get('page', 1, type=int)
    per_page = request.args.get('per_page', 25, type=int)

    if not acct_id:
        return jsonify({'transactions': [], 'total': 0, 'page': 1, 'pages': 0})

    try:
        with db_conn() as conn:
            base_sql = """
                FROM transactions t
                JOIN buckets b ON b.id = t.bucket_id
                WHERE b.acct_id = ? AND t.deleted=0
            """
            params = [acct_id]

            total  = conn.execute("SELECT COUNT(*) " + base_sql, params).fetchone()[0]
            offset = (page - 1) * per_page
            rows   = conn.execute(
                "SELECT t.id, t.tx_date, b.name AS bucket, t.bucket_id,"
                "       t.amount, t.note, t.posted, t.linked_tx_id "
                + base_sql
                + " ORDER BY t.tx_date DESC, t.id DESC LIMIT ? OFFSET ?",
                params + [per_page, offset],
            ).fetchall()

            skipped_sum = 0.0
            if offset > 0:
                skipped_sum = _fmt(conn.execute(
                    "SELECT COALESCE(SUM(amount),0) FROM (SELECT t.amount "
                    + base_sql
                    + " ORDER BY t.tx_date DESC, t.id DESC LIMIT ?)",
                    params + [offset],
                ).fetchone()[0])
            running = _fmt(_account_balance(conn, acct_id) - skipped_sum)

            transactions = []
            for r in rows:
                transactions.append({
                    'id':           r['id'],
                    'tx_date':      r['tx_date'],
                    'bucket':       r['bucket'],
                    'bucket_id':    r['bucket_id'],
                    'amount':       _fmt(r['amount']),
                    'note':         r['note'] or '',
                    'posted':       bool(r['posted']),
                    'linked_tx_id': r['linked_tx_id'],
                    'balance':      running,
                })
                running = _fmt(running - _fmt(r['amount']))

        pages = max(1, (total + per_page - 1) // per_page)
        return jsonify({
            'transactions': transactions,
            'total': total,
            'page': page,
            'pages': pages,
            'per_page': per_page,
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/transactions', methods=['POST'])
@login_required
def create_transaction():
    data      = request.json
    tx_date   = (data.get('tx_date') or date.today().isoformat()).strip()
    bucket_id = data.get('bucket_id')
    amount    = _fmt(data.get('amount', 0))
    note      = (data.get('note') or '').strip()
    if not bucket_id:
        return jsonify({'error': 'bucket_id is required'}), 400
    try:
        with db_conn() as conn:
            if amount == 0:
                return jsonify({'error': 'Amount cannot be zero.'}), 400
            archived = conn.execute(
                "SELECT archived FROM buckets WHERE id=?", (bucket_id,)
            ).fetchone()
            if archived and archived['archived']:
                return jsonify({'error': 'Cannot add a transaction to an archived bucket'}), 400
            if amount < 0:
                balance = _fmt(conn.execute(
                    "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE bucket_id=? AND deleted=0", (bucket_id,)
                ).fetchone()[0])
                if balance + amount < 0:
                    return jsonify({'error': f'Insufficient balance (${balance:.2f}) — overdraft not allowed'}), 400
            cur = conn.execute(
                "INSERT INTO transactions (tx_date, bucket_id, amount, note, posted)"
                " VALUES (?,?,?,?,0)",
                (tx_date, bucket_id, amount, note),
            )
            conn.commit()
            tx_id  = cur.lastrowid
            bucket = conn.execute(
                "SELECT name FROM buckets WHERE id=?", (bucket_id,)
            ).fetchone()
        return jsonify({
            'id': tx_id, 'tx_date': tx_date, 'bucket_id': bucket_id,
            'bucket': bucket['name'] if bucket else '',
            'amount': amount, 'note': note, 'posted': False,
        }), 201
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/transactions/<int:tx_id>', methods=['PUT'])
@login_required
def update_transaction(tx_id):
    data      = request.json
    tx_date   = (data.get('tx_date') or date.today().isoformat()).strip()
    bucket_id = data.get('bucket_id')
    amount    = _fmt(data.get('amount', 0))
    note      = (data.get('note') or '').strip()
    try:
        with db_conn() as conn:
            if amount == 0:
                return jsonify({'error': 'Amount cannot be zero.'}), 400
            archived = conn.execute(
                "SELECT archived FROM buckets WHERE id=?", (bucket_id,)
            ).fetchone()
            if archived and archived['archived']:
                return jsonify({'error': 'Cannot add a transaction to an archived bucket'}), 400
            if amount < 0:
                balance = _fmt(conn.execute(
                    "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE bucket_id=? AND id!=? AND deleted=0",
                    (bucket_id, tx_id)
                ).fetchone()[0])
                if balance + amount < 0:
                    return jsonify({'error': f'Insufficient balance (${balance:.2f}) — overdraft not allowed'}), 400
            conn.execute(
                "UPDATE transactions SET tx_date=?, bucket_id=?, amount=?, note=? WHERE id=?",
                (tx_date, bucket_id, amount, note, tx_id),
            )
            conn.commit()
            bucket = conn.execute(
                "SELECT name FROM buckets WHERE id=?", (bucket_id,)
            ).fetchone()
        return jsonify({
            'id': tx_id, 'tx_date': tx_date, 'bucket_id': bucket_id,
            'bucket': bucket['name'] if bucket else '',
            'amount': amount, 'note': note,
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/transactions/<int:tx_id>', methods=['DELETE'])
@login_required
def delete_transaction(tx_id):
    try:
        with db_conn() as conn:
            row = conn.execute(
                "SELECT linked_tx_id FROM transactions WHERE id=?", (tx_id,)
            ).fetchone()
            linked_id = row['linked_tx_id'] if row else None
            if linked_id:
                conn.execute("UPDATE transactions SET deleted=1 WHERE id IN (?,?)", (tx_id, linked_id))
            else:
                conn.execute("UPDATE transactions SET deleted=1 WHERE id=?", (tx_id,))
            conn.commit()
        return jsonify({'ok': True, 'deleted_linked': bool(linked_id)})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/transactions/deleted')
@login_required
def get_deleted_transactions():
    acct_id = request.args.get('acct_id', type=int)
    if not acct_id:
        return jsonify([])
    try:
        with db_conn() as conn:
            rows = conn.execute(
                """SELECT t.id, t.tx_date, b.name AS bucket, t.bucket_id,
                          t.amount, t.note, t.posted, t.linked_tx_id
                   FROM transactions t
                   JOIN buckets b ON b.id = t.bucket_id
                   WHERE b.acct_id = ? AND t.deleted=1
                   ORDER BY t.tx_date DESC, t.id DESC""",
                (acct_id,),
            ).fetchall()
            transactions = [
                {
                    'id':           r['id'],
                    'tx_date':      r['tx_date'],
                    'bucket':       r['bucket'],
                    'bucket_id':    r['bucket_id'],
                    'amount':       _fmt(r['amount']),
                    'note':         r['note'] or '',
                    'posted':       bool(r['posted']),
                    'linked_tx_id': r['linked_tx_id'],
                }
                for r in rows
            ]
        return jsonify(transactions)
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/transactions/deleted', methods=['DELETE'])
@login_required
def purge_deleted_transactions():
    acct_id = request.args.get('acct_id', type=int)
    if not acct_id:
        return jsonify({'error': 'acct_id required'}), 400
    try:
        with db_conn() as conn:
            result = conn.execute(
                """DELETE FROM transactions
                   WHERE deleted=1 AND bucket_id IN (
                       SELECT id FROM buckets WHERE acct_id=?
                   )""",
                (acct_id,),
            )
            conn.commit()
        return jsonify({'ok': True, 'purged': result.rowcount})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/transactions/<int:tx_id>/restore', methods=['POST'])
@login_required
def restore_transaction(tx_id):
    try:
        with db_conn() as conn:
            row = conn.execute(
                """SELECT t.linked_tx_id
                   FROM transactions t
                   JOIN buckets b ON b.id = t.bucket_id
                   JOIN accounts a ON a.id = b.acct_id
                   WHERE t.id=? AND a.user_id=? AND t.deleted=1""",
                (tx_id, current_user_id()),
            ).fetchone()
            if not row:
                return jsonify({'error': 'Transaction not found or not deleted'}), 404
            linked_id = row['linked_tx_id']
            if linked_id:
                conn.execute("UPDATE transactions SET deleted=0 WHERE id IN (?,?)", (tx_id, linked_id))
            else:
                conn.execute("UPDATE transactions SET deleted=0 WHERE id=?", (tx_id,))
            conn.commit()
        return jsonify({'ok': True, 'restored_linked': bool(linked_id)})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/transactions/purge-batch', methods=['POST'])
@login_required
def purge_batch_transactions():
    ids = request.json.get('ids', [])
    if not ids:
        return jsonify({'ok': True, 'purged': 0})
    try:
        with db_conn() as conn:
            to_delete = set()
            for tx_id in ids:
                row = conn.execute(
                    """SELECT t.id, t.linked_tx_id
                       FROM transactions t
                       JOIN buckets b ON b.id = t.bucket_id
                       JOIN accounts a ON a.id = b.acct_id
                       WHERE t.id=? AND a.user_id=? AND t.deleted=1""",
                    (tx_id, current_user_id()),
                ).fetchone()
                if row:
                    to_delete.add(row['id'])
                    if row['linked_tx_id']:
                        to_delete.add(row['linked_tx_id'])
            if to_delete:
                placeholders = ','.join('?' * len(to_delete))
                conn.execute(f"DELETE FROM transactions WHERE id IN ({placeholders})", list(to_delete))
                conn.commit()
        return jsonify({'ok': True, 'purged': len(to_delete)})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/transactions/<int:tx_id>/purge', methods=['DELETE'])
@login_required
def purge_transaction(tx_id):
    try:
        with db_conn() as conn:
            row = conn.execute(
                """SELECT t.linked_tx_id
                   FROM transactions t
                   JOIN buckets b ON b.id = t.bucket_id
                   JOIN accounts a ON a.id = b.acct_id
                   WHERE t.id=? AND a.user_id=? AND t.deleted=1""",
                (tx_id, current_user_id()),
            ).fetchone()
            if not row:
                return jsonify({'ok': True})
            linked_id = row['linked_tx_id']
            if linked_id:
                conn.execute("DELETE FROM transactions WHERE id IN (?,?)", (tx_id, linked_id))
            else:
                conn.execute("DELETE FROM transactions WHERE id=?", (tx_id,))
            conn.commit()
        return jsonify({'ok': True, 'purged_linked': bool(linked_id)})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/transactions/<int:tx_id>/post', methods=['POST'])
@login_required
def post_transaction(tx_id):
    try:
        with db_conn() as conn:
            conn.execute("UPDATE transactions SET posted=1 WHERE id=?", (tx_id,))
            conn.commit()
        return jsonify({'ok': True})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/transactions/<int:tx_id>/unpost', methods=['POST'])
@login_required
def unpost_transaction(tx_id):
    try:
        with db_conn() as conn:
            conn.execute("UPDATE transactions SET posted=0 WHERE id=?", (tx_id,))
            conn.commit()
        return jsonify({'ok': True})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/transactions/export')
@login_required
def export_transactions():
    acct_id = request.args.get('acct_id', type=int)
    if not acct_id:
        return jsonify({'error': 'acct_id required'}), 400
    try:
        with db_conn() as conn:
            rows = conn.execute(
                """SELECT t.tx_date, b.name AS bucket, t.amount, t.note, t.posted
                   FROM transactions t
                   JOIN buckets b ON b.id = t.bucket_id
                   WHERE b.acct_id = ? AND t.deleted=0
                   ORDER BY t.tx_date ASC, t.id ASC""",
                (acct_id,),
            ).fetchall()

        output = io.StringIO()
        writer = csv.writer(output)
        writer.writerow(['Date', 'Bucket', 'Amount', 'Note', 'Posted'])
        for r in rows:
            writer.writerow([r['tx_date'], r['bucket'], r['amount'],
                             r['note'] or '', 'Yes' if r['posted'] else 'No'])

        resp = make_response(output.getvalue())
        resp.headers['Content-Type'] = 'text/csv'
        resp.headers['Content-Disposition'] = 'attachment; filename=transactions.csv'
        return resp
    except Exception as e:
        return jsonify({'error': str(e)}), 500


# ---------------------------------------------------------------------------
# Summary API
# ---------------------------------------------------------------------------

@app.route('/api/summary')
@login_required
def summary():
    acct_id = request.args.get('acct_id', type=int)
    if not acct_id:
        return jsonify({})
    try:
        with db_conn() as conn:
            total_balance = _account_balance(conn, acct_id)
            bucket_count  = conn.execute(
                "SELECT COUNT(*) FROM buckets WHERE acct_id=? AND archived=0", (acct_id,)
            ).fetchone()[0]
            tx_count = conn.execute(
                """SELECT COUNT(*) FROM transactions t
                   JOIN buckets b ON b.id = t.bucket_id WHERE b.acct_id=? AND t.deleted=0""",
                (acct_id,),
            ).fetchone()[0]
            first_of_month = date.today().replace(day=1).isoformat()
            month_spending = conn.execute(
                """SELECT COALESCE(SUM(t.amount),0)
                   FROM transactions t JOIN buckets b ON b.id=t.bucket_id
                   WHERE b.acct_id=? AND t.amount<0 AND t.tx_date>=? AND t.deleted=0""",
                (acct_id, first_of_month),
            ).fetchone()[0]
        return jsonify({
            'total_balance': _fmt(total_balance),
            'bucket_count':  bucket_count,
            'tx_count':      tx_count,
            'month_spending': _fmt(month_spending),
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500


# ---------------------------------------------------------------------------
# IOU / Payment Requests API
# ---------------------------------------------------------------------------

@app.route('/api/users/lookup')
@login_required
def lookup_user():
    email = (request.args.get('email') or '').strip().lower()
    if not email:
        return jsonify({'error': 'email parameter required'}), 400
    try:
        with db_conn() as conn:
            me = conn.execute(
                "SELECT email FROM users WHERE id=?", (current_user_id(),)
            ).fetchone()
            if me and me['email'].lower() == email:
                return jsonify({'error': 'You cannot send an IOU to yourself.'}), 404
            user = conn.execute(
                "SELECT id, name FROM users WHERE LOWER(email)=?", (email,)
            ).fetchone()
        if not user:
            return jsonify({'error': 'No user found with that email.'}), 404
        return jsonify({'id': user['id'], 'name': user['name']})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/iou', methods=['POST'])
@login_required
def create_iou():
    data        = request.json
    payee_email = (data.get('payee_email') or '').strip().lower()
    amount      = _fmt(data.get('amount', 0))
    description = (data.get('description') or '').strip()
    due_date    = (data.get('due_date') or '').strip()
    notes       = (data.get('notes') or '').strip()

    if not payee_email:
        return jsonify({'error': 'payee_email is required'}), 400
    if not description or amount <= 0:
        return jsonify({'error': 'description and a positive amount are required'}), 400

    try:
        with db_conn() as conn:
            me = conn.execute(
                "SELECT email FROM users WHERE id=?", (current_user_id(),)
            ).fetchone()
            if me and me['email'].lower() == payee_email:
                return jsonify({'error': 'You cannot send an IOU to yourself.'}), 400
            payee = conn.execute(
                "SELECT id, name FROM users WHERE LOWER(email)=?", (payee_email,)
            ).fetchone()
            if not payee:
                return jsonify({'error': 'No user found with that email.'}), 404
            today = date.today().isoformat()
            cur = conn.execute(
                """INSERT INTO iou_requests
                   (requester_id, payee_id, amount, description, due_date, notes, status, created_at, updated_at)
                   VALUES (?,?,?,?,?,?,'pending',?,?)""",
                (current_user_id(), payee['id'], amount, description, due_date, notes, today, today),
            )
            conn.commit()
            iou_id = cur.lastrowid
        return jsonify({
            'id': iou_id, 'payee_id': payee['id'], 'payee_name': payee['name'],
            'amount': amount, 'description': description, 'due_date': due_date,
            'notes': notes, 'status': 'pending', 'linked_tx_id': None, 'created_at': today,
        }), 201
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/iou')
@login_required
def get_iou():
    uid = current_user_id()
    try:
        with db_conn() as conn:
            rows = conn.execute(
                """SELECT r.id, r.requester_id, r.payee_id,
                          r.amount, r.description, r.due_date, r.notes,
                          r.status, r.linked_tx_id, r.created_at, r.updated_at,
                          u_req.name  AS requester_name,  u_req.email  AS requester_email,
                          u_pay.name  AS payee_name,      u_pay.email  AS payee_email,
                          t.tx_date   AS linked_tx_date,  t.note       AS linked_tx_note,
                          t.amount    AS linked_tx_amount
                   FROM iou_requests r
                   JOIN users u_req ON u_req.id = r.requester_id
                   JOIN users u_pay ON u_pay.id = r.payee_id
                   LEFT JOIN transactions t ON t.id = r.linked_tx_id
                   WHERE r.requester_id = ? OR r.payee_id = ?
                   ORDER BY r.created_at DESC, r.id DESC""",
                (uid, uid),
            ).fetchall()

            items = []
            for r in rows:
                role = 'requester' if r['requester_id'] == uid else 'payee'
                other_name  = r['payee_name']   if role == 'requester' else r['requester_name']
                other_email = r['payee_email']  if role == 'requester' else r['requester_email']
                items.append({
                    'id':               r['id'],
                    'role':             role,
                    'other_user_name':  other_name,
                    'other_user_email': other_email,
                    'amount':           _fmt(r['amount']),
                    'description':      r['description'] or '',
                    'due_date':         r['due_date'] or '',
                    'notes':            r['notes'] or '',
                    'status':           r['status'],
                    'linked_tx_id':     r['linked_tx_id'],
                    'linked_tx_date':   r['linked_tx_date'],
                    'linked_tx_note':   r['linked_tx_note'],
                    'linked_tx_amount': _fmt(r['linked_tx_amount']) if r['linked_tx_amount'] is not None else None,
                    'created_at':       r['created_at'],
                    'updated_at':       r['updated_at'],
                })
        return jsonify(items)
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/iou/<int:iou_id>/cancel', methods=['POST'])
@login_required
def cancel_iou(iou_id):
    try:
        with db_conn() as conn:
            row = conn.execute("SELECT * FROM iou_requests WHERE id=?", (iou_id,)).fetchone()
            if not row:
                return jsonify({'error': 'IOU not found'}), 404
            if row['requester_id'] != current_user_id():
                return jsonify({'error': 'Only the requester can cancel.'}), 403
            if row['status'] == 'cancelled':
                return jsonify({'error': 'Already cancelled.'}), 400
            if row['status'] == 'settled':
                return jsonify({'error': 'Cannot cancel a settled IOU.'}), 400
            conn.execute(
                "UPDATE iou_requests SET status='cancelled', updated_at=? WHERE id=?",
                (date.today().isoformat(), iou_id),
            )
            conn.commit()
        return jsonify({'ok': True, 'status': 'cancelled'})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/iou/<int:iou_id>/settle', methods=['POST'])
@login_required
def settle_iou(iou_id):
    try:
        with db_conn() as conn:
            row = conn.execute("SELECT * FROM iou_requests WHERE id=?", (iou_id,)).fetchone()
            if not row:
                return jsonify({'error': 'IOU not found'}), 404
            if row['requester_id'] != current_user_id():
                return jsonify({'error': 'Only the requester can mark as settled.'}), 403
            if row['status'] == 'cancelled':
                return jsonify({'error': 'Cannot settle a cancelled IOU.'}), 400
            if row['status'] == 'settled':
                return jsonify({'error': 'Already settled.'}), 400
            conn.execute(
                "UPDATE iou_requests SET status='settled', updated_at=? WHERE id=?",
                (date.today().isoformat(), iou_id),
            )
            conn.commit()
        return jsonify({'ok': True, 'status': 'settled'})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/iou/<int:iou_id>/link', methods=['POST'])
@login_required
def link_iou(iou_id):
    tx_id = request.json.get('tx_id')
    if not tx_id:
        return jsonify({'error': 'tx_id is required'}), 400
    try:
        with db_conn() as conn:
            row = conn.execute("SELECT * FROM iou_requests WHERE id=?", (iou_id,)).fetchone()
            if not row:
                return jsonify({'error': 'IOU not found'}), 404
            if row['payee_id'] != current_user_id():
                return jsonify({'error': 'Only the payee can link a transaction.'}), 403
            if row['status'] in ('cancelled', 'settled'):
                return jsonify({'error': 'Cannot link to a cancelled or settled IOU.'}), 400
            tx = conn.execute(
                """SELECT t.id, t.tx_date, t.note, t.amount
                   FROM transactions t
                   JOIN buckets b ON b.id = t.bucket_id
                   JOIN accounts a ON a.id = b.acct_id
                   WHERE t.id = ? AND a.user_id = ?""",
                (tx_id, current_user_id()),
            ).fetchone()
            if not tx:
                return jsonify({'error': 'Transaction not found or does not belong to you.'}), 404
            conn.execute(
                "UPDATE iou_requests SET linked_tx_id=?, status='linked', updated_at=? WHERE id=?",
                (tx_id, date.today().isoformat(), iou_id),
            )
            conn.commit()
        return jsonify({
            'ok': True, 'status': 'linked', 'linked_tx_id': tx_id,
            'linked_tx_note': tx['note'], 'linked_tx_date': tx['tx_date'],
            'linked_tx_amount': _fmt(tx['amount']),
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/iou/<int:iou_id>/unlink', methods=['POST'])
@login_required
def unlink_iou(iou_id):
    try:
        with db_conn() as conn:
            row = conn.execute("SELECT * FROM iou_requests WHERE id=?", (iou_id,)).fetchone()
            if not row:
                return jsonify({'error': 'IOU not found'}), 404
            if row['payee_id'] != current_user_id():
                return jsonify({'error': 'Only the payee can unlink.'}), 403
            if row['status'] != 'linked':
                return jsonify({'error': 'IOU is not linked.'}), 400
            conn.execute(
                "UPDATE iou_requests SET linked_tx_id=NULL, status='pending', updated_at=? WHERE id=?",
                (date.today().isoformat(), iou_id),
            )
            conn.commit()
        return jsonify({'ok': True, 'status': 'pending'})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/iou/transactions')
@login_required
def iou_transactions():
    page     = request.args.get('page', 1, type=int)
    per_page = request.args.get('per_page', 20, type=int)
    q        = (request.args.get('q') or '').strip()
    uid      = current_user_id()

    try:
        with db_conn() as conn:
            base_sql = """
                FROM transactions t
                JOIN buckets b ON b.id = t.bucket_id
                JOIN accounts a ON a.id = b.acct_id
                WHERE a.user_id = ? AND t.deleted=0
            """
            params = [uid]
            if q:
                base_sql += " AND t.note LIKE ?"
                params.append(f'%{q}%')

            total  = conn.execute("SELECT COUNT(*) " + base_sql, params).fetchone()[0]
            offset = (page - 1) * per_page
            rows   = conn.execute(
                "SELECT t.id, t.tx_date, t.amount, t.note, b.name AS bucket, t.bucket_id"
                + base_sql
                + " ORDER BY t.tx_date DESC, t.id DESC LIMIT ? OFFSET ?",
                params + [per_page, offset],
            ).fetchall()

            transactions = [
                {
                    'id':        r['id'],
                    'tx_date':   r['tx_date'],
                    'amount':    _fmt(r['amount']),
                    'note':      r['note'] or '',
                    'bucket':    r['bucket'],
                    'bucket_id': r['bucket_id'],
                }
                for r in rows
            ]

        pages = max(1, (total + per_page - 1) // per_page)
        return jsonify({
            'transactions': transactions,
            'total': total,
            'page': page,
            'pages': pages,
            'per_page': per_page,
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500


init_db()

if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=8080)
