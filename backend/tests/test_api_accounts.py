# backend/tests/test_api_accounts.py
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool
from tms.main import app
from tms.db import get_db
from tms.models import Base, Account


def make_test_db():
    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    with Session(engine) as session:
        session.add_all([
            Account(name="Emirates NBD", bank="emirates_nbd", currency="AED",
                    type="checking", balance=52100.0, is_active=True),
            Account(name="Revolut EUR", bank="revolut", currency="EUR",
                    type="checking", balance=7500.0, is_active=True),
        ])
        session.commit()

    def override():
        with Session(engine) as session:
            yield session

    return override


def test_list_accounts():
    app.dependency_overrides[get_db] = make_test_db()
    client = TestClient(app)

    resp = client.get("/api/accounts")
    assert resp.status_code == 200
    data = resp.json()
    assert len(data) == 2
    assert data[0]["name"] == "Emirates NBD"
    assert data[0]["currency"] == "AED"
    app.dependency_overrides.clear()


def test_get_account_by_id():
    app.dependency_overrides[get_db] = make_test_db()
    client = TestClient(app)

    resp = client.get("/api/accounts/1")
    assert resp.status_code == 200
    assert resp.json()["name"] == "Emirates NBD"
    app.dependency_overrides.clear()


def test_get_account_not_found():
    app.dependency_overrides[get_db] = make_test_db()
    client = TestClient(app)

    resp = client.get("/api/accounts/999")
    assert resp.status_code == 404
    app.dependency_overrides.clear()
