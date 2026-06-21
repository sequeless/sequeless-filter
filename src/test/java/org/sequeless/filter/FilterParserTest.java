package org.sequeless.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.AndFilter;
import org.sequeless.filter.api.AnyFilter;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.filter.api.FilterParseException;
import org.sequeless.filter.api.FilterParser;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.api.OrFilter;

class FilterParserTest {

    private static final OperatorRegistry OPS = OperatorRegistry.defaults();
    private static final FieldRegistry FIELDS = FieldRegistry.permissive();

    private FilterNode parse(String input) {
        return FilterParser.parse(input, OPS, FIELDS);
    }

    @Test
    void parsesSimpleEquality() {
        FilterNode node = parse("status is 'active'");
        assertThat(node).isInstanceOfSatisfying(FieldFilter.class, f -> {
            assertThat(f.path()).isEqualTo("status");
            assertThat(f.op()).isEqualTo("is");
            assertThat(f.value().textValue()).isEqualTo("active");
        });
    }

    @Test
    void parsesEqualSymbolAlias() {
        FilterNode node = parse("status = 'active'");
        assertThat(node).isInstanceOfSatisfying(FieldFilter.class, f -> {
            assertThat(f.op()).isEqualTo("is");
            assertThat(f.value().textValue()).isEqualTo("active");
        });
    }

    @Test
    void parsesNotEqualsAlias() {
        FilterNode node = parse("status != 'inactive'");
        assertThat(node).isInstanceOfSatisfying(FieldFilter.class, f -> {
            assertThat(f.op()).isEqualTo("is not");
            assertThat(f.value().textValue()).isEqualTo("inactive");
        });
    }

    @Test
    void parsesNumericComparisons() {
        assertThat(parse("qty > 5")).isInstanceOfSatisfying(FieldFilter.class, f -> assertThat(f.op())
                .isEqualTo("is greater than"));
        assertThat(parse("qty >= 5")).isInstanceOfSatisfying(FieldFilter.class, f -> assertThat(f.op())
                .isEqualTo("is greater than or equal to"));
        assertThat(parse("qty < 5")).isInstanceOfSatisfying(FieldFilter.class, f -> assertThat(f.op())
                .isEqualTo("is less than"));
        assertThat(parse("qty <= 5")).isInstanceOfSatisfying(FieldFilter.class, f -> assertThat(f.op())
                .isEqualTo("is less than or equal to"));
    }

    @Test
    void parsesUnaryExists() {
        FilterNode node = parse("deletedAt exists");
        assertThat(node).isInstanceOfSatisfying(FieldFilter.class, f -> {
            assertThat(f.path()).isEqualTo("deletedAt");
            assertThat(f.op()).isEqualTo("exists");
            assertThat(f.value()).isNull();
        });
    }

    @Test
    void parsesDoesNotExist() {
        FilterNode node = parse("deletedAt does not exist");
        assertThat(node).isInstanceOfSatisfying(FieldFilter.class, f -> assertThat(f.op())
                .isEqualTo("does not exist"));
    }

    @Test
    void parsesNotExistsAlias() {
        FilterNode node = parse("deletedAt not exists");
        assertThat(node).isInstanceOfSatisfying(FieldFilter.class, f -> assertThat(f.op())
                .isEqualTo("does not exist"));
    }

    @Test
    void parsesAndConjunction() {
        FilterNode node = parse("status is 'active' and age > 18");
        assertThat(node).isInstanceOfSatisfying(AndFilter.class, a -> {
            assertThat(a.operands()).hasSize(2);
            assertThat(a.operands().get(0)).isInstanceOf(FieldFilter.class);
            assertThat(a.operands().get(1)).isInstanceOf(FieldFilter.class);
        });
    }

    @Test
    void parsesOrDisjunction() {
        FilterNode node = parse("status is 'active' or status is 'pending'");
        assertThat(node).isInstanceOfSatisfying(OrFilter.class, o -> assertThat(o.operands())
                .hasSize(2));
    }

    @Test
    void parsesNestedAndOr() {
        FilterNode node = parse("(status is 'active' or status is 'pending') and age > 18");
        assertThat(node).isInstanceOfSatisfying(AndFilter.class, a -> {
            assertThat(a.operands().get(0)).isInstanceOf(OrFilter.class);
            assertThat(a.operands().get(1)).isInstanceOf(FieldFilter.class);
        });
    }

    @Test
    void parsesDotNotationPath() {
        FilterNode node = parse("lineItems.qty > 0");
        assertThat(node).isInstanceOfSatisfying(FieldFilter.class, f -> assertThat(f.path())
                .isEqualTo("lineItems.qty"));
    }

    @Test
    void parsesAnyFilter() {
        FilterNode node = parse("any is 'active'");
        assertThat(node).isInstanceOfSatisfying(AnyFilter.class, a -> {
            assertThat(a.op()).isEqualTo("is");
            assertThat(a.value().textValue()).isEqualTo("active");
        });
    }

    @Test
    void parsesIsIn() {
        FilterNode node = parse("status is in ['active', 'pending']");
        assertThat(node).isInstanceOfSatisfying(FieldFilter.class, f -> {
            assertThat(f.op()).isEqualTo("is in");
            assertThat(f.value().isArray()).isTrue();
            assertThat(f.value().size()).isEqualTo(2);
        });
    }

    @Test
    void parsesNullValue() {
        FilterNode node = parse("deletedAt is null");
        assertThat(node)
                .isInstanceOfSatisfying(
                        FieldFilter.class, f -> assertThat(f.value().isNull()).isTrue());
    }

    @Test
    void parsesBooleanValue() {
        FilterNode node = parse("active is true");
        assertThat(node).isInstanceOfSatisfying(FieldFilter.class, f -> assertThat(
                        f.value().booleanValue())
                .isTrue());
    }

    @Test
    void parsesStringWithEmbeddedSingleQuote() {
        FilterNode node = parse("note contains 'it''s fine'");
        assertThat(node).isInstanceOfSatisfying(FieldFilter.class, f -> assertThat(
                        f.value().textValue())
                .isEqualTo("it's fine"));
    }

    @Test
    void throwsOnUnknownField() {
        FieldRegistry strict = FieldRegistry.of(java.util.List.of());
        assertThatThrownBy(() -> FilterParser.parse("status is 'active'", OPS, strict))
                .isInstanceOf(FilterParseException.class)
                .hasMessageContaining("status");
    }

    @Test
    void throwsOnUnknownOperator() {
        assertThatThrownBy(() -> parse("status flies 'active'"))
                .isInstanceOf(FilterParseException.class)
                .hasMessageContaining("flies");
    }

    @Test
    void throwsOnUnaryUsedWithValue() {
        assertThatThrownBy(() -> parse("deletedAt exists 'something'")).isInstanceOf(Exception.class);
    }
}
