package org.sequeless.filter.api;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Configuration for a strict {@link FieldRegistry}, built via {@link FieldRegistry#of(FieldRegistrySpec)}.
 *
 * <p>Exists so the registry can gain further configuration knobs later without another
 * signature change on {@code FieldRegistry.of(...)}.
 */
@Value
@Builder
public class FieldRegistrySpec {

    /** Registered field definitions, in registration order. Empty means an empty strict registry. */
    @Builder.Default
    List<FieldDefinition> fields = List.of();

    /** Defensive-copy override for the Lombok-generated builder setter. */
    public static class FieldRegistrySpecBuilder {

        public FieldRegistrySpecBuilder fields(List<FieldDefinition> fields) {
            this.fields$value = List.copyOf(fields);
            this.fields$set = true;
            return this;
        }
    }
}
