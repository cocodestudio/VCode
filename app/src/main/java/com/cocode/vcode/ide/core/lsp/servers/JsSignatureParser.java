package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;

import java.util.ArrayList;
import java.util.Collections;

public class JsSignatureParser {

    public static LspSignatureHelp parse(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;
        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return null;

        String text = doc.text;
        
        com.cocode.vcode.ide.core.diagnostic.util.TokenStream tokens = com.cocode.vcode.ide.core.language.js.JsLexer.tokenize(text);
        com.cocode.vcode.ide.core.language.js.JsSyntaxTree tree = com.cocode.vcode.ide.core.language.js.JsParser.parseFull(text, tokens);
        com.cocode.vcode.ide.core.language.js.ScopeTree scopeTree = com.cocode.vcode.ide.core.language.js.ScopeTree.build(tree);
        
        int callNode = 0;
        int minLen = Integer.MAX_VALUE;
        for (int j = 1; j < tree.nodeCount; j++) {
            if (tree.nodeType[j] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_CALL_EXPR) {
                if (offset >= tree.nodeStart[j] && offset <= tree.nodeEnd[j]) {
                    int len = tree.nodeEnd[j] - tree.nodeStart[j];
                    if (len < minLen) {
                        minLen = len;
                        callNode = j;
                    }
                }
            }
        }
        
        if (callNode == 0) return null;
        
        String funcName = tree.nodeName[callNode];
        if (funcName == null) return null;
        
        // Ensure offset is strictly after the function name to only show popup in arguments
        if (offset <= tree.nodeStart[callNode] + funcName.length()) {
            return null;
        }
        
        int argIndex = 0;
        int child = (callNode > 0 && callNode < tree.nodeCount) ? tree.nodeChild[callNode] : 0;
        int idx = 0;
        int loop = 0;
        while (child > 0 && child < tree.nodeCount && ++loop <= tree.nodeCount) {
            if (tree.nodeType[child] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_PARAM) {
                if (offset > tree.nodeStart[child] && offset <= tree.nodeEnd[child]) {
                    argIndex = idx;
                    break;
                }
                if (offset > tree.nodeEnd[child]) {
                    argIndex = idx + 1;
                }
                idx++;
            }
            child = tree.nodeSibling[child];
        }

        String baseIdentifier = funcName;
        int dotIdx = funcName.lastIndexOf('.');
        if (dotIdx >= 0) {
            baseIdentifier = funcName.substring(dotIdx + 1);
        }

        int scopeId = (callNode > 0 && callNode < tree.nodeCount) ? scopeTree.findScopeAt(tree.nodeStart[callNode], tree) : 0;
        int[] resolved = scopeTree.lookupSymbol(baseIdentifier, scopeId);
        
        String signature = null;
        String sourceLabel = "Local function";
        
        if (resolved != null) {
            int declNodeId = resolved[1];
            StringBuilder sigBuilder = new StringBuilder();
            int pChild = (declNodeId > 0 && declNodeId < tree.nodeCount) ? tree.nodeChild[declNodeId] : 0;
            boolean first = true;
            int pLoop = 0;
            while (pChild > 0 && pChild < tree.nodeCount && ++pLoop <= tree.nodeCount) {
                if (tree.nodeType[pChild] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_PARAM) {
                    if (!first) sigBuilder.append(", ");
                    sigBuilder.append(tree.nodeName[pChild] != null ? tree.nodeName[pChild] : "arg");
                    first = false;
                }
                pChild = tree.nodeSibling[pChild];
            }
            signature = sigBuilder.toString();
        } else {
            com.cocode.vcode.ide.core.lsp.ProjectIndex index = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance();
            java.util.List<com.cocode.vcode.ide.core.lsp.LspLocation> defs = index.findDefinitions(baseIdentifier);
            com.cocode.vcode.ide.core.lsp.SymbolEntry targetEntry = null;
            
            for (com.cocode.vcode.ide.core.lsp.LspLocation loc : defs) {
                java.util.List<com.cocode.vcode.ide.core.lsp.SymbolEntry> syms = index.getFileSymbols(loc.uri);
                for (com.cocode.vcode.ide.core.lsp.SymbolEntry s : syms) {
                    if (s.name.equals(baseIdentifier) && (s.kind == com.cocode.vcode.ide.core.lsp.SymbolEntry.KIND_FUNCTION || s.kind == com.cocode.vcode.ide.core.lsp.SymbolEntry.KIND_CLASS)) {
                        targetEntry = s;
                        break;
                    }
                }
                if (targetEntry != null) break;
            }
            
            if (targetEntry != null && targetEntry.detail != null) {
                signature = targetEntry.detail;
                sourceLabel = targetEntry.kind == com.cocode.vcode.ide.core.lsp.SymbolEntry.KIND_CLASS ? "Cross-file class" : "Cross-file function";
            }
        }

        if (signature == null) {
            return null;
        }

        LspSignatureHelp.LspSignatureInformation sig = new LspSignatureHelp.LspSignatureInformation(
                funcName + "(" + signature.trim().replaceAll("\\s+", " ") + ")",
                sourceLabel,
                new java.util.ArrayList<>()
        );

        if (!signature.trim().isEmpty()) {
            String[] args = signature.split(",");
            for (String arg : args) {
                sig.parameters.add(new LspSignatureHelp.LspParameterInformation(arg.trim(), null));
            }
        }

        return new LspSignatureHelp(java.util.Collections.singletonList(sig), 0, argIndex);
    }
}
