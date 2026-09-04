package com.cocode.vcode.ide.core.language.html;

import com.cocode.vcode.ide.core.diagnostic.util.KnownElements;
import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.language.js.ParseResult;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Real-time AST-driven linter for HTML, validating tag structure, syntax errors (N_ERROR),
 * deprecated elements/attributes, accessibility rules, required parent/child hierarchy, and document metadata.
 */
public class HtmlLinter {

    private static final Set<String> EMPTY_CHECK_TAGS = new HashSet<>(Arrays.asList(
            "div", "span", "p", "section", "ul", "ol", "button"
    ));

    public static List<Problem> analyze(File file, String text) {
        if (text == null || text.trim().isEmpty()) return new ArrayList<>();
        HtmlTokenStream stream = HtmlLexer.tokenize(text);
        ParseResult result = HtmlParser.parse(text, stream);
        return analyze(file, text, result.htmlTree, stream);
    }

    public static List<Problem> analyze(File file, String text, HtmlSyntaxTree tree, HtmlTokenStream stream) {
        if (text == null || text.trim().isEmpty() || tree == null) return new ArrayList<>();
        List<Problem> problems = new ArrayList<>();

        Set<String> seenIds = new HashSet<>();
        boolean hasCharset = false;
        boolean hasViewport = false;
        boolean hasTitle = false;
        boolean hasMetaDescription = false;
        int headingLevel = 0;

        int nodeCount = tree.nodeCount;
        for (int i = 1; i < nodeCount; i++) {
            int type = tree.nodeType[i];
            int start = tree.nodeStart[i];
            int end = tree.nodeEnd[i];
            int length = Math.max(1, end - start);

            if (type == HtmlSyntaxTree.N_ERROR) {
                String errorName = tree.nodeName[i];
                int line = LinterUtils.getLine(text, start);
                int col = LinterUtils.getColumn(text, start);
                String msg;
                if ("Unclosed".equals(tree.nodeValue[i])) {
                    msg = "Unclosed tag '<" + (errorName != null ? errorName : "") + ">'";
                } else if (errorName != null && KnownElements.VOID_ELEMENTS.contains(errorName.toLowerCase())) {
                    msg = "'" + errorName + "' is a void element and cannot have a closing tag";
                } else if (errorName != null) {
                    msg = "Stray closing tag '</" + errorName + ">' with no opening tag";
                } else {
                    msg = "Malformed HTML syntax near '" + extractSnippet(text, start, Math.min(start + 10, end)) + "'";
                }
                problems.add(new Problem(file, line, col, length, msg, Problem.Severity.ERROR));
                continue;
            }

            if (type == HtmlSyntaxTree.N_ELEMENT) {
                String tagName = tree.nodeName[i];
                if (tagName == null) continue;
                tagName = tagName.toLowerCase();

                int tagLine = LinterUtils.getLine(text, start);
                int tagCol = LinterUtils.getColumn(text, start);

                // Collect element attributes
                Map<String, String> attrs = new HashMap<>();
                Map<String, Integer> attrStarts = new HashMap<>();
                int child = tree.nodeChild[i];
                int textChildrenCount = 0;
                boolean hasNonWhitespaceText = false;
                int nonAttrChildCount = 0;

                while (child != 0) {
                    int cType = tree.nodeType[child];
                    if (cType == HtmlSyntaxTree.N_ATTRIBUTE) {
                        String aName = tree.nodeName[child];
                        String aVal = tree.nodeValue[child];
                        if (aName != null) {
                            String lowerName = aName.toLowerCase();
                            attrs.put(lowerName, aVal != null ? aVal : "");
                            attrStarts.put(lowerName, tree.nodeStart[child]);
                        }
                    } else {
                        nonAttrChildCount++;
                        if (cType == HtmlSyntaxTree.N_TEXT) {
                            textChildrenCount++;
                            int cStart = tree.nodeStart[child];
                            int cEnd = tree.nodeEnd[child];
                            if (cStart < cEnd && cStart < text.length()) {
                                String t = text.substring(cStart, Math.min(cEnd, text.length()));
                                if (!t.trim().isEmpty()) {
                                    hasNonWhitespaceText = true;
                                }
                            }
                        }
                    }
                    child = tree.nodeSibling[child];
                }

                // RULE: Valid HTML5 Element & Deprecated Elements
                boolean isKnown = KnownElements.VALID_HTML_TAGS.contains(tagName)
                        || KnownElements.DEPRECATED_ELEMENTS.containsKey(tagName)
                        || tagName.contains("-");
                if (!isKnown) {
                    problems.add(new Problem(file, tagLine, tagCol, tagName.length() + 2,
                            "'<" + tagName + ">' is not a valid HTML5 element",
                            Problem.Severity.ERROR));
                }

                if (KnownElements.DEPRECATED_ELEMENTS.containsKey(tagName)) {
                    problems.add(new Problem(file, tagLine, tagCol, tagName.length() + 2,
                            "'<" + tagName + ">' is deprecated — use " + KnownElements.DEPRECATED_ELEMENTS.get(tagName) + " instead",
                            Problem.Severity.WARNING));
                }

                // RULE: Deprecated Attributes
                for (Map.Entry<String, String> entry : attrs.entrySet()) {
                    String ak = entry.getKey();
                    String tagSpecific = tagName + ":" + ak;
                    String wildcard = "*:" + ak;
                    String suggestion = null;
                    if (KnownElements.DEPRECATED_ATTRIBUTES.containsKey(tagSpecific)) {
                        suggestion = KnownElements.DEPRECATED_ATTRIBUTES.get(tagSpecific);
                    } else if (KnownElements.DEPRECATED_ATTRIBUTES.containsKey(wildcard)) {
                        suggestion = KnownElements.DEPRECATED_ATTRIBUTES.get(wildcard);
                    }
                    if (suggestion != null) {
                        int attrStart = attrStarts.getOrDefault(ak, start);
                        int aLine = LinterUtils.getLine(text, attrStart);
                        int aCol = LinterUtils.getColumn(text, attrStart);
                        problems.add(new Problem(file, aLine, aCol, ak.length(),
                                "'" + ak + "' attribute on '<" + tagName + ">' is deprecated — use " + suggestion + " instead",
                                Problem.Severity.WARNING));
                    }
                }

                // RULE: ID Uniqueness
                if (attrs.containsKey("id")) {
                    String idVal = attrs.get("id");
                    if (idVal != null) {
                        // Strip quotes if present
                        if (idVal.length() >= 2 && (idVal.startsWith("\"") || idVal.startsWith("'"))) {
                            idVal = idVal.substring(1, idVal.length() - 1);
                        }
                        if (!idVal.isEmpty() && !seenIds.add(idVal)) {
                            int attrStart = attrStarts.getOrDefault("id", start);
                            int aLine = LinterUtils.getLine(text, attrStart);
                            int aCol = LinterUtils.getColumn(text, attrStart);
                            problems.add(new Problem(file, aLine, aCol, idVal.length() + 4,
                                    "Duplicate id='" + idVal + "': IDs must be unique in a document",
                                    Problem.Severity.ERROR));
                        }
                    }
                }

                // RULE: Inline Styles
                if (attrs.containsKey("style")) {
                    int attrStart = attrStarts.getOrDefault("style", start);
                    int aLine = LinterUtils.getLine(text, attrStart);
                    int aCol = LinterUtils.getColumn(text, attrStart);
                    problems.add(new Problem(file, aLine, aCol, 5,
                            "Avoid inline styles on '<" + tagName + ">': prefer CSS classes",
                            Problem.Severity.WARNING));
                }

                // RULE: Event Handler Attributes
                for (String ak : attrs.keySet()) {
                    if (ak.startsWith("on")) {
                        int attrStart = attrStarts.getOrDefault(ak, start);
                        int aLine = LinterUtils.getLine(text, attrStart);
                        int aCol = LinterUtils.getColumn(text, attrStart);
                        problems.add(new Problem(file, aLine, aCol, ak.length(),
                                "'" + ak + "' inline handler on '<" + tagName + ">': prefer addEventListener() in external JS",
                                Problem.Severity.INFO));
                    }
                }

                // RULE: Required Parent
                Set<String> requiredParents = KnownElements.REQUIRED_PARENTS.get(tagName);
                if (requiredParents != null) {
                    int parentId = tree.nodeParent[i];
                    String parentTagName = parentId > 0 && tree.nodeType[parentId] == HtmlSyntaxTree.N_ELEMENT 
                            ? tree.nodeName[parentId] : "";
                    if (!requiredParents.contains(parentTagName.toLowerCase())) {
                        problems.add(new Problem(file, tagLine, tagCol, tagName.length() + 2,
                                "'<" + tagName + ">' must be a child of " + requiredParents,
                                Problem.Severity.ERROR));
                    }
                }

                // RULE: Nested <a>
                if (tagName.equals("a")) {
                    int pId = tree.nodeParent[i];
                    while (pId > 0) {
                        if (tree.nodeType[pId] == HtmlSyntaxTree.N_ELEMENT && "a".equalsIgnoreCase(tree.nodeName[pId])) {
                            problems.add(new Problem(file, tagLine, tagCol, 3,
                                    "'<a>' cannot be nested inside another '<a>'",
                                    Problem.Severity.ERROR));
                            break;
                        }
                        pId = tree.nodeParent[pId];
                    }
                }

                // PER-TAG RULES
                switch (tagName) {
                    case "html":
                        if (!attrs.containsKey("lang")) {
                            problems.add(new Problem(file, tagLine, tagCol, 6,
                                    "'<html>' is missing 'lang' attribute: required for screen readers",
                                    Problem.Severity.WARNING));
                        }
                        break;
                    case "meta":
                        String nameAttr = cleanAttrVal(attrs.get("name"));
                        String httpEquiv = cleanAttrVal(attrs.get("http-equiv"));
                        String charset = attrs.get("charset");
                        if (charset != null || "content-type".equalsIgnoreCase(httpEquiv)) {
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
                            problems.add(new Problem(file, tagLine, tagCol, 5,
                                    "'<img>' is missing required attribute 'src'",
                                    Problem.Severity.ERROR));
                        }
                        if (!hasAlt) {
                            problems.add(new Problem(file, tagLine, tagCol, 5,
                                    "'<img>' is missing 'alt' attribute: required for accessibility",
                                    Problem.Severity.WARNING));
                        }
                        if (!attrs.containsKey("loading")) {
                            problems.add(new Problem(file, tagLine, tagCol, 5,
                                    "Consider adding 'loading=\"lazy\"' on '<img>' for performance",
                                    Problem.Severity.INFO));
                        }
                        break;
                    case "script":
                        boolean hasDefer = attrs.containsKey("defer");
                        boolean hasAsync = attrs.containsKey("async");
                        String scriptType = cleanAttrVal(attrs.get("type"));
                        boolean isModule = "module".equalsIgnoreCase(scriptType);
                        if (attrs.containsKey("src") && !hasDefer && !hasAsync && !isModule) {
                            problems.add(new Problem(file, tagLine, tagCol, 8,
                                    "'<script>' without 'defer' or 'async': may block page rendering",
                                    Problem.Severity.WARNING));
                        }
                        if ("text/javascript".equalsIgnoreCase(scriptType)) {
                            problems.add(new Problem(file, tagLine, tagCol, 8,
                                    "'type=\"text/javascript\"' is redundant in HTML5 — remove it",
                                    Problem.Severity.WARNING));
                        }
                        break;
                    case "link":
                        String linkType = cleanAttrVal(attrs.get("type"));
                        if ("text/css".equalsIgnoreCase(linkType)) {
                            problems.add(new Problem(file, tagLine, tagCol, 6,
                                    "'type=\"text/css\"' on '<link>' is redundant in HTML5 — remove it",
                                    Problem.Severity.WARNING));
                        }
                        break;
                    case "button":
                        if (!attrs.containsKey("type")) {
                            problems.add(new Problem(file, tagLine, tagCol, 8,
                                    "'<button>' is missing 'type' attribute (defaults to 'submit', which can cause bugs)",
                                    Problem.Severity.WARNING));
                        }
                        break;
                    case "a":
                        String target = cleanAttrVal(attrs.get("target"));
                        String rel = cleanAttrVal(attrs.get("rel"));
                        String href = cleanAttrVal(attrs.get("href"));
                        if ("_blank".equalsIgnoreCase(target)) {
                            if (rel == null || (!rel.toLowerCase().contains("noopener") && !rel.toLowerCase().contains("noreferrer"))) {
                                problems.add(new Problem(file, tagLine, tagCol, 3,
                                        "Using target=\"_blank\" without rel=\"noopener\" or rel=\"noreferrer\" is a security risk",
                                        Problem.Severity.WARNING));
                            }
                        }
                        if ("#".equals(href)) {
                            problems.add(new Problem(file, tagLine, tagCol, 3,
                                    "'<a href=\"#\">' used as button — use '<button>' for semantic correctness",
                                    Problem.Severity.WARNING));
                        }
                        break;
                    case "table":
                        boolean hasTh = false;
                        int tableChild = tree.nodeChild[i];
                        while (tableChild != 0) {
                            String cName = tree.nodeName[tableChild];
                            if ("thead".equalsIgnoreCase(cName) || "th".equalsIgnoreCase(cName)) {
                                hasTh = true;
                                break;
                            }
                            tableChild = tree.nodeSibling[tableChild];
                        }
                        if (!hasTh) {
                            problems.add(new Problem(file, tagLine, tagCol, 7,
                                    "'<table>' has no header row: add '<thead>' and '<th>' for accessibility",
                                    Problem.Severity.INFO));
                        }
                        break;
                    case "div":
                        String classAttr = cleanAttrVal(attrs.get("class"));
                        if (classAttr != null) {
                            for (String cls : classAttr.split("\\s+")) {
                                String suggestion = KnownElements.SEMANTIC_SUGGESTIONS.get(cls.toLowerCase());
                                if (suggestion != null) {
                                    problems.add(new Problem(file, tagLine, tagCol, 5,
                                            "'<div class=\"" + cls + "\">' could be replaced with '" + suggestion + "'",
                                            Problem.Severity.INFO));
                                }
                            }
                        }
                        break;
                }

                // RULE: Heading Hierarchy
                if (tagName.length() == 2 && tagName.charAt(0) == 'h' && Character.isDigit(tagName.charAt(1))) {
                    int level = tagName.charAt(1) - '0';
                    if (level >= 1 && level <= 6) {
                        if (headingLevel > 0 && level > headingLevel + 1) {
                            problems.add(new Problem(file, tagLine, tagCol, 4,
                                    "Heading level skipped: '<h" + level + ">' after '<h" + headingLevel + ">'",
                                    Problem.Severity.WARNING));
                        }
                        headingLevel = level;
                    }
                }

                // RULE: Required Attributes
                if (!tagName.equals("img") && !tagName.equals("script")) {
                    Set<String> req = KnownElements.REQUIRED_ATTRIBUTES.get(tagName);
                    if (req != null) {
                        for (String reqAttr : req) {
                            if (!attrs.containsKey(reqAttr)) {
                                if (tagName.equals("a") && reqAttr.equals("href")) continue;
                                problems.add(new Problem(file, tagLine, tagCol, tagName.length() + 2,
                                        "'<" + tagName + ">' is missing required attribute '" + reqAttr + "'",
                                        Problem.Severity.WARNING));
                            }
                        }
                    }
                }

                // RULE: Empty Tags Check
                if (EMPTY_CHECK_TAGS.contains(tagName) && !KnownElements.VOID_ELEMENTS.contains(tagName)) {
                    if (nonAttrChildCount == 0 || (textChildrenCount == nonAttrChildCount && !hasNonWhitespaceText)) {
                        problems.add(new Problem(file, tagLine, tagCol, tagName.length() + 2,
                                "Empty '<" + tagName + ">': likely unintentional",
                                Problem.Severity.WARNING));
                    }
                }
            }
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

        return problems;
    }

    private static String cleanAttrVal(String val) {
        if (val == null) return null;
        if (val.length() >= 2 && ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'")))) {
            return val.substring(1, val.length() - 1);
        }
        return val;
    }

    private static String extractSnippet(String text, int start, int end) {
        if (text == null || start >= text.length()) return "";
        int s = Math.max(0, start);
        int e = Math.min(text.length(), Math.max(s, end));
        return text.substring(s, e);
    }
}

