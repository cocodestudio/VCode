package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.language.js.JsStandardLibrary;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class JsSignatureParserTest {

    @Before
    public void setup() {
        com.cocode.vcode.ide.core.completion.staticdata.StaticAssetReader.setAppContext(RuntimeEnvironment.getApplication());
        JsStandardLibrary.reloadForTest();
    }

    @Test
    public void testGlobalFetch_activeParam() {
        String code = "fetch(\"https://api.com\", ";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(0, code.length());

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull("Signature help should not be null for fetch()", help);
        assertEquals(1, help.signatures.size());
        assertEquals(1, help.activeParameter);

        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertTrue(sig.label.startsWith("fetch("));
        assertEquals(2, sig.parameters.size());
        assertEquals("url", sig.parameters.get(0).label);
        assertEquals("options", sig.parameters.get(1).label);
        assertNotNull(sig.documentation);
    }

    @Test
    public void testConsoleLog_varargs() {
        String code = "console.log(\"hello\", 123, ";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(0, code.length());

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull("Signature help should not be null for console.log()", help);
        assertEquals(2, help.activeParameter);

        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertTrue(sig.label.startsWith("log("));
        assertTrue(sig.parameters.get(0).label.startsWith("..."));
    }

    @Test
    public void testMathMax() {
        String code = "Math.max(1, 2, 3, ";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(0, code.length());

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull(help);
        assertEquals(3, help.activeParameter);

        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertTrue(sig.label.startsWith("max("));
    }

    @Test
    public void testJsonStringify() {
        String code = "JSON.stringify(obj, replacer, ";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(0, code.length());

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull(help);
        assertEquals(2, help.activeParameter);

        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertTrue(sig.label.startsWith("stringify("));
        assertEquals(3, sig.parameters.size());
        assertEquals("value", sig.parameters.get(0).label);
        assertEquals("replacer", sig.parameters.get(1).label);
        assertEquals("space", sig.parameters.get(2).label);
    }

    @Test
    public void testConstructor_Promise() {
        String code = "new Promise((resolve, reject) => {";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(0, 15); // right after "new Promise("

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull("Signature help should support new Promise()", help);
        assertEquals(0, help.activeParameter);

        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertTrue(sig.label.contains("Promise("));
        assertEquals(1, sig.parameters.size());
        assertEquals("executor", sig.parameters.get(0).label);
    }

    @Test
    public void testConstructor_URL() {
        String code = "new URL(\"/path\", ";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(0, code.length());

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull(help);
        assertEquals(1, help.activeParameter);

        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertTrue(sig.label.contains("URL("));
        assertEquals(2, sig.parameters.size());
        assertEquals("url", sig.parameters.get(0).label);
        assertEquals("base", sig.parameters.get(1).label);
    }

    @Test
    public void testPrototypeMethod_ArrayPush() {
        String code = "items.push(1, 2, ";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(0, code.length());

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull(help);
        assertEquals(2, help.activeParameter);

        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertTrue(sig.label.contains("push("));
        assertTrue(sig.parameters.get(0).label.contains("items"));
    }

    @Test
    public void testPrototypeMethod_StringIndexOf() {
        String code = "str.indexOf(\"foo\", ";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(0, code.length());

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull(help);
        assertEquals(1, help.activeParameter);

        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertTrue(sig.label.contains("indexOf("));
        assertEquals(2, sig.parameters.size());
        assertEquals("searchString", sig.parameters.get(0).label);
        assertEquals("fromIndex", sig.parameters.get(1).label);
    }

    @Test
    public void testCommasInsideStringsAndNestedCalls() {
        // String literal contains commas and nested function call contains commas
        String code = "fetch(\"https://api.com/search?a=1,2,3\", Math.min(10, 20), ";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(0, code.length());

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull(help);
        // Depth-0 commas: one after the URL string, one after the Math.min(...) call -> index 2
        assertEquals(2, help.activeParameter);
    }

    @Test
    public void testOutsideParentheses_returnsNull() {
        String code = "fetch(\"https://api.com\")\nconsole.log(1);";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);

        // Before '('
        LspPosition beforeParen = new LspPosition(0, 5);
        assertNull("Cursor before '(' should return null", JsSignatureParser.parse(doc, beforeParen));

        // After ')'
        LspPosition afterParen = new LspPosition(0, 24);
        assertNull("Cursor after ')' should return null", JsSignatureParser.parse(doc, afterParen));
    }

    @Test
    public void testLocalFunctionOverridesBuiltin() {
        String code = "function fetch(customUrl) {\n  return customUrl;\n}\nfetch(";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(3, 6);

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull(help);
        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        // Should show local parameter `customUrl` instead of built-in fetch's `input`
        assertEquals(1, sig.parameters.size());
        assertEquals("customUrl", sig.parameters.get(0).label);
    }

    @Test
    public void testClassConstructor_explicitConstructor() {
        String code = "class Student {\n  constructor(name, rollNo) {\n    this.name = name;\n  }\n}\nnew Student(";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(5, 12);

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull("Signature help should not be null for new Student(", help);
        assertEquals(0, help.activeParameter);

        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertTrue(sig.label.startsWith("Student("));
        assertEquals(2, sig.parameters.size());
        assertEquals("name", sig.parameters.get(0).label);
        assertEquals("rollNo", sig.parameters.get(1).label);
        assertEquals("Class constructor", sig.documentation);
    }

    @Test
    public void testClassConstructor_secondArgument() {
        String code = "class Student {\n  constructor(name, rollNo) {}\n}\nnew Student(\"Alice\", ";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(3, 21);

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull(help);
        assertEquals(1, help.activeParameter);
        assertEquals("rollNo", help.signatures.get(0).parameters.get(1).label);
    }

    @Test
    public void testClassConstructor_defaultZeroArg() {
        String code = "class App {}\nnew App(";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(1, 8);

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull(help);
        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertEquals("App()", sig.label);
        assertEquals(0, sig.parameters.size());
    }

    @Test
    public void testSuperConstructorCall() {
        String code = "class Person {\n  constructor(name) {}\n}\nclass Student extends Person {\n  constructor(name, rollNo) {\n    super(\n  }\n}";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(5, 10);

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull("super() signature help should resolve Person constructor", help);
        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertTrue(sig.label.startsWith("super("));
        assertEquals(1, sig.parameters.size());
        assertEquals("name", sig.parameters.get(0).label);
    }

    @Test
    public void testSubclassInheritedConstructor() {
        String code = "class Animal {\n  constructor(species, age) {}\n}\nclass Dog extends Animal {}\nnew Dog(";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(4, 8);

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull(help);
        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertEquals(2, sig.parameters.size());
        assertEquals("species", sig.parameters.get(0).label);
        assertEquals("age", sig.parameters.get(1).label);
    }

    @Test
    public void testVariableAssignedArrowFunction() {
        String code = "const calculate = (x, y, z) => x + y + z;\ncalculate(10, ";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(1, 14);

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull(help);
        assertEquals(1, help.activeParameter);
        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertEquals(3, sig.parameters.size());
        assertEquals("x", sig.parameters.get(0).label);
        assertEquals("y", sig.parameters.get(1).label);
        assertEquals("z", sig.parameters.get(2).label);
    }

    @Test
    public void testVariableAssignedFunctionExpr() {
        String code = "const greet = function(greeting, recipient) {};\ngreet(";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(1, 6);

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull(help);
        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertEquals(2, sig.parameters.size());
        assertEquals("greeting", sig.parameters.get(0).label);
        assertEquals("recipient", sig.parameters.get(1).label);
    }

    @Test
    public void testGenericTypeScriptConstructorCall() {
        String code = "new Map<string, number>(";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(0, code.length());

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull("Generic constructor new Map<K, V>( should resolve Map built-in", help);
        assertTrue(help.signatures.get(0).label.startsWith("Map("));
    }

    @Test
    public void testChainedCall_addEventListener() {
        String code = "document.getElementById(\"btn\").addEventListener(\"click\", ";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(0, code.length());

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull("Chained addEventListener call should resolve signature help", help);
        assertEquals(1, help.activeParameter);
        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertTrue("Signature label should be method name only without chain", sig.label.startsWith("addEventListener("));
    }

    @Test
    public void testChainedCall_mapAfterFilter() {
        String code = "items.filter(x => x > 0).map(";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(0, code.length());

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull(help);
        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertTrue("Signature label should be method name only", sig.label.startsWith("map("));
    }

    @Test
    public void testSignatureHintPopup_extractMethodName() {
        assertEquals("getElementById", com.cocode.vcode.ide.views.SignatureHintPopup.extractMethodName("document.getElementById"));
        assertEquals("addEventListener", com.cocode.vcode.ide.views.SignatureHintPopup.extractMethodName("document.getElementById(\"btn\").addEventListener"));
        assertEquals("map", com.cocode.vcode.ide.views.SignatureHintPopup.extractMethodName("items.filter(x => x).map"));
        assertEquals("log", com.cocode.vcode.ide.views.SignatureHintPopup.extractMethodName("console.log"));
        assertEquals("doSomething", com.cocode.vcode.ide.views.SignatureHintPopup.extractMethodName("a.b.c.doSomething"));
        assertEquals("Student", com.cocode.vcode.ide.views.SignatureHintPopup.extractMethodName("new com.example.Student"));
        assertEquals("Map", com.cocode.vcode.ide.views.SignatureHintPopup.extractMethodName("new Map<string, number>"));
        assertEquals("bar", com.cocode.vcode.ide.views.SignatureHintPopup.extractMethodName("foo?.bar"));
        assertEquals("addEventListener", com.cocode.vcode.ide.views.SignatureHintPopup.extractMethodName(".addEventListener"));
        assertEquals("getName", com.cocode.vcode.ide.views.SignatureHintPopup.extractMethodName("student.getName"));
        assertEquals("fetch", com.cocode.vcode.ide.views.SignatureHintPopup.extractMethodName("fetch"));
        assertEquals("super", com.cocode.vcode.ide.views.SignatureHintPopup.extractMethodName("super"));
        assertEquals("", com.cocode.vcode.ide.views.SignatureHintPopup.extractMethodName(""));
        assertEquals("", com.cocode.vcode.ide.views.SignatureHintPopup.extractMethodName(null));
    }

    @Test
    public void testClassMethod_fetch_doesNotOverlapWithBuiltinFetch() {
        String code = "class ApiClient {\n" +
                "    fetch(customEndpoint, bodyData, headers) {\n" +
                "        return null;\n" +
                "    }\n" +
                "    request() {\n" +
                "        this.fetch(\n" +
                "    }\n" +
                "}\n";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(5, 19);

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull("Signature help should resolve this.fetch", help);
        assertEquals(1, help.signatures.size());
        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertTrue("Signature should contain customEndpoint parameter: " + sig.label, sig.label.contains("customEndpoint"));
        assertEquals("Method", sig.documentation);
        assertFalse("Doc must not be global fetch doc", sig.documentation != null && sig.documentation.contains("Fetches a resource from the network"));
    }

    @Test
    public void testSameFileFunction_labeledLocalFunctionNotCrossFile() {
        String code = "function localWorker(jobId, priority) {}\n" +
                "localWorker(\n";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(1, 12);

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull(help);
        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertTrue(sig.label.contains("jobId"));
        assertFalse("Same-file function must not be labeled Cross-file", "Cross-file function".equals(sig.documentation));
    }

    @Test
    public void testSameFileClass_labeledClassConstructorNotCrossFile() {
        String code = "class MyService {\n" +
                "    constructor(config, env) {}\n" +
                "}\n" +
                "new MyService(\n";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(3, 14);

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull(help);
        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertTrue(sig.label.contains("config"));
        assertFalse("Same-file class must not be labeled Cross-file", "Cross-file class".equals(sig.documentation));
        assertEquals("Class constructor", sig.documentation);
    }

    @Test
    public void testClassInstanceMethod_signatureHelp() {
        String code = "class Person {\n" +
                "    getName(prefix) {\n" +
                "        return prefix;\n" +
                "    }\n" +
                "}\n" +
                "const p = new Person();\n" +
                "p.getName(\n";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(6, 10);

        LspSignatureHelp help = JsSignatureParser.parse(doc, pos);
        assertNotNull("Signature help should resolve p.getName", help);
        assertEquals(1, help.signatures.size());
        LspSignatureHelp.LspSignatureInformation sig = help.signatures.get(0);
        assertTrue("Signature should contain 'prefix': " + sig.label, sig.label.contains("prefix"));
        assertEquals("Method", sig.documentation);
    }
}

