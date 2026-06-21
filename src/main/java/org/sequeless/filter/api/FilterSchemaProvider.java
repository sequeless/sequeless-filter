package org.sequeless.filter.api;

import java.util.List;

/**
 * Provides schema-level information for building form UIs and dropdowns.
 *
 * <p>{@code availableOperators(path)} intersects the operators applicable to the
 * field's type with any explicit {@code permittedOperators} on the field definition.
 */
public final class FilterSchemaProvider {

    private final OperatorRegistry ops;
    private final FieldRegistry fields;

    public FilterSchemaProvider(OperatorRegistry ops, FieldRegistry fields) {
        this.ops = ops;
        this.fields = fields;
    }

    /** Returns all registered fields, ordered by registration. */
    public List<FieldDefinition> availableFields() {
        return fields.all();
    }

    /**
     * Returns operators available for the given field path.
     * Respects the field's {@code permittedOperators} allowlist when non-empty.
     *
     * @param fieldPath dot-notation field path
     * @return operators applicable to the field; empty if the field is not found
     */
    public List<OperatorDefinition> availableOperators(String fieldPath) {
        return fields.find(fieldPath)
                .map(f -> {
                    List<OperatorDefinition> byType =
                            ops.applicableTo(f.getJsonSchemaType(), f.getJsonSchemaFormat());
                    if (f.getPermittedOperators().isEmpty()) {
                        return byType;
                    }
                    return byType.stream()
                            .filter(op -> f.getPermittedOperators().contains(op.getCanonicalName()))
                            .toList();
                })
                .orElse(List.of());
    }

    /**
     * Returns value suggestions for the given field and operator by delegating to
     * the field's registered {@link CompletionProvider}.
     *
     * @param fieldPath       dot-notation field path
     * @param canonicalOperator canonical operator name
     * @param partial         characters already typed for the value
     * @return candidate values; empty when no provider is registered or the field is not found
     */
    public List<String> suggestValues(String fieldPath, String canonicalOperator, String partial) {
        return fields.find(fieldPath)
                .filter(f -> f.getCompletionProvider() != null)
                .map(f -> f.getCompletionProvider()
                        .complete(CompletionContext.builder()
                                .fieldPath(fieldPath)
                                .jsonSchemaType(f.getJsonSchemaType())
                                .jsonSchemaFormat(f.getJsonSchemaFormat())
                                .operator(canonicalOperator)
                                .partialValue(partial)
                                .build()))
                .orElse(List.of());
    }
}
