package com.cocode.vcode.ide.core.language.md;

/**
 * Lexical line scanner for Markdown documents.
 * Scans raw document lines into an {@link MdLineStream}, classifying line structural types
 * such as headings, list items, blockquotes, fenced code blocks, thematic breaks, and table rows
 * with zero-allocation character scanning.
 */
public final class MdLexer {

    public static MdLineStream lex(String source) {
        int len = source.length();
        // A document can't have more lines than its character count + 1
        MdLineStream stream = new MdLineStream(len + 1);
        
        int lineStart = 0;
        while (lineStart <= len) {
            int lineEnd = source.indexOf('\n', lineStart);
            if (lineEnd == -1) {
                lineEnd = len;
            }
            
            byte type = classifyLine(source, lineStart, lineEnd);
            int idx = stream.lineCount++;
            stream.lineTypes[idx] = type;
            stream.lineStartOffsets[idx] = lineStart;
            stream.lineEndOffsets[idx] = lineEnd;
            
            if (lineEnd == len) {
                break;
            }
            lineStart = lineEnd + 1;
        }
        
        return stream;
    }

    private static byte classifyLine(String source, int start, int end) {
        int i = start;
        // Skip leading spaces
        while (i < end && source.charAt(i) == ' ') {
            i++;
        }
        
        // Blank line
        if (i == end) {
            return MdLineStream.L_BLANK;
        }
        
        char c = source.charAt(i);
        
        // Header
        if (c == '#') {
            int hashes = 0;
            while (i < end && source.charAt(i) == '#') {
                hashes++;
                i++;
            }
            if (hashes <= 6 && (i == end || source.charAt(i) == ' ')) {
                return MdLineStream.L_HEADER;
            }
            return MdLineStream.L_PARAGRAPH;
        }
        
        // Blockquote
        if (c == '>') {
            return MdLineStream.L_BLOCKQUOTE;
        }
        
        // Code fence
        if (c == '`') {
            int backticks = 0;
            while (i < end && source.charAt(i) == '`') {
                backticks++;
                i++;
            }
            if (backticks >= 3) {
                return MdLineStream.L_CODE_FENCE;
            }
            return MdLineStream.L_PARAGRAPH;
        }
        
        // Thematic break OR unordered list item
        if (c == '-' || c == '*' || c == '_') {
            char marker = c;
            int count = 1;
            int j = i + 1;
            
            // Check thematic break (at least 3 markers, separated by optional spaces, nothing else)
            while (j < end) {
                char ch = source.charAt(j);
                if (ch == marker) {
                    count++;
                } else if (ch != ' ') {
                    break;
                }
                j++;
            }
            
            boolean onlyMarkersAndSpaces = true;
            for (int k = j; k < end; k++) {
                if (source.charAt(k) != ' ') {
                    onlyMarkersAndSpaces = false;
                    break;
                }
            }
            
            if (count >= 3 && onlyMarkersAndSpaces) {
                return MdLineStream.L_THEMATIC_BREAK;
            }
            
            // If not a thematic break, check for list item marker ('-' or '*')
            if ((c == '-' || c == '*') && i + 1 <= end) {
                if (i + 1 == end || source.charAt(i + 1) == ' ') {
                    return MdLineStream.L_LIST_ITEM;
                }
            }
            
            return MdLineStream.L_PARAGRAPH;
        }
        
        // Ordered list item
        if (c >= '0' && c <= '9') {
            int j = i;
            while (j < end && source.charAt(j) >= '0' && source.charAt(j) <= '9') {
                j++;
            }
            if (j < end && (source.charAt(j) == '.' || source.charAt(j) == ')')) {
                j++;
                if (j == end || source.charAt(j) == ' ') {
                    return MdLineStream.L_LIST_ITEM;
                }
            }
        }
        
        return MdLineStream.L_PARAGRAPH;
    }
}
