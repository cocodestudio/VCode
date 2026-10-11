package com.cocode.vcode.ide.core.language.md;

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

        public MarkdownContext(ContextType type, String query, int replaceLength,
                               String language, int blockStartOffset, int blockEndOffset, int lineStart) {
            this.type = type;
            this.query = query != null ? query : "";
            this.replaceLength = replaceLength;
            this.language = language;
            this.blockStartOffset = blockStartOffset;
            this.blockEndOffset = blockEndOffset;
            this.lineStart = lineStart;
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

        // Check if inside link or image destination: [text](...cursor...) or ![alt](...cursor...)
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
            int trimStart = 0;
            while (trimStart < destPrefix.length() && (destPrefix.charAt(trimStart) == ' ' || destPrefix.charAt(trimStart) == '\t')) {
                trimStart++;
            }
            if (trimStart < destPrefix.length() && destPrefix.charAt(trimStart) == '#') {
                // Heading Anchor destination: [text](#...|
                int hashPos = parenPos + 1 + trimStart;
                String anchorQuery = text.substring(hashPos + 1, cursorPos).trim().toLowerCase();
                int replaceLen = cursorPos - (hashPos + 1);
                return new MarkdownContext(ContextType.HEADING_ANCHOR, anchorQuery, replaceLen, null, -1, -1, curLineStart);
            } else {
                // Link File Path destination: [text](path...|
                String pathQuery = destPrefix.trim();
                int lastSlash = pathQuery.lastIndexOf('/');
                String filterPrefix = (lastSlash != -1) ? pathQuery.substring(lastSlash + 1) : pathQuery;
                return new MarkdownContext(ContextType.LINK_FILE_PATH, filterPrefix, filterPrefix.length(), null, -1, -1, curLineStart);
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
            // Middle of prose text -> Inline formatting snippets only
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
