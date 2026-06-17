"""
valuation.py — Heuristic resale-value estimation for free items.

For free items: est_profit == est_resale_value (cost basis is $0).

Strategy:
  1. Start from a category baseline (median resale on secondary markets).
  2. Apply keyword multipliers from the title string.
  3. Clamp to a reasonable floor/ceiling.
  4. passes_value_filter() gates the listing against MIN_PROFIT.
"""
from __future__ import annotations

import re

from freebox_scraper import config

# ── Category baselines (USD) ──────────────────────────────────────────────────
# These are conservative median resale estimates on FB Marketplace / OfferUp.
# Adjust as real transaction data accumulates.
_CATEGORY_BASELINE: dict[str, int] = {
    "furniture":    120,
    "electronics":   90,
    "photography":   80,
    "computing":    100,
    "plants":        20,
    "clothing":      15,
    "food":           5,
    "toys":          25,
    "books":         10,
    "art":           40,
    "apparel":       20,
    "collectibles":  60,
    "tech":          85,
}
_DEFAULT_BASELINE: int = 30  # unknown category


# ── Keyword multipliers ───────────────────────────────────────────────────────
# Applied to title (lowercased). Multipliers stack multiplicatively if multiple
# keywords match, but are capped (see _apply_keywords).
_KEYWORD_MULTIPLIERS: list[tuple[re.Pattern[str], float]] = [
    # Premium brands / high-value signals (big bump)
    (re.compile(r"\bherman\s*miller\b"), 4.0),
    (re.compile(r"\beames\b"), 3.5),
    (re.compile(r"\bknoll\b"), 3.0),
    (re.compile(r"\bapple\b"), 2.5),
    (re.compile(r"\bmacbook\b"), 2.5),
    (re.compile(r"\bipad\b"), 2.0),
    (re.compile(r"\biphone\b"), 2.0),
    (re.compile(r"\bdyson\b"), 2.0),
    (re.compile(r"\bleica\b"), 3.0),
    (re.compile(r"\bnikon\b"), 1.8),
    (re.compile(r"\bcanon\b"), 1.8),
    (re.compile(r"\bsonos\b"), 1.8),
    (re.compile(r"\bmidcentury\b"), 2.0),
    (re.compile(r"\bmid.century\b"), 2.0),
    (re.compile(r"\bantique\b"), 1.8),
    (re.compile(r"\bvintage\b"), 1.6),
    (re.compile(r"\brare\b"), 1.4),
    # Condition / completeness signals (modest bump)
    (re.compile(r"\blike\s+new\b"), 1.3),
    (re.compile(r"\bin\s+box\b"), 1.3),
    (re.compile(r"\boriginal\s+box\b"), 1.3),
    (re.compile(r"\bsealed\b"), 1.3),
    # Negative signals (drag down value)
    (re.compile(r"\bbroken\b"), 0.3),
    (re.compile(r"\bdamaged\b"), 0.4),
    (re.compile(r"\bparts\s+only\b"), 0.2),
    (re.compile(r"\bfor\s+parts\b"), 0.2),
    (re.compile(r"\bnot\s+working\b"), 0.25),
    (re.compile(r"\bcracked\b"), 0.35),
]

_MAX_MULTIPLIER: float = 8.0   # cap to avoid absurd values
_VALUE_CEILING: int    = 1500  # no listing valued above $1500 by heuristic alone


def estimate(category: str | None, title: str) -> int:
    """
    Return estimated resale value in whole dollars.

    Parameters
    ----------
    category : str | None
        One of the allowed slug values from the DB schema, or None.
    title : str
        Raw listing title (any case).
    """
    baseline = _CATEGORY_BASELINE.get(category or "", _DEFAULT_BASELINE)
    multiplier = _apply_keywords(title or "")
    raw = baseline * multiplier
    return min(int(raw), _VALUE_CEILING)


def _apply_keywords(title: str) -> float:
    """Compute a composite multiplier from keyword matches on a lowercased title."""
    lower = title.lower()
    multiplier: float = 1.0
    for pattern, factor in _KEYWORD_MULTIPLIERS:
        if pattern.search(lower):
            multiplier *= factor
    # Clamp
    return min(multiplier, _MAX_MULTIPLIER)


def passes_value_filter(value: int) -> bool:
    """Return True if the estimated value meets the minimum profit threshold."""
    return value >= config.MIN_PROFIT
