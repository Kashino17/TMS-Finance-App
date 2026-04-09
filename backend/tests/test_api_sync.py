# backend/tests/test_api_sync.py
from unittest.mock import patch
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool
from tms.main import app
from tms.db import get_db
from tms.models import Base


def make_test_db():
    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)

    def override():
        with Session(engine) as session:
            yield session

    return override


def test_sync_status_empty():
    """GET /api/sync/status returns an empty list when no active accounts exist."""
    app.dependency_overrides[get_db] = make_test_db()
    client = TestClient(app)

    resp = client.get("/api/sync/status")
    assert resp.status_code == 200
    assert resp.json() == []
    app.dependency_overrides.clear()


def test_trigger_sync():
    """POST /api/sync/trigger returns triggered status."""
    app.dependency_overrides[get_db] = make_test_db()
    client = TestClient(app)

    # The sync router imports scheduled_sync from tms.main at call time
    with patch("tms.main.scheduled_sync", return_value=None):
        resp = client.post("/api/sync/trigger")

    assert resp.status_code == 200
    assert resp.json()["status"] == "triggered"
    app.dependency_overrides.clear()


def test_enbd_sync_status():
    """GET /api/sync/enbd/status returns the current ENBD sync status."""
    app.dependency_overrides[get_db] = make_test_db()
    client = TestClient(app)

    resp = client.get("/api/sync/enbd/status")
    assert resp.status_code == 200
    data = resp.json()
    assert "status" in data
    assert "message" in data
    # Initial state should be idle
    assert data["status"] == "idle"
    app.dependency_overrides.clear()
