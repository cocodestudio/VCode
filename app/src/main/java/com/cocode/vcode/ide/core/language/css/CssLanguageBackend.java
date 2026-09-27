package com.cocode.vcode.ide.core.language.css;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.language.base.BaseFormatter;
import com.cocode.vcode.ide.core.language.base.DomNode;
import com.cocode.vcode.ide.core.language.base.LanguageBackend;
import com.cocode.vcode.ide.core.language.base.SyntaxHighlighter;
import com.cocode.vcode.ide.core.language.base.SyntaxTreeDomAdapter;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.ServerCapabilities;
import com.cocode.vcode.ide.core.lsp.servers.CssLspServer;
import com.cocode.vcode.ide.core.model.FileType;

/**
 * Language intelligence backend for CSS and SCSS.
 */
public class CssLanguageBackend implements LanguageBackend {

    @Override
    public FileType getFileType() {
        return FileType.CSS;
    }

    @Override
    public String getLanguageId() {
        return "css";
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"css", "scss", "less"};
    }

    @Override
    public SyntaxHighlighter createHighlighter(Context context) {
        return new CssSyntaxHighlighter(context);
    }

    @Override
    public AutoCompleteEngine createAutoCompleteEngine(Context context) {
        return new CssAutoCompleteEngine(context);
    }

    @Override
    public BaseFormatter getFormatter() {
        return new CssFormatter();
    }

    @Override
    public LspServer createLspServer(Context context) {
        return new CssLspServer(context);
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
        String src = text != null ? text.toString() : "";
        CssTokenStream stream = CssLexer.tokenize(src);
        CssSyntaxTree tree = CssParser.parse(stream, src);
        return tree != null ? tree.toDomFile() : null;
    }
}
