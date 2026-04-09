import json
from datetime import date, datetime, timedelta, UTC
import httpx
from tms.connectors.base import RawTransaction, AccountBalance

LEAN_SANDBOX_URL = "https://sandbox.leantech.me"
LEAN_PROD_URL = "https://api.leantech.me"
LEAN_AUTH_SANDBOX_URL = "https://auth.sandbox.leantech.me"
LEAN_AUTH_PROD_URL = "https://auth.leantech.me"


class LeanConnector:
    def __init__(
        self, app_id: str, client_secret: str, customer_id: str, sandbox: bool = True
    ):
        self.app_id = app_id
        self.client_secret = client_secret
        self.customer_id = customer_id
        self.base_url = LEAN_SANDBOX_URL if sandbox else LEAN_PROD_URL
        self.auth_url = LEAN_AUTH_SANDBOX_URL if sandbox else LEAN_AUTH_PROD_URL
        self._access_token: str | None = None
        self._token_expires_at: datetime | None = None

    def _get_token(self) -> str:
        now = datetime.now(UTC)
        if self._access_token and self._token_expires_at and now < self._token_expires_at:
            return self._access_token

        resp = httpx.post(
            f"{self.auth_url}/oauth2/token",
            data={
                "grant_type": "client_credentials",
                "client_id": self.app_id,
                "client_secret": self.client_secret,
                "scope": "api",
            },
            headers={"Content-Type": "application/x-www-form-urlencoded"},
        )
        resp.raise_for_status()
        token_data = resp.json()
        self._access_token = token_data["access_token"]
        expires_in = token_data.get("expires_in", 3600)
        self._token_expires_at = now + timedelta(seconds=expires_in - 60)
        return self._access_token

    def _headers(self) -> dict:
        return {
            "Authorization": f"Bearer {self._get_token()}",
            "Content-Type": "application/json",
        }

    def create_customer(self) -> str:
        """Create a Lean customer and return the customer_id."""
        resp = httpx.post(
            f"{self.base_url}/customers",
            headers=self._headers(),
            json={"app_id": self.app_id},
        )
        resp.raise_for_status()
        return resp.json()["customer_id"]

    def fetch_accounts(self) -> list[AccountBalance]:
        resp = httpx.get(
            f"{self.base_url}/customers/{self.customer_id}/accounts",
            headers=self._headers(),
        )
        resp.raise_for_status()
        data = resp.json()

        return [
            AccountBalance(
                external_id=acc["account_id"],
                name=acc.get("name", acc.get("account_name", "Account")),
                currency=acc.get("currency", "AED"),
                balance=acc.get("balance", {}).get("amount", 0.0)
                if isinstance(acc.get("balance"), dict)
                else float(acc.get("balance", 0)),
            )
            for acc in data.get("results", data if isinstance(data, list) else [])
        ]

    def fetch_transactions(
        self, account_external_id: str, since: date
    ) -> list[RawTransaction]:
        resp = httpx.get(
            f"{self.base_url}/customers/{self.customer_id}/accounts/{account_external_id}/transactions",
            headers=self._headers(),
            params={"from_date": since.isoformat()},
        )
        resp.raise_for_status()
        data = resp.json()

        results = data.get("results", data if isinstance(data, list) else [])
        return [
            RawTransaction(
                external_id=txn.get("transaction_id", txn.get("id", "")),
                amount=txn.get("amount", {}).get("amount", 0.0)
                if isinstance(txn.get("amount"), dict)
                else float(txn.get("amount", 0)),
                currency=txn.get("amount", {}).get("currency", "AED")
                if isinstance(txn.get("amount"), dict)
                else txn.get("currency", "AED"),
                date=date.fromisoformat(txn.get("date", txn.get("timestamp", "2020-01-01"))[:10]),
                merchant_name=(
                    txn["merchant"]["name"]
                    if isinstance(txn.get("merchant"), dict)
                    else txn.get("merchant")
                ),
                description=txn.get("description"),
                raw_data=json.dumps(txn),
            )
            for txn in results
        ]
