# Buckets

A self-hosted envelope-budgeting web app. Money lives in **accounts**, accounts are divided into **buckets** (budget envelopes like Groceries, Rent, Savings), and every dollar gets refilled into a bucket before it's allowed to be spent — classic envelope budgeting, without spreadsheets.

## About

Buckets is a personal, self-hosted budgeting tool built around the envelope method: instead of tracking a single account balance, money is divided into purpose-specific buckets (Groceries, Rent, Savings, etc.), each refilled from a shared Settlement pool on your own schedule. It's designed for a household or a small number of trusted users who want simple, transparent budget tracking without third-party bank syncing, ads, or a subscription — just a Flask app and a SQLite file you control.

Access is invite-only: there's no signup page, and you create each account yourself from the command line. That keeps a hosted instance closed to everyone but the people you hand credentials to.

It was built iteratively with an AI coding assistant (Claude) rather than from an upfront spec, so it favors "solve the next real problem" over architectural completeness. It hasn't been security-audited or load-tested, and isn't intended for untrusted multi-tenant deployment — treat it as a personal-use tool.

## Features

- **Dashboard** — at-a-glance totals (balance, bucket count, transaction count, month-to-date spending) plus a quick bucket summary per account.
- **Accounts & buckets** — organize money into accounts, split each into budget envelopes with per-bucket budgets and refill amounts.
- **Transactions** — record income/spending against a bucket, post/unpost, soft-delete with restore, or purge permanently.
- **Refills** — refill a single bucket or all buckets at once from the account's Settlement pool; reset a bucket's balance.
- **Transfers** — move money between buckets or between accounts.
- **Upcoming expenses** — track bills/expenses you know are coming so they factor into your budget before they hit.
- **IOUs** — request/settle money owed between users, and link an IOU to a real transaction once it's paid.
- **CSV export** — pull transaction history out for spreadsheets or backups.
- **Multi-user, invite-only** — each user has their own accounts and buckets. Accounts are provisioned from the command line with `manage_users.py`; there is no public signup or self-service password reset.

## Tech stack

- **Backend:** Python, [Flask](https://flask.palletsprojects.com/)
- **Database:** SQLite (single file, no server to run)
- **Frontend:** Server-rendered templates + vanilla JS (no build step)
- **Serving:** gunicorn in production, Flask's dev server for local work

## Getting started

Requires Python 3.9+.

```bash
git clone <this-repo>
cd buckets
python -m venv venv

# macOS/Linux
source venv/bin/activate
# Windows
venv\Scripts\activate

pip install -r requirements.txt
```

Then start it:

```bash
python app.py
```

The app starts on [http://localhost:8080](http://localhost:8080). On first run it creates `buckets.db` (gitignored — it's your personal data) and seeds one initial user, plus a sample account with buckets and transactions so there's something to look at.

Since there's no signup page, that seeded user is how you get in. **Its password is generated and printed to the console once, at seed time** — copy it from the startup output:

```
======================================================================
  Seeded the initial Buckets user.
    Email:    demo@example.com
    Password: kekl2k6H6U2eyh
  Shown once, right now. It must be changed at first login.
======================================================================
```

There's no fixed default password on purpose. A hardcoded one would be published in this repo, which on a hosted instance would let a stranger claim the account by setting a password on it. The generated password is single-use: the account is flagged to force a password change, so you'll land on the change-password page before you can reach anything else.

To choose the credentials yourself instead — worth doing for a hosted deployment, so nothing is printed to logs — set these **before the first run**:

```bash
export BUCKETS_DEFAULT_NAME=yourname
export BUCKETS_DEFAULT_EMAIL=you@example.com
export BUCKETS_DEFAULT_PASSWORD=pick-something-long
```

These are only read at seed time. The seed looks for a user matching `BUCKETS_DEFAULT_EMAIL` and creates one if missing, so changing the email later adds a *second* user rather than renaming the first — set them before the first boot, or clean up afterwards with `manage_users.py delete`.

### Managing users

There's no signup page. Provision accounts from the machine running the app, inside the same virtualenv:

```bash
python manage_users.py add "Jane Doe" jane@example.com   # prints a temp password once
python manage_users.py list
python manage_users.py reset-password jane@example.com
python manage_users.py delete jane@example.com
```

`add` and `reset-password` generate a strong temporary password, print it once, and flag the account so the user must choose a new one at next login. Hand that password over a private channel — it isn't emailed. Deleting a user leaves their accounts and buckets orphaned; remove those separately if you care.

### Configuration

| Variable | Purpose |
| --- | --- |
| `BUCKETS_SECRET_KEY` | Signs session cookies. **Set this in production** — the fallback is a random key regenerated on every restart, which logs everyone out. Must stay stable. |
| `BUCKETS_SECURE_COOKIES` | Set to `1` when serving over HTTPS so session cookies are HTTPS-only. Leave unset for plain-HTTP local dev. |
| `BUCKETS_DEFAULT_NAME` / `_EMAIL` / `_PASSWORD` | Credentials for the user seeded on first run. Read only at seed time. If `_PASSWORD` is unset, one is generated and printed to the console once. |
| `BUCKETS_DB_PATH` | Where the SQLite file lives. Defaults to `buckets.db` in the project directory. Point this at a mounted volume on any host with ephemeral disk. |
| `HOST` / `PORT` | Bind address for `run.sh`. Defaults to `0.0.0.0:8080`. |

`run.sh` loads a gitignored `.env` file from the project directory if one exists, so you can keep these there for local dev instead of exporting them by hand.

### Quick-start scripts

- `run.sh` — serves the app with gunicorn on `0.0.0.0:8080` (override with `HOST`/`PORT`). Expects a `venv/` in the project directory and sources `.env` if present.
- `run.bat` — Windows helper that creates the venv, installs dependencies, and starts the app.

## Deploying to Fly.io

`Dockerfile` and `fly.toml` are set up for a single-instance deployment with a persistent volume. Edit `app` and `primary_region` in `fly.toml` first, then:

```bash
fly apps create <your-app-name>          # or: fly launch --no-deploy
fly volumes create buckets_data --size 1 --region <your-region>
```

Set the secrets **before the first deploy**, so the database is seeded with your account rather than a generated one:

```bash
fly secrets set \
  BUCKETS_SECRET_KEY="$(python3 -c 'import secrets;print(secrets.token_hex(32))')" \
  BUCKETS_DEFAULT_NAME="Your Name" \
  BUCKETS_DEFAULT_EMAIL="you@example.com" \
  BUCKETS_DEFAULT_PASSWORD="pick-something-long"

fly deploy
```

`BUCKETS_SECRET_KEY` matters as much as the credentials: without it the app generates a random key at every boot, so each deploy invalidates all sessions and logs everyone out.

Then add the people you're inviting:

```bash
fly ssh console -C "python /app/manage_users.py add 'Jane Doe' jane@example.com"
```

Give each person their temporary password over a private channel; they'll be forced to replace it at first login.

**Keep this at one machine.** A Fly volume attaches to a single machine, so scaling out gives each instance its own database and they diverge silently. To back up, copy the file off the volume:

```bash
fly ssh console -C "cat /data/buckets.db" > backup-$(date +%F).db
```

## Project structure

```
app.py            Flask routes (auth + JSON API)
database.py       SQLite schema, migrations, and seed data
manage_users.py   CLI to add / list / reset / delete users
templates/        Jinja2 page templates
static/           CSS and vanilla JS frontend
Dockerfile        Production image (gunicorn)
fly.toml          Fly.io app config — volume mount, HTTPS, single instance
```

## License

MIT — see [LICENSE](LICENSE). It covers the source code, not use of a running instance, so people you invite to a hosted deployment have nothing to accept.
