package org.sequeless.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.FilterParseException;
import org.sequeless.filter.api.FilterParser;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.api.ParameterDefinition;
import org.sequeless.filter.api.ParameterType;
import org.sequeless.filter.api.Syntax;
import org.sequeless.filter.spi.OperatorContributor;

class FunctionArgValidationTest {

    private OperatorRegistry ops;
    private FieldRegistry fields;

    @BeforeEach
    void setUp() {
        OperatorDefinition withinLast = OperatorDefinition.builder()
                .canonicalName("withinLast")
                .syntax(Syntax.FUNCTION)
                .applicableTypes(List.of("string"))
                .applicableFormats(List.of("date-time"))
                .parameters(List.of(
                        ParameterDefinition.builder()
                                .name("amount")
                                .type(ParameterType.INT)
                                .build(),
                        ParameterDefinition.builder()
                                .name("unit")
                                .type(ParameterType.ENUM)
                                .allowedValues(List.of("days", "hours", "minutes", "seconds"))
                                .build()))
                .build();

        OperatorContributor custom = () -> List.of(withinLast);
        ops = OperatorRegistry.of(List.of(custom));
        fields = FieldRegistry.permissive();
    }

    @Test
    void validFunctionCallParsesSuccessfully() {
        var result = FilterParser.parse("createdAt meets withinLast(7, days)", ops, fields);
        assertThat(result).isNotNull();
    }

    @Test
    void wrongArgCountThrows() {
        assertThatThrownBy(() -> FilterParser.parse("createdAt meets withinLast(7)", ops, fields))
                .isInstanceOf(FilterParseException.class)
                .hasMessageContaining("expects 2");
    }

    @Test
    void wrongArgTypeThrowsForIntParam() {
        assertThatThrownBy(() -> FilterParser.parse("createdAt meets withinLast('seven', days)", ops, fields))
                .isInstanceOf(FilterParseException.class)
                .hasMessageContaining("integer");
    }

    @Test
    void invalidEnumValueThrows() {
        assertThatThrownBy(() -> FilterParser.parse("createdAt meets withinLast(7, weeks)", ops, fields))
                .isInstanceOf(FilterParseException.class)
                .hasMessageContaining("weeks");
    }
}
