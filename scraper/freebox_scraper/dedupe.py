"""
dedupe.py — Intra-batch cross-source deduplication.

Database-level dedup (source, source_listing_id) handles same-source duplicates.
This module handles cross-source duplicates that may appear in the same worker pass:
the same physical item posted on both Craigslist and Facebook Marketplace.

Algorithm:
  For each pair of listings with the same zip, compare normalized titles using
  difflib.SequenceMatcher. If similarity ratio > TITLE_SIMILARITY_THRESHOLD,
  treat them as duplicates and keep only the one with the highest est_profit.

Complexity: O(n²) per zip bucket. For typical batch sizes (<500/zip) this is fine.
For very large batches, consider LSH or a faster fuzzy-match approach.
"""
from __future__ import annotations

import logging
import unicodedata
import re
from collections import defaultdict
from difflib import SequenceMatcher

from freebox_scraper.adapters.base import NormalizedListing

log = logging.getLogger(__name__)

TITLE_SIMILARITY_THRESHOLD: float = 0.85


def _normalize_title(title: str) -> str:
    """Lowercase, strip punctuation, collapse whitespace, NFKC-normalize."""
    title = unicodedata.normalize("NFKC", title).lower()
    title = re.sub(r"[^\w\s]", " ", title)
    title = re.sub(r"\s+", " ", title).strip()
    return title


def _similarity(a: str, b: str) -> float:
    return SequenceMatcher(None, a, b, autojunk=False).ratio()


def dedupe_batch(items: list[NormalizedListing]) -> list[NormalizedListing]:
    """
    Remove cross-source near-duplicates from a batch of NormalizedListing objects.

    - Groups by zip first (items from different zips are never duplicates).
    - Within each zip group, uses fuzzy title matching.
    - When duplicates are found, keeps the one with the highest est_profit.
      Ties are broken by preferring the first item encountered.

    Returns the deduplicated list (order not guaranteed within zip groups).
    """
    if not items:
        return items

    # Group by zip
    by_zip: dict[str, list[NormalizedListing]] = defaultdict(list)
    for item in items:
        by_zip[item.zip or "__no_zip__"].append(item)

    kept: list[NormalizedListing] = []
    total_dropped = 0

    for zip_code, group in by_zip.items():
        # Build normalized titles once per item
        norm_titles = [_normalize_title(item.title or "") for item in group]

        dropped: set[int] = set()

        for i in range(len(group)):
            if i in dropped:
                continue
            for j in range(i + 1, len(group)):
                if j in dropped:
                    continue
                # Only cross-source duplicates — same source already handled by DB
                if group[i].source == group[j].source:
                    continue
                ratio = _similarity(norm_titles[i], norm_titles[j])
                if ratio >= TITLE_SIMILARITY_THRESHOLD:
                    # Drop the lower-profit one; ties drop j
                    profit_i = group[i].est_profit or 0
                    profit_j = group[j].est_profit or 0
                    loser = j if profit_i >= profit_j else i
                    dropped.add(loser)
                    log.debug(
                        "Deduped cross-source: zip=%s ratio=%.2f  '%s' (%s) vs '%s' (%s) → dropped index %d",
                        zip_code,
                        ratio,
                        group[i].title,
                        group[i].source,
                        group[j].title,
                        group[j].source,
                        loser,
                    )

        zip_kept = [item for idx, item in enumerate(group) if idx not in dropped]
        kept.extend(zip_kept)
        n_dropped = len(dropped)
        total_dropped += n_dropped
        if n_dropped:
            log.info(
                "Cross-source dedupe: zip=%s dropped %d/%d items.",
                zip_code, n_dropped, len(group),
            )

    if total_dropped:
        log.info(
            "Cross-source dedupe total: %d item(s) dropped from batch of %d.",
            total_dropped, len(items),
        )

    return kept
