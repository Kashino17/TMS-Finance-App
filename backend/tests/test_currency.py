from datetime import date
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from tms.models import Base, ExchangeRate
from tms.services.currency import CurrencyService


def make_db():
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(engine)
    return engine


def test_convert_same_currency():
    engine = make_db()
    svc = CurrencyService(engine)
    result = svc.convert(100.0, "AED", "AED", date(2026, 4, 8))
    assert result == 100.0


def test_convert_with_stored_rate():
    engine = make_db()
    with Session(engine) as db:
        db.add(ExchangeRate(
            from_currency="EUR", to_currency="AED",
            rate=3.97, date=date(2026, 4, 8),
        ))
        db.commit()

    svc = CurrencyService(engine)
    result = svc.convert(100.0, "EUR", "AED", date(2026, 4, 8))
    assert result == 397.0


def test_convert_uses_nearest_date():
    engine = make_db()
    with Session(engine) as db:
        db.add(ExchangeRate(
            from_currency="EUR", to_currency="AED",
            rate=3.97, date=date(2026, 4, 6),
        ))
        db.commit()

    svc = CurrencyService(engine)
    result = svc.convert(100.0, "EUR", "AED", date(2026, 4, 8))
    assert result == 397.0


def test_convert_reverse_rate():
    engine = make_db()
    with Session(engine) as db:
        db.add(ExchangeRate(
            from_currency="EUR", to_currency="AED",
            rate=3.97, date=date(2026, 4, 8),
        ))
        db.commit()

    svc = CurrencyService(engine)
    result = svc.convert(397.0, "AED", "EUR", date(2026, 4, 8))
    assert abs(result - 100.0) < 0.1
