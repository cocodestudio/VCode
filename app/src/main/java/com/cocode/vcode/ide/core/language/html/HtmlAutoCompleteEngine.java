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

    // ─── Instance state ──────────────────────────────────────────────────────────
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

    // ─── Tag loading ────────────────────────────────────────────────────────────

    /**
     * Initialises HTML tag completions and per-tag attribute lists from the JSON asset.
     */
    private void loadTags() {
        try {
            String json = loadAssetJson("completions/html_tags.json");
            JSONArray arr = new JSONArray(json);
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
                JSONArray attrs = obj.optJSONArray("attributes");
                List<CompletionItem> attrList = new ArrayList<>(HtmlDefinitions.GLOBAL_ATTRS);
                if (attrs != null) {
                    for (int j = 0; j < attrs.length(); j++) {
                        String attr = attrs.optString(j);
                        attrList.add(0, new CompletionItem(attr, attr + "=\"|\"",
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

    // ─── Main entry point ───────────────────────────────────────────────────────

    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length()) {
            return new ArrayList<>();
        }

        String lineBefore = getLineBeforeCursor(fullText, cursorPos);
        String trimmed = lineBefore.trim();
        String word = getWordBeforeCursor(fullText, cursorPos);

        // ── 1. DOCTYPE / comment completions (when typing "<!" or "<!D") ─────────
        if (trimmed.equals("<!") || trimmed.startsWith("<!D") || trimmed.startsWith("<!d")) {
            String filter = trimmed.startsWith("<!") ? trimmed.substring(2) : "";
            return fuzzyFilter(HtmlDefinitions.DOCTYPE_ITEMS, filter);
        }

        // ── 1b. Entity completions (when typing "&" followed by letters) ──────
        if (!lineBefore.isEmpty()) {
            int ampIdx = lineBefore.lastIndexOf('&');
            if (ampIdx >= 0) {
                String afterAmp = lineBefore.substring(ampIdx + 1);
                // Only trigger if no semicolon yet and chars are entity-like
                if (!afterAmp.contains(";") && !afterAmp.contains(" ") && afterAmp.length() <= 10) {
                    String entityFilter = "&" + afterAmp;
                    List<CompletionItem> entityResults = fuzzyFilter(HtmlDefinitions.ENTITY_ITEMS, entityFilter);
                    if (!entityResults.isEmpty()) return entityResults;
                }
            }
        }

        HtmlTagParser.HtmlContext ctx = tagParser.parseContext(fullText, cursorPos);

        // ── 3. Closing-tag suggestion on "</" ─────────────────────────────────
        if (trimmed.endsWith("</") || lineBefore.endsWith("</")) {
            if (ctx.unclosedTag != null && !ctx.unclosedTag.isEmpty()) {
                List<CompletionItem> result = new ArrayList<>();
                result.add(new CompletionItem(
                        "</" + ctx.unclosedTag + ">",
                        "</" + ctx.unclosedTag + ">",
                        "Close tag",
                        CompletionItem.Type.TAG, 0));
                return result;
            }
        }

        // ── 4. Embedded <style> / <script> block delegation ───────────────────
        if ("style".equals(ctx.unclosedTag)) {
            // Extract only the CSS content between <style> and cursor
            int styleStart = findBlockContentStart(fullText, cursorPos, "style");
            if (styleStart >= 0) {
                String cssContent = fullText.substring(styleStart, cursorPos);
                int cssCursor = cursorPos - styleStart;
                return cssEngine.getSuggestions(cssContent, cssCursor);
            }
            return cssEngine.getSuggestions(fullText, cursorPos);
        } else if ("script".equals(ctx.unclosedTag)) {
            // Extract only the JS content between <script> and cursor
            int scriptStart = findBlockContentStart(fullText, cursorPos, "script");
            if (scriptStart >= 0) {
                String jsContent = fullText.substring(scriptStart, cursorPos);
                int jsCursor = cursorPos - scriptStart;
                return jsEngine.getSuggestions(jsContent, jsCursor);
            }
            return jsEngine.getSuggestions(fullText, cursorPos);
        }

        // ── 5. Inside an open tag — attribute / attribute-value completions ───
        if (ctx.isInsideOpenTag && !ctx.isTypingTagName && ctx.currentTagName != null) {
            if (ctx.isInsideAttributeValue && ctx.currentAttributeName != null) {
                String attrName = ctx.currentAttributeName;
                String typedValue = ctx.currentAttributeValue != null ? ctx.currentAttributeValue : "";

                // 5a. Inside style="…" → CSS
                if ("style".equals(attrName)) {
                    return cssEngine.getSuggestions(typedValue, typedValue.length(), true);
                }

                // 5b. Inside on*="…" → JS
                if (attrName.startsWith("on")) {
                    return jsEngine.getSuggestions(typedValue, typedValue.length());
                }

                // 5c. Inside file-path attribute → file suggestions
                if (attrName.equals("src") || attrName.equals("href") || attrName.equals("action") ||
                        attrName.equals("formaction") || attrName.equals("poster") || attrName.equals("data") ||
                        attrName.equals("cite") || attrName.equals("manifest") || attrName.equals("srcset")) {

                    String pathQuery = getPathQuery(typedValue, attrName);

                    return getFileSuggestions(pathQuery, ctx.currentTagName, attrName);
                }

                // 5d. Inside a generic attribute value (e.g. class="…", id="…", dir="…")
                String attrWord = typedValue;
                int lastSpace = typedValue.lastIndexOf(' ');
                if (lastSpace != -1) {
                    attrWord = typedValue.substring(lastSpace + 1);
                }

                if ("class".equals(attrName)) {
                    List<CompletionItem> classes = ProjectSymbolIndex.getInstance().getCssClassItems();
                    if (!classes.isEmpty()) {
                        return fuzzyFilter(classes, attrWord);
                    }
                } else if ("id".equals(attrName)) {
                    List<CompletionItem> ids = ProjectSymbolIndex.getInstance().getCssIdItems();
                    List<CompletionItem> htmlIds = ProjectSymbolIndex.getInstance().getHtmlIdItems();
                    List<CompletionItem> allIds = new ArrayList<>(ids);
                    allIds.addAll(htmlIds);
                    if (!allIds.isEmpty()) {
                        return fuzzyFilter(allIds, attrWord);
                    }
                }

                String[] values = HtmlDefinitions.ATTR_VALUES.get(attrName);
                if (values != null) {
                    List<CompletionItem> valItems = new ArrayList<>();
                    for (String v : values) {
                        valItems.add(new CompletionItem(v, v, attrName + " value",
                                CompletionItem.Type.VALUE, 0));
                    }
                    return fuzzyFilter(valItems, typedValue);
                }

                // We are inside quotes for an attribute, but we don't have specific completions.
                // Return empty list so we don't fall through and suggest attribute names.
                return new ArrayList<>();
            }

            // 5e. Attribute name completions for the current tag
            List<CompletionItem> attrs = attrMap.get(ctx.currentTagName);
            if (attrs == null) attrs = new ArrayList<>(HtmlDefinitions.GLOBAL_ATTRS);
            return fuzzyFilter(attrs, word);
        }

        // ── 6. Emmet expansion ────────────────────────────────────────────────
        String emmetAbbr = getEmmetAbbreviationBeforeCursor(fullText, cursorPos);
        List<CompletionItem> emmetResults = new ArrayList<>();
        if (emmetAbbr != null && !emmetAbbr.isEmpty() && !emmetAbbr.contains("<")) {
            String expanded = EmmetParser.expandHtml(emmetAbbr, htmlBoilerplate);
            if (expanded != null) {
                boolean isComplex = emmetAbbr.contains(".") || emmetAbbr.contains("#")
                        || emmetAbbr.contains(">") || emmetAbbr.contains("*")
                        || emmetAbbr.contains("+") || emmetAbbr.contains("^")
                        || emmetAbbr.contains("(") || emmetAbbr.contains("{")
                        || emmetAbbr.equals("!");
                CompletionItem emmetItem = new CompletionItem(emmetAbbr, expanded,
                        "Emmet Abbreviation", CompletionItem.Type.SNIPPET, 0);
                emmetItem.setReplaceLength(emmetAbbr.length());
                if (isComplex) {
                    // Complex Emmet abbreviation → only show this one item
                    List<CompletionItem> res = new ArrayList<>();
                    res.add(emmetItem);
                    return res;
                }
                emmetResults.add(emmetItem);
            }
        }

        // ── 7. Tag name completions (when typing "div", "<div", etc.) ─────────
        // Suppress tag suggestions when cursor is inside Emmet text braces {}
        if ((word != null && !word.isEmpty()) || trimmed.endsWith("<")) {
            if (isInsideEmmetBraces(lineBefore)) {
                return emmetResults.isEmpty() ? new ArrayList<>() : emmetResults;
            }
            List<CompletionItem> finalResults = new ArrayList<>(emmetResults);

            // Get O(L) fast prefix matches via Trie
            List<CompletionItem> prefixMatches = TAG_TRIE.getCompletions(word, MAX_SUGGESTIONS);
            if (!prefixMatches.isEmpty()) {
                finalResults.addAll(prefixMatches);
            } else {
                // Fallback to fuzzy filtering if no strict prefix matched
                finalResults.addAll(fuzzyFilter(tagItems, word));
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

    // ─── Emmet brace detection ─────────────────────────────────────────────────

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

    // ─── Embedded block content extraction ─────────────────────────────────────

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

    // ─── File / folder path suggestions ────────────────────────────────────────

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

    // ─── Path helpers ───────────────────────────────────────────────────────────

    private File getProjectRoot(File file) {
        if (file == null) return null;
        File dir = file.isDirectory() ? file : file.getParentFile();
        while (dir != null) {
            if (new File(dir, "project_meta.json").exists()) return dir;
            dir = dir.getParentFile();
        }
        return null;
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