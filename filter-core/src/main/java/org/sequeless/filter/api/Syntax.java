package org.sequeless.filter.api;

/** Controls how an operator is written in the DSL. */
public enum Syntax {
    /** {@code field op value} — classic infix notation. */
    INFIX,
    /** {@code field meets fnName(arg1, arg2)} — function-call notation. */
    FUNCTION
}
