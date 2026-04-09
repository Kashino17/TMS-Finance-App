"""Emirates NBD API Interceptor — captures transaction data from ENBD's own API calls.
Login via browser, click VIEW ALL, scroll to trigger API pagination,
and intercept all response bodies. No custom fetch() needed."""
import json
import re
from datetime import date, datetime
from playwright.sync_api import sync_playwright, Page
from tms.connectors.base import RawTransaction, AccountBalance

ENBD_LOGIN_URL = "https://online.emiratesnbd.com/"
SMART_PASS_TIMEOUT_MS = 600_000


class ENBDApiClient:
    def __init__(self, username: str, password: str):
        self.username = username
        self.password = password
        self._captured_transactions = []
        self._captured_accounts = []

    def sync(self, full_sync: bool = False, status_callback=None) -> tuple[list[AccountBalance], list[RawTransaction]]:
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

            # Intercept ALL API responses and capture transaction/account data
            page.on("response", lambda resp: self._intercept_response(resp))
            self._status_callback = status_callback

            def _update_status(msg):
                if self._status_callback:
                    self._status_callback(msg)

            try:
                _update_status("Opening Emirates NBD...")
                self._login(page)
                _update_status("Waiting for Smart Pass approval...")
                self._wait_for_smart_pass(page)

                _update_status("Logged in! Loading dashboard...")
                page.wait_for_timeout(5000)

                _update_status("Opening transactions...")
                try:
                    page.locator('text=/VIEW ALL/i').first.click()
                    page.wait_for_timeout(5000)
                except:
                    pass

                if full_sync:
                    _update_status("Full Backlog: scrolling through all transactions...")
                    self._scroll_for_api_calls(page)
                else:
                    _update_status("Quick Sync: loading recent transactions...")
                    page.wait_for_timeout(5000)  # Just wait for initial API calls

                # Parse captured data
                accounts = self._parse_captured_accounts()
                transactions = self._parse_captured_transactions()

                with open("/tmp/enbd_api_result.txt", "w") as f:
                    f.write(f"Captured {len(self._captured_accounts)} account responses\n")
                    f.write(f"Captured {len(self._captured_transactions)} transaction responses\n")
                    f.write(f"Parsed {len(accounts)} accounts, {len(transactions)} transactions\n")
                    if transactions:
                        f.write(f"Newest: {transactions[0].date}\nOldest: {transactions[-1].date}\n")

                return accounts, transactions
            finally:
                browser.close()

    def _intercept_response(self, response):
        """Capture API responses that contain account or transaction data."""
        try:
            url = response.url
            if response.status != 200:
                return

            # Capture account summary
            if "accounts/v1/summary" in url or "deposits/summary" in url:
                try:
                    body = response.json()
                    self._captured_accounts.append({"type": "account", "data": body})
                except:
                    pass

            # Capture credit cards
            if "creditcards/v1/list" in url:
                try:
                    body = response.json()
                    self._captured_accounts.append({"type": "card", "data": body})
                except:
                    pass

            # Capture transactions
            if "global-transactions" in url and "transactions" in url:
                try:
                    body = response.json()
                    self._captured_transactions.append(body)

                    # Log progress
                    total = sum(
                        len(r.get("transactions", r.get("results", [])))
                        for r in self._captured_transactions
                    )
                    with open("/tmp/enbd_api_progress.txt", "w") as f:
                        f.write(f"API pages: {len(self._captured_transactions)} | Raw txns: {total}")
                except:
                    pass

            # Capture loans
            if "loans/v2/list" in url:
                try:
                    body = response.json()
                    with open("/tmp/enbd_api_loans.txt", "w") as f:
                        f.write(json.dumps(body, indent=2, default=str)[:5000])
                except:
                    pass

        except:
            pass

    def _login(self, page: Page):
        page.goto(ENBD_LOGIN_URL, wait_until="domcontentloaded", timeout=60_000)
        page.wait_for_timeout(5000)
        try:
            page.wait_for_selector('input', timeout=30_000)
        except:
            for sel in ['button:has-text("Accept")', 'button:has-text("OK")', 'button:has-text("Close")']:
                try:
                    page.locator(sel).first.click(timeout=2000)
                    page.wait_for_timeout(1000)
                except:
                    pass
            page.wait_for_selector('input', timeout=15_000)

        page.locator('input[type="text"], input[type="email"]').first.fill(self.username)
        page.locator('input[type="password"]').first.fill(self.password)
        page.locator('button:has-text("LOGIN"), button:has-text("Login"), input[type="submit"]').first.click()

    def _wait_for_smart_pass(self, page: Page):
        try:
            page.wait_for_url(
                re.compile(r"(dashboard|accounts|home|overview)", re.IGNORECASE),
                timeout=SMART_PASS_TIMEOUT_MS,
            )
        except:
            try:
                page.wait_for_selector('text=/ACCOUNTS|RECENT TRANSACTIONS/i', timeout=10_000)
            except:
                raise TimeoutError("Smart Pass was not approved in time.")

    def _scroll_for_api_calls(self, page: Page):
        """Scroll to trigger ENBD's infinite scroll which makes API pagination calls.
        We capture the responses via the interceptor — no DOM parsing needed."""
        prev_count = 0
        stall_count = 0
        page.mouse.move(640, 400)

        for i in range(30000):
            # Scroll
            for _ in range(5):
                page.mouse.wheel(0, 600)
                page.wait_for_timeout(200)
            page.wait_for_timeout(3000)

            # Check how many transaction API responses we've captured
            current_count = len(self._captured_transactions)

            if current_count == prev_count:
                stall_count += 1
                if stall_count >= 8:
                    # Try harder
                    for _ in range(15):
                        page.mouse.wheel(0, 1500)
                        page.wait_for_timeout(400)
                    page.wait_for_timeout(8000)

                    if len(self._captured_transactions) == current_count:
                        # Try PageDown
                        for _ in range(20):
                            page.keyboard.press("PageDown")
                            page.wait_for_timeout(200)
                        page.wait_for_timeout(8000)

                        if len(self._captured_transactions) == current_count:
                            break  # No new API calls — we've reached the end
                    stall_count = 0
            else:
                stall_count = 0

            prev_count = current_count

            # Progress
            total_txns = sum(
                len(r.get("transactions", r.get("results", [])))
                for r in self._captured_transactions
            )
            with open("/tmp/enbd_api_progress.txt", "w") as f:
                f.write(f"Scroll {i} | API pages: {current_count} | Raw txns: {total_txns} | Stalls: {stall_count}")

            if self._status_callback:
                self._status_callback(f"Loading transactions... {total_txns} found")

    def _parse_captured_accounts(self) -> list[AccountBalance]:
        accounts = []
        for captured in self._captured_accounts:
            data = captured["data"]
            if captured["type"] == "account":
                for acc in data.get("accounts", data if isinstance(data, list) else []):
                    try:
                        accounts.append(AccountBalance(
                            external_id=str(acc.get("accountNumber", acc.get("id", ""))),
                            name=f"ENBD {acc.get('productName', acc.get('type', 'Account'))}",
                            currency=acc.get("currency", "AED"),
                            balance=float(acc.get("availableBalance", acc.get("balance", 0))),
                        ))
                    except:
                        pass
            elif captured["type"] == "card":
                for card in data.get("creditCards", data if isinstance(data, list) else []):
                    try:
                        accounts.append(AccountBalance(
                            external_id=str(card.get("cardNumber", card.get("id", ""))),
                            name=f"ENBD {card.get('productName', card.get('cardType', 'Card'))}",
                            currency=card.get("currency", "AED"),
                            balance=-abs(float(card.get("outstandingBalance", card.get("balance", 0)))),
                        ))
                    except:
                        pass

        # Save first response for debugging
        if self._captured_accounts:
            with open("/tmp/enbd_api_accounts_raw.txt", "w") as f:
                f.write(json.dumps(self._captured_accounts[0]["data"], indent=2, default=str)[:5000])

        return accounts

    def _parse_captured_transactions(self) -> list[RawTransaction]:
        all_txns = []
        for response_data in self._captured_transactions:
            txns = response_data.get("transactions", response_data.get("results", []))

            # Save first page for debugging
            if not all_txns and txns:
                with open("/tmp/enbd_api_txn_sample.txt", "w") as f:
                    f.write(json.dumps(txns[:3], indent=2, default=str)[:5000])

            for txn in txns:
                raw = self._parse_transaction(txn)
                if raw:
                    all_txns.append(raw)

        return all_txns

    def _parse_transaction(self, txn: dict) -> RawTransaction | None:
        try:
            amount = float(txn.get("amount", txn.get("transactionAmount", 0)))
            currency = txn.get("currencyCode", txn.get("currency", "AED"))

            # Date is timestamp in milliseconds
            date_val = txn.get("date", txn.get("transactionDate", txn.get("postingDate", "")))
            if isinstance(date_val, (int, float)) and date_val > 1000000000:
                txn_date = datetime.fromtimestamp(date_val / 1000).date()
            elif isinstance(date_val, str) and date_val:
                txn_date = date.fromisoformat(str(date_val)[:10])
            else:
                return None

            # Merchant name from purpose.extendedNarrations[0].title
            merchant = None
            purpose = txn.get("purpose", {})
            if isinstance(purpose, dict):
                narrations = purpose.get("extendedNarrations", [])
                if narrations and isinstance(narrations, list):
                    for n in narrations:
                        if n.get("languange") == "en" or not merchant:
                            merchant = n.get("title", "")
            if not merchant:
                merchant = txn.get("merchant", txn.get("merchantName", txn.get("description", "")))
                if isinstance(merchant, dict):
                    merchant = merchant.get("name", str(merchant))

            # Description from purpose subtitle
            description = ""
            if isinstance(purpose, dict):
                narrations = purpose.get("extendedNarrations", [])
                if narrations:
                    description = narrations[0].get("subTitle", "")
            if not description:
                description = txn.get("description", txn.get("narrative", txn.get("remarks", "")))

            # creditDebitIndicator: "DR" = debit (negative), "CR" = credit (positive)
            indicator = str(txn.get("creditDebitIndicator", txn.get("type", ""))).upper()
            if indicator == "DR" and amount > 0:
                amount = -amount
            elif indicator == "CR" and amount < 0:
                amount = abs(amount)

            external_id = str(txn.get("id", txn.get("transactionId", txn.get("referenceNumber", f"{txn_date}_{abs(amount)}"))))

            return RawTransaction(
                external_id=external_id,
                amount=amount,
                currency=currency,
                date=txn_date,
                merchant_name=str(merchant)[:200] if merchant else None,
                description=str(description)[:500] if description else None,
                raw_data=json.dumps(txn, default=str),
            )
        except:
            return None
