package org.sequeless.filter.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Serializes a {@link FilterNode} AST back to a human-readable DSL string.
 *
 * <p>Canonical output rules:
 * <ul>
 *   <li>String values → single-quoted: {@code 'active'}; embedded {@code '} doubled</li>
 *   <li>Numbers, booleans, null → unquoted</li>
 *   <li>Lists → {@code ['a', 'b']}</li>
 *   <li>Unary operators omit the value: {@code deletedAt exists}</li>
 *   <li>Binary infix: {@code field op value}</li>
 *   <li>Function call: {@code field meets fnName(arg1, arg2)}</li>
 *   <li>AND/OR use lowercase keywords; OR inside AND adds explicit parentheses</li>
 * </ul>
 *
 * <p>Guarantee: {@code parse(serialize(ast), ops, fields).equals(ast)} holds when the
 * two-argument overload is used (it selects a DSL-representable form for operators
 * whose canonical name contains grammar keywords).
 */
public final class FilterSerializer {

    private FilterSerializer() {}

    /**
     * Serializes using the canonical operator name directly.
     * May produce output not parseable by {@link FilterParser} if the canonical name
     * contains grammar keywords (e.g. {@code or} in {@code is greater than or equal to}).
     * Prefer {@link #serialize(FilterNode, OperatorRegistry)} for round-trip safety.
     */
    public static String serialize(FilterNode node) {
        return serializeNode(node, null, false);
    }

    /**
     * Serializes with the operator registry, falling back to the first DSL-representable
     * alias when the canonical name contains grammar keywords.
     * Guarantees {@code parse(serialize(ast, ops), ops, fields).equals(ast)}.
     */
    public static String serialize(FilterNode node, OperatorRegistry ops) {
        return serializeNode(node, ops, false);
    }

    // ---- private implementation ----

    private static String serializeNode(FilterNode node, OperatorRegistry ops, boolean insideAnd) {
        return switch (node) {
            case FieldFilter f -> serializeField(f, ops);
            case AnyFilter a -> serializeAny(a, ops);
            case AndFilter a -> serializeAnd(a, ops);
            case OrFilter o -> serializeOr(o, ops, insideAnd);
        };
    }

    private static String serializeField(FieldFilter f, OperatorRegistry ops) {
        String op = resolveDslOp(f.op(), ops);
        if (f.value() == null) {
            return f.path() + " " + op;
        }
        return f.path() + " " + op + " " + serializeValue(f.value(), f.op(), ops);
    }

    private static String serializeAny(AnyFilter a, OperatorRegistry ops) {
        String op = resolveDslOp(a.op(), ops);
        if (a.value() == null) {
            return "any " + op;
        }
        return "any " + op + " " + serializeValue(a.value(), a.op(), ops);
    }

    private static String serializeAnd(AndFilter a, OperatorRegistry ops) {
        List<FilterNode> operands = a.operands();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < operands.size(); i++) {
            if (i > 0) sb.append(" and ");
            sb.append(serializeNode(operands.get(i), ops, true));
        }
        return sb.toString();
    }

    private static String serializeOr(OrFilter o, OperatorRegistry ops, boolean insideAnd) {
        List<FilterNode> operands = o.operands();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < operands.size(); i++) {
            if (i > 0) sb.append(" or ");
            sb.append(serializeNode(operands.get(i), ops, false));
        }
        String result = sb.toString();
        return insideAnd ? "(" + result + ")" : result;
    }

    private static String serializeValue(JsonNode value, String canonicalOp, OperatorRegistry ops) {
        if (ops != null) {
            Optional<OperatorDefinition> opDef = ops.findByCanonicalOrAlias(canonicalOp);
            if (opDef.isPresent() && opDef.get().getSyntax() == Syntax.FUNCTION) {
                return serializeFunctionValue(opDef.get(), value);
            }
        }
        return serializeJsonNode(value);
    }

    private static String serializeFunctionValue(OperatorDefinition op, JsonNode args) {
        StringBuilder sb = new StringBuilder();
        sb.append("meets ").append(op.getCanonicalName()).append("(");
        if (args.isArray()) {
            ArrayNode array = (ArrayNode) args;
            List<ParameterDefinition> params = op.getParameters();
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) sb.append(", ");
                boolean isEnum = i < params.size() && params.get(i).getType() == ParameterType.ENUM;
                if (isEnum && array.get(i).isTextual()) {
                    // ENUM args are bare words in the grammar, not quoted strings
                    sb.append(array.get(i).textValue());
                } else {
                    sb.append(serializeJsonNode(array.get(i)));
                }
            }
        }
        sb.append(")");
        return sb.toString();
    }

    static String serializeJsonNode(JsonNode node) {
        if (node.isTextual()) {
            return "'" + node.textValue().replace("'", "''") + "'";
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder("[");
            Iterator<JsonNode> it = node.elements();
            while (it.hasNext()) {
                sb.append(serializeJsonNode(it.next()));
                if (it.hasNext()) sb.append(", ");
            }
            sb.append("]");
            return sb.toString();
        }
        // number, boolean, null
        return node.toString();
    }

    /**
     * Returns the operator phrase to emit. When the canonical name contains grammar keywords
     * (e.g. {@code or}), and an ops registry is available, falls back to the first alias
     * that is safely representable in the grammar.
     */
    private static String resolveDslOp(String canonicalName, OperatorRegistry ops) {
        if (ops == null || isDslSafe(canonicalName)) {
            return canonicalName;
        }
        return ops.findByCanonicalOrAlias(canonicalName)
                .flatMap(def -> def.getAliases().stream()
                        .filter(FilterSerializer::isDslSafe)
                        .findFirst())
                .orElse(canonicalName);
    }

    /** Returns true when the operator phrase contains no grammar keywords that would break parsing. */
    private static boolean isDslSafe(String phrase) {
        // 'or' and 'and' inside a multi-word operator phrase would be tokenized as AND/OR keywords
        for (String word : phrase.split("\\s+")) {
            if (word.equalsIgnoreCase("or") || word.equalsIgnoreCase("and")) return false;
        }
        return true;
    }
}
