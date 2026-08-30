package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for the AST-driven JS formatter.
 *
 * I.2's acceptance check is that verbatim token spans (strings, template
 * literals, comments, regex literals) are byte-identical after formatting.
 * These tests verify that. Indentation/structural behaviour of non-verbatim
 * code is covered by I.1's separate acceptance check and is intentionally
 * not asserted here.
 */
public class JsFormatterVerbatimTest {

    private static String format(String source) {
        TokenStream stream = JsLexer.tokenize(source);
        JsSyntaxTree tree = JsParser.parseTopLevel(source, stream);
        JsFormatter formatter = new JsFormatter();
        return formatter.format(tree, source, stream, JsFormatter.DEFAULT_INDENT);
    }

    @Test
    public void multiLineTemplateLiteralIsByteIdentical() {
        String src = "var t = `\n  line1\n  line2\n`;\n";
        String out = format(src);
        assertEquals(src, out);
    }

    @Test
    public void blockCommentIsByteIdentical() {
        String src = "/*\n * hello\n *\n *   indented line\n */\nvar x = 1;\n";
        String out = format(src);
        assertEquals(src, out);
    }

    @Test
    public void lineCommentIsByteIdentical() {
        String src = "// header comment\nvar y = 2;\n";
        String out = format(src);
        assertEquals(src, out);
    }

    @Test
    public void regexLiteralIsByteIdentical() {
        String src = "var r = /abc/gi;\n";
        String out = format(src);
        assertEquals(src, out);
    }

    @Test
    public void emptyInputReturnsEmpty() {
        assertEquals("", format(""));
    }

    @Test
    public void nullTreeReturnsEmpty() {
        assertEquals("", new JsFormatter().format(null, "var x;"));
    }

    @Test
    public void nullSourceReturnsEmpty() {
        TokenStream stream = JsLexer.tokenize("");
        JsSyntaxTree tree = JsParser.parseTopLevel("", stream);
        assertEquals("", new JsFormatter().format(tree, null, stream, JsFormatter.DEFAULT_INDENT));
    }

    @Test
    public void formatProducesOutputForValidProgram() {
        String src = "var x = 1;\n";
        String out = format(src);
        assertTrue("formatter must produce non-empty output for valid input", out != null && out.length() > 0);
    }
}
