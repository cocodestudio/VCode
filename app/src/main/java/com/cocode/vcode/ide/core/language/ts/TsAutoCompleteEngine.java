package com.cocode.vcode.ide.core.language.ts;

import android.content.Context;

import com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine;
import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.ArrayList;
import java.util.List;

/**
 * TypeScript-aware autocomplete engine.
 * Extends JsAutoCompleteEngine and prepends TS-specific type keywords so they rank
 * above generic JS suggestions when editing .ts / .tsx files.
 */
public class TsAutoCompleteEngine extends JsAutoCompleteEngine {

    private static final String[] TS_KEYWORDS = {
            "interface", "type", "enum", "namespace", "module", "declare", "abstract",
            "implements", "readonly", "override", "as", "satisfies", "asserts",
            "any", "unknown", "never", "void", "string", "number", "boolean",
            "bigint", "symbol", "object", "undefined", "null",
            "public", "private", "protected", "static",
            "keyof", "typeof", "infer", "extends", "is"
    };

    public TsAutoCompleteEngine(Context context) {
        super(context);
    }

    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        List<CompletionItem> base = super.getSuggestions(fullText, cursorPos);

        // Inject TS-specific keyword completions before the JS base results
        String word = getWordBeforeCursor(fullText, cursorPos);
        if (word.isEmpty()) return base;

        List<CompletionItem> tsItems = new ArrayList<>();
        String lower = word.toLowerCase();
        for (String kw : TS_KEYWORDS) {
            if (kw.startsWith(lower)) {
                tsItems.add(new CompletionItem(kw, kw, "TypeScript", CompletionItem.Type.KEYWORD, 100));
            }
        }

        // TS-specific suggestions go first, then JS base suggestions
        tsItems.addAll(base);
        return tsItems.size() > MAX_SUGGESTIONS ? tsItems.subList(0, MAX_SUGGESTIONS) : tsItems;
    }
}
