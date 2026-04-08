# backend/src/tms/config.py
from pydantic_settings import BaseSettings
from pathlib import Path


class Settings(BaseSettings):
    db_path: Path = Path("tms_banking.db")
    host: str = "0.0.0.0"
    port: int = 8000
    sync_interval_minutes: int = 15

    # Lean Technologies
    lean_app_id: str = ""
    lean_client_secret: str = ""
    lean_app_token: str = ""
    lean_customer_id: str = ""
    lean_sandbox: bool = True

    # Revolut
    revolut_client_id: str = ""
    revolut_client_secret: str = ""
    revolut_refresh_token: str = ""

    # FinTS (Sparkasse)
    fints_blz: str = ""
    fints_login: str = ""
    fints_pin: str = ""
    fints_endpoint: str = ""

    # Emirates NBD Web Banking
    enbd_username: str = ""
    enbd_password: str = ""

    # Exchange rate API
    exchange_rate_api_url: str = "https://api.exchangerate.host/latest"

    model_config = {"env_prefix": "TMS_", "env_file": ".env"}


settings = Settings()
