package org.sequeless.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.CompletionEngine;
import org.sequeless.filter.api.FieldDefinition;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.OperatorRegistry;

class CompletionEngineTest {

    private CompletionEngine engine;

    @BeforeEach
    void setUp() {
        FieldDefinition statusField = FieldDefinition.builder()
                .path("status")
                .jsonSchemaType("string")
                .completionProvider(ctx -> List.of("active", "pending", "closed"))
                .build();
        FieldDefinition ageField =
                FieldDefinition.builder().path("age").jsonSchemaType("number").build();

        FieldRegistry fields = FieldRegistry.of(List.of(statusField, ageField));
        OperatorRegistry ops = OperatorRegistry.defaults();
        engine = new CompletionEngine(ops, fields);
    }

    @Test
    void suggestsFieldsAtStart() {
        List<String> candidates = engine.complete("", 0);
        assertThat(candidates).contains("status", "age", "any");
    }

    @Test
    void suggestsFieldsAfterBooleanOperator() {
        List<String> candidates = engine.complete("status is 'active' and ", 23);
        assertThat(candidates).contains("status", "age");
    }

    @Test
    void suggestsOperatorsAfterField() {
        List<String> candidates = engine.complete("status ", 7);
        assertThat(candidates).contains("is", "is not", "contains", "starts with");
        assertThat(candidates).contains("meets");
    }

    @Test
    void suggestsOnlyTypeCompatibleOperators() {
        // 'age' is a number field — string-only ops should not appear
        List<String> candidates = engine.complete("age ", 4);
        assertThat(candidates).contains("is greater than", "is less than");
        assertThat(candidates).doesNotContain("contains", "starts with");
    }

    @Test
    void suggestsValuesAfterOperator() {
        // the status field has a CompletionProvider
        List<String> candidates = engine.complete("status is ", 10);
        assertThat(candidates).contains("active", "pending", "closed");
    }

    @Test
    void suggestsBooleanOpsAfterCompleteCondition() {
        List<String> candidates = engine.complete("status is 'active' ", 19);
        assertThat(candidates).containsExactlyInAnyOrder("and", "or");
    }
}
