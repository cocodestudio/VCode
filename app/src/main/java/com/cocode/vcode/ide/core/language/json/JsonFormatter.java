package com.cocode.vcode.ide.core.language.json;

import com.cocode.vcode.ide.core.language.base.BaseFormatter;

/**
 * Formatter for JSON documents, supporting configurable indentation.
 */
public class JsonFormatter extends BaseFormatter {

    @Override
    public String format(String code) {
        if (code == null || code.isEmpty()) return "";
        StringBuilder out = new StringBuilder(code.length() + code.length() / 4);
        int indent = 0;
        boolean inString = false;

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);

            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < code.length()) {
                    out.append(code.charAt(++i));
                    continue;
                }
                if (c == '"') inString = false;
                continue;
            }

            if (c == '"') {
                out.append(c);
                inString = true;
                continue;
            }

            // Skip structural whitespace — we rebuild it
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') continue;

            if (c == '{' || c == '[') {
                // Look ahead: if immediately closed, emit inline "{}" or "[]"
                int j = i + 1;
                while (j < code.length() && (code.charAt(j) == ' ' || code.charAt(j) == '\t'
                        || code.charAt(j) == '\n' || code.charAt(j) == '\r')) j++;
                char close = c == '{' ? '}' : ']';
                if (j < code.length() && code.charAt(j) == close) {
                    out.append(c).append(close);
                    i = j;
                    continue;
                }
                out.append(c).append('\n');
                indent++;
                out.append(getIndentString(indent));
            } else if (c == '}' || c == ']') {
                out.append('\n');
                indent = Math.max(0, indent - 1);
                out.append(getIndentString(indent)).append(c);
            } else if (c == ',') {
                out.append(",\n").append(getIndentString(indent));
            } else if (c == ':') {
                out.append(": ");
            } else {
                out.append(c);
            }
        }
        return out.toString().trim() + "\n";
    }
}
