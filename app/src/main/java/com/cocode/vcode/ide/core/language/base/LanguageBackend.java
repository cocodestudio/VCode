package com.cocode.vcode.ide.core.language.base;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.ServerCapabilities;
import com.cocode.vcode.ide.core.model.FileType;

/**
 * Service Provider Interface (SPI) for language intelligence in VCode.
 * Encapsulates syntax highlighting, code formatting, autocomplete, LSP server, and DOM parsing
 * into a single cohesive, pluggable backend contract.
 */
public interface LanguageBackend {

    /** Primary FileType associated with this language */
    FileType getFileType();

    /** LSP language identifier (e.g. "html", "javascript", "css") */
    String getLanguageId();

    /** Supported file extensions (e.g. ["js", "mjs", "cjs"]) */
    String[] getSupportedExtensions();

    /** Instantiates the syntax highlighter for this language */
    SyntaxHighlighter createHighlighter(Context context);

    /** Instantiates the autocomplete engine for this language */
    AutoCompleteEngine createAutoCompleteEngine(Context context);

    /** Returns the code formatter for this language, or null if unformatted */
    BaseFormatter getFormatter();

    /** Instantiates the LSP server for this language */
    LspServer createLspServer(Context context);

    /** Declared LSP server capabilities */
    ServerCapabilities getServerCapabilities();

    /** Parses the source text into a neutral DOM representation */
    DomNode parseToDom(CharSequence text);

    /** Returns characters that trigger autocomplete in this language */
    default String getTriggerCharacters() {
        return AutoCompleteEngine.getTriggerCharacters(getFileType());
    }
}
