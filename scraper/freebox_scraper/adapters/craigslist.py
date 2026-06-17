"""
adapters/craigslist.py — Craigslist free-listings adapter.

Strategy: browser-free direct HTTP scrape
══════════════════════════════════════════
Craigslist's markup is server-rendered and has been stable for years.  Unlike
Facebook or OfferUp, it doesn't require JavaScript execution to see listing
data, so we skip Playwright entirely and issue a plain HTTPX GET.

The "free stuff" section URL for a given Craigslist subdomain is:
  https://{subdomain}.craigslist.org/search/zip
  (section code 'zip' = "free stuff" in most US markets)

Zip→subdomain mapping
─────────────────────
Craigslist organises listings by metro region, not zip code.  We use a
hardcoded 3-digit zip-prefix → subdomain lookup table as a best-effort mapping.
It covers the most populous US metros.  For unrecognised prefixes the worker
logs a warning and falls back to 'sfbay' (almost certainly wrong — extend the
map for your actual target markets).

⚠️ VERIFICATION REQUIRED ON FIRST RUN
──────────────────────────────────────
  1. Run with --log-level DEBUG.
  2. The adapter logs the raw HTML and each parsed listing.
  3. Confirm CSS selectors in _parse_row() match the actual response HTML.
  4. Add or correct zip-prefix entries in _ZIP_TO_SUBDOMAIN below.
"""
from __future__ import annotations

import logging
import re
from typing import TYPE_CHECKING, Any

import httpx
from bs4 import BeautifulSoup

from freebox_scraper.adapters.base import SourceAdapter, NormalizedListing

if TYPE_CHECKING:
    from freebox_scraper.browser import BrowserSession

log = logging.getLogger(__name__)

# ── Craigslist section code for "free stuff" ─────────────────────────────────
# ⚠️ VERIFY: most US metros use 'zip' for free stuff, but some use 'fre'.
_FREE_SECTION = "zip"

_USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/124.0.0.0 Safari/537.36"
)

# ── 3-digit zip prefix → Craigslist subdomain ────────────────────────────────
# ⚠️ VERIFY: extend this for your target markets.
# First-run DEBUG logs will reveal if the subdomain is wrong for a zip.
_ZIP_TO_SUBDOMAIN: dict[str, str] = {
    # California — LA basin
    "900": "losangeles", "901": "losangeles", "902": "losangeles",
    "903": "losangeles", "904": "losangeles", "905": "losangeles",
    "906": "losangeles", "907": "losangeles", "908": "losangeles",
    "910": "losangeles", "911": "losangeles", "912": "losangeles",
    "913": "losangeles", "914": "losangeles", "915": "losangeles",
    "916": "losangeles",
    # California — Inland Empire
    "917": "inlandempire", "918": "inlandempire", "919": "inlandempire",
    "928": "inlandempire",
    # California — San Diego
    "920": "sandiego", "921": "sandiego", "922": "sandiego",
    # California — Orange County
    "926": "orangecounty", "927": "orangecounty",
    # California — Bay Area
    "925": "sfbay", "929": "sfbay",
    "940": "sfbay", "941": "sfbay", "943": "sfbay", "944": "sfbay",
    "945": "sfbay", "946": "sfbay", "947": "sfbay", "948": "sfbay",
    "949": "sfbay", "950": "sfbay", "951": "sfbay", "952": "sfbay",
    "953": "sfbay", "954": "sfbay", "955": "sfbay", "956": "sfbay",
    # California — Sacramento
    "942": "sacramento",
    # California — Fresno
    "932": "fresno", "933": "fresno", "934": "fresno", "938": "fresno",
    # California — Bakersfield
    "935": "bakersfieldca", "936": "bakersfieldca",
    # California — Merced
    "937": "merced",
    # California — Monterey
    "939": "monterey",
    # California — Ventura
    "930": "ventura", "931": "ventura",
    # California — Chico
    "957": "chico", "958": "chico", "959": "chico", "960": "chico", "961": "chico",
    # New York City
    "100": "newyork", "101": "newyork", "102": "newyork",
    "103": "newyork", "104": "newyork",
    "105": "westchester",
    "110": "longisland", "111": "longisland", "112": "longisland",
    "113": "longisland", "114": "longisland", "115": "longisland",
    "116": "longisland", "117": "longisland", "118": "longisland", "119": "longisland",
    "120": "albany", "121": "albany", "122": "albany", "123": "albany",
    "124": "albany", "125": "albany", "126": "albany", "127": "albany",
    "128": "albany", "129": "albany",
    # Texas — Dallas
    "750": "dallas", "751": "dallas", "752": "dallas", "753": "dallas",
    # Texas — Fort Worth
    "760": "fortworth", "761": "fortworth", "762": "fortworth",
    # Texas — Abilene / misc
    "763": "abilene",
    "764": "killeen",
    "765": "waco", "766": "waco", "767": "waco",
    # Texas — Houston
    "770": "houston", "771": "houston", "772": "houston",
    "773": "houston", "774": "houston", "775": "houston",
    "776": "houston", "777": "houston",
    # Texas — other
    "778": "corpus",
    "779": "brownsville",
    "780": "sanantonio", "781": "sanantonio", "782": "sanantonio",
    "783": "sanantonio", "784": "sanantonio",
    "785": "austin", "786": "austin", "787": "austin",
    "788": "austin", "789": "austin",
    "790": "amarillo", "791": "amarillo",
    "795": "lubbock", "796": "lubbock", "797": "lubbock",
    "798": "midland",
    "799": "elpaso",
    # Florida
    "320": "orlando", "321": "orlando",
    "322": "jacksonville",
    "323": "tallahassee", "325": "tallahassee",
    "324": "destin",
    "326": "gainesville",
    "327": "orlando", "328": "orlando",
    "329": "space-coast",
    "330": "miami", "331": "miami", "332": "miami",
    "333": "broward",
    "334": "swflorida",
    "335": "tampa", "336": "tampa", "337": "tampa",
    "338": "lakeland",
    "339": "sarasota", "342": "sarasota",
    "341": "naples",
    "346": "tampa",
    "347": "ocala",
    "349": "swflorida",
    # Illinois
    "606": "chicago", "607": "chicago", "608": "chicago",
    "609": "bloomington",
    "610": "chicago", "611": "chicago", "612": "chicago",
    "613": "peoria", "614": "peoria",
    "615": "springfieldil", "616": "springfieldil",
    "617": "decatur",
    "618": "southernillinois", "619": "southernillinois",
    # Washington state
    "980": "seattle", "981": "seattle",
    "982": "olympia",
    "983": "seattle", "984": "seattle",
    "985": "bellingham",
    "986": "portland",
    "988": "wenatchee",
    "989": "spokane",
    # Oregon
    "970": "portland", "971": "portland", "972": "portland", "973": "portland",
    "974": "medford",
    "975": "corvallis", "978": "corvallis",
    "976": "eugene", "979": "eugene",
    "977": "roseburg",
    # Colorado
    "800": "denver", "801": "denver", "802": "denver",
    "803": "denver", "804": "denver",
    "805": "boulder", "806": "boulder",
    "807": "pueblo", "810": "pueblo", "811": "pueblo",
    "808": "cosprings", "809": "cosprings", "812": "cosprings",
    "814": "cosprings",
    "816": "steamboatsprings",
    # Arizona
    "850": "phoenix", "851": "phoenix", "852": "phoenix", "853": "phoenix",
    "854": "tucson",
    "856": "tucson", "857": "tucson", "859": "tucson",
    "860": "flagstaff",
    "863": "prescott", "865": "prescott",
    "864": "showlow", "855": "showlow",
    # Georgia
    "300": "atlanta", "301": "atlanta", "302": "atlanta", "303": "atlanta",
    "304": "athens",
    "305": "atlanta", "312": "atlanta", "317": "atlanta",
    "306": "macon", "315": "macon",
    "307": "chattanooga",
    "308": "augusta",
    "309": "albanyga", "310": "albanyga", "318": "albanyga",
    "311": "savannah", "313": "savannah", "314": "savannah",
    "316": "valdosta",
    "319": "columbus",
}

_DEFAULT_SUBDOMAIN = "sfbay"  # fallback — wrong for most zips; extend map above


def _zip_to_subdomain(zip_code: str) -> str:
    """
    Map a US zip code to a Craigslist subdomain.

    ⚠️ VERIFY: extend _ZIP_TO_SUBDOMAIN for your actual target markets.
    """
    prefix = zip_code[:3]
    subdomain = _ZIP_TO_SUBDOMAIN.get(prefix)
    if subdomain is None:
        log.warning(
            "Craigslist: no subdomain mapping for zip=%s (prefix=%s), "
            "falling back to '%s' — add to _ZIP_TO_SUBDOMAIN for accuracy.",
            zip_code, prefix, _DEFAULT_SUBDOMAIN,
        )
        return _DEFAULT_SUBDOMAIN
    return subdomain


# ── Category mapping ──────────────────────────────────────────────────────────
_CATEGORY_MAP: dict[str, str | None] = {
    "furniture":            "furniture",
    "antiques":             "collectibles",
    "appliances":           "electronics",
    "arts+crafts":          "art",
    "auto parts":           "tech",
    "baby+kids":            "toys",
    "barter":               "collectibles",
    "beauty+hlth":          "apparel",
    "bikes":                "tech",
    "boats":                "collectibles",
    "books":                "books",
    "business":             "tech",
    "cars+trucks":          "collectibles",
    "cds/dvd/vhs":          "collectibles",
    "cell phones":          "electronics",
    "clothes+acc":          "clothing",
    "collectibles":         "collectibles",
    "computer parts":       "computing",
    "computers":            "computing",
    "electronics":          "electronics",
    "farm+garden":          "plants",
    "free":                 None,
    "garage sale":          None,
    "general":              None,
    "household":            "furniture",
    "jewelry":              "collectibles",
    "materials":            None,
    "motorcycles":          "collectibles",
    "musical instruments":  "collectibles",
    "photo+video":          "photography",
    "rvs+camp":             "collectibles",
    "sporting":             "collectibles",
    "tickets":              None,
    "tools":                "tech",
    "toys+games":           "toys",
    "video gaming":         "electronics",
    "wanted":               None,
}


def _id_from_url(url: str) -> str:
    """Extract a numeric CL listing ID from a URL like .../123456789.html"""
    match = re.search(r"/(\d{7,})", url)
    return match.group(1) if match else ""


class CraigslistAdapter(SourceAdapter):
    """
    Adapter for Craigslist free listings via direct HTTP (no browser needed).

    Craigslist serves server-rendered HTML, so we skip Playwright entirely
    and issue a plain HTTPX GET with a realistic User-Agent.

    ⚠️ HTML selectors and zip→subdomain mapping need first-run verification.
    """

    @property
    def source_name(self) -> str:
        return "Craigslist"

    async def fetch(
        self,
        zip_code: str,
        browser: "BrowserSession",  # not used — kept for interface compatibility
    ) -> list[NormalizedListing]:
        """
        Fetch free listings from the Craigslist region matching `zip_code`.
        """
        subdomain = _zip_to_subdomain(zip_code)
        # ⚠️ VERIFY: correct URL path for the free-stuff section in your region.
        # Common alternatives: /search/fre  /search/zip  /free
        url = f"https://{subdomain}.craigslist.org/search/{_FREE_SECTION}"
        params: dict[str, str] = {
            # ⚠️ VERIFY: Craigslist may not honour postal_code query param;
            # subdomain-level proximity may be the only geographic filter.
            "postal": zip_code,
            "search_distance": "5",  # miles — ⚠️ VERIFY param name
        }

        log.info("Craigslist: fetching zip=%s subdomain=%s", zip_code, subdomain)

        try:
            async with httpx.AsyncClient(
                headers={"User-Agent": _USER_AGENT},
                follow_redirects=True,
                timeout=30.0,
            ) as client:
                resp = await client.get(url, params=params)
            resp.raise_for_status()
        except httpx.HTTPError as exc:
            log.error("Craigslist: HTTP error for zip=%s: %s", zip_code, exc)
            return []

        html = resp.text
        log.debug(
            "Craigslist raw HTML (%d chars) for zip=%s: %s...",
            len(html), zip_code, html[:300],
        )

        listings = self._parse_html(html, zip_code)
        log.info(
            "Craigslist: %d listing(s) for zip=%s (subdomain=%s)",
            len(listings), zip_code, subdomain,
        )
        return listings

    def _parse_html(self, html: str, zip_code: str) -> list[NormalizedListing]:
        """
        Parse Craigslist search-results HTML into NormalizedListing objects.

        ⚠️ VERIFY: Craigslist's HTML structure is stable but not guaranteed.
        On first run, inspect the raw HTML logged at DEBUG level.

        As of 2024–2025, search results are rendered as:
          <li class="cl-search-result">  (newer site)
          <li class="result-row">        (older site / some regions)
        """
        soup = BeautifulSoup(html, "html.parser")
        results: list[NormalizedListing] = []

        # ⚠️ VERIFY: selector covers both legacy and current CL markup
        rows = soup.select("li.cl-search-result, li.result-row")
        if not rows:
            log.warning(
                "Craigslist: no listing rows found for zip=%s. "
                "CSS selector may need updating — inspect raw HTML at DEBUG level.",
                zip_code,
            )
            return results

        for row in rows:
            listing = self._parse_row(row, zip_code)
            if listing is not None:
                results.append(listing)

        return results

    def _parse_row(self, row: Any, zip_code: str) -> NormalizedListing | None:
        """Parse one search-result row element."""
        log.debug("Craigslist raw row HTML: %s", str(row)[:300])

        # ── ID ──
        # ⚠️ VERIFY: ID may be in data-id/data-pid attribute or extracted from URL
        listing_id = (
            str(row.get("data-id") or "").strip()
            or str(row.get("data-pid") or "").strip()
        )
        link = row.select_one("a.cl-app-anchor, a.result-title")
        href = link.get("href", "") if link else ""
        if not listing_id:
            listing_id = _id_from_url(href)
        if not listing_id:
            log.warning("Craigslist row missing ID, skipping.")
            return None

        # ── Title ──
        # ⚠️ VERIFY: may be in .label, .result-title, or the <a> text
        title_el = row.select_one(".label, .result-title, a.cl-app-anchor")
        title = (title_el.get_text(strip=True) if title_el else "").strip()
        if not title:
            log.warning("Craigslist listing %s missing title, skipping.", listing_id)
            return None

        # ── Location ──
        # ⚠️ VERIFY: may be in .location, .result-hood, or a <span class="meta">
        location_el = row.select_one(".location, .result-hood, .meta .location")
        city: str | None = None
        if location_el:
            city = location_el.get_text(strip=True).strip("() ").strip() or None

        # ── Date ──
        # ⚠️ VERIFY: may be in <time> element with a 'datetime' attribute
        time_el = row.select_one("time")
        posted_at: str | None = None
        if time_el:
            posted_at = (
                str(time_el.get("datetime") or "").strip()
                or time_el.get_text(strip=True)
                or None
            )

        # ── Image ──
        # ⚠️ VERIFY: thumbnails not always present on CL search results
        img_el = row.select_one("img")
        image_url: str | None = None
        if img_el:
            raw_src = str(img_el.get("src") or img_el.get("data-src") or "").strip()
            image_url = raw_src or None

        # ── Category ──
        # ⚠️ VERIFY: CL free section often has no sub-category in search results
        cat_el = row.select_one(".category, .result-category")
        raw_cat = cat_el.get_text(strip=True).lower() if cat_el else ""
        category = _CATEGORY_MAP.get(raw_cat)

        return NormalizedListing(
            source=self.source_name,
            source_listing_id=listing_id,
            title=title,
            description=None,  # not available from search results page
            category=category,
            city=city,
            latitude=None,
            longitude=None,
            zip=zip_code,
            image_url=image_url,
            posted_at=posted_at,
            price="Free",
            status="available",
        )
