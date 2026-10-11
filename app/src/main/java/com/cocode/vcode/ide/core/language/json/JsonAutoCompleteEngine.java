package com.cocode.vcode.ide.core.language.json;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.FastTrie;
import com.cocode.vcode.ide.core.autocomplete.PathAutoCompleteEngine;
import com.cocode.vcode.ide.core.completion.staticdata.JsonStaticCompletionDispatcher;
import com.cocode.vcode.ide.core.completion.staticdata.StaticAssetReader;
import com.cocode.vcode.ide.core.model.CompletionItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.cocode.vcode.ide.core.autocomplete.VFSManager;

/**
 * Context-aware suggestion provider for JSON documents.
 *
 * <p>Features:
 * <ul>
 *   <li>Fuzzy scoring for snippet matching</li>
 *   <li>Context-aware: key completions on the left of {@code :}, value completions on the right</li>
 *   <li>Document-key indexing — suggests keys already used elsewhere in the document</li>
 *   <li>Schema-aware completions for package.json, tsconfig.json, etc.</li>
 *   <li>Nested object depth awareness via brace counting</li>
 *   <li>Handles arrays, objects, boolean, null, and numbers</li>
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

    @Override
    public String getTriggerCharacters() {
        return "\"':{[/";
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
        SCHEMA_KEYS.putAll(com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader.getJsonSchemaKeysMap());
    }

    private PathAutoCompleteEngine pathEngine;

    public void setCurrentFile(File file) {
        this.currentFile = file;
        if (pathEngine != null) {
            pathEngine.setCurrentFile(file);
        }
    }

    /**
     * Returns the file name of the current JSON file being edited, or null.
     */
    private String getCurrentFileName() {
        if (currentFile == null) return null;
        String name = currentFile.getPath();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name;
    }

    // Snippet loading

    /**
     * Reads complex dictionary keys and developer-defined boilerplate schemas out of the JSON asset.
     */
    private void loadSnippets() {
        snippetItems.addAll(com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader.getJsonSnippetCompletionItems());
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

        JsonContextScanner.JsonContext ctx = JsonContextScanner.scan(fullText, cursorPos);
        if (ctx.type == JsonContextScanner.ContextType.COMMENT || ctx.type == JsonContextScanner.ContextType.NONE) {
            return new ArrayList<>();
        }

        String fileName = getCurrentFileName();
        if (ctx.type == JsonContextScanner.ContextType.FILE_EMPTY) {
            return JsonStaticCompletionDispatcher.buildCompletions(JsonStaticCompletionDispatcher.Position.FILE_EMPTY, fileName);
        }

        ensureDocKeysIndexed(fullText);

        String query = ctx.word;
        int replaceLen = ctx.replaceLength;

        // Prevent showing all suggestions immediately after typing { or } when query is empty outside quotes
        if (query.isEmpty() && !ctx.inQuotes) {
            String line = getLineBeforeCursor(fullText, cursorPos);
            String trimmed = line.trim();
            if (trimmed.endsWith("{") || trimmed.endsWith("}")) {
                return new ArrayList<>();
            }
            if (cursorPos > 0 && fullText.charAt(cursorPos - 1) == '(') {
                return new ArrayList<>();
            }
        }

        // 1. Value position completions
        if (ctx.type == JsonContextScanner.ContextType.OBJECT_VALUE) {
            List<CompletionItem> candidates = new ArrayList<>();

            // Specific value completions for package.json, tsconfig.json, manifest.json
            List<CompletionItem> specificValues = getSpecificValueCompletions(fileName, ctx.activeKey, ctx.inQuotes);
            if (specificValues != null && !specificValues.isEmpty()) {
                candidates.addAll(specificValues);
            }

            // Schema-driven enum and boolean completions
            addSchemaValueCompletions(fileName, ctx, candidates);

            // Generic values (booleans, null, structural snippets)
            for (CompletionItem vi : VALUE_ITEMS) {
                candidates.add(new CompletionItem(vi.getLabel(), vi.getInsertText(), vi.getDetail(), vi.getType(), vi.getCursorOffset()));
            }

            List<CompletionItem> filtered = fuzzyFilter(candidates, query);
            for (CompletionItem ci : filtered) {
                if (ci.getReplaceLength() < 0) {
                    ci.setReplaceLength(replaceLen);
                }
            }
            return filtered;
        }

        // 2. Array element position completions
        if (ctx.type == JsonContextScanner.ContextType.ARRAY_ELEMENT) {
            List<CompletionItem> candidates = new ArrayList<>();

            // Schema-driven array completions (e.g. lib or paths)
            addSchemaArrayCompletions(fileName, ctx, candidates);

            for (CompletionItem vi : VALUE_ITEMS) {
                candidates.add(new CompletionItem(vi.getLabel(), vi.getInsertText(), vi.getDetail(), vi.getType(), vi.getCursorOffset()));
            }

            List<CompletionItem> filtered = fuzzyFilter(candidates, query);
            for (CompletionItem ci : filtered) {
                if (ci.getReplaceLength() < 0) {
                    ci.setReplaceLength(replaceLen);
                }
            }
            return filtered;
        }

        // 3. Key position completions (OBJECT_KEY)
        List<CompletionItem> keyCandidates = new ArrayList<>();

        // Schema-aware key suggestions based on file name & active property path
        addSchemaKeyCompletions(fileName, ctx, keyCandidates);

        // Document keys from document itself
        for (CompletionItem dk : cachedDocKeys) {
            String clean = JsonSchemaManager.cleanKey(dk.getLabel());
            if (!ctx.existingKeys.contains(clean)) {
                keyCandidates.add(new CompletionItem(dk.getLabel(), dk.getInsertText(), dk.getDetail(), dk.getType(), dk.getCursorOffset()));
            }
        }
        // Snippet templates for non-schema files or fallback
        if (keyCandidates.isEmpty()) {
            for (CompletionItem si : snippetItems) {
                String clean = JsonSchemaManager.cleanKey(si.getLabel());
                if (!ctx.existingKeys.contains(clean)) {
                    keyCandidates.add(new CompletionItem(si.getLabel(), si.getInsertText(), si.getDetail(), si.getType(), si.getCursorOffset()));
                }
            }
        }

        List<CompletionItem> filteredKeys = fuzzyFilter(keyCandidates, query);
        int keyReplaceLen = (ctx.inQuotes ? 1 : 0) + query.length();
        for (CompletionItem ci : filteredKeys) {
            ci.setReplaceLength(keyReplaceLen);
        }
        return filteredKeys;
    }

    private void addSchemaValueCompletions(String fileName, JsonContextScanner.JsonContext ctx, List<CompletionItem> out) {
        if (ctx.activeKey == null || ctx.activeKey.isEmpty()) return;
        String clean = JsonSchemaManager.cleanKey(ctx.activeKey);
        JsonSchemaManager.SchemaProperty prop = resolveSchemaProperty(fileName, ctx.propertyPath, clean);
        if (prop != null) {
            if (prop.enumValues != null) {
                for (String ev : prop.enumValues) {
                    String insert = ctx.inQuotes ? ev : ("\"" + ev + "\"");
                    CompletionItem ci = new CompletionItem(ev, insert, "enum (" + prop.name + ")", CompletionItem.Type.VALUE, 0);
                    ci.setReplaceLength(ctx.word.length());
                    out.add(ci);
                    String lower = ev.toLowerCase(Locale.ROOT);
                    if (!lower.equals(ev)) {
                        String lowerInsert = ctx.inQuotes ? lower : ("\"" + lower + "\"");
                        CompletionItem lci = new CompletionItem(lower, lowerInsert, "enum (" + prop.name + ")", CompletionItem.Type.VALUE, 0);
                        lci.setReplaceLength(ctx.word.length());
                        out.add(lci);
                    }
                }
            } else if ("boolean".equals(prop.type)) {
                int rLen = (ctx.inQuotes ? 1 : 0) + ctx.word.length();
                CompletionItem t = new CompletionItem("true", "true", "Boolean", CompletionItem.Type.VALUE, 0);
                t.setReplaceLength(rLen);
                out.add(t);
                CompletionItem f = new CompletionItem("false", "false", "Boolean", CompletionItem.Type.VALUE, 0);
                f.setReplaceLength(rLen);
                out.add(f);
            }

            if (prop.isPath) {
                List<CompletionItem> paths = buildFileCompletions(ctx.word);
                if (paths != null) out.addAll(paths);
            }
        }
    }

    private void addSchemaArrayCompletions(String fileName, JsonContextScanner.JsonContext ctx, List<CompletionItem> out) {
        if (ctx.activeKey == null || ctx.activeKey.isEmpty()) return;
        String clean = JsonSchemaManager.cleanKey(ctx.activeKey);
        JsonSchemaManager.SchemaProperty prop = resolveSchemaProperty(fileName, ctx.propertyPath, clean);
        if (prop != null) {
            if (prop.enumValues != null) {
                for (String ev : prop.enumValues) {
                    String insert = ctx.inQuotes ? ev : ("\"" + ev + "\"");
                    CompletionItem ci = new CompletionItem(ev, insert, "enum (" + prop.name + ")", CompletionItem.Type.VALUE, 0);
                    ci.setReplaceLength(ctx.word.length());
                    out.add(ci);
                }
            }
            if (prop.isPath || "files".equals(clean) || "include".equals(clean) || "exclude".equals(clean)) {
                List<CompletionItem> paths = buildFileCompletions(ctx.word);
                if (paths != null) out.addAll(paths);
            }
        }
    }

    private void addSchemaKeyCompletions(String fileName, JsonContextScanner.JsonContext ctx, List<CompletionItem> out) {
        Map<String, JsonSchemaManager.SchemaProperty> schemaMap = null;
        if ("package.json".equals(fileName)) {
            if (ctx.propertyPath.isEmpty()) {
                schemaMap = JsonSchemaManager.getPackageProperties();
            } else if (ctx.propertyPath.contains("scripts")) {
                String[] scripts = {"build", "start", "test", "dev", "lint", "clean", "preview", "serve"};
                for (String s : scripts) {
                    if (!ctx.existingKeys.contains(s)) {
                        out.add(new CompletionItem(s, "\"" + s + "\": \"|\"", "Script", CompletionItem.Type.JSON_KEY, 0));
                    }
                }
            }
        } else if ("tsconfig.json".equals(fileName) || (fileName != null && fileName.startsWith("tsconfig") && fileName.endsWith(".json"))
                || "jsconfig.json".equals(fileName)) {
            if (ctx.propertyPath.isEmpty()) {
                schemaMap = JsonSchemaManager.getTsConfigRootProperties();
            } else if (ctx.propertyPath.contains("compilerOptions")) {
                schemaMap = JsonSchemaManager.getTsConfigCompilerOptions();
            }
        }

        if (schemaMap != null) {
            for (JsonSchemaManager.SchemaProperty prop : schemaMap.values()) {
                if (!ctx.existingKeys.contains(prop.name)) {
                    String insert = "\"" + prop.name + "\": " + (prop.defaultValue != null ? prop.defaultValue : "\"|\"");
                    out.add(new CompletionItem(prop.name, insert, prop.type, CompletionItem.Type.JSON_KEY, 0));
                }
            }
        } else if (fileName != null) {
            ensureSchemasLoaded();
            List<CompletionItem> schemaKeys = SCHEMA_KEYS.get(fileName);
            if (schemaKeys != null) {
                for (CompletionItem sk : schemaKeys) {
                    String clean = JsonSchemaManager.cleanKey(sk.getLabel());
                    if (!ctx.existingKeys.contains(clean)) {
                        out.add(new CompletionItem(sk.getLabel(), sk.getInsertText(), sk.getDetail(), sk.getType(), sk.getCursorOffset()));
                    }
                }
            }
        }
    }

    private JsonSchemaManager.SchemaProperty resolveSchemaProperty(String fileName, List<String> path, String key) {
        if ("package.json".equals(fileName)) {
            return JsonSchemaManager.getPackageProperties().get(key);
        } else if ("tsconfig.json".equals(fileName) || (fileName != null && fileName.startsWith("tsconfig") && fileName.endsWith(".json"))
                || "jsconfig.json".equals(fileName)) {
            if (path != null && path.contains("compilerOptions")) {
                return JsonSchemaManager.getTsConfigCompilerOptions().get(key);
            }
            JsonSchemaManager.SchemaProperty rootProp = JsonSchemaManager.getTsConfigRootProperties().get(key);
            if (rootProp != null) return rootProp;
            return JsonSchemaManager.getTsConfigCompilerOptions().get(key);
        }
        return null;
    }

    public List<CompletionItem> buildFileCompletions(String typedPath) {
        if (pathEngine == null) {
            pathEngine = new PathAutoCompleteEngine(context);
        }
        pathEngine.setCurrentFile(currentFile);
        return pathEngine.getSuggestionsForPath(typedPath, null);
    }

    private List<CompletionItem> getSpecificValueCompletions(String fileName, String keyName, boolean inQuotes) {
        if (keyName == null) return null;
        List<CompletionItem> list = new ArrayList<>();
        if ("package.json".equals(fileName)) {
            if ("type".equals(keyName)) {
                String[] types = {"module", "commonjs"};
                for (String t : types) {
                    list.add(new CompletionItem(t, inQuotes ? t : ("\"" + t + "\""), "Module Type", CompletionItem.Type.VALUE, 0));
                }
                return list;
            } else if ("private".equals(keyName) || "sideEffects".equals(keyName)) {
                list.add(new CompletionItem("true", "true", "Boolean", CompletionItem.Type.VALUE, 0));
                list.add(new CompletionItem("false", "false", "Boolean", CompletionItem.Type.VALUE, 0));
                return list;
            }
        }
        if ("tsconfig.json".equals(fileName) || (fileName != null && fileName.startsWith("tsconfig") && fileName.endsWith(".json"))) {
            if ("target".equals(keyName)) {
                String[] targets = {"ES2022", "ES2021", "ES2020", "ES2019", "ES2018", "ES2015", "ES6", "ESNext"};
                for (String t : targets) {
                    String insert = inQuotes ? t : ("\"" + t + "\"");
                    list.add(new CompletionItem(t, insert, "ECMAScript target", CompletionItem.Type.VALUE, 0));
                    String lower = t.toLowerCase(Locale.ROOT);
                    if (!lower.equals(t)) {
                        String lowerInsert = inQuotes ? lower : ("\"" + lower + "\"");
                        list.add(new CompletionItem(lower, lowerInsert, "ECMAScript target", CompletionItem.Type.VALUE, 0));
                    }
                }
                return list;
            } else if ("module".equals(keyName)) {
                String[] mods = {"ESNext", "NodeNext", "Node16", "CommonJS", "AMD", "System", "UMD"};
                for (String m : mods) {
                    String insert = inQuotes ? m : ("\"" + m + "\"");
                    list.add(new CompletionItem(m, insert, "Module system", CompletionItem.Type.VALUE, 0));
                    String lower = m.toLowerCase(Locale.ROOT);
                    if (!lower.equals(m)) {
                        String lowerInsert = inQuotes ? lower : ("\"" + lower + "\"");
                        list.add(new CompletionItem(lower, lowerInsert, "Module system", CompletionItem.Type.VALUE, 0));
                    }
                }
                return list;
            } else if ("moduleResolution".equals(keyName)) {
                String[] res = {"bundler", "node", "node16", "nodenext", "classic"};
                for (String r : res) {
                    String insert = inQuotes ? r : ("\"" + r + "\"");
                    list.add(new CompletionItem(r, insert, "Module resolution", CompletionItem.Type.VALUE, 0));
                }
                return list;
            } else if ("jsx".equals(keyName)) {
                String[] jsx = {"react-jsx", "react-jsxdev", "react", "preserve", "react-native"};
                for (String j : jsx) {
                    String insert = inQuotes ? j : ("\"" + j + "\"");
                    list.add(new CompletionItem(j, insert, "JSX mode", CompletionItem.Type.VALUE, 0));
                }
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
                String[] modes = {"standalone", "fullscreen", "minimal-ui", "browser"};
                for (String m : modes) {
                    String insert = inQuotes ? m : ("\"" + m + "\"");
                    list.add(new CompletionItem(m, insert, "Display mode", CompletionItem.Type.VALUE, 0));
                }
                return list;
            } else if ("orientation".equals(keyName)) {
                String[] orients = {"portrait", "landscape", "any", "natural"};
                for (String o : orients) {
                    String insert = inQuotes ? o : ("\"" + o + "\"");
                    list.add(new CompletionItem(o, insert, "Orientation", CompletionItem.Type.VALUE, 0));
                }
                return list;
            }
        }
        return null;
    }
}