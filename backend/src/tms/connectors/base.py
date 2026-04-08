from dataclasses import dataclass
from datetime import date
from typing import Protocol


@dataclass
class RawTransaction:
    """Normalized transaction from any bank connector."""
    external_id: str
    amount: float
    currency: str
    date: date
    merchant_name: str | None
    description: str | None
    raw_data: str  # JSON string of original API response


@dataclass
class AccountBalance:
    """Current balance from a bank connector."""
    external_id: str
    name: str
    currency: str
    balance: float


class BankConnector(Protocol):
    """Protocol that all bank connectors must implement."""

    def fetch_accounts(self) -> list[AccountBalance]: ...

    def fetch_transactions(
        self, account_external_id: str, since: date
    ) -> list[RawTransaction]: ...
