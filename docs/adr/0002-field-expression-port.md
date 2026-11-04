# 0002. A filter field can resolve to a trusted SQL expression

- Status: accepted
- Date: 2026-11-04

## Context

`filter-sql-adapter` renders a filter path to a single column name, and `AnsiQueryBuilder` quotes that name internally. `ColumnMapping.columnFor(path)` returns a bare `String`, so a field cannot be a computed expression. The JSONB store in the prototype (`PostgresQueryStore`) needs one: a property lives in a JSONB column, and it is read with a cast chosen by its datatype. Decision #21 asks how a field expression should carry its parameters, how the hooks should take it, whether the change is a major or minor bump, and where the Postgres builder lives. Decision #15 and #16 set the JSONB centre as the consumer.

## Decision

1. **A field expression is a SqlFragment-shaped record**: trusted SQL text plus its own parameter list. The builder splices its text where the quoted column would go, and its parameters go before the operator's. `ColumnMapping` is the trivial implementation: a quoted identifier with no parameters.
2. **The hooks take a sealed `Field`**: `QueryBuilder.binary` and `unary` take `Field` (a `Column` or an `Expression`). `ColumnMapping` extends `FieldResolver`, whose default `resolve` wraps `columnFor` in a `Column`. The hooks render a rendered operand (`SqlExpression`).
3. **No per-field value converter in this port.** The translator keeps one generic converter. A field's typing is carried by the `Field`. This deviates from #21, which asks for "a per-field value converter". It can be added later as an additive change.
4. **The Postgres builder lives in its own module**, `filter-sql-postgres-adapter`. It overrides only the LIKE hook, to `ILIKE`, and `filter-sql-testkit` pins the operator semantics for every dialect.

## Consequences

- **Breaking for `QueryBuilder` implementers and `AnsiQueryBuilder` subclasses.** `QueryBuilder.binary` and `unary` take `Field`, and the protected hooks take `SqlExpression`. An external subclass must update its overrides, and an override without `@Override` silently stops being called. In this repo, the anonymous subclass in `DefaultFilterQueryTranslatorTest` (line 330) is updated. `H2QueryBuilder` overrides only `quoteIdentifier(String)`, which keeps its signature, so it is unaffected. The filter is pre-1.0 and nothing is in production, so this is accepted; the owner's version policy records it as a patch.
- **NULL rules (owner decision, 2026-11-04):** each negated operator is the exact complement of its positive twin. `is not` and `is not like` include NULL rows, and `is in` with a null member also matches `IS NULL`. A null filter value is rejected for comparisons and the LIKE family, and it means SQL NULL only for `is` and `is not`. See spec section 3.3 for the table.
- `ColumnMapping` callers are unchanged, because `ColumnMapping` is a `FieldResolver`.
- `is not` becomes null-safe (`IS DISTINCT FROM`), and the LIKE family becomes case-insensitive and escapes literal wildcards. Existing filters that relied on `<>` or case-sensitive matching change behaviour. The change is recorded in the release notes.
- The JSONB centre can later render its property-IRI bind before the value bind, because the parameter order is decided by the builder, not by the translator.
- Deferred: a per-field converter. Add it, with an ADR amendment, when a field needs one.
