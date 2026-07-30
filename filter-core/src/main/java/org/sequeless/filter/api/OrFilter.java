package org.sequeless.filter.api;

import java.util.List;
import java.util.Objects;

/**
 * Disjunction: at least one operand must match.
 *
 * @param operands the child filter nodes; copied defensively on construction
 */
public record OrFilter(List<FilterNode> operands) implements FilterNode {

    public OrFilter {
        Objects.requireNonNull(operands, "operands");
        operands = List.copyOf(operands);
    }
}
