package com.cocode.vcode.ide.core.autocomplete;

import android.content.Context;

import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.data.repository.ProjectRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Unified autocomplete provider for cross-language filesystem paths and unquoted .gitignore rules.
 * Handles directory traversals (./, ../, nested subdirectories, and workspace-relative roots),
 * trailing slash appending for continuous navigation, and in-memory VFS querying.
 */
public class PathAutoCompleteEngine extends AutoCompleteEngine {

    public interface PathFilter {
        boolean isAllowed(File file, boolean isDirectory);

        default int getPriority(File file, boolean isDirectory) {
            return isDirectory ? 10 : 0;
        }
    }

    public static final Set<String> IMAGE_MEDIA_EXTS = new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "svg", "webp", "gif", "ico", "mp4", "mp3", "webm", "ogg"
    ));
    public static final Set<String> STYLESHEET_EXTS = new HashSet<>(Arrays.asList(
            "css", "scss", "less", "ico", "png"
    ));
    public static final Set<String> SCRIPT_EXTS = new HashSet<>(Arrays.asList(
            "js", "ts", "jsx", "tsx", "mjs", "cjs", "json", "vue"
    ));
    public static final Set<String> CSS_URL_EXTS = new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "svg", "webp", "gif", "woff", "woff2", "ttf", "otf", "eot", "css", "scss"
    ));
    public static final Set<String> CSS_IMPORT_EXTS = new HashSet<>(Arrays.asList(
            "css", "scss"
    ));
    public static final Set<String> MARKDOWN_DOC_EXTS = new HashSet<>(Arrays.asList(
            "md", "markdown", "png", "jpg", "jpeg", "svg", "webp", "gif", "pdf"
    ));
    public static final Set<String> HYPERLINK_EXTS = new HashSet<>(Arrays.asList(
            "html", "htm", "md"
    ));

    private File currentFile;
    private File workspaceRoot;

    public PathAutoCompleteEngine() {
        this(null);
    }

    public PathAutoCompleteEngine(Context context) {
        super(context);
    }

    @Override
    public String getTriggerCharacters() {
        return "/\\.\"'(=";
    }

    public void setCurrentFile(File file) {
        this.currentFile = file;
        if (workspaceRoot != null && !isWithinDirectory(file, workspaceRoot)) {
            this.workspaceRoot = null;
        }
    }

    public File getCurrentFile() {
        return currentFile;
    }

    public void setWorkspaceRoot(File root) {
        this.workspaceRoot = root;
    }

    public File getWorkspaceRoot() {
        if (workspaceRoot != null && workspaceRoot.exists()) return workspaceRoot;
        if (currentFile != null) {
            File root = com.cocode.vcode.ide.utils.FileUtils.resolveProjectRoot(currentFile);
            if (root != null) {
                workspaceRoot = root;
                return root;
            }
            return currentFile.getParentFile();
        }
        return null;
    }

    public boolean isGitIgnoreFile() {
        if (currentFile == null) return false;
        String name = currentFile.getName().toLowerCase(Locale.ROOT);
        return name.equals(".gitignore") || name.endsWith(".gitignore");
    }

    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (currentFile == null || fullText == null || cursorPos < 0 || cursorPos > fullText.length()) {
            return Collections.emptyList();
        }

        if (isGitIgnoreFile()) {
            return getGitIgnoreSuggestions(fullText, cursorPos);
        }

        PathContext ctx = extractPathContext(fullText, cursorPos);
        if (ctx == null) {
            return Collections.emptyList();
        }

        PathFilter filter = resolveFilterForContext(ctx);
        return getSuggestionsForPath(ctx.path, filter);
    }

    public List<CompletionItem> getSuggestionsForPath(String typedPath) {
        return getSuggestionsForPath(typedPath, null);
    }

    /**
     * Resolves path suggestions for an explicitly extracted path string.
     */
    public List<CompletionItem> getSuggestionsForPath(String typedPath, PathFilter filter) {
        if (currentFile == null) return Collections.emptyList();

        File projectRoot = getWorkspaceRoot();
        File fileParent = currentFile.getParentFile();
        File baseDir = fileParent != null ? fileParent : projectRoot;
        if (baseDir == null || !baseDir.exists()) return Collections.emptyList();

        String path = (typedPath != null) ? typedPath.replace('\\', '/') : "";

        // Strip enclosing quotes if accidentally passed by caller
        if ((path.startsWith("\"") && path.endsWith("\"")) || (path.startsWith("'") && path.endsWith("'"))) {
            if (path.length() >= 2) path = path.substring(1, path.length() - 1);
        } else if (path.startsWith("\"") || path.startsWith("'")) {
            path = path.substring(1);
        }

        File searchDir;
        String filterPrefix;
        boolean isRootRelative = path.startsWith("/");

        int lastSlash = path.lastIndexOf('/');
        if (isRootRelative) {
            searchDir = (projectRoot != null) ? projectRoot : baseDir;
            if (lastSlash > 0) {
                String dirPart = path.substring(1, lastSlash);
                filterPrefix = path.substring(lastSlash + 1);
                searchDir = resolvePathSegments(searchDir, dirPart, projectRoot);
            } else {
                filterPrefix = path.substring(1);
            }
        } else if (lastSlash != -1) {
            String dirPart = path.substring(0, lastSlash);
            filterPrefix = path.substring(lastSlash + 1);
            searchDir = resolvePathSegments(baseDir, dirPart, projectRoot);
        } else {
            filterPrefix = path;
            searchDir = baseDir;
        }

        if (searchDir == null || !searchDir.exists() || !searchDir.isDirectory()
                || (projectRoot != null && !isWithinDirectory(searchDir, projectRoot))) {
            return Collections.emptyList();
        }

        boolean isGitIgnore = isGitIgnoreFile();
        boolean includeHidden = isGitIgnore || (filterPrefix != null && filterPrefix.startsWith("."));
        List<File> files = VFSManager.getInstance().listCachedFiles(searchDir, includeHidden);
        if (files == null) {
            File[] disk = searchDir.listFiles();
            if (disk != null) {
                files = new ArrayList<>(disk.length);
                for (File d : disk) {
                    String dName = d.getName();
                    if (!dName.equalsIgnoreCase(".git")) {
                        files.add(d);
                    }
                }
            }
        }

        if (files == null || files.isEmpty()) {
            if (lastSlash == -1 && !filterPrefix.isEmpty() && !isGitIgnore && projectRoot != null) {
                return searchProjectFilesRecursively(projectRoot, baseDir, filterPrefix, filter);
            }
            return Collections.emptyList();
        }

        String lowerPrefix = filterPrefix.toLowerCase(Locale.ROOT);
        List<CompletionItem> candidates = new ArrayList<>();

        for (File f : files) {
            String name = f.getName();
            if (name.equals(".") || name.equals("..")) continue;
            if (name.equalsIgnoreCase(".git")) continue;
            if (!includeHidden && name.startsWith(".")) continue;

            if (!f.isDirectory() && currentFile != null && f.getAbsolutePath().equals(currentFile.getAbsolutePath())) {
                continue;
            }

            boolean isDir = f.isDirectory();
            if (filter != null && !filter.isAllowed(f, isDir)) {
                continue;
            }

            String lowerName = name.toLowerCase(Locale.ROOT);
            if (!lowerPrefix.isEmpty() && !lowerName.startsWith(lowerPrefix)) {
                continue;
            }

            String completionText = name + (isDir ? "/" : "");
            String detail = isDir ? "Directory" : getFileSizeHint(f);
            CompletionItem.Type type = isDir ? CompletionItem.Type.FOLDER : CompletionItem.Type.FILE;
            int priority = filter != null ? filter.getPriority(f, isDir) : 0;

            CompletionItem item = new CompletionItem(
                    completionText,
                    completionText,
                    detail,
                    type,
                    0
            );
            item.setReplaceLength(filterPrefix.length());
            item.setSortScore(isDir ? 1000 : (priority * 100));
            candidates.add(item);
        }

        // Tier 2: Fuzzy fallback if strict prefix returned no items
        if (candidates.isEmpty() && !lowerPrefix.isEmpty()) {
            for (File f : files) {
                String name = f.getName();
                if (name.equals(".") || name.equals("..")) continue;
                if (name.equalsIgnoreCase(".git")) continue;
                if (!includeHidden && name.startsWith(".")) continue;

                if (!f.isDirectory() && currentFile != null && f.getAbsolutePath().equals(currentFile.getAbsolutePath())) {
                    continue;
                }
                boolean isDir = f.isDirectory();
                if (filter != null && !filter.isAllowed(f, isDir)) continue;

                int score = computeFuzzyScore(name, lowerPrefix);
                if (score > 0) {
                    String completionText = name + (isDir ? "/" : "");
                    String detail = isDir ? "Directory" : getFileSizeHint(f);
                    CompletionItem.Type type = isDir ? CompletionItem.Type.FOLDER : CompletionItem.Type.FILE;
                    CompletionItem item = new CompletionItem(completionText, completionText, detail, type, 0);
                    item.setReplaceLength(filterPrefix.length());
                    item.setSortScore(score + (isDir ? 500 : 0));
                    candidates.add(item);
                }
            }
        }

        // If still empty and no slash was typed, try recursive search across workspace
        if (candidates.isEmpty() && lastSlash == -1 && !filterPrefix.isEmpty() && !isGitIgnore && projectRoot != null) {
            return searchProjectFilesRecursively(projectRoot, baseDir, filterPrefix, filter);
        }

        Collections.sort(candidates, (a, b) -> {
            boolean aDir = a.getType() == CompletionItem.Type.FOLDER;
            boolean bDir = b.getType() == CompletionItem.Type.FOLDER;
            if (aDir != bDir) return aDir ? -1 : 1;
            if (a.getSortScore() != b.getSortScore()) {
                return Integer.compare(b.getSortScore(), a.getSortScore());
            }
            return a.getLabel().compareToIgnoreCase(b.getLabel());
        });

        return candidates.size() > 50 ? candidates.subList(0, 50) : candidates;
    }

    private List<CompletionItem> getGitIgnoreSuggestions(String fullText, int cursorPos) {
        if (fullText == null || cursorPos <= 0 || cursorPos > fullText.length()) {
            return Collections.emptyList();
        }

        int lineStart = fullText.lastIndexOf('\n', cursorPos - 1);
        lineStart = (lineStart == -1) ? 0 : lineStart + 1;
        String lineBefore = fullText.substring(lineStart, cursorPos);
        String trimmed = lineBefore.trim();

        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return Collections.emptyList();
        }

        int p = 0;
        while (p < lineBefore.length() && Character.isWhitespace(lineBefore.charAt(p))) {
            p++;
        }
        if (p < lineBefore.length() && lineBefore.charAt(p) == '!') {
            p++;
        }
        String typedPath = lineBefore.substring(p);
        if (typedPath.isEmpty()) {
            return Collections.emptyList();
        }

        return getSuggestionsForPath(typedPath, null);
    }

    private File resolvePathSegments(File startDir, String dirPart, File boundaryRoot) {
        if (startDir == null || dirPart == null || dirPart.isEmpty()) return startDir;
        File current = startDir;
        String[] segments = dirPart.split("/");
        for (String seg : segments) {
            if (seg.isEmpty() || seg.equals(".")) {
                continue;
            }
            if (seg.equals("..")) {
                // Prevent walking above boundaryRoot
                if (boundaryRoot != null && isSameOrAncestor(boundaryRoot, current)) {
                    if (isSameCanonical(boundaryRoot, current)) {
                        return null; // Already at project boundary; cannot traverse to parent
                    }
                }
                File parent = current.getParentFile();
                if (parent == null) {
                    return null;
                }
                current = parent;
            } else {
                current = new File(current, seg);
            }
        }
        if (boundaryRoot != null && !isWithinDirectory(current, boundaryRoot)) {
            return null;
        }
        return current;
    }

    public static boolean isWithinDirectory(File file, File root) {
        if (file == null || root == null) return false;
        try {
            String filePath = file.getCanonicalPath();
            String rootPath = root.getCanonicalPath();
            return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
        } catch (Exception e) {
            String filePath = file.getAbsolutePath();
            String rootPath = root.getAbsolutePath();
            return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
        }
    }

    private static boolean isSameCanonical(File a, File b) {
        if (a == null || b == null) return false;
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (Exception e) {
            return a.getAbsolutePath().equals(b.getAbsolutePath());
        }
    }

    private static boolean isSameOrAncestor(File ancestor, File child) {
        return isWithinDirectory(child, ancestor);
    }

    private List<CompletionItem> searchProjectFilesRecursively(File projectRoot, File baseDir, String prefix, PathFilter filter) {
        List<File> matched = new ArrayList<>();
        collectMatchesRecursively(projectRoot, prefix.toLowerCase(Locale.ROOT), matched, 30, filter);

        List<CompletionItem> items = new ArrayList<>();
        for (File f : matched) {
            if (currentFile != null && f.getAbsolutePath().equals(currentFile.getAbsolutePath())) {
                continue;
            }
            String relPath = getRelativePath(baseDir, f);
            String label = f.getName() + (f.isDirectory() ? "/" : "");
            CompletionItem ci = new CompletionItem(
                    label,
                    relPath,
                    f.isDirectory() ? "Directory" : relPath,
                    f.isDirectory() ? CompletionItem.Type.FOLDER : CompletionItem.Type.FILE,
                    0
            );
            ci.setReplaceLength(prefix.length());
            items.add(ci);
        }

        Collections.sort(items, (a, b) -> {
            boolean aDir = a.getType() == CompletionItem.Type.FOLDER;
            boolean bDir = b.getType() == CompletionItem.Type.FOLDER;
            if (aDir != bDir) return aDir ? -1 : 1;
            return a.getLabel().compareToIgnoreCase(b.getLabel());
        });

        return items;
    }

    private void collectMatchesRecursively(File dir, String query, List<File> results, int limit, PathFilter filter) {
        if (dir == null || results.size() >= limit) return;
        List<File> files = VFSManager.getInstance().listCachedFiles(dir);
        if (files == null) return;

        for (File f : files) {
            if (f.getName().startsWith(".")) continue;
            boolean isDir = f.isDirectory();
            if (filter != null && !filter.isAllowed(f, isDir)) continue;

            if (f.getName().toLowerCase(Locale.ROOT).startsWith(query)) {
                results.add(f);
                if (results.size() >= limit) return;
            }

            if (isDir) {
                collectMatchesRecursively(f, query, results, limit, filter);
            }
        }
    }

    public static String getRelativePath(File baseDir, File target) {
        if (baseDir == null || target == null) return target != null ? target.getName() : "";
        String basePathStr = baseDir.getAbsolutePath().replace('\\', '/');
        String targetPathStr = target.getAbsolutePath().replace('\\', '/');

        String[] basePath = basePathStr.split("/");
        String[] targetPath = targetPathStr.split("/");

        int common = 0;
        while (common < basePath.length && common < targetPath.length
                && basePath[common].equals(targetPath[common])) {
            common++;
        }

        StringBuilder rel = new StringBuilder();
        for (int i = common; i < basePath.length; i++) {
            rel.append("../");
        }
        for (int i = common; i < targetPath.length; i++) {
            rel.append(targetPath[i]);
            if (i < targetPath.length - 1) {
                rel.append("/");
            }
        }
        if (target.isDirectory() && rel.length() > 0 && rel.charAt(rel.length() - 1) != '/') {
            rel.append("/");
        }
        return rel.length() == 0 ? "./" : rel.toString();
    }

    private static class PathContext {
        final String path;
        final char delimiter;
        final String precedingToken;

        PathContext(String path, char delimiter, String precedingToken) {
            this.path = path;
            this.delimiter = delimiter;
            this.precedingToken = precedingToken;
        }
    }

    private PathContext extractPathContext(String text, int pos) {
        if (text == null || pos < 0 || pos > text.length()) return null;

        int start = pos;
        char delim = 0;

        while (start > 0) {
            char c = text.charAt(start - 1);
            if (c == '"' || c == '\'' || c == '`' || c == '(' || c == '=') {
                delim = c;
                break;
            }
            if (c == '\n' || c == '\r' || c == ';' || c == '{' || c == '}') {
                break;
            }
            start--;
        }

        if (delim == 0) {
            return null; // Not inside quotes, parens, or assignment
        }

        String path = text.substring(start, pos);

        // Find token before the delimiter (e.g. "src", "href", "url", "@import", "from")
        int tokenEnd = start - 1;
        while (tokenEnd > 0 && Character.isWhitespace(text.charAt(tokenEnd - 1))) {
            tokenEnd--;
        }
        // If the character immediately preceding the quote (after skipping whitespace) is '(', '=', or ':',
        // skip that punctuation mark and any additional preceding whitespace before reading tokenStart.
        if (tokenEnd > 0) {
            char p = text.charAt(tokenEnd - 1);
            if (p == '(' || p == '=' || p == ':') {
                tokenEnd--;
                while (tokenEnd > 0 && Character.isWhitespace(text.charAt(tokenEnd - 1))) {
                    tokenEnd--;
                }
            }
        }
        if (tokenEnd > 0 && (text.charAt(tokenEnd - 1) == '"' || text.charAt(tokenEnd - 1) == '\'')) {
            tokenEnd--;
        }
        int tokenStart = tokenEnd;
        while (tokenStart > 0) {
            char c = text.charAt(tokenStart - 1);
            if (Character.isLetterOrDigit(c) || c == '@' || c == '_' || c == '-') {
                tokenStart--;
            } else {
                break;
            }
        }
        String token = (tokenStart < tokenEnd) ? text.substring(tokenStart, tokenEnd).toLowerCase(Locale.ROOT) : "";

        return new PathContext(path, delim, token);
    }

    private PathFilter resolveFilterForContext(PathContext ctx) {
        String token = ctx.precedingToken;
        String fileName = currentFile != null ? currentFile.getName().toLowerCase(Locale.ROOT) : "";

        if (token.equals("url")) {
            return (f, isDir) -> isDir || isExtensionIn(f, CSS_URL_EXTS);
        }
        if (token.equals("@import")) {
            return (f, isDir) -> isDir || isExtensionIn(f, CSS_IMPORT_EXTS);
        }
        if (token.equals("src") || token.equals("poster")) {
            return (f, isDir) -> isDir || isExtensionIn(f, IMAGE_MEDIA_EXTS) || isExtensionIn(f, SCRIPT_EXTS);
        }
        if (token.equals("href")) {
            return (f, isDir) -> isDir || isExtensionIn(f, STYLESHEET_EXTS) || isExtensionIn(f, HYPERLINK_EXTS);
        }
        if (token.equals("from") || token.equals("require") || token.equals("import")) {
            return (f, isDir) -> isDir || isExtensionIn(f, SCRIPT_EXTS);
        }
        if (token.equals("main") || token.equals("module") || token.equals("types") || token.equals("typings")) {
            return (f, isDir) -> isDir || isExtensionIn(f, SCRIPT_EXTS);
        }
        if (fileName.endsWith(".css") || fileName.endsWith(".scss")) {
            return (f, isDir) -> isDir || isExtensionIn(f, CSS_IMPORT_EXTS);
        }
        if (fileName.endsWith(".md") || fileName.endsWith(".markdown")) {
            return (f, isDir) -> isDir || isExtensionIn(f, MARKDOWN_DOC_EXTS);
        }
        if (fileName.endsWith(".js") || fileName.endsWith(".ts") || fileName.endsWith(".jsx") || fileName.endsWith(".tsx")) {
            return (f, isDir) -> isDir || isExtensionIn(f, SCRIPT_EXTS);
        }

        return null;
    }

    private static boolean isExtensionIn(File f, Set<String> extSet) {
        String name = f.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return false;
        String ext = name.substring(dot + 1);
        return extSet.contains(ext);
    }

    private String getFileSizeHint(File f) {
        long size = f.length();
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        return (size / (1024 * 1024)) + " MB";
    }
}
