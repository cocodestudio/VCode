package com.cocode.vcode.ide.core.language;

import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.css.CssAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.html.HtmlAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsParser;
import com.cocode.vcode.ide.core.language.js.JsSemanticLinter;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.language.js.ScopeTree;
import com.cocode.vcode.ide.core.model.CompletionItem;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EditorEngineAuditFixesTest {

    // Lexer

    @Test
    public void testModernNumericLiterals() {
        String code = "const a = 0x1f; const b = 0b1010; const c = 0o755; const d = 1_000_000; const e = 100n;";
        TokenStream stream = JsLexer.tokenize(code);
        assertNotNull(stream);

        // Check that 0x1f, 0b1010, 0o755, 1_000_000, 100n are all TK_NUMBER
        int idxA = code.indexOf("0x1f");
        assertEquals(TokenStream.TK_NUMBER, stream.types[idxA]);

        int idxB = code.indexOf("0b1010");
        assertEquals(TokenStream.TK_NUMBER, stream.types[idxB]);

        int idxC = code.indexOf("0o755");
        assertEquals(TokenStream.TK_NUMBER, stream.types[idxC]);

        int idxD = code.indexOf("1_000_000");
        assertEquals(TokenStream.TK_NUMBER, stream.types[idxD]);

        int idxE = code.indexOf("100n");
        assertEquals(TokenStream.TK_NUMBER, stream.types[idxE]);
    }

    @Test
    public void testRegexAfterKeywords() {
        String code = "await /test/g; case /abc/: export default /xyz/;";
        TokenStream stream = JsLexer.tokenize(code);
        assertNotNull(stream);

        int idxAwait = code.indexOf("/test/g");
        assertEquals(TokenStream.TK_REGEX, stream.types[idxAwait]);

        int idxCase = code.indexOf("/abc/");
        assertEquals(TokenStream.TK_REGEX, stream.types[idxCase]);

        int idxExport = code.indexOf("/xyz/");
        assertEquals(TokenStream.TK_REGEX, stream.types[idxExport]);
    }

    @Test
    public void testNestedTemplateLiterals() {
        String code = "`outer ${`inner ${val}`}`";
        TokenStream stream = JsLexer.tokenize(code);
        assertNotNull(stream);

        int valIdx = code.indexOf("val");
        assertEquals(TokenStream.TK_IDENTIFIER, stream.types[valIdx]);
    }

    // Parser and scope resolution

    @Test
    public void testArrowFunctionExpressionBody() {
        String code = "const add = (x, y) => x + y; const doubleVal = x => x * 2;";
        TokenStream stream = JsLexer.tokenize(code);
        JsSyntaxTree tree = JsParser.parseFull(code, stream);
        assertNotNull(tree);

        ScopeTree scopeTree = ScopeTree.build(tree);
        assertNotNull(scopeTree);

        // Verify parameters x and y are registered
        boolean hasX = false;
        boolean hasY = false;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_PARAM) {
                if ("x".equals(tree.nodeName[i])) hasX = true;
                if ("y".equals(tree.nodeName[i])) hasY = true;
            }
        }
        assertTrue("Parameter x must be in AST", hasX);
        assertTrue("Parameter y must be in AST", hasY);
    }

    @Test
    public void testForOfAndCatchScopeRegistration() {
        String code = "for (const item of items) { console.log(item); } try {} catch (err) { console.error(err); }";
        TokenStream stream = JsLexer.tokenize(code);
        JsSyntaxTree tree = JsParser.parseFull(code, stream);
        assertNotNull(tree);

        ScopeTree scopeTree = ScopeTree.build(tree);
        assertNotNull(scopeTree);

        // Look up 'item' inside the for body
        int itemLogPos = code.indexOf("log(item)") + 4;
        int scopeItem = scopeTree.findScopeAt(itemLogPos, tree);
        int[] symItem = scopeTree.lookupSymbol("item", scopeItem, itemLogPos, tree);
        assertNotNull("Symbol 'item' should be resolved inside for-of body", symItem);

        // Look up 'err' inside the catch body
        int errLogPos = code.indexOf("error(err)") + 6;
        int scopeErr = scopeTree.findScopeAt(errLogPos, tree);
        int[] symErr = scopeTree.lookupSymbol("err", scopeErr, errLogPos, tree);
        assertNotNull("Symbol 'err' should be resolved inside catch body", symErr);
    }

    @Test
    public void testDeclareGlobalRegistration() {
        String code = "declare global { var __DEV__: boolean; }";
        TokenStream stream = JsLexer.tokenize(code);
        JsSyntaxTree tree = JsParser.parseFull(code, stream);
        assertNotNull(tree);

        ScopeTree scopeTree = ScopeTree.build(tree);
        assertNotNull(scopeTree);

        // Look up '__DEV__' in root scope (scope 0)
        int[] symDev = scopeTree.lookupSymbol("__DEV__", 0, code.length(), tree);
        assertNotNull("Symbol '__DEV__' declared in 'declare global' must be visible at root scope", symDev);
    }

    // Autocomplete

    @Test
    public void testJsOptionalChainingDotCompletion() {
        JsAutoCompleteEngine engine = new JsAutoCompleteEngine(null);
        String code = "const obj = { name: 'VCode', count: 10 };\nconst val = obj?.";
        int cursorPos = code.length();

        List<CompletionItem> items = engine.getSuggestions(code, cursorPos);
        assertNotNull(items);
        boolean foundName = false;
        for (CompletionItem item : items) {
            if ("name".equals(item.getLabel())) {
                foundName = true;
                break;
            }
        }
        assertTrue("Optional chaining obj?. should suggest property 'name'", foundName);
    }

    // Language-specific completions (CSS, HTML)

    @Test
    public void testCssVarFunctionCompletions() {
        CssAutoCompleteEngine engine = new CssAutoCompleteEngine(null);
        String css = ":root {\n  --primary-color: #007acc;\n  --font-size: 16px;\n}\n.btn {\n  color: var(";
        int cursorPos = css.length();

        List<CompletionItem> items = engine.getSuggestions(css, cursorPos);
        assertNotNull(items);
        boolean foundPrimary = false;
        for (CompletionItem item : items) {
            if ("--primary-color".equals(item.getLabel())) {
                foundPrimary = true;
                break;
            }
        }
        assertTrue("CSS var( context must suggest custom property '--primary-color'", foundPrimary);
    }

    @Test
    public void testHtmlCloseTagCompletions() {
        HtmlAutoCompleteEngine engine = new HtmlAutoCompleteEngine(null);
        String html = "<div class=\"wrapper\">\n  <span>Hello\n  </";
        int cursorPos = html.length();

        List<CompletionItem> items = engine.getSuggestions(html, cursorPos);
        assertNotNull(items);
        boolean foundSpan = false;
        for (CompletionItem item : items) {
            if ("</span>".equals(item.getLabel())) {
                foundSpan = true;
                break;
            }
        }
        assertTrue("HTML </ context must suggest closing tag '</span>'", foundSpan);
    }

    // Semantic diagnostics

    @Test
    public void testConstReassignmentLocationAndDestructuring() {
        String code = "for (const { x = 1 } of data) {\n  console.log(x);\n}\nconst c = 10;\nc = 20;";
        TokenStream stream = JsLexer.tokenize(code);
        JsSyntaxTree tree = JsParser.parseFull(code, stream);
        assertNotNull(tree);
        ScopeTree scopeTree = ScopeTree.build(tree);
        assertNotNull(scopeTree);

        List<Problem> problems = new ArrayList<>();
        JsSemanticLinter.analyze(new File("test.js"), code, stream, tree, scopeTree, null, problems);

        // Expect single error for 'c = 20', excluding destructured default
        int constErrors = 0;
        int reportedLine = -1;
        for (Problem p : problems) {
            if (p.getMessage().contains("Cannot reassign 'const' variable 'c'")) {
                constErrors++;
                reportedLine = p.getLine();
            }
            assertFalse("Destructured default { x = 1 } must not trigger const reassignment",
                    p.getMessage().contains("Cannot reassign 'const' variable 'x'"));
        }

        assertEquals("Exactly 1 const reassignment error expected", 1, constErrors);
        assertEquals("Const reassignment must be reported on assignment line 5", 5, reportedLine);
    }
}
