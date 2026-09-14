package com.cocode.vcode.ide.core.completion.staticdata;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.ArrayList;
import java.util.List;

/**
 * JS completion dispatcher for the static keyword/builtin
 * dataset.
 *
 * <p>Detects whether the cursor is at a "statement-start" position
 * (the start of a new statement, before any identifier) and, if
 * so, returns the static JS keyword/builtin list.
 *
 * <p>Statement-start detection is text-based: the cursor is at a
 * statement start if it sits at the beginning of the source, right
 * after a {@code ;}, right after a {@code {} of a block, or after
 * a top-level newline. Conditional keyword gating
 * (else/catch/case/extends) is layered on top of this by the
 * caller — the static dispatcher is intentionally position-only
 * and not AST-role aware. Callers filter the result further.
 */
public final class JsStaticCompletionDispatcher {

    private JsStaticCompletionDispatcher() {}

    public enum Position {
        /** Cursor is at a statement-start position. */
        STATEMENT_START,
        /** Cursor is mid-identifier (typing `var foo|`). The static
         *  keyword list is offered only as a side-merge, never as
         *  the primary suggestion. */
        IDENTIFIER,
        /** Cursor is in dot-completion (member access) — the static
         *  keyword list is suppressed entirely. */
        MEMBER_ACCESS,
        /** Cursor is in some other position (e.g. string literal,
         *  comment) — no static suggestions. */
        OTHER
    }

    /**
     * Detect the cursor's position for static-data dispatch.
     *
     * @param source full source text
     * @param stream the pre-lexed token stream
     * @param tree   the parsed JS syntax tree (may be {@code null})
     * @param cursor 0-based source offset of the cursor
     */
    public static Position detectPosition(String source, TokenStream stream,
                                          JsSyntaxTree tree, int cursor) {
        if (source == null || cursor < 0 || cursor > source.length()) return Position.OTHER;
        // Find the last non-whitespace, non-comment token before the
        // cursor.
        int i = cursor - 1;
        while (i >= 0) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) { i--; continue; }
            // No comment-skipping here — the token stream tracks
            // those.
            break;
        }
        if (i < 0) return Position.STATEMENT_START;
        // Look at the previous token (or current if cursor is between
        // tokens).
        if (stream != null && i < stream.length) {
            byte t = stream.types[i];
            if (t == TokenStream.TK_PUNCT) {
                char c = source.charAt(i);
                if (c == ';' || c == '{' || c == '}') return Position.STATEMENT_START;
                if (c == '(' || c == ',' || c == '=') return Position.STATEMENT_START;
            }
            if (t == TokenStream.TK_OPERATOR) return Position.STATEMENT_START;
        }
        // If the previous non-whitespace character is a dot, we're
        // in a member-access context.
        int j = cursor - 1;
        while (j >= 0 && Character.isWhitespace(source.charAt(j))) j--;
        if (j >= 0 && source.charAt(j) == '.') return Position.MEMBER_ACCESS;
        return Position.IDENTIFIER;
    }

    /**
     * Build the static JS keyword/builtin completion list. Used
     * by M.12 (statement-start) and M.13 (filtered for structural
     * keywords). For M.15 (member-access), the caller should
     * not call this at all.
     */
    private static volatile List<CompletionItem> CACHED_COMPLETIONS = null;

    public static void clearCachesForTest() {
        CACHED_COMPLETIONS = null;
    }

    public static List<CompletionItem> buildCompletions() {
        List<CompletionItem> cached = CACHED_COMPLETIONS;
        if (cached != null) return cached;
        
        StaticCompletionItem[] items = StaticCompletionLoader.getJsKeywords();
        List<CompletionItem> out = new ArrayList<>(items.length);
        for (StaticCompletionItem k : items) {
            SnippetCursorParser.Result r = SnippetCursorParser.parse(k.insertText);
            String insertText = r != null ? r.insertText : k.insertText;
            int cursorOffset = r != null ? r.cursorOffset : 0;
            out.add(new CompletionItem(k.label, insertText, k.detail,
                    mapType(k.type), cursorOffset));
        }
        CACHED_COMPLETIONS = out;
        return out;
    }

    private static CompletionItem.Type mapType(String t) {
        if (t == null) return CompletionItem.Type.KEYWORD;
        switch (t) {
            case "KEYWORD": return CompletionItem.Type.KEYWORD;
            case "BUILTIN":  return CompletionItem.Type.BUILTIN;
            case "SNIPPET":  return CompletionItem.Type.SNIPPET;
            default:         return CompletionItem.Type.KEYWORD;
        }
    }

    // --- M.13 structural-keyword gating --------------------------------

    /**
     * Filter the static keyword list down to the labels that are
     * appropriate for the current cursor position. M.13:
     * <ul>
     *   <li>{@code else} / {@code else if} only when the immediately
     *       preceding sibling statement is an {@code if} statement.
     *   <li>{@code catch} / {@code finally} only when the preceding
     *       sibling is the {@code try}-block of a {@code try}
     *       statement.
     *   <li>{@code case} / {@code default} only when the enclosing
     *       node is a {@code switch} body.
     *   <li>{@code extends} only immediately after a class name in
     *       an in-progress {@code class Foo |} parse.
     * </ul>
     *
     * <p>Position-agnostic keywords (the rest of the list) are
     * always offered at statement-start.
     */
    public static List<CompletionItem> filterStructural(
            List<CompletionItem> items, String source, int cursor) {
        if (items == null || items.isEmpty()) return items;
        java.util.Set<String> excluded = excludedStructuralLabels(source, cursor);
        List<CompletionItem> out = new ArrayList<>(items.size());
        for (CompletionItem c : items) {
            if (!excluded.contains(c.getLabel())) out.add(c);
        }
        return out;
    }

    private static java.util.Set<String> excludedStructuralLabels(String source, int cursor) {
        java.util.Set<String> excluded = new java.util.HashSet<>();
        if (source == null) return excluded;
        // Find the last non-whitespace, non-comment text run before
        // the cursor. If it doesn't end with a known structural
        // trigger, exclude the corresponding conditional keyword.
        //
        // Example: "...} if (x) |" → preceding run ends with ")"
        // which is the close of the if's condition → else is allowed.
        //
        // We use a small set of "preceding" texts to disallow:
        //   - if the gap between the last ';' / '{' / '}' and the
        //     cursor contains an 'if' that's not closed → "if" is
        //     still open; else is allowed.
        //   - if the gap contains a complete 'try { ... }' → catch
        //     and finally are allowed.
        //   - if the gap ends inside a switch body → case/default
        //     allowed.
        //
        // The M.13 acceptance checks below cover the four families
        // with conservative text-based heuristics.
        if (!precedingHasIfStatement(source, cursor)) {
            excluded.add("else");
            excluded.add("else if");
        }
        if (!precedingHasTryBlock(source, cursor)) {
            excluded.add("catch");
            excluded.add("finally");
        }
        if (!precedingIsInsideSwitch(source, cursor)) {
            excluded.add("case");
            excluded.add("default");
        }
        if (!precedingIsAfterClassName(source, cursor)) {
            excluded.add("extends");
        }
        return excluded;
    }

    /**
     * Wider preceding context: walk back to find the most recent
     * ';' or '{' (which mark statement boundaries). If neither
     * is found within the window, return the entire prefix.
     */
    private static String precedingContext(String source, int cursor) {
        if (source == null) return "";
        int start = 0;
        int window = 2000;
        int from = Math.max(0, cursor - window);
        for (int i = cursor - 1; i >= from; i--) {
            char c = source.charAt(i);
            if (c == ';' || c == '{') {
                start = i + 1;
                break;
            }
        }
        if (start > cursor) start = cursor;
        return source.substring(start, cursor);
    }

    private static boolean precedingHasIfStatement(String source, int cursor) {
        // The last non-whitespace run before the cursor should be a
        // closing '}' (of the if's body). The text immediately before
        // that '}' (within the body) should start with "if (...)".
        // We use a simple sliding window of 200 chars to find this.
        int look = Math.min(cursor, 200);
        String window = source.substring(cursor - look, cursor);
        // Find the last '}' (the closing of the if's body).
        int closeIdx = window.lastIndexOf('}');
        if (closeIdx < 0) return false;
        // Inside the body, the 'if (' should appear.
        return window.contains("if (");
    }

    private static boolean precedingHasTryBlock(String source, int cursor) {
        int look = Math.min(cursor, 200);
        String window = source.substring(cursor - look, cursor);
        int closeIdx = window.lastIndexOf('}');
        if (closeIdx < 0) return false;
        return window.contains("try {");
    }

    private static boolean precedingIsInsideSwitch(String source, int cursor) {
        if (source == null) return false;
        int depth = 0;
        boolean inSwitch = false;
        for (int i = 0; i < cursor; i++) {
            char c = source.charAt(i);
            if (c == '{') {
                if (depth == 0) {
                    // Check if this is a switch body by looking at
                    // the preceding text for "switch".
                    int lookStart = Math.max(0, i - 200);
                    String pre = source.substring(lookStart, i);
                    if (pre.contains("switch")) inSwitch = true;
                }
                depth++;
            } else if (c == '}') {
                if (depth > 0) depth--;
                if (depth == 0) inSwitch = false;
            }
        }
        return inSwitch && depth > 0;
    }

    private static boolean precedingIsAfterClassName(String source, int cursor) {
        // `class Foo |` — cursor directly after an identifier
        // following the `class` keyword, no `{` yet.
        String ctx = precedingContext(source, cursor).trim();
        if (ctx.startsWith("class ")) {
            // If there's no '{' yet, we're in declaration position.
            if (!ctx.contains("{")) return true;
        }
        return false;
    }
}
