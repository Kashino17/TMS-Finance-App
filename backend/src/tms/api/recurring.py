from datetime import date, timedelta
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from tms.db import get_db
from tms.models import Transaction, Category

router = APIRouter(prefix="/api/recurring", tags=["recurring"])

# Known subscription cancel/manage URLs
CANCEL_URLS = {
    "netflix": "https://www.netflix.com/cancelplan",
    "spotify": "https://www.spotify.com/account/subscription/",
    "claude.ai": "https://claude.ai/settings/billing",
    "anthropic": "https://claude.ai/settings/billing",
    "openai": "https://platform.openai.com/settings/organization/billing/overview",
    "chatgpt": "https://platform.openai.com/settings/organization/billing/overview",
    "crunchyroll": "https://www.crunchyroll.com/account/subscription",
    "capcut": "https://www.capcut.com/my-account",
    "google one": "https://one.google.com/about/plans",
    "google workspace": "https://admin.google.com/ac/billing",
    "google cloud": "https://console.cloud.google.com/billing",
    "noon one": "https://www.noon.com/account",
    "careem plus": "https://app.careem.com/",
    "hostinger": "https://www.hostinger.com/cpanel/billing",
    "render": "https://dashboard.render.com/billing",
    "vercel": "https://vercel.com/account/billing",
    "supabase": "https://supabase.com/dashboard/org/_/billing",
    "shopify": "https://admin.shopify.com/store/settings/billing",
    "amazon": "https://www.amazon.com/hz/mycd/myx",
    "apple": "https://apps.apple.com/account/subscriptions",
    "uber one": "https://www.uber.com/account/",
    "facebk": "https://adsmanager.facebook.com/billing",
    "facebook": "https://adsmanager.facebook.com/billing",
    "wafeq": "https://app.wafeq.com/settings/billing",
    "moonshot": "https://platform.kimi.ai/console",
    "omnisend": "https://app.omnisend.com/billing",
}


def _get_cancel_url(merchant: str) -> str | None:
    merchant_lower = merchant.lower()
    for key, url in CANCEL_URLS.items():
        if key in merchant_lower:
            return url
    return None


def _detect_recurring(db: Session) -> list[dict]:
    """Core detection logic — used by both endpoints."""
    transactions = (
        db.query(Transaction)
        .filter(
            Transaction.merchant_name.isnot(None),
            Transaction.amount_aed < 0,
        )
        .order_by(Transaction.merchant_name, Transaction.date)
        .all()
    )

    by_merchant: dict[str, list[Transaction]] = {}
    for txn in transactions:
        merchant = txn.merchant_name.strip()
        if not merchant:
            continue
        by_merchant.setdefault(merchant, []).append(txn)

    all_category_ids = {
        txn.category_id for txns in by_merchant.values() for txn in txns if txn.category_id
    }
    categories = {
        cat.id: cat
        for cat in db.query(Category).filter(Category.id.in_(all_category_ids)).all()
    } if all_category_ids else {}

    today = date.today()
    current_month_start = today.replace(day=1)

    results = []
    for merchant, txns in by_merchant.items():
        if len(txns) < 2:
            continue

        txns_sorted = sorted(txns, key=lambda t: t.date)
        amounts = [t.amount_aed for t in txns_sorted]
        avg_amount = sum(amounts) / len(amounts)

        # Check amounts within ±30% tolerance (more lenient for currency fluctuations)
        tolerance = abs(avg_amount) * 0.30
        if any(abs(a - avg_amount) > tolerance for a in amounts):
            continue

        dates = [t.date for t in txns_sorted]
        intervals = [(dates[i + 1] - dates[i]).days for i in range(len(dates) - 1)]

        if not intervals:
            continue

        avg_interval = sum(intervals) / len(intervals)

        # Detect frequency
        if all(20 <= gap <= 40 for gap in intervals):
            frequency = "monthly"
        elif all(5 <= gap <= 9 for gap in intervals):
            frequency = "weekly"
        elif all(350 <= gap <= 380 for gap in intervals):
            frequency = "yearly"
        else:
            continue

        last_date = dates[-1]
        next_estimated = last_date + timedelta(days=round(avg_interval))

        # Active status: has a transaction this month?
        has_this_month = any(d >= current_month_start for d in dates)

        # If expected date is past and no charge this month → possibly cancelled
        is_overdue = next_estimated < today and not has_this_month
        status = "active" if has_this_month else ("overdue" if is_overdue else "pending")

        # Category info
        cat_ids = [t.category_id for t in txns_sorted if t.category_id]
        category_name = None
        category_type = "personal"
        if cat_ids:
            most_common_cat = max(set(cat_ids), key=cat_ids.count)
            cat = categories.get(most_common_cat)
            if cat:
                category_name = cat.name
                if cat.name == "Business":
                    category_type = "business"

        cancel_url = _get_cancel_url(merchant)

        results.append({
            "merchant": merchant,
            "avg_amount": round(avg_amount, 2),
            "frequency": frequency,
            "last_date": last_date.isoformat(),
            "next_estimated": next_estimated.isoformat(),
            "category": category_name,
            "category_type": category_type,
            "count": len(txns_sorted),
            "status": status,
            "cancel_url": cancel_url,
        })

    results.sort(key=lambda r: r["avg_amount"])
    return results


@router.get("")
def get_recurring(db: Session = Depends(get_db)):
    """All detected recurring payments."""
    return _detect_recurring(db)


@router.get("/subscriptions")
def get_subscriptions(db: Session = Depends(get_db)):
    """Personal subscriptions only (Abos)."""
    all_recurring = _detect_recurring(db)
    return [r for r in all_recurring if r["category_type"] == "personal"]


@router.get("/business")
def get_business_recurring(db: Session = Depends(get_db)):
    """Business recurring expenses only."""
    all_recurring = _detect_recurring(db)
    return [r for r in all_recurring if r["category_type"] == "business"]
