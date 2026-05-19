# Contributing To FinSight AI

Thanks for considering a contribution. FinSight is an AI research-agent backend project, so contributions are most valuable when they improve reliability, evidence quality, evaluation, or developer experience.

## Good First Areas

- Improve the dashboard demo flow.
- Add more deterministic evaluation cases.
- Add tests for workflow recovery, report versioning, or cache behavior.
- Improve PostgreSQL/pgvector query quality.
- Add docs, diagrams, or troubleshooting notes.

## Local Development

Run the default in-memory backend:

```bash
cd backend
mvn spring-boot:run
```

Run tests:

```bash
cd backend
mvn test
```

Run the full stack:

```bash
./scripts/run-full-stack.sh
```

Seed the demo:

```bash
./scripts/quick-demo.sh
```

## Pull Request Checklist

- Keep changes focused on one concern.
- Run `mvn test` from `backend`.
- Run `bash -n scripts/*.sh` if shell scripts changed.
- Update README or docs when behavior changes.
- Avoid committing local IDE files, generated build output, `.env`, or secrets.

## Design Principles

- Prefer explicit workflow state over hidden side effects.
- Keep AI output tied to evidence and data snapshots.
- Make expensive operations idempotent and single-flight protected.
- Preserve deterministic fallbacks so demos and tests work without external AI services.
- Add abstractions only when they reduce real complexity.

## Reporting Issues

Please include:

- environment and run mode;
- exact command or API endpoint;
- expected behavior;
- actual behavior;
- logs or screenshots when useful.
