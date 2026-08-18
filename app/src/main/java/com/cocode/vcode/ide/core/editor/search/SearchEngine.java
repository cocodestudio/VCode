package com.cocode.vcode.ide.core.editor.search;

import com.cocode.vcode.ide.core.model.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Core text search engine for the editor workspace.
 * Supports configurable search modes including case-sensitivity flags,
 * full regular expression scanning, and whole-word matching boundaries.
 */
public class SearchEngine {

    /**
     * Scans a document string to find all occurrences of a specific search query.
     *
     * @param query         The text string or regex pattern to look for.
     * @param text          The full text content of the target file being searched.
     * @param caseSensitive True to enforce strict character casing rules.
     * @param regex         True if the query parameter should be processed as a regular expression.
     * @param wholeWord     True to isolate matches wrapped entirely by word boundaries (\b).
     * @return A list of SearchResult objects detailing the position coordinates of each match found.
     */
    public List<SearchResult> find(String query, String text,
                                   boolean caseSensitive, boolean regex, boolean wholeWord) {
        List<SearchResult> results = new ArrayList<>();
        // Fast-fail check: Avoid executing scanning mechanisms on empty payloads
        if (query == null || query.isEmpty() || text == null || text.isEmpty()) return results;

        Pattern pattern = buildPattern(query, caseSensitive, regex, wholeWord);
        if (pattern == null)
            return results; // Exit gracefully if the query contains faulty regex syntax

        // Pre-compute line start offsets across the document up front.
        // This indexes line breaks once so we can quickly calculate line/column numbers
        // during the match loop without continuously re-scanning the string from scratch.
        List<Integer> lineStarts = new ArrayList<>();
        lineStarts.add(0); // The very first line always commences at index 0
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') lineStarts.add(i + 1);
        }

        Matcher m = pattern.matcher(text);
        while (m.find()) {
            int start = m.start();
            int end = m.end();

            // Map raw absolute text indexes into user-friendly line and column numbers
            int[] lc = getLineCol(start, lineStarts);
            results.add(new SearchResult(start, end, lc[0], lc[1]));
        }
        return results;
    }

    public String replaceAll(String query, String text, String replaceText,
                             boolean caseSensitive, boolean regex, boolean wholeWord) {
        if (query == null || query.isEmpty() || text == null || text.isEmpty()) return text;

        Pattern pattern = buildPattern(query, caseSensitive, regex, wholeWord);
        if (pattern == null) return text;

        Matcher m = pattern.matcher(text);
        if (regex) {
            return m.replaceAll(replaceText);
        } else {
            return m.replaceAll(Matcher.quoteReplacement(replaceText));
        }
    }

    /**
     * Compiles the search query into an actionable RegEx pattern based on user criteria.
     */
    private Pattern buildPattern(String query, boolean caseSensitive, boolean regex, boolean wholeWord) {
        try {
            // Escape structural control tokens if this is a plain-text search, avoiding accidental regex triggers
            String patternStr = regex ? query : Pattern.quote(query);

            // Wrap the expression pattern inside boundary tokens if whole-word restrictions are requested
            if (wholeWord) patternStr = "\\b" + patternStr + "\\b";

            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            return Pattern.compile(patternStr, flags);
        } catch (PatternSyntaxException e) {
            // Revert back safely if the input query is an unparseable malformed regular expression
            return null;
        }
    }

    /**
     * Maps an absolute character index to 1-indexed row and column coordinates.
     * Traces backward through line break offsets to deduce active row placement.
     */
    private int[] getLineCol(int absPos, List<Integer> lineStarts) {
        int line;
        // Loop backward from the final registered line break to locate the nearest preceding index row block
        for (int i = lineStarts.size() - 1; i >= 0; i--) {
            if (lineStarts.get(i) <= absPos) {
                line = i + 1; // Translate 0-indexed index loop tracking into human 1-indexed values
                int col = absPos - lineStarts.get(i) + 1; // Columns are also displayed 1-indexed
                return new int[]{line, col};
            }
        }
        return new int[]{1, absPos + 1}; // Standard structural baseline fallback protection
    }
}