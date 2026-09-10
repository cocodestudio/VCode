package com.cocode.vcode.ide.core.language.json;

import org.junit.Test;
import static org.junit.Assert.*;

public class JsonLexerTest {

    private String getTokens(JsonTokenStream stream, String source) {
        StringBuilder sb = new StringBuilder();
        int lastStart = -1;
        for (int i = 0; i < stream.length; i++) {
            if (stream.types[i] == JsonTokenStream.TK_WHITESPACE) continue;
            int start = stream.tokenStart[i];
            if (start != lastStart) {
                if (sb.length() > 0) sb.append("|");
                byte type = stream.types[start];
                sb.append(getTypeName(type)).append(":").append(source.substring(start, getTokenEnd(stream, start)));
                lastStart = start;
            }
        }
        return sb.toString();
    }
    
    private int getTokenEnd(JsonTokenStream stream, int start) {
        int i = start;
        while (i < stream.length && stream.tokenStart[i] == start) {
            i++;
        }
        return i;
    }
    
    private String getTypeName(byte type) {
        switch (type) {
            case JsonTokenStream.TK_BRACE_OPEN: return "{";
            case JsonTokenStream.TK_BRACE_CLOSE: return "}";
            case JsonTokenStream.TK_BRACKET_OPEN: return "[";
            case JsonTokenStream.TK_BRACKET_CLOSE: return "]";
            case JsonTokenStream.TK_COLON: return ":";
            case JsonTokenStream.TK_COMMA: return ",";
            case JsonTokenStream.TK_STRING: return "STR";
            case JsonTokenStream.TK_NUMBER: return "NUM";
            case JsonTokenStream.TK_KEYWORD: return "KW";
            case JsonTokenStream.TK_COMMENT: return "COM";
            case JsonTokenStream.TK_ERROR: return "ERR";
            default: return "UNK";
        }
    }

    @Test
    public void testBasicJson() {
        String source = "{ \"key\": 123, \"b\": true, \"c\": null }";
        JsonTokenStream stream = JsonLexer.tokenize(source);
        String expected = "{:{|STR:\"key\"|:::|NUM:123|,:,|STR:\"b\"|:::|KW:true|,:,|STR:\"c\"|:::|KW:null|}:}";
        assertEquals(expected, getTokens(stream, source));
    }

    @Test
    public void testComments() {
        String source = "[ 1, // line\n /* block */ 2 ]";
        JsonTokenStream stream = JsonLexer.tokenize(source);
        String expected = "[:[|NUM:1|,:,|COM:// line|COM:/* block */|NUM:2|]:]";
        assertEquals(expected, getTokens(stream, source));
    }

    @Test
    public void testFaultTolerance() {
        String source = "{ \"unterminated\n \"key\": false }";
        JsonTokenStream stream = JsonLexer.tokenize(source);
        // "unterminated is TK_STRING, then newline is whitespace, then "key" is TK_STRING
        String expected = "{:{|STR:\"unterminated|STR:\"key\"|:::|KW:false|}:}";
        assertEquals(expected, getTokens(stream, source));
    }

    @Test
    public void testTrailingComma() {
        String source = "[ 1, 2, ]";
        JsonTokenStream stream = JsonLexer.tokenize(source);
        String expected = "[:[|NUM:1|,:,|NUM:2|,:,|]:]";
        assertEquals(expected, getTokens(stream, source));
    }

    @Test
    public void testErrors() {
        String source = "{ xyz, }";
        JsonTokenStream stream = JsonLexer.tokenize(source);
        String expected = "{:{|ERR:xyz|,:,|}:}";
        assertEquals(expected, getTokens(stream, source));
    }

    @Test
    public void testValidNumbers() {
        String source = "[ 0, -0, 123, -456, 0.5, 1.25e+2, 1e-3 ]";
        JsonTokenStream stream = JsonLexer.tokenize(source);
        String expected = "[:[|NUM:0|,:,|NUM:-0|,:,|NUM:123|,:,|NUM:-456|,:,|NUM:0.5|,:,|NUM:1.25e+2|,:,|NUM:1e-3|]:]";
        assertEquals(expected, getTokens(stream, source));
    }

    @Test
    public void testMalformedNumbers() {
        String source = "[ 1.2.3, -., --5, 1e, 1. ]";
        JsonTokenStream stream = JsonLexer.tokenize(source);
        String expected = "[:[|ERR:1.2.3|,:,|ERR:-.|,:,|ERR:--5|,:,|ERR:1e|,:,|ERR:1.|]:]";
        assertEquals(expected, getTokens(stream, source));
    }
}
