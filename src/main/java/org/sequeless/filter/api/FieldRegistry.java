package org.sequeless.filter.api;

import java.util.List;
import java.util.Optional;
import org.sequeless.filter.internal.DefaultFieldRegistry;
import org.sequeless.filter.internal.PermissiveFieldRegistry;

/**
 * Queryable collection of {@link FieldDefinition}s.
 *
 * <p>Always required by the parser. Use {@link #permissive()} to skip field-path validation.
 */
public interface FieldRegistry {

    /**
     * Looks up a field by its dot-notation path.
     *
     * @param path the field path to look up
     * @return the matching definition, or empty if not registered
     */
    Optional<FieldDefinition> find(String path);

    /** Returns every registered field in registration order. */
    List<FieldDefinition> all();

    /**
     * Returns fields that may be used with the given operator.
     *
     * <p>A field is compatible when its {@code permittedOperators} list either contains
     * {@code canonicalOperator} or is empty <em>and</em> the operator's {@code applicableTypes}
     * include the field's {@code jsonSchemaType}.
     *
     * @param canonicalOperator canonical operator name
     * @param operators         registry used to resolve the operator definition
     */
    List<FieldDefinition> compatibleWith(String canonicalOperator, OperatorRegistry operators);

    /**
     * Whether this registry skips field-path validation.
     * Returns {@code false} for the default strict registry.
     */
    default boolean isPermissive() {
        return false;
    }

    /**
     * Builds a strict registry from the given field definitions.
     * The parser will reject any path not present in this list.
     */
    static FieldRegistry of(List<FieldDefinition> fields) {
        return new DefaultFieldRegistry(fields);
    }

    /**
     * No-op registry that accepts any valid dot-notation path without validation.
     * Use when field-path validation is not desired.
     */
    static FieldRegistry permissive() {
        return PermissiveFieldRegistry.INSTANCE;
    }
}
