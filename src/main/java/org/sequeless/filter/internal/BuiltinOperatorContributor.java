package org.sequeless.filter.internal;

import java.util.List;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.Syntax;
import org.sequeless.filter.spi.OperatorContributor;

/** Registers the standard set of filter operators. */
public class BuiltinOperatorContributor implements OperatorContributor {

    private static final List<String> ALL_TYPES = List.of();
    private static final List<String> NUMBER_TYPE = List.of("number", "integer");
    private static final List<String> STRING_TYPE = List.of("string");

    @Override
    public List<OperatorDefinition> operators() {
        return List.of(
                op("is", List.of("=", "equals", "is equal to", "equal to", "is null", "is not null"), ALL_TYPES),
                op("is not", List.of("!=", "not equals", "is not equal to"), ALL_TYPES),
                op("is greater than", List.of(">", "gt"), NUMBER_TYPE),
                op("is greater than or equal to", List.of(">=", "gte"), NUMBER_TYPE),
                op("is less than", List.of("<", "lt"), NUMBER_TYPE),
                op("is less than or equal to", List.of("<=", "lte"), NUMBER_TYPE),
                op("contains", List.of(), STRING_TYPE),
                op("starts with", List.of("starts_with"), STRING_TYPE),
                op("is like", List.of("like"), STRING_TYPE),
                op("is not like", List.of("not like"), STRING_TYPE),
                op("is in", List.of("in"), ALL_TYPES),
                unary("exists", List.of(), ALL_TYPES),
                unary("does not exist", List.of("not exists"), ALL_TYPES));
    }

    private static OperatorDefinition op(String canonical, List<String> aliases, List<String> types) {
        return OperatorDefinition.builder()
                .canonicalName(canonical)
                .aliases(aliases)
                .applicableTypes(types)
                .syntax(Syntax.INFIX)
                .build();
    }

    private static OperatorDefinition unary(
            String canonical, List<String> aliases, List<String> types) {
        return OperatorDefinition.builder()
                .canonicalName(canonical)
                .aliases(aliases)
                .applicableTypes(types)
                .syntax(Syntax.INFIX)
                .unary(true)
                .build();
    }
}
