package org.sequeless.filter.sql.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.sql.api.Field;
import org.sequeless.filter.sql.api.SqlFragment;
import org.sequeless.filter.sql.api.SqlRenderException;
import org.sequeless.filter.sql.spi.QueryBuilder;

/**
 * The shared contract for every {@link QueryBuilder} (spec 6, section 3.3): the operator semantics
 * are pinned by the rows a dialect returns, not by its SQL text.
 *
 * <p>Each subclass supplies a builder and a connection to a database of its dialect. The contract
 * creates and fills its own table, renders each operator through the builder, runs the SQL, and
 * asserts which fixture rows match. Fixtures are ASCII, so folding and escaping are the only
 * differences a dialect can show; the contract checks the shared rules, and a dialect-specific test
 * adds its own.
 *
 * <p>The rules pinned here are the owner's decision of 2026-11-04: {@code is not} and
 * {@code is not like} are null-safe complements (they include NULL fields); the LIKE family is
 * case-insensitive and escapes literal wildcards for {@code contains} and {@code starts with}; a
 * {@code null} value is rejected for comparisons and the LIKE family.
 */
public abstract class QueryBuilderContract {

    /** The table the contract creates. Every subclass must be able to create it. */
    protected static final String TABLE = "\"filter_contract\"";

    private static final String STATUS = "status";
    private static final String CLOSED = "closed";
    private static final String CONTAINS = "contains";
    private static final String IS_IN = "is in";

    private final OperatorRegistry ops = OperatorRegistry.defaults();

    /**
     * @return the builder under test
     */
    protected abstract QueryBuilder builder();

    /**
     * @return a fresh connection to a database of the dialect under test; the caller closes it
     * @throws SQLException if the connection cannot be opened
     */
    protected abstract Connection connect() throws SQLException;

    @BeforeEach
    void fillFixtures() throws SQLException {
        try (Connection c = connect();
                var statement = c.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + TABLE);
            // Quoted identifiers, as the builder quotes columns: H2 and Postgres then match them exactly.
            statement.execute("CREATE TABLE " + TABLE
                    + " (\"id\" INT PRIMARY KEY, \"name\" VARCHAR(100), \"status\" VARCHAR(20), \"age\" INT)");
            insert(c, 1, "Widget", "open", 30);
            insert(c, 2, "gadget", CLOSED, 40);
            insert(c, 3, "Gizmo", null, 50);
            insert(c, 4, "widget pro", "open", null);
            insert(c, 5, "50%off", "open", 10);
            insert(c, 6, "a_c", CLOSED, 20);
            insert(c, 7, null, "open", 60);
        }
    }

    // -- binary comparisons ------------------------------------------------------------------

    /** {@code is not} is null-safe: a NULL status is included, which a plain {@code <>} would drop. */
    @Test
    void isNotIncludesNullFields() throws SQLException {
        assertThat(matchBinary(STATUS, "is not", "open")).containsExactly(2, 3, 6);
    }

    /** {@code is} drops NULL fields, as a plain equality does. */
    @Test
    void isDropsNullFields() throws SQLException {
        assertThat(matchBinary(STATUS, "is", "open")).containsExactly(1, 4, 5, 7);
    }

    /** {@code is not null} keeps the non-NULL rows. */
    @Test
    void isNotNullKeepsNonNullRows() throws SQLException {
        assertThat(matchBinary(STATUS, "is not", null)).containsExactly(1, 2, 4, 5, 6, 7);
    }

    /** {@code is null} keeps only the NULL rows. */
    @Test
    void isNullKeepsNullRows() throws SQLException {
        assertThat(matchBinary(STATUS, "is", null)).containsExactly(3);
    }

    /** A comparison drops NULL fields. */
    @Test
    void comparisonDropsNullFields() throws SQLException {
        assertThat(matchBinary("age", "is greater than", 35)).containsExactly(2, 3, 7);
    }

    /** A comparison cannot take a null value: it would match nothing, so it is rejected. */
    @Test
    void comparisonRejectsNullValue() {
        assertThatThrownBy(() -> builder().binary(new Field.Column("age"), op("is greater than"), null))
                .isInstanceOf(SqlRenderException.class);
    }

    // -- the LIKE family ---------------------------------------------------------------------

    /** {@code contains} is case-insensitive. */
    @Test
    void containsIsCaseInsensitive() throws SQLException {
        assertThat(matchBinary("name", CONTAINS, "WIDGET")).containsExactly(1, 4);
    }

    /** A literal {@code %} in a {@code contains} value matches only itself. */
    @Test
    void containsEscapesPercent() throws SQLException {
        assertThat(matchBinary("name", CONTAINS, "%")).containsExactly(5);
    }

    /** A literal {@code _} in a {@code contains} value matches only itself. */
    @Test
    void containsEscapesUnderscore() throws SQLException {
        assertThat(matchBinary("name", CONTAINS, "_")).containsExactly(6);
    }

    /** {@code starts with} escapes its value too. */
    @Test
    void startsWithEscapesUnderscore() throws SQLException {
        assertThat(matchBinary("name", "starts with", "a_")).containsExactly(6);
    }

    /** {@code is like} keeps the caller's wildcards and is case-insensitive. */
    @Test
    void isLikeKeepsWildcardsAndIsCaseInsensitive() throws SQLException {
        assertThat(matchBinary("name", "is like", "w%")).containsExactly(1, 4);
    }

    /** {@code is not like} is the complement of {@code is like}: a NULL name is included. */
    @Test
    void isNotLikeIncludesNullFields() throws SQLException {
        assertThat(matchBinary("name", "is not like", "w%")).containsExactly(2, 3, 5, 6, 7);
    }

    /** The LIKE family cannot take a null value. */
    @Test
    void likeRejectsNullValue() {
        assertThatThrownBy(() -> builder().binary(new Field.Column("name"), op(CONTAINS), null))
                .isInstanceOf(SqlRenderException.class);
    }

    // -- 'is in' and the unary operators -----------------------------------------------------

    /** {@code is in} with a plain list matches its members. */
    @Test
    void isInMatchesMembers() throws SQLException {
        assertThat(matchBinary(STATUS, IS_IN, Arrays.asList(CLOSED))).containsExactly(2, 6);
    }

    /** {@code is in [a, null]} equals {@code is a or is null}. */
    @Test
    void isInWithNullMemberAlsoMatchesNullFields() throws SQLException {
        assertThat(matchBinary(STATUS, IS_IN, Arrays.asList("open", null))).containsExactly(1, 3, 4, 5, 7);
    }

    /** {@code is in [null]} means the field is NULL. */
    @Test
    void isInOfOnlyNullMatchesNullFields() throws SQLException {
        assertThat(matchBinary(STATUS, IS_IN, Arrays.asList((Object) null))).containsExactly(3);
    }

    /** {@code exists} keeps the rows whose field is set. */
    @Test
    void existsKeepsSetFields() throws SQLException {
        assertThat(matchUnary("age", "exists")).containsExactly(1, 2, 3, 5, 6, 7);
    }

    /** {@code does not exist} keeps the rows whose field is NULL. */
    @Test
    void doesNotExistKeepsNullFields() throws SQLException {
        assertThat(matchUnary("age", "does not exist")).containsExactly(4);
    }

    // -- helpers ------------------------------------------------------------------------------

    private OperatorDefinition op(String name) {
        return ops.findByCanonicalOrAlias(name).orElseThrow();
    }

    private List<Integer> matchBinary(String column, String operator, Object value) throws SQLException {
        return matchingIds(builder().binary(new Field.Column(column), op(operator), value));
    }

    private List<Integer> matchUnary(String column, String operator) throws SQLException {
        return matchingIds(builder().unary(new Field.Column(column), op(operator)));
    }

    /**
     * Runs the rendered condition against the fixture table and returns the matching ids, in
     * ascending order. The subclass owns the query: it selects {@code "id"} from {@value #TABLE}
     * {@code WHERE} the fragment's SQL, binding the fragment's parameters in order. The contract
     * asserts only which fixture rows match.
     *
     * @param fragment the rendered condition, with its positional parameters
     * @return the ids of the matching fixture rows, in ascending order
     * @throws SQLException if the query fails
     */
    protected abstract List<Integer> matchingIds(SqlFragment fragment) throws SQLException;

    private static void insert(Connection c, int id, String name, String status, Integer age) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + TABLE + " (\"id\", \"name\", \"status\", \"age\") VALUES (?, ?, ?, ?)")) {
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.setString(3, status);
            if (age == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, age);
            }
            ps.executeUpdate();
        }
    }
}
