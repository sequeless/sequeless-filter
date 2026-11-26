package org.sequeless.filter.api;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Describes an operator that can appear in a filter expression.
 *
 * <p>Operators are registered via {@link org.sequeless.filter.spi.OperatorContributor} and
 * looked up by canonical name or any alias via {@link OperatorRegistry}.
 */
@Value
@Builder
public class OperatorDefinition {

    /** Primary name used in serialized output and returned from the parser. */
    String canonicalName;

    /** Alternative DSL spellings that the parser accepts and resolves to {@link #canonicalName}. */
    @Builder.Default
    List<String> aliases = List.of();

    /** JSON Schema type strings (e.g. {@code "string"}, {@code "number"}) this operator applies to.
     *  Empty means applicable to all types. */
    @Builder.Default
    List<String> applicableTypes = List.of();

    /** JSON Schema format strings (e.g. {@code "date-time"}) this operator applies to.
     *  Empty means all formats. */
    @Builder.Default
    List<String> applicableFormats = List.of();

    Syntax syntax;

    /**
     * {@code true} for operators that take no right-hand-side value (e.g. {@code exists}).
     * The parser enforces this: using a unary operator with a value, or a binary operator
     * without a value, both produce a {@link FilterParseException}.
     */
    @Builder.Default
    boolean unary = false;

    /**
     * {@code true} for {@link Syntax#INFIX} operators whose right-hand side is a JSON array
     * (only {@code is in} among the built-ins). Ignored for unary and {@link Syntax#FUNCTION}
     * operators, whose shape follows from those flags — see {@link #getValueShape()}.
     */
    @Builder.Default
    boolean listValued = false;

    /** Non-empty only for {@link Syntax#FUNCTION} operators. */
    @Builder.Default
    List<ParameterDefinition> parameters = List.of();

    /**
     * Derived value shape — never set directly. {@link ValueShape#NONE} when {@code isUnary()};
     * {@link ValueShape#LIST} when {@code getSyntax()} is {@link Syntax#FUNCTION} (function
     * operators always carry an argument array, regardless of arity) or {@code isListValued()};
     * {@link ValueShape#SCALAR} otherwise. Precedence: unary &gt; function/list &gt; scalar.
     */
    public ValueShape getValueShape() {
        if (unary) {
            return ValueShape.NONE;
        }
        if (syntax == Syntax.FUNCTION || listValued) {
            return ValueShape.LIST;
        }
        return ValueShape.SCALAR;
    }

    /**
     * Whether this operator applies to a field with the given JSON Schema type and format.
     *
     * <p>This is the single applicability rule. {@link FilterValidator} and
     * {@link OperatorRegistry#applicableTo(String, String)} both call it, so they cannot diverge.
     *
     * <ul>
     *   <li>When {@code applicableTypes} or {@code applicableFormats} is empty (or both are), the
     *       operator applies when every non-empty list contains the matching value. An empty list
     *       matches everything. A {@code null} format matches only an empty {@code applicableFormats}.
     *       A formats-only operator is therefore listed only for fields whose format it names.</li>
     *   <li>When both lists are non-empty, the operator applies when {@code applicableTypes} contains
     *       the type <em>or</em> {@code applicableFormats} contains the format. The format is an
     *       additional route, so a field whose type is not listed still matches on its format.
     *       The comparison operators use this rule: they apply to numbers and integers, and also
     *       to {@code date} and {@code date-time} strings.</li>
     * </ul>
     *
     * <p>{@link FilterValidator} checks the type only for operators whose {@code applicableTypes} is
     * non-empty, so formats-only operators still ignore the field format during validation.
     */
    public boolean appliesTo(String jsonSchemaType, String jsonSchemaFormat) {
        boolean typeMatches = applicableTypes.isEmpty() || applicableTypes.contains(jsonSchemaType);
        boolean formatMatches = applicableFormats.isEmpty()
                || (jsonSchemaFormat != null && applicableFormats.contains(jsonSchemaFormat));
        if (applicableTypes.isEmpty() || applicableFormats.isEmpty()) {
            return typeMatches && formatMatches;
        }
        return typeMatches || formatMatches;
    }

    /** Defensive-copy overrides for the Lombok-generated builder setters. */
    public static class OperatorDefinitionBuilder {

        public OperatorDefinitionBuilder aliases(List<String> aliases) {
            this.aliases$value = List.copyOf(aliases);
            this.aliases$set = true;
            return this;
        }

        public OperatorDefinitionBuilder applicableTypes(List<String> applicableTypes) {
            this.applicableTypes$value = List.copyOf(applicableTypes);
            this.applicableTypes$set = true;
            return this;
        }

        public OperatorDefinitionBuilder applicableFormats(List<String> applicableFormats) {
            this.applicableFormats$value = List.copyOf(applicableFormats);
            this.applicableFormats$set = true;
            return this;
        }

        public OperatorDefinitionBuilder parameters(List<ParameterDefinition> parameters) {
            this.parameters$value = List.copyOf(parameters);
            this.parameters$set = true;
            return this;
        }
    }
}
