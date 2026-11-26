#!/usr/bin/env python3
"""Optional standalone evidence figure; requires Matplotlib, not needed by the CI gate."""
import argparse
import json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

parser = argparse.ArgumentParser()
parser.add_argument('--input', type=Path, default=Path('evidence/results/extension-final/assurance-summary.json'))
parser.add_argument('--output', type=Path, default=Path('evidence/extension-figure'))
args = parser.parse_args()
data = json.loads(args.input.read_text(encoding='utf-8'))
plt.rcParams.update({'font.family': 'DejaVu Sans', 'font.size': 9, 'axes.spines.top': False,
                     'axes.spines.right': False, 'svg.fonttype': 'none'})
figure, axes = plt.subplots(1, 3, figsize=(11, 3.8), constrained_layout=True)
states = ['00', '10', '01', '11']
counts = [data['state_counts'][state] for state in states]
bars = axes[0].bar(states, counts, color=['#4776a8', '#bd4b45', '#bd4b45', '#4776a8'])
axes[0].bar_label(bars, padding=3)
axes[0].set(title='(a) Persistence outcomes', xlabel='Archive/index bits', ylabel='Executions', ylim=(0, 100))
axes[0].text(.5, -.3, '192 executions; 72 torn outcomes\nCounts describe the finite matrix, not failure risk.',
             ha='center', transform=axes[0].transAxes, fontsize=8)
ablation = data['ablation'][:3]
values = [row['identified_torn_executions'] for row in ablation]
bars = axes[1].bar(['Return\nonly', 'API\nreads', 'Minimum\nbasis'], values,
                   color=['#9c9c9c', '#76a0c6', '#315f8b'])
axes[1].bar_label(bars, padding=3)
axes[1].set(title='(b) Observation ablation', ylabel='Identifiable torn executions /72', ylim=(0, 85))
axes[1].text(.5, -.3, 'Minimum basis: summary + archive inventory\nIdentifiability is relative to the declared catalogue.',
             ha='center', transform=axes[1].transAxes, fontsize=8)
names = ['Two\ninstances', 'Serial\ncontrols', 'Lag 0', 'Lag 1/2']
accepted, rejected = [2, 2, 2, 0], [4, 0, 0, 4]
axes[2].bar(names, accepted, label='Model accepts', color='#4776a8')
axes[2].bar(names, rejected, bottom=accepted, label='Counterexample', color='#bd4b45')
for index, (good, bad) in enumerate(zip(accepted, rejected)):
    axes[2].text(index, good + bad + .12, str(good + bad), ha='center')
axes[2].set(title='(c) Actual service histories', ylabel='Histories', ylim=(0, 7))
axes[2].legend(frameon=False, fontsize=8, loc='upper right')
axes[2].text(.5, -.3, '14 histories; eight counterexamples\nIn-memory storage/lag models, not AWS runs.',
             ha='center', transform=axes[2].transAxes, fontsize=8)
args.output.parent.mkdir(parents=True, exist_ok=True)
figure.savefig(args.output.with_suffix('.png'), dpi=180)
figure.savefig(args.output.with_suffix('.pdf'), metadata={'Title': 'Failure-cut and history evidence', 'Author': ''})
figure.savefig(args.output.with_suffix('.svg'), metadata={'Title': 'Failure-cut and history evidence', 'Creator': ''})
svg = args.output.with_suffix('.svg')
svg.write_text('\n'.join(line.rstrip() for line in svg.read_text(encoding='utf-8').splitlines()) + '\n',
               encoding='utf-8')
plt.close(figure)
print(args.output)
