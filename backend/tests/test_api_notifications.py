# backend/tests/test_api_notifications.py
from datetime import datetime
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.pool import StaticPool
from sqlalchemy.orm import Session
from tms.main import app
from tms.db import get_db
from tms.models import Base, Account
from tms.seed import seed_categories


def make_test_db():
    engine = create_engine("sqlite:///:memory:", poolclass=StaticPool, connect_args={"check_same_thread": False})
    Base.metadata.create_all(engine)
    with Session(engine) as session:
        seed_categories(session)
        session.add(Account(
            name="Mashreq", bank="mashreq", currency="AED",
            type="checking", balance=31450.0, is_active=True,
        ))
        session.commit()

    def override():
        with Session(engine) as session:
            yield session

    return override


def test_receive_notification():
    app.dependency_overrides[get_db] = make_test_db()
    client = TestClient(app)

    resp = client.post("/api/notifications", json={
        "bank_package": "com.mashreq.mobilebanking",
        "title": "Transaction Alert",
        "text": "AED 150.00 spent at Spinneys on your card ending 1234",
        "timestamp": "2026-04-08T14:30:00Z",
    })

    assert resp.status_code == 200
    assert resp.json()["status"] == "received"
    app.dependency_overrides.clear()
