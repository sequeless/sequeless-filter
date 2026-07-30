package org.sequeless.filter.api;

/**
 * Thrown when the input string cannot be parsed into a valid {@link FilterNode} AST.
 * Unchecked so callers are not forced to handle parse errors at every call site.
 */
public class FilterParseException extends RuntimeException {

    private final int offset;

    public FilterParseException(String message, int offset) {
        super(message);
        this.offset = offset;
    }

    public FilterParseException(String message, int offset, Throwable cause) {
        super(message, cause);
        this.offset = offset;
    }

    /** Character offset in the source string where the error was detected. */
    public int getOffset() {
        return offset;
    }
}
