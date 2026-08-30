package com.cocode.vcode.ide.core.language.md;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class MdLexerTest {

    @Test
    public void testLineClassification() {
        String source = "# Header 1\n" +
                "\n" +
                "This is a paragraph.\n" +
                "## Header 2\n" +
                "  \n" +
                "- List item\n" +
                "1. Numbered list\n" +
                "```javascript\n" +
                "code block\n" +
                "```";

        MdLineStream stream = MdLexer.lex(source);

        assertEquals("Should have exactly 10 lines", 10, stream.lineCount);
        
        assertEquals(MdLineStream.L_HEADER, stream.lineTypes[0]);
        assertEquals(MdLineStream.L_BLANK, stream.lineTypes[1]);
        assertEquals(MdLineStream.L_PARAGRAPH, stream.lineTypes[2]);
        assertEquals(MdLineStream.L_HEADER, stream.lineTypes[3]);
        assertEquals(MdLineStream.L_BLANK, stream.lineTypes[4]);
        assertEquals(MdLineStream.L_LIST_ITEM, stream.lineTypes[5]);
        assertEquals(MdLineStream.L_LIST_ITEM, stream.lineTypes[6]);
        assertEquals(MdLineStream.L_CODE_FENCE, stream.lineTypes[7]);
        assertEquals(MdLineStream.L_PARAGRAPH, stream.lineTypes[8]);
        assertEquals(MdLineStream.L_CODE_FENCE, stream.lineTypes[9]);
    }
}
