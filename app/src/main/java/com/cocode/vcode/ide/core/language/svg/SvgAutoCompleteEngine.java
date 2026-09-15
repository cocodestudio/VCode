package com.cocode.vcode.ide.core.language.svg;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.FastTrie;
import com.cocode.vcode.ide.core.language.html.HtmlTagParser;
import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * High-performance, zero-allocation context-aware autocomplete engine for SVG files.
 * Uses HtmlTagParser's lexical state machine to determine whether the cursor is inside:
 * - An SVG tag name (recommending SVG elements: svg, path, rect, circle, g, defs, etc.)
 * - An SVG attribute name (recommending presentation & geometry attributes: viewBox, fill, stroke, d, cx, cy, etc.)
 * - An SVG attribute value (recommending namespaces, colors, cap/join types, units)
 * - A closing tag (suggesting matching closing tags with exact replace length)
 * - Text content (offering SVG snippet templates when typing '<')
 */
public class SvgAutoCompleteEngine extends AutoCompleteEngine {

    private final List<String> svgTags = new ArrayList<>();
    private final List<String> commonPresentationAttrs = new ArrayList<>();
    private final Map<String, String[]> elementSpecificAttrs = new HashMap<>();
    private final Map<String, String[]> attrValues = new HashMap<>();
    private final List<CompletionItem> staticSnippets = new ArrayList<>();

    private final FastTrie tagTrie = new FastTrie();
    private final FastTrie attrTrie = new FastTrie();

    public SvgAutoCompleteEngine(Context context) {
        super(context);
        loadSvgCompletions();
    }

    private void loadSvgCompletions() {
        try {
            String json = loadAssetJson("completions/svg_completions.json");
            org.json.JSONObject root = new org.json.JSONObject(json);

            org.json.JSONArray tagsArr = root.optJSONArray("tags");
            if (tagsArr != null) {
                for (int i = 0; i < tagsArr.length(); i++) {
                    String tag = tagsArr.getString(i);
                    svgTags.add(tag);
                    tagTrie.insert(new CompletionItem(tag, tag, "SVG element", CompletionItem.Type.TAG, 0));
                }
            }

            org.json.JSONArray presAttrsArr = root.optJSONArray("presentationAttributes");
            if (presAttrsArr != null) {
                for (int i = 0; i < presAttrsArr.length(); i++) {
                    String attr = presAttrsArr.getString(i);
                    commonPresentationAttrs.add(attr);
                    attrTrie.insert(new CompletionItem(attr, attr + "=\"\"", "SVG attribute", CompletionItem.Type.ATTRIBUTE, -1));
                }
            }

            org.json.JSONObject specificObj = root.optJSONObject("elementSpecificAttributes");
            if (specificObj != null) {
                java.util.Iterator<String> keys = specificObj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    org.json.JSONArray arr = specificObj.optJSONArray(key);
                    if (arr != null) {
                        String[] list = new String[arr.length()];
                        for (int j = 0; j < arr.length(); j++) list[j] = arr.getString(j);
                        elementSpecificAttrs.put(key, list);
                    }
                }
            }

            org.json.JSONObject valObj = root.optJSONObject("attributeValues");
            if (valObj != null) {
                java.util.Iterator<String> keys = valObj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    org.json.JSONArray arr = valObj.optJSONArray(key);
                    if (arr != null) {
                        String[] list = new String[arr.length()];
                        for (int j = 0; j < arr.length(); j++) list[j] = arr.getString(j);
                        attrValues.put(key, list);
                    }
                }
            }

            org.json.JSONArray snippetsArr = root.optJSONArray("snippets");
            if (snippetsArr != null) {
                for (int i = 0; i < snippetsArr.length(); i++) {
                    org.json.JSONObject sObj = snippetsArr.getJSONObject(i);
                    String label = sObj.getString("label");
                    String insertText = sObj.optString("insertText", label);
                    String detail = sObj.optString("detail", "");
                    staticSnippets.add(new CompletionItem(label, insertText, detail, CompletionItem.Type.SNIPPET, 0));
                }
            }
        } catch (Exception ignored) { }
    }

    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length()) {
            return Collections.emptyList();
        }

        HtmlTagParser.HtmlContext ctx = HtmlTagParser.parseContext(fullText, cursorPos);

        // Suppress completions inside SVG comments
        if (ctx.isInsideComment) {
            return Collections.emptyList();
        }

        String word = getWordBeforeCursor(fullText, cursorPos);

        // Suggest matching closing tag
        if (ctx.isInsideCloseTag) {
            return getCloseTagSuggestions(fullText, cursorPos);
        }

        // Inside attribute value
        if (ctx.isInsideAttributeValue) {
            return getAttributeValueSuggestions(ctx, word);
        }

        // Inside attribute name
        if (ctx.isInsideOpenTag && !ctx.isTypingTagName) {
            return getAttributeNameSuggestions(ctx, word);
        }

        // Inside tag name
        if (ctx.isInsideOpenTag && ctx.isTypingTagName) {
            return getTagSuggestions(word);
        }

        // Outside tags: suggest element tags after '<' or snippets in content positions
        if (cursorPos > 0 && fullText.charAt(cursorPos - 1) == '<') {
            return getTagSuggestions(word);
        }

        return getSnippetSuggestions(word);
    }

    private List<CompletionItem> getCloseTagSuggestions(String fullText, int cursorPos) {
        int slashPos = cursorPos - 1;
        while (slashPos >= 0 && fullText.charAt(slashPos) != '/') {
            if (fullText.charAt(slashPos) == '\n' || fullText.charAt(slashPos) == '<') break;
            slashPos--;
        }

        int afterSlashStart = slashPos >= 0 ? slashPos + 1 : cursorPos;
        String afterSlash = fullText.substring(afterSlashStart, cursorPos);
        String lowerAfterSlash = afterSlash.toLowerCase();
        int replaceLength = (cursorPos - Math.max(0, slashPos - 1));

        List<CompletionItem> result = new ArrayList<>();
        for (String tag : svgTags) {
            int score = afterSlash.isEmpty() ? 50 : computeFuzzyScore(tag, lowerAfterSlash);
            if (score > 0) {
                CompletionItem item = new CompletionItem("</" + tag + ">", "</" + tag + ">", "Close SVG tag", CompletionItem.Type.TAG, 0);
                item.setSortScore(score);
                item.setReplaceLength(replaceLength);
                result.add(item);
            }
        }
        Collections.sort(result, (a, b) -> Integer.compare(b.getSortScore(), a.getSortScore()));
        return truncate(result);
    }

    private List<CompletionItem> getAttributeNameSuggestions(HtmlTagParser.HtmlContext ctx, String word) {
        List<CompletionItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String lowerWord = word.toLowerCase();

        String tagName = ctx.currentTagName != null ? ctx.currentTagName.toLowerCase() : "";

        // Element-specific attributes first (higher rank)
        String[] specific = elementSpecificAttrs.get(tagName);
        if (specific != null) {
            for (String attr : specific) {
                int score = word.isEmpty() ? 70 : computeFuzzyScore(attr, lowerWord);
                if (score > 0) {
                    CompletionItem item = new CompletionItem(attr, attr + "=\"\"", "SVG attribute", CompletionItem.Type.ATTRIBUTE, -1);
                    item.setSortScore(score + 20); // Specific attributes boosted
                    item.setReplaceLength(word.length());
                    result.add(item);
                    seen.add(attr);
                }
            }
        }

        // Common presentation attributes
        for (String attr : commonPresentationAttrs) {
            if (seen.contains(attr)) continue;
            int score = word.isEmpty() ? 50 : computeFuzzyScore(attr, lowerWord);
            if (score > 0) {
                CompletionItem item = new CompletionItem(attr, attr + "=\"\"", "SVG presentation attribute", CompletionItem.Type.ATTRIBUTE, -1);
                item.setSortScore(score);
                item.setReplaceLength(word.length());
                result.add(item);
            }
        }

        Collections.sort(result, (a, b) -> Integer.compare(b.getSortScore(), a.getSortScore()));
        return truncate(result);
    }

    private List<CompletionItem> getAttributeValueSuggestions(HtmlTagParser.HtmlContext ctx, String word) {
        String attrName = ctx.currentAttributeName != null ? ctx.currentAttributeName.toLowerCase() : "";
        String[] values = attrValues.get(attrName);
        if (values == null) return Collections.emptyList();

        List<CompletionItem> result = new ArrayList<>();
        String lowerWord = word.toLowerCase();
        for (String val : values) {
            int score = word.isEmpty() ? 60 : computeFuzzyScore(val, lowerWord);
            if (score > 0) {
                CompletionItem item = new CompletionItem(val, val, "Attribute value", CompletionItem.Type.VALUE, 0);
                item.setSortScore(score);
                item.setReplaceLength(word.length());
                result.add(item);
            }
        }
        Collections.sort(result, (a, b) -> Integer.compare(b.getSortScore(), a.getSortScore()));
        return truncate(result);
    }

    private List<CompletionItem> getTagSuggestions(String word) {
        List<CompletionItem> result = new ArrayList<>();
        String lowerWord = word.toLowerCase();
        for (String tag : svgTags) {
            int score = word.isEmpty() ? 50 : computeFuzzyScore(tag, lowerWord);
            if (score > 0) {
                CompletionItem item = new CompletionItem(tag, tag, "SVG element", CompletionItem.Type.TAG, 0);
                item.setSortScore(score);
                item.setReplaceLength(word.length());
                result.add(item);
            }
        }
        Collections.sort(result, (a, b) -> Integer.compare(b.getSortScore(), a.getSortScore()));
        return truncate(result);
    }

    private List<CompletionItem> getSnippetSuggestions(String word) {
        List<CompletionItem> result = new ArrayList<>();
        String lowerWord = word.toLowerCase();
        for (CompletionItem snip : staticSnippets) {
            int score = word.isEmpty() ? 40 : computeFuzzyScore(snip.getLabel(), lowerWord);
            if (score > 0) {
                CompletionItem item = new CompletionItem(snip.getLabel(), snip.getEffectiveInsertText(), snip.getDetail(), CompletionItem.Type.SNIPPET, 0);
                item.setSortScore(score);
                item.setReplaceLength(word.length());
                result.add(item);
            }
        }
        Collections.sort(result, (a, b) -> Integer.compare(b.getSortScore(), a.getSortScore()));
        return truncate(result);
    }

    private List<CompletionItem> truncate(List<CompletionItem> items) {
        if (items.size() > MAX_SUGGESTIONS) {
            return new ArrayList<>(items.subList(0, MAX_SUGGESTIONS));
        }
        return items;
    }
}
