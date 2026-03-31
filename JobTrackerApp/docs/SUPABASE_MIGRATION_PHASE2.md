# Supabase Phase 2 Migration Plan

> **Purpose:** Drop camelCase legacy columns from `public.jobs`, add `source` CHECK constraint
> to `public.shared_links`, and update the Android app to write snake_case column names.
>
> **Prerequisite:** Phase 1 app (Room DB v8, `on_conflict=job_url`) is fully deployed to all
> active devices before any Phase 2 SQL is executed on Supabase.

---

## Overview

| Phase | What Changes | Safe to Run |
|-------|-------------|-------------|
| **Phase 1 (app update)** | Android app writes `createdAt`/`updatedAt` (Long) to `"timestamp"`/`"lastModified"` bigint columns via `@SerializedName`; conflict key switched to `job_url` | Now |
| **Phase 2 (Supabase SQL)** | Drop camelCase columns from `public.jobs`; add `source` CHECK to `shared_links`; update upsert conflict key in app to snake_case columns | After Phase 1 rollout |

---

## Phase 1 Checklist (Pre-requisites for Phase 2)

- [ ] App version with Room DB v8 released and distributed.
- [ ] All active devices have updated to Phase 1 app (no device still writing to `"jobUrl"` or `"timestamp"` columns directly via the old Retrofit serialisation).
- [ ] `SupabaseApiService.upsertJob` `on_conflict` is already set to `"job_url"` (done in Phase 1).
- [ ] Verify live data: `SELECT COUNT(*) FROM public.jobs WHERE job_url IS NULL` → must be 0.

---

## Phase 2 SQL: `public.jobs`

### Step 1 — Pre-flight Checks

Run these queries in the Supabase SQL editor before making any schema changes:

```sql
-- 1. Confirm no active rows have a null snake_case business key
SELECT COUNT(*) AS rows_with_null_job_url
FROM public.jobs
WHERE job_url IS NULL;
-- Expected: 0

-- 2. Confirm snake_case and camelCase job_url values are in sync for all active rows
SELECT COUNT(*) AS mismatched_urls
FROM public.jobs
WHERE job_url IS DISTINCT FROM "jobUrl";
-- Expected: 0

-- 3. Confirm company_name / "companyName" are in sync
SELECT COUNT(*) AS mismatched_company
FROM public.jobs
WHERE company_name IS DISTINCT FROM "companyName";
-- Expected: 0

-- 4. Confirm role_title / "jobTitle" are in sync
SELECT COUNT(*) AS mismatched_title
FROM public.jobs
WHERE role_title IS DISTINCT FROM "jobTitle";
-- Expected: 0

-- 5. Confirm description / "jobDescription" are in sync
SELECT COUNT(*) AS mismatched_description
FROM public.jobs
WHERE description IS DISTINCT FROM "jobDescription";
-- Expected: 0
```

**Do not proceed to Step 2 if any of the above return a non-zero count.**

---

### Step 2 — Drop Legacy camelCase Columns

```sql
-- Drop the legacy sync trigger first (no longer needed after this step)
DROP TRIGGER IF EXISTS jobs_sync_legacy_columns ON public.jobs;

-- Drop the camelCase unique index before dropping the column
DROP INDEX IF EXISTS idx_jobs_joburl_unique;

-- Drop all legacy camelCase columns
ALTER TABLE public.jobs DROP COLUMN IF EXISTS "companyName";
ALTER TABLE public.jobs DROP COLUMN IF EXISTS "jobUrl";
ALTER TABLE public.jobs DROP COLUMN IF EXISTS "jobDescription";
ALTER TABLE public.jobs DROP COLUMN IF EXISTS "jobTitle";
ALTER TABLE public.jobs DROP COLUMN IF EXISTS "timestamp";
ALTER TABLE public.jobs DROP COLUMN IF EXISTS "lastModified";
ALTER TABLE public.jobs DROP COLUMN IF EXISTS "matchScore";
ALTER TABLE public.jobs DROP COLUMN IF EXISTS "sourcePlatform";
ALTER TABLE public.jobs DROP COLUMN IF EXISTS "filterReason";
ALTER TABLE public.jobs DROP COLUMN IF EXISTS "prepNotes";
```

---

### Step 3 — Update App for Phase 2 Column Mapping

After Step 2, the app must be updated so Gson serialises to snake_case names. Update
`@SerializedName` annotations in `JobEntity.kt`:

| Property | Phase 1 `@SerializedName` | Phase 2 `@SerializedName` |
|----------|--------------------------|--------------------------|
| `companyName` | *(none — default)* | `"company_name"` |
| `jobUrl` | *(none — default)* | `"job_url"` |
| `jobDescription` | *(none — default)* | `"description"` |
| `jobTitle` | *(none — default)* | `"role_title"` |
| `createdAt` | `"timestamp"` | `"created_at"` (ISO string or bigint via adapter) |
| `updatedAt` | `"lastModified"` | `"modified_at"` (ISO string or bigint via adapter) |
| `matchScore` | *(none — default)* | `"match_score"` |
| `sourcePlatform` | *(none — default)* | `"source_platform"` |
| `filterReason` | *(none — default)* | `"filter_reason"` |
| `prepNotes` | *(none — default)* | `"prep_notes"` |

Also update `SupabaseApiService.kt`:
```kotlin
// Phase 2: update on_conflict column reference (already done in Phase 1)
// Update order parameter to use the snake_case column:
@Query("order") order: String = "created_at.desc"
```

---

## Phase 2 SQL: `public.shared_links`

### Pre-flight Check

```sql
-- Confirm all existing source values are within the allowed set
SELECT DISTINCT source, COUNT(*) AS row_count
FROM public.shared_links
GROUP BY source;
-- Expected: only 'android-share-intent', 'web-extension', 'manual', or NULL
```

The table is currently empty (confirmed), so this constraint can be added immediately.

### Add Source CHECK Constraint

```sql
ALTER TABLE public.shared_links
ADD CONSTRAINT shared_links_source_check
CHECK (source IN ('android-share-intent', 'web-extension', 'manual'));
```

> If existing rows have unexpected source values, run a normalisation update first:
>
> ```sql
> -- Normalise unexpected source values before adding constraint
> UPDATE public.shared_links
> SET source = 'manual'
> WHERE source NOT IN ('android-share-intent', 'web-extension', 'manual');
> ```

---

## Rollback Plan

If Phase 2 SQL causes issues, the camelCase columns can be re-added and the sync trigger
restored. Keep a backup of the `sync_jobs_legacy_columns` trigger function before dropping it.

```sql
-- Emergency rollback: re-add dropped columns (values will be NULL)
ALTER TABLE public.jobs ADD COLUMN IF NOT EXISTS "companyName" text;
ALTER TABLE public.jobs ADD COLUMN IF NOT EXISTS "jobUrl" text;
ALTER TABLE public.jobs ADD COLUMN IF NOT EXISTS "jobDescription" text;
ALTER TABLE public.jobs ADD COLUMN IF NOT EXISTS "jobTitle" text;
ALTER TABLE public.jobs ADD COLUMN IF NOT EXISTS "timestamp" bigint;
ALTER TABLE public.jobs ADD COLUMN IF NOT EXISTS "lastModified" bigint;
ALTER TABLE public.jobs ADD COLUMN IF NOT EXISTS "matchScore" integer;
ALTER TABLE public.jobs ADD COLUMN IF NOT EXISTS "sourcePlatform" text;
ALTER TABLE public.jobs ADD COLUMN IF NOT EXISTS "filterReason" text;
ALTER TABLE public.jobs ADD COLUMN IF NOT EXISTS "prepNotes" text;

-- Backfill from snake_case columns
UPDATE public.jobs SET
    "companyName" = company_name,
    "jobUrl" = job_url,
    "jobDescription" = description,
    "jobTitle" = role_title,
    "matchScore" = match_score,
    "sourcePlatform" = source_platform,
    "filterReason" = filter_reason,
    "prepNotes" = prep_notes;

-- Restore the unique index on "jobUrl"
CREATE UNIQUE INDEX IF NOT EXISTS idx_jobs_joburl_unique ON public.jobs("jobUrl");
```

---

## Post-Phase-2 Verification

```sql
-- Confirm camelCase columns are gone
SELECT column_name
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'jobs'
  AND column_name IN (
    'companyName','jobUrl','jobDescription','jobTitle',
    'timestamp','lastModified','matchScore','sourcePlatform','filterReason','prepNotes'
  );
-- Expected: 0 rows

-- Confirm indexes
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'jobs'
  AND schemaname = 'public';

-- Confirm source constraint on shared_links
SELECT conname, consrc
FROM pg_constraint
WHERE conrelid = 'public.shared_links'::regclass
  AND contype = 'c';
```

---

## Summary Table

| Item | Phase 1 | Phase 2 |
|------|---------|---------|
| App `createdAt`/`updatedAt` type | `Long` (millis) | `Long` (millis, renamed `@SerializedName`) |
| Supabase upsert `on_conflict` | `job_url` | `job_url` (no change) |
| camelCase columns on Supabase | Present (kept by trigger) | **Dropped** |
| `sync_jobs_legacy_columns` trigger | Active | **Dropped** |
| `source` CHECK on `shared_links` | Not present | **Added** |
| App `@SerializedName` on `companyName` etc. | No annotation (sends camelCase) | Added (sends snake_case) |

