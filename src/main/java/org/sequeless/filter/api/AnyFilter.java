package org.sequeless.filter.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * A wildcard condition that targets all fields compatible with the given operator.
 * Expansion to an {@link OrFilter} over concrete fields happens via {@link AnyFilterExpander}.
 *
 * @param op canonical operator name
 * @param value {@code null} for unary operators
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnyFilter(String op, JsonNode value) implements FilterNode {

    public AnyFilter {
        Objects.requireNonNull(op, "op");
    }
}
