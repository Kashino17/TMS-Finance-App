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
