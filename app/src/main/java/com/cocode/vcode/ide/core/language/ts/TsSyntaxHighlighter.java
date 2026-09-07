package com.cocode.vcode.ide.core.language.ts;

import android.content.Context;

import com.cocode.vcode.ide.core.language.js.JsKeywords;
import com.cocode.vcode.ide.core.language.js.JsSyntaxHighlighter;

import java.util.Set;

/**
 * Syntax highlighter for TypeScript source files.
 */
public class TsSyntaxHighlighter extends JsSyntaxHighlighter {

    private static final Set<String> TS_KEYWORDS = JsKeywords.TS_KEYWORDS;

    public TsSyntaxHighlighter(Context context) {
        super(context);
    }

    public static TsSyntaxHighlighter forTest() {
        return new TsSyntaxHighlighter((Void) null);
    }

    public static TsSyntaxHighlighter forTestWithColors(int comment, int string, int keyword, int number, int function, int booleanCol, int operator) {
        return new TsSyntaxHighlighter(comment, string, keyword, number, function, booleanCol, operator);
    }

    TsSyntaxHighlighter(Void unusedForTest) {
        super((Void) null);
    }

    TsSyntaxHighlighter(int comment, int string, int keyword, int number, int function, int booleanCol, int operator) {
        super(comment, string, keyword, number, function, booleanCol, operator);
    }

    @Override
    protected boolean isKeyword(String word) {
        return super.isKeyword(word) || TS_KEYWORDS.contains(word);
    }
}
