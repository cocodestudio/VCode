package com.cocode.vcode.ide.core.lsp;

import java.io.File;
import java.io.IOException;

/**
 * Utility for resolving imported module paths to absolute file locations.
 */
public class ModuleResolver {

    private static boolean existsCaseSensitive(File parent, String exactName) {
        String[] names = parent.list();
        if (names == null) return false;
        for (String n : names) {
            if (n.equals(exactName)) return true;
        }
        return false;
    }

    /**
     * Resolves a module import path relative to the declaring document.
     *
     * @param docUri     the absolute URI of the document containing the import
     * @param importPath the raw path from the import statement (e.g. "./utils", "../utils/crypto.js")
     * @return the resolved LspLocation with a (0,0) range, or null if not found
     */
    public static LspLocation resolveModulePath(String docUri, String importPath) {
        File base = new File(docUri).getParentFile();
        if (base == null) return null;

        ProjectIndex index = ProjectIndex.getInstance();
        
        // Try exact path first (e.g. for .js, .json)
        File target = new File(base, importPath);
        if (target.getParentFile() != null && existsCaseSensitive(target.getParentFile(), target.getName()) && target.isFile()) {
            return new LspLocation(normalize(target), new LspRange(0, 0, 0, 0));
        }
        if (index != null) {
            String targetNorm = normalize(target);
            if (index.getParseResult(targetNorm) != null || index.getDocument(targetNorm) != null) {
                return new LspLocation(targetNorm, new LspRange(0, 0, 0, 0));
            }
        }
        
        // Try common JS/TS extensions if extension was omitted
        for (String ext : new String[]{".js", ".ts", ".mjs", ".cjs", ".tsx", ".jsx"}) {
            File extTarget = new File(base, importPath + ext);
            if (extTarget.getParentFile() != null && existsCaseSensitive(extTarget.getParentFile(), extTarget.getName())) {
                return new LspLocation(normalize(extTarget), new LspRange(0, 0, 0, 0));
            }
            if (index != null) {
                String extNorm = normalize(extTarget);
                if (index.getParseResult(extNorm) != null || index.getDocument(extNorm) != null) {
                    return new LspLocation(extNorm, new LspRange(0, 0, 0, 0));
                }
            }
        }
        
        // Try index files (e.g. ./utils/index.js)
        if (target.isDirectory()) {
             for (String ext : new String[]{"index.js", "index.ts", "index.mjs", "index.cjs", "index.tsx", "index.jsx"}) {
                 if (existsCaseSensitive(target, ext)) {
                     return new LspLocation(normalize(new File(target, ext)), new LspRange(0, 0, 0, 0));
                 }
             }
        }
        if (index != null) {
            for (String ext : new String[]{"index.js", "index.ts", "index.mjs", "index.cjs", "index.tsx", "index.jsx"}) {
                File idxTarget = new File(target, ext);
                String idxNorm = normalize(idxTarget);
                if (index.getParseResult(idxNorm) != null || index.getDocument(idxNorm) != null) {
                    return new LspLocation(idxNorm, new LspRange(0, 0, 0, 0));
                }
            }
        }
        
        return null;
    }

    private static String normalize(File file) {
        try {
            String path = file.getCanonicalPath();
            String abs = file.getAbsolutePath();
            if (abs.length() >= 2 && abs.charAt(1) == ':' && path.length() >= 2 && path.charAt(1) == ':') {
                if (Character.toLowerCase(abs.charAt(0)) == Character.toLowerCase(path.charAt(0))) {
                    path = abs.charAt(0) + path.substring(1);
                }
            }
            return path;
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }
}
