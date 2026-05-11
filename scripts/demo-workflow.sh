#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

curl -s -X POST "$BASE_URL/api/ingestion/demo/async"
echo
sleep 1
curl -s "$BASE_URL/api/workflows"
echo
curl -s "$BASE_URL/api/metrics/600519"
echo

