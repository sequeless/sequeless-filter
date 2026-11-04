package org.sequeless.filter.sql.api;

import java.util.Objects;

/**
 * A resolved filter field: either a plain column, which the dialect quotes, or a trusted SQL
 * expression (spec 6, section 3.3; decision #21).
 *
 * <p>{@link Column} covers every field that {@link ColumnMapping} resolves. {@link Expression} is
 * the port's extension point, used by a field that is computed in SQL, such as a JSONB property
 * read with a cast.
 */
public sealed interface Field permits Field.Column, Field.Expression {

    /**
     * A plain column name. The dialect quotes it as an identifier.
     *
     * @param name the column name, unquoted
     */
    record Column(String name) implements Field {
        public Column {
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * A trusted SQL expression, used as-is. See {@link SqlExpression}.
     *
     * @param expression the expression text and its parameters
     */
    record Expression(SqlExpression expression) implements Field {
        public Expression {
            Objects.requireNonNull(expression, "expression");
        }
    }
}
