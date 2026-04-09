# backend/src/tms/services/sync_engine.py
from datetime import date, datetime, timedelta, UTC
from sqlalchemy.orm import Session
from tms.models import Account, Transaction, SyncLog
from tms.connectors.base import BankConnector, RawTransaction
from tms.services.categorizer import Categorizer
from tms.services.currency import CurrencyService


class SyncEngine:
    def __init__(self, engine):
        self.engine = engine
        self.categorizer = Categorizer(engine)
        self.currency = CurrencyService(engine)

    def sync_account(
        self, account_id: int, connector: BankConnector, external_id: str
    ) -> None:
        with Session(self.engine) as db:
            account = db.get(Account, account_id)
            log = SyncLog(
                account_id=account_id,
                started_at=datetime.now(UTC),
                status="running",
            )
            db.add(log)
            db.commit()

            try:
                since = self._get_sync_since(account)
                raw_txns = connector.fetch_transactions(external_id, since)
                new_count = self._import_transactions(db, account, raw_txns)

                for acc_balance in connector.fetch_accounts():
                    if acc_balance.external_id == external_id:
                        account.balance = acc_balance.balance
                        break

                account.last_sync_at = datetime.now(UTC)
                log.status = "success"
                log.transactions_fetched = new_count
                log.finished_at = datetime.now(UTC)
                db.commit()

            except Exception as e:
                db.rollback()
                with Session(self.engine) as err_db:
                    err_log = err_db.get(SyncLog, log.id)
                    err_log.status = "error"
                    err_log.error = str(e)
                    err_log.finished_at = datetime.now(UTC)
                    err_db.commit()

    def _get_sync_since(self, account: Account) -> date:
        if account.last_sync_at:
            return account.last_sync_at.date() - timedelta(days=1)
        return date(2020, 1, 1)

    def _import_transactions(
        self, db: Session, account: Account, raw_txns: list[RawTransaction]
    ) -> int:
        new_count = 0
        for raw in raw_txns:
            # Dedup: check external_id first (if available), fallback to amount+date+merchant
            if raw.external_id:
                exists = (
                    db.query(Transaction)
                    .filter(
                        Transaction.account_id == account.id,
                        Transaction.external_id == raw.external_id,
                    )
                    .first()
                )
            else:
                exists = (
                    db.query(Transaction)
                    .filter(
                        Transaction.account_id == account.id,
                        Transaction.amount == raw.amount,
                        Transaction.date == raw.date,
                        Transaction.merchant_name == raw.merchant_name,
                    )
                    .first()
                )
            if exists:
                continue

            category_id = self.categorizer.categorize(raw.merchant_name, raw.description)

            try:
                amount_aed = self.currency.convert(
                    raw.amount, raw.currency, "AED", raw.date
                )
            except ValueError:
                amount_aed = raw.amount  # Fallback

            db.add(Transaction(
                account_id=account.id,
                external_id=raw.external_id if raw.external_id else None,
                amount=raw.amount,
                currency=raw.currency,
                amount_aed=amount_aed,
                date=raw.date,
                merchant_name=raw.merchant_name,
                description=raw.description,
                category_id=category_id,
                source=self._infer_source(account.bank),
                raw_data=raw.raw_data,
            ))
            new_count += 1

        db.commit()
        return new_count

    @staticmethod
    def _infer_source(bank: str) -> str:
        source_map = {
            "emirates_nbd": "lean",
            "mashreq": "lean",
            "fab": "lean",
            "vio": "lean",
            "revolut": "revolut",
            "sparkasse": "fints",
        }
        return source_map.get(bank, "manual")
