# backend/src/tms/schemas.py
from datetime import datetime, date as Date
from typing import Optional
from pydantic import BaseModel


class AccountOut(BaseModel):
    id: int
    name: str
    bank: str
    currency: str
    type: str
    balance: float
    is_active: bool
    last_sync_at: Optional[datetime]

    model_config = {"from_attributes": True}


class TransactionOut(BaseModel):
    id: int
    account_id: int
    external_id: Optional[str]
    amount: float
    currency: str
    amount_aed: float
    date: Date
    merchant_name: Optional[str]
    description: Optional[str]
    category_id: Optional[int]
    notes: Optional[str]
    source: str

    model_config = {"from_attributes": True}


class TransactionUpdate(BaseModel):
    merchant_name: Optional[str] = None
    description: Optional[str] = None
    amount: Optional[float] = None
    date: Optional[Date] = None
    category_id: Optional[int] = None
    notes: Optional[str] = None


class CategoryOut(BaseModel):
    id: int
    name: str
    icon: str
    color: str
    parent_id: Optional[int]

    model_config = {"from_attributes": True}


class UpdateTransactionCategory(BaseModel):
    category_id: int


class SyncStatusOut(BaseModel):
    account_id: int
    account_name: str
    last_sync_at: Optional[datetime]
    status: str
    transactions_fetched: Optional[int]


class NotificationIn(BaseModel):
    bank_package: str
    title: str
    text: str
    timestamp: datetime


class ENBDSyncRequest(BaseModel):
    username: str
    password: str
    full_sync: bool = False  # True = backlog (scroll all), False = quick (recent only)
