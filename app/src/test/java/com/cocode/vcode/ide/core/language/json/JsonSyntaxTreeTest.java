package com.cocode.vcode.ide.core.language.json;

import org.junit.Test;
import static org.junit.Assert.*;

public class JsonSyntaxTreeTest {

    @Test
    public void testJsonSyntaxTree() {
        JsonSyntaxTree tree = new JsonSyntaxTree(5);
        
        int obj = tree.addNode(JsonSyntaxTree.N_OBJECT, 0, 20, 0, null);
        int key = tree.addNode(JsonSyntaxTree.N_KEY, 2, 7, obj, "\"key\"");
        int val = tree.addNode(JsonSyntaxTree.N_VALUE_NUMBER, 9, 12, obj, "123");
        int arr = tree.addNode(JsonSyntaxTree.N_ARRAY, 14, 18, obj, null);
        
        assertEquals(5, tree.nodeCount);
        assertEquals(JsonSyntaxTree.N_OBJECT, tree.nodeType[obj]);
        assertEquals(obj, tree.nodeParent[key]);
        assertEquals(obj, tree.nodeParent[val]);
        assertEquals(obj, tree.nodeParent[arr]);
        
        // Children / siblings
        assertEquals(key, tree.nodeChild[obj]);
        assertEquals(val, tree.nodeSibling[key]);
        assertEquals(arr, tree.nodeSibling[val]);
        assertEquals(-1, tree.nodeSibling[arr]);
        
        // Lookup by offset
        tree.buildNodesByOffset();
        assertEquals(obj, tree.getNodeAtOffset(0));
        assertEquals(key, tree.getNodeAtOffset(4));
        assertEquals(val, tree.getNodeAtOffset(10));
        assertEquals(arr, tree.getNodeAtOffset(16));
        
        // Exceed capacity test
        for (int i = 0; i < 20; i++) {
            tree.addNode(JsonSyntaxTree.N_VALUE_BOOL, i, i+1, arr, "true");
        }
        
        assertTrue(tree.nodeCount > 5);
        assertTrue(tree.nodeType.length >= 25);
        assertEquals(arr, tree.nodeParent[tree.nodeCount - 1]);
    }

    @Test
    public void testDeeplyNestedTreeNoStackOverflow() {
        int depth = 600;
        JsonSyntaxTree tree = new JsonSyntaxTree(depth + 10);
        int currentParent = 0;
        int innermost = 0;

        for (int i = 0; i < depth; i++) {
            currentParent = tree.addNode(JsonSyntaxTree.N_OBJECT, i, depth * 2 - i, currentParent, null);
            innermost = currentParent;
        }

        tree.buildNodesByOffset();
        int found = tree.getNodeAtOffset(depth - 1);
        assertEquals(innermost, found);
    }

    @Test
    public void testQuicksortWithIdenticalOffsets() {
        JsonSyntaxTree tree = new JsonSyntaxTree(100);
        int parent = tree.addNode(JsonSyntaxTree.N_OBJECT, 0, 100, 0, null);
        for (int i = 0; i < 50; i++) {
            tree.addNode(JsonSyntaxTree.N_VALUE_NUMBER, 10, 20, parent, "1");
        }
        tree.buildNodesByOffset();
        assertTrue(tree.nodeCount > 50);
        int found = tree.getNodeAtOffset(15);
        assertTrue(found > 0);
    }
}
