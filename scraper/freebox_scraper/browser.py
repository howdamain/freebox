"""
browser.py — Async Playwright session manager for DIY headless scraping.

Design goals
────────────
• GUEST / cookieless context per run — no login, no saved state.  Logging in
  would produce faster results but also a near-instant ban on Facebook.  Guest
  mode keeps sessions disposable and interchangeable.

• Anti-detection init script — injected before every page load to mask the
  obvious `navigator.webdriver` flag and give the browser a plausible
  fingerprint.  This is intentionally lightweight (no heavy stealth library
  dependency) because Facebook's detection is focused on behavioural signals
  more than static fingerprints.

• Human telemetry — random delays between navigations/scrolls to avoid
  sub-second pattern detection.

• capture_graphql() — the core primitive shared by Facebook and OfferUp
  adapters.  It navigates to a URL, intercepts all network responses, collects
  any that look like GraphQL/API JSON payloads, and scrolls the page N times
  to trigger lazy-loaded batch requests.

Usage (from an async context)
─────────────────────────────
    async with BrowserSession() as session:
        payloads = await session.capture_graphql(
            url="https://www.facebook.com/marketplace/...",
            match_substrings=["marketplace_search", "MarketplaceProductItem"],
            scroll_passes=3,
        )
"""
from __future__ import annotations

import asyncio
import json
import logging
import random
from typing import Any

from playwright.async_api import (
    async_playwright,
    Browser,
    BrowserContext,
    Page,
    Response,
    Playwright,
)

from freebox_scraper import config

log = logging.getLogger(__name__)

# ── Anti-detect init script ───────────────────────────────────────────────────
# Injected into every page before any scripts run.  Masks the most commonly
# checked automation signals.  More sophisticated detectors (AudioContext
# fingerprinting, WebGL renderer, etc.) require a residential proxy + additional
# mitigations — but those are the domain of the proxy config, not this script.
_ANTI_DETECT_SCRIPT = """
() => {
    // 1. Hide navigator.webdriver
    Object.defineProperty(navigator, 'webdriver', {
        get: () => undefined,
        configurable: true,
    });

    // 2. Spoof a plausible plugins list (empty plugins = headless signal)
    const fakePlugin = (name, filename, desc) => {
        const p = Object.create(Plugin.prototype);
        Object.defineProperty(p, 'name',     { get: () => name });
        Object.defineProperty(p, 'filename', { get: () => filename });
        Object.defineProperty(p, 'description', { get: () => desc });
        Object.defineProperty(p, 'length',   { get: () => 0 });
        return p;
    };
    const pluginArray = [
        fakePlugin('Chrome PDF Plugin', 'internal-pdf-viewer', 'Portable Document Format'),
        fakePlugin('Chrome PDF Viewer',  'mhjfbmdgcfjbbpaeojofohoefgiehjai', ''),
        fakePlugin('Native Client',      'internal-nacl-plugin', ''),
    ];
    Object.defineProperty(navigator, 'plugins', {
        get: () => pluginArray,
        configurable: true,
    });

    // 3. Languages — match a common US-English browser
    Object.defineProperty(navigator, 'languages', {
        get: () => ['en-US', 'en'],
        configurable: true,
    });

    // 4. Canvas fingerprint randomization — add a 1-pixel noise offset
    //    so every session produces a slightly different fingerprint.
    const _noise = Math.floor(Math.random() * 10) - 5;
    const _origGetContext = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function(type, ...args) {
        const ctx = _origGetContext.apply(this, [type, ...args]);
        if (ctx && type === '2d') {
            const _origFillText = ctx.fillText.bind(ctx);
            ctx.fillText = (...a) => { ctx.shadowBlur = _noise; _origFillText(...a); };
        }
        return ctx;
    };
}
"""

# Desktop viewport + User-Agent that matches Chrome on macOS
_VIEWPORT = {"width": 1440, "height": 900}
_USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/124.0.0.0 Safari/537.36"
)


async def human_delay(min_s: float = 2.0, max_s: float = 7.0) -> None:
    """Sleep a random duration in [min_s, max_s] seconds to mimic human pacing."""
    delay = random.uniform(min_s, max_s)
    log.debug("human_delay: sleeping %.2fs", delay)
    await asyncio.sleep(delay)


class BrowserSession:
    """
    Async context manager wrapping a single Playwright Chromium session.

    One BrowserSession is created per worker pass and shared across all
    adapter calls in that pass — this amortises launch overhead (~1–2s)
    while keeping all contexts isolated (no cookie / storage leakage).

    Example
    ───────
        async with BrowserSession() as session:
            payloads = await session.capture_graphql(url, match_substrings, scroll_passes)
    """

    def __init__(self) -> None:
        self._pw: Playwright | None = None
        self._browser: Browser | None = None

    async def __aenter__(self) -> "BrowserSession":
        self._pw = await async_playwright().start()
        launch_kwargs: dict[str, Any] = {
            "headless": config.HEADLESS,
            "args": [
                "--disable-blink-features=AutomationControlled",
                "--no-sandbox",
                "--disable-dev-shm-usage",
            ],
        }
        self._browser = await self._pw.chromium.launch(**launch_kwargs)
        log.info(
            "Browser launched: headless=%s proxy=%s",
            config.HEADLESS,
            "configured" if config.PROXY_URL else "none",
        )
        return self

    async def __aexit__(self, *_: Any) -> None:
        if self._browser:
            await self._browser.close()
        if self._pw:
            await self._pw.stop()
        log.info("Browser closed.")

    async def _new_context(self) -> BrowserContext:
        """
        Create a fresh, cookieless context with anti-detect settings applied.

        Each adapter call gets its own context so cookies / local-storage never
        bleed between sources or between zip targets for the same source.
        """
        if self._browser is None:
            raise RuntimeError("BrowserSession not started — use as async context manager.")

        proxy_settings: dict[str, str] | None = None
        if config.PROXY_URL:
            proxy_settings = {"server": config.PROXY_URL}

        ctx = await self._browser.new_context(
            user_agent=_USER_AGENT,
            viewport=_VIEWPORT,
            locale="en-US",
            timezone_id="America/Los_Angeles",
            # No storage_state → clean cookie/storage jar every time
            proxy=proxy_settings,
        )
        # Inject anti-detect script before ANY page scripts run
        await ctx.add_init_script(_ANTI_DETECT_SCRIPT)
        return ctx

    async def capture_graphql(
        self,
        url: str,
        match_substrings: list[str],
        scroll_passes: int | None = None,
    ) -> list[dict[str, Any]]:
        """
        Navigate to `url` in a fresh guest context, intercept JSON network
        responses, and collect payloads whose body contains any of the strings
        in `match_substrings`.  Scrolls `scroll_passes` times to trigger
        lazy-loaded GraphQL batch requests.

        Parameters
        ──────────
        url
            The marketplace page URL to open.
        match_substrings
            List of strings.  A response body must contain at least one to be
            collected.  E.g. ["marketplace_search", "MarketplaceProductItem"].
        scroll_passes
            How many times to scroll to page bottom.  Defaults to
            config.SCROLL_PASSES.

        Returns
        ───────
        List of parsed JSON dicts captured from matching responses.
        """
        if scroll_passes is None:
            scroll_passes = config.SCROLL_PASSES

        collected: list[dict[str, Any]] = []

        ctx = await self._new_context()
        try:
            page: Page = await ctx.new_page()

            # ── Response interceptor ──────────────────────────────────────────
            async def _on_response(response: Response) -> None:
                resp_url = response.url
                # Quick heuristics: only inspect XHR/fetch responses, skip
                # image/font/CSS/JS resources.
                content_type = response.headers.get("content-type", "")
                if not (
                    "json" in content_type
                    or "graphql" in resp_url
                    or "api" in resp_url
                ):
                    return
                # Only try to read responses that indicate success
                if response.status < 200 or response.status >= 300:
                    return
                try:
                    body_bytes = await response.body()
                    body_text = body_bytes.decode("utf-8", errors="replace")
                except Exception:
                    return  # response already gone / streaming — skip

                # Check if any match substring appears in the raw body text
                if not any(sub in body_text for sub in match_substrings):
                    return

                # Parse JSON — FB sometimes sends line-delimited JSON (one
                # object per line) inside a single response; try both forms.
                try:
                    parsed = json.loads(body_text)
                    if isinstance(parsed, (dict, list)):
                        collected.append(parsed)
                        log.debug(
                            "capture_graphql: matched response from %s (%d bytes)",
                            resp_url[:120], len(body_bytes),
                        )
                        return
                except json.JSONDecodeError:
                    pass

                # Try line-delimited JSON (e.g. FB's "for_you" stream)
                for line in body_text.splitlines():
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        obj = json.loads(line)
                        if isinstance(obj, dict):
                            collected.append(obj)
                    except json.JSONDecodeError:
                        continue

            page.on("response", lambda r: asyncio.ensure_future(_on_response(r)))

            # ── Navigate ──────────────────────────────────────────────────────
            log.info("capture_graphql: navigating to %s", url[:120])
            try:
                await page.goto(url, wait_until="domcontentloaded", timeout=30_000)
            except Exception as exc:
                log.warning("capture_graphql: goto failed (%s) — continuing with what we have.", exc)

            await human_delay(3.0, 6.0)

            # ── Scroll passes ─────────────────────────────────────────────────
            for pass_num in range(scroll_passes):
                log.debug("capture_graphql: scroll pass %d/%d", pass_num + 1, scroll_passes)
                await page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
                await human_delay(2.5, 5.5)

            await page.close()
        finally:
            await ctx.close()

        log.info(
            "capture_graphql: collected %d matching response(s) from %s",
            len(collected), url[:80],
        )
        return collected
