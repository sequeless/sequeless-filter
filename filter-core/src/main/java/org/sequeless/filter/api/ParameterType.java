package org.sequeless.filter.api;

/** Type of a single function-call parameter in a {@link ParameterDefinition}. */
public enum ParameterType {
    INT,
    FLOAT,
    STRING,
    /** Constrained to a finite set of values listed in {@code ParameterDefinition#getAllowedValues()}. */
    ENUM
}
