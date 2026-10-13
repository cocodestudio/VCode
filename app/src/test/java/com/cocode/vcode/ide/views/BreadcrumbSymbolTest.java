package com.cocode.vcode.ide.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.base.DomFile;
import com.cocode.vcode.ide.core.language.base.DomNode;
import com.cocode.vcode.ide.core.language.base.SyntaxTreeDomAdapter;
import com.cocode.vcode.ide.core.language.html.HtmlParser;
import com.cocode.vcode.ide.core.language.html.HtmlSyntaxTree;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsParser;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.model.FileType;

import org.junit.Test;

public class BreadcrumbSymbolTest {

    @Test
    public void testBreadcrumbSymbolBasics() {
        BreadcrumbView.BreadcrumbSymbol s1 = new BreadcrumbView.BreadcrumbSymbol("class App", 4, 10, 50);
        BreadcrumbView.BreadcrumbSymbol s2 = new BreadcrumbView.BreadcrumbSymbol("class App", 4, 10, 50);
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
        assertEquals("class App", s1.getName());
        assertEquals(4, s1.getKind());
        assertEquals(10, s1.getStartOffset());
        assertEquals(50, s1.getEndOffset());
    }

    @Test
    public void testHtmlTagSingleClassFormatting() {
        String html = "<div id=\"main\" class=\"container fluid row\">\n  <p class=\"lead text-muted\">Hello</p>\n</div>";
        HtmlSyntaxTree tree = HtmlParser.parse(html).htmlTree;
        assertNotNull(tree);
        DomFile dom = SyntaxTreeDomAdapter.fromHtml(tree);
        assertNotNull(dom);

        // Find <div id="main" class="container fluid row">
        DomNode divNode = dom.findDeepestNodeAt(html.indexOf("Hello"));
        // Climb to div
        DomNode current = divNode;
        DomNode foundDiv = null;
        while (current != null) {
            if ("div".equals(current.getName())) {
                foundDiv = current;
                break;
            }
            current = current.getParent();
        }
        assertNotNull(foundDiv);

        // Verify id and first class extraction
        String id = null;
        String firstClass = null;
        for (DomNode child : foundDiv.getChildren()) {
            if (child.getKind() == HtmlSyntaxTree.N_ATTRIBUTE) {
                String name = child.getName();
                String val = child.getValue();
                if (val != null) {
                    val = val.trim();
                    if (val.length() >= 2 && (val.startsWith("\"") && val.endsWith("\""))) {
                        val = val.substring(1, val.length() - 1).trim();
                    }
                }
                if ("id".equalsIgnoreCase(name)) {
                    id = val;
                } else if ("class".equalsIgnoreCase(name) && firstClass == null && val != null) {
                    String[] parts = val.split("\\s+");
                    for (String p : parts) {
                        if (!p.isEmpty()) {
                            firstClass = p;
                            break;
                        }
                    }
                }
            }
        }
        assertEquals("main", id);
        assertEquals("container", firstClass);
    }

    @Test
    public void testJsLoopHeadersCaptured() {
        String code = "for (let i = 0; i < 10; i++) {\n" +
                "    while (i > 0) {\n" +
                "        i--;\n" +
                "    }\n" +
                "}";
        TokenStream stream = JsLexer.tokenize(code);
        JsSyntaxTree tree = JsParser.parseFull(code, stream);
        assertNotNull(tree);

        // Locate N_FOR_STMT and N_WHILE_STMT
        int forNode = 0;
        int whileNode = 0;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_FOR_STMT) {
                forNode = i;
            } else if (tree.nodeType[i] == JsSyntaxTree.N_WHILE_STMT) {
                whileNode = i;
            }
        }

        assertTrue("forNode should be found", forNode > 0);
        assertTrue("whileNode should be found", whileNode > 0);
        assertEquals("for (let i = 0; i < 10; i++)", tree.nodeName[forNode]);
        assertEquals("while (i > 0)", tree.nodeName[whileNode]);
    }

    @Test
    public void testJsConditionAndErrorHandlingCaptured() {
        String code = "try {\n" +
                "    if (x > 5) {\n" +
                "        switch (type) {\n" +
                "            case 'ACTION':\n" +
                "                break;\n" +
                "        }\n" +
                "    }\n" +
                "} catch (err) {\n" +
                "} finally {\n" +
                "}";
        TokenStream stream = JsLexer.tokenize(code);
        JsSyntaxTree tree = JsParser.parseFull(code, stream);
        assertNotNull(tree);

        int tryNode = 0, ifNode = 0, switchNode = 0, caseNode = 0, catchNode = 0, finallyNode = 0;
        for (int i = 1; i < tree.nodeCount; i++) {
            int t = tree.nodeType[i];
            if (t == JsSyntaxTree.N_TRY_STMT) tryNode = i;
            else if (t == JsSyntaxTree.N_IF_STMT) ifNode = i;
            else if (t == JsSyntaxTree.N_SWITCH_STMT) switchNode = i;
            else if (t == JsSyntaxTree.N_CASE_CLAUSE) caseNode = i;
            else if (t == JsSyntaxTree.N_CATCH_CLAUSE) catchNode = i;
            else if (t == JsSyntaxTree.N_FINALLY_CLAUSE) finallyNode = i;
        }

        assertTrue(tryNode > 0);
        assertTrue(ifNode > 0);
        assertTrue(switchNode > 0);
        assertTrue(caseNode > 0);
        assertTrue(catchNode > 0);
        assertTrue(finallyNode > 0);

        assertEquals("try", tree.nodeName[tryNode]);
        assertEquals("if (x > 5)", tree.nodeName[ifNode]);
        assertEquals("switch (type)", tree.nodeName[switchNode]);
        assertEquals("case 'ACTION'", tree.nodeName[caseNode]);
        assertEquals("catch (err)", tree.nodeName[catchNode]);
        assertEquals("finally", tree.nodeName[finallyNode]);
    }

    @Test
    public void testJsVarDeclFlagsAndMetadata() {
        String code = "const maxCount = 50;\nlet counter = 0;\nvar name = 'test';";
        TokenStream stream = JsLexer.tokenize(code);
        JsSyntaxTree tree = JsParser.parseFull(code, stream);
        assertNotNull(tree);

        DomFile dom = SyntaxTreeDomAdapter.fromJs(tree, FileType.JAVASCRIPT);
        assertNotNull(dom);

        int constNode = 0, letNode = 0, varNode = 0;
        for (int i = 1; i < tree.nodeCount; i++) {
            if ("maxCount".equals(tree.nodeName[i])) constNode = i;
            else if ("counter".equals(tree.nodeName[i])) letNode = i;
            else if ("name".equals(tree.nodeName[i])) varNode = i;
        }

        assertTrue(constNode > 0);
        assertTrue(letNode > 0);
        assertTrue(varNode > 0);

        assertEquals(JsSyntaxTree.FLAG_CONST, tree.nodeExtra[constNode]);
        assertEquals(JsSyntaxTree.FLAG_LET, tree.nodeExtra[letNode]);
        assertEquals(JsSyntaxTree.FLAG_VAR, tree.nodeExtra[varNode]);

        DomNode constDom = dom.findDeepestNodeAt(code.indexOf("maxCount"));
        assertEquals(JsSyntaxTree.FLAG_CONST, constDom.getExtra());
    }
}
