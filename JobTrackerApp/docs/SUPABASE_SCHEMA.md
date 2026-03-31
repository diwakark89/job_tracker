# Supabase Schema Reference

> **Last updated:** 2026-03-31
> **DB version (Room):** 8 | **Supabase tables:** `jobs`, `shared_links`

---

## Table: `public.jobs`

### Canonical Columns (snake_case — authoritative server-side)

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | `uuid` | NOT NULL | `gen_random_uuid()` | PK |
| `company_name` | `text` | NOT NULL | — | |
| `role_title` | `text` | NOT NULL | — | Maps to app field `jobTitle` |
| `description` | `text` | NULL | — | Maps to app field `jobDescription` |
| `job_url` | `text` | NULL | — | **Unique business key** (`jobs_job_url_key`) |
| `status` | `text` | NULL | `'Saved'` | CHECK constraint (see below) |
| `language` | `text` | NULL | `'English'` | |
| `match_score` | `integer` | NULL | — | |
| `prep_notes` | `text` | NULL | — | |
| `source_platform` | `text` | NULL | — | |
| `filter_reason` | `text` | NULL | — | |
| `scrape_run_id` | `text` | NULL | — | Server/pipeline use only |
| `created_at` | `timestamptz` | NULL | `now()` | Server-managed creation timestamp |
| `modified_at` | `timestamptz` | NULL | `now()` | Server-managed via `set_modified_at()` trigger |
| `is_deleted` | `boolean` | NOT NULL | `false` | Soft-delete tombstone flag |

### Legacy / App-Written Columns (camelCase — to be dropped in Phase 2)

| Column | Type | Nullable | Default | Replaces / Mirrors |
|--------|------|----------|---------|-------------------|
| `"companyName"` | `text` | NOT NULL | — | `company_name` |
| `"jobUrl"` | `text` | NOT NULL | — | `job_url` |
| `"jobDescription"` | `text` | NOT NULL | — | `description` |
| `"jobTitle"` | `text` | NOT NULL | — | `role_title` |
| `"timestamp"` | `bigint` | NOT NULL | — | `created_at` (millis vs ISO) |
| `"lastModified"` | `bigint` | NOT NULL | — | `modified_at` (millis vs ISO) |
| `"matchScore"` | `integer` | NULL | — | `match_score` |
| `"sourcePlatform"` | `text` | NULL | — | `source_platform` |
| `"filterReason"` | `text` | NULL | — | `filter_reason` |
| `"prepNotes"` | `text` | NULL | — | `prep_notes` |

> **Why duplicates exist:** The Android app originally wrote camelCase field names via Gson
> serialisation. A Supabase trigger (`sync_jobs_legacy_columns`) keeps the two column sets
> in sync during the transition period. Phase 2 drops the camelCase columns after the app
> is updated to write to snake_case names.

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
| `idx_jobs_joburl_unique` | `"jobUrl"` | Unique (legacy — dropped in Phase 2) |
| `idx_jobs_status_modified_at` | `status`, `modified_at DESC` | B-tree |
| `idx_jobs_created_at` | `created_at DESC` | B-tree |

### Triggers

| Name | Event | Function |
|------|-------|----------|
| `jobs_set_modified_at` | BEFORE UPDATE | `set_modified_at()` — auto-updates `modified_at` |
| `jobs_sync_legacy_columns` | BEFORE INSERT OR UPDATE | `sync_jobs_legacy_columns()` — keeps snake_case ↔ camelCase in sync |

---

## Table: `public.shared_links`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | `uuid` | NOT NULL | `gen_random_uuid()` | PK |
| `url` | `text` | NOT NULL | — | The shared URL |
| `source` | `text` | NULL | `'android-share-intent'` | CHECK constraint (see below) |
| `status` | `text` | NULL | `'Pending'` | `Pending`, `Processed`, `Failed` |
| `created_at` | `timestamptz` | NULL | `now()` | |
| `modified_at` | `timestamptz` | NULL | `now()` | |

### Status CHECK Constraint

```sql
status IN ('Pending', 'Processed', 'Failed')
```

### Source CHECK Constraint (to be added in Phase 2)

```sql
source IN ('android-share-intent', 'web-extension', 'manual')
```

### Indexes

| Name | Columns | Type |
|------|---------|------|
| `shared_links_pkey` | `id` | Primary key |
| `idx_shared_links_status_created_at` | `status`, `created_at` | B-tree |

### Triggers

| Name | Event | Function |
|------|-------|----------|
| `shared_links_set_modified_at` | BEFORE UPDATE | `set_modified_at()` |

---

## App ↔ Supabase Field Mapping

### `JobEntity` (Room / Kotlin) → `public.jobs` (Supabase)

| Kotlin Property | Room Column | `@SerializedName` | Supabase Column (Phase 1) | Supabase Column (Phase 2) |
|-----------------|-------------|-------------------|---------------------------|---------------------------|
| `id` | `id` | — | `id` | `id` |
| `companyName` | `companyName` | — | `"companyName"` | `company_name` |
| `jobUrl` | `jobUrl` | — | `"jobUrl"` | `job_url` |
| `jobDescription` | `jobDescription` | — | `"jobDescription"` | `description` |
| `jobTitle` | `jobTitle` | — | `"jobTitle"` | `role_title` |
| `status` | `status` | — | `status` | `status` |
| `createdAt` | `createdAt` | `"timestamp"` | `"timestamp"` (bigint) | `created_at` (convert ms→ISO) |
| `updatedAt` | `updatedAt` | `"lastModified"` | `"lastModified"` (bigint) | `modified_at` (convert ms→ISO) |
| `isDeleted` | `isDeleted` | `"is_deleted"` | `is_deleted` | `is_deleted` |
| `matchScore` | `matchScore` | — | `"matchScore"` | `match_score` |
| `language` | `language` | — | `language` | `language` |
| `prepNotes` | `prepNotes` | — | `"prepNotes"` | `prep_notes` |
| `sourcePlatform` | `sourcePlatform` | — | `"sourcePlatform"` | `source_platform` |
| `filterReason` | `filterReason` | — | `"filterReason"` | `filter_reason` |

> **Key naming decisions:**
>
> - `createdAt` / `updatedAt` replace the old `timestamp` / `lastModified` app fields.
>   Both store **epoch milliseconds** (`Long`). The `@SerializedName` annotation maps them
>   to the Supabase camelCase bigint columns during Phase 1.
>
> - The old `createdAt: String?` and `updatedAt: String?` fields (which mapped to server
>   ISO timestamps) have been removed. Server-managed `created_at` and `modified_at`
>   remain in Supabase but are not represented in the app entity.

---

## Design Rationale: Keeping `description` / `role_title` as Canonical Server Names

The Supabase table uses `description` and `role_title` as the canonical column names. The
Android app uses `jobDescription` and `jobTitle` in the Kotlin entity. We chose **not** to
rename the server columns to `job_description` / `job_title` for these reasons:

1. **Minimal churn:** Renaming DB columns would require updating all SQL queries, indexes,
   triggers, the sync function in the Apps Script, and any external integrations.
2. **Mapping is clean:** The app already uses `@SerializedName` (or will in Phase 2) to
   bridge the naming gap. This is a one-line annotation per field.
3. **Prevents future drift:** Documenting the explicit mapping here prevents future
   developers from creating yet another set of duplicate columns.
4. **Convention consistency:** `description` and `role_title` follow standard snake_case DB
   naming; `jobDescription` and `jobTitle` follow Kotlin camelCase. Both are idiomatic
   in their respective environments.

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

- `parseJobStatus()` normalises casing, hyphens, underscores, and maps legacy `"REJECTED"` → `RESUME_REJECTED`.
- The Supabase CHECK constraint accepts both display-name and uppercase forms.

---

## Conflict Resolution (Supabase Sync)

| Scenario | Resolution |
|----------|-----------|
| Remote `updatedAt` > Local `updatedAt` (beyond tolerance) | Remote wins → replace local |
| Local `updatedAt` > Remote `updatedAt` (beyond tolerance) | Local wins → push to cloud |
| Exact tie | Remote wins (cross-device convergence) |
| Within clock-skew tolerance (±2 min) | Fallback: keep local as-is |

- **`on_conflict`:** Phase 1 uses `job_url` for Supabase upsert conflict resolution.
- **Clock skew tolerance:** 2 minutes (120,000 ms).

