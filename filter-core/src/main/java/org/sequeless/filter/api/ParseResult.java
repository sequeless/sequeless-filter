package org.sequeless.filter.api;

/**
 * Result of {@link FilterParser#parsePartial}. Either a fully-parsed expression or a
 * best-effort partial parse with a hint for auto-completion at the cursor position.
 */
public sealed interface ParseResult permits ParseResult.Complete, ParseResult.Partial {

    /** The input parsed successfully into a complete filter expression. */
    record Complete(FilterNode ast) implements ParseResult {}

    /**
     * The input is incomplete or contains an error. Provides a best-effort AST fragment
     * (may be {@code null}) and a {@link CompletionHint} at the cursor position.
     */
    record Partial(FilterNode bestEffortAst, CompletionHint hint) implements ParseResult {}
}
