package com.cocode.vcode.ide.core.lsp;

import com.cocode.vcode.ide.core.language.js.JsExportTable;
import com.cocode.vcode.ide.core.language.js.ParseResult;
import com.cocode.vcode.ide.core.lsp.index.WorkspaceIndexService;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Backward-compatible delegate facade to {@link WorkspaceIndexService}.
 * <p>
 * Ensures all existing callers seamlessly route to the authoritative single source of truth
 * for workspace documents, ASTs, and symbols without maintaining duplicate caches or memory.
 */
public final class ProjectIndex {

    private static volatile ProjectIndex sInstance;

    private ProjectIndex() {
    }

    public static ProjectIndex getInstance() {
        if (sInstance == null) {
            synchronized (ProjectIndex.class) {
                if (sInstance == null) sInstance = new ProjectIndex();
            }
        }
        return sInstance;
    }

    public void indexProject(File root, Runnable onComplete) {
        WorkspaceIndexService.getInstance().indexProject(root, onComplete);
    }

    public void indexProject(File root) {
        WorkspaceIndexService.getInstance().indexProject(root, null);
    }

    public void indexProjectIncremental(File root) {
        WorkspaceIndexService.getInstance().indexProjectIncremental(root);
    }

    public void indexProjectIncremental(File root, Runnable onComplete) {
        WorkspaceIndexService.getInstance().indexProjectIncremental(root, onComplete);
    }

    public void clear() {
        WorkspaceIndexService.getInstance().clear();
    }

    public void updateParseResult(String uri, ParseResult result) {
        WorkspaceIndexService.getInstance().updateParseResult(uri, result);
    }

    public ParseResult getParseResult(String uri) {
        return WorkspaceIndexService.getInstance().getParseResult(uri);
    }

    public ParseResult getOrParseJsFile(String uri) {
        return WorkspaceIndexService.getInstance().getOrParseJsFile(uri);
    }

    public ParseResult getOrParseJsFile(File file) {
        return WorkspaceIndexService.getInstance().getOrParseJsFile(file);
    }

    public JsExportTable getExportTable(String uri) {
        return WorkspaceIndexService.getInstance().getExportTable(uri);
    }

    public List<ExportRef> getExportsByPrefix(String prefix) {
        List<WorkspaceIndexService.ExportRef> refs = WorkspaceIndexService.getInstance().getExportsByPrefix(prefix);
        if (refs == null || refs.isEmpty()) return Collections.emptyList();
        List<ExportRef> out = new ArrayList<>(refs.size());
        for (WorkspaceIndexService.ExportRef ref : refs) {
            out.add(new ExportRef(ref.name, ref.sourceUri));
        }
        return out;
    }

    public void updateDocument(LspDocument doc) {
        WorkspaceIndexService.getInstance().updateDocument(doc);
    }

    public void updateFileSymbols(String uri, List<SymbolEntry> symbols) {
        WorkspaceIndexService.getInstance().updateFileSymbols(uri, symbols);
    }

    public void updateDocumentSnapshot(String uri, String text, String languageId) {
        WorkspaceIndexService.getInstance().updateDocumentSnapshot(uri, text, languageId);
    }

    public void invalidateSymbols(String uri) {
        WorkspaceIndexService.getInstance().invalidateSymbols(uri);
    }

    public void reindexFile(File file) {
        WorkspaceIndexService.getInstance().reindexFile(file);
    }

    public void revertToDisk(File file) {
        WorkspaceIndexService.getInstance().revertToDisk(file);
    }

    public LspDocument getDocument(String uri) {
        return WorkspaceIndexService.getInstance().getDocument(uri);
    }

    public List<SymbolEntry> findSymbolsByPrefix(String prefix) {
        return WorkspaceIndexService.getInstance().findSymbolsByPrefix(prefix);
    }

    public List<LspLocation> findDefinitions(String name) {
        return WorkspaceIndexService.getInstance().findDefinitions(name);
    }

    public List<LspLocation> findReferences(String name) {
        return WorkspaceIndexService.getInstance().findReferences(name);
    }

    public List<LspLocation> findFileUsages(String filename) {
        return WorkspaceIndexService.getInstance().findFileUsages(filename);
    }

    public List<SymbolEntry> getFileSymbols(String uri) {
        return WorkspaceIndexService.getInstance().getFileSymbols(uri);
    }

    public List<String> getAllUris() {
        return WorkspaceIndexService.getInstance().getAllUris();
    }

    public String getProjectRoot() {
        return WorkspaceIndexService.getInstance().getProjectRoot();
    }

    public void indexFile(File file) {
        WorkspaceIndexService.getInstance().indexFile(file);
    }

    public static final class ExportRef {
        public final String name;
        public final String sourceUri;

        public ExportRef(String name, String sourceUri) {
            this.name = name;
            this.sourceUri = sourceUri;
        }
    }
}
