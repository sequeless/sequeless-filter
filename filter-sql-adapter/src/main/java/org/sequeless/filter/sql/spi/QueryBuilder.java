package org.sequeless.filter.sql.spi;

import java.util.List;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.sql.api.FilterQueryTranslator;
import org.sequeless.filter.sql.api.SqlFragment;

/**
 * Vendor port for rendering filter conditions to SQL.
 *
 * <p>This is the hexagonal boundary between {@link FilterQueryTranslator} — which walks the
 * {@link org.sequeless.filter.api.FilterNode} tree and resolves paths/values but knows nothing
 * about any particular SQL dialect — and a concrete vendor implementation, which owns full
 * construction of each returned {@link SqlFragment}: identifier quoting, operator-to-SQL
 * translation (including {@code is in}'s list expansion), value binding, and AND/OR combination.
 * The port is deliberately fixed to producing {@link SqlFragment} rather than a generic
 * {@code QueryBuilder<Q>} — every implementation renders raw, parameterized SQL text.
 *
 * <p>Each method receives the already-resolved column name and the already-converted Java value
 * (see {@link FilterQueryTranslator}'s value-conversion behavior) — never the raw
 * {@link com.fasterxml.jackson.databind.JsonNode} or unresolved field path. A {@code null} passed
 * as {@code value} to {@link #binary} means SQL {@code NULL} (e.g. from {@code status is null},
 * i.e. a {@code null}-converted {@link com.fasterxml.jackson.databind.node.NullNode}), not "no
 * value" — dispatch between {@link #binary} and {@link #unary} is by
 * {@link OperatorDefinition#isUnary()}, never by whether {@code value} is {@code null}.
 *
 * <p><strong>Implementations are expected to be stateless and thread-safe.</strong> Nothing in
 * this contract enforces that, but callers will naturally hold a single instance as a long-lived
 * singleton shared across concurrent request threads.
 */
public interface QueryBuilder {

    /**
     * Renders a binary condition: a column, an operator, and a value.
     *
     * <p>{@code value} is the already-converted Java value — a {@link String}, a boxed
     * {@link Number}, a {@link Boolean}, {@code null} (meaning SQL {@code NULL}), or a
     * {@code List<Object>} for a list-shaped operator such as {@code is in}.
     *
     * @param column   the resolved SQL column name
     * @param operator the operator being applied; never one for which {@link OperatorDefinition#isUnary()} is true
     * @param value    the already-converted value (never itself a raw {@code JsonNode})
     * @return the rendered fragment
     */
    SqlFragment binary(String column, OperatorDefinition operator, Object value);

    /**
     * Renders a unary condition: a column and an operator that takes no value (e.g. {@code exists}
     * / {@code does not exist}).
     *
     * @param column   the resolved SQL column name
     * @param operator the operator being applied; always one for which {@link OperatorDefinition#isUnary()} is true
     * @return the rendered fragment
     */
    SqlFragment unary(String column, OperatorDefinition operator);

    /**
     * Combines fragments with SQL {@code AND}.
     *
     * @param operands the fragments to combine; never empty (the translator rejects any
     *                 zero-operand conjunction before it would reach this method)
     * @return the combined fragment
     */
    SqlFragment and(List<SqlFragment> operands);

    /**
     * Combines fragments with SQL {@code OR}.
     *
     * @param operands the fragments to combine; never empty (the translator rejects any
     *                 zero-operand disjunction before it would reach this method)
     * @return the combined fragment
     */
    SqlFragment or(List<SqlFragment> operands);
}
