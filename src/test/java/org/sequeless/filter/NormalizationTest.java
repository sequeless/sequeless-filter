package org.sequeless.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.FilterParser;
import org.sequeless.filter.api.OperatorRegistry;

class NormalizationTest {

    private static final OperatorRegistry OPS = OperatorRegistry.defaults();
    private static final FieldRegistry FIELDS = FieldRegistry.permissive();

    @Test
    void equalsAliasResolvesToIsCanonical() {
        FieldFilter f = (FieldFilter) FilterParser.parse("status = 'active'", OPS, FIELDS);
        assertThat(f.op()).isEqualTo("is");
    }

    @Test
    void notEqualsAliasResolvesToIsNotCanonical() {
        FieldFilter f = (FieldFilter) FilterParser.parse("status != 'active'", OPS, FIELDS);
        assertThat(f.op()).isEqualTo("is not");
    }

    @Test
    void gteAliasResolvesToCanonical() {
        FieldFilter f = (FieldFilter) FilterParser.parse("qty >= 10", OPS, FIELDS);
        assertThat(f.op()).isEqualTo("is greater than or equal to");
    }

    @Test
    void gtSymbolResolvesToIsGreaterThan() {
        FieldFilter f = (FieldFilter) FilterParser.parse("qty > 10", OPS, FIELDS);
        assertThat(f.op()).isEqualTo("is greater than");
    }

    @Test
    void ltSymbolResolvesToIsLessThan() {
        FieldFilter f = (FieldFilter) FilterParser.parse("qty < 10", OPS, FIELDS);
        assertThat(f.op()).isEqualTo("is less than");
    }

    @Test
    void notExistsAliasResolvesToDoesNotExist() {
        FieldFilter f = (FieldFilter) FilterParser.parse("deletedAt not exists", OPS, FIELDS);
        assertThat(f.op()).isEqualTo("does not exist");
    }

    @Test
    void wordAliasResolvesToIsCanonical() {
        FieldFilter f = (FieldFilter) FilterParser.parse("status equals 'active'", OPS, FIELDS);
        assertThat(f.op()).isEqualTo("is");
    }
}
