package org.sequeless.filter.sql.api;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves a filter's dot-notation field path to a SQL column name.
 *
 * <p><strong>Scope limit:</strong> this contract supports only flat, single-table column
 * mappings — a {@code columnFor} implementation returns a bare column name, and
 * {@link SqlFragment} represents a single {@code WHERE}-fragment plus its binds. There is no way
 * to express a join, a correlated subquery, or a JSON path expression. This matters because the
 * DSL's own headline example is a nested path ({@code lineItems.qty > 5}), which on a typical
 * relational schema means a join, not a column — translating such a path requires a caller-supplied
 * {@link #of(Map)} mapping onto a view or denormalized column that already exists; there is no
 * built-in way to synthesize the join itself.
 */
@FunctionalInterface
public interface ColumnMapping {

    /**
     * Resolves {@code path} to a column name.
     *
     * @param path dot-notation field path, as it appears in the filter AST
     * @return the column name to use in generated SQL, or empty if {@code path} has no mapping
     */
    Optional<String> columnFor(String path);

    /**
     * A mapping under which every path resolves to a column of the same name.
     *
     * <p><strong>Trusted input only.</strong> This performs no validation beyond the dotted-path
     * check below: the resolved path is placed directly in SQL identifier position, subject only
     * to whatever quoting the {@link org.sequeless.filter.sql.spi.QueryBuilder} implementation
     * applies. It is intended for paths the caller already controls (e.g. field names drawn from
     * a fixed, developer-authored {@link org.sequeless.filter.api.FieldRegistry}) — never for
     * exposing arbitrary or untrusted input as column names. Callers whose filter paths may
     * originate from untrusted or deserialized input must use {@link #of(Map)} instead, which acts
     * as an explicit allowlist.
     *
     * <p>Because this contract only supports flat, single-table mappings (see the type-level
     * Javadoc), any path containing {@code .} resolves to {@link Optional#empty()} rather than a
     * well-formed but nonexistent quoted identifier such as {@code "lineItems.qty"} — this fails
     * fast as {@link SqlRenderException} at translation time instead of failing later as a
     * vendor-specific "column does not exist" error at execution time. A caller with a real column
     * for a dotted path (e.g. backed by a joined view) can still map it explicitly via
     * {@link #of(Map)}, which is unaffected by this restriction.
     *
     * @return an identity column mapping
     */
    static ColumnMapping identity() {
        return path -> path.indexOf('.') >= 0 ? Optional.empty() : Optional.of(path);
    }

    /**
     * A mapping backed by an explicit path-to-column allowlist.
     *
     * <p>Safe for untrusted paths: any path not present as a key in {@code mapping} resolves to
     * {@link Optional#empty()}. A dotted path is permitted here if the caller has deliberately
     * mapped it — e.g. onto a real column exposed by a joined view — since the caller controls the
     * mapping's contents directly, unlike {@link #identity()}.
     *
     * @param mapping explicit field-path-to-column-name allowlist
     * @return a mapping backed by {@code mapping}
     */
    static ColumnMapping of(Map<String, String> mapping) {
        Map<String, String> copy = Map.copyOf(mapping);
        return path -> Optional.ofNullable(copy.get(path));
    }
}
