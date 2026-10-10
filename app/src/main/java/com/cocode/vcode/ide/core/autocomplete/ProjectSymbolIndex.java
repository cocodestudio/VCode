package com.cocode.vcode.ide.core.autocomplete;

import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.lsp.index.IndexingProgressListener;
import com.cocode.vcode.ide.core.lsp.index.SegmentedSymbolStore;
import com.cocode.vcode.ide.core.lsp.index.WorkspaceIndexService;
import com.cocode.vcode.ide.core.model.CompletionItem;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backward-compatible facade for project-level symbols and autocomplete queries.
 * Delegates all workspace indexing and symbol store lookups to {@link WorkspaceIndexService}.
 */
public class ProjectSymbolIndex {

    private static volatile ProjectSymbolIndex sInstance;

    private ProjectSymbolIndex() {
    }

    public static ProjectSymbolIndex getInstance() {
        if (sInstance == null) {
            synchronized (ProjectSymbolIndex.class) {
                if (sInstance == null) {
                    sInstance = new ProjectSymbolIndex();
                }
            }
        }
        return sInstance;
    }

    public static File getProjectRoot(File file) {
        return WorkspaceIndexService.getProjectRoot(file);
    }

    public void addProgressListener(IndexingProgressListener listener) {
        WorkspaceIndexService.getInstance().addProgressListener(listener);
    }

    public void removeProgressListener(IndexingProgressListener listener) {
        WorkspaceIndexService.getInstance().removeProgressListener(listener);
    }

    public SegmentedSymbolStore getSegmentedStore() {
        return WorkspaceIndexService.getInstance().getSegmentedStore();
    }

    public SegmentedSymbolStore getSymbolStore() {
        return WorkspaceIndexService.getInstance().getSymbolStore();
    }

    public void invalidateScope(SymbolScope scope) {
        WorkspaceIndexService.getInstance().invalidateScope(scope);
    }

    public void putScopedSymbols(SymbolScope scope, String key, List<CompletionItem> items) {
        WorkspaceIndexService.getInstance().putScopedSymbols(scope, key, items);
    }

    public List<CompletionItem> getScopedSymbols(SymbolScope scope, String key) {
        return WorkspaceIndexService.getInstance().getScopedSymbols(scope, key);
    }

    public List<CompletionItem> getAllScopedSymbols(SymbolScope scope) {
        return WorkspaceIndexService.getInstance().getAllScopedSymbols(scope);
    }

    public void clearInMemorySymbols() {
        WorkspaceIndexService.getInstance().clearInMemorySymbols();
    }

    public void buildIndex(File rootDir) {
        WorkspaceIndexService.getInstance().buildIndex(rootDir);
    }

    public void buildIndex(File rootDir, Runnable onComplete) {
        WorkspaceIndexService.getInstance().buildIndex(rootDir, onComplete);
    }

    public static boolean hasDocument(File file) {
        return WorkspaceIndexService.getInstance().hasDocument(file);
    }

    public List<CompletionItem> getCssClassItems() {
        return WorkspaceIndexService.getInstance().getCssClassItems();
    }

    public List<CompletionItem> getCssIdItems() {
        return WorkspaceIndexService.getInstance().getCssIdItems();
    }

    public List<CompletionItem> getHtmlIdItems() {
        return WorkspaceIndexService.getInstance().getHtmlIdItems();
    }

    public List<CompletionItem> getClassMembers(String className) {
        return WorkspaceIndexService.getInstance().getClassMembers(className);
    }

    public Set<String> getAllClassNames() {
        return WorkspaceIndexService.getInstance().getAllClassNames();
    }

    public void registerDependencySymbols(String packageName, List<CompletionItem> members, List<CompletionItem> exports) {
        WorkspaceIndexService.getInstance().registerDependencySymbols(packageName, members, exports);
    }

    public void unregisterDependencySymbols(String packageName) {
        WorkspaceIndexService.getInstance().unregisterDependencySymbols(packageName);
    }

    public void ensureDependencyLoaded(File currentFile, String packageName) {
        WorkspaceIndexService.getInstance().ensureDependencyLoaded(currentFile, packageName);
    }

    public Map<String, List<CompletionItem>> getDependencyExports() {
        return WorkspaceIndexService.getInstance().getDependencyExports();
    }

    public Map<String, List<CompletionItem>> getDependencyMembers() {
        return WorkspaceIndexService.getInstance().getDependencyMembers();
    }

    public List<CompletionItem> getDependencyMembers(String packageName) {
        return WorkspaceIndexService.getInstance().getDependencyMembers(packageName);
    }

    public List<CompletionItem> getDependencyMembers(File currentFile, String packageName) {
        return WorkspaceIndexService.getInstance().getDependencyMembers(currentFile, packageName);
    }

    public List<CompletionItem> getExportsForPath(String importPath) {
        return WorkspaceIndexService.getInstance().getExportsForPath(importPath);
    }

    public List<CompletionItem> getExportsForPath(File currentFile, String importPath) {
        return WorkspaceIndexService.getInstance().getExportsForPath(currentFile, importPath);
    }

    public Set<String> getCssClassesForFile(File file) {
        return WorkspaceIndexService.getInstance().getCssClassesForFile(file);
    }

    public Set<String> getCssIdsForFile(File file) {
        return WorkspaceIndexService.getInstance().getCssIdsForFile(file);
    }

    public void collectCssSelectorsRecursively(File file, Set<String> classNames, Set<String> cssIds, Set<String> visited) {
        WorkspaceIndexService.getInstance().collectCssSelectorsRecursively(file, classNames, cssIds, visited);
    }

    public Map<String, CompletionItem> getJsSymbolsForFile(File file) {
        return WorkspaceIndexService.getInstance().getJsSymbolsForFile(file);
    }

    public void collectJsSymbolsRecursively(File file, Map<String, CompletionItem> targetSymbols, Set<String> visited) {
        WorkspaceIndexService.getInstance().collectJsSymbolsRecursively(file, targetSymbols, visited);
    }

    public void invalidateFile(String absolutePath) {
        WorkspaceIndexService.getInstance().invalidateFile(absolutePath);
    }

    public void updateFileSilently(File file) {
        WorkspaceIndexService.getInstance().updateFileSilently(file);
    }

    public void updateFileFromIndex(File file) {
        WorkspaceIndexService.getInstance().updateFileFromIndex(file);
    }

    public static boolean isValidCssClassName(String name) {
        return WorkspaceIndexService.isValidCssClassName(name);
    }

    public static boolean isValidCssId(String name) {
        return WorkspaceIndexService.isValidCssId(name);
    }

    public static boolean isValidHtmlId(String name) {
        return WorkspaceIndexService.isValidHtmlId(name);
    }

    public static boolean isValidJsIdentifier(String name) {
        return WorkspaceIndexService.isValidJsIdentifier(name);
    }

    public static boolean isJsReservedWord(String word) {
        return WorkspaceIndexService.isJsReservedWord(word);
    }

    public static boolean isKnownUnitOrExtension(String name) {
        return WorkspaceIndexService.isKnownUnitOrExtension(name);
    }

    public static String stripQuotes(String str) {
        return WorkspaceIndexService.stripQuotes(str);
    }

    public static void extractCssClassesAndIds(String selector, Set<String> classNames, Set<String> cssIds) {
        WorkspaceIndexService.extractCssClassesAndIds(selector, classNames, cssIds);
    }

    public static void extractSelectorsFromCssText(String cssContent, Set<String> classNames, Set<String> cssIds) {
        WorkspaceIndexService.extractSelectorsFromCssText(cssContent, classNames, cssIds);
    }

    public static List<File> extractCssImports(String cssContent, File baseFile) {
        return WorkspaceIndexService.extractCssImports(cssContent, baseFile);
    }

    public static List<File> extractJsImports(String jsContent, File baseFile) {
        return WorkspaceIndexService.extractJsImports(jsContent, baseFile);
    }

    public static void extractSymbolsFromJsTree(JsSyntaxTree tree, Map<String, CompletionItem> result, String origin) {
        WorkspaceIndexService.extractSymbolsFromJsTree(tree, result, origin);
    }

    public static void extractSymbolsFromJsText(String jsContent, Map<String, CompletionItem> result, String origin) {
        WorkspaceIndexService.extractSymbolsFromJsText(jsContent, result, origin);
    }

    public static void extractEmbeddedScriptSymbolsFromText(String html, Map<String, CompletionItem> targetSymbols, String origin) {
        WorkspaceIndexService.extractEmbeddedScriptSymbolsFromText(html, targetSymbols, origin);
    }

    public static void addOrMergeJsSymbol(Map<String, CompletionItem> result, String key, String label, String insertText, String origin, CompletionItem.Type type) {
        WorkspaceIndexService.addOrMergeJsSymbol(result, key, label, insertText, origin, type);
    }

    public static int indexOfIgnoreCaseStatic(String haystack, String needle, int from) {
        return WorkspaceIndexService.indexOfIgnoreCaseStatic(haystack, needle, from);
    }
}
