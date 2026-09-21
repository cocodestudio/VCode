package com.cocode.vcode.ide.core.language.ts;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex;
import com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine;
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
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length())
            return new ArrayList<>();

        ensureDocumentIndexed(fullText);

        String word = getWordBeforeCursor(fullText, cursorPos);

        // Check if cursor is after '.' (member access)
        int dotCheckPos = cursorPos - word.length() - 1;
        boolean isMemberAccess = (dotCheckPos >= 0 && fullText.charAt(dotCheckPos) == '.')
                || (dotCheckPos >= 1 && fullText.charAt(dotCheckPos) == '.' && fullText.charAt(dotCheckPos - 1) == '?');

        // If after dot, delegate entirely to member completions
        if (isMemberAccess) {
            return super.getSuggestions(fullText, cursorPos);
        }

        // Check if cursor is in a type annotation context
        if (isTypeAnnotationPosition(fullText, cursorPos, word)) {
            return getTypeAnnotationSuggestions(fullText, cursorPos, word);
        }

        List<CompletionItem> base = super.getSuggestions(fullText, cursorPos);
        if (word.isEmpty()) return base;

        List<CompletionItem> tsItems = new ArrayList<>();
        String lower = word.toLowerCase();
        for (String kw : JsKeywords.TS_KEYWORDS) {
            if (kw.startsWith(lower)) {
                CompletionItem item = new CompletionItem(kw, kw, "TypeScript", CompletionItem.Type.KEYWORD, 0);
                item.setSortScore(100);
                item.setReplaceLength(word.length());
                tsItems.add(item);
            }
        }

        // TS-specific suggestions go first, then JS base suggestions
        tsItems.addAll(base);
        return tsItems.size() > MAX_SUGGESTIONS ? tsItems.subList(0, MAX_SUGGESTIONS) : tsItems;
    }

    /**
     * Checks if the cursor is at a position expecting a type annotation, such as after
     * a colon, generic bracket, union/intersection operator, type alias assignment,
     * or type assertion keyword.
     */
    public static boolean isTypeAnnotationPosition(String text, int cursorPos, String word) {
        if (text == null || cursorPos <= 0 || cursorPos > text.length()) return false;

        int idx = cursorPos - word.length() - 1;
        while (idx >= 0 && Character.isWhitespace(text.charAt(idx))) {
            idx--;
        }
        if (idx < 0) return false;

        char c = text.charAt(idx);

        // Union or intersection ('A | ' or 'A & ')
        if (c == '|' || c == '&') {
            if (idx > 0 && text.charAt(idx - 1) == c) return false;
            return true;
        }

        // Generic type argument ('Promise<' or 'Map<string, ')
        if (c == '<' || c == ',') {
            int depth = 0;
            for (int j = idx; j >= 0; j--) {
                char ch = text.charAt(j);
                if (ch == '>') depth++;
                else if (ch == '<') {
                    if (depth > 0) depth--;
                    else return true;
                } else if (ch == ';' || ch == '{' || ch == '}' || ch == '\n') {
                    break;
                }
            }
        }

        // Type assertion or heritage keywords ('as', 'is', 'extends', 'implements')
        int wordEnd = idx + 1;
        int wordStart = idx;
        while (wordStart >= 0 && Character.isJavaIdentifierPart(text.charAt(wordStart))) {
            wordStart--;
        }
        wordStart++;
        if (wordStart < wordEnd) {
            String prevWord = text.substring(wordStart, wordEnd);
            if ("as".equals(prevWord) || "is".equals(prevWord) ||
                    "extends".equals(prevWord) || "implements".equals(prevWord)) {
                return true;
            }
        }

        // Type alias assignment ('type Foo = ')
        if (c == '=') {
            int lineStart = idx;
            while (lineStart > 0 && text.charAt(lineStart - 1) != '\n' && text.charAt(lineStart - 1) != ';') {
                lineStart--;
            }
            String stmt = text.substring(lineStart, idx).trim();
            if (stmt.matches(".*\\btype\\s+[A-Za-z0-9_$]+(\\s*<[^>]*>)?\\s*")) {
                return true;
            }
        }

        // Variable, parameter, or return type annotation
        if (c == ':') {
            int lineStart = idx;
            while (lineStart > 0 && text.charAt(lineStart - 1) != '\n' && text.charAt(lineStart - 1) != ';') {
                lineStart--;
            }
            String stmt = text.substring(lineStart, idx).trim();
            if (stmt.startsWith("case ") || stmt.startsWith("default:")) {
                return false;
            }
            return true;
        }

        return false;
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
}
