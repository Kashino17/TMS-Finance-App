"""AI-powered transaction categorization using Kimi Code (Anthropic Messages API)."""
import json
import re
from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy.orm import Session
import httpx
from tms.db import get_db
from tms.models import Transaction, Category

router = APIRouter(prefix="/api/ai", tags=["ai"])

KIMI_API_URL = "https://api.kimi.com/coding/v1/messages"
KIMI_MODEL = "kimi-for-coding"


class TestApiKeyRequest(BaseModel):
    api_key: str


class CategorizeRequest(BaseModel):
    api_key: str
    batch_size: int = 30


@router.post("/test-key")
async def test_api_key(body: TestApiKeyRequest):
    """Test if the Kimi Code API key works."""
    key = body.api_key.strip()
    key_preview = f"{key[:8]}...{key[-4:]}" if len(key) > 12 else "too_short"

    try:
        async with httpx.AsyncClient(timeout=15) as client:
            resp = await client.post(
                KIMI_API_URL,
                headers={
                    "x-api-key": key,
                    "anthropic-version": "2023-06-01",
                    "Content-Type": "application/json",
                },
                json={
                    "model": KIMI_MODEL,
                    "max_tokens": 10,
                    "messages": [{"role": "user", "content": "Say OK"}],
                },
            )

            with open("/tmp/enbd_kimi_debug.txt", "w") as f:
                f.write(f"URL: {KIMI_API_URL}\nKey: {key_preview}\nStatus: {resp.status_code}\nResponse: {resp.text[:500]}\n")

            if resp.status_code == 200:
                return {"status": "success", "message": "API key works!"}
            elif resp.status_code == 401:
                return {"status": "error", "message": f"401 — Invalid API key ({key_preview})"}
            elif resp.status_code == 403:
                return {"status": "error", "message": f"403 — Access forbidden ({resp.text[:100]})"}
            else:
                return {"status": "error", "message": f"{resp.status_code}: {resp.text[:150]}"}

    except httpx.TimeoutException:
        return {"status": "error", "message": "Timeout — API not reachable"}
    except Exception as e:
        return {"status": "error", "message": f"Error: {str(e)[:200]}"}


@router.post("/categorize")
async def categorize_transactions(body: CategorizeRequest, db: Session = Depends(get_db)):
    """Use Kimi AI to categorize uncategorized transactions."""
    categories = db.query(Category).filter(Category.parent_id.is_(None)).all()
    cat_map = {c.name: c.id for c in categories}
    cat_list = [f"{c.name} ({c.icon})" for c in categories]

    uncategorized = (
        db.query(Transaction)
        .filter(Transaction.category_id.is_(None))
        .order_by(Transaction.date.desc())
        .all()
    )

    if not uncategorized:
        return {"status": "done", "message": "All transactions already categorized!", "categorized": 0}

    total_categorized = 0
    errors = []

    for i in range(0, len(uncategorized), body.batch_size):
        batch = uncategorized[i:i + body.batch_size]

        txn_lines = []
        for tx in batch:
            txn_lines.append(f"ID:{tx.id} | {tx.date} | {tx.merchant_name or 'Unknown'} | {tx.description or ''} | {tx.amount} {tx.currency}")

        prompt = f"""Categorize these bank transactions into EXACTLY one of these categories:
{', '.join(cat_list)}

Transactions:
{chr(10).join(txn_lines)}

Categorization rules:
- Cash Advance Fee, VAT Fee, Card fees, bank charges → Bankgebühren
- Uber rides, Careem rides, Salik tolls, RTA, fuel/petrol stations (Aral, Shell, Esso) → Transport
- Rent-a-Car, Udrive, car rental → Auto
- Rewe, Aldi, Flink, Spinneys, Carrefour, Lulu, supermarkets, grocery stores → Lebensmittel
- Restaurants, cafes, food delivery (Noon Food, Uber Eats, Careem Food), kebab, fast food → Restaurants
- Monthly subscriptions: Netflix, Spotify, Claude.ai, OpenAI, Crunchyroll, Google One, Capcut, Noon One, Careem Plus → Abos
- Hotels, flights, Trip.com, Airalo (eSIM for travel), travel insurance, Deutsche Bahn tickets → Reisen
- Amazon, Noon.com, Shopify, online shopping, electronics stores (Media Markt) → Shopping
- Transfers between own accounts, payments to people (personal names) → Transfers
- Salary, income credits, Account Credited → Einkommen
- DEWA (electricity/water), du/Etisalat/E& (telecom) → Nebenkosten
- Rent/lease payments to landlords, real estate companies → Miete
- Pharmacy, medical, clinic, hospital, Apotheke → Gesundheit
- Card Payment Received (positive amount from card) → Transfers
- Cash Advance (withdrawing cash from credit card) → Bankgebühren
- Facebook/Meta ads, Google Ads → Shopping
- Hosting (Hostinger, Render, Vercel, Supabase), domain, SaaS tools → Abos
- If genuinely unclear → Sonstiges

Subscription detection:
- A transaction is an "Abo" (subscription) ONLY if the same merchant charges a similar amount monthly (e.g. Netflix, Claude.ai, Google One)
- Buying a train ticket, fuel, groceries, or food delivery is NOT an Abo even if frequent — those are individual purchases
- SaaS tools with monthly billing (Hostinger, Render, Supabase, Vercel) ARE Abos

Respond ONLY with JSON array: [{{"id": 123, "category": "CategoryName"}}]
No explanations, just the JSON array."""

        try:
            async with httpx.AsyncClient(timeout=60) as client:
                resp = await client.post(
                    KIMI_API_URL,
                    headers={
                        "x-api-key": body.api_key,
                        "anthropic-version": "2023-06-01",
                        "Content-Type": "application/json",
                    },
                    json={
                        "model": KIMI_MODEL,
                        "max_tokens": 4000,
                        "messages": [{"role": "user", "content": prompt}],
                    },
                )

                if resp.status_code != 200:
                    errors.append(f"Batch {i//body.batch_size}: HTTP {resp.status_code}")
                    continue

                # Anthropic Messages API returns content differently
                resp_json = resp.json()
                content = ""
                for block in resp_json.get("content", []):
                    if block.get("type") == "text":
                        content += block.get("text", "")

                content = content.strip()
                if content.startswith("```"):
                    content = content.split("\n", 1)[1] if "\n" in content else content[3:]
                    content = content.rsplit("```", 1)[0]
                content = content.strip()

                try:
                    assignments = json.loads(content)
                except json.JSONDecodeError:
                    match = re.search(r'\[.*\]', content, re.DOTALL)
                    if match:
                        assignments = json.loads(match.group())
                    else:
                        errors.append(f"Batch {i//body.batch_size}: Could not parse AI response")
                        continue

                for item in assignments:
                    txn_id = item.get("id")
                    cat_name = item.get("category", "")
                    if txn_id and cat_name in cat_map:
                        txn = db.get(Transaction, txn_id)
                        if txn and txn.category_id is None:
                            txn.category_id = cat_map[cat_name]
                            total_categorized += 1

                db.commit()

        except Exception as e:
            errors.append(f"Batch {i//body.batch_size}: {str(e)[:100]}")

    return {
        "status": "done",
        "categorized": total_categorized,
        "total_uncategorized": len(uncategorized),
        "errors": errors if errors else None,
        "message": f"Categorized {total_categorized}/{len(uncategorized)} transactions",
    }
