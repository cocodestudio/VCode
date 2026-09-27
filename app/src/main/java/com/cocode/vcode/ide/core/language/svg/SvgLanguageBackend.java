package com.cocode.vcode.ide.core.language.svg;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.language.base.BaseFormatter;
import com.cocode.vcode.ide.core.language.base.DomNode;
import com.cocode.vcode.ide.core.language.base.LanguageBackend;
import com.cocode.vcode.ide.core.language.base.SyntaxHighlighter;
import com.cocode.vcode.ide.core.language.base.SyntaxTreeDomAdapter;
import com.cocode.vcode.ide.core.language.html.HtmlFormatter;
import com.cocode.vcode.ide.core.language.html.HtmlParser;
import com.cocode.vcode.ide.core.language.js.ParseResult;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.ServerCapabilities;
import com.cocode.vcode.ide.core.lsp.servers.HtmlLspServer;
import com.cocode.vcode.ide.core.model.FileType;

/**
 * Language intelligence backend for SVG graphics.
 */
public class SvgLanguageBackend implements LanguageBackend {

    @Override
    public FileType getFileType() {
        return FileType.SVG;
    }

    @Override
    public String getLanguageId() {
        return "svg";
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"svg"};
    }

    @Override
    public SyntaxHighlighter createHighlighter(Context context) {
        return new SvgSyntaxHighlighter(context);
    }

    @Override
    public AutoCompleteEngine createAutoCompleteEngine(Context context) {
        return new SvgAutoCompleteEngine(context);
    }

    @Override
    public BaseFormatter getFormatter() {
        return new HtmlFormatter();
    }

    @Override
    public LspServer createLspServer(Context context) {
        return new HtmlLspServer(context);
    }

    @Override
    public ServerCapabilities getServerCapabilities() {
        return new ServerCapabilities.Builder()
                .setCompletionProvider(true)
                .setFormattingProvider(true)
                .build();
    }

    @Override
    public DomNode parseToDom(CharSequence text) {
        ParseResult res = HtmlParser.parse(text != null ? text.toString() : "");
        return SyntaxTreeDomAdapter.fromHtml(res.htmlTree);
    }
}
