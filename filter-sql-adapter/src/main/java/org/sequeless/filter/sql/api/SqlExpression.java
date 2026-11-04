package org.sequeless.filter.sql.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A trusted SQL expression with its own positional bind parameters, used as a filter field.
 *
 * <p>Same shape as {@link SqlFragment}. The text is written by the caller, who is trusted: it is
 * placed in SQL position as-is, never quoted as an identifier. Use it only for expressions the
 * application controls, such as a JSONB property read with a datatype cast. Paths and values from
 * untrusted input must never be placed in {@code sql}; they go in {@code parameters}.
 *
 * <p>The renderer places the expression's parameters before the operator's parameters, and repeats
 * them once for each place the expression appears in the rendered SQL (spec 6, section 3.3).
 *
 * @param sql        SQL text with positional {@code ?} placeholders
 * @param parameters bind values, one per placeholder in {@code sql}, in order
 */
public record SqlExpression(String sql, List<Object> parameters) {

    /**
     * Defensively copies {@code parameters}, keeping {@code null} elements as {@link SqlFragment}
     * does.
     */
    public SqlExpression {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(parameters, "parameters");
        parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
    }
}
