#!/usr/bin/env python3
"""Command-line user management for a self-hosted, invite-only Buckets deployment.

Run this on the VM (inside the same virtualenv as the app) to provision and
manage accounts. There is no web-based signup or password reset — this script
is the only way in.

Usage:
    python manage_users.py add "Jane Doe" jane@example.com
    python manage_users.py list
    python manage_users.py reset-password jane@example.com
    python manage_users.py delete jane@example.com

`add` and `reset-password` generate a strong temporary password, print it once,
and flag the account so the user is forced to choose a new password at next
login. Hand the temporary password to the user over a private channel.
"""

import argparse
import sqlite3
import sys

from werkzeug.security import generate_password_hash

from database import db_conn, gen_temp_password, init_db


def _find_user(conn, email):
    return conn.execute(
        "SELECT id, name, email, must_change_password FROM users WHERE LOWER(email)=?",
        (email.strip().lower(),),
    ).fetchone()


# Every table that hangs off a user, in the order they have to go: each
# foreign key below is declared with no ON DELETE action, so a child row left
# behind makes the parent delete fail outright. Children first, user last.
_DELETE_STEPS = [
    ("IOU request", """
        DELETE FROM iou_requests WHERE requester_id=:uid OR payee_id=:uid"""),
    ("transaction", """
        DELETE FROM transactions WHERE bucket_id IN (
            SELECT b.id FROM buckets b
            JOIN accounts a ON a.id = b.acct_id WHERE a.user_id=:uid)"""),
    ("upcoming expense", """
        DELETE FROM upcoming_expenses WHERE bucket_id IN (
            SELECT b.id FROM buckets b
            JOIN accounts a ON a.id = b.acct_id WHERE a.user_id=:uid)"""),
    ("bucket", """
        DELETE FROM buckets WHERE acct_id IN (
            SELECT id FROM accounts WHERE user_id=:uid)"""),
    ("bucket group", """
        DELETE FROM bucket_groups WHERE acct_id IN (
            SELECT id FROM accounts WHERE user_id=:uid)"""),
    ("account", "DELETE FROM accounts WHERE user_id=:uid"),
    ("tag", "DELETE FROM tags WHERE user_id=:uid"),
]


def _delete_user_data(conn, user_id):
    """Delete everything owned by a user, leaving only the users row.

    Must be called inside a transaction — the caller opens one — so a failure
    part-way through takes the whole delete with it rather than leaving a
    half-dismantled account. Returns {label: rows deleted} for the summary.
    """
    counts = {}
    for label, sql in _DELETE_STEPS:
        counts[label] = conn.execute(sql, {'uid': user_id}).rowcount
    return counts


def cmd_add(args):
    name  = args.name.strip()
    email = args.email.strip().lower()
    if not name or not email:
        print("Error: name and email are required.", file=sys.stderr)
        return 1

    temp = gen_temp_password()
    with db_conn() as conn:
        if _find_user(conn, email):
            print(f"Error: a user with email '{email}' already exists. "
                  f"Use reset-password to reset it.", file=sys.stderr)
            return 1
        conn.execute(
            "INSERT INTO users (name, email, passwd, must_change_password) "
            "VALUES (?,?,?,1)",
            (name, email, generate_password_hash(temp)),
        )
        conn.commit()

    print(f"\n  Created user: {name} <{email}>")
    print(f"  Temporary password: {temp}")
    print("  The user must change this password at first login.\n")
    return 0


def cmd_list(args):
    with db_conn() as conn:
        rows = conn.execute(
            "SELECT id, name, email, must_change_password FROM users ORDER BY id"
        ).fetchall()

    if not rows:
        print("No users.")
        return 0

    print(f"\n  {'ID':<4} {'Name':<24} {'Email':<32} {'Pwd change pending'}")
    print("  " + "-" * 78)
    for r in rows:
        pending = 'yes' if r['must_change_password'] else '—'
        print(f"  {r['id']:<4} {r['name'][:23]:<24} {r['email'][:31]:<32} {pending}")
    print()
    return 0


def cmd_reset_password(args):
    email = args.email.strip().lower()
    temp = gen_temp_password()
    with db_conn() as conn:
        user = _find_user(conn, email)
        if not user:
            print(f"Error: no user with email '{email}'.", file=sys.stderr)
            return 1
        conn.execute(
            "UPDATE users SET passwd=?, must_change_password=1 WHERE id=?",
            (generate_password_hash(temp), user['id']),
        )
        conn.commit()

    print(f"\n  Reset password for: {user['name']} <{email}>")
    print(f"  Temporary password: {temp}")
    print("  The user must change this password at next login.\n")
    return 0


def cmd_delete(args):
    email = args.email.strip().lower()
    with db_conn() as conn:
        user = _find_user(conn, email)
        if not user:
            print(f"Error: no user with email '{email}'.", file=sys.stderr)
            return 1

        if not args.yes:
            confirm = input(
                f"Delete user {user['name']} <{email}> and ALL their data? "
                f"Type 'delete' to confirm: "
            )
            if confirm.strip().lower() != 'delete':
                print("Aborted.")
                return 1

        # Own the transaction mode rather than inheriting whatever get_db()
        # happens to use, so a failed delete is always one this script can undo.
        # On 3.12+ ask for PEP 249 semantics outright; on older Pythons, where
        # the attribute does not exist, make sure the legacy implicit BEGIN has
        # not been disabled with isolation_level=None. Set after the prompt, so
        # the operator's thinking time is not spent inside an open transaction
        # holding a WAL snapshot open.
        if hasattr(conn, 'autocommit'):
            conn.autocommit = False
        else:
            conn.isolation_level = ''

        try:
            # `with conn` is the transaction, not the connection: it commits on
            # success and rolls back on any exception, so a user is either gone
            # with everything they owned or still entirely there.
            with conn:
                counts = _delete_user_data(conn, user['id'])
                conn.execute("DELETE FROM users WHERE id=?", (user['id'],))
        except sqlite3.IntegrityError as exc:
            print(f"Error: could not delete {user['name']} <{email}>: {exc}.\n"
                  f"Nothing was deleted. This means the user still owns rows "
                  f"that this script does not know about — a table added since "
                  f"it was last updated.", file=sys.stderr)
            return 1

    print(f"Deleted user {user['name']} <{email}>.")
    # Labels are singular in _DELETE_STEPS; every one pluralises with a plain 's'.
    summary = ", ".join(f"{n} {label}{'' if n == 1 else 's'}"
                        for label, n in counts.items() if n)
    print(f"Also removed: {summary}." if summary
          else "They had no accounts, buckets or transactions.")
    return 0


def main():
    parser = argparse.ArgumentParser(description="Manage Buckets users.")
    sub = parser.add_subparsers(dest='command', required=True)

    p_add = sub.add_parser('add', help='Create a new user with a temporary password.')
    p_add.add_argument('name', help='Full name, e.g. "Jane Doe"')
    p_add.add_argument('email', help='Login email')
    p_add.set_defaults(func=cmd_add)

    p_list = sub.add_parser('list', help='List all users.')
    p_list.set_defaults(func=cmd_list)

    p_reset = sub.add_parser('reset-password', help='Reset a user\'s password.')
    p_reset.add_argument('email', help='Login email')
    p_reset.set_defaults(func=cmd_reset_password)

    p_del = sub.add_parser('delete',
                           help='Delete a user and everything they own.')
    p_del.add_argument('email', help='Login email')
    p_del.add_argument('-y', '--yes', action='store_true',
                       help='Skip the confirmation prompt.')
    p_del.set_defaults(func=cmd_delete)

    args = parser.parse_args()

    # Ensure the schema (incl. must_change_password) exists before we touch it.
    # seed=False: managing users must never create the default user as a side
    # effect, which it otherwise would whenever BUCKETS_DEFAULT_EMAIL is unset.
    init_db(seed=False)
    sys.exit(args.func(args))


if __name__ == '__main__':
    main()
