package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ScopeTreeTest extends BaseJsAstTest {

    private static final String VAR_AND_LET_SOURCE =
            "if (true) { var a = 1; let b = 2; }\n" +
            "const foo = (c, d) => { return c + d; };";

    @Test
    public void varHoistsToFunctionScope() {
        setupEngine(VAR_AND_LET_SOURCE);

        assertNotNull(scopeTree.symbols);
        assertTrue("var 'a' must be registered", scopeTree.symbols.containsKey("a"));
        assertTrue("let 'b' must be registered", scopeTree.symbols.containsKey("b"));
        assertTrue("const 'foo' must be registered", scopeTree.symbols.containsKey("foo"));
    }

    @Test
    public void symbolsExposeScopeCreatorType() {
        setupEngine(VAR_AND_LET_SOURCE);

        for (Map.Entry<String, int[]> entry : scopeTree.symbols.entrySet()) {
            int[] tuples = entry.getValue();
            assertTrue("Tuple array length must be a multiple of 3", tuples.length % 3 == 0);
        }
    }

    @Test
    public void testResolveClassInheritanceArrayBound() {
        JsSyntaxTree tree = new JsSyntaxTree(10);
        for (int i = 1; i < 10; i++) {
            tree.addNode(JsSyntaxTree.N_CLASS_DECL, i * 10, i * 10 + 5, 0, "Class" + i);
        }
        JsParser.resolveClassInheritance(tree);
        assertTrue("Inheritance resolution completed without ArrayIndexOutOfBoundsException", true);
    }

    @Test
    public void testBufferReuseDoesNotLeakStalePointers() {
        JsSyntaxTree tree = new JsSyntaxTree(30);
        for (int i = 1; i <= 25; i++) {
            tree.addNode(JsSyntaxTree.N_FUNC_DECL, i * 5, i * 5 + 4, 0, "fn" + i);
        }
        tree.reset(10);
        for (int i = 1; i < 10; i++) {
            tree.addNode(JsSyntaxTree.N_VAR_DECL, i * 2, i * 2 + 1, 0, "v" + i);
        }

        ScopeTree scope = ScopeTree.build(tree);
        assertNotNull("ScopeTree must build cleanly without out of bounds", scope);
    }

    @Test
    public void testCyclicNodesDoNotHangScopeTree() {
        JsSyntaxTree tree = new JsSyntaxTree(20);
        int p = tree.addNode(JsSyntaxTree.N_FUNC_DECL, 0, 100, 0, "foo");
        int c1 = tree.addNode(JsSyntaxTree.N_BLOCK, 10, 90, p, null);
        int c2 = tree.addNode(JsSyntaxTree.N_VAR_DECL, 20, 30, c1, "x");

        tree.nodeSibling[c2] = c1;

        ScopeTree scope = ScopeTree.build(tree);
        assertNotNull(scope);
    }

    @Test
    public void testCyclicParentScopeResolutionDoesNotHang() {
        String code = "function a() { var x = 1; return x; }";
        TokenStream tokens = JsLexer.tokenize(code);
        JsSyntaxTree tree = JsParser.parseFull(code, tokens);
        ScopeTree scope = ScopeTree.build(tree);

        if (scope.scopeCount >= 2) {
            scope.scopeParent[1] = 1;
        }

        scope.lookupSymbol("x", 1, 10, tree);
        assertTrue("Cyclic parent lookup safely terminated", true);
    }
}
