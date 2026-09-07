package org.sequeless.filter.api;

import java.util.List;

/**
 * Strategy for expanding an {@link AnyFilter} node into a concrete sub-tree.
 *
 * @see AnyFilterExpander
 */
@FunctionalInterface
public interface AnyExpansionStrategy {

    /**
     * Expands the given {@link AnyFilter} into a concrete {@link FilterNode}.
     *
     * @param any              the wildcard filter node to expand
     * @param compatibleFields fields compatible with the operator in {@code any}
     * @return the expanded node (e.g. an {@link OrFilter} over {@code compatibleFields})
     */
    FilterNode expand(AnyFilter any, List<FieldDefinition> compatibleFields);
}
