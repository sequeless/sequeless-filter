package org.sequeless.filter.sql.api;

import java.util.Optional;

/**
 * Resolves a filter's dot-notation field path to a {@link Field} (spec 6, section 3.3).
 *
 * <p>{@link ColumnMapping} is the trivial implementation: every resolved path is a
 * {@link Field.Column}. A caller that needs a computed field, such as a JSONB property read,
 * implements this interface directly and returns a {@link Field.Expression}.
 *
 * <p>Implementations are called once per filter path during translation, so they should be cheap.
 */
@FunctionalInterface
public interface FieldResolver {

    /**
     * @param path dot-notation field path, as it appears in the filter AST
     * @return the field to render, or empty if {@code path} has no mapping
     */
    Optional<Field> resolve(String path);
}
