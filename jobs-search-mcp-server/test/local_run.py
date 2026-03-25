from jobspy_mcp_server.jobspy_scrapers import scrape_jobs

jobs = scrape_jobs(
    site_name=["linkedin"],
    search_term="software engineer",
    location="New York, NY",
    results_wanted=5,
    linkedin_fetch_description=False,
)

print(jobs[["title", "company", "location", "job_url"]].head(10).to_string(index=False))