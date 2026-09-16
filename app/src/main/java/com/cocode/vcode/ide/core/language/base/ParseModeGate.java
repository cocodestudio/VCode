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
 * <p>Tiered scheduling thresholds:
 * <ul>
 *   <li>{@code < DEFAULT_T1 (1,000)} &rarr; {@code MODE_FULL} (complete syntax tree with eager scope analysis)</li>
 *   <li>{@code <= DEFAULT_T2 (5,000)} &rarr; {@code MODE_LAZY_SCOPE} (complete syntax tree with deferred scope analysis)</li>
 *   <li>{@code <= DEFAULT_T3 (15,000)} &rarr; {@code MODE_TOP_LEVEL} (top-level declarations only)</li>
 *   <li>{@code > DEFAULT_T3} &rarr; {@code MODE_TOKENIZE_ONLY} (tokenization only, bypassing AST generation for large files)</li>
 * </ul>
 * Configurable per language according to document characteristics.
 */
public final class ParseModeGate {

    /**
     * Default threshold for full parse mode (lines &lt; 1,000).
     */
    public static final int DEFAULT_T1 = 1000;
    /**
     * Default threshold for lazy scope parse mode (lines &lt;= 5,000).
     */
    public static final int DEFAULT_T2 = 5000;
    /**
     * Default threshold for top-level only parse mode (lines &lt;= 15,000).
     */
    public static final int DEFAULT_T3 = 15000;

    private ParseModeGate() {
    }

    /**
     * Select a parse mode from a raw size value using the default
     * thresholds.
     *
     * @param metric the size unit (only the type is recorded; the
     *               actual comparison is value-based)
     * @param value  the measured size (line count, node count, or
     *               byte count depending on {@code metric})
     * @return one of {@link ParseResult#MODE_FULL},
     * {@link ParseResult#MODE_LAZY_SCOPE},
     * {@link ParseResult#MODE_TOP_LEVEL},
     * {@link ParseResult#MODE_TOKENIZE_ONLY}
     */
    public static int select(SizeMetric metric, int value) {
        return select(metric, value, DEFAULT_T1, DEFAULT_T2, DEFAULT_T3);
    }

    /**
     * Select a parse mode from a raw size value using caller-supplied
     * thresholds. Languages that require custom boundaries (such as JSON,
     * where node count or byte size is more informative than line count)
     * can provide specialized thresholds.
     *
     * <p>Threshold evaluation:
     * {@code value < t1} selects FULL, {@code value <= t2} selects LAZY_SCOPE,
     * {@code value <= t3} selects TOP_LEVEL, and any larger value selects TOKENIZE_ONLY.
     *
     * <p>Negative or zero values indicate minimal content and default to FULL.
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

    /**
     * The size metric the gate consumes. Languages pick the one
     * that best characterises their parse cost.
     */
    public enum SizeMetric {
        /**
         * Number of newlines + 1 (or 1 for empty input).
         */
        LINES,
        /**
         * Number of nodes the lexer/parser would produce.
         */
        NODES,
        /**
         * Source byte count.
         */
        BYTES
    }
}
