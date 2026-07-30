package org.sequeless.filter.api;

import lombok.Builder;
import lombok.Value;

/**
 * Context passed to a {@link CompletionProvider} when value suggestions are requested.
 */
@Value
@Builder
public class CompletionContext {

    String fieldPath;
    String jsonSchemaType;

    /** May be {@code null} when the field has no format constraint. */
    String jsonSchemaFormat;

    /** Canonical operator name. */
    String operator;

    /** Characters the user has already typed for the value; may be empty. */
    String partialValue;
}
