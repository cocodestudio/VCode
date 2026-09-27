package com.cocode.vcode.ide.core.language.md;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.language.base.BaseFormatter;
import com.cocode.vcode.ide.core.language.base.DomNode;
import com.cocode.vcode.ide.core.language.base.LanguageBackend;
import com.cocode.vcode.ide.core.language.base.SyntaxHighlighter;
import com.cocode.vcode.ide.core.language.base.SyntaxTreeDomAdapter;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.ServerCapabilities;
import com.cocode.vcode.ide.core.lsp.servers.MarkdownLspServer;
import com.cocode.vcode.ide.core.model.FileType;

/**
 * Language intelligence backend for Markdown.
 */
public class MarkdownLanguageBackend implements LanguageBackend {

    @Override
    public FileType getFileType() {
        return FileType.MARKDOWN;
    }

    @Override
    public String getLanguageId() {
        return "markdown";
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"md", "markdown"};
    }

    @Override
    public SyntaxHighlighter createHighlighter(Context context) {
        return new MarkdownSyntaxHighlighter(context);
    }

    @Override
    public AutoCompleteEngine createAutoCompleteEngine(Context context) {
        return new MarkdownAutoCompleteEngine(context);
    }

    @Override
    public BaseFormatter getFormatter() {
        return null;
    }

    @Override
    public LspServer createLspServer(Context context) {
        return new MarkdownLspServer(context);
    }

    @Override
    public ServerCapabilities getServerCapabilities() {
        return new ServerCapabilities.Builder()
                .setCompletionProvider(true)
                .build();
    }

    @Override
    public DomNode parseToDom(CharSequence text) {
        String src = text != null ? text.toString() : "";
        MdLineStream stream = MdLexer.lex(src);
        MdSyntaxTree tree = MdParser.parseBlocks(stream, src);
        return SyntaxTreeDomAdapter.fromMd(tree);
    }
}
