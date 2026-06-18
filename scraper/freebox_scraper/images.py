"""
images.py — enrich listings with a primary photo from the source detail page.

Some sources omit images from their search results: Craigslist's JS-free search
list carries no <img> at all (the photos live on each listing's detail page,
exposed as <meta property="og:image">). This module fetches the detail page for
any listing that has a `url` but no `image_url` and fills in og:image.

Listings that already have an image (e.g. OfferUp) are skipped, so this only
costs one extra GET per still-imageless, kept listing. Runs concurrently under
IMAGE_CONCURRENCY; failures are non-fatal (the listing simply keeps no image).
"""
from __future__ import annotations

import asyncio
import logging

import httpx
from bs4 import BeautifulSoup

from freebox_scraper import config
from freebox_scraper.adapters.base import NormalizedListing

log = logging.getLogger(__name__)

_USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/124.0.0.0 Safari/537.36"
)


def _extract_og_image(html: str) -> str | None:
    soup = BeautifulSoup(html, "html.parser")
    for sel in ('meta[property="og:image"]', 'meta[name="og:image"]', 'meta[name="twitter:image"]'):
        tag = soup.select_one(sel)
        if tag and tag.get("content"):
            return tag["content"].strip() or None
    return None


async def enrich_images(listings: list[NormalizedListing]) -> None:
    """Fill image_url (in place) for listings that have a url but no image."""
    targets = [l for l in listings if not l.image_url and l.url]
    if not targets:
        return

    sem = asyncio.Semaphore(config.IMAGE_CONCURRENCY)
    headers = {"User-Agent": _USER_AGENT, "Accept-Language": "en-US,en;q=0.9"}

    async with httpx.AsyncClient(
        headers=headers, follow_redirects=True, timeout=20.0, proxy=config.PROXY_URL or None
    ) as client:
        async def one(listing: NormalizedListing) -> None:
            async with sem:
                try:
                    resp = await client.get(listing.url)
                    if resp.status_code == 200:
                        img = _extract_og_image(resp.text)
                        if img:
                            listing.image_url = img
                except Exception as exc:
                    log.debug("Image enrich failed for %s: %s", listing.url, exc)

        await asyncio.gather(*(one(l) for l in targets))

    got = sum(1 for l in targets if l.image_url)
    log.info("Image enrich: %d/%d imageless listings got a photo", got, len(targets))
