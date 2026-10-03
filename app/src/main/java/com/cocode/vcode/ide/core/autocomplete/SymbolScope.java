package com.cocode.vcode.ide.core.autocomplete;

/**
 * Defines the origin/ownership scope of symbols indexed within the workspace.
 * Partitions the symbol tables so library/dependency symbols can be invalidated
 * independently of user project code or runtime extensions.
 */
public enum SymbolScope {
    /**
     * User project files (HTML, CSS, JS, TS, etc.) located in the workspace directory.
     */
    PROJECT,

    /**
     * External package dependencies (e.g. node_modules, .d.ts type indexers, CDN libraries).
     */
    LIBRARIES,

    /**
     * Symbols injected by third-party IDE plugins, language servers, or custom extensions.
     */
    EXTENSIONS
}
