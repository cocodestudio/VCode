package com.cocode.vcode.ide.views;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WordWrapHelperTest {

    @Test
    public void testShortLineDoesNotWrap() {
        String line = "const x = 10;";
        int[] breaks = WordWrapHelper.computeLineWrapBreaks(line, line.length(), 20);
        assertNull("Short line should not wrap", breaks);
    }

    @Test
    public void testWordIsNotSplitAcrossRows() {
        // Line length: 38
        // 0..14: "private String " (15 chars)
        // 15..21: "initial" (7 chars)
        // 22..37: " = \"hello\";" (16 chars)
        String line = "private String initial = \"hello\";";
        int charsPerRow = 20; // 20 chars would cut "initial" at char 20 ('i' of 'initial')
        int[] breaks = WordWrapHelper.computeLineWrapBreaks(line, line.length(), charsPerRow);

        assertNotNull("Line should wrap", breaks);
        assertEquals(2, breaks.length);
        assertEquals(0, breaks[0]);
        // Break must occur before "initial", at index 15 (start of word "initial")
        assertEquals(15, breaks[1]);

        int sr0Start = WordWrapHelper.getSubRowStart(breaks, 0);
        int sr0End = WordWrapHelper.getSubRowEnd(breaks, 0, line.length());
        int sr1Start = WordWrapHelper.getSubRowStart(breaks, 1);
        int sr1End = WordWrapHelper.getSubRowEnd(breaks, 1, line.length());

        assertEquals("private String ", line.substring(sr0Start, sr0End));
        assertEquals("initial = \"hello\";", line.substring(sr1Start, sr1End));
    }

    @Test
    public void testMultipleSubRowsKeepWordsIntact() {
        String line = "The quick brown fox jumps over the lazy dog and runs away";
        int charsPerRow = 16;
        int[] breaks = WordWrapHelper.computeLineWrapBreaks(line, line.length(), charsPerRow);

        assertNotNull(breaks);
        assertTrue("Should have multiple sub-rows", breaks.length >= 4);

        // Verify each sub-row does not exceed charsPerRow and does not slice words
        for (int sr = 0; sr < breaks.length; sr++) {
            int start = WordWrapHelper.getSubRowStart(breaks, sr);
            int end = WordWrapHelper.getSubRowEnd(breaks, sr, line.length());
            assertTrue("Sub-row length must be <= charsPerRow", (end - start) <= charsPerRow);
        }
    }

    @Test
    public void testPunctuationBreakOpportunities() {
        String line = "System.out.println(myVeryLongIdentifierName);";
        int charsPerRow = 20;
        int[] breaks = WordWrapHelper.computeLineWrapBreaks(line, line.length(), charsPerRow);

        assertNotNull(breaks);
        int sr0Start = WordWrapHelper.getSubRowStart(breaks, 0);
        int sr0End = WordWrapHelper.getSubRowEnd(breaks, 0, line.length());
        // Should break after '(' or after a dot '.'
        String row0 = line.substring(sr0Start, sr0End);
        assertTrue("Row 0 should end with '(' or '.'", row0.endsWith("(") || row0.endsWith("."));
    }

    @Test
    public void testCompoundOperatorsNotSplit() {
        String line = "if (firstVariable == secondVariable && thirdVariable != fourthVariable)";
        int charsPerRow = 22;
        int[] breaks = WordWrapHelper.computeLineWrapBreaks(line, line.length(), charsPerRow);

        assertNotNull(breaks);
        for (int sr = 0; sr < breaks.length; sr++) {
            int start = WordWrapHelper.getSubRowStart(breaks, sr);
            int end = WordWrapHelper.getSubRowEnd(breaks, sr, line.length());
            String row = line.substring(start, end);
            // Neither row should end with a lone '=' or '&' or '!' when next row starts with the matching symbol
            if (sr < breaks.length - 1) {
                int nextStart = WordWrapHelper.getSubRowStart(breaks, sr + 1);
                char nextChar = line.charAt(nextStart);
                if (nextChar == '=') {
                    assertTrue("Should not split compound operator", !row.endsWith("=") && !row.endsWith("!") && !row.endsWith("<") && !row.endsWith(">"));
                }
                if (nextChar == '&') {
                    assertTrue("Should not split &&", !row.endsWith("&"));
                }
            }
        }
    }

    @Test
    public void testVeryLongUnbrokenTokenFallsBackToHardBreak() {
        // 62-character unbroken string with no spaces or punctuation
        String line = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int charsPerRow = 20;
        int[] breaks = WordWrapHelper.computeLineWrapBreaks(line, line.length(), charsPerRow);

        assertNotNull(breaks);
        assertEquals(4, breaks.length);
        assertEquals(0, breaks[0]);
        assertEquals(20, breaks[1]);
        assertEquals(40, breaks[2]);
        assertEquals(60, breaks[3]);
    }

    @Test
    public void testCoordinateMappingRoundTrip() {
        String line = "private String initial = \"hello\";";
        int charsPerRow = 20;
        int[] breaks = WordWrapHelper.computeLineWrapBreaks(line, line.length(), charsPerRow);

        assertNotNull(breaks);
        for (int col = 0; col <= line.length(); col++) {
            int subRow = WordWrapHelper.visualSubRow(breaks, col);
            int colInSub = WordWrapHelper.colInSubRow(breaks, col);
            int reconstructedCol = WordWrapHelper.getSubRowStart(breaks, subRow) + colInSub;
            assertEquals("Column mapping round-trip must match for col " + col, col, reconstructedCol);
        }
    }

    @Test
    public void testColInSpecificSubRowBoundary() {
        // breaks: [0, 15] for line length 33
        int[] breaks = new int[]{0, 15};

        // Sub-row 0 spans [0, 15)
        assertEquals(0, WordWrapHelper.colInSpecificSubRow(breaks, 0, 0));
        assertEquals(5, WordWrapHelper.colInSpecificSubRow(breaks, 0, 5));
        // Boundary case: column 15 is the exclusive end of sub-row 0.
        // colInSubRow(breaks, 15) returns 0 (mapped to sub-row 1),
        // but colInSpecificSubRow(breaks, 0, 15) must return 15!
        assertEquals(15, WordWrapHelper.colInSpecificSubRow(breaks, 0, 15));
        assertEquals(0, WordWrapHelper.colInSubRow(breaks, 15));

        // Sub-row 1 spans [15, 33)
        assertEquals(0, WordWrapHelper.colInSpecificSubRow(breaks, 1, 15));
        assertEquals(10, WordWrapHelper.colInSpecificSubRow(breaks, 1, 25));
        assertEquals(18, WordWrapHelper.colInSpecificSubRow(breaks, 1, 33));
    }
}
