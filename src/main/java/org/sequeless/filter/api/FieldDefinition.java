package org.sequeless.filter.api;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Describes a filterable field: its dot-notation path, JSON Schema type/format,
 * the operators that may be used with it, and an optional value completion provider.
 */
@Value
@Builder
public class FieldDefinition {

    /** Dot-notation path, e.g. {@code "lineItems.qty"}. */
    String path;

    /** JSON Schema type string, e.g. {@code "string"}, {@code "number"}, {@code "boolean"}. */
    String jsonSchemaType;

    /** JSON Schema format string, e.g. {@code "date-time"}, or {@code null}. */
    String jsonSchemaFormat;

    /**
     * Explicit allowlist of canonical operator names. Empty means all operators applicable
     * to this field's type are permitted.
     */
    @Builder.Default
    List<String> permittedOperators = List.of();

    /**
     * Optional provider for value completion candidates.
     * May be {@code null} when the embedder does not need value suggestions for this field.
     */
    CompletionProvider completionProvider;

    /** Defensive-copy override for the Lombok-generated builder setter. */
    public static class FieldDefinitionBuilder {

        public FieldDefinitionBuilder permittedOperators(List<String> permittedOperators) {
            this.permittedOperators$value = List.copyOf(permittedOperators);
            this.permittedOperators$set = true;
            return this;
        }
    }
}
