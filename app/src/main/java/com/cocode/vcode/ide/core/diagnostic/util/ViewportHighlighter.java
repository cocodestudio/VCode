package com.cocode.vcode.ide.core.diagnostic.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared viewport-slice highlighter.
 *
 * Given a {@code TokenStream}-shaped pair of parallel arrays
 * ({@code types[i] = token type for source byte i,
 *  tokenStart[i] = start offset of the token containing byte i}),
 * a visible byte range {@code [startOffset, endOffset)}, and a colour
 * resolver, binary-searches to the first token whose start is at or
 * after {@code startOffset} and iterates forward only through tokens
 * that overlap the visible slice. Tokens are emitted as {@link
 * ViewportSpan}s in offset coordinates; converting them to
 * {@code (line, col)} for the editor is the caller's job.
 *
 * <p>The implementation does not depend on the AST and does not
 * touch the source string directly — it works on whatever token
 * stream was produced by the language's lexer, which is the design
 * rationale for keeping token-stream highlighting separate from
 * the structural AST.
 *
 * <p>Allocation policy: the returned list is the only allocation in
 * the call. The walk itself does not allocate, and the {@code
 * ColorResolver} is invoked with a primitive byte parameter.
 */
public final class ViewportHighlighter {

    private ViewportHighlighter() {}

    /**
     * Resolves a token type to an ARGB colour, or returns a negative
     * value to indicate "do not highlight". Implementing this as a
     * small functional interface lets each language wire its own
     * colour palette without subclassing.
     */
    public interface ColorResolver {
        int colorFor(byte tokenType);
    }

    /**
     * A highlight span expressed in absolute source-byte offsets.
     * The caller (the editor's draw layer) is responsible for
     * converting these to (line, col) coordinates if the existing
     * line-based pipeline requires that form.
     */
    public static final class ViewportSpan {
        public final int startOffset;
        public final int endOffset;
        public final int color;

        public ViewportSpan(int startOffset, int endOffset, int color) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.color = color;
        }
    }

    /**
     * Compute highlight spans for the visible byte range
     * {@code [startOffset, endOffset)}.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Find the first index {@code i} in the parallel arrays
     *       whose token could possibly overlap the visible range.
     *       This is the largest index whose {@code tokenStart} is
     *       {@code < startOffset} (so its token straddles the start
     *       of the visible range), or 0 if every {@code tokenStart}
     *       is {@code >= startOffset} (the first token starts inside
     *       or after the visible range). Returns -1 if the stream is
     *       empty or the visible range starts at or past EOF.</li>
     *   <li>Walk forward from {@code i}. For each distinct token
     *       (group of consecutive bytes with the same
     *       {@code tokenStart} and {@code types}), determine the
     *       token's end by scanning forward until the {@code
     *       tokenStart} changes. If the token's start is &ge;
     *       {@code endOffset} we are past the visible range and
     *       stop.</li>
     *   <li>For each visible token, ask the {@link ColorResolver}
     *       for a colour. If it returns a non-negative value, emit
     *       one {@link ViewportSpan} clipped to the visible range.</li>
     * </ol>
     *
     * @param types       per-byte token type (parallel to {@code tokenStart})
     * @param tokenStart  per-byte token start offset in the source
     * @param length      number of valid bytes in the parallel arrays
     * @param startOffset visible-range start (inclusive, absolute)
     * @param endOffset   visible-range end (exclusive, absolute)
     * @param resolver    maps a token type to an ARGB colour, or a
     *                    negative value to skip
     * @return a list of {@link ViewportSpan}s in offset order. May
     *         be empty.
     */
    public static List<ViewportSpan> highlight(
            byte[] types, int[] tokenStart, int length,
            int startOffset, int endOffset,
            ColorResolver resolver) {
        if (types == null || tokenStart == null || resolver == null) {
            return new ArrayList<>(0);
        }
        if (length <= 0) return new ArrayList<>(0);
        if (startOffset >= endOffset) return new ArrayList<>(0);
        if (startOffset < 0) startOffset = 0;
        if (endOffset > length) endOffset = length;
        if (startOffset >= length) return new ArrayList<>(0);

        int first = firstRelevantIndex(tokenStart, length, startOffset);
        if (first < 0) return new ArrayList<>(0);

        List<ViewportSpan> out = new ArrayList<>(16);
        int i = first;
        while (i < length) {
            int tokStart = tokenStart[i];
            if (tokStart >= endOffset) break;
            byte tokType = types[i];
            int tokEnd = i + 1;
            while (tokEnd < length && tokenStart[tokEnd] == tokStart) {
                tokEnd++;
            }
            int visibleStart = Math.max(tokStart, startOffset);
            int visibleEnd = Math.min(tokEnd, endOffset);
            if (visibleEnd > visibleStart) {
                int color = resolver.colorFor(tokType);
                if (color >= 0) {
                    out.add(new ViewportSpan(visibleStart, visibleEnd, color));
                }
            }
            i = tokEnd;
        }
        return out;
    }

    /**
     * Returns the largest index in {@code [0, length)} whose
     * {@code tokenStart} entry is strictly less than
     * {@code target}. If every entry is {@code >= target}, returns
     * 0. If the stream has no byte whose token could possibly
     * overlap the visible range (e.g. {@code target >= length}),
     * returns -1.
     *
     * <p>This is NOT a standard lower-bound; it is a "last index
     * strictly less than target" search, which is the right anchor
     * for the visible-slice walk: the token that contains
     * {@code target} (or the first token that starts at or after
     * {@code target}) is the first one to consider.
     */
    private static int firstRelevantIndex(int[] tokenStart, int length, int target) {
        int lo = 0;
        int hi = length;
        // After the loop, lo == hi == insertion point: the first
        // index whose tokenStart >= target.
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (tokenStart[mid] < target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        // lo is the first index with tokenStart[lo] >= target.
        if (lo == 0) {
            // Either every entry has tokenStart >= target (first
            // token starts inside or after visible range) or length
            // is 0. length is checked by the caller.
            return 0;
        }
        // lo > 0. tokenStart[lo-1] < target, and tokenStart[lo] is
        // either >= target or lo == length. Either way, the token
        // containing index lo-1 starts before target. But the
        // token at lo-1 may not extend to the visible range — its
        // end is the next index with a different tokenStart. The
        // walk's first iteration computes tokEnd and decides whether
        // to emit. So returning lo-1 is correct: the walk will
        // either emit a clipped span (if the token straddles
        // startOffset) or skip it (if the token ends at or before
        // startOffset).
        return lo - 1;
    }
}
