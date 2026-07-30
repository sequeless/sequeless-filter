package org.sequeless.filter.api;

import java.util.List;
import java.util.Optional;
import org.sequeless.filter.internal.BuiltinOperatorContributor;
import org.sequeless.filter.internal.DefaultOperatorRegistry;
import org.sequeless.filter.spi.OperatorContributor;

/**
 * Queryable collection of {@link OperatorDefinition}s assembled from one or more
 * {@link OperatorContributor}s.
 *
 * <p>Duplicate canonical names or aliases across contributors cause an
 * {@link IllegalArgumentException} at registry construction time (fail-fast).
 */
public interface OperatorRegistry {

    /**
     * Looks up an operator by its canonical name or any registered alias.
     *
     * @param nameOrAlias canonical name or DSL alias
     * @return the matching definition, or empty if not found
     */
    Optional<OperatorDefinition> findByCanonicalOrAlias(String nameOrAlias);

    /** Returns every registered operator in registration order. */
    List<OperatorDefinition> all();

    /**
     * Returns operators applicable to the given JSON Schema type and optional format.
     *
     * @param jsonSchemaType  e.g. {@code "string"}, {@code "number"}
     * @param jsonSchemaFormat e.g. {@code "date-time"}, or {@code null} for any format
     */
    List<OperatorDefinition> applicableTo(String jsonSchemaType, String jsonSchemaFormat);

    /** Registry containing only the built-in operators shipped with this module. */
    static OperatorRegistry defaults() {
        return of(List.of(new BuiltinOperatorContributor()));
    }

    /**
     * Builds a registry from the given contributors.
     *
     * @param contributors list of contributors; duplicate names or aliases throw
     *                     {@link IllegalArgumentException}
     */
    static OperatorRegistry of(List<OperatorContributor> contributors) {
        return new DefaultOperatorRegistry(contributors);
    }
}
