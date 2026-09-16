package com.cocode.vcode.ide.core.completion.staticdata;

import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON completion dispatcher.
 *
 * <p>Decides which static-completion subset (key snippets, value
 * snippets, full-file templates) is appropriate for the cursor's
 * current position inside a JSON document.
 *
 * <p>Position rules:
 * <ul>
 *   <li><b>Key position</b>: cursor sits immediately after
 *       a {@code {} of an object, or after a {@code ,} within an
 *       object. Offer key snippets: {@code "key: value"},
 *       {@code "key: object"}, etc.</li>
 *   <li><b>Value position</b>: cursor sits immediately after
 *       a {@code :}, after a {@code ,} within an array, or as the
 *       first non-whitespace token of the file. Offer structural
 *       snippets: {@code {}} and {@code []}.</li>
 *   <li><b>File-template position</b>: the file is empty
 *       or whitespace-only. Offer the full-file templates
 *       ({@code package.json}, {@code tsconfig.json}, etc.).
 *       When the open file's name matches a template's filename,
 *       that entry is boosted to the top of the list.</li>
 *   <li><b>None of the above</b>: return empty.</li>
 * </ul>
 */
public final class JsonStaticCompletionDispatcher {

    private static volatile List<CompletionItem> CACHED_KEYS = null;
    private static volatile List<CompletionItem> CACHED_VALUES = null;

    private JsonStaticCompletionDispatcher() {
    }

    /**
     * Detect the cursor's position within a JSON source.
     */
    public static Position detectPosition(String source, int cursor) {
        if (source == null) return Position.NONE;
        int len = source.length();
        if (cursor < 0 || cursor > len) return Position.NONE;
        // File empty / whitespace-only: FILE_EMPTY.
        boolean allWs = true;
        for (int i = 0; i < len; i++) {
            if (!Character.isWhitespace(source.charAt(i))) {
                allWs = false;
                break;
            }
        }
        if (allWs) return Position.FILE_EMPTY;

        // Skip current word or string literal being typed
        int i = cursor - 1;
        int quoteCount = 0;
        for (int k = 0; k < cursor; k++) {
            if (source.charAt(k) == '"' && (k == 0 || source.charAt(k - 1) != '\\')) {
                quoteCount++;
            }
        }
        boolean inQuotes = (quoteCount % 2 != 0);

        if (inQuotes) {
            while (i >= 0) {
                if (source.charAt(i) == '"' && (i == 0 || source.charAt(i - 1) != '\\')) {
                    i--;
                    break;
                }
                i--;
            }
        } else {
            while (i >= 0 && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_' || source.charAt(i) == '-')) {
                i--;
            }
        }

        while (i >= 0 && Character.isWhitespace(source.charAt(i))) i--;
        if (i < 0) return Position.KEY; // start of file → key
        char c = source.charAt(i);
        if (c == ':') return Position.VALUE;
        if (c == '[') return Position.VALUE;
        if (c == '{') return Position.KEY;
        if (c == ',') {
            int depth = 0;
            for (int j = i - 1; j >= 0; j--) {
                char cj = source.charAt(j);
                if (cj == '"' && (j == 0 || source.charAt(j - 1) != '\\')) {
                    j--;
                    while (j >= 0 && (source.charAt(j) != '"' || (j > 0 && source.charAt(j - 1) == '\\'))) {
                        j--;
                    }
                    continue;
                }
                if (cj == ']' || cj == '}') {
                    depth--;
                } else if (cj == '[') {
                    if (depth == 0) return Position.VALUE;
                    depth++;
                } else if (cj == '{') {
                    if (depth == 0) return Position.KEY;
                    depth++;
                }
            }
            return Position.KEY;
        }
        return Position.NONE;
    }

    public static void clearCachesForTest() {
        CACHED_KEYS = null;
        CACHED_VALUES = null;
    }

    /**
     * Build the static completion list for the cursor's current
     * position. The {@code currentFileName} argument is used by the
     * file template logic to boost specific filenames (e.g.
     * {@code package.json}) to the top of the list; pass
     * {@code null} if unknown.
     */
    public static List<CompletionItem> buildCompletions(Position position,
                                                        String currentFileName) {
        if (position == Position.KEY) {
            if (CACHED_KEYS == null) {
                List<CompletionItem> out = new ArrayList<>();
                for (StaticCompletionItem k : StaticCompletionLoader.getJsonSnippets()) {
                    if (k.label.startsWith("key:")) {
                        out.add(buildFromStaticItem(k));
                    }
                }
                CACHED_KEYS = out;
            }
            return new ArrayList<>(CACHED_KEYS);
        }
        if (position == Position.VALUE) {
            if (CACHED_VALUES == null) {
                List<CompletionItem> out = new ArrayList<>();
                for (StaticCompletionItem k : StaticCompletionLoader.getJsonSnippets()) {
                    if ("{}".equals(k.label) || "[]".equals(k.label)) {
                        out.add(buildFromStaticItem(k));
                    }
                }
                CACHED_VALUES = out;
            }
            return new ArrayList<>(CACHED_VALUES);
        }
        if (position == Position.FILE_EMPTY) {
            List<CompletionItem> boosted = new ArrayList<>();
            List<CompletionItem> normal = new ArrayList<>();
            for (StaticCompletionItem k : StaticCompletionLoader.getJsonSnippets()) {
                if (k.label.contains(".")) {
                    CompletionItem ci = buildFromStaticItem(k);
                    if (currentFileName != null && currentFileName.equalsIgnoreCase(k.label)) {
                        ci.setSortScore(1000);
                        boosted.add(ci);
                    } else {
                        normal.add(ci);
                    }
                }
            }
            List<CompletionItem> out = new ArrayList<>();
            out.addAll(boosted);
            out.addAll(normal);
            return out;
        }
        return new ArrayList<>();
    }

    private static CompletionItem buildFromStaticItem(StaticCompletionItem k) {
        SnippetCursorParser.Result r = SnippetCursorParser.parse(k.insertText);
        String insertText = r != null ? r.insertText : k.insertText;
        int cursorOffset = r != null ? r.cursorOffset : 0;
        return new CompletionItem(k.label, insertText, k.detail,
                CompletionItem.Type.SNIPPET, cursorOffset);
    }

    public enum Position {
        KEY,
        VALUE,
        FILE_EMPTY,
        NONE
    }
}
