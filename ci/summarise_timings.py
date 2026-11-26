#!/usr/bin/env python3
"""Reproduce the descriptive bootstrap and explicitly conditional zero-failure bound."""
import json
import math
import random
from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / 'evidence/results/final/performance.json'
data = json.loads(path.read_text(encoding='utf-8'))
samples = data['raw_ms']
if data['failures'] != 0 or len(samples) != data['samples']:
    raise ValueError('The zero-failure calculation is valid only for the recorded complete zero-failure sample')
replicates, seed = 2000, 20261126
random_source = random.Random(seed)
rank = math.ceil(.95 * len(samples)) - 1
bootstrap = sorted(sorted(random_source.choices(samples, k=len(samples)))[rank] for _ in range(replicates))
report = {
    'bootstrap_replicates': replicates,
    'seed': seed,
    'p95_ms': data['p95_ms'],
    'p95_percentile_bootstrap_95_interval_ms': [bootstrap[49], bootstrap[1949]],
    'iid_conditional_one_sided_failure_probability_upper_95': 1 - math.pow(.05, 1 / len(samples)),
    'caution': 'Resampling assumes exchangeable timings; zero-failure bound assumes IID Bernoulli trials, neither establishes production reliability.',
}
path.with_name('statistics.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report, indent=2))
