# Supabase Schema Reference

> **Last updated:** 2026-04-03
> **DB version (Room):** 8 | **Supabase tables:** `jobs`, `shared_links`

---

## Table: `public.jobs`

### Canonical Columns

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | `uuid` | NOT NULL | `gen_random_uuid()` | PK |
| `company_name` | `text` | NOT NULL | — | Maps to app field `companyName` |
| `role_title` | `text` | NOT NULL | — | Maps to app field `jobTitle` |
| `description` | `text` | NULL | — | Maps to app field `jobDescription` |
| `job_url` | `text` | NULL | — | Unique business key (`jobs_job_url_key`) |
| `status` | `text` | NULL | `'Saved'` | CHECK-constrained values |
| `language` | `text` | NULL | `'English'` | |
| `match_score` | `integer` | NULL | — | |
| `prep_notes` | `text` | NULL | — | |
| `source_platform` | `text` | NULL | — | |
| `filter_reason` | `text` | NULL | — | |
| `scrape_run_id` | `text` | NULL | — | Server/pipeline field |
| `created_at` | `timestamptz` | NULL | `now()` | Creation timestamp |
| `modified_at` | `timestamptz` | NULL | `now()` | Auto-updated by trigger |
| `is_deleted` | `boolean` | NOT NULL | `false` | Soft-delete tombstone |

### Status CHECK Constraint

```sql
status IN (
  'Saved', 'Applied', 'Interview', 'Interviewing', 'Offer',
  'Resume-Rejected', 'Interview-Rejected',
  'SAVED', 'APPLIED', 'INTERVIEW', 'INTERVIEWING', 'OFFER',
  'RESUME_REJECTED', 'INTERVIEW_REJECTED'
)
```

### Indexes

| Name | Columns | Type |
|------|---------|------|
| `jobs_pkey` | `id` | Primary key |
| `jobs_job_url_key` | `job_url` | Unique |
| `idx_jobs_status_modified_at` | `status`, `modified_at DESC` | B-tree |
| `idx_jobs_created_at` | `created_at DESC` | B-tree |

### Triggers

| Name | Event | Function |
|------|-------|----------|
| `jobs_set_modified_at` | BEFORE UPDATE | `set_modified_at()` |

---

## Table: `public.shared_links`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | `uuid` | NOT NULL | `gen_random_uuid()` | PK |
| `url` | `text` | NOT NULL | — | Shared URL |
| `source` | `text` | NULL | `'android-share-intent'` | Source marker |
| `created_at` | `timestamptz` | NULL | `now()` | |
| `modified_at` | `timestamptz` | NULL | `now()` | |

### Source CHECK Constraint

```sql
source IN ('android-share-intent', 'web-extension', 'manual')
```

### Indexes

| Name | Columns | Type |
|------|---------|------|
| `shared_links_pkey` | `id` | Primary key |

### Triggers

| Name | Event | Function |
|------|-------|----------|
| `shared_links_set_modified_at` | BEFORE UPDATE | `set_modified_at()` |

---

## App-to-Supabase Field Mapping (`JobEntity`)

| Kotlin Property | Wire Name |
|-----------------|-----------|
| `companyName` | `company_name` |
| `jobUrl` | `job_url` |
| `jobDescription` | `description` |
| `jobTitle` | `role_title` |
| `createdAt` | `created_at` |
| `updatedAt` | `modified_at` |
| `isDeleted` | `is_deleted` |
| `matchScore` | `match_score` |
| `language` | `language` |
| `prepNotes` | `prep_notes` |
| `sourcePlatform` | `source_platform` |
| `filterReason` | `filter_reason` |

---

## `JobStatus` Enum Values

| Kotlin Enum | Display Name | Supabase Value |
|-------------|-------------|----------------|
| `SAVED` | `Saved` | `Saved` |
| `APPLIED` | `Applied` | `Applied` |
| `INTERVIEW` | `Interview` | `Interview` |
| `INTERVIEWING` | `Interviewing` | `Interviewing` |
| `OFFER` | `Offer` | `Offer` |
| `RESUME_REJECTED` | `Resume-Rejected` | `Resume-Rejected` |
| `INTERVIEW_REJECTED` | `Interview-Rejected` | `Interview-Rejected` |

`parseJobStatus()` normalises casing and delimiters.

---

## Conflict Resolution (Supabase Sync)

| Scenario | Resolution |
|----------|-----------|
| Remote `updatedAt` > Local `updatedAt` (outside tolerance) | Remote wins |
| Local `updatedAt` > Remote `updatedAt` (outside tolerance) | Local wins |
| Exact tie | Remote wins (deterministic convergence) |
| Within clock-skew tolerance (+/- 2 min) | Keep local as-is |

- Upsert conflict key: `job_url`
- Clock-skew tolerance: `120000` ms
