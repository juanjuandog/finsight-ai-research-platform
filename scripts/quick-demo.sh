#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "Checking FinSight backend at $BASE_URL ..."
curl -fsS "$BASE_URL/actuator/health" >/dev/null

echo "Seeding demo data ..."
curl -fsS -X POST "$BASE_URL/api/ingestion/demo"
echo

echo "Running metric calculation ..."
curl -fsS -X POST "$BASE_URL/api/metrics/recalculate/600519"
echo

echo "Building document index ..."
curl -fsS -X POST "$BASE_URL/api/document-index/600519/rebuild"
echo

echo "Building company intelligence graph ..."
curl -fsS -X POST "$BASE_URL/api/intelligence/600519/rebuild"
echo

echo "Asking an evidence-grounded research question ..."
curl -fsS -X POST "$BASE_URL/api/analysis/ask" \
  -H "Content-Type: application/json" \
  -d '{"companySymbol":"600519","timeRange":"2022-2024","question":"贵州茅台最近的现金流质量和风险信号怎么样？"}'
echo

echo "Running RAG evaluation ..."
curl -fsS -X POST "$BASE_URL/api/evaluations/rag/run"
echo

echo "Quick demo complete. Open $BASE_URL to view the dashboard."
