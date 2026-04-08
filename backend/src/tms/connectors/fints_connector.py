import json
from datetime import date
from fints.client import FinTS3PinTanClient
from tms.connectors.base import RawTransaction, AccountBalance


class FinTSConnector:
    def __init__(self, blz: str, login: str, pin: str, endpoint: str):
        self.blz = blz
        self.login = login
        self.pin = pin
        self.endpoint = endpoint

    def _make_client(self) -> FinTS3PinTanClient:
        return FinTS3PinTanClient(
            self.blz, self.login, self.pin, self.endpoint
        )

    def fetch_accounts(self) -> list[AccountBalance]:
        with self._make_client() as client:
            sepa_accounts = client.get_sepa_accounts()
            results = []
            for sepa in sepa_accounts:
                balance = client.get_balance(sepa)
                results.append(AccountBalance(
                    external_id=sepa.iban,
                    name=f"Sparkasse {sepa.iban[-4:]}",
                    currency=str(balance.amount.currency),
                    balance=float(balance.amount.amount),
                ))
            return results

    def fetch_transactions(
        self, account_external_id: str, since: date
    ) -> list[RawTransaction]:
        with self._make_client() as client:
            sepa_accounts = client.get_sepa_accounts()
            sepa = next(a for a in sepa_accounts if a.iban == account_external_id)
            transactions = client.get_transactions(sepa, since)

            return [
                RawTransaction(
                    external_id=str(txn.data.get("id", {}).get("reference", "")),
                    amount=float(txn.data["amount"].amount),
                    currency=str(txn.data["amount"].currency),
                    date=txn.data["date"],
                    merchant_name=txn.data.get("applicant_name"),
                    description=txn.data.get("purpose"),
                    raw_data=json.dumps({
                        k: str(v) for k, v in txn.data.items()
                    }),
                )
                for txn in transactions
            ]
