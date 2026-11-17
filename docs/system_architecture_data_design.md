# System Architecture & Data Design

## 1. High-Level System Architecture

The platform follows an **Event-Driven, Modular Pipeline Architecture** that decouples synchronous user interactions (document uploads, browsing requirements, editing RTM links) from compute-intensive NLP and LLM analysis workflows.

```text
+-----------------------------------------------------------------------------------+
|                                CLIENT LAYER (Next.js)                             |
|  [SRS Uploader]  [PDF/DOCX Canvas Annotator]  [RTM Grid & Graph]  [Revision Diff] |
+------------------------------------+----------------------------------------------+
                                     | HTTPS (REST + SSE Stream)
                                     v
+-----------------------------------------------------------------------------------+
|                            API GATEWAY & CORE BACKEND (FastAPI)                   |
|  [Auth/RBAC Middleware]  [Document Service]  [RTM & Diff API]  [SSE Broadcaster]  |
+--------+---------------------------+-----------------------------------+----------+
         | Presigned URL             | Enqueue Task                      | Pub/Sub
         v                           v                                   v
+-----------------+       +---------------------------------------------------------+
| Object Storage  |       |           ASYNC WORKER CLUSTER (Celery Workers)         |
|  (AWS S3/MinIO) |       |                                                         |
| - Raw PDF/DOCX  |       |  1. [Layout-Aware Parser] (Docling / PyMuPDF / DOCX)    |
| - Preview PDFs  |       |  2. [Atomic Extractor & FR/NFR Classifier] (LLM)        |
| - Export Files  |       |  3. [Linguistic & Semantic Ambiguity Engine] (spaCy+LLM)|
+-----------------+       |  4. [Vector Deduplication & Conflict Engine] (NLI+LLM)  |
                          |  5. [RTM Auto-Linker & Executive Summarizer]            |
                          |  6. [Version Revision Diff Engine] (Hungarian Matching) |
                          |  7. [Report Export Generator] (WeasyPrint / openpyxl)   |
                          +----------------------------+----------------------------+
                                                       | Read / Write
                                                       v
                          +---------------------------------------------------------+
                          |        UNIFIED DATA LAYER (PostgreSQL 16 + pgvector)    |
                          |  - Relational Tables (Projects, Docs, Reqs, RTM, Diffs) |
                          |  - Vector Index (HNSW Cosine Similarity on Embeddings)  |
                          +---------------------------------------------------------+
```

---

## 2. Core Subsystem Architecture

### 2.1 Document Ingestion & Hierarchical Parsing Subsystem
Unstructured SRS documents contain noise (cover pages, revision history tables, headers, footers) mixed with critical specifications.
1. **Direct-to-S3 Upload:** Client requests a presigned S3 upload URL via FastAPI, uploads the `.pdf` or `.docx` directly to object storage, and triggers `POST /api/v1/documents/{id}/process`.
2. **Format Normalization:** If the file is `.docx`, headless LibreOffice renders a deterministic `.pdf` twin so the frontend PDF viewer can display bounding-box highlights consistently regardless of source format.
3. **Hierarchical Document Object Model (HDOM):**
   * The parser extracts structural blocks (`Heading`, `Paragraph`, `ListItem`, `TableCell`) along with section numbering (`1.0` $\rightarrow$ `1.2` $\rightarrow$ `1.2.4`), page numbers, and bounding coordinates $(x_0, y_0, x_1, y_1)$.
   * Boilerplate headers, footers, and Table of Contents blocks are filtered out before requirement extraction.

### 2.2 Requirement Extraction & FR/NFR Classification Subsystem
A single paragraph in an SRS often bundles multiple distinct requirements (compound statements) alongside descriptive background context.
1. **Context vs. Requirement Filtering:** Segments document sections into candidate blocks and filters out purely informational prose.
2. **Atomic Decomposition:** Compound sentences (e.g., *"The system shall encrypt all PII at rest using AES-256 and respond to login requests within 300ms"*) are split into two atomic, independently testable requirement records while retaining a pointer to the parent source span.
3. **Multi-Dimensional Classification:**
   * **Primary Type:** `FUNCTIONAL` (system behaviors, inputs, transformations, outputs) vs. `NON_FUNCTIONAL` (quality attributes, constraints).
   * **NFR Taxonomy (ISO/IEC 25010 aligned):** `Performance`, `Security`, `Reliability/Availability`, `Scalability`, `Usability`, `Maintainability`, `Compliance/Legal`.
   * **EARS Pattern Tagging:** Identifies whether a requirement follows a structured syntax (*Ubiquitous*, *Event-Driven*, *State-Driven*, *Unwanted Behavior*, or *Optional*).

### 2.3 Quality Defect Detection Engine (Ambiguity, Incomplete & Missing Specs)
Every atomic requirement passes through a two-tier evaluation pipeline:
1. **Tier 1 — Deterministic Linguistic Rules (`spaCy`):**
   * *Lexical Ambiguity:* Flags subjective or unquantified terms (*"fast"*, *"scalable"*, *"seamless"*, *"user-friendly"*, *"minimal"*, *"as soon as possible"*).
   * *Weak Imperatives:* Flags non-binding modals (*"should"*, *"could"*, *"may"*, *"might"*) instead of mandatory RFC 2119 keywords (*"shall"*, *"must"*).
   * *Syntactic Ambiguity:* Detects passive voice omitting the responsible actor (e.g., *"Data shall be validated"* $\rightarrow$ missing subject: validated by client, API gateway, or database?) and ambiguous pronouns (*"it"*, *"they"*, *"this"*).
   * *Open-Ended Clauses:* Flags *"etc."*, *"and/or"*, *"including but not limited to"*.
2. **Tier 2 — Contextual & Completeness LLM Audit:**
   * Evaluates each requirement within its parent section context to detect **Incomplete Conditions** (e.g., an `IF` trigger without an `ELSE` failure/exception handling path), **Missing Acceptance Metrics** (e.g., performance requirement without latency percentile or concurrency target), and **Section-Level Missing Requirements** (e.g., an authentication module that defines login and registration but omits password reset, session expiration, and rate-limiting).
   * Produces a structured defect explanation and an actionable **Suggested Rewrite**.

### 2.4 Duplicate & Contradiction Detection Engine
Comparing every requirement against every other requirement directly via an LLM scales at $O(N^2)$ and becomes prohibitively expensive for a 500-requirement SRS ($250,000$ pairs). We solve this using a **Two-Stage Retrieval & Adjudication Funnel**:
1. **Stage 1 — Candidate Pair Retrieval (`pgvector` HNSW):**
   * Compute dense embeddings $v_i \in \mathbb{R}^{1536}$ for all atomic requirements.
   * For each requirement $R_i$, query the top-$k$ nearest neighbors ($k = 15$) using cosine similarity:
     $$\text{sim}(v_i, v_j) = \frac{v_i \cdot v_j}{\|v_i\| \|v_j\|}$$
   * Retain candidate pairs where $\text{sim}(v_i, v_j) \ge 0.72$ (for potential duplicates/overlaps) OR pairs sharing identical entity/attribute subjects within the same domain module.
2. **Stage 2 — Pairwise NLI & LLM Reasoning:**
   * Candidate pairs are evaluated by a cross-encoder NLI model and batched into an LLM verifier to classify the relationship into:
     * `EXACT_DUPLICATE`: Identical meaning stated verbatim or trivially paraphrased ($\text{sim} \ge 0.92$).
     * `SEMANTIC_OVERLAP`: Partial redundancy where two requirements specify overlapping rules that should be merged.
     * `DIRECT_CONTRADICTION`: Mutually incompatible constraints (e.g., $R_{14}$: *"Passwords must expire every 30 days"* vs. $R_{89}$: *"System shall enforce passwordless-only authentication"* or conflicting numeric thresholds like *"timeout after 5s"* vs. *"timeout after 30s"*).

### 2.5 Requirement Traceability Matrix (RTM) & Summary Engine
* **Hierarchical & Cross-Artifact Linking:** Automatically maps extracted requirements into a Directed Acyclic Graph (DAG):
  $$\text{Business Goal / Section Objective} \rightarrow \text{Functional Requirement} \rightarrow \text{Non-Functional Constraint} \rightarrow \text{Derived Test Case / Acceptance Criterion}$$
* **Gap Detection:** Flags **Orphan Requirements** (no upstream business goal or section objective) and **Unverified Requirements** (no measurable acceptance criterion or linked verification rule).
* **Multi-Granularity Summarization:** Generates an **Executive Quality Scorecard** (overall readiness index $0\text{--}100$, defect density, high-risk contradictions) alongside **Module-by-Module Summaries** for engineering leads.

### 2.6 Revision & Version Tracking Engine
When a user uploads Version $V_{t+1}$ of an existing SRS document:
1. **Bipartite Requirement Alignment:** Because requirement numbering often shifts when sections are inserted or deleted, matching solely by ID (`REQ-042`) fails. We construct a similarity cost matrix $C_{i,j}$ combining normalized Levenshtein text similarity, vector cosine similarity, and section path proximity, then solve for optimal 1-to-1 alignment using the **Hungarian Algorithm** (Linear Sum Assignment).
2. **Change Classification:**
   * `UNCHANGED`: Similarity $= 1.0$.
   * `MODIFIED_EDITORIAL`: Minor wording/grammar polish with no behavioral change.
   * `MODIFIED_FUNCTIONAL` / `BREAKING`: Altered thresholds, actors, conditions, or scope.
   * `ADDED` / `DELETED`: Unmatched requirements in $V_{t+1}$ or $V_t$.
3. **Downstream Impact Propagation:** Traverses the RTM graph from every `MODIFIED_BREAKING` or `DELETED` node to highlight dependent requirements and test cases that require re-verification.

---

## 3. Data Flow Lifecycle (Upload to Interactive Report)

1. **Upload Phase:** User drops `Payment_Gateway_SRS_v2.pdf` $\rightarrow$ Frontend uploads to S3 $\rightarrow$ API creates `document` and `document_version` records (`status = QUEUED`) and pushes job `analyze_srs_version` to Redis.
2. **Streaming Pipeline Phase:** Frontend opens `GET /api/v1/versions/{version_id}/events` (SSE). As the Celery worker advances through stages (`PARSING` $\rightarrow$ `EXTRACTING` $\rightarrow$ `ANALYZING_QUALITY` $\rightarrow$ `DETECTING_CONFLICTS` $\rightarrow$ `BUILDING_RTM` $\rightarrow$ `COMPLETED`), progress percentages and intermediate counts stream live to the UI.
3. **Interactive Review Phase:** User inspects flagged ambiguities directly on the PDF canvas, accepts or edits AI-suggested rewrites, resolves duplicate/conflict alerts, and verifies RTM links.
4. **Export Phase:** User clicks **Export Audit Package** $\rightarrow$ Worker renders executive PDF report and multi-sheet XLSX RTM workbook to S3 and returns a secure download URL.

---

## 4. Database Schema (PostgreSQL 16 + `pgvector`)

```sql
-- Enable pgvector extension for semantic search & deduplication
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Workspaces & Projects
CREATE TABLE workspaces (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Documents & Versions (Supports Revision Tracking)
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE document_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    version_label VARCHAR(64) NOT NULL, -- e.g., "v1.0", "v2.1-draft"
    source_file_s3_key TEXT NOT NULL,
    preview_pdf_s3_key TEXT,
    file_type VARCHAR(16) NOT NULL CHECK (file_type IN ('PDF', 'DOCX')),
    processing_status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    overall_quality_score NUMERIC(5,2),
    executive_summary JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (document_id, version_number)
);

-- 3. Extracted Atomic Requirements
CREATE TABLE requirements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    version_id UUID NOT NULL REFERENCES document_versions(id) ON DELETE CASCADE,
    req_code VARCHAR(64) NOT NULL,           -- e.g., "FR-001", "NFR-SEC-004"
    section_path VARCHAR(255),               -- e.g., "3.2 > Authentication > Session Management"
    raw_text TEXT NOT NULL,                  -- Verbatim text from SRS
    normalized_text TEXT NOT NULL,           -- Cleaned atomic statement
    req_type VARCHAR(32) NOT NULL CHECK (req_type IN ('FUNCTIONAL', 'NON_FUNCTIONAL', 'CONSTRAINT')),
    nfr_category VARCHAR(64),                -- e.g., 'SECURITY', 'PERFORMANCE', 'AVAILABILITY'
    ears_pattern VARCHAR(64),                -- e.g., 'EVENT_DRIVEN', 'UBIQUITOUS'
    priority VARCHAR(32),                    -- 'MUST', 'SHOULD', 'COULD', 'WONT'
    clarity_score NUMERIC(5,2) NOT NULL DEFAULT 100.0,
    page_number INT,
    bounding_box JSONB,                      -- {"x0": 72.1, "y0": 140.5, "x1": 520.0, "y1": 168.2}
    embedding vector(1536),                  -- Dense semantic vector
    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'APPROVED', 'NEEDS_REVISION'
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_requirements_version ON requirements(version_id);
CREATE INDEX idx_requirements_embedding ON requirements
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- 4. Quality Defects (Ambiguities, Duplicates, Conflicts, Incomplete)
CREATE TABLE requirement_issues (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    version_id UUID NOT NULL REFERENCES document_versions(id) ON DELETE CASCADE,
    primary_req_id UUID NOT NULL REFERENCES requirements(id) ON DELETE CASCADE,
    related_req_id UUID REFERENCES requirements(id) ON DELETE SET NULL, -- Populated for DUPLICATE / CONFLICT
    issue_category VARCHAR(32) NOT NULL CHECK (
        issue_category IN ('AMBIGUOUS', 'INCOMPLETE', 'MISSING_METRIC', 'DUPLICATE', 'CONFLICT', 'MISSING_REQUIREMENT')
    ),
    severity VARCHAR(16) NOT NULL CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    flagged_phrase TEXT,
    explanation TEXT NOT NULL,
    suggested_rewrite TEXT,
    similarity_score NUMERIC(5,4),
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_issues_version ON requirement_issues(version_id);
CREATE INDEX idx_issues_primary_req ON requirement_issues(primary_req_id);

-- 5. Requirement Traceability Matrix (RTM) Links
CREATE TABLE traceability_links (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    version_id UUID NOT NULL REFERENCES document_versions(id) ON DELETE CASCADE,
    source_node_id UUID NOT NULL REFERENCES requirements(id) ON DELETE CASCADE,
    target_node_id UUID NOT NULL REFERENCES requirements(id) ON DELETE CASCADE,
    link_type VARCHAR(32) NOT NULL CHECK (
        link_type IN ('REFINES', 'DEPENDS_ON', 'CONSTRAINS', 'VERIFIED_BY')
    ),
    confidence_score NUMERIC(5,4) NOT NULL,
    is_human_verified BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (source_node_id, target_node_id, link_type)
);

-- 6. Revision Tracking & Semantic Diffs
CREATE TABLE requirement_diffs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    base_version_id UUID NOT NULL REFERENCES document_versions(id) ON DELETE CASCADE,
    target_version_id UUID NOT NULL REFERENCES document_versions(id) ON DELETE CASCADE,
    base_req_id UUID REFERENCES requirements(id) ON DELETE SET NULL,
    target_req_id UUID REFERENCES requirements(id) ON DELETE SET NULL,
    change_type VARCHAR(32) NOT NULL CHECK (
        change_type IN ('UNCHANGED', 'MODIFIED_EDITORIAL', 'MODIFIED_BREAKING', 'ADDED', 'DELETED')
    ),
    similarity_score NUMERIC(5,4),
    impact_summary TEXT,
    impacted_req_ids UUID[] DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 5. Security, Privacy & Enterprise Governance

* **Strict Multi-Tenant Isolation:** Every database query is scoped by `workspace_id` enforced via FastAPI dependency injection and PostgreSQL Row-Level Security (RLS) policies.
* **Encryption & Ephemeral Storage:** Raw SRS files are encrypted at rest in S3 using AES-256 (`SSE-KMS`) and transmitted over TLS 1.3. Download URLs expire within 15 minutes.
* **Zero-Data-Retention (ZDR) AI Inference:** Enterprise workspaces route LLM API calls through endpoints configured with Zero-Data-Retention agreements so proprietary product specifications are never logged or used for foundation model training.
* **Optional Pre-Inference PII/Entity Masking:** Microsoft Presidio can optionally anonymize client names, internal IP addresses, and proprietary codenames prior to external LLM calls, re-hydrating them locally before saving results.