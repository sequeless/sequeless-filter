# SQL adapter

`filter-sql-adapter` (`org.sequeless:filter-sql-adapter`) translates a parsed `FilterNode` AST into
raw, parameterized SQL — a `String` with `?` placeholders plus an ordered bind-parameter list, the
classic JDBC `PreparedStatement` shape. No database driver dependency, no ORM/Criteria object graph:
you get SQL text and values back, and you run them yourself.

**On this page:** the [translation pipeline](#the-pipeline), the
[value-conversion rules](#value-conversion-jsonnode--java), the
[`LIKE` escaping vs. identifier quoting](#like-escaping-vs-identifier-quoting) distinction,
[writing a vendor `QueryBuilder`](#writing-a-vendor-querybuilder), and
[known limitations](#known-limitations).

See [docs/filter-dsl.md](filter-dsl.md) for the DSL itself (grammar, operators, parsing).

## The pipeline

`FilterQueryTranslator` (`org.sequeless.filter.sql.api`) is the single vendor-agnostic entry point;
`DefaultFilterQueryTranslator` is its (and currently only) implementation.

```
DSL text
  │ FilterParser.parse
  ▼
FilterNode ──► FilterQueryTranslator.translate(node, columns, fields, ops, builder)
                 1. validate       FilterValidator.validate(node, ops, fields) — internal, not opt-in
                 2. walk the tree, expanding `any` per-node via AnyFilterExpander's default strategy
                 3. resolve        each path via ColumnMapping
                 4. convert        each JsonNode value to a plain Java value
                 5. delegate       rendering to QueryBuilder
                 ▼
               SqlFragment(sql, parameters)
                 ▼
               PreparedStatement + positional setObject(...)
```

Four behaviors worth calling out explicitly:

**Validation is internal, not opt-in.** Nothing else in this library calls `FilterValidator`
automatically — `FilterParser.parse` does not validate. `translate(...)` calls
`FilterValidator.validate(node, ops, fields)` itself, first, and converts any reported violation to
`SqlRenderException`. You never need a separate validation step before calling it, even against an
unvalidated, hand-built, or deserialized `FilterNode`.

**`any` is expanded internally, per-node, using the default expansion strategy.** As the walker
visits an `AnyFilter` node, it calls `AnyFilterExpander`'s default strategy right there — not once
over the whole tree up front — and continues walking the resulting operands. An operand whose path
has no `ColumnMapping` entry is silently dropped rather than failing the whole filter;
`SqlRenderException` is raised only if *no* expanded operand survives. This leniency is scoped to
`any`-expansion results only — an explicitly-named path anywhere else in the tree still hard-fails on
an unmapped column. There's no way to supply a *custom* `AnyExpansionStrategy` through
`translate(...)` — see [Known limitations](#known-limitations).

**`ColumnMapping` resolves each path to a bare column name**, and (per its `identity()` factory)
rejects dotted paths — see [Known limitations](#known-limitations).

**`QueryBuilder` owns all SQL construction** — identifier quoting, operator-to-SQL translation, value
binding, `AND`/`OR` combination. The translator never renders SQL text itself; it only walks,
validates, resolves, and converts, then delegates.

### Usage example

```java
import org.sequeless.filter.api.*;
import org.sequeless.filter.sql.api.*;
import org.sequeless.filter.sql.spi.AnsiQueryBuilder;
import org.sequeless.filter.sql.spi.QueryBuilder;

// 1. Registries — built the same way any production caller would.
FieldRegistry fields = FieldRegistry.of(FieldRegistrySpec.builder()
        .fields(List.of(
                FieldDefinition.builder().path("status").jsonSchemaType("string").build(),
                FieldDefinition.builder().path("age").jsonSchemaType("number").build()))
        .build());
OperatorRegistry ops = OperatorRegistry.defaults();

// 2. A caller-supplied path-to-column mapping (D20/B10: flat, single-table only).
ColumnMapping columns = ColumnMapping.of(Map.of(
        "status", "status",
        "age", "age"));

// 3. The vendor QueryBuilder — the shipped ANSI default, or a subclass for your dialect.
QueryBuilder builder = new AnsiQueryBuilder();

// 4. Parse and translate.
FilterNode node = FilterParser.parse("status is 'open' and age is greater than 30", ops, fields);
FilterQueryTranslator translator = new DefaultFilterQueryTranslator();
SqlFragment fragment = translator.translate(node, columns, fields, ops, builder);

// 5. Run it as a real PreparedStatement.
String sql = "SELECT * FROM products WHERE " + fragment.sql();
try (PreparedStatement stmt = connection.prepareStatement(sql)) {
    List<Object> parameters = fragment.parameters();
    for (int i = 0; i < parameters.size(); i++) {
        stmt.setObject(i + 1, parameters.get(i));
    }
    try (ResultSet rs = stmt.executeQuery()) {
        // ...
    }
}
```

`fragment.sql()` is a bare `WHERE`-clause fragment (e.g. `("status" = ? AND "age" > ?)`) — it's your
responsibility to embed it in a full statement (`SELECT ... WHERE <fragment>`, or combined with
other application-level conditions).

## Value conversion (`JsonNode` → Java)

`FieldFilter`/`AnyFilter` values arrive as Jackson `JsonNode`s (a parsed filter's literals, or
whatever a deserialized `FilterNode` happens to carry). The translator converts each value to a
plain Java object *before* calling `QueryBuilder`, so every vendor gets one consistent conversion
for free:

| `JsonNode` type | Java value |
| --- | --- |
| `TextNode` | `String` |
| `IntNode` / `LongNode` / `DoubleNode` | the matching boxed `Number` (`Integer`/`Long`/`Double`) |
| `BooleanNode` | `Boolean` |
| `NullNode` | `null` |
| `ArrayNode` | `List<Object>`, each element converted recursively |
| any other numeric node (e.g. `BigIntegerNode`, `DecimalNode`) | via `isNumber()`/`numberValue()` |
| anything else (`ObjectNode`, `BinaryNode`, `POJONode`, `MissingNode`, ...) | `SqlRenderException` |

Two caveats worth calling out explicitly:

- **No temporal coercion.** There's no date/time literal in the grammar, so a field with
  `jsonSchemaFormat: "date-time"` still arrives as a plain `TextNode` and converts to a plain
  `String`. Binding that string against a `TIMESTAMP`/`DATE` column is left entirely to the vendor
  `QueryBuilder`/JDBC driver — this adapter performs no parsing or coercion of date-time values.
- **`BigInteger` binding caveat.** A `BigIntegerNode` (a JSON integer past `Long.MAX_VALUE`, which
  can only arise from a deserialized `FilterNode`, not the parser) converts via the numeric
  catch-all to a raw `BigInteger`. Most JDBC drivers' `PreparedStatement.setObject` don't accept a
  `BigInteger` directly and will throw at execution time. If your values may exceed
  `Long.MAX_VALUE`, be prepared to handle this at the JDBC layer (e.g. converting to `BigDecimal`
  or a `String` column yourself).

A `null` Java value passed to `QueryBuilder.binary(...)` means SQL `NULL` — e.g. from
`status is null`, a `NullNode` converted to `null` — it does **not** mean "no value." Dispatch
between `binary(...)` and `unary(...)` is always by the operator's own `isUnary()` flag, never by
whether the converted value is `null`, so `status is null` reliably renders `IS NULL` rather than
being mistaken for a unary condition like `exists`.

## `LIKE` escaping vs. identifier quoting

These look like similar "vendor gets to decide the details" questions, but they aren't the same
kind of decision.

| | `LIKE`-family escaping | Identifier quoting |
| --- | --- | --- |
| Covers | `contains`, `starts with`, `is like`, `is not like` | Every column name the translator emits |
| Shipped default (`AnsiQueryBuilder`) | **No escaping.** `contains` → `%value%`, `starts with` → `value%`, `is like`/`is not like` use the value as-is (caller supplies wildcards) | `quoteIdentifier` emits `"…"` with any embedded `"` doubled |
| Vendor-overridable? | Yes, freely — a matching-semantics choice, not a security boundary | Yes, but must preserve an equivalent injection-safe guarantee |
| Why it's not fixed cross-vendor | Whether `%`/`_` inside a value is escaped changes literal-vs-wildcard matching; no cross-vendor test asserts one outcome | N/A — this one is fixed on the shipped default |

Identifier quoting on the shipped default is mandated because it's the one thing standing between a
caller-supplied column name and SQL injection — specifically when a `ColumnMapping` (especially
`identity()`, which performs no validation of its own) hands the translator a path that came from
programmatic or deserialized input rather than the grammar's own restricted identifier token
(`[a-zA-Z_][a-zA-Z_0-9]*`). A subclass overriding `quoteIdentifier` must preserve that guarantee for
its target dialect's quoting syntax — it isn't free to change for cosmetic reasons the way `LIKE`
escaping is.

## Writing a vendor `QueryBuilder`

There are two ways to supply your own SQL rendering:

1. **Implement `org.sequeless.filter.sql.spi.QueryBuilder` from scratch.** Four methods:
   `binary(String column, OperatorDefinition operator, Object value)`,
   `unary(String column, OperatorDefinition operator)`, `and(List<SqlFragment> operands)`,
   `or(List<SqlFragment> operands)`. You own every rendering decision — quoting, operator syntax,
   value binding, combination.
2. **Extend `org.sequeless.filter.sql.spi.AnsiQueryBuilder`** and override only the `protected` hooks
   that differ for your dialect. `AnsiQueryBuilder` is a public, non-`final` Template Method: its
   `binary()`/`unary()` are `final` dispatchers that delegate to exactly these 8 `protected` hooks
   (the complete, fixed set — not a starting point meant to accrete further overloads):

   | Hook | Covers |
   | --- | --- |
   | `quoteIdentifier(String column)` | Identifier quoting (mandated escaping contract, see above) |
   | `renderIs(String column, boolean negated, Object value)` | `is` / `is not`, including `IS NULL`/`IS NOT NULL` |
   | `renderComparison(String column, OperatorDefinition operator, Object value)` | The 4 numeric comparisons |
   | `renderLike(String column, OperatorDefinition operator, Object value)` | `contains` / `starts with` / `is like` / `is not like` |
   | `renderIn(String column, List<Object> values)` | `is in` |
   | `renderExists(String column, boolean negated)` | The unary `exists` / `does not exist` pair |
   | `renderUnknown(String column, OperatorDefinition operator, Object value)` | Fallback for any binary operator outside the 13 built-ins |
   | `unaryUnknown(String column, OperatorDefinition operator)` | Fallback twin of `renderUnknown` for unary operators |

   A vendor that only needs to change one concern — say, `LIKE` escaping, or supporting a custom
   operator registered via `OperatorRegistry.of(List<OperatorContributor>)` — overrides just that
   hook, instead of reimplementing the whole 13-operator dispatch. `renderUnknown`/`unaryUnknown`
   throw `SqlRenderException` by default; override them to support operators outside the built-in
   set (the H2 test fixture in this repo, `H2QueryBuilder`, demonstrates overriding a single hook —
   `quoteIdentifier` — purely to exercise the extension path).

There is **no `QueryBuilder.ansi()` static factory** — instantiate `AnsiQueryBuilder` (or your
subclass) directly with `new AnsiQueryBuilder()`. This was a deliberate choice: the port interface
stays free of any compile-time reference to a specific concrete adapter.

## Known limitations

| Limitation | Why / workaround |
| --- | --- |
| **Flat, single-table column mappings only.** No join, correlated subquery, or JSON path expression. | `ColumnMapping` returns a bare column name and `SqlFragment` is a single `WHERE`-clause fragment plus binds. Matters because the DSL's headline example, `lineItems.qty > 5`, typically means a join on a relational schema, not a column. If you have a real column backing a dotted path (e.g. a joined view), map it explicitly with `ColumnMapping.of(Map)`. |
| **`ColumnMapping.identity()` rejects dotted paths** — returns `Optional.empty()`, which becomes `SqlRenderException`. | Fails fast at translation time instead of producing a valid-looking but nonexistent quoted identifier (`"lineItems.qty"`) that would only fail later as a vendor-specific "column does not exist" error. Scoped to `identity()` only — `ColumnMapping.of(Map)` can map a dotted path deliberately. `identity()` is also trusted-input-only (no other validation); use an explicit `of(Map)` allowlist for untrusted/deserialized paths. |
| **A custom `AnyExpansionStrategy` is unreachable through this adapter.** | `translate(...)` always uses `AnyFilterExpander`'s default strategy internally, with no parameter to override it. Pre-expand the filter yourself with your own strategy before calling `translate(...)` if you need different semantics. |
| **`LIKE`-family escaping is vendor-defined**, not a fixed cross-vendor contract. | See [`LIKE` escaping vs. identifier quoting](#like-escaping-vs-identifier-quoting). |
| **Date-time values pass through as plain strings**, no temporal coercion. | See [Value conversion](#value-conversion-jsonnode--java). |
| **`BigInteger` values may not bind via `PreparedStatement.setObject`** on most JDBC drivers. | See [Value conversion](#value-conversion-jsonnode--java). |
| **`QueryBuilder`/`FilterQueryTranslator` implementations are expected to be stateless and thread-safe.** | Not enforced by any type — callers will naturally hold one long-lived singleton instance shared across request threads. `DefaultFilterQueryTranslator` and `AnsiQueryBuilder` both hold no mutable state; a subclass should preserve that. |
