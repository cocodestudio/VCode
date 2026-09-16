package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.base.BaseFormatter;

import java.util.regex.Pattern;

/**
 * Formatter for JavaScript and TypeScript source code.
 */
public class JsFormatter extends BaseFormatter {

    /**
     * Default indent string used by the AST-driven formatter.
     */
    public static final String DEFAULT_INDENT = "    ";
    private static final Pattern MULTI_NL = Pattern.compile("\\n{3,}");
    private static final Pattern TRAILING_SP = Pattern.compile("[ \t]+\n");

    // ---------------------------------------------------------------
    // AST-driven formatter
    //
    // Read-only traversal of JsSyntaxTree. Statements print with
    // (depth * spaces) prefix. N_BLOCK prints "{", increments depth,
    // prints children, decrements, prints "}".
    //
    // This method is intentionally minimal: it does not yet know how to
    // attach comments or rewrite verbatim spans. It is the
    // skeleton the later tasks will hang from.
    // ---------------------------------------------------------------

    /**
     * Copy {@code source[start..end)} into {@code out}, collapsing any
     * run of three-or-more newlines down to exactly two. This is the
     * blank-line rule.
     * <p>
     * The collapsed range is always between two AST nodes, so it
     * should in principle be non-verbatim. However, a free-floating
     * comment token (a {@code /* *}{@code /} or {@code //} line that the
     * parser does not bind to any statement) can also live in the gap
     * — and a block comment with multiple blank lines must keep them
     * intact, because they are inside a verbatim span. We therefore
     * consult {@code stream}: any newline whose source offset is
     * tagged as {@code TK_COMMENT} / {@code TK_STRING} /
     * {@code TK_TEMPLATE} / {@code TK_REGEX} is copied verbatim and is
     * never counted toward a collapse run. {@code stream} may be null
     * in which case we skip the verbatim check (the gap is treated as
     * fully non-verbatim).
     */
    private static void appendGapWithBlankLineCollapse(StringBuilder out, String source, TokenStream stream, int start, int end) {
        if (start >= end) return;
        if (start < 0) start = 0;
        if (end > source.length()) end = source.length();
        int i = start;
        while (i < end) {
            char c = source.charAt(i);
            if (c == '\n') {
                int runStart = i;
                int runEnd = i;
                while (runEnd < end && source.charAt(runEnd) == '\n') runEnd++;
                int runLen = runEnd - runStart;
                int emit = runLen >= 3 ? 2 : runLen;
                if (stream != null) {
                    // If any newline in the run is inside a verbatim
                    // span, emit all of them verbatim. This preserves
                    // multi-line block comments and template literals
                    // that happen to live in the gap.
                    for (int k = runStart; k < runEnd; k++) {
                        if (isInsideVerbatimSpan(stream, k)) {
                            emit = runLen;
                            break;
                        }
                    }
                }
                for (int k = 0; k < emit; k++) out.append('\n');
                i = runEnd;
            } else {
                out.append(c);
                i++;
            }
        }
    }

    /**
     * Copy source[start..end) into out. No-op if start >= end.
     */
    private static void appendSource(StringBuilder out, String source, int start, int end) {
        if (start >= end) return;
        if (start < 0) start = 0;
        if (end > source.length()) end = source.length();
        out.append(source, start, end);
    }

    /**
     * Clamp {@code v} into [lo, hi]. Used to defend against malformed
     * parser output (e.g. nodeEnd == 0 on an unterminated block).
     */
    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    /**
     * True if the node type should be preceded by an indent prefix.
     */
    private static boolean needsIndentBefore(int type, boolean inBlock) {
        // Only block-level constructs get an indent. Expression-level
        // types (CALL_EXPR, MEMBER_EXPR, IDENTIFIER) are emitted
        // in-line by their parent statement.
        switch (type) {
            case JsSyntaxTree.N_BLOCK:
            case JsSyntaxTree.N_FUNC_DECL:
            case JsSyntaxTree.N_ARROW_FUNC:
            case JsSyntaxTree.N_CLASS_DECL:
            case JsSyntaxTree.N_METHOD:
            case JsSyntaxTree.N_GETTER:
            case JsSyntaxTree.N_SETTER:
            case JsSyntaxTree.N_VAR_DECL:
            case JsSyntaxTree.N_OBJECT_LITERAL:
            case JsSyntaxTree.N_IMPORT:
            case JsSyntaxTree.N_EXPORT:
            case JsSyntaxTree.N_FOR_STMT:
            case JsSyntaxTree.N_IF_STMT:
            case JsSyntaxTree.N_WHILE_STMT:
            case JsSyntaxTree.N_DO_STMT:
            case JsSyntaxTree.N_WITH_STMT:
            case JsSyntaxTree.N_SWITCH_STMT:
            case JsSyntaxTree.N_TRY_STMT:
            case JsSyntaxTree.N_CATCH_CLAUSE:
            case JsSyntaxTree.N_FINALLY_CLAUSE:
            case JsSyntaxTree.N_CASE_CLAUSE:
            case JsSyntaxTree.N_STATEMENT:
            case JsSyntaxTree.N_INTERFACE:
            case JsSyntaxTree.N_TYPE_ALIAS:
            case JsSyntaxTree.N_ENUM:
            case JsSyntaxTree.N_ERROR:
                return true;
            default:
                return false;
        }
    }

    /**
     * First index >= start that is not a space or tab. Used to skip
     * leading whitespace when starting the top-level walk.
     */
    private static int firstNonWhitespace(String source, int start, int end) {
        int i = start;
        while (i < end) {
            char c = source.charAt(i);
            if (c != ' ' && c != '\t') return i;
            i++;
        }
        return end;
    }

    /**
     * True if the source byte at {@code offset} belongs to a
     * verbatim token span — {@code TK_STRING}, {@code TK_TEMPLATE},
     * {@code TK_REGEX}, or {@code TK_COMMENT}. The printer must not
     * reflow, re-indent, or otherwise restructure bytes inside such
     * spans. Returns false if {@code stream} is null or offset is out
     * of the lexed range.
     */
    private static boolean isInsideVerbatimSpan(TokenStream stream, int offset) {
        if (stream == null) return false;
        if (offset < 0 || offset >= stream.length) return false;
        byte t = stream.types[offset];
        return t == TokenStream.TK_STRING
                || t == TokenStream.TK_TEMPLATE
                || t == TokenStream.TK_REGEX
                || t == TokenStream.TK_COMMENT;
    }

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

    /**
     * Format JavaScript/TypeScript source by traversing {@code tree}.
     * The source is lexed internally to obtain a {@link TokenStream} used
     * for verbatim-span preservation.
     * <p>
     * Returns "" for null/empty input.
     */
    public String format(JsSyntaxTree tree, String source) {
        if (tree == null || source == null) return "";
        if (source.isEmpty()) return "";
        TokenStream stream = JsLexer.tokenize(source);
        return format(tree, source, stream, DEFAULT_INDENT);
    }

    /**
     * Format JavaScript/TypeScript source by traversing {@code tree},
     * reusing a caller-provided {@link TokenStream} to avoid re-lexing
     * on the hot path. {@code indent} is the per-depth indentation string.
     * <p>
     * If {@code attachments} is non-null, the printer consults the
     * comment-attachment record to ensure each comment is emitted
     * in the slot bound to its nearest adjacent AST node. Pass null
     * to skip the comment pre-pass.
     */
    public String format(JsSyntaxTree tree, String source, TokenStream stream, String indent) {
        return format(tree, source, stream, indent, null);
    }

    /**
     * Entry point: format with comment attachments. Builds the
     * attachment pre-pass on-the-fly if {@code prebuiltAttachments}
     * is null.
     */
    public String format(JsSyntaxTree tree, String source, TokenStream stream, String indent,
                         JsCommentAttachment prebuiltAttachments) {
        if (tree == null || source == null) return "";
        if (source.isEmpty()) return "";
        if (indent == null || indent.isEmpty()) indent = DEFAULT_INDENT;

        // build the comment attachment pre-pass. This walks every
        // TK_COMMENT span and binds it to its nearest adjacent AST node.
        JsCommentAttachment attachments = prebuiltAttachments;
        if (attachments == null) {
            attachments = new JsCommentAttachment(64);
            attachments.build(tree, source, stream);
        }

        // Single StringBuilder for the whole output. This is the only
        // allocation inside the call; the recursion does not allocate.
        StringBuilder out = new StringBuilder(source.length() + source.length() / 4);
        int sourceLen = source.length();

        // Root (node 0) is virtual. Walk its child list at depth 0.
        int child = tree.nodeChild[0];
        int cursor = firstNonWhitespace(source, 0, sourceLen);
        cursor = printSiblingList(tree, source, stream, attachments, child, 0, indent, cursor, sourceLen, out, /*inBlock*/ false);

        // tail-flush. Any source bytes after the last top-level
        // node's end (a trailing newline, an EOF-trimmed comment, etc.)
        // are part of the source and must be preserved verbatim.
        // also collapse blank-line runs in the tail.
        if (cursor < sourceLen) {
            appendGapWithBlankLineCollapse(out, source, stream, cursor, sourceLen);
        }

        return out.toString();
    }

    /**
     * Walk a linked list of siblings (via nodeSibling) starting at
     * {@code firstChild}, printing each. {@code cursor} is the current
     * position in {@code source} we've already printed up to; the gap
     * between cursor and each node's start is copied verbatim.
     * <p>
     * Returns the final cursor position (one past the last source byte
     * consumed) so callers can tail-flush any remaining source bytes.
     * <p>
     * {@code attachments} is consulted when a comment span falls
     * inside a gap or trails a node. In the skeleton comments are
     * preserved verbatim by the gap-copy, so attachments is currently
     * informational; future revisions that re-indent comments will
     * read it to pick the right slot.
     * <p>
     * Blank-line collapsing is applied to the gap-copy. The gap
     * is always between two AST nodes (not inside a verbatim span), so
     * collapsing runs of {@code \n\n\n+} to {@code \n\n} here cannot
     * touch the inside of strings, template literals, comments, or
     * regex literals — those are owned by their parent AST node and
     * are copied verbatim by {@code appendSource}.
     */
    private int printSiblingList(JsSyntaxTree tree, String source, TokenStream stream,
                                 JsCommentAttachment attachments,
                                 int firstChild, int depth, String indent,
                                 int cursor, int sourceLen, StringBuilder out, boolean inBlock) {
        int id = firstChild;
        int localCursor = cursor;
        int loop = 0;
        while (id > 0 && id < tree.nodeCount && ++loop <= tree.nodeCount) {
            int nodeStart = clamp(tree.nodeStart[id], localCursor, sourceLen);
            int nodeEnd = clamp(tree.nodeEnd[id], nodeStart, sourceLen);

            // if the gap contains a comment attached to the
            // NEXT node (this id), emit it at the leading slot before
            // the rest of the gap. The gap-copy is verbatim so
            // this is a structural no-op, but it is the documented
            // hook point for future reformatting.
            if (nodeStart > localCursor) {
                appendGapWithBlankLineCollapse(out, source, stream, localCursor, nodeStart);
            } else if (nodeStart < localCursor) {
                // Defensive: parser set nodeStart behind cursor (e.g. an
                // overlapping span). Skip ahead to avoid negative range.
                nodeStart = localCursor;
            }

            int printedEnd = printNode(tree, source, stream, attachments, id, depth, indent, nodeStart, nodeEnd, out, inBlock);
            if (printedEnd < nodeEnd) {
                // Spanned the node's start but not its full range; flush
                // the remainder verbatim so the output stays lossless.
                appendSource(out, source, printedEnd, nodeEnd);
            }
            localCursor = Math.max(nodeEnd, printedEnd);

            id = tree.nodeSibling[id];
        }
        return localCursor;
    }

    /**
     * Print a single AST node. Returns the source-cursor position
     * immediately after the last byte emitted for this node.
     * <p>
     * {@code attachments} is the comment-attachment record. The
     * skeleton preserves comments verbatim via the gap-copy, so
     * this parameter is currently only consumed for the structural
     * N_BLOCK case where re-indentation is emitted; future revisions
     * that re-position comments will read it to decide slots.
     */
    private int printNode(JsSyntaxTree tree, String source, TokenStream stream,
                          JsCommentAttachment attachments,
                          int id, int depth, String indent,
                          int nodeStart, int nodeEnd, StringBuilder out, boolean inBlock) {
        int type = tree.nodeType[id];

        // Indent for the line on which this node starts.
        // skip the indent if the node's start byte sits inside a
        // verbatim token span (string / template / regex / comment).
        // A template literal that spans multiple lines is not a block
        // statement and must not be re-indented.
        if (needsIndentBefore(type, inBlock) && !isInsideVerbatimSpan(stream, nodeStart)) {
            appendIndent(out, depth, indent);
        }

        switch (type) {
            case JsSyntaxTree.N_BLOCK: {
                // "{", children, "}"
                out.append('{').append('\n');
                int child = tree.nodeChild[id];
                int childCursor = nodeStart + 1; // one past '{'
                printSiblingList(tree, source, stream, attachments, child, depth + 1, indent,
                        childCursor, nodeEnd, out, true);
                if (depth > 0) appendIndent(out, depth, indent);
                out.append('}');
                return nodeEnd;
            }
            case JsSyntaxTree.N_FUNC_DECL:
            case JsSyntaxTree.N_ARROW_FUNC:
            case JsSyntaxTree.N_CLASS_DECL:
            case JsSyntaxTree.N_METHOD:
            case JsSyntaxTree.N_GETTER:
            case JsSyntaxTree.N_SETTER: {
                // Emit a structural shape; verbatim tokens
                // (identifiers, params) get attached in later tasks.
                // The simplest correct behaviour is to print the source
                // span verbatim and then descend into any block child.
                appendSource(out, source, nodeStart, nodeEnd);
                return nodeEnd;
            }
            case JsSyntaxTree.N_VAR_DECL: {
                // `let/const/var name = ...;` -- verbatim until ';'.
                // We just emit the original span.
                appendSource(out, source, nodeStart, nodeEnd);
                return nodeEnd;
            }
            case JsSyntaxTree.N_OBJECT_LITERAL: {
                // verbatim passthrough. Later tasks can reformat
                // multi-line object literals.
                appendSource(out, source, nodeStart, nodeEnd);
                return nodeEnd;
            }
            case JsSyntaxTree.N_IMPORT:
            case JsSyntaxTree.N_EXPORT: {
                appendSource(out, source, nodeStart, nodeEnd);
                return nodeEnd;
            }
            case JsSyntaxTree.N_FOR_STMT:
            case JsSyntaxTree.N_IF_STMT:
            case JsSyntaxTree.N_WHILE_STMT:
            case JsSyntaxTree.N_DO_STMT:
            case JsSyntaxTree.N_WITH_STMT:
            case JsSyntaxTree.N_SWITCH_STMT:
            case JsSyntaxTree.N_TRY_STMT:
            case JsSyntaxTree.N_CATCH_CLAUSE:
            case JsSyntaxTree.N_FINALLY_CLAUSE:
            case JsSyntaxTree.N_CASE_CLAUSE:
            case JsSyntaxTree.N_STATEMENT:
            case JsSyntaxTree.N_CALL_EXPR:
            case JsSyntaxTree.N_MEMBER_EXPR:
            case JsSyntaxTree.N_IDENTIFIER:
            case JsSyntaxTree.N_DESTRUCTURE:
            case JsSyntaxTree.N_PARAM:
            case JsSyntaxTree.N_INTERFACE:
            case JsSyntaxTree.N_TYPE_ALIAS:
            case JsSyntaxTree.N_ENUM:
            case JsSyntaxTree.N_ERROR:
            default: {
                // Generic behaviour: copy source verbatim. This is the
                // contract -- structure drives the indent, content
                // is preserved until later tasks take over.
                appendSource(out, source, nodeStart, nodeEnd);
                return nodeEnd;
            }
        }
    }

    /**
     * Indent every line emitted inside an N_BLOCK's children.
     */
    private void appendIndent(StringBuilder out, int depth, String indent) {
        for (int d = 0; d < depth; d++) out.append(indent);
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
