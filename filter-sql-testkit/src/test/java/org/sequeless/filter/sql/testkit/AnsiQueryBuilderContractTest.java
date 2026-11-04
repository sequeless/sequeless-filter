package org.sequeless.filter.sql.testkit;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.sequeless.filter.sql.spi.AnsiQueryBuilder;
import org.sequeless.filter.sql.spi.QueryBuilder;

/**
 * Runs the shared contract against the ANSI default builder, on an in-memory H2 database. H2 is the
 * ANSI check: it needs no Docker. Postgres runs the same contract on a real server in
 * {@code filter-sql-postgres-adapter} (spec 6, section 9).
 */
class AnsiQueryBuilderContractTest extends QueryBuilderContract {

    @Override
    protected QueryBuilder builder() {
        return new AnsiQueryBuilder();
    }

    @Override
    protected Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:h2:mem:filter_contract;DB_CLOSE_DELAY=-1");
    }
}
