from datetime import date, timedelta
from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session
from tms.db import get_db
from tms.models import Transaction, Category

router = APIRouter(prefix="/api/analytics", tags=["analytics"])


@router.get("/monthly-summary")
def monthly_summary(db: Session = Depends(get_db)):
    """Returns income/expenses per month for the last 12 months."""
    today = date.today()
    # Start from the first of the month 12 months ago
    if today.month == 12:
        start_month = date(today.year - 1, 1, 1)
    else:
        start_year = today.year - 1
        start_month_num = today.month + 1  # same month last year + 1 to get 12 full months back
        start_month = date(start_year, start_month_num, 1)

    transactions = (
        db.query(Transaction)
        .filter(Transaction.date >= start_month)
        .all()
    )

    # Group by year-month
    monthly: dict[str, dict] = {}
    for txn in transactions:
        key = txn.date.strftime("%Y-%m")
        if key not in monthly:
            monthly[key] = {"income": 0.0, "expenses": 0.0}
        if txn.amount_aed >= 0:
            monthly[key]["income"] += txn.amount_aed
        else:
            monthly[key]["expenses"] += txn.amount_aed

    result = []
    for month_key in sorted(monthly.keys()):
        data = monthly[month_key]
        income = round(data["income"], 2)
        expenses = round(data["expenses"], 2)
        result.append({
            "month": month_key,
            "income": income,
            "expenses": expenses,
            "net": round(income + expenses, 2),
        })

    return result


@router.get("/category-trend")
def category_trend(
    months: int = Query(6, ge=1, le=24),
    db: Session = Depends(get_db),
):
    """Returns spending per category per month for the given number of months."""
    today = date.today()
    # Calculate start date: first day of `months` months ago
    month_num = today.month - months
    year = today.year
    while month_num <= 0:
        month_num += 12
        year -= 1
    start_date = date(year, month_num, 1)

    transactions = (
        db.query(Transaction)
        .filter(
            Transaction.date >= start_date,
            Transaction.category_id.isnot(None),
            Transaction.amount_aed < 0,
        )
        .all()
    )

    # Collect category names
    category_ids = {txn.category_id for txn in transactions}
    categories = {
        cat.id: cat.name
        for cat in db.query(Category).filter(Category.id.in_(category_ids)).all()
    }

    # Group by (month, category_id)
    grouped: dict[tuple[str, int], float] = {}
    for txn in transactions:
        key = (txn.date.strftime("%Y-%m"), txn.category_id)
        grouped[key] = grouped.get(key, 0.0) + txn.amount_aed

    result = []
    for (month_key, cat_id) in sorted(grouped.keys()):
        result.append({
            "month": month_key,
            "category": categories.get(cat_id, "Unknown"),
            "category_id": cat_id,
            "total": round(grouped[(month_key, cat_id)], 2),
        })

    return result
