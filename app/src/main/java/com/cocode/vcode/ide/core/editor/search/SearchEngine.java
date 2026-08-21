package com.cocode.vcode.ide.core.editor.search;

import com.cocode.vcode.ide.core.model.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Text search and replace engine supporting case-sensitivity, regex, and whole-word matching.
 */
public class SearchEngine {

    /**
     * Finds all matches for the given query in the text content.
     *
     * @param query         the search string or regex pattern
     * @param text          the target document text
     * @param caseSensitive true for case-sensitive matching
     * @param regex         true if query is a regular expression
     * @param wholeWord     true to match whole words only
     * @return a list of {@link SearchResult} matches
     */
    public List<SearchResult> find(String query, String text,
                                   boolean caseSensitive, boolean regex, boolean wholeWord) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isEmpty() || text == null || text.isEmpty()) return results;

        Pattern pattern = buildPattern(query, caseSensitive, regex, wholeWord);
        if (pattern == null)
            return results;

        List<Integer> lineStarts = new ArrayList<>();
        lineStarts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') lineStarts.add(i + 1);
        }

        Matcher m = pattern.matcher(text);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
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

    private Pattern buildPattern(String query, boolean caseSensitive, boolean regex, boolean wholeWord) {
        try {
            String patternStr = regex ? query : Pattern.quote(query);

            if (wholeWord) patternStr = "\\b" + patternStr + "\\b";

            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            return Pattern.compile(patternStr, flags);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    private int[] getLineCol(int absPos, List<Integer> lineStarts) {
        for (int i = lineStarts.size() - 1; i >= 0; i--) {
            if (lineStarts.get(i) <= absPos) {
                int line = i + 1;
                int col = absPos - lineStarts.get(i) + 1;
                return new int[]{line, col};
            }
        }
        return new int[]{1, absPos + 1};
    }
}