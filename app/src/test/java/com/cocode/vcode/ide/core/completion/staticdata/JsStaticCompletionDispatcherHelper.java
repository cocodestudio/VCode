package com.cocode.vcode.ide.core.completion.staticdata;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.js.JsLexer;

/**
 * Test helper: thin wrapper around {@link JsLexer#tokenize} so the
 * keyword-dispatch tests don't have to import the language package
 * directly.
 */
public final class JsStaticCompletionDispatcherHelper {

    private JsStaticCompletionDispatcherHelper() {}

    public static TokenStream tokenize(String source) {
        return JsLexer.tokenize(source);
    }
}
