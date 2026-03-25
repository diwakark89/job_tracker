#!/usr/bin/env python3
"""
Standalone CLI for JobSpy job search.

Provides a direct command-line interface for searching jobs without MCP protocol
overhead. Designed to be called by OpenClaw's exec tool or any shell.

Usage:
    jobspy-search "software engineer" --location "NYC" --sites linkedin,indeed
    jobspy-search "data scientist" --remote --results 5 --hours-old 48
"""

import argparse
import sys

import pandas as pd

from jobspy_mcp_server.jobspy_scrapers import scrape_jobs

VALID_SITES = [
    "linkedin", "indeed", "glassdoor", "zip_recruiter",
    "google", "bayt", "naukri", "stepstone", "xing",
]


def main() -> None:
    """Entry point for the jobspy-search CLI."""
    parser = argparse.ArgumentParser(
        prog="jobspy-search",
        description="Search for jobs across multiple job boards",
    )
    parser.add_argument(
        "search_term",
        help="Job search keywords (e.g. 'software engineer', 'data scientist')",
    )
    parser.add_argument(
        "--location",
        default=None,
        help="Job location (e.g. 'San Francisco, CA', 'Remote')",
    )
    parser.add_argument(
        "--sites",
        default="linkedin",
        help="Comma-separated list of job boards (default: linkedin). "
        f"Valid: {', '.join(VALID_SITES)}",
    )
    parser.add_argument(
        "--results",
        type=int,
        default=10,
        help="Number of results (1-15, default: 10)",
    )
    parser.add_argument(
        "--job-type",
        default=None,
        choices=["fulltime", "parttime", "internship", "contract"],
        help="Type of employment",
    )
    parser.add_argument(
        "--remote",
        action="store_true",
        help="Filter for remote jobs only",
    )
    parser.add_argument(
        "--hours-old",
        type=int,
        default=24,
        help="Only jobs posted within last N hours (1-72, default: 24)",
    )
    parser.add_argument(
        "--distance",
        type=int,
        default=50,
        help="Search radius in miles (1-100, default: 50)",
    )
    parser.add_argument(
        "--country",
        default="usa",
        help="Country for Indeed/Glassdoor (default: usa)",
    )
    parser.add_argument(
        "--fetch-descriptions",
        action="store_true",
        help="Fetch full job descriptions from LinkedIn/Naukri (slower)",
    )

    args = parser.parse_args()

    # Parse and validate sites
    site_list = [s.strip() for s in args.sites.split(",") if s.strip()]
    invalid = [s for s in site_list if s not in VALID_SITES]
    if invalid:
        print(f"Error: Invalid sites: {invalid}. Valid: {VALID_SITES}", file=sys.stderr)
        sys.exit(1)
    if not site_list:
        print("Error: At least 1 site must be specified.", file=sys.stderr)
        sys.exit(1)
    if len(site_list) > 3:
        print(f"Error: Maximum 3 sites per request, got {len(site_list)}.", file=sys.stderr)
        sys.exit(1)

    # Clamp numeric parameters to safe ranges
    results_wanted = min(max(args.results, 1), 15)
    hours_old = min(max(args.hours_old, 1), 72)
    distance = min(max(args.distance, 1), 100)

    try:
        jobs_df = scrape_jobs(
            site_name=site_list,
            search_term=args.search_term,
            location=args.location,
            results_wanted=results_wanted,
            job_type=args.job_type,
            is_remote=args.remote,
            hours_old=hours_old,
            distance=distance,
            country_indeed=args.country,
            linkedin_fetch_description=args.fetch_descriptions,
            verbose=0,
            description_format="markdown",
        )
    except Exception as e:
        print(f"Error: Job search failed: {e}", file=sys.stderr)
        sys.exit(1)

    if jobs_df.empty:
        print("No jobs found matching your criteria. Try broadening your search.")
        sys.exit(0)

    # Format output as markdown
    print(f"## Found {len(jobs_df)} jobs for '{args.search_term}'")
    if args.location:
        print(f"**Location:** {args.location}")
    print(f"**Sites:** {', '.join(site_list)}\n")

    for i, (_, job) in enumerate(jobs_df.iterrows(), 1):
        lines = [f"### {i}. {job.get('title', 'N/A')}"]
        lines.append(f"**Company:** {job.get('company', 'N/A')}")
        lines.append(f"**Location:** {job.get('location', 'N/A')}")
        lines.append(f"**Source:** {str(job.get('site', 'N/A')).title()}")

        if pd.notna(job.get("job_type")):
            lines.append(f"**Type:** {job.get('job_type')}")
        if pd.notna(job.get("date_posted")):
            lines.append(f"**Posted:** {job.get('date_posted')}")
        if pd.notna(job.get("min_amount")) and pd.notna(job.get("max_amount")):
            currency = job.get("currency", "USD")
            interval = job.get("interval", "yearly")
            lines.append(
                f"**Salary:** ${job.get('min_amount'):,.0f} - "
                f"${job.get('max_amount'):,.0f} {currency} ({interval})"
            )
        if job.get("is_remote"):
            lines.append("**Remote:** Yes")
        if pd.notna(job.get("job_url")):
            lines.append(f"**Apply:** {job.get('job_url')}")
        if pd.notna(job.get("description")):
            desc = str(job.get("description"))
            if len(desc) > 300:
                desc = desc[:300] + "..."
            lines.append(f"**Description:** {desc}")
        if pd.notna(job.get("skills")):
            lines.append(f"**Skills:** {job.get('skills')}")

        print("\n".join(lines))
        print()


if __name__ == "__main__":
    main()
