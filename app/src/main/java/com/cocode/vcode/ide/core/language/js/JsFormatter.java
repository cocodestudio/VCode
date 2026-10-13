package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.base.BaseFormatter;

import java.util.ArrayList;
import java.util.List;
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

    public JsFormatter() {
        super();
    }

    public JsFormatter(String indent) {
        super();
        setIndentUnit(indent);
    }

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
        int parenDepth = 0;
        int singleLineBraceDepth = 0;
        int bracketDepth = 0;
        int totalBraceDepth = 0;
        long multilineObjectMask = 0L;

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
                out.append(c);
                continue;
            }

            // Static import statements
            if (parenDepth == 0 && isImportStart(code, i)) {
                int end = findStatementEnd(code, i);
                String rawStmt = code.substring(i, end);
                String normStmt = normalizeImportExportStatement(rawStmt);
                trimTrailingSpace(out);
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
                out.append(normStmt).append('\n');
                i = end - 1;
                continue;
            }

            // Export clause statements (export { ... } / export type { ... })
            if (parenDepth == 0 && isExportClauseStart(code, i)) {
                int end = findStatementEnd(code, i);
                String rawStmt = code.substring(i, end);
                String normStmt = normalizeImportExportStatement(rawStmt);
                trimTrailingSpace(out);
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
                out.append(normStmt).append('\n');
                i = end - 1;
                continue;
            }

            // Collapse whitespace
            if (c == '\t' || c == '\r') {
                if (out.length() > 0 && out.charAt(out.length() - 1) != ' '
                        && out.charAt(out.length() - 1) != '('
                        && out.charAt(out.length() - 1) != '['
                        && out.charAt(out.length() - 1) != '\n') {
                    out.append(' ');
                }
                continue;
            }
            if (c == '\n') {
                if (parenDepth > 0 && multilineObjectMask == 0) {
                    if (out.length() > 0 && out.charAt(out.length() - 1) != ' '
                            && out.charAt(out.length() - 1) != '('
                            && out.charAt(out.length() - 1) != '['
                            && out.charAt(out.length() - 1) != '\n') {
                        out.append(' ');
                    }
                    continue;
                }
                // When line ends with a member dot, transfer the dot to the next line for leading dot style
                if (out.length() > 0 && out.charAt(out.length() - 1) == '.') {
                    boolean isSpread = (out.length() >= 3 && out.charAt(out.length() - 2) == '.' && out.charAt(out.length() - 3) == '.');
                    boolean isFloat = (out.length() >= 2 && Character.isDigit(out.charAt(out.length() - 2)));
                    if (!isSpread && !isFloat) {
                        boolean isOpt = (out.length() >= 2 && out.charAt(out.length() - 2) == '?');
                        if (isOpt) {
                            out.delete(out.length() - 2, out.length());
                            trimTrailingSpace(out);
                            out.append("\n?.");
                        } else {
                            out.deleteCharAt(out.length() - 1);
                            trimTrailingSpace(out);
                            out.append("\n.");
                        }
                        continue;
                    }
                }
                // Keep at most one newline
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
                continue;
            }
            if (c == ' ') {
                if (out.length() > 0 && (out.charAt(out.length() - 1) == ' '
                        || out.charAt(out.length() - 1) == '('
                        || out.charAt(out.length() - 1) == '['
                        || out.charAt(out.length() - 1) == '\n')) {
                    continue;
                }
            }

            // Parentheses tracking
            if (c == '(') {
                parenDepth++;
                out.append('(');
                while (i + 1 < len && (code.charAt(i + 1) == ' ' || code.charAt(i + 1) == '\t'
                        || code.charAt(i + 1) == '\n' || code.charAt(i + 1) == '\r')) {
                    i++;
                }
                continue;
            }
            if (c == ')') {
                parenDepth = Math.max(0, parenDepth - 1);
                trimTrailingSpace(out);
                out.append(')');
                continue;
            }

            // Bracket tracking: index access (arr[i]) vs array literal
            if (c == '[') {
                bracketDepth++;
                int k = out.length() - 1;
                while (k >= 0 && out.charAt(k) == ' ') k--;
                char prevChar = k >= 0 ? out.charAt(k) : 0;
                boolean isIndexAccess = Character.isLetterOrDigit(prevChar)
                        || prevChar == '_' || prevChar == '$'
                        || prevChar == ')' || prevChar == ']';

                if (isIndexAccess) {
                    trimTrailingSpace(out);
                    out.append('[');
                } else {
                    ensureSpace(out);
                    out.append('[');
                }
                while (i + 1 < len && (code.charAt(i + 1) == ' ' || code.charAt(i + 1) == '\t'
                        || code.charAt(i + 1) == '\n' || code.charAt(i + 1) == '\r')) {
                    i++;
                }
                continue;
            }
            if (c == ']') {
                bracketDepth = Math.max(0, bracketDepth - 1);
                trimTrailingSpace(out);
                out.append(']');
                continue;
            }

            // Structural characters
            if (c == '{') {
                totalBraceDepth++;
                if (isSingleLineBrace(code, i)) {
                    singleLineBraceDepth++;
                    ensureSpace(out);
                    out.append("{ ");
                    while (i + 1 < len && (code.charAt(i + 1) == ' ' || code.charAt(i + 1) == '\t')) {
                        i++;
                    }
                    continue;
                }
                if (isObjectOrDestructuringBrace(code, i)) {
                    multilineObjectMask |= (1L << Math.min(62, totalBraceDepth));
                    ensureSpace(out);
                    out.append("{\n");
                    while (i + 1 < len && (code.charAt(i + 1) == ' ' || code.charAt(i + 1) == '\t'
                            || code.charAt(i + 1) == '\n' || code.charAt(i + 1) == '\r')) {
                        i++;
                    }
                    continue;
                }
                ensureSpace(out);
                out.append(c).append('\n');
                continue;
            }
            if (c == '}') {
                if (singleLineBraceDepth > 0) {
                    singleLineBraceDepth--;
                    totalBraceDepth = Math.max(0, totalBraceDepth - 1);
                    trimTrailingSpace(out);
                    if (out.length() > 0 && out.charAt(out.length() - 1) != '{') {
                        out.append(' ');
                    }
                    out.append('}');
                    int j = i + 1;
                    while (j < len && (code.charAt(j) == ' ' || code.charAt(j) == '\t')) j++;
                    char next = j < len ? code.charAt(j) : 0;
                    if (next == '=' || next == ';' || next == ',' || next == ')' || next == ']') {
                        if (next == '=') out.append(' ');
                    } else {
                        String rem = j < len ? code.substring(j) : "";
                        if (rem.startsWith("from")) {
                            out.append(' ');
                        }
                    }
                    continue;
                }

                boolean isMultilineObject = (multilineObjectMask & (1L << Math.min(62, totalBraceDepth))) != 0;
                multilineObjectMask &= ~(1L << Math.min(62, totalBraceDepth));
                totalBraceDepth = Math.max(0, totalBraceDepth - 1);

                trimTrailingSpace(out);
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
                out.append(c);
                int j = i + 1;
                while (j < len && (code.charAt(j) == ' ' || code.charAt(j) == '\t')) j++;
                char next = j < len ? code.charAt(j) : 0;
                if (next == ';' || next == ',' || next == ')' || next == ']' || next == '}') {
                    // stay same line — nothing
                } else if (next == '.' || next == '?') {
                    // method chaining — stay same line
                } else if (isMultilineObject && (next == 0 || next == '\n' || next == '\r') && parenDepth == 0 && bracketDepth == 0) {
                    out.append(';').append('\n');
                    continue;
                } else {
                    String rem = j < len ? code.substring(j) : "";
                    if (rem.startsWith("else") || rem.startsWith("catch") || rem.startsWith("finally") || rem.startsWith("from")) {
                        out.append(' ');
                    } else {
                        out.append('\n');
                    }
                }
                continue;
            }
            if (c == ';') {
                trimTrailingSpace(out);
                out.append(';');
                if (parenDepth > 0) {
                    int nextIdx = i + 1;
                    while (nextIdx < len && (code.charAt(nextIdx) == ' ' || code.charAt(nextIdx) == '\t'
                            || code.charAt(nextIdx) == '\n' || code.charAt(nextIdx) == '\r')) nextIdx++;
                    char nextChar = nextIdx < len ? code.charAt(nextIdx) : 0;
                    if (nextChar != ')') {
                        out.append(' ');
                    }
                    while (i + 1 < len && (code.charAt(i + 1) == ' ' || code.charAt(i + 1) == '\t'
                            || code.charAt(i + 1) == '\n' || code.charAt(i + 1) == '\r')) {
                        i++;
                    }
                } else {
                    out.append('\n');
                }
                continue;
            }
            // Comma: space after, newline if at statement level in multiline object
            if (c == ',') {
                trimTrailingSpace(out);
                boolean inMultilineObj = (multilineObjectMask & (1L << Math.min(62, totalBraceDepth))) != 0;
                if (inMultilineObj && singleLineBraceDepth == 0 && parenDepth == 0 && bracketDepth == 0) {
                    out.append(',').append('\n');
                } else {
                    out.append(',').append(' ');
                }
                while (i + 1 < len && (code.charAt(i + 1) == ' ' || code.charAt(i + 1) == '\t'
                        || code.charAt(i + 1) == '\n' || code.charAt(i + 1) == '\r')) {
                    i++;
                }
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

            // Triple equals (===) and strict not equals (!==)
            if ((c == '=' || c == '!') && i + 2 < len && code.charAt(i + 1) == '=' && code.charAt(i + 2) == '=') {
                ensureSpace(out);
                out.append(c).append("==");
                i += 2;
                ensureSpace(out);
                continue;
            }

            // Increment / Decrement operators: ++ and --
            if ((c == '+' || c == '-') && i + 1 < len && code.charAt(i + 1) == c) {
                int k = out.length() - 1;
                while (k >= 0 && out.charAt(k) == ' ') k--;
                char prevChar = k >= 0 ? out.charAt(k) : 0;
                boolean isPostfix = Character.isLetterOrDigit(prevChar)
                        || prevChar == '_' || prevChar == '$'
                        || prevChar == ')' || prevChar == ']';

                if (isPostfix) {
                    trimTrailingSpace(out);
                    out.append(c).append(c);
                    int nextIdx = i + 2;
                    while (nextIdx < len && (code.charAt(nextIdx) == ' ' || code.charAt(nextIdx) == '\t'
                            || code.charAt(nextIdx) == '\n' || code.charAt(nextIdx) == '\r')) nextIdx++;
                    char nextChar = nextIdx < len ? code.charAt(nextIdx) : 0;
                    if (nextChar != ';' && nextChar != ')' && nextChar != ']' && nextChar != ',' && nextChar != ':' && nextChar != 0) {
                        out.append(' ');
                    }
                    if (nextChar == ';' || nextChar == ')' || nextChar == ']' || nextChar == ',' || nextChar == ':') {
                        while (i + 2 < len && (code.charAt(i + 2) == ' ' || code.charAt(i + 2) == '\t'
                                || code.charAt(i + 2) == '\n' || code.charAt(i + 2) == '\r')) {
                            i++;
                        }
                    }
                } else {
                    if (out.length() > 0 && out.charAt(out.length() - 1) != ' '
                            && out.charAt(out.length() - 1) != '\n'
                            && out.charAt(out.length() - 1) != '('
                            && out.charAt(out.length() - 1) != '[') {
                        out.append(' ');
                    }
                    out.append(c).append(c);
                    while (i + 2 < len && (code.charAt(i + 2) == ' ' || code.charAt(i + 2) == '\t'
                            || code.charAt(i + 2) == '\n' || code.charAt(i + 2) == '\r')) {
                        i++;
                    }
                }
                i++;
                continue;
            }

            // Colon: trim preceding spaces, space after for key: value or ternary/types
            if (c == ':') {
                trimTrailingSpace(out);
                out.append(':');
                int nextIdx = i + 1;
                while (nextIdx < len && (code.charAt(nextIdx) == ' ' || code.charAt(nextIdx) == '\t')) {
                    nextIdx++;
                }
                char nextChar = nextIdx < len ? code.charAt(nextIdx) : 0;
                if (nextChar != ':' && nextChar != '=' && nextChar != '\n' && nextChar != '\r' && nextChar != 0) {
                    out.append(' ');
                }
                i = nextIdx - 1;
                continue;
            }

            // Question mark: ternary or nullish coalescing
            if (c == '?') {
                if (i + 1 < len && (code.charAt(i + 1) == '.' || code.charAt(i + 1) == ':')) {
                    out.append('?');
                    continue;
                }
                if (i + 1 < len && code.charAt(i + 1) == '?') {
                    ensureSpace(out);
                    out.append("??");
                    i++;
                    ensureSpace(out);
                    continue;
                }
                ensureSpace(out);
                out.append('?');
                ensureSpace(out);
                continue;
            }

            // Operators: surround with spaces (simple heuristic)
            if ((c == '=' || c == '+' || c == '-' || c == '*' || c == '/' || c == '%'
                    || c == '&' || c == '|' || c == '<' || c == '>' || c == '!')
                    && i + 1 < len) {
                char next = code.charAt(i + 1);
                boolean compound = (next == '=' || next == '>'
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

        String rawNorm = out.toString();
        String[] rawLines = rawNorm.split("\n", -1);
        StringBuilder broken = new StringBuilder(rawNorm.length() + 64);
        for (int idx = 0; idx < rawLines.length; idx++) {
            String l = rawLines[idx];
            if (idx > 0) broken.append('\n');
            broken.append(breakMultipleDotChains(l));
        }
        return broken.toString();
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

            int effectiveDepth = depth;
            if (line.startsWith(".") || line.startsWith("?.")) {
                effectiveDepth = depth + 1;
            }
            String pad = getIndentString(effectiveDepth);

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

    /**
     * Splits chained member expressions with 2 or more dots onto separate lines,
     * leaving the receiver on the initial line and each chained dot access on its own line.
     * Expressions with fewer than 2 dots (e.g. {@code console.log}) remain untouched.
     */
    public static String breakMultipleDotChains(String line) {
        if (line == null || line.indexOf('.') == -1) return line;

        int len = line.length();
        boolean inString = false;
        char quoteChar = 0;
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;

        int[] dotOffsets = new int[32];
        int dotCount = 0;

        for (int i = 0; i < len; i++) {
            char c = line.charAt(i);

            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == quoteChar) {
                    inString = false;
                }
                continue;
            }

            if (c == '\'' || c == '"' || c == '`') {
                inString = true;
                quoteChar = c;
                continue;
            }

            if (c == '/' && i + 1 < len && line.charAt(i + 1) == '/') {
                break;
            }

            if (c == '(') { parenDepth++; continue; }
            if (c == ')') { if (parenDepth > 0) parenDepth--; continue; }
            if (c == '[') { bracketDepth++; continue; }
            if (c == ']') { if (bracketDepth > 0) bracketDepth--; continue; }
            if (c == '{') { braceDepth++; continue; }
            if (c == '}') { if (braceDepth > 0) braceDepth--; continue; }

            if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                if (c == '?' && i + 1 < len && line.charAt(i + 1) == '.') {
                    boolean isDigit = (i + 2 < len && Character.isDigit(line.charAt(i + 2)));
                    if (!isDigit) {
                        if (dotCount == dotOffsets.length) {
                            int[] newArr = new int[dotOffsets.length * 2];
                            System.arraycopy(dotOffsets, 0, newArr, 0, dotCount);
                            dotOffsets = newArr;
                        }
                        dotOffsets[dotCount++] = i;
                        i++; // skip '.'
                        continue;
                    }
                }

                if (c == '.') {
                    if ((i + 1 < len && line.charAt(i + 1) == '.')
                            || (i > 0 && line.charAt(i - 1) == '.')) {
                        continue;
                    }
                    boolean prevDigit = (i > 0 && Character.isDigit(line.charAt(i - 1)));
                    boolean nextDigit = (i + 1 < len && Character.isDigit(line.charAt(i + 1)));
                    if (prevDigit && nextDigit) {
                        continue;
                    }

                    if (dotCount == dotOffsets.length) {
                        int[] newArr = new int[dotOffsets.length * 2];
                        System.arraycopy(dotOffsets, 0, newArr, 0, dotCount);
                        dotOffsets = newArr;
                    }
                    dotOffsets[dotCount++] = i;
                }
            }
        }

        if (dotCount < 2) {
            return line;
        }

        StringBuilder sb = new StringBuilder(len + dotCount * 2);
        int prevEnd = 0;

        for (int d = 0; d < dotCount; d++) {
            int dotIdx = dotOffsets[d];
            String part = line.substring(prevEnd, dotIdx).trim();
            if (sb.length() == 0) {
                sb.append(part);
            } else if (!part.isEmpty()) {
                sb.append('\n').append(part);
            }
            prevEnd = dotIdx;
        }

        String lastPart = line.substring(prevEnd).trim();
        if (!lastPart.isEmpty()) {
            sb.append('\n').append(lastPart);
        }

        return sb.toString();
    }

    /**
     * Normalizes import and export clause statements into Prettier-style formatting.
     * Short statements are kept on a clean single line, while statements with many
     * specifiers (> 3) or exceeding length budget (> 45 chars) break across multiple lines.
     */
    public static String normalizeImportExportStatement(String stmt) {
        if (stmt == null || stmt.trim().isEmpty()) return "";

        String trimmed = stmt.trim();
        int len = trimmed.length();

        int openBrace = -1;
        int closeBrace = -1;
        boolean inStr = false;
        char q = 0;
        int braceDepth = 0;

        for (int i = 0; i < len; i++) {
            char c = trimmed.charAt(i);
            if (inStr) {
                if (c == '\\') i++;
                else if (c == q) inStr = false;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                inStr = true;
                q = c;
                continue;
            }
            if (c == '/' && i + 1 < len) {
                if (trimmed.charAt(i + 1) == '/') {
                    while (i < len && trimmed.charAt(i) != '\n') i++;
                    continue;
                }
                if (trimmed.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < len && !(trimmed.charAt(i) == '*' && trimmed.charAt(i + 1) == '/')) i++;
                    i++;
                    continue;
                }
            }
            if (c == '{') {
                if (braceDepth == 0 && openBrace == -1) {
                    openBrace = i;
                }
                braceDepth++;
            } else if (c == '}') {
                braceDepth--;
                if (braceDepth == 0 && closeBrace == -1) {
                    closeBrace = i;
                    break;
                }
            }
        }

        if (openBrace == -1 || closeBrace == -1) {
            return normalizeSingleLineImportExport(trimmed);
        }

        String prefix = trimmed.substring(0, openBrace).replaceAll("\\s+", " ").trim();
        String body = trimmed.substring(openBrace + 1, closeBrace);
        String suffix = trimmed.substring(closeBrace + 1).replaceAll("\\s+", " ").trim();

        if (!suffix.isEmpty()) {
            if (suffix.startsWith(";")) {
                suffix = ";";
            } else {
                if (!suffix.endsWith(";")) {
                    suffix = suffix + ";";
                }
            }
        } else {
            suffix = ";";
        }

        List<String> items = new ArrayList<>();
        StringBuilder curItem = new StringBuilder();
        inStr = false;
        q = 0;
        int pDepth = 0;

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inStr) {
                curItem.append(c);
                if (c == '\\') {
                    if (i + 1 < body.length()) curItem.append(body.charAt(++i));
                } else if (c == q) {
                    inStr = false;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                inStr = true;
                q = c;
                curItem.append(c);
                continue;
            }
            if (c == '(') pDepth++;
            else if (c == ')') { if (pDepth > 0) pDepth--; }

            if (c == ',' && pDepth == 0) {
                String it = curItem.toString().replaceAll("\\s+", " ").trim();
                if (!it.isEmpty()) items.add(it);
                curItem.setLength(0);
                continue;
            }
            curItem.append(c);
        }
        String lastIt = curItem.toString().replaceAll("\\s+", " ").trim();
        if (!lastIt.isEmpty()) items.add(lastIt);

        StringBuilder singleLine = new StringBuilder();
        singleLine.append(prefix).append(" { ");
        for (int k = 0; k < items.size(); k++) {
            if (k > 0) singleLine.append(", ");
            singleLine.append(items.get(k));
        }
        singleLine.append(" }");
        if (!suffix.equals(";")) {
            singleLine.append(' ').append(suffix);
        } else {
            singleLine.append(';');
        }

        boolean isLong = items.size() > 3 || singleLine.length() > 45;
        if (!isLong) {
            return normalizeSingleLineImportExport(stmt);
        }

        StringBuilder multi = new StringBuilder();
        multi.append(prefix).append(" {\n");
        for (int k = 0; k < items.size(); k++) {
            multi.append(items.get(k));
            if (k < items.size() - 1) {
                multi.append(",\n");
            } else {
                multi.append('\n');
            }
        }
        if (!suffix.equals(";")) {
            multi.append("} ").append(suffix);
        } else {
            multi.append("};");
        }

        return multi.toString();
    }

    public static String normalizeSingleLineImportExport(String stmt) {
        if (stmt == null || stmt.trim().isEmpty()) return "";

        String trimmed = stmt.trim();
        int len = trimmed.length();
        StringBuilder out = new StringBuilder(len + 16);

        boolean inString = false;
        char quoteChar = 0;
        boolean inBlockComment = false;
        boolean inLineComment = false;

        for (int i = 0; i < len; i++) {
            char c = trimmed.charAt(i);

            if (inBlockComment) {
                out.append(c);
                if (c == '*' && i + 1 < len && trimmed.charAt(i + 1) == '/') {
                    out.append('/');
                    i++;
                    inBlockComment = false;
                }
                continue;
            }

            if (inLineComment) {
                out.append(c);
                if (c == '\n') inLineComment = false;
                continue;
            }

            if (inString) {
                out.append(c);
                if (c == '\\') {
                    if (i + 1 < len) {
                        out.append(trimmed.charAt(++i));
                    }
                } else if (c == quoteChar) {
                    inString = false;
                }
                continue;
            }

            if (c == '/' && i + 1 < len) {
                if (trimmed.charAt(i + 1) == '/') {
                    inLineComment = true;
                    if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') {
                        out.append(' ');
                    }
                    out.append("//");
                    i++;
                    continue;
                }
                if (trimmed.charAt(i + 1) == '*') {
                    inBlockComment = true;
                    if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') {
                        out.append(' ');
                    }
                    out.append("/*");
                    i++;
                    continue;
                }
            }

            if (c == '\'' || c == '"' || c == '`') {
                inString = true;
                quoteChar = c;
                if (out.length() > 0 && out.charAt(out.length() - 1) != ' '
                        && out.charAt(out.length() - 1) != '('
                        && out.charAt(out.length() - 1) != '{') {
                    out.append(' ');
                }
                out.append(c);
                continue;
            }

            if (Character.isWhitespace(c)) {
                if (out.length() > 0 && out.charAt(out.length() - 1) != ' '
                        && out.charAt(out.length() - 1) != '{'
                        && out.charAt(out.length() - 1) != '(') {
                    out.append(' ');
                }
                continue;
            }

            if (c == '{') {
                if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') {
                    out.append(' ');
                }
                out.append('{');
                int nextIdx = i + 1;
                while (nextIdx < len && Character.isWhitespace(trimmed.charAt(nextIdx))) nextIdx++;
                if (nextIdx < len && trimmed.charAt(nextIdx) != '}') {
                    out.append(' ');
                }
                i = nextIdx - 1;
                continue;
            }

            if (c == '}') {
                if (out.length() > 0 && out.charAt(out.length() - 1) != ' '
                        && out.charAt(out.length() - 1) != '{') {
                    out.append(' ');
                }
                out.append('}');
                int nextIdx = i + 1;
                while (nextIdx < len && Character.isWhitespace(trimmed.charAt(nextIdx))) nextIdx++;
                if (nextIdx < len && trimmed.charAt(nextIdx) != ';' && trimmed.charAt(nextIdx) != ',') {
                    out.append(' ');
                }
                i = nextIdx - 1;
                continue;
            }

            if (c == ',') {
                while (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
                    out.deleteCharAt(out.length() - 1);
                }
                out.append(',').append(' ');
                int nextIdx = i + 1;
                while (nextIdx < len && Character.isWhitespace(trimmed.charAt(nextIdx))) nextIdx++;
                i = nextIdx - 1;
                continue;
            }

            if (c == ';') {
                while (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
                    out.deleteCharAt(out.length() - 1);
                }
                out.append(';');
                continue;
            }

            out.append(c);
        }

        int commentIdx = -1;
        boolean inStr = false;
        char q = 0;
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
            if (inStr) {
                if (c == '\\') i++;
                else if (c == q) inStr = false;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                inStr = true;
                q = c;
                continue;
            }
            if (c == '/' && i + 1 < out.length() && (out.charAt(i + 1) == '/' || out.charAt(i + 1) == '*')) {
                commentIdx = i;
                break;
            }
        }

        if (commentIdx != -1) {
            int pre = commentIdx - 1;
            while (pre >= 0 && Character.isWhitespace(out.charAt(pre))) pre--;
            if (pre >= 0 && out.charAt(pre) != ';') {
                out.insert(pre + 1, ';');
            }
        } else {
            int k = out.length() - 1;
            while (k >= 0 && Character.isWhitespace(out.charAt(k))) k--;
            if (k >= 0 && out.charAt(k) != ';') {
                out.setLength(k + 1);
                out.append(';');
            } else if (k >= 0) {
                out.setLength(k + 1);
            }
        }

        return out.toString();
    }

    private static boolean isImportStart(String code, int i) {
        if (!code.startsWith("import", i)) return false;
        if (i > 0 && Character.isJavaIdentifierPart(code.charAt(i - 1))) return false;
        int after = i + 6;
        int len = code.length();
        while (after < len && (code.charAt(after) == ' ' || code.charAt(after) == '\t')) after++;
        if (after >= len) return false;
        char next = code.charAt(after);
        return next != '(' && next != '.';
    }

    private static boolean isExportClauseStart(String code, int i) {
        if (!code.startsWith("export", i)) return false;
        if (i > 0 && Character.isJavaIdentifierPart(code.charAt(i - 1))) return false;
        int after = i + 6;
        int len = code.length();
        while (after < len && (code.charAt(after) == ' ' || code.charAt(after) == '\t' || code.charAt(after) == '\n' || code.charAt(after) == '\r')) after++;
        if (after >= len) return false;
        if (code.charAt(after) == '{') return true;
        if (code.startsWith("type", after)) {
            int afterType = after + 4;
            while (afterType < len && (code.charAt(afterType) == ' ' || code.charAt(afterType) == '\t' || code.charAt(afterType) == '\n' || code.charAt(afterType) == '\r')) afterType++;
            return afterType < len && code.charAt(afterType) == '{';
        }
        return false;
    }

    private static int findStatementEnd(String code, int start) {
        int len = code.length();
        boolean inStr = false;
        char q = 0;
        boolean inLineComm = false;
        boolean inBlockComm = false;
        int braceDepth = 0;
        boolean seenFrom = false;
        boolean seenModuleString = false;

        for (int i = start; i < len; i++) {
            char c = code.charAt(i);

            if (inBlockComm) {
                if (c == '*' && i + 1 < len && code.charAt(i + 1) == '/') {
                    i++;
                    inBlockComm = false;
                }
                continue;
            }
            if (inLineComm) {
                if (c == '\n') {
                    inLineComm = false;
                    if (seenModuleString) return i;
                }
                continue;
            }
            if (inStr) {
                if (c == '\\') {
                    i++;
                } else if (c == q) {
                    inStr = false;
                    seenModuleString = true;
                    int j = i + 1;
                    while (j < len && (code.charAt(j) == ' ' || code.charAt(j) == '\t')) j++;
                    if (j < len && code.charAt(j) == ';') {
                        return j + 1;
                    }
                    if (j < len && (code.charAt(j) == '\n' || code.charAt(j) == '\r')) {
                        return j;
                    }
                    if (j >= len) return len;
                }
                continue;
            }

            if (c == '/' && i + 1 < len) {
                if (code.charAt(i + 1) == '/') {
                    inLineComm = true;
                    i++;
                    continue;
                }
                if (code.charAt(i + 1) == '*') {
                    inBlockComm = true;
                    i++;
                    continue;
                }
            }

            if (c == '\'' || c == '"' || c == '`') {
                inStr = true;
                q = c;
                continue;
            }

            if (c == '{') braceDepth++;
            else if (c == '}') braceDepth = Math.max(0, braceDepth - 1);

            if (braceDepth == 0 && (c == 'f' || c == 'F') && i + 4 <= len && code.substring(i, i + 4).equalsIgnoreCase("from")) {
                int before = i - 1;
                int after = i + 4;
                boolean beforeOk = before < 0 || !Character.isJavaIdentifierPart(code.charAt(before));
                boolean afterOk = after >= len || !Character.isJavaIdentifierPart(code.charAt(after));
                if (beforeOk && afterOk) {
                    seenFrom = true;
                }
            }

            if (c == ';') {
                return i + 1;
            }

            if (braceDepth == 0 && c == '\n') {
                if (seenModuleString) {
                    return i;
                }
                int j = i + 1;
                while (j < len && (code.charAt(j) == ' ' || code.charAt(j) == '\t' || code.charAt(j) == '\n' || code.charAt(j) == '\r')) j++;
                if (j + 4 <= len && code.substring(j, j + 4).equalsIgnoreCase("from")) {
                    continue;
                }
                if (!seenFrom) {
                    return i;
                }
            }
        }
        return len;
    }

    private static boolean isSingleLineBrace(String code, int openIdx) {
        int len = code.length();
        int depth = 1;
        boolean inStr = false;
        char q = 0;
        int commaCount = 0;
        for (int j = openIdx + 1; j < len; j++) {
            char ch = code.charAt(j);
            if (inStr) {
                if (ch == '\\') j++;
                else if (ch == q) inStr = false;
                continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                inStr = true;
                q = ch;
                continue;
            }
            if (ch == '\n' || ch == '\r') {
                return false;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    int contentLen = j - openIdx - 1;
                    if (contentLen > 40 || commaCount > 2) {
                        return false;
                    }
                    return true;
                }
            } else if (depth == 1) {
                if (ch == ';') return false;
                if (ch == ',') commaCount++;
            }
        }
        return false;
    }

    private static boolean isObjectOrDestructuringBrace(String code, int openIdx) {
        int len = code.length();
        int depth = 1;
        boolean inStr = false;
        char q = 0;
        boolean hasColonOrComma = false;

        for (int j = openIdx + 1; j < len; j++) {
            char ch = code.charAt(j);
            if (inStr) {
                if (ch == '\\') j++;
                else if (ch == q) inStr = false;
                continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                inStr = true;
                q = ch;
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return hasColonOrComma;
                }
            } else if (depth == 1) {
                if (ch == ';') {
                    return false;
                }
                if (ch == ':' || ch == ',') {
                    hasColonOrComma = true;
                }
            }
        }
        return false;
    }

    private void ensureSpace(StringBuilder sb) {
        if (sb.length() > 0) {
            char last = sb.charAt(sb.length() - 1);
            if (last != ' ' && last != '\n' && last != '(' && last != '[') sb.append(' ');
        }
    }

    private void trimTrailingSpace(StringBuilder sb) {
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ')
            sb.deleteCharAt(sb.length() - 1);
    }
}
