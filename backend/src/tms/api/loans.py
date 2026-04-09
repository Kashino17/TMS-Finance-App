from datetime import date
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session
from tms.db import get_db
from tms.models import Loan

router = APIRouter(prefix="/api/loans", tags=["loans"])


class LoanCreate(BaseModel):
    name: str
    type: str  # personal_loan / credit_card_loan / installment
    total_amount: float
    remaining_amount: float
    monthly_payment: float
    interest_rate: float | None = None
    currency: str = "AED"
    start_date: date
    end_date: date | None = None
    due_day: int | None = None
    notes: str | None = None


class LoanOut(BaseModel):
    id: int
    name: str
    type: str
    total_amount: float
    remaining_amount: float
    monthly_payment: float
    interest_rate: float | None
    currency: str
    start_date: date
    end_date: date | None
    due_day: int | None
    notes: str | None
    is_active: bool

    model_config = {"from_attributes": True}


class LoanUpdate(BaseModel):
    remaining_amount: float | None = None
    monthly_payment: float | None = None
    is_active: bool | None = None
    notes: str | None = None
    end_date: date | None = None


@router.get("", response_model=list[LoanOut])
def list_loans(active_only: bool = True, db: Session = Depends(get_db)):
    q = db.query(Loan)
    if active_only:
        q = q.filter(Loan.is_active.is_(True))
    return q.order_by(Loan.start_date.desc()).all()


@router.post("", response_model=LoanOut)
def create_loan(body: LoanCreate, db: Session = Depends(get_db)):
    loan = Loan(**body.model_dump())
    db.add(loan)
    db.commit()
    db.refresh(loan)
    return loan


@router.patch("/{loan_id}", response_model=LoanOut)
def update_loan(loan_id: int, body: LoanUpdate, db: Session = Depends(get_db)):
    loan = db.get(Loan, loan_id)
    if not loan:
        raise HTTPException(404, "Loan not found")
    for field, value in body.model_dump(exclude_unset=True).items():
        setattr(loan, field, value)
    db.commit()
    db.refresh(loan)
    return loan


@router.delete("/{loan_id}")
def delete_loan(loan_id: int, db: Session = Depends(get_db)):
    loan = db.get(Loan, loan_id)
    if not loan:
        raise HTTPException(404, "Loan not found")
    db.delete(loan)
    db.commit()
    return {"status": "deleted"}
