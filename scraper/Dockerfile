# Container image for the Freebox scraper — alternative to the GitHub Actions cron
# for always-on container hosts (Fly.io, Render, a VPS, Cloud Run job, etc.).
# Default CMD runs the worker in LOOP mode (one pass, then sleep, repeat).
#
# Craigslist + OfferUp are browser-free httpx, so no Chromium is bundled — keeping
# the image small. If Facebook Marketplace is re-enabled (requires_browser=True),
# switch the base image to one with Playwright + run `playwright install chromium`.
FROM python:3.12-slim

WORKDIR /app

# Install deps first for layer caching.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY freebox_scraper/ ./freebox_scraper/

# Config comes from the environment (set SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY,
# PERPLEXITY_API_KEY, and optionally PROXY_URL / APIFY_TOKEN at runtime — never bake
# secrets into the image).
ENV PYTHONUNBUFFERED=1

# Loop mode: continuous passes with LOOP_SLEEP_SECONDS between them.
CMD ["python", "-m", "freebox_scraper.worker"]
