package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for the I.3 comment-attachment pre-pass.
 *
 * The pre-pass walks every TK_COMMENT span and binds it to the
 * nearest adjacent AST node. The printer must preserve every comment
 * in a sensible position. The current I.1 skeleton preserves comments
 * verbatim via gap-copy, so the round-trip is lossless.
 */
public class JsCommentAttachmentTest {

    private static String format(String source) {
        TokenStream stream = JsLexer.tokenize(source);
        JsSyntaxTree tree = JsParser.parseTopLevel(source, stream);
        JsFormatter formatter = new JsFormatter();
        return formatter.format(tree, source, stream, JsFormatter.DEFAULT_INDENT);
    }

    private static JsCommentAttachment build(String source) {
        TokenStream stream = JsLexer.tokenize(source);
        JsSyntaxTree tree = JsParser.parseTopLevel(source, stream);
        JsCommentAttachment att = new JsCommentAttachment(16);
        att.build(tree, source, stream);
        return att;
    }

    @Test
    public void inlineCommentIsPreserved() {
        String src = "var x = 1; // tail comment\nvar y = 2;\n";
        String out = format(src);
        assertTrue("output must keep '// tail comment' verbatim\nout=" + out,
                out.contains("// tail comment"));
    }

    @Test
    public void leadingBlockCommentIsPreserved() {
        String src = "/* header */\nvar x = 1;\n";
        String out = format(src);
        assertTrue("output must keep the header comment\nout=" + out,
                out.contains("/* header */"));
    }

    @Test
    public void multiLineBlockCommentIsPreserved() {
        String src = "/*\n * line one\n * line two\n */\nvar x = 1;\n";
        String out = format(src);
        assertTrue("output must keep the multi-line block comment\nout=" + out,
                out.contains(" * line one") && out.contains(" * line two"));
    }

    @Test
    public void commentBeforeStatementIsAttachedAsLeading() {
        String src = "// above\nvar x = 1;\n";
        JsCommentAttachment att = build(src);
        assertNotNull(att);
        assertTrue("at least one comment must be attached", att.count >= 1);
        // The first comment is "// above". Find an attachment whose
        // start matches it.
        boolean foundLeading = false;
        for (int i = 0; i < att.count; i++) {
            String text = src.substring(att.commentStart[i], att.commentEnd[i]);
            if ("// above".equals(text) && att.isLeading[i] && att.attachedNode[i] != 0) {
                foundLeading = true;
                break;
            }
        }
        assertTrue("comment '// above' must be attached as leading to some node", foundLeading);
    }

    @Test
    public void trailingCommentIsAttachedAsTrailing() {
        String src = "var x = 1; // tail\nvar y = 2;\n";
        JsCommentAttachment att = build(src);
        boolean foundTrailing = false;
        for (int i = 0; i < att.count; i++) {
            String text = src.substring(att.commentStart[i], att.commentEnd[i]);
            if ("// tail".equals(text) && !att.isLeading[i] && att.attachedNode[i] != 0) {
                foundTrailing = true;
                break;
            }
        }
        assertTrue("comment '// tail' must be attached as trailing to some node", foundTrailing);
    }

    @Test
    public void multipleCommentsAreAllAttached() {
        String src = "// a\n// b\nvar x = 1; // c\n// d\n";
        JsCommentAttachment att = build(src);
        assertEquals("expected 4 attached comments, got " + att.count, 4, att.count);
    }

    @Test
    public void roundTripPreservesAllComments() {
        String src = "// header\n" +
                "var x = 1; // inline-x\n" +
                "/* block */\n" +
                "var y = 2; /* block-inline */ var z = 3;\n";
        String out = format(src);
        assertTrue("must preserve // header", out.contains("// header"));
        assertTrue("must preserve // inline-x", out.contains("// inline-x"));
        assertTrue("must preserve /* block */", out.contains("/* block */"));
        assertTrue("must preserve /* block-inline */", out.contains("/* block-inline */"));
    }
}
