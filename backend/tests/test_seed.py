from tms.models import Category
from tms.seed import seed_categories


def test_seed_creates_all_categories(db):
    seed_categories(db)
    categories = db.query(Category).all()
    top_level = [c for c in categories if c.parent_id is None]

    assert len(top_level) == 12
    names = {c.name for c in top_level}
    assert "Einkommen" in names
    assert "Lebensmittel" in names
    assert "Transport" in names
    assert "Sonstiges" in names


def test_seed_is_idempotent(db):
    seed_categories(db)
    seed_categories(db)
    categories = db.query(Category).all()
    top_level = [c for c in categories if c.parent_id is None]
    assert len(top_level) == 12
