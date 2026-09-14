package com.cocode.vcode.ide.core.language.md;

import com.cocode.vcode.ide.core.language.js.ParseResult;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MdEmbeddingTest {

    @Test
    public void testEmbeddedCodeBlocks() {
        String md = "# Hello\n" +
                "Some text\n" +
                "```javascript\n" +
                "console.log('hi');\n" +
                "```\n" +
                "More text\n" +
                "```css\n" +
                "body { margin: 0; }\n" +
                "```";
                
        MdLineStream lines = MdLexer.lex(md);
        MdSyntaxTree tree = MdParser.parseBlocks(lines, md);
        
        System.out.println("Node count: " + tree.nodeCount);
        for (int i = 1; i < tree.nodeCount; i++) {
            System.out.println("Node " + i + ": type=" + tree.nodeType[i] + " name=" + tree.nodeName[i]);
            if (tree.nodeType[i] == MdSyntaxTree.N_CODE_BLOCK) {
                System.out.println("Found code block, reference: " + tree.nodeReference[i]);
            }
        }
    }
}
