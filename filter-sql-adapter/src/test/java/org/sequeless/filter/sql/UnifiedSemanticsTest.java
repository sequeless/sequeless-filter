package org.sequeless.filter.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.sql.api.Field;
import org.sequeless.filter.sql.api.SqlFragment;
import org.sequeless.filter.sql.api.SqlRenderException;
import org.sequeless.filter.sql.spi.AnsiQueryBuilder;

/** The unified operator semantics (spec 6, section 3.3): escaping, NULL rules and rejections. */
class UnifiedSemanticsTest {

    private final AnsiQueryBuilder builder = new AnsiQueryBuilder();
    private final OperatorRegistry ops = OperatorRegistry.defaults();

    private OperatorDefinition op(String name) {
        return ops.findByCanonicalOrAlias(name).orElseThrow();
    }

    @Test
    void containsEscapesLiteralPercent() {
        SqlFragment f = builder.binary(new Field.Column("name"), op("contains"), "50%");

        assertThat(f.parameters()).containsExactly("%50\\%%");
    }

    @Test
    void startsWithEscapesLiteralUnderscore() {
        SqlFragment f = builder.binary(new Field.Column("name"), op("starts with"), "a_");

        assertThat(f.parameters()).containsExactly("a\\_%");
    }

    @Test
    void containsEscapesLiteralBackslash() {
        SqlFragment f = builder.binary(new Field.Column("name"), op("contains"), "a\\b");

        assertThat(f.parameters()).containsExactly("%a\\\\b%");
    }

    @Test
    void isLikeKeepsTheCallersWildcardsAsWritten() {
        SqlFragment f = builder.binary(new Field.Column("name"), op("is like"), "f_o%");

        assertThat(f.parameters()).containsExactly("f_o%");
    }

    @Test
    void likeFamilyIsCaseInsensitiveWithAnEscapeClause() {
        SqlFragment f = builder.binary(new Field.Column("name"), op("is like"), "f%");

        assertThat(f.sql()).isEqualTo("UPPER(\"name\") LIKE UPPER(?) ESCAPE '\\'");
    }

    @Test
    void nullLikeValueIsRejected() {
        assertThatThrownBy(() -> builder.binary(new Field.Column("name"), op("contains"), null))
                .isInstanceOf(SqlRenderException.class)
                .hasMessageContaining("needs a value");
    }

    @Test
    void nullComparisonValueIsRejected() {
        assertThatThrownBy(() -> builder.binary(new Field.Column("age"), op("is greater than"), null))
                .isInstanceOf(SqlRenderException.class)
                .hasMessageContaining("needs a value");
    }

    @Test
    void isInOfOnlyNullsMeansTheFieldIsNull() {
        List<Object> onlyNull = Arrays.asList((Object) null);

        SqlFragment f = builder.binary(new Field.Column("status"), op("is in"), onlyNull);

        assertThat(f.sql()).isEqualTo("\"status\" IS NULL");
        assertThat(f.parameters()).isEmpty();
    }

    @Test
    void isNotKeepsNullFieldsAndIsNullSafe() {
        SqlFragment f = builder.binary(new Field.Column("status"), op("is not"), "open");

        assertThat(f.sql()).isEqualTo("\"status\" IS DISTINCT FROM ?");
        assertThat(f.parameters()).containsExactly("open");
    }
}
