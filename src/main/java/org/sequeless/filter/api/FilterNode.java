package org.sequeless.filter.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Root of the filter AST. All nodes are immutable value types.
 *
 * <p>Serialises to/from JSON via the {@code "type"} discriminator property.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FieldFilter.class, name = "field"),
    @JsonSubTypes.Type(value = AnyFilter.class, name = "any"),
    @JsonSubTypes.Type(value = AndFilter.class, name = "and"),
    @JsonSubTypes.Type(value = OrFilter.class, name = "or")
})
public sealed interface FilterNode permits FieldFilter, AnyFilter, AndFilter, OrFilter {}
