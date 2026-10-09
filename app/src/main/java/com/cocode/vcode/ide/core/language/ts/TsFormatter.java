package com.cocode.vcode.ide.core.language.ts;

import com.cocode.vcode.ide.core.language.js.JsFormatter;

/**
 * Formatter for TypeScript files, extending JsFormatter with type annotation
 * spacing and union delimiter standardization.
 */
public class TsFormatter extends JsFormatter {

    @Override
    public String format(String code) {
        if (code == null || code.isEmpty()) return code;
        String base = super.format(code);
        return formatTypeSpacing(base);
    }

    public static String formatTypeSpacing(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length());
        boolean inString = false;
        char quote = 0;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (inLineComment) {
                sb.append(c);
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                sb.append(c);
                if (c == '*' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                    sb.append('/');
                    i++;
                    inBlockComment = false;
                }
                continue;
            }
            if (inString) {
                sb.append(c);
                if (c == quote && (i == 0 || text.charAt(i - 1) != '\\')) {
                    inString = false;
                }
                continue;
            }
            if (c == '/' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == '/') {
                    sb.append("//");
                    i++;
                    inLineComment = true;
                    continue;
                } else if (next == '*') {
                    sb.append("/*");
                    i++;
                    inBlockComment = true;
                    continue;
                }
            }
            if (c == '\'' || c == '"' || c == '`') {
                inString = true;
                quote = c;
                sb.append(c);
                continue;
            }

            // Space after colon in type annotations if immediately followed by letter: a:string -> a: string
            if (c == ':') {
                sb.append(c);
                if (i + 1 < text.length() && !Character.isWhitespace(text.charAt(i + 1)) && text.charAt(i + 1) != ':' && text.charAt(i + 1) != '=') {
                    sb.append(' ');
                }
                continue;
            }

            // Space around union pipe (|) in type positions (not ||)
            if (c == '|') {
                boolean prevIsPipe = (i > 0 && text.charAt(i - 1) == '|');
                boolean nextIsPipe = (i + 1 < text.length() && text.charAt(i + 1) == '|');
                if (!prevIsPipe && !nextIsPipe) {
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                        sb.append(' ');
                    }
                    sb.append(c);
                    if (i + 1 < text.length() && text.charAt(i + 1) != ' ') {
                        sb.append(' ');
                    }
                    continue;
                }
            }

            sb.append(c);
        }
        return sb.toString();
    }
}
