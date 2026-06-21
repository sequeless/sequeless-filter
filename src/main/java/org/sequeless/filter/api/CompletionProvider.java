package org.sequeless.filter.api;

import java.util.List;

/**
 * Supplies value completion candidates for a specific field and operator.
 * Implementations may query a database, call an external API, or return a static list.
 */
@FunctionalInterface
public interface CompletionProvider {

    /**
     * Returns candidate values for the given context.
     *
     * @param context details about the field, operator, and partial input typed so far
     * @return candidate strings; empty list if no suggestions are available
     */
    List<String> complete(CompletionContext context);
}
