"""
adapters/base.py — Abstract base class and shared types for source adapters.

Each adapter encapsulates how to scrape a particular source for a given zip
code and normalize the results into NormalizedListing objects.

The NormalizedListing dataclass mirrors the `listings` table columns that the
worker is responsible for writing.

Adapter interface change from Apify version
────────────────────────────────────────────
`fetch(zip_code, browser)` is now the single abstract method.  Adapters that
need a browser (Facebook, OfferUp) use it; browser-free adapters (Craigslist)
simply ignore the parameter.  The method is async so that Playwright-backed
adapters can use await internally.
"""
from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any

if TYPE_CHECKING:
    from freebox_scraper.browser import BrowserSession

# Allowed category slugs (must match the DB FK / enum exactly).
# Map source-specific category labels to one of these or leave as None.
ALLOWED_CATEGORIES: frozenset[str] = frozenset({
    "furniture",
    "electronics",
    "photography",
    "computing",
    "plants",
    "clothing",
    "food",
    "toys",
    "books",
    "art",
    "apparel",
    "collectibles",
    "tech",
})


@dataclass
class NormalizedListing:
    """
    Represents one listing ready to be written to the `listings` table.

    Fields marked REQUIRED must be non-None/non-empty; the worker will skip
    a listing and log a warning if they are missing.
    """
    # ── Required ─────────────────────────────────────────────────────────────
    source: str                     # e.g. "Craigslist"
    source_listing_id: str          # stable ID from the source (used for dedup)
    title: str                      # raw listing title

    # ── Derived (set by worker after valuation) ───────────────────────────────
    est_resale_value: int = 0       # from valuation.estimate()
    est_profit: int = 0             # same as est_resale_value for free items

    # ── Optional enrichment ───────────────────────────────────────────────────
    description: str | None = None
    category: str | None = None     # MUST be an ALLOWED_CATEGORIES slug or None
    city: str | None = None
    latitude: float | None = None
    longitude: float | None = None
    zip: str | None = None          # not a DB column; used internally for dedupe
    price: str = "Free"
    condition: str | None = None
    image_url: str | None = None
    url: str | None = None          # absolute link to the original source listing
    posted_at: str | None = None    # ISO 8601 timestamptz
    status: str = "available"

    def to_db_row(self) -> dict[str, Any]:
        """
        Serialize to a dict that maps directly to the `listings` table columns.
        Excludes None values so PostgREST doesn't write NULL over existing data
        unnecessarily.
        """
        row: dict[str, Any] = {
            "source": self.source,
            "source_listing_id": self.source_listing_id,
            "title": self.title,
            "price": self.price,
            "status": self.status,
            "est_resale_value": self.est_resale_value,
            "est_profit": self.est_profit,
        }
        # Only include optional fields when they have a value
        optional: dict[str, Any] = {
            "description": self.description,
            "category": self.category,
            "city": self.city,
            "zip": self.zip,
            "latitude": self.latitude,
            "longitude": self.longitude,
            "condition": self.condition,
            "image_url": self.image_url,
            "url": self.url,
            "posted_at": self.posted_at,
        }
        for k, v in optional.items():
            if v is not None:
                row[k] = v
        return row

    def is_valid(self) -> bool:
        """Return True if all required fields are present."""
        return bool(self.source and self.source_listing_id and self.title)


class SourceAdapter(ABC):
    """
    Abstract base for a scraping adapter.

    Subclasses must implement:
      - source_name  (str property)
      - fetch(zip_code, browser) -> list[NormalizedListing]   (async)

    The `browser` parameter is a BrowserSession instance shared across all
    adapters in a single worker pass.  Browser-free adapters (e.g. Craigslist)
    may ignore it.
    """

    # Override to True for adapters that need the shared Playwright BrowserSession
    # (e.g. GraphQL response interception). Browser-free httpx adapters leave this
    # False so the worker can skip launching Chromium entirely when no due target
    # needs it — keeping CI/hosted runs lightweight.
    requires_browser: bool = False

    @property
    @abstractmethod
    def source_name(self) -> str: ...

    @abstractmethod
    async def fetch(
        self,
        zip_code: str,
        browser: "BrowserSession",
    ) -> list[NormalizedListing]: ...

    # ── Shared helpers ────────────────────────────────────────────────────────

    @staticmethod
    def normalize_category(raw: str | None) -> str | None:
        """
        Map a source-specific category label to an allowed slug, or return None.
        Subclasses typically override with a source-specific mapping dict.
        """
        if not raw:
            return None
        slug = raw.lower().strip()
        return slug if slug in ALLOWED_CATEGORIES else None

    @staticmethod
    def safe_float(value: Any) -> float | None:
        """Parse a value as float, return None if not parseable."""
        try:
            return float(value)
        except (TypeError, ValueError):
            return None
