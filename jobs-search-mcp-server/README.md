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
| `search_term` | string | **required** | Job search keywords (e.g. `"software engineer"`) |
| `location` | string | `null` | City, state, or country (e.g. `"San Francisco, CA"`) |
| `site_name` | list | `["indeed","linkedin","zip_recruiter","google"]` | Job boards to search |
| `results_wanted` | int | `15` | Number of results to return (1–1000) |
| `job_type` | string | `null` | `fulltime`, `parttime`, `internship`, or `contract` |
| `is_remote` | bool | `false` | Remote jobs only |
| `hours_old` | int | `null` | Jobs posted within the last N hours |
| `distance` | int | `50` | Search radius in miles |
| `easy_apply` | bool | `false` | Easy apply listings only |
| `country_indeed` | string | `"usa"` | Country for Indeed/Glassdoor searches |
| `linkedin_fetch_description` | bool | `false` | Fetch full descriptions from LinkedIn (slower) |
| `offset` | int | `0` | Results to skip (for pagination) |
| `verbose` | int | `1` | Logging level: `0`=errors, `1`=warnings, `2`=all |

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

| `site_name` value | Platform | Best for |
|---|---|---|
| `linkedin` | LinkedIn | Professional roles; strict rate limits — keep `results_wanted` ≤ 20 |
| `indeed` | Indeed | General search; most reliable and rate-limit-tolerant |
| `glassdoor` | Glassdoor | Roles with salary and company review data |
| `zip_recruiter` | ZipRecruiter | US and Canada listings |
| `google` | Google Jobs | Aggregated results; use specific search terms |
| `bayt` | Bayt | Middle East region |
| `naukri` | Naukri | India; includes skills, experience range, company rating |
| `bdjobs` | BDJobs | Bangladesh |

### Platform-specific constraints

**LinkedIn** — only one of the following per search:
- `hours_old`
- `easy_apply`

**Indeed** — only one of the following per search:
- `hours_old`
- `job_type` + `is_remote` combined
- `easy_apply`

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
