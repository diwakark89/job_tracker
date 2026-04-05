# 🧠 System Build Roadmap (Phased)

## 🟢 Phase 0 — Stabilize Current System (Foundation)

### 🎯 Goal

Make your current pipeline consistent and safe to extend without breaking things.

### 🔧 Changes (Minimal Refactor)

1. **Fix status problem**

   ```sql
   ALTER TABLE jobs
   RENAME COLUMN status TO job_status;

   ALTER TABLE jobs
   ADD COLUMN pipeline_stage text DEFAULT 'SCRAPED';
   ```

2. **Normalize `job_status`**

   ```sql
   ALTER TABLE jobs
   DROP CONSTRAINT jobs_status_check;

   ALTER TABLE jobs
   ADD CONSTRAINT jobs_job_status_check CHECK (
     job_status IN ('SAVED','APPLIED','INTERVIEW','OFFER','REJECTED')
   );
   ```

3. **Add dedup support**

   ```sql
   ALTER TABLE jobs
   ADD COLUMN content_hash text;

   CREATE UNIQUE INDEX IF NOT EXISTS idx_jobs_content_hash
   ON jobs(content_hash);
   ```

### 🧪 Test Criteria

- MCP inserts jobs successfully.
- No duplicate jobs inserted (same hash).
- `pipeline_stage = SCRAPED`.

### ✅ Output of Phase 0

You now have:

- Stable ingestion
- Clean lifecycle tracking
- Deduplication base

## 🟡 Phase 1 — Proper Raw Data Layer

### 🎯 Goal

Separate raw scraped data from everything else.

### 🔧 Changes

- **Rename table:**

  ```sql
  ALTER TABLE jobs RENAME TO jobs_raw;
  ```

- **Remove AI-related columns:**

  ```sql
  ALTER TABLE jobs_raw
  DROP COLUMN match_score,
  DROP COLUMN prep_notes,
  DROP COLUMN filter_reason;
  ```

- **Add missing fields:**

  ```sql
  ALTER TABLE jobs_raw
  ADD COLUMN external_id text,
  ADD COLUMN location text;
  ```

### 🧪 Test Criteria

- MCP writes **only** raw job data.
- No AI fields exist in this table.
- Data is clean and consistent.

### ✅ Output of Phase 1

You now have:

- 🧱 Clean ingestion layer (like real data pipelines)

## 🟠 Phase 2 — Enrichment Layer

### 🎯 Goal

Extract structured metadata from raw jobs.

### 🔧 New Table

```sql
CREATE TABLE jobs_enriched (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id uuid REFERENCES jobs_raw(id) ON DELETE CASCADE,

  tech_stack text[],
  experience_level text,
  remote_type text,
  visa_sponsorship boolean,
  english_friendly boolean,

  created_at timestamptz DEFAULT now()
);
```

### ⚙️ Implementation

Create Python service:

```python
def enrich_job(job):
    return {
        "tech_stack": extract_tech(job["description"]),
        "experience_level": extract_level(job["role_title"]),
        "remote_type": detect_remote(job["description"]),
        "english_friendly": job["language"] == "English"
    }
```

### 🧪 Test Criteria

- Enriched record created for each raw job.
- Fields populated correctly.
- No AI involved yet.

### ✅ Output of Phase 2

You now have:

- 🧠 Structured job intelligence (without AI)

## 🔵 Phase 3 — Decision Layer (OpenClaw)

### 🎯 Goal

Add AI-based scoring and filtering.

### 🔧 New Table

```sql
CREATE TABLE job_decisions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id uuid REFERENCES jobs_raw(id) ON DELETE CASCADE,

  match_score numeric,
  decision text CHECK (decision IN ('AUTO_APPROVE', 'REVIEW', 'REJECT')),
  reason text,
  confidence numeric,

  created_at timestamptz DEFAULT now()
);
```

### ⚙️ Flow

`jobs_raw + jobs_enriched → OpenClaw → job_decisions`

### 🧠 Decision Logic

- `0.85` → `AUTO_APPROVE`
- `0.6–0.85` → `REVIEW`
- `< 0.6` → `REJECT`

### 🧪 Test Criteria

- Each job gets a decision record.
- JSON output is consistent.
- No overwrite of previous decisions.

### ✅ Output of Phase 3

You now have:

- 🤖 Intelligent filtering layer

## 🟣 Phase 4 — Telegram Approval System

### 🎯 Goal

Human-in-the-loop validation.

### 🔧 New Table

```sql
CREATE TABLE job_approvals (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id uuid REFERENCES jobs_raw(id) ON DELETE CASCADE,

  decision_id uuid REFERENCES job_decisions(id),

  user_action text CHECK (user_action IN ('APPROVED', 'REJECTED', 'PENDING')),
  approved_at timestamptz,

  created_at timestamptz DEFAULT now()
);
```

### ⚙️ Logic

| Decision | Action |
| --- | --- |
| `AUTO_APPROVE` | Skip Telegram |
| `REVIEW` | Send to Telegram |
| `REJECT` | Ignore |

### 🧪 Test Criteria

- Telegram message received.
- Button click updates DB.
- Approval recorded correctly.

### ✅ Output of Phase 4

You now have:

- 👤 Human-controlled filtering

## 🔴 Phase 5 — Final Storage Layer

### 🎯 Goal

Store only high-quality jobs.

### 🔧 New Table

```sql
CREATE TABLE jobs_final (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id uuid UNIQUE REFERENCES jobs_raw(id),

  company_name text,
  role_title text,
  job_url text,

  match_score numeric,
  tags text[],

  saved_at timestamptz DEFAULT now()
);
```

### ⚙️ Flow

`Approved jobs → jobs_final`

### 🧪 Test Criteria

- Only approved jobs inserted.
- No duplicates.
- Clean dataset.

### ✅ Output of Phase 5

You now have:

- 💎 High-quality job database

## ⚫ Phase 6 — Automation & Scheduling

### 🎯 Goal

Fully automated pipeline.

### ⚙️ Setup

- Cron job (daily)
- Pipeline:

  ```text
  Scrape → Enrich → Decide → Approve → Store
  ```

### 🧪 Test Criteria

- Runs daily automatically.
- No manual trigger needed.
- Logs generated.

### ✅ Output of Phase 6

You now have:

- ⚙️ Fully automated system

## 🧠 Phase 7 — Advanced (Optional but Powerful)

### 🎯 Goal

Make system “resume-level impressive”.

### 🔁 Feedback Loop

- Track accepted vs rejected jobs.
- Improve scoring.

### 📊 Analytics Table

```text
job_metrics (
  total_scraped,
  total_approved,
  total_rejected
)
```

### ⚡ Rule Engine Before AI

```python
if job.language != "English":
    skip
```

### ✅ Output of Phase 7

You now have:

- 🚀 Smart, self-improving system

## 🏁 Final Architecture

```text
MCP Server → jobs_raw
           ↓
      jobs_enriched
           ↓
      job_decisions
           ↓
      job_approvals
           ↓
         jobs_final
```

## 🧠 How to Execute This (Important)

**Do not jump phases.**

Follow strictly:

- Phase 0 → stabilize
- Phase 1 → clean data
- Phase 2 → enrichment
- Phase 3 → AI
- Phase 4 → Telegram
- Phase 5 → final storage
