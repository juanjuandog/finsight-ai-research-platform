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
