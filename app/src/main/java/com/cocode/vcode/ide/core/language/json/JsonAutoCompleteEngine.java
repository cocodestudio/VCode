package com.cocode.vcode.ide.core.language.json;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.FastTrie;
import com.cocode.vcode.ide.core.completion.staticdata.JsonStaticCompletionDispatcher;
import com.cocode.vcode.ide.core.completion.staticdata.StaticAssetReader;
import com.cocode.vcode.ide.core.model.CompletionItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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

    // Value type templates
    private static final List<CompletionItem> VALUE_ITEMS;
    private static final List<CompletionItem> BOOL_NULL_ITEMS;

    // Schema-aware key completions for known JSON files
    private static final Map<String, List<CompletionItem>> SCHEMA_KEYS = new HashMap<>();
    private static final Object lock = new Object();
    private static volatile boolean schemasLoaded = false;

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

    private static void ensureSchemasLoaded() {
        if (schemasLoaded) return;
        synchronized (lock) {
            if (schemasLoaded) return;
            loadSchemasFromAssets();
            schemasLoaded = true;
        }
    }

    private static void loadSchemasFromAssets() {
        String jsonStr = StaticAssetReader.readAsset("completions/json_schemas.json");
        if (jsonStr == null || jsonStr.trim().isEmpty()) return;
        try {
            JSONObject root = new JSONObject(jsonStr);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String schemaName = keys.next();
                JSONArray arr = root.getJSONArray(schemaName);
                List<CompletionItem> items = new ArrayList<>(arr.length());
                for (int i = 0; i < arr.length(); i++) {
                    JSONArray entry = arr.getJSONArray(i);
                    String label = entry.getString(0);
                    String insertText = entry.getString(1);
                    String detail = entry.getString(2);
                    items.add(new CompletionItem(label, insertText, detail, CompletionItem.Type.JSON_KEY, 0));
                }
                SCHEMA_KEYS.put(schemaName, items);
            }
        } catch (Exception ignored) {
        }
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

    // Snippet loading

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

    // Document key scanning via AST

    /**
     * Scans the full JSON document for object keys using JsonSyntaxTree and caches them.
     * Only re-scans when the document content has changed.
     */
    private void ensureDocKeysIndexed(String text) {
        int hash = text.hashCode();
        if (hash == lastTextHash) return;
        lastTextHash = hash;
        cachedDocKeys.clear();
        docKeysTrie.clear();

        java.util.Set<String> seen = new java.util.HashSet<>();
        JsonTokenStream stream = JsonLexer.tokenize(text);
        JsonSyntaxTree tree = JsonParser.parse(stream, text);
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsonSyntaxTree.N_KEY) {
                String rawKey = tree.nodeName[i];
                if (rawKey != null) {
                    String cleanKey = rawKey.startsWith("\"") && rawKey.endsWith("\"") && rawKey.length() >= 2
                            ? rawKey.substring(1, rawKey.length() - 1) : rawKey;
                    if (!cleanKey.isEmpty() && seen.add(cleanKey)) {
                        CompletionItem item = new CompletionItem(
                                "\"" + cleanKey + "\"",
                                "\"" + cleanKey + "\": |",
                                "Document key",
                                CompletionItem.Type.JSON_KEY, 0);
                        cachedDocKeys.add(item);
                        docKeysTrie.insert(item);
                    }
                }
            }
        }
    }

    // Main entry point
    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length())
            return new ArrayList<>();

        if (isInsideComment(fullText, cursorPos)) {
            return new ArrayList<>();
        }

        String fileName = getCurrentFileName();
        JsonStaticCompletionDispatcher.Position staticPos = JsonStaticCompletionDispatcher.detectPosition(fullText, cursorPos);
        if (staticPos == JsonStaticCompletionDispatcher.Position.FILE_EMPTY) {
            return JsonStaticCompletionDispatcher.buildCompletions(staticPos, fileName);
        }

        ensureDocKeysIndexed(fullText);

        // Check if cursor is inside a quoted string
        int quoteCount = 0;
        for (int k = 0; k < cursorPos; k++) {
            if (fullText.charAt(k) == '"' && (k == 0 || fullText.charAt(k - 1) != '\\')) {
                quoteCount++;
            }
        }
        boolean inQuotes = (quoteCount % 2 != 0);

        String query = getJsonQuery(fullText, cursorPos, inQuotes);
        int replaceLen = (inQuotes ? 1 : 0) + query.length();

        String line = getLineBeforeCursor(fullText, cursorPos);
        String trimmed = line.trim();

        // Prevent showing all suggestions immediately after typing { or }
        if (query.isEmpty() && !inQuotes) {
            if (trimmed.endsWith("{") || trimmed.endsWith("}")) {
                return new ArrayList<>();
            }
            if (cursorPos > 0 && fullText.charAt(cursorPos - 1) == '(') {
                return new ArrayList<>();
            }
        }

        // Value position completions
        if (staticPos == JsonStaticCompletionDispatcher.Position.VALUE) {
            String currentKey = resolveCurrentKey(fullText, cursorPos);
            List<CompletionItem> candidates = new ArrayList<>();

            List<CompletionItem> specificValues = getSpecificValueCompletions(fileName, currentKey);
            if (specificValues != null && !specificValues.isEmpty()) {
                candidates.addAll(specificValues);
            }

            for (CompletionItem vi : VALUE_ITEMS) {
                candidates.add(new CompletionItem(vi.getLabel(), vi.getInsertText(), vi.getDetail(), vi.getType(), vi.getCursorOffset()));
            }

            List<CompletionItem> filtered = fuzzyFilter(candidates, query);
            for (CompletionItem ci : filtered) {
                ci.setReplaceLength(replaceLen);
            }
            return filtered;
        }

        // Key position completions
        List<CompletionItem> keyCandidates = new ArrayList<>();

        // Schema-aware key suggestions based on file name
        if (fileName != null) {
            ensureSchemasLoaded();
            List<CompletionItem> schemaKeys = SCHEMA_KEYS.get(fileName);

            // For tsconfig.json, detect if we're inside compilerOptions block
            if ("tsconfig.json".equals(fileName) && isInsideObjectKey(fullText, cursorPos, "compilerOptions")) {
                schemaKeys = SCHEMA_KEYS.get("tsconfig_compilerOptions");
            }

            if (schemaKeys != null) {
                for (CompletionItem sk : schemaKeys) {
                    keyCandidates.add(new CompletionItem(sk.getLabel(), sk.getInsertText(), sk.getDetail(), sk.getType(), sk.getCursorOffset()));
                }
            }
        }

        for (CompletionItem dk : cachedDocKeys) {
            keyCandidates.add(new CompletionItem(dk.getLabel(), dk.getInsertText(), dk.getDetail(), dk.getType(), dk.getCursorOffset()));
        }
        for (CompletionItem si : snippetItems) {
            keyCandidates.add(new CompletionItem(si.getLabel(), si.getInsertText(), si.getDetail(), si.getType(), si.getCursorOffset()));
        }

        List<CompletionItem> filteredKeys = fuzzyFilter(keyCandidates, query);
        for (CompletionItem ci : filteredKeys) {
            ci.setReplaceLength(replaceLen);
        }
        return filteredKeys;
    }

    private String getJsonQuery(String text, int cursor, boolean inQuotes) {
        if (text == null || cursor <= 0) return "";
        if (inQuotes) {
            int i = cursor - 1;
            while (i >= 0) {
                if (text.charAt(i) == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                    return text.substring(i + 1, cursor);
                }
                i--;
            }
            return "";
        } else {
            return getWordBeforeCursor(text, cursor);
        }
    }

    private String resolveCurrentKey(String text, int cursor) {
        if (text == null || cursor <= 0) return null;
        int i = cursor - 1;
        int quoteCount = 0;
        for (int k = 0; k < cursor; k++) {
            if (text.charAt(k) == '"' && (k == 0 || text.charAt(k - 1) != '\\')) {
                quoteCount++;
            }
        }
        if (quoteCount % 2 != 0) {
            while (i >= 0) {
                if (text.charAt(i) == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                    i--;
                    break;
                }
                i--;
            }
        } else {
            while (i >= 0 && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_' || text.charAt(i) == '-')) {
                i--;
            }
        }
        while (i >= 0 && Character.isWhitespace(text.charAt(i))) i--;
        if (i < 0 || text.charAt(i) != ':') return null;
        i--;
        while (i >= 0 && Character.isWhitespace(text.charAt(i))) i--;
        if (i < 0) return null;
        if (text.charAt(i) == '"') {
            int endQuote = i;
            int startQuote = -1;
            for (int j = i - 1; j >= 0; j--) {
                if (text.charAt(j) == '"' && (j == 0 || text.charAt(j - 1) != '\\')) {
                    startQuote = j;
                    break;
                }
            }
            if (startQuote >= 0) {
                return text.substring(startQuote + 1, endQuote);
            }
        }
        return null;
    }

    private List<CompletionItem> getSpecificValueCompletions(String fileName, String keyName) {
        if (keyName == null) return null;
        List<CompletionItem> list = new ArrayList<>();
        if ("package.json".equals(fileName)) {
            if ("type".equals(keyName)) {
                list.add(new CompletionItem("\"module\"", "\"module\"", "ES Module", CompletionItem.Type.VALUE, 0));
                list.add(new CompletionItem("\"commonjs\"", "\"commonjs\"", "CommonJS", CompletionItem.Type.VALUE, 0));
                return list;
            } else if ("private".equals(keyName) || "sideEffects".equals(keyName)) {
                list.add(new CompletionItem("true", "true", "Boolean", CompletionItem.Type.VALUE, 0));
                list.add(new CompletionItem("false", "false", "Boolean", CompletionItem.Type.VALUE, 0));
                return list;
            }
        }
        if ("tsconfig.json".equals(fileName)) {
            if ("target".equals(keyName)) {
                String[] targets = {"\"ES2022\"", "\"ES2021\"", "\"ES2020\"", "\"ES2019\"", "\"ES2018\"", "\"ES2015\"", "\"ES6\"", "\"ESNext\""};
                for (String t : targets)
                    list.add(new CompletionItem(t, t, "ECMAScript target", CompletionItem.Type.VALUE, 0));
                return list;
            } else if ("module".equals(keyName)) {
                String[] mods = {"\"ESNext\"", "\"NodeNext\"", "\"Node16\"", "\"CommonJS\"", "\"AMD\"", "\"System\"", "\"UMD\""};
                for (String m : mods)
                    list.add(new CompletionItem(m, m, "Module system", CompletionItem.Type.VALUE, 0));
                return list;
            } else if ("moduleResolution".equals(keyName)) {
                String[] res = {"\"bundler\"", "\"node\"", "\"node16\"", "\"nodenext\"", "\"classic\""};
                for (String r : res)
                    list.add(new CompletionItem(r, r, "Module resolution", CompletionItem.Type.VALUE, 0));
                return list;
            } else if ("jsx".equals(keyName)) {
                String[] jsx = {"\"react-jsx\"", "\"react-jsxdev\"", "\"react\"", "\"preserve\"", "\"react-native\""};
                for (String j : jsx)
                    list.add(new CompletionItem(j, j, "JSX mode", CompletionItem.Type.VALUE, 0));
                return list;
            } else if ("strict".equals(keyName) || "esModuleInterop".equals(keyName) || "skipLibCheck".equals(keyName)
                    || "declaration".equals(keyName) || "sourceMap".equals(keyName) || "resolveJsonModule".equals(keyName)
                    || "allowJs".equals(keyName) || "noEmit".equals(keyName) || "isolatedModules".equals(keyName)
                    || "forceConsistentCasingInFileNames".equals(keyName) || "noUnusedLocals".equals(keyName)
                    || "noUnusedParameters".equals(keyName) || "noFallthroughCasesInSwitch".equals(keyName)) {
                list.add(new CompletionItem("true", "true", "Boolean", CompletionItem.Type.VALUE, 0));
                list.add(new CompletionItem("false", "false", "Boolean", CompletionItem.Type.VALUE, 0));
                return list;
            }
        }
        if ("manifest.json".equals(fileName)) {
            if ("display".equals(keyName)) {
                String[] modes = {"\"standalone\"", "\"fullscreen\"", "\"minimal-ui\"", "\"browser\""};
                for (String m : modes)
                    list.add(new CompletionItem(m, m, "Display mode", CompletionItem.Type.VALUE, 0));
                return list;
            } else if ("orientation".equals(keyName)) {
                String[] orients = {"\"portrait\"", "\"landscape\"", "\"any\"", "\"natural\""};
                for (String o : orients)
                    list.add(new CompletionItem(o, o, "Orientation", CompletionItem.Type.VALUE, 0));
                return list;
            }
        }
        return null;
    }

    /**
     * Checks if the cursor is inside a specific named object block.
     * e.g., for "compilerOptions": { ... cursor here ... }
     */
    private boolean isInsideObjectKey(String text, int cursorPos, String key) {
        String searchPattern = "\"" + key + "\"";
        int keyIdx = text.lastIndexOf(searchPattern, cursorPos);
        if (keyIdx < 0) return false;

        int open = 0, close = 0;
        boolean inStr = false;
        for (int i = keyIdx; i < cursorPos && i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inStr = !inStr;
                continue;
            }
            if (inStr) continue;
            if (c == '{') open++;
            else if (c == '}') close++;
        }
        return open > close;
    }
}