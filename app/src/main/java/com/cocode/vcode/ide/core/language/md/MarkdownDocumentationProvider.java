package com.cocode.vcode.ide.core.language.md;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Documentation provider for Markdown syntax elements: links, images, headings, and code blocks.
 */
public final class MarkdownDocumentationProvider {

    private static final Pattern LINK_PATTERN = Pattern.compile("(?<!!)\\[([^\\]]+)\\]\\(([^)]+)\\)");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);

    private MarkdownDocumentationProvider() {}

    /**
     * Resolves hover documentation for markdown syntax at the specified offset.
     */
    public static String getMarkdownDoc(String text, int offset) {
        if (text == null || offset < 0 || offset > text.length()) return null;

        // 1. Check Image ![alt](url)
        Matcher imgMatcher = IMAGE_PATTERN.matcher(text);
        while (imgMatcher.find()) {
            if (offset >= imgMatcher.start() && offset <= imgMatcher.end()) {
                String alt = imgMatcher.group(1);
                String url = imgMatcher.group(2);
                StringBuilder sb = new StringBuilder("### Markdown Image\n\n");
                sb.append("**Source:** `").append(url).append("`\n\n");
                if (alt != null && !alt.isEmpty()) {
                    sb.append("**Alt Text:** ").append(alt).append("\n\n");
                }
                return sb.toString().trim();
            }
        }

        // 2. Check Link [text](url)
        Matcher linkMatcher = LINK_PATTERN.matcher(text);
        while (linkMatcher.find()) {
            if (offset >= linkMatcher.start() && offset <= linkMatcher.end()) {
                String label = linkMatcher.group(1);
                String url = linkMatcher.group(2);
                StringBuilder sb = new StringBuilder("### Markdown Link\n\n");
                sb.append("**Target:** `").append(url).append("`\n\n");
                sb.append("**Text:** ").append(label).append("\n\n");
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    sb.append("[Open Link](").append(url).append(")");
                }
                return sb.toString().trim();
            }
        }

        // 3. Check Headings (# Title)
        // Find line start and end
        int lineStart = offset;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
        int lineEnd = offset;
        while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') lineEnd++;

        String line = text.substring(lineStart, lineEnd).trim();
        if (line.startsWith("#")) {
            int level = 0;
            while (level < line.length() && line.charAt(level) == '#') level++;
            if (level <= 6 && level < line.length() && Character.isWhitespace(line.charAt(level))) {
                String title = line.substring(level).trim();
                return "### Heading " + level + "\n\n" + title + "\n\n**Anchor:** `#"
                        + title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "") + "`";
            }
        }

        // 4. Check Code Fences (```lang)
        if (line.startsWith("```")) {
            String lang = line.substring(3).trim();
            return "### Code Block\n\n" + (lang.isEmpty() ? "Fenced code block" : "Language: `" + lang + "`");
        }

        // 5. Blockquote (> quote)
        if (line.startsWith(">")) {
            return "### Blockquote\n\nIndicates that the enclosed text is an extended quotation.";
        }

        return null;
    }
}
