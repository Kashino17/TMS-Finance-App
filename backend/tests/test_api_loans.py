# backend/tests/test_api_loans.py
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


def test_list_loans_empty():
    app.dependency_overrides[get_db] = make_test_db()
    client = TestClient(app)

    resp = client.get("/api/loans")
    assert resp.status_code == 200
    assert resp.json() == []
    app.dependency_overrides.clear()


def test_create_loan():
    app.dependency_overrides[get_db] = make_test_db()
    client = TestClient(app)

    payload = {
        "name": "Car Loan",
        "type": "personal_loan",
        "total_amount": 50000.0,
        "remaining_amount": 40000.0,
        "monthly_payment": 1500.0,
        "interest_rate": 4.5,
        "currency": "AED",
        "start_date": "2025-01-01",
        "end_date": "2028-01-01",
        "due_day": 15,
        "notes": "Toyota Corolla",
    }
    resp = client.post("/api/loans", json=payload)
    assert resp.status_code == 200
    data = resp.json()
    assert data["name"] == "Car Loan"
    assert data["total_amount"] == 50000.0
    assert data["remaining_amount"] == 40000.0
    assert data["monthly_payment"] == 1500.0
    assert data["currency"] == "AED"
    assert data["start_date"] == "2025-01-01"
    assert data["is_active"] is True
    assert "id" in data
    app.dependency_overrides.clear()


def test_delete_loan():
    app.dependency_overrides[get_db] = make_test_db()
    client = TestClient(app)

    # First create a loan
    payload = {
        "name": "Test Loan",
        "type": "installment",
        "total_amount": 10000.0,
        "remaining_amount": 8000.0,
        "monthly_payment": 500.0,
        "currency": "AED",
        "start_date": "2025-06-01",
    }
    create_resp = client.post("/api/loans", json=payload)
    assert create_resp.status_code == 200
    loan_id = create_resp.json()["id"]

    # Now delete it
    del_resp = client.delete(f"/api/loans/{loan_id}")
    assert del_resp.status_code == 200
    assert del_resp.json()["status"] == "deleted"

    # Confirm it's gone
    list_resp = client.get("/api/loans")
    assert list_resp.status_code == 200
    assert all(l["id"] != loan_id for l in list_resp.json())
    app.dependency_overrides.clear()


def test_delete_loan_not_found():
    app.dependency_overrides[get_db] = make_test_db()
    client = TestClient(app)

    resp = client.delete("/api/loans/999")
    assert resp.status_code == 404
    app.dependency_overrides.clear()
