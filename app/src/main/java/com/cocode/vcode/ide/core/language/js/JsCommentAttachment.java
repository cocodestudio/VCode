package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;

/**
 * Pre-pass that attaches every {@code TK_COMMENT} token span to its
 * nearest adjacent AST node. The result is stored in flat SoA
 * arrays so the printer can consult it without allocating.
 *
 * <p>For each comment in the source we record:
 * <ul>
 *   <li>{@code commentStart[i] / commentEnd[i]} — the comment's byte
 *       range in {@code source}.</li>
 *   <li>{@code attachedNode[i]} — the node-id the comment is bound to,
 *       or 0 if the comment is between top-level statements and not
 *       adjacent to any node.</li>
 *   <li>{@code isLeading[i]} — true if the comment is positioned before
 *       the attached node on the same line (or on a preceding line),
 *       false if it trails the attached node.</li>
 * </ul>
 */
public final class JsCommentAttachment {

    public int[] commentStart;
    public int[] commentEnd;
    public int[] attachedNode;
    public boolean[] isLeading;
    public int count;

    public JsCommentAttachment(int initialCapacity) {
        commentStart = new int[initialCapacity];
        commentEnd   = new int[initialCapacity];
        attachedNode = new int[initialCapacity];
        isLeading    = new boolean[initialCapacity];
    }

    /**
     * Walk every {@code TK_COMMENT} span in {@code stream}, attach it
     * to the nearest adjacent AST node, and grow the attachment arrays
     * as needed.
     */
    public void build(JsSyntaxTree tree, String source, TokenStream stream) {
        int sourceLen = source.length();
        int streamLen = stream.length;
        int i = 0;
        while (i < streamLen) {
            byte t = stream.types[i];
            if (t != TokenStream.TK_COMMENT) {
                i++;
                continue;
            }
            int start = i;
            // Find the end of this comment token span.
            int end = start;
            while (end < streamLen && stream.tokenStart[end] == stream.tokenStart[start]) {
                end++;
            }
            // The token's actual end offset in source is past the last
            // character of the comment. We need source[endOffset] not
            // the stream index, so look at the next non-comment byte
            // (or the source length).
            int srcStart = stream.tokenStart[start];
            int srcEnd;
            if (end < streamLen) {
                srcEnd = stream.tokenStart[end];
            } else {
                srcEnd = sourceLen;
            }
            // Clamp: a comment that runs to EOF ends at sourceLen.
            if (srcEnd > sourceLen) srcEnd = sourceLen;

            attachOne(tree, srcStart, srcEnd);
            i = end;
        }
    }

    private void attachOne(JsSyntaxTree tree, int srcStart, int srcEnd) {
        if (count >= commentStart.length) {
            int newCap = commentStart.length * 2;
            commentStart = grow(commentStart, newCap);
            commentEnd   = grow(commentEnd,   newCap);
            attachedNode = grow(attachedNode, newCap);
            // boolean[] does not need resize because boolean defaults to false.
            boolean[] grown = new boolean[newCap];
            System.arraycopy(isLeading, 0, grown, 0, isLeading.length);
            isLeading = grown;
        }
        // Find the nearest AST node to srcStart. Nodes are already
        // built; we walk them linearly because the tree count is
        // typically much smaller than the source length and the
        // comment-count is also small. (If hot-path profiling ever
        // shows this dominates, we can binary-search nodesByOffset.)
        int best = 0;
        int bestDelta = Integer.MAX_VALUE;
        boolean leading = false;
        for (int id = 1; id < tree.nodeCount; id++) {
            int ns = tree.nodeStart[id];
            int ne = tree.nodeEnd[id];
            if (ne == 0 && ns == 0) continue; // unused slot
            // The comment must not overlap a node.
            if (srcStart >= ns && srcStart < ne) {
                // Comment starts inside a node. Don't attach.
                best = 0;
                leading = false;
                bestDelta = Integer.MAX_VALUE;
                break;
            }
            int delta;
            if (srcEnd <= ns) {
                delta = ns - srcEnd;
                if (delta < bestDelta) {
                    bestDelta = delta;
                    best = id;
                    leading = true;
                }
            } else if (srcStart >= ne) {
                delta = srcStart - ne;
                if (delta < bestDelta) {
                    bestDelta = delta;
                    best = id;
                    leading = false;
                }
            }
        }
        commentStart[count] = srcStart;
        commentEnd[count]   = srcEnd;
        attachedNode[count] = best;
        isLeading[count]    = leading;
        count++;
    }

    private static int[] grow(int[] a, int newCap) {
        int[] n = new int[newCap];
        System.arraycopy(a, 0, n, 0, a.length);
        return n;
    }

    /**
     * Find the first comment attached to {@code nodeId} with
     * {@code isLeading == true} whose start offset is in
     * {@code [searchFrom, searchTo)}. Returns -1 if none.
     */
    public int findLeadingBefore(int nodeId, int searchFrom, int searchTo) {
        int found = -1;
        for (int i = 0; i < count; i++) {
            if (attachedNode[i] != nodeId) continue;
            if (!isLeading[i]) continue;
            int cs = commentStart[i];
            if (cs >= searchFrom && cs < searchTo) {
                if (found < 0 || commentStart[i] < commentStart[found]) {
                    found = i;
                }
            }
        }
        return found;
    }

    /**
     * Find the first comment attached to {@code nodeId} with
     * {@code isLeading == false} whose end offset is in
     * {@code [searchFrom, searchTo)}. Returns -1 if none.
     */
    public int findTrailingAfter(int nodeId, int searchFrom, int searchTo) {
        int found = -1;
        for (int i = 0; i < count; i++) {
            if (attachedNode[i] != nodeId) continue;
            if (isLeading[i]) continue;
            int ce = commentEnd[i];
            if (ce >= searchFrom && ce <= searchTo) {
                if (found < 0 || commentEnd[i] > commentEnd[found]) {
                    found = i;
                }
            }
        }
        return found;
    }

    /** True if the byte range [start, end) contains any comment. */
    public boolean rangeContainsComment(int start, int end) {
        for (int i = 0; i < count; i++) {
            int cs = commentStart[i];
            int ce = commentEnd[i];
            if (cs >= start && ce <= end) return true;
        }
        return false;
    }
}
