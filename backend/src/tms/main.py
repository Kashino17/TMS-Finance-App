# backend/src/tms/main.py
from fastapi import FastAPI
from tms.api.accounts import router as accounts_router
from tms.api.transactions import router as transactions_router
from tms.api.categories import router as categories_router

app = FastAPI(title="TMS Banking Backend")
app.include_router(accounts_router)
app.include_router(transactions_router)
app.include_router(categories_router)


@app.get("/health")
async def health():
    return {"status": "ok"}
