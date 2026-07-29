package org.sequeless.filter.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a {@link FilterNode} AST against the registered operators and fields.
 * Never throws — returns a list of violations instead.
 */
public final class FilterValidator {

    private FilterValidator() {}

    /**
     * Validates the AST and returns any violations found.
     * An empty list means the filter is valid according to the given registries.
     *
     * @param node    the AST to validate
     * @param ops     operator registry
     * @param fields  field registry
     * @return immutable list of violations; empty if the filter is valid
     */
    public static List<FilterViolation> validate(FilterNode node, OperatorRegistry ops, FieldRegistry fields) {
        List<FilterViolation> violations = new ArrayList<>();
        validateNode(node, ops, fields, violations);
        return List.copyOf(violations);
    }

    private static void validateNode(
            FilterNode node, OperatorRegistry ops, FieldRegistry fields, List<FilterViolation> violations) {
        switch (node) {
            case FieldFilter f -> validateField(f, ops, fields, violations);
            case AnyFilter a -> validateAny(a, ops, violations);
            case AndFilter a -> a.operands().forEach(o -> validateNode(o, ops, fields, violations));
            case OrFilter o -> o.operands().forEach(op -> validateNode(op, ops, fields, violations));
        }
    }

    private static void validateField(
            FieldFilter f, OperatorRegistry ops, FieldRegistry fields, List<FilterViolation> violations) {

        if (fields.find(f.path()).isEmpty()) {
            violations.add(new FilterViolation(f.path(), "Unknown field: '" + f.path() + "'"));
            return;
        }

        OperatorDefinition op = ops.findByCanonicalOrAlias(f.op()).orElse(null);
        if (op == null) {
            violations.add(new FilterViolation(f.path(), "Unknown operator: '" + f.op() + "'"));
            return;
        }

        fields.find(f.path()).ifPresent(fieldDef -> {
            if (!op.getApplicableTypes().isEmpty() && !op.getApplicableTypes().contains(fieldDef.getJsonSchemaType())) {
                violations.add(new FilterViolation(
                        f.path(),
                        "Operator '" + f.op() + "' is not applicable to type '" + fieldDef.getJsonSchemaType() + "'"));
            }
            if (!fieldDef.getPermittedOperators().isEmpty()
                    && !fieldDef.getPermittedOperators().contains(f.op())) {
                violations.add(new FilterViolation(
                        f.path(), "Operator '" + f.op() + "' is not permitted for field '" + f.path() + "'"));
            }
        });
    }

    private static void validateAny(AnyFilter a, OperatorRegistry ops, List<FilterViolation> violations) {
        if (ops.findByCanonicalOrAlias(a.op()).isEmpty()) {
            violations.add(new FilterViolation(null, "Unknown operator in AnyFilter: '" + a.op() + "'"));
        }
    }
}
