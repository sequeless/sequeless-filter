# Contributing to sequeless-filter

## Setup

Requires JDK 21. The Maven wrapper (`./mvnw`) is committed, so no local Maven install is
needed. A devcontainer (`.devcontainer/`) provides a ready JDK 21 toolchain.

```bash
make verify    # confirm a green baseline
```

There are no local git hooks — Conventional Commits and semantic-release run in CI only,
not via husky or any pre-commit/pre-push tooling.

## Workflow

1. **Branch** off `main`.
2. **Make the change** with tests — the test is the specification. Format with `make fmt`.
3. **Verify** with `make verify` (`./mvnw -B verify`).
4. **Commit** using **Conventional Commits** — nothing enforces this locally; CI's PR-title
   job lints the PR title with commitlint.
5. **Open a PR** following the template; CI runs all gates.

## Conventional Commits

`feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
Use `feat!:` or a `BREAKING CHANGE:` footer for majors. The commit history drives SemVer and
the generated `CHANGELOG.md` (via semantic-release) — **never hand-edit the changelog**.

## Recording a decision (ADR)

`make new-adr title="Short title"` scaffolds the next-numbered record under `docs/adr/`; fill
in Context / Decision / Consequences and reference its number in your PR. One ADR per
non-obvious decision; ADRs are immutable — supersede with a new one rather than editing.

## Quality gates

Spotless (format), Enforcer (dependency hygiene, including the "no Spring" ban), and ArchUnit
(boundaries) all run in `make verify`. CodeQL (static analysis) run in CI.

## Coverage (JaCoCo) convention

JaCoCo instrumentation runs on `verify`, but the line-coverage `check` (`jacoco-check`
execution) is **off by default** (`jacoco.check.skip=true` in `pom.xml`) — this repo has
**not yet opted in**. A project opts in once it carries settled production logic by flipping
the properties in `pom.xml`:

```xml
<properties>
  <jacoco.check.skip>false</jacoco.check.skip>
  <jacoco.line.min>0.32</jacoco.line.min> <!-- measured line coverage minus ~5pp -->
</properties>
```

Set `jacoco.line.min` from the module's actually-measured coverage (see the report under
`filter-core/target/site/jacoco/`), and ratchet it up over time rather than guessing a threshold now.
