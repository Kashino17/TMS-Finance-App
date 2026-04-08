# backend/src/tms/schemas.py
from datetime import datetime, date
from pydantic import BaseModel


class AccountOut(BaseModel):
    id: int
    name: str
    bank: str
    currency: str
    type: str
    balance: float
    is_active: bool
    last_sync_at: datetime | None

    model_config = {"from_attributes": True}


class TransactionOut(BaseModel):
    id: int
    account_id: int
    amount: float
    currency: str
    amount_aed: float
    date: date
    merchant_name: str | None
    description: str | None
    category_id: int | None
    source: str

    model_config = {"from_attributes": True}


class CategoryOut(BaseModel):
    id: int
    name: str
    icon: str
    color: str
    parent_id: int | None

    model_config = {"from_attributes": True}


class UpdateTransactionCategory(BaseModel):
    category_id: int


class SyncStatusOut(BaseModel):
    account_id: int
    account_name: str
    last_sync_at: datetime | None
    status: str
    transactions_fetched: int | None


class NotificationIn(BaseModel):
    bank_package: str
    title: str
    text: str
    timestamp: datetime


class ENBDSyncRequest(BaseModel):
    username: str
    password: str
