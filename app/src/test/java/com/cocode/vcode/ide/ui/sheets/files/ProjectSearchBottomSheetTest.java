package com.cocode.vcode.ide.ui.sheets.files;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ProjectSearchBottomSheetTest {

    @Test
    public void testCreateMatchSnippet_stripsLeadingIndentation() {
        File dummy = new File("/test/File.java");
        String line = "        int count = calculateTotal();";
        // "calculateTotal" starts at index 20, ends at 34 in line
        ProjectSearchBottomSheet.ProjectSearchResult result =
                ProjectSearchBottomSheet.createMatchSnippet(dummy, 15, line, 20, 34, "calculateTotal");

        assertNotNull(result);
        assertEquals(15, result.line);
        assertEquals(21, result.column);
        // Indent has 8 spaces, so snippet begins at "int count = ..."
        assertEquals("int count = calculateTotal();", result.snippet);
        // "calculateTotal" should start at 20 - 8 = 12
        assertEquals(12, result.matchStart);
        assertEquals(26, result.matchEnd);
        assertEquals("calculateTotal", result.snippet.substring(result.matchStart, result.matchEnd));
    }

    @Test
    public void testCreateMatchSnippet_fallbackQuerySearchWhenColsMissing() {
        File dummy = new File("/test/File.js");
        String line = "    const targetNode = findNode();";
        // colStart = -1, colEnd = -1: should find "targetNode" in line
        ProjectSearchBottomSheet.ProjectSearchResult result =
                ProjectSearchBottomSheet.createMatchSnippet(dummy, 42, line, -1, -1, "targetNode");

        assertNotNull(result);
        assertEquals(42, result.line);
        assertEquals(11, result.column);
        assertEquals("const targetNode = findNode();", result.snippet);
        assertEquals("targetNode", result.snippet.substring(result.matchStart, result.matchEnd));
    }

    @Test
    public void testCreateMatchSnippet_windowsLongLine() {
        File dummy = new File("/test/bundle.min.js");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) sb.append("a");
        sb.append("MY_SPECIAL_KEYWORD");
        for (int i = 0; i < 200; i++) sb.append("z");

        String longLine = sb.toString();
        int keywordStart = 200;
        int keywordEnd = 200 + "MY_SPECIAL_KEYWORD".length();

        ProjectSearchBottomSheet.ProjectSearchResult result =
                ProjectSearchBottomSheet.createMatchSnippet(dummy, 1, longLine, keywordStart, keywordEnd, "MY_SPECIAL_KEYWORD");

        assertNotNull(result);
        assertEquals(201, result.column);
        assertTrue(result.snippet.startsWith("..."));
        assertTrue(result.snippet.endsWith("..."));
        assertEquals("MY_SPECIAL_KEYWORD", result.snippet.substring(result.matchStart, result.matchEnd));
    }

    @Test
    public void testCreateMatchSnippet_handlesTabsInIndentation() {
        File dummy = new File("/test/Tabs.java");
        String line = "\t\tString s = \"hello\";";
        int start = line.indexOf("hello");
        int end = start + "hello".length();
        ProjectSearchBottomSheet.ProjectSearchResult result =
                ProjectSearchBottomSheet.createMatchSnippet(dummy, 10, line, start, end, "hello");

        assertNotNull(result);
        assertEquals(start + 1, result.column);
        assertEquals("String s = \"hello\";", result.snippet);
        assertEquals("hello", result.snippet.substring(result.matchStart, result.matchEnd));
    }
}
