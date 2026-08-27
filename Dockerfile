FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

WORKDIR /app

# Dependencies first, so code edits don't invalidate the pip layer.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# The SQLite file lives on the mounted volume, not in the image — container
# storage is recreated on every deploy.
ENV BUCKETS_DB_PATH=/data/buckets.db

EXPOSE 8080

# One worker: SQLite serializes writes, and extra worker processes buy nothing
# but lock contention. Threads handle the concurrency a household app needs.
CMD ["gunicorn", "--bind", "0.0.0.0:8080", "--workers", "1", "--threads", "4", "--timeout", "60", "app:app"]
