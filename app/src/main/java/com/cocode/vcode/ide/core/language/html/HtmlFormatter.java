package com.cocode.vcode.ide.core.language.html;

import com.cocode.vcode.ide.core.language.base.BaseFormatter;
import com.cocode.vcode.ide.core.language.css.CssFormatter;
import com.cocode.vcode.ide.core.language.js.JsFormatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlFormatter extends BaseFormatter {

    private static final Set<String> VOID = new HashSet<>(Arrays.asList(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"
    ));
    // Inline elements — stay on same line as their content
    private static final Set<String> INLINE = new HashSet<>(Arrays.asList(
            "a", "abbr", "acronym", "b", "bdo", "big", "br", "button", "cite", "code", "dfn", "em", "i",
            "img", "input", "kbd", "label", "map", "object", "output", "q", "s", "samp", "select", "small",
            "span", "strong", "sub", "sup", "textarea", "time", "tt", "u", "var"
    ));
    // Body-level block elements that get a blank line before/after for readability.
    // head, html, body, style, script deliberately excluded — they must not generate blank lines.

    private static final Pattern MULTI_NL = Pattern.compile("\\n{3,}");
    private static final Pattern ATTR_SPLIT = Pattern.compile("(\\S+)\\s*=\\s*(\"[^\"]*\"|'[^']*'|\\S+)|([\\w:@.#\\-]+)");

    @Override
    public String format(String code) {
        if (code == null || code.isEmpty()) return "";

        String uuid = UUID.randomUUID().toString().replace("-", "");
        List<String> styleBlocks = new ArrayList<>();
        List<String> scriptBlocks = new ArrayList<>();
        CssFormatter css = new CssFormatter();
        JsFormatter js = new JsFormatter();

        // Extract and format embedded CSS
        code = replaceEmbedded(code, "style", css, styleBlocks, uuid, "STYLE");
        // Extract and format embedded JS
        code = replaceEmbedded(code, "script", js, scriptBlocks, uuid, "SCRIPT");

        String result = formatHtml(code);

        // Re-inject formatted CSS blocks
        result = reInject(result, styleBlocks, uuid, "STYLE");
        result = reInject(result, scriptBlocks, uuid, "SCRIPT");

        result = MULTI_NL.matcher(result).replaceAll("\n\n");
        return result.trim() + "\n";
    }

    private String replaceEmbedded(String code, String tag, BaseFormatter fmt,
                                   List<String> store, String uuid, String key) {
        Pattern p = Pattern.compile("(?is)(<" + tag + "[^>]*>)(.*?)(</" + tag + ">)");
        Matcher m = p.matcher(code);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String inner = m.group(2);
            String formatted = Objects.requireNonNull(inner).trim().isEmpty() ? "" : fmt.format(inner);
            store.add(formatted);
            String ph = "___" + key + "_" + uuid + "_" + (store.size() - 1) + "___";
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    m.group(1) + (formatted.isEmpty() ? "" : "\n" + ph + "\n") + m.group(3)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String reInject(String result, List<String> blocks, String uuid, String key) {
        for (int i = 0; i < blocks.size(); i++) {
            String ph = "___" + key + "_" + uuid + "_" + i + "___";
            Matcher m = Pattern.compile("(?m)^([ \\t]*)" + Pattern.quote(ph) + "$").matcher(result);
            if (m.find()) {
                String pad = m.group(1);
                result = result.replace(Objects.requireNonNull(m.group(0)), indent(blocks.get(i), pad));
            }
        }
        return result;
    }

    private String indent(String text, String pad) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) sb.append(pad);
            sb.append(lines[i]);
            if (i < lines.length - 1) sb.append("\n");
        }
        return sb.toString();
    }

    private String formatHtml(String code) {
        // Tokenise into a flat list of tokens: tags + text nodes
        List<String> tokens = tokenise(code);
        StringBuilder out = new StringBuilder();
        int depth = 0;

        for (int ti = 0; ti < tokens.size(); ti++) {
            String token = tokens.get(ti);
            String text = token.trim();
            if (text.isEmpty()) continue;

            boolean isClose = text.startsWith("</");
            boolean isOpen = text.startsWith("<") && !isClose && !text.startsWith("<!--") && !text.startsWith("<!");
            boolean isComment = text.startsWith("<!--");
            boolean isDoctype = text.startsWith("<!");
            boolean isSelfClose = isOpen && (text.endsWith("/>") || isVoid(text));

            String tagName = "";
            if (isOpen || isClose) {
                tagName = tagName(text).toLowerCase();
            }

            // Decrease depth before printing close tag
            if (isClose) depth = Math.max(0, depth - 1);

            // Removed logic that added a blank line before section-level elements

            String pad = getIndentString(depth);

            if (isDoctype) {
                out.append(text).append("\n");
            } else if (isComment) {
                out.append(pad).append(text).append("\n");
            } else if (isOpen) {
                String formatted = formatTag(text, pad);
                out.append(formatted).append("\n");
                if (!isSelfClose && !INLINE.contains(tagName)) {
                    depth++;
                }
            } else if (isClose) {
                out.append(pad).append(text).append("\n");
            } else {
                // Text node
                out.append(pad).append(text).append("\n");
            }
        }
        return out.toString();
    }

    /**
     * Format a single opening tag — wrap long attribute lists one-per-line.
     */
    private String formatTag(String tag, String baseIndent) {
        // Extract tag name
        int nameEnd = 1;
        while (nameEnd < tag.length() && !Character.isWhitespace(tag.charAt(nameEnd))
                && tag.charAt(nameEnd) != '>' && tag.charAt(nameEnd) != '/') nameEnd++;
        String name = tag.substring(1, nameEnd);

        // Extract attribute string
        boolean selfClose = tag.endsWith("/>");
        String inner = tag.substring(nameEnd, selfClose ? tag.length() - 2 : tag.length() - 1).trim();

        if (inner.isEmpty()) {
            return baseIndent + "<" + name + (selfClose ? " />" : ">");
        }

        // Parse attributes preserving quoted values
        List<String> attrs = new ArrayList<>();
        Matcher m = ATTR_SPLIT.matcher(inner);
        while (m.find()) {
            String full = m.group(0);
            assert full != null;
            if (!full.trim().isEmpty()) attrs.add(full.trim());
        }

        if (attrs.isEmpty()) return baseIndent + "<" + name + (selfClose ? " />" : ">");

        // Single short attribute: keep inline
        String attrIndent = baseIndent + indentUnit;
        String singleLine = baseIndent + "<" + name + " " + String.join(" ", attrs) + (selfClose ? " />" : ">");
        if (singleLine.length() <= 80) return singleLine;

        // Multi-attribute: each on its own line
        StringBuilder sb = new StringBuilder(baseIndent).append("<").append(name).append("\n");
        for (int i = 0; i < attrs.size(); i++) {
            sb.append(attrIndent).append(attrs.get(i));
            if (i < attrs.size() - 1) sb.append("\n");
        }
        sb.append("\n").append(baseIndent).append(selfClose ? "/>" : ">");
        return sb.toString();
    }

    /**
     * Tokenise HTML into tags and text segments.
     */
    private List<String> tokenise(String html) {
        List<String> list = new ArrayList<>();
        int i = 0;
        while (i < html.length()) {
            if (html.charAt(i) == '<') {
                int end;
                if (html.startsWith("<!--", i)) {
                    end = html.indexOf("-->", i + 4);
                    end = end < 0 ? html.length() : end + 3;
                } else if (html.startsWith("<!", i)) {
                    end = html.indexOf('>', i) + 1;
                    if (end == 0) end = html.length();
                } else {
                    // Walk past attribute quoted values too
                    end = i + 1;
                    boolean inQ = false;
                    char qc = 0;
                    while (end < html.length()) {
                        char c = html.charAt(end);
                        if (inQ) {
                            if (c == qc) inQ = false;
                        } else if (c == '"' || c == '\'') {
                            inQ = true;
                            qc = c;
                        } else if (c == '>') {
                            end++;
                            break;
                        }
                        end++;
                    }
                }
                list.add(html.substring(i, end));
                i = end;
            } else {
                int end = html.indexOf('<', i);
                if (end < 0) end = html.length();
                String text = html.substring(i, end).trim();
                if (!text.isEmpty()) list.add(text);
                i = end;
            }
        }
        return list;
    }

    private String tagName(String tag) {
        int s = tag.startsWith("</") ? 2 : 1;
        int e = s;
        while (e < tag.length() && !Character.isWhitespace(tag.charAt(e))
                && tag.charAt(e) != '>' && tag.charAt(e) != '/') e++;
        return tag.substring(s, e);
    }

    private boolean isVoid(String tag) {
        return VOID.contains(tagName(tag).toLowerCase());
    }

}
