package com.cocode.vcode.ide.core.language.ts;

import android.content.Context;

import com.cocode.vcode.ide.core.language.js.JsSyntaxHighlighter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Syntax highlighter for TypeScript source files.
 */
public class TsSyntaxHighlighter extends JsSyntaxHighlighter {

    private static final Set<String> TS_KEYWORDS = new HashSet<>(Arrays.asList(
            "type", "interface", "implements", "public", "private", "protected",
            "readonly", "enum", "declare", "namespace", "module", "any", "number",
            "boolean", "string", "symbol", "unknown", "never", "as", "is", "keyof",
            "infer", "abstract", "get", "set"
    ));

    public TsSyntaxHighlighter(Context context) {
        super(context);
    }

    @Override
    protected boolean isKeyword(String word) {
        return super.isKeyword(word) || TS_KEYWORDS.contains(word);
    }
}
