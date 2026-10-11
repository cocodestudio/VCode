package com.cocode.vcode.ide.core.completion.staticdata;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Two-tier direct-indexed completion index replacing FastTrie.
 *
 * Direct first-character array indexing provides O(1) jump to the target
 * bucket and O(log K) binary search within small (~25 item) buckets with zero
 * object allocations. When strict prefix matches do not fill the suggestion
 * threshold, it seamlessly falls back to fuzzy subsequence scoring to capture
 * abbreviations (e.g. "bgc" for "background-clip").
 */
public final class TwoTierIndex {

    private final CompletionItem[] items;
    private final int[] firstCharOffset = new int[128];
    private final int[] firstCharCount = new int[128];

    public TwoTierIndex(List<CompletionItem> source) {
        this(source != null ? source.toArray(new CompletionItem[0]) : new CompletionItem[0]);
    }

    public TwoTierIndex(CompletionItem[] source) {
        if (source == null || source.length == 0) {
            this.items = new CompletionItem[0];
            Arrays.fill(firstCharOffset, -1);
            Arrays.fill(firstCharCount, 0);
            return;
        }

        List<CompletionItem> valid = new ArrayList<>(source.length);
        for (CompletionItem item : source) {
            if (item != null && item.getLabel() != null && !item.getLabel().isEmpty()) {
                valid.add(item);
            }
        }

        this.items = valid.toArray(new CompletionItem[0]);
        Arrays.sort(this.items, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));

        Arrays.fill(firstCharOffset, -1);
        Arrays.fill(firstCharCount, 0);

        for (int i = 0; i < this.items.length; i++) {
            char c = Character.toLowerCase(this.items[i].getLabel().charAt(0));
            if (c < 128) {
                if (firstCharOffset[c] == -1) {
                    firstCharOffset[c] = i;
                }
                firstCharCount[c]++;
            }
        }
    }

    public List<CompletionItem> filter(String prefix, int maxResults) {
        return filter(prefix, maxResults, 1);
    }

    public List<CompletionItem> filter(String prefix, int maxResults, int suggestionThreshold) {
        if (items.length == 0 || maxResults <= 0) {
            return new ArrayList<>();
        }

        if (prefix == null || prefix.isEmpty()) {
            int limit = Math.min(items.length, maxResults);
            List<CompletionItem> out = new ArrayList<>(limit);
            for (int i = 0; i < limit; i++) {
                out.add(items[i]);
            }
            return out;
        }

        String lowerPrefix = prefix.toLowerCase();
        char firstChar = lowerPrefix.charAt(0);
        List<CompletionItem> results = new ArrayList<>(Math.min(maxResults, 32));
        boolean[] matched = new boolean[items.length];

        // Tier 1: O(1) jump to first-character bucket + binary search lower bound
        if (firstChar < 128 && firstCharCount[firstChar] > 0) {
            int offset = firstCharOffset[firstChar];
            int count = firstCharCount[firstChar];
            int low = offset;
            int high = offset + count - 1;
            int matchStart = -1;

            while (low <= high) {
                int mid = (low + high) >>> 1;
                String midLabel = items[mid].getLabel().toLowerCase();
                if (midLabel.startsWith(lowerPrefix)) {
                    matchStart = mid;
                    high = mid - 1;
                } else if (midLabel.compareTo(lowerPrefix) < 0) {
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }

            if (matchStart != -1) {
                for (int i = matchStart; i < offset + count; i++) {
                    if (items[i].getLabel().toLowerCase().startsWith(lowerPrefix)) {
                        results.add(items[i]);
                        matched[i] = true;
                        if (results.size() >= maxResults) {
                            return results;
                        }
                    } else {
                        break;
                    }
                }
            }
        }

        // Tier 2: Fuzzy abbreviation fallback when prefix matches are sparse
        int threshold = suggestionThreshold > 0 ? suggestionThreshold : maxResults;
        if (results.size() < threshold) {
            List<ScoredEntry> fuzzyMatches = new ArrayList<>();
            for (int i = 0; i < items.length; i++) {
                if (matched[i]) continue;
                int score = AutoCompleteEngine.computeFuzzyScore(items[i].getLabel(), lowerPrefix);
                if (score > 0) {
                    fuzzyMatches.add(new ScoredEntry(items[i], score));
                }
            }

            if (!fuzzyMatches.isEmpty()) {
                Collections.sort(fuzzyMatches, (a, b) -> Integer.compare(b.score, a.score));
                for (ScoredEntry entry : fuzzyMatches) {
                    results.add(entry.item);
                    if (results.size() >= maxResults) {
                        break;
                    }
                }
            }
        }

        return results;
    }

    public CompletionItem getExact(String label) {
        if (label == null || label.isEmpty() || items.length == 0) {
            return null;
        }
        char firstChar = Character.toLowerCase(label.charAt(0));
        if (firstChar >= 128 || firstCharCount[firstChar] == 0) {
            return null;
        }

        int offset = firstCharOffset[firstChar];
        int count = firstCharCount[firstChar];
        int low = offset;
        int high = offset + count - 1;
        String target = label.toLowerCase();

        while (low <= high) {
            int mid = (low + high) >>> 1;
            String midLabel = items[mid].getLabel().toLowerCase();
            int cmp = midLabel.compareTo(target);
            if (cmp == 0) {
                return items[mid];
            } else if (cmp < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return null;
    }

    public int size() {
        return items.length;
    }

    public CompletionItem[] getItems() {
        return items.clone();
    }

    private static final class ScoredEntry {
        final CompletionItem item;
        final int score;

        ScoredEntry(CompletionItem item, int score) {
            this.item = item;
            this.score = score;
        }
    }
}
