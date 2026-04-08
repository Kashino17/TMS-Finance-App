# backend/tests/test_integration.py
"""
Integration test: seed DB → create account → sync transactions → check API.
"""
from datetime import date
from unittest.mock import MagicMock
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.pool import StaticPool
from sqlalchemy.orm import Session
from tms.main import app
from tms.db import get_db
from tms.models import Base, Account
from tms.seed import seed_categories
from tms.services.sync_engine import SyncEngine
from tms.connectors.base import RawTransaction, AccountBalance


def test_full_flow():
    engine = create_engine("sqlite:///:memory:", poolclass=StaticPool, connect_args={"check_same_thread": False})
    Base.metadata.create_all(engine)

    with Session(engine) as db:
        seed_categories(db)
        db.add(Account(
            name="Emirates NBD", bank="emirates_nbd", currency="AED",
            type="checking", balance=50000.0, is_active=True,
        ))
        db.commit()

    # Mock connector
    connector = MagicMock()
    connector.fetch_accounts.return_value = [
        AccountBalance("ext_1", "Emirates NBD", "AED", 52100.0),
    ]
    connector.fetch_transactions.return_value = [
        RawTransaction("t1", -234.50, "AED", date(2026, 4, 8),
                        "Carrefour", "POS", "{}"),
        RawTransaction("t2", -45.00, "AED", date(2026, 4, 7),
                        "Uber", "Ride", '{"id":"t2"}'),
        RawTransaction("t3", 15000.00, "AED", date(2026, 4, 1),
                        "Salary", "Monthly", '{"id":"t3"}'),
    ]

    # Sync
    sync = SyncEngine(engine)
    sync.sync_account(1, connector, "ext_1")

    # Test API
    def override():
        with Session(engine) as session:
            yield session

    app.dependency_overrides[get_db] = override
    client = TestClient(app)

    # Check accounts
    resp = client.get("/api/accounts")
    assert resp.status_code == 200
    accounts = resp.json()
    assert len(accounts) == 1
    assert accounts[0]["balance"] == 52100.0

    # Check transactions
    resp = client.get("/api/transactions")
    assert resp.status_code == 200
    txns = resp.json()
    assert len(txns) == 3

    # Check Carrefour was auto-categorized as Lebensmittel
    carrefour = next(t for t in txns if t["merchant_name"] == "Carrefour")
    assert carrefour["category_id"] is not None

    resp = client.get("/api/categories")
    categories = {c["id"]: c["name"] for c in resp.json()}
    assert categories[carrefour["category_id"]] == "Lebensmittel"

    # Check sync status
    resp = client.get("/api/sync/status")
    assert resp.status_code == 200
    status = resp.json()
    assert status[0]["status"] == "success"
    assert status[0]["transactions_fetched"] == 3

    app.dependency_overrides.clear()
