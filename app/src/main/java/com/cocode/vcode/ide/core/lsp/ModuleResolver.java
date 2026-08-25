package com.cocode.vcode.ide.core.lsp;

import java.io.File;

/**
 * Utility for resolving imported module paths to absolute file locations.
 */
public class ModuleResolver {

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
        
        // Try exact path first (e.g. for .js, .json)
        File target = new File(base, importPath);
        if (target.exists() && target.isFile()) {
            return new LspLocation(target.getAbsolutePath(), new LspRange(0, 0, 0, 0));
        }
        
        // Try common JS/TS extensions if extension was omitted
        for (String ext : new String[]{".js", ".ts", ".mjs", ".cjs", ".tsx", ".jsx"}) {
            File withExt = new File(base, importPath + ext);
            if (withExt.exists()) {
                return new LspLocation(withExt.getAbsolutePath(), new LspRange(0, 0, 0, 0));
            }
        }
        
        // Try index files in a directory
        if (target.exists() && target.isDirectory()) {
             for (String ext : new String[]{"index.js", "index.ts", "index.mjs", "index.cjs", "index.tsx", "index.jsx"}) {
                 File indexFile = new File(target, ext);
                 if (indexFile.exists()) {
                     return new LspLocation(indexFile.getAbsolutePath(), new LspRange(0, 0, 0, 0));
                 }
             }
        }
        
        return null;
    }
}
