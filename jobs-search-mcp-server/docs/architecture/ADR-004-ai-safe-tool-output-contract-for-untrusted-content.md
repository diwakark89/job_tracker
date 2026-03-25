# ADR-004: AI-Safe Tool Output Contract for Untrusted Scraped Content

- Status: Accepted
- Date: 2026-03-25
- Deciders: Architecture review

## Context

Scraped job descriptions are third-party, untrusted text and can contain adversarial instructions. Current tool output is a large markdown string that mixes metadata and untrusted content.

In agentic workflows, this increases prompt-injection and parsing risks.

## Decision

Adopt a structured output contract with trust boundaries:

- Return machine-readable structure for metadata (title, company, location, url, salary, timestamps).
- Place description text in a dedicated untrusted field with explicit label.
- Add optional description truncation modes (short, medium, full).
- Include safety metadata:
  - content_trust_level: untrusted_external
  - source_site
  - retrieval_timestamp
- Keep human-readable summary as secondary output only.

## Decision Drivers

- AI safety: reduce prompt-injection blast radius.
- Reliability: deterministic client parsing.
- Cost: token usage control through bounded description modes.

## Options Considered

1. Keep markdown-only output.
2. Return structured output only.
3. Hybrid output: structured primary plus concise human summary.

## Outcome

Chosen: Option 3.

## Consequences

### Positive
- Clear separation of trusted and untrusted fields.
- Better downstream filtering and policy controls.
- Lower token spend for default flows.

### Negative
- Requires client updates for full structured consumption.

## AI Security Notes

This decision addresses indirect prompt injection risks in retrieval/tool pipelines by preserving provenance and trust boundaries.

## Implementation Notes

- Define typed response model and update tests for schema validation.
- Add policy docs that clients must treat description as untrusted data.