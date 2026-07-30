package org.sequeless.filter.api;

/** The shape of value an operator expects on the right-hand side of a condition. */
public enum ValueShape {
    /** No value at all — unary operators such as {@code exists} / {@code does not exist}. */
    NONE,
    /** Exactly one JSON scalar: string, number, boolean, or null. */
    SCALAR,
    /** A JSON array — {@code is in}'s member list, or a {@link Syntax#FUNCTION} argument list. */
    LIST
}
