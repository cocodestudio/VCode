package com.cocode.vcode.ide.core.lsp;

import com.cocode.vcode.ide.core.lsp.model.LspPosition;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentContentChangeEvent;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentIdentifier;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentItem;
import com.cocode.vcode.ide.core.lsp.model.VersionedTextDocumentIdentifier;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active open documents and provides O(1) position ↔ flat offset mapping
 * across the editor and language servers using {@link OffsetIndexCache}.
 */
public final class LspDocumentManager {

    private static volatile LspDocumentManager sInstance;

    private final ConcurrentHashMap<String, LspDocument> documents = new ConcurrentHashMap<>();

    private LspDocumentManager() {
    }

    public static LspDocumentManager getInstance() {
        if (sInstance == null) {
            synchronized (LspDocumentManager.class) {
                if (sInstance == null) {
                    sInstance = new LspDocumentManager();
                }
            }
        }
        return sInstance;
    }

    public static String canonicalize(String uri) {
        if (uri == null) return null;
        String clean = uri;
        if (clean.startsWith("file://")) {
            clean = clean.substring(7);
        }
        try {
            return new File(clean).getAbsolutePath();
        } catch (Exception e) {
            return clean;
        }
    }

    /**
     * Registers an opened document snapshot.
     */
    public LspDocument openDocument(TextDocumentItem item) {
        if (item == null) return null;
        return openDocument(item.uri, item.text, item.languageId, item.version);
    }

    /**
     * Registers an opened document snapshot with precomputed {@link OffsetIndexCache}.
     */
    public LspDocument openDocument(String uri, String text, String languageId, int version) {
        if (uri == null) return null;
        String canonicalUri = canonicalize(uri);
        LspDocument doc = new LspDocument(canonicalUri, text, languageId, version);
        documents.put(canonicalUri, doc);
        return doc;
    }

    /**
     * Updates an active document with new full text and increments version.
     */
    public LspDocument updateDocument(String uri, String newText, int newVersion) {
        if (uri == null) return null;
        String canonicalUri = canonicalize(uri);
        LspDocument old = documents.get(canonicalUri);
        String lang = (old != null) ? old.languageId : "plaintext";
        LspDocument doc = new LspDocument(canonicalUri, newText, lang, newVersion);
        documents.put(canonicalUri, doc);
        return doc;
    }

    /**
     * Updates an active document from LSP change events.
     */
    public LspDocument updateDocument(VersionedTextDocumentIdentifier docId, List<TextDocumentContentChangeEvent> changes, String fullText) {
        if (docId == null) return null;
        String canonicalUri = canonicalize(docId.uri);
        LspDocument old = documents.get(canonicalUri);
        String lang = (old != null) ? old.languageId : "plaintext";

        String textToUse = fullText;
        if (textToUse == null && changes != null && !changes.isEmpty()) {
            for (TextDocumentContentChangeEvent change : changes) {
                if (change != null && change.range == null && change.text != null) {
                    textToUse = change.text;
                    break;
                }
            }
        }
        if (textToUse == null) {
            textToUse = (old != null) ? old.text : "";
        }

        LspDocument doc = new LspDocument(canonicalUri, textToUse, lang, docId.version);
        documents.put(canonicalUri, doc);
        return doc;
    }

    /**
     * Closes an active document.
     */
    public LspDocument closeDocument(TextDocumentIdentifier docId) {
        if (docId == null) return null;
        return closeDocument(docId.uri);
    }

    /**
     * Closes an active document.
     */
    public LspDocument closeDocument(String uri) {
        if (uri == null) return null;
        return documents.remove(canonicalize(uri));
    }

    /**
     * Retrieves the current snapshot of an active document.
     */
    public LspDocument getDocument(String uri) {
        if (uri == null) return null;
        return documents.get(canonicalize(uri));
    }

    /**
     * Returns true if the document is currently tracked as active.
     */
    public boolean isDocumentOpen(String uri) {
        if (uri == null) return false;
        return documents.containsKey(canonicalize(uri));
    }

    /**
     * Returns the OffsetIndexCache for the active document, or null if not open.
     */
    public OffsetIndexCache getOffsetIndexCache(String uri) {
        LspDocument doc = getDocument(uri);
        return (doc != null) ? doc.getOffsetIndexCache() : null;
    }

    /**
     * Converts coordinate to flat offset in O(1) for an active document.
     */
    public int toOffset(String uri, LspPosition pos) {
        LspDocument doc = getDocument(uri);
        return (doc != null) ? doc.toOffset(pos) : -1;
    }

    /**
     * Converts flat offset to coordinate in O(log N) for an active document.
     */
    public com.cocode.vcode.ide.core.lsp.LspPosition toPosition(String uri, int offset) {
        LspDocument doc = getDocument(uri);
        return (doc != null) ? doc.toPosition(offset) : new com.cocode.vcode.ide.core.lsp.LspPosition(0, 0);
    }

    /**
     * Returns an unmodifiable collection of all active open documents.
     */
    public Collection<LspDocument> getOpenDocuments() {
        return Collections.unmodifiableCollection(documents.values());
    }

    /**
     * Clears all open document state.
     */
    public void clear() {
        documents.clear();
    }
}
