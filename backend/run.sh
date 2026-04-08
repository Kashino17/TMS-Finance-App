#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
exec .venv/bin/python -m uvicorn tms.main:app \
    --host "${TMS_HOST:-0.0.0.0}" \
    --port "${TMS_PORT:-8000}" \
    --reload
