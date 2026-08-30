package com.cocode.vcode.ide.core.completion.staticdata;

import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.ArrayList;
import java.util.List;

/**
 * HTML completion position dispatcher.
 *
 * <p>Decides which static-completion subset (tag names, attribute
 * names, closing tags) is appropriate for the cursor's current
 * position inside an HTML document. Pure text + token analysis
 * — no per-keystroke re-parse.
 *
 * <p>Position rules:
 * <ul>
 *   <li><b>Tag-name position</b>: cursor sits right after
 *       {@code <} (and not after {@code </}) — offer all tag
 *       labels from {@code html_tags.json}.</li>
 *   <li><b>Attribute-name position</b>: cursor sits inside
 *       an open tag, after a tag name or after another attribute,
 *       before {@code =} — offer that tag's {@code attributes}
 *       plus global attributes/prefixes, minus any
 *       attribute already present on this tag instance.</li>
 *   <li><b>Closing-tag position</b>: cursor sits right
 *       after {@code </} — the AST (open-tag ancestry) is the
 *       sole source. Static data is not consulted here.</li>
 *   <li><b>None of the above</b>: return empty. Tag-attribute-value
 *       positions and content positions are out of scope for
 *       static completions.</li>
 * </ul>
 */
public final class HtmlStaticCompletionDispatcher {

    private HtmlStaticCompletionDispatcher() {}

    public enum Position {
        TAG_NAME,
        ATTRIBUTE_NAME,
        CLOSING_TAG,
        NONE
    }

    /**
     * Detect the cursor's position within an HTML source.
     */
    public static Position detectPosition(String source, int cursor) {
        if (source == null || cursor < 0 || cursor > source.length()) return Position.NONE;
        int lastLt = -1, lastGt = -1;
        for (int i = cursor - 1; i >= 0; i--) {
            char c = source.charAt(i);
            if (c == '<') { lastLt = i; break; }
            if (c == '>') { lastGt = i; break; }
        }
        if (lastLt < 0) return Position.NONE;
        if (lastGt > lastLt) return Position.NONE;
        if (lastLt + 1 < source.length() && source.charAt(lastLt + 1) == '/') {
            return Position.CLOSING_TAG;
        }
        // Walk forward from '<' to the cursor, tracking whether
        // we're inside an attribute VALUE. Attribute-name position
        // is "outside any value, past the tag name". A '="..."'
        // sequence is a value; once we exit it (closing quote or
        // unquoted value ends at whitespace/'>'), we're back in
        // attribute-name position.
        int i = lastLt + 1;
        // Skip the tag name.
        while (i < cursor) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c) || c == '/' || c == '>') break;
            i++;
        }
        if (i >= cursor) {
            // Cursor is still inside the tag name.
            return Position.TAG_NAME;
        }
        // Walk the attribute list. State: inName | inValue.
        boolean inValue = false;
        char quote = 0;
        while (i < cursor) {
            char c = source.charAt(i);
            if (inValue) {
                if (quote != 0) {
                    if (c == quote) { inValue = false; quote = 0; }
                } else {
                    if (Character.isWhitespace(c) || c == '>' || c == '/') {
                        inValue = false;
                    }
                }
                i++;
                continue;
            }
            // inName
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '/' || c == '>') { i++; continue; }
            // Start of an attribute. Read its name.
            int attrStart = i;
            while (i < cursor) {
                char d = source.charAt(i);
                if (Character.isWhitespace(d) || d == '=' || d == '>' || d == '/') break;
                i++;
            }
            if (i >= cursor) {
                // Cursor is inside the attribute name (or right at
                // its end). Attribute-name position.
                return Position.ATTRIBUTE_NAME;
            }
            // Skip past '=' and value.
            while (i < cursor && (Character.isWhitespace(source.charAt(i)) || source.charAt(i) == '=')) i++;
            if (i >= cursor) {
                // Cursor is at the '=' or just after. Still
                // attribute-name position (the user is starting
                // to type the value, but value completion is
                // out of M.8 scope).
                return Position.ATTRIBUTE_NAME;
            }
            char v = source.charAt(i);
            if (v == '"' || v == '\'') {
                inValue = true;
                quote = v;
                i++;
            } else {
                inValue = true;
                quote = 0;
            }
        }
        // Cursor is past all attributes. If we ended in a value,
        // it's value position; otherwise attribute-name position.
        if (inValue) return Position.NONE;
        return Position.ATTRIBUTE_NAME;
    }

    /**
     * Resolve the tag name that opens the tag the cursor is
     * currently inside. Returns the lowercased tag name, or
     * {@code null} if the cursor is not inside an open tag.
     */
    public static String resolveOpenTagName(String source, int cursor) {
        if (source == null || cursor < 0 || cursor > source.length()) return null;
        int lastLt = -1;
        for (int i = cursor - 1; i >= 0; i--) {
            char c = source.charAt(i);
            if (c == '<') { lastLt = i; break; }
            if (c == '>') return null;
        }
        if (lastLt < 0) return null;
        // The tag name is the sequence of name characters
        // immediately after the '<'. Read until the first
        // whitespace, '/', or '>'.
        int i = lastLt + 1;
        if (i < source.length() && source.charAt(i) == '/') return null; // closing
        int nameStart = i;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c) || c == '/' || c == '>') break;
            i++;
        }
        if (i == nameStart) return null;
        return source.substring(nameStart, i).toLowerCase();
    }

    /**
     * Build the static completion list for the cursor's current
     * position.
     *
     * <p>For {@link Position#TAG_NAME}: one
     * {@link CompletionItem} per tag from
     * {@link StaticCompletionLoader#getHtmlTags()}, with the
     * tag's snippet applied through
     * {@link SnippetCursorParser}.
     */
    private static volatile List<CompletionItem> CACHED_TAG_COMPLETIONS = null;
    private static volatile List<CompletionItem> CACHED_GLOBAL_ATTRS = null;
    private static volatile List<CompletionItem> CACHED_GLOBAL_PREFIXES = null;
    private static volatile java.util.Map<String, List<CompletionItem>> CACHED_TAG_ATTRS = null;

    public static void clearCachesForTest() {
        CACHED_TAG_COMPLETIONS = null;
        CACHED_GLOBAL_ATTRS = null;
        CACHED_GLOBAL_PREFIXES = null;
        CACHED_TAG_ATTRS = null;
    }

    private static void initHtmlCaches() {
        if (CACHED_TAG_COMPLETIONS != null) return;
        
        List<CompletionItem> tags = new ArrayList<>();
        java.util.Map<String, List<CompletionItem>> attrs = new java.util.HashMap<>();
        
        for (StaticCompletionItem t : StaticCompletionLoader.getHtmlTags()) {
            SnippetCursorParser.Result r = SnippetCursorParser.parse(t.insertText);
            tags.add(new CompletionItem(t.label, r != null ? r.insertText : t.insertText,
                    "HTML " + (t.detail == null ? "tag" : t.detail),
                    CompletionItem.Type.TAG, r != null ? r.cursorOffset : 0));
                    
            if (t.attributes != null) {
                List<CompletionItem> tagAttrs = new ArrayList<>();
                for (String a : t.attributes) {
                    tagAttrs.add(new CompletionItem(a, a + "=\"|\"", "Attribute", CompletionItem.Type.ATTRIBUTE, 0));
                }
                attrs.put(t.label.toLowerCase(), tagAttrs);
            }
        }
        CACHED_TAG_ATTRS = attrs;
        CACHED_TAG_COMPLETIONS = tags;
        
        List<CompletionItem> globals = new ArrayList<>();
        for (String a : StaticCompletionLoader.getHtmlGlobalAttributes()) {
            globals.add(new CompletionItem(a, a + "=\"|\"", "Global attribute", CompletionItem.Type.ATTRIBUTE, 0));
        }
        CACHED_GLOBAL_ATTRS = globals;
        
        List<CompletionItem> prefixes = new ArrayList<>();
        for (String p : StaticCompletionLoader.getHtmlGlobalAttributePrefixes()) {
            prefixes.add(new CompletionItem(p, p + "=\"|\"", "Global attribute prefix", CompletionItem.Type.ATTRIBUTE, 0));
        }
        CACHED_GLOBAL_PREFIXES = prefixes;
    }

    /**
     * Build the static completion list for the cursor's current
     * position.
     *
     * <p>For {@link Position#TAG_NAME}: one
     * {@link CompletionItem} per tag from
     * {@link StaticCompletionLoader#getHtmlTags()}, with the
     * tag's snippet applied through
     * {@link SnippetCursorParser}.
     */
    public static List<CompletionItem> buildCompletions(Position position, String source, int cursor) {
        if (position == Position.NONE || position == Position.CLOSING_TAG) return new ArrayList<>();
        initHtmlCaches();
        if (position == Position.TAG_NAME) {
            return new ArrayList<>(CACHED_TAG_COMPLETIONS);
        }
        if (position == Position.ATTRIBUTE_NAME) {
            List<CompletionItem> out = new ArrayList<>();
            // resolve the tag, merge its attributes with
            // global attrs/prefixes, exclude attributes already
            // present on this tag instance.
            String tag = resolveOpenTagName(source, cursor);
            java.util.Set<String> alreadyPresent = collectAttributesOnTag(source, cursor);
            java.util.Set<String> seen = new java.util.HashSet<>(alreadyPresent);
            if (tag != null) {
                List<CompletionItem> tagAttrs = CACHED_TAG_ATTRS.get(tag);
                if (tagAttrs != null) {
                    for (CompletionItem ci : tagAttrs) {
                        if (seen.add(ci.getLabel())) {
                            out.add(ci);
                        }
                    }
                }
            }
            // Global attributes.
            for (CompletionItem ci : CACHED_GLOBAL_ATTRS) {
                if (seen.add(ci.getLabel())) {
                    out.add(ci);
                }
            }
            // Prefix-only ?" emit the prefix as a completion so the
            // user can type `data-` and the editor expands to
            // `data-` with a value placeholder.
            for (CompletionItem ci : CACHED_GLOBAL_PREFIXES) {
                if (seen.add(ci.getLabel())) {
                    out.add(ci);
                }
            }
            return out;
        }
        return new ArrayList<>();
    }

    private static StaticCompletionItem findTagByName(String name) {
        if (name == null) return null;
        for (StaticCompletionItem t : StaticCompletionLoader.getHtmlTags()) {
            if (name.equalsIgnoreCase(t.label)) return t;
        }
        return null;
    }

    /**
     * Walk the open tag's attribute list and return the set of
     * attribute names already present (so we don't suggest
     * duplicates). Returns an empty set if no attributes are
     * present.
     */
    private static java.util.Set<String> collectAttributesOnTag(String source, int cursor) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (source == null) return out;
        int lastLt = -1;
        for (int i = cursor - 1; i >= 0; i--) {
            char c = source.charAt(i);
            if (c == '<') { lastLt = i; break; }
            if (c == '>') return out;
        }
        if (lastLt < 0) return out;
        // Find the end of the tag name (first whitespace or '>'
        // or '/' after '<').
        int nameEnd = lastLt + 1;
        while (nameEnd < source.length()) {
            char c = source.charAt(nameEnd);
            if (Character.isWhitespace(c) || c == '/' || c == '>') break;
            nameEnd++;
        }
        // Walk from nameEnd to cursor, splitting on whitespace.
        int i = nameEnd;
        while (i < cursor) {
            // Skip whitespace.
            while (i < cursor && Character.isWhitespace(source.charAt(i))) i++;
            if (i >= cursor) break;
            // Read attribute name (up to '=' or whitespace or '>').
            int attrStart = i;
            while (i < cursor) {
                char c = source.charAt(i);
                if (Character.isWhitespace(c) || c == '=' || c == '>' || c == '/') break;
                i++;
            }
            if (i > attrStart) {
                out.add(source.substring(attrStart, i));
            }
            // Skip past the '=' and the value if present.
            while (i < cursor && (Character.isWhitespace(source.charAt(i)) || source.charAt(i) == '=')) i++;
            if (i < cursor && (source.charAt(i) == '"' || source.charAt(i) == '\'')) {
                char quote = source.charAt(i);
                i++;
                while (i < cursor && source.charAt(i) != quote) i++;
                if (i < cursor) i++; // closing quote
            } else {
                // Unquoted value: read until whitespace.
                while (i < cursor && !Character.isWhitespace(source.charAt(i))) i++;
            }
        }
        return out;
    }
}
