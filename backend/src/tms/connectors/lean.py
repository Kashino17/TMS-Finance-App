import json
from datetime import date
import httpx
from tms.connectors.base import RawTransaction, AccountBalance

LEAN_BASE_URL = "https://api.leantech.me/v2"


class LeanConnector:
    def __init__(self, app_token: str, customer_id: str):
        self.app_token = app_token
        self.customer_id = customer_id
        self.headers = {
            "Authorization": f"Bearer {app_token}",
            "Content-Type": "application/json",
        }

    def fetch_accounts(self) -> list[AccountBalance]:
        resp = httpx.get(
            f"{LEAN_BASE_URL}/customers/{self.customer_id}/accounts",
            headers=self.headers,
        )
        resp.raise_for_status()
        data = resp.json()

        return [
            AccountBalance(
                external_id=acc["account_id"],
                name=acc["name"],
                currency=acc["currency"],
                balance=acc["balance"]["amount"],
            )
            for acc in data["results"]
        ]

    def fetch_transactions(
        self, account_external_id: str, since: date
    ) -> list[RawTransaction]:
        resp = httpx.get(
            f"{LEAN_BASE_URL}/customers/{self.customer_id}/accounts/{account_external_id}/transactions",
            headers=self.headers,
            params={"from_date": since.isoformat()},
        )
        resp.raise_for_status()
        data = resp.json()

        return [
            RawTransaction(
                external_id=txn["transaction_id"],
                amount=txn["amount"]["amount"],
                currency=txn["amount"]["currency"],
                date=date.fromisoformat(txn["date"]),
                merchant_name=txn["merchant"]["name"] if txn.get("merchant") else None,
                description=txn.get("description"),
                raw_data=json.dumps(txn),
            )
            for txn in data["results"]
        ]
