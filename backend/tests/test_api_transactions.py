# backend/tests/test_api_transactions.py
from datetime import date
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool
from tms.main import app
from tms.db import get_db
from tms.models import Base, Account, Transaction, Category


def make_test_db():
    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    with Session(engine) as session:
        acct = Account(name="Emirates NBD", bank="emirates_nbd", currency="AED",
                       type="checking", balance=52100.0, is_active=True)
        cat = Category(name="Lebensmittel", icon="🛒", color="#ff6b6b")
        session.add_all([acct, cat])
        session.commit()

        session.add_all([
            Transaction(account_id=acct.id, amount=-234.50, currency="AED",
                        amount_aed=-234.50, date=date(2026, 4, 8),
                        merchant_name="Carrefour", source="lean", category_id=cat.id),
            Transaction(account_id=acct.id, amount=-45.00, currency="AED",
                        amount_aed=-45.00, date=date(2026, 4, 7),
                        merchant_name="Uber", source="lean"),
            Transaction(account_id=acct.id, amount=15000.00, currency="AED",
                        amount_aed=15000.00, date=date(2026, 4, 1),
                        merchant_name="Salary", source="lean"),
        ])
        session.commit()

    def override():
        with Session(engine) as session:
            yield session

    return override


def test_list_transactions():
    app.dependency_overrides[get_db] = make_test_db()
    client = TestClient(app)
    resp = client.get("/api/transactions")
    assert resp.status_code == 200
    data = resp.json()
    assert len(data) == 3
    assert data[0]["merchant_name"] == "Carrefour"
    app.dependency_overrides.clear()


def test_filter_by_account():
    app.dependency_overrides[get_db] = make_test_db()
    client = TestClient(app)
    resp = client.get("/api/transactions?account_id=1")
    assert resp.status_code == 200
    assert len(resp.json()) == 3
    app.dependency_overrides.clear()


def test_update_transaction_category():
    app.dependency_overrides[get_db] = make_test_db()
    client = TestClient(app)
    resp = client.patch("/api/transactions/2", json={"category_id": 1})
    assert resp.status_code == 200
    assert resp.json()["category_id"] == 1
    app.dependency_overrides.clear()
