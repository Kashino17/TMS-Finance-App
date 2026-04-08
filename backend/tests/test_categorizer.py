from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from tms.models import Base, Category
from tms.seed import seed_categories
from tms.services.categorizer import Categorizer


def make_db():
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(engine)
    with Session(engine) as db:
        seed_categories(db)
    return engine


def test_match_known_merchant():
    engine = make_db()
    cat = Categorizer(engine)
    result = cat.categorize("Carrefour", "POS purchase")
    assert result is not None
    with Session(engine) as db:
        category = db.get(Category, result)
        assert category.name == "Lebensmittel"


def test_match_keyword_restaurant():
    engine = make_db()
    cat = Categorizer(engine)
    result = cat.categorize("Some Unknown Place", "Restaurant payment")
    assert result is not None
    with Session(engine) as db:
        category = db.get(Category, result)
        assert category.name == "Restaurants"


def test_unknown_merchant_returns_none():
    engine = make_db()
    cat = Categorizer(engine)
    result = cat.categorize("XYZABC Corp", "Wire transfer ref 123")
    assert result is None


def test_learn_from_correction():
    engine = make_db()
    cat = Categorizer(engine)

    assert cat.categorize("My Gym Dubai", "Monthly fee") is None

    with Session(engine) as db:
        health_cat = db.query(Category).filter_by(name="Gesundheit").first()
        cat.learn("My Gym Dubai", health_cat.id)

    result = cat.categorize("My Gym Dubai", "Monthly fee")
    with Session(engine) as db:
        category = db.get(Category, result)
        assert category.name == "Gesundheit"
