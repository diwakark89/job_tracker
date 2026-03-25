# ADR-002: Fault-Isolated Multi-Site Scraping Orchestration

- Status: Accepted
- Date: 2026-03-25
- Deciders: Architecture review

## Context

The current orchestrator runs one future per site and directly consumes future.result(). A failure in one site worker can fail the whole aggregated request.

Because external job boards are volatile and often rate-limited, partial failure is expected and should not collapse total service reliability.

## Decision

Adopt fault-isolated site execution with partial success semantics:

- Wrap each site future in exception handling.
- Continue processing when one site fails.
- Return site-level status summary with success and failure reasons.
- Add per-site timeout budget and cancellation where possible.
- Add adaptive backoff and simple circuit breaker for repeatedly failing sites.

## Decision Drivers

- Reliability: avoid full-request failure from single dependency errors.
- Observability: provide actionable diagnostics by site.
- UX: better outcomes via partial data instead of full error.

## Options Considered

1. Fail fast on first site error.
2. Retry indefinitely for each site.
3. Fail isolated per site with bounded retry and partial response.

## Outcome

Chosen: Option 3.

## Consequences

### Positive
- Higher aggregate success rate under third-party instability.
- Faster recovery and clearer incident triage.

### Negative
- More complex response contract.
- Slight implementation and testing overhead.

## Implementation Notes

- Orchestrator should emit: successful_sites, failed_sites, and counts.
- Add resilience tests simulating one or more scraper exceptions.