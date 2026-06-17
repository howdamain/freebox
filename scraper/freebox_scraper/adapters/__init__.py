"""
adapters/__init__.py — Registry of all source adapters.

ADAPTERS maps source_name (matching crawl_targets.source column values) to an
adapter instance. Add new adapters here when new sources are supported.
"""
from freebox_scraper.adapters.craigslist import CraigslistAdapter
from freebox_scraper.adapters.facebook import FacebookAdapter
from freebox_scraper.adapters.offerup import OfferUpAdapter
from freebox_scraper.adapters.base import SourceAdapter

ADAPTERS: dict[str, SourceAdapter] = {
    "Craigslist":           CraigslistAdapter(),
    "Facebook Marketplace": FacebookAdapter(),
    "OfferUp":              OfferUpAdapter(),
}

__all__ = ["ADAPTERS", "SourceAdapter"]
