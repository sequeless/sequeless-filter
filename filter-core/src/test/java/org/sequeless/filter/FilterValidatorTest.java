package org.sequeless.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.AnyFilter;
import org.sequeless.filter.api.FieldDefinition;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.FieldRegistrySpec;
import org.sequeless.filter.api.FilterValidator;
import org.sequeless.filter.api.FilterViolation;
import org.sequeless.filter.api.Filters;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.api.ParameterDefinition;
import org.sequeless.filter.api.ParameterType;
import org.sequeless.filter.api.Syntax;
import org.sequeless.filter.spi.OperatorContributor;

class FilterValidatorTest {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private OperatorRegistry ops;
    private FieldRegistry fields;

    @BeforeEach
    void setUp() {
        FieldDefinition status = FieldDefinition.builder()
                .path("status")
                .jsonSchemaType("string")
                .build();
        FieldDefinition age =
                FieldDefinition.builder().path("age").jsonSchemaType("number").build();
        FieldDefinition deletedAt = FieldDefinition.builder()
                .path("deletedAt")
                .jsonSchemaType("string")
                .build();
        FieldDefinition restricted = FieldDefinition.builder()
                .path("restricted")
                .jsonSchemaType("string")
                .permittedOperators(List.of("is"))
                .build();
        FieldDefinition createdAt = FieldDefinition.builder()
                .path("createdAt")
                .jsonSchemaType("string")
                .jsonSchemaFormat("date-time")
                .build();

        fields = FieldRegistry.of(FieldRegistrySpec.builder()
                .fields(List.of(status, age, deletedAt, restricted, createdAt))
                .build());
        ops = OperatorRegistry.defaults();
    }

    private static ArrayNode arrayOf(String... values) {
        ArrayNode array = JSON.arrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    @Test
    void isInWithArrayValueIsValid() {
        FieldFilter filter = Filters.field("status", "is in", arrayOf("a", "b"));
        assertThat(FilterValidator.validate(filter, ops, fields)).isEmpty();
    }

    @Test
    void isInWithScalarValueIsRejected() {
        FieldFilter filter = Filters.field("status", "is in", TextNode.valueOf("open"));
        List<FilterViolation> violations = FilterValidator.validate(filter, ops, fields);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).message()).contains("expects a list value");
    }

    @Test
    void isWithArrayValueIsRejected() {
        FieldFilter filter = Filters.field("status", "is", arrayOf("a", "b"));
        List<FilterViolation> violations = FilterValidator.validate(filter, ops, fields);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).message()).contains("expects a single value");
    }

    @Test
    void isWithNullValueIsValid() {
        FieldFilter filter = Filters.field("deletedAt", "is", NullNode.getInstance());
        assertThat(FilterValidator.validate(filter, ops, fields)).isEmpty();
    }

    @Test
    void unaryOperatorWithNoValueIsValid() {
        FieldFilter filter = Filters.field("deletedAt", "exists");
        assertThat(FilterValidator.validate(filter, ops, fields)).isEmpty();
    }

    @Test
    void unaryOperatorWithValueIsRejected() {
        FieldFilter filter = new FieldFilter("deletedAt", "exists", TextNode.valueOf("x"));
        List<FilterViolation> violations = FilterValidator.validate(filter, ops, fields);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).message()).contains("takes no value");
    }

    @Test
    void functionSyntaxOperatorWithArrayArgsIsValid() {
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
        OperatorRegistry customOps = OperatorRegistry.of(List.of(custom));

        ArrayNode args = JSON.arrayNode();
        args.add(7);
        args.add("days");
        FieldFilter filter = Filters.field("createdAt", "withinLast", args);

        assertThat(FilterValidator.validate(filter, customOps, fields)).isEmpty();
    }

    @Test
    void anyWithArrayValueForIsInIsValid() {
        AnyFilter filter = Filters.any("is in", arrayOf("a", "b"));
        assertThat(FilterValidator.validate(filter, ops, fields)).isEmpty();
    }

    @Test
    void anyWithScalarValueForIsInIsRejectedWithNullPath() {
        AnyFilter filter = Filters.any("is in", TextNode.valueOf("open"));
        List<FilterViolation> violations = FilterValidator.validate(filter, ops, fields);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).path()).isNull();
        assertThat(violations.get(0).message()).contains("expects a list value");
    }

    @Test
    void unknownFieldIsRejected() {
        FieldFilter filter = Filters.field("nope", "is", TextNode.valueOf("x"));
        List<FilterViolation> violations = FilterValidator.validate(filter, ops, fields);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).message()).contains("Unknown field");
    }

    @Test
    void unknownOperatorIsRejected() {
        FieldFilter filter = Filters.field("status", "nope", TextNode.valueOf("x"));
        List<FilterViolation> violations = FilterValidator.validate(filter, ops, fields);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).message()).contains("Unknown operator");
    }

    @Test
    void operatorNotApplicableToTypeIsRejected() {
        FieldFilter filter = Filters.field("age", "contains", TextNode.valueOf("5"));
        List<FilterViolation> violations = FilterValidator.validate(filter, ops, fields);
        assertThat(violations).anyMatch(v -> v.message().contains("not applicable to type"));
    }

    @Test
    void operatorNotPermittedForFieldIsRejected() {
        FieldFilter filter = Filters.field("restricted", "is not", TextNode.valueOf("x"));
        List<FilterViolation> violations = FilterValidator.validate(filter, ops, fields);
        assertThat(violations).anyMatch(v -> v.message().contains("not permitted for field"));
    }
}
