package org.sequeless.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.CompletionContext;
import org.sequeless.filter.api.CompletionProvider;
import org.sequeless.filter.api.FieldDefinition;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.FieldRegistrySpec;
import org.sequeless.filter.api.FilterSchemaProvider;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;

class FilterSchemaProviderTest {

    private FieldRegistry fields;
    private OperatorRegistry ops;
    private FilterSchemaProvider provider;

    @BeforeEach
    void setUp() {
        FieldDefinition statusField = FieldDefinition.builder()
                .path("status")
                .jsonSchemaType("string")
                .build();
        FieldDefinition ageField = FieldDefinition.builder()
                .path("age")
                .jsonSchemaType("number")
                .permittedOperators(List.of("is greater than", "is less than"))
                .build();

        fields = FieldRegistry.of(FieldRegistrySpec.builder()
                .fields(List.of(statusField, ageField))
                .build());
        ops = OperatorRegistry.defaults();
        provider = new FilterSchemaProvider(ops, fields);
    }

    @Test
    void returnsAvailableFields() {
        assertThat(provider.availableFields())
                .extracting(FieldDefinition::getPath)
                .containsExactly("status", "age");
    }

    @Test
    void operatorsAreFilteredByType() {
        List<OperatorDefinition> statusOps = provider.availableOperators("status");
        assertThat(statusOps)
                .extracting(OperatorDefinition::getCanonicalName)
                .contains("contains", "starts with", "is", "is not");
        // numeric-only ops should not appear for string field
        assertThat(statusOps).extracting(OperatorDefinition::getCanonicalName).doesNotContain("is greater than");
    }

    @Test
    void permittedOperatorsLimitResults() {
        List<OperatorDefinition> ageOps = provider.availableOperators("age");
        assertThat(ageOps)
                .extracting(OperatorDefinition::getCanonicalName)
                .containsExactlyInAnyOrder("is greater than", "is less than");
    }

    @Test
    void suggestValuesDelegatesToCompletionProvider() {
        CompletionProvider mockProvider = mock(CompletionProvider.class);
        when(mockProvider.complete(any(CompletionContext.class))).thenReturn(List.of("active", "pending"));

        FieldDefinition statusWithProvider = FieldDefinition.builder()
                .path("status")
                .jsonSchemaType("string")
                .completionProvider(mockProvider)
                .build();
        FieldRegistry fieldsWithProvider = FieldRegistry.of(
                FieldRegistrySpec.builder().fields(List.of(statusWithProvider)).build());
        FilterSchemaProvider p = new FilterSchemaProvider(ops, fieldsWithProvider);

        List<String> suggestions = p.suggestValues("status", "is", "act");
        assertThat(suggestions).containsExactly("active", "pending");
        verify(mockProvider).complete(any(CompletionContext.class));
    }

    @Test
    void suggestValuesReturnsEmptyWhenNoProvider() {
        assertThat(provider.suggestValues("age", "is greater than", "")).isEmpty();
    }

    @Test
    void returnsEmptyForUnknownField() {
        assertThat(provider.availableOperators("nonexistent")).isEmpty();
    }
}
