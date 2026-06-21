package org.sequeless.filter.api;

import java.util.List;

/**
 * Expands {@link AnyFilter} nodes into concrete {@link OrFilter} trees.
 *
 * <p>The default strategy wraps compatible fields into an OR of {@link FieldFilter}s.
 * Provide a custom {@link AnyExpansionStrategy} for different expansion semantics.
 */
public final class AnyFilterExpander {

    private static final AnyExpansionStrategy DEFAULT_STRATEGY = (any, compatibleFields) -> {
        List<FilterNode> operands = compatibleFields.stream()
                .map(f -> (FilterNode) new FieldFilter(f.getPath(), any.op(), any.value()))
                .toList();
        return new OrFilter(operands);
    };

    private AnyFilterExpander() {}

    /**
     * Expands all {@link AnyFilter} nodes in the tree using the default OR strategy.
     */
    public static FilterNode expand(FilterNode node, FieldRegistry fields, OperatorRegistry ops) {
        return expand(node, fields, ops, DEFAULT_STRATEGY);
    }

    /**
     * Expands all {@link AnyFilter} nodes in the tree using the supplied strategy.
     */
    public static FilterNode expand(
            FilterNode node,
            FieldRegistry fields,
            OperatorRegistry ops,
            AnyExpansionStrategy strategy) {
        return switch (node) {
            case AnyFilter a -> {
                List<FieldDefinition> compatible = fields.compatibleWith(a.op(), ops);
                yield strategy.expand(a, compatible);
            }
            case FieldFilter f -> f;
            case AndFilter a -> new AndFilter(
                    a.operands().stream().map(o -> expand(o, fields, ops, strategy)).toList());
            case OrFilter o -> new OrFilter(
                    o.operands().stream().map(op -> expand(op, fields, ops, strategy)).toList());
        };
    }
}
