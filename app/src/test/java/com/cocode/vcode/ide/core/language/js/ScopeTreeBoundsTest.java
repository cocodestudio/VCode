package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ScopeTreeBoundsTest {

    @Test
    public void testResolveClassInheritanceArrayBound() {
        // Create tree where capacity exactly equals 10 and nodeCount equals 10
        JsSyntaxTree tree = new JsSyntaxTree(10);
        // Add 9 nodes (id 1..9, nodeCount becomes 10)
        for (int i = 1; i < 10; i++) {
            tree.addNode(JsSyntaxTree.N_CLASS_DECL, i * 10, i * 10 + 5, 0, "Class" + i);
        }
        // nodeType.length is 10, nodeCount is 10.
        // Previously JsParser.resolveClassInheritance looped `for (int i = 1; i <= tree.nodeCount; i++)`,
        // accessing tree.nodeType[10] which threw ArrayIndexOutOfBoundsException: length=10; index=10.
        JsParser.resolveClassInheritance(tree);
        // Verify no exception thrown
        assertTrue("Inheritance resolution completed without ArrayIndexOutOfBoundsException", true);
    }

    @Test
    public void testBufferReuseDoesNotLeakStalePointers() {
        // Step 1: Simulate parsing a file with 25 nodes
        JsSyntaxTree tree = new JsSyntaxTree(30);
        for (int i = 1; i <= 25; i++) {
            tree.addNode(JsSyntaxTree.N_FUNC_DECL, i * 5, i * 5 + 4, 0, "fn" + i);
        }
        // Step 2: Reset for a small file with initial capacity of 10
        tree.reset(10);
        // Step 3: Add 9 nodes (nodeCount becomes 10, array length is 10)
        for (int i = 1; i < 10; i++) {
            tree.addNode(JsSyntaxTree.N_VAR_DECL, i * 2, i * 2 + 1, 0, "v" + i);
        }

        // Step 4: Build scope tree. Must not access stale pointers or index 10.
        ScopeTree scope = ScopeTree.build(tree);
        assertNotNull("ScopeTree must build cleanly without out of bounds", scope);
    }

    @Test
    public void testCyclicNodesDoNotHangScopeTree() {
        JsSyntaxTree tree = new JsSyntaxTree(20);
        int p = tree.addNode(JsSyntaxTree.N_FUNC_DECL, 0, 100, 0, "foo");
        int c1 = tree.addNode(JsSyntaxTree.N_BLOCK, 10, 90, p, null);
        int c2 = tree.addNode(JsSyntaxTree.N_VAR_DECL, 20, 30, c1, "x");

        // Manually introduce circular sibling reference
        tree.nodeSibling[c2] = c1;

        // Build scope tree - iterative DFS and loop counters must prevent hanging or crashing
        ScopeTree scope = ScopeTree.build(tree);
        assertNotNull(scope);
    }

    @Test
    public void testCyclicParentScopeResolutionDoesNotHang() {
        String code = "function a() { var x = 1; return x; }";
        TokenStream tokens = JsLexer.tokenize(code);
        JsSyntaxTree tree = JsParser.parseFull(code, tokens);
        ScopeTree scope = ScopeTree.build(tree);

        // Manually introduce cyclic parent scope
        if (scope.scopeCount >= 2) {
            scope.scopeParent[1] = 1;
        }

        // lookupSymbol has loop guard ++loop <= scopeCount, preventing infinite while loop
        scope.lookupSymbol("x", 1, 10, tree);
        assertTrue("Cyclic parent lookup safely terminated", true);
    }
}
