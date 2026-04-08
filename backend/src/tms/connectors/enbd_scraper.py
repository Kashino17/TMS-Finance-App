"""Emirates NBD Web Banking Scraper using Playwright."""
import json
import re
from datetime import date, datetime
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
            browser = p.chromium.launch(
                headless=False,
                args=["--disable-blink-features=AutomationControlled", "--no-sandbox"],
            )
            context = browser.new_context(
                user_agent="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                viewport={"width": 1280, "height": 800},
            )
            context.add_init_script("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})")
            page = context.new_page()

            try:
                self._login(page)
                self._wait_for_smart_pass(page)

                # Wait for dashboard to fully render
                page.wait_for_timeout(5000)

                # Get full page text
                text = page.inner_text("body")

                # Save debug
                page.screenshot(path="/tmp/enbd_dashboard.png")
                with open("/tmp/enbd_dashboard_text.txt", "w") as f:
                    f.write(text)

                accounts = self._parse_accounts(text)
                transactions = self._parse_transactions(text, page)

                return accounts, transactions
            finally:
                browser.close()

    def _login(self, page: Page):
        page.goto(ENBD_LOGIN_URL, wait_until="domcontentloaded", timeout=60_000)
        page.wait_for_timeout(3000)
        page.wait_for_selector(
            'input[type="text"], input[type="email"], input[name*="user"], input[placeholder*="Email"], input[placeholder*="Username"]',
            timeout=30_000,
        )
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
        except PwTimeout:
            try:
                page.wait_for_selector('text=/ACCOUNTS|RECENT TRANSACTIONS/i', timeout=10_000)
            except PwTimeout:
                raise TimeoutError("Smart Pass was not approved within 2 minutes.")

    def _parse_accounts(self, text: str) -> list[AccountBalance]:
        """Parse account balances from dashboard text.

        ENBD splits numbers across lines like:
            ACCOUNTS
            ⃃
            15
            .184,07

        So '15' + '.184,07' = '15.184,07' = 15184.07 AED
        """
        accounts = []
        lines = [l.strip() for l in text.split('\n') if l.strip()]

        def find_balance_after(keyword: str) -> float | None:
            """Find a balance amount in the lines following a keyword."""
            for i, line in enumerate(lines):
                if keyword in line.upper():
                    # Collect next few lines and try to reconstruct the number
                    # Pattern: optional ⃃, then digits possibly split across lines
                    parts = []
                    for j in range(i + 1, min(i + 8, len(lines))):
                        l = lines[j].strip()
                        if l == '⃃' or l == 'AED':
                            continue
                        if re.match(r'^[\d.,-]+$', l):
                            parts.append(l)
                        elif parts:
                            break  # We had number parts and hit non-number, stop
                        elif l.upper() in ('CARDS', 'LOAN', 'QUICK ACTIONS', 'RECENT'):
                            break
                    if parts:
                        combined = ''.join(parts)  # e.g. "15" + ".184,07" = "15.184,07"
                        return self._parse_european_amount(combined)
            return None

        # Parse account balance
        acct_balance = find_balance_after('ACCOUNTS')
        if acct_balance is not None:
            accounts.append(AccountBalance(
                external_id="enbd_current",
                name="Emirates NBD Current Account",
                currency="AED",
                balance=acct_balance,
            ))

        # Parse card balance
        card_balance = find_balance_after('CARDS')
        if card_balance is not None:
            accounts.append(AccountBalance(
                external_id="enbd_card",
                name="Emirates NBD Visa Flexi",
                currency="AED",
                balance=-card_balance,  # Card balance is debt
            ))

        return accounts

    def _parse_transactions(self, text: str, page: Page) -> list[RawTransaction]:
        """Parse transactions from the RECENT TRANSACTIONS section.

        ENBD format on dashboard:
        Today/Yesterday/Apr 06, 2026
        Merchant Name
        Payment Method
        - ⃃ amount
        """
        transactions = []

        # Click "VIEW ALL" to go to full transaction list
        try:
            view_all = page.locator('text=/VIEW ALL/i').first
            view_all.click()
            page.wait_for_timeout(3000)
        except Exception:
            pass

        # Infinite scroll until all transactions are loaded
        text = self._scroll_to_load_all(page)

        with open("/tmp/enbd_after_scrape_text.txt", "w") as f:
            f.write(text)
        page.screenshot(path="/tmp/enbd_after_scrape.png")

        lines = text.split('\n')
        lines = [l.strip() for l in lines if l.strip()]

        # Find RECENT TRANSACTIONS section
        start_idx = None
        for i, line in enumerate(lines):
            if 'RECENT TRANSACTIONS' in line.upper() or 'TRANSACTION' in line.upper() and 'AMOUNT' in lines[min(i+2, len(lines)-1)].upper():
                start_idx = i
                break

        if start_idx is None:
            return transactions

        # Parse transaction entries
        current_date = None
        i = start_idx + 1

        # Skip header row (DATE, TRANSACTION, PAYMENT METHOD, AMOUNT)
        while i < len(lines) and lines[i].upper() in ('DATE', 'TRANSACTION', 'PAYMENT METHOD', 'AMOUNT', 'VIEW ALL'):
            i += 1

        while i < len(lines):
            line = lines[i]

            # Check if this is a date line
            parsed_date = self._try_parse_date(line)
            if parsed_date:
                current_date = parsed_date
                i += 1
                continue

            # Check if this is an end marker
            if line.upper() in ('ACTIVITIES', 'MESSAGES', 'QUICK ACTIONS', 'LOAN', 'EDIT'):
                break

            # Skip "Convert to installments" lines
            if 'convert to installments' in line.lower():
                i += 1
                continue

            # This should be a merchant name
            merchant = line
            payment_method = None
            amount = None

            # Next line: payment method (e.g. "Visa Flexi", "PRIVAT ACCOUNT")
            if i + 1 < len(lines):
                next_line = lines[i + 1]
                if not self._is_amount_line(next_line) and not self._try_parse_date(next_line) and 'convert to installments' not in next_line.lower():
                    payment_method = next_line
                    i += 1

            # Next lines: look for amount (might be split across lines like "-\n⃃\n2\n.647,99")
            amount_parts = []
            while i + 1 < len(lines):
                i += 1
                next_line = lines[i]
                if next_line in ('-', '+', '⃃') or self._is_amount_fragment(next_line):
                    amount_parts.append(next_line)
                elif 'convert to installments' in next_line.lower():
                    continue  # Skip this line but keep looking
                else:
                    break

            if amount_parts:
                amount = self._parse_amount_parts(amount_parts)

            if current_date and merchant and amount is not None:
                transactions.append(RawTransaction(
                    external_id=f"enbd_{current_date.isoformat()}_{merchant[:20]}_{abs(amount):.2f}",
                    amount=amount,
                    currency="AED",
                    date=current_date,
                    merchant_name=merchant,
                    description=f"{merchant} ({payment_method})" if payment_method else merchant,
                    raw_data=json.dumps({
                        "date": current_date.isoformat(),
                        "merchant": merchant,
                        "payment_method": payment_method,
                        "amount": amount,
                    }),
                ))
            else:
                # Didn't match pattern, skip to next
                i += 1
                continue

        return transactions

    def _scroll_to_load_all(self, page: Page) -> str:
        """Scroll the transaction list to load ALL transactions via infinite scroll.

        Key insight: ENBD's infinite scroll triggers when you scroll inside a specific
        container, not the window. We simulate real mouse wheel scrolling in the center
        of the page, exactly like a user would do manually.
        """
        max_scrolls = 3000
        prev_line_count = 0
        stall_count = 0

        # Move mouse to center of page (where the transaction list is)
        page.mouse.move(640, 400)
        page.wait_for_timeout(1000)

        for i in range(max_scrolls):
            # Simulate real mouse wheel scroll — small increments like a human
            for _ in range(5):
                page.mouse.wheel(0, 600)
                page.wait_for_timeout(200)

            # Wait for ENBD to load the next batch
            page.wait_for_timeout(3000)

            # Count lines to see if new content loaded
            current_text = page.inner_text("body")
            current_lines = current_text.count('\n')

            # Do NOT check for "no more transactions" — it may be in DOM but hidden
            # Only stop when content stops growing

            if current_lines == prev_line_count:
                stall_count += 1
                if stall_count >= 3:
                    # Stalled — try multiple recovery methods
                    recovered = False

                    # Method 1: Aggressive mouse wheel
                    for _ in range(15):
                        page.mouse.wheel(0, 1500)
                        page.wait_for_timeout(400)
                    page.wait_for_timeout(8000)
                    current_text = page.inner_text("body")
                    current_lines = current_text.count('\n')
                    if current_lines > prev_line_count:
                        recovered = True

                    # Method 2: Page Down spam
                    if not recovered:
                        for _ in range(20):
                            page.keyboard.press("PageDown")
                            page.wait_for_timeout(200)
                        page.wait_for_timeout(8000)
                        current_text = page.inner_text("body")
                        current_lines = current_text.count('\n')
                        if current_lines > prev_line_count:
                            recovered = True

                    # Method 3: Scroll specific containers
                    if not recovered:
                        page.evaluate("""
                            document.querySelectorAll('*').forEach(el => {
                                if (el.scrollHeight > el.clientHeight + 50 && el.clientHeight > 100) {
                                    el.scrollTop = el.scrollHeight;
                                }
                            });
                        """)
                        page.wait_for_timeout(8000)
                        current_text = page.inner_text("body")
                        current_lines = current_text.count('\n')
                        if current_lines > prev_line_count:
                            recovered = True

                    # Method 4: Move mouse to different position and scroll
                    if not recovered:
                        page.mouse.move(640, 600)
                        for _ in range(15):
                            page.mouse.wheel(0, 1000)
                            page.wait_for_timeout(300)
                        page.wait_for_timeout(8000)
                        page.mouse.move(640, 400)  # Move back
                        current_text = page.inner_text("body")
                        current_lines = current_text.count('\n')
                        if current_lines > prev_line_count:
                            recovered = True

                    if not recovered:
                        # Give it one final long wait — maybe the server is slow
                        page.wait_for_timeout(15000)
                        current_text = page.inner_text("body")
                        current_lines = current_text.count('\n')
                        if current_lines == prev_line_count:
                            break  # Truly done after exhausting all methods

                    stall_count = 0
            else:
                stall_count = 0

            prev_line_count = current_lines

            # Log progress
            with open("/tmp/enbd_scroll_progress.txt", "w") as f:
                # Count dates to estimate transaction count
                date_count = sum(1 for line in current_text.split('\n')
                                 if any(m in line for m in ['Jan ', 'Feb ', 'Mar ', 'Apr ', 'May ', 'Jun ',
                                                             'Jul ', 'Aug ', 'Sep ', 'Oct ', 'Nov ', 'Dec ',
                                                             'Today', 'Yesterday']))
                f.write(f"Scroll {i} | Lines: {current_lines} | ~{date_count} transactions | Stalls: {stall_count}")

        return page.inner_text("body")

    def _scrape_full_history(self, page: Page) -> list[RawTransaction]:
        """Explore ENBD portal to find older transactions / statements.
        IMPORTANT: ENBD is a SPA — never use page.goto(), only click within the app.
        We explore from the current page (transaction list) using the top nav tabs."""
        all_txns = []
        step = 1

        def save_state(label: str):
            nonlocal step
            try:
                page.screenshot(path=f"/tmp/enbd_explore_{step:02d}_{label}.png")
                with open(f"/tmp/enbd_explore_{step:02d}_{label}.txt", "w") as f:
                    f.write(page.inner_text("body"))
                with open(f"/tmp/enbd_explore_{step:02d}_{label}_url.txt", "w") as f:
                    f.write(page.url)
            except:
                pass
            step += 1

        try:
            # Step 1: Save current state (we're on the transaction list after VIEW ALL)
            save_state("current")

            # Collect all clickable elements
            clickables = page.locator('a, button, [role="button"], [role="tab"], [role="menuitem"], nav a, nav button, [class*="nav"], [class*="menu"], [class*="tab"]').all()
            link_info = []
            for elem in clickables[:100]:
                try:
                    txt = elem.inner_text(timeout=500).strip().replace('\n', ' ')[:100]
                    tag = elem.evaluate("el => el.tagName")
                    cls = elem.get_attribute("class") or ""
                    href = elem.get_attribute("href") or ""
                    role = elem.get_attribute("role") or ""
                    if txt:
                        link_info.append(f"[{tag}] '{txt}' | class={cls[:80]} | href={href} | role={role}")
                except:
                    pass
            with open("/tmp/enbd_explore_clickables.txt", "w") as f:
                f.write("\n".join(link_info))

            # Step 2: Click "Home" in the top navigation to get back to dashboard
            try:
                home_nav = page.locator('nav >> text=/Home/i, [class*="nav"] >> text=/Home/i, a >> text=/Home/i').first
                home_nav.click()
                page.wait_for_timeout(3000)
                save_state("home")
            except:
                pass

            # Step 3: Try clicking on the account area (balance card) on dashboard
            for selector in [
                '[class*="account-card"]',
                '[class*="AccountCard"]',
                '[class*="account-widget"]',
                'div:has-text("ACCOUNTS") >> [class*="card"]',
                'div:has-text("ACCOUNTS") >> [class*="widget"]',
            ]:
                try:
                    elem = page.locator(selector).first
                    if elem.is_visible(timeout=1000):
                        elem.click()
                        page.wait_for_timeout(3000)
                        save_state("account_detail")
                        break
                except:
                    continue

            # Step 4: Look for statements from wherever we are
            for keyword in ['Statement', 'History', 'Download', 'Export', 'E-Statement']:
                try:
                    elem = page.locator(f'text=/{keyword}/i').first
                    if elem.is_visible(timeout=1000):
                        elem.click()
                        page.wait_for_timeout(3000)
                        save_state(f"found_{keyword.lower()}")
                except:
                    continue

            # Step 5: Go to Services tab
            try:
                page.locator('nav >> text=/Services/i, [class*="nav"] >> text=/Services/i').first.click()
                page.wait_for_timeout(3000)
                save_state("services")

                # Look for statement-related items in services
                for keyword in ['Statement', 'History', 'Download', 'Certificate', 'E-Statement']:
                    try:
                        elem = page.locator(f'text=/{keyword}/i').first
                        if elem.is_visible(timeout=1000):
                            elem.click()
                            page.wait_for_timeout(3000)
                            save_state(f"services_{keyword.lower()}")
                    except:
                        continue
            except:
                pass

        except Exception as e:
            with open("/tmp/enbd_history_error.txt", "w") as f:
                f.write(f"{type(e).__name__}: {str(e)}")

        return all_txns

    def _try_parse_date(self, s: str) -> date | None:
        s = s.strip()
        today = date.today()

        if s.lower() == 'today':
            return today
        if s.lower() == 'yesterday':
            return today.replace(day=today.day - 1) if today.day > 1 else today

        # "Apr 06, 2026" format
        for fmt in ("%b %d, %Y", "%B %d, %Y", "%d %b %Y", "%d/%m/%Y", "%d-%m-%Y"):
            try:
                return datetime.strptime(s, fmt).date()
            except ValueError:
                continue
        return None

    def _is_amount_line(self, s: str) -> bool:
        return bool(re.match(r'^[+-]?\s*[⃃]?\s*[\d.,]+$', s.strip()))

    def _is_amount_fragment(self, s: str) -> bool:
        s = s.strip()
        return bool(re.match(r'^[\d.,]+$', s)) or s in ('-', '+', '⃃')

    def _parse_amount_parts(self, parts: list[str]) -> float | None:
        """Parse amount from parts like ['-', '⃃', '21,95'] or ['⃃', '100,60']."""
        combined = ' '.join(p.strip() for p in parts)
        is_negative = '-' in combined

        # Extract number
        number_match = re.search(r'(\d[\d.]*,\d{2})', combined)
        if number_match:
            amount = self._parse_european_amount(number_match.group(1))
            if amount is not None:
                return -abs(amount) if is_negative else amount

        # Try simple number
        number_match = re.search(r'(\d+\.?\d*)', combined)
        if number_match:
            try:
                amount = float(number_match.group(1))
                return -abs(amount) if is_negative else amount
            except ValueError:
                pass

        return None

    @staticmethod
    def _parse_european_amount(s: str) -> float | None:
        """Parse European format: 15.184,07 → 15184.07"""
        try:
            cleaned = s.strip().replace('.', '').replace(',', '.')
            return float(cleaned)
        except ValueError:
            return None

    def _extract_amount_from_line(self, line: str) -> float | None:
        """Try to extract an amount from a single line."""
        # Remove currency symbols
        cleaned = line.replace('⃃', '').strip()
        if not cleaned:
            return None
        return self._parse_european_amount(cleaned) if ',' in cleaned else None
