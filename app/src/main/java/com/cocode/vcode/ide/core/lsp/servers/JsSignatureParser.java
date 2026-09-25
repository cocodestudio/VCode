package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.language.js.JsStandardLibrary;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolves context-aware signature help for JavaScript and TypeScript function and constructor invocations.
 * <p>Identifies the active function call at the cursor position using AST call expressions with a resilient
 * textual parenthesis fallback for transient unclosed calls. Accurately determines active parameter indices
 * across commas while ignoring string literals and comments, and provides rich signature labels, parameter
 * highlights, and documentation previews for local functions, project symbols, and built-in standard library APIs.
 */
public class JsSignatureParser {

    public static LspSignatureHelp parse(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;
        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return null;

        String text = doc.text;

        com.cocode.vcode.ide.core.diagnostic.util.TokenStream tokens = com.cocode.vcode.ide.core.language.js.JsLexer.tokenize(text);
        JsSyntaxTree tree = com.cocode.vcode.ide.core.language.js.JsParser.parseFull(text, tokens);
        com.cocode.vcode.ide.core.language.js.ScopeTree scopeTree = com.cocode.vcode.ide.core.language.js.ScopeTree.build(tree);

        CallFrame innerCall = findInnermostCall(text, offset);
        int callNode = 0;
        int activeOpenParen = -1;
        String funcName = null;

        if (innerCall != null && isCursorInsideCall(text, innerCall.openParen, offset)) {
            activeOpenParen = innerCall.openParen;
            funcName = innerCall.funcName;
            for (int j = 1; j < tree.nodeCount; j++) {
                if (tree.nodeType[j] == JsSyntaxTree.N_CALL_EXPR) {
                    int p = findOpenParenForCall(text, tree, j);
                    if (p == activeOpenParen) {
                        callNode = j;
                        break;
                    }
                }
            }
        }

        // Fallback: search AST nodes if findInnermostCall found no enclosing call
        if (activeOpenParen < 0) {
            for (int j = 1; j < tree.nodeCount; j++) {
                if (tree.nodeType[j] == JsSyntaxTree.N_CALL_EXPR) {
                    int openParen = findOpenParenForCall(text, tree, j);
                    if (openParen >= 0 && isCursorInsideCall(text, openParen, offset)) {
                        if (openParen > activeOpenParen) {
                            activeOpenParen = openParen;
                            callNode = j;
                        }
                    }
                }
            }
            if (callNode > 0) {
                funcName = tree.nodeName[callNode];
            }
        }

        // Secondary fallback: innermost unclosed '(' closest to cursor
        int unclosedParen = findInnermostUnclosedParen(text, offset);
        if (unclosedParen > activeOpenParen && isCursorInsideCall(text, unclosedParen, offset)) {
            String extracted = extractFunctionNameBeforeParen(text, unclosedParen);
            if (extracted != null && !extracted.isEmpty()) {
                activeOpenParen = unclosedParen;
                funcName = extracted;
                callNode = 0;
            }
        }

        if (funcName == null || funcName.isEmpty() || activeOpenParen < 0) {
            return null;
        }

        // Clean up funcName if prefixed with 'new '
        if (funcName.startsWith("new ")) {
            funcName = funcName.substring(4).trim();
        }

        while (funcName.startsWith(".")) {
            funcName = funcName.substring(1).trim();
        }

        if (funcName.isEmpty()) {
            return null;
        }

        int argIndex = computeArgIndex(text, activeOpenParen, offset);

        String baseIdentifier = funcName;
        int dotIdx = funcName.lastIndexOf('.');
        if (dotIdx >= 0) {
            baseIdentifier = funcName.substring(dotIdx + 1);
        }

        int scopeId = (callNode > 0 && callNode < tree.nodeCount)
                ? scopeTree.findScopeAt(tree.nodeStart[callNode], tree)
                : scopeTree.findScopeAt(activeOpenParen, tree);
        int[] resolved = scopeTree.lookupSymbol(baseIdentifier, scopeId);

        String signature = null;
        String sourceLabel = "Local function";
        List<String> parsedParamNames = null;

        // Special case: super(...) call inside derived class constructor or method
        if ("super".equals(baseIdentifier) || "super".equals(funcName)) {
            int enclosingClass = findEnclosingClassNode(tree, activeOpenParen);
            if (enclosingClass > 0 && tree.nodeTypeAnn[enclosingClass] != null) {
                String superName = tree.nodeTypeAnn[enclosingClass];
                int[] superResolved = scopeTree.lookupSymbol(superName, scopeId);
                if (superResolved != null && superResolved[1] > 0 && superResolved[1] < tree.nodeCount) {
                    parsedParamNames = extractClassConstructorParams(tree, superResolved[1], scopeTree, scopeId);
                    signature = String.join(", ", parsedParamNames);
                    sourceLabel = "Super constructor (" + superName + ")";
                } else {
                    JsStandardLibrary.SignatureInfo builtinSuper = JsStandardLibrary.getBuiltinSignature(superName, null);
                    if (builtinSuper != null && builtinSuper.parameters != null) {
                        parsedParamNames = builtinSuper.parameters;
                        signature = String.join(", ", parsedParamNames);
                        sourceLabel = "Super constructor (" + superName + ")";
                    }
                }
            }
        }

        // Check local file declaration in scope
        if (signature == null && resolved != null && resolved[1] > 0 && resolved[1] < tree.nodeCount) {
            int declNodeId = resolved[1];
            int declType = tree.nodeType[declNodeId];

            if (declType == JsSyntaxTree.N_FUNC_DECL || declType == JsSyntaxTree.N_ARROW_FUNC || declType == JsSyntaxTree.N_METHOD) {
                parsedParamNames = extractParamNames(tree, declNodeId);
                signature = String.join(", ", parsedParamNames);
                sourceLabel = (declType == JsSyntaxTree.N_METHOD) ? "Method" : "Local function";
            } else if (declType == JsSyntaxTree.N_CLASS_DECL) {
                parsedParamNames = extractClassConstructorParams(tree, declNodeId, scopeTree, scopeId);
                signature = String.join(", ", parsedParamNames);
                sourceLabel = "Class constructor";
            } else if (declType == JsSyntaxTree.N_VAR_DECL) {
                String typeAnn = tree.nodeTypeAnn[declNodeId];
                if (typeAnn != null && (typeAnn.startsWith("@IMPORT:") || typeAnn.startsWith("@REQUIRE:") || typeAnn.startsWith("@REQUIRE_PROP:"))) {
                    com.cocode.vcode.ide.core.lsp.ProjectIndex pIndex = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance();
                    String modPath = null;
                    String expName = null;
                    if (typeAnn.startsWith("@IMPORT:")) {
                        int colon = typeAnn.indexOf(':', 8);
                        if (colon != -1) {
                            modPath = typeAnn.substring(8, colon);
                            expName = typeAnn.substring(colon + 1);
                        }
                    } else if (typeAnn.startsWith("@REQUIRE_PROP:")) {
                        int colon = typeAnn.indexOf(':', 14);
                        if (colon != -1) {
                            modPath = typeAnn.substring(14, colon);
                            expName = typeAnn.substring(colon + 1);
                        }
                    } else if (typeAnn.startsWith("@REQUIRE:")) {
                        modPath = typeAnn.substring(9);
                        expName = "default";
                    }
                    if (modPath != null && pIndex != null) {
                        com.cocode.vcode.ide.core.lsp.LspLocation loc =
                                com.cocode.vcode.ide.core.lsp.ModuleResolver.resolveModulePath(doc.uri, modPath);
                        if (loc != null && loc.uri != null) {
                            com.cocode.vcode.ide.core.language.js.ParseResult targetPr =
                                    pIndex.getOrParseJsFile(new java.io.File(loc.uri));
                            if (targetPr != null && targetPr.tree != null) {
                                int expNode = com.cocode.vcode.ide.core.language.js.JsExportTable.findExportNode(targetPr.tree, expName);
                                if (expNode <= 0) {
                                    expNode = com.cocode.vcode.ide.core.language.js.JsExportTable.findTopLevelDecl(targetPr.tree, expName);
                                }
                                if (expNode > 0) {
                                    int eType = targetPr.tree.nodeType[expNode];
                                    if (eType == JsSyntaxTree.N_CLASS_DECL) {
                                        parsedParamNames = extractClassConstructorParams(targetPr.tree, expNode, targetPr.scopeTree, 0);
                                        signature = String.join(", ", parsedParamNames);
                                        sourceLabel = "Cross-file class";
                                    } else if (eType == JsSyntaxTree.N_FUNC_DECL || eType == JsSyntaxTree.N_ARROW_FUNC) {
                                        parsedParamNames = extractParamNames(targetPr.tree, expNode);
                                        signature = String.join(", ", parsedParamNames);
                                        sourceLabel = "Cross-file function";
                                    } else if (eType == JsSyntaxTree.N_VAR_DECL) {
                                        int c = targetPr.tree.nodeChild[expNode];
                                        if (c > 0 && c < targetPr.tree.nodeCount) {
                                            int ct = targetPr.tree.nodeType[c];
                                            if (ct == JsSyntaxTree.N_CLASS_DECL) {
                                                parsedParamNames = extractClassConstructorParams(targetPr.tree, c, targetPr.scopeTree, 0);
                                                signature = String.join(", ", parsedParamNames);
                                                sourceLabel = "Cross-file class";
                                            } else if (ct == JsSyntaxTree.N_FUNC_DECL || ct == JsSyntaxTree.N_ARROW_FUNC) {
                                                parsedParamNames = extractParamNames(targetPr.tree, c);
                                                signature = String.join(", ", parsedParamNames);
                                                sourceLabel = "Cross-file function";
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    int child = tree.nodeChild[declNodeId];
                    if (child > 0 && child < tree.nodeCount) {
                        int cType = tree.nodeType[child];
                        if (cType == JsSyntaxTree.N_FUNC_DECL || cType == JsSyntaxTree.N_ARROW_FUNC) {
                            parsedParamNames = extractParamNames(tree, child);
                            signature = String.join(", ", parsedParamNames);
                            sourceLabel = "Function";
                        } else if (cType == JsSyntaxTree.N_CLASS_DECL) {
                            parsedParamNames = extractClassConstructorParams(tree, child, scopeTree, scopeId);
                            signature = String.join(", ", parsedParamNames);
                            sourceLabel = "Class constructor";
                        }
                    }
                }
            }
        }

        // 2b. Check 'this.' method inside class
        if (signature == null && dotIdx >= 0 && funcName.startsWith("this.")) {
            int enclosingClass = findEnclosingClassNode(tree, activeOpenParen);
            if (enclosingClass > 0) {
                int methodNode = findMethodInClassNode(tree, enclosingClass, baseIdentifier);
                if (methodNode > 0) {
                    parsedParamNames = extractParamNames(tree, methodNode);
                    signature = String.join(", ", parsedParamNames);
                    sourceLabel = "Method";
                } else {
                    String superName = tree.nodeTypeAnn[enclosingClass];
                    if (superName != null && !superName.isEmpty()) {
                        int[] superResolved = scopeTree.lookupSymbol(superName, scopeId);
                        if (superResolved != null && superResolved[1] > 0 && superResolved[1] < tree.nodeCount) {
                            int superNode = superResolved[1];
                            if (tree.nodeType[superNode] == JsSyntaxTree.N_CLASS_DECL) {
                                int sMethod = findMethodInClassNode(tree, superNode, baseIdentifier);
                                if (sMethod > 0) {
                                    parsedParamNames = extractParamNames(tree, sMethod);
                                    signature = String.join(", ", parsedParamNames);
                                    sourceLabel = "Method";
                                }
                            }
                        }
                    }
                }
            }
        }

        // Check object literal member if receiver is in scope (e.g. mathUtils.add(|))
        if (signature == null && dotIdx >= 0) {
            String receiver = funcName.substring(0, dotIdx);
            int[] recResolved = scopeTree.lookupSymbol(receiver, scopeId);
            if (recResolved != null && recResolved[1] > 0 && recResolved[1] < tree.nodeCount) {
                int recNodeId = recResolved[1];
                int recType = tree.nodeType[recNodeId];
                int objNode = (recType == JsSyntaxTree.N_VAR_DECL) ? tree.nodeChild[recNodeId] : recNodeId;
                if (objNode > 0 && objNode < tree.nodeCount && tree.nodeType[objNode] == JsSyntaxTree.N_OBJECT_LITERAL) {
                    int mChild = tree.nodeChild[objNode];
                    int mGuard = 0;
                    while (mChild > 0 && mChild < tree.nodeCount && ++mGuard <= tree.nodeCount) {
                        if (baseIdentifier.equals(tree.nodeName[mChild])) {
                            int mType = tree.nodeType[mChild];
                            if (mType == JsSyntaxTree.N_METHOD) {
                                parsedParamNames = extractParamNames(tree, mChild);
                                signature = String.join(", ", parsedParamNames);
                                sourceLabel = "Method";
                                break;
                            } else if (mType == JsSyntaxTree.N_PROPERTY) {
                                int propVal = tree.nodeChild[mChild];
                                if (propVal > 0 && propVal < tree.nodeCount) {
                                    int pvType = tree.nodeType[propVal];
                                    if (pvType == JsSyntaxTree.N_FUNC_DECL || pvType == JsSyntaxTree.N_ARROW_FUNC) {
                                        parsedParamNames = extractParamNames(tree, propVal);
                                        signature = String.join(", ", parsedParamNames);
                                        sourceLabel = "Method";
                                        break;
                                    }
                                }
                            }
                        }
                        mChild = tree.nodeSibling[mChild];
                    }
                }
            }
        }

        // Check class instance method (e.g. p.getName(|))
        if (signature == null && dotIdx >= 0 && !funcName.startsWith("this.")) {
            String receiver = funcName.substring(0, dotIdx);
            File currentFile = (doc != null && doc.uri != null) ? new File(doc.uri) : null;
            com.cocode.vcode.ide.core.language.js.JsSemanticLinter.ResolvedMethod rm =
                    com.cocode.vcode.ide.core.language.js.JsSemanticLinter.resolveInstanceMethod(
                            receiver, baseIdentifier, activeOpenParen, currentFile, doc != null ? doc.text : null, tree, scopeTree, com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance());
            if (rm != null) {
                parsedParamNames = extractParamNames(rm.tree, rm.methodNode);
                signature = String.join(", ", parsedParamNames);
                sourceLabel = rm.isCrossFile ? "Cross-file method" : "Method";
            }
        }

        // Check built-in signatures from standard library
        if (signature == null) {
            String receiverType = null;
            if (dotIdx >= 0) {
                String receiver = funcName.substring(0, dotIdx);
                int[] recResolved = scopeTree.lookupSymbol(receiver, scopeId);
                if (recResolved != null && recResolved[1] > 0 && recResolved[1] < tree.nodeCount) {
                    receiverType = tree.nodeTypeAnn[recResolved[1]];
                }
            }

            JsStandardLibrary.SignatureInfo builtin = JsStandardLibrary.getBuiltinSignature(funcName, receiverType);
            if (builtin != null) {
                String paramsStr = String.join(", ", builtin.parameters);
                String displayLabel = baseIdentifier + "(" + paramsStr + ")";

                List<LspSignatureHelp.LspParameterInformation> paramInfoList = new ArrayList<>();
                for (String p : builtin.parameters) {
                    paramInfoList.add(new LspSignatureHelp.LspParameterInformation(p, null));
                }

                LspSignatureHelp.LspSignatureInformation sig = new LspSignatureHelp.LspSignatureInformation(
                        displayLabel,
                        builtin.doc != null && !builtin.doc.isEmpty() ? builtin.doc : "Built-in method",
                        paramInfoList
                );
                return new LspSignatureHelp(Collections.singletonList(sig), 0, argIndex, activeOpenParen);
            }
        }

        // Fallback to ProjectIndex definitions for cross-file and project symbols
        if (signature == null) {
            com.cocode.vcode.ide.core.lsp.ProjectIndex index = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance();
            List<com.cocode.vcode.ide.core.lsp.LspLocation> defs = index.findDefinitions(baseIdentifier);
            com.cocode.vcode.ide.core.lsp.SymbolEntry targetEntry = null;

            for (com.cocode.vcode.ide.core.lsp.LspLocation loc : defs) {
                List<com.cocode.vcode.ide.core.lsp.SymbolEntry> entries = index.getFileSymbols(loc.uri);
                if (entries == null) continue;
                for (com.cocode.vcode.ide.core.lsp.SymbolEntry entry : entries) {
                    if (entry.name.equals(baseIdentifier) &&
                            (entry.kind == com.cocode.vcode.ide.core.lsp.SymbolEntry.KIND_FUNCTION ||
                                    entry.kind == com.cocode.vcode.ide.core.lsp.SymbolEntry.KIND_METHOD ||
                                    entry.kind == com.cocode.vcode.ide.core.lsp.SymbolEntry.KIND_CLASS)) {
                        targetEntry = entry;
                        break;
                    }
                }
                if (targetEntry != null) break;
            }

            if (targetEntry != null) {
                if (targetEntry.detail != null) {
                    signature = targetEntry.detail;
                } else if (targetEntry.kind == com.cocode.vcode.ide.core.lsp.SymbolEntry.KIND_CLASS) {
                    signature = "";
                }
                boolean isSameFile = doc != null && doc.uri != null && (doc.uri.equals(targetEntry.uri) || new java.io.File(doc.uri).equals(new java.io.File(targetEntry.uri)));
                if (isSameFile) {
                    if (targetEntry.kind == com.cocode.vcode.ide.core.lsp.SymbolEntry.KIND_CLASS) {
                        sourceLabel = "Class constructor";
                    } else if (targetEntry.kind == com.cocode.vcode.ide.core.lsp.SymbolEntry.KIND_METHOD) {
                        sourceLabel = "Method";
                    } else {
                        sourceLabel = "Local function";
                    }
                } else {
                    if (targetEntry.kind == com.cocode.vcode.ide.core.lsp.SymbolEntry.KIND_CLASS) {
                        sourceLabel = "Cross-file class";
                    } else if (targetEntry.kind == com.cocode.vcode.ide.core.lsp.SymbolEntry.KIND_METHOD) {
                        sourceLabel = "Cross-file method";
                    } else {
                        sourceLabel = "Cross-file function";
                    }
                }
            }
        }

        if (signature == null) {
            return null;
        }

        LspSignatureHelp.LspSignatureInformation sig = new LspSignatureHelp.LspSignatureInformation(
                baseIdentifier + "(" + signature.trim().replaceAll("\\s+", " ") + ")",
                sourceLabel,
                new ArrayList<>()
        );

        if (parsedParamNames != null) {
            for (String arg : parsedParamNames) {
                sig.parameters.add(new LspSignatureHelp.LspParameterInformation(arg, null));
            }
        } else if (!signature.trim().isEmpty()) {
            String[] args = signature.split(",");
            for (String arg : args) {
                sig.parameters.add(new LspSignatureHelp.LspParameterInformation(arg.trim(), null));
            }
        }

        return new LspSignatureHelp(Collections.singletonList(sig), 0, argIndex, activeOpenParen);
    }

    private static List<String> extractParamNames(JsSyntaxTree tree, int funcNode) {
        List<String> list = new ArrayList<>();
        if (tree == null || funcNode <= 0 || funcNode >= tree.nodeCount) return list;
        int pChild = tree.nodeChild[funcNode];
        int pLoop = 0;
        while (pChild > 0 && pChild < tree.nodeCount && ++pLoop <= tree.nodeCount) {
            if (tree.nodeType[pChild] == JsSyntaxTree.N_PARAM) {
                list.add(tree.nodeName[pChild] != null ? tree.nodeName[pChild] : "arg");
            }
            pChild = tree.nodeSibling[pChild];
        }
        return list;
    }

    private static List<String> extractClassConstructorParams(JsSyntaxTree tree, int classNodeId, com.cocode.vcode.ide.core.language.js.ScopeTree scopeTree, int scopeId) {
        if (tree == null || classNodeId <= 0 || classNodeId >= tree.nodeCount)
            return Collections.emptyList();
        int child = tree.nodeChild[classNodeId];
        int guard = 0;
        while (child > 0 && child < tree.nodeCount && ++guard <= tree.nodeCount) {
            if (tree.nodeType[child] == JsSyntaxTree.N_METHOD && "constructor".equals(tree.nodeName[child])) {
                return extractParamNames(tree, child);
            }
            child = tree.nodeSibling[child];
        }
        // Check superclass
        String superName = tree.nodeTypeAnn[classNodeId];
        if (superName != null && !superName.isEmpty()) {
            if (scopeTree != null) {
                int[] superResolved = scopeTree.lookupSymbol(superName, scopeId);
                if (superResolved != null && superResolved[1] > 0 && superResolved[1] < tree.nodeCount) {
                    if (tree.nodeType[superResolved[1]] == JsSyntaxTree.N_CLASS_DECL) {
                        return extractClassConstructorParams(tree, superResolved[1], scopeTree, scopeId);
                    }
                }
            }
            JsStandardLibrary.SignatureInfo builtinSuper = JsStandardLibrary.getBuiltinSignature(superName, null);
            if (builtinSuper != null && builtinSuper.parameters != null) {
                return builtinSuper.parameters;
            }
        }
        return Collections.emptyList();
    }

    private static int findEnclosingClassNode(JsSyntaxTree tree, int offset) {
        if (tree == null || tree.nodeCount <= 1) return 0;
        int bestClass = 0;
        int minLen = Integer.MAX_VALUE;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_CLASS_DECL) {
                if (offset >= tree.nodeStart[i] && offset <= tree.nodeEnd[i]) {
                    int len = tree.nodeEnd[i] - tree.nodeStart[i];
                    if (len < minLen) {
                        minLen = len;
                        bestClass = i;
                    }
                }
            }
        }
        return bestClass;
    }

    private static int findOpenParenForCall(String text, JsSyntaxTree tree, int node) {
        String name = tree.nodeName[node];
        int start = tree.nodeStart[node];
        int searchFrom = (name != null && !name.isEmpty()) ? start + name.length() : start;
        int depth = 0;
        for (int i = searchFrom; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') {
                if (depth > 0) depth--;
            } else if (c == '(' && depth == 0) return i;
            else if (depth == 0 && !Character.isWhitespace(c) && c != '\n' && c != '\r') break;
        }
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') return i;
            if (c == ';' || c == '{' || c == '}') break;
        }
        return -1;
    }

    public static boolean isCursorInsideCall(String text, int openParen, int offset) {
        if (offset <= openParen || openParen < 0 || openParen >= text.length()) return false;
        int closeParen = findMatchingParen(text, openParen);
        if (closeParen >= 0) {
            return offset <= closeParen;
        }
        // Unclosed call: ensure no statement boundary ';' or block '}' at depth 0 between openParen and offset
        int depth = 0;
        int tplDepth = 0;
        int[] tplBraceStack = new int[32];
        boolean inSingle = false, inDouble = false;
        for (int i = openParen + 1; i < offset && i < text.length(); i++) {
            char c = text.charAt(i);
            char prev = (i > openParen + 1) ? text.charAt(i - 1) : 0;
            if (inSingle) {
                if (c == '\'' && prev != '\\') inSingle = false;
                continue;
            }
            if (inDouble) {
                if (c == '"' && prev != '\\') inDouble = false;
                continue;
            }

            boolean inTplText = (tplDepth > 0 && tplBraceStack[tplDepth - 1] == -1);
            if (inTplText) {
                if (c == '`' && prev != '\\') {
                    tplDepth--;
                    continue;
                }
                if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                    tplBraceStack[tplDepth - 1] = 1;
                    i++;
                    continue;
                }
                continue;
            }

            if (c == '\'') { inSingle = true; continue; }
            if (c == '"') { inDouble = true; continue; }
            if (c == '`') {
                if (tplDepth < tplBraceStack.length) {
                    tplBraceStack[tplDepth++] = -1;
                }
                continue;
            }
            if (tplDepth > 0 && tplBraceStack[tplDepth - 1] > 0) {
                if (c == '{') {
                    tplBraceStack[tplDepth - 1]++;
                } else if (c == '}') {
                    tplBraceStack[tplDepth - 1]--;
                    if (tplBraceStack[tplDepth - 1] == 0) {
                        tplBraceStack[tplDepth - 1] = -1;
                        continue;
                    }
                }
            }

            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') {
                if (depth > 0) depth--;
                else return false;
            } else if (c == ';' && depth == 0 && tplDepth == 0) {
                return false;
            }
        }
        return true;
    }

    public static int findInnermostUnclosedParen(String text, int offset) {
        CallFrame frame = findInnermostCall(text, offset);
        return frame != null ? frame.openParen : -1;
    }

    public static String extractFunctionNameBeforeParen(String text, int openParen) {
        int end = openParen - 1;
        while (end >= 0 && Character.isWhitespace(text.charAt(end))) end--;
        if (end < 0) return null;

        // Skip generic type arguments e.g. func<T, U>( or new Map<string, number>(
        if (text.charAt(end) == '>') {
            int gDepth = 0;
            int gIdx = end;
            while (gIdx >= 0) {
                char gc = text.charAt(gIdx);
                if (gc == '>') gDepth++;
                else if (gc == '<') {
                    gDepth--;
                    if (gDepth == 0) {
                        end = gIdx - 1;
                        while (end >= 0 && Character.isWhitespace(text.charAt(end))) end--;
                        break;
                    }
                } else if (gc == ';' || gc == '{' || gc == '}') {
                    break;
                }
                gIdx--;
            }
        }
        if (end < 0) return null;

        int start = end;
        while (start >= 0) {
            char c = text.charAt(start);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.') {
                start--;
            } else {
                break;
            }
        }
        String candidate = text.substring(start + 1, end + 1).trim();
        while (candidate.startsWith(".")) {
            candidate = candidate.substring(1).trim();
        }
        if (candidate.startsWith("new ")) {
            candidate = candidate.substring(4).trim();
        }

        // Non-callable language statements that take parens
        if ("if".equals(candidate) || "for".equals(candidate) || "while".equals(candidate)
                || "switch".equals(candidate) || "catch".equals(candidate) || "with".equals(candidate)) {
            return null;
        }

        return candidate.isEmpty() ? null : candidate;
    }

    public static int computeArgIndex(String text, int openParenOffset, int cursorOffset) {
        if (text == null || openParenOffset < 0 || cursorOffset <= openParenOffset) return 0;
        int depth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        int tplDepth = 0;
        int[] tplBraceStack = new int[32];
        int argIndex = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        int limit = Math.min(cursorOffset, text.length());

        for (int i = openParenOffset + 1; i < limit; i++) {
            char c = text.charAt(i);
            char prev = (i > openParenOffset + 1) ? text.charAt(i - 1) : 0;

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (prev == '*' && c == '/') inBlockComment = false;
                continue;
            }
            if (inSingle) {
                if (c == '\'' && prev != '\\') inSingle = false;
                continue;
            }
            if (inDouble) {
                if (c == '"' && prev != '\\') inDouble = false;
                continue;
            }

            boolean inTplText = (tplDepth > 0 && tplBraceStack[tplDepth - 1] == -1);
            if (inTplText) {
                if (c == '`' && prev != '\\') {
                    tplDepth--;
                    continue;
                }
                if (c == '$' && i + 1 < limit && text.charAt(i + 1) == '{') {
                    tplBraceStack[tplDepth - 1] = 1;
                    i++;
                    continue;
                }
                continue;
            }

            if (c == '/' && i + 1 < limit) {
                char next = text.charAt(i + 1);
                if (next == '/') {
                    inLineComment = true;
                    i++;
                    continue;
                } else if (next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }

            if (c == '\'') {
                inSingle = true;
                continue;
            }
            if (c == '"') {
                inDouble = true;
                continue;
            }
            if (c == '`') {
                if (tplDepth < tplBraceStack.length) {
                    tplBraceStack[tplDepth++] = -1;
                }
                continue;
            }
            if (tplDepth > 0 && tplBraceStack[tplDepth - 1] > 0) {
                if (c == '{') {
                    tplBraceStack[tplDepth - 1]++;
                } else if (c == '}') {
                    tplBraceStack[tplDepth - 1]--;
                    if (tplBraceStack[tplDepth - 1] == 0) {
                        tplBraceStack[tplDepth - 1] = -1;
                        continue;
                    }
                }
            }

            if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (depth > 0) depth--;
            } else if (c == '[') {
                bracketDepth++;
            } else if (c == ']') {
                if (bracketDepth > 0) bracketDepth--;
            } else if (c == '{') {
                braceDepth++;
            } else if (c == '}') {
                if (braceDepth > 0) braceDepth--;
            } else if (c == ',' && depth == 0 && bracketDepth == 0 && braceDepth == 0 && tplDepth == 0) {
                argIndex++;
            }
        }
        return argIndex;
    }

    public static int findMatchingParen(String text, int openParenOffset) {
        if (text == null || openParenOffset < 0 || openParenOffset >= text.length()) return -1;
        int depth = 0;
        int tplDepth = 0;
        int[] tplBraceStack = new int[32];
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = openParenOffset; i < text.length(); i++) {
            char c = text.charAt(i);
            char prev = (i > openParenOffset) ? text.charAt(i - 1) : 0;

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (prev == '*' && c == '/') inBlockComment = false;
                continue;
            }
            if (inSingle) {
                if (c == '\'' && prev != '\\') inSingle = false;
                continue;
            }
            if (inDouble) {
                if (c == '"' && prev != '\\') inDouble = false;
                continue;
            }

            boolean inTplText = (tplDepth > 0 && tplBraceStack[tplDepth - 1] == -1);
            if (inTplText) {
                if (c == '`' && prev != '\\') {
                    tplDepth--;
                    continue;
                }
                if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                    tplBraceStack[tplDepth - 1] = 1;
                    i++;
                    continue;
                }
                continue;
            }

            if (c == '/' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == '/') {
                    inLineComment = true;
                    i++;
                    continue;
                } else if (next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }

            if (c == '\'') {
                inSingle = true;
                continue;
            }
            if (c == '"') {
                inDouble = true;
                continue;
            }
            if (c == '`') {
                if (tplDepth < tplBraceStack.length) {
                    tplBraceStack[tplDepth++] = -1;
                }
                continue;
            }
            if (tplDepth > 0 && tplBraceStack[tplDepth - 1] > 0) {
                if (c == '{') {
                    tplBraceStack[tplDepth - 1]++;
                } else if (c == '}') {
                    tplBraceStack[tplDepth - 1]--;
                    if (tplBraceStack[tplDepth - 1] == 0) {
                        tplBraceStack[tplDepth - 1] = -1;
                        continue;
                    }
                }
            }

            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static final class CallFrame {
        public final int openParen;
        public final String funcName;

        public CallFrame(int openParen, String funcName) {
            this.openParen = openParen;
            this.funcName = funcName;
        }
    }

    public static CallFrame findInnermostCall(String text, int cursorOffset) {
        if (text == null || cursorOffset <= 0 || cursorOffset > text.length()) return null;

        List<CallFrame> stack = new ArrayList<>();
        int tplDepth = 0;
        int[] tplBraceStack = new int[32];
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < cursorOffset && i < text.length(); i++) {
            char c = text.charAt(i);
            char prev = (i > 0) ? text.charAt(i - 1) : 0;

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (prev == '*' && c == '/') inBlockComment = false;
                continue;
            }
            if (inSingle) {
                if (c == '\'' && prev != '\\') inSingle = false;
                continue;
            }
            if (inDouble) {
                if (c == '"' && prev != '\\') inDouble = false;
                continue;
            }

            boolean inTplText = (tplDepth > 0 && tplBraceStack[tplDepth - 1] == -1);
            if (inTplText) {
                if (c == '`' && prev != '\\') {
                    tplDepth--;
                    continue;
                }
                if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                    tplBraceStack[tplDepth - 1] = 1;
                    i++;
                    continue;
                }
                continue;
            }

            if (c == '/' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == '/') {
                    inLineComment = true;
                    i++;
                    continue;
                } else if (next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }

            if (c == '\'') { inSingle = true; continue; }
            if (c == '"') { inDouble = true; continue; }
            if (c == '`') {
                if (tplDepth < tplBraceStack.length) {
                    tplBraceStack[tplDepth++] = -1;
                }
                continue;
            }
            if (tplDepth > 0 && tplBraceStack[tplDepth - 1] > 0) {
                if (c == '{') {
                    tplBraceStack[tplDepth - 1]++;
                } else if (c == '}') {
                    tplBraceStack[tplDepth - 1]--;
                    if (tplBraceStack[tplDepth - 1] == 0) {
                        tplBraceStack[tplDepth - 1] = -1;
                        continue;
                    }
                }
            }

            if (c == '(') {
                String func = extractFunctionNameBeforeParen(text, i);
                if (func != null && !func.isEmpty()) {
                    stack.add(new CallFrame(i, func));
                } else {
                    stack.add(new CallFrame(i, null));
                }
            } else if (c == ')') {
                if (!stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
            } else if (c == ';' && tplDepth == 0) {
                boolean hasCall = false;
                for (CallFrame f : stack) {
                    if (f.funcName != null) { hasCall = true; break; }
                }
                if (!hasCall) {
                    stack.clear();
                }
            }
        }

        for (int j = stack.size() - 1; j >= 0; j--) {
            CallFrame f = stack.get(j);
            if (f.funcName != null) {
                return f;
            }
        }
        return null;
    }

    public static int findActiveCallOpenParen(String text, int cursorOffset) {
        CallFrame frame = findInnermostCall(text, cursorOffset);
        return frame != null ? frame.openParen : -1;
    }

    /**
     * Determines whether SignatureHelp popup should be shown at the current cursor offset.
     * <p>
     * SignatureHelp only auto-triggers when:
     * 1. The current argument is empty (e.g. immediately after '(' or ',' with no entered argument).
     * 2. The user starts typing a fresh argument (typing the leading identifier/literal at the start of the argument).
     * 3. The popup is already visible and updating parameters within the active call.
     *
     * It is suppressed when:
     * - The cursor is inside a bracket subscript [...] (e.g. user[key]) of an outer argument.
     * - The cursor is in the middle of an already entered argument expression (e.g. user moves caret into existing text).
     * - The cursor is in a template string outside of a callable expression.
     */
    public static boolean shouldTriggerSignatureHelp(String text, int openParen, int cursorOffset, boolean isTextChange) {
        return shouldTriggerSignatureHelp(text, openParen, cursorOffset, isTextChange, false);
    }

    public static boolean shouldTriggerSignatureHelp(String text, int openParen, int cursorOffset, boolean isTextChange, boolean isAlreadyVisible) {
        if (openParen < 0) {
            openParen = findActiveCallOpenParen(text, cursorOffset);
        }
        if (text == null || openParen < 0 || openParen >= text.length()) return false;
        if (cursorOffset <= openParen || cursorOffset > text.length()) return false;

        // Find the start of the current argument (after openParen or after previous comma at call depth 0)
        int depth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        int tplDepth = 0;
        int[] tplBraceStack = new int[32];
        boolean inSingle = false, inDouble = false;
        int lastComma = -1;

        for (int i = openParen + 1; i < cursorOffset; i++) {
            char c = text.charAt(i);
            char prev = (i > openParen + 1) ? text.charAt(i - 1) : 0;
            if (inSingle) {
                if (c == '\'' && prev != '\\') inSingle = false;
                continue;
            }
            if (inDouble) {
                if (c == '"' && prev != '\\') inDouble = false;
                continue;
            }

            boolean inTplText = (tplDepth > 0 && tplBraceStack[tplDepth - 1] == -1);
            if (inTplText) {
                if (c == '`' && prev != '\\') {
                    tplDepth--;
                    continue;
                }
                if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                    tplBraceStack[tplDepth - 1] = 1;
                    i++;
                    continue;
                }
                continue;
            }

            if (c == '\'') { inSingle = true; continue; }
            if (c == '"') { inDouble = true; continue; }
            if (c == '`') {
                if (tplDepth < tplBraceStack.length) {
                    tplBraceStack[tplDepth++] = -1;
                }
                continue;
            }
            if (tplDepth > 0 && tplBraceStack[tplDepth - 1] > 0) {
                if (c == '{') {
                    tplBraceStack[tplDepth - 1]++;
                } else if (c == '}') {
                    tplBraceStack[tplDepth - 1]--;
                    if (tplBraceStack[tplDepth - 1] == 0) {
                        tplBraceStack[tplDepth - 1] = -1;
                        continue;
                    }
                }
            }

            if (c == '(') depth++;
            else if (c == ')') { if (depth > 0) depth--; }
            else if (c == '[') bracketDepth++;
            else if (c == ']') { if (bracketDepth > 0) bracketDepth--; }
            else if (c == '{') braceDepth++;
            else if (c == '}') { if (braceDepth > 0) braceDepth--; }
            else if (c == ',' && depth == 0 && bracketDepth == 0 && braceDepth == 0 && tplDepth == 0) {
                lastComma = i;
            }
        }

        // Suppress if inside bracket subscript [...], object literal/block {...}, inner parens (...),
        // or inside a template string `...` opened for this function call's argument.
        if (bracketDepth > 0 || braceDepth > 0 || depth > 0 || tplDepth > 0) {
            return false;
        }

        int argStart = (lastComma >= 0) ? lastComma + 1 : openParen + 1;

        // Find end of current argument (next comma or closing paren at depth 0)
        int argEnd = text.length();
        int fDepth = 0;
        int fBracket = 0;
        int fBrace = 0;
        int fTplDepth = 0;
        int[] fTplBrace = new int[32];
        boolean fSingle = inSingle, fDouble = inDouble;

        for (int i = cursorOffset; i < text.length(); i++) {
            char c = text.charAt(i);
            char prev = (i > cursorOffset) ? text.charAt(i - 1) : 0;
            if (fSingle) {
                if (c == '\'' && prev != '\\') fSingle = false;
                continue;
            }
            if (fDouble) {
                if (c == '"' && prev != '\\') fDouble = false;
                continue;
            }

            boolean fInTplText = (fTplDepth > 0 && fTplBrace[fTplDepth - 1] == -1);
            if (fInTplText) {
                if (c == '`' && prev != '\\') {
                    fTplDepth--;
                    continue;
                }
                if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                    fTplBrace[fTplDepth - 1] = 1;
                    i++;
                    continue;
                }
                continue;
            }

            if (c == '\'') { fSingle = true; continue; }
            if (c == '"') { fDouble = true; continue; }
            if (c == '`') {
                if (fTplDepth < fTplBrace.length) {
                    fTplBrace[fTplDepth++] = -1;
                }
                continue;
            }
            if (fTplDepth > 0 && fTplBrace[fTplDepth - 1] > 0) {
                if (c == '{') {
                    fTplBrace[fTplDepth - 1]++;
                } else if (c == '}') {
                    fTplBrace[fTplDepth - 1]--;
                    if (fTplBrace[fTplDepth - 1] == 0) {
                        fTplBrace[fTplDepth - 1] = -1;
                        continue;
                    }
                }
            }

            if (c == '(') fDepth++;
            else if (c == ')') {
                if (fDepth > 0) fDepth--;
                else { argEnd = i; break; }
            } else if (c == '[') fBracket++;
            else if (c == ']') { if (fBracket > 0) fBracket--; }
            else if (c == '{') fBrace++;
            else if (c == '}') { if (fBrace > 0) fBrace--; }
            else if (c == ',' && fDepth == 0 && fBracket == 0 && fBrace == 0 && fTplDepth == 0) {
                argEnd = i;
                break;
            } else if (c == ';' && fDepth == 0 && fTplDepth == 0) {
                argEnd = i;
                break;
            }
        }

        String prefix = text.substring(argStart, cursorOffset).trim();
        String suffix = text.substring(cursorOffset, Math.min(argEnd, text.length())).trim();

        // Special case: cursor inside empty quotes starting the argument: foo("|") or foo('|')
        boolean isEmptyQuotes = (inDouble && prefix.equals("\"") && suffix.equals("\""))
                || (inSingle && prefix.equals("'") && suffix.equals("'"));

        if (!isEmptyQuotes && (inSingle || inDouble)) {
            return false;
        }

        // Condition 3: If popup is already visible and cursor remains inside the active call,
        // keep it visible and update parameter highlight/signature in real time across parameter edits/deletions.
        if (isAlreadyVisible && isCursorInsideCall(text, openParen, cursorOffset)) {
            return true;
        }

        // Condition 1: No entered argument (empty argument slot, e.g. foo(|) or foo(a, |) or foo("|"))
        if ((prefix.isEmpty() && suffix.isEmpty()) || isEmptyQuotes) {
            return true;
        }

        // Condition 2: User starts typing an argument (only valid if this was a text change / typing action)
        if (isTextChange && suffix.isEmpty() && !prefix.isEmpty()) {
            return isSimpleToken(prefix);
        }

        return false;
    }

    private static boolean isSimpleToken(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '$' && c != '.' && c != '"' && c != '\'') {
                return false;
            }
        }
        return true;
    }

    private static int findMethodInClassNode(JsSyntaxTree tree, int classNodeId, String methodName) {
        if (tree == null || classNodeId <= 0 || methodName == null) return 0;
        int child = tree.nodeChild[classNodeId];
        int guard = 0;
        while (child > 0 && child < tree.nodeCount && ++guard <= tree.nodeCount) {
            if ((tree.nodeType[child] == JsSyntaxTree.N_METHOD || tree.nodeType[child] == JsSyntaxTree.N_GETTER || tree.nodeType[child] == JsSyntaxTree.N_SETTER)
                    && methodName.equals(tree.nodeName[child])) {
                return child;
            }
            child = tree.nodeSibling[child];
        }
        return 0;
    }
}
