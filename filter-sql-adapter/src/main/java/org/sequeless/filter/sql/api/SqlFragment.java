package org.sequeless.filter.sql.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A rendered piece of SQL together with its positional bind parameters.
 *
 * <p>{@code sql} contains {@code ?} placeholders, in the same left-to-right order as the entries
 * in {@code parameters} — the classic JDBC {@code PreparedStatement} convention. A fragment may
 * represent a single condition (e.g. {@code "status" = ?}) or a combination of other fragments
 * (e.g. {@code ("status" = ? AND "region" = ?)}); {@link org.sequeless.filter.sql.spi.QueryBuilder}
 * is responsible for keeping the two lists aligned when it combines fragments.
 *
 * @param sql        SQL text with positional {@code ?} placeholders
 * @param parameters bind values, one per placeholder in {@code sql}, in order
 */
public record SqlFragment(String sql, List<Object> parameters) {

    /**
     * Defensively copies {@code parameters}.
     *
     * <p>Deliberately uses {@link Collections#unmodifiableList(List)} over a fresh
     * {@link ArrayList} rather than this codebase's usual {@code List.copyOf} idiom: a
     * value-converted filter can legitimately contain a {@code null} element (e.g. {@code is in
     * ['a', null]}), and {@code List.copyOf} throws {@link NullPointerException} on a {@code null}
     * element. Using the house idiom here would turn an otherwise-legal filter into a
     * construction-time crash.
     */
    public SqlFragment {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(parameters, "parameters");
        parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
    }
}
