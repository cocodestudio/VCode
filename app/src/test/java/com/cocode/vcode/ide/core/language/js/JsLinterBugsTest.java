package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JsLinterBugsTest extends BaseJsAstTest {

    private static final String CONST_REASSIGN_SOURCE = "const x = 1;\nx = 2;";

    @Test
    public void lexerAndParserProduceAVarDeclNode() {
        setupEngine(CONST_REASSIGN_SOURCE);

        assertNotNull(tree);
        assertTrue("Parser must produce at least one node beyond the root", tree.nodeCount > 1);

        boolean sawVarDecl = false;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_VAR_DECL) {
                sawVarDecl = true;
                break;
            }
        }
        assertTrue("Parser must register a VAR_DECL for 'const x = 1;'", sawVarDecl);
    }

    @Test
    public void testPrefixConstReassignment() {
        String js = "const x = 1;\n++x;\n--x;";
        setupEngine(js);
        java.util.List<com.cocode.vcode.ide.core.model.Problem> problems = JsLinter.analyze(mockFile, js, mockIndex);
        int constErrors = 0;
        for (com.cocode.vcode.ide.core.model.Problem p : problems) {
            if (p.getMessage().contains("Cannot reassign 'const' variable 'x'")) {
                constErrors++;
            }
        }
        assertTrue("Prefix ++ and -- must trigger const reassignment error", constErrors >= 2);
    }

    @Test
    public void testFunctionArityWithDefaultParameters() {
        String js = "function foo(a, b = 10) {}\n" +
                    "foo(1);\n" +
                    "const arrow = (x, y = 20) => x + y;\n" +
                    "arrow(5);";
        setupEngine(js);
        java.util.List<com.cocode.vcode.ide.core.model.Problem> problems = JsLinter.analyze(mockFile, js, mockIndex);
        for (com.cocode.vcode.ide.core.model.Problem p : problems) {
            org.junit.Assert.assertFalse("Default parameters should not trigger arity error: " + p.getMessage(),
                    p.getMessage().contains("Too few arguments"));
        }

        String jsTooFew = "function foo(a, b = 10) {}\nfoo();";
        setupEngine(jsTooFew);
        java.util.List<com.cocode.vcode.ide.core.model.Problem> problemsTooFew = JsLinter.analyze(mockFile, jsTooFew, mockIndex);
        boolean hasTooFew = false;
        for (com.cocode.vcode.ide.core.model.Problem p : problemsTooFew) {
            if (p.getMessage().contains("Too few arguments")) {
                hasTooFew = true;
                break;
            }
        }
        assertTrue("Calling foo() with 0 args should flag too few arguments", hasTooFew);
    }

    @Test
    public void testStandaloneConsoleDoesNotWarn() {
        String js = "const console = { custom: true };\n" +
                    "const myLogger = console;\n";
        setupEngine(js);
        java.util.List<com.cocode.vcode.ide.core.model.Problem> problems = JsLinter.analyze(mockFile, js, mockIndex);
        for (com.cocode.vcode.ide.core.model.Problem p : problems) {
            org.junit.Assert.assertFalse("Standalone console identifier should not trigger console warning: " + p.getMessage(),
                    p.getMessage().contains("Unexpected console statement"));
        }
    }

    @Test
    public void testCompoundConstReassignment() {
        String js = "const a = 1;\n" +
                    "a **= 2;\n" +
                    "const b = 2;\n" +
                    "b &= 1;\n" +
                    "const c = 3;\n" +
                    "c |= 1;\n" +
                    "const d = 4;\n" +
                    "d ^= 1;\n" +
                    "const e = 5;\n" +
                    "e <<= 1;\n" +
                    "const f = 6;\n" +
                    "f >>= 1;\n" +
                    "const g = 7;\n" +
                    "g >>>= 1;\n" +
                    "const h = null;\n" +
                    "h ??= 10;\n" +
                    "const i = true;\n" +
                    "i &&= false;\n" +
                    "const j = false;\n" +
                    "j ||= true;\n";
        setupEngine(js);
        java.util.List<com.cocode.vcode.ide.core.model.Problem> problems = JsLinter.analyze(mockFile, js, mockIndex);
        int constErrors = 0;
        for (com.cocode.vcode.ide.core.model.Problem p : problems) {
            if (p.getMessage().contains("Cannot reassign 'const' variable")) {
                constErrors++;
            }
        }
        org.junit.Assert.assertEquals("All 10 compound assignments to const must report error", 10, constErrors);

        // Property assignments on a const object should not trigger const error
        String jsProp = "const obj = {};\nobj.prop = 1;\nobj.prop += 2;\nobj.prop **= 2;\n";
        setupEngine(jsProp);
        java.util.List<com.cocode.vcode.ide.core.model.Problem> propProblems = JsLinter.analyze(mockFile, jsProp, mockIndex);
        for (com.cocode.vcode.ide.core.model.Problem p : propProblems) {
            org.junit.Assert.assertFalse("Property mutation on const object is valid: " + p.getMessage(),
                    p.getMessage().contains("Cannot reassign 'const' variable"));
        }
    }

    @Test
    public void testArrowFunctionSingleParamAndExpressionBody() {
        String js = "const double = x => x * 2;\n" +
                    "const greet = name => 'Hello ' + name;\n";
        setupEngine(js);
        java.util.List<com.cocode.vcode.ide.core.model.Problem> problems = JsLinter.analyze(mockFile, js, mockIndex);
        for (com.cocode.vcode.ide.core.model.Problem p : problems) {
            org.junit.Assert.assertFalse("Single-param arrow parameter should not be reported undefined: " + p.getMessage(),
                    p.getMessage().contains("'x' is not defined") || p.getMessage().contains("'name' is not defined"));
        }
    }

    @Test
    public void testForOfAndForInLoopVariablesInScope() {
        String js = "const items = [1, 2, 3];\n" +
                    "for (const item of items) {\n" +
                    "    console.log(item);\n" +
                    "}\n" +
                    "const dict = { a: 1 };\n" +
                    "for (let key in dict) {\n" +
                    "    console.log(key);\n" +
                    "}\n";
        setupEngine(js);
        java.util.List<com.cocode.vcode.ide.core.model.Problem> problems = JsLinter.analyze(mockFile, js, mockIndex);
        for (com.cocode.vcode.ide.core.model.Problem p : problems) {
            org.junit.Assert.assertFalse("for-of / for-in variables must be in scope: " + p.getMessage(),
                    p.getMessage().contains("'item' is not defined") || p.getMessage().contains("'key' is not defined"));
        }
    }

    @Test
    public void testCatchClauseVariableInScope() {
        String js = "try {\n" +
                    "    throw new Error('boom');\n" +
                    "} catch (err) {\n" +
                    "    console.log(err);\n" +
                    "}\n";
        setupEngine(js);
        java.util.List<com.cocode.vcode.ide.core.model.Problem> problems = JsLinter.analyze(mockFile, js, mockIndex);
        for (com.cocode.vcode.ide.core.model.Problem p : problems) {
            org.junit.Assert.assertFalse("catch clause parameter must be in scope: " + p.getMessage(),
                    p.getMessage().contains("'err' is not defined"));
        }
    }

    @Test
    public void testTypeScriptDeclareGlobalScope() {
        String ts = "declare global {\n" +
                    "    var __DEV__: boolean;\n" +
                    "}\n" +
                    "const dev = __DEV__;\n";
        setupEngine(ts);
        java.util.List<com.cocode.vcode.ide.core.model.Problem> problems = JsLinter.analyze(mockFile, ts, mockIndex);
        for (com.cocode.vcode.ide.core.model.Problem p : problems) {
            org.junit.Assert.assertFalse("declare global variables must be available in global scope: " + p.getMessage(),
                    p.getMessage().contains("'__DEV__' is not defined"));
        }
    }

    @Test
    public void testForEachAndArrayMethodsArity() {
        String jsValid = "const items = [1, 2, 3];\n" +
                         "items.forEach(item => console.log(item));\n" +
                         "items.forEach(function(item) { console.log(item); });\n" +
                         "items.forEach((item, idx) => console.log(item), this);\n" +
                         "items.map(x => x * 2);\n" +
                         "items.filter(x => x > 1);\n" +
                         "items.reduce((acc, x) => acc + x);\n" +
                         "items.reduce((acc, x) => acc + x, 0);\n" +
                         "items.slice(1);\n" +
                         "items.slice();\n" +
                         "items.join();\n" +
                         "items.sort();\n";
        setupEngine(jsValid);
        java.util.List<com.cocode.vcode.ide.core.model.Problem> validProblems = JsLinter.analyze(mockFile, jsValid, mockIndex);
        for (com.cocode.vcode.ide.core.model.Problem p : validProblems) {
            org.junit.Assert.assertFalse("Valid forEach/array invocation must not trigger arity warnings: " + p.getMessage(),
                    p.getMessage().contains("Too few arguments") || p.getMessage().contains("Too many arguments"));
        }

        String jsTooFew = "const items = [1, 2, 3];\nitems.forEach();";
        setupEngine(jsTooFew);
        java.util.List<com.cocode.vcode.ide.core.model.Problem> tooFewProblems = JsLinter.analyze(mockFile, jsTooFew, mockIndex);
        boolean hasTooFew = false;
        for (com.cocode.vcode.ide.core.model.Problem p : tooFewProblems) {
            if (p.getMessage().contains("Too few arguments") && p.getMessage().contains("forEach")) {
                hasTooFew = true;
                break;
            }
        }
        assertTrue("forEach() with 0 arguments must trigger 'Too few arguments' warning", hasTooFew);

        String jsTooMany = "const items = [1, 2, 3];\nitems.forEach(a => a, this, 123);";
        setupEngine(jsTooMany);
        java.util.List<com.cocode.vcode.ide.core.model.Problem> tooManyProblems = JsLinter.analyze(mockFile, jsTooMany, mockIndex);
        boolean hasTooMany = false;
        for (com.cocode.vcode.ide.core.model.Problem p : tooManyProblems) {
            if (p.getMessage().contains("Too many arguments") && p.getMessage().contains("forEach")) {
                hasTooMany = true;
                break;
            }
        }
        assertTrue("forEach() with 3 arguments must trigger 'Too many arguments' warning", hasTooMany);
    }
}

