package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.language.css.CssSyntaxTree;
import com.cocode.vcode.ide.core.language.html.HtmlSyntaxTree;
import com.cocode.vcode.ide.core.language.md.MdSyntaxTree;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SyntaxTreeSortStressTest {

    @Test
    public void testJsSyntaxTreeSortIdenticalOffsets() {
        JsSyntaxTree tree = new JsSyntaxTree(10050);
        for (int i = 1; i <= 10000; i++) {
            tree.addNode(JsSyntaxTree.N_STATEMENT, 100, 200, 0, "node" + i);
        }
        assertEquals(10001, tree.nodeCount);
        // Previously, standard recursive quicksort with identical pivots caused StackOverflowError
        tree.buildNodesByOffset();

        for (int i = 1; i < tree.nodeCount - 1; i++) {
            assertTrue("Nodes must be sorted by offset",
                    tree.nodeStart[tree.nodesByOffset[i]] <= tree.nodeStart[tree.nodesByOffset[i + 1]]);
        }
    }

    @Test
    public void testJsSyntaxTreeSortReverseAndSortedOffsets() {
        JsSyntaxTree tree = new JsSyntaxTree(5050);
        for (int i = 5000; i >= 1; i--) {
            tree.addNode(JsSyntaxTree.N_STATEMENT, i * 2, i * 2 + 1, 0, "node" + i);
        }
        tree.buildNodesByOffset();

        for (int i = 1; i < tree.nodeCount - 1; i++) {
            assertTrue("Reverse input must be correctly sorted",
                    tree.nodeStart[tree.nodesByOffset[i]] <= tree.nodeStart[tree.nodesByOffset[i + 1]]);
        }

        // Now test on already sorted input
        tree.buildNodesByOffset();
        for (int i = 1; i < tree.nodeCount - 1; i++) {
            assertTrue("Already sorted input must remain sorted",
                    tree.nodeStart[tree.nodesByOffset[i]] <= tree.nodeStart[tree.nodesByOffset[i + 1]]);
        }
    }

    @Test
    public void testHtmlSyntaxTreeSortIdenticalOffsets() {
        HtmlSyntaxTree tree = new HtmlSyntaxTree(5050);
        for (int i = 1; i <= 5000; i++) {
            tree.addNode(HtmlSyntaxTree.N_ELEMENT, 50, 100, 0, "div", null);
        }
        tree.buildNodesByOffset();

        for (int i = 1; i < tree.nodeCount - 1; i++) {
            assertTrue(tree.nodeStart[tree.nodesByOffset[i]] <= tree.nodeStart[tree.nodesByOffset[i + 1]]);
        }
    }

    @Test
    public void testCssSyntaxTreeSortIdenticalOffsets() {
        CssSyntaxTree tree = new CssSyntaxTree(5050);
        for (int i = 1; i <= 5000; i++) {
            tree.addNode(CssSyntaxTree.N_RULE, 25, 75, 0, ".cls", null);
        }
        tree.buildNodesByOffset();

        for (int i = 1; i < tree.nodeCount - 1; i++) {
            assertTrue(tree.nodeStart[tree.nodesByOffset[i]] <= tree.nodeStart[tree.nodesByOffset[i + 1]]);
        }
    }

    @Test
    public void testMdSyntaxTreeSortIdenticalOffsets() {
        MdSyntaxTree tree = new MdSyntaxTree(5050);
        for (int i = 1; i <= 5000; i++) {
            tree.addNode(MdSyntaxTree.N_PARAGRAPH, 10, 50, 0, "p");
        }
        tree.buildNodesByOffset();

        for (int i = 1; i < tree.nodeCount - 1; i++) {
            assertTrue(tree.nodeStart[tree.nodesByOffset[i]] <= tree.nodeStart[tree.nodesByOffset[i + 1]]);
        }
    }
}
