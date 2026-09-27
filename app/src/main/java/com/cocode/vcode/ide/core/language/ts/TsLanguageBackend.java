package com.cocode.vcode.ide.core.language.ts;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.base.BaseFormatter;
import com.cocode.vcode.ide.core.language.base.DomNode;
import com.cocode.vcode.ide.core.language.base.LanguageBackend;
import com.cocode.vcode.ide.core.language.base.SyntaxHighlighter;
import com.cocode.vcode.ide.core.language.base.SyntaxTreeDomAdapter;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsParser;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.ServerCapabilities;
import com.cocode.vcode.ide.core.lsp.servers.TsLspServer;
import com.cocode.vcode.ide.core.model.FileType;

/**
 * Language intelligence backend for TypeScript.
 */
public class TsLanguageBackend implements LanguageBackend {

    @Override
    public FileType getFileType() {
        return FileType.TYPESCRIPT;
    }

    @Override
    public String getLanguageId() {
        return "typescript";
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"ts", "tsx"};
    }

    @Override
    public SyntaxHighlighter createHighlighter(Context context) {
        return new TsSyntaxHighlighter(context);
    }

    @Override
    public AutoCompleteEngine createAutoCompleteEngine(Context context) {
        return new TsAutoCompleteEngine(context);
    }

    @Override
    public BaseFormatter getFormatter() {
        return new TsFormatter();
    }

    @Override
    public LspServer createLspServer(Context context) {
        return new TsLspServer(context);
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
        return SyntaxTreeDomAdapter.fromJs(tree, FileType.TYPESCRIPT);
    }
}
