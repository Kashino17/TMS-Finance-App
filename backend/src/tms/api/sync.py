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
    return {"status": "triggered"}
