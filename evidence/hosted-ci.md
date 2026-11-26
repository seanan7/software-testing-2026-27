# Hosted verification

The public evidence repository is
[seanan7/software-testing-2026-27](https://github.com/seanan7/software-testing-2026-27).
The complete existing Git history was imported without squashing or rewriting commits.
`testing-r001` preserves the original local-evidence snapshot; `testing-r002` identifies
the submission with hosted evidence and revised documents.

## Observed run

- [GitHub Actions run 45847283457](https://github.com/seanan7/software-testing-2026-27/actions/runs/45847283457)
- Trigger: push to `main`; conclusion: **success**.
- Tested commit: `9c635e62ef1426d2937f18cf79469eabeb7aa515`.
- Runner: `ubuntu-latest`; Temurin Java 21; Python 3.12.
- `bash ci/test.sh` passed functional tests, coverage gates and mutation analysis.
- `python3 -B ci/probe_gates.py` passed the seven positive/negative gate probes.
- Report upload succeeded: `test-evidence`, artifact ID `15132575130`, 593,159 bytes,
  unexpired when checked. The workflow declares 30-day retention.

The public API's run, step and artifact metadata is retained in
`results/hosted/run-45847283457.json`. The run page provides the hosted logs and artifact.
GitHub may require sign-in to download an Actions artifact; the retained baseline and final
XML/JSON evidence under `results/` is directly readable in the public repository.

## Scope of this result

The local final snapshot records 109 passing JUnit cases, 322/324 covered production lines,
62/62 covered branches and 80/80 assertion-killed mutants in the declared PIT scope. Local
timing samples and their interpretation remain separately labelled in `analysis.md`; they
are not measurements of the hosted runner or a deployed cloud service.

This successful push run establishes the observed hosted execution and report upload.
It does not establish every pull-request trigger, negative hosted workflow scenario,
cold-cache condition or artifact accessibility after retention expires. The original r001
documents are preserved as historical versions; their pending-hosted-run statements describe
that revision. The later extension is evaluated separately below.

## Extended method and evidence gates

The [extension run 45857614507](https://github.com/seanan7/software-testing-2026-27/actions/runs/45857614507)
tested commit `bf3dfb826ff435b0d5898bad896f1836c2bba3be` and completed successfully.
The full functional/coverage/mutation step, seven quality-gate probes, ten experiment-evidence
gate probes and artifact upload all succeeded. `test-evidence` artifact `15137115568` was
609,849 bytes and unexpired when checked. Its compact public API metadata is retained under
`results/hosted/run-45857614507.json`.

The extension's local snapshot has 319 passing JUnit cases, unchanged 99.38% line/100% branch
coverage and 80/80 assertion-killed PIT mutants. `extension-study.md` explains the separate
diagnostic findings: 72 torn outcomes in the finite 192-execution fault matrix and eight
nonlinearizable histories. Passing experiment checks must not be read as a claim that those
stronger safety objectives hold. The hosted component-gate probes deliberately corrupt or
remove evidence; they do not simulate a negative push or pull-request event end to end.
