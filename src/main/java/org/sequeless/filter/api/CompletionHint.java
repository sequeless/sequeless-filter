package org.sequeless.filter.api;

import lombok.Builder;
import lombok.Value;

/**
 * Populated by {@link FilterParser#parsePartial} to describe what the user is currently typing.
 */
@Value
@Builder
public class CompletionHint {

    int cursorOffset;
    CursorPosition position;

    /** Non-null when {@link CursorPosition#OPERATOR}, {@link CursorPosition#VALUE},
     *  or {@link CursorPosition#FUNCTION_NAME} — the field the operator applies to. */
    String fieldPath;

    /** Non-null when {@link CursorPosition#VALUE} or {@link CursorPosition#FUNCTION_ARG}. */
    OperatorDefinition operator;

    /**
     * Zero-based index of the function argument the cursor is at.
     * {@code -1} unless position is {@link CursorPosition#FUNCTION_ARG}.
     */
    @Builder.Default
    int argIndex = -1;
}
