package com.cocode.vcode.ide.core.diagnostic.util;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class LinterUtilsTest {

    @Test
    public void testGetLine() {
        String text = "hello\nworld\n!";
        assertEquals(1, LinterUtils.getLine(text, 0)); // 'h'
        assertEquals(1, LinterUtils.getLine(text, 4)); // 'o'
        assertEquals(1, LinterUtils.getLine(text, 5)); // '\n'
        assertEquals(2, LinterUtils.getLine(text, 6)); // 'w'
        assertEquals(3, LinterUtils.getLine(text, 12)); // '!'
    }

    @Test
    public void testGetColumn() {
        String text = "hello\nworld\n!";
        assertEquals(1, LinterUtils.getColumn(text, 0)); // 'h'
        assertEquals(5, LinterUtils.getColumn(text, 4)); // 'o'
        assertEquals(6, LinterUtils.getColumn(text, 5)); // '\n'
        assertEquals(1, LinterUtils.getColumn(text, 6)); // 'w'
        assertEquals(1, LinterUtils.getColumn(text, 12)); // '!'
    }

    @Test
    public void testSplitLines() {
        String text = "hello\nworld\n!";
        String[] lines = LinterUtils.splitLines(text);
        assertArrayEquals(new String[]{"hello", "world", "!"}, lines);

        String textEmpty = "";
        assertArrayEquals(new String[]{""}, LinterUtils.splitLines(textEmpty));

        String textTrailingNewline = "hello\n";
        assertArrayEquals(new String[]{"hello", ""}, LinterUtils.splitLines(textTrailingNewline));
    }

    @Test
    public void testTokenLength() {
        String text = "const myVar = 42;";
        assertEquals(5, LinterUtils.tokenLength(text, 0)); // "const"
        assertEquals(5, LinterUtils.tokenLength(text, 6)); // "myVar"
        assertEquals(1, LinterUtils.tokenLength(text, 12)); // "="
        assertEquals(2, LinterUtils.tokenLength(text, 14)); // "42"
    }

    @Test
    public void testLineStartOffset() {
        String text = "hello\nworld\n!";
        assertEquals(0, LinterUtils.lineStartOffset(text, 1));
        assertEquals(6, LinterUtils.lineStartOffset(text, 2));
        assertEquals(12, LinterUtils.lineStartOffset(text, 3));
    }

    @Test
    public void testTrimmedStart() {
        assertEquals(0, LinterUtils.trimmedStart("hello"));
        assertEquals(2, LinterUtils.trimmedStart("  hello"));
        assertEquals(4, LinterUtils.trimmedStart("\t\t  hello"));
    }
}
