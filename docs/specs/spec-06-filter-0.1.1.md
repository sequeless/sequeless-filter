# Spec 6: sequeless-filter 0.1.1 (release-please, then the field-expression port)

Issue: [#34](https://github.com/sequeless/sequeless-prototype/issues/34). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11), decisions on [#21](https://github.com/sequeless/sequeless-prototype/issues/21) and [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Draft status: decisions below are settled; items marked OPEN are not.

## 0. Scope and decisions

- Migrate from semantic-release to release-please. Adopt the sequeless-build parent and reusable workflows, add the manifest, delete `.releaserc.json` and `.github/workflows/release.yml`, and archive `CHANGELOG.md` as `docs/CHANGELOG-legacy.md`.
- Reset every module version from `1.0.0` to `0.1.0` in its own commit, before the migration. Precedent: sequeless-ontology commit `1dad55f` ("fix: reset version"). This spec's reset commit uses `chore:` so that the reset itself is not a release.
- The first release-please release of `filter-core` and `filter-sql-adapter` is `0.1.1`, forced with a per-package `release-as` pin. Reason: the feat commits after the `0.1.0` tag would otherwise produce `0.2.0`, and #34 and #21 fix the first release at `0.1.1`. The pin is temporary; remove it in the follow-up after the first release (sequeless-build did the same in PRs #7 and #12).
- New modules `filter-sql-testkit` and `filter-sql-postgres-adapter` start at `0.1.0`.
- No prototype code moves into filter.

## 1. Identity, stage and dependencies

- Stage 1. Waits for spec 1 ([#29](https://github.com/sequeless/sequeless-prototype/issues/29), done).
- On query's critical path: sequeless-query ([#42](https://github.com/sequeless/sequeless-prototype/issues/42), spec 14) waits for this release.

## 2. Moves (PROVENANCE)

- No code moves into filter. `CHANGELOG.md` is archived as `docs/CHANGELOG-legacy.md`, not deleted.
- Source for the archive: sequeless-filter `main` at `14c7a5d`.

## 3. Prerequisite refactors

- **3.1 Version reset.** `pom.xml:7`, `filter-core/pom.xml:8` and `filter-sql-adapter/pom.xml:8` go from `1.0.0` to `0.1.0`. Standalone `chore:` commit.
- **3.2 Parent adoption.** The module poms take `sequeless-build-parent` 0.1.3 from GitHub Packages, with the aggregator pattern of sequeless-template (aggregator with no parent; modules take the parent). OPEN: which of the current root `pom.xml` configuration (dependency management, ANTLR generation, Spotless, surefire, JaCoCo) the parent supplies, and which stays in filter. This must be checked by a build before the migration commit.
- **3.3 Field-expression port (per #21).** `FieldResolver` resolves a path to a sealed `Column | Expression` plus a per-field value converter, and hooks take a rendered operand. Operator semantics are unified across dialects (null-safe `is not`; the case-insensitive LIKE family), pinned by a `filter-sql-testkit` contract. A new `filter-sql-postgres-adapter` module. OPEN: the classes, packages and test files are not designed in this draft. They are written here before the port's code starts.

## 4. Package renames

None decided. OPEN until 3.3 is designed.

## 5. Modules and their dependencies

| Module | Version | Status |
|---|---|---|
| `filter-core` | 0.1.1 (release-as pin) | existing |
| `filter-sql-adapter` | 0.1.1 (release-as pin) | existing |
| `filter-sql-testkit` | 0.1.0 | new |
| `filter-sql-postgres-adapter` | 0.1.0 | new |

No dependency on sequeless-prototype code.

## 6. Release config

- release-please with `release-type: maven` and the `maven-workspace` plugin, as in sequeless-template and sequeless-build.
- Manifest: `.release-please-manifest.json` with `0.1.0` for every package at the reset commit.
- `release-as: 0.1.1` for `filter-core` and `filter-sql-adapter`. Removed after the first release.
- New modules: `initial-version: 0.1.0`.
- Cutting a release is outward-facing. It needs owner approval and is not part of this draft.

## 7. CI and workflows

- `.github/workflows/ci.yml` and `codeql.yml` use the sequeless-build reusable workflows at `workflows-v0.1.2`, as sequeless-template does. OPEN: the current filter `ci.yml` and `codeql.yml` pins are not compared in this draft.
- `.github/workflows/release.yml` is replaced by a release-please workflow using `maven-release.yml` at `workflows-v0.1.2`.

## 8. ADRs

None new in this draft. A port design ADR is needed before 3.3's code starts.

## 9. Implementation order

1. This draft.
2. Version reset (3.1), `chore:` commit.
3. Migration (parent adoption per 3.2, manifest, release-as pin, workflows, delete `.releaserc.json` and `release.yml`, archive changelog). Verified by a build.
4. Port (3.3), after its design is written here.

## 10. Definition of done

- The filter's CI is green on its branch (`./mvnw -B verify`, since filter has `mvnw`).
- The prototype's CI is green against released artifacts, not a SNAPSHOT (outward; owner approval).
- The release and BOM re-release (outward; owner approval).
