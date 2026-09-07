package org.sequeless.filter.api;

import java.util.List;
import java.util.Objects;

/**
 * Conjunction: all operands must match.
 *
 * @param operands the child filter nodes; copied defensively on construction
 */
public record AndFilter(List<FilterNode> operands) implements FilterNode {

    public AndFilter {
        Objects.requireNonNull(operands, "operands");
        operands = List.copyOf(operands);
    }
}
