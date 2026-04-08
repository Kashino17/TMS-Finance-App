"""AI-powered transaction categorization using Kimi K 2.5 (Moonshot API)."""
import json
from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy.orm import Session
import httpx
from tms.db import get_db
from tms.models import Transaction, Category

router = APIRouter(prefix="/api/ai", tags=["ai"])

KIMI_API_URL = "https://api.moonshot.ai/v1/chat/completions"


class TestApiKeyRequest(BaseModel):
    api_key: str


class CategorizeRequest(BaseModel):
    api_key: str
    batch_size: int = 30  # How many transactions per AI call


@router.post("/test-key")
async def test_api_key(body: TestApiKeyRequest):
    """Test if the Kimi API key works."""
    try:
        async with httpx.AsyncClient(timeout=15) as client:
            resp = await client.post(
                KIMI_API_URL,
                headers={
                    "Authorization": f"Bearer {body.api_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": "kimi-k2.5",
                    "messages": [{"role": "user", "content": "Say OK"}],
                    "max_tokens": 5,
                },
            )
            if resp.status_code == 200:
                return {"status": "success", "message": "API key works!"}
            elif resp.status_code == 401:
                return {"status": "error", "message": "401 Unauthorized — Invalid API key"}
            elif resp.status_code == 403:
                return {"status": "error", "message": "403 Forbidden — API key has no access"}
            elif resp.status_code == 429:
                return {"status": "error", "message": "429 Rate Limited — Too many requests"}
            else:
                detail = resp.text[:200]
                return {"status": "error", "message": f"{resp.status_code}: {detail}"}
    except httpx.TimeoutException:
        return {"status": "error", "message": "Timeout — Kimi API not reachable"}
    except Exception as e:
        return {"status": "error", "message": f"Error: {str(e)[:200]}"}


@router.post("/categorize")
async def categorize_transactions(body: CategorizeRequest, db: Session = Depends(get_db)):
    """Use Kimi AI to categorize uncategorized transactions."""
    # Get all categories
    categories = db.query(Category).filter(Category.parent_id.is_(None)).all()
    cat_map = {c.name: c.id for c in categories}
    cat_list = [f"{c.name} ({c.icon})" for c in categories]

    # Get uncategorized transactions
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

    # Process in batches
    for i in range(0, len(uncategorized), body.batch_size):
        batch = uncategorized[i:i + body.batch_size]

        txn_lines = []
        for tx in batch:
            txn_lines.append(f"ID:{tx.id} | {tx.date} | {tx.merchant_name or 'Unknown'} | {tx.description or ''} | {tx.amount} {tx.currency}")

        prompt = f"""Categorize these bank transactions into EXACTLY one of these categories:
{', '.join(cat_list)}

Transactions:
{chr(10).join(txn_lines)}

Rules:
- Cash Advance Fee, VAT Fee, Card fees → Bankgebühren
- Uber, Careem, Salik, RTA, fuel stations, Aral → Transport
- Rent-a-Car, Udrive → Auto
- Rewe, Aldi, Flink, Spinneys, Carrefour, supermarkets, grocery → Lebensmittel
- Restaurants, cafes, food delivery (Noon Food, Uber Eats, Careem Food) → Restaurants
- Netflix, Spotify, Claude.ai, OpenAI, Crunchyroll, subscriptions → Abos
- Hotels, flights, Trip.com, Airalo, travel insurance → Reisen
- Amazon, Noon.com, Shopify purchases → Shopping
- Transfers between own accounts, payments to people → Transfers
- Salary, income credits → Einkommen
- DEWA, electricity, telecom, E& → Nebenkosten
- Rent payments → Miete
- Pharmacy, medical → Gesundheit
- Card Payment Received (positive amount) → Transfers
- If unclear → Sonstiges

Respond ONLY with JSON array: [{{"id": 123, "category": "CategoryName"}}]
No explanations, just the JSON array."""

        try:
            async with httpx.AsyncClient(timeout=60) as client:
                resp = await client.post(
                    KIMI_API_URL,
                    headers={
                        "Authorization": f"Bearer {body.api_key}",
                        "Content-Type": "application/json",
                    },
                    json={
                        "model": "kimi-k2.5",
                        "messages": [{"role": "user", "content": prompt}],
                        "max_tokens": 4000,
                        "temperature": 0.1,
                    },
                )

                if resp.status_code != 200:
                    errors.append(f"Batch {i//body.batch_size}: HTTP {resp.status_code}")
                    continue

                content = resp.json()["choices"][0]["message"]["content"]

                # Extract JSON from response (might be wrapped in ```json ... ```)
                content = content.strip()
                if content.startswith("```"):
                    content = content.split("\n", 1)[1] if "\n" in content else content[3:]
                    content = content.rsplit("```", 1)[0]
                content = content.strip()

                try:
                    assignments = json.loads(content)
                except json.JSONDecodeError:
                    # Try to find JSON array in the response
                    import re
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
