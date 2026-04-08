"""TMS Banking CLI — start the server from anywhere."""
import argparse
import sys
import uvicorn
from tms.config import settings


def main():
    parser = argparse.ArgumentParser(
        prog="tms-bank",
        description="TMS Banking Backend Server",
    )
    parser.add_argument("--host", default=settings.host, help="Bind host (default: 0.0.0.0)")
    parser.add_argument("--port", "-p", type=int, default=settings.port, help="Bind port (default: 8000)")
    parser.add_argument("--reload", action="store_true", help="Auto-reload on code changes")

    args = parser.parse_args()

    print(f"🏦 TMS Banking Backend starting on http://{args.host}:{args.port}")
    print(f"   Sync interval: every {settings.sync_interval_minutes} min")
    print(f"   Database: {settings.db_path}")
    print()

    uvicorn.run(
        "tms.main:app",
        host=args.host,
        port=args.port,
        reload=args.reload,
    )
