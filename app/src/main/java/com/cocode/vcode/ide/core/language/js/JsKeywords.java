package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionItem;
import com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Centralized, shared repository for JavaScript and TypeScript keywords,
 * built-in identifiers, and booleans.
 * Replaces duplicated hardcoded sets in JsLexer, JsSemanticLinter,
 * JsSyntaxHighlighter, and TsSyntaxHighlighter.
 */
public final class JsKeywords {

    private static final Object lock = new Object();
    private static volatile boolean loaded = false;

    public static final Set<String> JS_KEYWORDS = new HashSet<>();
    public static final Set<String> TS_KEYWORDS = new HashSet<>(Arrays.asList(
            "type", "interface", "implements", "public", "private", "protected",
            "readonly", "enum", "declare", "namespace", "module", "any", "number",
            "boolean", "string", "symbol", "unknown", "never", "as", "is", "keyof",
            "infer", "abstract", "get", "set", "override", "satisfies", "asserts", "bigint"
    ));

    public static final Set<String> JS_BUILTINS = new HashSet<>(Arrays.asList(
            "console", "window", "document", "Math", "JSON", "Promise",
            "Object", "Array", "String", "Number", "Boolean", "RegExp",
            "Date", "Error", "Map", "Set", "Symbol", "globalThis"
    ));

    public static final Set<String> JS_BOOLEANS = new HashSet<>(Arrays.asList(
            "true", "false", "null", "undefined"
    ));

    public static final Set<String> ALL_JS_TS_KEYWORDS = new HashSet<>();

    static {
        ensureLoaded();
    }

    public static void ensureLoaded() {
        if (loaded) return;
        synchronized (lock) {
            if (loaded) return;
            loadKeywords();
            loaded = true;
        }
    }

    private static void loadKeywords() {
        Collections.addAll(JS_KEYWORDS,
                "await", "break", "case", "catch", "class", "const", "continue", "debugger",
                "default", "delete", "do", "else", "enum", "export", "extends", "false",
                "finally", "for", "function", "if", "import", "in", "instanceof", "new",
                "null", "return", "super", "switch", "this", "throw", "true", "try",
                "typeof", "var", "void", "while", "with", "yield", "let", "static", "async"
        );

        try {
            StaticCompletionItem[] items = StaticCompletionLoader.getJsKeywords();
            if (items != null) {
                for (StaticCompletionItem item : items) {
                    if (item.label != null && !item.label.contains(" ") && !item.label.contains(".")) {
                        JS_KEYWORDS.add(item.label);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        ALL_JS_TS_KEYWORDS.addAll(JS_KEYWORDS);
        ALL_JS_TS_KEYWORDS.addAll(TS_KEYWORDS);
    }

    public static boolean isJsKeyword(String word) {
        if (word == null) return false;
        ensureLoaded();
        return JS_KEYWORDS.contains(word);
    }

    public static boolean isTsKeyword(String word) {
        if (word == null) return false;
        return TS_KEYWORDS.contains(word);
    }

    private JsKeywords() {}
}
