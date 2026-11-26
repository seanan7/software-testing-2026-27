#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -B -ntp clean verify
python3 -B ci/quality.py --check
./mvnw -B -ntp org.pitest:pitest-maven:mutationCoverage
python3 -B ci/quality.py --check --mutation --export target/evidence
python3 -B ci/summarise_timings.py --input target/evidence/performance.json
python3 -B ci/analyse_assurance.py --input-dir target/evidence --output target/evidence/assurance-summary.json --check
