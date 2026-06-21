package org.sequeless.filter.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * A single field condition: {@code path op [value]}.
 *
 * @param path dot-notation application-layer path (e.g. {@code "lineItems.qty"})
 * @param op canonical operator name (aliases already resolved by the parser)
 * @param value {@code null} for unary operators (exists / does not exist)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldFilter(String path, String op, JsonNode value) implements FilterNode {

    public FieldFilter {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(op, "op");
    }
}
