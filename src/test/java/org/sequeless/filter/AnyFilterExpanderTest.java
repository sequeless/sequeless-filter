package org.sequeless.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.AnyExpansionStrategy;
import org.sequeless.filter.api.AnyFilter;
import org.sequeless.filter.api.AnyFilterExpander;
import org.sequeless.filter.api.FieldDefinition;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.filter.api.Filters;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.api.OrFilter;

class AnyFilterExpanderTest {

    private FieldRegistry fields;
    private OperatorRegistry ops;

    @BeforeEach
    void setUp() {
        FieldDefinition name =
                FieldDefinition.builder().path("name").jsonSchemaType("string").build();
        FieldDefinition description = FieldDefinition.builder()
                .path("description")
                .jsonSchemaType("string")
                .build();
        FieldDefinition age =
                FieldDefinition.builder().path("age").jsonSchemaType("number").build();

        fields = FieldRegistry.of(List.of(name, description, age));
        ops = OperatorRegistry.defaults();
    }

    @Test
    void expandsAnyToOrOverCompatibleStringFields() {
        AnyFilter any = new AnyFilter("contains", JsonNodeFactory.instance.textNode("foo"));
        FilterNode expanded = AnyFilterExpander.expand(any, fields, ops);

        assertThat(expanded).isInstanceOf(OrFilter.class);
        OrFilter or = (OrFilter) expanded;
        // 'name' and 'description' are string fields compatible with 'contains'; 'age' is not
        assertThat(or.operands()).hasSize(2);
        assertThat(or.operands()).allSatisfy(n -> assertThat(n).isInstanceOf(FieldFilter.class));
        assertThat(or.operands())
                .extracting(n -> ((FieldFilter) n).path())
                .containsExactlyInAnyOrder("name", "description");
    }

    @Test
    void expandsAnyNodeInsideAndFilter() {
        FilterNode and = Filters.and(
                new AnyFilter("contains", JsonNodeFactory.instance.textNode("foo")),
                Filters.field("age", "is greater than", JsonNodeFactory.instance.numberNode(18)));

        FilterNode expanded = AnyFilterExpander.expand(and, fields, ops);
        assertThat(expanded).isInstanceOfSatisfying(org.sequeless.filter.api.AndFilter.class, a -> assertThat(
                        a.operands().get(0))
                .isInstanceOf(OrFilter.class));
    }

    @Test
    void customStrategyIsInvoked() {
        AnyExpansionStrategy strategy = mock(AnyExpansionStrategy.class);
        FilterNode mockResult = Filters.field("name", "contains", JsonNodeFactory.instance.textNode("foo"));
        when(strategy.expand(any(AnyFilter.class), anyList())).thenReturn(mockResult);

        AnyFilter any = new AnyFilter("contains", JsonNodeFactory.instance.textNode("foo"));
        FilterNode expanded = AnyFilterExpander.expand(any, fields, ops, strategy);

        verify(strategy).expand(any(AnyFilter.class), anyList());
        assertThat(expanded).isSameAs(mockResult);
    }

    @Test
    void fieldFiltersArePassedThrough() {
        FilterNode field = Filters.field("name", "contains", JsonNodeFactory.instance.textNode("x"));
        assertThat(AnyFilterExpander.expand(field, fields, ops)).isSameAs(field);
    }
}
