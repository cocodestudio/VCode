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
}
