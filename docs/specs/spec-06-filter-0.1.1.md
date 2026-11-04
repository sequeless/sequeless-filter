# Spec 6: sequeless-filter 0.1.1 (release-please, then the field-expression port)

Issue: [#34](https://github.com/sequeless/sequeless-prototype/issues/34). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11), decisions on [#21](https://github.com/sequeless/sequeless-prototype/issues/21) and [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Status: the migration is released (filter-core and filter-sql-adapter 0.1.3, see the PRs below). The port design (section 3.3) is settled on 2026-11-04 and is decided by the navigator's three questions; it is not yet implemented.

## 0. Scope and decisions

- Migrate from semantic-release to release-please. Adopt the sequeless-build parent and reusable workflows, add the manifest, delete `.releaserc.json` and `.github/workflows/release.yml`, and archive `CHANGELOG.md` as `docs/CHANGELOG-legacy.md`. **Done**: [#13](https://github.com/sequeless/sequeless-filter/pull/13), [#14](https://github.com/sequeless/sequeless-filter/pull/14), [#16](https://github.com/sequeless/sequeless-filter/pull/16), [#17](https://github.com/sequeless/sequeless-filter/pull/17), [#18](https://github.com/sequeless/sequeless-filter/pull/18), [#19](https://github.com/sequeless/sequeless-filter/pull/19).
- Reset every module version from `1.0.0` to `0.1.0` in its own commit, before the migration. Precedent: sequeless-ontology commit `1dad55f` ("fix: reset version"). This spec's reset commit uses `chore:` so that the reset itself is not a release.
- The first release-please release of `filter-core` and `filter-sql-adapter` was forced to `0.1.1` with `release-as` pins. The pins are removed ([#16](https://github.com/sequeless/sequeless-filter/pull/16)).
- New modules `filter-sql-testkit` and `filter-sql-postgres-adapter` start at `0.1.0`.
- No prototype code moves into filter.
- **Version policy (owner, 2026-11-03):** every version change is a patch bump, so port-related commits use `fix:` types. Nothing is in production yet.

## 1. Identity, stage and dependencies

- Stage 1. Waits for spec 1 ([#29](https://github.com/sequeless/sequeless-prototype/issues/29), done).
- On query's critical path: sequeless-query ([#42](https://github.com/sequeless/sequeless-prototype/issues/42), spec 14) waits for this release.

## 2. Moves (PROVENANCE)

- No code moves into filter. `CHANGELOG.md` is archived as `docs/CHANGELOG-legacy.md`, not deleted.
- Source for the archive: sequeless-filter `main` at `14c7a5d`.

## 3. Prerequisite refactors

- **3.1 Version reset.** `pom.xml:7`, `filter-core/pom.xml:8` and `filter-sql-adapter/pom.xml:8` go from `1.0.0` to `0.1.0`. Standalone `chore:` commit. **Done** before the migration.
- **3.2 Parent adoption. Resolved.** The module poms take `sequeless-build-parent` 0.1.3 from GitHub Packages, with the aggregator pattern of sequeless-template (the root `pom.xml` is parent-less; modules take the parent). A build proves it: CI `build / maven` is green on [#18](https://github.com/sequeless/sequeless-filter/pull/18), [#19](https://github.com/sequeless/sequeless-filter/pull/19) and `main` at `487091a`. The ANTLR, Spotless, surefire and JaCoCo configuration that the parent supplies is the parent's; the adapter keeps its own `ban-spring` enforcer rule.
- **3.3 Field-expression port (per #21). Design settled.**

  The port lets a filter field resolve to a trusted SQL expression, not only to a quoted column. Three design questions were settled with the navigator:

  1. **A field expression is a SqlFragment-shaped record** (trusted SQL text plus its own parameter list). The builder splices its text where the quoted column would go, and its parameters go before the operator's parameters. `ColumnMapping` is the trivial implementation: a quoted identifier with no parameters.
  2. **The hooks take a sealed `Field`.** `QueryBuilder.binary` and `unary` take `Field` (a `Column` or an `Expression`) instead of a `String` column. `ColumnMapping` extends a new `FieldResolver` interface, whose default `resolve` wraps `columnFor` in a `Column`. This is breaking for `QueryBuilder` implementers and for `AnsiQueryBuilder` subclasses (see section 8). Nothing is in production, and the filter is pre-1.0.
  3. **No per-field converter in this port.** The translator keeps one generic value converter (`convertValue`). The `Field` carries the typing: an `Expression` states its datatype in its SQL text, and a `Column` binds as the generic Java type. **This deviates from #21**, which says "plus a per-field value converter". A later port can add one additively when a field needs it. The in-repo JSONB analogue converts by value variant, not per field (`sequeless-adapter-persistence-postgres/.../PostgresQueryStore.java:506`), and the query port places conversion in core (`docs/architecture/query-port.md`, lines 54 to 67).

  **Operator semantics, unified across dialects (from #21):**
  - `is not` (with a non-null value) is null-safe: `IS DISTINCT FROM ?`. A plain `<>` drops rows whose value is `NULL`, so `is not` never uses `<>`.
  - The LIKE family (`contains`, `starts with`, `is like`, `is not like`) is case-insensitive. The ANSI default renders `UPPER(lhs) LIKE UPPER(?) ESCAPE '\'`. A literal `%`, `_` or `\` in a `contains` or `starts with` value is escaped. The `is like` value is left as the caller wrote it.
  - `PostgresQueryBuilder` overrides only the LIKE hook, to `lhs ILIKE ? ESCAPE '\'`.
  - These semantics are pinned by a contract in `filter-sql-testkit`, so each dialect is checked against the same assertions.

  **Consequence:** `contains`, `starts with` and the LIKE family change behaviour (case, and escaping of literal wildcards). That is a `fix:` under the version policy and is recorded here.

## 4. Package names

- `filter-sql-adapter`, existing packages. `org.sequeless.filter.sql.api` gains `SqlExpression` (the trusted record), `Field` (the sealed `Column | Expression`) and `FieldResolver`. `ColumnMapping` stays in `org.sequeless.filter.sql.api`. `org.sequeless.filter.sql.spi` keeps `QueryBuilder` and `AnsiQueryBuilder`.
- `filter-sql-testkit` (new), package `org.sequeless.filter.sql.testkit`: the abstract `QueryBuilderContract`.
- `filter-sql-postgres-adapter` (new), package `org.sequeless.filter.sql.postgres`: `PostgresQueryBuilder`.
- The packages follow the repo segment, as decided for the kernel ([sequeless-kernel ADR 0001](https://github.com/sequeless/sequeless-kernel/blob/main/docs/adr/0001-package-names-follow-the-repo-segment.md)).

## 5. Modules and their dependencies

| Module | Version | Status | Depends on |
|---|---|---|---|
| `filter-core` | 0.1.3 | existing, unchanged by the port | none |
| `filter-sql-adapter` | 0.1.3 → 0.1.4 (fix) | existing, ported (3.3) | `filter-core` |
| `filter-sql-testkit` | 0.1.0 | new | `filter-sql-adapter` (compile); JUnit, AssertJ (test) |
| `filter-sql-postgres-adapter` | 0.1.0 | new | `filter-sql-adapter` (compile); `filter-sql-testkit` (test) |

- There is no cycle: `filter-sql-adapter`'s own tests do not use the testkit. The contract tests for the ANSI builder live in `filter-sql-testkit`'s test sources, where the testkit depends on the adapter.
- No dependency on sequeless-prototype code.

## 6. Release config

- release-please with `release-type: maven` and the `maven-workspace` plugin, as in sequeless-template and sequeless-build. Manifest: `.release-please-manifest.json`.
- `filter-core` and `filter-sql-adapter` are released from `fix:` commits (patch bumps, per the version policy). The `release-as` pins are gone ([#16](https://github.com/sequeless/sequeless-filter/pull/16)).
- New modules: `initial-version: 0.1.0`.
- Each new module's POM sets `sequeless.github.repository` to `sequeless-filter`. The parent hard-codes `sequeless-build`, and the deploy gets 403 without the override ([#19](https://github.com/sequeless/sequeless-filter/pull/19)).
- Cutting a release is outward-facing. The owner's standing approval covers merges and releases of the filter repo (the admin rebase-merge is recorded in the prototype's `remaining-work.md`).
- **BOM re-release:** `sequeless-build`'s `sequeless-bom` lists the new filter versions (`filter-sql-adapter` 0.1.4, `filter-sql-testkit` and `filter-sql-postgres-adapter` 0.1.0). The BOM is released after the filter release, as a patch.

## 7. CI and workflows

- `.github/workflows/ci.yml` and `codeql.yml` use the sequeless-build reusable workflows at `workflows-v0.1.2`. **Resolved:** the pins are current (checked 2026-11-04).
- `.github/workflows/release.yml` is a release-please workflow using `maven-release.yml` at `workflows-v0.1.2`.

## 8. ADRs

- **ADR 0002, field-expression port** (`docs/adr/0002-field-expression-port.md`): records section 3.3's three decisions, the breaking change to `QueryBuilder`, and the deviation from #21 (no per-field converter). Status `accepted` once merged.
- **Breaking for subclasses:** `AnsiQueryBuilder`'s protected hooks take `Field`, so an external subclass must update its overrides. The known subclasses in this repo are `H2QueryBuilder` (`filter-sql-adapter`'s tests) and the anonymous `AnsiQueryBuilder` in `DefaultFilterQueryTranslatorTest` (line 330). Both are updated in the port's PR.

## 9. Implementation order

1. This design: the spec section 3.3 and ADR 0002 (docs PR).
2. The port in `filter-sql-adapter`: `SqlExpression`, `Field`, `FieldResolver`, `ColumnMapping extends FieldResolver`, the `QueryBuilder` and `AnsiQueryBuilder` hooks, the translator, and the existing tests, which change where the behaviour changes. Verified by `./mvnw -B verify`.
3. `filter-sql-testkit`: the abstract `QueryBuilderContract` and its ANSI test.
4. `filter-sql-postgres-adapter`: `PostgresQueryBuilder` (ILIKE), tested with the contract.
5. Release: the release-please config for the two new packages, the root aggregator's modules, the BOM update in `sequeless-build`.

## 10. Definition of done

- The filter's CI is green on its branch (`./mvnw -B verify`, since filter has `mvnw`).
- The prototype's CI is green against released artifacts, not a SNAPSHOT. The prototype does not consume filter yet, so its `main` CI is the check (owner approval for the cut-over is a separate step).
- The release and BOM re-release: the filter release, then the `sequeless-bom` patch release.
- Issue #34 is closed after the release and BOM re-release are done. The closing comment links them.
