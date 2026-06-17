"""
adapters/facebook.py — Facebook Marketplace free-listings adapter.

Strategy: micro-query + GraphQL network interception
═════════════════════════════════════════════════════
Facebook Marketplace caps a single search result page at roughly 500–1 000
items and randomises CSS class names, so DOM scraping is brittle and a single
broad search misses a large fraction of the inventory.

To work around the cap, we split one "all free items in this zip" macro-search
into N micro-queries, one per top-level Marketplace category.  Each micro-query
targets `maxPrice=0` within a tight 1-mile radius, so each pool stays well
below the cap and the union covers the full free-stuff inventory.

Instead of parsing DOM, we intercept GraphQL responses that FB's React front-
end fires when the page loads and when the user scrolls.  These responses
contain structured listing data (id, title, price, photo, location, etc.) in a
stable-ish JSON envelope.

⚠️ VERIFICATION REQUIRED ON FIRST RUN
────────────────────────────────────────
Facebook's internal GraphQL schema is undocumented and changes without notice.
On first run:
  1.  Launch with --log-level DEBUG.
  2.  The adapter logs every raw GraphQL payload it captures.
  3.  Inspect the payloads to find the actual field paths.
  4.  Update the `# ⚠️ VERIFY` comments and .get() chains below accordingly.

Key things to verify:
  a. URL pattern for Marketplace category free search (see _build_category_url)
  b. zip→location binding: FB may need a place_id, not a raw zip
     (see _build_category_url TODO comment)
  c. GraphQL match substrings (see _GRAPHQL_MATCH)
  d. JSON path to the listing nodes array (see _extract_nodes)
  e. Field names on each node (see _parse_node)
"""
from __future__ import annotations

import logging
from typing import TYPE_CHECKING, Any

from freebox_scraper.adapters.base import SourceAdapter, NormalizedListing, ALLOWED_CATEGORIES
from freebox_scraper import config

if TYPE_CHECKING:
    from freebox_scraper.browser import BrowserSession

log = logging.getLogger(__name__)

# ── GraphQL response matching ─────────────────────────────────────────────────
# At least one of these strings must appear in a response body for it to be
# collected by capture_graphql().
# ⚠️ VERIFY: run in DEBUG to see which response bodies contain listing data.
_GRAPHQL_MATCH: list[str] = [
    "marketplace_search",
    "MarketplaceProductItem",
    "GroupCommerceProductItem",
    "listing_price",
    "marketplace_listing_title",
]

# ── Facebook Marketplace category URL slugs ───────────────────────────────────
# ⚠️ VERIFY: these slugs are best-effort guesses based on FB Marketplace URL
# patterns observed in 2024–2025.  FB may use numeric category IDs, different
# slug names, or require a location_id instead of a raw zip in the URL.
# Open chrome://marketplace in your browser, click each category, and check
# the URL to confirm the slug.
_FB_CATEGORY_SLUGS: list[str] = [
    "furniture",
    "electronics",
    "apparel",          # ⚠️ VERIFY: may be "clothing" or "clothing-shoes"
    "appliances",
    "baby-kids",        # ⚠️ VERIFY: may be "baby_and_kids"
    "tools",
    "sporting-goods",   # ⚠️ VERIFY: may be "sporting_goods"
    "toys-games",       # ⚠️ VERIFY: may be "toys_and_games"
    "books-movies-music",  # ⚠️ VERIFY
    "garden-outdoor",   # ⚠️ VERIFY: may be "garden"
    "home-goods",       # ⚠️ VERIFY: may be "home_and_garden" or "home_goods"
    "musical-instruments",  # ⚠️ VERIFY
    "pet-supplies",     # ⚠️ VERIFY
    "household",        # ⚠️ VERIFY: may not exist as a slug
    "office-supplies",  # ⚠️ VERIFY
    "health-beauty",    # ⚠️ VERIFY
    "art-crafts",       # ⚠️ VERIFY
    "collectibles",     # ⚠️ VERIFY
    "auto-parts",       # ⚠️ VERIFY: may be "auto_parts" or under "vehicles"
    "free-stuff",       # ⚠️ VERIFY: FB may have a top-level "free" category
]

# Map FB category slug → our allowed DB slug.
# Keys are the _FB_CATEGORY_SLUGS values (after first-run slug verification).
_CATEGORY_MAP: dict[str, str | None] = {
    "furniture":            "furniture",
    "electronics":          "electronics",
    "apparel":              "clothing",
    "appliances":           "electronics",
    "baby-kids":            "toys",
    "tools":                "tech",
    "sporting-goods":       "collectibles",
    "toys-games":           "toys",
    "books-movies-music":   "books",
    "garden-outdoor":       "plants",
    "home-goods":           "furniture",
    "musical-instruments":  "collectibles",
    "pet-supplies":         None,
    "household":            "furniture",
    "office-supplies":      "tech",
    "health-beauty":        "apparel",
    "art-crafts":           "art",
    "collectibles":         "collectibles",
    "auto-parts":           "tech",
    "free-stuff":           None,
}

# Additional category map for labels found on listing nodes themselves.
# ⚠️ VERIFY: FB node category labels after first-run inspection.
_NODE_CATEGORY_MAP: dict[str, str] = {
    "furniture":                "furniture",
    "home goods":               "furniture",
    "home & garden":            "furniture",
    "appliances":               "electronics",
    "electronics":              "electronics",
    "phones & tablets":         "electronics",
    "computers & laptops":      "computing",
    "video games":              "electronics",
    "cameras & photography":    "photography",
    "clothing & shoes":         "clothing",
    "bags & luggage":           "apparel",
    "jewelry & accessories":    "apparel",
    "health & beauty":          "apparel",
    "toys & games":             "toys",
    "baby & kids":              "toys",
    "books, movies & music":    "books",
    "books":                    "books",
    "music instruments":        "collectibles",
    "art":                      "art",
    "antiques":                 "collectibles",
    "collectibles":             "collectibles",
    "sporting goods":           "collectibles",
    "outdoor":                  "collectibles",
    "tools":                    "tech",
    "auto parts":               "tech",
    "plants":                   "plants",
    "garden":                   "plants",
    "food":                     "food",
    "free stuff":               None,
    "miscellaneous":            None,
    "other":                    None,
}


def _build_category_url(category_slug: str, zip_code: str) -> str:
    """
    Build a Facebook Marketplace URL for a single category + zip query.

    ⚠️ VERIFY: This URL pattern is a best-effort guess.  FB may:
      - Require a numeric location_id instead of a raw zip (e.g. ?location_id=12345).
        To find your city's location_id, open any FB Marketplace search in a
        browser and inspect the URL / the GraphQL request payload — the
        location_id appears in the query variables.
      - Use a different query-parameter name for zip/location.
      - Use numeric category IDs instead of slug strings.

    TODO (first-run): capture one real URL from a browser session to
    confirm the pattern, then update this function.
    """
    # ⚠️ VERIFY: confirmed Marketplace category URL format
    base = f"https://www.facebook.com/marketplace/category/{category_slug}"
    # radius=1 (miles) keeps the pool small; maxPrice=0 filters for free items.
    # ⚠️ VERIFY: 'deliveryMethod' may also need to be set.
    params = (
        f"?maxPrice=0"
        f"&radius=1"
        f"&query=free"
        f"&exact=false"
        f"&location={zip_code}"   # ⚠️ VERIFY: FB may need &place_id=... instead
    )
    return base + params


def _safe_get(d: Any, *keys: str, default: Any = None) -> Any:
    """Walk a nested dict/list path defensively, returning default on any miss."""
    cur = d
    for k in keys:
        if isinstance(cur, dict):
            cur = cur.get(k, default)
        elif isinstance(cur, list) and isinstance(k, int):
            try:
                cur = cur[k]
            except IndexError:
                return default
        else:
            return default
        if cur is None:
            return default
    return cur


def _extract_nodes(payload: dict[str, Any]) -> list[dict[str, Any]]:
    """
    Walk the GraphQL response JSON to find the list of listing nodes.

    ⚠️ VERIFY: This path is a best-effort guess based on common FB GraphQL
    response shapes.  Inspect actual payloads (logged at DEBUG level) to find
    the real path on first run.

    Common observed paths (any may be correct depending on query type):
      data.marketplace_search.feed_units.edges[*].node
      data.viewer.marketplace_feed.feed_units.edges[*].node
      data.MarketplaceSearchListingsEdge[*].node
      data.marketplace_product_details_page.target
    """
    nodes: list[dict[str, Any]] = []

    if not isinstance(payload, dict):
        return nodes

    # ── Attempt 1: data.marketplace_search.feed_units.edges ──
    # ⚠️ VERIFY path
    edges = _safe_get(
        payload,
        "data", "marketplace_search", "feed_units", "edges",
        default=None,
    )
    if isinstance(edges, list):
        for edge in edges:
            node = _safe_get(edge, "node", default=None)
            if isinstance(node, dict):
                nodes.append(node)
        if nodes:
            return nodes

    # ── Attempt 2: data.viewer.marketplace_feed.feed_units.edges ──
    # ⚠️ VERIFY path
    edges = _safe_get(
        payload,
        "data", "viewer", "marketplace_feed", "feed_units", "edges",
        default=None,
    )
    if isinstance(edges, list):
        for edge in edges:
            node = _safe_get(edge, "node", default=None)
            if isinstance(node, dict):
                nodes.append(node)
        if nodes:
            return nodes

    # ── Attempt 3: flat list at data.nodes ──
    # ⚠️ VERIFY path
    flat = _safe_get(payload, "data", "nodes", default=None)
    if isinstance(flat, list):
        nodes.extend(n for n in flat if isinstance(n, dict))
        if nodes:
            return nodes

    log.debug("_extract_nodes: no listing array found in payload keys: %s", list(payload.keys())[:10])
    return nodes


def _parse_node(
    node: dict[str, Any],
    zip_code: str,
    fallback_category: str | None,
) -> NormalizedListing | None:
    """
    Map a single GraphQL listing node → NormalizedListing.

    ⚠️ VERIFY: every .get() call here is provisional.  On first run, enable
    DEBUG logging to see the full raw node dict and adjust field paths.
    """
    log.debug("Facebook raw node: %s", node)

    # ── ID ──
    # ⚠️ VERIFY: may be at 'id', 'listing.id', or nested inside 'listing'
    listing_obj = node.get("listing") or node  # some responses wrap in "listing"
    listing_id = str(
        listing_obj.get("id")
        or listing_obj.get("listing_id")
        or node.get("id")
        or ""
    ).strip()
    if not listing_id:
        log.warning("Facebook node missing ID, skipping: %s", str(node)[:200])
        return None

    # ── Title ──
    # ⚠️ VERIFY: may be 'marketplace_listing_title', 'name', or 'title'
    title = str(
        listing_obj.get("marketplace_listing_title")
        or listing_obj.get("name")
        or listing_obj.get("title")
        or node.get("marketplace_listing_title")
        or node.get("name")
        or ""
    ).strip()
    if not title:
        log.warning("Facebook node %s missing title, skipping.", listing_id)
        return None

    # ── Price guard ──
    # We only want free listings.  Most micro-queries are already filtered by
    # maxPrice=0, but FB sometimes leaks non-free items through.
    # ⚠️ VERIFY: price field path
    price_obj = (
        listing_obj.get("listing_price")
        or listing_obj.get("price")
        or node.get("listing_price")
        or {}
    )
    if isinstance(price_obj, dict):
        amount_str = str(price_obj.get("amount") or price_obj.get("value") or "0")
    else:
        amount_str = str(price_obj or "0")
    try:
        amount = float(amount_str.replace(",", "").replace("$", ""))
    except (ValueError, TypeError):
        amount = 0.0
    if amount > 0:
        log.debug("Facebook: skipping non-free listing id=%s price=%s", listing_id, amount_str)
        return None

    # ── Description ──
    # ⚠️ VERIFY: may be 'description', 'redacted_description', or nested
    description = str(
        listing_obj.get("description")
        or listing_obj.get("redacted_description")
        or node.get("description")
        or ""
    ).strip() or None

    # ── Category ──
    # Use the loop's category as fallback; try the node's own category first.
    # ⚠️ VERIFY: may be 'category_name', 'marketplace_listing_category', or nested
    raw_cat = str(
        listing_obj.get("category_name")
        or listing_obj.get("marketplace_listing_category")
        or listing_obj.get("category")
        or node.get("category_name")
        or ""
    ).lower().strip()
    category: str | None = _NODE_CATEGORY_MAP.get(raw_cat) if raw_cat else fallback_category

    # ── Location ──
    # ⚠️ VERIFY: may be listing_obj["location"]["reverse_geocode"]["city"]
    #              or listing_obj["location"]["city"], etc.
    location_obj = listing_obj.get("location") or node.get("location") or {}
    city: str | None = None
    lat: float | None = None
    lon: float | None = None

    if isinstance(location_obj, dict):
        # ⚠️ VERIFY: path to city name
        reverse = location_obj.get("reverse_geocode") or {}
        city = (
            str(reverse.get("city") or reverse.get("neighborhood") or "").strip()
            or str(location_obj.get("city") or location_obj.get("name") or "").strip()
            or None
        )
        lat = SourceAdapter.safe_float(
            location_obj.get("latitude")
            or location_obj.get("lat")
        )
        lon = SourceAdapter.safe_float(
            location_obj.get("longitude")
            or location_obj.get("lng")
            or location_obj.get("lon")
        )

    # ── Image ──
    # ⚠️ VERIFY: may be primary_listing_photo.image.uri or thumbnail_image.uri
    primary_photo = (
        listing_obj.get("primary_listing_photo")
        or listing_obj.get("thumbnail_image")
        or node.get("primary_listing_photo")
        or {}
    )
    image_url: str | None = None
    if isinstance(primary_photo, dict):
        img = primary_photo.get("image") or primary_photo
        image_url = str(img.get("uri") or img.get("url") or "").strip() or None
    elif isinstance(primary_photo, str):
        image_url = primary_photo.strip() or None

    # ── Condition ──
    # ⚠️ VERIFY: may be 'condition', 'item_condition', or 'condition_display_name'
    condition = str(
        listing_obj.get("condition")
        or listing_obj.get("item_condition")
        or listing_obj.get("condition_display_name")
        or ""
    ).strip() or None

    # ── Date ──
    # ⚠️ VERIFY: may be 'creation_time', 'listed_date', or 'creation_story.comet_sections.timestamp'
    posted_at_raw = (
        listing_obj.get("creation_time")
        or listing_obj.get("listed_date")
        or listing_obj.get("created_time")
        or node.get("creation_time")
    )
    posted_at: str | None = None
    if posted_at_raw is not None:
        # FB often returns a Unix timestamp (int)
        try:
            import datetime as _dt
            ts = int(posted_at_raw)
            posted_at = _dt.datetime.fromtimestamp(ts, tz=_dt.timezone.utc).isoformat()
        except (ValueError, TypeError):
            posted_at = str(posted_at_raw).strip() or None

    return NormalizedListing(
        source="Facebook Marketplace",
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


class FacebookAdapter(SourceAdapter):
    """
    Facebook Marketplace free-listings adapter using DIY GraphQL interception.

    Cap-bypass math
    ───────────────
    FB caps each Marketplace search result page at ~500–1 000 visible items.
    By querying N=20 categories individually with maxPrice=0 and radius=1 mile,
    the total reachable inventory is N × cap, giving 10 000–20 000 potential
    results per zip — far more than Apify's actor achieved with a single broad
    search.  Deduplication handles any cross-category overlaps.

    The trade-off is N page loads per zip per pass.  With 3 scroll passes each
    and 2–7 s human delays, expect 2–4 minutes per zip target.

    ⚠️ Field paths + category slugs + zip→location binding need first-run
    verification via --log-level DEBUG.
    """

    @property
    def source_name(self) -> str:
        return "Facebook Marketplace"

    async def fetch(
        self,
        zip_code: str,
        browser: "BrowserSession",
    ) -> list[NormalizedListing]:
        """
        Run micro-queries across all FB category slugs and aggregate results.
        """
        all_listings: dict[str, NormalizedListing] = {}  # keyed by source_listing_id for local dedup

        for slug in _FB_CATEGORY_SLUGS:
            url = _build_category_url(slug, zip_code)
            fallback_category = _CATEGORY_MAP.get(slug)

            log.info(
                "Facebook: querying category=%s zip=%s url=%s",
                slug, zip_code, url[:100],
            )

            try:
                payloads = await browser.capture_graphql(
                    url=url,
                    match_substrings=_GRAPHQL_MATCH,
                    scroll_passes=config.SCROLL_PASSES,
                )
            except Exception as exc:
                log.warning(
                    "Facebook: capture_graphql failed for category=%s zip=%s: %s",
                    slug, zip_code, exc,
                )
                continue

            log.debug(
                "Facebook: captured %d payload(s) for category=%s zip=%s",
                len(payloads), slug, zip_code,
            )

            for payload in payloads:
                log.debug("Facebook raw payload (category=%s): %s", slug, str(payload)[:500])
                nodes = _extract_nodes(payload)
                for node in nodes:
                    listing = _parse_node(node, zip_code, fallback_category)
                    if listing is not None and listing.source_listing_id not in all_listings:
                        all_listings[listing.source_listing_id] = listing

        result = list(all_listings.values())
        log.info(
            "Facebook Marketplace: %d unique free listing(s) for zip=%s across %d categories.",
            len(result), zip_code, len(_FB_CATEGORY_SLUGS),
        )
        return result
