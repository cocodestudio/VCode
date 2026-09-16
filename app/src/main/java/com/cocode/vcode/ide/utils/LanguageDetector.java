package com.cocode.vcode.ide.utils;

import com.cocode.vcode.ide.core.model.FileType;

/**
 * Heuristic detector that infers the {@link FileType} of code snippets based on structural patterns and keywords.
 */
public class LanguageDetector {

    private static final String[] JS_WORDS = {
            "const ", "let ", "var ", "function ", "async ", "await ", "import ", "export ",
            "return ", "if (", "if(", "for (", "for(", "while (", "while(",
            "console.", "document.", "window."
    };

    /**
     * Detects the file type of the given source code snippet.
     *
     * @param code the source code to analyze
     * @return the detected {@link FileType}, defaulting to {@link FileType#TEXT} if undetermined
     */
    public static FileType detect(String code) {
        if (code == null || code.trim().isEmpty()) return FileType.TEXT;

        String content = code.trim();

        // 1. Check for HTML elements
        if (isHtml(content)) {
            return FileType.HTML;
        }

        // 2. Check for JSON root objects or arrays
        if ((content.startsWith("{") && content.endsWith("}")) || (content.startsWith("[") && content.endsWith("]"))) {
            return FileType.JSON;
        }

        // 3. Check for CSS rules or media queries
        if (isCss(content)) {
            return FileType.CSS;
        }

        // 4. Check for JavaScript or TypeScript keywords
        if (isJs(content)) {
            if (content.contains("interface ") || content.contains("type ") || content.contains(" as ")) {
                return FileType.TYPESCRIPT;
            }
            return FileType.JAVASCRIPT;
        }

        return FileType.TEXT;
    }

    private static boolean isHtml(String content) {
        if (content.indexOf('<') == -1) return false;
        String lower = content.toLowerCase();
        return lower.contains("<!doctype") || lower.contains("<html") || lower.contains("<head")
                || lower.contains("<body") || lower.contains("<div") || lower.contains("<script")
                || lower.contains("<style") || lower.contains("<link") || lower.contains("<span")
                || lower.contains("<p>") || lower.contains("<p ") || lower.contains("<ul>")
                || lower.contains("<ul ") || lower.contains("<li>") || lower.contains("<li ")
                || lower.contains("<a>") || lower.contains("<a ") || lower.contains("<img")
                || lower.contains("<h1>") || lower.contains("<h2>") || lower.contains("<h3>");
    }

    private static boolean isCss(String content) {
        if (content.contains("@media") || content.contains("@import") || content.contains("@keyframes"))
            return true;
        int openBrace = content.indexOf('{');
        int closeBrace = content.indexOf('}', openBrace + 1);
        if (openBrace != -1 && closeBrace != -1) {
            String inside = content.substring(openBrace + 1, closeBrace);
            return inside.indexOf(':') != -1;
        }
        return false;
    }

    private static boolean isJs(String content) {
        if (content.contains("=>")) return true;
        for (String kw : JS_WORDS) {
            if (content.contains(kw)) return true;
        }
        return false;
    }
}