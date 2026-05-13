# FinSight AI

FinSight is a backend-heavy AI financial research platform prototype. It turns financial reports, filings, research notes, news, and market data into structured company events, financial metrics, and source-grounded AI answers.

The first version focuses on architecture that is useful for interviews:

- Multi-source financial data ingestion with adapter/template abstractions.
- Event-driven document processing workflow with retryable task states.
- Financial metric calculation and risk signal detection.
- Retrieval-augmented answer orchestration with evidence binding.
- Separate Java business backend and Python AI capability service.
- Full A-share stock universe sync, async AI stock analysis, report persistence, and Redis-ready caching.
- Static dashboard and RAG evaluation for demos and regression checks.

## Modules

- `backend`: Spring Boot service for APIs, domain workflow, metrics, and RAG orchestration.
- `ai-service`: FastAPI service for document parsing, entity extraction, embedding, rerank, and answer generation stubs.
- `docker`: local infrastructure placeholders.

## Run

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

## Sample Flow

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
