#!/usr/bin/env bash
# Run the Buckets web app — accessible on all network interfaces
cd "$(dirname "$0")"
# Load local env overrides if present (.env is gitignored)
[ -f .env ] && set -a && . ./.env && set +a
HOST="${HOST:-0.0.0.0}"
PORT="${PORT:-8080}"
echo "Starting Buckets on http://${HOST}:${PORT}"
# venv/bin/flask --app app run --host "$HOST" --port "$PORT"
# Receipt scanning waits on a vision model, which routinely takes longer
# than gunicorn's 30s default — the worker gets killed mid-request and the
# browser sees a dead connection. Threads keep one slow upload from
# blocking the whole app.
venv/bin/gunicorn --bind "${HOST}:${PORT}" \
  --workers 1 --threads 4 --timeout "${BUCKETS_TIMEOUT:-300}" app:app
