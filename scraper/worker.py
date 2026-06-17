"""
worker.py (project-root entrypoint) — convenience shim.

Run as:
  python worker.py [--once] [--log-level DEBUG|INFO|WARNING]

Or as a module (preferred, ensures package imports work):
  python -m freebox_scraper.worker [--once]
"""
from freebox_scraper.worker import main

if __name__ == "__main__":
    main()
