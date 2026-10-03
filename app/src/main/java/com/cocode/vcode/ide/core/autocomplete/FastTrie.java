package com.cocode.vcode.ide.core.autocomplete;

import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * High-performance prefix tree (Trie) for O(L) time complexity code suggestions.
 * Optimized for IDE autocomplete where L is the length of the typed prefix.
 *
 * <p>Uses direct array indexing on the root node for instant O(1) first-character dispatch,
 * and adaptive compact sorted arrays (char[] keys + TrieNode[] children) with binary search
 * for deeper nodes. This reduces node memory consumption from ~528-1040 bytes down to ~32-64 bytes
 * per node, preventing GC churn and OOM on large npm package corpora.
 */
public class FastTrie {

    private RootTrieNode root = new RootTrieNode();

    /**
     * Clears all items from the Trie.
     */
    public void clear() {
        root = new RootTrieNode();
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

            current = current.getOrCreateChild(c);
            if (current == null) return;
        }
        current.addItem(item);
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

            current = current.getChild(c);
            if (current == null) {
                return results; // Prefix not found
            }
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

        if (node.isEndOfWord) {
            if (node.primaryItem != null) {
                results.add(node.primaryItem);
                if (results.size() >= maxCandidates) return;
            }
            if (node.extraItems != null) {
                for (CompletionItem extra : node.extraItems) {
                    results.add(extra);
                    if (results.size() >= maxCandidates) return;
                }
            }
        }

        node.gatherChildren(results, maxCandidates, this);
    }

    private static class TrieNode {
        char[] keys = null;
        TrieNode[] children = null;
        boolean isEndOfWord = false;
        CompletionItem primaryItem = null;
        List<CompletionItem> extraItems = null;

        TrieNode getChild(char c) {
            if (keys == null) return null;
            int idx = Arrays.binarySearch(keys, c);
            return idx >= 0 ? children[idx] : null;
        }

        TrieNode getOrCreateChild(char c) {
            if (keys == null) {
                keys = new char[]{c};
                children = new TrieNode[]{new TrieNode()};
                return children[0];
            }
            int idx = Arrays.binarySearch(keys, c);
            if (idx >= 0) {
                return children[idx];
            }
            int ins = -(idx + 1);
            int len = keys.length;
            char[] newKeys = new char[len + 1];
            TrieNode[] newChildren = new TrieNode[len + 1];

            if (ins > 0) {
                System.arraycopy(keys, 0, newKeys, 0, ins);
                System.arraycopy(children, 0, newChildren, 0, ins);
            }
            newKeys[ins] = c;
            newChildren[ins] = new TrieNode();
            if (ins < len) {
                System.arraycopy(keys, ins, newKeys, ins + 1, len - ins);
                System.arraycopy(children, ins, newChildren, ins + 1, len - ins);
            }
            keys = newKeys;
            children = newChildren;
            return newChildren[ins];
        }

        void addItem(CompletionItem item) {
            isEndOfWord = true;
            if (primaryItem == null) {
                primaryItem = item;
                return;
            }
            if (isSameSymbol(primaryItem, item)) {
                primaryItem = item;
                return;
            }
            if (extraItems != null) {
                for (int i = 0; i < extraItems.size(); i++) {
                    if (isSameSymbol(extraItems.get(i), item)) {
                        extraItems.set(i, item);
                        return;
                    }
                }
            } else {
                extraItems = new ArrayList<>(2);
            }
            extraItems.add(item);
        }

        private static boolean isSameSymbol(CompletionItem a, CompletionItem b) {
            if (a == null || b == null) return false;
            if (!a.getLabel().equalsIgnoreCase(b.getLabel())) return false;
            if (a.getType() != b.getType()) return false;
            return true;
        }

        void gatherChildren(List<CompletionItem> results, int maxCandidates, FastTrie trie) {
            if (keys == null) return;
            for (int i = 0; i < keys.length; i++) {
                if (children[i] != null) {
                    trie.gatherItems(children[i], results, maxCandidates);
                    if (results.size() >= maxCandidates) return;
                }
            }
        }
    }

    private static class RootTrieNode extends TrieNode {
        private final TrieNode[] asciiChildren = new TrieNode[128];

        @Override
        TrieNode getChild(char c) {
            if (c < 128) {
                return asciiChildren[c];
            }
            return null;
        }

        @Override
        TrieNode getOrCreateChild(char c) {
            if (c < 128) {
                if (asciiChildren[c] == null) {
                    asciiChildren[c] = new TrieNode();
                }
                return asciiChildren[c];
            }
            return null;
        }

        @Override
        void gatherChildren(List<CompletionItem> results, int maxCandidates, FastTrie trie) {
            for (int i = 0; i < 128; i++) {
                if (asciiChildren[i] != null) {
                    trie.gatherItems(asciiChildren[i], results, maxCandidates);
                    if (results.size() >= maxCandidates) return;
                }
            }
        }
    }
}

