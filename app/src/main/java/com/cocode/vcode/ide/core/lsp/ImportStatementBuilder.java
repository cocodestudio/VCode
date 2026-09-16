package com.cocode.vcode.ide.core.lsp;

import java.io.File;

/**
 * Builds the {@code import { X } from '...';} statement that the
 * editor should auto-insert at the top of the current file when the
 * user picks a cross-file completion.
 *
 * <p>Named exports only. Default exports and namespace imports are
 * explicitly out of scope (per the K.3 spec's Do NOT clause).
 *
 * <p>Path resolution is case-sensitive (per A.21): the computed
 * relative path uses the <em>exact</em> filename as it appears on
 * disk, so that {@code ModuleResolver.resolveModulePath} finds it
 * via its case-sensitive directory walk.
 */
public final class ImportStatementBuilder {

    private ImportStatementBuilder() {
    }

    /**
     * Build the import statement for {@code name} exported from
     * {@code sourceFileUri} as seen from {@code currentFileUri}.
     *
     * @param name           the exported symbol name to import
     * @param sourceFileUri  the absolute URI of the file that exports
     *                       {@code name}
     * @param currentFileUri the absolute URI of the file the user is
     *                       editing (the import is being added here)
     * @param currentText    the current text of {@code currentFileUri};
     *                       used only to find the insertion point (top
     *                       of file, after any existing import
     *                       statements)
     * @return the import statement and the offset where it should
     * be inserted, or {@code null} if the two files are the
     * same (no self-import)
     */
    public static Result build(String name, String sourceFileUri,
                               String currentFileUri, String currentText) {
        if (name == null || name.isEmpty()) return null;
        if (sourceFileUri == null || currentFileUri == null) return null;
        File source = new File(sourceFileUri);
        File current = new File(currentFileUri);
        File currentDir = current.getParentFile();
        if (currentDir == null) return null;
        // No self-imports.
        if (sourceFileUri.equals(currentFileUri)) return null;

        String relativePath = computeRelativePath(currentDir, source);
        if (relativePath == null) return null;

        String importLine = "import { " + name + " } from '" + relativePath + "';\n";
        int insertAt = findInsertionOffset(currentText);
        return new Result(importLine, insertAt);
    }

    /**
     * Compute the relative path from {@code fromDir} (a directory)
     * to {@code toFile} (a file). The result is case-preserving: it
     * uses the exact name of {@code toFile} as it appears on disk.
     * Always starts with {@code ./} or {@code ../}.
     */
    static String computeRelativePath(File fromDir, File toFile) {
        if (fromDir == null || toFile == null) return null;
        // Normalize: use absolute paths and split into components.
        String fromPath = fromDir.getAbsolutePath();
        String toPath = toFile.getAbsolutePath();
        String[] fromParts = fromPath.replace('\\', '/').split("/");
        String[] toParts = toPath.replace('\\', '/').split("/");
        // Find common prefix length.
        int common = 0;
        int minLen = Math.min(fromParts.length, toParts.length);
        while (common < minLen && fromParts[common].equals(toParts[common])) {
            common++;
        }
        if (common == 0 && fromParts.length > 0 && toParts.length > 0) {
            // No common root (e.g. different drives on Windows)
            return null;
        }
        // Up-walk from fromDir to the common root.
        int ups = fromParts.length - common;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ups; i++) {
            if (sb.length() > 0) sb.append('/');
            sb.append("..");
        }
        // Down-walk from common to toFile, skipping the final
        // component if it equals the filename (we'll append the
        // filename separately).
        for (int i = common; i < toParts.length; i++) {
            if (sb.length() > 0) sb.append('/');
            String part = toParts[i];
            if (i == toParts.length - 1) {
                // Strip .ts extension, map to .js or omit.
                if (part.equals("index.js") || part.equals("index.ts")) {
                    if (sb.length() == 0) {
                        sb.append("index.js");
                        continue;
                    } else if (sb.charAt(sb.length() - 1) == '/') {
                        sb.setLength(sb.length() - 1); // strip the trailing slash
                        if (sb.length() == 0) sb.append(".");
                        continue;
                    }
                }
                if (part.endsWith(".ts")) {
                    part = part.substring(0, part.length() - 3) + ".js";
                }
            }
            sb.append(part);
        }
        String rel = sb.toString();
        if (rel.isEmpty()) rel = ".";
        if (!rel.startsWith(".") && !rel.startsWith("/")) {
            rel = "./" + rel;
        }
        return rel;
    }

    /**
     * Find the offset in {@code text} where a new import statement
     * should be inserted. The rule:
     * <ul>
     *   <li>If the file is empty, return 0 (insert at the very top).</li>
     *   <li>If the file starts with one or more existing import
     *       statements (lines beginning with {@code import} or
     *       {@code import\{}), insert immediately after the last one,
     *       before the first non-import line.</li>
     *   <li>Otherwise, insert at offset 0 (top of file).</li>
     * </ul>
     * The offset is a character index into {@code text}.
     */
    static int findInsertionOffset(String text) {
        if (text == null || text.isEmpty()) return 0;

        com.cocode.vcode.ide.core.diagnostic.util.TokenStream stream = com.cocode.vcode.ide.core.language.js.JsLexer.tokenize(text);
        com.cocode.vcode.ide.core.language.js.JsSyntaxTree tree = com.cocode.vcode.ide.core.language.js.JsParser.parseTopLevel(text, stream);

        int lastImportEnd = 0;
        for (int id = 1; id < tree.nodeCount; id++) {
            if (tree.nodeType[id] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_IMPORT) {
                if (tree.nodeEnd[id] > lastImportEnd) {
                    lastImportEnd = tree.nodeEnd[id];
                }
            }
        }
        if (lastImportEnd == 0) {
            return 0;
        }

        while (lastImportEnd < text.length()) {
            if (text.charAt(lastImportEnd) == '\n') {
                return lastImportEnd + 1;
            }
            lastImportEnd++;
        }
        return text.length();
    }

    /**
     * Result of an import-statement build: the text to insert and
     * the offset in the current file where it should be inserted.
     * The editor's text-edit pipeline uses the {@code insertAtOffset}
     * to splice the {@code insertText} into the buffer.
     */
    public static final class Result {
        /**
         * The import statement, including the trailing newline.
         */
        public final String insertText;
        /**
         * Offset in the current file's text where the import should
         * be spliced in.
         */
        public final int insertAtOffset;

        public Result(String insertText, int insertAtOffset) {
            this.insertText = insertText;
            this.insertAtOffset = insertAtOffset;
        }
    }


}
