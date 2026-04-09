from datetime import date
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session
from tms.db import get_db
from tms.models import Budget, Transaction, Category

router = APIRouter(prefix="/api/budgets", tags=["budgets"])


class BudgetCreate(BaseModel):
    category_id: int
    amount_limit: float
    period: str = "monthly"


def _get_period_start(period: str) -> date:
    today = date.today()
    if period == "monthly":
        return date(today.year, today.month, 1)
    elif period == "yearly":
        return date(today.year, 1, 1)
    else:
        # Default to monthly
        return date(today.year, today.month, 1)


@router.get("")
def list_budgets(db: Session = Depends(get_db)):
    """List all budgets with current spending for this period."""
    budgets = db.query(Budget).all()

    # Collect all category ids
    cat_ids = [b.category_id for b in budgets]
    categories = {
        cat.id: cat.name
        for cat in db.query(Category).filter(Category.id.in_(cat_ids)).all()
    } if cat_ids else {}

    result = []
    for budget in budgets:
        period_start = _get_period_start(budget.period)

        # Sum of negative transactions (expenses) for this category in the current period
        spent_raw = (
            db.query(Transaction)
            .filter(
                Transaction.category_id == budget.category_id,
                Transaction.date >= period_start,
                Transaction.amount_aed < 0,
            )
            .all()
        )
        spent = abs(sum(t.amount_aed for t in spent_raw))
        percentage = round((spent / budget.amount_limit) * 100, 2) if budget.amount_limit else 0.0

        result.append({
            "id": budget.id,
            "category_id": budget.category_id,
            "category_name": categories.get(budget.category_id, "Unknown"),
            "amount_limit": budget.amount_limit,
            "spent": round(spent, 2),
            "percentage": percentage,
            "period": budget.period,
        })

    return result


@router.post("", status_code=201)
def create_budget(body: BudgetCreate, db: Session = Depends(get_db)):
    """Create a new budget."""
    # Verify category exists
    cat = db.get(Category, body.category_id)
    if not cat:
        raise HTTPException(404, "Category not found")

    budget = Budget(
        category_id=body.category_id,
        amount_limit=body.amount_limit,
        period=body.period,
    )
    db.add(budget)
    db.commit()
    db.refresh(budget)
    return {
        "id": budget.id,
        "category_id": budget.category_id,
        "category_name": cat.name,
        "amount_limit": budget.amount_limit,
        "spent": 0.0,
        "percentage": 0.0,
        "period": budget.period,
    }


@router.delete("/{budget_id}")
def delete_budget(budget_id: int, db: Session = Depends(get_db)):
    """Delete a budget by ID."""
    budget = db.get(Budget, budget_id)
    if not budget:
        raise HTTPException(404, "Budget not found")
    db.delete(budget)
    db.commit()
    return {"status": "deleted"}
