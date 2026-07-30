package org.sequeless.filter.sql.spi;

import java.util.ArrayList;
import java.util.List;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.sql.api.SqlFragment;
import org.sequeless.filter.sql.api.SqlRenderException;

/**
 * ANSI SQL default {@link QueryBuilder} implementation, structured as a Template Method.
 *
 * <p>{@link #binary} and {@link #unary} are {@code final} dispatchers: they inspect the
 * operator's canonical name and delegate to one of eight {@code protected} hooks, each owning one
 * rendering concern. A vendor that needs to change only one concern — say, {@code LIKE} escaping,
 * or identifier quoting — extends this class and overrides just that hook, rather than
 * reimplementing the full 13-operator dispatch from scratch.
 *
 * <p>This is the complete, fixed hook set — not a starting point meant to accrete further
 * overloads:
 *
 * <ul>
 *   <li>{@link #quoteIdentifier(String)}
 *   <li>{@link #renderIs(String, boolean, Object)}
 *   <li>{@link #renderComparison(String, OperatorDefinition, Object)}
 *   <li>{@link #renderLike(String, OperatorDefinition, Object)}
 *   <li>{@link #renderIn(String, List)}
 *   <li>{@link #renderExists(String, boolean)}
 *   <li>{@link #renderUnknown(String, OperatorDefinition, Object)}
 *   <li>{@link #unaryUnknown(String, OperatorDefinition)}
 * </ul>
 *
 * <p><strong>Implementations are expected to be stateless and thread-safe</strong> — this class
 * holds no mutable state, and subclasses should preserve that.
 */
public class AnsiQueryBuilder implements QueryBuilder {

    @Override
    public final SqlFragment binary(String column, OperatorDefinition operator, Object value) {
        String quoted = quoteIdentifier(column);
        return switch (operator.getCanonicalName()) {
            case "is" -> renderIs(quoted, false, value);
            case "is not" -> renderIs(quoted, true, value);
            case "is greater than",
                    "is greater than or equal to",
                    "is less than",
                    "is less than or equal to" -> renderComparison(quoted, operator, value);
            case "contains", "starts with", "is like", "is not like" -> renderLike(quoted, operator, value);
            case "is in" -> renderIn(quoted, toValueList(column, operator, value));
            default -> renderUnknown(quoted, operator, value);
        };
    }

    @Override
    public final SqlFragment unary(String column, OperatorDefinition operator) {
        String quoted = quoteIdentifier(column);
        return switch (operator.getCanonicalName()) {
            case "exists" -> renderExists(quoted, false);
            case "does not exist" -> renderExists(quoted, true);
            default -> unaryUnknown(quoted, operator);
        };
    }

    @SuppressWarnings("unchecked")
    private static List<Object> toValueList(String column, OperatorDefinition operator, Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new SqlRenderException(
                "Operator '" + operator.getCanonicalName() + "' on column '" + column + "' expects a list value");
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
     * @param column   already-quoted column identifier
     * @param negated  {@code true} for {@code is not}, {@code false} for {@code is}
     * @param value    the already-converted value; {@code null} means SQL {@code NULL}
     * @return the rendered fragment
     */
    protected SqlFragment renderIs(String column, boolean negated, Object value) {
        if (value == null) {
            return new SqlFragment(column + (negated ? " IS NOT NULL" : " IS NULL"), List.of());
        }
        return new SqlFragment(column + (negated ? " <> ?" : " = ?"), newList(value));
    }

    /**
     * Renders one of the four numeric comparison operators ({@code is greater than}, {@code is
     * greater than or equal to}, {@code is less than}, {@code is less than or equal to}).
     *
     * @param column   already-quoted column identifier
     * @param operator the resolved operator
     * @param value    the already-converted comparison value
     * @return the rendered fragment
     */
    protected SqlFragment renderComparison(String column, OperatorDefinition operator, Object value) {
        String sqlOperator =
                switch (operator.getCanonicalName()) {
                    case "is greater than" -> ">";
                    case "is greater than or equal to" -> ">=";
                    case "is less than" -> "<";
                    case "is less than or equal to" -> "<=";
                    default -> throw new SqlRenderException(
                            "Unsupported comparison operator: '" + operator.getCanonicalName() + "'");
                };
        return new SqlFragment(column + " " + sqlOperator + " ?", newList(value));
    }

    /**
     * Renders {@code contains} / {@code starts with} / {@code is like} / {@code is not like}.
     *
     * <p>Escaping of literal {@code %} / {@code _} characters already present in {@code value} is
     * <strong>not</strong> performed by this default implementation — matching semantics for such
     * values are left to each vendor's discretion (see {@code QueryBuilder}'s Javadoc). This
     * implementation uses a plain ANSI {@code LIKE} / {@code NOT LIKE} with the wildcard placed
     * according to the operator:
     *
     * <ul>
     *   <li>{@code contains}: {@code %value%}
     *   <li>{@code starts with}: {@code value%}
     *   <li>{@code is like}: {@code value} used as-is (the caller supplies its own wildcards)
     *   <li>{@code is not like}: same as {@code is like}, negated
     * </ul>
     *
     * @param column   already-quoted column identifier
     * @param operator the resolved operator
     * @param value    the already-converted string value
     * @return the rendered fragment
     */
    protected SqlFragment renderLike(String column, OperatorDefinition operator, Object value) {
        String pattern =
                switch (operator.getCanonicalName()) {
                    case "contains" -> "%" + value + "%";
                    case "starts with" -> value + "%";
                    case "is like", "is not like" -> String.valueOf(value);
                    default -> throw new SqlRenderException(
                            "Unsupported LIKE-family operator: '" + operator.getCanonicalName() + "'");
                };
        boolean negated = "is not like".equals(operator.getCanonicalName());
        return new SqlFragment(column + (negated ? " NOT LIKE ?" : " LIKE ?"), newList(pattern));
    }

    /**
     * Renders {@code is in}, expanding to {@code column IN (?, ?, …)}.
     *
     * @param column already-quoted column identifier
     * @param values the already-converted, non-empty member list (the translator rejects an empty
     *               list-shaped value before it would reach this hook)
     * @return the rendered fragment
     */
    protected SqlFragment renderIn(String column, List<Object> values) {
        String placeholders = String.join(", ", values.stream().map(v -> "?").toList());
        return new SqlFragment(column + " IN (" + placeholders + ")", new ArrayList<>(values));
    }

    /**
     * Renders the unary {@code exists} / {@code does not exist} pair, with no bind parameter.
     *
     * @param column  already-quoted column identifier
     * @param negated {@code true} for {@code does not exist}, {@code false} for {@code exists}
     * @return the rendered fragment
     */
    protected SqlFragment renderExists(String column, boolean negated) {
        return new SqlFragment(column + (negated ? " IS NULL" : " IS NOT NULL"), List.of());
    }

    /**
     * Fallback for any binary operator outside the 13 built-ins.
     *
     * <p>Throws {@link SqlRenderException} by default; a vendor supporting a custom operator
     * overrides this hook rather than reimplementing {@link #binary}'s dispatch.
     *
     * @param column   already-quoted column identifier
     * @param operator the unrecognized operator
     * @param value    the already-converted value
     * @return never returns normally in the default implementation
     * @throws SqlRenderException always, unless overridden
     */
    protected SqlFragment renderUnknown(String column, OperatorDefinition operator, Object value) {
        throw new SqlRenderException(
                "No ANSI rendering for operator '" + operator.getCanonicalName() + "' on column " + column);
    }

    /**
     * Fallback twin of {@link #renderUnknown} for unary operators outside the built-in
     * {@code exists} / {@code does not exist} pair.
     *
     * @param column   already-quoted column identifier
     * @param operator the unrecognized unary operator
     * @return never returns normally in the default implementation
     * @throws SqlRenderException always, unless overridden
     */
    protected SqlFragment unaryUnknown(String column, OperatorDefinition operator) {
        throw new SqlRenderException(
                "No ANSI rendering for unary operator '" + operator.getCanonicalName() + "' on column " + column);
    }

    private static List<Object> newList(Object value) {
        List<Object> list = new ArrayList<>();
        list.add(value);
        return list;
    }
}
