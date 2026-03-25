# ADR-003: Secure Transport and Container Hardening Baseline

- Status: Accepted
- Date: 2026-03-25
- Deciders: Architecture review

## Context

Current implementation includes insecure TLS behavior in one scraper path and container defaults that run as root. The repository also lacks a .dockerignore file.

These increase operational and security risk, especially when running in shared build and runtime environments.

## Decision

Establish a secure baseline:

- Remove verify=False usage from outbound requests.
- Stop globally suppressing insecure TLS warnings.
- Standardize explicit request timeouts across all outbound HTTP calls.
- Run container as non-root user.
- Add .dockerignore to reduce context and prevent accidental file inclusion.
- Prefer pinned base image version and digest for reproducibility.

## Decision Drivers

- Security: protect data in transit and reduce privilege.
- Compliance: align with secure-by-default posture.
- Operations: improve deterministic builds and safer runtime behavior.

## Options Considered

1. Preserve current behavior for scraper compatibility.
2. Make secure mode optional through flags.
3. Secure-by-default baseline with explicit exceptions only when justified.

## Outcome

Chosen: Option 3.

## Consequences

### Positive
- Reduced MITM and privilege-escalation exposure.
- Better auditability and secure defaults.

### Negative
- Some networks/proxies with invalid cert chains may require explicit CA configuration.

## Security Notes

This decision mitigates OWASP A02 cryptographic failures and least-privilege violations.

## Implementation Notes

- Add tests for CA bundle behavior where feasible.
- Add image scanning in CI (for example Trivy or Docker Scout).