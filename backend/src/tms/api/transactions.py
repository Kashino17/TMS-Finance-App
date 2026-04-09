from datetime import date
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from tms.db import get_db
from tms.models import Transaction
from tms.schemas import TransactionOut, UpdateTransactionCategory, TransactionUpdate
from tms.services.categorizer import Categorizer

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
    old_category_id = txn.category_id
    txn.category_id = body.category_id
    db.commit()
    db.refresh(txn)

    # Learn: if merchant is known and category changed, persist the mapping
    if txn.merchant_name and old_category_id != body.category_id:
        db_engine = db.get_bind()
        categorizer = Categorizer(db_engine)
        categorizer.learn(txn.merchant_name, body.category_id)

    return txn


@router.put("/{txn_id}", response_model=TransactionOut)
def update_transaction(
    txn_id: int,
    body: TransactionUpdate,
    db: Session = Depends(get_db),
):
    txn = db.get(Transaction, txn_id)
    if not txn:
        raise HTTPException(404, "Transaction not found")

    old_category_id = txn.category_id

    if body.merchant_name is not None:
        txn.merchant_name = body.merchant_name
    if body.description is not None:
        txn.description = body.description
    if body.amount is not None:
        txn.amount = body.amount
        # Recalculate amount_aed if currency hasn't changed
        txn.amount_aed = body.amount  # Simple passthrough; currency service not called here
    if body.date is not None:
        txn.date = body.date
    if body.category_id is not None:
        txn.category_id = body.category_id
    if body.notes is not None:
        txn.notes = body.notes

    db.commit()
    db.refresh(txn)

    # Learn the new category mapping if category was changed and merchant is known
    if body.category_id is not None and txn.merchant_name and old_category_id != body.category_id:
        db_engine = db.get_bind()
        categorizer = Categorizer(db_engine)
        categorizer.learn(txn.merchant_name, body.category_id)

    return txn


@router.delete("/{txn_id}")
def delete_transaction(
    txn_id: int,
    db: Session = Depends(get_db),
):
    txn = db.get(Transaction, txn_id)
    if not txn:
        raise HTTPException(404, "Transaction not found")
    db.delete(txn)
    db.commit()
    return {"deleted": True, "id": txn_id}
