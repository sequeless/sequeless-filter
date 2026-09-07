package org.sequeless.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.filter.api.FilterParser;
import org.sequeless.filter.api.FilterSerializer;
import org.sequeless.filter.api.Filters;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.testfixtures.PermissiveFieldRegistry;

class FilterSerializerTest {

    private static final OperatorRegistry OPS = OperatorRegistry.defaults();
    private static final FieldRegistry FIELDS = PermissiveFieldRegistry.INSTANCE;

    private FilterNode roundTrip(FilterNode node) {
        String serialized = FilterSerializer.serialize(node, OPS);
        return FilterParser.parse(serialized, OPS, FIELDS);
    }

    @Test
    void roundTripsFieldEquality() {
        FilterNode original = Filters.field("status", "is", JsonNodeFactory.instance.textNode("active"));
        assertThat(roundTrip(original)).isEqualTo(original);
    }

    @Test
    void roundTripsNumericGt() {
        FilterNode original = Filters.field("qty", "is greater than", JsonNodeFactory.instance.numberNode(5));
        assertThat(roundTrip(original)).isEqualTo(original);
    }

    @Test
    void roundTripsGteWithKeywordInCanonical() {
        // "is greater than or equal to" contains 'or' — serializer must use '>=' alias
        FilterNode original =
                Filters.field("qty", "is greater than or equal to", JsonNodeFactory.instance.numberNode(10));
        String serialized = FilterSerializer.serialize(original, OPS);
        assertThat(serialized).contains(">=");
        assertThat(roundTrip(original)).isEqualTo(original);
    }

    @Test
    void roundTripsLteWithKeywordInCanonical() {
        FilterNode original = Filters.field("qty", "is less than or equal to", JsonNodeFactory.instance.numberNode(10));
        String serialized = FilterSerializer.serialize(original, OPS);
        assertThat(serialized).contains("<=");
        assertThat(roundTrip(original)).isEqualTo(original);
    }

    @Test
    void roundTripsUnaryExists() {
        FilterNode original = Filters.field("deletedAt", "exists");
        assertThat(roundTrip(original)).isEqualTo(original);
        assertThat(FilterSerializer.serialize(original, OPS)).isEqualTo("deletedAt exists");
    }

    @Test
    void roundTripsDoesNotExist() {
        FilterNode original = Filters.field("deletedAt", "does not exist");
        assertThat(roundTrip(original)).isEqualTo(original);
    }

    @Test
    void roundTripsBooleanValue() {
        FilterNode original = Filters.field("active", "is", BooleanNode.TRUE);
        assertThat(roundTrip(original)).isEqualTo(original);
        assertThat(FilterSerializer.serialize(original, OPS)).contains("true");
    }

    @Test
    void roundTripsNullValue() {
        FilterNode original = Filters.field("deletedAt", "is", NullNode.getInstance());
        assertThat(roundTrip(original)).isEqualTo(original);
        assertThat(FilterSerializer.serialize(original, OPS)).contains("null");
    }

    @Test
    void roundTripsArrayValue() {
        var arr = JsonNodeFactory.instance.arrayNode();
        arr.add("active");
        arr.add("pending");
        FilterNode original = Filters.field("status", "is in", arr);
        assertThat(roundTrip(original)).isEqualTo(original);
    }

    @Test
    void serializesStringWithEmbeddedSingleQuote() {
        FilterNode original = Filters.field("note", "contains", JsonNodeFactory.instance.textNode("it's fine"));
        String serialized = FilterSerializer.serialize(original, OPS);
        assertThat(serialized).contains("'it''s fine'");
        assertThat(roundTrip(original)).isEqualTo(original);
    }

    @Test
    void roundTripsAndFilter() {
        FilterNode original = Filters.and(
                Filters.field("a", "is", JsonNodeFactory.instance.textNode("x")),
                Filters.field("b", "is", JsonNodeFactory.instance.textNode("y")));
        assertThat(roundTrip(original)).isEqualTo(original);
    }

    @Test
    void roundTripsOrFilter() {
        FilterNode original = Filters.or(
                Filters.field("a", "is", JsonNodeFactory.instance.textNode("x")),
                Filters.field("b", "is", JsonNodeFactory.instance.textNode("y")));
        assertThat(roundTrip(original)).isEqualTo(original);
    }

    @Test
    void addsParenthesesAroundOrInsideAnd() {
        FilterNode orNode = Filters.or(
                Filters.field("a", "is", JsonNodeFactory.instance.textNode("x")),
                Filters.field("b", "is", JsonNodeFactory.instance.textNode("y")));
        FilterNode andNode = Filters.and(orNode, Filters.field("c", "is", JsonNodeFactory.instance.textNode("z")));
        String serialized = FilterSerializer.serialize(andNode, OPS);
        assertThat(serialized).startsWith("(");
    }

    @Test
    void roundTripsIsIn() {
        var arr = JsonNodeFactory.instance.arrayNode();
        arr.add("a");
        arr.add("b");
        FilterNode original = Filters.field("status", "is in", arr);
        assertThat(roundTrip(original)).isEqualTo(original);
    }

    @Test
    void roundTripsContains() {
        FilterNode original = Filters.field("name", "contains", JsonNodeFactory.instance.textNode("foo"));
        assertThat(roundTrip(original)).isEqualTo(original);
    }

    @Test
    void roundTripsStartsWith() {
        FilterNode original = Filters.field("name", "starts with", JsonNodeFactory.instance.textNode("foo"));
        assertThat(roundTrip(original)).isEqualTo(original);
    }

    @Test
    void roundTripsIsLike() {
        FilterNode original = Filters.field("name", "is like", JsonNodeFactory.instance.textNode("foo%"));
        assertThat(roundTrip(original)).isEqualTo(original);
    }

    @Test
    void roundTripsIsNotLike() {
        FilterNode original = Filters.field("name", "is not like", JsonNodeFactory.instance.textNode("foo%"));
        assertThat(roundTrip(original)).isEqualTo(original);
    }
}
