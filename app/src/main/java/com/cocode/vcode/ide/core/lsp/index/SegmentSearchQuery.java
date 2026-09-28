package com.cocode.vcode.ide.core.lsp.index;

/**
 * Immutable query descriptor for flat-memory segment searches.
 */
public final class SegmentSearchQuery {

    private final String query;
    private final boolean exact;
    private final int limit;
    private final byte kindFilter;

    public static final byte NO_KIND_FILTER = -1;

    private SegmentSearchQuery(String query, boolean exact, int limit, byte kindFilter) {
        this.query = query != null ? query : "";
        this.exact = exact;
        this.limit = limit;
        this.kindFilter = kindFilter;
    }

    public static SegmentSearchQuery prefix(String prefix, int limit) {
        return new SegmentSearchQuery(prefix, false, limit, NO_KIND_FILTER);
    }

    public static SegmentSearchQuery prefix(String prefix, int limit, byte kind) {
        return new SegmentSearchQuery(prefix, false, limit, kind);
    }

    public static SegmentSearchQuery exact(String exactKey) {
        return new SegmentSearchQuery(exactKey, true, 0, NO_KIND_FILTER);
    }

    public String getQuery() {
        return query;
    }

    public boolean isExact() {
        return exact;
    }

    public int getLimit() {
        return limit;
    }

    public byte getKindFilter() {
        return kindFilter;
    }

    public boolean matchesKind(byte kind) {
        return kindFilter == NO_KIND_FILTER || kindFilter == kind;
    }
}
