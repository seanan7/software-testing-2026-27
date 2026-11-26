#!/usr/bin/env python3
"""Independently replay finite experiment evidence and synthesize a separating probe basis."""
import argparse
import collections
import itertools
import json
from pathlib import Path

PROBES = ('return', 'summary', 'manifest', 'archive-inventory', 'index-inventory')
STATES = {
    'register-control': (1, 1, 'CREATED'), 'lookup-before': (0, 0, 'ERROR'),
    'lookup-after': (0, 0, 'ERROR'), 'archive-before': (0, 0, 'ERROR'),
    'archive-after': (1, 0, 'ERROR'), 'index-before': (0, 0, 'ERROR'),
    'index-after': (0, 1, 'ERROR'), 'index-before-cleanup-before': (1, 0, 'ERROR'),
    'index-after-cleanup-before': (1, 1, 'ERROR'), 'index-before-cleanup-after': (0, 0, 'ERROR'),
    'index-after-cleanup-after': (0, 1, 'ERROR'), 'delete-control': (0, 0, 'DELETED'),
    'delete-archive-before': (1, 1, 'ERROR'), 'delete-archive-after': (0, 1, 'ERROR'),
    'delete-index-before': (0, 1, 'ERROR'), 'delete-index-after': (0, 0, 'ERROR'),
}

def require(condition, message):
    if not condition:
        raise ValueError(message)

def load(directory, name):
    return json.loads((directory / name).read_text(encoding='utf-8'))

def signature(case, probes):
    return tuple(case['observations'][probe] for probe in probes)

def separates(cases, probes):
    meanings = collections.defaultdict(set)
    for case in cases:
        meanings[signature(case, probes)].add((case['archive'], case['index']))
    return all(len(states) == 1 for states in meanings.values())

def identified_torn(cases, probes):
    meanings = collections.defaultdict(set)
    for case in cases:
        meanings[signature(case, probes)].add(case['coherent'])
    return sum(not case['coherent'] and meanings[signature(case, probes)] == {False} for case in cases)

def brute_witness(history):
    """Permutation enumeration, separate from the Java checker's backtracking implementation."""
    for order in itertools.permutations(range(len(history))):
        positions = {index: position for position, index in enumerate(order)}
        if any(first['response'] < second['invocation'] and positions[a] > positions[b]
               for a, first in enumerate(history) for b, second in enumerate(history)):
            continue
        seen = set()
        valid = True
        for index in order:
            operation = history[index]
            expected = 'DUPLICATE' if operation['manifest'] in seen else 'CREATED'
            if expected != operation['outcome']:
                valid = False
                break
            seen.add(operation['manifest'])
        if valid:
            return list(order)
    return None

def analyse(directory):
    cuts = load(directory, 'failure-cuts.json')
    histories = load(directory, 'concurrent-histories.json')
    cases = cuts['cases']
    require(cuts['planned_scenarios'] == 16 and cuts['planned_facets'] == 12, 'Changed declared bounds')
    require(len(cases) == 192, 'Incomplete failure-cut cross product')
    declared = set(itertools.product(STATES, (1, 2, 5), (False, True), (False, True)))
    observed = set()
    for case in cases:
        facet = case['facet']
        key = (case['scenario'], facet['parcels'], facet['unicode'], facet['padding'])
        require(key not in observed and key in declared, 'Missing, duplicate or undeclared case')
        observed.add(key)
        archive, index, outcome = STATES[case['scenario']]
        require((case['archive'], case['index'], case['outcome']) == (bool(archive), bool(index), outcome),
                'Persistence/outcome prediction mismatch: ' + case['scenario'])
        require(case['expected_state'] == [bool(archive), bool(index)], 'Changed model state')
        require(case['coherent'] == (archive == index), 'Incorrect safety classification')
        require(case['raw_bytes_checked'] is True, 'Missing raw-byte evidence')
        require(set(case['fired']) == set(case['declared_faults']), 'Unreached fault cut')
        expected_probes = {'return': outcome, 'summary': 'FOUND' if index else 'ABSENT',
                           'manifest': 'EXACT_BYTES' if archive and index else 'ABSENT',
                           'archive-inventory': 'PRESENT' if archive else 'ABSENT',
                           'index-inventory': 'PRESENT' if index else 'ABSENT'}
        require(case['observations'] == expected_probes, 'Observation/state mismatch')
    require(observed == declared, 'Incomplete declared matrix')

    training = [case for case in cases if case['facet'] == {'parcels': 1, 'unicode': False, 'padding': False}
                and case['outcome'] == 'ERROR']
    error_cases = [case for case in cases if case['outcome'] == 'ERROR']
    require(len({(case['archive'], case['index']) for case in training}) == 4, 'Missing persistence state')
    candidates = []
    for size in range(len(PROBES) + 1):
        candidates.extend([list(probes) for probes in itertools.combinations(PROBES, size)
                           if separates(training, probes)])
        if candidates:
            break
    require(candidates and len(candidates[0]) == 2, 'Unexpected separating basis size')
    for candidate in candidates:
        require(separates(error_cases, candidate), 'Basis fails on held-out payload facets')
    basis = candidates[0]
    state_representatives = {}
    for case in training:
        state_representatives[(case['archive'], case['index'])] = case
    pairs = list(itertools.combinations(state_representatives.values(), 2))
    ablations = []
    for name, probes in [('return-only', ['return']), ('API-only', ['return', 'summary', 'manifest']),
                         ('synthesized-basis', basis), ('all-probes', list(PROBES))]:
        ablations.append({'name': name, 'probes': probes,
                          'separated_state_pairs': sum(signature(a, probes) != signature(b, probes) for a, b in pairs),
                          'total_state_pairs': 6, 'identified_torn_executions': identified_torn(cases, probes),
                          'total_torn_executions': sum(not case['coherent'] for case in cases)})
    minimum_examples = []
    for scenario in STATES:
        unsafe = [case for case in cases if case['scenario'] == scenario and not case['coherent']]
        if unsafe:
            case = min(unsafe, key=lambda row: (row['facet']['parcels'], row['facet']['unicode'], row['facet']['padding']))
            minimum_examples.append({'scenario': scenario, 'facet': case['facet'], 'finding': case['finding'],
                                     'trace': case['trace'], 'state': [case['archive'], case['index']]})

    calibration = histories['calibration']
    require(len(calibration) == 24, 'Incomplete checker calibration')
    calibration_keys = set()
    for case in calibration:
        pattern, first, second = case['interval'], case['first'], case['second']
        key = (case['same_key'], pattern, first, second)
        require(key not in calibration_keys, 'Repeated calibration row')
        calibration_keys.add(key)
        expected = (first != second and (pattern == 'OVERLAP'
                    or pattern == 'FIRST_BEFORE_SECOND' and first == 'CREATED'
                    or pattern == 'SECOND_BEFORE_FIRST' and second == 'CREATED')) if case['same_key'] else (
                        first == second == 'CREATED')
        require(case['observed'] == expected and case['expected'] == expected, 'Checker truth-table disagreement')
    require(calibration_keys == set(itertools.product((False, True),
                ('FIRST_BEFORE_SECOND', 'SECOND_BEFORE_FIRST', 'OVERLAP'),
                ('CREATED', 'DUPLICATE'), ('CREATED', 'DUPLICATE'))), 'Missing calibration partition')
    records = histories['schedules']
    require(len(records) == 14 and len({row['name'] for row in records}) == 14, 'Missing/duplicate history')
    expected_names = {'two-instances/' + '-'.join(order) for order in itertools.permutations(('C0', 'P0', 'C1', 'P1'))
                      if order.index('C0') < order.index('P0') and order.index('C1') < order.index('P1')}
    expected_names |= {'one-instance-serial-' + str(first) for first in (0, 1)}
    expected_names |= {'one-instance-lag-' + str(lag) + '-calls-' + str(calls) for lag in (0, 1, 2) for calls in (2, 3)}
    require({row['name'] for row in records} == expected_names, 'Missing planned schedule or lag')
    counterexamples = []
    for record in records:
        history = record['operations']
        require(2 <= len(history) <= 3, 'Unexpected actual history bound')
        for operation in history:
            require(operation['invocation'] < operation['response'], 'Malformed invocation/response interval')
            require(operation['outcome'] in ('CREATED', 'DUPLICATE'), 'Unsupported operation outcome')
        witness = brute_witness(history)
        require(record['linearizable'] == (witness is not None), 'Independent history checker disagrees')
        require(record['persisted_shipments'] == sum(row['outcome'] == 'CREATED' for row in history),
                'Persistence/outcome count mismatch')
        if witness is not None:
            require(record['witness'] == witness, 'Invalid or inconsistent witness')
        else:
            require(record['witness'] == [], 'Invalid witness for rejected history')
            for length in range(1, len(history) + 1):
                minimal = [list(subset) for subset in itertools.combinations(history, length)
                           if brute_witness(subset) is None]
                if minimal:
                    counterexamples.append({'name': record['name'], 'minimum_operations': length,
                                            'operations': minimal[0]})
                    break
    require(len(counterexamples) == 8, 'Unexpected concurrency/lag result; investigate the changed behavior')
    result = {
        'scope': 'finite in-memory experiments; not deployment or probability estimates',
        'fault_executions': len(cases), 'scenario_count': 16, 'payload_facets': 12,
        'torn_executions': sum(not case['coherent'] for case in cases),
        'torn_scenarios': len(minimum_examples), 'state_counts': dict(collections.Counter(
            str(int(case['archive'])) + str(int(case['index'])) for case in cases)),
        'basis_training': 'one smallest payload facet; all 16 fault scenarios',
        'basis_validation': 'same fault scenarios crossed with 11 additional payload facets',
        'minimum_probe_count': len(basis), 'all_minimum_bases': candidates,
        'all_smaller_subsets_rejected': True, 'ablation': ablations,
        'minimum_payload_witnesses': minimum_examples,
        'checker_calibration_cases': len(calibration),
        'checker_calibration_accepts': sum(case['observed'] for case in calibration),
        'actual_histories': len(records), 'nonlinearizable_histories': len(counterexamples),
        'two_instance_counterexamples': sum(row['name'].startswith('two-instances') for row in counterexamples),
        'stale_index_counterexamples': sum('lag-' in row['name'] for row in counterexamples),
        'minimum_history_witnesses': counterexamples,
        'all_predictions_and_independent_checks': 'PASS',
        'safety_conclusion': 'The stronger cross-store and cross-instance safety objectives have counterexamples.'
    }
    return result

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input-dir', type=Path, default=Path('target/evidence'))
    parser.add_argument('--output', type=Path)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    try:
        result = analyse(args.input_dir)
    except (OSError, ValueError, KeyError, TypeError) as error:
        print('ASSURANCE FAILURE:', error)
        return 1
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(json.dumps({key: value for key, value in result.items() if key not in
                      ('minimum_payload_witnesses', 'minimum_history_witnesses')}, indent=2))
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
