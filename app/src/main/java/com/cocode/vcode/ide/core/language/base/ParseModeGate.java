package com.cocode.vcode.ide.core.language.base;

import com.cocode.vcode.ide.core.language.js.ParseResult;

/**
 * Per-language parse-mode dispatcher.
 *
 * <p>Generalises the JS-only large-file tiering into a single,
 * language-agnostic scheduling gate. Each language picks its
 * appropriate size unit (lines for HTML/CSS/Markdown/JS, node
 * count or byte size for JSON) and asks the gate to select one
 * of the four {@link ParseResult} modes.
 *
 * <p>The gate is purely a scheduling decision. It does NOT touch
 * any language's underlying parse or scope logic — that is the
 * per-language pipeline's job. The gate just answers: "given this
 * file's size, which mode should I be in?"
 *
 * <p>Default thresholds match the original JS pattern:
 * <ul>
 *   <li>{@code < T1} → {@code MODE_FULL} (full tree + full scope)</li>
 *   <li>{@code <= T2} → {@code MODE_LAZY_SCOPE} (full tree, lazy scope)</li>
 *   <li>{@code <= T3} → {@code MODE_TOP_LEVEL} (top-level-only tree)</li>
 *   <li>{@code > T3} → {@code MODE_TOKENIZE_ONLY} (tokens only)</li>
 * </ul>
 * Defaults: T1=1000, T2=5000, T3=15000. Configurable per language.
 */
public final class ParseModeGate {

    private ParseModeGate() {}

    /**
     * The size metric the gate consumes. Languages pick the one
     * that best characterises their parse cost.
     */
    public enum SizeMetric {
        /** Number of newlines + 1 (or 1 for empty input). */
        LINES,
        /** Number of nodes the lexer/parser would produce. */
        NODES,
        /** Source byte count. */
        BYTES
    }

    /**
     * Default thresholds: T1=1000, T2=5000, T3=15000. Matches the
     * original JS pattern.
     */
    public static final int DEFAULT_T1 = 1000;
    public static final int DEFAULT_T2 = 5000;
    public static final int DEFAULT_T3 = 15000;

    /**
     * Select a parse mode from a raw size value using the default
     * thresholds.
     *
     * @param metric the size unit (only the type is recorded; the
     *               actual comparison is value-based)
     * @param value the measured size (line count, node count, or
     *              byte count depending on {@code metric})
     * @return one of {@link ParseResult#MODE_FULL},
     *         {@link ParseResult#MODE_LAZY_SCOPE},
     *         {@link ParseResult#MODE_TOP_LEVEL},
     *         {@link ParseResult#MODE_TOKENIZE_ONLY}
     */
    public static int select(SizeMetric metric, int value) {
        return select(metric, value, DEFAULT_T1, DEFAULT_T2, DEFAULT_T3);
    }

    /**
     * Select a parse mode from a raw size value using caller-supplied
     * thresholds. Per-language call sites that need different cutoffs
     * (e.g. JSON, where "lines" is not the right unit) can pass their
     * own T1/T2/T3.
     *
     * <p>The threshold semantics match the original JS code:
     * {@code value < T1} is FULL, {@code value <= T2} is LAZY_SCOPE,
     * {@code value <= T3} is TOP_LEVEL, otherwise TOKENIZE_ONLY.
     *
     * <p>Negative or zero values are treated as "tiny file" → FULL.
     */
    public static int select(SizeMetric metric, int value, int t1, int t2, int t3) {
        if (value < t1) return ParseResult.MODE_FULL;
        if (value <= t2) return ParseResult.MODE_LAZY_SCOPE;
        if (value <= t3) return ParseResult.MODE_TOP_LEVEL;
        return ParseResult.MODE_TOKENIZE_ONLY;
    }

    /**
     * Convenience: count newlines in {@code source} and select a
     * mode based on the resulting line count. An empty source
     * counts as 1 line (matches the existing JS pipeline's
     * behaviour).
     */
    public static int selectByLineCount(String source) {
        return select(SizeMetric.LINES, countLines(source));
    }

    /**
     * Convenience: count newlines with custom thresholds.
     */
    public static int selectByLineCount(String source, int t1, int t2, int t3) {
        return select(SizeMetric.LINES, countLines(source), t1, t2, t3);
    }

    /**
     * Count the line count of {@code source}. An empty or null
     * source returns 1 (a one-line file with no content), matching
     * the existing JS pipeline's initial value of {@code 1}.
     */
    public static int countLines(String source) {
        if (source == null || source.isEmpty()) return 1;
        int count = 1;
        int len = source.length();
        for (int i = 0; i < len; i++) {
            if (source.charAt(i) == '\n') count++;
        }
        return count;
    }
}
