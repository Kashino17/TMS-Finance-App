from sqlalchemy.orm import Session
from tms.models import Category

DEFAULT_CATEGORIES = [
    ("Einkommen", "💰", "#4caf50"),
    ("Lebensmittel", "🛒", "#ff6b6b"),
    ("Restaurants", "🍽", "#ff9800"),
    ("Transport", "🚗", "#2196f3"),
    ("Shopping", "🛍", "#e91e63"),
    ("Nebenkosten", "💡", "#ffc107"),
    ("Miete", "🏠", "#795548"),
    ("Gesundheit", "🏥", "#00bcd4"),
    ("Unterhaltung", "🎬", "#9c27b0"),
    ("Abos", "📱", "#607d8b"),
    ("Transfers", "🔄", "#78909c"),
    ("Bankgebühren", "🏦", "#455a64"),
    ("Auto", "🚙", "#1565c0"),
    ("Reisen", "✈️", "#00897b"),
    ("Bildung", "📚", "#6a1b9a"),
    ("Business", "💼", "#37474f"),
    ("Spenden", "🤝", "#c62828"),
    ("Sonstiges", "📦", "#9e9e9e"),
]


def seed_categories(db: Session) -> None:
    existing = {c.name for c in db.query(Category).filter(Category.parent_id.is_(None)).all()}
    for name, icon, color in DEFAULT_CATEGORIES:
        if name not in existing:
            db.add(Category(name=name, icon=icon, color=color))
    db.commit()
