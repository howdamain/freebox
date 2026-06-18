"""
supabase_io.py — All reads/writes to Supabase via the PostgREST REST API.

Uses the SERVICE ROLE key, which bypasses RLS. Never expose this key client-side.

Endpoints used:
  GET  /crawl_targets   — pull due work
  POST /listings        — upsert normalized listings (on_conflict=source,source_listing_id)
  PATCH /crawl_targets  — update crawl metadata after success or error
"""
from __future__ import annotations

import logging
from collections import defaultdict
from datetime import datetime, timezone, timedelta
from typing import Any

import requests

from freebox_scraper import config

log = logging.getLogger(__name__)

_BASE = f"{config.SUPABASE_URL}/rest/v1"
_HEADERS = config.SUPABASE_HEADERS


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _next_due(frequency_tier: str) -> str:
    minutes = config.FREQUENCY_MINUTES.get(frequency_tier, 1440)
    due = datetime.now(timezone.utc) + timedelta(minutes=minutes)
    return due.isoformat()


# ── Read ─────────────────────────────────────────────────────────────────────

def get_due_targets(limit: int = config.TARGETS_PER_PASS) -> list[dict[str, Any]]:
    """
    Return up to `limit` active crawl_targets whose next_due_at is in the past,
    ordered by next_due_at ascending (oldest overdue first).
    """
    now = _now_iso()
    resp = requests.get(
        f"{_BASE}/crawl_targets",
        headers=_HEADERS,
        params={
            "active": "eq.true",
            "next_due_at": f"lte.{now}",
            "order": "next_due_at.asc",
            "limit": str(limit),
        },
        timeout=30,
    )
    resp.raise_for_status()
    targets: list[dict[str, Any]] = resp.json()
    log.info("Got %d due target(s) from Supabase.", len(targets))
    return targets


# ── Write ─────────────────────────────────────────────────────────────────────

def upsert_listings(rows: list[dict[str, Any]]) -> int:
    """
    Upsert a batch of normalized listing dicts into the `listings` table.
    Conflict key: (source, source_listing_id) → merge-duplicates.
    Returns the count of rows sent (not necessarily inserted vs updated).

    PostgREST bulk insert requires every object in one POST to have the SAME set
    of keys (PGRST102 "All object keys must match"). Because to_db_row() omits
    None-valued optional fields, rows can have differing key sets (e.g. some have
    image_url, some don't). We group rows by their key set and POST each uniform
    group separately — this satisfies PGRST102 while preserving the deliberate
    "don't write NULL over existing data" behaviour of omitting None fields.
    """
    if not rows:
        return 0

    # Stamp last_seen_at on every row so re-crawled listings stay fresh and ones
    # that fall off their source can be expired (see expire_stale_listings).
    seen = _now_iso()
    for row in rows:
        row["last_seen_at"] = seen

    groups: dict[frozenset[str], list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        groups[frozenset(row.keys())].append(row)

    total = 0
    for group in groups.values():
        _post_listings(group)
        total += len(group)
    log.info("Upserted %d listing(s) across %d key-group(s).", total, len(groups))
    return total


def expire_stale_listings(days: int) -> int:
    """
    Mark 'available' listings not re-seen within `days` as 'expired' so the feed
    ages out items that have fallen off their source (rotated off / sold / pulled).
    Returns the number expired. Safe to call every pass.
    """
    cutoff = (datetime.now(timezone.utc) - timedelta(days=days)).isoformat()
    resp = requests.patch(
        f"{_BASE}/listings",
        headers={**_HEADERS, "Prefer": "count=exact,return=minimal"},
        params={"status": "eq.available", "last_seen_at": f"lt.{cutoff}"},
        json={"status": "expired"},
        timeout=30,
    )
    resp.raise_for_status()
    count = 0
    content_range = resp.headers.get("content-range", "")
    if "/" in content_range:
        tail = content_range.rsplit("/", 1)[-1]
        if tail.isdigit():
            count = int(tail)
    log.info("Expired %d stale listing(s) (>%dd since last seen).", count, days)
    return count


def _post_listings(rows: list[dict[str, Any]]) -> None:
    """POST one uniform-key group of listing rows (upsert on merge-duplicates)."""
    resp = requests.post(
        f"{_BASE}/listings",
        headers={
            **_HEADERS,
            "Prefer": "resolution=merge-duplicates,return=minimal",
        },
        params={"on_conflict": "source,source_listing_id"},
        json=rows,
        timeout=60,
    )
    try:
        resp.raise_for_status()
    except requests.HTTPError:
        # Surface the response body for debugging (Postgres constraint violations
        # return a JSON error with the offending detail).
        log.error(
            "Supabase upsert failed: HTTP %d — %s",
            resp.status_code,
            resp.text[:500],
        )
        raise


def mark_crawled(zip_code: str, source: str, result_count: int, frequency_tier: str) -> None:
    """
    Update crawl_targets after a successful crawl:
    - Reset consecutive_errors to 0
    - Record last_crawled_at = now
    - Compute next_due_at from frequency_tier
    - Store last_result_count
    """
    payload = {
        "last_crawled_at": _now_iso(),
        "next_due_at": _next_due(frequency_tier),
        "last_result_count": result_count,
        "consecutive_errors": 0,
    }
    _patch_target(zip_code, source, payload)
    log.info(
        "Marked crawled: zip=%s source=%s count=%d tier=%s",
        zip_code, source, result_count, frequency_tier,
    )


def mark_error(zip_code: str, source: str, frequency_tier: str) -> None:
    """
    Increment consecutive_errors and push next_due_at forward (so a broken
    target doesn't spam the top of the queue on every pass).
    """
    # First fetch the current error count so we can increment it.
    resp = requests.get(
        f"{_BASE}/crawl_targets",
        headers=_HEADERS,
        params={
            "zip": f"eq.{zip_code}",
            "source": f"eq.{source}",
            "select": "consecutive_errors",
            "limit": "1",
        },
        timeout=15,
    )
    current_errors: int = 0
    if resp.ok:
        rows = resp.json()
        if rows:
            current_errors = rows[0].get("consecutive_errors", 0) or 0

    payload = {
        "last_crawled_at": _now_iso(),
        # Back off: push next attempt by one full frequency cycle so errors
        # don't saturate the queue.
        "next_due_at": _next_due(frequency_tier),
        "consecutive_errors": current_errors + 1,
    }
    _patch_target(zip_code, source, payload)
    log.warning(
        "Marked error: zip=%s source=%s consecutive_errors=%d",
        zip_code, source, current_errors + 1,
    )


def _patch_target(zip_code: str, source: str, payload: dict[str, Any]) -> None:
    resp = requests.patch(
        f"{_BASE}/crawl_targets",
        headers={**_HEADERS, "Prefer": "return=minimal"},
        params={
            "zip": f"eq.{zip_code}",
            "source": f"eq.{source}",
        },
        json=payload,
        timeout=15,
    )
    resp.raise_for_status()
