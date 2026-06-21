package org.sequeless.filter.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.Token;
import org.sequeless.filter.api.CompletionHint;
import org.sequeless.filter.api.CursorPosition;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.internal.parser.FilterParser;

/**
 * State-machine analysis of a token sequence to determine {@link CursorPosition}.
 * Used exclusively by {@link org.sequeless.filter.api.FilterParser#parsePartial}.
 */
public final class CursorAnalyzer {

    private CursorAnalyzer() {}

    /**
     * Walks the token list and returns a {@link CompletionHint} that describes what the
     * user is currently typing at {@code cursorOffset}.
     */
    public static CompletionHint analyze(
            List<Token> tokens,
            int cursorOffset,
            OperatorRegistry ops,
            FieldRegistry fields) {

        // State within a single condition clause
        State state = State.START;
        String currentField = null;
        List<Token> opTokens = new ArrayList<>();

        for (Token t : tokens) {
            int type = t.getType();
            switch (state) {
                case START -> {
                    if (isWordOrPath(type)) {
                        state = State.IN_PATH;
                        currentField = t.getText();
                        opTokens.clear();
                    } else if (type == FilterParser.ANY) {
                        state = State.AFTER_FIELD;
                        currentField = "any";
                        opTokens.clear();
                    } else if (type == FilterParser.AND || type == FilterParser.OR) {
                        state = State.START;
                        currentField = null;
                        opTokens.clear();
                    }
                }
                case IN_PATH -> {
                    if (type == FilterParser.DOT) {
                        // stay in IN_PATH, accumulating
                    } else if (isWordOrPath(type)) {
                        currentField = currentField + "." + t.getText();
                    } else if (type == FilterParser.MEETS) {
                        state = State.AFTER_MEETS;
                    } else if (isOpToken(type)) {
                        state = State.IN_OP;
                        opTokens.add(t);
                    } else if (type == FilterParser.AND || type == FilterParser.OR) {
                        state = State.START;
                        currentField = null;
                        opTokens.clear();
                    } else {
                        state = State.AFTER_FIELD;
                    }
                }
                case AFTER_FIELD -> {
                    if (type == FilterParser.MEETS) {
                        state = State.AFTER_MEETS;
                    } else if (isOpToken(type)) {
                        state = State.IN_OP;
                        opTokens.add(t);
                    } else if (type == FilterParser.AND || type == FilterParser.OR) {
                        state = State.START;
                        currentField = null;
                        opTokens.clear();
                    }
                }
                case IN_OP -> {
                    if (isOpToken(type)) {
                        opTokens.add(t);
                    } else if (isValueStart(type)) {
                        state = State.IN_VALUE;
                    } else if (type == FilterParser.AND || type == FilterParser.OR) {
                        state = State.START;
                        currentField = null;
                        opTokens.clear();
                    } else {
                        // end of op phrase, value next
                        state = State.AFTER_OP;
                    }
                }
                case AFTER_OP, IN_VALUE -> {
                    if (type == FilterParser.AND || type == FilterParser.OR) {
                        state = State.START;
                        currentField = null;
                        opTokens.clear();
                    } else {
                        state = State.AFTER_VALUE;
                    }
                }
                case AFTER_VALUE -> {
                    if (type == FilterParser.AND || type == FilterParser.OR) {
                        state = State.START;
                        currentField = null;
                        opTokens.clear();
                    }
                }
                case AFTER_MEETS -> {
                    if (isWordOrPath(type)) {
                        state = State.AFTER_FUNCTION_NAME;
                        opTokens.clear();
                        opTokens.add(t);
                    }
                }
                case AFTER_FUNCTION_NAME -> {
                    if (type == FilterParser.LPAREN) {
                        state = State.IN_FUNCTION_ARGS;
                    }
                }
                case IN_FUNCTION_ARGS -> {
                    if (type == FilterParser.RPAREN) {
                        state = State.AFTER_VALUE;
                    }
                }
            }
        }

        return buildHint(state, currentField, opTokens, cursorOffset, ops);
    }

    private static CompletionHint buildHint(
            State state,
            String field,
            List<Token> opTokens,
            int cursorOffset,
            OperatorRegistry ops) {

        return switch (state) {
            case START, IN_PATH -> CompletionHint.builder()
                    .cursorOffset(cursorOffset)
                    .position(CursorPosition.FIELD)
                    .build();

            case AFTER_FIELD, IN_OP -> {
                OperatorDefinition resolved = tryResolveOp(opTokens, ops);
                yield CompletionHint.builder()
                        .cursorOffset(cursorOffset)
                        .position(CursorPosition.OPERATOR)
                        .fieldPath(field)
                        .operator(resolved)
                        .build();
            }

            case AFTER_OP, IN_VALUE -> {
                OperatorDefinition resolved = tryResolveOp(opTokens, ops);
                yield CompletionHint.builder()
                        .cursorOffset(cursorOffset)
                        .position(CursorPosition.VALUE)
                        .fieldPath(field)
                        .operator(resolved)
                        .build();
            }

            case AFTER_VALUE -> CompletionHint.builder()
                    .cursorOffset(cursorOffset)
                    .position(CursorPosition.BOOLEAN_OP)
                    .fieldPath(field)
                    .build();

            case AFTER_MEETS -> CompletionHint.builder()
                    .cursorOffset(cursorOffset)
                    .position(CursorPosition.FUNCTION_NAME)
                    .fieldPath(field)
                    .build();

            case AFTER_FUNCTION_NAME, IN_FUNCTION_ARGS -> {
                String fnName = opTokens.isEmpty() ? null : opTokens.get(0).getText();
                OperatorDefinition resolved = fnName != null
                        ? ops.findByCanonicalOrAlias(fnName).orElse(null)
                        : null;
                yield CompletionHint.builder()
                        .cursorOffset(cursorOffset)
                        .position(CursorPosition.FUNCTION_ARG)
                        .fieldPath(field)
                        .operator(resolved)
                        .argIndex(0)
                        .build();
            }
        };
    }

    private static OperatorDefinition tryResolveOp(List<Token> opTokens, OperatorRegistry ops) {
        if (opTokens.isEmpty()) return null;
        String phrase = opTokens.stream().map(Token::getText).collect(Collectors.joining(" "));
        return ops.findByCanonicalOrAlias(phrase).orElse(null);
    }

    private static boolean isWordOrPath(int type) {
        return type == FilterParser.WORD;
    }

    private static boolean isOpToken(int type) {
        return type == FilterParser.WORD
                || type == FilterParser.EQ
                || type == FilterParser.NEQ
                || type == FilterParser.GT
                || type == FilterParser.GTE
                || type == FilterParser.LT
                || type == FilterParser.LTE;
    }

    private static boolean isValueStart(int type) {
        return type == FilterParser.STRING
                || type == FilterParser.NUMBER
                || type == FilterParser.TRUE
                || type == FilterParser.FALSE
                || type == FilterParser.NULL
                || type == FilterParser.LBRACKET;
    }

    private enum State {
        START,
        IN_PATH,
        AFTER_FIELD,
        IN_OP,
        AFTER_OP,
        IN_VALUE,
        AFTER_VALUE,
        AFTER_MEETS,
        AFTER_FUNCTION_NAME,
        IN_FUNCTION_ARGS
    }
}
