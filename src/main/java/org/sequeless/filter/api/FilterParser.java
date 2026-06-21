package org.sequeless.filter.api;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.sequeless.filter.internal.CursorAnalyzer;
import org.sequeless.filter.internal.FilterBuildingVisitor;
import org.sequeless.filter.internal.parser.FilterLexer;
import org.sequeless.filter.internal.parser.FilterParser.FilterContext;

/**
 * Entry point for parsing filter expressions from DSL strings.
 *
 * <p>Use {@link #parse} when the input is expected to be valid and errors should propagate.
 * Use {@link #parsePartial} when building auto-complete UIs or inline validation.
 */
public final class FilterParser {

    private FilterParser() {}

    /**
     * Parses a complete filter expression.
     *
     * @param input  DSL filter string
     * @param ops    operator registry for resolving operator phrases
     * @param fields field registry for validating field paths
     * @return the parsed AST
     * @throws FilterParseException if the input is syntactically or semantically invalid
     */
    public static FilterNode parse(String input, OperatorRegistry ops, FieldRegistry fields) {
        CollectingErrorListener errors = new CollectingErrorListener();
        org.sequeless.filter.internal.parser.FilterParser parser = buildParser(input, errors);
        FilterContext tree = parser.filter();

        if (!errors.messages.isEmpty()) {
            SyntaxError first = errors.messages.get(0);
            throw new FilterParseException(first.message(), first.offset());
        }

        return new FilterBuildingVisitor(ops, fields).visit(tree);
    }

    /**
     * Attempts to parse the input up to {@code cursorOffset} and returns a {@link ParseResult}.
     * Never throws; always returns either a {@link ParseResult.Complete} or a
     * {@link ParseResult.Partial} with the best-effort AST and a completion hint.
     *
     * @param input        DSL filter string (may be incomplete)
     * @param cursorOffset character index of the cursor in {@code input}
     * @param ops          operator registry
     * @param fields       field registry
     */
    public static ParseResult parsePartial(
            String input, int cursorOffset, OperatorRegistry ops, FieldRegistry fields) {
        try {
            FilterNode ast = parse(input, ops, fields);
            // If the cursor is at or past the end of the meaningful input, the user may want to
            // append a boolean operator — surface BOOLEAN_OP rather than returning Complete.
            String meaningful = input.stripTrailing();
            if (cursorOffset >= meaningful.length() && !meaningful.isEmpty()) {
                CompletionHint hint = CompletionHint.builder()
                        .cursorOffset(cursorOffset)
                        .position(CursorPosition.BOOLEAN_OP)
                        .build();
                return new ParseResult.Partial(ast, hint);
            }
            return new ParseResult.Complete(ast);
        } catch (Exception ignored) {
            // fall through to partial analysis
        }

        CompletionHint hint = inferHint(input, cursorOffset, ops, fields);
        return new ParseResult.Partial(null, hint);
    }

    // ---- private helpers ----

    private static org.sequeless.filter.internal.parser.FilterParser buildParser(
            String input, BaseErrorListener errorListener) {
        FilterLexer lexer = new FilterLexer(CharStreams.fromString(input));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        org.sequeless.filter.internal.parser.FilterParser parser =
                new org.sequeless.filter.internal.parser.FilterParser(tokenStream);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        return parser;
    }

    /**
     * Tokenises input up to cursorOffset and uses a simple state machine to infer where
     * the cursor is within the DSL grammar.
     */
    private static CompletionHint inferHint(
            String input, int cursorOffset, OperatorRegistry ops, FieldRegistry fields) {
        String upToCursor = cursorOffset <= input.length() ? input.substring(0, cursorOffset) : input;

        FilterLexer lexer = new FilterLexer(CharStreams.fromString(upToCursor));
        lexer.removeErrorListeners();
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        tokenStream.fill();

        List<Token> tokens = new ArrayList<>();
        for (Token t : tokenStream.getTokens()) {
            if (t.getType() != Token.EOF) tokens.add(t);
        }

        return CursorAnalyzer.analyze(tokens, cursorOffset, ops, fields);
    }

    // ---- error listener ----

    private record SyntaxError(String message, int offset) {}

    private static final class CollectingErrorListener extends BaseErrorListener {
        final List<SyntaxError> messages = new ArrayList<>();

        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String msg,
                RecognitionException e) {
            messages.add(new SyntaxError(msg, charPositionInLine));
        }
    }
}
