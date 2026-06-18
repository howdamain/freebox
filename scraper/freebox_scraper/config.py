"""
config.py — Load and validate environment variables. Fails fast with a clear message.
"""
from __future__ import annotations

import os
from pathlib import Path

from dotenv import load_dotenv

# Load .env from the scraper project root (one level above this package).
_ENV_PATH = Path(__file__).parent.parent / ".env"
load_dotenv(dotenv_path=_ENV_PATH)


def _require(name: str) -> str:
    """Return the env var or raise with a clear message."""
    value = os.getenv(name, "").strip()
    if not value:
        raise EnvironmentError(
            f"[config] Required environment variable '{name}' is missing or empty.\n"
            f"Copy .env.example → .env and fill in your values."
        )
    return value


# ── Required ────────────────────────────────────────────────────────────────
SUPABASE_URL: str = _require("SUPABASE_URL").rstrip("/")
SUPABASE_SERVICE_ROLE_KEY: str = _require("SUPABASE_SERVICE_ROLE_KEY")

# ── Browser / scraping ───────────────────────────────────────────────────────
# Run Chromium headless (set HEADLESS=false for headed debug mode).
HEADLESS: bool = os.getenv("HEADLESS", "true").lower() not in ("false", "0", "no")

# Optional HTTP/SOCKS5 proxy for all Playwright contexts.
# Format: http://user:pass@host:port  or  socks5://host:port
# Strongly recommended for Facebook scraping to avoid IP bans.
PROXY_URL: str | None = os.getenv("PROXY_URL", "").strip() or None

# How many scroll passes to perform per page to load lazy-loaded GraphQL batches.
SCROLL_PASSES: int = int(os.getenv("SCROLL_PASSES", "3"))

# ── Optional with defaults ───────────────────────────────────────────────────
MIN_PROFIT: int = int(os.getenv("MIN_PROFIT", "30"))
MAX_RESULTS_PER_RUN: int = int(os.getenv("MAX_RESULTS_PER_RUN", "500"))
TARGETS_PER_PASS: int = int(os.getenv("TARGETS_PER_PASS", "20"))

# ── Perplexity comp pricing ───────────────────────────────────────────────────
# Web-grounded resale valuation (Sonar). Items are batched to keep cost low.
# When unset, valuation falls back to the category-baseline heuristic.
PERPLEXITY_API_KEY: str | None = os.getenv("PERPLEXITY_API_KEY", "").strip() or None
PERPLEXITY_MODEL: str = os.getenv("PERPLEXITY_MODEL", "sonar")
PRICING_BATCH_SIZE: int = int(os.getenv("PRICING_BATCH_SIZE", "15"))
# Pricing batches run concurrently. PERPLEXITY_RPM spaces request STARTS to stay
# under Sonar's per-minute ceiling (Tier-0 ≈ 50 RPM; default 40 leaves headroom —
# raise if your key is a higher tier). PRICING_CONCURRENCY is a secondary in-flight
# cap. Failed batches retry up to PERPLEXITY_MAX_RETRIES (429 honors Retry-After).
PERPLEXITY_RPM: int = int(os.getenv("PERPLEXITY_RPM", "40"))
PRICING_CONCURRENCY: int = int(os.getenv("PRICING_CONCURRENCY", "6"))
PERPLEXITY_MAX_RETRIES: int = int(os.getenv("PERPLEXITY_MAX_RETRIES", "3"))

# ── Listing lifecycle ──────────────────────────────────────────────────────────
# 'available' listings not re-seen within this many days are marked 'expired' each
# pass so the feed ages out items that have fallen off their source.
LISTING_EXPIRY_DAYS: int = int(os.getenv("LISTING_EXPIRY_DAYS", "3"))

# ── Image enrichment ───────────────────────────────────────────────────────────
# Listings whose search results omit photos (e.g. Craigslist) get a primary image
# by fetching the source detail page's og:image. Concurrency caps in-flight fetches.
IMAGE_CONCURRENCY: int = int(os.getenv("IMAGE_CONCURRENCY", "6"))

# ── OfferUp ────────────────────────────────────────────────────────────────────
# Search radius (miles) for the OfferUp free-items query.
OFFERUP_DISTANCE: int = int(os.getenv("OFFERUP_DISTANCE", "10"))
# Apify fallback for OfferUp: the DIY httpx path works from a residential IP but
# OfferUp 403s datacenter IPs. When hosted on a cloud server, set APIFY_TOKEN to
# offload OfferUp to a managed actor (dormant otherwise). Actor id uses Apify's
# username~actor form.
APIFY_TOKEN: str | None = os.getenv("APIFY_TOKEN", "").strip() or None
APIFY_OFFERUP_ACTOR: str = os.getenv("APIFY_OFFERUP_ACTOR", "haketa~offerup-scraper")

# ── Derived ──────────────────────────────────────────────────────────────────
SUPABASE_HEADERS: dict[str, str] = {
    "apikey": SUPABASE_SERVICE_ROLE_KEY,
    "Authorization": f"Bearer {SUPABASE_SERVICE_ROLE_KEY}",
    "Content-Type": "application/json",
}

# Frequency tier → minutes until next crawl
FREQUENCY_MINUTES: dict[str, int] = {
    "hot": 15,
    "warm": 180,   # 3 h
    "cold": 1440,  # 24 h
}
