from datetime import date
from sqlalchemy.orm import Session
from tms.models import ExchangeRate


class CurrencyService:
    def __init__(self, engine):
        self.engine = engine

    def convert(self, amount: float, from_curr: str, to_curr: str, on_date: date) -> float:
        if from_curr == to_curr:
            return amount

        rate = self._get_rate(from_curr, to_curr, on_date)
        if rate:
            return round(amount * rate, 2)

        reverse = self._get_rate(to_curr, from_curr, on_date)
        if reverse:
            return round(amount / reverse, 2)

        raise ValueError(f"No exchange rate found for {from_curr} → {to_curr}")

    def _get_rate(self, from_curr: str, to_curr: str, on_date: date) -> float | None:
        with Session(self.engine) as db:
            rate = (
                db.query(ExchangeRate)
                .filter(
                    ExchangeRate.from_currency == from_curr,
                    ExchangeRate.to_currency == to_curr,
                    ExchangeRate.date <= on_date,
                )
                .order_by(ExchangeRate.date.desc())
                .first()
            )
            return rate.rate if rate else None

    def store_rates(self, rates: dict[str, float], base: str, on_date: date) -> None:
        with Session(self.engine) as db:
            for currency, rate in rates.items():
                if currency == base:
                    continue
                existing = (
                    db.query(ExchangeRate)
                    .filter_by(from_currency=base, to_currency=currency, date=on_date)
                    .first()
                )
                if existing:
                    existing.rate = rate
                else:
                    db.add(ExchangeRate(
                        from_currency=base, to_currency=currency,
                        rate=rate, date=on_date,
                    ))
            db.commit()
