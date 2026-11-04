package org.sequeless.filter.sql.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.sequeless.filter.api.AndFilter;
import org.sequeless.filter.api.AnyFilter;
import org.sequeless.filter.api.AnyFilterExpander;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.filter.api.FilterValidator;
import org.sequeless.filter.api.FilterViolation;
import org.sequeless.filter.api.FilterVisitor;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.api.OrFilter;
import org.sequeless.filter.api.ValueShape;
import org.sequeless.filter.sql.spi.QueryBuilder;

/**
 * The sole shipped {@link FilterQueryTranslator} implementation.
 *
 * <p>Stateless and thread-safe: a fresh {@link Visitor} is constructed per {@link #translate}
 * call, closing over that call's {@code resolver}/{@code fields}/{@code ops}/{@code builder}
 * arguments, so a single {@code DefaultFilterQueryTranslator} instance may safely be shared and
 * reused across concurrent callers.
 */
public final class DefaultFilterQueryTranslator implements FilterQueryTranslator {

    @Override
    public SqlFragment translate(
            FilterNode node, FieldResolver resolver, FieldRegistry fields, OperatorRegistry ops, QueryBuilder builder) {
        List<FilterViolation> violations = FilterValidator.validate(node, ops, fields);
        if (!violations.isEmpty()) {
            throw new SqlRenderException("Filter failed validation: " + violations);
        }
        return new Visitor(resolver, fields, ops, builder).walk(node);
    }

    /**
     * Per-call tree walker. Not reused across {@link #translate} calls, so it may safely hold the
     * call's arguments as fields without introducing shared mutable state.
     */
    private static final class Visitor extends FilterVisitor<SqlFragment> {

        private final FieldResolver resolver;
        private final FieldRegistry fields;
        private final OperatorRegistry ops;
        private final QueryBuilder builder;

        Visitor(FieldResolver resolver, FieldRegistry fields, OperatorRegistry ops, QueryBuilder builder) {
            this.resolver = resolver;
            this.fields = fields;
            this.ops = ops;
            this.builder = builder;
        }

        @Override
        public SqlFragment visitField(FieldFilter node) {
            OperatorDefinition operator = resolveOperator(node.op());
            Field field = resolveField(node.path());
            return renderCondition(field, operator, node.value());
        }

        @Override
        public SqlFragment visitAny(AnyFilter node) {
            FilterNode expanded = AnyFilterExpander.expand(node, fields, ops);
            List<FilterNode> candidateOperands = switch (expanded) {
                case OrFilter o -> o.operands();
                case AndFilter a -> a.operands();
                default -> List.of(expanded);
            };

            List<SqlFragment> survivors = new ArrayList<>();
            for (FilterNode candidate : candidateOperands) {
                if (candidate instanceof FieldFilter f
                        && resolver.resolve(f.path()).isEmpty()) {
                    continue;
                }
                survivors.add(walk(candidate));
            }

            if (survivors.isEmpty()) {
                throw new SqlRenderException(
                        "'any " + node.op() + "' expansion produced no operands mapped by the supplied FieldResolver");
            }
            return builder.or(survivors);
        }

        @Override
        public SqlFragment visitAnd(AndFilter node) {
            if (node.operands().isEmpty()) {
                throw new SqlRenderException("'and' has no operands");
            }
            List<SqlFragment> rendered =
                    node.operands().stream().map(this::walk).toList();
            return builder.and(rendered);
        }

        @Override
        public SqlFragment visitOr(OrFilter node) {
            if (node.operands().isEmpty()) {
                throw new SqlRenderException("'or' has no operands");
            }
            List<SqlFragment> rendered =
                    node.operands().stream().map(this::walk).toList();
            return builder.or(rendered);
        }

        private SqlFragment renderCondition(Field field, OperatorDefinition operator, JsonNode rawValue) {
            if (operator.isUnary()) {
                return builder.unary(field, operator);
            }
            Object value = convertValue(rawValue);
            if (operator.getValueShape() == ValueShape.LIST && value instanceof List<?> list && list.isEmpty()) {
                throw new SqlRenderException("Operator '" + operator.getCanonicalName() + "' on field '"
                        + describe(field) + "' was given an empty list value");
            }
            return builder.binary(field, operator, value);
        }

        private OperatorDefinition resolveOperator(String opName) {
            return ops.findByCanonicalOrAlias(opName)
                    .orElseThrow(() -> new SqlRenderException("Unknown operator: '" + opName + "'"));
        }

        private Field resolveField(String path) {
            return resolver.resolve(path)
                    .orElseThrow(() -> new SqlRenderException("Unmapped column for path: '" + path + "'"));
        }

        /** A human-readable name for error messages: the column name, or the expression text. */
        private static String describe(Field field) {
            return switch (field) {
                case Field.Column column -> column.name();
                case Field.Expression expression -> expression.expression().sql();
            };
        }

        private static Object convertValue(JsonNode node) {
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isTextual()) {
                return node.asText();
            }
            if (node.isInt()) {
                return node.intValue();
            }
            if (node.isLong()) {
                return node.longValue();
            }
            if (node.isDouble()) {
                return node.doubleValue();
            }
            if (node.isBoolean()) {
                return node.booleanValue();
            }
            if (node.isArray()) {
                List<Object> values = new ArrayList<>();
                node.forEach(element -> values.add(convertValue(element)));
                return values;
            }
            if (node.isNumber()) {
                return node.numberValue();
            }
            throw new SqlRenderException("Unsupported JSON value type: " + node.getNodeType());
        }
    }
}
