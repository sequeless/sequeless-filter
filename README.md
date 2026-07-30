# sequeless-filter

**sequeless-filter** is a standalone human-facing query language: a text DSL
that parses to a `FilterNode` AST, a serializer that renders an AST back to canonical DSL
text with a round-trip guarantee, pluggable operator and field registries, and completion
utilities for building an interactive query UI. It has no runtime dependency on any storage
or query engine — a consumer resolves the parsed AST against whatever backend it owns.

```
lineItems.qty > 5 and status is 'shipped'
any contains 'urgent'
createdAt meets between('2024-01-01', '2024-02-01')
status is in ['open', 'pending'] and (region is 'us' or region is 'eu')
```

See [docs/filter-dsl.md](docs/filter-dsl.md) for the full grammar, operator set, serialization
rules, and completion behavior.

## Add it as a dependency

Requires JDK 21.

```xml
<dependency>
  <groupId>org.sequeless</groupId>
  <artifactId>filter-core</artifactId>
  <version>0.0.0-SNAPSHOT</version>
</dependency>
```

## Modules

| Module | Coordinates | Contents |
| --- | --- | --- |
| [`filter-core`](filter-core) | `org.sequeless:filter-core` | The DSL: AST model, ANTLR 4 grammar and parser, serializer, operator and field registries, completion utilities. |

The repository root is a `pom`-packaged aggregator/parent (`org.sequeless:sequeless-filter`) that
ships no code — it holds the single shared version, dependency management, and build plugins. All
modules are released together from that one version.

## Build

```bash
make verify    # full build + tests + quality gates (Spotless, Enforcer, ArchUnit)
make test      # fast unit tests only
make fmt       # apply formatting (Spotless / Palantir Java Format)
```

`make verify` wraps `./mvnw -B verify`; the Maven wrapper is committed, so no local Maven
install is required.

## Documentation

- [Filter DSL reference](docs/filter-dsl.md) — grammar, operators, serialization, completion.
- Architecture decisions live under [`docs/adr/`](docs/adr/).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[Apache License 2.0](LICENSE).
