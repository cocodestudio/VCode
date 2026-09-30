package com.cocode.vcode.ide.core.lsp;

import com.cocode.vcode.ide.core.lsp.servers.CssLspServer;
import com.cocode.vcode.ide.core.lsp.servers.HtmlLspServer;
import com.cocode.vcode.ide.core.lsp.servers.JsLspServer;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HoverTest {

    @Test
    public void testHtmlTagHover() {
        HtmlLspServer server = new HtmlLspServer();
        String text = "<div class=\"main\">Hello</div>";
        LspDocument doc = new LspDocument("/index.html", text, "html", 1);
        LspPosition pos = new LspPosition(0, 2); // on "div"

        HoverInfo info = server.hover(doc, pos);
        assertNotNull(info);
        assertTrue(info.getMarkdownContent().contains("### `<div>`"));
    }

    @Test
    public void testCssPropertyHover() {
        CssLspServer server = new CssLspServer();
        String text = "body { color: red; }";
        LspDocument doc = new LspDocument("/style.css", text, "css", 1);
        LspPosition pos = new LspPosition(0, 9); // on "color"

        HoverInfo info = server.hover(doc, pos);
        assertNotNull(info);
        assertTrue(info.getMarkdownContent().contains("color"));
    }

    @Test
    public void testJsKeywordHover() {
        JsLspServer server = new JsLspServer();
        String text = "function calculate() { return 42; }";
        LspDocument doc = new LspDocument("/script.js", text, "javascript", 1);
        LspPosition pos = new LspPosition(0, 2); // on "function"

        HoverInfo info = server.hover(doc, pos);
        assertNotNull(info);
        assertTrue(info.getMarkdownContent().contains("Keyword"));
    }

    @Test
    public void testHoverOutsideSymbol() {
        JsLspServer server = new JsLspServer();
        String text = "     ";
        LspDocument doc = new LspDocument("/script.js", text, "javascript", 1);
        LspPosition pos = new LspPosition(0, 2);

        HoverInfo info = server.hover(doc, pos);
        assertNull(info);
    }

    @Test
    public void testUserWrittenFunctionHoverWithJsDoc() {
        JsLspServer server = new JsLspServer();
        String text = "/**\n"
                + " * Multiplies two numbers.\n"
                + " * @param x Factor 1\n"
                + " * @param y Factor 2\n"
                + " * @returns Product\n"
                + " */\n"
                + "function multiply(x, y) {\n"
                + "    return x * y;\n"
                + "}\n"
                + "multiply(2, 3);";
        LspDocument doc = new LspDocument("/script.js", text, "javascript", 1);
        // Position on 'multiply' call: line 9, char 2
        LspPosition pos = new LspPosition(9, 2);

        HoverInfo info = server.hover(doc, pos);
        assertNotNull(info);
        assertTrue(info.getMarkdownContent().contains("function multiply(x, y)"));
        assertTrue(info.getMarkdownContent().contains("Multiplies two numbers."));
        assertTrue(info.getMarkdownContent().contains("• `x` — Factor 1"));
        assertTrue(info.getMarkdownContent().contains("• `y` — Factor 2"));
        assertTrue(info.getMarkdownContent().contains("**Returns:**\nProduct"));
    }

    @Test
    public void testBuiltinMethodHover() {
        JsLspServer server = new JsLspServer();
        String text = "document.getElementById('test');";
        LspDocument doc = new LspDocument("/script.js", text, "javascript", 1);
        // Position on 'getElementById': line 0, char 12
        LspPosition pos = new LspPosition(0, 12);

        HoverInfo info = server.hover(doc, pos);
        assertNotNull(info);
        assertTrue(info.getMarkdownContent().contains("getElementById"));
    }
}
