package org.sequeless.filter.api;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Provides text-DSL autocomplete candidates at a given cursor position.
 *
 * <p>Calls {@link FilterParser#parsePartial} internally and dispatches on
 * {@link CursorPosition} to produce relevant candidates.
 */
public final class CompletionEngine {

    private final OperatorRegistry ops;
    private final FieldRegistry fields;

    public CompletionEngine(OperatorRegistry ops, FieldRegistry fields) {
        this.ops = ops;
        this.fields = fields;
    }

    /**
     * Returns completion candidates at the given cursor position within {@code input}.
     *
     * @param input        the DSL expression typed so far (may be incomplete)
     * @param cursorOffset character offset of the cursor
     * @return candidate strings to offer in the UI
     */
    public List<String> complete(String input, int cursorOffset) {
        ParseResult result = FilterParser.parsePartial(input, cursorOffset, ops, fields);
        if (result instanceof ParseResult.Complete) {
            return List.of();
        }
        ParseResult.Partial partial = (ParseResult.Partial) result;
        return candidatesFor(partial.hint());
    }

    private List<String> candidatesFor(CompletionHint hint) {
        return switch (hint.getPosition()) {
            case FIELD -> {
                List<String> candidates = new ArrayList<>();
                fields.all().stream().map(FieldDefinition::getPath).forEach(candidates::add);
                candidates.add("any");
                yield candidates;
            }
            case OPERATOR -> {
                String fieldPath = hint.getFieldPath();
                String fieldType = fieldPath != null
                        ? fields.find(fieldPath)
                                .map(FieldDefinition::getJsonSchemaType)
                                .orElse(null)
                        : null;
                String fieldFormat = fieldPath != null
                        ? fields.find(fieldPath)
                                .map(FieldDefinition::getJsonSchemaFormat)
                                .orElse(null)
                        : null;

                List<OperatorDefinition> applicable = fieldType != null
                        ? ops.applicableTo(fieldType, fieldFormat)
                        : ops.all();

                List<String> candidates = new ArrayList<>();
                applicable.stream()
                        .filter(op -> op.getSyntax() == Syntax.INFIX)
                        .flatMap(op -> Stream.concat(
                                Stream.of(op.getCanonicalName()), op.getAliases().stream()))
                        .forEach(candidates::add);
                candidates.add("meets");
                yield candidates;
            }
            case FUNCTION_NAME -> {
                String fieldPath = hint.getFieldPath();
                String fieldType = fieldPath != null
                        ? fields.find(fieldPath)
                                .map(FieldDefinition::getJsonSchemaType)
                                .orElse(null)
                        : null;
                String fieldFormat = fieldPath != null
                        ? fields.find(fieldPath)
                                .map(FieldDefinition::getJsonSchemaFormat)
                                .orElse(null)
                        : null;

                List<OperatorDefinition> applicable = fieldType != null
                        ? ops.applicableTo(fieldType, fieldFormat)
                        : ops.all();

                yield applicable.stream()
                        .filter(op -> op.getSyntax() == Syntax.FUNCTION)
                        .map(OperatorDefinition::getCanonicalName)
                        .toList();
            }
            case FUNCTION_ARG -> {
                OperatorDefinition op = hint.getOperator();
                int argIndex = hint.getArgIndex();
                if (op == null || argIndex < 0 || argIndex >= op.getParameters().size()) {
                    yield List.of();
                }
                ParameterDefinition param = op.getParameters().get(argIndex);
                if (param.getType() == ParameterType.ENUM) {
                    yield List.copyOf(param.getAllowedValues());
                }
                String fieldPath = hint.getFieldPath();
                if (fieldPath != null) {
                    yield callCompletionProvider(fieldPath, op, "");
                }
                yield List.of();
            }
            case VALUE -> {
                String fieldPath = hint.getFieldPath();
                OperatorDefinition op = hint.getOperator();
                if (fieldPath == null || op == null) yield List.of();
                yield callCompletionProvider(fieldPath, op, "");
            }
            case BOOLEAN_OP -> List.of("and", "or");
        };
    }

    private List<String> callCompletionProvider(
            String fieldPath, OperatorDefinition op, String partial) {
        return fields.find(fieldPath)
                .filter(f -> f.getCompletionProvider() != null)
                .map(f -> f.getCompletionProvider()
                        .complete(CompletionContext.builder()
                                .fieldPath(fieldPath)
                                .jsonSchemaType(f.getJsonSchemaType())
                                .jsonSchemaFormat(f.getJsonSchemaFormat())
                                .operator(op.getCanonicalName())
                                .partialValue(partial)
                                .build()))
                .orElse(List.of());
    }
}
