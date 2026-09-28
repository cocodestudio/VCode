package com.cocode.vcode.ide.core.lsp.index;

/**
 * Deterministic namespace keys for indexing and querying code symbols in flat-memory segments.
 */
public final class SymbolKey {

    public static final String PREFIX_CSS_CLASS = "css:cls:";
    public static final String PREFIX_CSS_ID = "css:id:";
    public static final String PREFIX_HTML_ID = "html:id:";
    public static final String PREFIX_HTML_TAG = "html:tag:";
    public static final String PREFIX_JS_EXPORT = "js:exp:";
    public static final String PREFIX_JS_CLASS = "js:cls:";
    public static final String PREFIX_JS_MODULE = "js:mod:";

    private SymbolKey() {
    }

    public static String cssClass(String className) {
        return PREFIX_CSS_CLASS + (className != null ? className.trim() : "");
    }

    public static String cssId(String idName) {
        return PREFIX_CSS_ID + (idName != null ? idName.trim() : "");
    }

    public static String htmlId(String idName) {
        return PREFIX_HTML_ID + (idName != null ? idName.trim() : "");
    }

    public static String htmlTag(String tagName) {
        return PREFIX_HTML_TAG + (tagName != null ? tagName.trim() : "");
    }

    public static String jsExport(String relativePath, String exportName) {
        String cleanPath = relativePath != null ? relativePath.replace('\\', '/') : "";
        String cleanName = exportName != null ? exportName.trim() : "";
        return PREFIX_JS_EXPORT + cleanPath + "#" + cleanName;
    }

    public static String jsClassMember(String className, String memberName) {
        String cleanClass = className != null ? className.trim() : "";
        String cleanMember = memberName != null ? memberName.trim() : "";
        return PREFIX_JS_CLASS + cleanClass + "." + cleanMember;
    }

    public static String jsClassPrefix(String className) {
        String cleanClass = className != null ? className.trim() : "";
        return PREFIX_JS_CLASS + cleanClass + ".";
    }

    public static String jsExportPathPrefix(String relativePath) {
        String cleanPath = relativePath != null ? relativePath.replace('\\', '/') : "";
        return PREFIX_JS_EXPORT + cleanPath + "#";
    }

    public static String extractName(String key) {
        if (key == null) return "";
        if (key.startsWith(PREFIX_CSS_CLASS)) {
            return key.substring(PREFIX_CSS_CLASS.length());
        } else if (key.startsWith(PREFIX_CSS_ID)) {
            return key.substring(PREFIX_CSS_ID.length());
        } else if (key.startsWith(PREFIX_HTML_ID)) {
            return key.substring(PREFIX_HTML_ID.length());
        } else if (key.startsWith(PREFIX_HTML_TAG)) {
            return key.substring(PREFIX_HTML_TAG.length());
        } else if (key.startsWith(PREFIX_JS_EXPORT)) {
            int hashIdx = key.indexOf('#');
            return hashIdx >= 0 && hashIdx + 1 < key.length() ? key.substring(hashIdx + 1) : key;
        } else if (key.startsWith(PREFIX_JS_CLASS)) {
            int dotIdx = key.indexOf('.');
            return dotIdx >= 0 && dotIdx + 1 < key.length() ? key.substring(dotIdx + 1) : key;
        }
        return key;
    }
}
