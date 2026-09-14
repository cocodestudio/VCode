package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for the I.4 blank-line collapsing rule.
 *
 * Consecutive blank lines between top-level statements must be capped
 * to exactly one preserved blank line. Blank lines inside strings,
 * template literals, and comments must be untouched.
 */
public class JsBlankLineCollapsingTest {

    private static String format(String source) {
        TokenStream stream = JsLexer.tokenize(source);
        JsSyntaxTree tree = JsParser.parseTopLevel(source, stream);
        JsFormatter formatter = new JsFormatter();
        return formatter.format(tree, source, stream, JsFormatter.DEFAULT_INDENT);
    }

    @Test
    public void threeBlankLinesCollapseToOne() {
        String src = "var a = 1;\n\n\n\nvar b = 2;\n";
        String out = format(src);
        // Expected: var a = 1;\n\nvar b = 2;\n (one blank line)
        assertEquals("var a = 1;\n\nvar b = 2;\n", out);
    }

    @Test
    public void oneBlankLineIsPreserved() {
        String src = "var a = 1;\n\nvar b = 2;\n";
        String out = format(src);
        assertEquals(src, out);
    }

    @Test
    public void zeroBlankLinesIsPreserved() {
        String src = "var a = 1;\nvar b = 2;\n";
        String out = format(src);
        assertEquals(src, out);
    }

    @Test
    public void manyBlankLinesCollapseToOne() {
        String src = "var a = 1;\n\n\n\n\n\n\n\nvar b = 2;\n";
        String out = format(src);
        assertEquals("var a = 1;\n\nvar b = 2;\n", out);
    }

    @Test
    public void blankLinesInsideTemplateLiteralArePreserved() {
        String src = "var t = `\n\n\n\n  body\n\n\n\n`;\n";
        String out = format(src);
        // The blank lines inside the template literal are part of the
        // N_VAR_DECL's verbatim span and must be preserved.
        assertTrue("template-literal blank lines must be preserved\nout=" + out,
                out.contains("`\n\n\n\n  body\n\n\n\n`"));
    }

    @Test
    public void blankLinesInsideBlockCommentArePreserved() {
        String src = "/*\n\n\n\n  body\n\n\n\n*/\nvar x = 1;\n";
        String out = format(src);
        System.err.println("=== bc ===");
        System.err.println("SRC len=" + src.length() + ": [" + src.replace("\n", "\\n") + "]");
        System.err.println("OUT len=" + out.length() + ": [" + out.replace("\n", "\\n") + "]");
        assertTrue("block-comment blank lines must be preserved\nout=" + out,
                out.contains("/*\n\n\n\n  body\n\n\n\n*/"));
    }

    @Test
    public void blankLinesAfterVerbatimSpanAreCollapsed() {
        // The var declaration contains 4 blank lines inside its
        // template literal (preserved), then 4 blank lines after the
        // semicolon (collapsed to 1).
        String src = "var t = `\n\n\n\n  body\n\n\n\n`;\n\n\n\nvar y = 2;\n";
        String out = format(src);
        assertTrue("template-literal blank lines must be preserved\nout=" + out,
                out.contains("`\n\n\n\n  body\n\n\n\n`"));
        // The trailing 4 newlines after `;` collapse to 2 newlines
        // (= one blank line).
        assertTrue("output should end with collapsed blank line + var y\nout=" + out,
                out.endsWith(";\n\nvar y = 2;\n"));
    }
}
