package com.cocode.vcode.ide.core.completion.staticdata;

import com.cocode.vcode.ide.core.language.css.CssSyntaxTree;
import com.cocode.vcode.ide.core.language.css.CssTokenStream;
import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CSS completion position dispatcher.
 *
 * <p>Decides which static-completion subset (property names,
 * property values, colors, global functions) is appropriate for
 * the cursor's current position inside a CSS rule body. The decision
 * is made purely from the source text + the {@link CssTokenStream}
 * + a tiny AST walk — no per-keystroke re-parse is needed beyond
 * what the pipeline already produces.
 *
 * <p>Position rules:
 * <ul>
 *   <li><b>Property-name position</b>: cursor sits between the
 *       start of a declaration (right after a {@code {} or {@code ;})
 *       and the {@code :} that introduces a value. Offer exactly the
 *       property labels from {@code css_properties.json}.</li>
 *   <li><b>Property-value position</b>: cursor sits after the
 *       {@code :} of a specific declaration. Look up the property
 *       in the static map and offer its {@code values} plus
 *       {@code global_functions}; if the property is color-accepting,
 *       also merge {@code colors} + {@code color_functions}.</li>
 *   <li><b>Unknown-property fallback</b>: when the property name
 *       doesn't appear in the static map, offer
 *       {@code global_functions} only.</li>
 *   <li><b>Not in a rule body</b>: return empty. The selector /
 *       at-rule / outside-of-any-rule positions are out of scope for
 *       static completions.</li>
 * </ul>
 */
public final class CssStaticCompletionDispatcher {

    /**
     * Build the static completion list for the cursor's current
     * position. Returns an empty list for {@link Position#NONE}.
     *
     * <p>For {@link Position#PROPERTY_NAME}: returns one
     * {@link CompletionItem} per property in
     * {@link StaticCompletionLoader#getCssProperties()}.
     *
     * <p>For {@link Position#PROPERTY_VALUE}: resolves
     * the declaration's property name, looks it up, and merges:
     * <ul>
     *   <li>the property's {@code values} array</li>
     *   <li>{@code global_functions} always</li>
     *   <li>{@code colors} + {@code color_functions} when
     *       {@code acceptsColor} is true</li>
     *   <li>unknown property: {@code global_functions} only</li>
     * </ul>
     *
     * <p>This method produces {@link CompletionItem}s from
     * {@link StaticCompletionItem}s; the conversion is the one
     * place where strings are concatenated (the insertText), so
     * the per-call allocation is bounded.
     */
    private static volatile List<CompletionItem> CACHED_PROP_NAMES = null;
    private static volatile List<CompletionItem> CACHED_GLOBAL_FUNCS = null;
    private static volatile List<CompletionItem> CACHED_COLORS = null;
    private static volatile List<CompletionItem> CACHED_COLOR_FUNCS = null;
    private static volatile java.util.Map<String, List<CompletionItem>> CACHED_PROP_VALUES = null;
    private CssStaticCompletionDispatcher() {
    }

    /**
     * Detect the cursor's position within a CSS source.
     *
     * @param source the full CSS source text
     * @param tokens the token stream for {@code source}
     * @param tree   the parsed CSS syntax tree (may be {@code null};
     *               the detector falls back to text-only heuristics)
     * @param cursor 0-based source offset of the cursor
     * @return the resolved {@link Position}
     */
    public static Position detectPosition(String source, CssTokenStream tokens,
                                          CssSyntaxTree tree, int cursor) {
        if (source == null || cursor < 0 || cursor > source.length()) return Position.NONE;
        // Find the enclosing block by scanning for '{' and '}' before
        // the cursor, then check whether the cursor is between '{' and
        // the matching '}'. If not inside a block, return NONE.
        int openBrace = -1;
        for (int i = cursor - 1; i >= 0; i--) {
            char c = source.charAt(i);
            if (c == '}') return Position.NONE; // outside any block
            if (c == '{') {
                openBrace = i;
                break;
            }
        }
        if (openBrace < 0) return Position.NONE;

        // Inside a block. Walk forward from openBrace to the cursor
        // to find the most recent declaration boundary (';' or the
        // '{' itself) and the most recent ':'.
        int lastBoundary = openBrace;
        int lastColon = -1;
        for (int i = openBrace + 1; i < cursor; i++) {
            char c = source.charAt(i);
            if (c == ';' || c == '{') lastBoundary = i;
            else if (c == ':') lastColon = i;
        }
        // Property-value position: there is a ':' after the most
        // recent boundary.
        if (lastColon > lastBoundary) return Position.PROPERTY_VALUE;
        // Property-name position: no ':' after the most recent
        // boundary.
        return Position.PROPERTY_NAME;
    }

    /**
     * Resolve the property name of the declaration the cursor is
     * currently inside. Returns the property name (trimmed) if
     * found, or {@code null} if not in a value position.
     */
    public static String resolvePropertyName(String source, CssTokenStream tokens, int cursor) {
        if (source == null || cursor < 0 || cursor > source.length()) return null;
        int openBrace = -1;
        for (int i = cursor - 1; i >= 0; i--) {
            char c = source.charAt(i);
            if (c == '}') return null;
            if (c == '{') {
                openBrace = i;
                break;
            }
        }
        if (openBrace < 0) return null;
        // Find the last ';' or '{' before cursor, then the ':' after
        // that boundary, then read the property-name token
        // between the boundary and the ':'.
        int lastBoundary = openBrace;
        int lastColon = -1;
        for (int i = openBrace + 1; i < cursor; i++) {
            char c = source.charAt(i);
            if (c == ';' || c == '{') lastBoundary = i;
            else if (c == ':') lastColon = i;
        }
        if (lastColon <= lastBoundary) return null;
        // Property name is the trimmed substring from (lastBoundary+1)
        // to (lastColon). Skip leading whitespace.
        int nameStart = lastBoundary + 1;
        while (nameStart < lastColon && Character.isWhitespace(source.charAt(nameStart))) {
            nameStart++;
        }
        if (nameStart >= lastColon) return null;
        return source.substring(nameStart, lastColon).trim();
    }

    public static void clearCachesForTest() {
        CACHED_PROP_NAMES = null;
        CACHED_GLOBAL_FUNCS = null;
        CACHED_COLORS = null;
        CACHED_COLOR_FUNCS = null;
        CACHED_PROP_VALUES = null;
    }

    private static void initCssCaches() {
        if (CACHED_PROP_NAMES != null) return;

        List<CompletionItem> names = new ArrayList<>();
        java.util.Map<String, List<CompletionItem>> vals = new java.util.HashMap<>();
        for (StaticCompletionItem p : StaticCompletionLoader.getCssProperties()) {
            names.add(new CompletionItem(p.label, p.label, "CSS property", CompletionItem.Type.CSS_PROPERTY, 0));
            if (p.values != null) {
                List<CompletionItem> pVals = new ArrayList<>();
                for (String v : p.values) {
                    pVals.add(new CompletionItem(v, v, "CSS value", CompletionItem.Type.CSS_VALUE, 0));
                }
                vals.put(p.label, pVals);
            }
        }
        CACHED_PROP_VALUES = vals;
        CACHED_PROP_NAMES = names;

        List<CompletionItem> gFuncs = new ArrayList<>();
        for (String f : StaticCompletionLoader.getCssGlobalFunctions()) {
            gFuncs.add(new CompletionItem(f, f, "CSS function", CompletionItem.Type.CSS_VALUE, 0));
        }
        CACHED_GLOBAL_FUNCS = gFuncs;

        List<CompletionItem> colors = new ArrayList<>();
        for (String c : StaticCompletionLoader.getCssColors()) {
            colors.add(new CompletionItem(c, c, "CSS color", CompletionItem.Type.CSS_VALUE, 0));
        }
        CACHED_COLORS = colors;

        List<CompletionItem> cFuncs = new ArrayList<>();
        for (String cf : StaticCompletionLoader.getCssColorFunctions()) {
            cFuncs.add(new CompletionItem(cf, cf, "CSS color function", CompletionItem.Type.CSS_VALUE, 0));
        }
        CACHED_COLOR_FUNCS = cFuncs;
    }

    /**
     * Build the static completion list for the cursor's current
     * position. Returns an empty list for {@link Position#NONE}.
     */
    public static List<CompletionItem> buildCompletions(Position position,
                                                        String source,
                                                        int cursor) {
        if (position == Position.NONE) return new ArrayList<>();
        initCssCaches();
        if (position == Position.PROPERTY_NAME) {
            return new ArrayList<>(CACHED_PROP_NAMES);
        }
        // PROPERTY_VALUE
        List<CompletionItem> out = new ArrayList<>();
        String propName = resolvePropertyName(source, null, cursor);
        StaticCompletionItem prop = StaticCompletionLoader.getCssProperty(propName);
        Set<String> seen = new HashSet<>();

        if (prop != null) {
            List<CompletionItem> pVals = CACHED_PROP_VALUES.get(prop.label);
            if (pVals != null) {
                for (CompletionItem ci : pVals) {
                    if (seen.add(ci.getLabel())) out.add(ci);
                }
            }
        }
        // Always offer global functions.
        for (CompletionItem ci : CACHED_GLOBAL_FUNCS) {
            if (seen.add(ci.getLabel())) out.add(ci);
        }
        // Color-accepting properties get colors + color_functions
        if (prop != null && prop.acceptsColor) {
            for (CompletionItem ci : CACHED_COLORS) {
                if (seen.add(ci.getLabel())) out.add(ci);
            }
            for (CompletionItem ci : CACHED_COLOR_FUNCS) {
                if (seen.add(ci.getLabel())) out.add(ci);
            }
        }
        return out;
    }

    /**
     * Resolved cursor position within a CSS rule.
     */
    public enum Position {
        /**
         * Cursor is in property-name position (before {@code :}).
         */
        PROPERTY_NAME,
        /**
         * Cursor is in property-value position (after {@code :}).
         */
        PROPERTY_VALUE,
        /**
         * Cursor is not inside a rule body's declaration.
         */
        NONE
    }
}
