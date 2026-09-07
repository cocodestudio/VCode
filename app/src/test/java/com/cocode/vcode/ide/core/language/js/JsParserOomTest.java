package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JsParserOomTest {

    @Test(timeout = 3000)
    public void testTopLevelClosingBraceDoesNotInfiniteLoopOrOom() {
        // This was the exact snippet that previously triggered OutOfMemoryError in JsParser.parseTopLevel
        String source = "export { obj as user }\n}";
        TokenStream tokens = JsLexer.tokenize(source);
        JsSyntaxTree tree = JsParser.parseFull(source, tokens);

        assertNotNull("Tree should not be null", tree);
        assertTrue("Node count must remain small and finite", tree.nodeCount < 50);
    }

    @Test(timeout = 3000)
    public void testMultipleStrayBracesTerminateSafely() {
        String source = "}}}}\nconst x = 1;\n}}";
        TokenStream tokens = JsLexer.tokenize(source);
        JsSyntaxTree tree = JsParser.parseFull(source, tokens);

        assertNotNull("Tree should not be null", tree);
        assertTrue("Node count must remain small and finite", tree.nodeCount < 50);
    }

    @Test(timeout = 3000)
    public void testUnclosedObjectWithTrailingBrace() {
        String source = "const x = {\nexport { a as b }\n}";
        TokenStream tokens = JsLexer.tokenize(source);
        JsSyntaxTree tree = JsParser.parseFull(source, tokens);

        assertNotNull("Tree should not be null", tree);
        assertTrue("Node count must remain small and finite", tree.nodeCount < 50);
    }

    @Test(timeout = 3000)
    public void testTreeHardCapacityCapPreventsUnboundedDoubling() {
        JsSyntaxTree tree = new JsSyntaxTree(16);
        for (int i = 0; i < JsSyntaxTree.MAX_NODES + 100; i++) {
            tree.addNode(JsSyntaxTree.N_STATEMENT, 0, 1, 0, null);
        }

        assertEquals("Node count must be capped at MAX_NODES", JsSyntaxTree.MAX_NODES, tree.nodeCount);
        assertTrue("Array length must not exceed MAX_NODES", tree.nodeType.length <= JsSyntaxTree.MAX_NODES);
    }

    @Test
    public void testResetClearsShapeTableAndDownsizesOversizedArrays() {
        JsSyntaxTree tree = new JsSyntaxTree(16);
        tree.shapeTable.put(1, new String[] { "prop1", "prop2" });
        assertEquals(1, tree.shapeTable.size());

        // Reset should clear shapeTable
        tree.reset(16);
        assertEquals("shapeTable must be cleared on reset to prevent memory leaks", 0, tree.shapeTable.size());

        // Expand tree
        tree.reset(50000);
        assertTrue(tree.nodeType.length >= 50000);

        // Next reset should downsize to prevent holding oversized arrays indefinitely
        tree.reset(100);
        assertTrue("Oversized tree must be downsized on reset", tree.nodeType.length <= 32768);
    }
}
