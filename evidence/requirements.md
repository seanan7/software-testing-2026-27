# Requirements and test rationale

Normative behaviour is derived from the Dispatch Manifest Hub CW2 specification v3, sections
2–4 and Appendices B, E and F. The additional quality objectives Q1–Q4 are study targets.
Classes below are under `src/test/java/de/campusshop/dispatch/`.

| ID | Requirement and level | Approach / evidence | Why this oracle is appropriate; remaining risk |
|---|---|---|---|
| R1 | Invalid manifest ->400; no writes (API/integration) | `controller/ShipmentApiIntegrationTest.invalidManifestsWriteNothing` | Boundary/partition tests plus never-write spies. Finite invalid-shape partitions. |
| R2 | Malformed JSON ->400; stable ProblemReport (API) | `malformedBodiesAre400AndWriteNothing`, `nullAndTrailingDocumentsAreRejected` | Raw malformed requests exercise the parser/advice together. Not exhaustive fuzzing. |
| R3 | Canonical UUIDs and ISO date (unit/API) | Invalid-ID/date partitions and `invalidPathIdentifiersAre400` | Format, absence, impossible date; valid boundary dates need separate sampling. |
| R4 | Exact S3 bytes, including unknown fields (integration/repository) | `registerReadListAndDeleteIncludingBothStorageSideEffects`, `repository/S3ManifestArchiveTest` | Byte equality exposes reconstruction loss that JSON equality misses. SDK mocks do not establish remote durability. |
| R5 | One tariff lookup per parcel (service/integration) | `service/ShipmentRegistrationServiceTest.registersArchivesAndIndexesInTheRequiredOrder`, `client/RestTariffClientTest` | In-order mocks plus real loopback HTTP verify layering and protocol. Real service availability is separate. |
| R6 | Decimal formula, final HALF_UP to 2dp (unit) | `service/ChargeCalculatorTest`, `service/ChargePropertiesTest` | Examples, half-cent boundaries, independent integer-rational oracle and monotonicity. Generated domain is bounded. |
| R7 | Unknown/error tariff ->UNRATED, kept with null fields (service/API) | Service failure tests; API null-field check; HTTP 404/5xx/timeout | Exact field and count/total assertions reject silently dropped parcels. No real network outage in JUnit. |
| R8 | Total sums rounded rated charges; count includes all (unit/model) | Example suite and `service/ShipmentStateModelTest` | Independent active-state model and generated operation sequences. Does not model every distributed interleaving. |
| R9 | Duplicate manifest ->409 before side effects (service/API/concurrency) | Existing duplicate tests; `service/ShipmentConcurrencyTest` | Sequential and controlled two-thread schedules probe check-then-write. Cross-process/GSI consistency remains a limitation. |
| R10 | Archive precedes pricing/index; failed index removes evidence (service) | Mock in-order/compensation tests, including failed cleanup | Interaction ordering plus captured-key equality. Compensation can itself fail; atomic multi-store transactions are not claimed. |
| R11 | Nine correct DynamoDB attributes; numbers stored as N (repository) | `repository/DynamoShipmentRepositoryTest` | Request capture and round-trip reconstruction; no money converted to double. Remote schema is external. |
| R12 | GetItem/Query, never Scan; ascending complete carrier query (repository/integration) | Pagination/never-scan unit tests and carrier API tests | Continuation-token inspection tests more than a single-page example. Eventual GSI freshness is not covered by mocks. |
| R13 | Unknown/deleted references ->404; carrier isolation (API/model) | API lifecycle, unknown references and state model | Observable outcomes and an independent index expectation. Access control is outside this open-API specification. |
| R14 | Delete both stores ->204; failure ->500 (service/API) | `service/ShipmentQueryServiceTest`, API lifecycle | Exact calls and post-deletion absence implement the chosen policy. Partial deletion failure is explicitly reviewed. |
| R15 | Unexpected errors ->generic 500, no stack trace in response (API) | `unexpectedStorageFailureReturns500AndCompensatesTheArchive` | Fault injection across the real advice/service stack. Logging secrecy is additionally inspected. |
| R16 | Runtime configuration/profiles isolate external access (configuration) | `config/ConfigurationTest`, baseline container evidence | Bean selection and environment-supplied settings. MockMvc is not a container-readiness test. |
| Q1 | Configured tariff timeout is bounded (HTTP integration) | Local delayed HTTP stub and elapsed-time assertion | Controls latency deterministically; CI contention can affect wall-clock timing. |
| Q2 | Local registration p95 <100ms after warm-up (performance) | `service/RegistrationPerformanceTest`, raw 500-sample timings | Describes only this JVM/in-memory workload, not network or production SLOs. |
| Q3 | Sensitive/local identity data absent from published evidence (inspection) | Evidence exporter strips JVM properties and local paths | Preserves counts and test outcomes; does not make source code a security proof. |
| Q4 | Repeatable tests and regression gates (process) | `ci/test.sh`, `ci/quality.py`, CI YAML; positive/negative gate evidence | Same entrypoint locally/CI; hosted trigger/permissions must be verified separately. |

The mix is intentional: boundary tests challenge inputs, model/property tests explore many valid
states, HTTP tests observe real parsing/timeouts, request capture observes storage semantics,
and mutation analysis challenges whether assertions notice implementation changes. High
coverage alone would not justify any of these behaviours.
