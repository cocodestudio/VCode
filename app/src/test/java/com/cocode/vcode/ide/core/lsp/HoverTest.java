package com.cocode.vcode.ide.core.lsp;

import com.cocode.vcode.ide.core.lsp.servers.CssLspServer;
import com.cocode.vcode.ide.core.lsp.servers.HtmlLspServer;
import com.cocode.vcode.ide.core.lsp.servers.JsLspServer;
import com.cocode.vcode.ide.core.lsp.servers.JsonLspServer;
import com.cocode.vcode.ide.core.lsp.servers.MarkdownLspServer;
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
        assertTrue(info.getMarkdownContent().contains("generic container"));
        assertTrue(info.getMarkdownContent().contains("developer.mozilla.org"));
    }

    @Test
    public void testHtmlAttributeHover() {
        HtmlLspServer server = new HtmlLspServer();
        String text = "<input type=\"checkbox\" id=\"agree\" class=\"form-check\">";
        LspDocument doc = new LspDocument("/index.html", text, "html", 1);

        // Hover on "type" attribute name (col 7)
        LspPosition posType = new LspPosition(0, 8);
        HoverInfo infoType = server.hover(doc, posType);
        assertNotNull(infoType);
        assertTrue(infoType.getMarkdownContent().contains("### `type`"));
        assertTrue(infoType.getMarkdownContent().contains("form control"));

        // Hover on "id" global attribute (col 24)
        LspPosition posId = new LspPosition(0, 24);
        HoverInfo infoId = server.hover(doc, posId);
        assertNotNull(infoId);
        assertTrue(infoId.getMarkdownContent().contains("### `id`"));
        assertTrue(infoId.getMarkdownContent().contains("unique"));
        assertTrue(infoId.getMarkdownContent().contains("identifier"));
    }

    @Test
    public void testHtmlAttributeValueHover() {
        HtmlLspServer server = new HtmlLspServer();
        String text = "<input type=\"checkbox\">";
        LspDocument doc = new LspDocument("/index.html", text, "html", 1);

        // Hover inside "checkbox" (col 15)
        LspPosition posVal = new LspPosition(0, 15);
        HoverInfo infoVal = server.hover(doc, posVal);
        assertNotNull(infoVal);
        assertTrue(infoVal.getMarkdownContent().contains("checkbox"));
        assertTrue(infoVal.getMarkdownContent().contains("check box"));
    }

    @Test
    public void testHtmlAriaAndEventHover() {
        HtmlLspServer server = new HtmlLspServer();
        String text = "<button onclick=\"handleClick()\" aria-label=\"Submit form\">Click</button>";
        LspDocument doc = new LspDocument("/index.html", text, "html", 1);

        // Hover on onclick
        LspPosition posEvt = new LspPosition(0, 10);
        HoverInfo infoEvt = server.hover(doc, posEvt);
        assertNotNull(infoEvt);
        assertTrue(infoEvt.getMarkdownContent().contains("### `onclick`"));

        // Hover on aria-label
        LspPosition posAria = new LspPosition(0, 35);
        HoverInfo infoAria = server.hover(doc, posAria);
        assertNotNull(infoAria);
        assertTrue(infoAria.getMarkdownContent().contains("### `aria-label`"));
        assertTrue(infoAria.getMarkdownContent().contains("labels the current element"));
    }

    @Test
    public void testCssPropertyHover() {
        CssLspServer server = new CssLspServer();
        String text = "body { display: flex; color: red; }";
        LspDocument doc = new LspDocument("/style.css", text, "css", 1);

        // Hover on display
        LspPosition posDisp = new LspPosition(0, 9);
        HoverInfo infoDisp = server.hover(doc, posDisp);
        assertNotNull(infoDisp);
        assertTrue(infoDisp.getMarkdownContent().contains("### `display`"));
        assertTrue(infoDisp.getMarkdownContent().contains("block or inline"));
        assertTrue(infoDisp.getMarkdownContent().contains("developer.mozilla.org"));

        // Hover on color
        LspPosition posColor = new LspPosition(0, 24);
        HoverInfo infoColor = server.hover(doc, posColor);
        assertNotNull(infoColor);
        assertTrue(infoColor.getMarkdownContent().contains("### `color`"));
    }

    @Test
    public void testCssPseudoClassHover() {
        CssLspServer server = new CssLspServer();
        String text = "button:hover { opacity: 0.8; }";
        LspDocument doc = new LspDocument("/style.css", text, "css", 1);
        LspPosition pos = new LspPosition(0, 9); // on :hover

        HoverInfo info = server.hover(doc, pos);
        assertNotNull(info);
        assertTrue(info.getMarkdownContent().contains("### `:hover`"));
        assertTrue(info.getMarkdownContent().contains("pointing device"));
        assertTrue(info.getMarkdownContent().contains("developer.mozilla.org"));
    }

    @Test
    public void testCssPseudoElementHover() {
        CssLspServer server = new CssLspServer();
        String text = "p::before { content: '>'; }";
        LspDocument doc = new LspDocument("/style.css", text, "css", 1);
        LspPosition pos = new LspPosition(0, 5); // on ::before

        HoverInfo info = server.hover(doc, pos);
        assertNotNull(info);
        assertTrue(info.getMarkdownContent().contains("### `::before`"));
        assertTrue(info.getMarkdownContent().contains("cosmetic content"));
    }

    @Test
    public void testCssAtRuleHover() {
        CssLspServer server = new CssLspServer();
        String text = "@media (max-width: 768px) { body { font-size: 14px; } }";
        LspDocument doc = new LspDocument("/style.css", text, "css", 1);
        LspPosition pos = new LspPosition(0, 3); // on @media

        HoverInfo info = server.hover(doc, pos);
        assertNotNull(info);
        assertTrue(info.getMarkdownContent().contains("### `@media`"));
        assertTrue(info.getMarkdownContent().contains("media queries"));
    }

    @Test
    public void testCssFunctionHover() {
        CssLspServer server = new CssLspServer();
        String text = "div { width: calc(100% - 20px); }";
        LspDocument doc = new LspDocument("/style.css", text, "css", 1);
        LspPosition pos = new LspPosition(0, 15); // on calc

        HoverInfo info = server.hover(doc, pos);
        assertNotNull(info);
        assertTrue(info.getMarkdownContent().contains("### `calc()`"));
        assertTrue(info.getMarkdownContent().contains("calculations"));
    }

    @Test
    public void testJsKeywordHover() {
        JsLspServer server = new JsLspServer();
        String text = "async function calculate() { return 42; }";
        LspDocument doc = new LspDocument("/script.js", text, "javascript", 1);

        // Hover on async
        LspPosition posAsync = new LspPosition(0, 2);
        HoverInfo infoAsync = server.hover(doc, posAsync);
        assertNotNull(infoAsync);
        assertTrue(infoAsync.getMarkdownContent().contains("async"));
        assertTrue(infoAsync.getMarkdownContent().contains("Promise"));

        // Hover on function
        LspPosition posFn = new LspPosition(0, 8);
        HoverInfo infoFn = server.hover(doc, posFn);
        assertNotNull(infoFn);
        assertTrue(infoFn.getMarkdownContent().contains("function"));
    }

    @Test
    public void testJsBuiltinMethodHover() {
        JsLspServer server = new JsLspServer();
        String text = "document.getElementById('test');\nconsole.log('hello');";
        LspDocument doc = new LspDocument("/script.js", text, "javascript", 1);

        // Hover on getElementById (line 0, col 12)
        LspPosition posId = new LspPosition(0, 12);
        HoverInfo infoId = server.hover(doc, posId);
        assertNotNull(infoId);
        assertTrue(infoId.getMarkdownContent().contains("getElementById"));
        assertTrue(infoId.getMarkdownContent().contains("Parameters"));
        assertTrue(infoId.getMarkdownContent().contains("developer.mozilla.org"));

        // Hover on console.log (line 1, col 10)
        LspPosition posLog = new LspPosition(1, 10);
        HoverInfo infoLog = server.hover(doc, posLog);
        assertNotNull(infoLog);
        assertTrue(infoLog.getMarkdownContent().contains("Console.log"));
        assertTrue(infoLog.getMarkdownContent().contains("Outputs a message to the console"));
    }

    @Test
    public void testJsGlobalApiHover() {
        JsLspServer server = new JsLspServer();
        String text = "fetch('/api/data').then(res => res.json());";
        LspDocument doc = new LspDocument("/script.js", text, "javascript", 1);

        // Hover on fetch (line 0, col 2)
        LspPosition posFetch = new LspPosition(0, 2);
        HoverInfo infoFetch = server.hover(doc, posFetch);
        assertNotNull(infoFetch);
        assertTrue(infoFetch.getMarkdownContent().contains("fetch"));
        assertTrue(infoFetch.getMarkdownContent().contains("Promise<Response>"));
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
    public void testJsonSchemaHover() {
        JsonLspServer server = new JsonLspServer();
        String text = "{\n  \"name\": \"vcode\",\n  \"version\": \"1.0.0\"\n}";
        LspDocument doc = new LspDocument("/package.json", text, "json", 1);

        // Hover on "name" (line 1, col 5)
        LspPosition pos = new LspPosition(1, 5);
        HoverInfo info = server.hover(doc, pos);
        assertNotNull(info);
        assertTrue(info.getMarkdownContent().contains("### `name`"));
        assertTrue(info.getMarkdownContent().contains("name of the package"));
    }

    @Test
    public void testMarkdownHover() {
        MarkdownLspServer server = new MarkdownLspServer();
        String text = "# Main Title\n\nVisit [Google](https://google.com) today.";
        LspDocument doc = new LspDocument("/README.md", text, "markdown", 1);

        // Hover on heading (line 0, col 5)
        LspPosition posHeading = new LspPosition(0, 5);
        HoverInfo infoHeading = server.hover(doc, posHeading);
        assertNotNull(infoHeading);
        assertTrue(infoHeading.getMarkdownContent().contains("Heading 1"));
        assertTrue(infoHeading.getMarkdownContent().contains("Main Title"));

        // Hover on link (line 2, col 12)
        LspPosition posLink = new LspPosition(2, 12);
        HoverInfo infoLink = server.hover(doc, posLink);
        assertNotNull(infoLink);
        assertTrue(infoLink.getMarkdownContent().contains("Markdown Link"));
        assertTrue(infoLink.getMarkdownContent().contains("https://google.com"));
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
