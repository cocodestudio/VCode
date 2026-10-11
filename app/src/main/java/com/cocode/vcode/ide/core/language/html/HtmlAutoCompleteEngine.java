package com.cocode.vcode.ide.core.language.html;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.EmmetParser;
import com.cocode.vcode.ide.core.autocomplete.FastTrie;
import com.cocode.vcode.ide.core.autocomplete.PathAutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex;
import com.cocode.vcode.ide.core.autocomplete.VFSManager;
import com.cocode.vcode.ide.core.language.css.CssAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.data.repository.ProjectRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Context-aware completion engine for HTML source code.
 *
 * <p>Features:
 * <ul>
 *   <li>Fuzzy scoring via {@link AutoCompleteEngine#fuzzyFilter}</li>
 *   <li>Tag completions with self-closing awareness (void elements get {@code />})</li>
 *   <li>Attribute completions per tag + global HTML attributes</li>
 *   <li>Attribute-value enumerations (e.g., {@code type="…"} → text, email, checkbox…)</li>
 *   <li>Inline {@code style="…"} delegates to {@link CssAutoCompleteEngine}</li>
 *   <li>Inline {@code on*="…"} event-handler attributes delegate to {@link JsAutoCompleteEngine}</li>
 *   <li>Embedded {@code <style>} and {@code <script>} block delegation</li>
 *   <li>File-path completions for {@code src}, {@code href}, {@code action}, {@code data} attributes</li>
 *   <li>Emmet expansion with cursor positioning</li>
 *   <li>Closing-tag auto-suggestion on {@code </}</li>
 * </ul>
 */
public class HtmlAutoCompleteEngine extends AutoCompleteEngine {

    // Patterns removed in favor of high-performance State Machine parser

    // Instance state
    private static final FastTrie TAG_TRIE = new FastTrie();
    private final List<CompletionItem> tagItems = new ArrayList<>();
    private final HtmlTagParser tagParser = new HtmlTagParser();
    private final Map<String, List<CompletionItem>> attrMap = new HashMap<>();

    private final CssAutoCompleteEngine cssEngine;
    private final JsAutoCompleteEngine jsEngine;
    private final PathAutoCompleteEngine pathEngine;
    private File currentFile;
    private String htmlBoilerplate;

    public HtmlAutoCompleteEngine(Context context) {
        super(context);
        loadTags();
        this.cssEngine = new CssAutoCompleteEngine(context);
        this.jsEngine = new JsAutoCompleteEngine(context);
        this.pathEngine = new PathAutoCompleteEngine(context);
    }

    @Override
    public String getTriggerCharacters() {
        return "<'/\"!=:@-.*+^#&";
    }

    @Override
    public void setCurrentFile(File file) {
        super.setCurrentFile(file);
        this.currentFile = file;
        this.lastHtmlSelectorsHash = 0;
        this.lastHtmlJsHash = 0;
        jsEngine.setCurrentFile(file);
        cssEngine.setCurrentFile(file);
        if (pathEngine != null) pathEngine.setCurrentFile(file);
        cssEngine.setScopedHtmlSelectors(null, null);
    }

    // Tag loading

    /**
     * Initialises HTML tag completions and per-tag attribute lists from shared static registries.
     */
    private void loadTags() {
        tagItems.clear();
        tagItems.addAll(com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader.getHtmlTagCompletionItems());
        for (CompletionItem item : tagItems) {
            TAG_TRIE.insert(item);
        }
        attrMap.clear();
        attrMap.putAll(com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader.getHtmlTagAttributeMap());
        htmlBoilerplate = com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader.getHtmlBoilerplate();
    }

    // Main entry point
    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length()) {
            return new ArrayList<>();
        }

        String lineBefore = getLineBeforeCursor(fullText, cursorPos);
        String trimmed = lineBefore.trim();
        String word = getWordBeforeCursor(fullText, cursorPos);

        // Tag closure '>': directly after typing '>', suppress completion popup
        if (lineBefore.endsWith(">")) {
            return Collections.emptyList();
        }

        // Suppress completions inside HTML comments
        if (isInsideComment(fullText, cursorPos)) {
            return new ArrayList<>();
        }

        // DOCTYPE completions (e.g. <!DOCTYPE html> or comment boilerplate)
        int lastLt = lineBefore.lastIndexOf('<');
        if (lastLt >= 0 && (trimmed.startsWith("<!") || trimmed.startsWith("<!--"))) {
            String prefix = lineBefore.substring(lastLt);
            List<CompletionItem> doctypeResults = fuzzyFilter(HtmlDefinitions.getDoctypeItems(), prefix);
            if (!doctypeResults.isEmpty()) {
                List<CompletionItem> out = new ArrayList<>();
                for (CompletionItem ci : doctypeResults) {
                    CompletionItem copy = new CompletionItem(ci);
                    copy.setReplaceLength(prefix.length());
                    out.add(copy);
                }
                return out;
            }
        }

        // HTML entity completions when typing '&'
        if (!lineBefore.isEmpty()) {
            int ampIdx = lineBefore.lastIndexOf('&');
            if (ampIdx >= 0) {
                String afterAmp = lineBefore.substring(ampIdx + 1);
                if (!afterAmp.contains(";") && !afterAmp.contains(" ") && afterAmp.length() <= 10) {
                    HtmlTagParser.HtmlContext ctx = HtmlTagParser.parseContext(fullText, cursorPos);
                    boolean insideCode = (("script".equalsIgnoreCase(ctx.unclosedTag) || "style".equalsIgnoreCase(ctx.unclosedTag)))
                            || (ctx.isInsideAttributeValue && ctx.currentAttributeName != null &&
                            (ctx.currentAttributeName.startsWith("on") || "style".equalsIgnoreCase(ctx.currentAttributeName)));
                    if (!insideCode) {
                        String entityFilter = "&" + afterAmp;
                        List<CompletionItem> entityResults = fuzzyFilter(HtmlDefinitions.getEntityItems(), entityFilter);
                        if (!entityResults.isEmpty()) {
                            List<CompletionItem> out = new ArrayList<>();
                            for (CompletionItem ci : entityResults) {
                                CompletionItem copy = new CompletionItem(ci);
                                copy.setReplaceLength(entityFilter.length());
                                out.add(copy);
                            }
                            return out;
                        }
                    }
                }
            }
        }

        // Delegate to CSS/JS completion engines for embedded <style> and <script> blocks
        if (currentFile != null) {
            com.cocode.vcode.ide.core.language.js.ParseResult cached = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().getParseResult(currentFile.getAbsolutePath());
            if (cached != null && cached.embeddedResults != null) {
                for (com.cocode.vcode.ide.core.language.js.ParseResult.EmbeddedResult emb : cached.embeddedResults) {
                    if (cursorPos >= emb.startOffset && cursorPos <= emb.endOffset) {
                        String embeddedContent = fullText.substring(emb.startOffset, Math.min(emb.endOffset, fullText.length()));
                        int embeddedCursor = cursorPos - emb.startOffset;
                        if (emb.result.tree != null) {
                            ensureHtmlSelectorsResolved(fullText);
                            ensureHtmlJsSymbolsResolved(fullText);
                            return jsEngine.getSuggestions(embeddedContent, embeddedCursor);
                        } else if (emb.result.cssTree != null) {
                            ensureHtmlSelectorsResolved(fullText);
                            cssEngine.setScopedHtmlSelectors(cachedHtmlClassItems, cachedHtmlIdItems);
                            return cssEngine.getSuggestions(embeddedContent, embeddedCursor);
                        }
                    }
                }
            }
        }

        // 3b. Real-time fallback for embedded <style> / <script> if ProjectIndex is stale or null
        int styleStart = findBlockContentStart(fullText, cursorPos, "style");
        if (styleStart >= 0 && cursorPos >= styleStart) {
            int styleEnd = fullText.indexOf("</style", styleStart);
            if (styleEnd == -1 || cursorPos <= styleEnd) {
                String embeddedContent = fullText.substring(styleStart, styleEnd == -1 ? fullText.length() : styleEnd);
                int embeddedCursor = cursorPos - styleStart;
                ensureHtmlSelectorsResolved(fullText);
                cssEngine.setScopedHtmlSelectors(cachedHtmlClassItems, cachedHtmlIdItems);
                return cssEngine.getSuggestions(embeddedContent, embeddedCursor);
            }
        }
        int scriptStart = findBlockContentStart(fullText, cursorPos, "script");
        if (scriptStart >= 0 && cursorPos >= scriptStart) {
            int scriptEnd = fullText.indexOf("</script", scriptStart);
            if (scriptEnd == -1 || cursorPos <= scriptEnd) {
                String embeddedContent = fullText.substring(scriptStart, scriptEnd == -1 ? fullText.length() : scriptEnd);
                int embeddedCursor = cursorPos - scriptStart;
                ensureHtmlSelectorsResolved(fullText);
                ensureHtmlJsSymbolsResolved(fullText);
                return jsEngine.getSuggestions(embeddedContent, embeddedCursor);
            }
        }

        // 6. Context Scanning via Lexical State Machine
        HtmlTagParser.HtmlContext ctx = HtmlTagParser.parseContext(fullText, cursorPos);
        if (ctx.isInsideComment) {
            return Collections.emptyList();
        }

        // 7. Closing Tag Context (Explicitly typing "</")
        // Guarded: only triggers if cursor is typing directly after "</"
        int lastCloseTagIdx = lineBefore.lastIndexOf("</");
        if (ctx.isInsideCloseTag || (lastCloseTagIdx != -1 && isDirectlyAfterCloseTag(lineBefore, lastCloseTagIdx))) {
            if (lastCloseTagIdx != -1) {
                String afterSlash = lineBefore.substring(lastCloseTagIdx + 2);
                boolean validTagChars = true;
                for (int i = 0; i < afterSlash.length(); i++) {
                    char ch = afterSlash.charAt(i);
                    if (!Character.isLetterOrDigit(ch) && ch != '-' && ch != '_') {
                        validTagChars = false;
                        break;
                    }
                }
                if (validTagChars) {
                    List<String> unclosedTags = new ArrayList<>(ctx.openTags);
                    if (unclosedTags.isEmpty() && ctx.unclosedTag != null && !ctx.unclosedTag.isEmpty()) {
                        unclosedTags.add(ctx.unclosedTag);
                    }
                    if (unclosedTags.isEmpty()) {
                        try {
                            HtmlTokenStream tokens = HtmlLexer.tokenize(fullText);
                            com.cocode.vcode.ide.core.language.js.ParseResult parseRes = HtmlParser.parse(fullText, tokens);
                            int elem = parseRes.htmlTree.getEnclosingElement(cursorPos);
                            while (elem > 0 && elem < parseRes.htmlTree.nodeCount) {
                                if (parseRes.htmlTree.nodeType[elem] == HtmlSyntaxTree.N_ELEMENT && parseRes.htmlTree.nodeName[elem] != null) {
                                    String name = parseRes.htmlTree.nodeName[elem];
                                    if (!unclosedTags.contains(name)) {
                                        unclosedTags.add(name);
                                    }
                                }
                                elem = parseRes.htmlTree.nodeParent[elem];
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    if (!unclosedTags.isEmpty()) {
                        List<CompletionItem> result = new ArrayList<>();
                        for (String unclosedTag : unclosedTags) {
                            if (afterSlash.isEmpty() || unclosedTag.toLowerCase().startsWith(afterSlash.toLowerCase())) {
                                CompletionItem ci = new CompletionItem(
                                        "</" + unclosedTag + ">",
                                        "</" + unclosedTag + ">",
                                        "Close tag <" + unclosedTag + ">",
                                        CompletionItem.Type.TAG, 0);
                                ci.setReplaceLength(2 + afterSlash.length());
                                result.add(ci);
                            }
                        }
                        if (!result.isEmpty()) {
                            return result;
                        }
                    }
                }
            }
            return Collections.emptyList();
        }

        // 8. Inside Open Tag Context
        if (ctx.isInsideOpenTag) {
            // 8a. Attribute Value position
            if (ctx.isInsideAttributeValue && ctx.currentAttributeName != null) {
                String attrName = ctx.currentAttributeName;
                String typedValue = ctx.currentAttributeValue != null ? ctx.currentAttributeValue : "";

                // style="..." -> forward to cssEngine inline
                if ("style".equals(attrName)) {
                    return cssEngine.getSuggestions(typedValue, typedValue.length(), true);
                }

                // on*="..." -> forward to jsEngine
                if (attrName.startsWith("on")) {
                    ensureHtmlSelectorsResolved(fullText);
                    ensureHtmlJsSymbolsResolved(fullText);
                    return jsEngine.getSuggestions(typedValue, typedValue.length());
                }

                // File path attributes
                if (attrName.equals("src") || attrName.equals("href") || attrName.equals("action") ||
                        attrName.equals("formaction") || attrName.equals("poster") || attrName.equals("data") ||
                        attrName.equals("cite") || attrName.equals("manifest") || attrName.equals("srcset")) {
                    String pathQuery = getPathQuery(typedValue, attrName);
                    return getFileSuggestions(pathQuery, ctx.currentTagName, attrName);
                }

                int lastSpace = typedValue.lastIndexOf(' ');
                String attrWord = lastSpace != -1 ? typedValue.substring(lastSpace + 1) : typedValue;

                // class="..."
                if ("class".equals(attrName)) {
                    ensureHtmlSelectorsResolved(fullText);
                    if (!cachedHtmlClassItems.isEmpty()) {
                        Set<String> alreadyChosen = new HashSet<>();
                        String[] tokens = typedValue.split("\\s+");
                        for (String t : tokens) {
                            if (!t.isEmpty()) alreadyChosen.add(t.toLowerCase());
                        }
                        alreadyChosen.remove(attrWord.toLowerCase());

                        List<CompletionItem> candidates = new ArrayList<>();
                        for (CompletionItem ci : cachedHtmlClassItems) {
                            if (!alreadyChosen.contains(ci.getLabel().toLowerCase())) {
                                candidates.add(ci);
                            }
                        }

                        List<CompletionItem> res = fuzzyFilter(candidates, attrWord);
                        for (CompletionItem ci : res) {
                            ci.setReplaceLength(attrWord.length());
                        }
                        return res;
                    }
                    return Collections.emptyList();
                }

                // id="..."
                if ("id".equals(attrName)) {
                    ensureHtmlSelectorsResolved(fullText);
                    if (!cachedHtmlIdItems.isEmpty()) {
                        List<CompletionItem> res = fuzzyFilter(cachedHtmlIdItems, attrWord);
                        for (CompletionItem ci : res) {
                            ci.setReplaceLength(attrWord.length());
                        }
                        return res;
                    }
                    return Collections.emptyList();
                }

                // Known attribute values
                String[] values = HtmlDefinitions.getAttributeValues(ctx.currentTagName, attrName);
                if (values != null) {
                    boolean isMultiValue = "rel".equals(attrName) || "sandbox".equals(attrName)
                            || "autocomplete".equals(attrName) || "part".equals(attrName)
                            || "aria-haspopup".equals(attrName) || "role".equals(attrName);

                    String filterWord = typedValue;
                    Set<String> alreadyChosen = new HashSet<>();
                    if (isMultiValue) {
                        String[] tokens = typedValue.split("\\s+");
                        for (String t : tokens) {
                            if (!t.isEmpty()) alreadyChosen.add(t.toLowerCase());
                        }
                        if (lastSpace != -1) {
                            filterWord = typedValue.substring(lastSpace + 1);
                        }
                        alreadyChosen.remove(filterWord.toLowerCase());
                    }

                    List<CompletionItem> valItems = new ArrayList<>();
                    for (String v : values) {
                        if (isMultiValue && alreadyChosen.contains(v.toLowerCase())) continue;
                        String insertText = v;
                        if (!ctx.isQuotedAttributeValue && lastSpace == -1) {
                            insertText = "\"" + v + "\"";
                        }
                        CompletionItem ci = new CompletionItem(v, insertText, attrName + " value",
                                CompletionItem.Type.VALUE, 0);
                        ci.setReplaceLength(filterWord.length());
                        valItems.add(ci);
                    }
                    return fuzzyFilter(valItems, filterWord);
                }

                // In attribute values, tag names and attributes are forbidden
                return Collections.emptyList();
            }

            // 8b. Opening Tag Name position (typing directly after '<')
            if (ctx.isTypingTagName) {
                String tagPrefix = word != null ? word : "";
                List<CompletionItem> tagMatches = com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader.getHtmlTagIndex().filter(tagPrefix, MAX_SUGGESTIONS);
                if (tagMatches.isEmpty()) {
                    tagMatches = fuzzyFilter(tagItems, tagPrefix);
                }
                boolean hasLtBefore = lineBefore.lastIndexOf('<') >= 0 &&
                        lineBefore.lastIndexOf('<') == lineBefore.length() - tagPrefix.length() - 1;
                List<CompletionItem> out = new ArrayList<>(tagMatches.size());
                for (CompletionItem ci : tagMatches) {
                    CompletionItem copy = new CompletionItem(ci);
                    int rLen = (hasLtBefore && copy.getInsertText().startsWith("<")) ? (tagPrefix.length() + 1) : tagPrefix.length();
                    copy.setReplaceLength(rLen);
                    out.add(copy);
                }
                return out;
            }

            // 8c. Attribute Name position (inside open tag, past tag name, before '=')
            com.cocode.vcode.ide.core.completion.staticdata.HtmlStaticCompletionDispatcher.Position pos =
                    com.cocode.vcode.ide.core.completion.staticdata.HtmlStaticCompletionDispatcher.detectPosition(fullText, cursorPos);
            List<CompletionItem> attrItems = com.cocode.vcode.ide.core.completion.staticdata.HtmlStaticCompletionDispatcher.buildCompletions(
                    pos == com.cocode.vcode.ide.core.completion.staticdata.HtmlStaticCompletionDispatcher.Position.ATTRIBUTE_NAME ? pos :
                            com.cocode.vcode.ide.core.completion.staticdata.HtmlStaticCompletionDispatcher.Position.ATTRIBUTE_NAME,
                    fullText, cursorPos);
            String attrPrefix = word != null ? word : "";
            List<CompletionItem> filteredAttrs = fuzzyFilter(attrItems, attrPrefix);
            List<CompletionItem> outAttrs = new ArrayList<>(filteredAttrs.size());
            for (CompletionItem ci : filteredAttrs) {
                CompletionItem copy = new CompletionItem(ci);
                copy.setReplaceLength(attrPrefix.length());
                outAttrs.add(copy);
            }
            return outAttrs;
        }

        // 9. Text Content position (between tags, on blank lines, or outside tags)
        // Check Emmet first
        boolean inEmmetBraces = isInsideEmmetBraces(lineBefore);
        boolean inEmmetBrackets = isInsideEmmetBrackets(lineBefore);

        String emmetAbbr = getEmmetAbbreviationBeforeCursor(fullText, cursorPos);
        if (!inEmmetBraces && !inEmmetBrackets && emmetAbbr != null && !emmetAbbr.isEmpty() && !emmetAbbr.contains("<")) {
            String expanded = EmmetParser.expandHtml(emmetAbbr, htmlBoilerplate);
            if (expanded != null) {
                boolean isComplex = emmetAbbr.contains(".") || emmetAbbr.contains("#")
                        || emmetAbbr.contains(">") || emmetAbbr.contains("*")
                        || emmetAbbr.contains("+") || emmetAbbr.contains("^")
                        || emmetAbbr.contains("(") || emmetAbbr.contains("{")
                        || emmetAbbr.contains("[") || emmetAbbr.contains("]")
                        || emmetAbbr.contains(":")
                        || emmetAbbr.equals("!")
                        || emmetAbbr.startsWith("lorem")
                        || EmmetParser.isHtmlAlias(emmetAbbr);

                if (isComplex) {
                    CompletionItem emmetItem = new CompletionItem(emmetAbbr, expanded,
                            "Emmet Abbreviation", CompletionItem.Type.SNIPPET, 0);
                    emmetItem.setReplaceLength(emmetAbbr.length());

                    if (cursorPos < fullText.length()) {
                        char nextChar = fullText.charAt(cursorPos);
                        if (nextChar == '}' && emmetAbbr.contains("{")) {
                            emmetItem.setReplaceAfterLength(1);
                        } else if (nextChar == ']' && emmetAbbr.contains("[")) {
                            emmetItem.setReplaceAfterLength(1);
                        }
                    }

                    List<CompletionItem> res = new ArrayList<>();
                    res.add(emmetItem);
                    return res;
                }
            }
        }

        // Candidate Emmet inside braces or brackets
        if (inEmmetBraces) {
            int braceIdx = lineBefore.lastIndexOf('{');
            if (braceIdx >= 0) {
                int prefixEnd = Math.max(0, cursorPos - (lineBefore.length() - braceIdx));
                String prefix = (prefixEnd > 0) ? getEmmetAbbreviationBeforeCursor(fullText, prefixEnd) : "";
                String inside = lineBefore.substring(braceIdx + 1);
                String candidateAbbr = prefix + "{" + inside + "}";
                if (!candidateAbbr.contains("<")) {
                    String expanded = EmmetParser.expandHtml(candidateAbbr, htmlBoilerplate);
                    if (expanded != null) {
                        CompletionItem emmetItem = new CompletionItem(candidateAbbr, expanded,
                                "Emmet Abbreviation", CompletionItem.Type.SNIPPET, 0);
                        emmetItem.setReplaceLength(prefix.length() + 1 + inside.length());
                        int replaceAfter = (cursorPos < fullText.length() && fullText.charAt(cursorPos) == '}') ? 1 : 0;
                        emmetItem.setReplaceAfterLength(replaceAfter);

                        List<CompletionItem> res = new ArrayList<>();
                        res.add(emmetItem);
                        return res;
                    }
                }
            }
            return Collections.emptyList();
        } else if (inEmmetBrackets) {
            int bracketIdx = lineBefore.lastIndexOf('[');
            if (bracketIdx >= 0 && !lineBefore.substring(bracketIdx).contains("]")) {
                int prefixEnd = Math.max(0, cursorPos - (lineBefore.length() - bracketIdx));
                String prefix = (prefixEnd > 0) ? getEmmetAbbreviationBeforeCursor(fullText, prefixEnd) : "";
                String inside = lineBefore.substring(bracketIdx + 1);
                String candidateAbbr = prefix + "[" + inside + "]";
                if (!candidateAbbr.contains("<")) {
                    String expanded = EmmetParser.expandHtml(candidateAbbr, htmlBoilerplate);
                    if (expanded != null) {
                        CompletionItem emmetItem = new CompletionItem(candidateAbbr, expanded,
                                "Emmet Abbreviation", CompletionItem.Type.SNIPPET, 0);
                        emmetItem.setReplaceLength(prefix.length() + 1 + inside.length());
                        int replaceAfter = (cursorPos < fullText.length() && fullText.charAt(cursorPos) == ']') ? 1 : 0;
                        emmetItem.setReplaceAfterLength(replaceAfter);

                        List<CompletionItem> res = new ArrayList<>();
                        res.add(emmetItem);
                        return res;
                    }
                }
            }
            return Collections.emptyList();
        }

        // Bare HTML tag suggestions in Text Content (e.g. typing "div" or "p" on blank line or after indent)
        if (word != null && !word.isEmpty()) {
            List<CompletionItem> tagMatches = com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader.getHtmlTagIndex().filter(word, MAX_SUGGESTIONS);
            if (tagMatches.isEmpty()) {
                tagMatches = fuzzyFilter(tagItems, word);
            }
            List<CompletionItem> outTags = new ArrayList<>(tagMatches.size());
            for (CompletionItem ci : tagMatches) {
                CompletionItem copy = new CompletionItem(ci);
                copy.setReplaceLength(word.length());
                outTags.add(copy);
            }
            return outTags;
        }

        return Collections.emptyList();
    }

    private static boolean isDirectlyAfterCloseTag(String lineBefore, int lastCloseTagIdx) {
        if (lastCloseTagIdx < 0 || lastCloseTagIdx + 2 > lineBefore.length()) return false;
        String after = lineBefore.substring(lastCloseTagIdx + 2);
        for (int i = 0; i < after.length(); i++) {
            char c = after.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '_') {
                return false;
            }
        }
        return true;
    }

    private String getPathQuery(String typedValue, String attrName) {
        String pathQuery = typedValue;
        if (attrName.equals("srcset")) {
            // In srcset, URLs can be separated by commas and spaces. We only want the last token.
            int lastComma = pathQuery.lastIndexOf(',');
            if (lastComma != -1) {
                pathQuery = pathQuery.substring(lastComma + 1).trim();
            }
            int lastSpace = pathQuery.lastIndexOf(' ');
            if (lastSpace != -1) {
                pathQuery = pathQuery.substring(lastSpace + 1);
            }
        }
        return pathQuery;
    }

    // Emmet brace and bracket detection

    /**
     * Returns true if the cursor is inside unmatched curly braces on the current line.
     * This indicates the user is typing Emmet text content like {@code a{Click me|}}
     * and we should NOT show HTML tag suggestions for the words inside.
     */
    private boolean isInsideEmmetBraces(String lineBefore) {
        int depth = 0;
        boolean inQuote = false;
        char quoteChar = 0;
        for (int i = 0; i < lineBefore.length(); i++) {
            char c = lineBefore.charAt(i);
            if (inQuote) {
                if (c == quoteChar && (i == 0 || lineBefore.charAt(i - 1) != '\\')) {
                    inQuote = false;
                }
            } else {
                if (c == '"' || c == '\'') {
                    inQuote = true;
                    quoteChar = c;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
            }
        }
        return depth > 0;
    }

    /**
     * Returns true if the cursor is inside unmatched square brackets on the current line.
     * This indicates the user is typing Emmet attribute content like {@code a[href="#|"]}
     * and we should not show HTML tag suggestions or match inner words as Emmet expressions.
     */
    private boolean isInsideEmmetBrackets(String lineBefore) {
        int depth = 0;
        boolean inQuote = false;
        char quoteChar = 0;
        for (int i = 0; i < lineBefore.length(); i++) {
            char c = lineBefore.charAt(i);
            if (inQuote) {
                if (c == quoteChar && (i == 0 || lineBefore.charAt(i - 1) != '\\')) {
                    inQuote = false;
                }
            } else {
                if (c == '"' || c == '\'') {
                    inQuote = true;
                    quoteChar = c;
                } else if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                }
            }
        }
        return depth > 0;
    }

    // Embedded block content extraction

    /**
     * Finds the content start position of the last unclosed &lt;style&gt; or &lt;script&gt; block
     * before the cursor. Returns the position right after the closing '>' of the opening tag.
     *
     * @param tag "style" or "script"
     * @return index of first char of content, or -1 if not found
     */
    private int findBlockContentStart(String text, int cursorPos, String tag) {
        String searchText = text.substring(0, Math.min(cursorPos, text.length()));
        // Find last opening <style...> or <script...> tag
        String openPattern = "<" + tag;
        int lastOpen = -1;
        int pos = 0;
        while (true) {
            int idx = searchText.indexOf(openPattern, pos);
            if (idx < 0) break;
            // Verify it's a proper tag (not e.g. <styled>)
            int afterTag = idx + openPattern.length();
            if (afterTag < searchText.length()) {
                char next = searchText.charAt(afterTag);
                if (next == '>' || next == ' ' || next == '\n' || next == '\r' || next == '\t') {
                    // Find the closing > of this opening tag
                    int closeAngle = searchText.indexOf('>', afterTag);
                    if (closeAngle >= 0) {
                        // Make sure there isn't a </style> or </script> between this open and cursor
                        String closeTag = "</" + tag;
                        int closeIdx = searchText.indexOf(closeTag, closeAngle);
                        if (closeIdx < 0) {
                            // No closing tag found before cursor — this is the active block
                            lastOpen = closeAngle + 1;
                        }
                    }
                }
            }
            pos = idx + 1;
        }
        return lastOpen;
    }

    // File / folder path suggestions

    /**
     * Provides file and folder path completions for path-bearing attributes
     * (src, href, action…). Shows directory contents when a slash is
     * present; otherwise searches from project root.
     */
    private List<CompletionItem> getFileSuggestions(String typedPath, String tagName, String attrName) {
        if (pathEngine == null) return Collections.emptyList();
        pathEngine.setCurrentFile(currentFile);
        return pathEngine.getSuggestionsForPath(typedPath, new PathAutoCompleteEngine.PathFilter() {
            @Override
            public boolean isAllowed(File f, boolean isDirectory) {
                return isFileAllowed(f, tagName, attrName);
            }

            @Override
            public int getPriority(File f, boolean isDirectory) {
                return isDirectory ? 10 : 5;
            }
        });
    }



    private boolean isFileAllowed(File f, String tagName, String attrName) {
        if (f.isDirectory()) return true; // Always allow traversing directories
        String name = f.getName().toLowerCase();

        if ("img".equals(tagName) || "poster".equals(attrName) || "srcset".equals(attrName)) {
            return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                    || name.endsWith(".gif") || name.endsWith(".svg") || name.endsWith(".webp") || name.endsWith(".ico");
        }
        if ("script".equals(tagName)) {
            return name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".jsx")
                    || name.endsWith(".tsx") || name.endsWith(".mjs") || name.endsWith(".cjs") || name.endsWith(".vue");
        }
        if ("link".equals(tagName) && "href".equals(attrName)) {
            return name.endsWith(".css") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".ico")
                    || name.endsWith(".svg") || name.endsWith(".json") || name.endsWith(".webmanifest")
                    || name.endsWith(".woff") || name.endsWith(".woff2") || name.endsWith(".ttf")
                    || name.endsWith(".otf") || name.endsWith(".eot") || name.endsWith(".xml");
        }
        if ("audio".equals(tagName)) {
            return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg");
        }
        if ("video".equals(tagName) && "src".equals(attrName)) {
            return name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".ogg");
        }
        if ("source".equals(tagName)) {
            return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                    || name.endsWith(".gif") || name.endsWith(".svg") || name.endsWith(".webp")
                    || name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg")
                    || name.endsWith(".mp4") || name.endsWith(".webm");
        }
        if ("html".equals(tagName) && "manifest".equals(attrName)) {
            return name.endsWith(".json") || name.endsWith(".webmanifest");
        }
        if ("form".equals(tagName) || "action".equals(attrName) || "formaction".equals(attrName)) {
            return name.endsWith(".php") || name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".js");
        }
        // For other generic tags (like <a>, <iframe>), allow everything
        return true;
    }

    // CSS and JS symbol completions for HTML
    private final List<CompletionItem> cachedHtmlClassItems = new ArrayList<>();
    private final List<CompletionItem> cachedHtmlIdItems = new ArrayList<>();
    private int lastHtmlSelectorsHash = 0;

    private final List<CompletionItem> cachedHtmlJsSymbols = new ArrayList<>();
    private int lastHtmlJsHash = 0;

    private void ensureHtmlSelectorsResolved(String fullText) {
        if (fullText == null) return;
        int docHash = fullText.hashCode();
        if (docHash == lastHtmlSelectorsHash) {
            return;
        }
        lastHtmlSelectorsHash = docHash;
        cachedHtmlClassItems.clear();
        cachedHtmlIdItems.clear();

        Map<String, String> classOrigins = new LinkedHashMap<>();
        Map<String, String> idOrigins = new LinkedHashMap<>();
        Set<String> visitedFiles = new HashSet<>();

        // 1. Linked CSS files (<link rel="stylesheet" href="...">)
        List<File> linkedFiles = extractLinkedCssFiles(fullText, currentFile);
        for (File linkedFile : linkedFiles) {
            String fileName = linkedFile.getName();
            Set<String> fClasses = new LinkedHashSet<>();
            Set<String> fIds = new LinkedHashSet<>();
            ProjectSymbolIndex.getInstance().collectCssSelectorsRecursively(linkedFile, fClasses, fIds, visitedFiles);
            for (String c : fClasses) {
                if (!classOrigins.containsKey(c)) {
                    classOrigins.put(c, fileName);
                }
            }
            for (String id : fIds) {
                if (!idOrigins.containsKey(id)) {
                    idOrigins.put(id, fileName);
                }
            }
        }

        // 2. Embedded <style> tags in fullText
        Set<String> styleClasses = new LinkedHashSet<>();
        Set<String> styleIds = new LinkedHashSet<>();
        extractEmbeddedStyleSelectors(fullText, styleClasses, styleIds, currentFile, visitedFiles);

        for (String c : styleClasses) {
            String prev = classOrigins.get(c);
            if (prev != null) {
                classOrigins.put(c, "<style>, " + prev);
            } else {
                classOrigins.put(c, "<style>");
            }
        }
        for (String id : styleIds) {
            String prev = idOrigins.get(id);
            if (prev != null) {
                idOrigins.put(id, "<style>, " + prev);
            } else {
                idOrigins.put(id, "<style>");
            }
        }

        // 3. HTML element id="..." attributes declared in fullText
        extractHtmlDocumentIds(fullText, idOrigins);

        for (Map.Entry<String, String> entry : classOrigins.entrySet()) {
            cachedHtmlClassItems.add(new CompletionItem(entry.getKey(), entry.getKey(), entry.getValue(), CompletionItem.Type.CSS_VALUE, 0));
        }
        for (Map.Entry<String, String> entry : idOrigins.entrySet()) {
            cachedHtmlIdItems.add(new CompletionItem(entry.getKey(), entry.getKey(), entry.getValue(), CompletionItem.Type.CSS_VALUE, 0));
        }
    }

    public static void extractHtmlDocumentIds(String html, Map<String, String> idOrigins) {
        if (html == null || !html.contains("id")) return;
        int len = html.length();
        int p = 0;
        while (p < len) {
            if (html.startsWith("<!--", p)) {
                int end = html.indexOf("-->", p + 4);
                p = (end == -1) ? len : end + 3;
                continue;
            }
            if (html.charAt(p) == '<') {
                int tagEnd = html.indexOf('>', p + 1);
                if (tagEnd == -1) break;
                String tag = html.substring(p, tagEnd);
                String idVal = extractAttributeValue(tag, "id");
                if (idVal != null && !idVal.trim().isEmpty() && ProjectSymbolIndex.isValidHtmlId(idVal.trim())) {
                    String cleanId = idVal.trim();
                    String prev = idOrigins.get(cleanId);
                    if (prev != null) {
                        if (!prev.contains("HTML")) {
                            idOrigins.put(cleanId, prev + ", HTML");
                        }
                    } else {
                        idOrigins.put(cleanId, "HTML");
                    }
                }
                p = tagEnd + 1;
                continue;
            }
            p++;
        }
    }

    public static List<File> extractLinkedCssFiles(String html, File currentHtmlFile) {
        if (html == null || html.isEmpty()) return Collections.emptyList();
        List<File> files = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        File parentDir = currentHtmlFile != null ? currentHtmlFile.getParentFile() : null;

        int len = html.length();
        int p = 0;
        while (p < len) {
            // Skip HTML comments
            if (html.startsWith("<!--", p)) {
                int end = html.indexOf("-->", p + 4);
                p = (end == -1) ? len : end + 3;
                continue;
            }

            // Check for <link
            if (p + 5 < len && html.charAt(p) == '<' &&
                    (html.charAt(p + 1) == 'l' || html.charAt(p + 1) == 'L') &&
                    (html.charAt(p + 2) == 'i' || html.charAt(p + 2) == 'I') &&
                    (html.charAt(p + 3) == 'n' || html.charAt(p + 3) == 'N') &&
                    (html.charAt(p + 4) == 'k' || html.charAt(p + 4) == 'K') &&
                    (Character.isWhitespace(html.charAt(p + 5)) || html.charAt(p + 5) == '/' || html.charAt(p + 5) == '>')) {

                int tagEnd = html.indexOf('>', p + 5);
                if (tagEnd == -1) tagEnd = len;
                String tagContent = html.substring(p, tagEnd);

                String rel = extractAttributeValue(tagContent, "rel");
                String as = extractAttributeValue(tagContent, "as");
                String type = extractAttributeValue(tagContent, "type");
                boolean isStylesheet = (rel != null && rel.toLowerCase().contains("stylesheet"))
                        || ("style".equalsIgnoreCase(as))
                        || ("text/css".equalsIgnoreCase(type));

                if (isStylesheet) {
                    String href = extractAttributeValue(tagContent, "href");
                    if (href != null && !href.trim().isEmpty()) {
                        int q = href.indexOf('?');
                        if (q != -1) href = href.substring(0, q);
                        int h = href.indexOf('#');
                        if (h != -1) href = href.substring(0, h);
                        href = href.trim();

                        if (!href.startsWith("http://") && !href.startsWith("https://")
                                && !href.startsWith("//") && !href.startsWith("data:")
                                && !href.startsWith("javascript:") && !href.startsWith("#")) {

                            File resolved = null;
                            if (href.startsWith("/")) {
                                File root = currentHtmlFile != null ? ProjectSymbolIndex.getProjectRoot(currentHtmlFile) : null;
                                resolved = new File(root != null ? root : parentDir, href.substring(1));
                            } else if (parentDir != null) {
                                resolved = new File(parentDir, href);
                            }

                            if (resolved != null) {
                                try {
                                    resolved = resolved.getCanonicalFile();
                                } catch (Exception e) {
                                    resolved = resolved.getAbsoluteFile();
                                }
                                String canonical = resolved.getAbsolutePath();
                                if (seenPaths.add(canonical)) {
                                    files.add(resolved);
                                }
                            }
                        }
                    }
                }
                p = (tagEnd < len) ? tagEnd + 1 : len;
                continue;
            }
            p++;
        }
        return files;
    }

    private void extractEmbeddedStyleSelectors(String html, Set<String> classNames, Set<String> cssIds, File currentHtmlFile, Set<String> visited) {
        if (html == null || html.isEmpty()) return;
        int len = html.length();
        int p = 0;
        while (p < len) {
            if (html.startsWith("<!--", p)) {
                int end = html.indexOf("-->", p + 4);
                p = (end == -1) ? len : end + 3;
                continue;
            }

            if (p + 6 <= len && html.charAt(p) == '<' &&
                    (html.charAt(p + 1) == 's' || html.charAt(p + 1) == 'S') &&
                    (html.charAt(p + 2) == 't' || html.charAt(p + 2) == 'T') &&
                    (html.charAt(p + 3) == 'y' || html.charAt(p + 3) == 'Y') &&
                    (html.charAt(p + 4) == 'l' || html.charAt(p + 4) == 'L') &&
                    (html.charAt(p + 5) == 'e' || html.charAt(p + 5) == 'E') &&
                    (p + 6 == len || Character.isWhitespace(html.charAt(p + 6)) || html.charAt(p + 6) == '>')) {

                int openTagEnd = html.indexOf('>', p + 6);
                if (openTagEnd == -1) break;

                int contentStart = openTagEnd + 1;
                int closeTagStart = indexOfIgnoreCase(html, "</style", contentStart);
                int contentEnd = (closeTagStart != -1) ? closeTagStart : len;

                if (contentEnd > contentStart) {
                    String cssSnippet = html.substring(contentStart, contentEnd);
                    ProjectSymbolIndex.extractSelectorsFromCssText(cssSnippet, classNames, cssIds);

                    // Check for @import inside <style>
                    if (currentHtmlFile != null && cssSnippet.contains("@import")) {
                        List<File> imported = ProjectSymbolIndex.extractCssImports(cssSnippet, currentHtmlFile);
                        for (File imp : imported) {
                            ProjectSymbolIndex.getInstance().collectCssSelectorsRecursively(imp, classNames, cssIds, visited);
                        }
                    }
                }

                if (closeTagStart != -1) {
                    int closeTagEnd = html.indexOf('>', closeTagStart);
                    p = (closeTagEnd != -1) ? closeTagEnd + 1 : len;
                } else {
                    p = len;
                }
                continue;
            }
            p++;
        }
    }

    public static String extractAttributeValue(String tagContent, String attrName) {
        if (tagContent == null || attrName == null) return null;
        int len = tagContent.length();
        int nameLen = attrName.length();
        int p = 0;
        while (p < len) {
            int idx = indexOfIgnoreCase(tagContent, attrName, p);
            if (idx == -1) break;
            if (idx > 0) {
                char before = tagContent.charAt(idx - 1);
                if (!Character.isWhitespace(before) && before != '<') {
                    p = idx + nameLen;
                    continue;
                }
            }
            int after = idx + nameLen;
            while (after < len && Character.isWhitespace(tagContent.charAt(after))) after++;
            if (after < len && tagContent.charAt(after) == '=') {
                after++;
                while (after < len && Character.isWhitespace(tagContent.charAt(after))) after++;
                if (after < len) {
                    char quote = tagContent.charAt(after);
                    if (quote == '"' || quote == '\'') {
                        int valEnd = tagContent.indexOf(quote, after + 1);
                        if (valEnd != -1) {
                            return tagContent.substring(after + 1, valEnd);
                        } else {
                            return tagContent.substring(after + 1);
                        }
                    } else {
                        int valEnd = after;
                        while (valEnd < len && !Character.isWhitespace(tagContent.charAt(valEnd))
                                && tagContent.charAt(valEnd) != '>' && tagContent.charAt(valEnd) != '/') {
                            valEnd++;
                        }
                        return tagContent.substring(after, valEnd);
                    }
                }
            }
            p = idx + nameLen;
        }
        return null;
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        if (haystack == null || needle == null) return -1;
        int hLen = haystack.length();
        int nLen = needle.length();
        if (from >= hLen || nLen == 0) return -1;
        for (int i = Math.max(0, from); i <= hLen - nLen; i++) {
            boolean match = true;
            for (int j = 0; j < nLen; j++) {
                if (Character.toLowerCase(haystack.charAt(i + j)) != Character.toLowerCase(needle.charAt(j))) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    private void ensureHtmlJsSymbolsResolved(String fullText) {
        if (fullText == null) return;
        int docHash = fullText.hashCode();
        if (docHash == lastHtmlJsHash) {
            return;
        }
        lastHtmlJsHash = docHash;
        cachedHtmlJsSymbols.clear();

        Map<String, CompletionItem> combinedSymbols = new LinkedHashMap<>();
        Set<String> visitedFiles = new HashSet<>();

        // 1. Linked JS files (<script src="...">)
        List<File> linkedFiles = extractLinkedJsFiles(fullText, currentFile);
        for (File linkedFile : linkedFiles) {
            ProjectSymbolIndex.getInstance().collectJsSymbolsRecursively(linkedFile, combinedSymbols, visitedFiles);
        }

        // 2. Embedded <script> tags in fullText
        extractEmbeddedScriptSymbols(fullText, combinedSymbols, currentFile);

        cachedHtmlJsSymbols.addAll(combinedSymbols.values());
        jsEngine.setScopedHtmlSymbols(cachedHtmlJsSymbols);
        jsEngine.setScopedHtmlIdsAndClasses(cachedHtmlIdItems, cachedHtmlClassItems);
    }

    public static List<File> extractLinkedJsFiles(String html, File currentHtmlFile) {
        if (html == null || html.isEmpty()) return Collections.emptyList();
        List<File> files = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        File parentDir = currentHtmlFile != null ? currentHtmlFile.getParentFile() : null;

        int len = html.length();
        int p = 0;
        while (p < len) {
            // Skip HTML comments
            if (html.startsWith("<!--", p)) {
                int end = html.indexOf("-->", p + 4);
                p = (end == -1) ? len : end + 3;
                continue;
            }

            // Check for <script
            if (p + 7 < len && html.charAt(p) == '<' &&
                    (html.charAt(p + 1) == 's' || html.charAt(p + 1) == 'S') &&
                    (html.charAt(p + 2) == 'c' || html.charAt(p + 2) == 'C') &&
                    (html.charAt(p + 3) == 'r' || html.charAt(p + 3) == 'R') &&
                    (html.charAt(p + 4) == 'i' || html.charAt(p + 4) == 'I') &&
                    (html.charAt(p + 5) == 'p' || html.charAt(p + 5) == 'P') &&
                    (html.charAt(p + 6) == 't' || html.charAt(p + 6) == 'T') &&
                    (Character.isWhitespace(html.charAt(p + 7)) || html.charAt(p + 7) == '/' || html.charAt(p + 7) == '>')) {

                int tagEnd = html.indexOf('>', p + 7);
                if (tagEnd == -1) tagEnd = len;
                String tagContent = html.substring(p, tagEnd);

                String src = extractAttributeValue(tagContent, "src");
                if (src != null && !src.trim().isEmpty()) {
                    String type = extractAttributeValue(tagContent, "type");
                    boolean isJs = isJavaScriptType(type);
                    if (isJs) {
                        int q = src.indexOf('?');
                        if (q != -1) src = src.substring(0, q);
                        int h = src.indexOf('#');
                        if (h != -1) src = src.substring(0, h);
                        src = src.trim();

                        if (!src.startsWith("http://") && !src.startsWith("https://")
                                && !src.startsWith("//") && !src.startsWith("data:")
                                && !src.startsWith("javascript:") && !src.startsWith("#")) {

                            File resolved = null;
                            if (src.startsWith("/")) {
                                File root = currentHtmlFile != null ? ProjectSymbolIndex.getProjectRoot(currentHtmlFile) : null;
                                resolved = new File(root != null ? root : parentDir, src.substring(1));
                            } else if (parentDir != null) {
                                resolved = new File(parentDir, src);
                            }

                            if (resolved != null) {
                                boolean exists = resolved.exists();
                                if (!exists) {
                                    exists = ProjectSymbolIndex.hasDocument(resolved);
                                }
                                if (exists) {
                                    try {
                                        resolved = resolved.getCanonicalFile();
                                    } catch (Exception e) {
                                        resolved = resolved.getAbsoluteFile();
                                    }
                                    String canonical = resolved.getAbsolutePath();
                                    if (seenPaths.add(canonical)) {
                                        files.add(resolved);
                                    }
                                }
                            }
                        }
                    }
                }
                p = (tagEnd < len) ? tagEnd + 1 : len;
                continue;
            }
            p++;
        }
        return files;
    }

    public static boolean isJavaScriptType(String type) {
        if (type == null || type.trim().isEmpty()) return true;
        String t = type.trim().toLowerCase();
        return t.equals("text/javascript") || t.equals("application/javascript")
                || t.equals("module") || t.equals("text/ecmascript")
                || t.equals("application/ecmascript") || t.equals("javascript");
    }

    private void extractEmbeddedScriptSymbols(String html, Map<String, CompletionItem> targetSymbols, File currentHtmlFile) {
        if (html == null || !html.contains("<script")) return;
        int len = html.length();
        int p = 0;
        while (p < len) {
            if (html.startsWith("<!--", p)) {
                int end = html.indexOf("-->", p + 4);
                p = (end == -1) ? len : end + 3;
                continue;
            }

            if (p + 7 < len && html.charAt(p) == '<' &&
                    (html.charAt(p + 1) == 's' || html.charAt(p + 1) == 'S') &&
                    (html.charAt(p + 2) == 'c' || html.charAt(p + 2) == 'C') &&
                    (html.charAt(p + 3) == 'r' || html.charAt(p + 3) == 'R') &&
                    (html.charAt(p + 4) == 'i' || html.charAt(p + 4) == 'I') &&
                    (html.charAt(p + 5) == 'p' || html.charAt(p + 5) == 'P') &&
                    (html.charAt(p + 6) == 't' || html.charAt(p + 6) == 'T') &&
                    (Character.isWhitespace(html.charAt(p + 7)) || html.charAt(p + 7) == '/' || html.charAt(p + 7) == '>')) {

                int tagEnd = html.indexOf('>', p + 7);
                if (tagEnd == -1) break;
                String tagContent = html.substring(p, tagEnd);
                String src = extractAttributeValue(tagContent, "src");
                if (src == null || src.trim().isEmpty()) {
                    String type = extractAttributeValue(tagContent, "type");
                    if (isJavaScriptType(type)) {
                        int contentStart = tagEnd + 1;
                        int closeIdx = indexOfIgnoreCase(html, "</script", contentStart);
                        if (closeIdx > contentStart) {
                            String js = html.substring(contentStart, closeIdx);
                            ProjectSymbolIndex.extractSymbolsFromJsText(js, targetSymbols, "<script>");
                        }
                    }
                }
                p = tagEnd + 1;
                continue;
            }
            p++;
        }
    }
}