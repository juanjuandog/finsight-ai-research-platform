#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../backend"
mvn spring-boot:run -Dspring-boot.run.profiles=postgres,prod

