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

    @Test
    public void testCodeBlockParsing() {
        String md = "# Hello\n" +
                "Some text\n" +
                "```javascript\n" +
                "console.log('hi');\n" +
                "```\n";
        MdLineStream lines = MdLexer.lex(md);
        MdSyntaxTree tree = MdParser.parseBlocks(lines, md);

        assertNotNull(tree);
        boolean foundCodeBlock = false;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == MdSyntaxTree.N_CODE_BLOCK) {
                foundCodeBlock = true;
                break;
            }
        }
        assertTrue("Tree should contain N_CODE_BLOCK", foundCodeBlock);
    }

    @Test
    public void testToSlugUnicodeAndFormatting() {
        assertEquals("über-uns", MdParser.toSlug("Über Uns"));
        assertEquals("hello-world", MdParser.toSlug("  ### Hello World!  "));
        assertEquals("bold-and-italic", MdParser.toSlug("**Bold** and *Italic*"));
        assertEquals("use-code-here", MdParser.toSlug("Use `code` here"));
        assertEquals("visit-google", MdParser.toSlug("Visit [Google](https://google.com)"));
        assertEquals("español-y-olé", MdParser.toSlug("Español y Olé"));
    }

    @Test
    public void testExtractHeadingSlugsDisambiguation() {
        String md = "# Overview\n\n" +
                "## Setup\n\n" +
                "## Overview\n\n" +
                "## Overview\n\n" +
                "## Über Uns\n";
        MdLineStream lines = MdLexer.lex(md);
        MdSyntaxTree tree = MdParser.parseBlocks(lines, md);

        java.util.List<MdParser.HeadingSlug> slugs = MdParser.extractHeadingSlugs(tree, md);
        assertEquals(5, slugs.size());

        assertEquals("overview", slugs.get(0).slug);
        assertEquals(1, slugs.get(0).level);

        assertEquals("setup", slugs.get(1).slug);
        assertEquals(2, slugs.get(1).level);

        assertEquals("overview-1", slugs.get(2).slug);
        assertEquals(2, slugs.get(2).level);

        assertEquals("overview-2", slugs.get(3).slug);
        assertEquals(2, slugs.get(3).level);

        assertEquals("über-uns", slugs.get(4).slug);
    }
}
