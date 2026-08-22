package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.js.JsLinter;
import com.cocode.vcode.ide.core.lsp.LspCompletionItem;

import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspRange;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-process Language Server for JavaScript files.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Completions</b>: Delegates to {@link JsAutoCompleteEngine}.</li>
 *   <li><b>Diagnostics</b>: Delegates to {@link JsLinter}.</li>
 *   <li><b>Go to Definition</b>: Resolves {@code import ... from './module'} paths,
 *       then falls back to {@link ProjectIndex} symbol lookup.</li>
 *   <li><b>Find References</b>: Symbol lookup via {@link ProjectIndex}.</li>
 *   <li><b>Signature Help</b>: Returns null (to be enhanced in a future phase).</li>
 * </ul>
 */
public final class JsLspServer implements LspServer {

    private static final Pattern IMPORT_FROM =
            Pattern.compile("import\\s+.*?from\\s+['\"]([^'\"]+)['\"]");

    // Member access completion table

    private static final Map<String, List<LspCompletionItem>> MEMBER_MAP;

    static {
        MEMBER_MAP = new HashMap<>();
        MEMBER_MAP.put("document", Arrays.asList(
                fn("getElementById", "getElementById(id)", "Returns element by ID"),
                fn("querySelector", "querySelector(selector)", "First matching element"),
                fn("querySelectorAll", "querySelectorAll(selector)", "NodeList of matches"),
                fn("createElement", "createElement(tag)", "Creates HTML element"),
                fn("createTextNode", "createTextNode(text)", "Creates text node"),
                fn("addEventListener", "addEventListener(type, handler)", "Adds event listener"),
                fn("removeEventListener", "removeEventListener(type, handler)", "Removes event listener"),
                fn("getElementsByClassName", "getElementsByClassName(name)", "Find by class name"),
                fn("getElementsByTagName", "getElementsByTagName(tag)", "Find by tag name"),
                fn("write", "write(content)", "Write to document"),
                fn("close", "close()", "Close document stream"),
                prop("body", "HTMLBodyElement"),
                prop("head", "HTMLHeadElement"),
                prop("title", "string"),
                prop("URL", "string \u2013 current URL"),
                prop("cookie", "string \u2013 cookies"),
                prop("readyState", "string \u2013 loading state"),
                prop("documentElement", "HTMLElement \u2013 root element")
        ));
        MEMBER_MAP.put("console", Arrays.asList(
                fn("log", "log(...data)", "General log"),
                fn("warn", "warn(...data)", "Warning log"),
                fn("error", "error(...data)", "Error log"),
                fn("info", "info(...data)", "Info log"),
                fn("debug", "debug(...data)", "Debug log"),
                fn("table", "table(data)", "Tabular output"),
                fn("group", "group(label)", "Start group"),
                fn("groupEnd", "groupEnd()", "End group"),
                fn("time", "time(label)", "Start timer"),
                fn("timeEnd", "timeEnd(label)", "Stop timer"),
                fn("count", "count(label)", "Count calls"),
                fn("clear", "clear()", "Clear console"),
                fn("assert", "assert(condition, msg)", "Assert condition"),
                fn("dir", "dir(obj)", "List properties")
        ));
        MEMBER_MAP.put("Math", Arrays.asList(
                fn("floor", "Math.floor(x)", "Round down"),
                fn("ceil", "Math.ceil(x)", "Round up"),
                fn("round", "Math.round(x)", "Round to nearest"),
                fn("random", "Math.random()", "Random 0\u20131"),
                fn("max", "Math.max(...values)", "Maximum value"),
                fn("min", "Math.min(...values)", "Minimum value"),
                fn("abs", "Math.abs(x)", "Absolute value"),
                fn("sqrt", "Math.sqrt(x)", "Square root"),
                fn("pow", "Math.pow(base, exp)", "Power"),
                fn("log", "Math.log(x)", "Natural log"),
                fn("log2", "Math.log2(x)", "Log base 2"),
                fn("log10", "Math.log10(x)", "Log base 10"),
                fn("sin", "Math.sin(x)", "Sine"),
                fn("cos", "Math.cos(x)", "Cosine"),
                fn("tan", "Math.tan(x)", "Tangent"),
                fn("sign", "Math.sign(x)", "Sign (-1/0/1)"),
                fn("trunc", "Math.trunc(x)", "Integer part"),
                fn("hypot", "Math.hypot(...values)", "Hypotenuse"),
                prop("PI", "number \u2013 3.14159..."),
                prop("E", "number \u2013 2.71828...")
        ));
        MEMBER_MAP.put("JSON", Arrays.asList(
                fn("parse", "JSON.parse(text)", "JSON string \u2192 object"),
                fn("stringify", "JSON.stringify(value, replacer, space)", "Object \u2192 JSON string")
        ));
        MEMBER_MAP.put("Object", Arrays.asList(
                fn("keys", "Object.keys(obj)", "Array of own property names"),
                fn("values", "Object.values(obj)", "Array of own values"),
                fn("entries", "Object.entries(obj)", "Array of [key, value] pairs"),
                fn("assign", "Object.assign(target, ...sources)", "Merge objects"),
                fn("freeze", "Object.freeze(obj)", "Make immutable"),
                fn("isFrozen", "Object.isFrozen(obj)", "Check if frozen"),
                fn("create", "Object.create(proto)", "Create with prototype"),
                fn("defineProperty", "Object.defineProperty(obj, prop, descriptor)", "Define property"),
                fn("getOwnPropertyNames", "Object.getOwnPropertyNames(obj)", "All own property names"),
                fn("fromEntries", "Object.fromEntries(entries)", "Entries \u2192 object"),
                fn("hasOwn", "Object.hasOwn(obj, key)", "Has own property")
        ));
        MEMBER_MAP.put("Array", Arrays.asList(
                fn("from", "Array.from(iterable)", "Create from iterable"),
                fn("isArray", "Array.isArray(value)", "Check if array"),
                fn("of", "Array.of(...items)", "Create from arguments")
        ));
        MEMBER_MAP.put("Promise", Arrays.asList(
                fn("resolve", "Promise.resolve(value)", "Fulfilled promise"),
                fn("reject", "Promise.reject(reason)", "Rejected promise"),
                fn("all", "Promise.all([...promises])", "Wait for all"),
                fn("allSettled", "Promise.allSettled([...promises])", "Wait for all, any outcome"),
                fn("race", "Promise.race([...promises])", "First to settle"),
                fn("any", "Promise.any([...promises])", "First to fulfill")
        ));
        List<LspCompletionItem> storageMembers = Arrays.asList(
                fn("getItem", "getItem(key)", "Read value"),
                fn("setItem", "setItem(key, value)", "Write value"),
                fn("removeItem", "removeItem(key)", "Delete entry"),
                fn("clear", "clear()", "Clear all"),
                fn("key", "key(index)", "Get key by index"),
                prop("length", "number \u2013 entries count")
        );
        MEMBER_MAP.put("localStorage", storageMembers);
        MEMBER_MAP.put("sessionStorage", storageMembers);
        MEMBER_MAP.put("window", Arrays.asList(
                fn("setTimeout", "setTimeout(fn, ms)", "Delayed call"),
                fn("setInterval", "setInterval(fn, ms)", "Repeating call"),
                fn("clearTimeout", "clearTimeout(id)", "Cancel timeout"),
                fn("clearInterval", "clearInterval(id)", "Cancel interval"),
                fn("fetch", "fetch(url, options)", "HTTP request"),
                fn("alert", "alert(message)", "Show alert"),
                fn("confirm", "confirm(message)", "Show confirm"),
                fn("prompt", "prompt(message, default)", "Show input"),
                fn("addEventListener", "addEventListener(type, handler)", "Listen to events"),
                fn("scrollTo", "scrollTo(x, y)", "Scroll to position"),
                fn("open", "open(url, target)", "Open window"),
                prop("localStorage", "Storage object"),
                prop("sessionStorage", "Storage object"),
                prop("location", "Location object"),
                prop("history", "History object"),
                prop("navigator", "Navigator object"),
                prop("document", "Document object"),
                prop("innerWidth", "number \u2013 viewport width"),
                prop("innerHeight", "number \u2013 viewport height"),
                prop("scrollX", "number \u2013 horizontal scroll"),
                prop("scrollY", "number \u2013 vertical scroll")
        ));
        // Inferred type: array variable (e.g. const arr = [])
        MEMBER_MAP.put("__array__", Arrays.asList(
                fn("push", "push(...items)", "Append items"),
                fn("pop", "pop()", "Remove last"),
                fn("shift", "shift()", "Remove first"),
                fn("unshift", "unshift(...items)", "Prepend items"),
                fn("map", "map(fn)", "Transform elements"),
                fn("filter", "filter(fn)", "Keep matching"),
                fn("reduce", "reduce(fn, initial)", "Accumulate"),
                fn("find", "find(fn)", "First matching"),
                fn("findIndex", "findIndex(fn)", "Index of first match"),
                fn("includes", "includes(value)", "Check membership"),
                fn("indexOf", "indexOf(value)", "Find index"),
                fn("forEach", "forEach(fn)", "Iterate"),
                fn("sort", "sort(compareFn)", "Sort in place"),
                fn("reverse", "reverse()", "Reverse in place"),
                fn("splice", "splice(start, count)", "Modify in place"),
                fn("slice", "slice(start, end)", "Extract subarray"),
                fn("join", "join(separator)", "Join as string"),
                fn("flat", "flat(depth)", "Flatten"),
                fn("flatMap", "flatMap(fn)", "Map then flatten"),
                fn("every", "every(fn)", "All match"),
                fn("some", "some(fn)", "Any match"),
                fn("fill", "fill(value, start, end)", "Fill range"),
                fn("concat", "concat(...arrays)", "Merge arrays"),
                fn("at", "at(index)", "Get by index (negative ok)"),
                fn("entries", "entries()", "[index, value] iterator"),
                fn("keys", "keys()", "Index iterator"),
                fn("values", "values()", "Value iterator"),
                prop("length", "number \u2013 array length")
        ));
        // Inferred type: string variable (e.g. const s = '')
        MEMBER_MAP.put("__string__", Arrays.asList(
                fn("split", "split(separator)", "Split to array"),
                fn("trim", "trim()", "Remove whitespace"),
                fn("trimStart", "trimStart()", "Remove leading whitespace"),
                fn("trimEnd", "trimEnd()", "Remove trailing whitespace"),
                fn("includes", "includes(search)", "Check contains"),
                fn("startsWith", "startsWith(prefix)", "Check prefix"),
                fn("endsWith", "endsWith(suffix)", "Check suffix"),
                fn("replace", "replace(from, to)", "Replace first"),
                fn("replaceAll", "replaceAll(from, to)", "Replace all"),
                fn("toUpperCase", "toUpperCase()", "Uppercase"),
                fn("toLowerCase", "toLowerCase()", "Lowercase"),
                fn("indexOf", "indexOf(search)", "Find index"),
                fn("substring", "substring(start, end)", "Extract substring"),
                fn("slice", "slice(start, end)", "Extract slice"),
                fn("charAt", "charAt(index)", "Get character"),
                fn("charCodeAt", "charCodeAt(index)", "Get char code"),
                fn("padStart", "padStart(len, char)", "Pad start"),
                fn("padEnd", "padEnd(len, char)", "Pad end"),
                fn("repeat", "repeat(count)", "Repeat string"),
                fn("match", "match(regex)", "Match against regex"),
                fn("search", "search(regex)", "Search with regex"),
                fn("at", "at(index)", "Get by index (negative ok)"),
                fn("normalize", "normalize(form)", "Unicode normalize"),
                prop("length", "number \u2013 character count")
        ));
    }

    private final JsAutoCompleteEngine autoCompleteEngine;
    private volatile boolean ready = false;

    public JsLspServer(Context context) {
        this.autoCompleteEngine = new JsAutoCompleteEngine(context);
    }

    public JsLspServer() {
        this(null);
    }

    private static LspCompletionItem fn(String label, String insert, String detail) {
        return new LspCompletionItem(label, insert, LspCompletionItem.KIND_FUNCTION, detail, null);
    }

    private static LspCompletionItem prop(String label, String detail) {
        return new LspCompletionItem(label, label, LspCompletionItem.KIND_PROPERTY, detail, null);
    }

    // -------------------------------------------------------------------------
    // LspServer contract
    // -------------------------------------------------------------------------

    private static LspLocation resolveModulePath(String docUri, String importPath) {
        File base = new File(docUri).getParentFile();
        if (base == null) return null;
        // Try exact path first
        File target = new File(base, importPath);
        if (target.exists() && target.isFile()) {
            return new LspLocation(target.getAbsolutePath(), new LspRange(0, 0, 0, 0));
        }
        // Try common JS/TS extensions
        for (String ext : new String[]{".js", ".ts", ".mjs", ".cjs", ".tsx"}) {
            File withExt = new File(base, importPath + ext);
            if (withExt.exists()) {
                return new LspLocation(withExt.getAbsolutePath(), new LspRange(0, 0, 0, 0));
            }
        }
        return null;
    }

    private static String extractWord(String text, int offset) {
        if (text == null || offset < 0 || offset > text.length()) return "";
        int start = Math.min(offset, text.length() - 1);
        while (start > 0 && isWordChar(text.charAt(start - 1))) start--;
        int end = offset;
        while (end < text.length() && isWordChar(text.charAt(end))) end++;
        return start < end ? text.substring(start, end) : "";
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static int mapKind(CompletionItem.Type type) {
        if (type == null) return LspCompletionItem.KIND_TEXT;
        switch (type) {
            case FUNCTION:
            case BUILTIN:
                return LspCompletionItem.KIND_FUNCTION;
            case KEYWORD:
                return LspCompletionItem.KIND_KEYWORD;
            case SNIPPET:
                return LspCompletionItem.KIND_SNIPPET;
            case VALUE:
                return LspCompletionItem.KIND_VALUE;
            case FILE:
                return LspCompletionItem.KIND_FILE;
            case FOLDER:
                return LspCompletionItem.KIND_FOLDER;
            default:
                return LspCompletionItem.KIND_TEXT;
        }
    }

    // -------------------------------------------------------------------------
    // Completions
    // -------------------------------------------------------------------------



    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------

    /**
     * Returns the text on the current line from the line start up to {@code offset}.
     */
    private static String getLineBeforeCursor(String text, int offset) {
        if (text == null || offset <= 0) return "";
        int lineStart = Math.min(offset, text.length());
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
        return text.substring(lineStart, Math.min(offset, text.length()));
    }

    // -------------------------------------------------------------------------
    // Go to Definition — returns single LspLocation or null
    // -------------------------------------------------------------------------

    /**
     * Extracts the identifier immediately before {@code idx} in {@code line}.
     */
    private static String extractWordBefore(String line, int end) {
        int start = end;
        while (start > 0 && isWordChar(line.charAt(start - 1))) start--;
        return line.substring(start, end);
    }

    // -------------------------------------------------------------------------
    // Find References
    // -------------------------------------------------------------------------

    /**
     * Simple pattern-based type inference.
     * {@code const arr = []} → "__array__"; {@code const s = ''} → "__string__".
     */
    private static String inferType(String varName, String text) {
        if (varName == null || varName.isEmpty() || text == null) return null;
        String quoted = Pattern.quote(varName);
        if (Pattern.compile("(?:const|let|var)\\s+" + quoted + "\\s*=\\s*\\[").matcher(text).find())
            return "__array__";
        if (Pattern.compile("(?:const|let|var)\\s+" + quoted + "\\s*=\\s*['\"`]").matcher(text).find())
            return "__string__";
        return null;
    }

    // -------------------------------------------------------------------------
    // Signature Help
    // -------------------------------------------------------------------------

    @Override
    public void initialize(ProjectIndex index) {
        ready = true;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    @Override
    public void shutdown() {
        ready = false;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String getLanguageId() {
        return "javascript";
    }

    @Override
    public List<LspCompletionItem> completion(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null) return Collections.emptyList();
        int offset = doc.toOffset(pos);
        if (offset < 0) offset = doc.text.length();

        // Member access completions (detect patterns like 'document.', 'console.', 'arr.')
        String lineBeforeCursor = getLineBeforeCursor(doc.text, offset);
        int dotIdx = lineBeforeCursor.lastIndexOf('.');
        if (dotIdx > 0) {
            String objectName = extractWordBefore(lineBeforeCursor, dotIdx);
            String prefix = lineBeforeCursor.substring(dotIdx + 1);
            List<LspCompletionItem> members = MEMBER_MAP.get(objectName);
            if (members == null) {
                // Type inference: 'const arr = []' → array methods
                String inferred = inferType(objectName, doc.text);
                if (inferred != null) members = MEMBER_MAP.get(inferred);
            }
            if (members != null) {
                List<LspCompletionItem> filtered = new ArrayList<>();
                for (LspCompletionItem item : members) {
                    if (prefix.isEmpty() || item.label.startsWith(prefix)) {
                        filtered.add(item);
                    }
                }
                if (!filtered.isEmpty()) return filtered;
            }
        }

        // --- Fall back to legacy engine for general keyword/scope completions ---
        autoCompleteEngine.setCurrentFile(new File(doc.uri));
        List<CompletionItem> suggestions = autoCompleteEngine.getSuggestions(doc.text, offset);
        if (suggestions == null) return Collections.emptyList();

        List<LspCompletionItem> result = new ArrayList<>(suggestions.size());
        for (CompletionItem item : suggestions) {
            String insert = item.getEffectiveInsertText();
            int curOffset = item.getCursorOffset();
            if (curOffset < 0) {
                int pipeIdx = insert.length() + curOffset;
                if (pipeIdx >= 0 && pipeIdx <= insert.length()) {
                    insert = insert.substring(0, pipeIdx) + "|" + insert.substring(pipeIdx);
                }
            }
            result.add(new LspCompletionItem(
                    item.getLabel(),
                    insert,
                    mapKind(item.getType()),
                    item.getDetail(),
                    null,
                    item.getReplaceLength()
            ));
        }
        return result;
    }

    @Override
    public List<Problem> diagnostics(LspDocument doc) {
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        File file = new File(doc.uri);
        List<Problem> problems = JsLinter.analyze(file, doc.text);
        return problems != null ? problems : Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Member-access detection helpers
    // -------------------------------------------------------------------------

    @Override
    public LspLocation definition(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;

        String lineText = doc.getLine(pos.line);
        if (lineText != null) {
            // Resolve `import ... from './module'`
            Matcher m = IMPORT_FROM.matcher(lineText);
            while (m.find()) {
                if (pos.character >= m.start() && pos.character <= m.end()) {
                    String importPath = m.group(1);
                    LspLocation resolved = resolveModulePath(doc.uri, importPath);
                    if (resolved != null) return resolved;
                }
            }
        }

        // Fall back to project-wide symbol lookup
        int offset = doc.toOffset(pos);
        String word = extractWord(doc.text, offset >= 0 ? offset : 0);
        if (word.isEmpty()) return null;

        List<LspLocation> defs = ProjectIndex.getInstance().findDefinitions(word);
        return (defs != null && !defs.isEmpty()) ? defs.get(0) : null;
    }

    @Override
    public List<LspLocation> references(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return Collections.emptyList();
        int offset = doc.toOffset(pos);
        String word = extractWord(doc.text, offset >= 0 ? offset : 0);
        if (word.isEmpty()) return Collections.emptyList();

        return findUsagesInProject(word);
    }

    private List<LspLocation> findUsagesInProject(String word) {
        List<LspLocation> result = new ArrayList<>();
        ProjectIndex projectIndex = ProjectIndex.getInstance();
        List<LspLocation> defs = projectIndex.findDefinitions(word);
        
        for (String uri : projectIndex.getAllUris()) {
            LspDocument d = projectIndex.getDocument(uri);
            if (d == null || d.text == null) continue;

            if (uri.endsWith(".js") || uri.endsWith(".ts") || uri.endsWith(".jsx") || uri.endsWith(".tsx")) {
                Pattern p = Pattern.compile("\\b" + Pattern.quote(word) + "\\b");
                Matcher m = p.matcher(d.text);
                while (m.find()) {
                    LspPosition start = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(d.text, m.start());
                    LspPosition end = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(d.text, m.end());
                    LspRange range = new LspRange(start, end);
                    LspLocation loc = new LspLocation(uri, range);
                    
                    boolean isDef = false;
                    for (LspLocation def : defs) {
                        if (def.uri.equals(uri) && def.range.start.line == range.start.line && def.range.start.character == range.start.character) {
                            isDef = true;
                            break;
                        }
                    }
                    if (!isDef) {
                        result.add(loc);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos) {
        return JsSignatureParser.parse(doc, pos);
    }
}
