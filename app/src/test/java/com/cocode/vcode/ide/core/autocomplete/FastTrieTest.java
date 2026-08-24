package com.cocode.vcode.ide.core.autocomplete;

import com.cocode.vcode.ide.core.model.CompletionItem;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FastTrieTest {

    private FastTrie trie;

    @Before
    public void setUp() {
        trie = new FastTrie();
    }

    private CompletionItem createItem(String label, CompletionItem.Type type) {
        CompletionItem item = new CompletionItem(label, label, null, type, 0);
        return item;
    }

    @Test
    public void testInsertAndGetCompletions() {
        trie.insert(createItem("console", CompletionItem.Type.VALUE));
        trie.insert(createItem("const", CompletionItem.Type.KEYWORD));
        trie.insert(createItem("continue", CompletionItem.Type.KEYWORD));
        trie.insert(createItem("let", CompletionItem.Type.KEYWORD));

        List<CompletionItem> results = trie.getCompletions("con", 10);
        assertEquals("Should find exactly 3 completions for 'con'", 3, results.size());

        boolean foundConsole = false, foundConst = false, foundContinue = false;
        for (CompletionItem item : results) {
            if ("console".equals(item.getLabel())) foundConsole = true;
            if ("const".equals(item.getLabel())) foundConst = true;
            if ("continue".equals(item.getLabel())) foundContinue = true;
        }
        assertTrue(foundConsole && foundConst && foundContinue);
    }

    @Test
    public void testCaseInsensitivity() {
        trie.insert(createItem("Document", CompletionItem.Type.VALUE));
        trie.insert(createItem("document", CompletionItem.Type.VALUE));
        trie.insert(createItem("DOMParser", CompletionItem.Type.VALUE));

        List<CompletionItem> results = trie.getCompletions("dom", 10);
        assertEquals("Should find DOMParser regardless of case", 1, results.size());
        assertEquals("DOMParser", results.get(0).getLabel());

        List<CompletionItem> docResults = trie.getCompletions("Doc", 10);
        // FastTrie uses the lowercase label as the key, so "document" and "Document" map to the same node
        // The last inserted ("document") overwrites the previous one.
        assertEquals("Should find 1 item due to overwrite", 1, docResults.size());
    }

    @Test
    public void testSortingPriority() {
        CompletionItem var = createItem("MathVar", CompletionItem.Type.VALUE); // priority typically lower than KEYWORD
        CompletionItem cls = createItem("MathClass", CompletionItem.Type.KEYWORD); // keyword has higher priority

        trie.insert(var);
        trie.insert(cls);

        List<CompletionItem> results = trie.getCompletions("math", 10);
        assertEquals(2, results.size());
        
        // Ensure sorting by type priority descending
        // KEYWORD > VALUE
        assertTrue("Higher priority items should appear first",
                results.get(0).getTypePriority() >= results.get(1).getTypePriority());
    }

    @Test
    public void testMaxResultsLimit() {
        for (int i = 0; i < 20; i++) {
            trie.insert(createItem("item" + i, CompletionItem.Type.VALUE));
        }

        List<CompletionItem> results = trie.getCompletions("item", 5);
        assertEquals("Should cap results at maxResults (5)", 5, results.size());
    }

    @Test
    public void testClear() {
        trie.insert(createItem("hello", CompletionItem.Type.KEYWORD));
        assertEquals(1, trie.getCompletions("hel", 10).size());

        trie.clear();
        assertEquals("Trie should be empty after clear", 0, trie.getCompletions("hel", 10).size());
    }

    @Test
    public void testEmptyAndNullPrefix() {
        trie.insert(createItem("test", CompletionItem.Type.KEYWORD));
        
        List<CompletionItem> nullResults = trie.getCompletions(null, 10);
        assertNotNull(nullResults);
        assertTrue(nullResults.isEmpty());
        
        // Empty prefix should return all items (up to maxResults) since empty string is prefix of everything
        List<CompletionItem> emptyResults = trie.getCompletions("", 10);
        assertEquals(1, emptyResults.size());
    }

    @Test
    public void testNonAsciiHandling() {
        // The FastTrie explicitly skips non-ASCII characters during insert and returns empty if prefix has non-ASCII
        trie.insert(createItem("testé", CompletionItem.Type.KEYWORD)); // The 'é' will be skipped
        
        // The prefix "testé" contains non-ASCII, getCompletions returns empty
        List<CompletionItem> results = trie.getCompletions("testé", 10);
        assertTrue("Non-ASCII prefix should return empty", results.isEmpty());
        
        // The item was indexed as "test", so searching for "test" should find it
        List<CompletionItem> validResults = trie.getCompletions("test", 10);
        assertEquals(1, validResults.size());
        assertEquals("testé", validResults.get(0).getLabel());
    }
}
