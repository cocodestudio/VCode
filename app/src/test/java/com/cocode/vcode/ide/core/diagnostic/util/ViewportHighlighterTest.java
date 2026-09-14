package com.cocode.vcode.ide.core.diagnostic.util;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the shared viewport-slice highlighter.
 */
public class ViewportHighlighterTest {

    /**
     * Build a TokenStream-shaped pair of arrays from a tiny
     * hand-rolled DSL. Each entry is a {@code (type, startOffset)}
     * pair; the helper fills the parallel per-byte arrays the way the
     * lexer would.
     */
    private static TokenStream streamOf(Object[][] tokens, int totalLength) {
        byte[] types = new byte[totalLength];
        int[] starts = new int[totalLength];
        for (Object[] t : tokens) {
            byte type = (Byte) t[0];
            int start = (Integer) t[1];
            int end = (Integer) t[2];
            for (int i = start; i < end && i < totalLength; i++) {
                types[i] = type;
                starts[i] = start;
            }
        }
        return new TokenStream(types, starts);
    }

    private static final ViewportHighlighter.ColorResolver STUB_RESOLVER =
            new ViewportHighlighter.ColorResolver() {
                @Override
                public int colorFor(byte tokenType) {
                    // Return the token type as a pseudo-colour so each
                    // distinct token type is observable in the output.
                    return tokenType;
                }
            };

    @Test
    public void emptyVisibleRangeReturnsEmpty() {
        TokenStream s = streamOf(new Object[][]{
                {TokenStream.TK_KEYWORD, 0, 3},
                {TokenStream.TK_IDENTIFIER, 4, 7},
        }, 8);
        List<ViewportHighlighter.ViewportSpan> out = ViewportHighlighter.highlight(
                s.types, s.tokenStart, s.length, 3, 3, STUB_RESOLVER);
        assertNotNull(out);
        assertEquals(0, out.size());
    }

    @Test
    public void nullInputsReturnEmpty() {
        List<ViewportHighlighter.ViewportSpan> out = ViewportHighlighter.highlight(
                null, null, 0, 0, 10, STUB_RESOLVER);
        assertNotNull(out);
        assertEquals(0, out.size());
    }

    @Test
    public void singleTokenInsideRangeIsEmitted() {
        // Adjacent tokens (no gap) so the resolver sees only the
        // two distinct token types.
        TokenStream s = streamOf(new Object[][]{
                {TokenStream.TK_KEYWORD, 0, 3},
                {TokenStream.TK_IDENTIFIER, 3, 7},
        }, 7);
        List<ViewportHighlighter.ViewportSpan> out = ViewportHighlighter.highlight(
                s.types, s.tokenStart, s.length, 0, 7, STUB_RESOLVER);
        assertEquals(2, out.size());
        assertEquals(0, out.get(0).startOffset);
        assertEquals(3, out.get(0).endOffset);
        assertEquals(TokenStream.TK_KEYWORD, out.get(0).color);
        assertEquals(3, out.get(1).startOffset);
        assertEquals(7, out.get(1).endOffset);
        assertEquals(TokenStream.TK_IDENTIFIER, out.get(1).color);
    }

    @Test
    public void tokenOutsideRangeIsSkipped() {
        TokenStream s = streamOf(new Object[][]{
                {TokenStream.TK_KEYWORD, 0, 3},
                {TokenStream.TK_IDENTIFIER, 3, 7},
                {TokenStream.TK_STRING, 7, 20},
        }, 20);
        // Visible range covers only [0, 7) — string token at [7, 20)
        // must be excluded entirely.
        List<ViewportHighlighter.ViewportSpan> out = ViewportHighlighter.highlight(
                s.types, s.tokenStart, s.length, 0, 7, STUB_RESOLVER);
        assertEquals(2, out.size());
        assertTrue(out.get(0).endOffset <= 7);
        assertTrue(out.get(1).endOffset <= 7);
    }

    @Test
    public void tokenStraddlingStartIsClipped() {
        TokenStream s = streamOf(new Object[][]{
                {TokenStream.TK_STRING, 0, 10},
        }, 10);
        // Visible range [5, 10) — same string token, only [5, 10).
        List<ViewportHighlighter.ViewportSpan> out = ViewportHighlighter.highlight(
                s.types, s.tokenStart, s.length, 5, 10, STUB_RESOLVER);
        assertEquals(1, out.size());
        assertEquals(5, out.get(0).startOffset);
        assertEquals(10, out.get(0).endOffset);
    }

    @Test
    public void tokenStraddlingEndIsClipped() {
        TokenStream s = streamOf(new Object[][]{
                {TokenStream.TK_COMMENT, 0, 10},
        }, 10);
        // Visible range [0, 5) — comment is cut at the visible end.
        List<ViewportHighlighter.ViewportSpan> out = ViewportHighlighter.highlight(
                s.types, s.tokenStart, s.length, 0, 5, STUB_RESOLVER);
        assertEquals(1, out.size());
        assertEquals(0, out.get(0).startOffset);
        assertEquals(5, out.get(0).endOffset);
    }

    @Test
    public void resolverCanSkipHighlighting() {
        TokenStream s = streamOf(new Object[][]{
                {TokenStream.TK_WHITESPACE, 0, 2},
                {TokenStream.TK_KEYWORD, 2, 5},
        }, 5);
        ViewportHighlighter.ColorResolver skipWhitespace = new ViewportHighlighter.ColorResolver() {
            @Override
            public int colorFor(byte tokenType) {
                if (tokenType == TokenStream.TK_WHITESPACE) return -1;
                return tokenType;
            }
        };
        List<ViewportHighlighter.ViewportSpan> out = ViewportHighlighter.highlight(
                s.types, s.tokenStart, s.length, 0, 5, skipWhitespace);
        assertEquals(1, out.size());
        assertEquals(TokenStream.TK_KEYWORD, out.get(0).color);
    }

    @Test
    public void largeStreamScrolledViewportDoesNotTouchHiddenTokens() {
        // Build a stream of 10,000 "tokens", each 10 bytes long, and
        // ask for a tiny visible window. The implementation must
        // binary-search to the window's first token and stop at the
        // last token whose start is < endOffset.
        int totalLen = 100_000;
        byte[] types = new byte[totalLen];
        int[] starts = new int[totalLen];
        for (int i = 0; i < 10_000; i++) {
            byte t = (i % 2 == 0) ? TokenStream.TK_KEYWORD : TokenStream.TK_IDENTIFIER;
            int s = i * 10;
            int e = s + 10;
            for (int k = s; k < e; k++) {
                types[k] = t;
                starts[k] = s;
            }
        }
        TokenStream s = new TokenStream(types, starts);

        // Visible range is bytes [50_000, 50_100): the 5000th to the
        // 5009th token. Should emit exactly 10 spans.
        List<ViewportHighlighter.ViewportSpan> out = ViewportHighlighter.highlight(
                s.types, s.tokenStart, s.length, 50_000, 50_100, STUB_RESOLVER);
        assertEquals(10, out.size());
        assertEquals(50_000, out.get(0).startOffset);
        assertEquals(50_010, out.get(0).endOffset);
        assertEquals(50_090, out.get(9).startOffset);
        assertEquals(50_100, out.get(9).endOffset);
    }

    @Test
    public void endOffsetClampedToStreamLength() {
        TokenStream s = streamOf(new Object[][]{
                {TokenStream.TK_STRING, 0, 5},
        }, 5);
        // Visible range is past EOF.
        List<ViewportHighlighter.ViewportSpan> out = ViewportHighlighter.highlight(
                s.types, s.tokenStart, s.length, 0, 999, STUB_RESOLVER);
        assertEquals(1, out.size());
        assertEquals(0, out.get(0).startOffset);
        assertEquals(5, out.get(0).endOffset);
    }

    @Test
    public void startOffsetBelowZeroClamped() {
        TokenStream s = streamOf(new Object[][]{
                {TokenStream.TK_STRING, 0, 5},
        }, 5);
        List<ViewportHighlighter.ViewportSpan> out = ViewportHighlighter.highlight(
                s.types, s.tokenStart, s.length, -10, 5, STUB_RESOLVER);
        assertEquals(1, out.size());
        assertEquals(0, out.get(0).startOffset);
        assertEquals(5, out.get(0).endOffset);
    }

    @Test
    public void allWhitespaceStreamReturnsEmpty() {
        TokenStream s = streamOf(new Object[][]{
                {TokenStream.TK_WHITESPACE, 0, 5},
        }, 5);
        ViewportHighlighter.ColorResolver skipWs = new ViewportHighlighter.ColorResolver() {
            @Override
            public int colorFor(byte tokenType) {
                if (tokenType == TokenStream.TK_WHITESPACE) return -1;
                return tokenType;
            }
        };
        List<ViewportHighlighter.ViewportSpan> out = ViewportHighlighter.highlight(
                s.types, s.tokenStart, s.length, 0, 5, skipWs);
        assertEquals(0, out.size());
    }

    @Test
    public void zeroLengthStreamReturnsEmpty() {
        TokenStream s = streamOf(new Object[][]{}, 0);
        List<ViewportHighlighter.ViewportSpan> out = ViewportHighlighter.highlight(
                s.types, s.tokenStart, s.length, 0, 10, STUB_RESOLVER);
        assertEquals(0, out.size());
    }
}
