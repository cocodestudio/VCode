package com.cocode.vcode.ide.core.language.ts;

import android.content.Context;

import com.cocode.vcode.ide.core.language.js.JsKeywords;
import com.cocode.vcode.ide.core.language.js.JsSyntaxHighlighter;

/**
 * Syntax highlighter for TypeScript source files.
 */
public class TsSyntaxHighlighter extends JsSyntaxHighlighter {

    public TsSyntaxHighlighter(Context context) {
        super(context);
    }

    TsSyntaxHighlighter(Void unusedForTest) {
        super((Void) null);
    }

    TsSyntaxHighlighter(int comment, int string, int keyword, int number, int function, int booleanCol, int operator) {
        super(comment, string, keyword, number, function, booleanCol, operator);
    }

    public static TsSyntaxHighlighter forTest() {
        return new TsSyntaxHighlighter((Void) null);
    }

    public static TsSyntaxHighlighter forTestWithColors(int comment, int string, int keyword, int number, int function, int booleanCol, int operator) {
        return new TsSyntaxHighlighter(comment, string, keyword, number, function, booleanCol, operator);
    }

    @Override
    public boolean isKeyword(CharSequence cs, int start, int end) {
        if (super.isKeyword(cs, start, end)) return true;
        int len = end - start;
        if (len < 2 || len > 10) return false;
        char c0 = cs.charAt(start);
        switch (len) {
            case 2:
                return (c0 == 'a' && match(cs, start, end, "as")) || (c0 == 'i' && match(cs, start, end, "is"));
            case 3:
                return (c0 == 'a' && match(cs, start, end, "any")) || (c0 == 'g' && match(cs, start, end, "get")) || (c0 == 's' && match(cs, start, end, "set"));
            case 4:
                return (c0 == 't' && match(cs, start, end, "type")) || (c0 == 'e' && match(cs, start, end, "enum"));
            case 5:
                return (c0 == 'i' && match(cs, start, end, "infer")) || (c0 == 'n' && match(cs, start, end, "never")) || (c0 == 'k' && match(cs, start, end, "keyof"));
            case 6:
                return (c0 == 'p' && match(cs, start, end, "public"))
                        || (c0 == 'm' && match(cs, start, end, "module"))
                        || (c0 == 'n' && match(cs, start, end, "number"))
                        || (c0 == 'o' && match(cs, start, end, "object"))
                        || (c0 == 's' && (match(cs, start, end, "string") || match(cs, start, end, "symbol")))
                        || (c0 == 'b' && match(cs, start, end, "bigint"));
            case 7:
                return (c0 == 'p' && match(cs, start, end, "private")) || (c0 == 'd' && match(cs, start, end, "declare")) || (c0 == 'b' && match(cs, start, end, "boolean")) || (c0 == 'u' && match(cs, start, end, "unknown")) || (c0 == 'a' && match(cs, start, end, "asserts"));
            case 8:
                return (c0 == 'r' && match(cs, start, end, "readonly")) || (c0 == 'a' && match(cs, start, end, "abstract")) || (c0 == 'o' && match(cs, start, end, "override"));
            case 9:
                return (c0 == 'i' && match(cs, start, end, "interface")) || (c0 == 'p' && match(cs, start, end, "protected")) || (c0 == 'n' && match(cs, start, end, "namespace")) || (c0 == 's' && match(cs, start, end, "satisfies"));
            case 10:
                return match(cs, start, end, "implements");
            default:
                break;
        }
        return JsKeywords.isTsKeyword(cs.subSequence(start, end).toString());
    }

    @Override
    protected boolean isKeyword(String word) {
        if (word == null) return false;
        return super.isKeyword(word) || JsKeywords.isTsKeyword(word);
    }
}
