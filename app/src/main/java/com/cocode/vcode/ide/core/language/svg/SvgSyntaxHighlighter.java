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
}
