# Buckets

A self-hosted envelope-budgeting web app. Money lives in **accounts**, accounts are divided into **buckets** (budget envelopes like Groceries, Rent, Savings), and every dollar gets refilled into a bucket before it's allowed to be spent — classic envelope budgeting, without spreadsheets.

## About

Buckets is a personal, self-hosted budgeting tool built around the envelope method: instead of tracking a single account balance, money is divided into purpose-specific buckets (Groceries, Rent, Savings, etc.), each refilled from a shared Settlement pool on your own schedule. It's designed for a household or a small number of trusted users who want simple, transparent budget tracking without third-party bank syncing, ads, or a subscription — just a Flask app and a SQLite file you control.

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
- **Multi-user** — signup/login/password reset, each user has their own accounts and buckets.

## Tech stack

- **Backend:** Python, [Flask](https://flask.palletsprojects.com/)
- **Database:** SQLite (single file, no server to run)
- **Frontend:** Server-rendered templates + vanilla JS (no build step)

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
python app.py
```

The app starts on [http://localhost:8080](http://localhost:8080). On first run it creates `buckets.db` (ignored by git — it's your personal data) and seeds one starter account with a few sample buckets and transactions so there's something to look at.

The seeded starter login is:

- **Email:** `demo@example.com`
- **Password:** `changeme123`

**After setup, sign up for your own account from the login page** rather than using the seeded one for real data — the demo credentials are public (they're in this README), so anyone with access to your instance could log into that account. Once you've signed up, you can ignore or delete the seeded demo account entirely.

If you'd rather not have the demo account seeded at all, override its credentials before first run with environment variables:

```bash
export BUCKETS_DEFAULT_NAME=yourname
export BUCKETS_DEFAULT_EMAIL=you@example.com
export BUCKETS_DEFAULT_PASSWORD=your-password
```

### Quick-start scripts

- `run.sh` — starts the app bound to `0.0.0.0:8080` (override with `HOST`/`PORT` env vars). Expects a `venv/` in the project directory.
- `run.bat` — Windows helper that creates the venv, installs dependencies, and starts the app.

## Project structure

```
app.py          Flask routes (auth + JSON API)
database.py     SQLite schema, migrations, and seed data
templates/      Jinja2 page templates
static/         CSS and vanilla JS frontend
```

## License

MIT — see [LICENSE](LICENSE).
