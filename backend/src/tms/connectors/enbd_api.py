"""Emirates NBD Direct API Client — makes API calls from within the browser session.
No scrolling needed — fetches ALL transactions via cursor-based pagination."""
import json
from datetime import date, datetime
from playwright.sync_api import sync_playwright, Page
import re
from tms.connectors.base import RawTransaction, AccountBalance

ENBD_LOGIN_URL = "https://online.emiratesnbd.com/"
SMART_PASS_TIMEOUT_MS = 600_000  # 10 minutes


class ENBDApiClient:
    def __init__(self, username: str, password: str):
        self.username = username
        self.password = password

    def sync(self, full_sync: bool = False) -> tuple[list[AccountBalance], list[RawTransaction]]:
        """Login via browser, then use fetch() from browser to call ENBD API directly."""
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

            try:
                self._login(page)
                self._wait_for_smart_pass(page)

                # Now logged in — make API calls from the browser
                accounts = self._fetch_accounts(page)
                transactions = self._fetch_all_transactions(page)

                return accounts, transactions
            finally:
                browser.close()

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

        username_input = page.locator('input[type="text"], input[type="email"]').first
        username_input.fill(self.username)
        password_input = page.locator('input[type="password"]').first
        password_input.fill(self.password)
        login_btn = page.locator('button:has-text("LOGIN"), button:has-text("Login"), input[type="submit"]').first
        login_btn.click()

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
        page.wait_for_timeout(5000)  # Let dashboard fully load

    def _browser_fetch(self, page: Page, url: str) -> dict | list | None:
        """Make a fetch() call from within the browser — uses the browser's session cookies & auth."""
        result = page.evaluate(f"""
            async () => {{
                try {{
                    const resp = await fetch("{url}");
                    if (!resp.ok) return {{ _error: resp.status, _body: await resp.text() }};
                    return await resp.json();
                }} catch (e) {{
                    return {{ _error: "fetch_failed", _body: e.message }};
                }}
            }}
        """)
        if isinstance(result, dict) and "_error" in result:
            with open("/tmp/enbd_api_fetch_error.txt", "a") as f:
                f.write(f"{url} → {result['_error']}: {str(result.get('_body', ''))[:200]}\n")
            return None
        return result

    def _fetch_accounts(self, page: Page) -> list[AccountBalance]:
        accounts = []

        # Current/Savings accounts
        data = self._browser_fetch(page, "https://apionline.emiratesnbd.com/a/o/retail/accounts/v1/summary")
        if data:
            with open("/tmp/enbd_api_accounts_raw.txt", "w") as f:
                f.write(json.dumps(data, indent=2, default=str)[:5000])
            accts = data.get("accounts", data if isinstance(data, list) else [])
            for acc in accts:
                try:
                    accounts.append(AccountBalance(
                        external_id=str(acc.get("accountNumber", acc.get("id", ""))),
                        name=f"ENBD {acc.get('productName', acc.get('type', 'Account'))}",
                        currency=acc.get("currency", "AED"),
                        balance=float(acc.get("availableBalance", acc.get("balance", 0))),
                    ))
                except:
                    pass

        # Credit cards
        data = self._browser_fetch(page, "https://apionline.emiratesnbd.com/a/o/retail/creditcards/v1/list")
        if data:
            with open("/tmp/enbd_api_cards_raw.txt", "w") as f:
                f.write(json.dumps(data, indent=2, default=str)[:5000])
            cards = data.get("creditCards", data if isinstance(data, list) else [])
            for card in cards:
                try:
                    accounts.append(AccountBalance(
                        external_id=str(card.get("cardNumber", card.get("id", ""))),
                        name=f"ENBD {card.get('productName', card.get('cardType', 'Card'))}",
                        currency=card.get("currency", "AED"),
                        balance=-abs(float(card.get("outstandingBalance", card.get("balance", 0)))),
                    ))
                except:
                    pass

        with open("/tmp/enbd_api_accounts_result.txt", "w") as f:
            for a in accounts:
                f.write(f"{a.name} | {a.currency} {a.balance}\n")

        return accounts

    def _fetch_all_transactions(self, page: Page) -> list[RawTransaction]:
        """Fetch ALL transactions using cursor-based pagination via browser fetch()."""
        all_transactions = []
        cursor = ""
        page_num = 0

        # Clear error log
        with open("/tmp/enbd_api_fetch_error.txt", "w") as f:
            f.write("")

        while True:
            page_num += 1
            size = 300 if page_num == 1 else 50

            url = f"https://apionline.emiratesnbd.com/a/o/retail/global-transactions/v1/transactions?productType=seeAll&size={size}&tranStatus=all"
            if cursor:
                url += f"&next={cursor}"

            data = self._browser_fetch(page, url)
            if not data:
                break

            # Save first page for debugging
            if page_num == 1:
                with open("/tmp/enbd_api_txn_sample.txt", "w") as f:
                    f.write(json.dumps(data, indent=2, default=str)[:10000])

            # Extract transactions
            txns = data.get("transactions", data.get("results", []))
            if not txns:
                break

            for txn in txns:
                raw = self._parse_transaction(txn)
                if raw:
                    all_transactions.append(raw)

            # Get next cursor
            cursor = data.get("next", data.get("nextCursor", ""))
            if not cursor:
                break

            # Progress
            with open("/tmp/enbd_api_progress.txt", "w") as f:
                oldest = all_transactions[-1].date if all_transactions else "?"
                f.write(f"Page {page_num} | Total: {len(all_transactions)} | Oldest: {oldest}")

        with open("/tmp/enbd_api_result.txt", "w") as f:
            f.write(f"Total: {len(all_transactions)}\n")
            if all_transactions:
                f.write(f"Newest: {all_transactions[0].date}\nOldest: {all_transactions[-1].date}\n")

        return all_transactions

    def _parse_transaction(self, txn: dict) -> RawTransaction | None:
        try:
            amount = float(txn.get("amount", txn.get("transactionAmount", 0)))
            currency = txn.get("currency", txn.get("transactionCurrency", "AED"))

            date_str = txn.get("date", txn.get("transactionDate", txn.get("postingDate", "")))
            if isinstance(date_str, (int, float)):
                txn_date = datetime.fromtimestamp(date_str / 1000).date()
            elif date_str:
                txn_date = date.fromisoformat(str(date_str)[:10])
            else:
                return None

            merchant = txn.get("merchant", txn.get("merchantName", txn.get("description", txn.get("narrative", ""))))
            if isinstance(merchant, dict):
                merchant = merchant.get("name", str(merchant))
            description = txn.get("description", txn.get("narrative", txn.get("remarks", "")))

            txn_type = str(txn.get("type", txn.get("transactionType", ""))).lower()
            if txn_type in ("debit", "dr") and amount > 0:
                amount = -amount

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
