package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.diagnostic.util.ViewportHighlighter;

import java.util.ArrayList;
import java.util.List;

/**
 * AST second-pass role recolorer.
 *
 * <p>Given a {@link JsSyntaxTree} that was built from a {@link
 * TokenStream} of the same source, find every identifier byte in the
 * visible byte range that represents a <em>declaration</em> — the
 * binding site of a variable, function, class, parameter, or import
 * — and emit a {@link ViewportHighlighter.ViewportSpan} for it.
 *
 * <p>The output is intended to be merged with the J.1 base-pass
 * output by overwriting the identifier spans with a
 * declaration-specific colour. The base pass uses one colour for all
 * {@code TK_IDENTIFIER} bytes; this pass refines that to a
 * different colour for declaration sites.
 *
 * <p>Allocation policy: the returned list is the only allocation in
 * the call. The pre-pass that builds the declaration-offset array
 * runs once per pipeline cycle (not per frame) and is cached on the
 * {@code JsSyntaxTree}-shaped snapshot.
 */
public final class JsRoleRecolorer {

    private JsRoleRecolorer() {
    }

    /**
     * Build a flat SoA list of declaration byte ranges from
     * {@code tree}. One entry per named declaration: {@code N_VAR_DECL},
     * {@code N_FUNC_DECL}, {@code N_CLASS_DECL}, {@code N_PARAM},
     * {@code N_IMPORT}-bound names, {@code N_INTERFACE},
     * {@code N_TYPE_ALIAS}, {@code N_ENUM}, and {@code N_METHOD}/
     * {@code N_GETTER}/{@code N_SETTER}. Each entry is
     * {@code [startOffset, endOffset)} of the identifier token in
     * the source.
     *
     * <p>Returns a flat {@code int[]} of length {@code 2 * N} where
     * pairs are (start, end). The caller can binary-search this
     * array to find whether a given offset is a declaration.
     *
     * <p>To locate the identifier token for a declaration, the source
     * string and {@link TokenStream} are needed. The first
     * {@code TK_IDENTIFIER} byte whose token text equals the
     * declaration's name and whose {@code tokenStart} lies inside
     * the node's {@code [nodeStart, nodeEnd)} range is the
     * declaration identifier.
     */
    public static int[] buildDeclarationRanges(JsSyntaxTree tree, String source, TokenStream stream) {
        if (tree == null || source == null || stream == null) return new int[0];
        // First pass: count candidates that have a matching name.
        int count = 0;
        for (int id = 1; id < tree.nodeCount; id++) {
            int t = tree.nodeType[id];
            String name = tree.nodeName[id];
            if (name == null || name.isEmpty()) continue;
            if (!isDeclarationType(t)) continue;
            if (findIdentifierInRange(stream, source, tree.nodeStart[id], tree.nodeEnd[id], name) >= 0) {
                count++;
            }
        }
        int[] out = new int[count * 2];
        int idx = 0;
        for (int id = 1; id < tree.nodeCount; id++) {
            int t = tree.nodeType[id];
            String name = tree.nodeName[id];
            if (name == null || name.isEmpty()) continue;
            if (!isDeclarationType(t)) continue;
            int identStart = findIdentifierInRange(stream, source, tree.nodeStart[id], tree.nodeEnd[id], name);
            if (identStart < 0) continue;
            out[idx++] = identStart;
            out[idx++] = identStart + name.length();
        }
        return out;
    }

    /**
     * Find the first {@code TK_IDENTIFIER} byte in
     * {@code [nodeStart, nodeEnd)} whose token text equals
     * {@code name}. Returns the token's start offset, or -1 if no
     * such identifier exists in the range.
     */
    private static int findIdentifierInRange(TokenStream stream, String source, int nodeStart, int nodeEnd, String name) {
        if (nodeStart < 0) nodeStart = 0;
        if (nodeEnd > stream.length) nodeEnd = stream.length;
        // Lower-bound search for the first byte with tokenStart >= nodeStart.
        int first = lowerBound(stream.tokenStart, nodeEnd, nodeStart);
        if (first < 0) return -1;
        for (int i = first; i < nodeEnd; i++) {
            if (stream.types[i] != TokenStream.TK_IDENTIFIER) continue;
            int ts = stream.tokenStart[i];
            int te = ts + name.length();
            if (te > nodeEnd) continue;
            if (source.regionMatches(ts, name, 0, name.length())) {
                return ts;
            }
        }
        return -1;
    }

    private static boolean isDeclarationType(int t) {
        switch (t) {
            case JsSyntaxTree.N_VAR_DECL:
            case JsSyntaxTree.N_FUNC_DECL:
            case JsSyntaxTree.N_ARROW_FUNC:
            case JsSyntaxTree.N_CLASS_DECL:
            case JsSyntaxTree.N_PARAM:
            case JsSyntaxTree.N_METHOD:
            case JsSyntaxTree.N_GETTER:
            case JsSyntaxTree.N_SETTER:
            case JsSyntaxTree.N_CASE_CLAUSE:
            case JsSyntaxTree.N_INTERFACE:
            case JsSyntaxTree.N_TYPE_ALIAS:
            case JsSyntaxTree.N_ENUM:
            case JsSyntaxTree.N_IMPORT:
                return true;
            default:
                return false;
        }
    }

    /**
     * Test whether {@code offset} is the start of a declaration
     * identifier, using binary search on the ranges produced by
     * {@link #buildDeclarationRanges(JsSyntaxTree)}.
     */
    public static boolean isDeclarationStart(int[] ranges, int offset) {
        if (ranges == null) return false;
        int n = ranges.length / 2;
        int lo = 0, hi = n;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            int start = ranges[mid * 2];
            if (start < offset) lo = mid + 1;
            else if (start > offset) hi = mid;
            else return true;
        }
        if (lo >= n) return false;
        int start = ranges[lo * 2];
        int end = ranges[lo * 2 + 1];
        return start == offset;
    }

    /**
     * Walk the visible byte range and emit a
     * {@link ViewportHighlighter.ViewportSpan} for every
     * {@code TK_IDENTIFIER} byte whose offset is the start of a
     * declaration identifier. Returned spans have
     * {@code endOffset - startOffset == identifier length}.
     *
     * <p>Bytes outside the visible range are not visited.
     */
    public static List<ViewportHighlighter.ViewportSpan> recolor(
            byte[] types, int[] tokenStart, int length,
            int startOffset, int endOffset,
            int[] declarationRanges,
            int declarationColor) {
        if (types == null || tokenStart == null) return new ArrayList<>(0);
        if (length <= 0 || startOffset >= endOffset) return new ArrayList<>(0);
        if (startOffset < 0) startOffset = 0;
        if (endOffset > length) endOffset = length;
        if (declarationRanges == null || declarationRanges.length == 0) {
            return new ArrayList<>(0);
        }

        // Binary-search for the first token whose tokenStart >=
        // startOffset (standard lower-bound).
        int first = lowerBound(tokenStart, length, startOffset);
        if (first < 0) return new ArrayList<>(0);

        List<ViewportHighlighter.ViewportSpan> out = new ArrayList<>(8);
        int i = first;
        while (i < length) {
            int tokStart = tokenStart[i];
            if (tokStart >= endOffset) break;
            byte tokType = types[i];
            int tokEnd = i + 1;
            while (tokEnd < length && tokenStart[tokEnd] == tokStart) {
                tokEnd++;
            }
            if (tokType == TokenStream.TK_IDENTIFIER) {
                if (isDeclarationStart(declarationRanges, tokStart)) {
                    int visStart = Math.max(tokStart, startOffset);
                    int visEnd = Math.min(tokEnd, endOffset);
                    if (visEnd > visStart) {
                        out.add(new ViewportHighlighter.ViewportSpan(
                                visStart, visEnd, declarationColor));
                    }
                }
            }
            i = tokEnd;
        }
        return out;
    }

    private static int lowerBound(int[] tokenStart, int length, int target) {
        int lo = 0, hi = length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (tokenStart[mid] < target) lo = mid + 1;
            else hi = mid;
        }
        if (lo >= length) return -1;
        return lo;
    }

    /**
     * J.2 merge step: take a base-pass list of
     * {@link ViewportHighlighter.ViewportSpan}s and overlay the
     * declaration-recolour spans on top. Any base-pass span whose
     * range overlaps a declaration recolour is removed; the
     * recolour span replaces it. Spans are returned in offset order
     * (the input base-pass list is assumed already sorted, which
     * the J.1 utility guarantees).
     *
     * <p>This is a pure transformation; no AST access and no
     * allocation beyond the output list. The caller (the editor's
     * draw layer) is expected to invoke this once per visible frame.
     */
    public static List<ViewportHighlighter.ViewportSpan> mergeOverlays(
            List<ViewportHighlighter.ViewportSpan> base,
            List<ViewportHighlighter.ViewportSpan> overlays) {
        if (base == null || base.isEmpty()) {
            return overlays == null ? new ArrayList<>(0) : new ArrayList<>(overlays);
        }
        if (overlays == null || overlays.isEmpty()) {
            return new ArrayList<>(base);
        }
        List<ViewportHighlighter.ViewportSpan> out = new ArrayList<>(base.size() + overlays.size());
        int oi = 0;
        int oN = overlays.size();
        for (ViewportHighlighter.ViewportSpan b : base) {
            // Skip overlays that end before this base span starts.
            while (oi < oN && overlays.get(oi).endOffset <= b.startOffset) oi++;
            // Walk through overlays that overlap [b.start, b.end).
            int cursor = b.startOffset;
            while (oi < oN) {
                ViewportHighlighter.ViewportSpan ov = overlays.get(oi);
                if (ov.startOffset >= b.endOffset) break;
                if (ov.startOffset > cursor) {
                    out.add(new ViewportHighlighter.ViewportSpan(
                            cursor, ov.startOffset, b.color));
                }
                if (ov.endOffset > cursor) {
                    int visEnd = Math.min(ov.endOffset, b.endOffset);
                    if (visEnd > Math.max(ov.startOffset, cursor)) {
                        out.add(new ViewportHighlighter.ViewportSpan(
                                Math.max(ov.startOffset, cursor), visEnd, ov.color));
                    }
                    cursor = visEnd;
                }
                if (ov.endOffset <= b.endOffset) {
                    oi++;
                } else {
                    // overlay extends past b; stop processing this base
                    // span but don't advance oi.
                    break;
                }
                if (cursor >= b.endOffset) break;
            }
            if (cursor < b.endOffset) {
                out.add(new ViewportHighlighter.ViewportSpan(
                        cursor, b.endOffset, b.color));
            }
        }
        return out;
    }
}
