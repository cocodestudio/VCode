package com.cocode.vcode.ide.core.autocomplete;

import androidx.annotation.NonNull;

import com.cocode.vcode.ide.core.model.CompletionItem;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.data.repository.ProjectRepository;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Global project indexer that extracts CSS classes, IDs, and HTML IDs
 * for cross-file intellisense.
 */
public class ProjectSymbolIndex {
    // CSS class regex: .my-class
    private static final Pattern PAT_CSS_CLASS = Pattern.compile("\\.([a-zA-Z_][a-zA-Z0-9_-]*)");
    // CSS ID regex: #my-id
    private static final Pattern PAT_CSS_ID = Pattern.compile("#([a-zA-Z_][a-zA-Z0-9_-]*)");
    // HTML ID regex: id="my-id" or id='my-id'
    private static final Pattern PAT_HTML_ID = Pattern.compile("id\\s*=\\s*[\"']([a-zA-Z0-9_-]+)[\"']");
    // JS Exports
    private static final Pattern PAT_JS_EXPORT_DECL = Pattern.compile("export\\s+(?:const|let|var|function|class|interface|type)\\s+([a-zA-Z_$][\\w$]*)");
    private static final Pattern PAT_JS_EXPORT_DEFAULT = Pattern.compile("export\\s+default\\s+(?:class\\s+|function\\s+)?([a-zA-Z_$][\\w$]*)?");
    private static final Pattern PAT_JS_EXPORT_BLOCK = Pattern.compile("export\\s*\\{\\s*([^}]+)\\s*\\}");
    private static final Pattern PAT_JS_EXPORT_DESTRUCT = Pattern.compile("export\\s+(?:const|let|var)\\s*\\{\\s*([^}]+)\\s*\\}");
    private static final Pattern PAT_MODULE_EXPORTS = Pattern.compile("module\\.exports\\s*=\\s*(?:[a-zA-Z_$][\\w$]*|\\{([^}]+)\\})");
    private static final Pattern PAT_CLASS_DECL = Pattern.compile(
            "(?:export\\s+)?(?:default\\s+)?class\\s+([a-zA-Z_$][\\w$]*)(?:\\s+extends\\s+([a-zA-Z_$][\\w$]*))?");
    private static final Pattern PAT_CLASS_METHOD = Pattern.compile(
            "(?:(?:static|async|get|set|#)\\s+)*([a-zA-Z_$][\\w$]*)\\s*\\(([^)]*)\\)\\s*\\{");
    private static final Pattern PAT_CLASS_PROP = Pattern.compile(
            "(?:(?:static|readonly|#)\\s+)?([a-zA-Z_$][\\w$]*)\\s*(?:=[^{;\\n]+)?;");
    private static final Pattern PAT_FUNC_SIG = Pattern.compile("(?:export\\s+)?(?:default\\s+)?function\\s+([a-zA-Z_$][\\w$]*)\\s*(\\([^)]*\\))");
    private static final Pattern PAT_ARROW_FUNC = Pattern.compile("(?:export\\s+)?(?:const|let|var)\\s+([a-zA-Z_$][\\w$]*)\\s*=\\s*(\\([^)]*\\)|[a-zA-Z_$][\\w$]*)\\s*=>");
    private static ProjectSymbolIndex instance;
    // Maps class name → list of member CompletionItems (methods + properties)
    private final Map<String, List<CompletionItem>> classMembers = new HashMap<>();
    // Also maps absolute file path → class members for cross-file class resolution
    private final Map<String, Map<String, List<CompletionItem>>> fileClassMembers = new HashMap<>();
    private final List<CompletionItem> cssClassItems = new ArrayList<>();
    private final List<CompletionItem> cssIdItems = new ArrayList<>();
    private final List<CompletionItem> htmlIdItems = new ArrayList<>();
    // Maps absolute file path to its exported CompletionItems
    private final Map<String, List<CompletionItem>> jsFileExports = new HashMap<>();
    private String projectRoot = null;

    private ProjectSymbolIndex() {
    }

    public static synchronized ProjectSymbolIndex getInstance() {
        if (instance == null) {
            instance = new ProjectSymbolIndex();
        }
        return instance;
    }

    public static File getProjectRoot(File file) {
        return ProjectRepository.findProjectRoot(file);
    }

    public void buildIndex(File rootDir) {
        if (rootDir == null) return;
        String rootPath = rootDir.getAbsolutePath();
        if (rootPath.equals(projectRoot)) return;

        projectRoot = rootPath;
        ExecutorProvider.getInstance().runOnIo(() -> {
            Set<String> classNames = new HashSet<>();
            Set<String> cssIds = new HashSet<>();
            Set<String> htmlIds = new HashSet<>();

            indexDirectoryRecursively(rootDir, classNames, cssIds, htmlIds);

            synchronized (this) {
                cssClassItems.clear();
                for (String c : classNames) {
                    cssClassItems.add(new CompletionItem(c, c, "CSS Class", CompletionItem.Type.CSS_VALUE, 0));
                }
                cssIdItems.clear();
                for (String id : cssIds) {
                    cssIdItems.add(new CompletionItem(id, id, "CSS ID", CompletionItem.Type.CSS_VALUE, 0));
                }
                htmlIdItems.clear();
                for (String id : htmlIds) {
                    htmlIdItems.add(new CompletionItem(id, id, "HTML ID", CompletionItem.Type.VALUE, 0));
                }
            }
        });
    }

    private void indexDirectoryRecursively(File dir, Set<String> classNames, Set<String> cssIds, Set<String> htmlIds) {


        List<File> files = com.cocode.vcode.ide.core.autocomplete.VFSManager.getInstance().listCachedFiles(dir);
        if (files == null) {
            // Fallback if VFS isn't built yet
            File[] diskFiles = dir.listFiles();
            if (diskFiles == null) return;
            files = new ArrayList<>();
            for (File f : diskFiles) {
                if (!f.getName().startsWith(".")) {
                    files.add(f);
                }
            }
        }

        for (File f : files) {
            if (f.isDirectory()) {
                indexDirectoryRecursively(f, classNames, cssIds, htmlIds);
            } else {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".css")) {
                    indexCssFile(f, classNames, cssIds);
                } else if (name.endsWith(".html") || name.endsWith(".htm")) {
                    indexHtmlFile(f, classNames, htmlIds);
                } else if (name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".jsx") || name.endsWith(".tsx") || name.endsWith(".mjs")) {
                    indexJsFile(f);
                }
            }
        }
    }

    private void indexCssFile(File file, Set<String> classNames, Set<String> cssIds) {
        String content = readFile(file);
        if (content == null) return;

        Matcher mClass = PAT_CSS_CLASS.matcher(content);
        while (mClass.find()) {
            classNames.add(mClass.group(1));
        }

        Matcher mId = PAT_CSS_ID.matcher(content);
        while (mId.find()) {
            String id = mId.group(1);
            // Ignore hex colors matching exactly 3, 4, 6, or 8 hex digits
            if (id.matches("^([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")) {
                continue;
            }
            cssIds.add(id);
        }
    }

    private void indexHtmlFile(File file, Set<String> classNames, Set<String> htmlIds) {
        String content = readFile(file);
        if (content == null) return;

        Matcher m = PAT_HTML_ID.matcher(content);
        while (m.find()) {
            htmlIds.add(m.group(1));
        }

        Matcher mClass = Pattern.compile("class\\s*=\\s*[\"']([^\"']+)[\"']").matcher(content);
        while (mClass.find()) {
            String[] classes = mClass.group(1).split("\\s+");
            for (String c : classes) {
                if (!c.isEmpty()) {
                    classNames.add(c);
                }
            }
        }
    }

    private void indexJsFile(File file) {
        String content = readFile(file);
        if (content == null) return;
        List<CompletionItem> exports = new ArrayList<>();
        Map<String, String> signatures = getSignatures(content);

        // 2. Standard exports: export const foo ...
        Matcher mDecl = PAT_JS_EXPORT_DECL.matcher(content);
        while (mDecl.find()) {
            String name = mDecl.group(1);
            String sig = signatures.getOrDefault(name, "");
            exports.add(new CompletionItem(name + sig, name, "Export", CompletionItem.Type.VALUE, 0));
        }

        // 3. Export default
        Matcher mDefault = PAT_JS_EXPORT_DEFAULT.matcher(content);
        while (mDefault.find()) {
            String name = mDefault.group(1);
            if (name != null && !name.isEmpty()) {
                String sig = signatures.getOrDefault(name, "");
                exports.add(new CompletionItem(name + sig, name, "Default Export", CompletionItem.Type.VALUE, 0));
            } else {
                // Anonymous default export, try to guess from filename
                String fileName = file.getName();
                int dotIdx = fileName.lastIndexOf('.');
                if (dotIdx > 0) fileName = fileName.substring(0, dotIdx);
                exports.add(new CompletionItem(fileName, fileName, "Default Export", CompletionItem.Type.VALUE, 0));
            }
        }

        // 4. Export blocks: export { a, b as c }
        Matcher mBlock = PAT_JS_EXPORT_BLOCK.matcher(content);
        while (mBlock.find()) {
            String[] names = mBlock.group(1).split(",");
            for (String n : names) {
                String name = n.trim();
                if (name.contains(" as ")) {
                    name = name.split(" as ")[1].trim();
                }
                if (!name.isEmpty()) {
                    String sig = signatures.getOrDefault(name, "");
                    exports.add(new CompletionItem(name + sig, name, "Export", CompletionItem.Type.VALUE, 0));
                }
            }
        }

        // 5. Destructured exports: export const { a, b } = ...
        Matcher mDestruct = PAT_JS_EXPORT_DESTRUCT.matcher(content);
        while (mDestruct.find()) {
            String[] names = mDestruct.group(1).split(",");
            for (String n : names) {
                String name = n.trim().split(":")[0].trim(); // Handle aliases { a: b }
                if (!name.isEmpty()) {
                    exports.add(new CompletionItem(name, name, "Export", CompletionItem.Type.VALUE, 0));
                }
            }
        }

        // 6. module.exports
        Matcher mModExp = PAT_MODULE_EXPORTS.matcher(content);
        while (mModExp.find()) {
            String block = mModExp.group(1);
            if (block != null && !block.isEmpty()) {
                // It's a block: module.exports = { a, b }
                String[] names = block.split(",");
                for (String n : names) {
                    String name = n.trim().split(":")[0].trim();
                    if (!name.isEmpty()) {
                        String sig = signatures.getOrDefault(name, "");
                        exports.add(new CompletionItem(name + sig, name, "module.exports", CompletionItem.Type.VALUE, 0));
                    }
                }
            } else {
                // Just a single assignment (fallback to file name heuristic like default export)
                String fileName = file.getName();
                int dotIdx = fileName.lastIndexOf('.');
                if (dotIdx > 0) fileName = fileName.substring(0, dotIdx);
                exports.add(new CompletionItem(fileName, fileName, "module.exports", CompletionItem.Type.VALUE, 0));
            }
        }

        synchronized (this) {
            try {
                jsFileExports.put(file.getCanonicalPath(), exports);
            } catch (Exception e) {
                jsFileExports.put(file.getAbsolutePath(), exports);
            }
        }
        indexClassMembers(file, content);
    }

    @NonNull
    private Map<String, String> getSignatures(String content) {
        Map<String, String> signatures = new HashMap<>();

        // 1. Find signatures first so we can attach them to exports later
        Matcher mFunc = PAT_FUNC_SIG.matcher(content);
        while (mFunc.find()) {
            signatures.put(mFunc.group(1), mFunc.group(2));
        }
        Matcher mArrow = PAT_ARROW_FUNC.matcher(content);
        while (mArrow.find()) {
            String args = mArrow.group(2);
            if (!args.startsWith("(")) args = "(" + args + ")";
            signatures.put(mArrow.group(1), args);
        }
        return signatures;
    }

    private void indexClassMembers(File file, String content) {
        Map<String, List<CompletionItem>> fileClasses = new HashMap<>();
        Matcher mClass = PAT_CLASS_DECL.matcher(content);
        while (mClass.find()) {
            String className = mClass.group(1);
            List<CompletionItem> members = new ArrayList<>();
            // Find the opening brace of the class body
            int bodyStart = content.indexOf('{', mClass.end());
            if (bodyStart < 0) continue;
            // Find the matching closing brace
            int depth = 1, pos = bodyStart + 1;
            while (pos < content.length() && depth > 0) {
                char c = content.charAt(pos);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                pos++;
            }
            String body = content.substring(bodyStart + 1, pos - 1);
            // Skip constructor for members (still add it)
            Matcher mMethod = PAT_CLASS_METHOD.matcher(body);
            Set<String> seen = new HashSet<>();
            while (mMethod.find()) {
                String name = mMethod.group(1);
                String params = mMethod.group(2);
                if (name.equals("constructor") || seen.contains(name)) {
                    seen.add(name);
                    continue;
                }
                seen.add(name);
                members.add(new CompletionItem(name + "(" + params + ")", name, className + " method", CompletionItem.Type.FUNCTION, 0));
            }
            Matcher mProp = PAT_CLASS_PROP.matcher(body);
            while (mProp.find()) {
                String name = mProp.group(1);
                if (seen.contains(name) || name.length() < 2) continue;
                seen.add(name);
                members.add(new CompletionItem(name, name, className + " property", CompletionItem.Type.VALUE, 0));
            }
            fileClasses.put(className, members);
        }
        synchronized (this) {
            try {
                fileClassMembers.put(file.getCanonicalPath(), fileClasses);
                // Merge into global classMembers map
                classMembers.putAll(fileClasses);
            } catch (Exception e) {
                fileClassMembers.put(file.getAbsolutePath(), fileClasses);
                classMembers.putAll(fileClasses);
            }
        }
    }

    private String readFile(File file) {
        com.cocode.vcode.ide.core.lsp.LspDocument doc = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().getDocument(file.getAbsolutePath());
        if (doc != null && doc.text != null) {
            return doc.text;
        }

        try {
            if (file.length() > 500 * 1024) return null; // Skip files > 500KB
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int read;
                while ((read = br.read(buffer)) != -1) {
                    sb.append(buffer, 0, read);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized List<CompletionItem> getCssClassItems() {
        return new ArrayList<>(cssClassItems);
    }

    public synchronized List<CompletionItem> getCssIdItems() {
        return new ArrayList<>(cssIdItems);
    }

    public synchronized List<CompletionItem> getHtmlIdItems() {
        return new ArrayList<>(htmlIdItems);
    }

    public synchronized List<CompletionItem> getClassMembers(String className) {
        List<CompletionItem> members = classMembers.get(className);
        return members != null ? new ArrayList<>(members) : new ArrayList<>();
    }

    public synchronized List<CompletionItem> getExportsForPath(File currentFile, String importPath) {
        if (currentFile == null || importPath == null) return new ArrayList<>();

        File parent = currentFile.getParentFile();
        if (parent == null) return new ArrayList<>();

        File targetFile = new File(parent, importPath);
        if (!targetFile.exists() && !importPath.endsWith(".js") && !importPath.endsWith(".ts")) {
            targetFile = new File(parent, importPath + ".js");
            if (!targetFile.exists()) targetFile = new File(parent, importPath + ".ts");
        }

        List<CompletionItem> exports = null;
        try {
            exports = jsFileExports.get(targetFile.getCanonicalPath());
        } catch (Exception e) {
            exports = jsFileExports.get(targetFile.getAbsolutePath());
        }

        // If not indexed yet but file exists, index it now (on-demand, synchronous)
        if ((exports == null || exports.isEmpty()) && targetFile.exists()) {
            indexJsFile(targetFile);
            try {
                exports = jsFileExports.get(targetFile.getCanonicalPath());
            } catch (Exception e) {
                exports = jsFileExports.get(targetFile.getAbsolutePath());
            }
        }

        return exports != null ? new ArrayList<>(exports) : new ArrayList<>();
    }
}
