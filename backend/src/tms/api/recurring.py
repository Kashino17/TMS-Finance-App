from datetime import date, timedelta
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from tms.db import get_db
from tms.models import Transaction, Category

router = APIRouter(prefix="/api/recurring", tags=["recurring"])


@router.get("")
def get_recurring(db: Session = Depends(get_db)):
    """Analyzes transactions and returns detected recurring payments."""
    transactions = (
        db.query(Transaction)
        .filter(
            Transaction.merchant_name.isnot(None),
            Transaction.amount_aed < 0,
        )
        .order_by(Transaction.merchant_name, Transaction.date)
        .all()
    )

    # Group by merchant_name
    by_merchant: dict[str, list[Transaction]] = {}
    for txn in transactions:
        merchant = txn.merchant_name.strip()
        if not merchant:
            continue
        by_merchant.setdefault(merchant, []).append(txn)

    # Collect category names
    all_category_ids = {
        txn.category_id for txns in by_merchant.values() for txn in txns if txn.category_id
    }
    categories = {
        cat.id: cat.name
        for cat in db.query(Category).filter(Category.id.in_(all_category_ids)).all()
    } if all_category_ids else {}

    results = []
    for merchant, txns in by_merchant.items():
        if len(txns) < 3:
            continue

        # Sort by date
        txns_sorted = sorted(txns, key=lambda t: t.date)
        amounts = [t.amount_aed for t in txns_sorted]
        avg_amount = sum(amounts) / len(amounts)

        # Check that all amounts are within ±20% of average
        tolerance = abs(avg_amount) * 0.20
        if any(abs(a - avg_amount) > tolerance for a in amounts):
            continue

        # Check roughly monthly intervals (25-35 days) between consecutive transactions
        dates = [t.date for t in txns_sorted]
        intervals = [(dates[i + 1] - dates[i]).days for i in range(len(dates) - 1)]

        if not all(25 <= gap <= 35 for gap in intervals):
            continue

        last_date = dates[-1]
        avg_interval = sum(intervals) / len(intervals)
        next_estimated = last_date + timedelta(days=round(avg_interval))

        # Most common category
        cat_ids = [t.category_id for t in txns_sorted if t.category_id]
        category_name = None
        if cat_ids:
            most_common_cat = max(set(cat_ids), key=cat_ids.count)
            category_name = categories.get(most_common_cat)

        results.append({
            "merchant": merchant,
            "avg_amount": round(avg_amount, 2),
            "frequency": "monthly",
            "last_date": last_date.isoformat(),
            "next_estimated": next_estimated.isoformat(),
            "category": category_name,
            "count": len(txns_sorted),
        })

    # Sort by avg_amount ascending (largest expenses first)
    results.sort(key=lambda r: r["avg_amount"])
    return results
