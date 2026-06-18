"""
adapters/offerup.py — OfferUp free-listings adapter.

Strategy: browser-free httpx GET + __NEXT_DATA__ parse (verified 2026-06-18)
══════════════════════════════════════════════════════════════════════════
OfferUp server-renders its search results into the page's Next.js __NEXT_DATA__
JSON. A plain HTTPX GET (no browser, no JS execution) of the free-items search
URL returns the full result set under:
    props.pageProps.searchFeedResponse.looseTiles[]
Each tile with tileType == "LISTING" carries a `listing` object:
    {listingId, title, price, conditionText, locationName, image:{url}, flags}
Free items have price in {"0", "00", "0.00"} (we filter price == 0).

This mirrors craigslist.py (httpx + parse), so the shared Playwright browser is
unused for OfferUp — it is accepted only for interface compatibility.

⚠️ Hosting note: OfferUp 403s datacenter IPs and shadow-bans after ~5-10 pages
per IP. From a residential IP this works unproxied. When hosted on a cloud
server, set PROXY_URL to a residential proxy, OR set APIFY_TOKEN to enable the
managed Apify fallback below (which offloads anti-bot to a hosted actor).
"""
from __future__ import annotations

import json
import logging
import re
from typing import TYPE_CHECKING, Any

import httpx

from freebox_scraper.adapters.base import SourceAdapter, NormalizedListing
from freebox_scraper import config

if TYPE_CHECKING:
    from freebox_scraper.browser import BrowserSession

log = logging.getLogger(__name__)

_USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/124.0.0.0 Safari/537.36"
)

_NEXT_DATA_RE = re.compile(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', re.S)

# A free item's price renders as one of these (string) in the feed payload.
_FREE_PRICES = {"0", "00", "0.0", "0.00", "free"}


def _build_search_url(zip_code: str) -> str:
    """Free-items search URL near a zip. OfferUp resolves zip → lat/lon server-side."""
    return (
        "https://offerup.com/search/"
        f"?q=free&price_min=0&price_max=0"
        f"&distance={config.OFFERUP_DISTANCE}"
        f"&sort=recent&zip={zip_code}"
    )


def _is_free(price: Any) -> bool:
    if price is None:
        return False
    s = str(price).strip().lower().lstrip("$")
    if s in _FREE_PRICES:
        return True
    try:
        return float(s) == 0.0
    except ValueError:
        return False


def _image_url(image: Any) -> str | None:
    if isinstance(image, dict):
        url = image.get("url")
        return str(url).strip() if url else None
    return None


def _parse_tiles(html: str, zip_code: str) -> list[NormalizedListing]:
    """Extract free listings from the page's __NEXT_DATA__ searchFeedResponse."""
    m = _NEXT_DATA_RE.search(html)
    if not m:
        log.warning(
            "OfferUp: no __NEXT_DATA__ in page for zip=%s (challenge page or markup change?).",
            zip_code,
        )
        return []
    try:
        data = json.loads(m.group(1))
    except json.JSONDecodeError as exc:
        log.warning("OfferUp: __NEXT_DATA__ JSON parse failed for zip=%s: %s", zip_code, exc)
        return []

    tiles = (
        data.get("props", {})
        .get("pageProps", {})
        .get("searchFeedResponse", {})
        .get("looseTiles")
    )
    if not isinstance(tiles, list):
        log.warning("OfferUp: searchFeedResponse.looseTiles missing for zip=%s.", zip_code)
        return []

    results: list[NormalizedListing] = []
    for tile in tiles:
        if not isinstance(tile, dict) or tile.get("tileType") != "LISTING":
            continue  # skip ad tiles (AD_3P_GOOGLE_DISPLAY, AD_1P)
        lst = tile.get("listing")
        if not isinstance(lst, dict):
            continue

        listing_id = str(lst.get("listingId") or "").strip()
        title = str(lst.get("title") or "").strip()
        if not listing_id or not title:
            continue
        if not _is_free(lst.get("price")):
            continue

        condition = str(lst.get("conditionText") or "").strip()
        if condition.lower() in ("", "none"):
            condition = None

        results.append(NormalizedListing(
            source="OfferUp",
            source_listing_id=listing_id,
            title=title,
            description=None,  # not present on feed tiles
            category=None,     # OfferUp feed tiles don't carry a category slug
            city=str(lst.get("locationName") or "").strip() or None,
            zip=zip_code,
            image_url=_image_url(lst.get("image")),
            url=f"https://offerup.com/item/detail/{listing_id}",
            condition=condition,
            price="Free",
            status="available",
        ))
    return results


async def _fetch_diy(zip_code: str) -> list[NormalizedListing]:
    """Primary: browser-free httpx GET + __NEXT_DATA__ parse (residential IP)."""
    url = _build_search_url(zip_code)
    log.info("OfferUp: fetching zip=%s url=%s", zip_code, url)
    headers = {
        "User-Agent": _USER_AGENT,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9",
    }
    try:
        async with httpx.AsyncClient(
            headers=headers,
            follow_redirects=True,
            timeout=30.0,
            proxy=config.PROXY_URL or None,
        ) as client:
            resp = await client.get(url)
    except httpx.HTTPError as exc:
        log.error("OfferUp: HTTP error for zip=%s: %s", zip_code, exc)
        return []

    if resp.status_code != 200:
        log.warning(
            "OfferUp: HTTP %d for zip=%s (likely datacenter-IP block — set PROXY_URL "
            "to a residential proxy or APIFY_TOKEN for the managed fallback).",
            resp.status_code, zip_code,
        )
        return []
    return _parse_tiles(resp.text, zip_code)


async def _fetch_apify(zip_code: str) -> list[NormalizedListing]:
    """Managed fallback (dormant unless APIFY_TOKEN is set): run an OfferUp actor.

    ⚠️ The actor input schema and output field names are actor-specific and have
    NOT been verified against a live run. Confirm against the chosen actor's docs
    and a real dataset before relying on this path in production.
    """
    actor = config.APIFY_OFFERUP_ACTOR
    endpoint = (
        f"https://api.apify.com/v2/acts/{actor}"
        f"/run-sync-get-dataset-items?token={config.APIFY_TOKEN}"
    )
    run_input = {
        "query": "free",
        "zipCode": zip_code,
        "maxPrice": 0,
        "maxItems": config.MAX_RESULTS_PER_RUN,
    }
    try:
        async with httpx.AsyncClient(timeout=120.0) as client:
            resp = await client.post(endpoint, json=run_input)
        resp.raise_for_status()
        items = resp.json()
    except Exception as exc:
        log.error("OfferUp Apify fallback failed for zip=%s: %s", zip_code, exc)
        return []

    results: list[NormalizedListing] = []
    for raw in items if isinstance(items, list) else []:
        if not isinstance(raw, dict):
            continue
        listing_id = str(raw.get("listingId") or raw.get("id") or "").strip()
        title = str(raw.get("title") or raw.get("name") or "").strip()
        if not listing_id or not title:
            continue
        if not _is_free(raw.get("price")):
            continue
        results.append(NormalizedListing(
            source="OfferUp",
            source_listing_id=listing_id,
            title=title,
            city=str(raw.get("locationName") or raw.get("location") or "").strip() or None,
            zip=zip_code,
            image_url=str(raw.get("image") or raw.get("imageUrl") or "").strip() or None,
            url=str(raw.get("listingUrl") or raw.get("url")
                    or f"https://offerup.com/item/detail/{listing_id}").strip(),
            condition=str(raw.get("condition") or raw.get("conditionText") or "").strip() or None,
            price="Free",
            status="available",
        ))
    log.info("OfferUp Apify fallback: %d free listing(s) for zip=%s.", len(results), zip_code)
    return results


class OfferUpAdapter(SourceAdapter):
    """OfferUp free-listings adapter (browser-free httpx + __NEXT_DATA__ parse).

    Primary path is a plain HTTPX GET; the shared browser is ignored. Falls back
    to a managed Apify actor only when the DIY path yields nothing AND APIFY_TOKEN
    is configured (e.g. when hosted on a datacenter IP that OfferUp 403s).
    """

    @property
    def source_name(self) -> str:
        return "OfferUp"

    async def fetch(
        self,
        zip_code: str,
        browser: "BrowserSession",  # unused — kept for interface compatibility
    ) -> list[NormalizedListing]:
        results = await _fetch_diy(zip_code)
        if not results and config.APIFY_TOKEN:
            log.info("OfferUp: DIY returned 0 for zip=%s; trying Apify fallback.", zip_code)
            results = await _fetch_apify(zip_code)
        log.info("OfferUp: %d free listing(s) for zip=%s.", len(results), zip_code)
        return results
