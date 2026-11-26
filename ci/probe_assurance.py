#!/usr/bin/env python3
"""Exercise the evidence gate with labelled corruptions; none is an application test result."""
import copy
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = ROOT / 'target/evidence'
work = ROOT / 'work/assurance-gate-fixtures'
work.mkdir(parents=True, exist_ok=True)
cuts = json.loads((source / 'failure-cuts.json').read_text(encoding='utf-8'))
histories = json.loads((source / 'concurrent-histories.json').read_text(encoding='utf-8'))
cases = [('recorded-actual-pass', None, 0), ('missing-cut-report', None, 1), ('missing-history-report', None, 1),
         ('missing-fault-case', lambda c, h: c['cases'].pop(), 1),
         ('wrong-persistence-bit', lambda c, h: c['cases'][0].update(archive=False), 1),
         ('unreached-fault', lambda c, h: c['cases'][1].update(fired=[]), 1),
         ('incorrect-checker-calibration', lambda c, h: h['calibration'][0].update(observed=not h['calibration'][0]['observed']), 1),
         ('incorrect-history-classification', lambda c, h: h['schedules'][0].update(linearizable=not h['schedules'][0]['linearizable']), 1),
         ('wrong-probe-reading', lambda c, h: c['cases'][0]['observations'].update(summary='ABSENT'), 1),
         ('unchecked-raw-bytes', lambda c, h: c['cases'][0].update(raw_bytes_checked=False), 1)]
records = []
for name, mutation, expected in cases:
    folder = work / name
    folder.mkdir(exist_ok=True)
    current_cuts, current_histories = copy.deepcopy(cuts), copy.deepcopy(histories)
    if mutation:
        mutation(current_cuts, current_histories)
    for filename, value in [('failure-cuts.json', current_cuts), ('concurrent-histories.json', current_histories)]:
        path = folder / filename
        if name == 'missing-cut-report' and filename == 'failure-cuts.json' or name == 'missing-history-report' and filename == 'concurrent-histories.json':
            path.unlink(missing_ok=True)
        else:
            path.write_text(json.dumps(value), encoding='utf-8')
    result = subprocess.run([sys.executable, str(ROOT / 'ci/analyse_assurance.py'), '--input-dir', str(folder), '--check'],
                            stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                            **({'creationflags': subprocess.CREATE_NO_WINDOW} if sys.platform == 'win32' else {}))
    records.append({'case': name, 'expected_exit': expected, 'observed_exit': result.returncode,
                    'classification': 'recorded actual evidence' if expected == 0 else 'deliberate evidence-gate corruption'})
    if result.returncode != expected:
        raise AssertionError((name, result.returncode, result.stdout))
(source / 'assurance-gates.json').write_text(json.dumps({'scope': 'gate component probes, not product faults',
    'cases': records}, indent=2) + '\n', encoding='utf-8')
print('PASS:', len(records), 'evidence-gate probes')
