import threading
from datetime import datetime, UTC
from fastapi import APIRouter, Depends, BackgroundTasks
from sqlalchemy.orm import Session
from tms.db import get_db, engine
from tms.models import Account, SyncLog, Transaction
from tms.schemas import SyncStatusOut, ENBDSyncRequest
from tms.services.categorizer import Categorizer

router = APIRouter(prefix="/api/sync", tags=["sync"])

# Track running syncs
_sync_status = {"enbd": "idle"}  # idle | waiting_smartpass | syncing | done | error
_sync_message = {"enbd": ""}


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
    return {"status": "triggered"}


@router.post("/enbd")
def sync_enbd(body: ENBDSyncRequest, background_tasks: BackgroundTasks):
    """Trigger Emirates NBD sync. Credentials sent from the app (not stored on server).
    User must approve Smart Pass on their phone within 2 minutes."""
    if _sync_status["enbd"] in ("waiting_smartpass", "syncing"):
        return {"status": "already_running", "message": "ENBD sync already in progress"}

    mode = "Full Backlog" if body.full_sync else "Quick Sync"
    _sync_status["enbd"] = "waiting_smartpass"
    _sync_message["enbd"] = f"{mode}: Logging in... Approve Smart Pass on your phone!"
    background_tasks.add_task(_run_enbd_sync, body.username, body.password, body.full_sync)
    return {
        "status": "started",
        "message": f"{mode}: Logging into Emirates NBD... Approve Smart Pass!",
    }


@router.get("/enbd/status")
def enbd_sync_status():
    return {"status": _sync_status["enbd"], "message": _sync_message["enbd"]}


def _run_enbd_sync(username: str, password: str, full_sync: bool = False):
    from tms.connectors.enbd_api import ENBDApiClient

    try:
        def update_status(msg):
            _sync_status["enbd"] = "syncing"
            _sync_message["enbd"] = msg

        client = ENBDApiClient(username, password)

        _sync_status["enbd"] = "waiting_smartpass"
        _sync_message["enbd"] = "Waiting for Smart Pass approval..."

        accounts, transactions = client.sync(full_sync=full_sync, status_callback=update_status)

        _sync_status["enbd"] = "syncing"
        _sync_message["enbd"] = f"Saving {len(transactions)} transactions..."

        # Save to database
        with Session(engine) as db:
            categorizer = Categorizer(engine)
            new_txn_count = 0

            for acc_balance in accounts:
                # Find or create account
                account = db.query(Account).filter_by(
                    bank="emirates_nbd", name=acc_balance.name
                ).first()

                if not account:
                    account = Account(
                        name=acc_balance.name,
                        bank="emirates_nbd",
                        currency=acc_balance.currency,
                        type="checking",
                        balance=acc_balance.balance,
                        is_active=True,
                    )
                    db.add(account)
                    db.commit()
                    db.refresh(account)
                else:
                    account.balance = acc_balance.balance

                account.last_sync_at = datetime.now(UTC)
                db.commit()

            # Import transactions
            # Use first ENBD account if we can't match
            enbd_account = db.query(Account).filter_by(bank="emirates_nbd").first()
            if enbd_account:
                for raw_txn in transactions:
                    # Dedup
                    exists = db.query(Transaction).filter(
                        Transaction.account_id == enbd_account.id,
                        Transaction.amount == raw_txn.amount,
                        Transaction.date == raw_txn.date,
                        Transaction.merchant_name == raw_txn.merchant_name,
                    ).first()
                    if exists:
                        continue

                    category_id = categorizer.categorize(raw_txn.merchant_name, raw_txn.description)
                    db.add(Transaction(
                        account_id=enbd_account.id,
                        amount=raw_txn.amount,
                        currency=raw_txn.currency,
                        amount_aed=raw_txn.amount,  # ENBD is AED
                        date=raw_txn.date,
                        merchant_name=raw_txn.merchant_name,
                        description=raw_txn.description,
                        category_id=category_id,
                        source="scraper",
                        raw_data=raw_txn.raw_data,
                    ))
                    new_txn_count += 1

                db.commit()

                # Log sync
                db.add(SyncLog(
                    account_id=enbd_account.id,
                    started_at=datetime.now(UTC),
                    finished_at=datetime.now(UTC),
                    status="success",
                    transactions_fetched=new_txn_count,
                ))
                db.commit()

        _sync_status["enbd"] = "done"
        _sync_message["enbd"] = f"Sync complete! {len(accounts)} accounts, {new_txn_count} new transactions."

    except TimeoutError as e:
        _sync_status["enbd"] = "error"
        _sync_message["enbd"] = str(e)
    except Exception as e:
        _sync_status["enbd"] = "error"
        _sync_message["enbd"] = f"Error: {str(e)}"
