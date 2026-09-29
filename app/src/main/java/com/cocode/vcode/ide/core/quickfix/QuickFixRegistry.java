package com.cocode.vcode.ide.core.quickfix;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.diagnostic.ProblemCode;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.views.CodeEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Universal registry resolving actionable QuickFix intentions for ALL diagnostic issues
 * across HTML, CSS, JavaScript, TypeScript, JSON, Markdown, and syntax/bracket mismatches.
 */
public final class QuickFixRegistry {

    private QuickFixRegistry() {
    }

    /**
     * Resolves all applicable quick-fix intentions for the given diagnostic problem.
     * Guaranteed to provide at least one actionable intention for any recognized problem.
     *
     * @param problem diagnostic problem at cursor/gutter
     * @param editor  active code editor view
     * @return non-null list of available quick fixes
     */
    public static List<QuickFix> getFixesForProblem(Problem problem, CodeEditText editor) {
        if (problem == null || editor == null) return Collections.emptyList();

        List<QuickFix> fixes = new ArrayList<>();
        ProblemCode code = problem.getProblemCode();
        String msg = problem.getMessage() != null ? problem.getMessage() : "";

        // Build category-specific fixes
        buildHtmlFixes(fixes, problem, editor, code, msg);
        buildCssFixes(fixes, problem, editor, code, msg);
        buildJsTsFixes(fixes, problem, editor, code, msg);
        buildJsonFixes(fixes, problem, editor, code, msg);
        buildMarkdownFixes(fixes, problem, editor, code, msg);
        buildBracketSyntaxFixes(fixes, problem, editor, code, msg);

        return fixes;
    }

    /**
     * Checks whether the problem has any registered quick fix intentions.
     * Returns true for ALL standardized problem codes and all diagnostic messages.
     */
    public static boolean hasFixes(Problem problem) {
        if (problem == null) return false;
        if (problem.getProblemCode() != null) return true;
        String msg = problem.getMessage();
        return msg != null && !msg.trim().isEmpty();
    }

    // ==========================================
    // 1. HTML Quick Fixes
    // ==========================================
    private static void buildHtmlFixes(List<QuickFix> fixes, Problem problem, CodeEditText editor, ProblemCode code, String msg) {
        // HTML: Missing alt on <img>
        if (code == ProblemCode.HTML_MISSING_ALT || msg.contains("missing 'alt' attribute")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_alt, "Add alt=\"\" attribute");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    insertAttribute(ed, prob, "<img", " alt=\"\"");
                }
            });
        }

        // HTML: Missing type on <button>
        if (code == ProblemCode.HTML_BUTTON_MISSING_TYPE || (msg.contains("missing 'type' attribute") && msg.contains("button"))) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_button_type, "Add type=\"button\" attribute");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    insertAttribute(ed, prob, "<button", " type=\"button\"");
                }
            });
        }

        // HTML: Empty <title> element
        if (code == ProblemCode.HTML_EMPTY_TITLE || msg.contains("Empty '<title>'")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_title, "Add title text");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lineText = ed.getLineText(pLine);
                        int titleIdx = lineText.toLowerCase().indexOf("<title>");
                        if (titleIdx >= 0) {
                            int insertCol = titleIdx + 7;
                            ed.beginAtomicGroup();
                            try {
                                ed.replaceRange(pLine, insertCol, pLine, insertCol, "Document Title");
                            } finally {
                                ed.endAtomicGroup();
                            }
                        }
                    }
                }
            });
        }

        // HTML: Missing lang on <html>
        if (code == ProblemCode.HTML_MISSING_LANG || msg.contains("missing 'lang' attribute")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_lang, "Add lang=\"en\" attribute");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    insertAttribute(ed, prob, "<html", " lang=\"en\"");
                }
            });
        }

        // HTML: Missing src attribute
        if (code == ProblemCode.HTML_MISSING_SRC || msg.contains("missing required attribute 'src'")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_src, "Add src=\"\" attribute");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        int tagIdx = lt.indexOf('<');
                        if (tagIdx >= 0) {
                            int spaceOrClose = lt.indexOf('>', tagIdx);
                            int ws = lt.indexOf(' ', tagIdx);
                            int insertAt = (ws > 0 && ws < spaceOrClose) ? ws : spaceOrClose;
                            if (insertAt > 0) {
                                ed.beginAtomicGroup();
                                try {
                                    ed.replaceRange(pLine, insertAt, pLine, insertAt, " src=\"\"");
                                } finally {
                                    ed.endAtomicGroup();
                                }
                            }
                        }
                    }
                }
            });
        }

        // HTML: Missing content attribute
        if (code == ProblemCode.HTML_MISSING_CONTENT || msg.contains("missing 'content' attribute")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_content, "Add content=\"\" attribute");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    insertAttribute(ed, prob, "<meta", " content=\"\"");
                }
            });
        }

        // HTML: Missing for attribute
        if (code == ProblemCode.HTML_MISSING_FOR || msg.contains("missing 'for' attribute")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_for, "Add for=\"\" attribute");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    insertAttribute(ed, prob, "<label", " for=\"\"");
                }
            });
        }

        // HTML: Missing charset
        if (code == ProblemCode.HTML_MISSING_CHARSET || msg.contains("Missing '<meta charset")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_charset, "Insert <meta charset=\"UTF-8\">");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    insertInHeadOrTop(ed, "    <meta charset=\"UTF-8\">\n");
                }
            });
        }

        // HTML: Missing viewport
        if (code == ProblemCode.HTML_MISSING_VIEWPORT || msg.contains("Missing viewport meta tag")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_viewport, "Insert viewport meta tag");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    insertInHeadOrTop(ed, "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
                }
            });
        }

        // HTML: Missing <title> in head
        if (code == ProblemCode.HTML_MISSING_TITLE || msg.contains("Missing '<title>' in <head>")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_insert_title_tag, "Insert <title> tag");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    insertInHeadOrTop(ed, "    <title>Document Title</title>\n");
                }
            });
        }

        // HTML: Missing meta description
        if (code == ProblemCode.HTML_MISSING_META_DESCRIPTION || msg.contains("meta name=\"description\"")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_meta_desc, "Add meta description");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    insertInHeadOrTop(ed, "    <meta name=\"description\" content=\"\">\n");
                }
            });
        }

        // HTML: Missing required attribute
        if (code == ProblemCode.HTML_MISSING_REQ_ATTR || msg.contains("missing required attribute")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_required_attr, "Add required attribute");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    String attr = extractQuotedName(prob.getMessage());
                    if (attr.isEmpty()) attr = "required";
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        int gt = lt.indexOf('>');
                        if (gt > 0) {
                            int insertCol = (lt.charAt(gt - 1) == '/') ? gt - 1 : gt;
                            ed.beginAtomicGroup();
                            try {
                                ed.replaceRange(pLine, insertCol, pLine, insertCol, " " + attr + "=\"\"");
                            } finally {
                                ed.endAtomicGroup();
                            }
                        }
                    }
                }
            });
        }

        // HTML: Deprecated tag
        if (code == ProblemCode.HTML_DEPRECATED_TAG || (msg.contains("is deprecated — use") && msg.contains("<"))) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_replace_deprecated_tag, "Replace deprecated element");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceAll("(?i)<center>", "<div>")
                                .replaceAll("(?i)</center>", "</div>")
                                .replaceAll("(?i)<font[^>]*>", "<span>")
                                .replaceAll("(?i)</font>", "</span>")
                                .replaceAll("(?i)<strike>", "<del>")
                                .replaceAll("(?i)</strike>", "</del>")
                                .replaceAll("(?i)<tt>", "<code>")
                                .replaceAll("(?i)</tt>", "</code>");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // HTML: Deprecated attribute
        if (code == ProblemCode.HTML_DEPRECATED_ATTR || (msg.contains("attribute on '<") && msg.contains("deprecated"))) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_deprecated_attr, "Remove deprecated attribute");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    removeWordAt(ed, prob, extractQuotedName(prob.getMessage()));
                }
            });
        }

        // HTML: Duplicate attribute
        if (code == ProblemCode.HTML_DUPLICATE_ATTR || msg.contains("Duplicate attribute")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_duplicate_attr, "Remove duplicate attribute");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    removeWordAt(ed, prob, extractQuotedName(prob.getMessage()));
                }
            });
        }

        // HTML: Duplicate ID
        if (code == ProblemCode.HTML_DUPLICATE_ID || msg.contains("Duplicate id=")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_make_id_unique, "Make ID unique");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        int idIdx = lt.indexOf("id=");
                        if (idIdx >= 0) {
                            int q1 = lt.indexOf('"', idIdx + 3);
                            if (q1 < 0) q1 = lt.indexOf('\'', idIdx + 3);
                            if (q1 > 0) {
                                int q2 = lt.indexOf(lt.charAt(q1), q1 + 1);
                                if (q2 > q1) {
                                    ed.beginAtomicGroup();
                                    try {
                                        ed.replaceRange(pLine, q2, pLine, q2, "-2");
                                    } finally {
                                        ed.endAtomicGroup();
                                    }
                                }
                            }
                        }
                    }
                }
            });
        }

        // HTML: Unclosed tag
        if (code == ProblemCode.HTML_UNCLOSED_TAG || msg.contains("Unclosed tag '<")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_close_tag, "Close tag");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    String tagName = extractQuotedName(prob.getMessage()).replace("<", "").replace(">", "");
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, lt.length(), pLine, lt.length(), "</" + tagName + ">");
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // HTML: Void element closing or stray closing tag
        if (code == ProblemCode.HTML_VOID_ELEMENT_CLOSING || code == ProblemCode.HTML_STRAY_CLOSING_TAG
                || msg.contains("void element and cannot have a closing tag") || msg.contains("Stray closing tag")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_stray_closing, "Remove stray closing tag");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        int closeIdx = lt.indexOf("</");
                        if (closeIdx >= 0) {
                            int endIdx = lt.indexOf('>', closeIdx);
                            if (endIdx > closeIdx) {
                                ed.beginAtomicGroup();
                                try {
                                    ed.replaceRange(pLine, closeIdx, pLine, endIdx + 1, "");
                                } finally {
                                    ed.endAtomicGroup();
                                }
                            }
                        }
                    }
                }
            });
        }

        // HTML: Inline style
        if (code == ProblemCode.HTML_INLINE_STYLE || msg.contains("Avoid inline styles on '<")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_inline_style, "Remove inline style");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    removeAttributeByPrefix(ed, prob, "style=");
                }
            });
        }

        // HTML: Inline event handler
        if (code == ProblemCode.HTML_INLINE_EVENT || msg.contains("inline handler on '<")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_inline_event, "Remove inline event handler");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    String handler = extractQuotedName(prob.getMessage());
                    if (!handler.isEmpty()) {
                        removeAttributeByPrefix(ed, prob, handler + "=");
                    }
                }
            });
        }

        // HTML: Required parent
        if (code == ProblemCode.HTML_REQUIRED_PARENT || msg.contains("must be a child of")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_wrap_parent, "Wrap with required parent");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String parent = "div";
                        if (lt.contains("<li")) parent = "ul";
                        else if (lt.contains("<tr") || lt.contains("<td") || lt.contains("<th")) parent = "table";
                        else if (lt.contains("<dt") || lt.contains("<dd")) parent = "dl";
                        else if (lt.contains("<option")) parent = "select";

                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), "<" + parent + ">\n  " + lt.trim() + "\n</" + parent + ">");
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // HTML: Nested <a>
        if (code == ProblemCode.HTML_NESTED_ANCHOR || msg.contains("'<a>' cannot be nested inside another '<a>'")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_nested_anchor_to_span, "Change nested <a> to <span>");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceFirst("<a\\b", "<span").replaceFirst("</a>", "</span>");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // HTML: Redundant type attribute
        if (code == ProblemCode.HTML_REDUNDANT_TYPE_ATTR || msg.contains("is redundant in HTML5 — remove it")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_redundant_type, "Remove redundant type attribute");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    removeAttributeByPrefix(ed, prob, "type=");
                }
            });
        }

        // HTML: Script missing defer
        if (code == ProblemCode.HTML_SCRIPT_MISSING_DEFER || msg.contains("'<script>' without 'defer' or 'async'")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_defer, "Add 'defer' attribute");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    insertAttribute(ed, prob, "<script", " defer");
                }
            });
        }

        // HTML: Target blank security
        if (code == ProblemCode.HTML_TARGET_BLANK_SECURITY || msg.contains("target=\"_blank\" without rel")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_rel_noopener, "Add rel=\"noopener noreferrer\"");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    insertAttribute(ed, prob, "<a", " rel=\"noopener noreferrer\"");
                }
            });
        }

        // HTML: Anchor used as button
        if (code == ProblemCode.HTML_ANCHOR_AS_BUTTON || msg.contains("used as button — use '<button>'")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_anchor_to_button, "Replace with <button type=\"button\">");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceAll("<a\\s+href=[\"']#[\"']", "<button type=\"button\"")
                                .replaceAll("</a>", "</button>");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // HTML: Semantic suggestion
        if (code == ProblemCode.HTML_SEMANTIC_SUGGESTION || msg.contains("could be replaced with '<")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_replace_with_semantic_tag, "Replace <div> with semantic tag");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    String semantic = extractQuotedName(prob.getMessage()).replace("<", "").replace(">", "");
                    if (semantic.isEmpty()) semantic = "section";
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceFirst("<div\\b", "<" + semantic).replaceFirst("</div>", "</" + semantic + ">");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // HTML: Empty tag
        if (code == ProblemCode.HTML_EMPTY_TAG || msg.contains("Empty '<") && msg.contains("likely unintentional")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_empty_tag, "Remove empty element");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    deleteProblemLine(ed, prob);
                }
            });
        }

        // HTML: Lazy loading
        if (code == ProblemCode.HTML_LAZY_LOADING || msg.contains("loading=\"lazy\"")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_lazy_loading, "Add loading=\"lazy\" attribute");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    insertAttribute(ed, prob, "<img", " loading=\"lazy\"");
                }
            });
        }

        // HTML: Missing <thead> in <table>
        if (code == ProblemCode.HTML_MISSING_THEAD || msg.contains("'<table>' has no header row")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_thead, "Add <thead> header row");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        int tblIdx = lt.toLowerCase().indexOf("<table");
                        if (tblIdx >= 0) {
                            int gt = lt.indexOf('>', tblIdx);
                            if (gt >= 0) {
                                ed.beginAtomicGroup();
                                try {
                                    ed.replaceRange(pLine, gt + 1, pLine, gt + 1, "\n  <thead>\n    <tr><th>Heading</th></tr>\n  </thead>");
                                } finally {
                                    ed.endAtomicGroup();
                                }
                            }
                        }
                    }
                }
            });
        }
    }

    // ==========================================
    // 2. CSS Quick Fixes
    // ==========================================
    private static void buildCssFixes(List<QuickFix> fixes, Problem problem, CodeEditText editor, ProblemCode code, String msg) {
        // CSS: Unknown property
        if (code == ProblemCode.CSS_UNKNOWN_PROPERTY || msg.contains("Unknown CSS property")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_unknown_css_prop, "Remove unknown property");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    deleteProblemLine(ed, prob);
                }
            });
        }

        // CSS: Deprecated property (clip, zoom)
        if (code == ProblemCode.CSS_DEPRECATED_PROPERTY || (msg.contains("is deprecated — use '") && !msg.contains("<"))) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_replace_deprecated_css_prop, "Replace deprecated property");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceFirst("\\bclip\\s*:", "clip-path:")
                                .replaceFirst("\\bzoom\\s*:\\s*([^;]+);", "transform: scale($1);");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // CSS: Duplicate property
        if (code == ProblemCode.CSS_DUPLICATE_PROPERTY || msg.contains("Duplicate property")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_duplicate_css_prop, "Remove duplicate property");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    deleteProblemLine(ed, prob);
                }
            });
        }

        // CSS: !important usage
        if (code == ProblemCode.CSS_IMPORTANT_USAGE || msg.contains("!important")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_important, "Remove '!important'");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceAll("\\s*!\\s*important", "");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // CSS: 0px zero unit
        if (code == ProblemCode.CSS_ZERO_UNIT || msg.contains("'0px' — units are unnecessary")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_replace_zero_px, "Replace '0px' with '0'");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceAll("\\b0px\\b", "0");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // CSS: pt unit
        if (code == ProblemCode.CSS_PT_UNIT || msg.contains("Avoid using 'pt' units")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_convert_pt_to_px, "Convert 'pt' to 'px'");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceAll("(?<=\\d)pt\\b", "px");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // CSS: var() without fallback
        if (code == ProblemCode.CSS_VAR_NO_FALLBACK || msg.contains("used without a fallback value")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_var_fallback, "Add fallback to var()");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceAll("var\\((--[^,\\)]+)\\)", "var($1, inherit)");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // CSS: Unused variable
        if (code == ProblemCode.CSS_VAR_UNUSED || (msg.contains("CSS variable") && msg.contains("never used"))) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_unused_css_var, "Remove unused CSS variable");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    deleteProblemLine(ed, prob);
                }
            });
        }

        // CSS: Shortenable hex color
        if (code == ProblemCode.CSS_HEX_SHORTENABLE || msg.contains("can be shortened to '")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_shorten_hex, "Shorten hex color");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceAll("#([0-9a-fA-F])\\1([0-9a-fA-F])\\2([0-9a-fA-F])\\3", "#$1$2$3");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // CSS: Empty rule
        if (code == ProblemCode.CSS_EMPTY_RULE || msg.contains("Empty rule for selector")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_empty_css_rule, "Remove empty CSS rule");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    deleteProblemLine(ed, prob);
                }
            });
        }

        // CSS: Vendor prefix needed
        if (code == ProblemCode.CSS_VENDOR_PREFIX_NEEDED || msg.contains("may need '")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_vendor_prefix, "Add vendor prefix");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String vendor = extractQuotedName(prob.getMessage());
                        if (vendor.isEmpty()) vendor = "-webkit-" + lt.trim().split(":")[0];
                        String prefix = "    " + vendor + ":" + lt.substring(lt.indexOf(':') + 1) + "\n";
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, 0, prefix);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // CSS: Missing background or color
        if (code == ProblemCode.CSS_MISSING_BG_OR_COLOR || msg.contains("is set without 'background-color'") || msg.contains("is set without 'color'")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_missing_color_or_bg, "Add missing background/color declaration");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String decl = prob.getMessage().contains("background-color")
                                ? "    background-color: transparent;\n" : "    color: inherit;\n";
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine + 1, 0, pLine + 1, 0, decl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // CSS: Missing gap
        if (code == ProblemCode.CSS_MISSING_GAP || msg.contains("No 'gap' property in flex/grid rule")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_gap, "Add gap property");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine + 1, 0, pLine + 1, 0, "    gap: 1rem;\n");
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // CSS: Margin auto shorthand
        if (code == ProblemCode.CSS_MARGIN_AUTO_SHORTHAND || msg.contains("margin: 0 auto")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_margin_auto_shorthand, "Use 'margin: 0 auto' shorthand");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), "    margin: 0 auto;");
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // CSS: ID selector
        if (code == ProblemCode.CSS_ID_SELECTOR || msg.contains("Avoid using ID selectors ('#id')")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_id_to_class_selector, "Convert '#id' to '.id' class selector");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        int hIdx = lt.indexOf('#');
                        if (hIdx >= 0) {
                            ed.beginAtomicGroup();
                            try {
                                ed.replaceRange(pLine, hIdx, pLine, hIdx + 1, ".");
                            } finally {
                                ed.endAtomicGroup();
                            }
                        }
                    }
                }
            });
        }
    }

    // ==========================================
    // 3. JavaScript & TypeScript Quick Fixes
    // ==========================================
    private static void buildJsTsFixes(List<QuickFix> fixes, Problem problem, CodeEditText editor, ProblemCode code, String msg) {
        // JS: Const reassignment
        if (code == ProblemCode.JS_CONST_REASSIGNMENT || msg.contains("reassign 'const' variable")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_const_to_let, "Convert 'const' declaration to 'let'");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    String varName = extractQuotedName(prob.getMessage());
                    if (varName.isEmpty()) return;
                    int curLine = Math.max(0, prob.getLine() - 1);
                    for (int l = curLine; l >= 0; l--) {
                        String lt = ed.getLineText(l);
                        int constIdx = lt.indexOf("const ");
                        if (constIdx >= 0 && lt.contains(varName)) {
                            ed.beginAtomicGroup();
                            try {
                                ed.replaceRange(l, constIdx, l, constIdx + 5, "let");
                            } finally {
                                ed.endAtomicGroup();
                            }
                            break;
                        }
                    }
                }
            });
        }

        // JS: Missing semicolon
        if (code == ProblemCode.JS_MISSING_SEMICOLON || msg.toLowerCase().contains("missing semicolon")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_insert_semicolon, "Insert missing ';'");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        int col = prob.getColumn() - 1;
                        int insertCol = (col >= 0 && col <= lt.length()) ? col : lt.length();
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, insertCol, pLine, insertCol, ";");
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // JS: Unused variable
        if (code == ProblemCode.JS_UNUSED_VARIABLE || (msg.contains("never used") && !msg.contains("CSS"))) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_prefix_unused, "Prefix unused identifier with '_'");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    String varName = extractQuotedName(prob.getMessage());
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        int vIdx = !varName.isEmpty() ? lt.indexOf(varName) : -1;
                        if (vIdx < 0 && prob.getColumn() > 0) {
                            vIdx = prob.getColumn() - 1;
                        }
                        if (vIdx >= 0 && vIdx <= lt.length()) {
                            ed.beginAtomicGroup();
                            try {
                                ed.replaceRange(pLine, vIdx, pLine, vIdx, "_");
                            } finally {
                                ed.endAtomicGroup();
                            }
                        }
                    }
                }
            });
        }

        // JS: Undefined variable
        if (code == ProblemCode.JS_UNDEFINED_VARIABLE || msg.contains("is not defined")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_declare_undefined_var, "Declare variable with 'let'");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    String id = extractQuotedName(prob.getMessage());
                    if (!id.isEmpty()) {
                        int pLine = Math.max(0, prob.getLine() - 1);
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, 0, "let " + id + ";\n");
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // JS: Duplicate declaration
        if (code == ProblemCode.JS_DUPLICATE_DECLARATION || msg.contains("has already been declared")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_rename_duplicate_decl, "Rename duplicate declaration");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    String id = extractQuotedName(prob.getMessage());
                    if (!id.isEmpty()) {
                        int pLine = Math.max(0, prob.getLine() - 1);
                        if (pLine < ed.getLineCount()) {
                            String lt = ed.getLineText(pLine);
                            int idx = lt.indexOf(id);
                            if (idx >= 0) {
                                ed.beginAtomicGroup();
                                try {
                                    ed.replaceRange(pLine, idx + id.length(), pLine, idx + id.length(), "2");
                                } finally {
                                    ed.endAtomicGroup();
                                }
                            }
                        }
                    }
                }
            });
        }

        // JS: Console statement
        if (code == ProblemCode.JS_CONSOLE_STATEMENT || msg.contains("Unexpected console statement")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_console, "Remove console statement");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    deleteProblemLine(ed, prob);
                }
            });
        }

        // JS: Division by zero
        if (code == ProblemCode.JS_DIVISION_BY_ZERO || msg.contains("Division by zero")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_division_by_zero, "Replace '0' with '1'");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceAll("/\\s*0\\b", "/ 1");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // JS: Typeof undefined comparison
        if (code == ProblemCode.JS_TYPEOF_UNDEFINED_COMPARISON || msg.contains("Comparing typeof to undefined directly")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_typeof_string, "Compare to 'undefined' string");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceAll("\\bundefined\\b", "'undefined'");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // TS: Type mismatch
        if (code == ProblemCode.TS_TYPE_MISMATCH || msg.contains("Type mismatch: cannot assign")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_update_type_mismatch, "Update type annotation");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    deleteProblemLine(ed, prob);
                }
            });
        }

        // TS: Return any
        if (code == ProblemCode.TS_RETURN_ANY || msg.contains("returns 'any': specify an explicit return type")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_replace_any_return, "Replace 'any' with 'unknown'");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceAll(":\\s*any\\b", ": unknown");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // TS: Missing return type on exported function
        if (code == ProblemCode.TS_MISSING_RETURN_TYPE || msg.contains("is missing a return type annotation")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_void_return_type, "Add ': void' return type annotation");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        int brace = lt.indexOf('{');
                        if (brace > 0) {
                            ed.beginAtomicGroup();
                            try {
                                ed.replaceRange(pLine, brace, pLine, brace, ": void ");
                            } finally {
                                ed.endAtomicGroup();
                            }
                        }
                    }
                }
            });
        }

        // TS: Explicit any type
        if (code == ProblemCode.TS_EXPLICIT_ANY || msg.contains("Explicit 'any' type")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_replace_any_type, "Replace 'any' with 'unknown'");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceAll(":\\s*any\\b", ": unknown");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // TS: Redundant type annotation
        if (code == ProblemCode.TS_REDUNDANT_TYPE || msg.contains("is inferred: remove the explicit annotation")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_redundant_type_ann, "Remove redundant type annotation");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceAll(":\\s*(string|number|boolean)\\b", "");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // TS: Union undefined in parameter
        if (code == ProblemCode.TS_UNION_UNDEFINED || msg.contains("in parameter can be written as")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_convert_optional_param, "Convert to optional parameter syntax");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceAll("(\\w+):\\s*([^\\|]+)\\s*\\|\\s*undefined", "$1?: $2");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // TS: Readonly array
        if (code == ProblemCode.TS_READONLY_ARRAY || msg.contains("Consider 'readonly")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_readonly, "Add 'readonly' modifier");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        int startWs = 0;
                        while (startWs < lt.length() && Character.isWhitespace(lt.charAt(startWs))) startWs++;
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, startWs, pLine, startWs, "readonly ");
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // TS: Broad Function type
        if (code == ProblemCode.TS_BROAD_FUNCTION_TYPE || msg.contains("'Function' type is too broad")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_replace_broad_function_type, "Replace with '() => void'");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceFirst("\\bFunction\\b", "() => void");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // TS: Non-null assertion
        if (code == ProblemCode.TS_NON_NULL_ASSERTION || msg.contains("Non-null assertion '!'")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_replace_non_null_with_optional, "Replace '!' with optional chaining '?.'");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceAll("!\\.", "?.");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }
    }

    // ==========================================
    // 4. JSON Quick Fixes
    // ==========================================
    private static void buildJsonFixes(List<QuickFix> fixes, Problem problem, CodeEditText editor, ProblemCode code, String msg) {
        // JSON: Disallowed comment
        if (code == ProblemCode.JSON_COMMENTS_DISALLOWED || msg.contains("Comments are not permitted in standard JSON")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_json_comment, "Remove disallowed comment");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    deleteProblemLine(ed, prob);
                }
            });
        }

        // JSON: Syntax error
        if (code == ProblemCode.JSON_SYNTAX_ERROR || (msg.contains("Syntax error") && probBelongsToJson(problem))) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_fix_json_syntax, "Fix JSON syntax error");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        // Fix trailing comma or single quotes
                        String repl = lt.replaceAll(",\\s*([}\\]])", "$1")
                                .replaceAll("'([^']*)'", "\"$1\"");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // JSON: Duplicate key
        if (code == ProblemCode.JSON_DUPLICATE_KEY || msg.contains("Duplicate object key")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_duplicate_json_key, "Remove duplicate object key");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    deleteProblemLine(ed, prob);
                }
            });
        }
    }

    // ==========================================
    // 5. Markdown Quick Fixes
    // ==========================================
    private static void buildMarkdownFixes(List<QuickFix> fixes, Problem problem, CodeEditText editor, ProblemCode code, String msg) {
        // MD: Heading level increment
        if (code == ProblemCode.MD_HEADING_INCREMENT || msg.contains("Heading levels should only increment")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_adjust_md_heading, "Adjust heading level");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        if (lt.startsWith("###")) {
                            ed.beginAtomicGroup();
                            try {
                                ed.replaceRange(pLine, 0, pLine, lt.length(), "##" + lt.substring(3));
                            } finally {
                                ed.endAtomicGroup();
                            }
                        }
                    }
                }
            });
        }

        // MD: Empty blockquote
        if (code == ProblemCode.MD_EMPTY_BLOCKQUOTE || msg.contains("Empty blockquote")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_empty_blockquote, "Remove empty blockquote");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    deleteProblemLine(ed, prob);
                }
            });
        }

        // MD: Inconsistent list marker
        if (code == ProblemCode.MD_INCONSISTENT_LIST_MARKER || msg.contains("Inconsistent list marker")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_standardize_list_marker, "Standardize list marker");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceFirst("^(\\s*)[\\*\\+]\\s+", "$1- ");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // MD: Link has no text
        if (code == ProblemCode.MD_EMPTY_LINK_TEXT || msg.contains("Link has no text")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_link_text, "Add link text");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceFirst("\\[\\s*\\]\\(", "[Link](");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // MD: Missing image alt text
        if (code == ProblemCode.MD_MISSING_IMAGE_ALT || msg.contains("Image is missing alt text")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_image_alt, "Add alt text");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceFirst("!\\[\\s*\\]\\(", "![Image description](");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // MD: Useless image alt text
        if (code == ProblemCode.MD_USELESS_IMAGE_ALT || msg.contains("Useless alt text")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_replace_useless_alt, "Replace with descriptive alt text");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceFirst("(?i)!\\[(image|picture|logo|photo|img|pic)\\]\\(", "![Descriptive image text](");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // MD: Raw URL detected
        if (code == ProblemCode.MD_RAW_URL || msg.contains("Raw URL detected")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_enclose_raw_url, "Enclose URL in < >");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceAll("(?<![\\(\\[<])(https?://[^\\s<>()]+)(?![\\)\\]>])", "<$1>");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // MD: Trailing whitespace
        if (code == ProblemCode.MD_TRAILING_WHITESPACE || msg.contains("Trailing whitespace detected")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_trailing_whitespace, "Remove trailing whitespace");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replaceAll("[ \\t]+$", "");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // MD: Hard tab
        if (code == ProblemCode.MD_HARD_TAB || msg.contains("Hard tab detected")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_convert_tabs_to_spaces, "Convert tabs to spaces");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        String repl = lt.replace("\t", "    ");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }
    }

    // ==========================================
    // 6. Bracket & General Syntax Quick Fixes
    // ==========================================
    private static void buildBracketSyntaxFixes(List<QuickFix> fixes, Problem problem, CodeEditText editor, ProblemCode code, String msg) {
        // Bracket Mismatch
        if (code == ProblemCode.BRACKET_MISMATCH || msg.contains("Unclosed") || msg.contains("bracket")
                || msg.contains("brace") || msg.contains("parenthesis") || msg.contains("Mismatched closing")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_fix_bracket_mismatch, "Insert matching bracket");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        char missing = '}';
                        if (prob.getMessage().contains("parenthesis") || prob.getMessage().contains("'('") || prob.getMessage().contains("')'")) {
                            missing = ')';
                        } else if (prob.getMessage().contains("bracket") || prob.getMessage().contains("'['") || prob.getMessage().contains("']'")) {
                            missing = ']';
                        }

                        if (prob.getMessage().contains("Unmatched closing")) {
                            // Delete the unmatched closing bracket
                            int col = Math.max(0, prob.getColumn() - 1);
                            if (col < lt.length()) {
                                ed.beginAtomicGroup();
                                try {
                                    ed.replaceRange(pLine, col, pLine, col + 1, "");
                                } finally {
                                    ed.endAtomicGroup();
                                }
                                return;
                            }
                        }

                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, lt.length(), pLine, lt.length(), String.valueOf(missing));
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // Generic Syntax Error
        if (fixes.isEmpty() || code == ProblemCode.SYNTAX_ERROR || msg.toLowerCase().contains("syntax error")) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_clean_syntax_error, "Clean up syntax error");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        // Clean up trailing commas or unbalanced unclosed quotes
                        String repl = lt.replaceAll(",\\s*([}\\]\\)])", "$1");
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, 0, pLine, lt.length(), repl);
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private static void insertAttribute(CodeEditText ed, Problem prob, String tagPrefix, String attributeToInsert) {
        int pLine = Math.max(0, prob.getLine() - 1);
        if (pLine >= ed.getLineCount()) return;
        String lineText = ed.getLineText(pLine);
        int tagIdx = lineText.toLowerCase().indexOf(tagPrefix.toLowerCase());
        if (tagIdx >= 0) {
            int closeTagIdx = lineText.indexOf('>', tagIdx);
            if (closeTagIdx > 0) {
                int insertCol = closeTagIdx;
                if (lineText.charAt(closeTagIdx - 1) == '/') {
                    insertCol = closeTagIdx - 1;
                }
                ed.beginAtomicGroup();
                try {
                    ed.replaceRange(pLine, insertCol, pLine, insertCol, attributeToInsert);
                } finally {
                    ed.endAtomicGroup();
                }
            }
        }
    }

    private static void insertInHeadOrTop(CodeEditText ed, String snippet) {
        int targetLine = 0;
        int lineCount = ed.getLineCount();
        for (int l = 0; l < lineCount; l++) {
            String lt = ed.getLineText(l).toLowerCase();
            if (lt.contains("<head>")) {
                targetLine = l + 1;
                break;
            }
        }
        ed.beginAtomicGroup();
        try {
            ed.replaceRange(targetLine, 0, targetLine, 0, snippet);
        } finally {
            ed.endAtomicGroup();
        }
    }

    private static void deleteProblemLine(CodeEditText ed, Problem prob) {
        int pLine = Math.max(0, prob.getLine() - 1);
        if (pLine >= 0 && pLine < ed.getLineCount()) {
            String lt = ed.getLineText(pLine);
            ed.beginAtomicGroup();
            try {
                if (pLine + 1 < ed.getLineCount()) {
                    ed.replaceRange(pLine, 0, pLine + 1, 0, "");
                } else {
                    ed.replaceRange(pLine, 0, pLine, lt.length(), "");
                }
            } finally {
                ed.endAtomicGroup();
            }
        }
    }

    private static void removeWordAt(CodeEditText ed, Problem prob, String word) {
        if (word == null || word.isEmpty()) return;
        int pLine = Math.max(0, prob.getLine() - 1);
        if (pLine >= ed.getLineCount()) return;
        String lt = ed.getLineText(pLine);
        int idx = lt.indexOf(word);
        if (idx >= 0) {
            int endIdx = idx + word.length();
            if (endIdx < lt.length() && lt.charAt(endIdx) == '=') {
                endIdx++;
                if (endIdx < lt.length() && (lt.charAt(endIdx) == '"' || lt.charAt(endIdx) == '\'')) {
                    char quote = lt.charAt(endIdx);
                    endIdx = lt.indexOf(quote, endIdx + 1);
                    if (endIdx >= 0) endIdx++;
                    else endIdx = lt.length();
                }
            }
            while (endIdx < lt.length() && Character.isWhitespace(lt.charAt(endIdx))) endIdx++;
            ed.beginAtomicGroup();
            try {
                ed.replaceRange(pLine, idx, pLine, Math.min(lt.length(), endIdx), "");
            } finally {
                ed.endAtomicGroup();
            }
        }
    }

    private static void removeAttributeByPrefix(CodeEditText ed, Problem prob, String prefix) {
        int pLine = Math.max(0, prob.getLine() - 1);
        if (pLine >= ed.getLineCount()) return;
        String lt = ed.getLineText(pLine);
        int idx = lt.indexOf(prefix);
        if (idx >= 0) {
            int end = idx + prefix.length();
            if (end < lt.length() && (lt.charAt(end) == '"' || lt.charAt(end) == '\'')) {
                char q = lt.charAt(end);
                int q2 = lt.indexOf(q, end + 1);
                end = (q2 > 0) ? q2 + 1 : lt.length();
            }
            while (end < lt.length() && Character.isWhitespace(lt.charAt(end))) end++;
            ed.beginAtomicGroup();
            try {
                ed.replaceRange(pLine, idx, pLine, end, "");
            } finally {
                ed.endAtomicGroup();
            }
        }
    }

    private static String extractQuotedName(String msg) {
        if (msg == null) return "";
        int q1 = msg.indexOf('\'');
        if (q1 >= 0) {
            int q2 = msg.indexOf('\'', q1 + 1);
            if (q2 > q1) return msg.substring(q1 + 1, q2);
        }
        return "";
    }

    private static boolean probBelongsToJson(Problem problem) {
        if (problem.getFile() != null) {
            String name = problem.getFile().getName().toLowerCase();
            return name.endsWith(".json") || name.endsWith(".jsonc");
        }
        return false;
    }

    private static String getFixTitle(CodeEditText ed, int resId, String defaultTitle) {
        if (ed != null && ed.getContext() != null) {
            try {
                return ed.getContext().getString(resId);
            } catch (Throwable ignored) {
            }
        }
        return defaultTitle;
    }
}
