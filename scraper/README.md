# Freebox Scraper Worker

Server-side Python worker that powers the Freebox free-item flipping app's listing feed.

Runs on your server (or a cron job), uses a self-managed headless Chromium browser (Playwright) to scrape free listings per US zip, normalizes and value-filters them, dedupes cross-source near-duplicates, and upserts into Supabase — which the mobile app reads from.

**Never run on a phone or client device.** This worker uses a Supabase SERVICE ROLE key (bypasses RLS) and consumes local CPU/memory for Chromium.

---

## Architecture

```
worker.py (entrypoint / async orchestrator)
│
├─ config.py          — load & validate .env
├─ browser.py         — async Playwright session manager (anti-detect, capture_graphql)
├─ supabase_io.py     — PostgREST CRUD (get_due_targets, upsert_listings, mark_*)
├─ valuation.py       — category baseline + keyword-multiplier resale heuristic
├─ dedupe.py          — cross-source near-duplicate removal (difflib SequenceMatcher)
└─ adapters/
   ├─ base.py         — SourceAdapter ABC + NormalizedListing dataclass
   ├─ facebook.py     — GraphQL interception, micro-query cap-bypass strategy
   ├─ craigslist.py   — browser-free direct HTTP scrape (BeautifulSoup)
   └─ offerup.py      — Playwright network interception (Next.js SPA)
```

### Why DIY instead of Apify actors?

Apify actors are community-maintained third parties that can break, go paid-only, or change field names without notice.  This worker owns the full scraping stack:

- No external API billing — Chromium runs locally (or on your server).
- Field mapping failures are debuggable locally with `--log-level DEBUG`.
- The guest-context approach (no login) keeps sessions disposable and reduces ban risk compared to logged-in actor accounts.

### Pipeline flow per pass

1. `supabase_io.get_due_targets()` — pull rows from `crawl_targets` where `active=true` and `next_due_at <= now`
2. Launch ONE shared `BrowserSession` (one Chromium process for the whole pass)
3. For each target: `adapter.fetch(zip, browser)` — each adapter gets a fresh, cookieless browser **context**
4. Valuation: `valuation.estimate(category, title)` → drop if below `MIN_PROFIT`
5. `dedupe.dedupe_batch()` — collapse cross-source near-duplicates by fuzzy title matching within same zip
6. `supabase_io.upsert_listings()` — POST with `on_conflict=source,source_listing_id`
7. `supabase_io.mark_crawled()` or `mark_error()` — update `crawl_targets` with next schedule
8. Browser closed

### Facebook Marketplace: micro-query cap-bypass

Facebook caps a single Marketplace search page at ~500–1 000 visible items.  To work around this, the Facebook adapter splits one macro-search into micro-queries — one per top-level category slug (`furniture`, `electronics`, `toys-games`, etc., ~20 total) — each with `maxPrice=0` and a 1-mile radius.  Each micro-query pool is well under the cap, and the union covers the full free-stuff inventory.  Cross-category duplicates are collapsed by `dedupe.py`.

The trade-off is ~20 page loads per zip target.  With 3 scroll passes each and 2–7 s human delays, expect 2–4 minutes per zip.

### Guest-session / anti-ban design

- **No login.** Every context is cookieless.  Logged-in scraping produces faster bans.
- **Anti-detect init script.** Masks `navigator.webdriver`, spoofs a realistic plugins list and canvas fingerprint.  Applied to every page via `add_init_script`.
- **Human telemetry.** Random 2–7 s delays between navigations and scroll passes.
- **Residential proxy** (via `PROXY_URL`): strongly recommended for Facebook.  Without it, a data-centre IP will be rate-limited quickly.

---

## ⚠️ First-Run Verification Required

**Every adapter has `# ⚠️ VERIFY` comments** marking fields that are best-effort guesses.  The following items MUST be confirmed with a real run before relying on production data.

### 1. Facebook category URL slugs

`adapters/facebook.py` → `_FB_CATEGORY_SLUGS` — the list of Marketplace category slug strings used in URLs.  Open `facebook.com/marketplace` in a browser, click each top-level category, and copy the slug from the URL.

### 2. Facebook zip → location binding

`_build_category_url()` currently appends `&location={zip_code}`.  Facebook may require a numeric `place_id` instead of a raw zip.  To find your city's `place_id`, open any FB Marketplace search and inspect the GraphQL request variables in DevTools Network tab.

### 3. Facebook GraphQL JSON paths

`_extract_nodes()` in `adapters/facebook.py` — the path from the top of the JSON response to the array of listing nodes.  Confirm by running `--log-level DEBUG` and reading the raw payloads logged as `"Facebook raw payload"`.

`_parse_node()` — all field paths (ID, title, price, location, image, etc.).

### 4. Craigslist zip → subdomain mapping

`adapters/craigslist.py` → `_ZIP_TO_SUBDOMAIN` — the 3-digit zip-prefix → Craigslist subdomain dict.  Extend it for your target markets.  The default fallback is `sfbay`, which is wrong for most US zips.

### 5. Craigslist HTML selectors

`_parse_html()` and `_parse_row()` — CSS selectors for listing rows, title, location, date, and image.  Confirm by inspecting raw HTML logged at DEBUG level.

### 6. OfferUp API endpoints and response shape

`adapters/offerup.py` → `_build_search_url()`, `_extract_items()`, `_parse_item()`.  OfferUp's internal API is undocumented.  Capture real responses at DEBUG level and update all field paths.

---

## Terms of Service

Scraping Facebook Marketplace, Craigslist, and OfferUp may violate their Terms of Service.  Assess your risk tolerance before running in production.  Use responsibly.  A residential proxy (`PROXY_URL`) is strongly recommended for Facebook to reduce account/IP action risk.

---

## Environment Variables

Copy `.env.example` to `.env` and fill in the values.

| Variable | Required | Default | Description |
|---|---|---|---|
| `SUPABASE_URL` | Yes | — | Your Supabase project URL |
| `SUPABASE_SERVICE_ROLE_KEY` | Yes | — | Service role key — bypasses RLS. Keep secret. |
| `HEADLESS` | No | `true` | `false` to run Chromium in visible mode for debugging |
| `PROXY_URL` | No | — | HTTP/SOCKS5 proxy for all browser contexts. Strongly recommended for FB. |
| `SCROLL_PASSES` | No | `3` | Scroll passes per page to trigger lazy-loaded API requests |
| `MIN_PROFIT` | No | `30` | Minimum estimated resale value (dollars) to keep a listing |
| `MAX_RESULTS_PER_RUN` | No | `500` | Max total items fetched per worker pass |
| `TARGETS_PER_PASS` | No | `20` | How many `crawl_targets` rows to pull per pass |

---

## Installation

```bash
cd scraper/
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
playwright install chromium
cp .env.example .env
# Edit .env with your Supabase credentials and optional PROXY_URL
```

---

## Running

### Single pass — good for cron:
```bash
python -m freebox_scraper.worker --once
```

### Single pass with debug logging (use on first run to verify field mappings):
```bash
python -m freebox_scraper.worker --once --log-level DEBUG
```

### Continuous loop (runs every ~5 minutes, self-pacing):
```bash
python -m freebox_scraper.worker
```

### Visible browser (watch Chromium navigate — useful for debugging bans):
```bash
HEADLESS=false python -m freebox_scraper.worker --once --log-level DEBUG
```

### Alternatively, from the project root:
```bash
python worker.py --once
```

---

## Cron Scheduling

Use `--once` and schedule via cron.  The `crawl_targets` table controls per-target frequency (`hot`=15 min, `warm`=3 h, `cold`=24 h), so running the worker every 10–15 minutes with `--once` is fine.

Example crontab:
```
*/10 * * * * /path/to/.venv/bin/python -m freebox_scraper.worker --once >> /var/log/freebox_scraper.log 2>&1
```
