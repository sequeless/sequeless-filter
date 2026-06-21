package org.sequeless.filter.api;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Describes an operator that can appear in a filter expression.
 *
 * <p>Operators are registered via {@link org.sequeless.filter.spi.OperatorContributor} and
 * looked up by canonical name or any alias via {@link OperatorRegistry}.
 */
@Value
@Builder
public class OperatorDefinition {

    /** Primary name used in serialized output and returned from the parser. */
    String canonicalName;

    /** Alternative DSL spellings that the parser accepts and resolves to {@link #canonicalName}. */
    @Builder.Default
    List<String> aliases = List.of();

    /** JSON Schema type strings (e.g. {@code "string"}, {@code "number"}) this operator applies to.
     *  Empty means applicable to all types. */
    @Builder.Default
    List<String> applicableTypes = List.of();

    /** JSON Schema format strings (e.g. {@code "date-time"}) this operator applies to.
     *  Empty means all formats. */
    @Builder.Default
    List<String> applicableFormats = List.of();

    Syntax syntax;

    /**
     * {@code true} for operators that take no right-hand-side value (e.g. {@code exists}).
     * The parser enforces this: using a unary operator with a value, or a binary operator
     * without a value, both produce a {@link FilterParseException}.
     */
    @Builder.Default
    boolean unary = false;

    /** Non-empty only for {@link Syntax#FUNCTION} operators. */
    @Builder.Default
    List<ParameterDefinition> parameters = List.of();
}
