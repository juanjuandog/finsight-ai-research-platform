#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
OUTPUT_DIR="${OUTPUT_DIR:-}"

write_output() {
  local name="$1"
  local payload="$2"
  if [[ -n "$OUTPUT_DIR" ]]; then
    mkdir -p "$OUTPUT_DIR"
    printf '%s\n' "$payload" > "$OUTPUT_DIR/$name.json"
  fi
}

echo "Checking FinSight backend at $BASE_URL ..."
curl -fsS "$BASE_URL/actuator/health" >/dev/null

echo "Seeding demo data ..."
payload="$(curl -fsS -X POST "$BASE_URL/api/ingestion/demo")"
write_output "ingestion" "$payload"
printf '%s\n' "$payload"
echo

echo "Running metric calculation ..."
payload="$(curl -fsS -X POST "$BASE_URL/api/metrics/recalculate/600519")"
write_output "metrics" "$payload"
printf '%s\n' "$payload"
echo

echo "Building document index ..."
payload="$(curl -fsS -X POST "$BASE_URL/api/document-index/600519/rebuild")"
write_output "document-index" "$payload"
printf '%s\n' "$payload"
echo

echo "Building company intelligence graph ..."
payload="$(curl -fsS -X POST "$BASE_URL/api/intelligence/600519/rebuild")"
write_output "intelligence" "$payload"
printf '%s\n' "$payload"
echo

echo "Asking an evidence-grounded research question ..."
payload="$(curl -fsS -X POST "$BASE_URL/api/analysis/ask" \
  -H "Content-Type: application/json" \
  -d '{"companySymbol":"600519","timeRange":"2022-2024","question":"贵州茅台最近的现金流质量和风险信号怎么样？"}')"
write_output "answer" "$payload"
printf '%s\n' "$payload"
echo

echo "Running RAG evaluation ..."
payload="$(curl -fsS -X POST "$BASE_URL/api/evaluations/rag/run")"
write_output "evaluation" "$payload"
printf '%s\n' "$payload"
echo

echo "Quick demo complete. Open $BASE_URL to view the dashboard."
