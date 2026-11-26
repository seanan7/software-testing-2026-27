# Structured inspection and regression decisions

Scope: `ShipmentRegistrationService.register`, `ShipmentQueryService.delete`,
`ManifestReader.read`, the tariff mapper/client and `DynamoShipmentRepository.listByCarrier`.
Criteria: requirement traceability, boundary predicates, state transitions, side-effect order,
termination, failure recovery, concurrent access, configuration fidelity and observability.
This is a single-reviewer inspection; no peer or independent external review is claimed.

## D1: concurrent duplicate check/write race — reproduced and addressed within one JVM

The service checked `manifestExists`, then archived/priced/wrote without synchronisation.
Two requests can both observe absence. `ShipmentConcurrencyTest` captures a pre-write snapshot
in each of two threads and controls the schedule with latches. The first real run returned
`CREATED, CREATED`, failing the expected `CREATED, DUPLICATE` assertion. See
`results/red-concurrency/test.xml` and `run.log`; the failing-test commit precedes the fix.

The portfolio copy serialises registration on the service instance. The new test checks one
success, one duplicate and one persisted shipment. The change is deliberately limited:
it does not make an eventually consistent DynamoDB GSI a uniqueness constraint, nor protect
multiple processes. Serialising unrelated manifests can reduce concurrent throughput; the
sequential timing study does not quantify that trade-off. A production solution would need
an atomic idempotency/uniqueness design, recovery policy and revised storage contract.

## D2: positive-only tariff mapping tests missed valid zero boundaries — improved

Baseline PIT survivors changed `<0` to `<=0` for each numeric tariff field. Tests that used only
positive values and incomplete objects did not detect rejection of valid zero fees/rates.
Individual zero-boundary cases and independent negative/null partitions now test that distinction.

## D3: catalog upper endpoint omitted — improved

The simulated catalog tests covered TRF-1, TRF-4, TRF-0 and TRF-501 but not valid TRF-500.
A boundary mutant incorrectly rejecting 500 survived. The endpoint test adds that missing
partition, plus null and oversized numeric codes for the client's documented no-tariff contract.

## D4: diagnostic field names unasserted — improved

Replacing `ManifestReader.describe` with an empty string survived. The API suite asserted the
error code but not the diagnostic content. `ManifestReaderTest` now checks both the field path
and the violated rule. Its standalone mapper explicitly matches Spring's unknown-field setting;
the first draft of this test used different defaults and was corrected as a test-setup issue,
not counted as an application defect.

## D5: pagination mutation caused an unbounded mock loop — instrumentation improved

PIT's headline baseline counted a timed-out pagination mutant as detected. Raw XML separates
74 assertion kills, one timeout and five survivors. Query stubs now throw on a third request,
or on a second request after an empty terminal page. Incorrect continuation is therefore
observable as an assertion failure without relying on a timeout. This is stronger test
instrumentation, not a production pagination change.

## Remaining review limitations

The extension now turns several of the following risks into executed counterexamples;
it does not implement a new storage contract or claim that those risks have been repaired.
`extension-study.md` links the instrumented calls, raw outcomes and minimum witnesses.

### D6: ambiguous persistence and observation aliasing — characterized

`FailureCutExperimentTest` crosses 16 before/after-change fault scenarios with 12 payload
facets. In `archive-after`, the archive remains after an error and is invisible through an
index-based API read. In `index-after`, the index is committed but error compensation removes
the archive. Partial deletion produces the same dangling-index shape. Six scenarios produce
72 torn executions; these are mechanism-linked findings, not 72 new independent bugs.
The synthesized `{summary, archive-inventory}` basis resolves all four persistence states
within this model; API-only observations leave the orphan/empty distinction unresolved.
Raw evidence: `results/extension-final/failure-cuts.json` and `assurance-summary.json`.

### D7: one-instance lock does not transfer to shared instances or stale indexes — characterized

All six two-client check/put orders are executed against two real service instances sharing
an instrumented repository. Four create duplicate shipments. Four further delayed-index
histories fail even with sequential calls on one instance. Eight histories have two-operation
uniqueness counterexamples; six serial/zero-lag histories satisfy the sequential model.
Calibration and separate Python replay agree. This is not AWS or multiprocess execution.
Raw evidence: `results/extension-final/concurrent-histories.json`.

### Unresolved product risks

- Deletion removes evidence before removing the index. If the second operation fails, a
  dangling index can remain. The API returns 500; all-or-nothing deletion is not established.
- An ambiguous storage timeout may have committed remotely. Local mocks cannot prove that
  every transport failure leaves no data. Recovery/idempotency needs a wider fault model.
- Cleanup failure is logged and preserved as a suppressed cause, but cannot guarantee S3
  removal while S3 itself is unavailable. No distributed transaction is claimed.
- Base-image/dependency vulnerabilities, IAM policies and sustained cloud load are outside
  this finite study. Their omission limits production-readiness conclusions, not the reported
  passing unit/integration cases.
