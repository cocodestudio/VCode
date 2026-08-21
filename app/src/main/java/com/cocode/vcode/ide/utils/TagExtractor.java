package com.cocode.vcode.ide.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for extracting inline {@code <style>} or {@code <script>} tags from HTML documents
 * into separate external files and replacing them with {@code <link>} or external {@code <script>} references.
 */
public class TagExtractor {

    public static class Result {
        public boolean success;
        public String extractedContent;
        public String modifiedHtml;
        public String errorMessage;

        public Result(boolean success, String extractedContent, String modifiedHtml, String errorMessage) {
            this.success = success;
            this.extractedContent = extractedContent;
            this.modifiedHtml = modifiedHtml;
            this.errorMessage = errorMessage;
        }
    }

    public enum Type {
        STYLE, SCRIPT
    }

    // Matches <style ...>...</style> non-greedily, allowing DOTALL to match newlines
    private static final Pattern STYLE_PATTERN = Pattern.compile("<style[^>]*>(.*?)</style>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    
    // Matches <script ...>...</script> non-greedily. 
    // We want to exclude scripts that have a 'src=' attribute, as they are already external.
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("<script([^>]*)>(.*?)</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /**
     * Extracts styles or scripts from the given HTML.
     * 
     * @param html The original HTML content.
     * @param type What to extract (STYLE or SCRIPT).
     * @param targetFilename The name of the file being extracted to (to insert the correct link/src tag).
     * @return Result containing extracted text and modified HTML.
     */
    public static Result extract(String html, Type type, String targetFilename) {
        if (html == null || html.isEmpty()) {
            return new Result(false, null, null, "HTML is empty.");
        }

        Pattern pattern = (type == Type.STYLE) ? STYLE_PATTERN : SCRIPT_PATTERN;
        Matcher matcher = pattern.matcher(html);
        
        StringBuilder extracted = new StringBuilder();
        StringBuffer modifiedHtml = new StringBuffer();
        boolean firstMatch = true;
        boolean foundAny = false;

        while (matcher.find()) {
            if (type == Type.SCRIPT) {
                String attrs = matcher.group(1);
                if (attrs != null && attrs.toLowerCase().contains("src=")) {
                    // Skip external scripts
                    matcher.appendReplacement(modifiedHtml, Matcher.quoteReplacement(matcher.group(0)));
                    continue;
                }
            }
            
            String content = (type == Type.STYLE) ? matcher.group(1) : matcher.group(2);
            if (content != null && !content.trim().isEmpty()) {
                if (extracted.length() > 0) {
                    extracted.append("\n\n");
                }
                extracted.append(content.trim());
            }
            foundAny = true;

            if (firstMatch) {
                // Inject the link or script tag at the site of the first match
                String replacementTag = "";
                if (type == Type.STYLE) {
                    replacementTag = "<link rel=\"stylesheet\" href=\"" + targetFilename + "\">\n";
                } else {
                    replacementTag = "<script src=\"" + targetFilename + "\"></script>\n";
                }
                matcher.appendReplacement(modifiedHtml, Matcher.quoteReplacement(replacementTag));
                firstMatch = false;
            } else {
                // For subsequent matches, just remove them entirely
                matcher.appendReplacement(modifiedHtml, "");
            }
        }
        
        matcher.appendTail(modifiedHtml);

        if (!foundAny) {
            return new Result(false, null, null, "No " + (type == Type.STYLE ? "<style>" : "inline <script>") + " tags found.");
        }

        return new Result(true, extracted.toString(), modifiedHtml.toString(), null);
    }
}
