# End-to-End Implementation Guide & Roadmap

## 1. Monorepo Directory Structure

Organizing the frontend, backend, and shared schemas in a clean monorepo streamlines local development and CI/CD deployments:

```text
srs-analyzer-platform/
├── apps/
│   ├── web/                          # Next.js 15 TypeScript Frontend
│   │   ├── src/
│   │   │   ├── app/                  # App Router pages (dashboard, workspace, analyzer)
│   │   │   ├── components/
│   │   │   │   ├── pdf-annotator/    # PDF.js viewer + bounding-box highlight overlays
│   │   │   │   ├── rtm-matrix/       # TanStack Table + React Flow traceability graph
│   │   │   │   ├── revision-diff/    # Side-by-side semantic version comparison UI
│   │   │   │   └── reports/          # Quality scorecards & export triggers
│   │   │   └── lib/api-client/       # Auto-generated OpenAPI TypeScript SDK
│   └── api/                          # FastAPI Python 3.12 Backend & Celery Workers
│       ├── app/
│       │   ├── api/v1/               # REST endpoints & SSE progress streams
│       │   ├── core/                 # Config, Auth/JWT, DB session, S3 client
│       │   ├── models/               # SQLAlchemy 2.0 ORM + pgvector models
│       │   ├── schemas/              # Pydantic v2 request/response & LLM schemas
│       │   ├── services/
│       │   │   ├── parser/           # PDF/DOCX layout-aware extraction
│       │   │   ├── extractor/        # Atomic requirement extraction & FR/NFR classifier
│       │   │   ├── quality/          # spaCy INCOSE rules + LLM ambiguity/gap engine
│       │   │   ├── conflicts/        # pgvector candidate search + NLI/LLM conflict check
│       │   │   ├── rtm/              # Traceability linking & summary generator
│       │   │   ├── diff/             # Hungarian algorithm version revision tracker
│       │   │   └── exporter/         # WeasyPrint PDF & openpyxl XLSX generators
│       │   └── workers/              # Celery tasks & pipeline orchestrator
├── infra/
│   └── docker/                       # Dockerfiles & docker-compose.yml (PG+pgvector, Redis, MinIO)
└── tests/
    ├── golden_dataset/               # Annotated benchmark SRS documents (FR/NFR, ambiguities, conflicts)
    └── evaluation/                   # Precision/Recall/F1 automated evaluation scripts
```

---

## 2. Phased Implementation Roadmap (12 Weeks)

### Phase 1: Infrastructure, Ingestion & Layout Parsing (Weeks 1–3)
* **Deliverables:** Docker Compose environment (`PostgreSQL 16 + pgvector`, `Redis`, `MinIO`), FastAPI skeleton with Auth/RBAC, presigned S3 upload flow, LibreOffice DOCX-to-PDF conversion, and layout-aware PDF/DOCX parser extracting sections and bounding boxes.
* **Milestone:** Upload a 50-page PDF or DOCX SRS and render its structured section hierarchy alongside visual bounding boxes in the Next.js viewer.

### Phase 2: Atomic Extraction, FR/NFR Classification & Ambiguity Engine (Weeks 4–6)
* **Deliverables:** `Instructor` + Pydantic structured extraction pipeline, compound requirement splitter, FR vs. NFR (ISO 25010) classifier, `spaCy` INCOSE linguistic rule matcher, and LLM contextual ambiguity/incompleteness detector with suggested rewrites.
* **Milestone:** Achieve $\ge 90\%$ precision on FR/NFR classification and surface inline ambiguity highlights with one-click rewrite suggestions.

### Phase 3: Deduplication, Conflict Detection, Summaries & RTM (Weeks 7–9)
* **Deliverables:** `pgvector` HNSW embedding pipeline, two-stage duplicate and contradiction detector (`EXACT_DUPLICATE`, `SEMANTIC_OVERLAP`, `DIRECT_CONTRADICTION`), executive & module summary generator, and automated Requirement Traceability Matrix (RTM) builder with orphan detection.
* **Milestone:** Detect planted numerical and logical contradictions across distant SRS sections and render an interactive RTM table and dependency graph.

### Phase 4: Revision Tracking, Multi-Format Exports & Hardening (Weeks 10–12)
* **Deliverables:** Hungarian-matching version diff engine (`ADDED`, `DELETED`, `MODIFIED_BREAKING`, `MODIFIED_EDITORIAL`), WeasyPrint executive PDF report generator, `openpyxl` multi-sheet Excel RTM exporter, Langfuse observability, and golden-dataset CI evaluation suite.
* **Milestone:** Compare `SRS_v1.pdf` against `SRS_v2.docx`, inspect semantic diffs and downstream impact, and export a complete audit package.

---

## 3. Core Module Implementations

### 3.1 Layout-Aware Document Parser (`app/services/parser/document_parser.py`)
Extracts text spans, preserves section hierarchy (`1.0` $\rightarrow$ `1.1`), and captures bounding-box coordinates so UI annotations map cleanly to PDF pages.

```python
import re
import fitz  # PyMuPDF
from dataclasses import dataclass, field
from typing import List, Dict, Optional

HEADING_REGEX = re.compile(r"^(\d+(?:\.\d+)*)\s+([A-Z][^\n]{2,100})$")

@dataclass
class ParsedBlock:
    section_path: str
    text: str
    page_number: int
    bounding_box: Dict[str, float]
    is_table_row: bool = False

class SRSLayoutParser:
    def parse_pdf(self, pdf_path: str) -> List[ParsedBlock]:
        doc = fitz.open(pdf_path)
        blocks: List[ParsedBlock] = []
        section_stack: List[str] = ["1.0 General"]

        for page_idx in range(len(doc)):
            page = doc[page_idx]
            page_height = page.rect.height
            # Extract blocks sorted in natural reading order (top-to-bottom, left-to-right)
            raw_blocks = page.get_text("blocks", sort=True)

            for b in raw_blocks:
                x0, y0, x1, y1, text, _, block_type = b
                if block_type != 0:  # Skip non-text image blocks
                    continue

                # Filter out running headers (top 6%) and footers (bottom 6%)
                if y0 < page_height * 0.06 or y1 > page_height * 0.94:
                    continue

                cleaned = " ".join(text.strip().split())
                if len(cleaned) < 10:
                    continue

                # Track hierarchical section headings
                heading_match = HEADING_REGEX.match(cleaned)
                if heading_match:
                    sec_num, sec_title = heading_match.groups()
                    depth = sec_num.count(".")
                    section_stack = section_stack[:depth] + [f"{sec_num} {sec_title}"]
                    continue

                blocks.append(
                    ParsedBlock(
                        section_path=" > ".join(section_stack),
                        text=cleaned,
                        page_number=page_idx + 1,
                        bounding_box={
                            "x0": round(x0, 2),
                            "y0": round(y0, 2),
                            "x1": round(x1, 2),
                            "y1": round(y1, 2),
                        },
                    )
                )
        return blocks
```

### 3.2 Hybrid Ambiguity & Atomic Extraction Engine (`app/services/quality/analyzer.py`)
Combines deterministic `spaCy` linguistic rules with `Instructor`-enforced structured LLM outputs to extract atomic requirements, classify FR/NFRs, and generate concrete rewrites.

```python
import spacy
import instructor
from openai import AsyncOpenAI
from pydantic import BaseModel, Field
from typing import List, Literal, Optional

nlp = spacy.load("en_core_web_trf")
client = instructor.from_openai(AsyncOpenAI())

WEAK_MODALS = {"should", "could", "may", "might", "would"}
VAGUE_TERMS = {
    "fast", "quick", "quickly", "user-friendly", "intuitive", "seamless",
    "scalable", "flexible", "robust", "efficient", "minimal", "adequate",
    "etc", "and/or", "asap", "appropriate", "reasonable"
}

class LinguisticFlag(BaseModel):
    rule_id: str
    flagged_phrase: str
    reason: str

class ExtractedRequirement(BaseModel):
    req_code: str = Field(..., description="Sequential or document ID e.g. FR-001")
    raw_quote: str = Field(..., description="Exact verbatim quote from source text")
    atomic_statement: str = Field(..., description="Single, testable requirement statement")
    req_type: Literal["FUNCTIONAL", "NON_FUNCTIONAL", "CONSTRAINT"]
    nfr_category: Optional[Literal[
        "PERFORMANCE", "SECURITY", "RELIABILITY", "SCALABILITY", "USABILITY", "COMPLIANCE"
    ]] = None
    ears_pattern: Literal["UBIQUITOUS", "EVENT_DRIVEN", "STATE_DRIVEN", "UNWANTED_BEHAVIOR", "OPTIONAL"]
    is_ambiguous: bool
    is_incomplete: bool
    defect_explanation: Optional[str] = Field(
        None, description="Specific explanation of ambiguity, missing actor, or missing metric"
    )
    suggested_rewrite: Optional[str] = Field(
        None, description="IEEE 29148 compliant rewrite with measurable criteria"
    )
    clarity_score: float = Field(..., ge=0.0, le=100.0)

class BlockExtractionResult(BaseModel):
    requirements: List[ExtractedRequirement]
    missing_section_observations: List[str] = Field(
        default_factory=list,
        description="Expected edge cases or requirements missing from this section"
    )

def run_linguistic_rules(text: str) -> List[LinguisticFlag]:
    doc = nlp(text)
    flags: List[LinguisticFlag] = []

    for token in doc:
        lower = token.text.lower()
        if lower in WEAK_MODALS:
            flags.append(LinguisticFlag(
                rule_id="INCOSE-R12",
                flagged_phrase=token.text,
                reason=f"Weak modal verb '{token.text}' implies optionality; use 'shall' for mandatory requirements."
            ))
        if lower in VAGUE_TERMS:
            flags.append(LinguisticFlag(
                rule_id="INCOSE-R07",
                flagged_phrase=token.text,
                reason=f"Subjective/unbounded term '{token.text}' lacks measurable acceptance criteria."
            ))
        # Detect passive voice without an explicit agent ('by ...')
        if token.dep_ == "nsubjpass":
            has_agent = any(child.dep_ == "agent" for child in token.head.children)
            if not has_agent:
                flags.append(LinguisticFlag(
                    rule_id="INCOSE-R03",
                    flagged_phrase=token.head.text,
                    reason="Passive voice omits the system actor responsible for performing the action."
                ))
    return flags

async def analyze_srs_block(section_path: str, text: str) -> BlockExtractionResult:
    linguistic_hints = run_linguistic_rules(text)
    hint_summary = "\n".join(f"- [{h.rule_id}] {h.reason}" for h in linguistic_hints) or "None"

    return await client.chat.completions.create(
        model="gpt-4o",
        temperature=0.1,
        response_model=BlockExtractionResult,
        messages=[
            {
                "role": "system",
                "content": (
                    "You are a Principal Systems Engineer auditing an SRS per ISO/IEC/IEEE 29148. "
                    "Decompose compound clauses into atomic requirements, classify FR vs NFR, "
                    "identify ambiguities or missing failure/metric conditions, and provide "
                    "precise, verifiable rewrites without inventing unauthorized business scope."
                ),
            },
            {
                "role": "user",
                "content": (
                    f"Section: {section_path}\n"
                    f"Automated Linguistic Flags:\n{hint_summary}\n\n"
                    f"Source Text:\n{text}"
                ),
            },
        ],
    )
```

### 3.3 Two-Stage Duplicate & Contradiction Engine (`app/services/conflicts/conflict_engine.py`)
Uses `pgvector` cosine similarity to retrieve high-affinity pairs in $O(N \log N)$ time, then adjudicates duplicates vs. logical contradictions via structured LLM reasoning.

```python
import instructor
from openai import AsyncOpenAI
from pydantic import BaseModel, Field
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession
from typing import Literal, Optional, List

client = instructor.from_openai(AsyncOpenAI())

class PairwiseAdjudication(BaseModel):
    relationship: Literal["EXACT_DUPLICATE", "SEMANTIC_OVERLAP", "DIRECT_CONTRADICTION", "INDEPENDENT"]
    severity: Literal["CRITICAL", "HIGH", "MEDIUM", "LOW"]
    explanation: str = Field(..., description="Why these requirements duplicate or contradict each other")
    resolution_recommendation: str

async def detect_duplicates_and_conflicts(db: AsyncSession, version_id: str) -> List[dict]:
    # Stage 1: Retrieve candidate pairs with cosine similarity >= 0.72 using pgvector (<=> is cosine distance)
    query = text("""
        SELECT
            r1.id AS req1_id, r1.req_code AS req1_code, r1.normalized_text AS req1_text,
            r2.id AS req2_id, r2.req_code AS req2_code, r2.normalized_text AS req2_text,
            1 - (r1.embedding <=> r2.embedding) AS similarity
        FROM requirements r1
        JOIN requirements r2
          ON r1.version_id = r2.version_id
         AND r1.id < r2.id
        WHERE r1.version_id = :version_id
          AND (1 - (r1.embedding <=> r2.embedding)) >= 0.72
        ORDER BY similarity DESC
        LIMIT 150;
    """)
    result = await db.execute(query, {"version_id": version_id})
    candidate_pairs = result.fetchall()

    detected_issues = []

    # Stage 2: Adjudicate each high-similarity candidate pair
    for pair in candidate_pairs:
        verdict = await client.chat.completions.create(
            model="gpt-4o",
            temperature=0.0,
            response_model=PairwiseAdjudication,
            messages=[
                {
                    "role": "system",
                    "content": (
                        "Compare two software requirements from the same SRS. Determine if they are "
                        "EXACT_DUPLICATE, SEMANTIC_OVERLAP, DIRECT_CONTRADICTION (incompatible thresholds, "
                        "states, permissions, or behaviors), or INDEPENDENT (related topic, compatible rules)."
                    ),
                },
                {
                    "role": "user",
                    "content": (
                        f"Requirement A [{pair.req1_code}]: {pair.req1_text}\n"
                        f"Requirement B [{pair.req2_code}]: {pair.req2_text}"
                    ),
                },
            ],
        )

        if verdict.relationship != "INDEPENDENT":
            detected_issues.append({
                "primary_req_id": pair.req1_id,
                "related_req_id": pair.req2_id,
                "issue_category": "CONFLICT" if verdict.relationship == "DIRECT_CONTRADICTION" else "DUPLICATE",
                "severity": "CRITICAL" if verdict.relationship == "DIRECT_CONTRADICTION" else verdict.severity,
                "explanation": verdict.explanation,
                "suggested_rewrite": verdict.resolution_recommendation,
                "similarity_score": round(float(pair.similarity), 4),
            })

    return detected_issues
```

### 3.4 Revision Tracking & Semantic Diff Engine (`app/services/diff/diff_engine.py`)
Aligns requirements across two SRS versions using the Hungarian algorithm (`scipy.optimize.linear_sum_assignment`) over cosine similarity matrices so shifted IDs do not cause false deletions/additions.

```python
import numpy as np
from scipy.optimize import linear_sum_assignment
from typing import List, Dict, Any

MATCH_THRESHOLD = 0.78

def compute_version_diff(
    base_reqs: List[Dict[str, Any]],
    target_reqs: List[Dict[str, Any]]
) -> List[Dict[str, Any]]:
    """
    Matches requirements between base_reqs (V_n) and target_reqs (V_{n+1})
    using optimal bipartite matching over dense embeddings.
    """
    if not base_reqs:
        return [{"base_req": None, "target_req": r, "change_type": "ADDED", "similarity": 0.0} for r in target_reqs]
    if not target_reqs:
        return [{"base_req": r, "target_req": None, "change_type": "DELETED", "similarity": 0.0} for r in base_reqs]

    base_vecs = np.array([r["embedding"] for r in base_reqs], dtype=np.float32)
    target_vecs = np.array([r["embedding"] for r in target_reqs], dtype=np.float32)

    # Normalize vectors and compute cosine similarity matrix S of shape (len(base), len(target))
    base_norm = base_vecs / np.linalg.norm(base_vecs, axis=1, keepdims=True)
    target_norm = target_vecs / np.linalg.norm(target_vecs, axis=1, keepdims=True)
    sim_matrix = np.dot(base_norm, target_norm.T)

    # Minimize cost = 1 - similarity
    row_ind, col_ind = linear_sum_assignment(1.0 - sim_matrix)

    matched_base = set()
    matched_target = set()
    diffs = []

    for r_idx, c_idx in zip(row_ind, col_ind):
        sim = float(sim_matrix[r_idx, c_idx])
        if sim >= MATCH_THRESHOLD:
            matched_base.add(r_idx)
            matched_target.add(c_idx)
            b_req = base_reqs[r_idx]
            t_req = target_reqs[c_idx]

            if b_req["normalized_text"].strip() == t_req["normalized_text"].strip():
                change_type = "UNCHANGED"
            elif sim >= 0.96:
                change_type = "MODIFIED_EDITORIAL"
            else:
                change_type = "MODIFIED_BREAKING"

            diffs.append({
                "base_req": b_req,
                "target_req": t_req,
                "change_type": change_type,
                "similarity": round(sim, 4),
            })

    for idx, b_req in enumerate(base_reqs):
        if idx not in matched_base:
            diffs.append({"base_req": b_req, "target_req": None, "change_type": "DELETED", "similarity": 0.0})

    for idx, t_req in enumerate(target_reqs):
        if idx not in matched_target:
            diffs.append({"base_req": None, "target_req": t_req, "change_type": "ADDED", "similarity": 0.0})

    return diffs
```

### 3.5 RTM & Excel Workbook Exporter (`app/services/exporter/xlsx_exporter.py`)
Generates a formatted multi-sheet Excel Requirement Traceability Matrix and Defect Audit workbook.

```python
import openpyxl
from openpyxl.styles import Font, PatternFill, Alignment
from typing import List, Dict, Any

def export_rtm_workbook(
    output_path: str,
    requirements: List[Dict[str, Any]],
    issues: List[Dict[str, Any]],
    rtm_links: List[Dict[str, Any]]
) -> str:
    wb = openpyxl.Workbook()

    # Sheet 1: Requirement Traceability Matrix
    ws_rtm = wb.active
    ws_rtm.title = "Traceability Matrix"
    headers = [
        "Req ID", "Section Path", "Type", "Category", "Atomic Requirement",
        "Clarity Score", "Upstream Source", "Downstream Verification / Link", "Traceability Status"
    ]
    ws_rtm.append(headers)

    header_fill = PatternFill(start_color="1E293B", end_color="1E293B", fill_type="solid")
    header_font = Font(color="FFFFFF", bold=True)
    for cell in ws_rtm[1]:
        cell.fill = header_fill
        cell.font = header_font
        cell.alignment = Alignment(horizontal="center", vertical="center")

    upstream_map = {}
    downstream_map = {}
    for link in rtm_links:
        downstream_map.setdefault(link["source_node_id"], []).append(link["target_code"])
        upstream_map.setdefault(link["target_node_id"], []).append(link["source_code"])

    for req in requirements:
        up = ", ".join(upstream_map.get(req["id"], [])) or "ORPHAN (No Parent)"
        down = ", ".join(downstream_map.get(req["id"], [])) or "UNVERIFIED"
        status = "COMPLETE" if ("ORPHAN" not in up and "UNVERIFIED" not in down) else "GAP DETECTED"

        ws_rtm.append([
            req["req_code"],
            req["section_path"],
            req["req_type"],
            req.get("nfr_category") or "N/A",
            req["normalized_text"],
            float(req["clarity_score"]),
            up,
            down,
            status,
        ])

    # Sheet 2: Quality Defects & Conflicts
    ws_issues = wb.create_sheet(title="Defects & Ambiguities")
    ws_issues.append([
        "Primary Req ID", "Related Req ID", "Issue Category", "Severity",
        "Explanation", "AI Suggested Rewrite"
    ])
    for cell in ws_issues[1]:
        cell.fill = header_fill
        cell.font = header_font

    for iss in issues:
        ws_issues.append([
            iss["primary_req_code"],
            iss.get("related_req_code") or "-",
            iss["issue_category"],
            iss["severity"],
            iss["explanation"],
            iss.get("suggested_rewrite") or "",
        ])

    ws_rtm.freeze_panes = "A2"
    ws_issues.freeze_panes = "A2"
    wb.save(output_path)
    return output_path
```

---

## 4. Core REST & SSE API Specification

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/projects/{project_id}/documents/upload-url` | Generates a presigned S3 URL for `.pdf` / `.docx` upload and initializes a `document_version`. |
| `POST` | `/api/v1/versions/{version_id}/analyze` | Enqueues the asynchronous Celery pipeline (parse $\rightarrow$ extract $\rightarrow$ quality $\rightarrow$ conflicts $\rightarrow$ RTM). |
| `GET` | `/api/v1/versions/{version_id}/stream` | Server-Sent Events (SSE) stream emitting real-time analysis stage updates (`0%` to `100%`). |
| `GET` | `/api/v1/versions/{version_id}/summary` | Returns executive summary, overall readiness score, FR/NFR breakdown, and missing-section insights. |
| `GET` | `/api/v1/versions/{version_id}/requirements` | Paginated, filterable list of extracted requirements with PDF bounding boxes and clarity scores. |
| `GET` | `/api/v1/versions/{version_id}/issues` | Lists all detected ambiguities, incomplete statements, duplicates, and contradictions. |
| `PATCH` | `/api/v1/requirements/{req_id}` | Allows engineers to accept an AI rewrite, edit text, or override FR/NFR classification. |
| `GET` | `/api/v1/versions/{version_id}/rtm` | Returns nodes and edges for the Requirement Traceability Matrix table and React Flow graph. |
| `POST` | `/api/v1/documents/{doc_id}/compare` | Runs semantic revision diff between `base_version_id` and `target_version_id` with impact analysis. |
| `POST` | `/api/v1/versions/{version_id}/export` | Generates and returns a signed S3 URL for `PDF`, `XLSX` (RTM), `DOCX`, or `JSON` audit reports. |

---

## 5. AI Evaluation, Testing & Guardrails

To ensure high accuracy and prevent LLM hallucinations in mission-critical engineering workflows, enforce the following automated quality gates in CI/CD:

1. **Verbatim Grounding Verification:** Every extracted requirement's `raw_quote` is verified against the parsed source document text using normalized fuzzy substring matching ($\ge 95\%$ token overlap). Any ungrounded extraction is automatically rejected or flagged for human review.
2. **Golden Benchmark Suite (`tests/evaluation/`):**
   * **FR vs. NFR Classification:** Evaluated against the **PROMISE NFR Dataset** + 25 annotated industry SRS documents (Target: $\text{Macro-F1} \ge 0.92$).
   * **Ambiguity & Conflict Detection:** Evaluated against a synthetic test corpus containing 200 injected defects—weak modals, passive voice, missing edge cases, exact duplicates, and numeric contradictions (Target: $\text{Recall} \ge 0.88$, $\text{Precision} \ge 0.85$).
3. **Deterministic Regression Testing:** Run `pytest` with mocked/cached LLM responses for parser, Hungarian diff alignment, and Excel/PDF export logic on every pull request, and execute live model evaluations weekly via Langfuse datasets.