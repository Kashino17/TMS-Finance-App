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
