package org.sequeless.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.OperatorRegistry;

class OperatorAliasResolutionTest {

    private static final OperatorRegistry OPS = OperatorRegistry.defaults();

    @Test
    void isNotNullAliasResolvesToIsNot() {
        assertThat(OPS.findByCanonicalOrAlias("is not null"))
                .hasValueSatisfying(op -> assertThat(op.getCanonicalName()).isEqualTo("is not"));
    }

    @Test
    void isNullAliasResolvesToIs() {
        assertThat(OPS.findByCanonicalOrAlias("is null"))
                .hasValueSatisfying(op -> assertThat(op.getCanonicalName()).isEqualTo("is"));
    }
}
