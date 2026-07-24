<!--
PR titles MUST be Conventional Commits (feat:, fix:, docs:, refactor:, test:, chore:, build:, ci:, perf:);
the title drives versioning and the changelog on squash-merge.
-->

## What changed

<!-- A concise description of the change. -->

## Why

<!-- The motivation / problem being solved. Link the ADR number if this was a decision. -->

## How it was verified

<!-- Which gates/tests prove this works: `./mvnw verify`, new tests, manual steps. -->

- [ ] `./mvnw verify` is green (unit tests + quality gates)
- [ ] Public API changes carry Javadoc; decisions carry an ADR
- [ ] README/doc pointers updated if a command or convention changed
