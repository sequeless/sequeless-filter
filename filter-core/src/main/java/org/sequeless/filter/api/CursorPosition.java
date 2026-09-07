package org.sequeless.filter.api;

/** Describes where in the DSL expression the cursor sits, used to drive completions. */
public enum CursorPosition {
    /** Cursor is at or completing a field path (or {@code any}). */
    FIELD,
    /** Cursor is at or completing an infix operator phrase. */
    OPERATOR,
    /** Cursor is at or completing a function name after {@code meets}. */
    FUNCTION_NAME,
    /** Cursor is inside a function argument list. */
    FUNCTION_ARG,
    /** Cursor is at or completing the right-hand-side value. */
    VALUE,
    /** Cursor follows a complete condition; expects {@code and} / {@code or}. */
    BOOLEAN_OP
}
