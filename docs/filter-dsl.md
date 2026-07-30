# Filter DSL

`sequeless-filter` is a standalone (no Spring) human-facing query language: a text DSL
that parses to a `FilterNode` AST, plus a serializer that renders an AST back to DSL text
and completion utilities for building an interactive query UI. It has no runtime dependency
on any storage or query engine — a consumer resolves the parsed AST against whatever backend
it owns.

This page covers the DSL's syntax, its operator set, how contributors add fields and
operators, its round-trip serialization guarantee, and its completion behavior.

## Syntax

```
lineItems.qty > 5 and status is 'shipped'
any contains 'urgent'
createdAt meets between('2024-01-01', '2024-02-01')
deletedAt does not exist
status is in ['open', 'pending'] and (region is 'us' or region is 'eu')
```

- **Path** — dot-notation, e.g. `lineItems.qty`.
- **Infix condition** — `path op value`, e.g. `qty > 5`. Unary operators omit the value: `deletedAt
  exists`.
- **Function condition** — `path meets fnName(arg1, arg2)` for operators with more than a value (e.g. a
  range). `meets` is a fixed keyword; the function name and its arguments are operator-specific
  (`ParameterDefinition`s on the `OperatorDefinition`).
- **`any`** — a wildcard target in place of a path: `any op value` matches if *some* field compatible with
  `op` matches. Expanded to a concrete `OrFilter` over compatible fields by `AnyFilterExpander` (a custom
  `AnyExpansionStrategy` can change what "compatible" expansion means).
- **Boolean composition** — `and`/`or` (case-insensitive), left-to-right within each precedence level;
  `and` binds tighter than `or`. Parenthesize to override.
- **Values** — string (`'single'` or `"double"` quoted; doubled-quote escaping, e.g. `'it''s fine'` — no
  backslash escapes), number, `true`/`false`/`null`, or a bracketed list `['a', 'b']`.

The grammar itself never needs to change when operators are added or removed — `opPhrase` matches any
word/symbol run and is resolved dynamically against the `OperatorRegistry` by the parsing visitor.

## Operators

The built-in set (`BuiltinOperatorContributor`), each with its applicable JSON Schema type(s) (empty =
all types):

| Canonical | Aliases | Applies to | Arity |
| --- | --- | --- | --- |
| `is` | `=`, `equals`, `is equal to`, `equal to`, `is null`, `is not null` | all | binary |
| `is not` | `!=`, `not equals`, `is not equal to` | all | binary |
| `is greater than` | `>`, `gt` | number/integer | binary |
| `is greater than or equal to` | `>=`, `gte` | number/integer | binary |
| `is less than` | `<`, `lt` | number/integer | binary |
| `is less than or equal to` | `<=`, `lte` | number/integer | binary |
| `contains` | — | string | binary |
| `starts with` | `starts_with` | string | binary |
| `is like` | `like` | string | binary |
| `is not like` | `not like` | string | binary |
| `is in` | `in` | all | binary (list value) |
| `exists` | — | all | unary |
| `does not exist` | `not exists` | all | unary |

A `FieldContributor`/`OperatorContributor` embedder can add both custom fields and custom operators
(including `Syntax.FUNCTION` ones with typed `ParameterDefinition`s, e.g. an ENUM parameter whose allowed
values render as bare words rather than quoted strings). The parser and serializer need no changes for a
new operator — only a registry entry.

## Round-trip serialization

`FilterSerializer.serialize(node, ops)` renders an AST back to DSL text with a round-trip guarantee:
`parse(serialize(ast, ops), ops, fields).equals(ast)`. Canonical rendering rules: strings single-quoted,
numbers/booleans/null unquoted, lists as `['a', 'b']`, unary operators without a value, function calls as
`field meets fnName(args)`, and an operator whose *canonical* name itself contains a grammar keyword
(e.g. `is greater than or equal to` contains `or`) is rendered using the first DSL-safe alias instead —
which is why the two-argument overload (with the `OperatorRegistry`) is the one to prefer; the
single-argument `serialize(node)` skips that substitution and can produce output the parser rejects.

## Completion behavior

`FilterParser.parsePartial(input, cursorOffset, ops, fields)` never throws: it returns a `ParseResult`
that is either `Complete` (with the full AST) or `Partial` (best-effort AST, if any, plus a
`CompletionHint`). The hint names a `CursorPosition` — `FIELD`, `OPERATOR`, `FUNCTION_NAME`,
`FUNCTION_ARG`, `VALUE`, or `BOOLEAN_OP` — plus, where relevant, the field path, resolved operator, and
function-argument index the cursor sits in.

`CompletionEngine.complete(input, cursorOffset)` turns that hint into candidate strings for a UI:

- `FIELD` → every registered field path, plus `any`.
- `OPERATOR` → every infix operator (canonical name + aliases) applicable to the field's type/format,
  plus `meets`.
- `FUNCTION_NAME` → every function-syntax operator applicable to the field's type/format.
- `FUNCTION_ARG` → the parameter's allowed values if `ENUM`-typed, else the field's `CompletionProvider`
  (if one is registered) for value suggestions.
- `VALUE` → the field's `CompletionProvider`, if any.
- `BOOLEAN_OP` → `and`, `or`.

A field's `CompletionProvider` is optional and embedder-supplied (e.g. backing a `status` field's value
completion with the live set of distinct values) — fields without one simply offer no value candidates.

## See also

- [SQL adapter](sql-adapter.md) — translating a parsed `FilterNode` AST into parameterized SQL
  against a relational database, via `filter-sql-adapter`.
