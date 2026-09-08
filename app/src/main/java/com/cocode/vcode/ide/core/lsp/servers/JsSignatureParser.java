package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.language.js.JsStandardLibrary;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;

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

        int callNode = 0;
        int activeOpenParen = -1;

        // 1. Find the innermost N_CALL_EXPR whose open parenthesis precedes offset and whose argument list contains offset
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

        String funcName = (callNode > 0) ? tree.nodeName[callNode] : null;

        // 2. Fallback: if AST did not produce N_CALL_EXPR (e.g. unclosed / transient syntax or constructor call), find innermost unclosed '('
        if (callNode == 0 || funcName == null || funcName.isEmpty()) {
            int unclosedParen = findInnermostUnclosedParen(text, offset);
            if (unclosedParen >= 0 && isCursorInsideCall(text, unclosedParen, offset)) {
                String extracted = extractFunctionNameBeforeParen(text, unclosedParen);
                if (extracted != null && !extracted.isEmpty()) {
                    activeOpenParen = unclosedParen;
                    funcName = extracted;
                    callNode = 0;
                }
            }
        }

        if (funcName == null || funcName.isEmpty() || activeOpenParen < 0) {
            return null;
        }

        // Clean up funcName if prefixed with 'new '
        if (funcName.startsWith("new ")) {
            funcName = funcName.substring(4).trim();
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

        // 1. Special case: super(...) call inside derived class constructor or method
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

        // 2. Check local file declaration in scope
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

        // 3. Check object literal member if receiver is in scope (e.g. mathUtils.add(|))
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

        // 4. Check built-in signatures from JsStandardLibrary
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
                String displayLabel = funcName + "(" + paramsStr + ")";

                List<LspSignatureHelp.LspParameterInformation> paramInfoList = new ArrayList<>();
                for (String p : builtin.parameters) {
                    paramInfoList.add(new LspSignatureHelp.LspParameterInformation(p, null));
                }

                LspSignatureHelp.LspSignatureInformation sig = new LspSignatureHelp.LspSignatureInformation(
                        displayLabel,
                        builtin.doc != null && !builtin.doc.isEmpty() ? builtin.doc : "Built-in method",
                        paramInfoList
                );
                return new LspSignatureHelp(Collections.singletonList(sig), 0, argIndex);
            }
        }

        // 5. Fallback to ProjectIndex definitions for cross-file project symbols
        if (signature == null) {
            com.cocode.vcode.ide.core.lsp.ProjectIndex index = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance();
            List<com.cocode.vcode.ide.core.lsp.LspLocation> defs = index.findDefinitions(baseIdentifier);
            com.cocode.vcode.ide.core.lsp.SymbolEntry targetEntry = null;

            for (com.cocode.vcode.ide.core.lsp.LspLocation loc : defs) {
                List<com.cocode.vcode.ide.core.lsp.SymbolEntry> entries = index.getFileSymbols(loc.uri);
                if (entries == null) continue;
                for (com.cocode.vcode.ide.core.lsp.SymbolEntry entry : entries) {
                    if (entry.name.equals(baseIdentifier) &&
                            (entry.kind == com.cocode.vcode.ide.core.lsp.SymbolEntry.KIND_FUNCTION || entry.kind == com.cocode.vcode.ide.core.lsp.SymbolEntry.KIND_CLASS)) {
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
                sourceLabel = targetEntry.kind == com.cocode.vcode.ide.core.lsp.SymbolEntry.KIND_CLASS ? "Cross-file class" : "Cross-file function";
            }
        }

        if (signature == null) {
            return null;
        }

        LspSignatureHelp.LspSignatureInformation sig = new LspSignatureHelp.LspSignatureInformation(
                funcName + "(" + signature.trim().replaceAll("\\s+", " ") + ")",
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

        return new LspSignatureHelp(Collections.singletonList(sig), 0, argIndex);
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
        if (tree == null || classNodeId <= 0 || classNodeId >= tree.nodeCount) return Collections.emptyList();
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
            else if (c == '>') { if (depth > 0) depth--; }
            else if (c == '(' && depth == 0) return i;
            else if (depth == 0 && !Character.isWhitespace(c) && c != '\n' && c != '\r') break;
        }
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') return i;
            if (c == ';' || c == '{' || c == '}') break;
        }
        return -1;
    }

    private static boolean isCursorInsideCall(String text, int openParen, int offset) {
        if (offset <= openParen || openParen < 0 || openParen >= text.length()) return false;
        int closeParen = findMatchingParen(text, openParen);
        if (closeParen >= 0) {
            return offset <= closeParen;
        }
        // Unclosed call: ensure no statement boundary ';' or block '}' at depth 0 between openParen and offset
        int depth = 0;
        boolean inSingle = false, inDouble = false, inTpl = false;
        for (int i = openParen + 1; i < offset && i < text.length(); i++) {
            char c = text.charAt(i);
            char prev = (i > openParen + 1) ? text.charAt(i - 1) : 0;
            if (c == '\'' && !inDouble && !inTpl && prev != '\\') inSingle = !inSingle;
            else if (c == '"' && !inSingle && !inTpl && prev != '\\') inDouble = !inDouble;
            else if (c == '`' && !inSingle && !inDouble && prev != '\\') inTpl = !inTpl;
            else if (!inSingle && !inDouble && !inTpl) {
                if (c == '(' || c == '[' || c == '{') depth++;
                else if (c == ')' || c == ']' || c == '}') {
                    if (depth > 0) depth--;
                    else return false;
                } else if (c == ';' && depth == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int findInnermostUnclosedParen(String text, int offset) {
        int depth = 0;
        boolean inSingle = false, inDouble = false, inTpl = false;
        for (int i = Math.min(offset - 1, text.length() - 1); i >= 0; i--) {
            char c = text.charAt(i);
            char prev = (i > 0) ? text.charAt(i - 1) : 0;
            if (c == '\'' && prev != '\\') inSingle = !inSingle;
            else if (c == '"' && prev != '\\') inDouble = !inDouble;
            else if (c == '`' && prev != '\\') inTpl = !inTpl;
            else if (!inSingle && !inDouble && !inTpl) {
                if (c == ')') depth++;
                else if (c == '(') {
                    if (depth > 0) {
                        depth--;
                    } else {
                        return i;
                    }
                } else if ((c == ';' || c == '{' || c == '}') && depth == 0) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private static String extractFunctionNameBeforeParen(String text, int openParen) {
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
        if (candidate.startsWith("new ")) {
            candidate = candidate.substring(4).trim();
        }
        return candidate.isEmpty() ? null : candidate;
    }

    public static int computeArgIndex(String text, int openParenOffset, int cursorOffset) {
        if (text == null || openParenOffset < 0 || cursorOffset <= openParenOffset) return 0;
        int depth = 0;
        int argIndex = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inTemplate = false;
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
            if (inTemplate) {
                if (c == '`' && prev != '\\') inTemplate = false;
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

            if (c == '\'') { inSingle = true; continue; }
            if (c == '"') { inDouble = true; continue; }
            if (c == '`') { inTemplate = true; continue; }

            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                if (depth > 0) depth--;
            } else if (c == ',' && depth == 0) {
                argIndex++;
            }
        }
        return argIndex;
    }

    public static int findMatchingParen(String text, int openParenOffset) {
        if (text == null || openParenOffset < 0 || openParenOffset >= text.length()) return -1;
        int depth = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inTemplate = false;
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
            if (inTemplate) {
                if (c == '`' && prev != '\\') inTemplate = false;
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
            if (c == '`') { inTemplate = true; continue; }

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
}
