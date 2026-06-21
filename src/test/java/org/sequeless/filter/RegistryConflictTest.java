package org.sequeless.filter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.api.Syntax;
import org.sequeless.filter.spi.OperatorContributor;

class RegistryConflictTest {

    @Test
    void duplicateCanonicalNameThrows() {
        OperatorContributor contrib = () -> List.of(
                OperatorDefinition.builder().canonicalName("is").syntax(Syntax.INFIX).build(),
                OperatorDefinition.builder().canonicalName("is").syntax(Syntax.INFIX).build());

        assertThatThrownBy(() -> OperatorRegistry.of(List.of(contrib)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is");
    }

    @Test
    void duplicateAliasThrows() {
        OperatorContributor contrib = () -> List.of(
                OperatorDefinition.builder()
                        .canonicalName("equals")
                        .aliases(List.of("="))
                        .syntax(Syntax.INFIX)
                        .build(),
                OperatorDefinition.builder()
                        .canonicalName("same as")
                        .aliases(List.of("="))
                        .syntax(Syntax.INFIX)
                        .build());

        assertThatThrownBy(() -> OperatorRegistry.of(List.of(contrib)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("=");
    }

    @Test
    void aliasConflictingWithOtherCanonicalNameThrows() {
        OperatorContributor contrib = () -> List.of(
                OperatorDefinition.builder()
                        .canonicalName("is")
                        .syntax(Syntax.INFIX)
                        .build(),
                OperatorDefinition.builder()
                        .canonicalName("check")
                        .aliases(List.of("is"))
                        .syntax(Syntax.INFIX)
                        .build());

        assertThatThrownBy(() -> OperatorRegistry.of(List.of(contrib)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is");
    }
}
