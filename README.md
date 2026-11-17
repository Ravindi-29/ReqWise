# ReqWise — Automated SRS Analysis & Requirements Intelligence Platform

[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4%2B-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18%2F19-blue.svg)](https://react.dev/)
[![Vite](https://img.shields.io/badge/Vite-6.x-purple.svg)](https://vitejs.dev/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)](https://www.docker.com/)
[![Standard](https://img.shields.io/badge/Standard-ISO%2FIEC%2FIEEE%2029148-blueviolet.svg)](https://standards.ieee.org/ieee/29148/7255/)
[![Standard](https://img.shields.io/badge/Standard-INCOSE%20Guide-darkgreen.svg)](https://www.incose.org/)

ReqWise is an enterprise-grade AI-assisted software requirements intelligence platform designed to audit, structure, and validate Software Requirement Specification (SRS) documents before engineering begins.

---

## Architecture Overview

```text
+-----------------------------------------------------------------------------------------+
|                                    REQWISE PLATFORM                                     |
|                                                                                         |
|   +---------------------------------------+   +-------------------------------------+   |
|   |         CLIENT: React 18 / 19         |   |     API: Java 21 & Spring Boot      |   |
|   |       http://localhost:5173           |   |       http://localhost:8088 / :8080 |   |
|   |                                       |   |                                     |   |
|   | - PDF.js Grounded Canvas Annotator    |   | - Apache PDFBox 3.x Layout Parser   |   |
|   | - TanStack Table Virtualized RTM      |   | - Apache POI Word Parser            |   |
|   | - React Flow Interactive DAG Graph    |   | - INCOSE Deterministic Rule Engine  |   |
|   | - Revision Diff Comparison Viewer     |   | - Kuhn-Munkres Hungarian Engine     |   |
|   | - Executive Scorecard Dashboards      |   | - Spring SseEmitter Streaming Engine|   |
|   +-------------------+-------------------+   +------------------+------------------+   |
|                       |                                          |                      |
|                       | REST APIs / SSE Progress Stream          | JDBC Driver          |
|                       +----------------------------------------> |                      |
|                                                                  v                      |
|   +---------------------------------------+   +-------------------------------------+   |
|   |         PERSISTENT FILE STORAGE       |   |         DATABASE LAYER              |   |
|   |             (storage_data/)           |   |       PostgreSQL 16 / H2            |   |
|   |                                       |   |                                     |   |
|   | - Uploaded PDF & Word documents       |   | - Workspaces, Projects, Documents   |   |
|   | - Parsed section preview blocks       |   | - Requirements & Quality Issues     |   |
|   | - Multi-tab XLSX & Executive PDFs     |   | - Traceability DAG Links & Diffs    |   |
|   +---------------------------------------+   +-------------------------------------+   |
+-----------------------------------------------------------------------------------------+
```

---

## Key Features

1. **Grounded Document Ingestion & Layout Parsing:**
   - Multi-page PDF layout analysis preserving hierarchical section paths (`1.0` $\rightarrow$ `1.2`) and exact bounding-box coordinates ($x_0, y_0, x_1, y_1$) via **Apache PDFBox 3.x**.
   - Word XML parsing via **Apache POI**.
   - Live Server-Sent Events (SSE) streaming progress ($0\text{--}100\%$) to client UI.
2. **INCOSE Linguistic Rules & Atomic Clause Decomposition:**
   - Deterministic rule checks for **INCOSE-R12** (weak modals `should`, `could`, `may`), **INCOSE-R07** (vague terms `fast`, `user-friendly`, `seamless`), and **INCOSE-R03** (passive voice omissions).
   - Compound sentence decomposition into atomic statements conforming to **ISO/IEC/IEEE 29148**.
   - EARS syntax tagging (`UBIQUITOUS`, `EVENT_DRIVEN`, `STATE_DRIVEN`, `UNWANTED_BEHAVIOR`, `OPTIONAL`).
   - ISO 25010 categorization and clarity scoring ($0\text{--}100$).
3. **Semantic Conflict & Contradiction Engine:**
   - 1536-dimensional dense embedding generation and cosine similarity candidate retrieval.
   - Deep logical contradiction adjudicator detecting numerical threshold discrepancies (e.g. 15min vs 60min timeout) and polarity contradictions (e.g. "never expire" vs "expire every 90 days").
4. **Requirement Traceability Matrix (RTM) DAG:**
   - Auto-links Business Goals $\rightarrow$ Functional Requirements $\rightarrow$ Non-Functional Constraints $\rightarrow$ Test Verification rules.
   - Detects orphan and unverified specifications.
5. **Kuhn-Munkres Hungarian Revision Diff:**
   - Bipartite matching on embedding similarity cost matrix ($1.0 - \text{sim}$) to align shifted IDs between revisions.
   - Classifies changes into `UNCHANGED`, `MODIFIED_EDITORIAL`, `MODIFIED_BREAKING`, `ADDED`, `DELETED` with downstream impact propagation.
6. **Multi-Format Enterprise Exporters:**
   - Multi-sheet formatted Excel workbook (`Traceability Matrix`, `Defects & Ambiguities`, `Executive Scorecard`).
   - Print-ready Executive Audit Report PDF with KPI scorecard and critical defect matrices.

---

## Quick Start Guide

### Option 1: Run with Docker Compose (Recommended)

Start the entire system (PostgreSQL 16, Spring Boot API, and React SPA) with a single command:

```bash
docker-compose up --build
```

- **Web Workspace:** [http://localhost:5173](http://localhost:5173)
- **REST API:** [http://localhost:8088](http://localhost:8088)
- **Swagger Documentation:** [http://localhost:5173/swagger-ui.html](http://localhost:5173/swagger-ui.html)
- **Database:** PostgreSQL on `localhost:5432` (`reqwise` / `postgres`)

---

### Option 2: Run Host-Native (Zero Containers)

#### 1. Start Spring Boot Backend:
```bash
cd apps/api
./mvnw spring-boot:run
```
API runs on `http://localhost:8080` with embedded H2/PostgreSQL.

#### 2. Start React Vite Frontend:
```bash
cd apps/web
npm install
npm run dev
```
Client runs on `http://localhost:5173`.

---

## Automated Test Suites & Benchmarks

Run the complete backend integration and golden benchmark suite:
```bash
cd apps/api
./mvnw test
```
* **Results:** 53 / 53 integration tests passing (100% green).

Run frontend test suite and production build:
```bash
cd apps/web
npm test
npm run build
```
* **Results:** 6 / 6 tests passing; TypeScript build verified with 0 errors.

---

## Documentation

Full architectural specifications, data design, and implementation plans are located in the [`docs/`](./docs/) directory:
- [Master Implementation Plan](./docs/IMPLEMENTATION_PLAN.md)
- [System Architecture & Data Design](./docs/system_architecture_data_design.md)
- [Technology Stack Specification](./docs/technology_stack_tooling_specification.md)
- [Product Overview & Problem Statement](./docs/product_overview_problem_statement_solutions.md)
