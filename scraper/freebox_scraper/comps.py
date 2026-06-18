"""
Comp-based valuation: price each free item at the median of recent eBay SOLD
listings for its cleaned title query (gross resale value — no fee deduction).

⚠️ Requires the headless browser (eBay blocks plain HTTP) AND, at any volume, a
residential proxy via config.PROXY_URL — from a plain/datacenter IP eBay returns
a ~2 KB bot-wall page (verified). When blocked, ebay_sold_median() returns None
and the worker falls back to the category baseline, so the pipeline degrades safely.

⚠️ VERIFY on the first proxied run (run with --log-level DEBUG):
  - the sold-price selector `.s-item__price` still matches eBay's markup;
  - `LH_Sold=1&LH_Complete=1` returns SOLD (not active) items;
  - eBay's leading "Shop on eBay" template row is excluded (outlier trim handles it).
"""
from __future__ import annotations

import logging
import re
import statistics
from urllib.parse import quote_plus

from bs4 import BeautifulSoup

from freebox_scraper.browser import BrowserSession, human_delay

log = logging.getLogger(__name__)

# Words to strip from a listing title before searching eBay (free-listing noise).
_STOPWORDS: set[str] = {
    "free", "curb", "curbside", "pickup", "pick", "up", "today", "gone", "must",
    "obo", "available", "now", "asap", "porch", "lot", "set", "of", "the", "a",
    "an", "for", "in", "on", "with", "and", "to", "your", "my", "x", "pcs", "pc",
}

# In-memory per-run cache so a batch of 182 items doesn't re-query duplicates.
# (At scale, promote to a Supabase comp_cache table with a TTL — see README.)
_cache: dict[str, int | None] = {}

_PRICE_RE = re.compile(r"s-item__price[^>]*>\s*\$([0-9][0-9,]*\.?\d*)", re.I)
_BLOCK_RE = re.compile(r"pardon the interruption|captcha|unusual traffic|robot check", re.I)


def clean_query(title: str) -> str:
    """Turn a noisy free-listing title into a focused eBay search query."""
    t = title.lower()
    t = re.sub(r"\$\d[\d,]*", " ", t)          # strip prices
    t = re.sub(r"[^a-z0-9 ]", " ", t)          # strip punctuation
    tokens = [w for w in t.split() if w not in _STOPWORDS and len(w) > 1]
    return " ".join(tokens[:6])                # brand + model + noun is plenty


def _parse_prices(html: str) -> list[float]:
    prices = [float(p.replace(",", "")) for p in _PRICE_RE.findall(html)]
    if not prices:  # selector fallback if the regex misses
        soup = BeautifulSoup(html, "html.parser")
        for el in soup.select(".s-item__price"):
            m = re.search(r"\$([0-9][0-9,]*\.?\d*)", el.get_text())
            if m:
                prices.append(float(m.group(1).replace(",", "")))
    return prices


def _trimmed_median(prices: list[float]) -> int | None:
    vals = sorted(p for p in prices if p > 0)
    if not vals:
        return None
    if len(vals) >= 5:                          # drop top/bottom 10% outliers
        k = max(1, len(vals) // 10)
        vals = vals[k:-k] or vals
    return round(statistics.median(vals))


async def ebay_sold_median(title: str, browser: BrowserSession) -> int | None:
    """Median recent SOLD price on eBay for this item, or None if unavailable."""
    q = clean_query(title)
    if len(q) < 3:
        return None
    if q in _cache:
        return _cache[q]

    url = (
        f"https://www.ebay.com/sch/i.html?_nkw={quote_plus(q)}"
        "&LH_Sold=1&LH_Complete=1&_ipg=60"
    )
    try:
        await human_delay(2.0, 5.0)             # gentle pacing to reduce bans
        html = await browser.fetch_html(url)
    except Exception as e:
        log.warning("eBay comp fetch failed for '%s': %s", q, e)
        _cache[q] = None
        return None

    if len(html) < 5000 or _BLOCK_RE.search(html):
        log.warning("eBay blocked/empty for '%s' (len=%d) — needs a residential proxy.", q, len(html))
        _cache[q] = None
        return None

    median = _trimmed_median(_parse_prices(html))
    log.info("eBay comps '%s' → median $%s", q, median)
    _cache[q] = median
    return median
