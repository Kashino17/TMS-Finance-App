# TMS Banking Backend — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a self-hosted Python backend that automatically syncs bank transactions from Lean Technologies (UAE), Revolut API, and FinTS (Sparkasse), categorizes them, handles multi-currency conversion to AED, and exposes a REST API for the Android app.

**Architecture:** FastAPI server with SQLite database. Bank connectors run on a scheduler (every 15 min while alive). On startup, a catch-up sync fetches all transactions missed while the server was off. The app communicates over Tailscale VPN.

**Tech Stack:** Python 3.12+, FastAPI, SQLAlchemy 2.0, SQLite (aiosqlite), APScheduler, python-fints, httpx, pytest

**Spec:** `docs/superpowers/specs/2026-04-08-tms-banking-design.md`

---

## File Structure

```
backend/
├── pyproject.toml
├── src/
│   └── tms/
│       ├── __init__.py
│       ├── main.py                 # FastAPI app, lifespan, scheduler
│       ├── config.py               # Settings (Pydantic BaseSettings)
│       ├── db.py                   # SQLAlchemy engine + session
│       ├── models.py               # All SQLAlchemy ORM models
│       ├── schemas.py              # Pydantic request/response schemas
│       ├── seed.py                 # Category seed data
│       ├── api/
│       │   ├── __init__.py
│       │   ├── accounts.py         # GET /accounts, GET /accounts/{id}
│       │   ├── transactions.py     # GET /transactions, PATCH category
│       │   ├── categories.py       # GET /categories
│       │   ├── sync.py             # POST /sync/trigger, GET /sync/status
│       │   └── notifications.py    # POST /notifications (from phone)
│       ├── services/
│       │   ├── __init__.py
│       │   ├── categorizer.py      # Merchant + keyword matching + learning
│       │   ├── currency.py         # Exchange rate fetch + conversion
│       │   └── sync_engine.py      # Orchestrates all connectors
│       └── connectors/
│           ├── __init__.py
│           ├── base.py             # BankConnector protocol
│           ├── lean.py             # Lean Technologies (UAE banks)
│           ├── revolut.py          # Revolut API
│           └── fints_connector.py  # FinTS/HBCI (Sparkasse)
└── tests/
    ├── conftest.py                 # Fixtures: test DB, test client
    ├── test_models.py
    ├── test_api_accounts.py
    ├── test_api_transactions.py
    ├── test_api_categories.py
    ├── test_categorizer.py
    ├── test_currency.py
    └── test_sync_engine.py
```

---

## Task 1: Project Scaffolding

**Files:**
- Create: `backend/pyproject.toml`
- Create: `backend/src/tms/__init__.py`
- Create: `backend/src/tms/main.py`
- Create: `backend/src/tms/config.py`

- [ ] **Step 1: Create pyproject.toml**

```toml
[project]
name = "tms-banking-backend"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = [
    "fastapi>=0.115.0",
    "uvicorn[standard]>=0.32.0",
    "sqlalchemy[asyncio]>=2.0.36",
    "aiosqlite>=0.20.0",
    "pydantic-settings>=2.6.0",
    "httpx>=0.28.0",
    "apscheduler>=3.10.4",
    "python-fints>=4.2.0",
]

[project.optional-dependencies]
dev = [
    "pytest>=8.3.0",
    "pytest-asyncio>=0.24.0",
    "httpx",
]

[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

[tool.pytest.ini_options]
asyncio_mode = "auto"
pythonpath = ["src"]
```

- [ ] **Step 2: Create config.py**

```python
# backend/src/tms/config.py
from pydantic_settings import BaseSettings
from pathlib import Path


class Settings(BaseSettings):
    db_path: Path = Path("tms_banking.db")
    host: str = "0.0.0.0"
    port: int = 8000
    sync_interval_minutes: int = 15

    # Lean Technologies
    lean_app_token: str = ""
    lean_customer_id: str = ""

    # Revolut
    revolut_client_id: str = ""
    revolut_client_secret: str = ""
    revolut_refresh_token: str = ""

    # FinTS (Sparkasse)
    fints_blz: str = ""
    fints_login: str = ""
    fints_pin: str = ""
    fints_endpoint: str = ""

    # Exchange rate API
    exchange_rate_api_url: str = "https://api.exchangerate.host/latest"

    model_config = {"env_prefix": "TMS_", "env_file": ".env"}


settings = Settings()
```

- [ ] **Step 3: Create main.py with health endpoint**

```python
# backend/src/tms/main.py
from fastapi import FastAPI

app = FastAPI(title="TMS Banking Backend")


@app.get("/health")
async def health():
    return {"status": "ok"}
```

- [ ] **Step 4: Create __init__.py**

```python
# backend/src/tms/__init__.py
```

- [ ] **Step 5: Install dependencies and verify**

Run: `cd backend && pip install -e ".[dev]"`
Expected: Successful installation

- [ ] **Step 6: Run the server to verify**

Run: `cd backend && python -m uvicorn tms.main:app --host 0.0.0.0 --port 8000 &; sleep 2; curl http://localhost:8000/health; kill %1`
Expected: `{"status":"ok"}`

- [ ] **Step 7: Commit**

```bash
git add backend/
git commit -m "feat(backend): scaffold project with FastAPI and health endpoint"
```

---

## Task 2: Database Models

**Files:**
- Create: `backend/src/tms/db.py`
- Create: `backend/src/tms/models.py`
- Create: `backend/tests/conftest.py`
- Create: `backend/tests/test_models.py`

- [ ] **Step 1: Write the test for model creation**

```python
# backend/tests/conftest.py
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from tms.models import Base


@pytest.fixture
def db():
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(engine)
    with Session(engine) as session:
        yield session
    engine.dispose()
```

```python
# backend/tests/test_models.py
from datetime import datetime, date, UTC
from tms.models import Account, Transaction, Category, ExchangeRate, SyncLog


def test_create_account(db):
    account = Account(
        name="Emirates NBD Checking",
        bank="emirates_nbd",
        currency="AED",
        type="checking",
        balance=52100.0,
        is_active=True,
    )
    db.add(account)
    db.commit()
    db.refresh(account)

    assert account.id is not None
    assert account.name == "Emirates NBD Checking"
    assert account.currency == "AED"


def test_create_transaction_with_account(db):
    account = Account(
        name="Mashreq", bank="mashreq", currency="AED",
        type="checking", balance=31450.0, is_active=True,
    )
    db.add(account)
    db.commit()

    txn = Transaction(
        account_id=account.id,
        amount=-234.50,
        currency="AED",
        amount_aed=-234.50,
        date=date(2026, 4, 8),
        merchant_name="Carrefour",
        description="Groceries",
        source="lean",
    )
    db.add(txn)
    db.commit()
    db.refresh(txn)

    assert txn.id is not None
    assert txn.account_id == account.id
    assert txn.amount == -234.50
    assert txn.source == "lean"


def test_category_hierarchy(db):
    parent = Category(name="Essen", icon="🍽", color="#ff6b6b")
    db.add(parent)
    db.commit()

    child = Category(name="Restaurant", icon="🍕", color="#ff6b6b", parent_id=parent.id)
    db.add(child)
    db.commit()

    assert child.parent_id == parent.id


def test_exchange_rate(db):
    rate = ExchangeRate(
        from_currency="EUR", to_currency="AED",
        rate=3.97, date=date(2026, 4, 8),
    )
    db.add(rate)
    db.commit()

    assert rate.rate == 3.97


def test_sync_log(db):
    account = Account(
        name="Revolut", bank="revolut", currency="EUR",
        type="checking", balance=7500.0, is_active=True,
    )
    db.add(account)
    db.commit()

    log = SyncLog(
        account_id=account.id,
        started_at=datetime.now(UTC),
        status="running",
    )
    db.add(log)
    db.commit()

    assert log.status == "running"
    assert log.transactions_fetched is None
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && pytest tests/test_models.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'tms.models'`

- [ ] **Step 3: Implement db.py**

```python
# backend/src/tms/db.py
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from tms.config import settings

engine = create_engine(f"sqlite:///{settings.db_path}", echo=False)
SessionLocal = sessionmaker(bind=engine)


def get_db():
    with Session(engine) as session:
        yield session
```

- [ ] **Step 4: Implement models.py**

```python
# backend/src/tms/models.py
from datetime import datetime, date, UTC
from sqlalchemy import (
    String, Float, Integer, Boolean, Date, DateTime, ForeignKey, Text,
)
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


class Account(Base):
    __tablename__ = "accounts"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(200))
    bank: Mapped[str] = mapped_column(String(50))
    currency: Mapped[str] = mapped_column(String(3))
    type: Mapped[str] = mapped_column(String(50))
    balance: Mapped[float] = mapped_column(Float, default=0.0)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    last_sync_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, default=lambda: datetime.now(UTC)
    )

    transactions: Mapped[list["Transaction"]] = relationship(back_populates="account")
    sync_logs: Mapped[list["SyncLog"]] = relationship(back_populates="account")


class Transaction(Base):
    __tablename__ = "transactions"

    id: Mapped[int] = mapped_column(primary_key=True)
    account_id: Mapped[int] = mapped_column(ForeignKey("accounts.id"))
    amount: Mapped[float] = mapped_column(Float)
    currency: Mapped[str] = mapped_column(String(3))
    amount_aed: Mapped[float] = mapped_column(Float)
    date: Mapped[date] = mapped_column(Date)
    merchant_name: Mapped[str | None] = mapped_column(String(300), nullable=True)
    description: Mapped[str | None] = mapped_column(String(500), nullable=True)
    category_id: Mapped[int | None] = mapped_column(
        ForeignKey("categories.id"), nullable=True
    )
    source: Mapped[str] = mapped_column(String(20))  # lean/revolut/fints/notification/manual
    raw_data: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, default=lambda: datetime.now(UTC)
    )

    account: Mapped["Account"] = relationship(back_populates="transactions")
    category: Mapped["Category | None"] = relationship()


class Category(Base):
    __tablename__ = "categories"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100))
    icon: Mapped[str] = mapped_column(String(10))
    color: Mapped[str] = mapped_column(String(7))
    parent_id: Mapped[int | None] = mapped_column(
        ForeignKey("categories.id"), nullable=True
    )


class ExchangeRate(Base):
    __tablename__ = "exchange_rates"

    id: Mapped[int] = mapped_column(primary_key=True)
    from_currency: Mapped[str] = mapped_column(String(3))
    to_currency: Mapped[str] = mapped_column(String(3))
    rate: Mapped[float] = mapped_column(Float)
    date: Mapped[date] = mapped_column(Date)


class SyncLog(Base):
    __tablename__ = "sync_log"

    id: Mapped[int] = mapped_column(primary_key=True)
    account_id: Mapped[int] = mapped_column(ForeignKey("accounts.id"))
    started_at: Mapped[datetime] = mapped_column(DateTime)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    status: Mapped[str] = mapped_column(String(20))  # running/success/error
    transactions_fetched: Mapped[int | None] = mapped_column(Integer, nullable=True)
    error: Mapped[str | None] = mapped_column(Text, nullable=True)

    account: Mapped["Account"] = relationship(back_populates="sync_logs")
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && pytest tests/test_models.py -v`
Expected: All 5 tests PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/tms/db.py backend/src/tms/models.py backend/tests/
git commit -m "feat(backend): add database models for accounts, transactions, categories, rates, sync"
```

---

## Task 3: Category Seed Data

**Files:**
- Create: `backend/src/tms/seed.py`
- Create: `backend/tests/test_seed.py`

- [ ] **Step 1: Write the test**

```python
# backend/tests/test_seed.py
from tms.models import Category
from tms.seed import seed_categories


def test_seed_creates_all_categories(db):
    seed_categories(db)
    categories = db.query(Category).all()
    top_level = [c for c in categories if c.parent_id is None]

    assert len(top_level) == 12
    names = {c.name for c in top_level}
    assert "Einkommen" in names
    assert "Lebensmittel" in names
    assert "Transport" in names
    assert "Sonstiges" in names


def test_seed_is_idempotent(db):
    seed_categories(db)
    seed_categories(db)
    categories = db.query(Category).all()
    top_level = [c for c in categories if c.parent_id is None]
    assert len(top_level) == 12
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_seed.py -v`
Expected: FAIL — `cannot import name 'seed_categories'`

- [ ] **Step 3: Implement seed.py**

```python
# backend/src/tms/seed.py
from sqlalchemy.orm import Session
from tms.models import Category

DEFAULT_CATEGORIES = [
    ("Einkommen", "💰", "#4caf50"),
    ("Lebensmittel", "🛒", "#ff6b6b"),
    ("Restaurants", "🍽", "#ff9800"),
    ("Transport", "🚗", "#2196f3"),
    ("Shopping", "🛍", "#e91e63"),
    ("Nebenkosten", "💡", "#ffc107"),
    ("Miete", "🏠", "#795548"),
    ("Gesundheit", "🏥", "#00bcd4"),
    ("Unterhaltung", "🎬", "#9c27b0"),
    ("Abos", "📱", "#607d8b"),
    ("Transfers", "🔄", "#78909c"),
    ("Sonstiges", "📦", "#9e9e9e"),
]


def seed_categories(db: Session) -> None:
    existing = {c.name for c in db.query(Category).filter(Category.parent_id.is_(None)).all()}
    for name, icon, color in DEFAULT_CATEGORIES:
        if name not in existing:
            db.add(Category(name=name, icon=icon, color=color))
    db.commit()
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && pytest tests/test_seed.py -v`
Expected: 2 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/tms/seed.py backend/tests/test_seed.py
git commit -m "feat(backend): add category seed data with 12 default categories"
```

---

## Task 4: Pydantic Schemas + API Accounts

**Files:**
- Create: `backend/src/tms/schemas.py`
- Create: `backend/src/tms/api/__init__.py`
- Create: `backend/src/tms/api/accounts.py`
- Create: `backend/tests/test_api_accounts.py`

- [ ] **Step 1: Write the test**

```python
# backend/tests/test_api_accounts.py
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from tms.main import app
from tms.db import get_db
from tms.models import Base, Account


def make_test_db():
    engine = create_engine("sqlite:///:memory:")
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_api_accounts.py -v`
Expected: FAIL — route not found (404 for /api/accounts)

- [ ] **Step 3: Implement schemas.py**

```python
# backend/src/tms/schemas.py
from datetime import datetime, date
from pydantic import BaseModel


class AccountOut(BaseModel):
    id: int
    name: str
    bank: str
    currency: str
    type: str
    balance: float
    is_active: bool
    last_sync_at: datetime | None

    model_config = {"from_attributes": True}


class TransactionOut(BaseModel):
    id: int
    account_id: int
    amount: float
    currency: str
    amount_aed: float
    date: date
    merchant_name: str | None
    description: str | None
    category_id: int | None
    source: str

    model_config = {"from_attributes": True}


class CategoryOut(BaseModel):
    id: int
    name: str
    icon: str
    color: str
    parent_id: int | None

    model_config = {"from_attributes": True}


class UpdateTransactionCategory(BaseModel):
    category_id: int


class SyncStatusOut(BaseModel):
    account_id: int
    account_name: str
    last_sync_at: datetime | None
    status: str
    transactions_fetched: int | None


class NotificationIn(BaseModel):
    bank_package: str
    title: str
    text: str
    timestamp: datetime
```

- [ ] **Step 4: Implement api/accounts.py**

```python
# backend/src/tms/api/__init__.py
```

```python
# backend/src/tms/api/accounts.py
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from tms.db import get_db
from tms.models import Account
from tms.schemas import AccountOut

router = APIRouter(prefix="/api/accounts", tags=["accounts"])


@router.get("", response_model=list[AccountOut])
def list_accounts(db: Session = Depends(get_db)):
    return db.query(Account).filter(Account.is_active.is_(True)).all()


@router.get("/{account_id}", response_model=AccountOut)
def get_account(account_id: int, db: Session = Depends(get_db)):
    account = db.get(Account, account_id)
    if not account:
        raise HTTPException(404, "Account not found")
    return account
```

- [ ] **Step 5: Register router in main.py**

```python
# backend/src/tms/main.py
from fastapi import FastAPI
from tms.api.accounts import router as accounts_router

app = FastAPI(title="TMS Banking Backend")
app.include_router(accounts_router)


@app.get("/health")
async def health():
    return {"status": "ok"}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && pytest tests/test_api_accounts.py -v`
Expected: All 3 tests PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/tms/schemas.py backend/src/tms/api/ backend/tests/test_api_accounts.py
git commit -m "feat(backend): add accounts API with list and detail endpoints"
```

---

## Task 5: Transactions API

**Files:**
- Create: `backend/src/tms/api/transactions.py`
- Create: `backend/tests/test_api_transactions.py`

- [ ] **Step 1: Write the test**

```python
# backend/tests/test_api_transactions.py
from datetime import date
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from tms.main import app
from tms.db import get_db
from tms.models import Base, Account, Transaction, Category


def make_test_db():
    engine = create_engine("sqlite:///:memory:")
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
    # Newest first
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_api_transactions.py -v`
Expected: FAIL — route not found

- [ ] **Step 3: Implement api/transactions.py**

```python
# backend/src/tms/api/transactions.py
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from tms.db import get_db
from tms.models import Transaction
from tms.schemas import TransactionOut, UpdateTransactionCategory

router = APIRouter(prefix="/api/transactions", tags=["transactions"])


@router.get("", response_model=list[TransactionOut])
def list_transactions(
    account_id: int | None = Query(None),
    limit: int = Query(50, le=500),
    offset: int = Query(0),
    db: Session = Depends(get_db),
):
    q = db.query(Transaction).order_by(Transaction.date.desc(), Transaction.id.desc())
    if account_id:
        q = q.filter(Transaction.account_id == account_id)
    return q.offset(offset).limit(limit).all()


@router.patch("/{txn_id}", response_model=TransactionOut)
def update_transaction_category(
    txn_id: int,
    body: UpdateTransactionCategory,
    db: Session = Depends(get_db),
):
    txn = db.get(Transaction, txn_id)
    if not txn:
        raise HTTPException(404, "Transaction not found")
    txn.category_id = body.category_id
    db.commit()
    db.refresh(txn)
    return txn
```

- [ ] **Step 4: Register router in main.py**

```python
# backend/src/tms/main.py
from fastapi import FastAPI
from tms.api.accounts import router as accounts_router
from tms.api.transactions import router as transactions_router

app = FastAPI(title="TMS Banking Backend")
app.include_router(accounts_router)
app.include_router(transactions_router)


@app.get("/health")
async def health():
    return {"status": "ok"}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && pytest tests/test_api_transactions.py -v`
Expected: All 3 tests PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/tms/api/transactions.py backend/tests/test_api_transactions.py backend/src/tms/main.py
git commit -m "feat(backend): add transactions API with list, filter, and category update"
```

---

## Task 6: Categories API

**Files:**
- Create: `backend/src/tms/api/categories.py`
- Create: `backend/tests/test_api_categories.py`

- [ ] **Step 1: Write the test**

```python
# backend/tests/test_api_categories.py
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from tms.main import app
from tms.db import get_db
from tms.models import Base
from tms.seed import seed_categories


def make_test_db():
    engine = create_engine("sqlite:///:memory:")
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_api_categories.py -v`
Expected: FAIL — route not found

- [ ] **Step 3: Implement api/categories.py**

```python
# backend/src/tms/api/categories.py
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from tms.db import get_db
from tms.models import Category
from tms.schemas import CategoryOut

router = APIRouter(prefix="/api/categories", tags=["categories"])


@router.get("", response_model=list[CategoryOut])
def list_categories(db: Session = Depends(get_db)):
    return db.query(Category).order_by(Category.name).all()
```

- [ ] **Step 4: Register router in main.py**

```python
# backend/src/tms/main.py
from fastapi import FastAPI
from tms.api.accounts import router as accounts_router
from tms.api.transactions import router as transactions_router
from tms.api.categories import router as categories_router

app = FastAPI(title="TMS Banking Backend")
app.include_router(accounts_router)
app.include_router(transactions_router)
app.include_router(categories_router)


@app.get("/health")
async def health():
    return {"status": "ok"}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && pytest tests/test_api_categories.py -v`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/tms/api/categories.py backend/tests/test_api_categories.py backend/src/tms/main.py
git commit -m "feat(backend): add categories API endpoint"
```

---

## Task 7: Currency Service

**Files:**
- Create: `backend/src/tms/services/__init__.py`
- Create: `backend/src/tms/services/currency.py`
- Create: `backend/tests/test_currency.py`

- [ ] **Step 1: Write the test**

```python
# backend/tests/test_currency.py
from datetime import date
from unittest.mock import AsyncMock, patch
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from tms.models import Base, ExchangeRate
from tms.services.currency import CurrencyService


def make_db():
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(engine)
    return engine


def test_convert_same_currency():
    engine = make_db()
    svc = CurrencyService(engine)
    result = svc.convert(100.0, "AED", "AED", date(2026, 4, 8))
    assert result == 100.0


def test_convert_with_stored_rate():
    engine = make_db()
    with Session(engine) as db:
        db.add(ExchangeRate(
            from_currency="EUR", to_currency="AED",
            rate=3.97, date=date(2026, 4, 8),
        ))
        db.commit()

    svc = CurrencyService(engine)
    result = svc.convert(100.0, "EUR", "AED", date(2026, 4, 8))
    assert result == 397.0


def test_convert_uses_nearest_date():
    engine = make_db()
    with Session(engine) as db:
        db.add(ExchangeRate(
            from_currency="EUR", to_currency="AED",
            rate=3.97, date=date(2026, 4, 6),
        ))
        db.commit()

    svc = CurrencyService(engine)
    # No rate for April 8, should use April 6
    result = svc.convert(100.0, "EUR", "AED", date(2026, 4, 8))
    assert result == 397.0


def test_convert_reverse_rate():
    engine = make_db()
    with Session(engine) as db:
        db.add(ExchangeRate(
            from_currency="EUR", to_currency="AED",
            rate=3.97, date=date(2026, 4, 8),
        ))
        db.commit()

    svc = CurrencyService(engine)
    # AED → EUR should use 1/3.97
    result = svc.convert(397.0, "AED", "EUR", date(2026, 4, 8))
    assert abs(result - 100.0) < 0.1
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_currency.py -v`
Expected: FAIL — `ModuleNotFoundError`

- [ ] **Step 3: Implement currency.py**

```python
# backend/src/tms/services/__init__.py
```

```python
# backend/src/tms/services/currency.py
from datetime import date
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from tms.models import ExchangeRate


class CurrencyService:
    def __init__(self, engine):
        self.engine = engine

    def convert(self, amount: float, from_curr: str, to_curr: str, on_date: date) -> float:
        if from_curr == to_curr:
            return amount

        rate = self._get_rate(from_curr, to_curr, on_date)
        if rate:
            return round(amount * rate, 2)

        # Try reverse
        reverse = self._get_rate(to_curr, from_curr, on_date)
        if reverse:
            return round(amount / reverse, 2)

        raise ValueError(f"No exchange rate found for {from_curr} → {to_curr}")

    def _get_rate(self, from_curr: str, to_curr: str, on_date: date) -> float | None:
        with Session(self.engine) as db:
            rate = (
                db.query(ExchangeRate)
                .filter(
                    ExchangeRate.from_currency == from_curr,
                    ExchangeRate.to_currency == to_curr,
                    ExchangeRate.date <= on_date,
                )
                .order_by(ExchangeRate.date.desc())
                .first()
            )
            return rate.rate if rate else None

    def store_rates(self, rates: dict[str, float], base: str, on_date: date) -> None:
        """Store exchange rates from API response. rates = {'EUR': 0.92, 'AED': 3.67, ...}"""
        with Session(self.engine) as db:
            for currency, rate in rates.items():
                if currency == base:
                    continue
                existing = (
                    db.query(ExchangeRate)
                    .filter_by(from_currency=base, to_currency=currency, date=on_date)
                    .first()
                )
                if existing:
                    existing.rate = rate
                else:
                    db.add(ExchangeRate(
                        from_currency=base, to_currency=currency,
                        rate=rate, date=on_date,
                    ))
            db.commit()
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && pytest tests/test_currency.py -v`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/tms/services/ backend/tests/test_currency.py
git commit -m "feat(backend): add currency conversion service with rate lookup and storage"
```

---

## Task 8: Auto-Categorization Engine

**Files:**
- Create: `backend/src/tms/services/categorizer.py`
- Create: `backend/tests/test_categorizer.py`

- [ ] **Step 1: Write the test**

```python
# backend/tests/test_categorizer.py
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from tms.models import Base, Category
from tms.seed import seed_categories
from tms.services.categorizer import Categorizer


def make_db():
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(engine)
    with Session(engine) as db:
        seed_categories(db)
    return engine


def test_match_known_merchant():
    engine = make_db()
    cat = Categorizer(engine)
    result = cat.categorize("Carrefour", "POS purchase")
    assert result is not None
    with Session(engine) as db:
        category = db.get(Category, result)
        assert category.name == "Lebensmittel"


def test_match_keyword_restaurant():
    engine = make_db()
    cat = Categorizer(engine)
    result = cat.categorize("Some Unknown Place", "Restaurant payment")
    assert result is not None
    with Session(engine) as db:
        category = db.get(Category, result)
        assert category.name == "Restaurants"


def test_unknown_merchant_returns_none():
    engine = make_db()
    cat = Categorizer(engine)
    result = cat.categorize("XYZABC Corp", "Wire transfer ref 123")
    assert result is None


def test_learn_from_correction():
    engine = make_db()
    cat = Categorizer(engine)

    # First time: unknown
    assert cat.categorize("My Gym Dubai", "Monthly fee") is None

    # User corrects to "Gesundheit"
    with Session(engine) as db:
        health_cat = db.query(Category).filter_by(name="Gesundheit").first()
        cat.learn("My Gym Dubai", health_cat.id)

    # Now it should match
    result = cat.categorize("My Gym Dubai", "Monthly fee")
    with Session(engine) as db:
        category = db.get(Category, result)
        assert category.name == "Gesundheit"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_categorizer.py -v`
Expected: FAIL — `ModuleNotFoundError`

- [ ] **Step 3: Implement categorizer.py**

```python
# backend/src/tms/services/categorizer.py
import json
from pathlib import Path
from sqlalchemy.orm import Session
from tms.models import Category

# Known merchant → category name mappings
MERCHANT_MAP = {
    "carrefour": "Lebensmittel",
    "spinneys": "Lebensmittel",
    "lulu": "Lebensmittel",
    "choithrams": "Lebensmittel",
    "uber": "Transport",
    "careem": "Transport",
    "rta": "Transport",
    "salik": "Transport",
    "dewa": "Nebenkosten",
    "etisalat": "Nebenkosten",
    "du telecom": "Nebenkosten",
    "amazon": "Shopping",
    "noon": "Shopping",
    "namshi": "Shopping",
    "netflix": "Abos",
    "spotify": "Abos",
    "apple.com": "Abos",
    "google play": "Abos",
}

# Keyword → category name mappings (checked against merchant + description)
KEYWORD_MAP = {
    "restaurant": "Restaurants",
    "café": "Restaurants",
    "cafe": "Restaurants",
    "coffee": "Restaurants",
    "pharmacy": "Gesundheit",
    "clinic": "Gesundheit",
    "hospital": "Gesundheit",
    "medical": "Gesundheit",
    "cinema": "Unterhaltung",
    "gym": "Gesundheit",
    "rent": "Miete",
    "salary": "Einkommen",
    "gehalt": "Einkommen",
}


class Categorizer:
    def __init__(self, engine):
        self.engine = engine
        self._learned: dict[str, int] = {}
        self._category_cache: dict[str, int] = {}
        self._load_category_ids()

    def _load_category_ids(self):
        with Session(self.engine) as db:
            for cat in db.query(Category).filter(Category.parent_id.is_(None)).all():
                self._category_cache[cat.name] = cat.id

    def categorize(self, merchant_name: str | None, description: str | None) -> int | None:
        if not merchant_name and not description:
            return None

        merchant_lower = (merchant_name or "").lower().strip()
        desc_lower = (description or "").lower().strip()
        combined = f"{merchant_lower} {desc_lower}"

        # 1. Check learned mappings
        if merchant_lower in self._learned:
            return self._learned[merchant_lower]

        # 2. Check known merchants
        for pattern, cat_name in MERCHANT_MAP.items():
            if pattern in merchant_lower:
                return self._category_cache.get(cat_name)

        # 3. Check keywords
        for keyword, cat_name in KEYWORD_MAP.items():
            if keyword in combined:
                return self._category_cache.get(cat_name)

        return None

    def learn(self, merchant_name: str, category_id: int) -> None:
        self._learned[merchant_name.lower().strip()] = category_id
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && pytest tests/test_categorizer.py -v`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/tms/services/categorizer.py backend/tests/test_categorizer.py
git commit -m "feat(backend): add auto-categorization with merchant matching, keywords, and learning"
```

---

## Task 9: Bank Connector Interface

**Files:**
- Create: `backend/src/tms/connectors/__init__.py`
- Create: `backend/src/tms/connectors/base.py`

- [ ] **Step 1: Create the connector protocol**

```python
# backend/src/tms/connectors/__init__.py
```

```python
# backend/src/tms/connectors/base.py
from dataclasses import dataclass
from datetime import date, datetime
from typing import Protocol


@dataclass
class RawTransaction:
    """Normalized transaction from any bank connector."""
    external_id: str
    amount: float
    currency: str
    date: date
    merchant_name: str | None
    description: str | None
    raw_data: str  # JSON string of original API response


@dataclass
class AccountBalance:
    """Current balance from a bank connector."""
    external_id: str
    name: str
    currency: str
    balance: float


class BankConnector(Protocol):
    """Protocol that all bank connectors must implement."""

    def fetch_accounts(self) -> list[AccountBalance]: ...

    def fetch_transactions(
        self, account_external_id: str, since: date
    ) -> list[RawTransaction]: ...
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/tms/connectors/
git commit -m "feat(backend): add BankConnector protocol and data classes"
```

---

## Task 10: Lean Technologies Connector

**Files:**
- Create: `backend/src/tms/connectors/lean.py`
- Create: `backend/tests/test_lean_connector.py`

- [ ] **Step 1: Write the test with mocked HTTP**

```python
# backend/tests/test_lean_connector.py
from datetime import date
from unittest.mock import patch, MagicMock
from tms.connectors.lean import LeanConnector


MOCK_ACCOUNTS_RESPONSE = {
    "results": [
        {
            "account_id": "acc_123",
            "name": "Current Account",
            "currency": "AED",
            "balance": {"amount": 52100.0, "currency": "AED"},
        }
    ]
}

MOCK_TRANSACTIONS_RESPONSE = {
    "results": [
        {
            "transaction_id": "txn_001",
            "amount": {"amount": -234.50, "currency": "AED"},
            "date": "2026-04-08",
            "description": "POS Purchase - Carrefour",
            "merchant": {"name": "Carrefour"},
        },
        {
            "transaction_id": "txn_002",
            "amount": {"amount": 15000.00, "currency": "AED"},
            "date": "2026-04-01",
            "description": "Salary Credit",
            "merchant": None,
        },
    ]
}


@patch("tms.connectors.lean.httpx")
def test_fetch_accounts(mock_httpx):
    resp = MagicMock()
    resp.json.return_value = MOCK_ACCOUNTS_RESPONSE
    resp.raise_for_status = MagicMock()
    mock_httpx.get.return_value = resp

    connector = LeanConnector(app_token="test_token", customer_id="cust_123")
    accounts = connector.fetch_accounts()

    assert len(accounts) == 1
    assert accounts[0].external_id == "acc_123"
    assert accounts[0].balance == 52100.0
    assert accounts[0].currency == "AED"


@patch("tms.connectors.lean.httpx")
def test_fetch_transactions(mock_httpx):
    resp = MagicMock()
    resp.json.return_value = MOCK_TRANSACTIONS_RESPONSE
    resp.raise_for_status = MagicMock()
    mock_httpx.get.return_value = resp

    connector = LeanConnector(app_token="test_token", customer_id="cust_123")
    txns = connector.fetch_transactions("acc_123", since=date(2026, 4, 1))

    assert len(txns) == 2
    assert txns[0].external_id == "txn_001"
    assert txns[0].amount == -234.50
    assert txns[0].merchant_name == "Carrefour"
    assert txns[1].amount == 15000.00
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_lean_connector.py -v`
Expected: FAIL — `ModuleNotFoundError`

- [ ] **Step 3: Implement lean.py**

```python
# backend/src/tms/connectors/lean.py
import json
from datetime import date
import httpx
from tms.connectors.base import BankConnector, RawTransaction, AccountBalance

LEAN_BASE_URL = "https://api.leantech.me/v2"


class LeanConnector:
    def __init__(self, app_token: str, customer_id: str):
        self.app_token = app_token
        self.customer_id = customer_id
        self.headers = {
            "Authorization": f"Bearer {app_token}",
            "Content-Type": "application/json",
        }

    def fetch_accounts(self) -> list[AccountBalance]:
        resp = httpx.get(
            f"{LEAN_BASE_URL}/customers/{self.customer_id}/accounts",
            headers=self.headers,
        )
        resp.raise_for_status()
        data = resp.json()

        return [
            AccountBalance(
                external_id=acc["account_id"],
                name=acc["name"],
                currency=acc["currency"],
                balance=acc["balance"]["amount"],
            )
            for acc in data["results"]
        ]

    def fetch_transactions(
        self, account_external_id: str, since: date
    ) -> list[RawTransaction]:
        resp = httpx.get(
            f"{LEAN_BASE_URL}/customers/{self.customer_id}/accounts/{account_external_id}/transactions",
            headers=self.headers,
            params={"from_date": since.isoformat()},
        )
        resp.raise_for_status()
        data = resp.json()

        return [
            RawTransaction(
                external_id=txn["transaction_id"],
                amount=txn["amount"]["amount"],
                currency=txn["amount"]["currency"],
                date=date.fromisoformat(txn["date"]),
                merchant_name=txn["merchant"]["name"] if txn.get("merchant") else None,
                description=txn.get("description"),
                raw_data=json.dumps(txn),
            )
            for txn in data["results"]
        ]
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && pytest tests/test_lean_connector.py -v`
Expected: All 2 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/tms/connectors/lean.py backend/tests/test_lean_connector.py
git commit -m "feat(backend): add Lean Technologies connector for UAE banks"
```

---

## Task 11: Revolut Connector

**Files:**
- Create: `backend/src/tms/connectors/revolut.py`
- Create: `backend/tests/test_revolut_connector.py`

- [ ] **Step 1: Write the test**

```python
# backend/tests/test_revolut_connector.py
from datetime import date
from unittest.mock import patch, MagicMock
from tms.connectors.revolut import RevolutConnector


MOCK_ACCOUNTS = [
    {"id": "rev_acc_1", "name": "EUR Wallet", "currency": "EUR", "balance": 750000},
    {"id": "rev_acc_2", "name": "AED Wallet", "currency": "AED", "balance": 289200},
]

MOCK_TRANSACTIONS = [
    {
        "id": "rev_txn_1",
        "amount": -4500,
        "currency": "AED",
        "created_at": "2026-04-07T14:30:00Z",
        "description": "Uber",
        "merchant": {"name": "Uber"},
    },
    {
        "id": "rev_txn_2",
        "amount": -6700,
        "currency": "EUR",
        "created_at": "2026-04-05T10:00:00Z",
        "description": "Amazon.de",
        "merchant": {"name": "Amazon.de"},
    },
]


@patch("tms.connectors.revolut.httpx")
def test_fetch_accounts(mock_httpx):
    resp = MagicMock()
    resp.json.return_value = MOCK_ACCOUNTS
    resp.raise_for_status = MagicMock()
    mock_httpx.get.return_value = resp

    connector = RevolutConnector(access_token="test")
    accounts = connector.fetch_accounts()

    assert len(accounts) == 2
    assert accounts[0].currency == "EUR"
    # Revolut amounts are in minor units (cents)
    assert accounts[0].balance == 7500.00


@patch("tms.connectors.revolut.httpx")
def test_fetch_transactions(mock_httpx):
    resp = MagicMock()
    resp.json.return_value = MOCK_TRANSACTIONS
    resp.raise_for_status = MagicMock()
    mock_httpx.get.return_value = resp

    connector = RevolutConnector(access_token="test")
    txns = connector.fetch_transactions("rev_acc_1", since=date(2026, 4, 1))

    assert len(txns) == 2
    assert txns[0].amount == -45.00  # Minor units converted
    assert txns[0].merchant_name == "Uber"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_revolut_connector.py -v`
Expected: FAIL

- [ ] **Step 3: Implement revolut.py**

```python
# backend/src/tms/connectors/revolut.py
import json
from datetime import date, datetime
import httpx
from tms.connectors.base import BankConnector, RawTransaction, AccountBalance

REVOLUT_BASE_URL = "https://b2b.revolut.com/api/1.0"


class RevolutConnector:
    def __init__(self, access_token: str):
        self.headers = {
            "Authorization": f"Bearer {access_token}",
        }

    def fetch_accounts(self) -> list[AccountBalance]:
        resp = httpx.get(f"{REVOLUT_BASE_URL}/accounts", headers=self.headers)
        resp.raise_for_status()

        return [
            AccountBalance(
                external_id=acc["id"],
                name=acc["name"],
                currency=acc["currency"],
                balance=acc["balance"] / 100,  # Minor units → major
            )
            for acc in resp.json()
        ]

    def fetch_transactions(
        self, account_external_id: str, since: date
    ) -> list[RawTransaction]:
        resp = httpx.get(
            f"{REVOLUT_BASE_URL}/transactions",
            headers=self.headers,
            params={
                "account_id": account_external_id,
                "from": f"{since.isoformat()}T00:00:00Z",
            },
        )
        resp.raise_for_status()

        return [
            RawTransaction(
                external_id=txn["id"],
                amount=txn["amount"] / 100,  # Minor units → major
                currency=txn["currency"],
                date=datetime.fromisoformat(txn["created_at"].replace("Z", "+00:00")).date(),
                merchant_name=txn["merchant"]["name"] if txn.get("merchant") else None,
                description=txn.get("description"),
                raw_data=json.dumps(txn),
            )
            for txn in resp.json()
        ]
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && pytest tests/test_revolut_connector.py -v`
Expected: All 2 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/tms/connectors/revolut.py backend/tests/test_revolut_connector.py
git commit -m "feat(backend): add Revolut connector with minor-unit conversion"
```

---

## Task 12: FinTS Connector (Sparkasse)

**Files:**
- Create: `backend/src/tms/connectors/fints_connector.py`
- Create: `backend/tests/test_fints_connector.py`

- [ ] **Step 1: Write the test**

```python
# backend/tests/test_fints_connector.py
from datetime import date
from decimal import Decimal
from unittest.mock import patch, MagicMock
from tms.connectors.fints_connector import FinTSConnector


def make_mock_transaction(amount_value, date_val, applicant_name, purpose):
    txn = MagicMock()
    txn_amount = MagicMock()
    txn_amount.amount = Decimal(str(amount_value))
    txn_amount.currency = "EUR"
    txn.data = {
        "amount": txn_amount,
        "date": date_val,
        "applicant_name": applicant_name,
        "purpose": purpose,
        "id": {"reference": f"ref_{abs(int(amount_value * 100))}"},
    }
    return txn


@patch("tms.connectors.fints_connector.FinTS3PinTanClient")
def test_fetch_transactions(mock_client_cls):
    mock_client = MagicMock()
    mock_client_cls.return_value = mock_client
    mock_client.__enter__ = MagicMock(return_value=mock_client)
    mock_client.__exit__ = MagicMock(return_value=False)

    mock_sepa = MagicMock()
    mock_sepa.iban = "DE123456789"
    mock_client.get_sepa_accounts.return_value = [mock_sepa]

    mock_txns = [
        make_mock_transaction(-67.00, date(2026, 4, 5), "Amazon.de", "Order 123"),
        make_mock_transaction(2500.00, date(2026, 4, 1), "Arbeitgeber", "Gehalt April"),
    ]
    mock_client.get_transactions.return_value = mock_txns

    connector = FinTSConnector(
        blz="12345678", login="user", pin="1234",
        endpoint="https://fints.sparkasse.de",
    )
    txns = connector.fetch_transactions("DE123456789", since=date(2026, 4, 1))

    assert len(txns) == 2
    assert txns[0].amount == -67.00
    assert txns[0].merchant_name == "Amazon.de"
    assert txns[1].amount == 2500.00


@patch("tms.connectors.fints_connector.FinTS3PinTanClient")
def test_fetch_accounts(mock_client_cls):
    mock_client = MagicMock()
    mock_client_cls.return_value = mock_client
    mock_client.__enter__ = MagicMock(return_value=mock_client)
    mock_client.__exit__ = MagicMock(return_value=False)

    mock_sepa = MagicMock()
    mock_sepa.iban = "DE123456789"
    mock_client.get_sepa_accounts.return_value = [mock_sepa]

    mock_balance = MagicMock()
    mock_balance.amount.amount = Decimal("12340.50")
    mock_balance.amount.currency = "EUR"
    mock_client.get_balance.return_value = mock_balance

    connector = FinTSConnector(
        blz="12345678", login="user", pin="1234",
        endpoint="https://fints.sparkasse.de",
    )
    accounts = connector.fetch_accounts()

    assert len(accounts) == 1
    assert accounts[0].external_id == "DE123456789"
    assert accounts[0].balance == 12340.50
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_fints_connector.py -v`
Expected: FAIL

- [ ] **Step 3: Implement fints_connector.py**

```python
# backend/src/tms/connectors/fints_connector.py
import json
from datetime import date
from fints.client import FinTS3PinTanClient
from tms.connectors.base import BankConnector, RawTransaction, AccountBalance


class FinTSConnector:
    def __init__(self, blz: str, login: str, pin: str, endpoint: str):
        self.blz = blz
        self.login = login
        self.pin = pin
        self.endpoint = endpoint

    def _make_client(self) -> FinTS3PinTanClient:
        return FinTS3PinTanClient(
            self.blz, self.login, self.pin, self.endpoint
        )

    def fetch_accounts(self) -> list[AccountBalance]:
        with self._make_client() as client:
            sepa_accounts = client.get_sepa_accounts()
            results = []
            for sepa in sepa_accounts:
                balance = client.get_balance(sepa)
                results.append(AccountBalance(
                    external_id=sepa.iban,
                    name=f"Sparkasse {sepa.iban[-4:]}",
                    currency=str(balance.amount.currency),
                    balance=float(balance.amount.amount),
                ))
            return results

    def fetch_transactions(
        self, account_external_id: str, since: date
    ) -> list[RawTransaction]:
        with self._make_client() as client:
            sepa_accounts = client.get_sepa_accounts()
            sepa = next(a for a in sepa_accounts if a.iban == account_external_id)
            transactions = client.get_transactions(sepa, since)

            return [
                RawTransaction(
                    external_id=str(txn.data.get("id", {}).get("reference", "")),
                    amount=float(txn.data["amount"].amount),
                    currency=str(txn.data["amount"].currency),
                    date=txn.data["date"],
                    merchant_name=txn.data.get("applicant_name"),
                    description=txn.data.get("purpose"),
                    raw_data=json.dumps({
                        k: str(v) for k, v in txn.data.items()
                    }),
                )
                for txn in transactions
            ]
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && pytest tests/test_fints_connector.py -v`
Expected: All 2 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/tms/connectors/fints_connector.py backend/tests/test_fints_connector.py
git commit -m "feat(backend): add FinTS connector for Sparkasse"
```

---

## Task 13: Sync Engine

**Files:**
- Create: `backend/src/tms/services/sync_engine.py`
- Create: `backend/tests/test_sync_engine.py`

- [ ] **Step 1: Write the test**

```python
# backend/tests/test_sync_engine.py
from datetime import date, datetime, UTC
from unittest.mock import MagicMock
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from tms.models import Base, Account, Transaction, SyncLog
from tms.seed import seed_categories
from tms.connectors.base import RawTransaction, AccountBalance
from tms.services.sync_engine import SyncEngine


def make_env():
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(engine)
    with Session(engine) as db:
        seed_categories(db)
        db.add(Account(
            name="Test Bank", bank="test", currency="AED",
            type="checking", balance=10000.0, is_active=True,
        ))
        db.commit()
    return engine


def make_mock_connector():
    connector = MagicMock()
    connector.fetch_accounts.return_value = [
        AccountBalance(external_id="ext_1", name="Test Bank", currency="AED", balance=15000.0),
    ]
    connector.fetch_transactions.return_value = [
        RawTransaction(
            external_id="txn_new_1", amount=-234.50, currency="AED",
            date=date(2026, 4, 8), merchant_name="Carrefour",
            description="POS", raw_data="{}",
        ),
        RawTransaction(
            external_id="txn_new_2", amount=-45.00, currency="AED",
            date=date(2026, 4, 7), merchant_name="Uber",
            description="Ride", raw_data="{}",
        ),
    ]
    return connector


def test_sync_imports_new_transactions():
    engine = make_env()
    connector = make_mock_connector()

    sync = SyncEngine(engine)
    sync.sync_account(account_id=1, connector=connector, external_id="ext_1")

    with Session(engine) as db:
        txns = db.query(Transaction).all()
        assert len(txns) == 2

        account = db.get(Account, 1)
        assert account.balance == 15000.0
        assert account.last_sync_at is not None

        log = db.query(SyncLog).first()
        assert log.status == "success"
        assert log.transactions_fetched == 2


def test_sync_deduplicates():
    engine = make_env()
    connector = make_mock_connector()

    sync = SyncEngine(engine)
    sync.sync_account(account_id=1, connector=connector, external_id="ext_1")
    sync.sync_account(account_id=1, connector=connector, external_id="ext_1")

    with Session(engine) as db:
        txns = db.query(Transaction).all()
        assert len(txns) == 2  # No duplicates


def test_sync_logs_error():
    engine = make_env()
    connector = MagicMock()
    connector.fetch_accounts.return_value = []
    connector.fetch_transactions.side_effect = Exception("API timeout")

    sync = SyncEngine(engine)
    sync.sync_account(account_id=1, connector=connector, external_id="ext_1")

    with Session(engine) as db:
        log = db.query(SyncLog).first()
        assert log.status == "error"
        assert "API timeout" in log.error
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_sync_engine.py -v`
Expected: FAIL

- [ ] **Step 3: Implement sync_engine.py**

```python
# backend/src/tms/services/sync_engine.py
from datetime import date, datetime, timedelta, UTC
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from tms.models import Account, Transaction, SyncLog
from tms.connectors.base import BankConnector, RawTransaction
from tms.services.categorizer import Categorizer
from tms.services.currency import CurrencyService


class SyncEngine:
    def __init__(self, engine):
        self.engine = engine
        self.categorizer = Categorizer(engine)
        self.currency = CurrencyService(engine)

    def sync_account(
        self, account_id: int, connector: BankConnector, external_id: str
    ) -> None:
        with Session(self.engine) as db:
            account = db.get(Account, account_id)
            log = SyncLog(
                account_id=account_id,
                started_at=datetime.now(UTC),
                status="running",
            )
            db.add(log)
            db.commit()

            try:
                since = self._get_sync_since(account)
                raw_txns = connector.fetch_transactions(external_id, since)
                new_count = self._import_transactions(db, account, raw_txns)

                # Update balance from connector
                for acc_balance in connector.fetch_accounts():
                    if acc_balance.external_id == external_id:
                        account.balance = acc_balance.balance
                        break

                account.last_sync_at = datetime.now(UTC)
                log.status = "success"
                log.transactions_fetched = new_count
                log.finished_at = datetime.now(UTC)
                db.commit()

            except Exception as e:
                db.rollback()
                with Session(self.engine) as err_db:
                    err_log = err_db.get(SyncLog, log.id)
                    err_log.status = "error"
                    err_log.error = str(e)
                    err_log.finished_at = datetime.now(UTC)
                    err_db.commit()

    def _get_sync_since(self, account: Account) -> date:
        if account.last_sync_at:
            return account.last_sync_at.date() - timedelta(days=1)
        # Initial full sync: go back as far as possible
        return date(2020, 1, 1)

    def _import_transactions(
        self, db: Session, account: Account, raw_txns: list[RawTransaction]
    ) -> int:
        # Get existing external IDs for dedup
        existing_ids = {
            row[0] for row in
            db.query(Transaction.raw_data)
            .filter(Transaction.account_id == account.id)
            .all()
        }

        new_count = 0
        for raw in raw_txns:
            if raw.raw_data in existing_ids:
                continue

            # Check dedup by external_id in raw_data
            # Simple dedup: check if transaction with same amount+date+merchant exists
            exists = (
                db.query(Transaction)
                .filter(
                    Transaction.account_id == account.id,
                    Transaction.amount == raw.amount,
                    Transaction.date == raw.date,
                    Transaction.merchant_name == raw.merchant_name,
                )
                .first()
            )
            if exists:
                continue

            category_id = self.categorizer.categorize(raw.merchant_name, raw.description)

            try:
                amount_aed = self.currency.convert(
                    raw.amount, raw.currency, "AED", raw.date
                )
            except ValueError:
                amount_aed = raw.amount  # Fallback: store as-is

            db.add(Transaction(
                account_id=account.id,
                amount=raw.amount,
                currency=raw.currency,
                amount_aed=amount_aed,
                date=raw.date,
                merchant_name=raw.merchant_name,
                description=raw.description,
                category_id=category_id,
                source=self._infer_source(account.bank),
                raw_data=raw.raw_data,
            ))
            new_count += 1

        db.commit()
        return new_count

    @staticmethod
    def _infer_source(bank: str) -> str:
        source_map = {
            "emirates_nbd": "lean",
            "mashreq": "lean",
            "fab": "lean",
            "vio": "lean",
            "revolut": "revolut",
            "sparkasse": "fints",
        }
        return source_map.get(bank, "manual")
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && pytest tests/test_sync_engine.py -v`
Expected: All 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/tms/services/sync_engine.py backend/tests/test_sync_engine.py
git commit -m "feat(backend): add sync engine with dedup, categorization, and error logging"
```

---

## Task 14: Notification Bridge API

**Files:**
- Create: `backend/src/tms/api/notifications.py`
- Create: `backend/tests/test_api_notifications.py`

- [ ] **Step 1: Write the test**

```python
# backend/tests/test_api_notifications.py
from datetime import datetime
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from tms.main import app
from tms.db import get_db
from tms.models import Base, Account
from tms.seed import seed_categories


def make_test_db():
    engine = create_engine("sqlite:///:memory:")
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_api_notifications.py -v`
Expected: FAIL

- [ ] **Step 3: Implement api/notifications.py**

```python
# backend/src/tms/api/notifications.py
import re
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from tms.db import get_db
from tms.models import Account, Transaction
from tms.schemas import NotificationIn
from tms.services.categorizer import Categorizer

router = APIRouter(prefix="/api/notifications", tags=["notifications"])

# Map Android package names to bank identifiers
PACKAGE_TO_BANK = {
    "com.mashreq.mobilebanking": "mashreq",
    "com.mashreqbank": "mashreq",
    "com.fab.personalbanking": "fab",
    "com.adcb.bank": "fab",
}

# Regex to extract amount and merchant from notification text
AMOUNT_PATTERN = re.compile(r"AED\s*([\d,]+\.?\d*)")
MERCHANT_PATTERN = re.compile(r"(?:at|from|to)\s+(.+?)(?:\s+on|\s+with|\s*$)", re.IGNORECASE)


@router.post("")
def receive_notification(body: NotificationIn, db: Session = Depends(get_db)):
    bank = PACKAGE_TO_BANK.get(body.bank_package)
    if not bank:
        return {"status": "ignored", "reason": "unknown package"}

    account = db.query(Account).filter_by(bank=bank, is_active=True).first()
    if not account:
        return {"status": "ignored", "reason": "no matching account"}

    amount = _extract_amount(body.text)
    merchant = _extract_merchant(body.text)

    if amount is None:
        return {"status": "received", "parsed": False}

    # Check for duplicate (same amount, same day, same merchant)
    existing = (
        db.query(Transaction)
        .filter(
            Transaction.account_id == account.id,
            Transaction.amount == amount,
            Transaction.date == body.timestamp.date(),
            Transaction.merchant_name == merchant,
        )
        .first()
    )
    if existing:
        return {"status": "received", "duplicate": True}

    txn = Transaction(
        account_id=account.id,
        amount=amount,
        currency="AED",
        amount_aed=amount,
        date=body.timestamp.date(),
        merchant_name=merchant,
        description=body.text,
        source="notification",
        raw_data=body.model_dump_json(),
    )
    db.add(txn)
    db.commit()

    return {"status": "received", "transaction_id": txn.id}


def _extract_amount(text: str) -> float | None:
    match = AMOUNT_PATTERN.search(text)
    if match:
        return -abs(float(match.group(1).replace(",", "")))
    return None


def _extract_merchant(text: str) -> str | None:
    match = MERCHANT_PATTERN.search(text)
    return match.group(1).strip() if match else None
```

- [ ] **Step 4: Register router in main.py**

```python
# backend/src/tms/main.py
from fastapi import FastAPI
from tms.api.accounts import router as accounts_router
from tms.api.transactions import router as transactions_router
from tms.api.categories import router as categories_router
from tms.api.notifications import router as notifications_router

app = FastAPI(title="TMS Banking Backend")
app.include_router(accounts_router)
app.include_router(transactions_router)
app.include_router(categories_router)
app.include_router(notifications_router)


@app.get("/health")
async def health():
    return {"status": "ok"}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && pytest tests/test_api_notifications.py -v`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/tms/api/notifications.py backend/tests/test_api_notifications.py backend/src/tms/main.py
git commit -m "feat(backend): add notification bridge API for phone push/SMS forwarding"
```

---

## Task 15: Sync Status API + Trigger

**Files:**
- Create: `backend/src/tms/api/sync.py`

- [ ] **Step 1: Implement api/sync.py**

```python
# backend/src/tms/api/sync.py
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from tms.db import get_db
from tms.models import Account, SyncLog
from tms.schemas import SyncStatusOut

router = APIRouter(prefix="/api/sync", tags=["sync"])


@router.get("/status", response_model=list[SyncStatusOut])
def sync_status(db: Session = Depends(get_db)):
    accounts = db.query(Account).filter(Account.is_active.is_(True)).all()
    results = []
    for account in accounts:
        last_log = (
            db.query(SyncLog)
            .filter_by(account_id=account.id)
            .order_by(SyncLog.started_at.desc())
            .first()
        )
        results.append(SyncStatusOut(
            account_id=account.id,
            account_name=account.name,
            last_sync_at=account.last_sync_at,
            status=last_log.status if last_log else "never",
            transactions_fetched=last_log.transactions_fetched if last_log else None,
        ))
    return results


@router.post("/trigger")
def trigger_sync():
    # Will be wired up to the scheduler in the lifespan task
    return {"status": "triggered"}
```

- [ ] **Step 2: Register router in main.py**

```python
# backend/src/tms/main.py
from fastapi import FastAPI
from tms.api.accounts import router as accounts_router
from tms.api.transactions import router as transactions_router
from tms.api.categories import router as categories_router
from tms.api.notifications import router as notifications_router
from tms.api.sync import router as sync_router

app = FastAPI(title="TMS Banking Backend")
app.include_router(accounts_router)
app.include_router(transactions_router)
app.include_router(categories_router)
app.include_router(notifications_router)
app.include_router(sync_router)


@app.get("/health")
async def health():
    return {"status": "ok"}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/tms/api/sync.py backend/src/tms/main.py
git commit -m "feat(backend): add sync status and trigger API endpoints"
```

---

## Task 16: App Lifespan — Scheduler + DB Init

**Files:**
- Modify: `backend/src/tms/main.py`

- [ ] **Step 1: Implement lifespan with scheduler and DB init**

```python
# backend/src/tms/main.py
from contextlib import asynccontextmanager
from apscheduler.schedulers.background import BackgroundScheduler
from fastapi import FastAPI
from tms.config import settings
from tms.db import engine
from tms.models import Base
from tms.seed import seed_categories
from tms.api.accounts import router as accounts_router
from tms.api.transactions import router as transactions_router
from tms.api.categories import router as categories_router
from tms.api.notifications import router as notifications_router
from tms.api.sync import router as sync_router

scheduler = BackgroundScheduler()


def scheduled_sync():
    """Called by APScheduler every N minutes."""
    from tms.services.sync_engine import SyncEngine
    from tms.connectors.lean import LeanConnector
    from tms.connectors.revolut import RevolutConnector
    from tms.connectors.fints_connector import FinTSConnector
    from sqlalchemy.orm import Session

    sync = SyncEngine(engine)
    with Session(engine) as db:
        accounts = db.query(
            __import__("tms.models", fromlist=["Account"]).Account
        ).filter_by(is_active=True).all()

        for account in accounts:
            connector = _get_connector(account.bank)
            if connector:
                # external_id stored as raw_data on first sync — for now use name
                sync.sync_account(account.id, connector, account.name)


def _get_connector(bank: str):
    from tms.connectors.lean import LeanConnector
    from tms.connectors.revolut import RevolutConnector
    from tms.connectors.fints_connector import FinTSConnector

    if bank in ("emirates_nbd", "mashreq", "fab", "vio"):
        if settings.lean_app_token:
            return LeanConnector(settings.lean_app_token, settings.lean_customer_id)
    elif bank == "revolut":
        if settings.revolut_refresh_token:
            return RevolutConnector(settings.revolut_refresh_token)
    elif bank == "sparkasse":
        if settings.fints_blz:
            return FinTSConnector(
                settings.fints_blz, settings.fints_login,
                settings.fints_pin, settings.fints_endpoint,
            )
    return None


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: create tables, seed categories, start scheduler
    Base.metadata.create_all(engine)
    from sqlalchemy.orm import Session
    with Session(engine) as db:
        seed_categories(db)

    scheduler.add_job(
        scheduled_sync, "interval",
        minutes=settings.sync_interval_minutes, id="bank_sync",
    )
    scheduler.start()

    # Run catch-up sync on startup
    scheduled_sync()

    yield

    # Shutdown
    scheduler.shutdown()


app = FastAPI(title="TMS Banking Backend", lifespan=lifespan)
app.include_router(accounts_router)
app.include_router(transactions_router)
app.include_router(categories_router)
app.include_router(notifications_router)
app.include_router(sync_router)


@app.get("/health")
async def health():
    return {"status": "ok"}
```

- [ ] **Step 2: Run all tests to make sure nothing broke**

Run: `cd backend && pytest -v`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/tms/main.py
git commit -m "feat(backend): add lifespan with scheduler, DB init, and startup catch-up sync"
```

---

## Task 17: Run Script + .env Template

**Files:**
- Create: `backend/.env.example`
- Create: `backend/run.sh`

- [ ] **Step 1: Create .env.example**

```bash
# backend/.env.example
# Lean Technologies (UAE Banks)
TMS_LEAN_APP_TOKEN=
TMS_LEAN_CUSTOMER_ID=

# Revolut
TMS_REVOLUT_CLIENT_ID=
TMS_REVOLUT_CLIENT_SECRET=
TMS_REVOLUT_REFRESH_TOKEN=

# FinTS / Sparkasse
TMS_FINTS_BLZ=
TMS_FINTS_LOGIN=
TMS_FINTS_PIN=
TMS_FINTS_ENDPOINT=

# Server
TMS_HOST=0.0.0.0
TMS_PORT=8000
TMS_SYNC_INTERVAL_MINUTES=15
TMS_DB_PATH=tms_banking.db
```

- [ ] **Step 2: Create run.sh**

```bash
#!/usr/bin/env bash
# backend/run.sh
set -e
cd "$(dirname "$0")"
exec python -m uvicorn tms.main:app \
    --host "${TMS_HOST:-0.0.0.0}" \
    --port "${TMS_PORT:-8000}" \
    --reload
```

- [ ] **Step 3: Make executable and commit**

```bash
chmod +x backend/run.sh
git add backend/.env.example backend/run.sh
git commit -m "feat(backend): add .env template and run script"
```

---

## Task 18: Final Integration Test

**Files:**
- Create: `backend/tests/test_integration.py`

- [ ] **Step 1: Write end-to-end integration test**

```python
# backend/tests/test_integration.py
"""
Integration test: seed DB → create account → sync transactions → check API.
"""
from datetime import date
from unittest.mock import MagicMock
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from tms.main import app
from tms.db import get_db
from tms.models import Base, Account
from tms.seed import seed_categories
from tms.services.sync_engine import SyncEngine
from tms.connectors.base import RawTransaction, AccountBalance


def test_full_flow():
    engine = create_engine("sqlite:///:memory:")
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
```

- [ ] **Step 2: Run full test suite**

Run: `cd backend && pytest -v`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add backend/tests/test_integration.py
git commit -m "test(backend): add end-to-end integration test covering sync + API + categorization"
```

---

## Summary

| Task | Component | What it builds |
|------|-----------|----------------|
| 1 | Scaffolding | Project structure, FastAPI app, health endpoint |
| 2 | Models | SQLAlchemy models for all 5 tables |
| 3 | Seed | 12 default categories |
| 4 | API | Accounts endpoints |
| 5 | API | Transactions endpoints with category update |
| 6 | API | Categories endpoint |
| 7 | Service | Currency conversion with rate storage |
| 8 | Service | Auto-categorization (merchant + keyword + learning) |
| 9 | Connector | BankConnector protocol |
| 10 | Connector | Lean Technologies (UAE banks) |
| 11 | Connector | Revolut API |
| 12 | Connector | FinTS (Sparkasse) |
| 13 | Service | Sync engine with dedup and error handling |
| 14 | API | Notification bridge for phone push/SMS |
| 15 | API | Sync status and manual trigger |
| 16 | Lifecycle | Scheduler, DB init, startup catch-up |
| 17 | DevOps | .env template, run script |
| 18 | Test | End-to-end integration test |
