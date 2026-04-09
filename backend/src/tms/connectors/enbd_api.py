"""Emirates NBD Direct API Client — uses ENBD's internal API after browser login.
No scrolling needed — fetches ALL transactions via cursor-based pagination."""
import json
import re
from datetime import date, datetime
from playwright.sync_api import sync_playwright, Page
import httpx
from tms.connectors.base import RawTransaction, AccountBalance

ENBD_LOGIN_URL = "https://online.emiratesnbd.com/"
ENBD_API_BASE = "https://apionline.emiratesnbd.com"
SMART_PASS_TIMEOUT_MS = 600_000  # 10 minutes


class ENBDApiClient:
    def __init__(self, username: str, password: str):
        self.username = username
        self.password = password
        self.auth_token = None

    def sync(self, full_sync: bool = False) -> tuple[list[AccountBalance], list[RawTransaction]]:
        """Login via browser, capture auth token, then use API directly."""
        # Step 1: Browser login to get auth token
        self._login_and_capture_token()

        if not self.auth_token:
            raise Exception("Failed to capture auth token after login")

        # Step 2: Use API directly — no scrolling needed!
        accounts = self._fetch_accounts_api()
        transactions = self._fetch_all_transactions_api()

        return accounts, transactions

    def _login_and_capture_token(self):
        """Open browser, login, wait for Smart Pass, capture the auth token."""
        with sync_playwright() as p:
            browser = p.chromium.launch(
                headless=False,
                channel="chromium",
                args=["--disable-blink-features=AutomationControlled", "--no-sandbox"],
            )
            context = browser.new_context(
                user_agent="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                viewport={"width": 1280, "height": 800},
            )
            context.set_default_timeout(3600_000)
            context.set_default_navigation_timeout(120_000)
            context.add_init_script("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})")

            page = context.new_page()

            # Capture auth token from API responses
            def capture_token(response):
                try:
                    if "login-profile" in response.url and response.status == 200:
                        body = response.json()
                        if "access_token" in body:
                            self.auth_token = body["access_token"]
                            with open("/tmp/enbd_token.txt", "w") as f:
                                f.write(self.auth_token[:50] + "...")
                except:
                    pass

            page.on("response", capture_token)

            try:
                # Navigate to login
                page.goto(ENBD_LOGIN_URL, wait_until="domcontentloaded", timeout=60_000)
                page.wait_for_timeout(5000)

                # Fill login form
                try:
                    page.wait_for_selector('input', timeout=30_000)
                except:
                    # Retry with dismiss
                    for sel in ['button:has-text("Accept")', 'button:has-text("OK")', 'button:has-text("Close")']:
                        try:
                            page.locator(sel).first.click(timeout=2000)
                            page.wait_for_timeout(1000)
                        except:
                            pass
                    page.wait_for_selector('input', timeout=15_000)

                username_input = page.locator('input[type="text"], input[type="email"]').first
                username_input.fill(self.username)
                password_input = page.locator('input[type="password"]').first
                password_input.fill(self.password)
                login_btn = page.locator('button:has-text("LOGIN"), button:has-text("Login"), input[type="submit"]').first
                login_btn.click()

                # Wait for Smart Pass approval — token gets captured automatically
                page.wait_for_url(
                    re.compile(r"(dashboard|accounts|home|overview)", re.IGNORECASE),
                    timeout=SMART_PASS_TIMEOUT_MS,
                )
                page.wait_for_timeout(3000)  # Let all API calls complete

            finally:
                browser.close()

    def _api_headers(self) -> dict:
        return {
            "Authorization": f"Bearer {self.auth_token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        }

    def _fetch_accounts_api(self) -> list[AccountBalance]:
        """Fetch accounts via ENBD API."""
        accounts = []

        try:
            # Current/Savings accounts
            resp = httpx.get(
                f"{ENBD_API_BASE}/a/o/retail/accounts/v1/summary",
                headers=self._api_headers(),
                timeout=30,
            )
            if resp.status_code == 200:
                data = resp.json()
                for acc in data.get("accounts", data if isinstance(data, list) else []):
                    accounts.append(AccountBalance(
                        external_id=acc.get("accountNumber", acc.get("id", "")),
                        name=f"ENBD {acc.get('productName', acc.get('type', 'Account'))}",
                        currency=acc.get("currency", "AED"),
                        balance=float(acc.get("availableBalance", acc.get("balance", 0))),
                    ))
        except Exception as e:
            with open("/tmp/enbd_api_accounts_error.txt", "w") as f:
                f.write(str(e))

        try:
            # Credit cards
            resp = httpx.get(
                f"{ENBD_API_BASE}/a/o/retail/creditcards/v1/list",
                headers=self._api_headers(),
                timeout=30,
            )
            if resp.status_code == 200:
                data = resp.json()
                for card in data.get("creditCards", data if isinstance(data, list) else []):
                    accounts.append(AccountBalance(
                        external_id=card.get("cardNumber", card.get("id", "")),
                        name=f"ENBD {card.get('productName', card.get('cardType', 'Card'))}",
                        currency=card.get("currency", "AED"),
                        balance=-float(card.get("outstandingBalance", card.get("balance", 0))),
                    ))
        except Exception as e:
            with open("/tmp/enbd_api_cards_error.txt", "w") as f:
                f.write(str(e))

        # Save debug
        with open("/tmp/enbd_api_accounts.txt", "w") as f:
            for a in accounts:
                f.write(f"{a.name} | {a.currency} {a.balance}\n")

        return accounts

    def _fetch_all_transactions_api(self) -> list[RawTransaction]:
        """Fetch ALL transactions using cursor-based pagination. No scrolling needed!"""
        all_transactions = []
        cursor = ""
        page_num = 0
        page_size = 300  # First page can be large

        while True:
            page_num += 1
            url = f"{ENBD_API_BASE}/a/o/retail/global-transactions/v1/transactions"
            params = {
                "productType": "seeAll",
                "size": str(page_size),
                "tranStatus": "all",
            }
            if cursor:
                params["next"] = cursor

            try:
                resp = httpx.get(
                    url,
                    params=params,
                    headers=self._api_headers(),
                    timeout=30,
                )

                if resp.status_code != 200:
                    with open("/tmp/enbd_api_txn_error.txt", "w") as f:
                        f.write(f"Page {page_num}: HTTP {resp.status_code}\n{resp.text[:500]}")
                    break

                data = resp.json()

                # Extract transactions from response
                txns = data.get("transactions", data.get("results", []))
                if not txns:
                    break

                for txn in txns:
                    raw = self._parse_api_transaction(txn)
                    if raw:
                        all_transactions.append(raw)

                # Get next cursor
                cursor = data.get("next", data.get("nextCursor", ""))
                if not cursor:
                    break

                # After first page, use smaller page size
                page_size = 50

                # Progress log
                with open("/tmp/enbd_api_progress.txt", "w") as f:
                    oldest = all_transactions[-1].date if all_transactions else "?"
                    f.write(f"Page {page_num} | Total: {len(all_transactions)} | Oldest: {oldest}")

            except Exception as e:
                with open("/tmp/enbd_api_txn_error.txt", "w") as f:
                    f.write(f"Page {page_num}: {str(e)}")
                break

        with open("/tmp/enbd_api_result.txt", "w") as f:
            f.write(f"Total transactions: {len(all_transactions)}\n")
            if all_transactions:
                f.write(f"Newest: {all_transactions[0].date}\n")
                f.write(f"Oldest: {all_transactions[-1].date}\n")

        return all_transactions

    def _parse_api_transaction(self, txn: dict) -> RawTransaction | None:
        """Parse a transaction from the ENBD API response."""
        try:
            # Try multiple possible field names
            amount = float(txn.get("amount", txn.get("transactionAmount", 0)))
            currency = txn.get("currency", txn.get("transactionCurrency", "AED"))

            # Date parsing
            date_str = txn.get("date", txn.get("transactionDate", txn.get("postingDate", "")))
            if isinstance(date_str, (int, float)):
                # Timestamp in milliseconds
                txn_date = datetime.fromtimestamp(date_str / 1000).date()
            elif date_str:
                txn_date = date.fromisoformat(date_str[:10])
            else:
                return None

            # Merchant / Description
            merchant = txn.get("merchant", txn.get("merchantName", txn.get("description", txn.get("narrative", ""))))
            if isinstance(merchant, dict):
                merchant = merchant.get("name", str(merchant))
            description = txn.get("description", txn.get("narrative", txn.get("remarks", "")))

            # Debit/Credit
            txn_type = txn.get("type", txn.get("transactionType", "")).lower()
            if txn_type in ("debit", "dr") and amount > 0:
                amount = -amount

            external_id = txn.get("id", txn.get("transactionId", txn.get("referenceNumber", f"{txn_date}_{abs(amount)}")))

            return RawTransaction(
                external_id=str(external_id),
                amount=amount,
                currency=currency,
                date=txn_date,
                merchant_name=str(merchant)[:200] if merchant else None,
                description=str(description)[:500] if description else None,
                raw_data=json.dumps(txn, default=str),
            )
        except Exception as e:
            return None
