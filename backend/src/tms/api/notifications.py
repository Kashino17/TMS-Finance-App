# backend/src/tms/api/notifications.py
import re
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from tms.db import get_db
from tms.models import Account, Transaction
from tms.schemas import NotificationIn

router = APIRouter(prefix="/api/notifications", tags=["notifications"])

PACKAGE_TO_BANK = {
    "com.mashreq.mobilebanking": "mashreq",
    "com.mashreqbank": "mashreq",
    "com.fab.personalbanking": "fab",
    "com.adcb.bank": "fab",
}

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
