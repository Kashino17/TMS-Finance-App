# backend/tests/test_api_categories.py
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool
from tms.main import app
from tms.db import get_db
from tms.models import Base
from tms.seed import seed_categories


def make_test_db():
    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    with Session(engine) as session:
        seed_categories(session)

    def override():
        with Session(engine) as session:
            yield session

    return override


def test_list_categories():
    app.dependency_overrides[get_db] = make_test_db()
    client = TestClient(app)
    resp = client.get("/api/categories")
    assert resp.status_code == 200
    data = resp.json()
    assert len(data) == 12
    names = {c["name"] for c in data}
    assert "Einkommen" in names
    assert "Sonstiges" in names
    app.dependency_overrides.clear()
