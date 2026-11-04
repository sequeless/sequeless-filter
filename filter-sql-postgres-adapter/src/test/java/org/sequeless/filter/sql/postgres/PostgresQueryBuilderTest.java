package org.sequeless.filter.sql.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.sql.api.Field;
import org.sequeless.filter.sql.api.SqlFragment;

/** The Postgres rendering of the LIKE family: the SQL text itself. The rows are checked by the IT. */
class PostgresQueryBuilderTest {

    private final PostgresQueryBuilder builder = new PostgresQueryBuilder();
    private final OperatorRegistry ops = OperatorRegistry.defaults();

    @Test
    void containsRendersIlikeWithAnEscapeClauseAndTheEscapedPattern() {
        SqlFragment f = builder.binary(
                new Field.Column("name"), ops.findByCanonicalOrAlias("contains").orElseThrow(), "50%");

        assertThat(f.sql()).isEqualTo("\"name\" ILIKE ? ESCAPE '\\'");
        assertThat(f.parameters()).containsExactly("%50\\%%");
    }

    @Test
    void isNotLikeIncludesNullFieldsWithIlike() {
        SqlFragment f = builder.binary(
                new Field.Column("name"),
                ops.findByCanonicalOrAlias("is not like").orElseThrow(),
                "w%");

        assertThat(f.sql()).isEqualTo("(\"name\" IS NULL OR \"name\" NOT ILIKE ? ESCAPE '\\')");
        assertThat(f.parameters()).containsExactly("w%");
    }

    @Test
    void equalityIsUnchangedFromTheAnsiDefault() {
        SqlFragment f = builder.binary(
                new Field.Column("status"), ops.findByCanonicalOrAlias("is not").orElseThrow(), "open");

        assertThat(f.sql()).isEqualTo("\"status\" IS DISTINCT FROM ?");
    }
}
