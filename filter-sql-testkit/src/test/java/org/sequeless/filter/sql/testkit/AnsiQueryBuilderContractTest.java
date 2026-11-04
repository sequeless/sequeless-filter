package org.sequeless.filter.sql.testkit;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.sequeless.filter.sql.api.SqlFragment;
import org.sequeless.filter.sql.spi.AnsiQueryBuilder;
import org.sequeless.filter.sql.spi.QueryBuilder;

/**
 * Runs the shared contract against the ANSI default builder, on an in-memory H2 database. H2 is the
 * ANSI check: it needs no Docker. Postgres runs the same contract on a real server in
 * {@code filter-sql-postgres-adapter} (spec 6, section 9).
 *
 * <p>The SQL runner lives here, in the test sources, so the shipped contract in {@code src/main}
 * runs no SQL of its own.
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

    @Override
    protected List<Integer> matchingIds(SqlFragment fragment) throws SQLException {
        try (Connection c = connect();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT \"id\" FROM " + TABLE + " WHERE " + fragment.sql() + " ORDER BY \"id\"")) {
            List<Object> params = fragment.parameters();
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            List<Integer> ids = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
            return ids;
        }
    }
}
