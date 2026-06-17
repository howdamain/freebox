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
