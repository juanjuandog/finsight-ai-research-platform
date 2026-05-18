# FinSight AI

[English](README.md) | [简体中文](README.zh-CN.md)

![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-blue)
![Redis](https://img.shields.io/badge/Redis-single--flight-red)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-workflows-orange)

Open-source AI equity research agent with evidence-grounded reports, resilient workflow orchestration, and RAG evaluation.

FinSight turns filings, financial reports, research notes, market data, and company events into source-grounded answers and versioned AI research reports. The project is intentionally backend-heavy: it shows how to build the infrastructure around an AI agent, not just how to call a model.

![FinSight dashboard preview](docs/dashboard-preview.png)

## Why It Exists

Most RAG demos stop at "retrieve chunks and ask an LLM." FinSight focuses on the parts that make an AI research system dependable:

- long-running agent workflows with explicit state transitions;
- idempotent task submission and duplicate execution control;
- Redis Lua single-flight leases with fencing tokens;
- report caching tied to data snapshots instead of loose prompt strings;
- PostgreSQL/pgvector hybrid retrieval with evidence traceability;
- RAG and agent quality evaluation for regression checks.

## Highlights

| Area | What FinSight Implements |
| --- | --- |
| Agent workflow | Data ingestion, metric recalculation, document indexing, intelligence build, and AI report generation as recoverable stages |
| Concurrency control | Idempotency keys, repository-level `createIfAbsent`, Redis Lua single-flight lease, fencing token, local fallback lock |
| Failure recovery | Task status machine, stage tracking, retry, dead letter state, timeout takeover scheduler |
| Trustworthy AI cache | `contextHash`, `dataSnapshotHash`, `reportVersion`, Redis/PostgreSQL-backed report reuse |
| Retrieval | PostgreSQL JSONB, full-text search, pgvector embeddings, hybrid recall, deduped evidence chunks |
| Evaluation | RAG hit rate, evidence coverage, answer coverage, hallucination risk, conclusion consistency, confidence calibration, latency |
| Demo surface | Spring Boot API, static dashboard, sample data flow, Actuator and Prometheus metrics |

## Architecture

```mermaid
flowchart LR
    UI["Dashboard / REST API"] --> Backend["Spring Boot Backend"]
    Backend --> Workflow["Agent Workflow Orchestrator"]
    Workflow --> MQ["RabbitMQ Async Queue"]
    Workflow --> Redis["Redis Lua Lease + Cache"]
    Workflow --> PG["PostgreSQL + pgvector"]
    Backend --> AI["FastAPI AI Service / Ollama fallback"]
    PG --> Retrieval["Hybrid Retrieval + Evidence"]
    Retrieval --> Backend
    AI --> Report["Versioned AI Report"]
    Report --> PG
    Backend --> Eval["RAG / Agent Evaluation"]
```

More detail: [Architecture Notes](docs/architecture.md)

## Documentation

- [Architecture Notes](docs/architecture.md)
- [Resume And Interview Notes](docs/resume-and-interview.md)
- [GitHub Presentation Snippets](docs/github-profile.md)
- [Troubleshooting](docs/troubleshooting.md)

## Quick Start

### 1. Run the full stack

```bash
./scripts/run-full-stack.sh
```

Then open:

```bash
open http://localhost:8080
```

This starts the backend, dashboard, PostgreSQL/pgvector, RabbitMQ, Redis, the FastAPI AI sidecar, and supporting infrastructure. If Ollama is not running, the AI service returns deterministic fallback analysis so the demo still works.

### 2. Seed and exercise the demo

In another terminal:

```bash
./scripts/quick-demo.sh
```

Or run the smaller flows separately:

```bash
./scripts/demo-flow.sh
./scripts/demo-workflow.sh
```

Useful endpoints:

```bash
GET  /api/workflows/summary
POST /api/evaluations/rag/run
GET  /api/companies/600519/ai-analysis/latest
GET  /api/document-index/600519/search?q=现金流风险
```

Example demo output after `./scripts/quick-demo.sh`:

| Signal | Example Result |
| --- | --- |
| Agent workflow | `1/1 tasks`, `0 failed/dead-letter` |
| RAG evaluation | `85 / 100`, `2/3 cases passed` |
| Evidence index | `6 documents`, `6 chunks` for `600519` |
| Intelligence graph | `20 events`, `36 entities`, `47 relations` |
| Report cache | `dataSnapshotHash + contextHash + reportVersion` |

### 3. Run without Docker

For a lightweight local backend using in-memory repositories:

```bash
cd backend
mvn spring-boot:run
open http://localhost:8080
```

## Modules

- `backend`: Spring Boot service for APIs, domain workflow, metrics, and RAG orchestration.
- `ai-service`: FastAPI service for document parsing, entity extraction, embedding, rerank, and answer generation stubs.
- `docker`: local infrastructure placeholders.

## Alternative Run Modes

Backend:

```bash
cd backend
mvn spring-boot:run
```

Dashboard:

```bash
open http://localhost:8080
```

Backend with PostgreSQL profile:

```bash
docker compose up -d postgres
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=postgres,prod
```

Backend with PostgreSQL + RabbitMQ workflow:

```bash
./scripts/run-backend-workflow.sh
```

Production-like stack with PostgreSQL, pgvector, RabbitMQ, FastAPI AI service, Actuator, and the dashboard:

```bash
./scripts/run-full-stack.sh
open http://localhost:8080
```

AI service:

```bash
cd ai-service
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

Optional local Ollama analysis:

```bash
ollama serve
ollama pull qwen2.5:7b
```

The FastAPI sidecar calls `OLLAMA_BASE_URL` (`http://localhost:11434` by default) and `OLLAMA_MODEL`
(`qwen2.5:7b` by default) from `/analyze-stock`. If Ollama is not installed, not running, or the model is
missing, the endpoint returns a deterministic rule-based fallback with `aiGenerated=false`, so the dashboard
keeps working.

## Sample API Flow

1. `POST /api/ingestion/demo` seeds a sample company document and financial statements.
2. `POST /api/metrics/recalculate/600519` calculates financial indicators and risk signals.
3. `POST /api/analysis/ask` asks a source-grounded question.
4. `POST /api/document-index/{symbol}/rebuild` rebuilds document chunks for retrieval.
5. `POST /api/intelligence/{symbol}/rebuild` builds timeline events and a lightweight knowledge graph.

Async workflow:

```bash
POST /api/ingestion/demo/async
GET /api/workflows
GET /api/document-index/600519/search?q=现金流风险
GET /api/metrics/600519/runs
GET /api/intelligence/600519/timeline
GET /api/intelligence/600519/graph
POST /api/evaluations/rag/run
```

## Database Stage

The PostgreSQL implementation is enabled by `postgres,prod` profiles. Flyway creates the core schema:

- `companies`
- `financial_documents`
- `financial_statements`
- `financial_metrics`
- `risk_signals`
- `workflow_tasks`
- `company_events`
- `rag_traces`
- `stock_analysis_reports`
- `user_watchlists`

Default profile still uses in-memory repositories so the backend remains easy to run without Docker.

## Workflow Stage

The workflow stage splits long financial data processing into task lifecycle and execution:

- `WorkflowTask` stores idempotency key, status, agent stage, attempt count, payload, error message, lease owner, fencing token, and update time.
- `WorkflowTaskPublisher` has two implementations:
  - default direct publisher for local development;
  - RabbitMQ publisher enabled by `rabbitmq` profile.
- `WorkflowOrchestrator` uses Redis Lua single-flight leases, idempotency keys, and local fallback locking to prevent duplicate cross-node execution.
- `WorkflowRecoveryScheduler` scans timed-out `RUNNING` tasks, marks them recoverable/dead-lettered, and republishes retryable work.
- Agent stages model the long-running research flow from ingestion to metrics, indexing, intelligence build, AI analysis, success, failure, and recovery.
- `DOCUMENT_INDEX_BUILD` chunks ingested documents and writes retrieval-ready evidence chunks.
- `COMPANY_INTELLIGENCE_BUILD` turns documents, metrics, and risk signals into timeline events and graph relations.
- `STOCK_AI_ANALYSIS` creates source-grounded AI stock reports and persists them for history and caching.
- `RabbitWorkflowListener` consumes messages and moves failed messages to a dead-letter queue when RabbitMQ rejects them.

Run:

```bash
./scripts/run-backend-workflow.sh
./scripts/demo-workflow.sh
```

## Retrieval Stage

The retrieval stage indexes financial documents at evidence-chunk granularity:

- `DocumentChunker` splits long documents with overlap and section metadata.
- `EmbeddingService` creates deterministic 384-dimensional embeddings for local demos, and can call the FastAPI AI sidecar `/embed` endpoint when `finsight.ai-service.enabled=true`.
- `DocumentChunkRepository` supports keyword search, vector search, and chunk replacement.
- PostgreSQL profile stores chunks in `document_chunks` with JSONB metadata, full-text GIN index, and pgvector cosine index.
- `HybridRetrievalGateway` merges keyword and vector channels, deduplicates chunks, and passes source-bound evidence to RAG.

Useful endpoints:

```bash
POST /api/document-index/600519/rebuild
GET /api/document-index/600519/count
GET /api/document-index/600519/search?q=现金流风险
```

## Metric Engine Stage

The metric engine stage turns hard-coded ratios into a governed calculation pipeline:

- `MetricDefinitionCatalog` defines source metrics, ratio metrics, year-over-year metrics, and derived spreads.
- `CoreFinancialMetricCalculator` evaluates metrics in fiscal-year order and stores results with a plan version.
- `MetricCalculationRun` records each calculation run with statement count, metric count, risk count, timestamps, and metadata.
- `RiskRule` components evaluate financial risk signals from the metric map:
  - cash earnings quality;
  - receivable pressure;
  - profitability trend weakening;
  - leverage risk.

Useful endpoints:

```bash
GET /api/metrics/definitions
POST /api/metrics/recalculate/600519
GET /api/metrics/600519
GET /api/metrics/600519/risks
GET /api/metrics/600519/runs
```

## Intelligence Stage

The intelligence stage upgrades isolated documents and metrics into company state modeling:

- `CompanyIntelligenceService` extracts standard events from filings, research notes, metrics, and risk signals.
- `CompanyEventRepository` stores a company timeline ordered by event date.
- `KnowledgeGraphRepository` stores lightweight graph nodes and relations in PostgreSQL.
- Graph entities include company, industry, document, product/keyword, financial metric, and risk event.
- Graph relations include industry membership, published documents, mentioned keywords, financial metrics, risks, and timeline events.

Useful endpoints:

```bash
POST /api/intelligence/600519/rebuild
GET /api/intelligence/600519/timeline
GET /api/intelligence/600519/graph
```

## Dashboard And Evaluation Stage

The final stage adds a demo console and regression-style RAG evaluation:

- Static dashboard is served by Spring Boot from `/`.
- The dashboard shows workflow tasks, metric output, retrieval evidence, timeline events, graph counts, and evaluation results.
- `EvaluationCaseCatalog` defines fixed financial QA test cases.
- `RagEvaluationService` checks RAG hit rate, evidence coverage, answer coverage, citation presence, hallucination risk, conclusion consistency, confidence calibration, and latency.

Useful endpoints:

```bash
GET /
GET /api/evaluations/rag/cases
POST /api/evaluations/rag/run
```

## Stock AI Stage

The stock AI stage turns the dashboard into a practical A-share research workflow:

- `StockUniverseService` syncs 5500+ A-share symbols from free public providers and falls back to Eastmoney search.
- `StockAnalysisApplicationService` submits single-stock and batch analysis as workflow tasks.
- `StockAiAnalysisService` builds a prompt context from quote data, financial metrics, risk signals, and RAG evidence chunks.
- AI analysis calls the FastAPI sidecar and local Ollama when available, then falls back to deterministic rules when the model is unavailable.
- `stock_analysis_reports` stores every generated report with model/source metadata, citations, context hash, `data_snapshot_hash`, report version, and generated time.
- `StockAnalysisCache` has an in-memory local implementation and a Redis implementation enabled by the `redis` profile; cache keys are tied to the data snapshot hash so stale AI conclusions are not reused after evidence changes.
- `StockMarketScheduler` can sync the stock universe and submit a morning batch scan on a configurable cron schedule.
- `user_watchlists` provides a simple user-scoped stock watchlist foundation using the `X-Finsight-User` request header.

Useful endpoints:

```bash
POST /api/companies/sync-a-shares
POST /api/companies/batch-analysis
GET /api/companies/600519/ai-analysis
GET /api/companies/600519/ai-analysis/latest
GET /api/companies/600519/ai-analysis/history
GET /api/watchlist
POST /api/watchlist/600519
DELETE /api/watchlist/600519
```

## Production Engineering Stage

The production-like stage makes the prototype easier to present as a backend/AI system:

- Docker Compose builds and runs `backend`, `ai-service`, PostgreSQL/pgvector, RabbitMQ, Redis, Elasticsearch, and MinIO.
- `postgres,rabbitmq,redis,prod` profiles enable persistent repositories, Flyway migrations, pgvector search, Redis analysis cache, and RabbitMQ task dispatch.
- `RestAiServiceClient` calls FastAPI `/rerank` and `/generate-answer`, while keeping deterministic local fallback for demos and tests.
- Workflow APIs expose task listing, task detail, status summary, and manual retry for failed/dead-letter tasks.
- Spring Boot Actuator exposes health, metrics, and Prometheus scrape output at `/actuator/health`, `/actuator/metrics`, and `/actuator/prometheus`.
- Test coverage includes deterministic embedding tests and a Testcontainers smoke test for PostgreSQL/pgvector + RabbitMQ profiles.

Useful endpoints:

```bash
GET /actuator/health
GET /actuator/prometheus
GET /api/workflows/summary
GET /api/workflows/{taskId}
POST /api/workflows/{taskId}/retry
```
