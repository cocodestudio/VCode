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
}
