package com.cocode.vcode.ide.core.autocomplete;

import android.content.Context;

import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The base architectural blueprint for context-aware code completion.
 * Houses shared text tracking utilities, VS Code-style fuzzy matching with score-based ranking,
 * token extraction helpers, and asset loading utilities.
 *
 * <p>Fuzzy matching algorithm mirrors VS Code's IntelliSense scorer:
 * <ul>
 *   <li>Exact full match → highest score</li>
 *   <li>Prefix match → high score with bonus for start-of-word</li>
 *   <li>Consecutive character run → proportional run-length bonus</li>
 *   <li>CamelCase / word-boundary alignment → bonus per boundary hit</li>
 *   <li>Contains (non-consecutive) → low base score</li>
 * </ul>
 */
public abstract class AutoCompleteEngine {

    /**
     * Maximum number of suggestions shown in the popup — matches VS Code's default.
     */
    protected static final int MAX_SUGGESTIONS = 20;

    protected final Context context;

    public AutoCompleteEngine(Context context) {
        // Guard against memory leaks by capturing the application-wide context reference
        this.context = context.getApplicationContext();
    }

    /**
     * Scans the editor text at the specified cursor pointer to compute valid choices.
     * Implemented individually by language-specific engines.
     */
    public abstract List<CompletionItem> getSuggestions(String fullText, int cursorPos);

    // Text extraction helpers

    /**
     * Traces backward from the current cursor location to extract the current word fragment being typed.
     * Word characters: letters, digits, underscore, hyphen (for CSS), dollar sign (JS).
     *
     * <p>If the character directly before the cursor is not a word character (e.g. '.', ':', '(',
     * '"', ' '), this returns an empty string to enable trigger-character-based completion.
     */
    protected String getWordBeforeCursor(String text, int pos) {
        if (text == null || pos <= 0 || pos > text.length()) return "";

        // If the char directly before cursor is not a word char, there is no typed word fragment.
        // This is what enables trigger characters like '.' and ':' to work correctly.
        if (!isWordChar(text.charAt(pos - 1))) return "";

        int start = pos - 1;
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }
        return text.substring(start, pos);
    }

    /**
     * Extracts an Emmet abbreviation — alphanumeric characters plus Emmet operator symbols.
     * Handles nested braces/brackets by tracking depth so that text content like {Click me}
     * is properly included.
     */
    protected String getEmmetAbbreviationBeforeCursor(String text, int pos) {
        if (text == null || pos <= 0 || pos > text.length()) return "";
        int start = pos - 1;
        int braceDepth = 0;  // {} depth
        int bracketDepth = 0; // [] depth
        int parenDepth = 0;  // () depth

        // Walk backward, tracking brace/bracket/paren depth
        while (start >= 0) {
            char c = text.charAt(start);

            // Inside braces/brackets/parens, allow any character
            if (braceDepth > 0 || bracketDepth > 0 || parenDepth > 0) {
                if (c == '}') braceDepth++;
                else if (c == '{') {
                    braceDepth--;
                    if (braceDepth < 0) break;
                } else if (c == ']') bracketDepth++;
                else if (c == '[') {
                    bracketDepth--;
                    if (bracketDepth < 0) break;
                } else if (c == ')') parenDepth++;
                else if (c == '(') {
                    parenDepth--;
                    if (parenDepth < 0) break;
                }
                start--;
                continue;
            }

            // Opening delimiters (walking backward, these are "closing" from our perspective)
            if (c == '}') {
                braceDepth++;
                start--;
                continue;
            }
            if (c == ']') {
                bracketDepth++;
                start--;
                continue;
            }
            if (c == ')') {
                parenDepth++;
                start--;
                continue;
            }

            // Standard Emmet characters
            if (c == '>') {
                // Distinguish Emmet child operator '>' from HTML tag closing '>'.
                // An HTML tag closing '>' is preceded by a tag name (letters/digits)
                // which is itself preceded by '<' or '</'.
                // Walk back from start-1 to check: if we find a '<' before any
                // non-tag-name character, this '>' belongs to an HTML tag — stop.
                int lookahead = start - 1;
                while (lookahead >= 0 && (Character.isLetterOrDigit(text.charAt(lookahead))
                        || text.charAt(lookahead) == '-' || text.charAt(lookahead) == '_'
                        || text.charAt(lookahead) == '/')) {
                    lookahead--;
                }
                if (lookahead >= 0 && text.charAt(lookahead) == '<') {
                    // This '>' closes an HTML tag like <p>, </p>, <br/> — stop here.
                    break;
                }
                // It's an Emmet child operator — include it.
                start--;
            } else if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '$'
                    || c == '#' || c == '.' || c == '*' || c == '+'
                    || c == '^' || c == '!' || c == ':') {
                start--;
            } else {
                break;
            }
        }
        return text.substring(start + 1, pos);
    }

    /**
     * Isolates the current text fragment running from the last newline up to the current pointer position.
     */
    protected String getLineBeforeCursor(String text, int pos) {
        if (text == null || pos <= 0 || pos > text.length()) return "";

        int start = pos - 1;
        while (start > 0 && text.charAt(start - 1) != '\n') {
            start--;
        }
        return text.substring(start, pos);
    }

    /**
     * Grabs the nearest non-blank text string directly preceding the current pointer location,
     * skipping through intermediate trailing whitespaces.
     */
    protected String getNonWhitespaceBeforeCursor(String text, int pos) {
        if (text == null || pos <= 0) return "";

        int i = pos - 1;
        while (i >= 0 && Character.isWhitespace(text.charAt(i))) i--;
        if (i < 0) return "";

        int end = i + 1;
        int start = i;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) start--;
        return text.substring(start, end);
    }

    // Fuzzy filtering and scoring

    /**
     * Checks if the cursor is inside a string literal on the current line.
     * Handles single quotes, double quotes, and backticks.
     */
    protected boolean isInsideStringLiteral(String fullText, int cursorPos) {
        String line = getLineBeforeCursor(fullText, cursorPos);
        boolean inDouble = false;
        boolean inSingle = false;
        boolean inBacktick = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            boolean escaped = (i > 0 && line.charAt(i - 1) == '\\');
            // If the backslash itself is escaped (e.g. \\"), it doesn't escape the quote.
            // Simple heuristic for single-line check:
            if (escaped) {
                int slashes = 0;
                for (int j = i - 1; j >= 0 && line.charAt(j) == '\\'; j--) slashes++;
                escaped = slashes % 2 != 0;
            }

            if (!escaped) {
                if (c == '"' && !inSingle && !inBacktick) inDouble = !inDouble;
                else if (c == '\'' && !inDouble && !inBacktick) inSingle = !inSingle;
                else if (c == '`' && !inDouble && !inSingle) inBacktick = !inBacktick;
            }
        }

        return inDouble || inSingle || inBacktick;
    }

    /**
     * Performs VS Code-style fuzzy matching and score-based ranking against a candidate list.
     *
     * <p>Scoring tiers (higher = better):
     * <ol>
     *   <li>Exact full match</li>
     *   <li>Prefix match (starts with query)</li>
     *   <li>Word-boundary / CamelCase hit match</li>
     *   <li>Fuzzy subsequence match with consecutive-run and boundary bonuses</li>
     * </ol>
     * <p>
     * Results are sorted descending by (sortScore + typePriority) then returned up to MAX_SUGGESTIONS.
     */
    protected List<CompletionItem> fuzzyFilter(List<CompletionItem> all, String prefix) {
        if (all == null) return new ArrayList<>();

        // Empty prefix → return top items ordered by type priority
        if (prefix == null || prefix.isEmpty()) {
            List<CompletionItem> copy = new ArrayList<>(all);
            for (CompletionItem item : copy) item.setSortScore(item.getTypePriority() * 10);
            Collections.sort(copy, (a, b) -> b.getSortScore() - a.getSortScore());
            return copy.size() > MAX_SUGGESTIONS ? copy.subList(0, MAX_SUGGESTIONS) : copy;
        }

        String lowerPrefix = prefix.toLowerCase();
        List<CompletionItem> matched = new ArrayList<>();

        for (CompletionItem item : all) {
            if (item.getLabel() == null) continue;
            int score = computeFuzzyScore(item.getLabel(), lowerPrefix);
            if (score > 0) {
                item.setSortScore(score + item.getTypePriority() * 10);
                matched.add(item);
            }
        }

        // Stable sort: higher score first
        Collections.sort(matched, (a, b) -> b.getSortScore() - a.getSortScore());

        return matched.size() > MAX_SUGGESTIONS ? matched.subList(0, MAX_SUGGESTIONS) : matched;
    }

    /**
     * Computes a fuzzy match score between a candidate label and a lowercase query prefix.
     * Returns 0 if the query is not a subsequence of the label (no match).
     *
     * <p>Scoring bonuses (mirrors VS Code's internal scorer):
     * <ul>
     *   <li>+100 exact full match</li>
     *   <li>+80  prefix match (label starts with query)</li>
     *   <li>+60  word-boundary prefix (e.g. query="log" matches "console.log")</li>
     *   <li>+40  consecutive character run of length ≥ 2</li>
     *   <li>+10  each word-boundary or CamelCase hit</li>
     *   <li>+5   consecutive run continuation</li>
     *   <li>-3   each non-consecutive gap character (distance penalty)</li>
     * </ul>
     */
    protected int computeFuzzyScore(String label, String lowerQuery) {
        if (label == null || lowerQuery == null || lowerQuery.isEmpty()) return 0;

        String lowerLabel = label.toLowerCase();
        int queryLen = lowerQuery.length();
        int labelLen = lowerLabel.length();

        // Exact match
        if (lowerLabel.equals(lowerQuery)) return 1000;

        // Prefix match
        if (lowerLabel.startsWith(lowerQuery)) {
            // Prefer shorter labels (more precise match)
            return 800 - (labelLen - queryLen);
        }

        // Word-boundary prefix (e.g. "log" matching "console.log")
        for (int i = 1; i < labelLen; i++) {
            char prev = label.charAt(i - 1);
            char curr = label.charAt(i);
            boolean isBoundary = prev == '.' || prev == '_' || prev == '-' || prev == ' '
                    || (Character.isLowerCase(prev) && Character.isUpperCase(curr));
            if (isBoundary && lowerLabel.startsWith(lowerQuery, i)) {
                return 600 - i; // Earlier boundary hit → higher score
            }
        }

        // Fuzzy subsequence with bonuses
        int qi = 0; // query index
        int score = 0;
        int consecutive = 0;
        int lastMatchIdx = -1;

        for (int li = 0; li < labelLen && qi < queryLen; li++) {
            if (lowerLabel.charAt(li) == lowerQuery.charAt(qi)) {
                // Boundary hit bonus
                boolean atBoundary = li == 0;
                if (!atBoundary && li > 0) {
                    char prev = label.charAt(li - 1);
                    char curr = label.charAt(li);
                    atBoundary = prev == '.' || prev == '_' || prev == '-' || prev == ' '
                            || (Character.isLowerCase(prev) && Character.isUpperCase(curr));
                }
                score += atBoundary ? 15 : 5;

                // Consecutive run bonus
                if (lastMatchIdx == li - 1) {
                    consecutive++;
                    score += 5 + consecutive; // accelerating bonus for longer runs
                } else {
                    consecutive = 0;
                    // Gap penalty
                    if (lastMatchIdx >= 0) {
                        score -= Math.min(3, (li - lastMatchIdx - 1));
                    }
                }

                lastMatchIdx = li;
                qi++;
            }
        }

        // Full query must be consumed (query is a subsequence of label)
        if (qi < queryLen) return 0;

        // Consecutive run bonus for long runs (≥2 consecutive chars)
        if (consecutive >= 1) score += consecutive * 3;

        return Math.max(1, score);
    }

    // Asset loading helpers

    /**
     * Standard utility to extract and parse string configuration data out of local JSON asset documents.
     */
    protected String loadAssetJson(String assetPath) {
        try (java.io.InputStream is = context.getAssets().open(assetPath);
             java.io.BufferedReader reader = new java.io.BufferedReader(
                     new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * Standard utility to extract raw text data out of local asset documents, preserving newlines.
     */
    protected String loadAssetText(String assetPath) {
        try (java.io.InputStream is = context.getAssets().open(assetPath);
             java.io.BufferedReader reader = new java.io.BufferedReader(
                     new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // Character classification

    /**
     * Validates whether a character qualifies as a standard part of code keywords,
     * inclusive of special developer variables like underscore, hyphens (CSS), or $ (JS).
     */
    protected boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '$';
    }
}