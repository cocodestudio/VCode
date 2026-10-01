package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.lsp.HoverInfo;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.servers.JsLspServer;
import com.cocode.vcode.ide.core.model.CompletionItem;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class JsFunctionArgsAutoCompleteTest {

    private JsAutoCompleteEngine engine;
    private JsLspServer lspServer;

    @Before
    public void setUp() {
        com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().clear();
        engine = new JsAutoCompleteEngine(null);
        lspServer = new JsLspServer();
    }

    @Test
    public void testFunctionCallArgsDoNotSuggestObjectLiteralKeys() {
        String code = "const obj = {\n"
                + "  name: \"Adeel\",\n"
                + "  age: 21,\n"
                + "  langs: {\n"
                + "    lang1: \"Java\",\n"
                + "    lang2: \"C\",\n"
                + "  },\n"
                + "  fullName: \"Adeel Ahmad\",\n"
                + "};\n\n"
                + "export { obj as data };\n\n"
                + "function calculateTotal(price, taxRate = 0.07) {\n"
                + "  return price + (price * taxRate);\n"
                + "}\n\n"
                + "calculateTotal(1, );\n";

        int cursorPos = code.indexOf("calculateTotal(1, ") + "calculateTotal(1, ".length();

        List<CompletionItem> items = engine.getSuggestions(code, cursorPos);
        assertNotNull(items);

        // Verify that object keys 'name', 'age', 'fullName' are NOT returned as "Object key"
        for (CompletionItem ci : items) {
            assertNotEquals("Object key should not be suggested inside function arguments", "Object key", ci.getDetail());
        }
    }

    @Test
    public void testObjectLiteralStillSuggestsObjectKeys() {
        String code = "const obj = {\n"
                + "  name: \"Adeel\",\n"
                + "  age: 21,\n"
                + "};\n\n"
                + "const another = {  };\n";

        int cursorPos = code.indexOf("another = { ") + "another = { ".length();

        List<CompletionItem> items = engine.getSuggestions(code, cursorPos);
        assertNotNull(items);

        boolean hasObjectKey = false;
        for (CompletionItem ci : items) {
            if ("Object key".equals(ci.getDetail())) {
                hasObjectKey = true;
                break;
            }
        }
        assertTrue("Object keys should be suggested inside object literal braces", hasObjectKey);
    }

    @Test
    public void testHoverAccuratelyDistinguishesConstLetVar() {
        String code = "const count = 10;\n"
                + "let user = 'alice';\n"
                + "var legacy = true;\n";

        LspDocument doc = new LspDocument("file:///test.js", code, "javascript", 1);

        // Hover over 'count'
        HoverInfo constHover = lspServer.hover(doc, new LspPosition(0, 6));
        assertNotNull(constHover);
        assertTrue("Hover for const should show 'const count'", constHover.getMarkdownContent().contains("const count"));
        assertFalse("Hover for const must not say 'let count'", constHover.getMarkdownContent().contains("let count"));

        // Hover over 'user'
        HoverInfo letHover = lspServer.hover(doc, new LspPosition(1, 4));
        assertNotNull(letHover);
        assertTrue("Hover for let should show 'let user'", letHover.getMarkdownContent().contains("let user"));

        // Hover over 'legacy'
        HoverInfo varHover = lspServer.hover(doc, new LspPosition(2, 4));
        assertNotNull(varHover);
        assertTrue("Hover for var should show 'var legacy'", varHover.getMarkdownContent().contains("var legacy"));
    }

    @Test
    public void testHoverShowsJsDocForExportedFunction() {
        String code = "/*\n"
                + " * Calculates the total cost of an order including tax.\n"
                + " *\n"
                + " * @param {number} price - The base price of the item.\n"
                + " * @param {number} [taxRate=0.07] - Optional tax rate (defaults to 7%).\n"
                + " * @returns {number} The total calculated cost.\n"
                + " * @throws {Error} If price is negative.\n"
                + " */\n"
                + "export function calculateTotal(price, taxRate = 0.07) {\n"
                + "  if (price < 0) {\n"
                + "    throw new Error('Price cannot be negative.');\n"
                + "  }\n"
                + "  return price + (price * taxRate);\n"
                + "}\n";

        LspDocument doc = new LspDocument("file:///test.js", code, "javascript", 1);

        int calcOffset = code.indexOf("calculateTotal");
        HoverInfo hover = lspServer.hover(doc, doc.toPosition(calcOffset + 2));

        assertNotNull(hover);
        String md = hover.getMarkdownContent();
        assertTrue("Hover must contain function description", md.contains("Calculates the total cost of an order including tax."));
        assertTrue("Hover must contain @param price", md.contains("@param `price: number` — The base price of the item."));
        assertTrue("Hover must contain @param taxRate", md.contains("@param `taxRate: number` — Optional tax rate (defaults to 7%)."));
        assertTrue("Hover must contain @returns", md.contains("@returns `number` — The total calculated cost."));
        assertTrue("Hover must contain @throws", md.contains("@throws `Error` — If price is negative."));
    }

    @Test
    public void testCrossFileHoverOnImportStatement() {
        String mathCode = "/**\n"
                + " * Calculates the total cost of an order including tax.\n"
                + " *\n"
                + " * @param {number} price - The base price of the item.\n"
                + " * @param {number} [taxRate=0.07] - Optional tax rate (defaults to 7%).\n"
                + " * @returns {number} The total calculated cost.\n"
                + " * @throws {Error} If price is negative.\n"
                + " */\n"
                + "export function calculateTotal(price, taxRate = 0.07) {\n"
                + "  return price + (price * taxRate);\n"
                + "}\n";

        com.cocode.vcode.ide.core.lsp.ProjectIndex pIndex = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance();
        pIndex.updateDocumentSnapshot("file:///workspace/math.js", mathCode, "javascript");

        String mainCode = "import { calculateTotal } from './math';\n\ncalculateTotal(100, 0.05);\n";
        LspDocument mainDoc = new LspDocument("file:///workspace/main.js", mainCode, "javascript", 1);

        // Hover over 'calculateTotal' inside the import statement
        int importOffset = mainCode.indexOf("calculateTotal");
        HoverInfo hover = lspServer.hover(mainDoc, mainDoc.toPosition(importOffset + 2));

        assertNotNull("Hover on import statement must resolve cross-file", hover);
        String md = hover.getMarkdownContent();
        assertTrue("Must contain function signature", md.contains("function calculateTotal(price, taxRate = 0.07)"));
        assertTrue("Must contain description", md.contains("Calculates the total cost of an order including tax."));
        assertTrue("Must contain @param price", md.contains("@param `price: number` — The base price of the item."));
        assertTrue("Must contain @param taxRate", md.contains("@param `taxRate: number` — Optional tax rate (defaults to 7%)."));
        assertTrue("Must contain @returns", md.contains("@returns `number` — The total calculated cost."));
        assertTrue("Must contain @throws", md.contains("@throws `Error` — If price is negative."));
    }

    @Test
    public void testCrossFileHoverOnFunctionCall() {
        String mathCode = "/**\n"
                + " * Calculates the total cost of an order including tax.\n"
                + " *\n"
                + " * @param {number} price - The base price of the item.\n"
                + " * @param {number} [taxRate=0.07] - Optional tax rate (defaults to 7%).\n"
                + " * @returns {number} The total calculated cost.\n"
                + " */\n"
                + "export function calculateTotal(price, taxRate = 0.07) {\n"
                + "  return price + (price * taxRate);\n"
                + "}\n";

        com.cocode.vcode.ide.core.lsp.ProjectIndex pIndex = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance();
        pIndex.updateDocumentSnapshot("file:///workspace/math.js", mathCode, "javascript");

        String mainCode = "import { calculateTotal } from './math';\n\ncalculateTotal(100, 0.05);\n";
        LspDocument mainDoc = new LspDocument("file:///workspace/main.js", mainCode, "javascript", 1);

        // Hover over 'calculateTotal' on the function call (line 2)
        int callOffset = mainCode.lastIndexOf("calculateTotal");
        HoverInfo hover = lspServer.hover(mainDoc, mainDoc.toPosition(callOffset + 2));

        assertNotNull("Hover on function call must resolve cross-file", hover);
        String md = hover.getMarkdownContent();
        assertTrue("Must contain function signature", md.contains("function calculateTotal(price, taxRate = 0.07)"));
        assertTrue("Must contain description", md.contains("Calculates the total cost of an order including tax."));
        assertTrue("Must contain @param price", md.contains("@param `price: number`"));
        assertTrue("Must contain @returns", md.contains("@returns `number`"));
    }

    @Test
    public void testCrossFileHoverOnAliasedImport() {
        String mathCode = "/**\n"
                + " * Calculates the total cost.\n"
                + " * @param {number} price\n"
                + " * @returns {number}\n"
                + " */\n"
                + "export function calculateTotal(price) {\n"
                + "  return price * 1.1;\n"
                + "}\n";

        com.cocode.vcode.ide.core.lsp.ProjectIndex pIndex = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance();
        pIndex.updateDocumentSnapshot("file:///workspace/math.js", mathCode, "javascript");

        String mainCode = "import { calculateTotal as total } from './math';\n\ntotal(100);\n";
        LspDocument mainDoc = new LspDocument("file:///workspace/main.js", mainCode, "javascript", 1);

        int callOffset = mainCode.lastIndexOf("total");
        HoverInfo hover = lspServer.hover(mainDoc, mainDoc.toPosition(callOffset + 1));

        assertNotNull("Hover on aliased import must resolve cross-file", hover);
        String md = hover.getMarkdownContent();
        assertTrue("Must contain function signature with alias name", md.contains("function total(price)"));
        assertTrue("Must contain description from original function", md.contains("Calculates the total cost."));
        assertTrue("Must contain @param price", md.contains("@param `price: number`"));
    }

    @Test
    public void testCrossFileHoverOnDefaultExport() {
        String currCode = "/**\n"
                + " * Formats a number into currency representation.\n"
                + " * @param {number} amount Base amount to format.\n"
                + " * @returns {string} Formatted string.\n"
                + " */\n"
                + "export default function(amount) {\n"
                + "  return '$' + amount;\n"
                + "}\n";

        com.cocode.vcode.ide.core.lsp.ProjectIndex pIndex = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance();
        pIndex.updateDocumentSnapshot("file:///workspace/currency.js", currCode, "javascript");

        String mainCode = "import formatCurrency from './currency';\n\nformatCurrency(50);\n";
        LspDocument mainDoc = new LspDocument("file:///workspace/main.js", mainCode, "javascript", 1);

        int callOffset = mainCode.lastIndexOf("formatCurrency");
        HoverInfo hover = lspServer.hover(mainDoc, mainDoc.toPosition(callOffset + 2));

        assertNotNull("Hover on default export must resolve cross-file", hover);
        String md = hover.getMarkdownContent();
        assertTrue("Must contain function signature", md.contains("function formatCurrency(amount)"));
        assertTrue("Must contain description", md.contains("Formats a number into currency representation."));
        assertTrue("Must contain @param amount", md.contains("@param `amount: number`"));
        assertTrue("Must contain @returns", md.contains("@returns `string`"));
    }

    @Test
    public void testCrossFileHoverOnNamespaceImport() {
        String mathCode = "/**\n"
                + " * Multiplies two numbers.\n"
                + " * @param {number} a Factor 1.\n"
                + " * @param {number} b Factor 2.\n"
                + " * @returns {number} Product.\n"
                + " */\n"
                + "export function multiply(a, b) {\n"
                + "  return a * b;\n"
                + "}\n";

        com.cocode.vcode.ide.core.lsp.ProjectIndex pIndex = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance();
        pIndex.updateDocumentSnapshot("file:///workspace/math.js", mathCode, "javascript");

        String mainCode = "import * as math from './math';\n\nmath.multiply(10, 20);\n";
        LspDocument mainDoc = new LspDocument("file:///workspace/main.js", mainCode, "javascript", 1);

        int callOffset = mainCode.lastIndexOf("multiply");
        HoverInfo hover = lspServer.hover(mainDoc, mainDoc.toPosition(callOffset + 2));

        assertNotNull("Hover on namespace member must resolve cross-file", hover);
        String md = hover.getMarkdownContent();
        assertTrue("Must contain function signature", md.contains("function multiply(a, b)"));
        assertTrue("Must contain description", md.contains("Multiplies two numbers."));
        assertTrue("Must contain @param a", md.contains("@param `a: number`"));
        assertTrue("Must contain @returns", md.contains("@returns `number`"));
    }
}
