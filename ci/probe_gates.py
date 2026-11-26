#!/usr/bin/env python3
"""Deliberately perturb recorded metrics to verify that the CI gate rejects regressions."""
import copy
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
baseline = json.loads((ROOT / 'target/evidence/summary.json').read_text())
work = ROOT / 'work/gate-fixtures'
work.mkdir(parents=True, exist_ok=True)
cases = [('passing-recorded-results', baseline, 0)]
for name, section, field, value in [
    ('test-failure', 'tests', 'failures', 1), ('skipped-test', 'tests', 'skipped', 1),
    ('line-below-target', 'coverage.LINE', 'ratio', .89),
    ('branch-below-target', 'coverage.BRANCH', 'ratio', .84),
    ('mutation-below-target', 'mutation', 'assertion_kill_ratio', .84),
    ('mutation-execution-error', 'mutation.statuses', 'RUN_ERROR', 1),
]:
    fixture = copy.deepcopy(baseline)
    target = fixture
    for key in section.split('.'):
        target = target[key]
    target[field] = value
    fixture['fixture_kind'] = 'Deliberately perturbed metric; not an observed application result'
    cases.append((name, fixture, 1))
records = []
for name, fixture, expected in cases:
    path = work / (name + '.json')
    path.write_text(json.dumps(fixture), encoding='utf-8')
    completed = subprocess.run([sys.executable, str(ROOT / 'ci/quality.py'), '--input', str(path), '--check', '--mutation'],
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    records.append({'case': name, 'expected_exit': expected, 'observed_exit': completed.returncode,
                    'classification': 'recorded actual pass' if expected == 0 else 'deliberate gate fault injection'})
    if completed.returncode != expected:
        raise AssertionError((name, completed.returncode, completed.stdout))
output = ROOT / 'evidence/results/ci-gates.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps({'scope': 'local gate component; not a hosted workflow run', 'cases': records}, indent=2) + '\n', encoding='utf-8')
print(f'PASS: {len(records)} local positive/negative gate probes')
