#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

curl -s -X POST "$BASE_URL/api/ingestion/demo/async"
echo
sleep 1
curl -s "$BASE_URL/api/workflows"
echo
curl -s "$BASE_URL/api/document-index/600519/count"
echo
curl -s "$BASE_URL/api/document-index/600519/search?q=%E7%8E%B0%E9%87%91%E6%B5%81%E9%A3%8E%E9%99%A9"
echo
curl -s "$BASE_URL/api/metrics/600519"
echo
curl -s "$BASE_URL/api/metrics/600519/risks"
echo
curl -s "$BASE_URL/api/metrics/600519/runs"
echo
