# Troubleshooting

This page covers the common local demo problems.

## The Dashboard Opens But Has No Data

Run the demo seeding script:

```bash
./scripts/quick-demo.sh
```

Then refresh `http://localhost:8080`.

## `curl: Failed to connect to localhost:8080`

Start the backend first:

```bash
cd backend
mvn spring-boot:run
```

Or start the full stack:

```bash
./scripts/run-full-stack.sh
```

## Docker Is Not Running

The default backend mode does not require Docker. Use:

```bash
cd backend
mvn spring-boot:run
```

The full stack requires Docker for PostgreSQL/pgvector, RabbitMQ, Redis, the AI sidecar, Elasticsearch, and MinIO.

## Ollama Is Not Installed

Ollama is optional. If Ollama is missing, the AI service and backend fall back to deterministic rule-based analysis with `aiGenerated=false`.

To enable local model output:

```bash
ollama serve
ollama pull qwen2.5:7b
```

## Port Conflicts

Default ports:

| Service | Port |
| --- | --- |
| Backend dashboard/API | `8080` |
| FastAPI AI sidecar | `8001` |
| PostgreSQL | `5432` |
| Redis | `6379` |
| RabbitMQ | `5672`, `15672` |

Stop the conflicting process or edit `docker-compose.yml` / Spring configuration.

## Testcontainers Smoke Test Is Skipped

`mvn test` includes a Testcontainers smoke test for PostgreSQL/pgvector and RabbitMQ. If Docker is not available, that smoke test is skipped while unit tests still run.

Expected local output without Docker:

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

## Redis Is Unavailable

In local mode, the workflow lease service falls back to process-local single-flight locking. In the production-like profile, Redis enables cross-instance Lua leases and report caching.
