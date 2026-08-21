package com.cocode.vcode.ide.core.language.html;

import com.cocode.vcode.ide.core.diagnostic.util.KnownElements;
import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.diagnostic.util.TokenMask;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Real-time linter for HTML, validating tag pairing, deprecated elements/attributes, accessibility, and syntax.
 */
public class HtmlLinter {

    private static final Set<String> EMPTY_CHECK_TAGS = new HashSet<>(java.util.Arrays.asList(
            "div", "span", "p", "section", "ul", "ol", "button"
    ));

    public static List<Problem> analyze(File file, String text) {
        if (text == null || text.trim().isEmpty()) return new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        TokenMask mask = TokenMask.build(text, "html");

        Deque<TagFrame> openStack = new ArrayDeque<>();
        Set<String> seenIds = new HashSet<>();
        boolean hasCharset = false;
        boolean hasViewport = false;
        boolean hasTitle = false;
        boolean hasLangOnHtml;
        boolean hasMetaDescription = false;
        int headingLevel = 0;
        // track table structure for table-has-no-th check
        boolean tableHasTh = false;

        int i = 0;
        int len = text.length();

        while (i < len) {
            // Skip masked (comment) chars
            if (mask.inComment[i]) {
                i++;
                continue;
            }

            char c = text.charAt(i);

            if (c != '<') {
                i++;
                continue;
            }

            // DOCTYPE
            if (i + 8 < len && text.substring(i, i + 9).equalsIgnoreCase("<!DOCTYPE")) {
                while (i < len && text.charAt(i) != '>') i++;
                i++;
                continue;
            }

            // Comment <!-- --> — handled by mask, but skip the token
            if (i + 3 < len && text.charAt(i + 1) == '!' && text.charAt(i + 2) == '-' && text.charAt(i + 3) == '-') {
                while (i + 2 < len && !(text.charAt(i) == '-' && text.charAt(i + 1) == '-' && text.charAt(i + 2) == '>'))
                    i++;
                i += 3;
                continue;
            }

            // Closing tag
            if (i + 1 < len && text.charAt(i + 1) == '/') {
                int nameStart = i + 2;
                int nameEnd = nameStart;
                while (nameEnd < len && isTagNameChar(text.charAt(nameEnd))) nameEnd++;
                if (nameEnd > nameStart) {
                    String tagName = text.substring(nameStart, nameEnd).toLowerCase();
                    int tagLine = LinterUtils.getLine(text, i);
                    int tagCol = LinterUtils.getColumn(text, i);
                    int tagLen = tagName.length() + 3; // </name>

                    if (KnownElements.VOID_ELEMENTS.contains(tagName)) {
                        problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                "'" + tagName + "' is a void element and cannot have a closing tag",
                                Problem.Severity.ERROR));
                    } else {
                        // find matching open on stack
                        boolean found = false;
                        TagFrame[] frames = openStack.toArray(new TagFrame[0]);
                        for (int si = 0; si < frames.length; si++) {
                            if (frames[si].name.equals(tagName)) {
                                // pop everything above it — report each as unclosed
                                for (int si2 = 0; si2 < si; si2++) {
                                    TagFrame unclosed = openStack.pop();
                                    problems.add(new Problem(file, unclosed.line, unclosed.col,
                                            unclosed.name.length() + 2,
                                            "Unclosed tag '<" + unclosed.name + ">'",
                                            Problem.Severity.ERROR));
                                }
                                openStack.pop();
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                    "Stray closing tag '</" + tagName + ">' with no opening tag",
                                    Problem.Severity.ERROR));
                        }

                        // table th tracking
                        if (tagName.equals("table")) {
                            if (!tableHasTh) {
                                problems.add(new Problem(file, tagLine, tagCol, 7,
                                        "'<table>' has no header row: add '<thead>' and '<th>' for accessibility",
                                        Problem.Severity.INFO));
                            }
                            tableHasTh = false;
                        }
                    }
                }
                // skip to end of tag
                while (i < len && text.charAt(i) != '>') i++;
                i++;
                continue;
            }

            // Opening tag
            if (i + 1 < len && (isTagNameChar(text.charAt(i + 1)) || text.charAt(i + 1) == '!')) {
                int nameStart = i + 1;
                int nameEnd = nameStart;
                while (nameEnd < len && isTagNameChar(text.charAt(nameEnd))) nameEnd++;
                if (nameEnd == nameStart) {
                    i++;
                    continue;
                }
                String tagName = text.substring(nameStart, nameEnd).toLowerCase();
                int tagLine = LinterUtils.getLine(text, i);
                int tagCol = LinterUtils.getColumn(text, i);
                int tagLen;

                // parse attributes
                Map<String, String> attrs = new HashMap<>();
                int j = nameEnd;
                boolean selfClosing = false;

                // skip whitespace
                while (j < len && text.charAt(j) != '>' && !(text.charAt(j) == '/' && j + 1 < len && text.charAt(j + 1) == '>')) {
                    // skip whitespace
                    while (j < len && Character.isWhitespace(text.charAt(j))) j++;
                    if (j >= len || text.charAt(j) == '>') break;
                    if (text.charAt(j) == '/' && j + 1 < len && text.charAt(j + 1) == '>') {
                        selfClosing = true;
                        j += 2;
                        break;
                    }
                    // read attr name
                    int attrStart = j;
                    while (j < len && !Character.isWhitespace(text.charAt(j)) && text.charAt(j) != '=' && text.charAt(j) != '>' && text.charAt(j) != '/')
                        j++;
                    if (j <= attrStart) {
                        j++;
                        continue;
                    }
                    String attrName = text.substring(attrStart, j).toLowerCase();
                    String attrVal = "";

                    while (j < len && Character.isWhitespace(text.charAt(j))) j++;
                    if (j < len && text.charAt(j) == '=') {
                        do j++;
                        while (j < len && Character.isWhitespace(text.charAt(j)));
                        if (j < len && (text.charAt(j) == '"' || text.charAt(j) == '\'')) {
                            char q = text.charAt(j);
                            j++;
                            int valStart = j;
                            boolean closed = false;
                            while (j < len) {
                                if (text.charAt(j) == q) {
                                    closed = true;
                                    break;
                                }
                                if (text.charAt(j) == '>') break; // unclosed
                                j++;
                            }
                            if (!closed) {
                                problems.add(new Problem(file, tagLine, tagCol, attrName.length(),
                                        "Unclosed attribute value for '" + attrName + "': missing closing quote",
                                        Problem.Severity.ERROR));
                            }
                            attrVal = text.substring(valStart, j);
                            if (closed) j++; // skip closing quote
                        } else {
                            // unquoted value
                            int valStart = j;
                            while (j < len && !Character.isWhitespace(text.charAt(j)) && text.charAt(j) != '>')
                                j++;
                            attrVal = text.substring(valStart, j);
                        }
                    }
                    attrs.put(attrName, attrVal);
                }
                if (j < len && text.charAt(j) == '>') j++;

                // Update tagLen to cover the entire tag
                tagLen = j - i;

    // RULE: unknown tag
                if (!KnownElements.VALID_HTML_TAGS.contains(tagName)
                        && !KnownElements.DEPRECATED_ELEMENTS.containsKey(tagName)
                        && !tagName.contains("-")) {
                    problems.add(new Problem(file, tagLine, tagCol, tagLen,
                            "'<" + tagName + ">' is not a valid HTML5 element",
                            Problem.Severity.ERROR));
                }

    // RULE: deprecated element
                if (KnownElements.DEPRECATED_ELEMENTS.containsKey(tagName)) {
                    problems.add(new Problem(file, tagLine, tagCol, tagLen,
                            "'<" + tagName + ">' is deprecated — use " + KnownElements.DEPRECATED_ELEMENTS.get(tagName) + " instead",
                            Problem.Severity.WARNING));
                }

    // RULE: deprecated attributes
                for (Map.Entry<String, String> attrEntry : attrs.entrySet()) {
                    String ak = attrEntry.getKey();
                    String tagSpecific = tagName + ":" + ak;
                    String wildcard = "*:" + ak;
                    String suggestion = null;
                    if (KnownElements.DEPRECATED_ATTRIBUTES.containsKey(tagSpecific)) {
                        suggestion = KnownElements.DEPRECATED_ATTRIBUTES.get(tagSpecific);
                    } else if (KnownElements.DEPRECATED_ATTRIBUTES.containsKey(wildcard)) {
                        suggestion = KnownElements.DEPRECATED_ATTRIBUTES.get(wildcard);
                    }
                    if (suggestion != null) {
                        problems.add(new Problem(file, tagLine, tagCol, ak.length(),
                                "'" + ak + "' attribute on '<" + tagName + ">' is deprecated — use " + suggestion + " instead",
                                Problem.Severity.WARNING));
                    }
                }

    // RULE: id uniqueness
                if (attrs.containsKey("id")) {
                    String idVal = attrs.get("id");
                    if (!Objects.requireNonNull(idVal).isEmpty() && !seenIds.add(idVal)) {
                        problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                "Duplicate id='" + idVal + "': IDs must be unique in a document",
                                Problem.Severity.ERROR));
                    }
                }

    // RULE: inline style
                if (attrs.containsKey("style")) {
                    problems.add(new Problem(file, tagLine, tagCol, tagLen,
                            "Avoid inline styles on '<" + tagName + ">': prefer CSS classes",
                            Problem.Severity.WARNING));
                }

    // RULE: event handler attributes
                for (String ak : attrs.keySet()) {
                    if (ak.startsWith("on")) {
                        problems.add(new Problem(file, tagLine, tagCol, ak.length(),
                                "'" + ak + "' inline handler on '<" + tagName + ">': prefer addEventListener() in external JS",
                                Problem.Severity.INFO));
                    }
                }

    // RULE: required parent
                Set<String> requiredParents = KnownElements.REQUIRED_PARENTS.get(tagName);
                if (requiredParents != null) {
                    String immediateParent = openStack.isEmpty() ? "" : Objects.requireNonNull(openStack.peek()).name;
                    if (!requiredParents.contains(immediateParent)) {
                        problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                "'<" + tagName + ">' must be a child of " + requiredParents,
                                Problem.Severity.ERROR));
                    }
                }

    // RULE: nested <a>
                if (tagName.equals("a")) {
                    for (TagFrame frame : openStack) {
                        if (frame.name.equals("a")) {
                            problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                    "'<a>' cannot be nested inside another '<a>'",
                                    Problem.Severity.ERROR));
                            break;
                        }
                    }
                }

    // PER-TAG RULES
                switch (tagName) {
                    case "html":
                        hasLangOnHtml = attrs.containsKey("lang");
                        if (!hasLangOnHtml) {
                            problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                    "'<html>' is missing 'lang' attribute: required for screen readers",
                                    Problem.Severity.WARNING));
                        }
                        break;
                    case "meta":
                        String nameAttr = attrs.get("name");
                        String httpEquiv = attrs.get("http-equiv");
                        String charset = attrs.get("charset");
                        if (charset != null || (httpEquiv != null && httpEquiv.equalsIgnoreCase("content-type"))) {
                            hasCharset = true;
                        }
                        if ("viewport".equalsIgnoreCase(nameAttr)) hasViewport = true;
                        if ("description".equalsIgnoreCase(nameAttr)) hasMetaDescription = true;
                        break;
                    case "title":
                        hasTitle = true;
                        break;
                    case "img":
                        boolean hasSrc = attrs.containsKey("src");
                        boolean hasAlt = attrs.containsKey("alt");
                        if (!hasSrc) {
                            problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                    "'<img>' is missing required attribute 'src'",
                                    Problem.Severity.ERROR));
                        }
                        if (!hasAlt) {
                            problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                    "'<img>' is missing 'alt' attribute: required for accessibility",
                                    Problem.Severity.WARNING));
                        }
                        if (!attrs.containsKey("loading")) {
                            problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                    "Consider adding 'loading=\"lazy\"' on '<img>' for performance",
                                    Problem.Severity.INFO));
                        }
                        break;
                    case "script":
                        boolean hasDefer = attrs.containsKey("defer");
                        boolean hasAsync = attrs.containsKey("async");
                        String scriptType = attrs.get("type");
                        boolean isModule = "module".equalsIgnoreCase(scriptType);
                        if (!hasDefer && !hasAsync && !isModule && !attrs.containsKey("src")) {
                            // inline script — only warn if has src
                        } else if (!hasDefer && !hasAsync && !isModule) {
                            problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                    "'<script>' without 'defer' or 'async': may block page rendering",
                                    Problem.Severity.WARNING));
                        }
                        if ("text/javascript".equalsIgnoreCase(scriptType)) {
                            problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                    "'type=\"text/javascript\"' is redundant in HTML5 — remove it",
                                    Problem.Severity.WARNING));
                        }
                        break;
                    case "link":
                        String linkType = attrs.get("type");
                        if ("text/css".equalsIgnoreCase(linkType)) {
                            problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                    "'type=\"text/css\"' on '<link>' is redundant in HTML5 — remove it",
                                    Problem.Severity.WARNING));
                        }
                        break;
                    case "button":
                        if (!attrs.containsKey("type")) {
                            problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                    "'<button>' is missing 'type' attribute (defaults to 'submit', which can cause bugs)",
                                    Problem.Severity.WARNING));
                        }
                        break;
                    case "a":
                        if ("_blank".equalsIgnoreCase(attrs.get("target"))) {
                            String rel = attrs.get("rel");
                            if (rel == null || (!rel.toLowerCase().contains("noopener") && !rel.toLowerCase().contains("noreferrer"))) {
                                problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                        "Using target=\"_blank\" without rel=\"noopener\" or rel=\"noreferrer\" is a security risk",
                                        Problem.Severity.WARNING));
                            }
                        }
                        String href = attrs.get("href");
                        String target = attrs.get("target");
                        if ("#".equals(href)) {
                            problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                    "'<a href=\"#\">' used as button — use '<button>' for semantic correctness",
                                    Problem.Severity.WARNING));
                        }
                        if ("_blank".equals(target)) {
                            String rel = attrs.get("rel");
                            if (rel == null || !rel.contains("noopener")) {
                                problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                        "'<a target=\"_blank\">' is missing 'rel=\"noopener noreferrer\"': security risk",
                                        Problem.Severity.WARNING));
                            }
                        }
                        break;
                    case "table":
                        tableHasTh = false;
                        break;
                    case "th":
                        tableHasTh = true;
                        break;
                    case "div":
                        // semantic suggestion
                        String classAttr = attrs.get("class");
                        if (classAttr != null) {
                            for (String cls : classAttr.split("\\s+")) {
                                String suggestion = KnownElements.SEMANTIC_SUGGESTIONS.get(cls.toLowerCase());
                                if (suggestion != null) {
                                    problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                            "'<div class=\"" + cls + "\">' could be replaced with '" + suggestion + "'",
                                            Problem.Severity.INFO));
                                }
                            }
                        }
                        break;
                }

    // RULE: heading level skip
                if (tagName.matches("h[1-6]")) {
                    int level = tagName.charAt(1) - '0';
                    if (headingLevel > 0 && level > headingLevel + 1) {
                        problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                "Heading level skipped: '<h" + level + ">' after '<h" + headingLevel + ">'",
                                Problem.Severity.WARNING));
                    }
                    headingLevel = level;
                }

    // RULE: required attributes (not img, handled above)
                if (!tagName.equals("img") && !tagName.equals("script")) {
                    Set<String> req = KnownElements.REQUIRED_ATTRIBUTES.get(tagName);
                    if (req != null) {
                        for (String reqAttr : req) {
                            if (!attrs.containsKey(reqAttr)) {
                                // skip 'script' src requirement (inline is fine), skip optionals
                                if (tagName.equals("a") && reqAttr.equals("href"))
                                    continue; // flagged as warning separately
                                problems.add(new Problem(file, tagLine, tagCol, tagLen,
                                        "'<" + tagName + ">' is missing required attribute '" + reqAttr + "'",
                                        Problem.Severity.WARNING));
                            }
                        }
                    }
                }

                // Push to stack if not void and not self-closing
                // Deprecated elements still have closing tags, so push them too
                boolean isVoid = KnownElements.VOID_ELEMENTS.contains(tagName);
                boolean isKnown = KnownElements.VALID_HTML_TAGS.contains(tagName)
                        || KnownElements.DEPRECATED_ELEMENTS.containsKey(tagName)
                        || tagName.contains("-");
                if (!isVoid && !selfClosing && isKnown) {
                    openStack.push(new TagFrame(tagName, tagLine, tagCol, attrs, j));
                }

                i = j;
                continue;
            }

            i++;
        }

        // Unclosed tags
        for (TagFrame frame : openStack) {
            int tlen = frame.name.length() + 2;
            problems.add(new Problem(file, frame.line, frame.col, tlen,
                    "Unclosed tag '<" + frame.name + ">'",
                    Problem.Severity.ERROR));
        }

    // DOCUMENT-LEVEL WARNINGS
        if (!hasCharset) {
            problems.add(new Problem(file, 1, 1, 1,
                    "Missing '<meta charset=\"...\">' in <head>: may cause encoding issues",
                    Problem.Severity.WARNING));
        }
        if (!hasViewport) {
            problems.add(new Problem(file, 1, 1, 1,
                    "Missing viewport meta tag: page may not be mobile-responsive",
                    Problem.Severity.WARNING));
        }
        if (!hasTitle) {
            problems.add(new Problem(file, 1, 1, 1,
                    "Missing '<title>' in <head>",
                    Problem.Severity.WARNING));
        }
        if (!hasMetaDescription) {
            problems.add(new Problem(file, 1, 1, 1,
                    "Consider adding '<meta name=\"description\">' for SEO",
                    Problem.Severity.INFO));
        }

    // RULE: empty tags check
        checkEmptyTags(file, text, mask, problems);

        return problems;
    }

    private static void checkEmptyTags(File file, String text, TokenMask mask, List<Problem> problems) {
        int i = 0;
        int len = text.length();
        while (i < len) {
            if (mask.inComment[i]) {
                i++;
                continue;
            }
            if (text.charAt(i) != '<') {
                i++;
                continue;
            }
            if (i + 1 >= len || text.charAt(i + 1) == '/') {
                i++;
                continue;
            }
            int nameStart = i + 1;
            int nameEnd = nameStart;
            while (nameEnd < len && isTagNameChar(text.charAt(nameEnd))) nameEnd++;
            if (nameEnd == nameStart) {
                i++;
                continue;
            }
            String tagName = text.substring(nameStart, nameEnd).toLowerCase();
            if (!EMPTY_CHECK_TAGS.contains(tagName)) {
                i++;
                continue;
            }
            int tagLine = LinterUtils.getLine(text, i);
            int tagCol = LinterUtils.getColumn(text, i);
            // skip to end of opening tag
            int j = nameEnd;
            while (j < len && text.charAt(j) != '>') j++;
            j++; // skip >
            // skip whitespace
            int k = j;
            while (k < len && Character.isWhitespace(text.charAt(k))) k++;
            // check if immediately closing tag
            if (k + tagName.length() + 2 <= len
                    && text.charAt(k) == '<' && text.charAt(k + 1) == '/'
                    && text.substring(k + 2, k + 2 + tagName.length()).equalsIgnoreCase(tagName)) {
                problems.add(new Problem(file, tagLine, tagCol, tagName.length() + 2,
                        "Empty '<" + tagName + ">': likely unintentional",
                        Problem.Severity.WARNING));
            }
            i = j;
        }
    }

    private static boolean isTagNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }

    private static class TagFrame {
        final String name;
        final int line, col;
        final Map<String, String> attrs;
        final int endOffset;

        TagFrame(String name, int line, int col, Map<String, String> attrs, int endOffset) {
            this.name = name;
            this.line = line;
            this.col = col;
            this.attrs = attrs;
            this.endOffset = endOffset;
        }
    }
}
