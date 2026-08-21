package com.cocode.vcode.ide.core.editor.indent;

import com.cocode.vcode.ide.core.language.html.HtmlTagCache;
import com.cocode.vcode.ide.core.model.FileType;

/**
 * Engine calculating auto-indentation whitespace when a new line is inserted,
 * handling braces, brackets, and open HTML tags.
 */
public class IndentationEngine {
    private final String tabString;

    public IndentationEngine(int tabSize) {
        int tabSize1 = Math.max(1, tabSize);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tabSize1; i++) sb.append(" ");
        this.tabString = sb.toString();
    }

    /**
     * Calculates the leading indentation string for a new line inserted at the cursor position.
     */
    public String getIndentForNewLine(String text, int cursorPos, FileType lang) {
        if (text == null || cursorPos <= 0) return "";

        int minPos = Math.min(cursorPos, text.length());
        int lineStart = text.lastIndexOf('\n', minPos - 1);
        String currentLine = text.substring(lineStart + 1, minPos);

        String baseIndent = getLeadingWhitespace(currentLine);
        String trimmedLine = currentLine.trim();

        if (shouldIncreaseIndent(trimmedLine, lang)) {
            return baseIndent + getTabString();
        }

        return baseIndent;
    }

    public String getTabString() {
        return this.tabString;
    }

    private boolean shouldIncreaseIndent(String trimmedLine, FileType lang) {
        if (trimmedLine.isEmpty()) return false;
        char last = trimmedLine.charAt(trimmedLine.length() - 1);

        if (last == '{' || last == '(' || last == '[') return true;

        if (lang == FileType.HTML || lang == FileType.TEXT) {
            if (trimmedLine.endsWith(">") && !trimmedLine.contains("</")) {
                int openAngle = trimmedLine.lastIndexOf('<');
                if (openAngle >= 0) {
                    int spaceIdx = trimmedLine.indexOf(' ', openAngle);
                    int closeAngle = trimmedLine.indexOf('>', openAngle);
                    int endIdx = spaceIdx > -1 && spaceIdx < closeAngle ? spaceIdx : closeAngle;

                    if (endIdx > openAngle + 1) {
                        String tag = trimmedLine.substring(openAngle + 1, endIdx);
                        return !HtmlTagCache.isVoidElement(tag);
                    }
                }
            }
        }

        return false;
    }

    private String getLeadingWhitespace(String line) {
        if (line == null) return "";
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return i == 0 ? "" : line.substring(0, i);
    }
}