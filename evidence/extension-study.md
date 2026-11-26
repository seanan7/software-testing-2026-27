# Paired failure cuts and minimum observation synthesis

## Question and contribution

The earlier suite already killed all 80 scoped PIT mutants, yet this does not answer whether
an error response leaves a coherent archive/index pair. The extension therefore asks a
different adequacy question: **can the chosen observations distinguish the hidden outcomes
of the same failure response?** The plan was committed before execution in
`extension-plan.md`. Production code is unchanged from `testing-r002`.

The project-specific contribution is an executable protocol coupling paired pre/post-change
failure cuts, a four-state persistence oracle, exhaustive probe-subset synthesis, independent
checker calibration, and measured observation ablation. It is a constructed method for this
service, not a claim to have invented fault injection, set cover, or linearizability.
The exceptional self-assessment rests on this construction and its demonstrated additional
information, rather than on renaming existing tools or increasing an already passing metric.

## Fixed experiment and calibration

`assurance/FailureCutExperimentTest.java` runs the actual registration/query services with
fault adapters at lookup, archive put, index put, archive remove and index remove. Each
injected exception is placed before or after its storage effect. Cleanup pairs are included.
Sixteen scenarios are crossed with 12 facets: 1/2/5 parcels, ASCII/UTF-8 and compact/padded
raw JSON. Every declared fault must fire, and all surviving raw evidence must retain exact bytes.

The expected states are a declarative table, independent of the service's execution. Let
`A` mean an archived object remains and `I` an index record remains. The study objective is
`A = I`: `00` is empty, `11` complete, `10` orphan evidence and `01` a dangling index. This
stronger robustness objective is additional to the specification's exception/status policy.
Known failing safety states are recorded as findings. A passing characterization test says
that the predicted state was observed, **not** that this stronger objective was met.

The four-state truth table checks acceptance of `00/11` and rejection of `01/10`. A raw-byte
negative control accepts padded and compact JSON as semantically equal but rejects their
bytes as unequal. `ConcurrentHistoryTest.java` additionally calibrates the history checker
against all 24 combinations of key equality, interval pattern and two-call outcomes. The
closed-form calibration has seven accepted and 17 rejected cases. A separate Python
permutation enumerator checks every recorded actual history against the Java backtracking
checker; malformed intervals and unsupported outcomes are rejected.

## Observation synthesis and exact minimality

Use the smallest payload facet to construct the observation map, restricted to error outcomes.
There are four distinct persistence states. Candidate features are return outcome, summary
availability, exact manifest availability, archive inventory and index inventory. Inventory
means privileged fixture inspection, not a claimed public endpoint or an AWS inventory scan.
The API-read comparator is deliberately generous: the harness supplies the attempted reference,
even though a real caller receiving an error might not know it.

For states `s,t`, a probe set `B` separates them when at least one probe has different values:

`separates(B) <=> for every s != t, exists p in B: p(s) != p(t)`.

`ci/analyse_assurance.py` enumerates subsets in increasing cardinality. Every zero/one-probe
subset fails. Exactly two minimum bases succeed: `{summary, archive-inventory}` and
`{archive-inventory, index-inventory}`. The first directly observes the index/archive bits.
This also supplies a lower-bound argument: return is constant on the error partition and
every other single feature is binary, so no one feature can distinguish four states. Two
features attain the bound. This is minimality within the declared probes and state model,
not a theorem about all possible instruments, network requests or real systems.

The selected basis is validated on the same faults crossed with the other 11 payload facets.
This is a held-out payload check, not an independent fault-family holdout. All cases are in
the same finite storage model; the result does not estimate performance on unseen failures.

## Measured results and ablation

The full run passed **319 JUnit cases**: the previous 109 plus 210 experiment/calibration
cases. It retained 322/324 covered production lines (99.38%), 62/62 branches, and 80/80
assertion-killed PIT mutants. The new diagnostic experiments are excluded from PIT selection:
changing a known unsafe signature must not be counted as killing a product fault.

| Persistence state | Executions | Interpretation |
|---|---:|---|
| `00` | 84 | No archive or index remains |
| `11` | 36 | Both remain |
| `10` | 24 | Orphan archive |
| `01` | 48 | Dangling index |

| Observations on the same executions | Error-state pairs separated /6 | Torn executions identifiable /72 |
|---|---:|---:|
| Return only | 0 | 0 |
| Return + summary + manifest | 5 | 48 |
| Synthesized two-feature basis | 6 | 72 |
| All five features | 6 | 72 |

“Identifiable” is a closed-world property: an observation signature denotes only unsafe
states in this catalogue. Mixed safe/unsafe signatures remain undetermined; they are not
guessed safe. Thus the inventory observation resolves 24 orphan cases that the API-only
comparator cannot distinguish from a clean failure. The two-feature basis retains the same
state distinction as all five features. These are information gains, not latency savings.

The 72 torn executions come from six fault scenarios, not 72 independent defects. The
smallest witnesses use one ASCII parcel without padding, retained with their cut traces.
The mechanism clusters are: archive commits before returning an error; index commits before
an error and compensation removes its evidence; and partial deletion removes evidence while
the index remains. Existing cleanup failure is distinguished from newly exercised post-commit
ambiguity. No measured execution frequency is presented as a real-world failure probability.

## Concurrent histories and transfer limits

Two actual service instances share one instrumented repository. Gates enumerate all six
orders of `check0 < put0` and `check1 < put1`. Four histories create two shipments for one
manifest and violate the sequential uniqueness model; two serialize successfully. Two
one-instance serial controls pass. Among six further one-instance sequential histories,
zero-lag controls pass while four lag-1/lag-2 histories violate uniqueness. The lag unit is
a lookup opportunity in the adapter, not milliseconds or an AWS propagation estimate.

All eight rejected histories have a two-operation counterexample. All 24 calibration cases
agree, and independent Python replay agrees on all 14 actual histories. The one-instance
lock therefore repairs its earlier schedule but does not establish cross-instance uniqueness
or freshness of a delayed index. AWS, multiple JVMs, network partitions and sustained load
remain outside execution; no proof of distributed correctness is claimed.

## CI, reproducibility and decision

`bash ci/test.sh` runs regressions, coverage, PIT, per-run descriptive timing analysis and
the independent extension evidence checker. The workflow also executes seven metric-gate
probes and ten extension-gate probes. The latter reject missing reports, missing fault cases,
wrong persistence bits, unreached cuts, wrong calibration answers, wrong history decisions,
inconsistent observations and unchecked bytes. Injected gate corruptions are labelled and
are not reported as product failures. Raw outputs and their independent summary are retained
under `results/extension-final/`; `hosted-ci.md` identifies the actual hosted execution.

The evidence gate deliberately pins this characterized production snapshot. A future repair
must update the state model and retained counterexamples through an explicit design change;
an intentionally repaired unsafe state is not automatically a product regression.

The original timing snapshot remains in `results/final/`. The extension run's 500 samples
and matching bootstrap are in `results/extension-final/`, with the same seed 20261126.
Timing assumes exchangeability; the zero-failure bound additionally assumes IID trials.
Neither bound repairs the observed structural counterexamples or estimates deployed reliability.

The practical decision is to use storage-aware observations when testing ambiguous writes
and to require an atomic uniqueness/reconciliation design before claiming distributed safety.
The coursework evaluates the testing method and its limits; it does not disguise those
outstanding product risks by presenting passing characterization cases as successful recovery.

## Attribution and originality boundary

The sequential/real-time correctness criterion is established in Wing and Gong,
[Testing and Verifying Concurrent Objects (1993)](https://www.cs.cmu.edu/~wing/publications/WingGong93.pdf).
[MOLLY](https://people.ucsc.edu/~palvaro/molly.pdf) and
[Legolas](https://www.usenix.org/conference/nsdi24/presentation/wu-haoze) demonstrate prior
systematic fault exploration; this small in-memory adapter is not an implementation of either.
[AWS's GSI documentation](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/GSI.html)
motivates examining delayed visibility, while [PIT's operator documentation](https://pitest.org/quickstart/mutators/)
defines its separate fault scope. The original contribution claimed here is the specific
paired-cut observation protocol, its minimum-basis derivation and calibrated integration
for this service. It is not a worldwide algorithmic novelty claim. Its exceptional-coursework
merit remains open to the auditor's evaluation against the material actually taught.
