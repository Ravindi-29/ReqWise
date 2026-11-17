# Technology Stack & Tooling Specification

## 1. Architectural Philosophy & Stack Strategy

Building an enterprise-grade Software Requirement Specification (SRS) analysis platform requires bridging **deterministic document engineering** (preserving layout, section hierarchies, and table structures from PDFs and DOCX files) with **probabilistic AI reasoning** (extracting atomic requirements, detecting subtle linguistic ambiguity, resolving logical contradictions, and tracking semantic drift across versions).

The stack is designed around four core engineering principles:
1. **Asynchronous Pipeline Execution:** Processing a 150-page SRS involves OCR/layout parsing, chunking, embedding generation, pairwise similarity checks, and multi-pass LLM evaluation. All heavy processing runs asynchronously via background workers with real-time progress streaming to the browser.
2. **Hybrid NLP + LLM Intelligence:** Relying solely on LLMs for every check is slow and costly. We combine fast, deterministic rule-based linguistic analyzers (`spaCy` + INCOSE/EARS rules) with dense vector embeddings (`pgvector`) and frontier LLMs (with strict structured outputs) for deep semantic reasoning.
3. **Unified Data & Vector Persistence:** Using PostgreSQL with the `pgvector` extension eliminates data synchronization bugs between relational requirement trees, traceability graphs, and vector embeddings.
4. **Interactive Document Grounding:** Users must be able to click an ambiguity warning or conflict alert and immediately see the exact highlighted clause inside the original PDF or DOCX layout.

---

## 2. Frontend Stack (Interactive Analysis & Annotation UI)

| Layer | Technology | Version / Spec | Purpose & Justification |
| :--- | :--- | :--- | :--- |
| **Core Framework** | **Next.js (App Router)** | `15.x` (React 19) | Hybrid SSR/CSR rendering, fast route transitions, and strong TypeScript integration. |
| **Language** | **TypeScript** | `5.6+` | Strict end-to-end type safety synced with backend OpenAPI schemas. |
| **Styling & Design System** | **Tailwind CSS + Shadcn/UI** | `Tailwind v4` | Accessible, composable UI primitives (data tables, split panes, drawers, badges) with rapid customization. |
| **Document Viewer & Highlighter** | **PDF.js (`react-pdf`) + Custom Canvas Overlay** | `4.x` | Renders uploaded PDFs (and PDF-converted DOCX previews) in-browser with interactive bounding-box overlays for flagged requirements. |
| **RTM & Data Grids** | **TanStack Table** | `v8` | High-performance virtualized grid for inspecting, filtering, and editing large Requirement Traceability Matrices (1,000+ rows). |
| **Dependency & Traceability Graph** | **React Flow (`@xyflow/react`)** | `12.x` | Interactive node-edge graph visualizing links between Business Goals, Epics, Functional/Non-Functional Requirements, and Test Cases. |
| **Diff & Revision Viewer** | **Monaco Diff Editor / `diff-match-patch`** | Latest | Side-by-side token-level and semantic comparison view for tracking requirement revisions across document versions. |
| **Analytics & Charts** | **Recharts** | `2.x` | Renders requirement quality scorecards, FR vs. NFR distributions, and ambiguity severity breakdowns. |
| **State & Server Cache** | **TanStack Query + Zustand** | `v5` / `v5` | TanStack Query manages API caching and optimistic updates; Zustand manages active PDF highlight coordinates and split-pane state. |

---

## 3. Backend & Asynchronous Processing Stack

| Layer | Technology | Version / Spec | Purpose & Justification |
| :--- | :--- | :--- | :--- |
| **Primary API Server** | **FastAPI (Python)** | `0.115+` (Python `3.12`) | High-throughput async ASGI framework with native Pydantic v2 validation and first-class access to the Python AI/NLP ecosystem. |
| **Distributed Task Queue** | **Celery** | `5.4+` | Orchestrates multi-stage document ingestion, embedding generation, pairwise conflict analysis, and report exports. |
| **Message Broker & Cache** | **Redis** | `7.4+` | Serves as the Celery broker, rate-limiter store, short-lived session cache, and Pub/Sub channel for real-time job progress. |
| **Real-Time Streaming** | **Server-Sent Events (SSE)** | `sse-starlette` | Streams granular pipeline telemetry (e.g., *"Parsing pages"*, *"Extracting FR/NFRs"*, *"Checking contradictions"*) directly to the frontend. |
| **API Client Generation** | **OpenAPI TypeScript Codegen** | `openapi-ts` | Automatically generates TypeScript types and fetch hooks from FastAPI's OpenAPI schema to prevent contract drift. |

---

## 4. Document Ingestion, NLP & AI Engine

### 4.1 Document Parsing & Layout Extraction
* **IBM Docling / Unstructured.io:** Layout-aware document understanding models capable of separating headers, footers, section headings, nested bullet lists, and complex multi-column tables without scrambling reading order.
* **PyMuPDF (`fitz`):** High-speed PDF text and bounding-box coordinate extraction ($x_0, y_0, x_1, y_1$ per text span) used to map extracted requirements back to exact visual coordinates on the PDF page.
* **`python-docx` + LibreOffice Headless:** Extracts structured XML paragraphs, styles (`Heading 1`–`Heading 6`), and tables from `.docx` files, while LibreOffice converts `.docx` to `.pdf` for unified visual rendering in the frontend viewer.

### 4.2 Hybrid NLP & LLM Intelligence Pipeline
* **Deterministic Linguistic Layer (`spaCy` `en_core_web_trf`):**
  * Performs dependency parsing, POS tagging, and morphological analysis to flag structural defects instantly before invoking LLMs.
  * Implements rule sets from **INCOSE Guide for Writing Requirements** and **ISO/IEC/IEEE 29148** (detecting passive voice without an actor, weak modal verbs like *"may"*, *"might"*, *"should"*, unbounded lists like *"etc."*, *"and/or"*, and unquantified adverbs like *"quickly"*, *"efficiently"*, *"user-friendly"*).
* **LLM Orchestration & Structured Outputs (`Instructor` + `LangGraph`):**
  * **Primary Reasoning Models:** Anthropic **Claude 3.7 Sonnet** or OpenAI **GPT-4o** for atomic requirement decomposition, FR/NFR classification, ambiguity root-cause explanation, concrete rewrite suggestions, and executive summarization.
  * **Fast/High-Throughput Model:** **GPT-4o-mini** or **Claude 3.5 Haiku** for initial candidate sentence filtering and batch metadata tagging.
  * **Strict Schema Enforcement:** Uses `Instructor` with Pydantic v2 models to guarantee valid JSON outputs with exact source character offsets for hallucination-free citation grounding.
* **Embeddings & Pairwise Conflict Detection:**
  * **Embedding Model:** `text-embedding-3-large` (configured to $d = 1536$ dimensions) or self-hosted `BAAI/bge-large-en-v1.5` ($d = 1024$) to convert every atomic requirement into a dense semantic vector.
  * **Cross-Encoder NLI Reranker:** `cross-encoder/nli-deberta-v3-large` (running on ONNX Runtime) to score high-similarity requirement pairs for `Entailment` (duplicates) vs. `Contradiction` (conflicting specifications) before final LLM adjudication.

---

## 5. Data Storage & Infrastructure Stack

| Component | Technology | Purpose & Configuration |
| :--- | :--- | :--- |
| **Primary Database** | **PostgreSQL 16** | Stores workspaces, projects, document metadata, hierarchical requirement trees, revision diffs, RTM links, and audit logs. |
| **Vector Search Engine** | **`pgvector` (`0.7+`)** | PostgreSQL extension storing `vector(1536)` embeddings with **HNSW (Hierarchical Navigable Small World)** indexes for sub-10ms cosine similarity queries. |
| **Blob / Object Storage** | **AWS S3 / MinIO** | Stores raw uploaded `.pdf` and `.docx` documents, normalized PDF previews, intermediate JSON parse trees, and generated export reports. |
| **Authentication & RBAC** | **Clerk / Auth0 (OIDC + JWT)** | Handles SSO, organization workspaces, and Role-Based Access Control (`Admin`, `Lead Architect`, `Reviewer`, `Viewer`). |
| **Containerization** | **Docker & Kubernetes (AWS EKS)** | Isolates API web pods from CPU/GPU-heavy NLP worker pods for independent horizontal autoscaling. |
| **AI Observability & Tracing** | **Langfuse** | Logs prompt versions, token consumption per SRS document, latency per pipeline stage, and user feedback on AI suggestions. |
| **Application Monitoring** | **OpenTelemetry + Grafana + Sentry** | Distributed tracing across FastAPI and Celery workers, infrastructure metrics, and real-time exception capture. |

---

## 6. Report Export & Generation Tooling

* **Executive PDF Reports:** **WeasyPrint** + **Jinja2** — Renders rich HTML5/CSS3 templates (including SVG charts, color-coded severity badges, and summary tables) into print-ready PDF audit reports.
* **Requirement Traceability Matrix (RTM) Spreadsheets:** **`openpyxl`** — Generates multi-tab `.xlsx` workbooks featuring a Traceability Matrix tab, Full Requirements Register tab, Defects & Ambiguities tab, and frozen headers with conditional formatting.
* **Editable Word Reports:** **`python-docx`** — Exports cleaned, rewritten SRS drafts and review comment tables into `.docx` format so teams can re-import them into legacy enterprise workflows.