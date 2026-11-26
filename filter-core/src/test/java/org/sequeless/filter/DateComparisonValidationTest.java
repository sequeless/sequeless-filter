package org.sequeless.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.TextNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.FieldDefinition;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.FieldRegistrySpec;
import org.sequeless.filter.api.FilterValidator;
import org.sequeless.filter.api.FilterViolation;
import org.sequeless.filter.api.Filters;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.api.Syntax;
import org.sequeless.filter.spi.OperatorContributor;

class DateComparisonValidationTest {

    private static final OperatorRegistry OPS = OperatorRegistry.defaults();

    private static FieldRegistry fieldRegistry(String type, String format) {
        FieldDefinition field = FieldDefinition.builder()
                .path("when")
                .jsonSchemaType(type)
                .jsonSchemaFormat(format)
                .build();
        return FieldRegistry.of(
                FieldRegistrySpec.builder().fields(List.of(field)).build());
    }

    private static List<FilterViolation> validate(String op, String type, String format) {
        FieldFilter filter = Filters.field("when", op, TextNode.valueOf("2026-01-01"));
        return FilterValidator.validate(filter, OPS, fieldRegistry(type, format));
    }

    @Test
    void dateFieldAcceptsGreaterThanOrEqual() {
        assertThat(validate("gte", "string", "date")).isEmpty();
    }

    @Test
    void dateFieldAcceptsLessThan() {
        assertThat(validate("lt", "string", "date")).isEmpty();
    }

    @Test
    void dateFieldAcceptsGreaterThanAndLessThanOrEqual() {
        assertThat(validate("gt", "string", "date")).isEmpty();
        assertThat(validate("lte", "string", "date")).isEmpty();
    }

    @Test
    void dateFieldEqualityAndContainsBehaveAsBefore() {
        assertThat(validate("is", "string", "date")).isEmpty();
        assertThat(validate("contains", "string", "date")).isEmpty();
    }

    @Test
    void dateTimeFieldAcceptsGreaterThan() {
        assertThat(validate("gt", "string", "date-time")).isEmpty();
    }

    @Test
    void plainStringFieldWithoutFormatRejectsGreaterThanOrEqual() {
        List<FilterViolation> violations = validate("gte", "string", null);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).message()).isEqualTo("Operator 'gte' is not applicable to type 'string'");
    }

    @Test
    void uriStringFieldRejectsGreaterThanOrEqual() {
        List<FilterViolation> violations = validate("gte", "string", "uri");
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).message()).contains("not applicable to type 'string'");
    }

    @Test
    void integerFieldWithoutFormatAcceptsAllComparisonOperators() {
        for (String op : List.of("gt", "gte", "lt", "lte")) {
            assertThat(validate(op, "integer", null)).as(op).isEmpty();
        }
    }

    @Test
    void numberFieldWithoutFormatAcceptsAllComparisonOperators() {
        for (String op : List.of("gt", "gte", "lt", "lte")) {
            assertThat(validate(op, "number", null)).as(op).isEmpty();
        }
    }

    @Test
    void formatsOnlyOperatorStillIgnoresFieldFormatInValidator() {
        OperatorDefinition formatsOnly = OperatorDefinition.builder()
                .canonicalName("onDateTime")
                .syntax(Syntax.INFIX)
                .applicableFormats(List.of("date-time"))
                .build();
        OperatorContributor custom = () -> List.of(formatsOnly);
        OperatorRegistry customOps = OperatorRegistry.of(List.of(custom));
        FieldFilter filter = Filters.field("when", "onDateTime", TextNode.valueOf("x"));

        assertThat(FilterValidator.validate(filter, customOps, fieldRegistry("string", null)))
                .isEmpty();
    }
}
