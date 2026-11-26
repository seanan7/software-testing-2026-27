#!/usr/bin/env python3
"""Collect auditable test results and enforce the planned quality thresholds."""
import argparse
import collections
import json
import os
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def clean(text):
    for path in (str(ROOT), str(ROOT).replace('/', '\\'), str(Path.home())):
        text = text.replace(path, '<project>' if 'testing-project' in path else '<home>')
    text = re.sub(r'(?i)[A-Z]:[\\/]OneDrive[^\r\n<]*?testing-project', '<project>', text)
    text = re.sub(r'started by [^\s]+', 'started locally', text)
    return text

def collect():
    suites = [ET.parse(path).getroot() for path in sorted((ROOT / 'target/surefire-reports').glob('TEST-*.xml'))]
    if not suites:
        raise ValueError('No Surefire test evidence')
    counts = {key: sum(int(suite.get(key, '0')) for suite in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
    report = ET.parse(ROOT / 'target/site/jacoco/jacoco.xml').getroot()
    coverage = {counter.get('type'): {key: int(counter.get(key)) for key in ('missed', 'covered')}
                for counter in report.findall('counter')}
    for count in coverage.values():
        count['total'] = count['covered'] + count['missed']
        count['ratio'] = count['covered'] / count['total'] if count['total'] else 1.0
    result = {'tests': counts, 'coverage': coverage, 'test_classes': len(suites)}
    mutation_file = ROOT / 'target/pit-reports/mutations.xml'
    if mutation_file.exists():
        mutations = ET.parse(mutation_file).getroot().findall('mutation')
        statuses = dict(collections.Counter(mutation.get('status') for mutation in mutations))
        result['mutation'] = {'total': len(mutations), 'statuses': statuses,
                              'assertion_kill_ratio': statuses.get('KILLED', 0) / len(mutations) if mutations else 0}
    return result, suites, report

def export(destination, result, suites, coverage):
    destination.mkdir(parents=True, exist_ok=True)
    (destination / 'summary.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    for suite in suites:
        for properties in suite.findall('properties'):
            suite.remove(properties)
        text = clean(ET.tostring(suite, encoding='unicode'))
        (destination / (suite.get('name') + '.xml')).write_text(text, encoding='utf-8')
    for session in coverage.findall('sessioninfo'):
        coverage.remove(session)
    (destination / 'jacoco.xml').write_text(ET.tostring(coverage, encoding='unicode'), encoding='utf-8')
    mutations = ROOT / 'target/pit-reports/mutations.xml'
    if mutations.exists():
        (destination / 'mutations.xml').write_text(clean(mutations.read_text(encoding='utf-8')), encoding='utf-8')
    performance = ROOT / 'target/evidence/performance.json'
    if performance.exists():
        (destination / 'performance.json').write_text(performance.read_text(encoding='utf-8'), encoding='utf-8')

def check(result, mutation=False):
    errors = []
    tests = result['tests']
    if tests['tests'] == 0 or any(tests[key] for key in ('failures', 'errors', 'skipped')):
        errors.append('All tests must pass with none skipped')
    if result['coverage']['LINE']['ratio'] < .90:
        errors.append('Line coverage below 90%')
    if result['coverage']['BRANCH']['ratio'] < .85:
        errors.append('Branch coverage below 85%')
    if mutation:
        if result.get('mutation', {}).get('assertion_kill_ratio', 0) < .85:
            errors.append('Assertion-killed mutation ratio below 85%')
        if any(result.get('mutation', {}).get('statuses', {}).get(key, 0) for key in ('RUN_ERROR', 'MEMORY_ERROR')):
            errors.append('Mutation execution errors require investigation')
    return errors

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--export', type=Path)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--mutation', action='store_true')
    parser.add_argument('--input', type=Path, help='Replay a recorded or deliberately failing gate fixture')
    args = parser.parse_args()
    if args.input:
        result = json.loads(args.input.read_text(encoding='utf-8'))
    else:
        result, suites, coverage = collect()
        if args.export:
            export(args.export, result, suites, coverage)
    print(json.dumps(result, indent=2))
    errors = check(result, args.mutation) if args.check else []
    for error in errors:
        print('QUALITY FAILURE:', error)
    return 1 if errors else 0

if __name__ == '__main__':
    raise SystemExit(main())
