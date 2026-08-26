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
import secrets
import string
import sys

from werkzeug.security import generate_password_hash

from database import db_conn, init_db


def _gen_temp_password(length=14):
    """A readable but strong temporary password (letters + digits)."""
    alphabet = string.ascii_letters + string.digits
    return ''.join(secrets.choice(alphabet) for _ in range(length))


def _find_user(conn, email):
    return conn.execute(
        "SELECT id, name, email, must_change_password FROM users WHERE LOWER(email)=?",
        (email.strip().lower(),),
    ).fetchone()


def cmd_add(args):
    name  = args.name.strip()
    email = args.email.strip().lower()
    if not name or not email:
        print("Error: name and email are required.", file=sys.stderr)
        return 1

    temp = _gen_temp_password()
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
    temp = _gen_temp_password()
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

        conn.execute("DELETE FROM users WHERE id=?", (user['id'],))
        conn.commit()

    print(f"Deleted user {user['name']} <{email}>.")
    print("Note: any accounts/buckets owned by this user are now orphaned; "
          "remove them separately if needed.")
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

    p_del = sub.add_parser('delete', help='Delete a user.')
    p_del.add_argument('email', help='Login email')
    p_del.add_argument('-y', '--yes', action='store_true',
                       help='Skip the confirmation prompt.')
    p_del.set_defaults(func=cmd_delete)

    args = parser.parse_args()

    # Ensure the schema (incl. must_change_password) exists before we touch it.
    init_db()
    sys.exit(args.func(args))


if __name__ == '__main__':
    main()
