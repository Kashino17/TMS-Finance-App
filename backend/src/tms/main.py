# backend/src/tms/main.py
from fastapi import FastAPI

app = FastAPI(title="TMS Banking Backend")


@app.get("/health")
async def health():
    return {"status": "ok"}
