package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.language.base.BaseFormatter;

import java.util.regex.Pattern;

/**
 * Formatter for JavaScript and TypeScript source code.
 */
public class JsFormatter extends BaseFormatter {

    private static final Pattern MULTI_NL = Pattern.compile("\\n{3,}");
    private static final Pattern TRAILING_SP = Pattern.compile("[ \t]+\n");

    @Override
    public String format(String code) {
        if (code == null || code.isEmpty()) return "";

        code = code.replace("\r\n", "\n").replace("\r", "\n");

    // Pass 1: normalise the token stream
        String norm = normalise(code);

    // Pass 2: re-indent
        String indented = reIndent(norm);

    // Pass 3: post-process
        indented = TRAILING_SP.matcher(indented).replaceAll("\n");
        indented = MULTI_NL.matcher(indented).replaceAll("\n\n");
        return indented.trim() + "\n";
    }

    // Pass 1: Produce a clean, single-normalised-space stream
    private String normalise(String code) {
        StringBuilder out = new StringBuilder(code.length() + code.length() / 4);
        int len = code.length();
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        char stringChar = 0;

        for (int i = 0; i < len; i++) {
            char c = code.charAt(i);

    // Block comment
            if (inBlockComment) {
                out.append(c);
                if (c == '*' && i + 1 < len && code.charAt(i + 1) == '/') {
                    out.append('/');
                    i++;
                    out.append('\n');
                    inBlockComment = false;
                }
                continue;
            }
    // Line comment
            if (inLineComment) {
                out.append(c);
                if (c == '\n') inLineComment = false;
                continue;
            }
    // String / template literal
            if (inString) {
                out.append(c);
                if (c == '\\') {
                    if (i + 1 < len) {
                        out.append(code.charAt(++i));
                    }
                    continue;
                }
                if (c == stringChar) {
                    if (stringChar == '`') ;
                    inString = false;
                }
                continue;
            }

            // Start comment
            if (c == '/' && i + 1 < len) {
                if (code.charAt(i + 1) == '/') {
                    inLineComment = true;
                    out.append("//");
                    i++;
                    continue;
                }
                if (code.charAt(i + 1) == '*') {
                    inBlockComment = true;
                    out.append("/*");
                    i++;
                    continue;
                }
            }
            // Start string
            if (c == '"' || c == '\'' || c == '`') {
                inString = true;
                stringChar = c;
                if (c == '`') ;
                out.append(c);
                continue;
            }

            // Collapse whitespace
            if (c == '\t' || c == '\r') {
                out.append(' ');
                continue;
            }
            if (c == '\n') {
                // Keep at most one newline
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
                continue;
            }
            if (c == ' ' && out.length() > 0 && out.charAt(out.length() - 1) == ' ') continue;

            // Structural characters
            if (c == '{' || c == '[') {
                ensureSpace(out);
                out.append(c).append('\n');
                continue;
            }
            if (c == '}' || c == ']') {
                trimTrailingSpace(out);
                out.append('\n').append(c);
                // peek: if followed by ; or , or ) keep on same line, else newline
                int j = i + 1;
                while (j < len && (code.charAt(j) == ' ' || code.charAt(j) == '\t')) j++;
                char next = j < len ? code.charAt(j) : 0;
                if (next == ';' || next == ',' || next == ')' || next == ']' || next == '}') {
                    // stay same line — nothing
                } else if (next == '.' || next == '?') {
                    // method chaining — stay same line
                } else {
                    // check for else/catch/finally
                    String rem = j < len ? code.substring(j) : "";
                    if (rem.startsWith("else") || rem.startsWith("catch") || rem.startsWith("finally")) {
                        out.append(' ');
                    } else {
                        out.append('\n');
                    }
                }
                continue;
            }
            if (c == ';') {
                out.append(';').append('\n');
                continue;
            }
            // Comma: space after, newline if at statement level handled in re-indent
            if (c == ',') {
                out.append(',').append(' ');
                continue;
            }
            // Arrow
            if (c == '=' && i + 1 < len && code.charAt(i + 1) == '>') {
                ensureSpace(out);
                out.append("=>");
                i++;
                ensureSpace(out);
                continue;
            }
            // Operators: surround with spaces (simple heuristic)
            if ((c == '=' || c == '+' || c == '-' || c == '*' || c == '/' || c == '%'
                    || c == '&' || c == '|' || c == '<' || c == '>' || c == '!')
                    && i + 1 < len) {
                char next = code.charAt(i + 1);
                boolean compound = (next == '=' || next == '>' || next == '+' || next == '-'
                        || next == '&' || next == '|' || next == '?');
                // Always surround; let post-processing keep clean spacing
                if (out.length() > 0 && out.charAt(out.length() - 1) != ' '
                        && out.charAt(out.length() - 1) != '\n') out.append(' ');
                out.append(c);
                if (compound) {
                    out.append(code.charAt(++i));
                }
                out.append(' ');
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    // Pass 2: re-indent the normalised stream
    private String reIndent(String norm) {
        StringBuilder out = new StringBuilder(norm.length());
        String[] lines = norm.split("\n", -1);
        int depth = 0;
        boolean lastWasBlank = false;

        for (String s : lines) {
            String line = s.trim();
            if (line.isEmpty()) {
                if (!lastWasBlank && out.length() > 0) {
                    out.append('\n');
                    lastWasBlank = true;
                }
                continue;
            }

            boolean startsWithClose = line.charAt(0) == '}' || line.charAt(0) == ']';
            boolean endsWithOpen = line.charAt(line.length() - 1) == '{'
                    || line.charAt(line.length() - 1) == '[';

            // Blank line before top-level function/class declarations
            if (depth == 0 && (line.startsWith("function ") || line.startsWith("class ")
                    || line.startsWith("const ") || line.startsWith("let ")
                    || line.startsWith("var ") || line.startsWith("export "))
                    && out.length() > 0 && !lastWasBlank) {
                out.append('\n');
            }

            if (startsWithClose) depth = Math.max(0, depth - 1);

            String pad = getIndentString(depth);

            // Handle lines that contain both closing and opening braces on same line (e.g. "} else {")
            out.append(pad).append(line).append('\n');
            lastWasBlank = false;

            if (endsWithOpen && !startsWithClose) depth++;
            else if (endsWithOpen) { /* depth already decremented, now increment */
                depth++;
            }

            // Blank line after closing a top-level block
            if (startsWithClose && depth == 0) {
                out.append('\n');
                lastWasBlank = true;
            }
        }
        return out.toString();
    }

    private void ensureSpace(StringBuilder sb) {
        if (sb.length() > 0) {
            char last = sb.charAt(sb.length() - 1);
            if (last != ' ' && last != '\n' && last != '(') sb.append(' ');
        }
    }

    private void trimTrailingSpace(StringBuilder sb) {
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ')
            sb.deleteCharAt(sb.length() - 1);
    }
}
