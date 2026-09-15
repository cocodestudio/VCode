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

        // Walk the trie to the end of the prefix node
        for (int i = 0; i < lowerPrefix.length(); i++) {
            char c = lowerPrefix.charAt(i);
            if (c >= 128) return results; // Prefix contains non-ASCII

            if (current.children[c] == null) {
                return results; // Prefix not found
            }
            current = current.children[c];
        }

        // Depth-first search to collect matching candidates within limits
        gatherItems(current, results, Math.max(maxResults * 4, 128));

        // Sort candidates by priority rank descending, then alphabetically
        Collections.sort(results, (a, b) -> {
            int pDiff = b.getTypePriority() - a.getTypePriority();
            if (pDiff != 0) return pDiff;
            return a.getLabel().compareToIgnoreCase(b.getLabel());
        });

        if (results.size() > maxResults) {
            return new ArrayList<>(results.subList(0, maxResults));
        }
        return results;
    }

    private void gatherItems(TrieNode node, List<CompletionItem> results, int maxCandidates) {
        if (results.size() >= maxCandidates) return;

        if (node.isEndOfWord && node.item != null) {
            results.add(node.item);
        }

        for (int i = 0; i < 128; i++) {
            if (node.children[i] != null) {
                gatherItems(node.children[i], results, maxCandidates);
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
