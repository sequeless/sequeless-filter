package org.sequeless.filter.api;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Describes a single parameter of a {@link Syntax#FUNCTION} operator.
 *
 * @see OperatorDefinition
 */
@Value
@Builder
public class ParameterDefinition {

    String name;
    ParameterType type;

    /** Non-empty only when {@code type == ENUM}; lists the accepted literal values. */
    @Builder.Default
    List<String> allowedValues = List.of();
}
