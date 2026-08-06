package com.cocode.vcode.ide.core.language.json;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.FastTrie;
import com.cocode.vcode.ide.core.model.CompletionItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Formatted suggestion provider for JSON — mirrors VS Code's JSON language server.
 *
 * <p>Improvements:
 * <ul>
 *   <li>VS Code-style fuzzy scoring for snippet matching</li>
 *   <li>Context-aware: key completions on the left of {@code :}, value completions on the right</li>
 *   <li>Document-key indexing — suggests keys already used elsewhere in the document</li>
 *   <li>Schema-aware completions for package.json, tsconfig.json, etc.</li>
 *   <li>Nested object depth awareness via brace counting</li>
 *   <li>Properly handles arrays, objects, boolean, null, number</li>
 * </ul>
 */
public class JsonAutoCompleteEngine extends AutoCompleteEngine {

    // ─── Value type templates ────────────────────────────────────────────────────
    private static final List<CompletionItem> VALUE_ITEMS;
    private static final List<CompletionItem> BOOL_NULL_ITEMS;

    // ─── Schema-aware key completions for known JSON files ────────────────────
    private static final Map<String, List<CompletionItem>> SCHEMA_KEYS = new HashMap<>();

    static {
        VALUE_ITEMS = new ArrayList<>();
        VALUE_ITEMS.add(new CompletionItem("\"\"", "\"\"", "String value", CompletionItem.Type.JSON_KEY, -1));
        VALUE_ITEMS.add(new CompletionItem("0", "0", "Number", CompletionItem.Type.VALUE, 0));
        VALUE_ITEMS.add(new CompletionItem("true", "true", "Boolean", CompletionItem.Type.VALUE, 0));
        VALUE_ITEMS.add(new CompletionItem("false", "false", "Boolean", CompletionItem.Type.VALUE, 0));
        VALUE_ITEMS.add(new CompletionItem("null", "null", "Null", CompletionItem.Type.VALUE, 0));
        VALUE_ITEMS.add(new CompletionItem("{}", "{\n  |\n}", "Object", CompletionItem.Type.SNIPPET, 0));
        VALUE_ITEMS.add(new CompletionItem("[]", "[\n  |\n]", "Array", CompletionItem.Type.SNIPPET, 0));

        BOOL_NULL_ITEMS = new ArrayList<>();
        BOOL_NULL_ITEMS.add(new CompletionItem("true", "true", "Boolean", CompletionItem.Type.VALUE, 0));
        BOOL_NULL_ITEMS.add(new CompletionItem("false", "false", "Boolean", CompletionItem.Type.VALUE, 0));
        BOOL_NULL_ITEMS.add(new CompletionItem("null", "null", "Null", CompletionItem.Type.VALUE, 0));

        // ── package.json keys ────────────────────────────────────────────
        List<CompletionItem> pkgKeys = new ArrayList<>();
        String[][] pkgEntries = {
                {"name", "\"name\": \"|\"", "Package name"},
                {"version", "\"version\": \"|1.0.0\"", "Semver version"},
                {"description", "\"description\": \"|\"", "Package description"},
                {"main", "\"main\": \"|index.js\"", "Entry point"},
                {"module", "\"module\": \"|index.mjs\"", "ES module entry"},
                {"type", "\"type\": \"|module\"", "Package type"},
                {"types", "\"types\": \"|index.d.ts\"", "TypeScript types"},
                {"exports", "\"exports\": {\n  \".\": \"|\" \n}", "Package exports"},
                {"scripts", "\"scripts\": {\n  |\n}", "NPM scripts"},
                {"dependencies", "\"dependencies\": {\n  |\n}", "Runtime dependencies"},
                {"devDependencies", "\"devDependencies\": {\n  |\n}", "Dev dependencies"},
                {"peerDependencies", "\"peerDependencies\": {\n  |\n}", "Peer dependencies"},
                {"keywords", "\"keywords\": [|\"\"]", "Search keywords"},
                {"author", "\"author\": \"|\"", "Package author"},
                {"license", "\"license\": \"|MIT\"", "License identifier"},
                {"repository", "\"repository\": {\n  \"type\": \"git\",\n  \"url\": \"|\"\n}", "Source repository"},
                {"bugs", "\"bugs\": {\n  \"url\": \"|\"\n}", "Bug tracker URL"},
                {"homepage", "\"homepage\": \"|\"", "Project homepage"},
                {"private", "\"private\": |true", "Prevent publishing"},
                {"engines", "\"engines\": {\n  \"node\": \"|>=18\"\n}", "Engine constraints"},
                {"files", "\"files\": [|\"\"]", "Files to publish"},
                {"bin", "\"bin\": {\n  |\n}", "CLI executables"},
                {"browserslist", "\"browserslist\": [\"|> 0.5%\", \"not dead\"]", "Browser targets"},
                {"workspaces", "\"workspaces\": [|\"packages/*\"]", "Monorepo workspaces"},
                {"sideEffects", "\"sideEffects\": |false", "Tree-shaking hint"},
                {"publishConfig", "\"publishConfig\": {\n  \"access\": \"|public\"\n}", "Publish settings"},
        };
        for (String[] e : pkgEntries) {
            pkgKeys.add(new CompletionItem(e[0], e[1], e[2], CompletionItem.Type.JSON_KEY, 0));
        }
        SCHEMA_KEYS.put("package.json", pkgKeys);

        // ── tsconfig.json keys ───────────────────────────────────────────
        List<CompletionItem> tsKeys = new ArrayList<>();
        String[][] tsEntries = {
                {"compilerOptions", "\"compilerOptions\": {\n  |\n}", "Compiler settings"},
                {"include", "\"include\": [|\"src\"]", "Files to include"},
                {"exclude", "\"exclude\": [|\"node_modules\"]", "Files to exclude"},
                {"extends", "\"extends\": \"|\"", "Extends base config"},
                {"files", "\"files\": [|\"\"]", "Explicit file list"},
                {"references", "\"references\": [{\n  \"path\": \"|\"\n}]", "Project references"},
        };
        for (String[] e : tsEntries) {
            tsKeys.add(new CompletionItem(e[0], e[1], e[2], CompletionItem.Type.JSON_KEY, 0));
        }
        SCHEMA_KEYS.put("tsconfig.json", tsKeys);

        // ── tsconfig compilerOptions keys ────────────────────────────────
        List<CompletionItem> tsCompilerKeys = new ArrayList<>();
        String[][] tsCompilerEntries = {
                {"target", "\"target\": \"|ES2020\"", "ECMAScript target"},
                {"module", "\"module\": \"|ESNext\"", "Module system"},
                {"moduleResolution", "\"moduleResolution\": \"|bundler\"", "Resolution strategy"},
                {"lib", "\"lib\": [|\"ES2020\", \"DOM\"]", "Library files"},
                {"strict", "\"strict\": |true", "Enable strict mode"},
                {"esModuleInterop", "\"esModuleInterop\": |true", "ES module compat"},
                {"skipLibCheck", "\"skipLibCheck\": |true", "Skip .d.ts checking"},
                {"outDir", "\"outDir\": \"|./dist\"", "Output directory"},
                {"rootDir", "\"rootDir\": \"|./src\"", "Root directory"},
                {"declaration", "\"declaration\": |true", "Generate .d.ts"},
                {"sourceMap", "\"sourceMap\": |true", "Generate source maps"},
                {"jsx", "\"jsx\": \"|react-jsx\"", "JSX handling"},
                {"baseUrl", "\"baseUrl\": \".|\"", "Base path for modules"},
                {"paths", "\"paths\": {\n  \"|@/*\": [\"./src/*\"]\n}", "Path aliases"},
                {"resolveJsonModule", "\"resolveJsonModule\": |true", "Import .json files"},
                {"allowJs", "\"allowJs\": |true", "Allow JavaScript"},
                {"noEmit", "\"noEmit\": |true", "Don't emit output"},
                {"isolatedModules", "\"isolatedModules\": |true", "Single-file transpile"},
                {"forceConsistentCasingInFileNames", "\"forceConsistentCasingInFileNames\": |true", "Case sensitivity"},
                {"noUnusedLocals", "\"noUnusedLocals\": |true", "Warn unused locals"},
                {"noUnusedParameters", "\"noUnusedParameters\": |true", "Warn unused params"},
                {"noFallthroughCasesInSwitch", "\"noFallthroughCasesInSwitch\": |true", "Switch fallthrough"},
        };
        for (String[] e : tsCompilerEntries) {
            tsCompilerKeys.add(new CompletionItem(e[0], e[1], e[2], CompletionItem.Type.JSON_KEY, 0));
        }
        SCHEMA_KEYS.put("tsconfig_compilerOptions", tsCompilerKeys);

        // ── .eslintrc.json keys ──────────────────────────────────────────
        List<CompletionItem> eslintKeys = new ArrayList<>();
        String[][] eslintEntries = {
                {"env", "\"env\": {\n  \"browser\": true,\n  \"es2021\": true,\n  \"node\": true\n}|", "Environments"},
                {"extends", "\"extends\": [|\"eslint:recommended\"]", "Base configs"},
                {"plugins", "\"plugins\": [|\"\"]", "Plugins"},
                {"rules", "\"rules\": {\n  |\n}", "Rule overrides"},
                {"parserOptions", "\"parserOptions\": {\n  \"ecmaVersion\": \"latest\",\n  \"sourceType\": \"|module\"\n}", "Parser settings"},
                {"parser", "\"parser\": \"|\"", "Custom parser"},
                {"globals", "\"globals\": {\n  |\n}", "Global variables"},
                {"overrides", "\"overrides\": [{\n  \"files\": [\"|\"],\n  \"rules\": {}\n}]", "File-specific rules"},
                {"ignorePatterns", "\"ignorePatterns\": [|\"node_modules\"]", "Ignored files"},
                {"root", "\"root\": |true", "Root config marker"},
        };
        for (String[] e : eslintEntries) {
            eslintKeys.add(new CompletionItem(e[0], e[1], e[2], CompletionItem.Type.JSON_KEY, 0));
        }
        SCHEMA_KEYS.put(".eslintrc.json", eslintKeys);

        // ── manifest.json (PWA) keys ─────────────────────────────────────
        List<CompletionItem> manifestKeys = new ArrayList<>();
        String[][] manifestEntries = {
                {"name", "\"name\": \"|\"", "App name"},
                {"short_name", "\"short_name\": \"|\"", "Short name"},
                {"description", "\"description\": \"|\"", "Description"},
                {"start_url", "\"start_url\": \"|\"/\"", "Start URL"},
                {"display", "\"display\": \"|standalone\"", "Display mode"},
                {"background_color", "\"background_color\": \"|#ffffff\"", "Background color"},
                {"theme_color", "\"theme_color\": \"|#000000\"", "Theme color"},
                {"icons", "\"icons\": [{\n  \"src\": \"|\",\n  \"sizes\": \"192x192\",\n  \"type\": \"image/png\"\n}]", "App icons"},
                {"scope", "\"scope\": \"|\"/\"", "Navigation scope"},
                {"orientation", "\"orientation\": \"|portrait\"", "Orientation"},
                {"lang", "\"lang\": \"|en\"", "Language"},
                {"categories", "\"categories\": [|\"\"]", "App categories"},
        };
        for (String[] e : manifestEntries) {
            manifestKeys.add(new CompletionItem(e[0], e[1], e[2], CompletionItem.Type.JSON_KEY, 0));
        }
        SCHEMA_KEYS.put("manifest.json", manifestKeys);
    }

    private final List<CompletionItem> snippetItems = new ArrayList<>();
    private final List<CompletionItem> cachedDocKeys = new ArrayList<>();
    private final FastTrie docKeysTrie = new FastTrie();
    /**
     * Cache for keys extracted from the document itself.
     */
    private int lastTextHash = 0;
    private File currentFile;

    public JsonAutoCompleteEngine(Context context) {
        super(context);
        loadSnippets();
    }

    public void setCurrentFile(File file) {
        this.currentFile = file;
    }

    /**
     * Returns the file name of the current JSON file being edited, or null.
     */
    private String getCurrentFileName() {
        if (currentFile == null) return null;
        return currentFile.getName();
    }

    // ─── Snippet loading ──────────────────────────────────────────────────────────

    /**
     * Reads complex dictionary keys and developer-defined boilerplate schemas out of the JSON asset.
     */
    private void loadSnippets() {
        try {
            String json = loadAssetJson("completions/json_snippets.json");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String label = obj.optString("label");
                String snippet = obj.optString("snippet", label);
                String detail = obj.optString("detail", "");
                int offset = 0;

                if (snippet.contains("|")) {
                    String after = snippet.substring(snippet.indexOf('|') + 1);
                    offset = after.length();
                    snippet = snippet.replace("|", "");
                }
                snippetItems.add(new CompletionItem(label, snippet, detail,
                        CompletionItem.Type.SNIPPET, offset));
            }
        } catch (Exception e) {
            // Non-critical
        }
    }

    // ─── Document key scanning ─────────────────────────────────────────────────

    /**
     * Scans the full JSON document for quoted keys (strings before {@code :}) and caches them.
     * Only re-scans when the document content has changed.
     */
    private void ensureDocKeysIndexed(String text) {
        int hash = text.hashCode();
        if (hash == lastTextHash) return;
        lastTextHash = hash;
        cachedDocKeys.clear();
        docKeysTrie.clear();

        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\"([^\"\\\\]{1,64})\"\\s*:").matcher(text);
        int limit = Math.min(text.length(), 200_000);
        while (m.find() && m.start() < limit) {
            String key = m.group(1);
            if (seen.add(key)) {
                CompletionItem item = new CompletionItem(
                        "\"" + key + "\"",
                        "\"" + key + "\": |",
                        "Document key",
                        CompletionItem.Type.JSON_KEY, 0);
                cachedDocKeys.add(item);
                docKeysTrie.insert(item);
            }
        }
    }

    // ─── Main entry point ──────────────────────────────────────────────────────

    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (fullText == null || cursorPos < 0) return new ArrayList<>();

        if (isInsideStringLiteral(fullText, cursorPos)) {
            return new ArrayList<>();
        }

        ensureDocKeysIndexed(fullText);

        String line = getLineBeforeCursor(fullText, cursorPos);
        String trimmed = line.trim();
        String word = getWordBeforeCursor(fullText, cursorPos);

        // Prevent showing all suggestions immediately after typing { or }
        // We only want to show suggestions when the user hits Enter (trimmed will be empty)
        // or actually starts typing a word.
        if (word.isEmpty()) {
            if (trimmed.endsWith("{") || trimmed.endsWith("}")) {
                return new ArrayList<>();
            }
        }

        // ── After ':' — value completions ────────────────────────────────────
        if (trimmed.endsWith(":") || trimmed.endsWith(": ")) {
            List<CompletionItem> items = new ArrayList<>(VALUE_ITEMS);
            return fuzzyFilter(items, word.isEmpty() ? "" : word);
        }

        // ── Inside array or after comma — value completions ──────────────────
        if (trimmed.endsWith("[") || trimmed.endsWith(",")) {
            List<CompletionItem> items = new ArrayList<>(VALUE_ITEMS);
            return fuzzyFilter(items, word);
        }

        // ── Boolean / null keyword completions ──────────────────────────────
        if (!word.isEmpty() && ("true".startsWith(word) || "false".startsWith(word) || "null".startsWith(word))) {
            return fuzzyFilter(BOOL_NULL_ITEMS, word);
        }

        // ── Schema-aware key suggestions based on file name ──────────────────
        String fileName = getCurrentFileName();
        if (fileName != null) {
            List<CompletionItem> schemaKeys = SCHEMA_KEYS.get(fileName);

            // For tsconfig.json, detect if we're inside compilerOptions block
            if ("tsconfig.json".equals(fileName) && isInsideObjectKey(fullText, cursorPos, "compilerOptions")) {
                schemaKeys = SCHEMA_KEYS.get("tsconfig_compilerOptions");
            }

            if (schemaKeys != null) {
                List<CompletionItem> all = new ArrayList<>(schemaKeys);
                all.addAll(cachedDocKeys);
                all.addAll(snippetItems);
                return fuzzyFilter(all, word);
            }
        }

        // ── Key suggestions: snippets + document-extracted keys ──────────────
        List<CompletionItem> all = new ArrayList<>();
        List<CompletionItem> prefixMatches = docKeysTrie.getCompletions(word, MAX_SUGGESTIONS);
        if (!prefixMatches.isEmpty()) {
            all.addAll(prefixMatches);
            all.addAll(fuzzyFilter(snippetItems, word));
            return all;
        } else {
            List<CompletionItem> fallback = new ArrayList<>(snippetItems);
            fallback.addAll(cachedDocKeys);
            return fuzzyFilter(fallback, word);
        }
    }

    /**
     * Checks if the cursor is inside a specific named object block.
     * e.g., for "compilerOptions": { ... cursor here ... }
     */
    private boolean isInsideObjectKey(String text, int cursorPos, String key) {
        // Find the last occurrence of "compilerOptions" before cursor
        String searchPattern = "\"" + key + "\"";
        int keyIdx = text.lastIndexOf(searchPattern, cursorPos);
        if (keyIdx < 0) return false;

        // Count braces from key position to cursor
        int open = 0, close = 0;
        for (int i = keyIdx; i < cursorPos && i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') open++;
            else if (c == '}') close++;
        }
        return open > close;
    }
}