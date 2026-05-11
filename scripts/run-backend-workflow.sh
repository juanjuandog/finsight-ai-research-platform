#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
docker compose up -d postgres rabbitmq

cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=postgres,rabbitmq,prod

