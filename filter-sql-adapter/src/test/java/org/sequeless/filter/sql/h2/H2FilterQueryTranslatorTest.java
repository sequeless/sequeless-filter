package org.sequeless.filter.sql.h2;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.AnyFilter;
import org.sequeless.filter.api.FieldDefinition;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.FieldRegistrySpec;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.filter.api.Filters;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.sql.api.ColumnMapping;
import org.sequeless.filter.sql.api.DefaultFilterQueryTranslator;
import org.sequeless.filter.sql.api.FilterQueryTranslator;
import org.sequeless.filter.sql.api.SqlFragment;
import org.sequeless.filter.sql.spi.QueryBuilder;

/**
 * Executes {@link DefaultFilterQueryTranslator} output as real {@code PreparedStatement}s against
 * an in-memory H2 database, covering each operator family end to end (T7) rather than only
 * asserting SQL text/bind-parameter shape as {@code DefaultFilterQueryTranslatorTest} does.
 *
 * <p>Named {@code *Test}, not {@code *IT}, per the plan: the reactor's JaCoCo setup has no
 * {@code report-integration} execution, so an {@code *IT} name would run under Failsafe but its
 * coverage wouldn't reach the Sonar report.
 */
class H2FilterQueryTranslatorTest {

    private Connection connection;
    private FieldRegistry fields;
    private OperatorRegistry ops;
    private QueryBuilder builder;
    private FilterQueryTranslator translator;

    @BeforeEach
    void setUp() throws SQLException {
        // Fresh in-memory database per test method (unique URL) so tests can't see each other's
        // schema/state and don't need explicit cleanup between runs.
        // DATABASE_TO_LOWER folds unquoted identifiers to lower case, matching the lower-case
        // column names ColumnMapping/quoteIdentifier produce below (H2 defaults to upper case).
        connection = DriverManager.getConnection("jdbc:h2:mem:" + getClass().getSimpleName() + "_" + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(
                    """
                    CREATE TABLE products (
                        id BIGINT PRIMARY KEY,
                        status VARCHAR(32),
                        name VARCHAR(128),
                        description VARCHAR(256),
                        age INT,
                        archived_at TIMESTAMP
                    )
                    """);
            stmt.execute(
                    """
                    INSERT INTO products (id, status, name, description, age, archived_at) VALUES
                        (1, 'open',   'Widget',   'A useful widget',        21, NULL),
                        (2, 'closed', 'Gadget',   'A fancy gadget',         65, TIMESTAMP '2024-01-01 00:00:00'),
                        (3, 'open',   'Sprocket', 'A rugged sprocket',      42, NULL),
                        (4, 'pending', 'Widget Pro', NULL,                 30, TIMESTAMP '2023-06-15 00:00:00')
                    """);
        }

        FieldDefinition status = FieldDefinition.builder()
                .path("status")
                .jsonSchemaType("string")
                .build();
        FieldDefinition name =
                FieldDefinition.builder().path("name").jsonSchemaType("string").build();
        FieldDefinition description = FieldDefinition.builder()
                .path("description")
                .jsonSchemaType("string")
                .build();
        FieldDefinition age =
                FieldDefinition.builder().path("age").jsonSchemaType("number").build();
        FieldDefinition archivedAt = FieldDefinition.builder()
                .path("archivedAt")
                .jsonSchemaType("string")
                .build();
        // Registry-compatible but deliberately left out of most tests' ColumnMapping (see
        // anyExpansionSkipsUnmappedRegistryField) to exercise D18's drop-unmapped-operands path.
        FieldDefinition nickname = FieldDefinition.builder()
                .path("nickname")
                .jsonSchemaType("string")
                .build();

        fields = FieldRegistry.of(FieldRegistrySpec.builder()
                .fields(List.of(status, name, description, age, archivedAt, nickname))
                .build());
        ops = OperatorRegistry.defaults();
        builder = new H2QueryBuilder();
        translator = new DefaultFilterQueryTranslator();
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    private List<Map<String, Object>> query(FilterNode node, ColumnMapping columns) throws SQLException {
        SqlFragment fragment = translator.translate(node, columns, fields, ops, builder);
        String sql = "SELECT * FROM products WHERE " + fragment.sql() + " ORDER BY id";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            List<Object> parameters = fragment.parameters();
            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(Map.of(
                            "id", rs.getLong("id"),
                            "status", rs.getString("status"),
                            "name", rs.getString("name")));
                }
                return rows;
            }
        }
    }

    private static ColumnMapping fullMapping() {
        return ColumnMapping.of(Map.of(
                "status", "status",
                "name", "name",
                "description", "description",
                "age", "age",
                "archivedAt", "archived_at"));
    }

    // --- equality / is-not ---

    @Test
    void isEqualsFiltersToMatchingRows() throws SQLException {
        List<Map<String, Object>> rows =
                query(Filters.field("status", "is", JsonNodeFactory.instance.textNode("open")), fullMapping());
        assertThat(rows).extracting(r -> r.get("id")).containsExactly(1L, 3L);
    }

    @Test
    void isNotExcludesMatchingRows() throws SQLException {
        List<Map<String, Object>> rows =
                query(Filters.field("status", "is not", JsonNodeFactory.instance.textNode("open")), fullMapping());
        assertThat(rows).extracting(r -> r.get("id")).containsExactly(2L, 4L);
    }

    // --- comparison ---

    @Test
    void isGreaterThanFiltersNumerically() throws SQLException {
        List<Map<String, Object>> rows =
                query(Filters.field("age", "is greater than", JsonNodeFactory.instance.numberNode(30)), fullMapping());
        assertThat(rows).extracting(r -> r.get("id")).containsExactly(2L, 3L);
    }

    // --- like-family ---

    @Test
    void containsMatchesSubstring() throws SQLException {
        List<Map<String, Object>> rows =
                query(Filters.field("name", "contains", JsonNodeFactory.instance.textNode("Widget")), fullMapping());
        assertThat(rows).extracting(r -> r.get("id")).containsExactly(1L, 4L);
    }

    // --- in, including a null element (D21) ---

    @Test
    void isInWithNullElementExecutesWithoutThrowing() throws SQLException {
        var values = JsonNodeFactory.instance.arrayNode();
        values.add("open");
        values.addNull();
        List<Map<String, Object>> rows = query(Filters.field("status", "is in", values), fullMapping());
        // The null member contributes an "= NULL"-equivalent bind that matches no row (SQL NULL
        // comparison semantics), but the query must execute cleanly and still return the 'open' rows.
        assertThat(rows).extracting(r -> r.get("id")).containsExactly(1L, 3L);
    }

    // --- exists-family ---

    @Test
    void existsFiltersToNonNullColumn() throws SQLException {
        List<Map<String, Object>> rows = query(Filters.field("archivedAt", "exists"), fullMapping());
        assertThat(rows).extracting(r -> r.get("id")).containsExactly(2L, 4L);
    }

    @Test
    void doesNotExistFiltersToNullColumn() throws SQLException {
        List<Map<String, Object>> rows = query(Filters.field("archivedAt", "does not exist"), fullMapping());
        assertThat(rows).extracting(r -> r.get("id")).containsExactly(1L, 3L);
    }

    // --- and/or combination ---

    @Test
    void andCombinesConditions() throws SQLException {
        List<Map<String, Object>> rows = query(
                Filters.and(
                        Filters.field("status", "is", JsonNodeFactory.instance.textNode("open")),
                        Filters.field("age", "is greater than", JsonNodeFactory.instance.numberNode(30))),
                fullMapping());
        assertThat(rows).extracting(r -> r.get("id")).containsExactly(3L);
    }

    @Test
    void orCombinesConditions() throws SQLException {
        List<Map<String, Object>> rows = query(
                Filters.or(
                        Filters.field("status", "is", JsonNodeFactory.instance.textNode("closed")),
                        Filters.field("status", "is", JsonNodeFactory.instance.textNode("pending"))),
                fullMapping());
        assertThat(rows).extracting(r -> r.get("id")).containsExactly(2L, 4L);
    }

    // --- any-expansion with a deliberately partial ColumnMapping (D18) ---

    @Test
    void anyExpansionSkipsUnmappedRegistryField() throws SQLException {
        // 'name', 'description', and 'nickname' are all registry-compatible with 'contains', but
        // 'nickname' is deliberately left out of this ColumnMapping — there is no such column in
        // the schema at all. The translator must drop that operand rather than failing the whole
        // filter, and the query must still execute correctly against only the mapped columns.
        ColumnMapping partial = ColumnMapping.of(Map.of(
                "status", "status",
                "name", "name",
                "description", "description"));
        List<Map<String, Object>> rows =
                query(new AnyFilter("contains", JsonNodeFactory.instance.textNode("Widget")), partial);
        assertThat(rows).extracting(r -> r.get("id")).containsExactly(1L, 4L);
    }
}
