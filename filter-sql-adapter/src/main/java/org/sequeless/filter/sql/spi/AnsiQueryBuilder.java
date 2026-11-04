package org.sequeless.filter.sql.spi;

import java.util.ArrayList;
import java.util.List;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.sql.api.Field;
import org.sequeless.filter.sql.api.SqlExpression;
import org.sequeless.filter.sql.api.SqlFragment;
import org.sequeless.filter.sql.api.SqlRenderException;

/**
 * ANSI SQL default {@link QueryBuilder} implementation, structured as a Template Method.
 *
 * <p>{@link #binary} and {@link #unary} are {@code final} dispatchers. They turn the {@link Field}
 * into an operand (a {@link SqlExpression}: a quoted identifier for a column, or the trusted
 * expression as-is), inspect the operator's canonical name, and delegate to one of eight
 * {@code protected} hooks. Each hook owns one rendering concern and receives the operand, so a
 * vendor overrides only the hook it needs, and quoting stays in one place.
 *
 * <p>The operand's parameters are placed before the hook's own parameters, and repeated once for
 * each place the operand appears in the rendered SQL (spec 6, section 3.3).
 *
 * <p>This is the complete, fixed hook set:
 *
 * <ul>
 *   <li>{@link #quoteIdentifier(String)}
 *   <li>{@link #renderIs(SqlExpression, boolean, Object)}
 *   <li>{@link #renderComparison(SqlExpression, OperatorDefinition, Object)}
 *   <li>{@link #renderLike(SqlExpression, OperatorDefinition, Object)}
 *   <li>{@link #renderIn(SqlExpression, List)}
 *   <li>{@link #renderExists(SqlExpression, boolean)}
 *   <li>{@link #renderUnknown(SqlExpression, OperatorDefinition, Object)}
 *   <li>{@link #unaryUnknown(SqlExpression, OperatorDefinition)}
 * </ul>
 *
 * <p><strong>Implementations are expected to be stateless and thread-safe</strong> — this class
 * holds no mutable state, and subclasses should preserve that.
 */
public class AnsiQueryBuilder implements QueryBuilder {

    @Override
    public final SqlFragment binary(Field field, OperatorDefinition operator, Object value) {
        SqlExpression operand = operand(field);
        return switch (operator.getCanonicalName()) {
            case "is" -> renderIs(operand, false, value);
            case "is not" -> renderIs(operand, true, value);
            case "is greater than", "is greater than or equal to", "is less than", "is less than or equal to" ->
                renderComparison(operand, operator, value);
            case "contains", "starts with", "is like", "is not like" -> renderLike(operand, operator, value);
            case "is in" -> renderIn(operand, toValueList(operand, operator, value));
            default -> renderUnknown(operand, operator, value);
        };
    }

    @Override
    public final SqlFragment unary(Field field, OperatorDefinition operator) {
        SqlExpression operand = operand(field);
        return switch (operator.getCanonicalName()) {
            case "exists" -> renderExists(operand, false);
            case "does not exist" -> renderExists(operand, true);
            default -> unaryUnknown(operand, operator);
        };
    }

    /** A plain column is quoted as an identifier with no parameters; an expression is used as-is. */
    private SqlExpression operand(Field field) {
        return switch (field) {
            case Field.Column column -> new SqlExpression(quoteIdentifier(column.name()), List.of());
            case Field.Expression expression -> expression.expression();
        };
    }

    @SuppressWarnings("unchecked")
    private static List<Object> toValueList(SqlExpression operand, OperatorDefinition operator, Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new SqlRenderException(
                "Operator '" + operator.getCanonicalName() + "' on column " + operand.sql() + " expects a list value");
    }

    @Override
    public SqlFragment and(List<SqlFragment> operands) {
        return combine(operands, "AND");
    }

    @Override
    public SqlFragment or(List<SqlFragment> operands) {
        return combine(operands, "OR");
    }

    private static SqlFragment combine(List<SqlFragment> operands, String connective) {
        StringBuilder sql = new StringBuilder("(");
        List<Object> parameters = new ArrayList<>();
        for (int i = 0; i < operands.size(); i++) {
            if (i > 0) {
                sql.append(' ').append(connective).append(' ');
            }
            SqlFragment operand = operands.get(i);
            sql.append(operand.sql());
            parameters.addAll(operand.parameters());
        }
        sql.append(')');
        return new SqlFragment(sql.toString(), parameters);
    }

    /**
     * Quotes {@code column} as an ANSI SQL delimited identifier.
     *
     * <p><strong>Security-relevant.</strong> Emits {@code "…"} with any embedded {@code "}
     * doubled. This is the only defense between a resolved column name and SQL injection when
     * that name did not originate from the grammar's own identifier token (e.g. a
     * programmatically or deserialization-supplied {@link org.sequeless.filter.sql.api.ColumnMapping}).
     * A subclass overriding this hook must preserve an equivalent injection-safe guarantee for
     * its target dialect's quoting syntax.
     *
     * @param column the resolved column name
     * @return the quoted identifier
     */
    protected String quoteIdentifier(String column) {
        return "\"" + column.replace("\"", "\"\"") + "\"";
    }

    /**
     * Renders {@code is} / {@code is not}, including {@code IS NULL} / {@code IS NOT NULL} when
     * {@code value} is {@code null}.
     *
     * @param operand  the already-rendered field operand; its parameters come first
     * @param negated  {@code true} for {@code is not}, {@code false} for {@code is}
     * @param value    the already-converted value; {@code null} means SQL {@code NULL}
     * @return the rendered fragment
     */
    protected SqlFragment renderIs(SqlExpression operand, boolean negated, Object value) {
        if (value == null) {
            return new SqlFragment(operand.sql() + (negated ? " IS NOT NULL" : " IS NULL"), operand.parameters());
        }
        return new SqlFragment(operand.sql() + (negated ? " <> ?" : " = ?"), bind(operand, value));
    }

    /**
     * Renders one of the four numeric comparison operators ({@code is greater than}, {@code is
     * greater than or equal to}, {@code is less than}, {@code is less than or equal to}).
     *
     * @param operand  the already-rendered field operand; its parameters come first
     * @param operator the resolved operator
     * @param value    the already-converted comparison value
     * @return the rendered fragment
     */
    protected SqlFragment renderComparison(SqlExpression operand, OperatorDefinition operator, Object value) {
        String sqlOperator = switch (operator.getCanonicalName()) {
            case "is greater than" -> ">";
            case "is greater than or equal to" -> ">=";
            case "is less than" -> "<";
            case "is less than or equal to" -> "<=";
            default ->
                throw new SqlRenderException("Unsupported comparison operator: '" + operator.getCanonicalName() + "'");
        };
        return new SqlFragment(operand.sql() + " " + sqlOperator + " ?", bind(operand, value));
    }

    /**
     * Renders {@code contains} / {@code starts with} / {@code is like} / {@code is not like}.
     *
     * <p>Escaping of literal {@code %} / {@code _} characters already present in {@code value} is
     * <strong>not</strong> performed by this default implementation (the unified semantics of spec
     * 6, section 3.3 come in a later change). The wildcard is placed according to the operator:
     *
     * <ul>
     *   <li>{@code contains}: {@code %value%}
     *   <li>{@code starts with}: {@code value%}
     *   <li>{@code is like}: {@code value} used as-is (the caller supplies its own wildcards)
     *   <li>{@code is not like}: same as {@code is like}, negated
     * </ul>
     *
     * @param operand  the already-rendered field operand; its parameters come first
     * @param operator the resolved operator
     * @param value    the already-converted string value
     * @return the rendered fragment
     */
    protected SqlFragment renderLike(SqlExpression operand, OperatorDefinition operator, Object value) {
        String pattern = switch (operator.getCanonicalName()) {
            case "contains" -> "%" + value + "%";
            case "starts with" -> value + "%";
            case "is like", "is not like" -> String.valueOf(value);
            default ->
                throw new SqlRenderException("Unsupported LIKE-family operator: '" + operator.getCanonicalName() + "'");
        };
        boolean negated = "is not like".equals(operator.getCanonicalName());
        return new SqlFragment(operand.sql() + (negated ? " NOT LIKE ?" : " LIKE ?"), bind(operand, pattern));
    }

    /**
     * Renders {@code is in}, expanding to {@code operand IN (?, ?, …)}.
     *
     * @param operand the already-rendered field operand; its parameters come first
     * @param values  the already-converted, non-empty member list (the translator rejects an empty
     *                list-shaped value before it would reach this hook)
     * @return the rendered fragment
     */
    protected SqlFragment renderIn(SqlExpression operand, List<Object> values) {
        String placeholders = String.join(", ", values.stream().map(v -> "?").toList());
        List<Object> parameters = new ArrayList<>(operand.parameters());
        parameters.addAll(values);
        return new SqlFragment(operand.sql() + " IN (" + placeholders + ")", parameters);
    }

    /**
     * Renders the unary {@code exists} / {@code does not exist} pair, with no bind parameter of
     * its own.
     *
     * @param operand the already-rendered field operand
     * @param negated {@code true} for {@code does not exist}, {@code false} for {@code exists}
     * @return the rendered fragment
     */
    protected SqlFragment renderExists(SqlExpression operand, boolean negated) {
        return new SqlFragment(operand.sql() + (negated ? " IS NULL" : " IS NOT NULL"), operand.parameters());
    }

    /**
     * Fallback for any binary operator outside the 13 built-ins.
     *
     * <p>Throws {@link SqlRenderException} by default; a vendor supporting a custom operator
     * overrides this hook rather than reimplementing {@link #binary}'s dispatch.
     *
     * @param operand  the already-rendered field operand
     * @param operator the unrecognized operator
     * @param value    the already-converted value
     * @return never returns normally in the default implementation
     * @throws SqlRenderException always, unless overridden
     */
    protected SqlFragment renderUnknown(SqlExpression operand, OperatorDefinition operator, Object value) {
        throw new SqlRenderException(
                "No ANSI rendering for operator '" + operator.getCanonicalName() + "' on column " + operand.sql());
    }

    /**
     * Fallback twin of {@link #renderUnknown} for unary operators outside the built-in
     * {@code exists} / {@code does not exist} pair.
     *
     * @param operand  the already-rendered field operand
     * @param operator the unrecognized unary operator
     * @return never returns normally in the default implementation
     * @throws SqlRenderException always, unless overridden
     */
    protected SqlFragment unaryUnknown(SqlExpression operand, OperatorDefinition operator) {
        throw new SqlRenderException("No ANSI rendering for unary operator '" + operator.getCanonicalName()
                + "' on column " + operand.sql());
    }

    /** The operand's parameters followed by {@code value}: the order the SQL text binds them in. */
    private static List<Object> bind(SqlExpression operand, Object value) {
        List<Object> parameters = new ArrayList<>(operand.parameters());
        parameters.add(value);
        return parameters;
    }
}
