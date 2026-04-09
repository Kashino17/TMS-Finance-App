from contextlib import asynccontextmanager
from apscheduler.schedulers.background import BackgroundScheduler
from fastapi import FastAPI
from tms.config import settings
from tms.db import engine
from tms.models import Base, Account
from tms.seed import seed_categories
from tms.api.accounts import router as accounts_router
from tms.api.transactions import router as transactions_router
from tms.api.categories import router as categories_router
from tms.api.notifications import router as notifications_router
from tms.api.sync import router as sync_router
from tms.api.ai_categorize import router as ai_router
from tms.api.loans import router as loans_router

scheduler = BackgroundScheduler()


def scheduled_sync():
    """Called by APScheduler every N minutes."""
    from tms.services.sync_engine import SyncEngine
    from sqlalchemy.orm import Session

    sync = SyncEngine(engine)
    with Session(engine) as db:
        accounts = db.query(Account).filter_by(is_active=True).all()
        for account in accounts:
            connector = _get_connector(account.bank)
            if connector:
                sync.sync_account(account.id, connector, account.name)


def _get_connector(bank: str):
    from tms.connectors.lean import LeanConnector
    from tms.connectors.revolut import RevolutConnector
    from tms.connectors.fints_connector import FinTSConnector

    if bank in ("emirates_nbd", "mashreq", "fab", "vio"):
        if settings.lean_app_id and settings.lean_client_secret:
            return LeanConnector(
                app_id=settings.lean_app_id,
                client_secret=settings.lean_client_secret,
                customer_id=settings.lean_customer_id,
                sandbox=settings.lean_sandbox,
            )
    elif bank == "revolut":
        if settings.revolut_refresh_token:
            return RevolutConnector(settings.revolut_refresh_token)
    elif bank == "sparkasse":
        if settings.fints_blz:
            return FinTSConnector(
                settings.fints_blz, settings.fints_login,
                settings.fints_pin, settings.fints_endpoint,
            )
    return None


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: create tables, seed categories, start scheduler
    Base.metadata.create_all(engine)
    from sqlalchemy.orm import Session
    with Session(engine) as db:
        seed_categories(db)

    scheduler.add_job(
        scheduled_sync, "interval",
        minutes=settings.sync_interval_minutes, id="bank_sync",
    )
    scheduler.start()

    # Run catch-up sync on startup
    scheduled_sync()

    yield

    # Shutdown
    scheduler.shutdown()


app = FastAPI(title="TMS Banking Backend", lifespan=lifespan)
app.include_router(accounts_router)
app.include_router(transactions_router)
app.include_router(categories_router)
app.include_router(notifications_router)
app.include_router(sync_router)
app.include_router(ai_router)
app.include_router(loans_router)


@app.get("/health")
async def health():
    return {"status": "ok"}
