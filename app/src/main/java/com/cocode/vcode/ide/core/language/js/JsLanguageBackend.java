package com.cocode.vcode.ide.core.language.js;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.base.BaseFormatter;
import com.cocode.vcode.ide.core.language.base.DomNode;
import com.cocode.vcode.ide.core.language.base.LanguageBackend;
import com.cocode.vcode.ide.core.language.base.SyntaxHighlighter;
import com.cocode.vcode.ide.core.language.base.SyntaxTreeDomAdapter;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.ServerCapabilities;
import com.cocode.vcode.ide.core.lsp.servers.JsLspServer;
import com.cocode.vcode.ide.core.model.FileType;

/**
 * Language intelligence backend for JavaScript.
 */
public class JsLanguageBackend implements LanguageBackend {

    @Override
    public FileType getFileType() {
        return FileType.JAVASCRIPT;
    }

    @Override
    public String getLanguageId() {
        return "javascript";
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"js", "mjs", "cjs"};
    }

    @Override
    public SyntaxHighlighter createHighlighter(Context context) {
        return new JsSyntaxHighlighter(context);
    }

    @Override
    public AutoCompleteEngine createAutoCompleteEngine(Context context) {
        return new JsAutoCompleteEngine(context);
    }

    @Override
    public BaseFormatter getFormatter() {
        return new JsFormatter();
    }

    @Override
    public LspServer createLspServer(Context context) {
        return new JsLspServer(context);
    }

    @Override
    public ServerCapabilities getServerCapabilities() {
        return ServerCapabilities.DEFAULT_WEB;
    }

    @Override
    public DomNode parseToDom(CharSequence text) {
        String src = text != null ? text.toString() : "";
        TokenStream stream = JsLexer.tokenize(src);
        JsSyntaxTree tree = JsParser.parseFull(src, stream);
        return tree != null ? tree.toDomFile() : null;
    }
}
