# Plan — Multi-module restructure, ArchUnit guards, filter-sql-adapter

## Goal

Turn the single-module `org.sequeless:sequeless-filter` jar into a Maven reactor with two
modules — `filter-core` (the existing DSL: AST, ANTLR grammar/parser, serializer, registries,
completion) and a new `filter-sql-adapter` (FilterNode AST → SQL), with ArchUnit guarding the
package/module boundaries that remain enforceable. (Neither module is actually published today —
no `<distributionManagement>` or deploy step exists anywhere in this repo's tooling; that's a
pre-existing gap this plan doesn't attempt to close.) **Note on scope of "ArchUnit-guarded":** two
of the rules originally envisioned (an api→internal facade rule in filter-core, and a
"testfixtures never leak to main" rule) were dropped during review because Maven's own compilation
model already makes both invariants un-violatable — see D8/D15. What ArchUnit actually enforces
after this plan: filter-core's existing `BoundaryRulesTest` rules (`filter_is_free_of_spring`,
`api_and_spi_types_are_public`) survive untouched; filter-sql-adapter's new ArchUnit test adds a
module-local public-types rule, a cross-module rule that `filter-sql-adapter`'s main scope may not
reach filter-core's `internal`/`spi`, and — kept for documentation purposes despite being
unfalsifiable given Maven already rejects the cycle — a rule that filter-core may not depend on
`sql..` (D26). That is a smaller guarantee than "layering is fully policed," and is accurate to
state as such.

Along the way, filter-core's field-registry *defaults* stop being production code: they become
test-scope fixtures, and `FieldRegistry`'s contract is trimmed to what production actually needs —
except `of(...)`, which Stage 3 decided to keep (see D5). Separately, `OperatorDefinition` gains a
value-shape signal and `FilterValidator` gains a value-shape check (D17) — a small, independently
motivated improvement to filter-core surfaced by reviewing the SQL adapter's requirements, not
something filter-sql-adapter depends on filter-core changing for it (the adapter still degrades
gracefully via its own `SqlRenderException` if a shape mismatch somehow reaches it).

## Settled decisions

These were `Needs Answer` in the brief's Register; each was verified against the codebase and is
now folded into the plan body below. Deviations from the brief's first-ranked suggestion are
marked and justified. **Several of these were revised during Stage 3 (user interview) and Stage 4
(two rounds of automated review) — see `## Decisions` below for the current, authoritative
version; rows here are kept as the historical Stage 2 record.**

| Ref | Decision |
| --- | --- |
| B6 | Root `pom.xml` becomes `<packaging>pom</packaging>` aggregator/parent (`org.sequeless:sequeless-filter`), with `filter-core` and `filter-sql-adapter` as `<module>` children. groupId stays `org.sequeless`; artifactIds are `filter-core` / `filter-sql-adapter` matching their directory names. Shared versions/plugin config live in the parent's `<properties>` / `<dependencyManagement>` / `<pluginManagement>`, never duplicated per module — **refined by D9**: most plugins stay in the parent's inherited `<build><plugins>`, only `flatten` moves to `<pluginManagement>`. **Deviation:** the suggestion proposed groupId `org.sequeless.filter`; keeping `org.sequeless` avoids inventing a second group for a project that already ships under `org.sequeless`. |
| B7 | One shared, parent-inherited version. Children declare no `<version>` of their own and reference each other with `${project.version}`. Verified: `.releaserc.json` drives a single `versions:set` and no `mvn deploy`/`distributionManagement` exists anywhere, so per-module release cadence buys nothing today. |
| B8 | filter-sql-adapter uses `org.sequeless.filter.sql.api`, `org.sequeless.filter.sql.spi`, `org.sequeless.filter.sql.internal` — the same `{api,internal,spi}` layering filter-core already uses, one subdomain down. **Refined by D12**: the shipped default `QueryBuilder` implementation lives in `sql.spi` (public, extensible), not `sql.internal`; `sql.internal` currently has no classes reserved for it. |
| B9 | The SPI produces **raw SQL fragments plus an ordered bind-parameter list** (JDBC `PreparedStatement` style). Verified: `FieldFilter`/`AnyFilter` carry fully-resolved `JsonNode` values (not placeholders), and the enforcer `ban-spring` rule plus the "no storage/query engine dependency" stance in README rule out a JPA Criteria object graph. Reaffirmed directly by **D2**. **Superseded mechanism** — see D3/D6: the original plan had a single `SqlRenderer` walk the tree with a `SqlDialect` override hook; this is now a hexagonal `QueryBuilder` port (`binary`/`unary`/`and`/`or`, each receiving `OperatorDefinition`) that owns full construction. |
| B10 | The adapter **does** own a path-to-column contract: a `ColumnMapping` functional interface in `org.sequeless.filter.sql.api`, supplied by the caller at render time, with a `ColumnMapping.identity()` for callers whose paths already equal column names. **Refined by D13**: `identity()` is documented as trusted-input-only (no runtime validation added) — callers exposing untrusted paths must supply an explicit `of(Map)` allowlist instead. **Scope limit surfaced by D20**: only flat, single-table column mappings are supported — there is no way to contribute a join, correlated subquery, or JSON path expression. |
| B11 | filter-sql-adapter main scope depends on filter-core's `api` package only — never `spi`, never `internal`. Unaffected by Stage 3/4; still holds under D4 (AnyFilter expansion uses `AnyFilterExpander`, itself in `api`). |
| B12 | **Superseded by D5.** The original finding (filter-sql-adapter defines its own test-only `FieldRegistry` fixture rather than sharing filter-core's) is moot: D5 reinstates `FieldRegistry.of(...)` as production API, so there is no test-only fixture to share or duplicate in the first place. |
| B2 | **Superseded by D5**, for the same reason as B12 — no test-scope fixture-sharing question remains once `FieldRegistry.of(...)` is production API again. |
| B13 | **Partially reversed by D5.** The behavior-preservation finding for removing `isPermissive()` still holds (its only two callers collapse to `fields.find(path).isEmpty()` unconditionally), and `permissive()`/`PermissiveFieldRegistry` still move to test-only. What changed: `of(List)` does **not** go away — it's kept, retyped to take `FieldRegistrySpec` (D5). |

## Decisions

Answers reached by interviewing the user in Stage 3 and via two rounds of Stage 4 automated
review, in the order resolved. These are the authoritative, current design — the
`## Settled decisions` table above is historical.

### Stage 3 (user interview)

- **D1 — Commit message convention: no breaking-change markers.** Removing
  `FieldRegistry.isPermissive()` is technically a breaking API change, but **no commit produced
  while implementing this plan should be marked as breaking** (no `!` after the conventional-commit
  type, no `BREAKING CHANGE:` footer) — use a plain `fix:` (or otherwise appropriate type) instead,
  for this and every other commit in the plan. Reconsidered and reaffirmed at D10 after the review
  flagged the semver implications.
- **D2 — SQL SPI output stays raw SQL, confirmed.** Re-affirms B9's conclusion (`SqlFragment`, not
  JPA Criteria) after being asked directly; only the internal mechanism changed (D3/D6).
- **D3 — Hexagonal query-builder architecture replaces the renderer+dialect-override design.**
  `sql.spi.QueryBuilder` is now the **port**: vendor adapters implement it and own construction of
  each `SqlFragment` — identifier quoting, operator-to-SQL translation, value expansion, AND/OR
  combination — rather than a shared renderer that only exposes override hooks. `sql.api`'s
  translator is the vendor-agnostic core: it walks the `FilterNode` tree and delegates every node to
  the injected `QueryBuilder`. The port stays **fixed to producing `SqlFragment`** (not a generic
  `QueryBuilder<Q>`) — considered and rejected, since full genericity would reopen the
  already-settled JPA-Criteria question (B9/D2) and would mean filter-sql-adapter's own H2 fixture
  and ArchUnit guards could no longer assume one concrete output shape. This supersedes the Stage 2
  plan's `SqlRenderer`/`SqlDialect` design entirely; the exact port signature is D6.
- **D4 — The SQL adapter expands `AnyFilter` itself.** Reverses the Stage 2 plan's stance (which
  rejected unexpanded `AnyFilter` and required callers to pre-expand). The translator now depends on
  filter-core's `AnyFilterExpander` (api) and expands any `AnyFilter` node before delegating to the
  `QueryBuilder`, using `AnyFilterExpander`'s **default** expansion strategy. This means the
  translator's entry point needs a `FieldRegistry` and `OperatorRegistry` in addition to
  `ColumnMapping` and `QueryBuilder` — which motivates D5. **Caveat:** callers who need a *custom*
  `AnyExpansionStrategy` (a real, documented feature of `AnyFilterExpander`) have no way to supply
  one through the adapter's entry point — they must still pre-expand with their own strategy before
  calling `translate(...)` if they need anything other than the default. **Refined by D18**: an
  operand of the expansion that has no `ColumnMapping` entry is dropped rather than failing the
  whole filter — see D18.
- **D5 — `FieldRegistry.of(...)` reinstated in filter-core main scope, parameterized by a new
  builder-constructed spec type, not a raw `List`.** Partially reverses B13/T1 (which removed
  `FieldRegistry.of(List<FieldDefinition>)` entirely and moved `DefaultFieldRegistry` to test-only).
  `DefaultFieldRegistry` stays in filter-core's main scope (not moved to test), backing a reinstated
  `FieldRegistry.of(FieldRegistrySpec)` static factory. The parameter type changes from a raw
  `List<FieldDefinition>` to a new `FieldRegistrySpec` value type, built via Lombok `@Builder`
  (holding `List<FieldDefinition> fields` today), so the registry can be configured by more than a
  flat field list later without another signature break. `DefaultFieldRegistry` itself does **not**
  need its own builder — only its constructor's parameter type changes, from `List<FieldDefinition>`
  to `FieldRegistrySpec`. `PermissiveFieldRegistry`/`permissive()`/`isPermissive()` still move to
  test-only / get removed exactly as B13 already settled ("keep the permissive one out unless we
  discover later that we'll need it," per the user — main scope, that is). This eliminates the
  Stage 2 plan's `testfixtures.InMemoryFieldRegistry` fixture entirely: tests construct a
  `FieldRegistry` the same way any production caller now does, via
  `FieldRegistry.of(FieldRegistrySpec.builder().fields(...).build())`. Only
  `testfixtures.PermissiveFieldRegistry` remains as a genuine test-only fixture. **Supersedes B2 and
  B12**: with the default registry back in production, there is no test-only fixture left to share
  or duplicate across modules.

### Stage 4, first review pass

- **D6 — `QueryBuilder`'s methods take `OperatorDefinition`, not a bare canonical-name string.**
  `SqlFragment binary(String column, OperatorDefinition operator, Object value)`,
  `SqlFragment unary(String column, OperatorDefinition operator)`,
  `SqlFragment and(List<SqlFragment> operands)`, `SqlFragment or(List<SqlFragment> operands)`. Lets
  a vendor branch on `operator.getSyntax()`/`isUnary()`/`getParameters()` without re-deriving them
  from a string, and — as a side effect, not a scope expansion — structurally accommodates a future
  `Syntax.FUNCTION` operator (same `(column, operator, value)` shape; `value` would be the
  argument list) without a fifth port method. `BuiltinOperatorContributor` ships no `FUNCTION`
  operator today, so the translator/builder tasks implement only the 13 shipped `INFIX` operators;
  this is forward compatibility, not a commitment to build FUNCTION support now. `value` here is the
  **already-converted** Java value — see D7. **Refined by D16**: `binary()`/`unary()` are `final`
  dispatchers on the shipped `AnsiQueryBuilder`, which needs its own fallback path for operators
  outside the 13 built-ins — see D16.
- **D7 — The translator (not the vendor `QueryBuilder`) converts `JsonNode` values to plain Java
  objects before calling the port.** One documented mapping, applied uniformly so every vendor gets
  it for free: `TextNode`→`String`; numeric (`IntNode`/`LongNode`/`DoubleNode`, matching
  `FilterBuildingVisitor.parseNumber`'s int/long/double selection)→the corresponding boxed
  `Number`; `BooleanNode`→`Boolean`; `NullNode`→Java `null`; `ArrayNode`→`List<Object>`, each
  element converted recursively (the grammar allows nested arrays). **Explicit non-goal:** no
  temporal coercion — a `jsonSchemaFormat: "date-time"` field's value arrives as a plain `String`
  (the grammar has no date literal); binding it against a `TIMESTAMP` column is left to the vendor's
  JDBC driver and is a known limitation (carried into Caveats). **Extended by D19**: this table
  doesn't cover every `JsonNode` subtype a deserialized (not just parsed) `FilterNode` can carry —
  D19 adds an explicit catch-all.
- **D8 — No api→internal ArchUnit rule in filter-core.** Dropped entirely rather than fixed. The
  incomplete version in the Stage 2 plan named only two exemptions
  (`DefaultOperatorRegistry`/`DefaultFieldRegistry`) but missed `BuiltinOperatorContributor` (via
  `OperatorRegistry.defaults()`) and `FilterParser`'s reach into `CursorAnalyzer`,
  `FilterBuildingVisitor`, and the generated `internal.parser` package. filter-core's `api` is
  deliberately a facade over `internal`; policing that edge would mean editing an exemption list on
  every future facade method. The invariant that actually matters — nothing *outside* filter-core
  depending on its `internal` — is covered by the cross-module ArchUnit rule instead.
- **D9 — Only `flatten-maven-plugin` moves to `<pluginManagement>`; everything else stays in the
  parent's inherited `<build><plugins>`.** `compiler`, `antlr4`, `spotless`, `jacoco`, `surefire`,
  `failsafe`, `enforcer` stay exactly where they are today (Maven inherits their executions to
  children automatically); only `flatten` — the one plugin the B5 caveat already identified as
  needing per-module control — moves to `<pluginManagement>`. **Corrected by D24**: no `<skip>` flag
  belongs in the managed entry itself — see D24 for the actual mechanics.
- **D10 — Reaffirms D1: proceed with `fix:`-only commits, no breaking markers, despite shipping 4
  breaking API changes as a nominal patch release.** Confirmed acceptable specifically because no
  `<distributionManagement>` or deploy step exists anywhere in this repo's tooling — there is no
  real published artifact and thus no actual external consumer to break today.
- **D11 — The translator explicitly rejects two cases `AnyFilterExpander` doesn't itself guard
  against.** `AnyFilterExpander.expand` silently returns an empty `OrFilter`/`AndFilter` when zero
  fields are compatible (not an exception, contrary to what an earlier draft of this plan claimed);
  the translator now detects a zero-operand expansion result and raises `SqlRenderException`.
  Separately, `is in []` (an empty array) is legal DSL but `IN ()` is a syntax error in every SQL
  dialect; the translator raises `SqlRenderException` for an empty converted value list on `is in`
  as well. **Generalized by D22**: the empty-operand rejection applies to *any* zero-operand
  `AndFilter`/`OrFilter` the translator encounters, not only ones produced by `any`-expansion.
- **D12 — The default `QueryBuilder` implementation is public, extensible, and structured as a
  Template Method — but `QueryBuilder` itself has no static factory pointing at it.** Refined twice
  after the initial finding, in order:
  1. *Public + extensible, in `sql.spi` not `sql.internal`.* Target: `sql.spi.AnsiQueryBuilder`, a
     non-`final` public class implementing `QueryBuilder`. Reasoning: D3 explicitly rejected "a
     shared renderer that only exposes override hooks," but the initial idea had the H2 fixture
     override one method of an *internal* class — only possible because the fixture shares a module
     with `sql.internal`. A real third-party vendor can't reach `sql.internal` at all and would have
     had to reimplement the entire port to change one method.
  2. *Template Method, not a single override-the-whole-method class.* Because all 13 built-in
     operators funnel through one `binary()`/`unary()` call, "extend and override one method" was
     still not cheap partial customization — overriding `binary()` alone means reimplementing the
     dispatch for every other operator too. `AnsiQueryBuilder`'s `binary()`/`unary()` are `final`
     dispatchers that delegate to `protected` per-concern hooks (e.g. `quoteIdentifier(String)`,
     `renderIs(...)`, `renderLike(...)`, `renderIn(...)`) — a vendor overriding, say, only `LIKE`
     escaping touches one small hook, not a 13-way switch.
  3. *No `QueryBuilder.ansi()` static factory.* Considered keeping it (mirroring
     `OperatorRegistry.defaults()`), but concluded the factory and vendor extensibility are
     orthogonal: a vendor writes `class PostgresQueryBuilder extends AnsiQueryBuilder` and never
     calls `ansi()` at all — extensibility comes entirely from (1)+(2). The factory only helped a
     *non-customizing* caller reach the plain default one line shorter (`QueryBuilder.ansi()` vs.
     `new AnsiQueryBuilder()`), which isn't worth the port interface carrying a compile-time
     reference to one specific concrete adapter. `QueryBuilder` stays a pure port; callers and
     vendors instantiate `AnsiQueryBuilder` (or a subclass) directly.

  **Extended by D16 and D23** (second review pass): D16 adds a fallback hook for operators outside
  the 13 built-ins; D23 mandates a specific escaping contract for the default `quoteIdentifier`
  hook.
- **D13 — `ColumnMapping.identity()` is documented as trusted-input-only; no runtime validation is
  added.** The translator accepts arbitrary `FilterNode`s (including ones built from untrusted or
  deserialized input), and `identity()` places the resolved path directly in SQL identifier position
  with no check independent of whatever quoting a vendor's `QueryBuilder` applies. Rather than adding
  a validation pass to the shared core, this is called out explicitly in `identity()`'s Javadoc:
  intended for paths the caller already controls, not for exposing arbitrary input as column names.
  Callers with untrusted paths must use `ColumnMapping.of(Map)` (an explicit allowlist) instead. This
  same "deserialized/untrusted `FilterNode` is in-scope input" framing is what motivates D19's
  catch-all requirement for value conversion, and D23's requirement that the *shipped* default
  quoting still be technically sound even though `identity()` itself isn't validated.
- **D14 — `LIKE` escaping for `contains`/`starts with`/`is like`/`is not like` is left entirely to
  each `QueryBuilder` implementation's discretion; the plan does not mandate specific escaping
  semantics.** Consistent with D3's "vendor owns 100% of construction" — accepted with the explicit
  understanding that escaping behavior (and thus exact matching semantics for values containing `%`
  or `_`) may differ across vendor adapters, including between the shipped `AnsiQueryBuilder` and any
  third-party one, and no cross-vendor test can assert one specific outcome. This is intentional, not
  an oversight. (This is distinct from D23's `quoteIdentifier` mandate: `LIKE` escaping is a
  matching-semantics question left open by design; identifier quoting is an injection boundary in
  the one implementation this repo actually ships, so it isn't.)
- **D15 — The `testfixtures` "fixture-leak" ArchUnit rule is dropped entirely, not just its
  "confirm it fails" verification step.** It can never trip: Maven's compilation model already makes
  a main-scope class depending on a test-scope class impossible, and filter-sql-adapter has no
  test-jar dependency on filter-core's tests (D5 eliminated that mechanism), so the rule would guard
  against something that literally cannot happen through any build-time path.

### Stage 4, second review pass

- **D16 — `AnsiQueryBuilder` gets a fallback hook for operators outside the 13 built-ins, rather
  than leaving `binary()`/`unary()` as closed `final` dispatchers.** `OperatorRegistry.of(List<OperatorContributor>)`
  is public API and custom operators are a real, already-tested extension point
  (`FunctionArgValidationTest` exercises one) — without a fallback, a vendor registering a custom
  operator hits a `final` method it cannot extend and must reimplement `QueryBuilder` from scratch,
  recreating the exact gap D12 was raised to close, one level down. `AnsiQueryBuilder` adds
  `protected SqlFragment renderUnknown(String column, OperatorDefinition operator, Object value)`
  and a unary twin, both throwing `SqlRenderException` by default and overridable by a vendor that
  wants to support the custom operator. The full `protected` hook set (`quoteIdentifier`, `renderIs`,
  `renderLike`, `renderIn`, `renderUnknown`, plus whatever else the 13-operator implementation needs)
  must be enumerated explicitly when the contracts are defined, not left as "etc." — subclass-visible
  members are as much a compatibility commitment as public ones.
- **D17 — Value-shape validation moves into filter-core's `FilterValidator`, not the SQL adapter.**
  Nothing today checks that a value's shape matches what its operator expects: the grammar accepts
  `status is in 'open'` (scalar into a list-shaped operator) or `status is ['a','b']` (list into a
  scalar operator) equally, and either would reach a vendor `QueryBuilder` as the wrong Java shape.
  Rather than fixing this only for the SQL adapter, the check is added to the existing, shared
  `FilterValidator` so every consumer benefits. This requires giving `OperatorDefinition` a
  value-shape signal it doesn't have today (e.g. distinguishing `is in`, list-shaped, from the other
  12 built-ins, which are scalar) — a small, independently-motivated filter-core change, not
  something filter-sql-adapter depends on to function (a shape mismatch that somehow still reaches
  the adapter is still caught there as an ordinary `SqlRenderException`, just later than ideal). This
  is a new task in Phase 1 alongside T1, not a change to filter-sql-adapter's tasks.
- **D18 — Partial `ColumnMapping` coverage during `any` expansion drops the unmapped operands
  instead of failing the whole filter.** `AnyFilterExpander` expands `any` to one `FieldFilter` per
  *registry*-compatible field, but `FieldRegistry` (domain vocabulary) and `ColumnMapping`
  (persistence vocabulary — especially once a caller follows D13's advice to use an explicit
  allowlist) are independently populated and can drift out of sync. Under the original "empty result
  ⇒ reject" rule (D11), a single unmapped registry field breaks `any` entirely — likely on the first
  realistic use. The translator now silently drops any expanded operand whose path has no
  `ColumnMapping` entry, and only raises `SqlRenderException` if *none* of the expanded operands
  survive (folding into D11's existing empty-expansion check). An explicitly-named path (not reached
  through `any`) is unaffected — it still hard-fails on an unmapped column, exactly as before.
- **D19 — Value conversion (D7) gets an explicit catch-all for `JsonNode` subtypes outside the
  original five-case table.** `FilterNode` is fully Jackson-deserializable
  (`@JsonTypeInfo`/`@JsonSubTypes`), and D13 already treats deserialized/untrusted ASTs as in-scope
  input to `translate(FilterNode …)` — but a JSON integer past `Long.MAX_VALUE` deserializes to
  `BigIntegerNode` and an object-valued field to `ObjectNode`, neither handled by D7's table as
  originally written. The translator now additionally: converts any other numeric node via
  `isNumber()`/`numberValue()`; raises `SqlRenderException` for anything else unrecognized
  (`ObjectNode`, `BinaryNode`, `POJONode`, `MissingNode`, etc.) rather than guessing.
- **D20 — The single-table, flat-column limitation is stated as an explicit caveat, not silently
  discovered by the first real user.** `ColumnMapping` returns a bare column name and `SqlFragment`
  is a single `WHERE`-fragment plus binds — there's no way to express a join, a correlated `EXISTS`,
  or a JSON path expression, yet the DSL's own headline example is a nested path
  (`lineItems.qty > 5`, per `FieldFilter`'s Javadoc and both `README.md` and `docs/filter-dsl.md`)
  that on a relational schema typically means a join, not a column. No design change — this is
  purely calling out a real limitation explicitly (see Caveats and `docs/sql-adapter.md`, added to
  the docs task) rather than widening scope to cover it in this plan.
- **D21 — `SqlFragment`'s bind-parameter list is defensively copied in a way that tolerates `null`
  elements.** Every existing collection-carrying type in this codebase (`AndFilter`, `OrFilter`,
  `OperatorDefinition`'s builder) defensively copies with `List.copyOf`, which throws
  `NullPointerException` on a `null` element — but D7 legitimately produces `null` (from
  `NullNode`), and `is in ['a', null]` is grammatically valid, landing a `null` in the parameter
  list. `SqlFragment`'s compact constructor uses
  `Collections.unmodifiableList(new ArrayList<>(parameters))` instead of `List.copyOf`, deliberately
  deviating from the house idiom for this one type since the idiom would silently turn a legal
  filter into a construction-time `NullPointerException`.
- **D22 — Generalizes D11: the translator rejects *any* zero-operand `AndFilter`/`OrFilter` it
  encounters, not only ones produced by `any`-expansion.** `AndFilter`/`OrFilter` null-check but
  don't size-check their operand lists, so both `Filters.and()` and a deserialized
  `{"type":"and","operands":[]}` construct without error — and since `AnyFilterExpander.expand`
  recurses, a zero-compatible-field `any` nested inside a larger `and`/`or` produces an empty
  operand *inside* the tree, not necessarily at the root. Any such node reaching the port would
  render as `()`, a syntax error in every dialect; the translator now checks every `AndFilter`/
  `OrFilter` node it visits, at any depth, not just the top-level result of an expansion.
- **D23 — The shipped `AnsiQueryBuilder.quoteIdentifier` hook has a mandated escaping contract: emit
  `"…"` with any embedded `"` doubled.** D13 accepted `ColumnMapping.identity()` shipping with no
  runtime validation specifically on the reasoning that quoting is the vendor `QueryBuilder`'s
  responsibility — but that only holds if the one `QueryBuilder` this repo actually ships has a
  correct quoting implementation to point to. Parser-produced paths are already safe
  (`WORD : [a-zA-Z_][a-zA-Z_0-9]*`); the exposure is entirely from programmatic/deserialized ASTs,
  which D13 already treats as in-scope. `quoteIdentifier` is `protected` and overridable (D12.2), so
  its Javadoc must also say overriding it is security-relevant, not just a formatting choice.
- **D24 — Corrects D9's mechanics: no `<skip>` flag belongs on `flatten` in the parent's
  `<pluginManagement>`.** `<pluginManagement>` configuration is inherited by every child that
  *declares* the plugin — putting `<skip>true</skip>` inside the managed entry would make every
  child that re-declares `flatten` inherit the skip too, silently disabling flattening everywhere
  (nothing would surface the mistake immediately, since nothing consumes `.flattened-pom.xml`
  today). The actual mechanism: `flatten` goes into `<pluginManagement>` with **no** `<skip>`
  element at all; the aggregator simply never declares/binds it in its own `<build><plugins>`
  (an unbound plugin doesn't run — no skip flag needed); each child declares it normally in its own
  `<build><plugins>` and gets the managed config.
- **D25 — Before touching Sonar configuration, verify whether `sonar-project.properties` is read at
  all by the scanner CI actually invokes.** CI runs `./mvnw -B verify sonar:sonar` — the
  Maven-integrated Sonar scanner, which is documented to source its configuration from the POM and
  `-D` flags, not from a standalone `sonar-project.properties` file (that file is a SonarScanner CLI
  artifact). If true, the B5 coverage-path caveat's whole premise is moot, and coverage may already
  be mis-reported today for reasons unrelated to this restructure. Sequencing: check empirically
  first (a CI run / Sonar dashboard check for non-zero current coverage) before making changes; only
  if confirmed, move `sonar.projectKey`/`sonar.organization`/exclusions/`sonar.coverage.jacoco.xmlReportPaths`
  into the parent POM's `<properties>` as part of the reactor-tooling task, and retire
  `sonar-project.properties`. Do not fix speculatively.
- **D26 — Keeps the "no filter-core class may depend on `sql..`" cross-module ArchUnit rule despite
  it being technically unfalsifiable by the same argument that removed D15's rule.** A
  filter-core→filter-sql-adapter dependency is a reactor build-order cycle Maven rejects outright,
  so — like the deleted fixture-leak rule — this one "can never trip through any build-time path"
  either. Kept anyway, and the inconsistency with D15 is deliberate, not an oversight: it costs two
  lines, documents the intended dependency direction at the module boundary for a human reader, and
  would start actually mattering the moment a third module or an unexpected dependency edge appears.
  D15's rule had no equivalent documentation value once removed (it was guarding an accident, not a
  direction).

### Stage 4, third review pass

- **D27 — `FilterQueryTranslator.translate(...)` validates its input itself, calling
  `FilterValidator` before translating.** Neither `FilterParser.parse` nor anything else in this
  plan actually invokes `FilterValidator` — validation is entirely opt-in today — so D17's new
  value-shape check never protected the SQL path as D17/the Goal originally claimed: a shape
  mismatch would have reached a vendor `QueryBuilder` as a raw `ClassCastException` or, past that,
  a driver-level `SQLException`, not the clean `SqlRenderException` the plan asserted. `translate(...)`
  already receives both `FieldRegistry` and `OperatorRegistry` — the two things `FilterValidator`
  needs — so calling it costs no new parameters. Any violation `FilterValidator` reports is
  converted to `SqlRenderException`. Accepted cost: one extra validation pass per `translate(...)`
  call, and the adapter is now coupled to validator semantics it doesn't own — judged worth it so
  the adapter is genuinely safe to call standalone on an unvalidated or deserialized `FilterNode`.
- **D28 — The value-shape signal on `OperatorDefinition` (D17) is a derived three-state signal, not
  a boolean defaulting to scalar.** A plain boolean breaks on the operator model's other two shapes:
  unary operators (`exists`/`does not exist`) carry no value at all, and `Syntax.FUNCTION` operators
  (a real, already-tested extension point — `FunctionArgValidationTest`'s custom `withinLast`) always
  carry an `ArrayNode` of arguments regardless of arity. As specified in D17/T2 ("mark `is in` as
  list-shaped … the other 12 default to scalar"), a `FilterValidator` shape check would have wrongly
  rejected every `FUNCTION`-syntax filter, including the one this plan's own D16 cites as proof that
  custom operators matter. The signal is instead **derived**, not independently set per operator:
  `NONE` when `operator.isUnary()`, `LIST` when `operator.getSyntax() == Syntax.FUNCTION`, `SCALAR`
  otherwise (which is also the correct default for `is in` once explicitly marked — see D17/T2's
  "mark `is in` as list-shaped" for the one exception to the `SCALAR` default among `INFIX`
  operators).
- **D29 — `FilterQueryTranslator` expands `AnyFilter` per-node inside its own tree walk (in
  `visitAny`), not via one whole-tree `AnyFilterExpander.expand(node, …)` call.** D18 requires
  distinguishing an `any`-expansion-produced `OrFilter` (whose unmapped operands get silently
  dropped) from an author-written or deserialized `OrFilter` at the same tree position (which still
  hard-fails on an unmapped column) — but `AnyFilterExpander.expand` recurses over the *entire* tree
  and returns plain `OrFilter`s indistinguishable from any other, so a single whole-tree call would
  either drop unmapped columns everywhere (silently widening every explicit path's failure mode,
  which D18 explicitly says should stay a hard fail) or drop nothing (reintroducing the exact
  problem D18 exists to fix). The translator instead expands each `AnyFilter` node exactly where it
  visits one, so the resulting operands are known, at that point in the code, to be
  expansion-derived, and D18's drop-unmapped behavior is applied only there.
- **D30 — The empty-list rejection (D11/D22) keys on the value-shape signal (D28), not on the
  literal operator name `is in`.** D28 gives `OperatorDefinition` a general list-shape signal
  precisely so the core doesn't need to special-case operator names — hardcoding the check to `is
  in` would mean a vendor-registered custom list-shaped operator (e.g. an `is not in`) produces an
  `IN ()`/similar syntax error at execution instead of the clean `SqlRenderException` this plan
  promises, the same class of gap D16 closed for the unknown-operator dispatch case one level down.
  The check becomes: "any operator whose declared shape is `LIST` and whose converted value is an
  empty list is rejected."
- **D31 — `filter-sql-adapter` declares `jackson-databind` as a direct compile dependency in its own
  `pom.xml`, not just relies on filter-core's transitive.** The adapter's entire D7/D19 value
  conversion step operates directly on `com.fasterxml.jackson.databind.JsonNode` — a first-class
  compile dependency of this module, not an incidental one. T3 groups `jackson-databind` with
  `antlr4-runtime` as parent-`<dependencyManagement>`-only specifically so `filter-sql-adapter`
  doesn't silently inherit ANTLR (which it never uses); the same reasoning means it must explicitly
  redeclare `jackson-databind` rather than relying on a transitive that would silently break if
  filter-core's own Jackson scope ever changed.
- **D32 — `ColumnMapping.identity()` returns `Optional.empty()` for any path containing `.`.** D20
  already documents flat/single-table mappings as the adapter's scope limit, but nothing previously
  made that limit fail fast: `identity()` plus D23's mandated quoting turns the DSL's own headline
  example, `lineItems.qty` (the first line of both `README.md` and `docs/filter-dsl.md`, and the
  Javadoc example on `FieldDefinition`/`FieldFilter`), into a single well-formed, injection-safe,
  but nonexistent quoted identifier — `"lineItems.qty"` — which fails only as a vendor-specific
  database error at execution time, not as `SqlRenderException`. Scoped to `identity()` only, not to
  the translator generally: a caller using `ColumnMapping.of(Map)` who has deliberately mapped a
  dotted path to a real (e.g. joined-view) column is unaffected.
- **D33 — The translator dispatches `binary()` vs. `unary()` by `operator.isUnary()`, never by
  `value == null`; the port's Javadoc states that a `null` passed to `binary(...)` means SQL
  `NULL`, not "no value."** `FieldFilter.value` is `null` for a genuinely unary operator, but
  `status is null` parses to a *binary* `FieldFilter("status", "is", NullNode)` — and D7 converts
  `NullNode` to Java `null` — so after conversion the two cases are indistinguishable by value alone.
  A `value == null` dispatch would silently render `status is null` as `exists`, contradicting this
  plan's own Operator-coverage requirement that `is`/`is not` produce `IS NULL`/`IS NOT NULL` for
  null values. Dispatch is by the operator's own `isUnary()` flag, which is unambiguous regardless
  of what the (possibly absent) value converts to.

## Target layout

```
pom.xml                        # org.sequeless:sequeless-filter, packaging=pom, aggregator+parent
filter-core/
  pom.xml                      # org.sequeless:filter-core
  src/main/antlr4/org/sequeless/filter/internal/parser/Filter.g4
  src/main/java/org/sequeless/filter/
      api/FieldRegistry.java             # of(FieldRegistrySpec) restored; permissive()/isPermissive() gone
      api/FieldRegistrySpec.java         # NEW — Lombok @Builder, holds List<FieldDefinition> fields
      api/OperatorDefinition.java        # gains a derived 3-state value-shape signal (D17/D28):
                                          # NONE if isUnary(), LIST if syntax==FUNCTION, else SCALAR
                                          # ("is in" explicitly marked LIST among INFIX operators)
      api/FilterValidator.java           # gains a value-shape check alongside its existing
                                          # applicableTypes/permittedOperators checks (D17)
      internal/DefaultFieldRegistry.java # stays in main; ctor now takes FieldRegistrySpec
      internal/BuiltinOperatorContributor.java # marks "is in" as list-shaped (D17)
      internal/…                        # DefaultOperatorRegistry, CursorAnalyzer,
                                         # FilterBuildingVisitor otherwise unchanged
      spi/…                              # unchanged
  src/test/java/org/sequeless/filter/…                      # existing tests
  src/test/java/org/sequeless/filter/testfixtures/           # NEW, test scope only
      PermissiveFieldRegistry.java    # was internal/PermissiveFieldRegistry — the only fixture left
filter-sql-adapter/
  pom.xml                      # org.sequeless:filter-sql-adapter
  src/main/java/org/sequeless/filter/sql/
      api/SqlFragment.java               # null-tolerant defensive copy of parameters (D21)
      api/ColumnMapping.java             # identity() rejects dotted paths, returns empty (D32)
      api/FilterQueryTranslator.java     # core: validates via FilterValidator first (D27); walks
                                          # FilterNode, converts values (with catch-all, D19),
                                          # expands AnyFilter per-node in visitAny (D29), dropping
                                          # unmapped operands only there (D18), rejects any empty
                                          # and/or at any depth keyed on the shape signal (D22/D30),
                                          # dispatches binary()/unary() by operator.isUnary() (D33),
                                          # delegates to QueryBuilder
      api/SqlRenderException.java
      spi/QueryBuilder.java              # vendor port (binary/unary/and/or); no static factory (D12.3)
      spi/AnsiQueryBuilder.java          # public, Template Method default impl — 8 protected hooks:
                                          # quoteIdentifier (mandated escaping, D23), renderIs,
                                          # renderComparison, renderLike, renderIn, renderExists,
                                          # renderUnknown, unaryUnknown (fallback pair, D16)
      internal/                          # currently unused — reserved, no classes today
  src/test/java/org/sequeless/filter/sql/…                   # H2 QueryBuilder fixture + tests —
                                          # must include at least one deliberate hook override to
                                          # exercise the Template Method extension path, even if H2
                                          # otherwise needs no ANSI deviations
```

The test-fixture package name `org.sequeless.filter.testfixtures` exists purely as a naming
convention now (D15 dropped the ArchUnit rule that would have keyed on it) — kept for readability,
not enforcement.

## Design notes

**filter-core contract change.** `FieldRegistry` keeps `find`/`all`/`compatibleWith`/`of` and loses
`permissive`/`isPermissive`. `of`'s parameter changes from `List<FieldDefinition>` to the new
`FieldRegistrySpec` (Lombok `@Builder`). `DefaultFieldRegistry` stays a production class in
`internal`, backing `of`; only `PermissiveFieldRegistry` moves to test scope. There is no
api→internal ArchUnit rule (D8) — the facade relationship is intentional and unpoliced within
filter-core; cross-module leakage is what the ArchUnit task actually guards.

`isPermissive()` is still removed (verified behavior-preserving: its two call sites collapse
unconditionally to `fields.find(path).isEmpty()`), but per **D1/D10**, no commit implementing this
should be flagged as breaking even though it technically is one.

`FieldContributor`'s Javadoc referencing `FieldRegistry#of(List)` needs updating for the new
signature (`FieldRegistrySpec`), not deletion — `of` still exists, just with a different parameter.
`FieldContributor` itself remains unwired (no production implementor or consumer in this repo, same
as before this plan) — `FieldRegistrySpec` is not being extended to accept `List<FieldContributor>`
in this pass; that stays a known, pre-existing gap, not something this plan closes.

**Value-shape validation (D17/D28).** `OperatorDefinition` gains a *derived* three-state shape
signal — `NONE` for unary operators, `LIST` for `Syntax.FUNCTION` operators (which always carry an
argument-list `ArrayNode` regardless of arity) and for `is in` (the one `INFIX` operator explicitly
marked list-shaped), `SCALAR` for every other `INFIX` operator. A plain boolean defaulting to scalar
was considered and rejected (D28): it would wrongly reject every `FUNCTION`-syntax filter, including
`FunctionArgValidationTest`'s already-shipped custom `withinLast` operator. `FilterValidator` checks
a `FieldFilter`/`AnyFilter`'s value shape (JSON array vs. non-array, or absent for `NONE`) against
its resolved operator's declared shape, alongside its existing `applicableTypes`/`permittedOperators`
checks, and rejects a mismatch the same way it rejects an unknown field today. This benefits every
filter-core consumer, not just filter-sql-adapter — a parsed-then-hand-edited or deserialized
`FilterNode` with `status is in 'open'` (scalar into a list operator) or `status is ['a','b']` (list
into a scalar operator) is now rejected at validation time instead of reaching a downstream consumer
as a malformed value. **Per D27**, `FilterQueryTranslator.translate(...)` calls `FilterValidator`
itself before translating (converting any violation to `SqlRenderException`) — neither
`FilterParser.parse` nor anything else validates automatically, so without this the SQL adapter path
would never actually benefit from this check.

**filter-sql-adapter contracts** (`org.sequeless.filter.sql.api`):

- `SqlFragment(String sql, List<Object> parameters)` — immutable record; `sql` contains `?`
  placeholders positionally matching `parameters`. Per **D21**, the compact constructor defensively
  copies `parameters` with `Collections.unmodifiableList(new ArrayList<>(parameters))`, not
  `List.copyOf` — the list may legitimately contain `null` elements (a `null`-valued `is in` member).
- `ColumnMapping` — `Optional<String> columnFor(String path)`; an empty result on an
  explicitly-named path makes the translator reject the filter with `SqlRenderException`. Ships
  `ColumnMapping.identity()` (trusted-input-only, per D13 — documented, not validated; **per D32**,
  returns `Optional.empty()` for any path containing `.`, so the flat/single-table scope limit
  fails fast as `SqlRenderException` instead of producing a well-formed but nonexistent quoted
  identifier like `"lineItems.qty"`) and `ColumnMapping.of(Map<String,String>)` (an explicit
  allowlist, safe for untrusted paths, and unaffected by D32 — a caller who has deliberately mapped
  a dotted path to a real column is not blocked). Scoped to flat, single-table mappings only — see
  D20/Caveats.
- `FilterQueryTranslator` — the vendor-agnostic core. Entry point:
  `SqlFragment translate(FilterNode node, ColumnMapping columns, FieldRegistry fields, OperatorRegistry ops, QueryBuilder builder)`.
  **Per D27**, first calls `FilterValidator` (using the same `fields`/`ops`) and converts any
  violation to `SqlRenderException` — the translator does not assume its input was validated
  upstream. Then walks the tree via `FilterVisitor<SqlFragment>`; **per D29**, `AnyFilter` expansion
  happens per-node inside the visitor's `visitAny` (calling `AnyFilterExpander`'s default strategy
  there, not once over the whole tree up front), so the resulting operands are known at that point
  to be expansion-derived — this is what lets **D18**'s behavior (drop any operand with no
  `ColumnMapping` entry, rather than hard-failing) apply only to `any`-expansion results, while an
  author-written or deserialized `OrFilter` at the same tree position still hard-fails on an
  unmapped column exactly as before. Resolves each field path through `columns` and converts each
  `JsonNode` value per D7/D19's mapping (including the catch-all for deserialization-only node
  types) before handing it to the builder; dispatches to `builder.binary(...)` vs. `builder.unary(...)`
  by `operator.isUnary()` — **never** by `value == null` (D33), since a null-valued *binary*
  condition (`status is null`, i.e. `NullNode` converted per D7) and a genuinely *unary* condition
  are otherwise indistinguishable after conversion, and dispatching on value would silently turn
  `status is null` into `exists`. Per **D11/D22/D30**, raises `SqlRenderException` for any
  zero-operand `AndFilter`/`OrFilter` encountered at any depth (including an `any`-expansion result
  with nothing left after D18's drop-unmapped step) and for any `LIST`-shaped operator (per D28's
  signal — not hardcoded to the literal name `is in`) whose converted value is an empty list.
- `SqlRenderException extends RuntimeException` — a `FilterValidator` violation caught at the
  translator's entry (D27), unknown column, unmappable operator, empty and/or or empty list-shaped
  value (D11/D22/D30), or an unrecognized `JsonNode` subtype during conversion (D19).

`org.sequeless.filter.sql.spi.QueryBuilder` is the vendor **port** (D6): `binary(String column,
OperatorDefinition operator, Object value)`, `unary(String column, OperatorDefinition operator)`,
`and(List<SqlFragment> operands)`, `or(List<SqlFragment> operands)`. A vendor implementation owns
full construction of each `SqlFragment` from these calls: identifier quoting, operator-to-SQL
translation (including `is in`'s list expansion — the converted `value` arrives as `List<Object>`
for that operator), and `LIKE`/`is like`/`is not like`/`contains`/`starts with` escaping semantics,
which are deliberately unspecified by the plan (D14) — except identifier quoting on the *shipped
default*, which D23 does mandate. `sql.spi.AnsiQueryBuilder` (D12) is the shipped default — public,
non-`final`, structured as a Template Method: `final binary()`/`unary()` dispatchers (dispatch
between them by `operator.isUnary()`, per D33) delegating to exactly **8 `protected` hooks**,
enumerated here rather than left as "etc." (per D16's own requirement that the hook set be a
deliberate, complete commitment, not something that accretes):
`quoteIdentifier(String)` (mandated escaping, D23); `renderIs(String column, boolean negated, Object value)`
(`is`/`is not`, including `IS NULL`/`IS NOT NULL` for a `null` value); `renderComparison(String column, OperatorDefinition operator, Object value)`
(the 4 numeric comparisons); `renderLike(String column, OperatorDefinition operator, Object value)`
(`contains`/`starts with`/`is like`/`is not like` — escaping left to the implementation, D14);
`renderIn(String column, List<Object> values)` (`is in`); `renderExists(String column, boolean negated)`
(the unary `exists`/`does not exist` pair); and the D16 fallback pair,
`renderUnknown(String column, OperatorDefinition operator, Object value)` /
`unaryUnknown(String column, OperatorDefinition operator)`, both throwing `SqlRenderException` by
default. Any vendor — including this repo's own H2 test fixture — can override just one hook instead
of reimplementing the whole dispatch. Unlike `OperatorRegistry.defaults()`/`FieldRegistry.of()`,
`QueryBuilder` itself has **no** static factory referencing `AnsiQueryBuilder` (D12.3) — the port
stays free of any reference to a concrete adapter; callers and vendors instantiate
`new AnsiQueryBuilder()` (or a subclass) directly.

**Operator coverage.** `BuiltinOperatorContributor` ships 13 operators: `is`, `is not`,
`is greater than`, `is greater than or equal to`, `is less than`, `is less than or equal to`,
`contains`, `starts with`, `is like`, `is not like`, `is in`, and the unary `exists` /
`does not exist`. All 13 must translate; `is`/`is not` need `IS NULL`/`IS NOT NULL` handling for null
values, `is in` expands to an `IN (?, ?, …)` list from its converted `List<Object>` value (rejecting
an empty list, checked by the `LIST`-shape signal per D28/D30 rather than the literal name, per
D11/D22), and the unary pair renders to `IS NOT NULL` / `IS NULL` with no bind parameter. Any
operator outside these 13 reaches `AnsiQueryBuilder`'s `renderUnknown`/`unaryUnknown` fallback
(D16), which throws `SqlRenderException` by default.

**Release plumbing.** `.releaserc.json`'s `prepareCmd` becomes
`./mvnw -B versions:set -DnewVersion=${nextRelease.version} -DgenerateBackupPoms=false -DprocessAllModules=true`
and its `@semantic-release/git` assets grow to `["CHANGELOG.md", "pom.xml", "*/pom.xml"]` so the
children's `<parent><version>` bumps are committed. A `${revision}`/flatten CI-friendly-version
setup was considered and rejected: it interacts awkwardly with the existing `versions:set` step for
no benefit while the version is shared.

## Caveats (carried from the brief's Register, plus Stage 4 review findings)

- **H2 version (B3)** could not be pinned from the repo — no database dependency exists today. Pin
  the current H2 2.3.x line (2.3.232 at last check) in the parent's `<dependencyManagement>` with
  `<scope>test</scope>`; `renovate.json` will keep it current. Confirm the exact latest patch at
  implementation time rather than trusting this number.
- **antlr4-maven-plugin in a reactor (B4)** could not be verified pre-restructure. The generated
  package is derived from the grammar's directory path under `src/main/antlr4`, which is preserved
  verbatim by the move, so no output change is expected — but the reactor-conversion task must prove
  it by confirming
  `filter-core/target/generated-sources/antlr4/org/sequeless/filter/internal/parser/` is populated.
- **flatten-maven-plugin on the aggregator (B5/D9/D24).** `flattenMode=ossrh` on a `pom`-packaged
  parent can rewrite the parent POM that children resolve against. `flatten` moves to the parent's
  `<pluginManagement>` with **no `<skip>` element** (D24 — a skip flag in the managed entry would be
  inherited by every child that declares the plugin, silently disabling it everywhere); the
  aggregator simply never declares/binds `flatten` in its own `<build><plugins>`; each child declares
  it normally and gets the managed config. Every other plugin stays in the parent's inherited
  `<build><plugins>` unchanged.
- **JaCoCo/Sonar coverage paths (B5/D25).** `sonar.coverage.jacoco.xmlReportPaths` currently names a
  single `target/site/jacoco/jacoco.xml` in `sonar-project.properties` — but CI invokes the
  Maven-integrated Sonar scanner (`./mvnw -B verify sonar:sonar`), which is documented to read
  configuration from the POM, not from that file. Per D25: verify empirically first (confirm current
  coverage is actually non-zero) before changing anything; if `sonar-project.properties` is indeed
  unread, move the relevant Sonar properties into the parent POM's `<properties>` and retire the
  file, as part of the reactor-tooling task. Do not add a coverage-aggregator module speculatively —
  only if a genuine post-fix zero-coverage symptom appears.
- **Split packages.** Both jars contribute classes under a shared package *prefix*
  (`org.sequeless.filter.*`), not the same package — no class is contributed by both jars, so this
  is not actually a split-package situation, and neither JPMS nor OSGi would reject it as one. No
  `module-info.java` exists anywhere in the repo today; the real work if JPMS/OSGi is ever adopted
  would be adding module descriptors, not renaming packages.
- **Custom `AnyExpansionStrategy` is unreachable through the adapter (D4).** `FilterQueryTranslator`
  always uses `AnyFilterExpander`'s default strategy internally. A caller needing a non-default
  `AnyExpansionStrategy` must pre-expand the filter with their own strategy before calling
  `translate(...)` — the adapter's entry point has no parameter for supplying one.
- **Date-time values pass through as plain strings (D7).** No temporal coercion is performed;
  binding a `date-time`-formatted field's string value against a `TIMESTAMP`/`DATE` column is left
  entirely to the vendor `QueryBuilder`/JDBC driver.
- **`LIKE`/pattern-escaping semantics are vendor-defined, not specified (D14).** Different
  `QueryBuilder` implementations — including the shipped `AnsiQueryBuilder` versus any third-party
  one — may treat `%`/`_` inside `contains`/`starts with`/`is like`/`is not like` values differently.
  (Identifier *quoting* on the shipped default is nonetheless mandated — see D23 — this caveat is
  about matching semantics, a different concern from the injection boundary D23 closes.)
- **Only flat, single-table column mappings are supported (D20/B10).** `ColumnMapping` returns a
  bare column name; there is no way to express a join, correlated subquery, or JSON path expression.
  The DSL's own headline nested-path example (`lineItems.qty > 5`) typically requires a join on a
  relational schema and is out of scope for this adapter as specified. Per **D32**,
  `ColumnMapping.identity()` fails fast on this (`Optional.empty()` for any dotted path) rather than
  producing a syntactically valid but nonexistent quoted identifier; a caller using `of(Map)` can
  still deliberately map a dotted path if they have a real column for it. State this plainly in
  `docs/sql-adapter.md`.
- **`BigIntegerNode`'s catch-all conversion (D19) can still fail at the JDBC driver, not as
  `SqlRenderException`.** D19's numeric catch-all uses `isNumber()`/`numberValue()`, which for a
  `BigIntegerNode` (a JSON integer past `Long.MAX_VALUE`) yields a raw `BigInteger` — a type most
  JDBC drivers' `setObject` rejects. Consider normalizing to `BigDecimal` at implementation time
  (`DecimalNode`, produced when a caller enables `USE_BIG_DECIMAL_FOR_FLOATS`, is unaffected — it
  already binds fine); note the distinction in `docs/sql-adapter.md` regardless of which is chosen.
- **`QueryBuilder`/`FilterQueryTranslator` implementations are expected to be stateless and
  thread-safe.** Not enforced by any type in the plan, but callers will naturally hold one instance
  as a long-lived singleton across request threads; state this expectation in the port's Javadoc.
- **The `JsonNode`→`Object` conversion (D7/D19) still leaves each `QueryBuilder` writing its own
  type dispatch** (`instanceof`/pattern-match over `String`/`Number`/`Boolean`/`null`/`List`) to
  decide scalar binding vs. list expansion vs. null handling — centralizing *conversion* doesn't
  eliminate needing to branch on the *result* type. Accepted as consistent with D3's minimalism
  (no sealed value type introduced for this), just noting the "every vendor gets it for free" framing
  in D7 covers conversion, not dispatch.

## Phases

- Phase 1: Core contract cleanup — T1, T2
- Phase 2: Multi-module reactor — T3, T4
- Phase 3: SQL adapter contracts and translator — T5, T6
- Phase 4: H2 verification and cross-module guards — T7, T8, T9

## Tasks

- [ ] T1: (Phase 1) Add `FieldRegistrySpec` (Lombok `@Builder`, `List<FieldDefinition> fields`) to
      `org.sequeless.filter.api`; change `DefaultFieldRegistry`'s constructor and
      `FieldRegistry.of(...)` to accept `FieldRegistrySpec` instead of `List<FieldDefinition>`
      (`DefaultFieldRegistry` itself stays a plain constructor, no builder needed); delete
      `FieldRegistry.permissive()` and `FieldRegistry.isPermissive()`; move `PermissiveFieldRegistry`
      out of `internal` into test scope as `org.sequeless.filter.testfixtures.PermissiveFieldRegistry`;
      collapse `FilterBuildingVisitor.resolvePath` (line 156) and `FilterValidator.validateField`
      (line 42) to an unconditional `fields.find(path).isEmpty()` check; update call sites in
      `FilterParserTest`, `FilterSerializerTest`, `NormalizationTest`, `FunctionArgValidationTest`,
      `FilterSchemaProviderTest`, `AnyFilterExpanderTest`, `CompletionEngineTest` — `FieldRegistry.of(List.of(...))`
      calls become `FieldRegistry.of(FieldRegistrySpec.builder().fields(List.of(...)).build())`, the
      handful using `FieldRegistry.permissive()` switch to the new test fixture; update
      `FieldContributor`'s Javadoc for the new `of` signature. No new ArchUnit rule is added for this
      change (D8 dropped the api→internal rule; D15 dropped the fixture-leak rule) — `BoundaryRulesTest`
      is expected to keep passing unmodified. One atomic commit, plain `fix:` type — per D1/D10, do
      not mark it a breaking change even though `isPermissive()` removal technically is one. `make
      verify` stays green.
- [ ] T2: (Phase 1) Give `OperatorDefinition` a **derived**, three-state value-shape signal (per
      **D17/D28**) — `NONE` when `isUnary()`, `LIST` when `getSyntax() == Syntax.FUNCTION`, `SCALAR`
      otherwise — and mark `is in` as the one `LIST`-shaped exception among the `INFIX` built-ins in
      `BuiltinOperatorContributor`. A plain scalar-default boolean is explicitly wrong here: it would
      reject every `FUNCTION`-syntax filter, including `FunctionArgValidationTest`'s existing custom
      `withinLast` operator. Extend `FilterValidator` to reject a `FieldFilter`/`AnyFilter` whose
      value shape (absent for `NONE`, JSON array for `LIST`, non-array for `SCALAR`) doesn't match
      its resolved operator's declared shape, alongside the existing
      `applicableTypes`/`permittedOperators` checks. This is a filter-core improvement independently
      motivated by reviewing the SQL adapter's needs — benefits every consumer of `FilterValidator`,
      not just filter-sql-adapter (which additionally calls it directly per D27/T6, since nothing
      calls `FilterValidator` automatically). Separate commit from T1 (different concern, though both
      touch validator-adjacent code).
- [ ] T3: (Phase 2) Convert the root `pom.xml` to `<packaging>pom</packaging>` with
      `<modules><module>filter-core</module></modules>`, hoisting the current plain `<dependencies>`
      (jackson-databind, antlr4-runtime, lombok, junit-jupiter, assertj-core, mockito-core,
      archunit-junit5 — there is no `<dependencyManagement>` block today, so this is introducing one,
      not hoisting an existing one) into the parent's `<dependencyManagement>`. Resolved split:
      `jackson-databind` and `antlr4-runtime` are `<dependencyManagement>`-only at the parent (so
      `filter-sql-adapter` doesn't silently inherit an ANTLR runtime dependency it never uses, and
      per **D31** must redeclare `jackson-databind` itself in T5 rather than relying on a
      transitive); `lombok`/`junit-jupiter`/`assertj-core`/`mockito-core`/`archunit-junit5` are
      wanted as real dependencies in both modules and stay as plain, inherited `<dependencies>` on
      the parent directly (simplest — neither child needs to redeclare these). `filter-core/pom.xml`
      redeclares `jackson-databind` and `antlr4-runtime` directly (both are first-class compile
      dependencies of the DSL/parser). Per **D9/D24**, leave `compiler`, `antlr4`, `spotless`, `jacoco`, `surefire`,
      `failsafe`, `enforcer` in the parent's `<build><plugins>` exactly as today (inherited
      automatically), and move only `flatten` to `<pluginManagement>` with **no `<skip>` element**
      — the aggregator simply doesn't declare/bind `flatten` in its own `<build><plugins>`; create
      `filter-core/pom.xml` (`org.sequeless:filter-core`, no `<version>`) re-declaring `flatten` in
      its own `<build><plugins>`; `git mv` `src/` and the ANTLR grammar under `filter-core/`. Verify
      `filter-core/target/generated-sources/antlr4/org/sequeless/filter/internal/parser/` is
      generated and all existing tests still run.
- [ ] T4: (Phase 2) Reactor-proof the surrounding tooling: `.releaserc.json` (`-DprocessAllModules=true`,
      git assets `["CHANGELOG.md", "pom.xml", "*/pom.xml"]`), `README.md` dependency coordinates
      (`filter-core`, and a short module table), a `make verify` smoke check, and an ADR recording the
      split via `make new-adr`. Per **D25**: before touching Sonar config, verify empirically whether
      `sonar-project.properties` is actually read by CI's `sonar:sonar` Maven-plugin invocation
      (check current/pre-split coverage reporting). Only if confirmed unread, move
      `sonar.projectKey`/`sonar.organization`/exclusions/`sonar.coverage.jacoco.xmlReportPaths` into
      the parent POM's `<properties>` and retire the file — do not change it speculatively.
- [ ] T5: (Phase 3) Add `filter-sql-adapter` to `<modules>` with `org.sequeless:filter-sql-adapter`
      depending on `org.sequeless:filter-core:${project.version}`, re-declaring `flatten` in its own
      `<build><plugins>` per D9/D24/T3; per **D31**, also directly declare `jackson-databind` (its
      value-conversion step is a first-class `JsonNode` consumer, not just a transitive rider on
      filter-core); define the public contracts: `sql.api.SqlFragment`
      (null-tolerant parameter copy per D21), `sql.api.ColumnMapping` (+`identity()` documented
      trusted-input-only per D13 and rejecting dotted paths per D32, `of(Map)`; document the
      flat/single-table scope limit per D20),
      `sql.api.FilterQueryTranslator`, `sql.api.SqlRenderException`, and the vendor port
      `sql.spi.QueryBuilder` with its four methods (`binary`/`unary`/`and`/`or`, D6). State the
      expected statelessness/thread-safety of implementations in the port's Javadoc. Contracts +
      Javadoc only; no translation logic yet.
- [ ] T6: (Phase 3) Implement `sql.spi.AnsiQueryBuilder` (public, non-`final`, Template Method:
      `final` `binary()`/`unary()` dispatchers delegating to exactly 8 `protected` hooks —
      `quoteIdentifier` (mandated `"…"`-with-doubled-quotes escaping, security-relevant per D23),
      `renderIs`, `renderComparison`, `renderLike`, `renderIn`, `renderExists`, and the
      `renderUnknown`/`unaryUnknown` fallback pair for non-built-in operators, throwing
      `SqlRenderException` by default (D16) — this is the complete, fixed hook set, not a starting
      point to extend ad hoc) and `FilterQueryTranslator`'s tree walk as a `FilterVisitor<SqlFragment>`:
      **first** call `FilterValidator` (per **D27**) with the same `fields`/`ops` and convert any
      violation to `SqlRenderException` — `translate(...)` does not assume its `FilterNode` was
      validated upstream; convert each `JsonNode` value per D7/D19's mapping including the catch-all
      for unrecognized subtypes; expand `AnyFilter` **per-node inside `visitAny`** (per **D29** — not
      one whole-tree `AnyFilterExpander.expand` call up front), using the default strategy, dropping
      any expanded operand with no `ColumnMapping` entry (D18) and raising `SqlRenderException` only
      if none survive — an author-written or deserialized `OrFilter` at any other tree position still
      hard-fails on an unmapped column, exactly as before; dispatch `binary()` vs. `unary()` by
      `operator.isUnary()`, **never** by `value == null` (D33 — `status is null` is a binary
      condition whose converted value happens to be Java `null`, and must render `IS NULL`, not be
      mistaken for `exists`); reject any zero-operand `AndFilter`/`OrFilter` encountered at any depth,
      and any operator whose declared shape (per D28's signal, not the literal name `is in`) is
      `LIST` and whose converted value is an empty list (D11/D22/D30); all 13 built-in operators
      including `IS NULL`/`IS NOT NULL` for null-valued `is`/`is not`, `IN (?, …)` expansion for
      `is in`, the unary `exists`/`does not exist` pair, AND/OR grouping with parentheses;
      `ColumnMapping` failures on an explicitly-named path raising `SqlRenderException`; unit tests
      asserting emitted SQL text and bind-parameter order, with ASTs built directly via `Filters` and
      fields via `FieldRegistry.of(FieldRegistrySpec...)`, including at least one custom operator
      exercising the `renderUnknown` fallback path and at least one `status is null` case proving the
      dispatch-by-`isUnary()` rule (D33) renders `IS NULL`, not `exists`.
- [ ] T7: (Phase 4) Add H2 (test scope) plus a test-scope `QueryBuilder` fixture for H2 — extend
      `sql.spi.AnsiQueryBuilder`, and deliberately override **at least one** `protected` hook (even
      if H2 needs no real ANSI deviation, override something — e.g. a distinct quoting or escaping
      choice — solely to exercise the Template Method extension path with real test coverage, per a
      review finding that otherwise this mechanism could ship completely untested); build a
      `FieldRegistry` the same way any production caller would
      (`FieldRegistry.of(FieldRegistrySpec...)`, no separate test-only registry fixture needed);
      create an H2 schema + seed data, then execute translated `SqlFragment`s as real
      `PreparedStatement`s and assert the returned rows — covering each operator family end to end,
      including at least one filter using `any` (with a deliberately partial `ColumnMapping`, to
      exercise D18's drop-unmapped-operands behavior) and at least one `is in` containing a `null`
      element (to exercise D21's null-tolerant parameter list). Name these tests `*Test` (surefire),
      not `*IT` — the reactor's JaCoCo setup has no `report-integration` execution, so `*IT`-named
      tests would run but their coverage wouldn't reach the Sonar report.
- [ ] T8: (Phase 4) Add `filter-sql-adapter`'s ArchUnit test carrying the module-local rule
      (`sql.api`/`sql.spi` types are public) and the cross-module rules that only this module's
      classpath can see: no main-scope class in `org.sequeless.filter.sql..` may depend on
      `org.sequeless.filter.internal..` or `org.sequeless.filter.spi..`; and no filter-core class may
      depend on `org.sequeless.filter.sql..` (kept per **D26** as documentation of intent even though
      it's currently unfalsifiable — Maven's reactor structure already rejects the cycle). Per
      **D15**, do not add a `testfixtures`-leak rule here — it can never trip (no test-jar dependency
      exists between the modules) and would be unenforceable documentation, not a real guard. Confirm
      the rule that *can* meaningfully fail (`sql..` → `internal..`/`spi..`) does so on a deliberate
      violation before reverting it.
- [ ] T9: (Phase 4) Document the adapter: a `docs/sql-adapter.md` covering the
      parse → validate (`FilterValidator`, called internally by `translate(...)` — D27) →
      `FilterQueryTranslator` (which expands `any` per-node internally, using the default expansion
      strategy — D4/D29 — and drops unmapped operands rather than failing, D18) → `ColumnMapping`
      (flat/single-table scope limit and dotted-path rejection in `identity()`, D20/D32) →
      `QueryBuilder` → `PreparedStatement` pipeline, the `JsonNode`→Java value-conversion table
      (D7/D19, including the `BigInteger`-binding caveat) and its date-time non-goal, `LIKE`-escaping
      being vendor-defined while identifier quoting on the shipped default is not (D14 vs. D23), and
      how to write a vendor `QueryBuilder` — implement the interface from scratch, or extend
      `AnsiQueryBuilder` and override just the `protected` hooks that differ (the 8-hook set, D16 —
      including `renderUnknown`/`unaryUnknown` for custom operators) — (no `QueryBuilder.ansi()`
      factory exists; instantiate `AnsiQueryBuilder`/a subclass directly, D12) — linked from
      `README.md` and `docs/filter-dsl.md`.
