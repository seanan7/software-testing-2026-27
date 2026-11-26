# Results, adequacy and limits

The evidence below comes from actual runs. Baseline and final snapshots retain sanitised
Surefire XML, JaCoCo XML and raw PIT mutation outcomes. `results/ci-positive.log` is the
local pipeline transcript; `results/ci-gates.json` contains deliberately failing gate probes.
Hosted execution is recorded separately in `hosted-ci.md`: the complete GitHub Actions
entrypoint and gate probes passed, and their report artifact was retained. No quiz pass,
independent peer review or operational reliability claim is inferred from these results.

## Original local study results and targets

The following table retains the original enhanced study (`results/final/`). The extension
adds 210 diagnostic/calibration JUnit cases: 319/319 now pass, with unchanged production
coverage and 80/80 assertion kills. `extension-study.md` separately reports 192 failure-cut
executions, 72 torn outcomes and eight counterexample histories; these are not safety passes.

| Measure | Predeclared target | Baseline | Enhanced r001 |
|---|---|---|---|
| Functional cases | All pass, none skipped | 84/84 | 109/109; 15 test classes |
| Production line coverage | >=90% | 301/324 = 92.90% | 322/324 = 99.38% |
| Production branch coverage | >=85% | 53/62 = 85.48% | 62/62 = 100% |
| Assertion-killed PIT mutants | >=85% in declared package/operator scope | 74/80 = 92.50% | 80/80 = 100% |
| Other mutation outcomes | Report separately | 5 survivors; 1 timeout | 0 survivors, uncovered, timeouts or execution errors |
| Generated pricing checks | 1,000 inputs per property | Not present | 4 properties x1,000 inputs; seed 20261126 |
| Stateful sequences | 100 x40 operations | Not present | 4,000 register/read/duplicate/delete operations |
| Local sequential registration | p95 <100ms after warm-up | Not measured | 500 measured after 50 warm-ups; p95 0.088890ms |
| Local CI gate behaviour | Correct pass/fail outcomes | Not exercised | 7/7 probes match expected exit status |

Only `DispatchApplication.main` remains outside execution (two lines); its exclusion is
visible rather than hidden by filters. JaCoCo's counters describe executed bytecode branches,
not all input combinations or exception interleavings. The mutation scope is exactly the
service/client/mapper/repository classes under the pinned PIT default operators. Defaults do
not exhaust arithmetic faults in BigDecimal method calls or configuration/environment faults.

## Yield and what changed

The useful production finding was a deterministic single-instance duplicate-registration race,
not a failing example of normal sequential use. Its red result and repaired case are retained.
Five surviving mutants exposed missing assertions/partitions: three numeric zero boundaries,
the valid TRF-500 endpoint and the invalid-field diagnostic text. Additional tests detect all
five. The previous pagination timeout now becomes a prompt assertion kill because the mocks
reject calls beyond a terminal page. This explains the change from 74 assertion kills plus one
timeout to 80 assertion kills; the five test gaps are not misreported as five real product bugs.

The enhanced suite covers the plan's functional layers and all 20 requirement/objective rows,
but scope limits remain. In particular, the new concurrency fix applies to one service instance;
real cloud safety remains unproved. The extension's two-instance and delayed-index models
now provide explicit counterexamples to extrapolating that lock to a distributed guarantee.

## Timing and conditional statistical interpretation

The numbers in this section describe the retained original timing sample. The extension
full run has a separate 500-sample record under `results/extension-final/`: p95 0.046572ms
and descriptive bootstrap interval [0.033636, 0.054819]ms. Both are sequential in-memory
workloads; the difference is not a controlled claim of performance improvement.

The local workload uses simulated tariffs, in-memory stores and an empty store before each
operation. It measures registration itself; setup, assertions and deletion are outside timing.
All 500 measured operations completed and passed their result/evidence assertions. Raw samples
are in `results/final/performance.json`: median 0.006796ms, p95 0.088890ms, maximum 0.479522ms.
These values are not cloud latency or a deployed service-level objective.

`results/final/statistics.json` records 2,000 percentile-bootstrap resamples at seed 20261126.
The descriptive 95% interval for this workload's p95 is [0.070745, 0.122631]ms. Resampling assumes
exchangeable observations; warm-up, runtime scheduling and serial correlation can invalidate
that interpretation. No independent repeated deployment or hardware comparison was performed.

For zero failures in 500 IID Bernoulli trials, the exact one-sided 95% upper probability bound is
`1 - 0.05^(1/500) = 0.00597355` (0.597%). The IID and representative-workload assumptions are
not established here. The calculation illustrates why zero observed failures cannot justify
zero failure probability, and is not presented as a production reliability estimate.

## Process gaps and improvements

- Already met: the stated coverage, mutation and local timing thresholds. Raising a passing
  threshold after observing results would not create new assurance; targets were fixed first.
- Hosted CI: genuine push runs passed the complete entrypoint and retained their artifacts.
  The extension adds ten corruption/missing-evidence gate probes to the seven metric probes.
  Whole-workflow negative push/PR scenarios, cold-cache behavior and long-term artifact
  access have not been systematically exercised; gate-component probes are labelled separately.
- Real cloud concurrency/failures: test multiple processes against an eventually consistent
  index; introduce recoverable, idempotent write semantics; inject committed-but-unacknowledged
  requests and verify reconciliation. Do not treat a JVM lock as a distributed guarantee.
- Deletion/recovery: inspect and test partial deletion, and define how missing evidence or an
  unavailable cleanup store is reconciled. Current generic 500 handling does not supply that policy.
- Performance: add increasing store sizes, sustained concurrent arrival rates, isolated repeated
  JVM runs and real network dependencies. Record environment and dependence before generalising.
- Generated tests: diversify profiles and date/JSON shapes; current fixed-seed valid-input
  properties and a compact state model provide bounded exploration, not exhaustive fuzzing.
- Review: seek a second reviewer and retain resolved comments. The current inspection is useful
  but cannot supply independent confirmation.

## Tool references

JaCoCo agent integration and counter definitions: [Maven integration](https://www.jacoco.org/jacoco/trunk/doc/maven.html)
and [coverage counters](https://www.jacoco.org/jacoco/trunk/doc/counters.html).
PIT's declared target scope, operators and machine-readable outcomes follow its
[Maven configuration](https://pitest.org/quickstart/maven/) and
[JUnit 5 integration](https://github.com/pitest/pitest-junit5-plugin).
The original GitLab artifact declaration follows [GitLab's JaCoCo integration](https://docs.gitlab.com/ci/testing/code_coverage/jacoco/).
The active workflow is `.github/workflows/tests.yml`; `hosted-ci.md` links its actual run.
