#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -B -ntp clean verify
python3 -B ci/quality.py --check
./mvnw -B -ntp org.pitest:pitest-maven:mutationCoverage
python3 -B ci/quality.py --check --mutation --export target/evidence
