package com.cocode.vcode.ide.core.language.css;

import org.junit.Test;
import static org.junit.Assert.*;

public class CssDataContainersTest {

    @Test
    public void testCssTokenStream() {
        byte[] types = new byte[10];
        int[] starts = new int[10];

        types[0] = CssTokenStream.TK_SELECTOR; starts[0] = 0;
        types[1] = CssTokenStream.TK_SELECTOR; starts[1] = 0;
        types[2] = CssTokenStream.TK_PUNCT;    starts[2] = 2;
        
        CssTokenStream stream = new CssTokenStream(types, starts);
        assertTrue(stream.isSelector(0));
        assertTrue(stream.isSelector(1));
        assertTrue(stream.isPunct(2));
        assertFalse(stream.isProperty(0));
    }

    @Test
    public void testCssSyntaxTree() {
        CssSyntaxTree tree = new CssSyntaxTree(10);
        
        int root = 0;
        int rule = tree.addNode(CssSyntaxTree.N_RULE, 0, 10, root, null, null);
        int selector = tree.addNode(CssSyntaxTree.N_SELECTOR, 1, 5, rule, ".class", null);
        int decl = tree.addNode(CssSyntaxTree.N_DECLARATION, 6, 9, rule, null, null);
        
        assertEquals(4, tree.nodeCount);
        
        // Check hierarchy
        assertEquals(root, tree.nodeParent[rule]);
        assertEquals(rule, tree.nodeParent[selector]);
        assertEquals(rule, tree.nodeParent[decl]);
        
        assertEquals(selector, tree.nodeChild[rule]);
        assertEquals(decl, tree.nodeSibling[selector]);
        assertEquals(0, tree.nodeSibling[decl]); // no sibling
        
        // Binary search build test
        tree.buildNodesByOffset();
        assertEquals(1, tree.nodesByOffset[1]); // rule (start 0)
        assertEquals(2, tree.nodesByOffset[2]); // selector (start 1)
        assertEquals(3, tree.nodesByOffset[3]); // decl (start 6)
    }
}
