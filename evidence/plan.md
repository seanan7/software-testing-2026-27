# Test plan and targets

The subject is the existing CampusShop Dispatch Manifest Hub, imported with 84 passing test cases.
This portfolio begins with that baseline. Earlier tests are reused; their creation is not claimed
as test-driven development in this study. New regression work records the actual red/green order.

The original specification requires validation, exact raw evidence, decimal parcel pricing,
UNRATED failure handling, unique manifest numbers, DynamoDB queries, compensation and deletion.
The assurance targets below are additional study objectives, not invented client requirements.

## Targets fixed before baseline measurement

- All existing and new functional tests pass; no disabled/skipped cases.
- Production line coverage >=90%, branch coverage >=85%. Report the entire bundle and the
  core separately; the startup main method is not claimed as exercised by MockMvc.
- PIT: at least 85% of generated mutants detected in service/client/mapper/repository packages.
  Publish killed, surviving, uncovered, timed-out and error counts separately. A timeout is
  not silently counted as a test assertion kill. Review survivors rather than deleting them.
- Independently generated pricing checks: 1,000 fixed-seed inputs for each property.
- State-model checks: 100 sequences of 40 operations with an independent expected-state map.
- Local in-memory two-parcel registration: 50 warm-up operations and 500 measured operations;
  p95 <100 ms and no functional failures. This does not measure S3/DynamoDB network latency.
- Every requirements row maps to a test and an observable oracle; reviews cover critical
  ordering, boundaries, exception handling, query pagination and concurrency.
- CI runs unit/integration tests, coverage gates and mutation analysis, retains reports and
  stops on failure. Hosted execution is only claimed after a genuine hosted run is available.

## Layers and approach

Unit: integer-rational pricing oracle, half-cent boundaries, mapping and repository request
capture. Integration: MockMvc to in-memory repositories and local HTTP tariff stub.
System: the earlier container/LocalStack checks are supporting baseline evidence only; new
results are labelled separately. Generated/metamorphic and state-model tests extend the
example-based suite. A deterministic two-thread schedule probes the duplicate-check race.

Coverage observes execution; mutation testing checks sensitivity to injected implementation
changes. Neither proves correctness. Test spies expose side-effect order; raw byte comparison
is stronger than JSON semantic equality. The integer oracle avoids repeating BigDecimal code.

## Risks and limitations identified before execution

Mocks do not reproduce AWS consistency, IAM, outages or byte-transport limits. A single JVM
cannot establish distributed uniqueness through an eventually consistent GSI. Generated inputs
are finite and bounded; the fixed seed improves repeatability rather than establishing a real
operational distribution. Timing depends on warm-up and the local environment. Performance
sampling, source coverage and mutation score have different denominators and must not be
treated as interchangeable estimates of residual defects.
