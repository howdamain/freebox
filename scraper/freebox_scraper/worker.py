"""
worker.py — Main orchestration loop for the Freebox scraper.

Usage:
  python -m freebox_scraper.worker [--once] [--log-level DEBUG|INFO|WARNING]

Flags:
  --once        Run a single pass then exit (good for cron; default is loop mode)
  --log-level   Logging verbosity (default: INFO)
                Use DEBUG on first run to inspect raw GraphQL payloads and
                verify field mappings in each adapter.

What one pass does:
  1. Pull up to TARGETS_PER_PASS due crawl_targets from Supabase.
  2. Launch ONE shared async Playwright browser session for the pass.
  3. For each target (zip × source), call adapter.fetch(zip, browser).
  4. Apply valuation heuristic; drop items below MIN_PROFIT.
  5. Dedupe the full batch (cross-source near-duplicates).
  6. Upsert remaining listings to Supabase.
  7. Mark target as crawled (success) or mark_error (failure).
  8. Close the browser at the end of the pass.
  9. Stop if MAX_RESULTS_PER_RUN total items have been processed.

Browser lifetime
────────────────
One BrowserSession is created per pass (not per target) to amortise Chromium
launch overhead (~1–2s).  Each adapter call gets its own fresh browser context
(cookieless, no storage leakage) inside the shared browser process.
"""
from __future__ import annotations

import argparse
import asyncio
import contextlib
import logging
import sys
import time
from typing import Any

from freebox_scraper import pricing
from freebox_scraper import images
from freebox_scraper import config
from freebox_scraper.browser import BrowserSession
from freebox_scraper.adapters import ADAPTERS
from freebox_scraper.adapters.base import NormalizedListing
from freebox_scraper import supabase_io
from freebox_scraper import valuation
from freebox_scraper import dedupe

log = logging.getLogger(__name__)

# How long to sleep between passes in loop mode (seconds).
LOOP_SLEEP_SECONDS: int = 300


async def run_pass() -> dict[str, Any]:
    """
    Execute one complete worker pass. Returns a stats dict for logging/monitoring.

    Creates a shared BrowserSession for the duration of the pass and closes it
    when done.
    """
    stats: dict[str, Any] = {
        "targets_processed": 0,
        "targets_errored": 0,
        "items_fetched": 0,
        "items_kept": 0,
        "items_deduped_out": 0,
        "items_upserted": 0,
        "sources": {},
    }

    targets = supabase_io.get_due_targets(limit=config.TARGETS_PER_PASS)
    if not targets:
        log.info("No due targets found. Pass complete.")
        return stats

    # Accumulate all valid listings across all targets for batch dedup at the end.
    batch: list[NormalizedListing] = []
    # Validated-but-not-yet-priced listings per target. Pricing is deferred until
    # after the fetch loop so the whole pass is priced in ONE batched Perplexity
    # call (single-pass — avoids per-target request bursts and the cross-call cache
    # race; see the parallelization audit).
    pending: list[tuple[dict[str, Any], str, list[NormalizedListing]]] = []
    # Track successfully processed targets for mark_crawled after upsert.
    target_result_counts: list[tuple[dict[str, Any], int]] = []

    total_fetched = 0

    # Only launch Chromium if a due target actually needs it (Facebook). With just
    # the browser-free httpx sources (Craigslist, OfferUp) due, skip it entirely so
    # hosted/CI runs stay lightweight (no Playwright/Chromium required).
    needs_browser = any(
        getattr(ADAPTERS.get(t["source"]), "requires_browser", False) for t in targets
    )
    browser_cm = BrowserSession() if needs_browser else contextlib.nullcontext(None)
    if not needs_browser:
        log.info("No browser-backed source due this pass — skipping Chromium launch.")

    async with browser_cm as browser:
        for target in targets:
            zip_code: str = target["zip"]
            source: str = target["source"]
            frequency_tier: str = target.get("frequency_tier", "cold")

            log.info(
                "Processing target: zip=%s source=%s tier=%s",
                zip_code, source, frequency_tier,
            )

            adapter = ADAPTERS.get(source)
            if adapter is None:
                log.error(
                    "No adapter registered for source '%s' (zip=%s). Skipping.",
                    source, zip_code,
                )
                supabase_io.mark_error(zip_code, source, frequency_tier)
                stats["targets_errored"] += 1
                continue

            # ── Fetch ──
            try:
                listings = await adapter.fetch(zip_code, browser)
            except Exception as exc:
                log.error(
                    "Fetch failed for zip=%s source=%s: %s",
                    zip_code, source, exc, exc_info=True,
                )
                supabase_io.mark_error(zip_code, source, frequency_tier)
                stats["targets_errored"] += 1
                continue

            raw_count = len(listings)
            total_fetched += raw_count
            stats["items_fetched"] += raw_count
            stats["sources"].setdefault(source, {"fetched": 0, "kept": 0})
            stats["sources"][source]["fetched"] += raw_count

            # ── Validate required fields ──
            valid: list[NormalizedListing] = []
            for item in listings:
                if not item.is_valid():
                    log.warning(
                        "Dropping listing with missing required fields: source=%s id=%s title=%s",
                        item.source, item.source_listing_id, item.title,
                    )
                    continue
                valid.append(item)

            # Defer valuation: stash validated listings and price them all in one
            # batched Perplexity call after the fetch loop (see `pending` above).
            pending.append((target, source, valid))
            target_result_counts.append((target, raw_count))
            stats["targets_processed"] += 1
            log.info(
                "zip=%s source=%s: fetched=%d valid=%d",
                zip_code, source, raw_count, len(valid),
            )

            # Throughput cap: stop fetching if we've hit the per-run limit
            if total_fetched >= config.MAX_RESULTS_PER_RUN:
                log.info(
                    "Reached MAX_RESULTS_PER_RUN (%d). Stopping target iteration.",
                    config.MAX_RESULTS_PER_RUN,
                )
                break

    # Browser is now closed.

    # ── Valuation + filter (single batched Perplexity pass over ALL titles) ──
    # One call over the union of titles: chunks run concurrently under a shared
    # rate limiter, no title is priced twice, and the cache stays race-free.
    all_titles = [item.title for _, _, valid in pending for item in valid]
    price_map = await pricing.batch_values(all_titles)
    for target, source, valid in pending:
        kept_count = 0
        for item in valid:
            comp = price_map.get(item.title)
            item.est_resale_value = (
                comp if comp is not None
                else valuation.estimate(item.category, item.title)
            )
            item.est_profit = item.est_resale_value  # gross resale value (free item)
            if valuation.passes_value_filter(item.est_profit):
                batch.append(item)
                kept_count += 1
            else:
                log.debug(
                    "Filtered out low-value listing '%s' (est_profit=%d < %d)",
                    item.title[:60], item.est_profit, config.MIN_PROFIT,
                )
        stats["items_kept"] += kept_count
        stats["sources"][source]["kept"] = (
            stats["sources"][source].get("kept", 0) + kept_count
        )

    # ── Cross-source dedupe ──
    pre_dedupe_count = len(batch)
    batch = dedupe.dedupe_batch(batch)
    stats["items_deduped_out"] = pre_dedupe_count - len(batch)

    # ── Image enrichment ──
    # Fill a primary photo for kept listings that lack one (Craigslist's search
    # list omits images; they live on each detail page as og:image). Sources that
    # already provide images (OfferUp) are skipped automatically.
    await images.enrich_images(batch)

    # ── Upsert ──
    if batch:
        rows = [item.to_db_row() for item in batch]
        try:
            upserted = supabase_io.upsert_listings(rows)
            stats["items_upserted"] = upserted
        except Exception as exc:
            log.error("Supabase upsert failed: %s", exc, exc_info=True)
            stats["items_upserted"] = 0

    # ── Expire stale listings (fell off their source) ──
    try:
        stats["items_expired"] = supabase_io.expire_stale_listings(config.LISTING_EXPIRY_DAYS)
    except Exception as exc:
        log.error("Listing expiry failed: %s", exc)
        stats["items_expired"] = 0

    # ── Mark crawled for all successfully processed targets ──
    for target, raw_count in target_result_counts:
        supabase_io.mark_crawled(
            zip_code=target["zip"],
            source=target["source"],
            result_count=raw_count,
            frequency_tier=target.get("frequency_tier", "cold"),
        )

    log.info(
        "Pass complete: targets_processed=%d errored=%d fetched=%d kept=%d "
        "deduped_out=%d upserted=%d",
        stats["targets_processed"],
        stats["targets_errored"],
        stats["items_fetched"],
        stats["items_kept"],
        stats["items_deduped_out"],
        stats["items_upserted"],
    )
    return stats


def main() -> None:
    parser = argparse.ArgumentParser(description="Freebox scraper worker")
    parser.add_argument(
        "--once",
        action="store_true",
        help="Run a single pass and exit (default: run continuously).",
    )
    parser.add_argument(
        "--log-level",
        default="INFO",
        choices=["DEBUG", "INFO", "WARNING", "ERROR"],
        help="Logging verbosity (default: INFO). Use DEBUG on first run to verify field mappings.",
    )
    args = parser.parse_args()

    logging.basicConfig(
        level=getattr(logging, args.log_level),
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
        stream=sys.stdout,
    )

    log.info(
        "Freebox scraper starting — SUPABASE_URL=%s  headless=%s  proxy=%s  "
        "MIN_PROFIT=%d  MAX_RESULTS=%d",
        config.SUPABASE_URL,
        config.HEADLESS,
        "configured" if config.PROXY_URL else "none",
        config.MIN_PROFIT,
        config.MAX_RESULTS_PER_RUN,
    )

    if args.once:
        asyncio.run(run_pass())
        return

    # Loop mode
    log.info("Loop mode active. Sleeping %ds between passes.", LOOP_SLEEP_SECONDS)
    while True:
        try:
            asyncio.run(run_pass())
        except KeyboardInterrupt:
            log.info("Worker stopped by user.")
            break
        except Exception as exc:
            log.error("Unexpected error in pass: %s", exc, exc_info=True)

        log.info("Sleeping %ds until next pass...", LOOP_SLEEP_SECONDS)
        time.sleep(LOOP_SLEEP_SECONDS)


if __name__ == "__main__":
    main()
