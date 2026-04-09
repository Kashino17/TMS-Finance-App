import csv
import io
from datetime import date
from fastapi import APIRouter, Depends, Query
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session
from tms.db import get_db
from tms.models import Transaction, Category, Account

router = APIRouter(prefix="/api/export", tags=["export"])


@router.get("/csv")
def export_csv(
    from_date: date = Query(None, alias="from"),
    to_date: date = Query(None, alias="to"),
    account_id: int = Query(None),
    db: Session = Depends(get_db),
):
    """Export transactions as a CSV file."""
    query = db.query(Transaction)

    if from_date:
        query = query.filter(Transaction.date >= from_date)
    if to_date:
        query = query.filter(Transaction.date <= to_date)
    if account_id:
        query = query.filter(Transaction.account_id == account_id)

    transactions = query.order_by(Transaction.date.desc()).all()

    # Pre-fetch categories and accounts for efficiency
    cat_ids = {t.category_id for t in transactions if t.category_id}
    categories = {
        cat.id: cat.name
        for cat in db.query(Category).filter(Category.id.in_(cat_ids)).all()
    } if cat_ids else {}

    acc_ids = {t.account_id for t in transactions}
    accounts = {
        acc.id: acc.name
        for acc in db.query(Account).filter(Account.id.in_(acc_ids)).all()
    } if acc_ids else {}

    def generate():
        output = io.StringIO()
        writer = csv.writer(output)
        # Header
        writer.writerow([
            "Date", "Merchant", "Description", "Amount", "Currency",
            "Amount_AED", "Category", "Account"
        ])
        yield output.getvalue()

        for txn in transactions:
            output = io.StringIO()
            writer = csv.writer(output)
            writer.writerow([
                txn.date.isoformat(),
                txn.merchant_name or "",
                txn.description or "",
                txn.amount,
                txn.currency,
                txn.amount_aed,
                categories.get(txn.category_id, "") if txn.category_id else "",
                accounts.get(txn.account_id, ""),
            ])
            yield output.getvalue()

    filename = "transactions"
    if from_date:
        filename += f"_from_{from_date}"
    if to_date:
        filename += f"_to_{to_date}"
    filename += ".csv"

    return StreamingResponse(
        generate(),
        media_type="text/csv",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )
