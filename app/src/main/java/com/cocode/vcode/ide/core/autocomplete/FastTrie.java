package com.cocode.vcode.ide.core.autocomplete;

import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * High-performance prefix tree (Trie) for O(L) time complexity code suggestions.
 * Optimized for IDE autocomplete where L is the length of the typed prefix.
 */
public class FastTrie {

    private TrieNode root = new TrieNode();

    /**
     * Clears all items from the Trie.
     */
    public void clear() {
        root = new TrieNode();
    }

    /**
     * Inserts a CompletionItem into the Trie based on its label.
     */
    public void insert(CompletionItem item) {
        if (item == null || item.getLabel() == null) return;
        String word = item.getLabel().toLowerCase();

        TrieNode current = root;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c >= 128) continue; // Only index standard ASCII for speed, skip others

            if (current.children[c] == null) {
                current.children[c] = new TrieNode();
            }
            current = current.children[c];
        }
        current.isEndOfWord = true;
        current.item = item;
    }

    /**
     * Returns all CompletionItems that start with the given prefix.
     * Completes in O(L + V) where L is prefix length and V is number of matches.
     */
    public List<CompletionItem> getCompletions(String prefix, int maxResults) {
        List<CompletionItem> results = new ArrayList<>();
        if (prefix == null) return results;

        TrieNode current = root;
        String lowerPrefix = prefix.toLowerCase();

        // 1. Traverse to the end of the prefix
        for (int i = 0; i < lowerPrefix.length(); i++) {
            char c = lowerPrefix.charAt(i);
            if (c >= 128) return results; // Prefix contains non-ASCII

            if (current.children[c] == null) {
                return results; // Prefix not found
            }
            current = current.children[c];
        }

        // 2. Perform DFS to gather all end-of-word items from this node
        gatherItems(current, results, maxResults);

        // 3. Sort by priority
        Collections.sort(results, (a, b) -> b.getTypePriority() - a.getTypePriority());
        return results;
    }

    private void gatherItems(TrieNode node, List<CompletionItem> results, int maxResults) {
        if (results.size() >= maxResults) return;

        if (node.isEndOfWord && node.item != null) {
            results.add(node.item);
        }

        for (int i = 0; i < 128; i++) {
            if (node.children[i] != null) {
                gatherItems(node.children[i], results, maxResults);
            }
        }
    }

    private static class TrieNode {
        // Using an array for ASCII characters (0-127) for O(1) child lookup
        final TrieNode[] children = new TrieNode[128];
        boolean isEndOfWord = false;
        CompletionItem item = null;
    }
}
