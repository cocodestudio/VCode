package com.cocode.vcode.ide.core.extension;

import androidx.annotation.NonNull;

/**
 * Service Provider Interface for native and dynamic VCode plugins.
 */
public interface VCodePlugin {

    /**
     * Invoked when the plugin is activated by {@link ExtensionHost}.
     *
     * @param context the extension context holding registries and disposable tracking
     */
    void initialize(@NonNull ExtensionContext context);

    /**
     * Invoked when the plugin is deactivated or uninstalled.
     */
    default void deactivate() {
    }
}
