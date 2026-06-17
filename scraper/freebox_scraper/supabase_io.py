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
    """
    if not rows:
        return 0

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
    except requests.HTTPError as exc:
        # Surface the response body for debugging (Postgres constraint violations
        # return a JSON error with the offending detail).
        log.error(
            "Supabase upsert failed: HTTP %d — %s",
            resp.status_code,
            resp.text[:500],
        )
        raise
    log.info("Upserted %d listing(s).", len(rows))
    return len(rows)


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
