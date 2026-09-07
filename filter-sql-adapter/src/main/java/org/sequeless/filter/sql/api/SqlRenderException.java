package org.sequeless.filter.sql.api;

/**
 * Thrown by {@link FilterQueryTranslator#translate} when a {@link org.sequeless.filter.api.FilterNode}
 * cannot be translated to SQL.
 *
 * <p>Raised for: a {@link org.sequeless.filter.api.FilterValidator} violation detected on entry;
 * an explicitly-named field path with no {@link ColumnMapping} entry; a zero-operand
 * {@link org.sequeless.filter.api.AndFilter}/{@link org.sequeless.filter.api.OrFilter} encountered
 * at any depth (including one left empty after dropping unmapped {@code any}-expansion operands);
 * an empty converted value for a list-shaped operator (e.g. {@code is in []}); or a
 * {@link com.fasterxml.jackson.databind.JsonNode} value of an unrecognized subtype.
 */
public class SqlRenderException extends RuntimeException {

    public SqlRenderException(String message) {
        super(message);
    }

    public SqlRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
