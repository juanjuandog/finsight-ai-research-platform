#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
docker compose up --build backend redis elasticsearch minio
