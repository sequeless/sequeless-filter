package org.sequeless.filter.sql.postgres;

import java.util.ArrayList;
import java.util.List;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.sql.api.SqlExpression;
import org.sequeless.filter.sql.api.SqlFragment;
import org.sequeless.filter.sql.spi.AnsiQueryBuilder;

/**
 * PostgreSQL {@link org.sequeless.filter.sql.spi.QueryBuilder}: the ANSI default, with the LIKE family
 * rendered as {@code ILIKE}.
 *
 * <p>Only the LIKE hook changes. It keeps the ANSI rules (spec 6, section 3.3): the same escaping of
 * literal wildcards, the same rejection of a {@code null} value, and the same NULL-inclusive
 * complement for {@code is not like}. {@code ILIKE} is case-insensitive, and the contract in
 * {@code filter-sql-testkit} checks it on ASCII fixtures. Its folding of non-ASCII text can differ from
 * the ANSI {@code UPPER(...)} form (for example {@code ß}); that difference is not verified here.
 *
 * <p>Stateless and thread-safe.
 */
public class PostgresQueryBuilder extends AnsiQueryBuilder {

    @Override
    protected SqlFragment renderLike(SqlExpression operand, OperatorDefinition operator, Object value) {
        rejectNullValue(operator, operand, value);
        String pattern = likePattern(operator, value);
        if (!"is not like".equals(operator.getCanonicalName())) {
            return new SqlFragment(operand.sql() + " ILIKE ? ESCAPE '\\'", withPattern(operand, pattern));
        }
        // The complement of 'is like': a NULL field is included, so the operand appears three times.
        String sql = "(" + operand.sql() + " IS NULL OR " + operand.sql() + " NOT ILIKE ? ESCAPE '\\')";
        List<Object> parameters = new ArrayList<>(operand.parameters());
        parameters.addAll(operand.parameters());
        parameters.add(pattern);
        return new SqlFragment(sql, parameters);
    }

    private static List<Object> withPattern(SqlExpression operand, String pattern) {
        List<Object> parameters = new ArrayList<>(operand.parameters());
        parameters.add(pattern);
        return parameters;
    }
}
