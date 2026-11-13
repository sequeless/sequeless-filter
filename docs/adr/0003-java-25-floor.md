# 0003. filter-core and filter-sql-adapter require Java 25

- Status: accepted
- Date: 2026-11-13

## Context

`filter-core` and `filter-sql-adapter` are consumer-facing libraries. Their poms set `maven.compiler.release` to 25 (`filter-core/pom.xml` line 22, `filter-sql-adapter/pom.xml` line 22). `filter-sql-testkit` and `filter-sql-postgres-adapter` also set it to 25 (line 22 of each pom); they depend on the two core modules, so they carry the same floor.

`sequeless-prototype` consumes `filter-core` through `sequeless-bom`: `sequeless-core/pom.xml` declares `filter-core` without a version, and the root `pom.xml` imports `sequeless-bom` to manage it.

The alternative, publishing `filter-core` built at release 21, was withdrawn by the owner. On 2026-11-08 the owner chose to move `sequeless-ontology` to release 25, because `filter-core` 0.1.3 is already Java 25 (answer on sequeless-prototype [#39](https://github.com/sequeless/sequeless-prototype/issues/39#issuecomment-6726146761), Q11 in spec 11). The same floor therefore applies to `sequeless-ontology`.

## Decision

We will build `filter-core` and `filter-sql-adapter` at release 25, so a consumer must run on JDK 25 or later.

## Consequences

- A consumer on JDK 21 or earlier cannot use these modules.
- The field-expression port design of spec 6 section 3.3 is recorded in [ADR 0002](0002-field-expression-port.md) (accepted); this ADR does not repeat it.
- The version is pre-1.0 and nothing is in production, so the floor is accepted as is.
- Follow-up, not in this change: `README.md` line 21 ("Requires JDK 21."), `CONTRIBUTING.md` lines 5-6 and `.devcontainer/devcontainer.json` (`java:21-bookworm` image, Java feature 21) still say JDK 21. They need updating to 25 in a separate change.
