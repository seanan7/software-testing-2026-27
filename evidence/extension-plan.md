# Extension plan, fixed before execution

This extension starts from code tag `testing-r002` and document cleanup commit `aad8292`.
It strengthens the testing study; it does not redefine the CW2 storage contract or modify
the separately delivered CW2 archive. Existing targets, snapshots and negative results remain.

## Questions and declared comparisons

1. Does an exception before a storage change have the same externally visible result as
   an exception after that change? Execute registration/deletion fault cuts with an
   independent two-bit archive/index transition model. Check every model prediction and
   record unsafe states, without calling a successful characterization a safety pass.
2. Which observations distinguish these outcomes? Compare return-only, API-read and
   storage-aware observations on the same executions. Enumerate every candidate subset
   to find a minimum separating probe basis for the distinct observed persistence states.
   This project-specific synthesis, calibration and measured observation ablation are the
   extension's original contribution; fault injection and set cover themselves are established.
3. Does one-instance mutual exclusion imply unique manifests across instances or stale
   indexes? Enumerate all six interleavings of two clients' check/put steps against two real
   service instances sharing an instrumented in-memory store. Record invocation/response
   order and check a sequential uniqueness model. Compare with serialized controls and
   bounded stale-index simulations. These are storage-model experiments, not AWS tests.
4. Can the checker itself be trusted? Calibrate the fault-state oracle against all four
   persistence states and the history checker against an independently specified exhaustive
   two-operation truth table, including real-time constraints and rejection controls.

## Fixed bounds and targets

- Fault locations: lookup, archive put, index put, cleanup remove, archive delete, index delete.
- Include normal registration/deletion controls, pre/post-change failures and cleanup pairs.
- Payload facets: 1, 2 and 5 parcels; ASCII/UTF-8 raw evidence; compact/padded bytes.
  Fault combinations and facets are crossed exhaustively; no probability of real failures is inferred.
- Require predicted/observed state agreement for every execution; each declared fault must fire.
- Require exact-byte preservation wherever evidence remains and no hidden or unreachable cut.
- Require a separating observation subset and exhaustively prove minimality within the
  enumerated candidate probes; report missing states if the executed matrix omits any.
- Explore all six check/put schedules, two serial controls and index lags of 0, 1 and 2
  lookup opportunities for two and three sequential registrations. No random seeds are needed
  for this finite enumeration; earlier generated/timing work keeps seed 20261126.
- All checker calibration and contract regression tests must pass. Known counterexamples
  must be explicitly reported as findings. Never turn their occurrence into a claim of safety.
- Preserve the original 109-test/80-mutant results. Run the full functional/coverage/PIT
  entrypoint after implementation; exclude the new diagnostic characterization experiments
  from PIT test selection so changing a known unsafe signature cannot count as an assertion kill.
- Integrate raw extension reports and independent analysis checks into the hosted workflow.
  Demonstrate that deliberately corrupted/missing evidence is rejected and preserve artifacts.

## Sources, attribution and limits

Sequential specification and real-time history checking follow Wing and Gong,
[Testing and Verifying Concurrent Objects (1993)](https://www.cs.cmu.edu/~wing/publications/WingGong93.pdf).
Failure-location exploration is established; compare Alvaro et al.,
[Lineage-driven Fault Injection (2015)](https://people.ucsc.edu/~palvaro/molly.pdf) and
Wu et al., [Legolas (NSDI 2024)](https://www.usenix.org/conference/nsdi24/presentation/wu-haoze).
This implementation is not MOLLY, Legolas, or a new general linearizability algorithm.
[AWS's GSI documentation](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/GSI.html)
motivates the delayed-index model, which does not reproduce or measure AWS propagation.
[PIT's operator list](https://pitest.org/quickstart/mutators/) defines the separate bytecode scope.

The new contribution is narrowly the design and validation of a minimum observation basis
for this service's paired failure cuts, connected to actual service executions and concurrency
counterexamples. No worldwide novelty, exhaustive distributed verification, deployment SLO,
independent peer review or course-quiz result is claimed. Any exceptional self-evaluation must
point to the implemented derivation, ablation, calibration and concrete gain, not just tools.
