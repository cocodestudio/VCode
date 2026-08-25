package com.cocode.vcode.ide.core.editor.text;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ContentTest {

    private Content content;

    @Before
    public void setUp() {
        content = new Content();
    }

    @Test
    public void testLoadText() {
        content = new Content("hello\nworld");
        assertEquals(2, content.lineCount());
        assertEquals("hello", content.getLineCopy(0));
        assertEquals("world", content.getLineCopy(1));
    }

    @Test
    public void testInsertSingleLine() {
        content.insert(0, 0, "hello");
        assertEquals("hello", content.getLineCopy(0));
        assertEquals(1, content.lineCount());

        content.insert(0, 5, " world");
        assertEquals("hello world", content.getLineCopy(0));
    }

    @Test
    public void testInsertMultiLine() {
        content.insert(0, 0, "line1\nline2\nline3");
        assertEquals(3, content.lineCount());
        assertEquals("line1", content.getLineCopy(0));
        assertEquals("line2", content.getLineCopy(1));
        assertEquals("line3", content.getLineCopy(2));
    }

    @Test
    public void testDeleteSingleLine() {
        content = new Content("hello world");
        content.delete(0, 5, 0, 11);
        assertEquals("hello", content.getLineCopy(0));
    }

    @Test
    public void testDeleteMultiLine() {
        content = new Content("line1\nline2\nline3");
        content.delete(0, 2, 2, 2);
        // "li" + "ne3" => "line3"
        assertEquals(1, content.lineCount());
        assertEquals("line3", content.getLineCopy(0));
    }

    @Test
    public void testReplace() {
        content = new Content("hello world");
        content.replace(0, 6, 0, 11, "universe");
        assertEquals("hello universe", content.getLineCopy(0));
    }

    @Test
    public void testFlatOffsetAndPositionAt() {
        content = new Content("a\nbc\ndef");
        // "a\n" (2 chars)
        // "bc\n" (3 chars)
        // "def" (3 chars)

        // flatOffset
        assertEquals(0, content.flatOffset(new ContentPosition(0, 0))); // before 'a'
        assertEquals(1, content.flatOffset(new ContentPosition(0, 1))); // after 'a', before '\n'
        assertEquals(2, content.flatOffset(new ContentPosition(1, 0))); // before 'b'
        assertEquals(5, content.flatOffset(new ContentPosition(2, 0))); // before 'd'

        // positionAt
        ContentPosition pos1 = content.positionAt(0);
        assertEquals(0, pos1.line);
        assertEquals(0, pos1.column);

        ContentPosition pos2 = content.positionAt(2);
        assertEquals(1, pos2.line);
        assertEquals(0, pos2.column);

        ContentPosition pos3 = content.positionAt(6); // 'e'
        assertEquals(2, pos3.line);
        assertEquals(1, pos3.column);
    }
}
