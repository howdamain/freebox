"""
pricing.py — comp-based resale valuation via the Perplexity (Sonar) API.

For each free item, Perplexity does web-grounded research and returns a typical
USED resale value in USD (gross — no fees, the item itself is free).

Concurrency model (see the parallelization audit):
  - Titles are de-duplicated, then chunked (PRICING_BATCH_SIZE per request).
  - Chunks are dispatched CONCURRENTLY via asyncio.gather, but two limits apply:
      * a _RateLimiter that spaces request STARTS to stay under Sonar's per-minute
        ceiling (PERPLEXITY_RPM). A semaphore alone bounds in-flight count, NOT
        arrival rate — at low latency N concurrent slots can fire far over the RPM
        cap, so an explicit rate limiter is the load-bearing control.
      * an asyncio.Semaphore(PRICING_CONCURRENCY) as a secondary in-flight bound.
  - ONE shared httpx.AsyncClient is reused across all chunks (connection pooling).
  - Each chunk retries 429/5xx/timeouts with Retry-After + exponential backoff,
    bounded to PERPLEXITY_MAX_RETRIES; a permanently-failed chunk falls back to {}
    and the worker uses the category baseline for those titles.
  - _cache is mutated only in the parent coroutine AFTER gather (no await between
    read and write), so the populate-then-read sequence stays race-free.

Call this ONCE per pass over the union of all titles (not per target) so chunks
share a single limiter and no title is priced twice.
"""
from __future__ import annotations

import asyncio
import json
import logging
import random
import re

import httpx

from freebox_scraper import config

log = logging.getLogger(__name__)

_API = "https://api.perplexity.ai/chat/completions"
_cache: dict[str, int] = {}


class _RateLimiter:
    """Spaces request starts to at most `rpm` per minute (leaky-bucket-by-spacing)."""

    def __init__(self, rpm: int) -> None:
        self._min_interval = 60.0 / max(1, rpm)
        self._lock = asyncio.Lock()
        self._next_allowed = 0.0

    async def acquire(self) -> None:
        async with self._lock:
            loop = asyncio.get_running_loop()
            now = loop.time()
            wait = self._next_allowed - now
            if wait > 0:
                await asyncio.sleep(wait)
                now = loop.time()
            self._next_allowed = now + self._min_interval


def _backoff(attempt: int) -> float:
    return (2 ** attempt) + random.uniform(0, 1)


def _retry_after(resp: httpx.Response) -> float | None:
    val = resp.headers.get("Retry-After")
    if not val:
        return None
    try:
        return float(val)
    except ValueError:
        return None


async def batch_values(titles: list[str]) -> dict[str, int]:
    """Return {title: usd_resale_estimate} for as many titles as Perplexity prices.

    Safe to call with the union of all titles in a pass; chunks run concurrently
    under a shared rate limiter + concurrency cap.
    """
    if not config.PERPLEXITY_API_KEY:
        return {}
    uniq = [t for t in dict.fromkeys(titles) if t and t not in _cache]
    if not uniq:
        return {t: _cache[t] for t in titles if t in _cache}

    chunks = [
        uniq[i:i + config.PRICING_BATCH_SIZE]
        for i in range(0, len(uniq), config.PRICING_BATCH_SIZE)
    ]
    limiter = _RateLimiter(config.PERPLEXITY_RPM)
    sem = asyncio.Semaphore(config.PRICING_CONCURRENCY)
    failures = 0

    async with httpx.AsyncClient(timeout=90) as client:
        async def run(chunk: list[str]) -> dict[str, int]:
            nonlocal failures
            async with sem:
                try:
                    return await _price_chunk(chunk, client, limiter)
                except Exception as e:
                    failures += 1
                    log.warning("Perplexity pricing batch failed (giving up): %s", e)
                    return {}

        results = await asyncio.gather(*(run(c) for c in chunks))

    # Cache writes happen here, in the parent coroutine, after all tasks resolve —
    # no await between read and write, so the populate-then-read stays race-free.
    for r in results:
        _cache.update(r)

    priced = {t: _cache[t] for t in titles if t in _cache}
    log.info(
        "Perplexity pricing: %d/%d titles priced across %d batch(es), %d batch(es) failed",
        len(priced), len(titles), len(chunks), failures,
    )
    return priced


async def _price_chunk(
    titles: list[str],
    client: httpx.AsyncClient,
    limiter: _RateLimiter,
) -> dict[str, int]:
    numbered = "\n".join(f"{i + 1}. {t}" for i, t in enumerate(titles))
    prompt = (
        "You are a reselling expert pricing curbside/secondhand finds. Every item below is being "
        "GIVEN AWAY FOR FREE — so IGNORE the words 'free', 'curb', or any price written in the "
        "title. Your job is to estimate what the described ITEM itself realistically sells for in "
        "USED condition on eBay or Facebook Marketplace today. NEVER answer 0 just because the "
        "item is free; value it by the item type, brand, and model. Only use a very small number "
        "(or 0) when the item genuinely has no resale market (e.g. scrap, empty boxes, common "
        "cardboard, half-used supplies). Respond with ONLY a JSON array, no prose, each element "
        '{"n": <item number>, "usd": <integer dollars>}.\n\nItems:\n' + numbered
    )
    payload = {
        "model": config.PERPLEXITY_MODEL,
        "messages": [{"role": "user", "content": prompt}],
        "temperature": 0,
    }
    headers = {"Authorization": f"Bearer {config.PERPLEXITY_API_KEY}"}

    content: str | None = None
    for attempt in range(config.PERPLEXITY_MAX_RETRIES + 1):
        await limiter.acquire()
        try:
            resp = await client.post(_API, json=payload, headers=headers)
        except (httpx.TransportError, httpx.TimeoutException):
            if attempt >= config.PERPLEXITY_MAX_RETRIES:
                raise
            await asyncio.sleep(_backoff(attempt))
            continue
        # Retry on rate-limit / transient server errors.
        if resp.status_code == 429 or resp.status_code >= 500:
            if attempt >= config.PERPLEXITY_MAX_RETRIES:
                resp.raise_for_status()
            await asyncio.sleep(_retry_after(resp) or _backoff(attempt))
            continue
        resp.raise_for_status()
        content = resp.json()["choices"][0]["message"]["content"]
        break

    if content is None:
        return {}

    out: dict[str, int] = {}
    for el in _extract_json_array(content):
        n, usd = el.get("n"), el.get("usd")
        if isinstance(n, int) and 1 <= n <= len(titles) and isinstance(usd, (int, float)):
            out[titles[n - 1]] = max(0, int(usd))
    log.info("Perplexity priced %d/%d items in batch", len(out), len(titles))
    return out


def _extract_json_array(text: str) -> list:
    m = re.search(r"\[.*\]", text, re.S)
    if not m:
        return []
    try:
        return json.loads(m.group(0))
    except Exception:
        return []
