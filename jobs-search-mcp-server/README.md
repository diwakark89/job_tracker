# JobSpy MCP Server

A self-contained [MCP (Model Context Protocol)](https://modelcontextprotocol.io) server that exposes multi-site job scraping as AI-callable tools. Connect it to any MCP-compatible client (Claude Desktop, Cursor, custom agents) and search millions of job listings across LinkedIn, Indeed, Glassdoor, ZipRecruiter, Google Jobs, and regional boards — all through natural language or structured tool calls.

The scraper implementation is vendored directly in this repository; no external `python-jobspy` package is required.

---

## Features

- **4 MCP tools** — `scrape_jobs_tool`, `get_supported_countries`, `get_supported_sites`, `get_job_search_tips`
- **8 job boards** — LinkedIn, Indeed, Glassdoor, ZipRecruiter, Google Jobs, Bayt (Middle East), Naukri (India), BDJobs (Bangladesh)
- **Rich result output** — title, company, location, salary range, job type, remote flag, apply URL, description preview
- **Advanced filtering** — job type, remote-only, posting age, distance, easy apply, pagination offset
- **Progress reporting** — real-time progress updates via MCP context
- **Docker support** — single-image deployment
- **Fully self-contained** — scraper source vendored under `jobspy_mcp_server/jobspy_scrapers/`

---

## Tech Stack

| Layer | Technology |
|---|---|
| MCP framework | [FastMCP](https://github.com/jlowin/fastmcp) + `mcp>=1.1.0` |
| Language | Python 3.10+ |
| Scraping | Custom HTTP scrapers (requests, tls-client, BeautifulSoup4) |
| Data processing | pandas, numpy |
| Text formatting | markdownify |
| Package manager | [uv](https://github.com/astral-sh/uv) |
| Build backend | Hatchling |
| Testing | pytest |
| Containerisation | Docker |

---

## Project Structure

```
jobspy-mcp-server/
├── jobspy_mcp_server/
│   ├── __init__.py            # Package exports
│   ├── __main__.py            # python -m entrypoint
│   ├── server.py              # MCP tool definitions (scrape_jobs_tool, etc.)
│   └── jobspy_scrapers/       # Vendored scraper implementation
│       ├── __init__.py        # scrape_jobs() public API
│       ├── model.py           # JobPost, Country, and other data models
│       ├── util.py            # Shared utilities
│       ├── exception.py       # Custom exceptions
│       ├── linkedin/          # LinkedIn scraper
│       ├── indeed/            # Indeed scraper
│       ├── glassdoor/         # Glassdoor scraper
│       ├── google/            # Google Jobs scraper
│       ├── ziprecruiter/      # ZipRecruiter scraper
│       ├── bayt/              # Bayt (Middle East) scraper
│       ├── naukri/            # Naukri (India) scraper
│       └── bdjobs/            # BDJobs (Bangladesh) scraper
├── test/
│   ├── test_jobspy_mcp.py     # Unit tests (mocked)
│   └── test_server.py         # MCP protocol smoke test
├── Dockerfile
├── pyproject.toml
├── requirements.lock          # Pinned dependency lockfile
├── THIRD_PARTY_LICENSES.md
└── LICENSE
```

---

## Installation

### Prerequisites

- Python 3.10 or higher
- [`uv`](https://github.com/astral-sh/uv) (recommended) **or** `pip`

### With uv (recommended)

```bash
git clone https://github.com/yourorg/jobspy-mcp-server
cd jobspy-mcp-server
uv sync
```

### With pip

```bash
git clone https://github.com/yourorg/jobspy-mcp-server
cd jobspy-mcp-server
pip install -e .
```

---

## Running the Server

**uv:**
```bash
uv run python -m jobspy_mcp_server
```

**Python:**
```bash
python -m jobspy_mcp_server
```

**Installed script entry point:**
```bash
jobspy-mcp-server
```

**Docker:**
```bash
docker build -t jobspy-mcp-server .
docker run --rm -it jobspy-mcp-server
```

The server communicates over **stdio** and is designed to be launched by an MCP client, not accessed via HTTP.

---

## MCP Client Configuration

### Claude Desktop

Windows config path: `%APPDATA%\Claude\claude_desktop_config.json`  
macOS config path: `~/Library/Application Support/Claude/claude_desktop_config.json`

**Using uv (recommended):**
```json
{
  "mcpServers": {
    "jobspy": {
      "command": "uv",
      "args": ["run", "python", "-m", "jobspy_mcp_server"],
      "cwd": "/absolute/path/to/jobspy-mcp-server"
    }
  }
}
```

**Using installed script:**
```json
{
  "mcpServers": {
    "jobspy": {
      "command": "jobspy-mcp-server"
    }
  }
}
```

**Using Docker:**
```json
{
  "mcpServers": {
    "jobspy": {
      "command": "docker",
      "args": ["run", "--rm", "-i", "jobspy-mcp-server"]
    }
  }
}
```

After editing the config, restart Claude Desktop.

---

## How to Search for Jobs

Once the server is connected to a client, you can search for jobs through natural language prompts or by invoking the tool directly.

### Natural Language (Claude Desktop)

Type in the chat:

```
Search for Python developer jobs in New York on LinkedIn
```

```
Find remote data scientist roles on Indeed posted in the last 48 hours
```

```
Look for full-time software engineer jobs in London using LinkedIn and Glassdoor, fetch full descriptions
```

```
Find senior Java developer contract jobs on LinkedIn and Indeed, return 25 results
```

The client automatically translates these into `scrape_jobs_tool` calls.

### Direct Tool Calls

**LinkedIn only:**
```json
{
  "search_term": "software engineer",
  "location": "San Francisco, CA",
  "site_name": ["linkedin"],
  "results_wanted": 20,
  "linkedin_fetch_description": true
}
```

**LinkedIn + Indeed combined:**
```json
{
  "search_term": "data scientist",
  "location": "New York, NY",
  "site_name": ["linkedin", "indeed"],
  "results_wanted": 30,
  "hours_old": 72
}
```

**Remote jobs across all major boards:**
```json
{
  "search_term": "frontend developer",
  "is_remote": true,
  "site_name": ["linkedin", "indeed", "glassdoor", "zip_recruiter"],
  "job_type": "fulltime",
  "results_wanted": 25
}
```

**India / Naukri regional search:**
```json
{
  "search_term": "machine learning engineer",
  "location": "Bangalore",
  "site_name": ["naukri", "linkedin"],
  "results_wanted": 20
}
```

**Middle East / Bayt search:**
```json
{
  "search_term": "project manager",
  "location": "Dubai",
  "site_name": ["bayt", "indeed"],
  "country_indeed": "united arab emirates",
  "results_wanted": 15
}
```

---

## MCP Tools Reference

### `scrape_jobs_tool`

Search for jobs across multiple job boards.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `search_term` | string | **required** | Main search keyword(s), for example `"python developer"`. |
| `location` | string | `null` | Location filter (city/state/country/region). |
| `site_name` | list[string] | `["indeed","linkedin","zip_recruiter","google"]` | Supported values: `linkedin`, `indeed`, `glassdoor`, `zip_recruiter`, `google`, `bayt`, `naukri`, `bdjobs`. |
| `results_wanted` | int | `15` | Desired result count. Some providers cap internally (see provider table below). |
| `job_type` | string | `null` | Supported values come from the internal `JobType` enum: `fulltime`, `parttime`, `internship`, `contract`, `temporary`, `other` and more aliases. |
| `is_remote` | bool | `false` | Enables remote-only filtering where supported. |
| `hours_old` | int | `null` | Filters by posting age where supported. |
| `distance` | int | `50` | Radius in miles where provider supports location-radius queries. |
| `easy_apply` | bool | `false` | Easy-apply filter where supported. |
| `country_indeed` | string | `"usa"` | Country enum selector. Primarily used by Indeed and Glassdoor. |
| `linkedin_fetch_description` | bool | `false` | Enables per-job detail fetch on LinkedIn and Naukri (slower). |
| `offset` | int | `0` | Pagination offset. Behavior varies by provider and is also applied in the final combined DataFrame. |
| `verbose` | int | `1` | Logging level: `0` errors, `1` warnings, `2` info. |

Parameter notes:

- `site_name` is validated against a fixed allow-list in the server. Any invalid value returns an error.
- `country_indeed` accepts aliases like `usa`, `us`, `united states`. Use `get_supported_countries` for the complete list.
- `linkedin_fetch_description=true` increases runtime because it triggers additional page/API fetches.

### `get_supported_countries`

Returns the full list of country identifiers accepted by `country_indeed`.

```
What countries are supported for job searches?
```

### `get_supported_sites`

Returns all supported job board names with descriptions and usage guidance.

```
What job boards can I search?
```

### `get_job_search_tips`

Returns an in-depth guide covering search term optimisation, site selection, performance tips, and common troubleshooting.

```
Give me tips for finding jobs faster
```

---

## Supported Job Boards

| `site_name` value | Platform | Region coverage | Internal behavior notes |
|---|---|---|---|
| `linkedin` | LinkedIn | Global | Uses offset pagination with a practical hard stop near 1000 records. Supports remote, job type, hours-old, easy apply, distance, and optional full-description fetch. |
| `indeed` | Indeed | Multi-country | Uses country-specific domain/API codes from `country_indeed`. Supports location+distance, hours-old, job type, remote, and easy apply (conditional query branches). |
| `glassdoor` | Glassdoor | Multi-country | Uses Glassdoor domain mapped from `country_indeed`. Supports location/remote/hours-old/job_type/easy_apply. Results are capped internally at 900. |
| `zip_recruiter` | ZipRecruiter | US/Canada focus | Cookie + token pagination flow. Does not use `country_indeed`. Limited advanced filtering compared to LinkedIn/Indeed. |
| `google` | Google Jobs | Global aggregation | Query-built scraping with optional `google_search_term` override (internal API), plus support for remote/job_type/hours_old/location. Results capped internally at 900. |
| `bayt` | Bayt | Middle East focus | HTML parsing flow. Uses search term and paging primarily; advanced filters are limited. |
| `naukri` | Naukri | India focus | JSON API flow with extra fields (`skills`, `experience_range`, `company_rating`, etc.). Supports location/remote/hours_old and optional detailed description fetch. |
| `bdjobs` | BDJobs | Bangladesh focus | HTML parser with region-specific endpoint. Primarily search term + paging oriented; advanced filters are limited. |

### Provider Parameter Support Matrix

Legend: `Yes` = directly supported, `Partial` = supported with caveat/provider-specific behavior, `No` = ignored or not implemented.

| Provider | `location` | `country_indeed` | `distance` | `is_remote` | `job_type` | `hours_old` | `easy_apply` | `offset` | `linkedin_fetch_description` |
|---|---|---|---|---|---|---|---|---|---|
| LinkedIn | Yes | No (fixed worldwide) | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| Indeed | Yes | Yes | Yes | Yes | Yes | Partial (query branch rules) | Partial (query branch rules) | Yes | No |
| Glassdoor | Yes | Yes | No | Yes | Yes | Yes | Yes | Yes | No |
| ZipRecruiter | Yes | No (US/CA flow) | No | No | No | No | No | Partial | No |
| Google Jobs | Yes | No | No | Yes | Yes | Yes | No | Yes | No |
| Bayt | Partial | No (fixed worldwide) | No | No | No | No | No | Partial | No |
| Naukri | Yes | No (India focus) | No | Yes | No | Yes | No | Yes | Yes |
| BDJobs | Partial | No (Bangladesh focus) | No | No | No | No | No | Partial | No |

### Provider Caveats and Limits

- LinkedIn: stricter anti-bot behavior and backoff sensitivity than other sources.
- Indeed: country selection is meaningful and should be set for non-US searches.
- Glassdoor: internally caps requested results to 900.
- Google Jobs: internally caps requested results to 900 and depends on cursor availability.
- ZipRecruiter: strongest fit for US/Canada; global behavior is limited.
- Bayt, Naukri, BDJobs: regional providers with fewer advanced filter guarantees.

---

## How to Add a New Provider

This project uses a provider-per-module architecture under `jobspy_mcp_server/jobspy_scrapers/`.

### 1. Create provider module

Create a new folder:

`jobspy_mcp_server/jobspy_scrapers/<new_provider>/`

Recommended files:

- `__init__.py` (required): scraper class implementation.
- `constant.py` (optional but recommended): URLs, payload templates, selectors, headers.
- `util.py` (optional): provider-specific parsing helpers.

Implement a class inheriting the abstract `Scraper` and return `JobResponse` from `scrape(scraper_input: ScraperInput)`.

### 2. Register provider enum

Update `Site` in `jobspy_mcp_server/jobspy_scrapers/model.py` with the new string value used by `site_name`.

Example:

```python
class Site(Enum):
  # ... existing providers
  MY_PROVIDER = "my_provider"
```

### 3. Add scraper mapping

Update `jobspy_mcp_server/jobspy_scrapers/__init__.py`:

- Add import for your new scraper class.
- Add mapping in `SCRAPER_MAPPING`.

Example:

```python
from jobspy_mcp_server.jobspy_scrapers.my_provider import MyProvider

SCRAPER_MAPPING = {
  # ... existing mappings
  Site.MY_PROVIDER: MyProvider,
}
```

### 4. Expose provider in MCP layer

Update `jobspy_mcp_server/server.py`:

- Add your provider to `valid_sites` in `scrape_jobs_tool` validation.
- Add description in `get_supported_sites` output.
- Optionally add a direct usage example in README (recommended).

### 5. Follow data contract expectations

Populate `JobPost` consistently:

- Required quality fields: `title`, `job_url`, `company_name`, `location` where available.
- Optional enrichment: `description`, compensation fields, `date_posted`, `job_type`, `is_remote`.
- Normalize text output with existing converters where needed (markdown/plain).

### 6. Handle errors and resilience

- Catch provider-specific request/parse failures and return `JobResponse(jobs=[])` instead of crashing the whole run.
- Use provider logger via existing `create_logger` utility.
- Add controlled delays/backoff for anti-bot or rate-limited providers.

### 7. Security checklist (required)

- Validate and normalize any scraped URLs before returning them.
- Avoid returning unsafe raw HTML unless intentionally required.
- Prefer markdown/plain conversion for descriptions.
- Do not hardcode secrets, API keys, or credentials in provider modules.

### 8. Testing checklist

Add or extend tests in `test/`:

- `test_jobspy_mcp.py`: validate the new site name passes `scrape_jobs_tool` validation and appears in supported sites output.
- Provider unit tests: parser robustness, empty-result handling, pagination behavior, and offset/result slicing.
- `test_server.py`: ensure MCP server structure/tools remain intact.

### 9. Common integration mistakes

- Added enum but forgot `SCRAPER_MAPPING` import/entry.
- Added scraper mapping but forgot server `valid_sites` allow-list.
- Returned raw structures instead of `JobResponse`/`JobPost` schema.
- Ignored offset/results slicing and produced unbounded result sets.
- Added provider but forgot README supported-site documentation.

---

## Configuration

No environment variables are required for basic operation. Optional runtime behaviour:

| Setting | How to configure |
|---|---|
| Logging verbosity | Set `verbose` parameter in each tool call (`0`/`1`/`2`) |
| Proxy support | Pass `proxies` directly into the underlying scraper if extending the server |
| TLS certificate | Pass `ca_cert` path via the scraper layer for corporate proxies |
| Description format | Hard-coded to `"markdown"` in `server.py`; change `description_format` to `"html"` or `"plain"` if needed |

---

## Testing

```bash
# Run all unit tests (no network required)
uv run pytest test -v

# Exclude integration tests (default CI mode)
uv run pytest test -q -m "not integration"

# Run integration tests (live network, real job boards)
uv run pytest test -v -m integration

# Run a specific file
uv run pytest test/test_jobspy_mcp.py -v
uv run pytest test/test_server.py -v
```

With `pip` instead of `uv`:

```bash
pytest test -v
```

---

## Build & Deployment

### Local editable install

```bash
uv sync
# or
pip install -e .
```

### Docker

```bash
# Build
docker build -t jobspy-mcp-server .

# Run (stdio mode — attach via MCP client)
docker run --rm -it jobspy-mcp-server
```

### Package build (wheel/sdist)

```bash
uv build
# outputs dist/jobspy_mcp_server-1.0.0-*.whl
```

---

## Troubleshooting

**Module or import errors**
```bash
uv sync            # reinstall all deps
python -m jobspy_mcp_server   # confirm the entry point starts cleanly
```

**No results returned**
- Broaden `search_term` (fewer words)
- Try `indeed` as a single site first
- Check `location` spelling; for non-US searches set `country_indeed` explicitly

**Rate limiting / HTTP 429**
- Reduce `results_wanted` (start with 10)
- Avoid searching LinkedIn for high volumes
- Add spacing between consecutive searches
- Use `proxies` for repeated automated searches

**Claude Desktop does not detect tools**
- Validate the `claude_desktop_config.json` is valid JSON
- Confirm `cwd` points to the repository root
- Restart Claude Desktop after config changes
- Run the server manually once to confirm no startup errors

**`linkedin_fetch_description` is very slow**
- Only enable this when you need full descriptions; it makes an additional HTTP request per job

---

## Contributing

1. Fork the repository and create a feature branch.
2. Make changes with tests where applicable.
3. Ensure all unit tests pass: `uv run pytest test -q -m "not integration"`
4. Open a pull request with a clear description of the change.

Code style is enforced by `black` (line length 88). Run before committing:

```bash
uv run black .
```

---

## Attribution

The scraper implementation (`jobspy_mcp_server/jobspy_scrapers/`) is derived from
[JobSpy](https://github.com/cullenwatson/JobSpy) by Cullen Watson (MIT License) and vendored
into this repository for single-project deployment.

Full license text is in [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

---

## License

MIT License — see [LICENSE](LICENSE) for details.
