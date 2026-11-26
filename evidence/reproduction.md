# Reproduction and evidence navigation

Requirements/rationale: `requirements.md`; initial plan/targets: `plan.md`; inspection and
defect decisions: `review.md`; quantitative interpretation/limitations: `analysis.md`.
The result snapshots in `results/` are retained evidence, not a substitute for executable tests.

Clone the public evidence repository:

```bash
git clone https://github.com/seanan7/software-testing-2026-27.git
cd software-testing-2026-27
git checkout testing-r004
```

If using the accompanying r004 archive, unzip it and restore the Git history from its
top-level bundle (the `testing-project/` directory is also a readable snapshot):

```bash
git clone history.bundle restored-project
cd restored-project
git checkout testing-r004
```

## Run from repository root

Use Java 21, Python 3 and the supplied Maven wrapper (Maven 3.9.9):

```bash
chmod +x mvnw ci/test.sh
bash ci/test.sh
```

The JUnit suite uses in-memory stores, generated inputs and a loopback HTTP stub. It needs
neither Docker nor an external tariff/AWS account. The dependency versions and mutation scope
are fixed in `pom.xml`. JaCoCo agent options are passed through Surefire's late-bound argLine.

The entrypoint stops on any Maven or quality-gate failure. Its outputs are Surefire XML,
`target/site/jacoco/`, `target/pit-reports/` and `target/evidence/`. Re-run a failed case directly:

```bash
./mvnw -Dtest=ShipmentConcurrencyTest test
./mvnw -Dtest=ChargePropertiesTest test
./mvnw -Dtest=ShipmentStateModelTest test
python3 -B ci/summarise_timings.py
python3 -B ci/analyse_assurance.py --input-dir evidence/results/extension-final --check
python3 -B ci/probe_assurance.py
```

The active GitHub Actions workflow invokes the same entrypoint and gate probes, with
Java 21 and retained test/coverage artifacts. `hosted-ci.md` records the successful actual
push run, commit and artifact. Local and hosted results are labelled separately; the original
GitLab configuration and `testing-r001` snapshot remain historical evidence.

The full entrypoint now also produces the paired failure-cut and concurrent-history reports,
checks them independently and summarizes the current run's timings. The optional standalone
figure is reproduced with `python3 ci/plot_assurance.py` after installing Matplotlib in a
local virtual environment. It is not required by the test or evidence gate.

`extension-plan.md` fixes the experiment bounds before execution. `extension-study.md`
explains the minimum probe basis, ablation and counterexamples. The 210 new diagnostic and
calibration cases are distinct from the earlier 109 functional cases. Passing diagnostic
cases do not establish that the stronger cross-store or cross-instance safety objectives hold.

## Audit the red/green history

The baseline import, mutation findings, failing concurrency test and fix are separate real
commits. `git log --oneline` identifies them. The red result is
`results/red-concurrency/test.xml`; the repaired test is in the final result snapshot.
No earlier calendar history, quiz result or peer review is claimed.

## Interpretation and privacy

Coverage reports include all production classes; no missed business methods are hidden.
The application's startup main remains outside the in-process test execution. Mutation scope
is limited to the service/client/mapper/repository packages and the default PIT operators;
do not extrapolate its score to every fault or to library/configuration code.

Published Surefire copies omit JVM property dumps and redact local checkout/home paths and
local login names; original working reports remain in ignored `target/` and `work/`. Counter
values, test names, assertions and mutation outcomes are retained. Test addresses use reserved
example domains. The accompanying repository snapshot contains no actual credentials.
