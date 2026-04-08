import json
from datetime import date, datetime
import httpx
from tms.connectors.base import RawTransaction, AccountBalance

REVOLUT_BASE_URL = "https://b2b.revolut.com/api/1.0"


class RevolutConnector:
    def __init__(self, access_token: str):
        self.headers = {
            "Authorization": f"Bearer {access_token}",
        }

    def fetch_accounts(self) -> list[AccountBalance]:
        resp = httpx.get(f"{REVOLUT_BASE_URL}/accounts", headers=self.headers)
        resp.raise_for_status()

        return [
            AccountBalance(
                external_id=acc["id"],
                name=acc["name"],
                currency=acc["currency"],
                balance=acc["balance"] / 100,  # Minor units → major
            )
            for acc in resp.json()
        ]

    def fetch_transactions(
        self, account_external_id: str, since: date
    ) -> list[RawTransaction]:
        resp = httpx.get(
            f"{REVOLUT_BASE_URL}/transactions",
            headers=self.headers,
            params={
                "account_id": account_external_id,
                "from": f"{since.isoformat()}T00:00:00Z",
            },
        )
        resp.raise_for_status()

        return [
            RawTransaction(
                external_id=txn["id"],
                amount=txn["amount"] / 100,  # Minor units → major
                currency=txn["currency"],
                date=datetime.fromisoformat(txn["created_at"].replace("Z", "+00:00")).date(),
                merchant_name=txn["merchant"]["name"] if txn.get("merchant") else None,
                description=txn.get("description"),
                raw_data=json.dumps(txn),
            )
            for txn in resp.json()
        ]
