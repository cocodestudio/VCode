package com.cocode.vcode.ide.core.language.base;

import com.cocode.vcode.ide.core.language.js.ParseResult;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Acceptance tests for L.1: the per-language parse-mode dispatcher.
 */
public class ParseModeGateTest {

    @Test
    public void tinyFileGetsModeFull() {
        assertEquals(ParseResult.MODE_FULL,
                ParseModeGate.select(ParseModeGate.SizeMetric.LINES, 1));
        assertEquals(ParseResult.MODE_FULL,
                ParseModeGate.select(ParseModeGate.SizeMetric.LINES, 0));
        assertEquals(ParseResult.MODE_FULL,
                ParseModeGate.select(ParseModeGate.SizeMetric.LINES, 999));
    }

    @Test
    public void mediumFileGetsModeLazyScope() {
        assertEquals(ParseResult.MODE_LAZY_SCOPE,
                ParseModeGate.select(ParseModeGate.SizeMetric.LINES, 1000));
        assertEquals(ParseResult.MODE_LAZY_SCOPE,
                ParseModeGate.select(ParseModeGate.SizeMetric.LINES, 3000));
        assertEquals(ParseResult.MODE_LAZY_SCOPE,
                ParseModeGate.select(ParseModeGate.SizeMetric.LINES, 5000));
    }

    @Test
    public void largeFileGetsModeTopLevel() {
        assertEquals(ParseResult.MODE_TOP_LEVEL,
                ParseModeGate.select(ParseModeGate.SizeMetric.LINES, 5001));
        assertEquals(ParseResult.MODE_TOP_LEVEL,
                ParseModeGate.select(ParseModeGate.SizeMetric.LINES, 10000));
        assertEquals(ParseResult.MODE_TOP_LEVEL,
                ParseModeGate.select(ParseModeGate.SizeMetric.LINES, 15000));
    }

    @Test
    public void hugeFileGetsModeTokenizeOnly() {
        // The L.1 acceptance check: a >15000-line file in each
        // language falls back to tokenize-only without crashing.
        assertEquals(ParseResult.MODE_TOKENIZE_ONLY,
                ParseModeGate.select(ParseModeGate.SizeMetric.LINES, 15001));
        assertEquals(ParseResult.MODE_TOKENIZE_ONLY,
                ParseModeGate.select(ParseModeGate.SizeMetric.LINES, 100000));
        assertEquals(ParseResult.MODE_TOKENIZE_ONLY,
                ParseModeGate.select(ParseModeGate.SizeMetric.LINES, Integer.MAX_VALUE));
    }

    @Test
    public void customThresholdsOverrideDefaults() {
        // JSON might want different cutoffs. Verify the gate honours
        // caller-supplied thresholds.
        int jsonT1 = 200, jsonT2 = 1000, jsonT3 = 5000;
        assertEquals(ParseResult.MODE_FULL,
                ParseModeGate.select(ParseModeGate.SizeMetric.NODES, 100,
                        jsonT1, jsonT2, jsonT3));
        assertEquals(ParseResult.MODE_LAZY_SCOPE,
                ParseModeGate.select(ParseModeGate.SizeMetric.NODES, 500,
                        jsonT1, jsonT2, jsonT3));
        assertEquals(ParseResult.MODE_TOP_LEVEL,
                ParseModeGate.select(ParseModeGate.SizeMetric.NODES, 3000,
                        jsonT1, jsonT2, jsonT3));
        assertEquals(ParseResult.MODE_TOKENIZE_ONLY,
                ParseModeGate.select(ParseModeGate.SizeMetric.NODES, 10000,
                        jsonT1, jsonT2, jsonT3));
    }

    @Test
    public void countLinesHandlesEmptyAndNull() {
        assertEquals(1, ParseModeGate.countLines(""));
        assertEquals(1, ParseModeGate.countLines(null));
    }

    @Test
    public void countLinesHandlesSingleLine() {
        assertEquals(1, ParseModeGate.countLines("var x = 1;"));
    }

    @Test
    public void countLinesHandlesMultipleLines() {
        assertEquals(3, ParseModeGate.countLines("a\nb\nc"));
        // The count includes a trailing line for a terminal newline.
        assertEquals(4, ParseModeGate.countLines("a\nb\nc\n"));
        // A single newline character yields a 2-line count (line + blank line).
        assertEquals(2, ParseModeGate.countLines("\n"));
    }

    @Test
    public void selectByLineCountCombinesCountAndSelect() {
        // > 15000 lines → MODE_TOKENIZE_ONLY
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 20000; i++) huge.append("var x;\n");
        assertEquals(ParseResult.MODE_TOKENIZE_ONLY,
                ParseModeGate.selectByLineCount(huge.toString()));

        // < 1000 lines → MODE_FULL
        StringBuilder small = new StringBuilder();
        for (int i = 0; i < 100; i++) small.append("var x;\n");
        assertEquals(ParseResult.MODE_FULL,
                ParseModeGate.selectByLineCount(small.toString()));
    }

    @Test
    public void modeIsConsistentWithOriginalJsTiering() {
        // Boundary-value check: the new gate must produce the same
        // mode as the original inline if-else in JsParsePipeline.
        // The original was:
        //   if (lineCount < 1000) MODE_FULL
        //   else if (lineCount <= 5000) MODE_LAZY_SCOPE
        //   else if (lineCount <= 15000) MODE_TOP_LEVEL
        //   else MODE_TOKENIZE_ONLY
        for (int lines : new int[]{0, 1, 999, 1000, 1001, 4999, 5000, 5001, 14999, 15000, 15001}) {
            int expected;
            if (lines < 1000) expected = ParseResult.MODE_FULL;
            else if (lines <= 5000) expected = ParseResult.MODE_LAZY_SCOPE;
            else if (lines <= 15000) expected = ParseResult.MODE_TOP_LEVEL;
            else expected = ParseResult.MODE_TOKENIZE_ONLY;
            assertEquals("lines=" + lines, expected,
                    ParseModeGate.select(ParseModeGate.SizeMetric.LINES, lines));
        }
    }

    @Test
    public void bytesMetricFollowsSameThresholds() {
        // BYTES, NODES, and LINES all use the same threshold logic;
        // the metric is just a label.
        assertEquals(ParseResult.MODE_FULL,
                ParseModeGate.select(ParseModeGate.SizeMetric.BYTES, 100));
        assertEquals(ParseResult.MODE_TOKENIZE_ONLY,
                ParseModeGate.select(ParseModeGate.SizeMetric.BYTES, 999999));
    }
}
