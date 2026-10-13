package com.cocode.vcode.ide.core.language.md;

import java.util.Locale;

/**
 * Zero-allocation, high-performance lexical context scanner for Markdown documents.
 * Isolates grammatical contexts according to the LSP Completion Master Specification:
 * <ul>
 *   <li><b>CODE_BLOCK_LANGUAGE</b>: Directly following triple backtick (``` or ~~~) on opening fence line</li>
 *   <li><b>EMBEDDED_CODE_BLOCK</b>: Inside a fenced code block body between opening and closing fences</li>
 *   <li><b>LINK_FILE_PATH</b>: Inside {@code [text](path|)} or {@code ![alt](path|)}</li>
 *   <li><b>HEADING_ANCHOR</b>: Inside {@code [text](#anchor|)} or {@code ![alt](#anchor|)}</li>
 *   <li><b>STRUCTURAL_SNIPPET</b>: At beginning of line or after indentation</li>
 *   <li><b>INLINE_SNIPPET</b>: In prose/body text</li>
 * </ul>
 */
public final class MarkdownContextScanner {

    public enum ContextType {
        CODE_BLOCK_LANGUAGE,
        EMBEDDED_CODE_BLOCK,
        LINK_FILE_PATH,
        HEADING_ANCHOR,
        STRUCTURAL_SNIPPET,
        INLINE_SNIPPET,
        NONE
    }

    public static final class MarkdownContext {
        public final ContextType type;
        public final String query;
        public final int replaceLength;
        public final String language;
        public final int blockStartOffset;
        public final int blockEndOffset;
        public final int lineStart;
        public final String targetFilePath;
        public final boolean isWikilink;

        public MarkdownContext(ContextType type, String query, int replaceLength,
                               String language, int blockStartOffset, int blockEndOffset, int lineStart) {
            this(type, query, replaceLength, language, blockStartOffset, blockEndOffset, lineStart, null, false);
        }

        public MarkdownContext(ContextType type, String query, int replaceLength,
                               String language, int blockStartOffset, int blockEndOffset, int lineStart,
                               String targetFilePath, boolean isWikilink) {
            this.type = type;
            this.query = query != null ? query : "";
            this.replaceLength = replaceLength;
            this.language = language;
            this.blockStartOffset = blockStartOffset;
            this.blockEndOffset = blockEndOffset;
            this.lineStart = lineStart;
            this.targetFilePath = targetFilePath;
            this.isWikilink = isWikilink;
        }
    }

    private MarkdownContextScanner() {
    }

    /**
     * Scans the Markdown document up to and around {@code cursorPos} to determine the active grammatical context.
     *
     * @param text      full Markdown document text
     * @param cursorPos 0-based cursor offset
     * @return resolved {@link MarkdownContext}
     */
    public static MarkdownContext scan(String text, int cursorPos) {
        if (text == null || cursorPos < 0 || cursorPos > text.length()) {
            return new MarkdownContext(ContextType.NONE, "", 0, null, 0, 0, 0);
        }

        int len = text.length();

        // 1. Scan code fences across the document to determine if cursor is in or on a fence
        int i = 0;
        boolean inFence = false;
        char fenceChar = 0;
        int fenceLen = 0;
        String fenceLang = null;
        int activeBlockStart = -1;
        int activeFenceLineStart = -1;

        while (i < len) {
            int lineStart = i;
            while (i < len && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
                i++;
            }
            int lineEnd = i;
            if (i < len && text.charAt(i) == '\r') i++;
            if (i < len && text.charAt(i) == '\n') i++;

            // Check leading indentation (CommonMark allows up to 3 spaces for fences)
            int k = lineStart;
            int leadingSpaces = 0;
            while (k < lineEnd && leadingSpaces < 4 && (text.charAt(k) == ' ' || text.charAt(k) == '\t')) {
                leadingSpaces++;
                k++;
            }

            if (leadingSpaces < 4 && k < lineEnd) {
                char c = text.charAt(k);
                if (c == '`' || c == '~') {
                    int run = 0;
                    while (k + run < lineEnd && text.charAt(k + run) == c) {
                        run++;
                    }
                    if (run >= 3) {
                        if (!inFence) {
                            // Opening fence line
                            int langStart = k + run;
                            while (langStart < lineEnd && (text.charAt(langStart) == ' ' || text.charAt(langStart) == '\t')) {
                                langStart++;
                            }
                            int langEnd = langStart;
                            while (langEnd < lineEnd && !Character.isWhitespace(text.charAt(langEnd))) {
                                langEnd++;
                            }
                            String parsedLang = (langEnd > langStart) ? text.substring(langStart, langEnd).toLowerCase() : "";

                            if (cursorPos >= lineStart && cursorPos <= lineEnd) {
                                int fenceBoundary = k + run;
                                if (cursorPos >= fenceBoundary) {
                                    // Cursor is right after ``` or inside/after the language name on opening fence
                                    String query = (cursorPos >= langStart) ? text.substring(langStart, cursorPos).trim().toLowerCase() : "";
                                    int replaceLen = (cursorPos >= langStart) ? (cursorPos - langStart) : 0;
                                    return new MarkdownContext(ContextType.CODE_BLOCK_LANGUAGE, query, replaceLen, parsedLang, -1, -1, lineStart);
                                }
                            }

                            inFence = true;
                            fenceChar = c;
                            fenceLen = run;
                            fenceLang = parsedLang;
                            activeBlockStart = i; // body starts after newline
                            activeFenceLineStart = lineStart;
                            continue;
                        } else {
                            // Potential closing fence if matching character and count >= opening run
                            if (c == fenceChar && run >= fenceLen) {
                                int rest = k + run;
                                while (rest < lineEnd && (text.charAt(rest) == ' ' || text.charAt(rest) == '\t')) {
                                    rest++;
                                }
                                if (rest >= lineEnd) {
                                    // Valid closing fence
                                    if (cursorPos >= lineStart && cursorPos <= lineEnd) {
                                        return new MarkdownContext(ContextType.NONE, "", 0, null, 0, 0, lineStart);
                                    }
                                    if (cursorPos < lineStart && inFence && cursorPos >= activeBlockStart) {
                                        // Cursor was inside this block body
                                        return new MarkdownContext(ContextType.EMBEDDED_CODE_BLOCK, "", 0, fenceLang,
                                                activeBlockStart, lineStart, activeFenceLineStart);
                                    }
                                    inFence = false;
                                    fenceChar = 0;
                                    fenceLen = 0;
                                    fenceLang = null;
                                    activeBlockStart = -1;
                                    continue;
                                }
                            }
                        }
                    }
                }
            }

            // Cursor falls inside the code block body
            if (inFence && cursorPos >= lineStart && cursorPos <= lineEnd) {
                // Find next closing fence or EOF to determine blockEndOffset
                int blockEnd = findClosingFence(text, i, fenceChar, fenceLen);
                return new MarkdownContext(ContextType.EMBEDDED_CODE_BLOCK, "", 0, fenceLang,
                        activeBlockStart, blockEnd, lineStart);
            }
        }

        if (inFence && cursorPos >= activeBlockStart) {
            return new MarkdownContext(ContextType.EMBEDDED_CODE_BLOCK, "", 0, fenceLang,
                    activeBlockStart, len, activeFenceLineStart);
        }

        // 2. We are outside code blocks: find the line boundaries containing cursorPos
        int curLineStart = cursorPos - 1;
        while (curLineStart >= 0 && text.charAt(curLineStart) != '\n' && text.charAt(curLineStart) != '\r') {
            curLineStart--;
        }
        curLineStart++;

        int curLineEnd = cursorPos;
        while (curLineEnd < len && text.charAt(curLineEnd) != '\n' && text.charAt(curLineEnd) != '\r') {
            curLineEnd++;
        }

        // Case A: Inside link or image destination: [text](...cursor...) or ![alt](...cursor...)
        int parenPos = -1;
        int p = cursorPos - 1;
        while (p >= curLineStart) {
            char ch = text.charAt(p);
            if (ch == ')') {
                boolean escaped = isEscaped(text, p);
                if (!escaped) {
                    // Closed destination before cursor
                    break;
                }
            }
            if (ch == '(') {
                boolean escaped = isEscaped(text, p);
                if (!escaped) {
                    // Check if preceded by ']'
                    int j = p - 1;
                    while (j >= curLineStart && (text.charAt(j) == ' ' || text.charAt(j) == '\t')) {
                        j--;
                    }
                    if (j >= curLineStart && text.charAt(j) == ']' && !isEscaped(text, j)) {
                        parenPos = p;
                    }
                    break;
                }
            }
            p--;
        }

        if (parenPos != -1) {
            String destPrefix = text.substring(parenPos + 1, cursorPos);
            int lastHashInDest = -1;
            for (int d = 0; d < destPrefix.length(); d++) {
                if (destPrefix.charAt(d) == '#' && !isEscaped(destPrefix, d)) {
                    lastHashInDest = d;
                }
            }

            if (lastHashInDest != -1) {
                int hashPos = parenPos + 1 + lastHashInDest;
                String rawTarget = destPrefix.substring(0, lastHashInDest).trim();
                if (rawTarget.startsWith("<") && rawTarget.endsWith(">")) {
                    rawTarget = rawTarget.substring(1, rawTarget.length() - 1).trim();
                } else if (rawTarget.startsWith("<")) {
                    rawTarget = rawTarget.substring(1).trim();
                }

                boolean isExternalWeb = rawTarget.startsWith("http://") || rawTarget.startsWith("https://")
                        || rawTarget.startsWith("mailto:") || rawTarget.startsWith("ftp://");
                if (!isExternalWeb) {
                    String targetFilePath = rawTarget.isEmpty() ? null : rawTarget;
                    String anchorQuery = destPrefix.substring(lastHashInDest + 1).trim().toLowerCase(Locale.ROOT);
                    int replaceLen = cursorPos - (hashPos + 1);
                    return new MarkdownContext(ContextType.HEADING_ANCHOR, anchorQuery, replaceLen,
                            null, -1, -1, curLineStart, targetFilePath, false);
                }
            }

            // Link File Path destination: [text](path...|
            String pathQuery = destPrefix.trim();
            if (pathQuery.startsWith("<")) pathQuery = pathQuery.substring(1).trim();
            int lastSlash = pathQuery.lastIndexOf('/');
            String filterPrefix = (lastSlash != -1) ? pathQuery.substring(lastSlash + 1) : pathQuery;
            return new MarkdownContext(ContextType.LINK_FILE_PATH, filterPrefix, filterPrefix.length(), pathQuery, -1, -1, curLineStart);
        }

        // Case B: Inside wikilink: [[...cursor...]]
        int wikiOpenPos = -1;
        int w = cursorPos - 1;
        while (w > curLineStart) {
            if (text.charAt(w) == ']' && text.charAt(w - 1) == ']' && !isEscaped(text, w - 1)) {
                // Closed wikilink before cursor
                break;
            }
            if (text.charAt(w) == '[' && text.charAt(w - 1) == '[' && !isEscaped(text, w - 1)) {
                wikiOpenPos = w - 1;
                break;
            }
            w--;
        }

        if (wikiOpenPos != -1) {
            String wikiContent = text.substring(wikiOpenPos + 2, cursorPos);
            int pipePos = wikiContent.indexOf('|');
            if (pipePos == -1) {
                int lastHashInWiki = -1;
                for (int d = 0; d < wikiContent.length(); d++) {
                    if (wikiContent.charAt(d) == '#' && !isEscaped(wikiContent, d)) {
                        lastHashInWiki = d;
                    }
                }

                if (lastHashInWiki != -1) {
                    int hashPos = wikiOpenPos + 2 + lastHashInWiki;
                    String rawTarget = wikiContent.substring(0, lastHashInWiki).trim();
                    String targetFilePath = rawTarget.isEmpty() ? null : rawTarget;
                    String anchorQuery = wikiContent.substring(lastHashInWiki + 1).trim().toLowerCase(Locale.ROOT);
                    int replaceLen = cursorPos - (hashPos + 1);
                    return new MarkdownContext(ContextType.HEADING_ANCHOR, anchorQuery, replaceLen,
                            null, -1, -1, curLineStart, targetFilePath, true);
                } else {
                    String pathQuery = wikiContent.trim();
                    return new MarkdownContext(ContextType.LINK_FILE_PATH, pathQuery, pathQuery.length(),
                            pathQuery, -1, -1, curLineStart, null, true);
                }
            }
        }

        // Case C: Reference-style link definition: [id]: ...cursor...
        int lineNonWs = curLineStart;
        while (lineNonWs < cursorPos && (text.charAt(lineNonWs) == ' ' || text.charAt(lineNonWs) == '\t')) {
            lineNonWs++;
        }
        if (lineNonWs < cursorPos && text.charAt(lineNonWs) == '[') {
            int closeBracket = -1;
            for (int b = lineNonWs + 1; b < curLineEnd; b++) {
                if (text.charAt(b) == ']' && !isEscaped(text, b)) {
                    closeBracket = b;
                    break;
                }
            }
            if (closeBracket != -1) {
                int colonPos = closeBracket + 1;
                while (colonPos < curLineEnd && (text.charAt(colonPos) == ' ' || text.charAt(colonPos) == '\t')) {
                    colonPos++;
                }
                if (colonPos < curLineEnd && text.charAt(colonPos) == ':') {
                    int destStart = colonPos + 1;
                    while (destStart < curLineEnd && (text.charAt(destStart) == ' ' || text.charAt(destStart) == '\t')) {
                        destStart++;
                    }
                    if (cursorPos >= destStart) {
                        String refDest = text.substring(destStart, cursorPos);
                        int lastHashInRef = -1;
                        for (int d = 0; d < refDest.length(); d++) {
                            if (refDest.charAt(d) == '#' && !isEscaped(refDest, d)) {
                                lastHashInRef = d;
                            }
                        }

                        if (lastHashInRef != -1) {
                            int hashPos = destStart + lastHashInRef;
                            String rawTarget = refDest.substring(0, lastHashInRef).trim();
                            if (rawTarget.startsWith("<") && rawTarget.endsWith(">")) {
                                rawTarget = rawTarget.substring(1, rawTarget.length() - 1).trim();
                            } else if (rawTarget.startsWith("<")) {
                                rawTarget = rawTarget.substring(1).trim();
                            }

                            boolean isExternalWeb = rawTarget.startsWith("http://") || rawTarget.startsWith("https://")
                                    || rawTarget.startsWith("mailto:") || rawTarget.startsWith("ftp://");
                            if (!isExternalWeb) {
                                String targetFilePath = rawTarget.isEmpty() ? null : rawTarget;
                                String anchorQuery = refDest.substring(lastHashInRef + 1).trim().toLowerCase(Locale.ROOT);
                                int replaceLen = cursorPos - (hashPos + 1);
                                return new MarkdownContext(ContextType.HEADING_ANCHOR, anchorQuery, replaceLen,
                                        null, -1, -1, curLineStart, targetFilePath, false);
                            }
                        } else if (!refDest.trim().isEmpty()) {
                            String pathQuery = refDest.trim();
                            if (pathQuery.startsWith("<")) pathQuery = pathQuery.substring(1).trim();
                            int lastSlash = pathQuery.lastIndexOf('/');
                            String filterPrefix = (lastSlash != -1) ? pathQuery.substring(lastSlash + 1) : pathQuery;
                            return new MarkdownContext(ContextType.LINK_FILE_PATH, filterPrefix, filterPrefix.length(),
                                    pathQuery, -1, -1, curLineStart);
                        }
                    }
                }
            }
        }

        // 3. Structural Snippet vs Inline Snippet
        int nonWs = curLineStart;
        while (nonWs < cursorPos && (text.charAt(nonWs) == ' ' || text.charAt(nonWs) == '\t')) {
            nonWs++;
        }
        String linePrefix = text.substring(nonWs, cursorPos);
        boolean isPunctuationLineTrigger = !linePrefix.isEmpty() &&
                (linePrefix.startsWith("#") || linePrefix.startsWith("`") || linePrefix.startsWith("-")
                        || linePrefix.startsWith(">") || linePrefix.startsWith("1.") || linePrefix.startsWith("|")
                        || linePrefix.startsWith("---"));

        String word = extractWordBeforeCursor(text, cursorPos);

        if (nonWs == cursorPos) {
            // Empty line or cursor directly after indentation
            return new MarkdownContext(ContextType.STRUCTURAL_SNIPPET, "", 0, null, -1, -1, curLineStart);
        } else if (isPunctuationLineTrigger) {
            return new MarkdownContext(ContextType.STRUCTURAL_SNIPPET, linePrefix.toLowerCase(), linePrefix.length(), null, -1, -1, curLineStart);
        } else if (nonWs == cursorPos - word.length()) {
            // First word on the line
            return new MarkdownContext(ContextType.STRUCTURAL_SNIPPET, word.toLowerCase(), word.length(), null, -1, -1, curLineStart);
        } else {
            // Middle of prose text -> Inline formatting snippets only if word is non-empty
            if (word.isEmpty()) {
                return new MarkdownContext(ContextType.NONE, "", 0, null, -1, -1, curLineStart);
            }
            return new MarkdownContext(ContextType.INLINE_SNIPPET, word.toLowerCase(), word.length(), null, -1, -1, curLineStart);
        }
    }

    private static int findClosingFence(String text, int fromOffset, char fenceChar, int fenceLen) {
        int len = text.length();
        int i = fromOffset;
        while (i < len) {
            int lineStart = i;
            while (i < len && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
                i++;
            }
            int lineEnd = i;
            if (i < len && text.charAt(i) == '\r') i++;
            if (i < len && text.charAt(i) == '\n') i++;

            int k = lineStart;
            int leadingSpaces = 0;
            while (k < lineEnd && leadingSpaces < 4 && (text.charAt(k) == ' ' || text.charAt(k) == '\t')) {
                leadingSpaces++;
                k++;
            }
            if (leadingSpaces < 4 && k < lineEnd && text.charAt(k) == fenceChar) {
                int run = 0;
                while (k + run < lineEnd && text.charAt(k + run) == fenceChar) {
                    run++;
                }
                if (run >= fenceLen) {
                    int rest = k + run;
                    while (rest < lineEnd && (text.charAt(rest) == ' ' || text.charAt(rest) == '\t')) {
                        rest++;
                    }
                    if (rest >= lineEnd) {
                        return lineStart;
                    }
                }
            }
        }
        return len;
    }

    private static boolean isEscaped(String text, int pos) {
        int count = 0;
        int i = pos - 1;
        while (i >= 0 && text.charAt(i) == '\\') {
            count++;
            i--;
        }
        return (count % 2) != 0;
    }

    private static String extractWordBeforeCursor(String text, int pos) {
        if (text == null || pos <= 0 || pos > text.length()) return "";
        int start = pos - 1;
        while (start >= 0) {
            char c = text.charAt(start);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '*' || c == '`' || c == '~') {
                start--;
            } else {
                break;
            }
        }
        return text.substring(start + 1, pos);
    }
}
