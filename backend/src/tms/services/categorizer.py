from sqlalchemy.orm import Session
from tms.models import Category, MerchantCategoryMapping

MERCHANT_MAP = {
    "carrefour": "Lebensmittel",
    "spinneys": "Lebensmittel",
    "lulu": "Lebensmittel",
    "choithrams": "Lebensmittel",
    "uber": "Transport",
    "careem": "Transport",
    "rta": "Transport",
    "salik": "Transport",
    "dewa": "Nebenkosten",
    "etisalat": "Nebenkosten",
    "du telecom": "Nebenkosten",
    "amazon": "Shopping",
    "noon": "Shopping",
    "namshi": "Shopping",
    "netflix": "Abos",
    "spotify": "Abos",
    "apple.com": "Abos",
    "google play": "Abos",
}

KEYWORD_MAP = {
    "restaurant": "Restaurants",
    "café": "Restaurants",
    "cafe": "Restaurants",
    "coffee": "Restaurants",
    "pharmacy": "Gesundheit",
    "clinic": "Gesundheit",
    "hospital": "Gesundheit",
    "medical": "Gesundheit",
    "cinema": "Unterhaltung",
    "gym": "Gesundheit",
    "rent": "Miete",
    "salary": "Einkommen",
    "gehalt": "Einkommen",
}


class Categorizer:
    def __init__(self, engine):
        self.engine = engine
        self._learned: dict[str, int] = {}
        self._category_cache: dict[str, int] = {}
        self._load_category_ids()
        self._load_learned_mappings()

    def _load_category_ids(self):
        with Session(self.engine) as db:
            for cat in db.query(Category).filter(Category.parent_id.is_(None)).all():
                self._category_cache[cat.name] = cat.id

    def _load_learned_mappings(self):
        """Load persisted merchant→category mappings from DB into in-memory cache."""
        with Session(self.engine) as db:
            for mapping in db.query(MerchantCategoryMapping).all():
                self._learned[mapping.merchant_name] = mapping.category_id

    def categorize(self, merchant_name: str | None, description: str | None) -> int | None:
        if not merchant_name and not description:
            return None

        merchant_lower = (merchant_name or "").lower().strip()
        desc_lower = (description or "").lower().strip()

        # 1. Check learned mappings
        if merchant_lower in self._learned:
            return self._learned[merchant_lower]

        # 2. Check known merchants
        for pattern, cat_name in MERCHANT_MAP.items():
            if pattern in merchant_lower:
                return self._category_cache.get(cat_name)

        # 3. Check keywords in description only
        for keyword, cat_name in KEYWORD_MAP.items():
            if keyword in desc_lower:
                return self._category_cache.get(cat_name)

        return None

    def learn(self, merchant_name: str, category_id: int) -> None:
        """Persist a merchant→category mapping to DB and update in-memory cache."""
        key = merchant_name.lower().strip()
        self._learned[key] = category_id

        with Session(self.engine) as db:
            existing = db.query(MerchantCategoryMapping).filter_by(merchant_name=key).first()
            if existing:
                existing.category_id = category_id
            else:
                db.add(MerchantCategoryMapping(merchant_name=key, category_id=category_id))
            db.commit()
