# backend/src/tms/main.py
from fastapi import FastAPI
from tms.api.accounts import router as accounts_router

app = FastAPI(title="TMS Banking Backend")
app.include_router(accounts_router)


@app.get("/health")
async def health():
    return {"status": "ok"}
