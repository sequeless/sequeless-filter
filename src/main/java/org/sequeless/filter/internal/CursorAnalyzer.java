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

    public static CompletionHint analyze(
            List<Token> tokens, int cursorOffset, OperatorRegistry ops, FieldRegistry fields) {

        State state = State.START;
        String currentField = null;
        List<Token> opTokens = new ArrayList<>();
        Token lastToken = tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);

        for (Token t : tokens) {
            int type = t.getType();
            state = transition(state, type, t, opTokens);

            if (type == FilterParser.WORD && state == State.IN_PATH) {
                currentField = buildPath(currentField, t.getText(), state);
            } else if (type == FilterParser.ANY) {
                currentField = "any";
            } else if (type == FilterParser.DOT) {
                // path separator, don't reset currentField
            } else if (state == State.IN_PATH && type == FilterParser.WORD) {
                currentField = buildPath(currentField, t.getText(), state);
            }
        }

        // Re-derive currentField from a clean walk (state machine above is complex for field tracking)
        currentField = extractField(tokens);

        // Trailing-whitespace advancement: if the cursor is after the last token, the user has
        // finished typing that token and is ready for the next one.
        boolean trailingSpace = lastToken != null
                && cursorOffset
                        > lastToken.getStartIndex() + lastToken.getText().length();

        if (trailingSpace) {
            state = advanceOnTrailingSpace(state, opTokens, ops);
        }

        return buildHint(state, currentField, opTokens, cursorOffset, ops);
    }

    private static State transition(State state, int type, Token t, List<Token> opTokens) {
        return switch (state) {
            case START -> {
                if (type == FilterParser.WORD) {
                    opTokens.clear();
                    yield State.IN_PATH;
                } else if (type == FilterParser.ANY) {
                    opTokens.clear();
                    yield State.AFTER_FIELD;
                }
                yield State.START;
            }
            case IN_PATH -> {
                if (type == FilterParser.DOT) {
                    yield State.IN_PATH_DOT;
                } else if (type == FilterParser.MEETS) {
                    opTokens.clear();
                    yield State.AFTER_MEETS;
                } else if (type == FilterParser.AND || type == FilterParser.OR) {
                    opTokens.clear();
                    yield State.START;
                } else if (isOpToken(type)) {
                    // First token after field path = start of operator phrase
                    opTokens.clear();
                    opTokens.add(t);
                    yield State.IN_OP;
                }
                yield State.IN_PATH;
            }
            case IN_PATH_DOT -> {
                if (type == FilterParser.WORD) yield State.IN_PATH;
                yield State.IN_PATH_DOT;
            }
            case AFTER_FIELD -> {
                if (type == FilterParser.MEETS) {
                    opTokens.clear();
                    yield State.AFTER_MEETS;
                } else if (isOpToken(type)) {
                    opTokens.clear();
                    opTokens.add(t);
                    yield State.IN_OP;
                } else if (type == FilterParser.AND || type == FilterParser.OR) {
                    opTokens.clear();
                    yield State.START;
                }
                yield State.AFTER_FIELD;
            }
            case IN_OP -> {
                if (isOpToken(type)) {
                    opTokens.add(t);
                    yield State.IN_OP;
                } else if (isValueStart(type)) {
                    yield State.IN_VALUE;
                } else if (type == FilterParser.AND || type == FilterParser.OR) {
                    opTokens.clear();
                    yield State.START;
                }
                yield State.AFTER_OP;
            }
            case AFTER_OP, IN_VALUE -> {
                if (type == FilterParser.AND || type == FilterParser.OR) {
                    opTokens.clear();
                    yield State.START;
                }
                yield State.AFTER_VALUE;
            }
            case AFTER_VALUE -> {
                if (type == FilterParser.AND || type == FilterParser.OR) {
                    opTokens.clear();
                    yield State.START;
                }
                yield State.AFTER_VALUE;
            }
            case AFTER_MEETS -> {
                if (isOpToken(type)) {
                    opTokens.clear();
                    opTokens.add(t);
                    yield State.AFTER_FUNCTION_NAME;
                }
                yield State.AFTER_MEETS;
            }
            case AFTER_FUNCTION_NAME -> {
                if (type == FilterParser.LPAREN) yield State.IN_FUNCTION_ARGS;
                yield State.AFTER_FUNCTION_NAME;
            }
            case IN_FUNCTION_ARGS -> {
                if (type == FilterParser.RPAREN) yield State.AFTER_VALUE;
                yield State.IN_FUNCTION_ARGS;
            }
        };
    }

    /** Advance state when the cursor has whitespace after the last token. */
    private static State advanceOnTrailingSpace(State state, List<Token> opTokens, OperatorRegistry ops) {
        return switch (state) {
            case IN_PATH, IN_PATH_DOT -> State.AFTER_FIELD;
            case IN_OP -> {
                // If current op tokens form a known operator, move to value position
                String phrase = opTokens.stream().map(Token::getText).collect(Collectors.joining(" "));
                yield ops.findByCanonicalOrAlias(phrase).isPresent() ? State.AFTER_OP : State.IN_OP;
            }
            default -> state;
        };
    }

    /** Extracts the current field path from the token list (first WORD/DOT sequence). */
    private static String extractField(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        boolean inPath = false;
        for (Token t : tokens) {
            int type = t.getType();
            if (!inPath && type == FilterParser.WORD) {
                sb.append(t.getText());
                inPath = true;
            } else if (inPath && type == FilterParser.DOT) {
                sb.append('.');
            } else if (inPath && type == FilterParser.WORD && sb.length() > 0 && sb.charAt(sb.length() - 1) == '.') {
                sb.append(t.getText());
            } else if (inPath) {
                break;
            } else if (type == FilterParser.ANY) {
                return "any";
            } else if (type == FilterParser.AND || type == FilterParser.OR) {
                sb.setLength(0);
                inPath = false;
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static String buildPath(String current, String segment, State state) {
        if (current == null || current.isEmpty()) return segment;
        return current + "." + segment;
    }

    private static CompletionHint buildHint(
            State state, String field, List<Token> opTokens, int cursorOffset, OperatorRegistry ops) {

        return switch (state) {
            case START, IN_PATH, IN_PATH_DOT -> CompletionHint.builder()
                    .cursorOffset(cursorOffset)
                    .position(CursorPosition.FIELD)
                    .build();

            case AFTER_FIELD, IN_OP -> CompletionHint.builder()
                    .cursorOffset(cursorOffset)
                    .position(CursorPosition.OPERATOR)
                    .fieldPath(field)
                    .operator(tryResolveOp(opTokens, ops))
                    .build();

            case AFTER_OP, IN_VALUE -> CompletionHint.builder()
                    .cursorOffset(cursorOffset)
                    .position(CursorPosition.VALUE)
                    .fieldPath(field)
                    .operator(tryResolveOp(opTokens, ops))
                    .build();

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
                OperatorDefinition resolved =
                        fnName != null ? ops.findByCanonicalOrAlias(fnName).orElse(null) : null;
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
        IN_PATH_DOT,
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
