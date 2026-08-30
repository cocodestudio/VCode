package com.cocode.vcode.ide.core.language.css;

import org.junit.Test;
import static org.junit.Assert.*;

public class CssLexerTest {

    private String getTokens(CssTokenStream stream, String source) {
        StringBuilder sb = new StringBuilder();
        int lastType = -1;
        int lastStart = -1;
        for (int i = 0; i < stream.length; i++) {
            if (stream.types[i] != CssTokenStream.TK_NONE) {
                if (stream.types[i] != lastType || stream.tokenStart[i] != lastStart) {
                    if (sb.length() > 0) sb.append("|");
                    sb.append(typeToString(stream.types[i])).append(":");
                    lastType = stream.types[i];
                    lastStart = stream.tokenStart[i];
                }
                sb.append(source.charAt(i));
            }
        }
        return sb.toString();
    }

    private String typeToString(byte type) {
        switch (type) {
            case CssTokenStream.TK_SELECTOR: return "SEL";
            case CssTokenStream.TK_PROPERTY: return "PROP";
            case CssTokenStream.TK_VALUE: return "VAL";
            case CssTokenStream.TK_PUNCT: return "PUNCT";
            case CssTokenStream.TK_COMMENT: return "COMM";
            default: return "UNK";
        }
    }

    @Test
    public void testBasicRule() {
        String source = ".class { color: red; }";
        CssTokenStream stream = CssLexer.tokenize(source);
        String expected = "SEL:.class|PUNCT:{|PROP:color|PUNCT::|VAL:red|PUNCT:;|PUNCT:}";
        assertEquals(expected, getTokens(stream, source));
    }

    @Test
    public void testComments() {
        String source = "/* c1 */ .class /* c2 */ { /* c3 */ color: /* c4 */ red; }";
        CssTokenStream stream = CssLexer.tokenize(source);
        String expected = "COMM:/* c1 */|SEL:.class|COMM:/* c2 */|PUNCT:{|COMM:/* c3 */|PROP:color|PUNCT::|COMM:/* c4 */|VAL:red|PUNCT:;|PUNCT:}";
        assertEquals(expected, getTokens(stream, source));
    }

    @Test
    public void testStrings() {
        String source = ".class[data=\"{\"] { content: \";\"; }";
        CssTokenStream stream = CssLexer.tokenize(source);
        String expected = "SEL:.class[data=\"{\"]|PUNCT:{|PROP:content|PUNCT::|VAL:\";\"|PUNCT:;|PUNCT:}";
        assertEquals(expected, getTokens(stream, source));
    }

    @Test
    public void testAtRule() {
        String source = "@import url(\"foo.css\");";
        CssTokenStream stream = CssLexer.tokenize(source);
        String expected = "SEL:@import|SEL:url(\"foo.css\")|PUNCT:;";
        assertEquals(expected, getTokens(stream, source));
    }

    @Test
    public void testMediaBlock() {
        String source = "@media screen { .c { color: red; } }";
        CssTokenStream stream = CssLexer.tokenize(source);
        String expected = "SEL:@media|SEL:screen|PUNCT:{|SEL:.c|PUNCT:{|PROP:color|PUNCT::|VAL:red|PUNCT:;|PUNCT:}|PUNCT:}";
        assertEquals(expected, getTokens(stream, source));
    }
}
