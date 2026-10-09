package com.cocode.vcode.ide.core.language.json;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.language.base.BaseFormatter;
import com.cocode.vcode.ide.core.language.base.DomNode;
import com.cocode.vcode.ide.core.language.base.LanguageBackend;
import com.cocode.vcode.ide.core.language.base.SyntaxHighlighter;
import com.cocode.vcode.ide.core.language.base.SyntaxTreeDomAdapter;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.ServerCapabilities;
import com.cocode.vcode.ide.core.lsp.servers.JsonLspServer;
import com.cocode.vcode.ide.core.model.FileType;

/**
 * Language intelligence backend for JSON and JSONC.
 */
public class JsonLanguageBackend implements LanguageBackend {

    @Override
    public FileType getFileType() {
        return FileType.JSON;
    }

    @Override
    public String getLanguageId() {
        return "json";
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"json", "jsonc"};
    }

    @Override
    public SyntaxHighlighter createHighlighter(Context context) {
        return new JsonSyntaxHighlighter(context);
    }

    @Override
    public AutoCompleteEngine createAutoCompleteEngine(Context context) {
        return new JsonAutoCompleteEngine(context);
    }

    @Override
    public BaseFormatter getFormatter() {
        return new JsonFormatter();
    }

    @Override
    public LspServer createLspServer(Context context) {
        return new JsonLspServer(context);
    }

    @Override
    public ServerCapabilities getServerCapabilities() {
        return new ServerCapabilities.Builder()
                .setCompletionProvider(true)
                .setHoverProvider(true)
                .setDefinitionProvider(true)
                .setDocumentSymbolProvider(true)
                .setCodeActionProvider(true)
                .setFormattingProvider(true)
                .build();
    }

    @Override
    public DomNode parseToDom(CharSequence text) {
        String src = text != null ? text.toString() : "";
        JsonTokenStream stream = JsonLexer.tokenize(src);
        JsonSyntaxTree tree = JsonParser.parse(stream, src);
        return tree != null ? tree.toDomFile() : null;
    }
}
