#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

curl -s -X POST "$BASE_URL/api/ingestion/demo"
echo
curl -s -X POST "$BASE_URL/api/metrics/recalculate/600519"
echo
curl -s -X POST "$BASE_URL/api/analysis/ask" \
  -H "Content-Type: application/json" \
  -d '{"companySymbol":"600519","timeRange":"2022-2024","question":"贵州茅台最近的现金流质量和风险信号怎么样？"}'
echo
curl -s -X POST "$BASE_URL/api/evaluations/rag/run"
echo
