package org.sequeless.filter.internal;

import java.util.List;
import java.util.Optional;
import org.sequeless.filter.api.FieldDefinition;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.OperatorRegistry;

/**
 * No-op {@link FieldRegistry} that accepts any well-formed dot-notation path.
 * Field validation is disabled; {@link #compatibleWith} always returns an empty list.
 */
public final class PermissiveFieldRegistry implements FieldRegistry {

    public static final PermissiveFieldRegistry INSTANCE = new PermissiveFieldRegistry();

    private static final FieldDefinition WILDCARD = FieldDefinition.builder()
            .path("*")
            .jsonSchemaType("string")
            .build();

    private PermissiveFieldRegistry() {}

    @Override
    public Optional<FieldDefinition> find(String path) {
        return Optional.of(
                FieldDefinition.builder().path(path).jsonSchemaType("string").build());
    }

    @Override
    public List<FieldDefinition> all() {
        return List.of();
    }

    @Override
    public List<FieldDefinition> compatibleWith(String canonicalOperator, OperatorRegistry operators) {
        return List.of();
    }

    @Override
    public boolean isPermissive() {
        return true;
    }
}
