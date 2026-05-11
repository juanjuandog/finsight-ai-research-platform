# FinSight AI

FinSight is a backend-heavy AI financial research platform prototype. It turns financial reports, filings, research notes, news, and market data into structured company events, financial metrics, and source-grounded AI answers.

The first version focuses on architecture that is useful for interviews:

- Multi-source financial data ingestion with adapter/template abstractions.
- Event-driven document processing workflow with retryable task states.
- Financial metric calculation and risk signal detection.
- Retrieval-augmented answer orchestration with evidence binding.
- Separate Java business backend and Python AI capability service.

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

AI service:

```bash
cd ai-service
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

## Sample Flow

1. `POST /api/ingestion/demo` seeds a sample company document and financial statements.
2. `POST /api/metrics/recalculate/600519` calculates financial indicators and risk signals.
3. `POST /api/analysis/ask` asks a source-grounded question.
4. `POST /api/document-index/{symbol}/rebuild` rebuilds document chunks for retrieval.

Async workflow:

```bash
POST /api/ingestion/demo/async
GET /api/workflows
GET /api/document-index/600519/search?q=现金流风险
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

Default profile still uses in-memory repositories so the backend remains easy to run without Docker.

## Workflow Stage

The workflow stage splits long financial data processing into task lifecycle and execution:

- `WorkflowTask` stores idempotency key, status, attempt count, payload, and error message.
- `WorkflowTaskPublisher` has two implementations:
  - default direct publisher for local development;
  - RabbitMQ publisher enabled by `rabbitmq` profile.
- `WorkflowOrchestrator` executes task types and links ingestion to metric recalculation.
- `DOCUMENT_INDEX_BUILD` chunks ingested documents and writes retrieval-ready evidence chunks.
- `RabbitWorkflowListener` consumes messages and moves failed messages to a dead-letter queue when RabbitMQ rejects them.

Run:

```bash
./scripts/run-backend-workflow.sh
./scripts/demo-workflow.sh
```

## Retrieval Stage

The retrieval stage indexes financial documents at evidence-chunk granularity:

- `DocumentChunker` splits long documents with overlap and section metadata.
- `EmbeddingService` creates deterministic 16-dimensional embeddings for local demos; this is the seam where a real embedding model is plugged in later.
- `DocumentChunkRepository` supports keyword search, vector search, and chunk replacement.
- PostgreSQL profile stores chunks in `document_chunks` with JSONB metadata, full-text GIN index, and pgvector cosine index.
- `HybridRetrievalGateway` merges keyword and vector channels, deduplicates chunks, and passes source-bound evidence to RAG.

Useful endpoints:

```bash
POST /api/document-index/600519/rebuild
GET /api/document-index/600519/count
GET /api/document-index/600519/search?q=现金流风险
```
