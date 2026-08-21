package com.cocode.vcode.ide.core.language.base;

/**
 * Shared architectural foundation for source code formatters.
 * Provides central access to core configuration states like user indentation preferences
 * and reusable helper utilities to build indentation spacing layout strings.
 */
public abstract class BaseFormatter {

    protected String indentUnit = "  "; // 2 tabs
    private String[] indentCache = new String[50];

    protected BaseFormatter() {
        buildIndentCache();
    }

    private void buildIndentCache() {
        indentCache[0] = "";
        for (int i = 1; i < 50; i++) {
            indentCache[i] = indentCache[i - 1] + indentUnit;
        }
    }

    public void setIndentUnit(String indent) {
        this.indentUnit = (indent != null && !indent.isEmpty()) ? indent : "  ";
        buildIndentCache();
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
        if (level < indentCache.length) return indentCache[level];

        StringBuilder sb = new StringBuilder(indentCache[indentCache.length - 1]);
        for (int i = indentCache.length; i < level; i++) {
            sb.append(indentUnit);
        }
        return sb.toString();
    }
}