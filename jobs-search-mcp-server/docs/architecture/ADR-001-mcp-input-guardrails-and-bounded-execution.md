# ADR-001: MCP Input Guardrails and Bounded Execution

- Status: Accepted
- Date: 2026-03-25
- Deciders: Architecture review

## Context

The MCP tool accepts user-driven parameters (site list, result counts, offset, distance, and recency windows). Current behavior does not enforce documented ranges at tool boundary.

Without strict validation, requests can create excessive upstream traffic, long execution times, and unstable behavior under abuse or accidental misconfiguration.

## Decision

Introduce a strict validation and normalization layer at MCP tool ingress:

- Enforce allow-list for site names.
- Limit site count to 1-3 per request (default: linkedin).
- Clamp and validate numeric ranges:
  - results_wanted: 1 to 15 (default 10)
  - distance: 1 to 100 (default 50)
  - offset: 0 to 1000 (default 0)
  - hours_old: 1 to 72 (default 24 — always enforced to prevent stale results)
- Reject invalid values with deterministic error messages.
- Log warnings when parameter values are clamped to safe ranges.
- Set server-side execution budget (soft timeout) per request.

## Decision Drivers

- Security: prevent misuse and resource abuse.
- Reliability: maintain predictable execution under variable loads.
- Cost: bound network and LLM token usage.

## Options Considered

1. No validation, rely on scraper internals.
2. Soft guidance only in docstrings.
3. Hard validation at MCP boundary plus bounded defaults.

## Outcome

Chosen: Option 3.

## Consequences

### Positive
- Reduced risk of overload and rate-limit amplification.
- Improved user feedback for invalid requests.
- Better SLO adherence and operational predictability.

### Negative
- Some previously accepted edge cases are now rejected.
- Requires parameter policy maintenance as capabilities evolve.

## Security Notes

This decision mitigates insecure design patterns by applying deny-by-default validation on tool inputs.

## Implementation Notes

- Implement with explicit checks in scrape_jobs_tool before dispatch.
- Add tests for boundary values and invalid inputs.