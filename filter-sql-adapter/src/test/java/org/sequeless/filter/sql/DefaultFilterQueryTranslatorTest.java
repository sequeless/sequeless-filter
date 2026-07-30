package org.sequeless.filter.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.AnyFilter;
import org.sequeless.filter.api.FieldDefinition;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.FieldRegistrySpec;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.filter.api.Filters;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.api.Syntax;
import org.sequeless.filter.spi.OperatorContributor;
import org.sequeless.filter.sql.api.ColumnMapping;
import org.sequeless.filter.sql.api.DefaultFilterQueryTranslator;
import org.sequeless.filter.sql.api.FilterQueryTranslator;
import org.sequeless.filter.sql.api.SqlFragment;
import org.sequeless.filter.sql.api.SqlRenderException;
import org.sequeless.filter.sql.spi.AnsiQueryBuilder;
import org.sequeless.filter.sql.spi.QueryBuilder;

/**
 * Covers all 13 built-in operators' translation through {@link DefaultFilterQueryTranslator} and
 * {@link AnsiQueryBuilder}, plus the D11/D18/D22/D30/D33 edge cases: empty and/or rejection, empty
 * list-shaped value rejection, {@code any}-expansion's drop-unmapped-then-succeed and
 * drop-all-then-fail paths, and dispatch-by-{@code isUnary()} rather than by null value.
 */
class DefaultFilterQueryTranslatorTest {

    private FieldRegistry fields;
    private OperatorRegistry ops;
    private ColumnMapping columns;
    private QueryBuilder builder;
    private FilterQueryTranslator translator;

    @BeforeEach
    void setUp() {
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

        fields = FieldRegistry.of(FieldRegistrySpec.builder()
                .fields(List.of(status, name, description, age, archivedAt))
                .build());
        ops = OperatorRegistry.defaults();
        columns = ColumnMapping.identity();
        builder = new AnsiQueryBuilder();
        translator = new DefaultFilterQueryTranslator();
    }

    private SqlFragment translate(FilterNode node) {
        return translator.translate(node, columns, fields, ops, builder);
    }

    // --- 13 built-in operators ---

    @Test
    void translatesIsToEquals() {
        SqlFragment f = translate(Filters.field("status", "is", JsonNodeFactory.instance.textNode("open")));
        assertThat(f.sql()).isEqualTo("\"status\" = ?");
        assertThat(f.parameters()).containsExactly("open");
    }

    @Test
    void translatesIsNotToNotEquals() {
        SqlFragment f = translate(Filters.field("status", "is not", JsonNodeFactory.instance.textNode("open")));
        assertThat(f.sql()).isEqualTo("\"status\" <> ?");
        assertThat(f.parameters()).containsExactly("open");
    }

    @Test
    void statusIsNullRendersIsNullNotExists() {
        // D33: dispatch by operator.isUnary(), never by value == null. 'is' is binary; a null
        // value must render IS NULL, not be mistaken for the unary 'exists' operator.
        SqlFragment f = translate(Filters.field("status", "is", JsonNodeFactory.instance.nullNode()));
        assertThat(f.sql()).isEqualTo("\"status\" IS NULL");
        assertThat(f.parameters()).isEmpty();
    }

    @Test
    void statusIsNotNullRendersIsNotNull() {
        SqlFragment f = translate(Filters.field("status", "is not", JsonNodeFactory.instance.nullNode()));
        assertThat(f.sql()).isEqualTo("\"status\" IS NOT NULL");
        assertThat(f.parameters()).isEmpty();
    }

    @Test
    void translatesIsGreaterThan() {
        SqlFragment f = translate(Filters.field("age", "is greater than", JsonNodeFactory.instance.numberNode(21)));
        assertThat(f.sql()).isEqualTo("\"age\" > ?");
        assertThat(f.parameters()).containsExactly(21);
    }

    @Test
    void translatesIsGreaterThanOrEqualTo() {
        SqlFragment f =
                translate(Filters.field("age", "is greater than or equal to", JsonNodeFactory.instance.numberNode(21)));
        assertThat(f.sql()).isEqualTo("\"age\" >= ?");
        assertThat(f.parameters()).containsExactly(21);
    }

    @Test
    void translatesIsLessThan() {
        SqlFragment f = translate(Filters.field("age", "is less than", JsonNodeFactory.instance.numberNode(65)));
        assertThat(f.sql()).isEqualTo("\"age\" < ?");
        assertThat(f.parameters()).containsExactly(65);
    }

    @Test
    void translatesIsLessThanOrEqualTo() {
        SqlFragment f =
                translate(Filters.field("age", "is less than or equal to", JsonNodeFactory.instance.numberNode(65)));
        assertThat(f.sql()).isEqualTo("\"age\" <= ?");
        assertThat(f.parameters()).containsExactly(65);
    }

    @Test
    void translatesContains() {
        SqlFragment f = translate(Filters.field("name", "contains", JsonNodeFactory.instance.textNode("foo")));
        assertThat(f.sql()).isEqualTo("\"name\" LIKE ?");
        assertThat(f.parameters()).containsExactly("%foo%");
    }

    @Test
    void translatesStartsWith() {
        SqlFragment f = translate(Filters.field("name", "starts with", JsonNodeFactory.instance.textNode("foo")));
        assertThat(f.sql()).isEqualTo("\"name\" LIKE ?");
        assertThat(f.parameters()).containsExactly("foo%");
    }

    @Test
    void translatesIsLike() {
        SqlFragment f = translate(Filters.field("name", "is like", JsonNodeFactory.instance.textNode("f_o%")));
        assertThat(f.sql()).isEqualTo("\"name\" LIKE ?");
        assertThat(f.parameters()).containsExactly("f_o%");
    }

    @Test
    void translatesIsNotLike() {
        SqlFragment f = translate(Filters.field("name", "is not like", JsonNodeFactory.instance.textNode("f_o%")));
        assertThat(f.sql()).isEqualTo("\"name\" NOT LIKE ?");
        assertThat(f.parameters()).containsExactly("f_o%");
    }

    @Test
    void translatesIsInToInClause() {
        var values = JsonNodeFactory.instance.arrayNode();
        values.add("open");
        values.add("closed");
        SqlFragment f = translate(Filters.field("status", "is in", values));
        assertThat(f.sql()).isEqualTo("\"status\" IN (?, ?)");
        assertThat(f.parameters()).containsExactly("open", "closed");
    }

    @Test
    void isInWithNullMemberBindsNullParameter() {
        // D21: SqlFragment tolerates a null bind parameter (e.g. 'is in' with a null member).
        var values = JsonNodeFactory.instance.arrayNode();
        values.add("open");
        values.addNull();
        SqlFragment f = translate(Filters.field("status", "is in", values));
        assertThat(f.sql()).isEqualTo("\"status\" IN (?, ?)");
        assertThat(f.parameters()).containsExactly("open", null);
    }

    @Test
    void translatesExists() {
        SqlFragment f = translate(Filters.field("archivedAt", "exists"));
        assertThat(f.sql()).isEqualTo("\"archivedAt\" IS NOT NULL");
        assertThat(f.parameters()).isEmpty();
    }

    @Test
    void translatesDoesNotExist() {
        SqlFragment f = translate(Filters.field("archivedAt", "does not exist"));
        assertThat(f.sql()).isEqualTo("\"archivedAt\" IS NULL");
        assertThat(f.parameters()).isEmpty();
    }

    // --- AND / OR combination and bind-parameter ordering ---

    @Test
    void andCombinesFragmentsWithParenthesesAndOrderedParameters() {
        SqlFragment f = translate(Filters.and(
                Filters.field("status", "is", JsonNodeFactory.instance.textNode("open")),
                Filters.field("age", "is greater than", JsonNodeFactory.instance.numberNode(21))));
        assertThat(f.sql()).isEqualTo("(\"status\" = ? AND \"age\" > ?)");
        assertThat(f.parameters()).containsExactly("open", 21);
    }

    @Test
    void orCombinesFragmentsWithParenthesesAndOrderedParameters() {
        SqlFragment f = translate(Filters.or(
                Filters.field("status", "is", JsonNodeFactory.instance.textNode("open")),
                Filters.field("status", "is", JsonNodeFactory.instance.textNode("closed"))));
        assertThat(f.sql()).isEqualTo("(\"status\" = ? OR \"status\" = ?)");
        assertThat(f.parameters()).containsExactly("open", "closed");
    }

    // --- Empty and/or rejection (D11/D22) ---

    @Test
    void emptyAndFilterAtRootIsRejected() {
        assertThatThrownBy(() -> translate(new org.sequeless.filter.api.AndFilter(List.of())))
                .isInstanceOf(SqlRenderException.class);
    }

    @Test
    void emptyOrFilterNestedInsideAndIsRejectedAtAnyDepth() {
        FilterNode nestedEmptyOr = Filters.and(
                Filters.field("status", "is", JsonNodeFactory.instance.textNode("open")),
                new org.sequeless.filter.api.OrFilter(List.of()));
        assertThatThrownBy(() -> translate(nestedEmptyOr)).isInstanceOf(SqlRenderException.class);
    }

    // --- Empty list-shaped value rejection (D11/D22/D30) ---

    @Test
    void isInWithEmptyListIsRejected() {
        var empty = JsonNodeFactory.instance.arrayNode();
        assertThatThrownBy(() -> translate(Filters.field("status", "is in", empty)))
                .isInstanceOf(SqlRenderException.class);
    }

    // --- any-expansion: drop unmapped operands (D18/D29) ---

    @Test
    void anyExpansionDropsUnmappedOperandsThenSucceeds() {
        // 'name' and 'description' are both string fields compatible with 'contains'; map only
        // 'name' to a column, leaving 'description' unmapped. The any-expansion should drop the
        // unmapped operand rather than failing the whole filter.
        ColumnMapping partial = ColumnMapping.of(java.util.Map.of("name", "name"));
        FilterQueryTranslator t = new DefaultFilterQueryTranslator();
        SqlFragment f = t.translate(
                new AnyFilter("contains", JsonNodeFactory.instance.textNode("foo")), partial, fields, ops, builder);
        // Only 'name' survives the drop-unmapped step; the single survivor is still combined via
        // builder.or(...), so it comes back parenthesized like any other OR result.
        assertThat(f.sql()).isEqualTo("(\"name\" LIKE ?)");
        assertThat(f.parameters()).containsExactly("%foo%");
    }

    @Test
    void anyExpansionWithAllOperandsUnmappedFails() {
        ColumnMapping none = ColumnMapping.of(java.util.Map.of());
        FilterQueryTranslator t = new DefaultFilterQueryTranslator();
        assertThatThrownBy(() -> t.translate(
                        new AnyFilter("contains", JsonNodeFactory.instance.textNode("foo")),
                        none,
                        fields,
                        ops,
                        builder))
                .isInstanceOf(SqlRenderException.class);
    }

    @Test
    void explicitlyNamedUnmappedColumnStillHardFails() {
        // An explicitly-named path (not reached through 'any') must still hard-fail on an
        // unmapped column — D18's leniency is scoped to any-expansion only.
        ColumnMapping none = ColumnMapping.of(java.util.Map.of());
        FilterQueryTranslator t = new DefaultFilterQueryTranslator();
        assertThatThrownBy(() -> t.translate(
                        Filters.field("status", "is", JsonNodeFactory.instance.textNode("open")),
                        none,
                        fields,
                        ops,
                        builder))
                .isInstanceOf(SqlRenderException.class);
    }

    // --- Unknown/custom operator fallback (D16) ---

    @Test
    void customBinaryOperatorFallsBackToRenderUnknown() {
        OperatorDefinition soundsLike = OperatorDefinition.builder()
                .canonicalName("sounds like")
                .syntax(Syntax.INFIX)
                .applicableTypes(List.of("string"))
                .build();
        OperatorContributor custom = () -> List.of(soundsLike);
        OperatorRegistry customOps = OperatorRegistry.of(List.of(custom));
        FieldRegistry soundexFields = FieldRegistry.of(FieldRegistrySpec.builder()
                .fields(List.of(FieldDefinition.builder()
                        .path("name")
                        .jsonSchemaType("string")
                        .build()))
                .build());

        FilterQueryTranslator t = new DefaultFilterQueryTranslator();
        FilterNode node = Filters.field("name", "sounds like", JsonNodeFactory.instance.textNode("smith"));

        assertThatThrownBy(() -> t.translate(node, columns, soundexFields, customOps, builder))
                .isInstanceOf(SqlRenderException.class);
    }

    @Test
    void customBinaryOperatorCanBeSupportedByOverridingRenderUnknown() {
        OperatorDefinition soundsLike = OperatorDefinition.builder()
                .canonicalName("sounds like")
                .syntax(Syntax.INFIX)
                .applicableTypes(List.of("string"))
                .build();
        OperatorContributor custom = () -> List.of(soundsLike);
        OperatorRegistry customOps = OperatorRegistry.of(List.of(custom));
        FieldRegistry soundexFields = FieldRegistry.of(FieldRegistrySpec.builder()
                .fields(List.of(FieldDefinition.builder()
                        .path("name")
                        .jsonSchemaType("string")
                        .build()))
                .build());

        QueryBuilder soundexBuilder = new AnsiQueryBuilder() {
            @Override
            protected SqlFragment renderUnknown(String column, OperatorDefinition operator, Object value) {
                if ("sounds like".equals(operator.getCanonicalName())) {
                    return new SqlFragment("SOUNDEX(" + column + ") = SOUNDEX(?)", List.of(value));
                }
                return super.renderUnknown(column, operator, value);
            }
        };

        FilterQueryTranslator t = new DefaultFilterQueryTranslator();
        FilterNode node = Filters.field("name", "sounds like", JsonNodeFactory.instance.textNode("smith"));
        SqlFragment f = t.translate(node, columns, soundexFields, customOps, soundexBuilder);

        assertThat(f.sql()).isEqualTo("SOUNDEX(\"name\") = SOUNDEX(?)");
        assertThat(f.parameters()).containsExactly("smith");
    }

    @Test
    void customUnaryOperatorFallsBackToUnaryUnknown() {
        OperatorDefinition isArchived = OperatorDefinition.builder()
                .canonicalName("is archived")
                .syntax(Syntax.INFIX)
                .unary(true)
                .applicableTypes(List.of("string"))
                .build();
        OperatorContributor custom = () -> List.of(isArchived);
        OperatorRegistry customOps = OperatorRegistry.of(List.of(custom));

        FilterQueryTranslator t = new DefaultFilterQueryTranslator();
        FilterNode node = Filters.field("status", "is archived");

        assertThatThrownBy(() -> t.translate(node, columns, fields, customOps, builder))
                .isInstanceOf(SqlRenderException.class);
    }

    // --- Validation entry point (D27) ---

    @Test
    void unknownFieldFailsValidationBeforeReachingQueryBuilder() {
        assertThatThrownBy(() -> translate(Filters.field("nope", "is", JsonNodeFactory.instance.textNode("x"))))
                .isInstanceOf(SqlRenderException.class);
    }
}
