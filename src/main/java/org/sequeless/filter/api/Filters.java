package org.sequeless.filter.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Static factory convenience methods for building {@link FilterNode} ASTs programmatically.
 */
public final class Filters {

    private Filters() {}

    /** Creates a binary {@link FieldFilter} (path op value). */
    public static FieldFilter field(String path, String op, JsonNode value) {
        return new FieldFilter(path, op, value);
    }

    /** Creates a unary {@link FieldFilter} (path op, no value — e.g. {@code exists}). */
    public static FieldFilter field(String path, String op) {
        return new FieldFilter(path, op, null);
    }

    /** Creates a binary {@link AnyFilter} (any op value). */
    public static AnyFilter any(String op, JsonNode value) {
        return new AnyFilter(op, value);
    }

    /** Creates a conjunction of the given operands. */
    public static AndFilter and(FilterNode... operands) {
        return new AndFilter(java.util.List.of(operands));
    }

    /** Creates a disjunction of the given operands. */
    public static OrFilter or(FilterNode... operands) {
        return new OrFilter(java.util.List.of(operands));
    }
}
