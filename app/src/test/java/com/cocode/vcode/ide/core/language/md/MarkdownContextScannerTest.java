package com.cocode.vcode.ide.core.language.md;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MarkdownContextScannerTest {

    @Test
    public void testCodeBlockLanguageOpeningFence() {
        String text = "```\n";
        MarkdownContextScanner.MarkdownContext ctx = MarkdownContextScanner.scan(text, 3);
        assertNotNull(ctx);
        assertEquals(MarkdownContextScanner.ContextType.CODE_BLOCK_LANGUAGE, ctx.type);
        assertEquals("", ctx.query);
        assertEquals(0, ctx.replaceLength);

        String textWithLang = "```js\n";
        MarkdownContextScanner.MarkdownContext ctx2 = MarkdownContextScanner.scan(textWithLang, 5);
        assertNotNull(ctx2);
        assertEquals(MarkdownContextScanner.ContextType.CODE_BLOCK_LANGUAGE, ctx2.type);
        assertEquals("js", ctx2.query);
        assertEquals(2, ctx2.replaceLength);

        String textTilde = "   ~~~python\n";
        MarkdownContextScanner.MarkdownContext ctx3 = MarkdownContextScanner.scan(textTilde, textTilde.indexOf('\n'));
        assertNotNull(ctx3);
        assertEquals(MarkdownContextScanner.ContextType.CODE_BLOCK_LANGUAGE, ctx3.type);
        assertEquals("python", ctx3.query);
    }

    @Test
    public void testEmbeddedCodeBlockBody() {
        String text = "```js\nconst x = 10;\n```\n";
        int cursor = text.indexOf("const");
        MarkdownContextScanner.MarkdownContext ctx = MarkdownContextScanner.scan(text, cursor);
        assertNotNull(ctx);
        assertEquals(MarkdownContextScanner.ContextType.EMBEDDED_CODE_BLOCK, ctx.type);
        assertEquals("js", ctx.language);

        String textJson = "# Title\n\n```json\n{\n  \"key\": 1\n}\n```\n";
        int cursorJson = textJson.indexOf("\"key\"");
        MarkdownContextScanner.MarkdownContext ctxJson = MarkdownContextScanner.scan(textJson, cursorJson);
        assertNotNull(ctxJson);
        assertEquals(MarkdownContextScanner.ContextType.EMBEDDED_CODE_BLOCK, ctxJson.type);
        assertEquals("json", ctxJson.language);
    }

    @Test
    public void testHeadingAnchor() {
        String text = "# Section\n\nCheck [link](#\n";
        int cursor = text.indexOf("(#") + 2;
        MarkdownContextScanner.MarkdownContext ctx = MarkdownContextScanner.scan(text, cursor);
        assertNotNull(ctx);
        assertEquals(MarkdownContextScanner.ContextType.HEADING_ANCHOR, ctx.type);
        assertEquals("", ctx.query);
        assertEquals(0, ctx.replaceLength);

        String textWithPrefix = "# Section\n\nCheck [link](#sec\n";
        int cursor2 = textWithPrefix.indexOf("#sec") + 4;
        MarkdownContextScanner.MarkdownContext ctx2 = MarkdownContextScanner.scan(textWithPrefix, cursor2);
        assertNotNull(ctx2);
        assertEquals(MarkdownContextScanner.ContextType.HEADING_ANCHOR, ctx2.type);
        assertEquals("sec", ctx2.query);
        assertEquals(3, ctx2.replaceLength);
    }

    @Test
    public void testLinkFilePath() {
        String text = "See [guide](./docs/\n";
        int cursor = text.indexOf("./docs/") + 7;
        MarkdownContextScanner.MarkdownContext ctx = MarkdownContextScanner.scan(text, cursor);
        assertNotNull(ctx);
        assertEquals(MarkdownContextScanner.ContextType.LINK_FILE_PATH, ctx.type);
        assertEquals("", ctx.query);

        String textFile = "See ![logo](assets/logo.png";
        int cursorFile = textFile.length();
        MarkdownContextScanner.MarkdownContext ctxFile = MarkdownContextScanner.scan(textFile, cursorFile);
        assertNotNull(ctxFile);
        assertEquals(MarkdownContextScanner.ContextType.LINK_FILE_PATH, ctxFile.type);
        assertEquals("logo.png", ctxFile.query);
    }

    @Test
    public void testStructuralSnippetAtLineStart() {
        String text = "# Title\n\n";
        MarkdownContextScanner.MarkdownContext ctxEmpty = MarkdownContextScanner.scan(text, text.length());
        assertNotNull(ctxEmpty);
        assertEquals(MarkdownContextScanner.ContextType.STRUCTURAL_SNIPPET, ctxEmpty.type);

        String textIndent = "  ";
        MarkdownContextScanner.MarkdownContext ctxIndent = MarkdownContextScanner.scan(textIndent, 2);
        assertNotNull(ctxIndent);
        assertEquals(MarkdownContextScanner.ContextType.STRUCTURAL_SNIPPET, ctxIndent.type);

        String textHash = "#";
        MarkdownContextScanner.MarkdownContext ctxHash = MarkdownContextScanner.scan(textHash, 1);
        assertNotNull(ctxHash);
        assertEquals(MarkdownContextScanner.ContextType.STRUCTURAL_SNIPPET, ctxHash.type);
        assertEquals("#", ctxHash.query);

        String textDash = "- ";
        MarkdownContextScanner.MarkdownContext ctxDash = MarkdownContextScanner.scan(textDash, 2);
        assertNotNull(ctxDash);
        assertEquals(MarkdownContextScanner.ContextType.STRUCTURAL_SNIPPET, ctxDash.type);
    }

    @Test
    public void testInlineSnippetInProse() {
        String text = "Here is some text with bold";
        MarkdownContextScanner.MarkdownContext ctx = MarkdownContextScanner.scan(text, text.length());
        assertNotNull(ctx);
        assertEquals(MarkdownContextScanner.ContextType.INLINE_SNIPPET, ctx.type);
        assertEquals("bold", ctx.query);
    }

    @Test
    public void testCodeBlockClosingFenceSuppressesLanguage() {
        String text = "```js\nconst a = 1;\n```\n";
        int cursor = text.lastIndexOf("```") + 3;
        MarkdownContextScanner.MarkdownContext ctx = MarkdownContextScanner.scan(text, cursor);
        assertNotNull(ctx);
        assertEquals(MarkdownContextScanner.ContextType.NONE, ctx.type);
    }
}
