package com.cocode.vcode.ide.core.language.ts;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsParser;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dedicated AST parser and structural extractor for TypeScript files.
 * Coordinates with JsParser for full-fidelity lexical scanning while providing
 * specialized helpers for interface hierarchies, type aliases, and enums.
 */
public final class TsParser {

    private TsParser() {
    }

    public static JsSyntaxTree parseFull(String text) {
        if (text == null || text.isEmpty()) return null;
        TokenStream stream = JsLexer.tokenize(text);
        return parseFull(text, stream);
    }

    public static JsSyntaxTree parseFull(String text, TokenStream stream) {
        if (text == null || text.isEmpty()) return null;
        if (stream == null) {
            stream = JsLexer.tokenize(text);
        }
        JsSyntaxTree tree = JsParser.parseFull(text, stream);
        if (tree != null && tree.nodesByOffset == null) {
            tree.buildNodesByOffset();
        }
        return tree;
    }

    public static List<Integer> getInterfaces(JsSyntaxTree tree) {
        if (tree == null) return Collections.emptyList();
        List<Integer> list = new ArrayList<>();
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_INTERFACE) {
                list.add(i);
            }
        }
        return list;
    }

    public static List<Integer> getTypeAliases(JsSyntaxTree tree) {
        if (tree == null) return Collections.emptyList();
        List<Integer> list = new ArrayList<>();
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_TYPE_ALIAS) {
                list.add(i);
            }
        }
        return list;
    }

    public static List<Integer> getEnums(JsSyntaxTree tree) {
        if (tree == null) return Collections.emptyList();
        List<Integer> list = new ArrayList<>();
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_ENUM) {
                list.add(i);
            }
        }
        return list;
    }

    public static int findInterfaceOrType(JsSyntaxTree tree, String name) {
        if (tree == null || name == null || name.isEmpty()) return -1;
        for (int i = 1; i < tree.nodeCount; i++) {
            int t = tree.nodeType[i];
            if (t == JsSyntaxTree.N_INTERFACE || t == JsSyntaxTree.N_TYPE_ALIAS || t == JsSyntaxTree.N_CLASS_DECL || t == JsSyntaxTree.N_ENUM) {
                if (name.equals(tree.nodeName[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static List<Integer> getInterfaceMembers(JsSyntaxTree tree, int interfaceNode) {
        if (tree == null || interfaceNode <= 0 || interfaceNode >= tree.nodeCount) {
            return Collections.emptyList();
        }
        List<Integer> members = new ArrayList<>();
        int child = tree.nodeChild[interfaceNode];
        while (child > 0 && child < tree.nodeCount) {
            int t = tree.nodeType[child];
            if (t == JsSyntaxTree.N_METHOD || t == JsSyntaxTree.N_PROPERTY || t == JsSyntaxTree.N_GETTER || t == JsSyntaxTree.N_SETTER) {
                members.add(child);
            }
            child = tree.nodeSibling[child];
        }
        return members;
    }

    public static String extractTypeSnippet(String source, JsSyntaxTree tree, int nodeId) {
        if (source == null || tree == null || nodeId <= 0 || nodeId >= tree.nodeCount) return null;
        int start = tree.nodeStart[nodeId];
        int end = tree.nodeEnd[nodeId];
        if (start >= 0 && end > start && end <= source.length()) {
            return source.substring(start, end).trim();
        }
        return null;
    }
}
