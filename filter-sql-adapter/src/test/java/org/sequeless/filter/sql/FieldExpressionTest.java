package org.sequeless.filter.sql;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.FieldDefinition;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.FieldRegistrySpec;
import org.sequeless.filter.api.Filters;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.sql.api.DefaultFilterQueryTranslator;
import org.sequeless.filter.sql.api.Field;
import org.sequeless.filter.sql.api.FieldResolver;
import org.sequeless.filter.sql.api.SqlExpression;
import org.sequeless.filter.sql.api.SqlFragment;
import org.sequeless.filter.sql.spi.AnsiQueryBuilder;

/**
 * A {@link Field.Expression} renders as trusted SQL: its text is spliced in where the quoted
 * column would go, and its parameters come before the operator's (spec 6, section 3.3).
 */
class FieldExpressionTest {

    private static final SqlExpression AGE_AS_INT = new SqlExpression("(props ->> ?)::int", List.of("age"));

    private final AnsiQueryBuilder builder = new AnsiQueryBuilder();
    private final OperatorRegistry ops = OperatorRegistry.defaults();

    private OperatorDefinition op(String name) {
        return ops.findByCanonicalOrAlias(name).orElseThrow();
    }

    @Test
    void expressionIsSplicedUnquotedAndItsParametersComeBeforeTheOperands() {
        SqlFragment f = builder.binary(new Field.Expression(AGE_AS_INT), op("is greater than"), 5);

        assertThat(f.sql()).isEqualTo("(props ->> ?)::int > ?");
        assertThat(f.parameters()).containsExactly("age", 5);
    }

    @Test
    void columnIsStillQuotedAndHasNoParameters() {
        SqlFragment f = builder.binary(new Field.Column("age"), op("is greater than"), 5);

        assertThat(f.sql()).isEqualTo("\"age\" > ?");
        assertThat(f.parameters()).containsExactly(5);
    }

    @Test
    void unaryExpressionKeepsItsParameters() {
        SqlFragment f = builder.unary(new Field.Expression(AGE_AS_INT), op("exists"));

        assertThat(f.sql()).isEqualTo("(props ->> ?)::int IS NOT NULL");
        assertThat(f.parameters()).containsExactly("age");
    }

    @Test
    void isInPlacesTheOperandParametersFirstThenTheMembers() {
        SqlFragment f = builder.binary(new Field.Expression(AGE_AS_INT), op("is in"), List.of(1, 2));

        assertThat(f.sql()).isEqualTo("(props ->> ?)::int IN (?, ?)");
        assertThat(f.parameters()).containsExactly("age", 1, 2);
    }

    @Test
    void translatorResolvesAPathToAnExpressionField() {
        FieldResolver resolver =
                path -> path.equals("age") ? Optional.of(new Field.Expression(AGE_AS_INT)) : Optional.empty();
        FieldRegistry fields = FieldRegistry.of(FieldRegistrySpec.builder()
                .fields(List.of(FieldDefinition.builder()
                        .path("age")
                        .jsonSchemaType("integer")
                        .build()))
                .build());

        SqlFragment f = new DefaultFilterQueryTranslator()
                .translate(
                        Filters.field("age", "is greater than", JsonNodeFactory.instance.numberNode(5)),
                        resolver,
                        fields,
                        ops,
                        builder);

        assertThat(f.sql()).isEqualTo("(props ->> ?)::int > ?");
        assertThat(f.parameters()).containsExactly("age", 5);
    }
}
