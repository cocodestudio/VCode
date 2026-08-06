package com.cocode.vcode.ide.core.language.base;

import com.cocode.vcode.ide.data.model.AppSettings;

/**
 * Shared architectural foundation for source code formatters.
 * Provides central access to core configuration states like user indentation preferences
 * and reusable helper utilities to build indentation spacing layout strings.
 */
public abstract class BaseFormatter {

    // Pulls the current active indentation preference (e.g., space or tab count) from app configurations
    protected static final String INDENT = new AppSettings().getIndent(); // 2 tabs
    private static final String[] INDENT_CACHE = new String[50];

    static {
        INDENT_CACHE[0] = "";
        for (int i = 1; i < INDENT_CACHE.length; i++) {
            INDENT_CACHE[i] = INDENT_CACHE[i - 1] + INDENT;
        }
    }

    /**
     * Abstract contract to format source string code into a structured, uniform style.
     *
     * @param code The raw, unformatted source text sequence.
     * @return The formatted source code string.
     */
    public abstract String format(String code);

    /**
     * Builds an indentation block sequence corresponding directly to the nested bracket hierarchy depth.
     *
     * @param level The current structural nesting depth level.
     * @return A consolidated spacer sequence string matching the requested indentation weight.
     */
    protected String getIndentString(int level) {
        if (level <= 0) return "";
        if (level < INDENT_CACHE.length) return INDENT_CACHE[level];

        StringBuilder sb = new StringBuilder(INDENT_CACHE[INDENT_CACHE.length - 1]);
        for (int i = INDENT_CACHE.length; i < level; i++) {
            sb.append(INDENT);
        }
        return sb.toString();
    }
}