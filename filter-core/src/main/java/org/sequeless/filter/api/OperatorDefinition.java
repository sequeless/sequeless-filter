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
     *  Empty means applicable to all types. A field must match this list AND {@link #applicableFormats}
     *  (see {@link #appliesTo}), unless {@link #alsoAppliesToFormats} gives it an extra route. */
    @Builder.Default
    List<String> applicableTypes = List.of();

    /** JSON Schema format strings (e.g. {@code "date-time"}) this operator applies to.
     *  Empty means all formats. A non-empty list never matches a field with no format. A field must
     *  match this list AND {@link #applicableTypes}, unless {@link #alsoAppliesToFormats} gives it an
     *  extra route. */
    @Builder.Default
    List<String> applicableFormats = List.of();

    /** Formats that give an extra route to applicability, for these operators only: a field whose
     *  format is listed here is applicable even when its type is not in {@link #applicableTypes}.
     *  Empty means no extra route. */
    @Builder.Default
    List<String> alsoAppliesToFormats = List.of();

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
     * <p>The base rule is AND: the type must be in {@code applicableTypes} (or the list is empty), and
     * the format must be in {@code applicableFormats} (or the list is empty). A {@code null} format
     * never matches a non-empty {@code applicableFormats}. Every operator uses this rule.
     *
     * <p>{@code alsoAppliesToFormats} adds one extra route, used only by the ordering comparisons: a
     * field whose format is listed there applies even when its type is not in {@code applicableTypes}.
     * Operators with an empty {@code alsoAppliesToFormats} get no extra route. A {@code null} format
     * never takes the extra route.
     *
     * <p>{@link FilterValidator} checks the type only for operators whose {@code applicableTypes} is
     * non-empty or whose {@code alsoAppliesToFormats} is non-empty. Formats-only operators therefore
     * still ignore the field format during validation.
     */
    public boolean appliesTo(String jsonSchemaType, String jsonSchemaFormat) {
        boolean typeMatches = applicableTypes.isEmpty() || applicableTypes.contains(jsonSchemaType);
        boolean formatMatches = applicableFormats.isEmpty()
                || (jsonSchemaFormat != null && applicableFormats.contains(jsonSchemaFormat));
        boolean oldAnd = typeMatches && formatMatches;
        return oldAnd || (jsonSchemaFormat != null && alsoAppliesToFormats.contains(jsonSchemaFormat));
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

        public OperatorDefinitionBuilder alsoAppliesToFormats(List<String> alsoAppliesToFormats) {
            this.alsoAppliesToFormats$value = List.copyOf(alsoAppliesToFormats);
            this.alsoAppliesToFormats$set = true;
            return this;
        }

        public OperatorDefinitionBuilder parameters(List<ParameterDefinition> parameters) {
            this.parameters$value = List.copyOf(parameters);
            this.parameters$set = true;
            return this;
        }
    }
}
