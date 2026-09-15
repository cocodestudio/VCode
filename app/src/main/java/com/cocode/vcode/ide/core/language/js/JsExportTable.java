package com.cocode.vcode.ide.core.language.js;

import java.util.HashSet;
import java.util.Set;

/**
 * Per-file export table populated from a {@link JsSyntaxTree}.
 *
 * <p>Records every named export in the file. SoA layout: parallel
 * arrays of {@code exportName} and {@code exportSourceUri}, with
 * {@code count} indicating how many entries are live. Entries from
 * the same source URI are stored contiguously (since the table is
 * built in a single pass over one tree), but consumers must not
 * rely on this ordering.
 *
 * <p>The table is rebuilt from scratch every time a file's parse
 * cycle completes (the {@link com.cocode.vcode.ide.core.lsp.ProjectIndex}
 * update hook calls {@link #build(JsSyntaxTree, String)}). The old
 * table is replaced atomically; no incremental diffing is performed.
 */
public final class JsExportTable {

    public String[] exportName;
    public String[] exportSourceUri;
    public int count;

    public JsExportTable(int initialCapacity) {
        exportName      = new String[initialCapacity];
        exportSourceUri = new String[initialCapacity];
    }

    private JsExportTable() {
        exportName      = new String[0];
        exportSourceUri = new String[0];
    }

    /** Empty table singleton. Returned by {@link #build} when there
     *  is nothing to record. */
    public static final JsExportTable EMPTY = new JsExportTable();

    /**
     * Walk {@code tree} and collect the name of every named export.
     * Exported-name sources are:
     * <ul>
     *   <li>Identifiers inside {@code export { a, b, c }} (children
     *       of type {@code N_IDENTIFIER} of an {@code N_EXPORT} parent).</li>
     *   <li>The name of a {@code N_FUNC_DECL}, {@code N_CLASS_DECL},
     *       {@code N_VAR_DECL}, {@code N_INTERFACE},
     *       {@code N_TYPE_ALIAS}, or {@code N_ENUM} that is a direct
     *       child of an {@code N_EXPORT} parent
     *       (e.g. {@code export function foo}, {@code export class Bar},
     *       {@code export const x = 1}, {@code export interface I},
     *       {@code export type T = ...}, {@code export enum E {...}}).</li>
     * </ul>
     *
     * <p>Default exports are <em>not</em> included; the parser's
     * {@code export default} branch does not produce a name-bearing
     * child node, so there is nothing to record.
     *
     * <p>Re-exports ({@code export { x } from './y'}) are also not
     * currently handled by the parser; they will simply be absent
     * from the table.
     */
    public static JsExportTable build(JsSyntaxTree tree, String sourceUri) {
        if (tree == null || sourceUri == null) return EMPTY;
        // Two-pass: first count, then fill. Avoids per-entry grow().
        int cap = 0;
        for (int id = 1; id < tree.nodeCount; id++) {
            if (tree.nodeType[id] != JsSyntaxTree.N_EXPORT) continue;
            int child = tree.nodeChild[id];
            int childLoop = 0;
            while (child > 0 && child < tree.nodeCount && ++childLoop <= tree.nodeCount) {
                if (isNamedExportChild(tree.nodeType[child])) cap++;
                child = tree.nodeSibling[child];
            }
        }
        if (cap == 0) return EMPTY;
        JsExportTable t = new JsExportTable(cap);
        Set<String> seen = new HashSet<>(cap * 2);
        for (int id = 1; id < tree.nodeCount; id++) {
            if (tree.nodeType[id] != JsSyntaxTree.N_EXPORT) continue;
            int child = tree.nodeChild[id];
            int childLoop = 0;
            while (child > 0 && child < tree.nodeCount && ++childLoop <= tree.nodeCount) {
                int cType = tree.nodeType[child];
                String cName = tree.nodeName[child];
                if (isNamedExportChild(cType) && cName != null && !cName.isEmpty()) {
                    // De-duplicate within a file: a parser bug could
                    // emit the same export name twice. Keep the first
                    // occurrence and discard the rest.
                    if (seen.add(cName)) {
                        t.exportName[t.count]      = cName;
                        t.exportSourceUri[t.count] = sourceUri;
                        t.count++;
                    }
                }
                child = tree.nodeSibling[child];
            }
        }
        return t;
    }

    private static boolean isNamedExportChild(int type) {
        switch (type) {
            case JsSyntaxTree.N_IDENTIFIER:
            case JsSyntaxTree.N_FUNC_DECL:
            case JsSyntaxTree.N_CLASS_DECL:
            case JsSyntaxTree.N_VAR_DECL:
            case JsSyntaxTree.N_INTERFACE:
            case JsSyntaxTree.N_TYPE_ALIAS:
            case JsSyntaxTree.N_ENUM:
                return true;
            default:
                return false;
        }
    }

    /** True if the table contains an export of the exact given name. */
    public boolean containsName(String name) {
        if (name == null) return false;
        for (int i = 0; i < count; i++) {
            if (name.equals(exportName[i])) return true;
        }
        return false;
    }

    /**
     * Resolves the AST node ID for the given export name within the syntax tree.
     * Handles 'default' exports, named exports within N_EXPORT, top-level declarations,
     * and CommonJS object literals.
     */
    public static int findExportNode(JsSyntaxTree tree, String exportName) {
        if (tree == null) return 0;

        if ("default".equals(exportName) || exportName == null) {
            for (int i = 1; i < tree.nodeCount; i++) {
                if (tree.nodeType[i] == JsSyntaxTree.N_EXPORT && "default".equals(tree.nodeName[i])) {
                    int child = tree.nodeChild[i];
                    if (child > 0 && child < tree.nodeCount) {
                        if (tree.nodeType[child] == JsSyntaxTree.N_IDENTIFIER) {
                            int topId = findTopLevelDecl(tree, tree.nodeName[child]);
                            return topId > 0 ? topId : child;
                        }
                        return child;
                    }
                    return i;
                }
            }
            int cjs = findCommonJsExportNode(tree);
            if (cjs > 0) return cjs;
            return 0;
        }

        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_EXPORT) {
                int child = tree.nodeChild[i];
                int childLoop = 0;
                while (child > 0 && child < tree.nodeCount && ++childLoop <= tree.nodeCount) {
                    String cName = tree.nodeName[child];
                    if (exportName.equals(cName)) {
                        if (tree.nodeType[child] == JsSyntaxTree.N_IDENTIFIER) {
                            String originalSymbol = tree.nodeTypeAnn[child] != null ? tree.nodeTypeAnn[child] : cName;
                            int topId = findTopLevelDecl(tree, originalSymbol);
                            return topId > 0 ? topId : child;
                        }
                        return child;
                    }
                    child = tree.nodeSibling[child];
                }
            }
        }

        int topDecl = findTopLevelDecl(tree, exportName);
        if (topDecl > 0) return topDecl;

        return 0;
    }

    /**
     * Finds a top-level declaration node with the matching name.
     */
    public static int findTopLevelDecl(JsSyntaxTree tree, String name) {
        if (tree == null || name == null) return 0;
        for (int i = 1; i < tree.nodeCount; i++) {
            int type = tree.nodeType[i];
            if (type == JsSyntaxTree.N_VAR_DECL || type == JsSyntaxTree.N_FUNC_DECL 
                    || type == JsSyntaxTree.N_CLASS_DECL || type == JsSyntaxTree.N_INTERFACE) {
                if (name.equals(tree.nodeName[i])) {
                    return i;
                }
            }
        }
        return 0;
    }

    /**
     * Locates a CommonJS export object literal node if present.
     */
    public static int findCommonJsExportNode(JsSyntaxTree tree) {
        if (tree == null) return 0;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_OBJECT_LITERAL) {
                return i;
            }
        }
        return 0;
    }
}
