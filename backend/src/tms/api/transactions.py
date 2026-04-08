from datetime import date
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from tms.db import get_db
from tms.models import Transaction
from tms.schemas import TransactionOut, UpdateTransactionCategory

router = APIRouter(prefix="/api/transactions", tags=["transactions"])


@router.get("", response_model=list[TransactionOut])
def list_transactions(
    account_id: int | None = Query(None),
    category_id: int | None = Query(None),
    date_from: date | None = Query(None),
    date_to: date | None = Query(None),
    search: str | None = Query(None),
    limit: int = Query(500, le=10000),
    offset: int = Query(0),
    db: Session = Depends(get_db),
):
    q = db.query(Transaction).order_by(Transaction.date.desc(), Transaction.id.desc())
    if account_id:
        q = q.filter(Transaction.account_id == account_id)
    if category_id:
        q = q.filter(Transaction.category_id == category_id)
    if date_from:
        q = q.filter(Transaction.date >= date_from)
    if date_to:
        q = q.filter(Transaction.date <= date_to)
    if search:
        q = q.filter(
            Transaction.merchant_name.ilike(f"%{search}%")
            | Transaction.description.ilike(f"%{search}%")
        )
    return q.offset(offset).limit(limit).all()


@router.get("/count")
def count_transactions(
    account_id: int | None = Query(None),
    category_id: int | None = Query(None),
    date_from: date | None = Query(None),
    date_to: date | None = Query(None),
    db: Session = Depends(get_db),
):
    q = db.query(Transaction)
    if account_id:
        q = q.filter(Transaction.account_id == account_id)
    if category_id:
        q = q.filter(Transaction.category_id == category_id)
    if date_from:
        q = q.filter(Transaction.date >= date_from)
    if date_to:
        q = q.filter(Transaction.date <= date_to)
    return {"count": q.count()}


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
