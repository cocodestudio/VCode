package com.cocode.vcode.ide.core.language.html;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.EmmetParser;
import com.cocode.vcode.ide.core.autocomplete.FastTrie;
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
import java.util.List;
import java.util.Map;

/**
 * Intelligent completion coordinator for HTML source code — mirrors VS Code's HTML language server.
 *
 * <p>Feature highlights:
 * <ul>
 *   <li>VS Code-style fuzzy scoring via {@link AutoCompleteEngine#fuzzyFilter}</li>
 *   <li>Tag completions with self-closing awareness (void elements get {@code />})</li>
 *   <li>Attribute completions per tag (loaded from JSON) + global HTML attributes</li>
 *   <li>Attribute-value enumerations (e.g., {@code type="…"} → text, email, checkbox…)</li>
 *   <li>Inline {@code style="…"} delegates to {@link CssAutoCompleteEngine}</li>
 *   <li>Inline {@code on*="…"} event-handler attributes delegate to {@link JsAutoCompleteEngine}</li>
 *   <li>Embedded {@code <style>} / {@code <script>} block delegation</li>
 *   <li>Smart file-path completions for {@code src}, {@code href}, {@code action}, {@code data} attributes</li>
 *   <li>Full Emmet expansion (unchanged — keeps pipe-based cursor positioning)</li>
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
    private File currentFile;
    private String htmlBoilerplate;

    public HtmlAutoCompleteEngine(Context context) {
        super(context);
        loadTags();
        this.cssEngine = new CssAutoCompleteEngine(context);
        this.jsEngine = new JsAutoCompleteEngine(context);
    }

    public void setCurrentFile(File file) {
        this.currentFile = file;
        jsEngine.setCurrentFile(file);
        File projectRoot = getProjectRoot(file);
        if (projectRoot != null) {
            ProjectSymbolIndex.getInstance().buildIndex(projectRoot);
        }
    }

    // Tag loading
    /**
     * Initialises HTML tag completions and per-tag attribute lists from the JSON asset.
     */
    private void loadTags() {
        try {
            String json = loadAssetJson("completions/html_tags.json");
            // The asset is a top-level object with a "tags" array plus
            // globalAttributes / globalAttributePrefixes keys. Older
            // versions were a bare array; accept both for back-compat.
            org.json.JSONArray arr;
            if (json.trim().startsWith("[")) {
                arr = new org.json.JSONArray(json);
            } else {
                org.json.JSONObject root = new org.json.JSONObject(json);
                arr = root.getJSONArray("tags");
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String tag = obj.optString("tag");
                String snippet = obj.optString("snippet", "<" + tag + ">|</" + tag + ">");
                String detail = obj.optString("detail", "");

                CompletionItem item = new CompletionItem(tag, snippet, detail,
                        CompletionItem.Type.TAG, 0);
                tagItems.add(item);
                TAG_TRIE.insert(item);

                // Build per-tag attribute list (tag-specific + global HTML attributes)
                HtmlDefinitions.ensureLoaded();
                JSONArray attrs = obj.optJSONArray("attributes");
                List<CompletionItem> attrList = new ArrayList<>(HtmlDefinitions.getGlobalAttrs());
                if (attrs != null) {
                    for (int j = 0; j < attrs.length(); j++) {
                        String attr = attrs.optString(j);
                        String insert = com.cocode.vcode.ide.core.completion.staticdata.HtmlStaticCompletionDispatcher.isHtmlBooleanAttribute(attr)
                                ? attr : attr + "=\"|\"";
                        attrList.add(0, new CompletionItem(attr, insert,
                                detail.isEmpty() ? tag : detail, CompletionItem.Type.ATTRIBUTE, 0));
                    }
                }
                attrMap.put(tag, attrList);
            }
        } catch (Exception e) {
            // Completion data not critical — proceed with empty list
        }

        // Load the ! boilerplate (Emmet)
        try {
            String template = loadAssetText("templates/template_blank.html");
            if (template != null && !template.trim().isEmpty()) {
                htmlBoilerplate = template.replace("<body>\n\n", "<body>\n    |\n")
                        .replace("<body>\r\n\r\n", "<body>\r\n    |\r\n");
                if (!htmlBoilerplate.contains("|")) {
                    htmlBoilerplate = htmlBoilerplate.replace("<body>", "<body>\n    |");
                }
            }
        } catch (Exception e) {
            // Non-critical
        }
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

        // 0. Comment gating
        if (isInsideComment(fullText, cursorPos)) {
            return new ArrayList<>();
        }

        // 1. DOCTYPE completions (when typing "<!" or "<!D" or "<!--" etc.)
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

        // 2. Entity completions (when typing "&" followed by letters)
        if (!lineBefore.isEmpty()) {
            int ampIdx = lineBefore.lastIndexOf('&');
            if (ampIdx >= 0) {
                String afterAmp = lineBefore.substring(ampIdx + 1);
                if (!afterAmp.contains(";") && !afterAmp.contains(" ") && afterAmp.length() <= 10) {
                    HtmlTagParser.HtmlContext ctx = tagParser.parseContext(fullText, cursorPos);
                    boolean insideCode = (ctx.unclosedTag != null &&
                            ("script".equalsIgnoreCase(ctx.unclosedTag) || "style".equalsIgnoreCase(ctx.unclosedTag)))
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

        // 3. Embedded <style> / <script> block delegation via AST/ProjectIndex
        if (currentFile != null) {
            com.cocode.vcode.ide.core.language.js.ParseResult cached = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().getParseResult(currentFile.getAbsolutePath());
            if (cached != null && cached.embeddedResults != null) {
                for (com.cocode.vcode.ide.core.language.js.ParseResult.EmbeddedResult emb : cached.embeddedResults) {
                    if (cursorPos >= emb.startOffset && cursorPos <= emb.endOffset) {
                        String embeddedContent = fullText.substring(emb.startOffset, Math.min(emb.endOffset, fullText.length()));
                        int embeddedCursor = cursorPos - emb.startOffset;
                        if (emb.result.tree != null) {
                            return jsEngine.getSuggestions(embeddedContent, embeddedCursor);
                        } else if (emb.result.cssTree != null) {
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
                return cssEngine.getSuggestions(embeddedContent, embeddedCursor);
            }
        }
        int scriptStart = findBlockContentStart(fullText, cursorPos, "script");
        if (scriptStart >= 0 && cursorPos >= scriptStart) {
            int scriptEnd = fullText.indexOf("</script", scriptStart);
            if (scriptEnd == -1 || cursorPos <= scriptEnd) {
                String embeddedContent = fullText.substring(scriptStart, scriptEnd == -1 ? fullText.length() : scriptEnd);
                int embeddedCursor = cursorPos - scriptStart;
                return jsEngine.getSuggestions(embeddedContent, embeddedCursor);
            }
        }

        // 4. Closing-tag suggestion on "</" using AST and tagParser
        int lastCloseTagIdx = lineBefore.lastIndexOf("</");
        if (lastCloseTagIdx != -1) {
            String afterSlash = lineBefore.substring(lastCloseTagIdx + 2);
            boolean onlyTagChars = true;
            for (int i = 0; i < afterSlash.length(); i++) {
                char ch = afterSlash.charAt(i);
                if (!Character.isLetterOrDigit(ch) && ch != '-' && ch != '_') {
                    onlyTagChars = false;
                    break;
                }
            }
            if (onlyTagChars) {
                String unclosedTag = null;
                try {
                    HtmlTokenStream tokens = HtmlLexer.tokenize(fullText);
                    com.cocode.vcode.ide.core.language.js.ParseResult parseRes = HtmlParser.parse(fullText, tokens);
                    int elem = parseRes.htmlTree.getEnclosingElement(cursorPos);
                    if (elem > 0 && parseRes.htmlTree.nodeName[elem] != null) {
                        unclosedTag = parseRes.htmlTree.nodeName[elem];
                    }
                } catch (Exception ignored) {}

                if (unclosedTag == null) {
                    HtmlTagParser.HtmlContext c = tagParser.parseContext(fullText, cursorPos);
                    unclosedTag = c.unclosedTag;
                }

                if (unclosedTag != null && !unclosedTag.isEmpty()) {
                    if (afterSlash.isEmpty() || unclosedTag.toLowerCase().startsWith(afterSlash.toLowerCase())) {
                        List<CompletionItem> result = new ArrayList<>();
                        CompletionItem ci = new CompletionItem(
                                "</" + unclosedTag + ">",
                                "</" + unclosedTag + ">",
                                "Close tag <" + unclosedTag + ">",
                                CompletionItem.Type.TAG, 0);
                        ci.setReplaceLength(2 + afterSlash.length());
                        result.add(ci);
                        return result;
                    }
                }
            }
        }

        // 5. AST-Aware Position Dispatching via HtmlStaticCompletionDispatcher
        com.cocode.vcode.ide.core.completion.staticdata.HtmlStaticCompletionDispatcher.Position pos =
                com.cocode.vcode.ide.core.completion.staticdata.HtmlStaticCompletionDispatcher.detectPosition(fullText, cursorPos);

        if (pos == com.cocode.vcode.ide.core.completion.staticdata.HtmlStaticCompletionDispatcher.Position.ATTRIBUTE_NAME) {
            List<CompletionItem> attrItems = com.cocode.vcode.ide.core.completion.staticdata.HtmlStaticCompletionDispatcher.buildCompletions(pos, fullText, cursorPos);
            if (!attrItems.isEmpty()) {
                return fuzzyFilter(attrItems, word != null ? word : "");
            }
        }

        HtmlTagParser.HtmlContext ctx = tagParser.parseContext(fullText, cursorPos);
        if (ctx.isInsideComment) {
            return new ArrayList<>();
        }

        // 6. Inside an attribute value
        if (ctx.isInsideOpenTag && !ctx.isTypingTagName && ctx.currentTagName != null) {
            if (ctx.isInsideAttributeValue && ctx.currentAttributeName != null) {
                String attrName = ctx.currentAttributeName;
                String typedValue = ctx.currentAttributeValue != null ? ctx.currentAttributeValue : "";

                // 6a. Inside style="…" → CSS
                if ("style".equals(attrName)) {
                    return cssEngine.getSuggestions(typedValue, typedValue.length(), true);
                }

                // 6b. Inside on*="…" → JS
                if (attrName.startsWith("on")) {
                    return jsEngine.getSuggestions(typedValue, typedValue.length());
                }

                // 6c. Inside file-path attribute → file suggestions
                if (attrName.equals("src") || attrName.equals("href") || attrName.equals("action") ||
                        attrName.equals("formaction") || attrName.equals("poster") || attrName.equals("data") ||
                        attrName.equals("cite") || attrName.equals("manifest") || attrName.equals("srcset")) {

                    String pathQuery = getPathQuery(typedValue, attrName);
                    return getFileSuggestions(pathQuery, ctx.currentTagName, attrName);
                }

                // 6d. Inside class="…" or id="…"
                String attrWord = typedValue;
                int lastSpace = typedValue.lastIndexOf(' ');
                if (lastSpace != -1) {
                    attrWord = typedValue.substring(lastSpace + 1);
                }

                if ("class".equals(attrName)) {
                    List<CompletionItem> classes = ProjectSymbolIndex.getInstance().getCssClassItems();
                    if (!classes.isEmpty()) {
                        List<CompletionItem> res = fuzzyFilter(classes, attrWord);
                        for (CompletionItem ci : res) {
                            ci.setReplaceLength(attrWord.length());
                        }
                        return res;
                    }
                } else if ("id".equals(attrName)) {
                    List<CompletionItem> ids = ProjectSymbolIndex.getInstance().getCssIdItems();
                    List<CompletionItem> htmlIds = ProjectSymbolIndex.getInstance().getHtmlIdItems();
                    List<CompletionItem> allIds = new ArrayList<>(ids);
                    allIds.addAll(htmlIds);
                    if (!allIds.isEmpty()) {
                        List<CompletionItem> res = fuzzyFilter(allIds, attrWord);
                        for (CompletionItem ci : res) {
                            ci.setReplaceLength(attrWord.length());
                        }
                        return res;
                    }
                }

                String[] values = HtmlDefinitions.getAttributeValues(ctx.currentTagName, attrName);
                if (values != null) {
                    boolean isMultiValue = "rel".equals(attrName) || "sandbox".equals(attrName)
                            || "autocomplete".equals(attrName) || "part".equals(attrName)
                            || "aria-haspopup".equals(attrName) || "role".equals(attrName);

                    String filterWord = typedValue;
                    java.util.Set<String> alreadyChosen = new java.util.HashSet<>();
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
                        if (isMultiValue && alreadyChosen.contains(v.toLowerCase())) {
                            continue;
                        }
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

                return new ArrayList<>();
            }
        }

        // 7. Emmet expansion
        String emmetAbbr = getEmmetAbbreviationBeforeCursor(fullText, cursorPos);
        List<CompletionItem> emmetResults = new ArrayList<>();
        if (emmetAbbr != null && !emmetAbbr.isEmpty() && !emmetAbbr.contains("<")) {
            String expanded = EmmetParser.expandHtml(emmetAbbr, htmlBoilerplate);
            if (expanded != null) {
                boolean isComplex = emmetAbbr.contains(".") || emmetAbbr.contains("#")
                        || emmetAbbr.contains(">") || emmetAbbr.contains("*")
                        || emmetAbbr.contains("+") || emmetAbbr.contains("^")
                        || emmetAbbr.contains("(") || emmetAbbr.contains("{")
                        || emmetAbbr.contains("[") || emmetAbbr.contains("]")
                        || emmetAbbr.contains(":")
                        || emmetAbbr.equals("!")
                        || emmetAbbr.startsWith("lorem");
                
                if (isComplex) {
                    CompletionItem emmetItem = new CompletionItem(emmetAbbr, expanded,
                            "Emmet Abbreviation", CompletionItem.Type.SNIPPET, 0);
                    emmetItem.setReplaceLength(emmetAbbr.length());
                    
                    List<CompletionItem> res = new ArrayList<>();
                    res.add(emmetItem);
                    return res;
                }
            }
        }

        // 8. Tag name completions
        boolean isTagNamePos = pos == com.cocode.vcode.ide.core.completion.staticdata.HtmlStaticCompletionDispatcher.Position.TAG_NAME
                || ctx.isTypingTagName
                || trimmed.endsWith("<")
                || (lineBefore.lastIndexOf('<') > lineBefore.lastIndexOf('>'));

        boolean isTagEligible = isTagNamePos || (word != null && !word.isEmpty() && lineBefore.trim().equals(word));

        if (isTagEligible && !ctx.isInsideAttributeValue && !ctx.isInsideComment && !ctx.isInsideCloseTag) {
            if (isInsideEmmetBraces(lineBefore)) {
                return emmetResults.isEmpty() ? new ArrayList<>() : emmetResults;
            }
            List<CompletionItem> finalResults = new ArrayList<>(emmetResults);

            List<CompletionItem> staticTags = com.cocode.vcode.ide.core.completion.staticdata.HtmlStaticCompletionDispatcher.buildCompletions(
                    com.cocode.vcode.ide.core.completion.staticdata.HtmlStaticCompletionDispatcher.Position.TAG_NAME, fullText, cursorPos);
            
            if (!staticTags.isEmpty()) {
                finalResults.addAll(fuzzyFilter(staticTags, word != null ? word : ""));
            } else {
                List<CompletionItem> prefixMatches = TAG_TRIE.getCompletions(word, MAX_SUGGESTIONS);
                if (!prefixMatches.isEmpty()) {
                    finalResults.addAll(prefixMatches);
                } else {
                    finalResults.addAll(fuzzyFilter(tagItems, word));
                }
            }
            return finalResults;
        }

        return emmetResults.isEmpty() ? new ArrayList<>() : emmetResults;
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

    // Emmet brace detection
    /**
     * Returns true if the cursor is inside unmatched curly braces on the current line.
     * This indicates the user is typing Emmet text content like {@code a{Click me|}}
     * and we should NOT show HTML tag suggestions for the words inside.
     */
    private boolean isInsideEmmetBraces(String lineBefore) {
        int depth = 0;
        for (int i = 0; i < lineBefore.length(); i++) {
            char c = lineBefore.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
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
     * Provides VS Code-style file/folder path completions for path-bearing attributes
     * (src, href, action…). Shows the immediate directory contents when a slash is
     * present; otherwise does a recursive fuzzy-prefix search from the project root.
     */
    private List<CompletionItem> getFileSuggestions(String typedPath, String tagName, String attrName) {
        if (currentFile == null) return new ArrayList<>();
        File currentDir = currentFile.getParentFile();
        if (currentDir == null) return new ArrayList<>();

        List<CompletionItem> items = new ArrayList<>();
        int lastSlash = typedPath.lastIndexOf('/');

        if (lastSlash != -1 || typedPath.isEmpty()) {
            // User typed a path with a directory component OR it's empty — list that directory
            String dirPart = lastSlash != -1 ? typedPath.substring(0, lastSlash) : "";
            String filterPrefix = lastSlash != -1 ? typedPath.substring(lastSlash + 1).toLowerCase() : typedPath.toLowerCase();
            File searchDir = dirPart.isEmpty() ? currentDir : new File(currentDir, dirPart);

            if (searchDir.exists() && searchDir.isDirectory()) {
                List<File> files = VFSManager.getInstance().listCachedFiles(searchDir);
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().startsWith(".")) continue;
                        if (!isFileAllowed(f, tagName, attrName)) continue;
                        String name = f.getName();
                        if (!filterPrefix.isEmpty() && !name.toLowerCase().startsWith(filterPrefix))
                            continue;
                        String completion = name + (f.isDirectory() ? "/" : "");
                        CompletionItem ci = new CompletionItem(completion, completion,
                                f.isDirectory() ? "Directory" : getFileSizeHint(f),
                                f.isDirectory() ? CompletionItem.Type.FOLDER : CompletionItem.Type.FILE, 0);
                        ci.setReplaceLength(filterPrefix.length());
                        items.add(ci);
                    }
                }
            }
            sortFileItems(items);
            return items.size() > MAX_SUGGESTIONS ? items.subList(0, MAX_SUGGESTIONS) : items;
        }

        // No slash — search recursively from the project root
        File projectRoot = getProjectRoot(currentFile);
        if (projectRoot == null) projectRoot = currentDir;

        List<File> allMatching = new ArrayList<>();
        findFilesRecursively(projectRoot, typedPath.toLowerCase(), allMatching, 50, tagName, attrName);

        for (File f : allMatching) {
            String relPath = getRelativeHtmlPath(currentDir, f);
            String label = f.getName() + (f.isDirectory() ? "/" : "");
            CompletionItem ci = new CompletionItem(label, relPath,
                    f.isDirectory() ? "Directory" : relPath,
                    f.isDirectory() ? CompletionItem.Type.FOLDER : CompletionItem.Type.FILE, 0);
            ci.setReplaceLength(typedPath.length());
            items.add(ci);
        }
        sortFileItems(items);
        return items;
    }

    private void sortFileItems(List<CompletionItem> items) {
        // Folders first, then files alphabetically
        Collections.sort(items, (a, b) -> {
            int fa = a.getType() == CompletionItem.Type.FOLDER ? 0 : 1;
            int fb = b.getType() == CompletionItem.Type.FOLDER ? 0 : 1;
            if (fa != fb) return fa - fb;
            return a.getLabel().compareToIgnoreCase(b.getLabel());
        });
    }

    private String getFileSizeHint(File f) {
        long size = f.length();
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        return (size / (1024 * 1024)) + " MB";
    }

    // Path helpers
    private File getProjectRoot(File file) {
        return ProjectRepository.findProjectRoot(file);
    }

    private String getRelativeHtmlPath(File baseDir, File target) {
        String[] basePath = baseDir.getAbsolutePath().split("/");
        String[] targetPath = target.getAbsolutePath().split("/");

        int common = 0;
        while (common < basePath.length && common < targetPath.length
                && basePath[common].equals(targetPath[common])) {
            common++;
        }

        StringBuilder rel = new StringBuilder();
        for (int i = common; i < basePath.length; i++) rel.append("../");
        for (int i = common; i < targetPath.length; i++) {
            rel.append(targetPath[i]);
            if (i < targetPath.length - 1) rel.append("/");
        }
        if (target.isDirectory() && rel.length() > 0 && rel.charAt(rel.length() - 1) != '/') {
            rel.append("/");
        }
        return rel.length() == 0 ? "./" : rel.toString();
    }

    private void findFilesRecursively(File dir, String query, List<File> results, int limit, String tagName, String attrName) {
        if (results.size() >= limit) return;
        List<File> files = VFSManager.getInstance().listCachedFiles(dir);
        if (files == null) return;
        for (File f : files) {
            if (f.getName().startsWith(".")) continue;
            if (!isFileAllowed(f, tagName, attrName)) continue;

            if (f.getName().toLowerCase().startsWith(query)) {
                results.add(f);
                if (results.size() >= limit) return;
            }
            if (f.isDirectory()) {
                findFilesRecursively(f, query, results, limit, tagName, attrName);
            }
        }
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
}