"""
adapters/offerup.py — OfferUp free-listings adapter.

Strategy: Playwright response interception (Next.js SPA)
═════════════════════════════════════════════════════════
OfferUp is a Next.js single-page application.  Listing data is loaded via
fetch() calls to OfferUp's internal API/GraphQL endpoints — it is not present
in the initial HTML.  We use browser.capture_graphql() to intercept those
fetch responses and extract free-item listings.

⚠️ VERIFICATION REQUIRED ON FIRST RUN
──────────────────────────────────────
OfferUp's internal API is undocumented and has changed before.

  1. Run with --log-level DEBUG.
  2. The adapter logs every raw API payload it captures.
  3. Inspect payloads to find the correct field paths and update the
     `# ⚠️ VERIFY` comments and .get() chains below.
  4. Update _GRAPHQL_MATCH substrings if the captured payloads don't match.
  5. Update _build_search_url() if the URL pattern changes.
"""
from __future__ import annotations

import logging
from typing import TYPE_CHECKING, Any

from freebox_scraper.adapters.base import SourceAdapter, NormalizedListing, ALLOWED_CATEGORIES
from freebox_scraper import config

if TYPE_CHECKING:
    from freebox_scraper.browser import BrowserSession

log = logging.getLogger(__name__)

# ── API response matching ─────────────────────────────────────────────────────
# Substrings that must appear in a response body for it to be collected.
# ⚠️ VERIFY: run in DEBUG mode and look for the responses that contain listing data.
_GRAPHQL_MATCH: list[str] = [
    "offer_price",      # ⚠️ VERIFY: price field name in OfferUp API
    "listing_id",       # ⚠️ VERIFY
    "offerup",          # domain hint — OfferUp API calls often contain this
    "graphql",          # OfferUp may use GraphQL or a REST-style JSON API
    "item",             # generic fallback — ⚠️ VERIFY won't over-match
]

# ── Category mapping ──────────────────────────────────────────────────────────
# ⚠️ VERIFY: category names are from OfferUp's public UI as of 2024–2025.
_CATEGORY_MAP: dict[str, str | None] = {
    "antiques & collectibles":    "collectibles",
    "appliances":                 "electronics",
    "art":                        "art",
    "auto parts & accessories":   "tech",
    "baby & kids":                "toys",
    "books, movies & music":      "books",
    "cameras & photography":      "photography",
    "cell phones":                "electronics",
    "clothing & shoes":           "clothing",
    "computers & laptops":        "computing",
    "electronics":                "electronics",
    "furniture":                  "furniture",
    "garden & outdoor":           "plants",
    "health & beauty":            "apparel",
    "home goods & décor":         "furniture",
    "jewelry & accessories":      "apparel",
    "musical instruments":        "collectibles",
    "other":                      None,
    "sporting goods":             "collectibles",
    "tools & home improvement":   "tech",
    "toys & games":               "toys",
    "video games & consoles":     "electronics",
}


def _build_search_url(zip_code: str) -> str:
    """
    Build an OfferUp search URL for free items near a zip code.

    ⚠️ VERIFY: OfferUp's search URL and parameter names may change.
    Observed 2024 pattern: https://offerup.com/search/?q=free&zip=XXXXX&price_max=0
    May also accept: location_id, city_slug, or lat/lon params.
    """
    # ⚠️ VERIFY: confirm URL and param names from a real browser session
    return (
        f"https://offerup.com/search/"
        f"?q=free"
        f"&zip={zip_code}"
        f"&price_max=0"         # ⚠️ VERIFY: param name may be 'max_price' or 'maxPrice'
        f"&price_min=0"         # ⚠️ VERIFY
        f"&distance=10"         # miles — ⚠️ VERIFY param name and units
        f"&sort=recent"         # ⚠️ VERIFY: may not be a valid value
    )


def _safe_get(d: Any, *keys: str, default: Any = None) -> Any:
    """Walk a nested dict path defensively."""
    cur = d
    for k in keys:
        if isinstance(cur, dict):
            cur = cur.get(k, default)
        else:
            return default
        if cur is None:
            return default
    return cur


def _extract_items(payload: dict[str, Any]) -> list[dict[str, Any]]:
    """
    Walk an OfferUp API response to find the list of item/listing objects.

    ⚠️ VERIFY: the actual response shape.  Common patterns observed:
      data.search.results[*]
      data.items[*]
      data.feed.items[*]
      data.results[*]
    """
    items: list[dict[str, Any]] = []

    if not isinstance(payload, dict):
        return items

    # ── Attempt 1: data.search.results ── ⚠️ VERIFY
    results = _safe_get(payload, "data", "search", "results", default=None)
    if isinstance(results, list):
        items.extend(r for r in results if isinstance(r, dict))
        if items:
            return items

    # ── Attempt 2: data.items ── ⚠️ VERIFY
    results = _safe_get(payload, "data", "items", default=None)
    if isinstance(results, list):
        items.extend(r for r in results if isinstance(r, dict))
        if items:
            return items

    # ── Attempt 3: data.feed.items ── ⚠️ VERIFY
    results = _safe_get(payload, "data", "feed", "items", default=None)
    if isinstance(results, list):
        items.extend(r for r in results if isinstance(r, dict))
        if items:
            return items

    # ── Attempt 4: flat top-level items array ── ⚠️ VERIFY
    results = payload.get("items") or payload.get("results")
    if isinstance(results, list):
        items.extend(r for r in results if isinstance(r, dict))
        if items:
            return items

    log.debug("_extract_items: no item array found in payload keys: %s", list(payload.keys())[:10])
    return items


def _is_free(raw: dict[str, Any]) -> bool:
    """
    Return True if the listing appears to be free (price = 0 or text = "free").

    ⚠️ VERIFY: adjust field names for actual API output.
    """
    # Numeric price field — ⚠️ VERIFY field name
    price_num = (
        raw.get("offer_price")
        or raw.get("price")
        or raw.get("priceAmount")
        or _safe_get(raw, "price", "amount", default=None)
    )
    if price_num is not None:
        try:
            return float(price_num) == 0.0
        except (TypeError, ValueError):
            pass

    # Text price field — ⚠️ VERIFY field name
    price_text = str(
        raw.get("offer_price_label")
        or raw.get("displayPrice")
        or raw.get("priceText")
        or raw.get("price_label")
        or ""
    ).lower().strip()
    return price_text in ("", "0", "$0", "free", "0.00", "$0.00")


def _parse_item(raw: dict[str, Any], zip_code: str) -> NormalizedListing | None:
    """
    Map a single OfferUp API item object → NormalizedListing.

    ⚠️ VERIFY: every .get() call here is provisional.
    """
    log.debug("OfferUp raw item: %s", raw)

    # ── ID ── ⚠️ VERIFY field name
    listing_id = str(
        raw.get("id")
        or raw.get("item_id")
        or raw.get("listing_id")
        or raw.get("postId")
        or ""
    ).strip()
    if not listing_id:
        log.warning("OfferUp item missing ID, skipping: %s", str(raw)[:200])
        return None

    # ── Title ── ⚠️ VERIFY field name
    title = str(
        raw.get("title")
        or raw.get("name")
        or raw.get("item_title")
        or ""
    ).strip()
    if not title:
        log.warning("OfferUp item %s missing title, skipping.", listing_id)
        return None

    # ── Description ── ⚠️ VERIFY field name
    description = str(
        raw.get("description")
        or raw.get("details")
        or raw.get("body")
        or ""
    ).strip() or None

    # ── Category ── ⚠️ VERIFY field name and shape
    cat_raw = raw.get("category") or {}
    if isinstance(cat_raw, dict):
        raw_cat_str = str(cat_raw.get("name") or "").lower().strip()
    else:
        raw_cat_str = str(cat_raw or raw.get("categoryName") or "").lower().strip()
    category = _CATEGORY_MAP.get(raw_cat_str) if raw_cat_str else None

    # ── Location ── ⚠️ VERIFY field name and shape
    location_obj = raw.get("location") or {}
    if isinstance(location_obj, dict):
        city = str(
            location_obj.get("city")
            or location_obj.get("name")
            or location_obj.get("locality")
            or ""
        ).strip() or None
        lat = SourceAdapter.safe_float(
            location_obj.get("lat")
            or location_obj.get("latitude")
        )
        lon = SourceAdapter.safe_float(
            location_obj.get("lng")
            or location_obj.get("lon")
            or location_obj.get("longitude")
        )
    else:
        city = str(location_obj or raw.get("city") or "").strip() or None
        lat = SourceAdapter.safe_float(raw.get("latitude") or raw.get("lat"))
        lon = SourceAdapter.safe_float(raw.get("longitude") or raw.get("lng"))

    # ── Image ── ⚠️ VERIFY field name and shape
    image_url: str | None = None
    img_raw = (
        raw.get("photo_url")
        or raw.get("thumbnail")
        or raw.get("imageUrl")
        or raw.get("primaryImage")
    )
    if img_raw is None:
        # Sometimes it's in a photos / images list
        photos = raw.get("photos") or raw.get("images") or []
        if isinstance(photos, list) and photos:
            first = photos[0]
            if isinstance(first, dict):
                img_raw = first.get("url") or first.get("src") or first.get("uri")
            elif isinstance(first, str):
                img_raw = first
    if img_raw:
        image_url = str(img_raw).strip() or None

    # ── Condition ── ⚠️ VERIFY field name
    condition = str(
        raw.get("condition")
        or raw.get("conditionLabel")
        or raw.get("condition_label")
        or ""
    ).strip() or None

    # ── Date ── ⚠️ VERIFY field name
    posted_at = str(
        raw.get("listed_at")
        or raw.get("posted_at")
        or raw.get("createdAt")
        or raw.get("publishedAt")
        or raw.get("created")
        or ""
    ).strip() or None

    return NormalizedListing(
        source="OfferUp",
        source_listing_id=listing_id,
        title=title,
        description=description,
        category=category,
        city=city,
        latitude=lat,
        longitude=lon,
        zip=zip_code,
        image_url=image_url,
        condition=condition,
        posted_at=posted_at,
        price="Free",
        status="available",
    )


class OfferUpAdapter(SourceAdapter):
    """
    OfferUp free-listings adapter using Playwright network response interception.

    OfferUp is a Next.js SPA; all listing data arrives via fetch() calls to
    their internal API.  We navigate to the free-items search URL in a headless
    browser, capture the API responses, extract item objects, and post-filter
    for zero-price items.

    ⚠️ API endpoints, response shapes, and URL params need first-run verification
    via --log-level DEBUG.
    """

    @property
    def source_name(self) -> str:
        return "OfferUp"

    async def fetch(
        self,
        zip_code: str,
        browser: "BrowserSession",
    ) -> list[NormalizedListing]:
        """
        Capture OfferUp API responses for free items near `zip_code`.
        """
        url = _build_search_url(zip_code)
        log.info("OfferUp: fetching zip=%s url=%s", zip_code, url)

        try:
            payloads = await browser.capture_graphql(
                url=url,
                match_substrings=_GRAPHQL_MATCH,
                scroll_passes=config.SCROLL_PASSES,
            )
        except Exception as exc:
            log.error("OfferUp: capture_graphql failed for zip=%s: %s", zip_code, exc)
            return []

        log.debug("OfferUp: captured %d payload(s) for zip=%s", len(payloads), zip_code)

        results: list[NormalizedListing] = []
        seen_ids: set[str] = set()

        for payload in payloads:
            log.debug("OfferUp raw payload: %s", str(payload)[:500])
            items = _extract_items(payload)
            for raw in items:
                # Post-filter: keep only free items
                if not _is_free(raw):
                    log.debug(
                        "OfferUp: skipping non-free item: %s",
                        raw.get("title") or raw.get("id"),
                    )
                    continue
                listing = _parse_item(raw, zip_code)
                if listing is not None and listing.source_listing_id not in seen_ids:
                    seen_ids.add(listing.source_listing_id)
                    results.append(listing)

        log.info("OfferUp: %d free listing(s) for zip=%s.", len(results), zip_code)
        return results
