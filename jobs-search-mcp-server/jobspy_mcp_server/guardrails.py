"""
Centralized search-constraint constants (ADR-001).

Adjust any value here to tighten or relax limits across the entire
MCP tool surface.  Every guardrail used in ``server.py`` and validated
in the test suite is sourced from this single file.
"""

# ── results_wanted ──────────────────────────────────────────────
RESULTS_WANTED_MIN = 1
RESULTS_WANTED_MAX = 15
RESULTS_WANTED_DEFAULT = 10

# ── hours_old (job recency window) ──────────────────────────────
HOURS_OLD_MIN = 1
HOURS_OLD_MAX = 72
HOURS_OLD_DEFAULT = 24

# ── distance (search radius in miles) ──────────────────────────
DISTANCE_MIN = 1
DISTANCE_MAX = 100
DISTANCE_DEFAULT = 50

# ── offset (pagination) ────────────────────────────────────────
OFFSET_MIN = 0
OFFSET_MAX = 1000
OFFSET_DEFAULT = 0

# ── site_name ───────────────────────────────────────────────────
SITES_MIN = 1
SITES_MAX = 3
SITES_DEFAULT: list[str] = ["linkedin"]
VALID_SITES: list[str] = [
    "linkedin",
    "indeed",
    "glassdoor",
    "zip_recruiter",
    "google",
    "bayt",
    "naukri",
    "stepstone",
    "xing",
]
