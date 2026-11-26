package org.sequeless.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.spi.OperatorContributor;

class OperatorApplicabilityTest {

    private static final List<String> COMPARISONS =
            List.of("is greater than", "is greater than or equal to", "is less than", "is less than or equal to");

    private static final OperatorRegistry DEFAULTS = OperatorRegistry.defaults();

    @Test
    void applicableToStringDateIncludesAllFourComparisonOperators() {
        assertThat(DEFAULTS.applicableTo("string", "date"))
                .extracting(OperatorDefinition::getCanonicalName)
                .containsAll(COMPARISONS);
    }

    @Test
    void applicableToStringDateTimeIncludesGreaterThan() {
        assertThat(DEFAULTS.applicableTo("string", "date-time"))
                .extracting(OperatorDefinition::getCanonicalName)
                .contains("is greater than");
    }

    @Test
    void applicableToStringWithoutFormatExcludesComparisonOperators() {
        assertThat(DEFAULTS.applicableTo("string", null))
                .extracting(OperatorDefinition::getCanonicalName)
                .doesNotContainAnyElementsOf(COMPARISONS);
    }

    @Test
    void applicableToStringWithUriFormatExcludesComparisonOperators() {
        assertThat(DEFAULTS.applicableTo("string", "uri"))
                .extracting(OperatorDefinition::getCanonicalName)
                .doesNotContainAnyElementsOf(COMPARISONS);
    }

    @Test
    void applicableToIntegerWithoutFormatIncludesComparisonOperators() {
        assertThat(DEFAULTS.applicableTo("integer", null))
                .extracting(OperatorDefinition::getCanonicalName)
                .containsAll(COMPARISONS);
    }

    @Test
    void applicableToNumberWithoutFormatIncludesComparisonOperators() {
        assertThat(DEFAULTS.applicableTo("number", null))
                .extracting(OperatorDefinition::getCanonicalName)
                .containsAll(COMPARISONS);
    }

    @Test
    void applicableToStringDateStillIncludesEqualityAndContains() {
        assertThat(DEFAULTS.applicableTo("string", "date"))
                .extracting(OperatorDefinition::getCanonicalName)
                .contains("is", "is not", "contains", "starts with");
    }

    @Test
    void formatsOnlyOperatorRegistryRuleIsUnchanged() {
        OperatorDefinition formatsOnly = OperatorDefinition.builder()
                .canonicalName("onDateTime")
                .applicableFormats(List.of("date-time"))
                .build();
        OperatorContributor custom = () -> List.of(formatsOnly);
        OperatorRegistry ops = OperatorRegistry.of(List.of(custom));

        assertThat(ops.applicableTo("string", "date-time")).containsExactly(formatsOnly);
        assertThat(ops.applicableTo("integer", "date-time")).containsExactly(formatsOnly);
        assertThat(ops.applicableTo("string", null)).isEmpty();
        assertThat(ops.applicableTo("string", "uri")).isEmpty();
    }

    @Test
    void typesOnlyOperatorIgnoresFormat() {
        OperatorDefinition typesOnly = OperatorDefinition.builder()
                .canonicalName("stringy")
                .applicableTypes(List.of("string"))
                .build();

        assertThat(typesOnly.appliesTo("string", null)).isTrue();
        assertThat(typesOnly.appliesTo("string", "uri")).isTrue();
        assertThat(typesOnly.appliesTo("number", "date")).isFalse();
    }

    @Test
    void operatorWithBothListsMatchesOnTypeOrFormat() {
        OperatorDefinition both = OperatorDefinition.builder()
                .canonicalName("numberOrDate")
                .applicableTypes(List.of("number", "integer"))
                .applicableFormats(List.of("date"))
                .build();

        assertThat(both.appliesTo("integer", null)).isTrue();
        assertThat(both.appliesTo("number", "uri")).isTrue();
        assertThat(both.appliesTo("string", "date")).isTrue();
        assertThat(both.appliesTo("string", null)).isFalse();
        assertThat(both.appliesTo("string", "uri")).isFalse();
    }
}
