package org.sequeless.filter.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.sequeless.filter.api.AnyFilter;
import org.sequeless.filter.api.AndFilter;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.filter.api.FilterParseException;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.api.OrFilter;
import org.sequeless.filter.api.ParameterType;
import org.sequeless.filter.api.Syntax;
import org.sequeless.filter.internal.parser.FilterBaseVisitor;
import org.sequeless.filter.internal.parser.FilterParser;
import org.sequeless.filter.internal.parser.FilterParser.ArgContext;
import org.sequeless.filter.internal.parser.FilterParser.BinaryConditionContext;
import org.sequeless.filter.internal.parser.FilterParser.FieldTargetContext;
import org.sequeless.filter.internal.parser.FilterParser.FunctionConditionContext;
import org.sequeless.filter.internal.parser.FilterParser.OpPhraseContext;
import org.sequeless.filter.internal.parser.FilterParser.UnaryConditionContext;
import org.sequeless.filter.internal.parser.FilterParser.ValueContext;

/**
 * ANTLR parse-tree visitor that builds a {@link FilterNode} AST.
 * Validates field paths and operator phrases as it walks the tree.
 */
public class FilterBuildingVisitor extends FilterBaseVisitor<FilterNode> {

    private final OperatorRegistry ops;
    private final FieldRegistry fields;

    public FilterBuildingVisitor(OperatorRegistry ops, FieldRegistry fields) {
        this.ops = ops;
        this.fields = fields;
    }

    @Override
    public FilterNode visitFilter(FilterParser.FilterContext ctx) {
        return visit(ctx.disjunction());
    }

    @Override
    public FilterNode visitDisjunction(FilterParser.DisjunctionContext ctx) {
        List<FilterParser.ConjunctionContext> conjunctions = ctx.conjunction();
        if (conjunctions.size() == 1) {
            return visit(conjunctions.get(0));
        }
        List<FilterNode> operands = new ArrayList<>();
        for (FilterParser.ConjunctionContext c : conjunctions) {
            operands.add(visit(c));
        }
        return new OrFilter(operands);
    }

    @Override
    public FilterNode visitConjunction(FilterParser.ConjunctionContext ctx) {
        List<FilterParser.PrimaryContext> primaries = ctx.primary();
        if (primaries.size() == 1) {
            return visit(primaries.get(0));
        }
        List<FilterNode> operands = new ArrayList<>();
        for (FilterParser.PrimaryContext p : primaries) {
            operands.add(visit(p));
        }
        return new AndFilter(operands);
    }

    @Override
    public FilterNode visitPrimary(FilterParser.PrimaryContext ctx) {
        if (ctx.disjunction() != null) {
            return visit(ctx.disjunction());
        }
        return visit(ctx.condition());
    }

    @Override
    public FilterNode visitBinaryCondition(BinaryConditionContext ctx) {
        OperatorDefinition op = resolveOpPhrase(ctx.opPhrase());
        if (op.isUnary()) {
            throw new FilterParseException(
                    "Operator '" + op.getCanonicalName() + "' is unary (no value expected)",
                    ctx.opPhrase().getStart().getStartIndex());
        }
        JsonNode value = convertValue(ctx.value());
        return buildCondition(ctx.fieldOrAny(), op.getCanonicalName(), value);
    }

    @Override
    public FilterNode visitUnaryCondition(UnaryConditionContext ctx) {
        OperatorDefinition op = resolveOpPhrase(ctx.opPhrase());
        if (!op.isUnary()) {
            throw new FilterParseException(
                    "Operator '" + op.getCanonicalName() + "' requires a value",
                    ctx.opPhrase().getStart().getStartIndex());
        }
        return buildCondition(ctx.fieldOrAny(), op.getCanonicalName(), null);
    }

    @Override
    public FilterNode visitFunctionCondition(FunctionConditionContext ctx) {
        String fnName = ctx.WORD().getText();
        OperatorDefinition op = ops.findByCanonicalOrAlias(fnName)
                .orElseThrow(() -> new FilterParseException(
                        "Unknown function operator: '" + fnName + "'",
                        ctx.WORD().getSymbol().getStartIndex()));

        if (op.getSyntax() != Syntax.FUNCTION) {
            throw new FilterParseException(
                    "Operator '" + fnName + "' is not a function-call operator",
                    ctx.WORD().getSymbol().getStartIndex());
        }

        List<JsonNode> args = ctx.argList() != null
                ? convertArgList(ctx.argList())
                : List.of();

        if (args.size() != op.getParameters().size()) {
            throw new FilterParseException(
                    "Operator '" + op.getCanonicalName() + "' expects " + op.getParameters().size()
                            + " argument(s) but got " + args.size(),
                    ctx.getStart().getStartIndex());
        }

        for (int i = 0; i < op.getParameters().size(); i++) {
            var param = op.getParameters().get(i);
            var argCtx = ctx.argList().arg(i);
            validateArg(argCtx, param, op.getCanonicalName(), i);
        }

        ArrayNode argArray = JsonNodeFactory.instance.arrayNode();
        args.forEach(argArray::add);

        return buildCondition(ctx.fieldOrAny(), op.getCanonicalName(), argArray);
    }

    private FilterNode buildCondition(
            FilterParser.FieldOrAnyContext fieldOrAny, String canonicalOp, JsonNode value) {
        if (fieldOrAny instanceof FieldTargetContext ftc) {
            String path = resolvePath(ftc.path());
            return new FieldFilter(path, canonicalOp, value);
        }
        // AnyTargetContext
        return new AnyFilter(canonicalOp, value);
    }

    private String resolvePath(FilterParser.PathContext ctx) {
        String path = ctx.WORD().stream()
                .map(TerminalNode::getText)
                .collect(Collectors.joining("."));

        if (!fields.isPermissive() && fields.find(path).isEmpty()) {
            throw new FilterParseException(
                    "Unknown field path: '" + path + "'",
                    ctx.getStart().getStartIndex());
        }
        return path;
    }

    private OperatorDefinition resolveOpPhrase(OpPhraseContext ctx) {
        String phrase = ctx.children.stream()
                .filter(c -> c instanceof TerminalNode)
                .map(c -> ((TerminalNode) c).getText())
                .collect(Collectors.joining(" "));

        return ops.findByCanonicalOrAlias(phrase)
                .orElseThrow(() -> new FilterParseException(
                        "Unknown operator: '" + phrase + "'",
                        ctx.getStart().getStartIndex()));
    }

    private JsonNode convertValue(ValueContext ctx) {
        if (ctx.STRING() != null) {
            return JsonNodeFactory.instance.textNode(unescapeString(ctx.STRING().getText()));
        }
        if (ctx.NUMBER() != null) {
            return parseNumber(ctx.NUMBER().getText());
        }
        if (ctx.TRUE() != null) return BooleanNode.TRUE;
        if (ctx.FALSE() != null) return BooleanNode.FALSE;
        if (ctx.NULL() != null) return NullNode.getInstance();

        // Array: LBRACKET value* RBRACKET
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (ValueContext element : ctx.value()) {
            array.add(convertValue(element));
        }
        return array;
    }

    private List<JsonNode> convertArgList(FilterParser.ArgListContext ctx) {
        List<JsonNode> result = new ArrayList<>();
        for (ArgContext argCtx : ctx.arg()) {
            result.add(convertArg(argCtx));
        }
        return result;
    }

    private JsonNode convertArg(ArgContext ctx) {
        if (ctx.STRING() != null) {
            return JsonNodeFactory.instance.textNode(unescapeString(ctx.STRING().getText()));
        }
        if (ctx.NUMBER() != null) {
            return parseNumber(ctx.NUMBER().getText());
        }
        // WORD — used for ENUM args
        return JsonNodeFactory.instance.textNode(ctx.WORD().getText());
    }

    private void validateArg(
            ArgContext argCtx,
            org.sequeless.filter.api.ParameterDefinition param,
            String opName,
            int index) {
        boolean isString = argCtx.STRING() != null || argCtx.WORD() != null;
        boolean isNumber = argCtx.NUMBER() != null;

        switch (param.getType()) {
            case INT -> {
                if (!isNumber || argCtx.NUMBER().getText().contains(".")) {
                    throw new FilterParseException(
                            "Argument " + index + " of '" + opName + "' must be an integer",
                            argCtx.getStart().getStartIndex());
                }
            }
            case FLOAT -> {
                if (!isNumber) {
                    throw new FilterParseException(
                            "Argument " + index + " of '" + opName + "' must be a number",
                            argCtx.getStart().getStartIndex());
                }
            }
            case STRING -> {
                if (!isString) {
                    throw new FilterParseException(
                            "Argument " + index + " of '" + opName + "' must be a string",
                            argCtx.getStart().getStartIndex());
                }
            }
            case ENUM -> {
                String val = argCtx.WORD() != null
                        ? argCtx.WORD().getText()
                        : (argCtx.STRING() != null
                                ? unescapeString(argCtx.STRING().getText())
                                : null);
                if (val == null || !param.getAllowedValues().contains(val)) {
                    throw new FilterParseException(
                            "Argument " + index + " of '" + opName + "' must be one of "
                                    + param.getAllowedValues() + " but was '" + val + "'",
                            argCtx.getStart().getStartIndex());
                }
            }
        }
    }

    private static JsonNode parseNumber(String text) {
        BigDecimal bd = new BigDecimal(text);
        if (bd.scale() <= 0) {
            long lv = bd.longValueExact();
            if (lv >= Integer.MIN_VALUE && lv <= Integer.MAX_VALUE) {
                return JsonNodeFactory.instance.numberNode((int) lv);
            }
            return JsonNodeFactory.instance.numberNode(lv);
        }
        return JsonNodeFactory.instance.numberNode(bd.doubleValue());
    }

    /** Strips outer quote characters and unescapes doubled inner quotes. */
    static String unescapeString(String raw) {
        if (raw == null || raw.length() < 2) return raw;
        char quote = raw.charAt(0);
        String inner = raw.substring(1, raw.length() - 1);
        if (quote == '\'') {
            return inner.replace("''", "'");
        } else {
            return inner.replace("\"\"", "\"");
        }
    }
}
