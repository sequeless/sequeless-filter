# 0001. Split the build into a Maven reactor

- Status: accepted
- Date: 2026-07-30

## Context

`sequeless-filter` was a single `<packaging>jar</packaging>` module holding the entire filter
DSL: AST model, ANTLR 4 grammar/parser, serializer, operator and field registries, and
completion utilities. A SQL adapter (`FilterNode` AST → SQL) is arriving as a second module and
must not inherit the ANTLR runtime dependency it never uses — a plain single-jar layout has no
way to express "this dependency belongs to one consumer of the DSL, not the DSL itself."
Publishing does not exist yet for this project: there is no `<distributionManagement>` and no
`mvn deploy` step anywhere in CI, so there is no real external consumer whose published
coordinates would be broken by this restructuring.

## Decision

We will convert the root `pom.xml` into a `pom`-packaged aggregator/parent that keeps the
existing coordinates `org.sequeless:sequeless-filter`, and move all existing source under a new
`filter-core` module (`org.sequeless:filter-core`). The two modules share one
parent-inherited version — children declare no `<version>` of their own. `jackson-databind` and
`antlr4-runtime` move to the parent's `<dependencyManagement>` only, so each module must opt in
explicitly by redeclaring the dependency it actually needs (this is what will let the upcoming
`filter-sql-adapter` module avoid pulling in ANTLR). `lombok`, `junit-jupiter`, `assertj-core`,
`mockito-core`, and `archunit-junit5` stay as plain, parent-inherited `<dependencies>`, since
every module wants all five and redeclaring them per module would buy nothing. All build plugins
stay in the parent's inherited `<build><plugins>` exactly as before, with one exception:
`flatten-maven-plugin` moves to `<pluginManagement>` with no `<skip>` element, and the
aggregator simply never declares/binds it in its own `<build><plugins>` — each child declares it
bare (groupId + artifactId only) and inherits the full managed configuration.

## Consequences

Published coordinates change from `sequeless-filter` to `filter-core` for the actual DSL jar.
This is accepted and intentionally left unmarked as a breaking change in commit messages, since
nothing is published today and there is no real external consumer to break.

`.releaserc.json` must now commit `*/pom.xml` (alongside the root `pom.xml`) as a release asset,
because `versions:set -DprocessAllModules=true` rewrites both the aggregator's `<version>` and
each child's `<parent><version>` — omitting the child glob would leave the child POM's parent
reference stale after a release commit.

JaCoCo coverage reports are now produced per module (e.g. `filter-core/target/site/jacoco/`)
with no aggregate report across the reactor. This is deliberate, not an oversight — a coverage
aggregator module is not needed until there is a concrete reason to view coverage across
modules in one report.

The Sonar configuration in `sonar-project.properties` was deliberately left untouched by this
change. CI's SonarCloud job has never actually executed an analysis: every run to date has
logged "SONAR_TOKEN not set; skipping SonarCloud scan." and exited 0, so whether
`sonar-project.properties` (a SonarScanner-CLI-style file) is even read by the
Maven-integrated `sonar:sonar` goal CI invokes remains unconfirmed either way — there is no
"ANALYSIS SUCCESSFUL ... dashboard?id=..." log line to point at a working configuration to
preserve or fix. Speculatively migrating Sonar properties into the parent POM without that
confirmation risks guessing wrong about the project key or paths it depends on. Whoever enables
`SONAR_TOKEN` later must check the resulting analysis log's `dashboard?id=` line and then either
fix `sonar.coverage.jacoco.xmlReportPaths` to point at
`filter-core/target/site/jacoco/jacoco.xml`, or migrate the relevant Sonar properties into the
parent POM's `<properties>` and retire `sonar-project.properties` at that point.
