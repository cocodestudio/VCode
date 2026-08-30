package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.diagnostic.util.ViewportHighlighter;
import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for the JS wiring of the J.1 viewport highlighter.
 * Uses the resolver-supplied overload so no Android Context is needed.
 */
public class JsViewportHighlighterTest {

    /** Pseudo-colour resolver: returns the token type as the colour
     *  so we can identify which token was emitted in the output. */
    private static final ViewportHighlighter.ColorResolver STUB_RESOLVER =
            new ViewportHighlighter.ColorResolver() {
                @Override
                public int colorFor(byte tokenType) {
                    if (tokenType == TokenStream.TK_WHITESPACE) return -1;
                    return tokenType;
                }
            };

    @Test
    public void keywordInVisibleRangeIsEmitted() {
        String source = "var x = 1;";
        TokenStream stream = JsLexer.tokenize(source);
        List<HighlightToken> tokens = JsSyntaxHighlighter.forTest().highlightViewport(
                source, stream, 0, source.length(), 0, STUB_RESOLVER);
        assertNotNull(tokens);
        boolean foundVar = false;
        for (HighlightToken t : tokens) {
            if (t.startCol == 0 && t.endCol == 3 && t.color == TokenStream.TK_KEYWORD) {
                foundVar = true;
                break;
            }
        }
        assertTrue("expected a keyword token at (line=0, col=0..3) for 'var'", foundVar);
    }

    @Test
    public void visibleRangeThatCoversNothingReturnsEmpty() {
        String source = "var x = 1;";
        TokenStream stream = JsLexer.tokenize(source);
        List<HighlightToken> tokens = JsSyntaxHighlighter.forTest().highlightViewport(
                source, stream, 5, 5, 0, STUB_RESOLVER);
        assertEquals(0, tokens.size());
    }

    @Test
    public void stringTokenIsHighlightedAsString() {
        String source = "var s = 'hello';";
        TokenStream stream = JsLexer.tokenize(source);
        List<HighlightToken> tokens = JsSyntaxHighlighter.forTest().highlightViewport(
                source, stream, 0, source.length(), 0, STUB_RESOLVER);
        boolean foundString = false;
        for (HighlightToken t : tokens) {
            if (t.startCol == 8 && t.endCol >= 14 && t.color == TokenStream.TK_STRING) {
                foundString = true;
                break;
            }
        }
        assertTrue("expected a string-colour token covering 'hello'", foundString);
    }

    @Test
    public void partialVisibleRangeOnlyTouchesVisibleTokens() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) sb.append("var x").append(i).append(" = 1;\n");
        String source = sb.toString();
        TokenStream stream = JsLexer.tokenize(source);
        int start = source.length() / 2;
        int end = start + 100;
        List<HighlightToken> tokens = JsSyntaxHighlighter.forTest().highlightViewport(
                source, stream, start, end, 0, STUB_RESOLVER);
        assertNotNull(tokens);
        assertTrue("viewport highlighter should produce a bounded result, got " + tokens.size(),
                tokens.size() < 50);
    }

    @Test
    public void multiLineVisibleRangePreservesLineIndex() {
        String source = "var a = 1;\nvar b = 2;\nvar c = 3;\n";
        TokenStream stream = JsLexer.tokenize(source);
        int start = "var a = 1;\n".length();
        int end = start + "var b = 2;\n".length();
        List<HighlightToken> tokens = JsSyntaxHighlighter.forTest().highlightViewport(
                source, stream, start, end, 1, STUB_RESOLVER);
        assertNotNull(tokens);
        for (HighlightToken t : tokens) {
            assertEquals("line index must be firstVisibleLine (1)", 1, t.line);
        }
    }
}
