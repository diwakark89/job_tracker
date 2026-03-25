from __future__ import annotations

import random
import time
from urllib.parse import quote_plus

from bs4 import BeautifulSoup

from jobspy_mcp_server.jobspy_scrapers.model import (
    Scraper,
    ScraperInput,
    Site,
    JobPost,
    JobResponse,
)
from jobspy_mcp_server.jobspy_scrapers.stepstone.constant import headers
from jobspy_mcp_server.jobspy_scrapers.stepstone.util import parse_location
from jobspy_mcp_server.jobspy_scrapers.exception import StepstoneException
from jobspy_mcp_server.jobspy_scrapers.util import create_logger, create_session

log = create_logger("Stepstone")


class StepstoneScraper(Scraper):
    base_url = "https://www.stepstone.de"
    delay = 2
    band_delay = 3

    def __init__(
        self, proxies: list[str] | str | None = None, ca_cert: str | None = None, user_agent: str | None = None
    ):
        super().__init__(Site.STEPSTONE, proxies=proxies, ca_cert=ca_cert, user_agent=user_agent)
        self.scraper_input = None
        self.session = None

    def scrape(self, scraper_input: ScraperInput) -> JobResponse:
        self.scraper_input = scraper_input
        self.session = create_session(
            proxies=self.proxies, ca_cert=self.ca_cert, is_tls=False, has_retry=True
        )
        if self.user_agent:
            headers["user-agent"] = self.user_agent
        self.session.headers.update(headers)

        job_list: list[JobPost] = []
        page = 1
        results_wanted = scraper_input.results_wanted or 10

        while len(job_list) < results_wanted:
            log.info(f"Fetching Stepstone jobs page {page}")
            job_elements = self._fetch_jobs(scraper_input.search_term, scraper_input.location, page)
            if not job_elements:
                break

            initial_count = len(job_list)
            for job in job_elements:
                try:
                    job_post = self._extract_job_info(job)
                    if job_post:
                        job_list.append(job_post)
                        if len(job_list) >= results_wanted:
                            break
                except Exception as e:
                    log.error(f"Stepstone: Error extracting job info: {str(e)}")
                    continue

            if len(job_list) == initial_count:
                log.info(f"No new jobs found on page {page}. Ending pagination.")
                break

            page += 1
            time.sleep(random.uniform(self.delay, self.delay + self.band_delay))

        job_list = job_list[: scraper_input.results_wanted]
        return JobResponse(jobs=job_list)

    def _fetch_jobs(self, query: str, location: str | None, page: int) -> list | None:
        """Fetches job listing elements from Stepstone search results page."""
        try:
            params = f"?q={quote_plus(query)}&page={page}"
            if location:
                params += f"&li={quote_plus(location)}"
            if self.scraper_input and self.scraper_input.distance:
                params += f"&radius={self.scraper_input.distance}"

            url = f"{self.base_url}/jobs{params}"
            response = self.session.get(url)
            response.raise_for_status()
            soup = BeautifulSoup(response.text, "html.parser")

            # Stepstone uses article elements or divs with data-at attributes for job cards
            job_listings = soup.find_all("article", attrs={"data-at": "job-item"})
            if not job_listings:
                job_listings = soup.find_all("article", class_=lambda c: c and "job" in c.lower() if c else False)
            if not job_listings:
                job_listings = soup.select("[data-testid='job-item-component']")

            log.debug(f"Found {len(job_listings)} job listing elements")
            return job_listings
        except Exception as e:
            log.error(f"Stepstone: Error fetching jobs - {str(e)}")
            return None

    def _extract_job_info(self, job: BeautifulSoup) -> JobPost | None:
        """Extracts job information from a single Stepstone job listing element."""
        # Extract title from heading element
        title_tag = job.find(["h2", "h3"])
        if not title_tag:
            return None

        job_title = title_tag.get_text(strip=True)
        if not job_title:
            return None

        # Extract job URL
        job_url = self._extract_job_url(job)
        if not job_url:
            return None

        # Extract company name
        company_tag = job.find("span", attrs={"data-at": "job-item-company-name"})
        if not company_tag:
            company_tag = job.find(
                "span", class_=lambda c: c and "company" in c.lower() if c else False
            )
        company_name = company_tag.get_text(strip=True) if company_tag else None

        # Extract location
        location_tag = job.find("span", attrs={"data-at": "job-item-location"})
        if not location_tag:
            location_tag = job.find(
                "span", class_=lambda c: c and "location" in c.lower() if c else False
            )
        location_text = location_tag.get_text(strip=True) if location_tag else None
        location_obj = parse_location(location_text)

        job_id = f"stepstone-{abs(hash(job_url))}"

        return JobPost(
            id=job_id,
            title=job_title,
            company_name=company_name,
            location=location_obj,
            job_url=job_url,
        )

    def _extract_job_url(self, job: BeautifulSoup) -> str | None:
        """Extracts the job detail URL from a job listing element."""
        a_tag = job.find("a", href=True)
        if a_tag:
            href = a_tag["href"].strip()
            if href.startswith("http"):
                return href
            return self.base_url + href
        return None
