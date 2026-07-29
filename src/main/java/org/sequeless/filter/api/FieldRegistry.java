package org.sequeless.filter.api;

import java.util.List;
import java.util.Optional;
import org.sequeless.filter.internal.DefaultFieldRegistry;

/**
 * Queryable collection of {@link FieldDefinition}s.
 *
 * <p>Always required by the parser. Build one with {@link #of(FieldRegistrySpec)}, or supply your
 * own implementation.
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
     * Builds a strict registry from the given spec.
     * The parser will reject any path not present in {@link FieldRegistrySpec#getFields()}.
     */
    static FieldRegistry of(FieldRegistrySpec spec) {
        return new DefaultFieldRegistry(spec);
    }
}
