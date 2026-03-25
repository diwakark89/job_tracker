# OpenClaw Integration Guide

Connect the **JobSpy MCP Server** to your local [OpenClaw](https://openclaw.ai/) instance so the assistant can search for jobs across 9 job boards on your behalf.

Two integration methods are provided:

| Method | Transport | Best for |
|--------|-----------|----------|
| **A — CLI (exec)** | Shell command via `exec` tool | Simplest setup, no background server |
| **B — HTTP (SSE)** | HTTP via `web_fetch` / `curl` | Persistent service, multiple clients |

---

## Prerequisites

- **Python 3.10+** installed and on PATH
- **OpenClaw** installed and running locally (`openclaw gateway`)
- This repository cloned locally

---

## Installation

### 1. Clone the repository

```bash
git clone https://github.com/diwakark89/job_tracker.git
cd job_tracker
```

### 2. Install the package

**With uv (recommended):**
```bash
uv sync
```

**With pip:**
```bash
pip install -e .
```

### 3. Verify the installation

```bash
# CLI wrapper should be available
jobspy-search --help

# MCP server should be available
jobspy-mcp-server --help
```

---

## Method A: CLI Integration (exec-based)

This is the **recommended** approach. OpenClaw's agent calls the `jobspy-search` CLI via the built-in `exec` tool. No background server needed.

### Step 1 — Verify the CLI works

```bash
jobspy-search "software engineer" --location "New York" --sites linkedin --results 3
```

You should see markdown-formatted job listings printed to stdout.

### Step 2 — Install the OpenClaw Skill

The skill file teaches the OpenClaw agent how and when to use the job search tool.

**Option A — Copy from this repo:**

```powershell
# Windows (PowerShell)
Copy-Item -Path ".\assets\openclaw-skill\SKILL.md" `
          -Destination "$env:USERPROFILE\.openclaw\workspace\skills\jobspy-search\SKILL.md" `
          -Force

# macOS / Linux
mkdir -p ~/.openclaw/workspace/skills/jobspy-search
cp ./assets/openclaw-skill/SKILL.md ~/.openclaw/workspace/skills/jobspy-search/SKILL.md
```

**Option B — Create manually:**

Create `~/.openclaw/workspace/skills/jobspy-search/SKILL.md` with the contents from [`assets/openclaw-skill/SKILL.md`](../assets/openclaw-skill/SKILL.md).

### Step 3 — Restart OpenClaw or start a new session

```bash
# From chat
/new

# Or restart the gateway
openclaw gateway restart
```

Verify the skill is loaded:

```bash
openclaw skills list
```

You should see `jobspy_search` in the list.

### Step 4 — Test it

Send a message to OpenClaw:

```
Search for Python developer jobs in San Francisco
```

The agent will use the `exec` tool to run `jobspy-search` and return formatted results.

### Example prompts

```
Find remote data scientist roles posted in the last 48 hours
```

```
Search for senior Java developer jobs in London on LinkedIn and Indeed
```

```
Look for machine learning engineer positions in Bangalore on Naukri
```

---

## Method B: HTTP Integration (SSE-based)

Run the MCP server as a persistent HTTP service. OpenClaw's agent uses `web_fetch` or `exec` with `curl` to interact with it.

### Step 1 — Start the server

```bash
# SSE transport (recommended for HTTP)
jobspy-mcp-server --transport sse --host 127.0.0.1 --port 8765

# Or streamable-http transport (modern MCP)
jobspy-mcp-server --transport streamable-http --host 127.0.0.1 --port 8765
```

> **Security:** The server binds to `127.0.0.1` (loopback) by default. Only processes on your local machine can reach it. Do not bind to `0.0.0.0` unless you understand the security implications.

To run in the background on Windows:

```powershell
Start-Process -NoNewWindow jobspy-mcp-server -ArgumentList "--transport", "sse", "--port", "8765"
```

On macOS/Linux:

```bash
nohup jobspy-mcp-server --transport sse --port 8765 > /tmp/jobspy-mcp.log 2>&1 &
```

### Step 2 — Verify the endpoint

```bash
# SSE transport — should open an event stream (Ctrl+C to stop)
curl http://127.0.0.1:8765/sse

# Streamable-http transport — POST to /mcp
curl -X POST http://127.0.0.1:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}},"id":1}'
```

### Step 3 — Install the OpenClaw Skill

Same as Method A, Step 2. The skill includes instructions for both CLI and HTTP usage — the agent picks the appropriate method based on context.

### Step 4 — Test it

Same as Method A, Step 4.

---

## Exposed Endpoints

When running with `--transport sse`:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/sse` | `GET` | Opens an SSE event stream for MCP protocol communication |
| `/messages` | `POST` | Receives MCP JSON-RPC messages |

When running with `--transport streamable-http`:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/mcp` | `POST` | Receives MCP JSON-RPC messages, returns SSE or JSON responses |

Both transports expose the same 4 MCP tools described below.

---

## MCP Tools Reference

### 1. `scrape_jobs_tool`

**Description:** Search for jobs across multiple job boards.

**Parameters:**

| Parameter | Type | Required | Default | Range | Description |
|-----------|------|----------|---------|-------|-------------|
| `search_term` | string | **Yes** | — | — | Job search keywords |
| `location` | string | No | `null` | — | Location filter (city/state/country) |
| `site_name` | list[string] | No | `["linkedin"]` | Max 3 | Job boards to search |
| `results_wanted` | int | No | `10` | 1–15 | Number of results |
| `job_type` | string | No | `null` | — | `fulltime`, `parttime`, `internship`, `contract` |
| `is_remote` | bool | No | `false` | — | Remote jobs only |
| `hours_old` | int | No | `24` | 1–72 | Max posting age in hours |
| `distance` | int | No | `50` | 1–100 | Search radius in miles |
| `easy_apply` | bool | No | `false` | — | Easy apply filter |
| `country_indeed` | string | No | `"usa"` | — | Country for Indeed/Glassdoor |
| `linkedin_fetch_description` | bool | No | `false` | — | Fetch full descriptions (slower) |
| `offset` | int | No | `0` | 0–1000 | Pagination offset |
| `verbose` | int | No | `1` | 0–2 | Logging verbosity |

**Valid `site_name` values:** `linkedin`, `indeed`, `glassdoor`, `zip_recruiter`, `google`, `bayt`, `naukri`, `stepstone`, `xing`

**Example JSON-RPC call:**

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "scrape_jobs_tool",
    "arguments": {
      "search_term": "software engineer",
      "location": "San Francisco, CA",
      "site_name": ["linkedin", "indeed"],
      "results_wanted": 5,
      "is_remote": false
    }
  },
  "id": 1
}
```

### 2. `get_supported_countries`

**Description:** Returns all supported country identifiers for the `country_indeed` parameter.

**Parameters:** None.

### 3. `get_supported_sites`

**Description:** Returns all 9 supported job boards with descriptions and usage tips.

**Parameters:** None.

### 4. `get_job_search_tips`

**Description:** Returns a comprehensive guide with search optimization strategies, site selection advice, performance tips, and troubleshooting.

**Parameters:** None.

---

## OpenClaw Configuration (optional)

If you want to explicitly enable/disable the skill or supply configuration, add to `~/.openclaw/openclaw.json`:

```json5
{
  skills: {
    entries: {
      "jobspy-search": {
        enabled: true,
      },
    },
  },
}
```

No API keys or environment variables are required — the scrapers use direct HTTP requests to public job board websites.

---

## Supported Job Boards

| Board | `site_name` value | Region | Notes |
|-------|-------------------|--------|-------|
| LinkedIn | `linkedin` | Global | Best data quality, strict rate limits |
| Indeed | `indeed` | Multi-country | Most reliable, use `country_indeed` for non-US |
| Glassdoor | `glassdoor` | Multi-country | Includes company reviews/salary |
| ZipRecruiter | `zip_recruiter` | US/Canada | Good for US jobs |
| Google Jobs | `google` | Global | Aggregated listings |
| Bayt | `bayt` | Middle East | Regional specialist |
| Naukri | `naukri` | India | Extra fields: skills, experience, rating |
| Stepstone | `stepstone` | Germany/DACH | Strong European coverage |
| Xing | `xing` | Germany | German professional network |

---

## Troubleshooting

### "command not found: jobspy-search"

The package is not installed or not on PATH.

```bash
# Reinstall with pip
pip install -e /path/to/jobs-search-mcp-server

# Or use the full path
python -m jobspy_mcp_server.cli "software engineer" --sites indeed
```

### Skill not showing in OpenClaw

1. Verify the file exists: `~/.openclaw/workspace/skills/jobspy-search/SKILL.md`
2. Start a new session: `/new` in chat
3. Check: `openclaw skills list`

### No job results

- Broaden search terms (e.g. "developer" instead of "senior staff Python ML engineer")
- Try different sites — `indeed` has the broadest coverage
- Increase `--hours-old` to 72
- For non-US searches, set `--country` appropriately

### Rate limiting / blocked

- Reduce `--results` to 5
- Use `indeed` or `glassdoor` (less restrictive than LinkedIn)
- Add delays between successive searches

### HTTP server connection refused

- Verify the server is running: check for the process
- Confirm the port is correct (default: 8765)
- Ensure you're connecting to `127.0.0.1`, not `localhost` (IPv6 mismatch on some systems)

---

## Security Considerations

- The HTTP server binds to **loopback only** (`127.0.0.1`) by default — not accessible from the network.
- No authentication is applied on the loopback endpoint (matches OpenClaw's own localhost pattern).
- The scrapers make outbound HTTP requests to public job board websites. No credentials or API keys are sent.
- Job board responses are treated as untrusted content — output is sanitized for markdown display.
- Do not expose the HTTP server on `0.0.0.0` or a public interface without adding authentication.

---

## Architecture

```
┌─────────────────────────────────┐
│         OpenClaw Gateway        │
│      (ws://127.0.0.1:18789)    │
└───────────────┬─────────────────┘
                │
    ┌───────────┴────────────┐
    │    Pi Agent Runtime    │
    │  (tool execution)      │
    └───┬────────────────┬───┘
        │                │
   Method A          Method B
   exec tool         web_fetch
        │                │
        ▼                ▼
┌──────────────┐  ┌──────────────────┐
│ jobspy-search│  │ jobspy-mcp-server│
│  (CLI)       │  │ (HTTP on :8765)  │
└──────┬───────┘  └──────┬───────────┘
       │                 │
       ▼                 ▼
┌──────────────────────────────────┐
│     JobSpy Scrapers (vendored)   │
│  LinkedIn│Indeed│Glassdoor│...   │
└──────────────────────────────────┘
       │
       ▼
   Public Job Board Websites
```
