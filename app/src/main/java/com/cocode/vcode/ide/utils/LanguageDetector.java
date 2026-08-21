package com.cocode.vcode.ide.utils;

import com.cocode.vcode.ide.core.model.FileType;

import java.util.regex.Pattern;

/**
 * Heuristic detector that infers the {@link FileType} of code snippets based on structural patterns and keywords.
 */
public class LanguageDetector {

    private static final Pattern HTML_PATTERN = Pattern.compile("(?is).*<(!DOCTYPE|html|head|body|div|p|script|style|link|a|img|ul|li|span|br|h[1-6]).*>.*");
    private static final Pattern JS_KEYWORDS = Pattern.compile("(?s).*\\b(const|let|var|function|async|await|import|export|return|if|for|while|console\\.|document\\.|window\\.)\\b.*");
    private static final Pattern CSS_BLOCK = Pattern.compile("(?s).*[^{]+\\s*\\{\\s*[a-zA-Z-]+\\s*:\\s*[^;]+;?\\s*\\}.*");

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
        if (HTML_PATTERN.matcher(content).matches()) {
            return FileType.HTML;
        }

        // 2. Check for JSON root objects or arrays
        if ((content.startsWith("{") && content.endsWith("}")) || (content.startsWith("[") && content.endsWith("]"))) {
            return FileType.JSON;
        }

        // 3. Check for CSS rules or media queries
        if (CSS_BLOCK.matcher(content).matches() || content.contains("@media")) {
            return FileType.CSS;
        }

        // 4. Check for JavaScript or TypeScript keywords
        if (JS_KEYWORDS.matcher(content).matches() || content.contains("=>")) {
            if (content.contains("interface ") || content.contains("type ") || content.contains(" as ")) {
                return FileType.TYPESCRIPT;
            }
            return FileType.JAVASCRIPT;
        }

        return FileType.TEXT;
    }
}