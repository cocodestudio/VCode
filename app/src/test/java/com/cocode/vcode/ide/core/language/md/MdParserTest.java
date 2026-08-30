package com.cocode.vcode.ide.core.language.md;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MdParserTest {

    @Test
    public void testParserCreatesProperNodes() {
        String md = "# Header\n" +
                "Paragraph text\n" +
                "\n" +
                "- Item 1\n" +
                "- Item 2\n" +
                "***\n" +
                "> Quote\n" +
                "More text";

        MdLineStream lines = MdLexer.lex(md);
        MdSyntaxTree tree = MdParser.parseBlocks(lines, md);

        assertNotNull(tree);
        assertTrue(tree.nodeCount > 0);
        
        int headers = 0;
        int paragraphs = 0;
        int lists = 0;
        int listItems = 0;
        int thematicBreaks = 0;
        int blockQuotes = 0;
        
        for (int i = 1; i < tree.nodeCount; i++) {
            int type = tree.nodeType[i];
            if (type == MdSyntaxTree.N_HEADER) headers++;
            else if (type == MdSyntaxTree.N_PARAGRAPH) paragraphs++;
            else if (type == MdSyntaxTree.N_LIST) lists++;
            else if (type == MdSyntaxTree.N_LIST_ITEM) listItems++;
            else if (type == MdSyntaxTree.N_THEMATIC_BREAK) thematicBreaks++;
            else if (type == MdSyntaxTree.N_BLOCKQUOTE) blockQuotes++;
        }
        
        assertEquals(1, headers);
        assertEquals(5, paragraphs); 
        assertEquals(1, lists);
        assertEquals(2, listItems);
        assertEquals(1, thematicBreaks);
        assertEquals(1, blockQuotes);
    }
}
