package com.cocode.vcode.ide.core.lsp;

import android.content.Context;

/**
 * Functional factory for instantiating an {@link LspServer} on-demand.
 */
@FunctionalInterface
public interface LspServerFactory {

    /**
     * Creates a new uninitialized {@link LspServer} instance.
     *
     * @param context application context for loading assets and schemas
     * @return the newly created server instance
     */
    LspServer createServer(Context context);
}
