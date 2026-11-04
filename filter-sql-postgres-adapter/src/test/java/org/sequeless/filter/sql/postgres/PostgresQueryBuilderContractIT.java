package org.sequeless.filter.sql.postgres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.sequeless.filter.sql.api.SqlFragment;
import org.sequeless.filter.sql.spi.QueryBuilder;
import org.sequeless.filter.sql.testkit.QueryBuilderContract;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the shared filter contract against a real PostgreSQL 18 server, through Testcontainers. This
 * is the dialect check for {@code ILIKE}: the same rows must match as on the ANSI builder. It is an
 * {@code IT}, so failsafe runs it in {@code verify}, on a runner with Docker.
 */
@Testcontainers
class PostgresQueryBuilderContractIT extends QueryBuilderContract {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Override
    protected QueryBuilder builder() {
        return new PostgresQueryBuilder();
    }

    @Override
    protected Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
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
