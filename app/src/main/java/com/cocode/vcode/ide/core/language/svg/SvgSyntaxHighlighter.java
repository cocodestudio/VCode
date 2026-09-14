package com.cocode.vcode.ide.core.language.svg;

import android.content.Context;

import com.cocode.vcode.ide.core.language.html.HtmlSyntaxHighlighter;

/**
 * SVG files are XML-based, so we reuse HtmlSyntaxHighlighter's tokenizer
 * which correctly handles tags, attributes, values and comments.
 */
public class SvgSyntaxHighlighter extends HtmlSyntaxHighlighter {
    public SvgSyntaxHighlighter(Context context) {
        super(context);
    }

    public static SvgSyntaxHighlighter forTest() {
        return new SvgSyntaxHighlighter((Void) null);
    }

    public static SvgSyntaxHighlighter forTestWithColors(int tag, int attribute, int value, int bracket, int comment) {
        return new SvgSyntaxHighlighter(tag, attribute, value, bracket, comment);
    }

    SvgSyntaxHighlighter(Void unusedForTest) {
        super((Void) null);
    }

    SvgSyntaxHighlighter(int tag, int attribute, int value, int bracket, int comment) {
        super(tag, attribute, value, bracket, comment);
    }
}
