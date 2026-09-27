package com.cocode.vcode.ide.core.language.html;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.language.base.BaseFormatter;
import com.cocode.vcode.ide.core.language.base.DomNode;
import com.cocode.vcode.ide.core.language.base.LanguageBackend;
import com.cocode.vcode.ide.core.language.base.SyntaxHighlighter;
import com.cocode.vcode.ide.core.language.base.SyntaxTreeDomAdapter;
import com.cocode.vcode.ide.core.language.js.ParseResult;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.ServerCapabilities;
import com.cocode.vcode.ide.core.lsp.servers.HtmlLspServer;
import com.cocode.vcode.ide.core.model.FileType;

/**
 * Language intelligence backend for HTML.
 */
public class HtmlLanguageBackend implements LanguageBackend {

    @Override
    public FileType getFileType() {
        return FileType.HTML;
    }

    @Override
    public String getLanguageId() {
        return "html";
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"html", "htm"};
    }

    @Override
    public SyntaxHighlighter createHighlighter(Context context) {
        return new HtmlSyntaxHighlighter(context);
    }

    @Override
    public AutoCompleteEngine createAutoCompleteEngine(Context context) {
        return new HtmlAutoCompleteEngine(context);
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
                .setDefinitionProvider(true)
                .setReferencesProvider(true)
                .setFormattingProvider(true)
                .setHoverProvider(true)
                .build();
    }

    @Override
    public DomNode parseToDom(CharSequence text) {
        ParseResult res = HtmlParser.parse(text != null ? text.toString() : "");
        return res != null && res.htmlTree != null ? res.htmlTree.toDomFile() : null;
    }
}
