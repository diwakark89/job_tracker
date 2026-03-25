# Architecture Review - JobSpy MCP Server

Date: 2026-03-25
Reviewer: GitHub Copilot (SE: Architect)
Scope: Current repository and subfolders only

## 1) System Context and Review Plan

### System type
- AI/Agent-integrated tool server (MCP over stdio) wrapping multi-site web scrapers.

### Complexity assessment
- Growing complexity: multi-source scraping, parallel execution, heterogeneous reliability of third-party sites.

### Primary concerns selected (Well-Architected focus)
- Security: secure transport, untrusted input and output handling, container hardening.
- Reliability: partial failure tolerance, timeout/retry discipline, predictable degradation.
- Performance and cost efficiency: concurrency controls, bounded outputs, token/computation efficiency for AI clients.

## 2) Architecture Summary (Current)

- Entry point runs FastMCP with stdio transport in [jobspy_mcp_server/server.py](jobspy_mcp_server/server.py#L380).
- Main MCP tool [scrape_jobs_tool](jobspy_mcp_server/server.py#L25) orchestrates vendored scrapers and formats markdown output.
- Aggregation executes site scrapers concurrently using default ThreadPoolExecutor in [jobspy_mcp_server/jobspy_scrapers/__init__.py](jobspy_mcp_server/jobspy_scrapers/__init__.py#L120).
- Runtime is packaged as a single container image via [Dockerfile](Dockerfile#L1).

## 3) Findings (Prioritized)

### Critical

1. TLS verification is explicitly disabled for Indeed API calls.
- Evidence: [jobspy_mcp_server/jobspy_scrapers/indeed/__init__.py](jobspy_mcp_server/jobspy_scrapers/indeed/__init__.py#L118)
- Risk: MITM exposure, data integrity compromise, credential/session interception risk on hostile networks.
- OWASP mapping: A02 Cryptographic Failures.

### High

2. Global suppression of insecure TLS warnings hides certificate risk signals.
- Evidence: [jobspy_mcp_server/jobspy_scrapers/util.py](jobspy_mcp_server/jobspy_scrapers/util.py#L16)
- Risk: operators lose visibility when insecure transport behavior is present.

3. Scraper orchestration is fail-fast at aggregate level instead of fault-isolated per site.
- Evidence: future result collection in [jobspy_mcp_server/jobspy_scrapers/__init__.py](jobspy_mcp_server/jobspy_scrapers/__init__.py#L125)
- Risk: one provider failure can abort entire request and reduce reliability under normal external volatility.

4. Unbounded input parameters from MCP tool can cause abuse and resource exhaustion.
- Evidence: parameter docs promise ranges but are not enforced in [jobspy_mcp_server/server.py](jobspy_mcp_server/server.py#L54)
- Risk: high-cardinality searches and large offsets increase runtime, rate-limit pressure, and downstream instability.
- OWASP mapping: A04 Insecure Design, A05 Security Misconfiguration.

5. Container runs as root and lacks explicit runtime hardening baseline.
- Evidence: [Dockerfile](Dockerfile#L1)
- Risk: higher blast radius if process compromise occurs.

### Medium

6. Several outbound HTTP calls have no explicit timeout.
- Evidence: Glassdoor post call in [jobspy_mcp_server/jobspy_scrapers/glassdoor/__init__.py](jobspy_mcp_server/jobspy_scrapers/glassdoor/__init__.py#L249)
- Risk: hung calls can tie up worker threads and degrade throughput.

7. No repository-level .dockerignore increases build context risk and inefficiency.
- Evidence: .dockerignore missing at repository root.
- Risk: larger build contexts, accidental inclusion of sensitive/dev files, slower CI.

8. Current response format is text-heavy markdown only, not structured.
- Evidence: string assembly in [jobspy_mcp_server/server.py](jobspy_mcp_server/server.py#L188)
- Risk: token bloat for LLM clients, harder deterministic post-processing, higher inference cost.

### AI-Specific

9. Job descriptions are untrusted third-party content returned directly into LLM context.
- Evidence: description inclusion in [jobspy_mcp_server/server.py](jobspy_mcp_server/server.py#L150)
- Risk: indirect prompt injection and instruction contamination in downstream agent workflows.
- OWASP LLM concern: prompt injection via retrieved tool content.

10. No tool-level safety policy boundary for content routing.
- Evidence: single free-form string return path in [jobspy_mcp_server/server.py](jobspy_mcp_server/server.py#L188)
- Risk: clients cannot easily separate trusted metadata from untrusted scraped text.

## 4) Validation Against Well-Architected Areas

### Reliability
- Strengths: retry support exists in utility sessions and many scraper calls include timeouts.
- Gaps: no per-site error isolation, no circuit breaker, no standardized deadline budget.

### Security
- Strengths: local stdio deployment reduces internet attack surface compared with public HTTP APIs.
- Gaps: TLS disablement, root container, weak parameter guardrails, and limited output trust boundaries.

### Performance Efficiency
- Strengths: multi-site parallelism implemented.
- Gaps: no adaptive concurrency/rate limiting, potentially large markdown payloads, no bounded serialization profile.

### Operational Excellence
- Strengths: basic logging and tests exist.
- Gaps: no architecture runbook, no SLO/error budget definition, limited resilience test coverage.

### Cost Optimization
- Strengths: single-container deployment simplicity.
- Gaps: output verbosity can over-consume LLM tokens; no caching for repeated queries.

## 5) Recommended Target Architecture (Incremental)

1. Input guardrail layer before scraper dispatch.
- Strict bounds and allow-lists for site names, results, offsets, distance, and hours_old.

2. Fault-isolated orchestration.
- Per-site try/except around futures, return partial results with explicit site-level errors.

3. Secure transport baseline.
- Remove verify=False and warning suppression; support optional custom CA bundle safely.

4. AI-safe response contract.
- Return structured object with trusted metadata and separately labeled untrusted text fields.

5. Container hardening baseline.
- Multi-stage build, non-root runtime user, minimal copy set, .dockerignore, pinned base digest.

## 6) Roadmap (Suggested)

### Phase 1 (1-2 days)
- Implement input validation and bounds.
- Remove insecure TLS behaviors.
- Add .dockerignore and non-root container user.

### Phase 2 (2-4 days)
- Add fault-isolated per-site execution and partial success response model.
- Add explicit timeouts everywhere and standardized retry policy.

### Phase 3 (3-5 days)
- Introduce structured MCP output contract and AI safety annotations for untrusted fields.
- Add observability metrics (site success rate, latency percentiles, timeout counts).

## 7) Open Constraints Needed From You

To finalize the architecture target with right tradeoffs, please confirm:
- Scale: expected requests per day and peak concurrent runs?
- Team: size and strongest operational skills (Python, Docker, cloud, observability)?
- Budget: target monthly hosting budget range?

## 8) Related ADRs

- [ADR-001: MCP Input Guardrails and Bounded Execution](docs/architecture/ADR-001-mcp-input-guardrails-and-bounded-execution.md)
- [ADR-002: Fault-Isolated Multi-Site Scraping Orchestration](docs/architecture/ADR-002-fault-isolated-multi-site-scraping-orchestration.md)
- [ADR-003: Secure Transport and Container Hardening Baseline](docs/architecture/ADR-003-secure-transport-and-container-hardening-baseline.md)
- [ADR-004: AI-Safe Tool Output Contract for Untrusted Scraped Content](docs/architecture/ADR-004-ai-safe-tool-output-contract-for-untrusted-content.md)