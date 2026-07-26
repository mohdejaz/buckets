#!/usr/bin/env bash
# Run the Buckets web app — accessible on all network interfaces
cd "$(dirname "$0")"
HOST="${HOST:-0.0.0.0}"
PORT="${PORT:-8080}"
echo "Starting Buckets on http://${HOST}:${PORT}"
venv/bin/flask --app app run --host "$HOST" --port "$PORT"
