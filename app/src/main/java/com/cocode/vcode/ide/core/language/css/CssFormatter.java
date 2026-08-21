package com.cocode.vcode.ide.core.language.css;

import com.cocode.vcode.ide.core.language.base.BaseFormatter;

import java.util.regex.Pattern;

/**
 * Formatter for CSS stylesheets, enforcing consistent indentation and block spacing.
 */
public class CssFormatter extends BaseFormatter {

    private static final Pattern MULTI_NL = Pattern.compile("\\n{3,}");

    @Override
    public String format(String code) {
        if (code == null || code.isEmpty()) return "";

        // Normalise line endings and collapse horizontal whitespace runs
        code = code.replace("\r\n", "\n").replace("\r", "\n");

        StringBuilder out = new StringBuilder(code.length() + code.length() / 4);
        int depth = 0;
        boolean inString = false;
        char stringChar = 0;
        boolean inComment = false;

        // We do a two-pass approach:
        // Pass 1 — normalise into a canonical single-line stream with clear delimiters
        // Pass 2 — re-indent the stream
        // This is cleaner than per-char state + indent tracking simultaneously.

    // Pass 1: produce a normalised token stream
        StringBuilder norm = new StringBuilder();
        int braceDepth = 0; // tracks whether cursor is inside a rule block { }
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);

            // Block comments
            if (inComment) {
                norm.append(c);
                if (c == '*' && i + 1 < code.length() && code.charAt(i + 1) == '/') {
                    norm.append('/');
                    i++;
                    norm.append('\n');
                    inComment = false;
                }
                continue;
            }
            if (c == '/' && i + 1 < code.length() && code.charAt(i + 1) == '*') {
                norm.append("/*");
                i++;
                inComment = true;
                continue;
            }

            // Strings
            if (inString) {
                norm.append(c);
                if (c == stringChar && (i == 0 || code.charAt(i - 1) != '\\')) inString = false;
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                stringChar = c;
                norm.append(c);
                continue;
            }

            // Normalise whitespace to single space
            if (c == '\t' || c == '\r') {
                norm.append(' ');
                continue;
            }
            if (c == '\n') {
                // Collapse newlines: keep at most one
                if (norm.length() > 0 && norm.charAt(norm.length() - 1) != '\n') norm.append('\n');
                continue;
            }
            // Collapse multiple spaces to one
            if (c == ' ' && norm.length() > 0 && norm.charAt(norm.length() - 1) == ' ') continue;

            // Track brace depth so we know if we're inside a rule block
            if (c == '{') {
                while (norm.length() > 0 && norm.charAt(norm.length() - 1) == ' ') {
                    norm.deleteCharAt(norm.length() - 1);
                }
                norm.append(" {\n");
                braceDepth++;
                continue;
            }
            if (c == '}') {
                norm.append("\n}\n\n");
                if (braceDepth > 0) braceDepth--;
                continue;
            }
            if (c == ';') {
                norm.append(";\n");
                continue;
            }
            if (c == ':') {
                // Double colon pseudo-element (::before, ::after, ::root, etc.) — emit as-is
                if (i + 1 < code.length() && code.charAt(i + 1) == ':') {
                    norm.append("::");
                    i++;
                    continue;
                }
                // Single colon: only add space when inside a rule block (property: value)
                // Outside a block it's a pseudo-class selector (:hover, :nth-child, etc.)
                if (braceDepth > 0) {
                    norm.append(": ");
                    while (i + 1 < code.length() && code.charAt(i + 1) == ' ') i++;
                } else {
                    norm.append(':');
                }
                continue;
            }
            if (c == ',') {
                // Inside a selector list: comma + newline. Inside a value (e.g. rgb()): comma + space.
                // We detect value context by checking if the next non-space char is a digit/letter that
                // looks like a function argument. Simple heuristic: if there's no '{' or '}' between
                // current position and the next ';', we're in a value.
                norm.append(", ");
                continue;
            }
            norm.append(c);
        }

    // Pass 2: re-indent the normalised stream
        String[] lines = norm.toString().split("\n", -1);
        boolean lastWasBlank = false;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                if (!lastWasBlank && out.length() > 0) {
                    out.append('\n');
                    lastWasBlank = true;
                }
                continue;
            }
            lastWasBlank = false;

            // Closing brace — dedent first
            if (line.equals("}")) {
                depth = Math.max(0, depth - 1);
                out.append(getIndentString(depth)).append('}').append('\n');
                continue;
            }

            // Opening brace at end of line (selector)
            if (line.endsWith("{")) {
                // For selector comma lists, each selector on its own line
                String selector = line.substring(0, line.length() - 1).trim();
                String[] selParts = selector.split(",");
                if (selParts.length > 1) {
                    for (int s = 0; s < selParts.length; s++) {
                        out.append(getIndentString(depth)).append(selParts[s].trim());
                        if (s < selParts.length - 1) out.append(",\n");
                    }
                    out.append(" {\n");
                } else {
                    out.append(getIndentString(depth)).append(line).append('\n');
                }
                depth++;
                continue;
            }

            // Declaration line (property: value;)
            if (line.endsWith(";") && depth > 0) {
                out.append(getIndentString(depth)).append(line).append('\n');
                continue;
            }

            // @-rules that open a block
            if (line.startsWith("@") && line.endsWith("{")) {
                out.append(getIndentString(depth)).append(line).append('\n');
                depth++;
                continue;
            }

            // Everything else (comments, at-rules without block, etc.)
            out.append(getIndentString(depth)).append(line).append('\n');
        }

        String result = MULTI_NL.matcher(out.toString()).replaceAll("\n\n");
        return result.trim() + "\n";
    }
}
