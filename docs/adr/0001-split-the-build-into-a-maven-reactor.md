# 0001. Split the build into a Maven reactor

- Status: accepted
- Date: 2026-07-30

## Context

`sequeless-filter` was a single `<packaging>jar</packaging>` module holding the entire filter DSL:
AST model, ANTLR 4 grammar/parser, serializer, operator and field registries, and completion
utilities.

A SQL adapter (`FilterNode` AST → SQL) is arriving as a second module. It must not inherit the
ANTLR runtime dependency it never uses — a single-jar layout has no way to express "this
dependency belongs to one consumer of the DSL, not the DSL itself."

Publishing doesn't exist for this project today: there's no `<distributionManagement>` and no
`mvn deploy` step anywhere in CI, so there's no real external consumer whose published coordinates
this restructuring could break.

## Decision

Convert the root `pom.xml` into a `pom`-packaged aggregator/parent that keeps the existing
coordinates `org.sequeless:sequeless-filter`, and move all existing source under a new
`filter-core` module (`org.sequeless:filter-core`).

- **One shared, parent-inherited version.** Children declare no `<version>` of their own.
- **`jackson-databind` and `antlr4-runtime` move to `<dependencyManagement>`-only.** Each module
  must opt in by redeclaring the dependency it actually needs — this is what lets the upcoming
  `filter-sql-adapter` module avoid pulling in ANTLR.
- **`lombok`, `junit-jupiter`, `assertj-core`, `mockito-core`, `archunit-junit5` stay as plain,
  parent-inherited `<dependencies>`.** Every module wants all five, so redeclaring them per module
  would buy nothing.
- **All build plugins stay in the parent's inherited `<build><plugins>`, with one exception:**
  `flatten-maven-plugin` moves to `<pluginManagement>` with no `<skip>` element, and the aggregator
  never declares/binds it in its own `<build><plugins>`. Each child declares it bare
  (groupId + artifactId only) and inherits the full managed configuration.

## Consequences

- **Published coordinates change** from `sequeless-filter` to `filter-core` for the actual DSL jar.
  This is accepted and intentionally left unmarked as a breaking change in commit messages, since
  nothing is published today and there's no real external consumer to break.
- **`.releaserc.json` must commit `*/pom.xml`** (alongside the root `pom.xml`) as a release asset.
  `versions:set -DprocessAllModules=true` rewrites both the aggregator's `<version>` and each
  child's `<parent><version>` — omitting the child glob would leave the child POM's parent
  reference stale after a release commit.
- **JaCoCo coverage reports are now produced per module** (e.g. `filter-core/target/site/jacoco/`),
  with no aggregate report across the reactor. Deliberate, not an oversight — a coverage aggregator
  module isn't needed until there's a concrete reason to view coverage across modules in one report.
- **`sonar-project.properties` was deliberately left untouched.** CI's SonarCloud job has never
  actually executed an analysis — every run to date logs "SONAR_TOKEN not set; skipping SonarCloud
  scan." and exits 0. Whether `sonar-project.properties` (a SonarScanner-CLI-style file) is even
  read by the Maven-integrated `sonar:sonar` goal CI invokes remains unconfirmed: there's no
  "ANALYSIS SUCCESSFUL ... dashboard?id=..." log line to point at a working configuration to
  preserve or fix. Speculatively migrating Sonar properties into the parent POM without that
  confirmation risks guessing wrong about the project key or paths it depends on.

  **Follow-up for whoever enables `SONAR_TOKEN` later:** check the resulting analysis log's
  `dashboard?id=` line, then either fix `sonar.coverage.jacoco.xmlReportPaths` to point at
  `filter-core/target/site/jacoco/jacoco.xml`, or migrate the relevant Sonar properties into the
  parent POM's `<properties>` and retire `sonar-project.properties`.
