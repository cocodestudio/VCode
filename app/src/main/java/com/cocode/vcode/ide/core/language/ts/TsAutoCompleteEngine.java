package com.cocode.vcode.ide.core.language.ts;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex;
import com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.js.JsContextScanner;
import com.cocode.vcode.ide.core.language.js.JsKeywords;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Autocomplete engine for TypeScript and TSX files.
 *
 * Handles type annotation completions (primitive types, utility types, and local/project symbols)
 * in type positions such as variable annotations, generic arguments, unions, and type aliases,
 * while delegating member accesses and standard JS expressions to {@link JsAutoCompleteEngine}.
 */
public class TsAutoCompleteEngine extends JsAutoCompleteEngine {

    private static final String[] TS_PRIMITIVES = {
            "string", "number", "boolean", "any", "unknown", "void", "never",
            "object", "symbol", "bigint", "null", "undefined"
    };

    private static final String[] TS_UTILITY_TYPES = {
            "Array", "Promise", "Record", "Partial", "Readonly", "Required",
            "Pick", "Omit", "Exclude", "Extract", "NonNullable", "ReturnType",
            "InstanceType", "Parameters", "Map", "Set", "Date", "RegExp",
            "Function", "Error", "HTMLElement", "Element", "Node", "Document",
            "Event", "MouseEvent", "KeyboardEvent", "TouchEvent", "FocusEvent",
            "PointerEvent", "Response", "Request", "Headers", "FormData", "URL",
            "URLSearchParams"
    };

    public TsAutoCompleteEngine(Context context) {
        super(context);
    }

    @Override
    public String getTriggerCharacters() {
        return ".'\"`/@({*:<";
    }

    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length())
            return new ArrayList<>();

        ensureDocumentIndexed(fullText);

        JsContextScanner.JsContext ctx = JsContextScanner.scan(fullText, cursorPos, true);

        // 1. Suppress inside regular string literals and comments
        if (ctx.type == JsContextScanner.ContextType.STRING_OR_COMMENT) {
            return Collections.emptyList();
        }

        // 2. Import / require module path completion (relative project files or bare npm packages)
        if (ctx.type == JsContextScanner.ContextType.IMPORT_PATH) {
            if (ctx.isRelativeImport) {
                return buildFileCompletions(ctx.importPath);
            } else {
                List<CompletionItem> bare = buildBareImportSuggestions(ctx.importPath);
                if (bare != null && !bare.isEmpty()) return bare;
                return buildFileCompletions(ctx.importPath);
            }
        }

        // 3. Named import and export clause completions
        if (ctx.type == JsContextScanner.ContextType.IMPORT_CLAUSE || ctx.type == JsContextScanner.ContextType.IMPORT_TYPE) {
            List<CompletionItem> importExport = getImportExportSuggestions(fullText, cursorPos, ctx.word);
            if (importExport != null) return importExport;
        }

        // 4. Dot-member access completions (strictly isolated to object members)
        if (ctx.type == JsContextScanner.ContextType.DOT_MEMBER) {
            return super.getSuggestions(fullText, cursorPos);
        }

        // 5. TypeScript type annotation, generic type argument, or type assertion positions
        if (ctx.type == JsContextScanner.ContextType.TYPE_ANNOTATION
                || ctx.type == JsContextScanner.ContextType.GENERIC_TYPE
                || ctx.type == JsContextScanner.ContextType.TYPE_ASSERTION) {
            return getTypeAnnotationSuggestions(fullText, cursorPos, ctx.word);
        }

        // 6. Interface or type alias body member positions
        if (ctx.type == JsContextScanner.ContextType.INTERFACE_MEMBER) {
            return Collections.emptyList();
        }

        // 7. Check if cursor is inside an object literal conforming to an interface
        List<CompletionItem> ifaceProps = getInterfacePropertySuggestions(fullText, cursorPos, ctx.word);
        if (ifaceProps != null && !ifaceProps.isEmpty()) {
            return ifaceProps;
        }

        // 8. Event name string completions
        if (ctx.type == JsContextScanner.ContextType.EVENT_NAME) {
            return super.getSuggestions(fullText, cursorPos);
        }

        // 9. DOM getElementById ID completions
        if (ctx.type == JsContextScanner.ContextType.GET_ELEMENT_BY_ID) {
            return super.getSuggestions(fullText, cursorPos);
        }

        // 10. DOM querySelector CSS selector completions
        if (ctx.type == JsContextScanner.ContextType.DOM_SELECTOR) {
            return super.getSuggestions(fullText, cursorPos);
        }

        // 11. Fallback check for type annotation position
        if (isTypeAnnotationPosition(fullText, cursorPos, ctx.word)) {
            return getTypeAnnotationSuggestions(fullText, cursorPos, ctx.word);
        }

        // 12. Lexical scope, globals and keywords
        List<CompletionItem> base = super.getSuggestions(fullText, cursorPos);
        if (ctx.word.isEmpty()) return base;

        List<CompletionItem> tsItems = new ArrayList<>();
        String lower = ctx.word.toLowerCase();
        for (String kw : JsKeywords.TS_KEYWORDS) {
            if (kw.startsWith(lower)) {
                CompletionItem item = new CompletionItem(kw, kw, "TypeScript", CompletionItem.Type.KEYWORD, 0);
                item.setSortScore(100);
                item.setReplaceLength(ctx.word.length());
                tsItems.add(item);
            }
        }

        tsItems.addAll(base);
        Map<String, CompletionItem> dedup = new LinkedHashMap<>();
        for (CompletionItem ci : tsItems) {
            if (ci.getLabel() == null) continue;
            CompletionItem existing = dedup.get(ci.getLabel());
            if (existing == null) {
                dedup.put(ci.getLabel(), ci);
            } else if (existing.getType() == CompletionItem.Type.KEYWORD && "TypeScript".equals(existing.getDetail())) {
                if (ci.getType() == CompletionItem.Type.VALUE && "Variable".equals(ci.getDetail())) {
                    dedup.put(ci.getLabel(), ci);
                }
            } else if (ci.getSortScore() > existing.getSortScore()) {
                dedup.put(ci.getLabel(), ci);
            }
        }
        List<CompletionItem> result = new ArrayList<>(dedup.values());
        Collections.sort(result, (a, b) -> {
            int scoreDiff = b.getSortScore() - a.getSortScore();
            if (scoreDiff != 0) return scoreDiff;
            return b.getTypePriority() - a.getTypePriority();
        });
        return result.size() > MAX_SUGGESTIONS ? result.subList(0, MAX_SUGGESTIONS) : result;
    }

    /**
     * Checks if the cursor is at a position expecting a type annotation, delegating
     * to {@link JsContextScanner#isTypeAnnotationPosition(String, int, String)}.
     */
    public static boolean isTypeAnnotationPosition(String text, int cursorPos, String word) {
        return JsContextScanner.isTypeAnnotationPosition(text, cursorPos, word);
    }

    private List<CompletionItem> getTypeAnnotationSuggestions(String fullText, int cursorPos, String word) {
        Map<String, CompletionItem> items = new LinkedHashMap<>();

        // Document symbols (interfaces, enums, type aliases, classes)
        JsSyntaxTree tree = getCachedTree();
        if (tree != null) {
            for (int i = 1; i < tree.nodeCount; i++) {
                int id = tree.nodesByOffset != null && i < tree.nodesByOffset.length ? tree.nodesByOffset[i] : i;
                int type = tree.nodeType[id];
                if (type == JsSyntaxTree.N_INTERFACE || type == JsSyntaxTree.N_ENUM ||
                        type == JsSyntaxTree.N_TYPE_ALIAS || type == JsSyntaxTree.N_CLASS_DECL) {
                    String name = tree.nodeName[id];
                    if (name != null && !name.isEmpty() && !items.containsKey(name)) {
                        String desc = type == JsSyntaxTree.N_INTERFACE ? "Interface" :
                                (type == JsSyntaxTree.N_ENUM ? "Enum" :
                                        (type == JsSyntaxTree.N_TYPE_ALIAS ? "Type Alias" : "Class"));
                        CompletionItem ci = new CompletionItem(name, name, desc, CompletionItem.Type.VALUE, 0);
                        ci.setSortScore(100);
                        ci.setReplaceLength(word.length());
                        items.put(name, ci);
                    }
                }
            }
        }

        // Project-wide symbols from index
        Set<String> projectClasses = ProjectSymbolIndex.getInstance().getAllClassNames();
        if (projectClasses != null) {
            for (String cls : projectClasses) {
                if (cls != null && !cls.isEmpty() && !items.containsKey(cls)) {
                    CompletionItem ci = new CompletionItem(cls, cls, "Type", CompletionItem.Type.VALUE, 0);
                    ci.setSortScore(90);
                    ci.setReplaceLength(word.length());
                    items.put(cls, ci);
                }
            }
        }

        // Built-in primitives
        for (String prim : TS_PRIMITIVES) {
            if (!items.containsKey(prim)) {
                CompletionItem ci = new CompletionItem(prim, prim, "Primitive Type", CompletionItem.Type.KEYWORD, 0);
                ci.setSortScore(80);
                ci.setReplaceLength(word.length());
                items.put(prim, ci);
            }
        }

        // Utility and DOM types
        for (String util : TS_UTILITY_TYPES) {
            if (!items.containsKey(util)) {
                CompletionItem ci = new CompletionItem(util, util, "Type", CompletionItem.Type.BUILTIN, 0);
                ci.setSortScore(70);
                ci.setReplaceLength(word.length());
                items.put(util, ci);
            }
        }

        List<CompletionItem> candidateList = new ArrayList<>(items.values());
        if (word.isEmpty()) {
            return candidateList.size() > MAX_SUGGESTIONS ? candidateList.subList(0, MAX_SUGGESTIONS) : candidateList;
        }

        return fuzzyFilter(candidateList, word);
    }

    private List<CompletionItem> getInterfacePropertySuggestions(String fullText, int cursorPos, String word) {
        int openBrace = -1;
        int depth = 0;
        for (int i = cursorPos - word.length() - 1; i >= 0; i--) {
            char c = fullText.charAt(i);
            if (c == '}') depth++;
            else if (c == '{') {
                if (depth > 0) depth--;
                else {
                    openBrace = i;
                    break;
                }
            } else if (c == ';') {
                break;
            }
        }
        if (openBrace <= 0) return null;

        int scan = openBrace - 1;
        while (scan >= 0 && Character.isWhitespace(fullText.charAt(scan))) scan--;
        if (scan < 0 || fullText.charAt(scan) != '=') return null;
        scan--;
        while (scan >= 0 && Character.isWhitespace(fullText.charAt(scan))) scan--;
        int typeEnd = scan + 1;
        while (scan >= 0 && (Character.isJavaIdentifierPart(fullText.charAt(scan)) || fullText.charAt(scan) == '<' || fullText.charAt(scan) == '>')) scan--;
        if (scan < 0 || scan >= typeEnd) return null;
        String typeName = fullText.substring(scan + 1, typeEnd).trim();
        int angle = typeName.indexOf('<');
        if (angle >= 0) typeName = typeName.substring(0, angle).trim();
        if (typeName.isEmpty()) return null;

        int beforeType = scan;
        while (beforeType >= 0 && Character.isWhitespace(fullText.charAt(beforeType))) beforeType--;
        if (beforeType < 0 || fullText.charAt(beforeType) != ':') return null;

        JsSyntaxTree tree = getCachedTree();
        if (tree == null) return null;
        int ifaceNode = TsParser.findInterfaceOrType(tree, typeName);
        if (ifaceNode <= 0 || tree.nodeType[ifaceNode] != JsSyntaxTree.N_INTERFACE) return null;

        List<CompletionItem> results = new ArrayList<>();
        String lower = word.toLowerCase();
        int child = tree.nodeChild[ifaceNode];
        while (child > 0 && child < tree.nodeCount) {
            if (tree.nodeType[child] == JsSyntaxTree.N_PROPERTY || tree.nodeType[child] == JsSyntaxTree.N_METHOD) {
                String pName = tree.nodeName[child];
                if (pName != null && !pName.isEmpty()) {
                    String cleanName = pName.endsWith("?") ? pName.substring(0, pName.length() - 1) : pName;
                    if (cleanName.toLowerCase().startsWith(lower)) {
                        String detail = tree.nodeTypeAnn[child] != null ? tree.nodeTypeAnn[child] : "Property";
                        CompletionItem ci = new CompletionItem(cleanName, cleanName + ": |", detail, CompletionItem.Type.VALUE, 0);
                        ci.setSortScore(120);
                        ci.setReplaceLength(word.length());
                        results.add(ci);
                    }
                }
            }
            child = tree.nodeSibling[child];
        }
        return results;
    }
}
