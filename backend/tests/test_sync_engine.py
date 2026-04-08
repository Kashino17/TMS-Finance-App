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
            description="Ride", raw_data='{"id":"t2"}',
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
