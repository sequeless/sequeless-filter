package org.sequeless.filter.sql.h2;

import org.sequeless.filter.sql.spi.AnsiQueryBuilder;

/**
 * Test-scope {@link AnsiQueryBuilder} subclass for the H2-backed tests.
 *
 * <p>H2 accepts the default ANSI {@code "…"} quoting as-is, so this override isn't required for
 * correctness against H2 — it exists solely to exercise the Template Method extension path (per
 * T7) with a real database round-trip, not because H2 needs different behavior. The override
 * re-implements the same {@code "…"}-with-doubled-quotes contract independently rather than
 * delegating to {@code super}, so the test coverage actually exercises this class's own code path.
 */
public class H2QueryBuilder extends AnsiQueryBuilder {

    @Override
    protected String quoteIdentifier(String column) {
        StringBuilder quoted = new StringBuilder(column.length() + 2).append('"');
        for (int i = 0; i < column.length(); i++) {
            char c = column.charAt(i);
            if (c == '"') {
                quoted.append('"');
            }
            quoted.append(c);
        }
        return quoted.append('"').toString();
    }
}
