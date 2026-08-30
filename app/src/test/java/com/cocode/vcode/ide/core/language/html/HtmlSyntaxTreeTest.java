package com.cocode.vcode.ide.core.language.html;

import org.junit.Test;
import static org.junit.Assert.*;

public class HtmlSyntaxTreeTest {

    @Test
    public void testManualNodeInsertAndTraverse() {
        HtmlSyntaxTree tree = new HtmlSyntaxTree(10);
        
        // Root element <div class="container">
        int rootId = tree.addNode(HtmlSyntaxTree.N_ELEMENT, 0, 50, 0, "div", null);
        assertEquals(1, rootId);
        
        // Attribute class="container"
        int attrId = tree.addNode(HtmlSyntaxTree.N_ATTRIBUTE, 5, 22, rootId, "class", "container");
        assertEquals(2, attrId);
        
        // Text node
        int textId = tree.addNode(HtmlSyntaxTree.N_TEXT, 23, 30, rootId, null, "content");
        assertEquals(3, textId);
        
        // Another element <span />
        int spanId = tree.addNode(HtmlSyntaxTree.N_ELEMENT, 30, 38, rootId, "span", null);
        assertEquals(4, spanId);

        // Verify counts
        assertEquals(5, tree.nodeCount); // 0 + 4 added nodes

        // Verify relationships
        assertEquals(0, tree.nodeParent[rootId]); // 0
        assertEquals(rootId, tree.nodeParent[attrId]);
        assertEquals(rootId, tree.nodeParent[textId]);
        assertEquals(rootId, tree.nodeParent[spanId]);
        
        assertEquals(attrId, tree.nodeChild[rootId]);
        assertEquals(textId, tree.nodeSibling[attrId]);
        assertEquals(spanId, tree.nodeSibling[textId]);
        assertEquals(0, tree.nodeSibling[spanId]); // last child
        
        assertEquals(spanId, tree.nodeLastChild[rootId]);
        
        // Verify buildNodesByOffset
        tree.buildNodesByOffset();
        
        // Order should be 1 (root), 2 (attr), 3 (text), 4 (span)
        assertEquals(1, tree.nodesByOffset[1]);
        assertEquals(2, tree.nodesByOffset[2]);
        assertEquals(3, tree.nodesByOffset[3]);
        assertEquals(4, tree.nodesByOffset[4]);
    }
    
    @Test
    public void testReset() {
        HtmlSyntaxTree tree = new HtmlSyntaxTree(10);
        int rootId = tree.addNode(HtmlSyntaxTree.N_ELEMENT, 0, 10, 0, "div", null);
        int childId = tree.addNode(HtmlSyntaxTree.N_TEXT, 0, 10, rootId, null, "hello");
        
        assertEquals(3, tree.nodeCount);
        assertEquals("hello", tree.nodeValue[childId]);
        
        tree.reset();
        
        assertEquals(1, tree.nodeCount);
        assertNull(tree.nodeValue[childId]);
        assertNull(tree.nodeName[rootId]);
    }
}
