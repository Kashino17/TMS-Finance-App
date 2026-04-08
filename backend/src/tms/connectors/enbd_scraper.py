"""Emirates NBD Web Banking Scraper using Playwright."""
import json
import re
from datetime import date
from playwright.sync_api import sync_playwright, Page, TimeoutError as PwTimeout
from tms.connectors.base import RawTransaction, AccountBalance

ENBD_LOGIN_URL = "https://online.emiratesnbd.com/"
SMART_PASS_TIMEOUT_MS = 120_000  # 2 minutes to approve Smart Pass


class ENBDScraper:
    def __init__(self, username: str, password: str):
        self.username = username
        self.password = password

    def sync(self) -> tuple[list[AccountBalance], list[RawTransaction]]:
        """Login, wait for Smart Pass approval, scrape accounts + transactions."""
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            context = browser.new_context(
                user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                viewport={"width": 1280, "height": 800},
            )
            page = context.new_page()

            try:
                self._login(page)
                self._wait_for_smart_pass(page)
                accounts = self._scrape_accounts(page)
                transactions = self._scrape_transactions(page)
                return accounts, transactions
            finally:
                browser.close()

    def _login(self, page: Page):
        page.goto(ENBD_LOGIN_URL, wait_until="networkidle", timeout=30_000)
        page.wait_for_selector('input[type="text"], input[type="email"], input[name*="user"], input[placeholder*="Email"], input[placeholder*="Username"]', timeout=15_000)

        # Find and fill username field
        username_input = page.locator('input[type="text"], input[type="email"]').first
        username_input.fill(self.username)

        # Find and fill password field
        password_input = page.locator('input[type="password"]').first
        password_input.fill(self.password)

        # Click login button
        login_btn = page.locator('button:has-text("LOGIN"), button:has-text("Login"), button:has-text("Sign in"), input[type="submit"]').first
        login_btn.click()

    def _wait_for_smart_pass(self, page: Page):
        """Wait up to 2 minutes for user to approve Smart Pass on their phone."""
        # After login, the page shows "Authorise with the app" and waits.
        # Once approved, it redirects to the dashboard.
        # We wait for either the dashboard to load or a known dashboard element.
        try:
            page.wait_for_url(
                re.compile(r"(dashboard|accounts|home|overview)", re.IGNORECASE),
                timeout=SMART_PASS_TIMEOUT_MS,
            )
        except PwTimeout:
            # Try alternative: wait for any account-related content
            try:
                page.wait_for_selector(
                    'text=/balance|account|AED|Current Account/i',
                    timeout=10_000,
                )
            except PwTimeout:
                raise TimeoutError(
                    "Smart Pass was not approved within 2 minutes. "
                    "Please approve the notification in your ENBD X app and try again."
                )

    def _scrape_accounts(self, page: Page) -> list[AccountBalance]:
        accounts = []
        page.wait_for_timeout(3000)  # Let dashboard fully render

        # Try to get page content and parse account info
        content = page.content()

        # Look for account cards/sections on the dashboard
        # ENBD typically shows accounts with balance on the main page
        account_elements = page.locator('[class*="account"], [class*="Account"], [data-testid*="account"]').all()

        if not account_elements:
            # Fallback: try to find balance information in the page text
            text = page.inner_text('body')
            accounts = self._parse_accounts_from_text(text)
            if accounts:
                return accounts

            # Try clicking on "Accounts" menu item
            try:
                page.locator('a:has-text("Accounts"), button:has-text("Accounts"), [href*="account"]').first.click()
                page.wait_for_timeout(3000)
                text = page.inner_text('body')
                accounts = self._parse_accounts_from_text(text)
            except Exception:
                pass

            return accounts

        for elem in account_elements:
            try:
                text = elem.inner_text()
                account = self._parse_account_element(text)
                if account:
                    accounts.append(account)
            except Exception:
                continue

        return accounts

    def _parse_accounts_from_text(self, text: str) -> list[AccountBalance]:
        """Parse account info from raw page text using regex."""
        accounts = []
        # Pattern: Look for AED followed by amount
        balance_pattern = re.compile(
            r'(Current Account|Savings Account|Account\s*\w*)\s*.*?'
            r'(AED|USD|EUR)\s*([\d,]+\.?\d*)',
            re.IGNORECASE | re.DOTALL,
        )
        for match in balance_pattern.finditer(text):
            name = match.group(1).strip()
            currency = match.group(2).strip()
            amount_str = match.group(3).replace(',', '')
            try:
                balance = float(amount_str)
                accounts.append(AccountBalance(
                    external_id=f"enbd_{name.lower().replace(' ', '_')}",
                    name=f"ENBD {name}",
                    currency=currency,
                    balance=balance,
                ))
            except ValueError:
                continue
        return accounts

    def _parse_account_element(self, text: str) -> AccountBalance | None:
        """Parse a single account element's text."""
        amount_match = re.search(r'(AED|USD|EUR)\s*([\d,]+\.?\d*)', text)
        if not amount_match:
            return None

        currency = amount_match.group(1)
        balance = float(amount_match.group(2).replace(',', ''))

        name_match = re.search(r'(Current|Savings|Fixed Deposit|Account)\s*\w*', text, re.IGNORECASE)
        name = name_match.group(0) if name_match else "Account"

        return AccountBalance(
            external_id=f"enbd_{name.lower().replace(' ', '_')}",
            name=f"ENBD {name}",
            currency=currency,
            balance=balance,
        )

    def _scrape_transactions(self, page: Page) -> list[RawTransaction]:
        transactions = []

        # Try to navigate to transaction history
        try:
            page.locator('a:has-text("Transaction"), button:has-text("Transaction"), a:has-text("History"), [href*="transaction"]').first.click()
            page.wait_for_timeout(3000)
        except Exception:
            pass  # May already be on a page with transactions

        text = page.inner_text('body')
        transactions = self._parse_transactions_from_text(text)

        # If no transactions found, try looking for a table
        if not transactions:
            try:
                rows = page.locator('table tbody tr, [class*="transaction-row"], [class*="TransactionRow"]').all()
                for row in rows:
                    row_text = row.inner_text()
                    txn = self._parse_transaction_row(row_text)
                    if txn:
                        transactions.append(txn)
            except Exception:
                pass

        return transactions

    def _parse_transactions_from_text(self, text: str) -> list[RawTransaction]:
        """Parse transactions from raw page text."""
        transactions = []
        # Pattern: date, description, amount
        # ENBD format varies, but typically: DD/MM/YYYY or DD MMM YYYY, description, +/-amount
        txn_pattern = re.compile(
            r'(\d{1,2}[/\-]\d{1,2}[/\-]\d{2,4}|\d{1,2}\s+\w{3}\s+\d{4})\s+'
            r'(.+?)\s+'
            r'([+-]?\s*(?:AED|USD|EUR)?\s*[\d,]+\.?\d*)',
            re.IGNORECASE,
        )
        for match in txn_pattern.finditer(text):
            try:
                date_str = match.group(1).strip()
                description = match.group(2).strip()
                amount_str = match.group(3).strip()

                txn_date = self._parse_date(date_str)
                amount = self._parse_amount(amount_str)

                if txn_date and amount is not None:
                    transactions.append(RawTransaction(
                        external_id=f"enbd_{txn_date.isoformat()}_{abs(amount):.2f}",
                        amount=amount,
                        currency="AED",
                        date=txn_date,
                        merchant_name=description[:200] if description else None,
                        description=description,
                        raw_data=json.dumps({
                            "date": date_str,
                            "description": description,
                            "amount": amount_str,
                        }),
                    ))
            except Exception:
                continue

        return transactions

    def _parse_transaction_row(self, text: str) -> RawTransaction | None:
        """Parse a single transaction table row."""
        lines = [l.strip() for l in text.split('\n') if l.strip()]
        if len(lines) < 2:
            return None

        date_str = lines[0]
        description = lines[1] if len(lines) > 1 else ""
        amount_str = lines[-1] if len(lines) > 2 else lines[1]

        txn_date = self._parse_date(date_str)
        amount = self._parse_amount(amount_str)

        if txn_date and amount is not None:
            return RawTransaction(
                external_id=f"enbd_{txn_date.isoformat()}_{abs(amount):.2f}",
                amount=amount,
                currency="AED",
                date=txn_date,
                merchant_name=description[:200] if description else None,
                description=description,
                raw_data=json.dumps({"text": text}),
            )
        return None

    @staticmethod
    def _parse_date(s: str) -> date | None:
        import datetime as dt
        for fmt in ("%d/%m/%Y", "%d-%m-%Y", "%d %b %Y", "%d/%m/%y", "%d-%m-%y"):
            try:
                return dt.datetime.strptime(s.strip(), fmt).date()
            except ValueError:
                continue
        return None

    @staticmethod
    def _parse_amount(s: str) -> float | None:
        cleaned = re.sub(r'[^\d.,-]', '', s.replace(',', ''))
        try:
            return float(cleaned)
        except ValueError:
            return None
