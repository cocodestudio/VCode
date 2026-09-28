package com.cocode.vcode.ide.core.lsp.index;

import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.Objects;

/**
 * Compact record representing an indexed symbol entry for flat-memory segment storage.
 */
public final class IndexSymbolEntry {

    public static final byte KIND_CSS_CLASS = 0;
    public static final byte KIND_CSS_ID = 1;
    public static final byte KIND_HTML_ID = 2;
    public static final byte KIND_HTML_TAG = 3;
    public static final byte KIND_JS_EXPORT = 4;
    public static final byte KIND_JS_CLASS_MEMBER = 5;
    public static final byte KIND_JS_MODULE = 6;

    public static final byte FLAG_NONE = 0;
    public static final byte FLAG_DEPRECATED = 1;
    public static final byte FLAG_EXPORTED = 2;
    public static final byte FLAG_ASYNC = 4;
    public static final byte FLAG_STATIC = 8;

    private final String key;
    private final String name;
    private final byte kind;
    private final byte flags;
    private final int fileId;
    private final int line;
    private final String detail;

    public IndexSymbolEntry(String key, String name, byte kind, byte flags, int fileId, int line, String detail) {
        this.key = key != null ? key : "";
        this.name = name != null ? name : "";
        this.kind = kind;
        this.flags = flags;
        this.fileId = fileId;
        this.line = line;
        this.detail = detail != null ? detail : "";
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public byte getKind() {
        return kind;
    }

    public byte getFlags() {
        return flags;
    }

    public int getFileId() {
        return fileId;
    }

    public int getLine() {
        return line;
    }

    public String getDetail() {
        return detail;
    }

    public CompletionItem toCompletionItem(String sourceUri) {
        CompletionItem.Type type;
        switch (kind) {
            case KIND_CSS_CLASS:
            case KIND_CSS_ID:
                type = CompletionItem.Type.CSS_VALUE;
                break;
            case KIND_HTML_ID:
                type = CompletionItem.Type.VALUE;
                break;
            case KIND_HTML_TAG:
                type = CompletionItem.Type.TAG;
                break;
            case KIND_JS_EXPORT:
                if (detail != null && (detail.startsWith("(") || detail.contains("=>") || detail.contains("function"))) {
                    type = CompletionItem.Type.FUNCTION;
                } else {
                    type = CompletionItem.Type.VALUE;
                }
                break;
            case KIND_JS_CLASS_MEMBER:
                if (detail != null && detail.contains("method")) {
                    type = CompletionItem.Type.FUNCTION;
                } else {
                    type = CompletionItem.Type.VALUE;
                }
                break;
            case KIND_JS_MODULE:
                type = CompletionItem.Type.FILE;
                break;
            default:
                type = CompletionItem.Type.VALUE;
                break;
        }

        if (sourceUri != null && !sourceUri.isEmpty()) {
            return new CompletionItem(name, name, detail, type, 0, sourceUri);
        } else {
            return new CompletionItem(name, name, detail, type, 0);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IndexSymbolEntry that = (IndexSymbolEntry) o;
        return kind == that.kind &&
                flags == that.flags &&
                fileId == that.fileId &&
                line == that.line &&
                key.equals(that.key) &&
                name.equals(that.name) &&
                detail.equals(that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, name, kind, flags, fileId, line, detail);
    }
}
