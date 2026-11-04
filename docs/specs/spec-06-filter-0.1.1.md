# Spec 6: sequeless-filter 0.1.1 (release-please, then the field-expression port)

Issue: [#34](https://github.com/sequeless/sequeless-prototype/issues/34). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11), decisions on [#21](https://github.com/sequeless/sequeless-prototype/issues/21) and [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Status: the migration is released (filter-core and filter-sql-adapter 0.1.3, see the PRs below). The port design is settled (section 3.3): the navigator's three questions, the gap analysis (signatures, NULL rules, testkit packaging) and the owner's decisions of 2026-11-04. Not yet implemented.

## 0. Scope and decisions

- Migrate from semantic-release to release-please. Adopt the sequeless-build parent and reusable workflows, add the manifest, delete `.releaserc.json` and `.github/workflows/release.yml`, and archive `CHANGELOG.md` as `docs/CHANGELOG-legacy.md`. **Done**: [#13](https://github.com/sequeless/sequeless-filter/pull/13), [#14](https://github.com/sequeless/sequeless-filter/pull/14), [#16](https://github.com/sequeless/sequeless-filter/pull/16), [#17](https://github.com/sequeless/sequeless-filter/pull/17), [#18](https://github.com/sequeless/sequeless-filter/pull/18), [#19](https://github.com/sequeless/sequeless-filter/pull/19).
- Reset every module version from `1.0.0` to `0.1.0` in its own commit, before the migration. Precedent: sequeless-ontology commit `1dad55f` ("fix: reset version"). This spec's reset commit uses `chore:` so that the reset itself is not a release.
- The first release-please release of `filter-core` and `filter-sql-adapter` was forced to `0.1.1` with `release-as` pins. The pins are removed ([#16](https://github.com/sequeless/sequeless-filter/pull/16)).
- New modules `filter-sql-testkit` and `filter-sql-postgres-adapter` start at `0.1.0`.
- No prototype code moves into filter.
- **Version policy (owner, 2026-11-03):** every version change is a patch bump, so port-related commits use `fix:` types. Nothing is in production yet.
- **Delivery (owner, 2026-11-04): option D, three PRs in this order.** See section 9.

## 1. Identity, stage and dependencies

- Stage 1. Waits for spec 1 ([#29](https://github.com/sequeless/sequeless-prototype/issues/29), done).
- On query's critical path: sequeless-query ([#42](https://github.com/sequeless/sequeless-prototype/issues/42), spec 14) waits for this release.

## 2. Moves (PROVENANCE)

- No code moves into filter. `CHANGELOG.md` is archived as `docs/CHANGELOG-legacy.md`, not deleted.
- Source for the archive: sequeless-filter `main` at `14c7a5d`.

## 3. Prerequisite refactors

- **3.1 Version reset.** `pom.xml:7`, `filter-core/pom.xml:8` and `filter-sql-adapter/pom.xml:8` go from `1.0.0` to `0.1.0`. Standalone `chore:` commit. **Done** before the migration.
- **3.2 Parent adoption. Resolved.** The module poms take `sequeless-build-parent` 0.1.3 from GitHub Packages, with the aggregator pattern of sequeless-template (the root `pom.xml` is parent-less; modules take the parent). A build proves it: CI `build / maven` is green on [#18](https://github.com/sequeless/sequeless-filter/pull/18), [#19](https://github.com/sequeless/sequeless-filter/pull/19) and `main` at `487091a`. The adapter keeps its own `ban-spring` enforcer rule.
- **3.3 Field-expression port (per #21). Design settled.**

  The port lets a filter field resolve to a trusted SQL expression, not only to a quoted column.

  **Settled decisions**

  1. **A field expression is a SqlFragment-shaped record** (`SqlExpression`: trusted SQL text plus its own parameter list). The renderer splices its text where the quoted column would go. Its parameters go before the operator's parameters, and they repeat once for each place the operand appears in the SQL. `ColumnMapping` is the trivial implementation: a quoted identifier with no parameters.
  2. **Port boundary.** `QueryBuilder.binary` and `unary` take a sealed `Field` (`Column` or `Expression`). Inside `AnsiQueryBuilder`, the final `binary`/`unary` turn the field into a `SqlExpression` in one private step (a `Column` is quoted, an `Expression` passes through), and the **protected render hooks take that `SqlExpression`**. Quoting stays in one place, so an override cannot skip the injection guard. `quoteIdentifier(String)` keeps its signature.
  3. **Resolution.** `FieldResolver` (new) resolves a path to `Optional<Field>`. `ColumnMapping` extends it, with a default `resolve` that wraps `columnFor` in a `Column`. `FilterQueryTranslator.translate` takes a `FieldResolver`, so existing `ColumnMapping` callers still compile from source. Compiled callers must be rebuilt, because the descriptor changes.
  4. **No per-field value converter in this port.** The translator keeps one generic converter (`convertValue`). The `Field` carries the typing: an `Expression` states its datatype in its SQL text, and a `Column` binds as the generic Java type. **This deviates from #21**, which says "plus a per-field value converter". A later port can add one additively. The in-repo JSONB analogue converts by value variant, not per field (`sequeless-adapter-persistence-postgres/.../PostgresQueryStore.java:506`), and the query port places conversion in core (`docs/architecture/query-port.md`, lines 54 to 67).
  5. **Case folding is `UPPER`.** #21 wrote `LOWER`. The contract pins `UPPER`, the form that the ANSI default renders.

  **Operator semantics: null-safe complements (owner decision, 2026-11-04)**

  Each negated operator is the exact complement of its positive twin over all rows (`is`/`is not`, `is like`/`is not like`, `exists`/`does not exist`). A null filter value means SQL NULL only where "equals NULL" has a meaning; elsewhere it is rejected.

  | Operator | Rendering (ANSI) | NULL rows | Null filter value |
  |---|---|---|---|
  | `is` | `lhs = ?` | dropped (unchanged) | `lhs IS NULL` (unchanged) |
  | `is not` | `lhs IS DISTINCT FROM ?` | **included** (change) | `lhs IS NOT NULL` (unchanged) |
  | `is greater than`, `>=`, `<`, `<=` | `lhs op ?` | dropped | **rejected**: `SqlRenderException` (change) |
  | `contains`, `starts with`, `is like` | `UPPER(lhs) LIKE UPPER(?) ESCAPE '\'` | dropped | **rejected** (change) |
  | `is not like` | `(lhs IS NULL OR UPPER(lhs) NOT LIKE UPPER(?) ESCAPE '\')` | **included** (change) | **rejected** (change) |
  | `is in` | `lhs IN (non-null members)`, plus `OR lhs IS NULL` when a member is null | dropped, unless a member is null (change) | all members null: `lhs IS NULL` |
  | `exists`, `does not exist` | `lhs IS NOT NULL` / `lhs IS NULL` | unchanged | n/a |

  - `contains`, `starts with` and the LIKE family are case-insensitive. A literal `%`, `_` or `\` in a `contains` or `starts with` value is escaped. The `is like` value is left as the caller wrote it, so a trailing `\` is the caller's, and it is a known dialect difference (section 9, PR 2).
  - `is` stays case-sensitive. The docs state this asymmetry with the LIKE family.
  - `is in [a, b]` equals `is a or is b`.
  - `PostgresQueryBuilder` overrides only the LIKE hook, to `lhs ILIKE ? ESCAPE '\'`, and the `is not like` NULL form for Postgres.
  - filter-core stays unchanged. The rejection is made by the ANSI builder and pinned by the testkit contract.

  **Known out-of-scope bug (filter-core, not this spec):** `is not null` and `is null` are registered as aliases of `is` (`BuiltinOperatorContributor.java:18`), so a filter built in code with `is not null` renders `IS NULL`. The DSL cannot reach it, because the lexer turns `null` into the NULL token. Track it as a separate `fix:` issue.

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
| `filter-sql-testkit` | 0.1.0 | new | `filter-sql-adapter` (compile); JUnit and AssertJ at **compile** scope (the contract is in `src/main`, so they must propagate); H2 (test, for the ANSI contract run) |
| `filter-sql-postgres-adapter` | 0.1.0 | new | `filter-sql-adapter` (compile); `filter-sql-testkit` (test); Testcontainers `postgres` and the `org.postgresql` driver (test, for the `*ContractIT`) |

- There is no cycle. `filter-sql-adapter`'s own tests do not use the testkit (Maven rejects a reactor cycle whatever the scope). The ANSI contract run lives in `filter-sql-testkit`'s test sources.
- Each Postgres contract run is an `*IT` class, so failsafe runs it. The prototype already does this with `postgres:18` through Testcontainers, and it is green in CI.
- No dependency on sequeless-prototype code.

## 6. Release config

- release-please with `release-type: maven` and the `maven-workspace` plugin, as in sequeless-template and sequeless-build. Manifest: `.release-please-manifest.json`.
- `filter-core` and `filter-sql-adapter` are released from `fix:` commits (patch bumps, per the version policy). The `release-as` pins are gone ([#16](https://github.com/sequeless/sequeless-filter/pull/16)).
- New modules: `initial-version: 0.1.0`.
- Each new module's POM sets `sequeless.github.repository` to `sequeless-filter`. The parent hard-codes `sequeless-build`, and the deploy gets 403 without the override ([#19](https://github.com/sequeless/sequeless-filter/pull/19)).
- Cutting a release is outward-facing. The owner's standing approval covers merges and releases of the filter repo (the admin rebase-merge is recorded in the prototype's `remaining-work.md`).
- **BOM re-release:** `sequeless-build`'s `sequeless-bom` lists the new filter versions (`filter-sql-adapter` 0.1.4, `filter-sql-testkit` and `filter-sql-postgres-adapter` 0.1.0). The BOM is released after the filter release, as a patch.

## 7. CI and workflows

- `.github/workflows/ci.yml` and `codeql.yml` use the sequeless-build reusable workflows at `workflows-v0.1.2`. **Resolved:** the pins are current (checked 2026-11-04). The reusable workflow runs `mvn -B -ntp verify` on `ubuntu-latest` with failsafe enabled, so the Testcontainers ITs run in CI (verified by the prototype's runs on the same workflow).
- `.github/workflows/release.yml` is a release-please workflow using `maven-release.yml` at `workflows-v0.1.2`.

## 8. ADRs

- **ADR 0002, field-expression port** (`docs/adr/0002-field-expression-port.md`): records section 3.3's decisions, the signature split (`Field` at the port, `SqlExpression` at the hooks), the breaking changes, the null-safe complement rules and the deferred per-field converter.
- **Breaking for subclasses:** the protected hooks of `AnsiQueryBuilder` take `SqlExpression`, so an external subclass must update its overrides. An override without `@Override` does not fail to compile, and it silently stops being called. The release note says so. The known subclass in this repo is the anonymous `AnsiQueryBuilder` in `DefaultFilterQueryTranslatorTest` (line 330), which PR 1 updates. `H2QueryBuilder` overrides only `quoteIdentifier(String)`, which keeps its signature, so it is unaffected.

## 9. Implementation order (option D, three PRs)

1. **PR 1, the port with the current operator semantics.** `SqlExpression`, `Field`, `FieldResolver`, `ColumnMapping extends FieldResolver`, `QueryBuilder` taking `Field`, the `AnsiQueryBuilder` hooks taking `SqlExpression`, the translator on `FieldResolver`. Existing assertions are unchanged, except the one compile fix at line 330. New tests cover an `Expression`: its text is spliced, and its parameters come first. No behaviour change. `./mvnw -B verify`.
2. **PR 2, the unified semantics with `filter-sql-testkit`.** The null-safe complement rules in the table above, and the case-insensitive LIKE family with escaping. The contract is written for the new semantics and fails against the current ANSI builder, then passes. The six assertions that pin the old SQL (`<>`, `LIKE`, `NOT LIKE`) change here, with `docs/sql-adapter.md` (lines 143 to 161 and 206). The ANSI contract runs on H2 in-memory.
3. **PR 3, `filter-sql-postgres-adapter`, the release config and the BOM.** `PostgresQueryBuilder` (ILIKE, and the Postgres `is not like` form), its `*ContractIT` on Testcontainers `postgres:18`, the release-please config for the two new packages, and the `sequeless-build` BOM update.

**Ordering risk and mitigation:** if the release-please PR merges between PR 1 and PR 2, 0.1.4 ships the breaking hooks with the old semantics. Hold the release-please PR until PR 3 merges, so `filter-sql-adapter` still ships as one 0.1.4.

## 10. Evidence and what is not verified

- **Checked with H2 2.3.232 (standalone SQL, not the builder's output):** `IS DISTINCT FROM` and `<>` return the same rows when no status is NULL. A trailing escape in a LIKE pattern returns NULL, with no error. `ILIKE` and `ESCAPE '\'` parse on H2.
- **Not checked on Postgres:** `ILIKE` over non-ASCII text (folding differs from `UPPER` for ß, Kelvin sign and İ), and the error for a trailing escape character ("LIKE pattern must not end with escape character", from memory). These are the reasons the Postgres dialect runs its own `*ContractIT` on Testcontainers rather than relying on H2.
- **Consumers:** no repository outside sequeless-filter depends on `filter-sql-adapter` (checked across the sibling clones and the BOM). The break is confined to the in-repo subclasses.

## 11. Definition of done

- Each PR: `./mvnw -B verify` is green on its branch.
- The prototype's CI is green against released artifacts, not a SNAPSHOT. The prototype does not consume filter yet, so its `main` CI is the check (owner approval for the cut-over is a separate step).
- The release and BOM re-release: the filter release, then the `sequeless-bom` patch release.
- Issue #34 is closed after the release and BOM re-release are done. The closing comment links them.
