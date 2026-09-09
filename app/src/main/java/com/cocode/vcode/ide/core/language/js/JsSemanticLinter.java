package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.KnownElements;
import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.ModuleResolver;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.lsp.SymbolEntry;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Semantic diagnostics analyzer for JavaScript and TypeScript source files.
 * Performs deep semantic validation using the AST {@link JsSyntaxTree}, lexical {@link ScopeTree},
 * and workspace-wide {@link ProjectIndex}. Detects duplicate declarations, const reassignments,
 * function call arity mismatches, undefined symbols, and language structural violations.
 */
public class JsSemanticLinter {

    // Reserved JS and TS keywords
    private static final Set<String> JS_KEYWORDS = JsKeywords.ALL_JS_TS_KEYWORDS;

    public static void analyze(File file, String text, TokenStream mask, JsSyntaxTree tree, ScopeTree scopeTree, ProjectIndex index, List<Problem> problems) {
        if (text == null || text.trim().isEmpty() || tree == null || scopeTree == null) return;

        // Scope and declaration validation
        checkDuplicateDeclarations(file, text, scopeTree, tree, problems);
        checkConstReassignment(file, text, mask, scopeTree, tree, problems);

        // Function call arity and signature validation
        checkArity(file, text, mask, index, scopeTree, tree, problems);
        
        // Undefined symbol detection against active scope, standard library, and project index
        checkUndefined(file, text, mask, scopeTree, tree, problems);
        
        // AST structural rules and syntax checks
        try { checkAdditionalAstRules(file, text, mask, tree, problems); } catch (Exception ignored) {}
    }
    
    private static void checkDuplicateDeclarations(File file, String text, ScopeTree scopeTree, JsSyntaxTree tree, List<Problem> problems) {
        for (java.util.Map.Entry<String, int[]> entry : scopeTree.symbols.entrySet()) {
            String name = entry.getKey();
            int[] tuples = entry.getValue();
            if (tuples.length <= 3) continue;
            
            java.util.Map<Integer, java.util.List<Integer>> scopeToNodes = new java.util.HashMap<>();
            for (int i = 0; i < tuples.length; i += 3) {
                int scopeId = tuples[i];
                int nodeId = tuples[i+1];
                scopeToNodes.computeIfAbsent(scopeId, k -> new java.util.ArrayList<>()).add(nodeId);
            }
            
            for (java.util.Map.Entry<Integer, java.util.List<Integer>> scopeEntry : scopeToNodes.entrySet()) {
                java.util.List<Integer> nodes = scopeEntry.getValue();
                if (nodes.size() > 1) {
                    boolean hasBlockLevel = false;
                    for (int nodeId : nodes) {
                        if (tree.nodeType[nodeId] == JsSyntaxTree.N_CLASS_DECL || 
                            tree.nodeExtra[nodeId] == JsSyntaxTree.FLAG_CONST || 
                            tree.nodeExtra[nodeId] == JsSyntaxTree.FLAG_LET) {
                            hasBlockLevel = true;
                            break;
                        }
                    }
                    if (hasBlockLevel) {
                        for (int i = 1; i < nodes.size(); i++) {
                            int nodeId = nodes.get(i);
                            int line = LinterUtils.getLine(text, tree.nodeStart[nodeId]);
                            int col = LinterUtils.getColumn(text, tree.nodeStart[nodeId]);
                            problems.add(new Problem(file, line, col, name.length(),
                                    "Identifier '" + name + "' has already been declared",
                                    Problem.Severity.ERROR));
                        }
                    }
                }
            }
        }
    }
    
    private static void checkConstReassignment(File file, String text, TokenStream mask, ScopeTree scopeTree, JsSyntaxTree tree, List<Problem> problems) {
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_IDENTIFIER || tree.nodeType[i] == JsSyntaxTree.N_MEMBER_EXPR) {
                // Ignore the identifier if it is part of its own declaration (Test 1.1 fix)
                int parentId = tree.nodeParent[i];
                if (parentId != 0 && tree.nodeType[parentId] == JsSyntaxTree.N_VAR_DECL && tree.nodeName[i].equals(tree.nodeName[parentId])) {
                    continue;
                }
                
                String name = tree.nodeName[i];
                if (name == null || name.isEmpty() || "{destructure}".equals(name)) continue;
                
                int endOffset = tree.nodeEnd[i];
                int nextTok = -1;
                
                int low = 0;
                int high = mask.types.length - 1;
                int startTokenIdx = high;
                while (low <= high) {
                    int mid = (low + high) >>> 1;
                    if (mask.tokenStart[mid] >= endOffset) {
                        startTokenIdx = mid;
                        high = mid - 1;
                    } else {
                        low = mid + 1;
                    }
                }
                
                for (int t = startTokenIdx; t < mask.types.length; t++) {
                    if (mask.types[t] != TokenStream.TK_WHITESPACE && mask.types[t] != TokenStream.TK_COMMENT) {
                        nextTok = t;
                        break;
                    }
                }
                
                if (nextTok != -1 && mask.types[nextTok] == TokenStream.TK_OPERATOR) {
                    char ch = text.charAt(mask.tokenStart[nextTok]);
                    boolean isAssign = false;
                    
                    if (ch == '=') {
                        isAssign = true;
                        // Ignore '==' or '==='
                        if (nextTok + 1 < mask.types.length && mask.tokenStart[nextTok+1] == mask.tokenStart[nextTok] + 1) {
                            if (mask.types[nextTok+1] == TokenStream.TK_OPERATOR && text.charAt(mask.tokenStart[nextTok+1]) == '=') {
                                isAssign = false;
                            }
                        }
                    } else if (ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == '%') {
                        if (nextTok + 1 < mask.types.length && mask.tokenStart[nextTok+1] == mask.tokenStart[nextTok] + 1) {
                            if (mask.types[nextTok+1] == TokenStream.TK_OPERATOR) {
                                char nextCh = text.charAt(mask.tokenStart[nextTok+1]);
                                if (nextCh == '=' || (ch == '+' && nextCh == '+') || (ch == '-' && nextCh == '-')) {
                                    isAssign = true;
                                }
                            }
                        }
                    }
                    
                    if (isAssign) {
                    
                        String baseIdentifier = name;
                        int dotIdx = name.indexOf('.');
                        if (dotIdx >= 0) {
                            // assigning to a property of a const variable is allowed!
                            continue;
                        }
                        
                        int scopeId = scopeTree.findScopeAt(tree.nodeStart[i], tree);
                        int[] resolved = scopeTree.lookupSymbol(baseIdentifier, scopeId, tree.nodeStart[i], tree);
                        
                        if (resolved != null && resolved[2] == JsSyntaxTree.N_VAR_DECL) {
                            int declNodeId = resolved[1];
                            
                            if (tree.nodeExtra[declNodeId] == JsSyntaxTree.FLAG_CONST) {
                                int line = LinterUtils.getLine(text, tree.nodeStart[i]);
                                int col = LinterUtils.getColumn(text, tree.nodeStart[i]);
                                problems.add(new Problem(file, line, col, baseIdentifier.length(),
                                        "Cannot reassign 'const' variable '" + baseIdentifier + "'",
                                        Problem.Severity.ERROR));
                            }
                        }
                    }
                }
            }
        }
    }

    private static void checkArity(File file, String text, TokenStream mask, ProjectIndex index, ScopeTree scopeTree, JsSyntaxTree tree, List<Problem> problems) {
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_CALL_EXPR) {
                String identifier = tree.nodeName[i];
                if (identifier == null) continue;
                
                String baseIdentifier = identifier;
                int dotIdx = identifier.lastIndexOf('.');
                if (dotIdx >= 0) {
                    baseIdentifier = identifier.substring(dotIdx + 1);
                }

                int scopeId = scopeTree.findScopeAt(tree.nodeStart[i], tree);
                int[] resolved = scopeTree.lookupSymbol(baseIdentifier, scopeId, tree.nodeStart[i], tree);
                
                int totalParams = 0;
                boolean isVariadic = false;
                
                if (resolved != null) {
                    int declNodeId = resolved[1];
                    int targetNodeId = declNodeId;
                    
                    if (declNodeId > 0 && declNodeId < tree.nodeCount && tree.nodeType[declNodeId] == JsSyntaxTree.N_VAR_DECL) {
                        int child = tree.nodeChild[declNodeId];
                        if (child > 0 && child < tree.nodeCount && (tree.nodeType[child] == JsSyntaxTree.N_ARROW_FUNC 
                                || tree.nodeType[child] == JsSyntaxTree.N_FUNC_DECL
                                || tree.nodeType[child] == JsSyntaxTree.N_CLASS_DECL)) {
                            targetNodeId = child;
                        }
                    }
                    
                    if (targetNodeId > 0 && targetNodeId < tree.nodeCount && tree.nodeType[targetNodeId] == JsSyntaxTree.N_CLASS_DECL) {
                        int[] ctorInfo = resolveClassConstructorParams(tree, targetNodeId, scopeTree, scopeId, index);
                        if (ctorInfo == null) {
                            continue; // Unknown external superclass: bypass arity check to prevent false positives
                        }
                        totalParams = ctorInfo[0];
                        if (ctorInfo[1] == 1) isVariadic = true;
                    } else {
                        int child = (targetNodeId > 0 && targetNodeId < tree.nodeCount) ? tree.nodeChild[targetNodeId] : 0;
                        int childLoop = 0;
                        while (child > 0 && child < tree.nodeCount && ++childLoop <= tree.nodeCount) {
                            if (tree.nodeType[child] == JsSyntaxTree.N_PARAM) {
                                if ((tree.nodeExtra[child] & JsSyntaxTree.FLAG_REST) != 0) {
                                    isVariadic = true;
                                } else {
                                    totalParams++;
                                }
                            }
                            child = tree.nodeSibling[child];
                        }
                    }
                } else {
                    List<LspLocation> defs = (index != null) ? index.findDefinitions(baseIdentifier) : java.util.Collections.emptyList();
                    SymbolEntry targetEntry = null;
                    
                    for (LspLocation loc : defs) {
                        List<SymbolEntry> syms = index.getFileSymbols(loc.uri);
                        for (SymbolEntry s : syms) {
                            if (s.name.equals(baseIdentifier) && (s.kind == SymbolEntry.KIND_FUNCTION || s.kind == SymbolEntry.KIND_CLASS)) {
                                targetEntry = s;
                                break;
                            }
                        }
                        if (targetEntry != null) break;
                    }
                    
                    if (targetEntry != null && targetEntry.detail != null) {
                        String detail = targetEntry.detail; 
                        if (detail.contains("...")) isVariadic = true;
                        
                        String[] declaredParams = detail.trim().isEmpty() ? new String[0] : detail.split(",");
                        totalParams = declaredParams.length;
                    } else {
                        continue;
                    }
                }
                
                if (isVariadic) continue;
                
                int actualArgs = 0;
                int child = tree.nodeChild[i];
                int argLoop = 0;
                while (child > 0 && child < tree.nodeCount && ++argLoop <= tree.nodeCount) {
                    if (tree.nodeType[child] == JsSyntaxTree.N_PARAM) {
                        actualArgs++;
                    }
                    child = tree.nodeSibling[child];
                }
                
                if (actualArgs < totalParams) {
                    int line = LinterUtils.getLine(text, tree.nodeStart[i]);
                    int col = LinterUtils.getColumn(text, tree.nodeStart[i]);
                    problems.add(new Problem(file, line, col, identifier.length(),
                            "Too few arguments: '" + identifier + "' expects " + totalParams + " argument(s), but got " + actualArgs,
                            Problem.Severity.ERROR));
                } else if (actualArgs > totalParams) {
                    int line = LinterUtils.getLine(text, tree.nodeStart[i]);
                    int col = LinterUtils.getColumn(text, tree.nodeStart[i]);
                    problems.add(new Problem(file, line, col, identifier.length(),
                            "Too many arguments: '" + identifier + "' expects " + totalParams + " argument(s), but got " + actualArgs,
                            Problem.Severity.ERROR));
                }
            }
        }
    }

    private static int[] resolveClassConstructorParams(JsSyntaxTree tree, int classNodeId, ScopeTree scopeTree, int scopeId, ProjectIndex index) {
        if (tree == null || classNodeId <= 0 || classNodeId >= tree.nodeCount) {
            return new int[]{0, 0};
        }
        // 1. Look for explicit constructor method in this class
        int child = tree.nodeChild[classNodeId];
        int guard = 0;
        while (child > 0 && child < tree.nodeCount && ++guard <= tree.nodeCount) {
            if (tree.nodeType[child] == JsSyntaxTree.N_METHOD && "constructor".equals(tree.nodeName[child])) {
                int total = 0;
                boolean variadic = false;
                int pChild = tree.nodeChild[child];
                int pGuard = 0;
                while (pChild > 0 && pChild < tree.nodeCount && ++pGuard <= tree.nodeCount) {
                    if (tree.nodeType[pChild] == JsSyntaxTree.N_PARAM) {
                        if ((tree.nodeExtra[pChild] & JsSyntaxTree.FLAG_REST) != 0) {
                            variadic = true;
                        } else {
                            total++;
                        }
                    }
                    pChild = tree.nodeSibling[pChild];
                }
                return new int[]{total, variadic ? 1 : 0};
            }
            child = tree.nodeSibling[child];
        }

        // 2. No explicit constructor in this class -> check superclass if any
        String superName = tree.nodeTypeAnn[classNodeId];
        if (superName != null && !superName.isEmpty()) {
            // 2a. Check if superclass is in scope in the same file
            if (scopeTree != null) {
                int[] superResolved = scopeTree.lookupSymbol(superName, scopeId);
                if (superResolved != null && superResolved[1] > 0 && superResolved[1] < tree.nodeCount) {
                    int superDeclId = superResolved[1];
                    if (tree.nodeType[superDeclId] == JsSyntaxTree.N_CLASS_DECL) {
                        return resolveClassConstructorParams(tree, superDeclId, scopeTree, scopeId, index);
                    } else if (tree.nodeType[superDeclId] == JsSyntaxTree.N_VAR_DECL) {
                        int sc = tree.nodeChild[superDeclId];
                        if (sc > 0 && sc < tree.nodeCount && tree.nodeType[sc] == JsSyntaxTree.N_CLASS_DECL) {
                            return resolveClassConstructorParams(tree, sc, scopeTree, scopeId, index);
                        }
                    }
                }
            }

            // 2b. Check built-in standard library classes (e.g. Error, Map, Set, Event, etc.)
            JsStandardLibrary.SignatureInfo builtinSuper = JsStandardLibrary.getBuiltinSignature(superName, null);
            if (builtinSuper != null && builtinSuper.parameters != null) {
                boolean variadic = false;
                int total = 0;
                for (String p : builtinSuper.parameters) {
                    if (p.contains("...")) variadic = true;
                    else total++;
                }
                return new int[]{total, variadic ? 1 : 0};
            }

            // 2c. Check cross-file project symbols via index
            if (index != null) {
                List<LspLocation> defs = index.findDefinitions(superName);
                for (LspLocation loc : defs) {
                    List<SymbolEntry> syms = index.getFileSymbols(loc.uri);
                    if (syms == null) continue;
                    for (SymbolEntry s : syms) {
                        if (s.name.equals(superName) && s.kind == SymbolEntry.KIND_CLASS) {
                            if (s.detail != null) {
                                String detail = s.detail.trim();
                                boolean variadic = detail.contains("...");
                                String[] parts = detail.isEmpty() ? new String[0] : detail.split(",");
                                return new int[]{parts.length, variadic ? 1 : 0};
                            }
                            return new int[]{0, 0};
                        }
                    }
                }
            }

            // Superclass cannot be resolved (e.g. external node_modules or unindexed library)
            // Return null to bypass arity check and prevent false positives
            return null;
        }

        // 3. No explicit constructor and no superclass -> default ES6 constructor takes 0 arguments
        return new int[]{0, 0};
    }
    
    private static void checkUndefined(File file, String text, TokenStream mask, ScopeTree scopeTree, JsSyntaxTree tree, List<Problem> problems) {
        int ieCount = 0;
        int[] ieStart = new int[16];
        int[] ieEnd = new int[16];
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_IMPORT || tree.nodeType[i] == JsSyntaxTree.N_EXPORT) {
                if (ieCount == ieStart.length) {
                    ieStart = java.util.Arrays.copyOf(ieStart, ieCount * 2);
                    ieEnd = java.util.Arrays.copyOf(ieEnd, ieCount * 2);
                }
                ieStart[ieCount] = tree.nodeStart[i];
                ieEnd[ieCount] = tree.nodeEnd[i];
                ieCount++;
            }
        }
        
        for (int t = 0; t < mask.length; t++) {
            if (mask.types[t] != TokenStream.TK_IDENTIFIER) continue;
            int offset = mask.tokenStart[t];
            if (mask.isMasked(offset)) continue;

            int nextOffset = (t + 1 < mask.length) ? mask.tokenStart[t + 1] : text.length();
            int idEnd = offset;
            while (idEnd < nextOffset && (Character.isLetterOrDigit(text.charAt(idEnd)) || text.charAt(idEnd) == '_' || text.charAt(idEnd) == '$')) {
                idEnd++;
            }
            if (idEnd <= offset) continue;
            String id = text.substring(offset, idEnd);

            // Ignore identifiers bound within module import/export clauses
            boolean isInsideImportExport = false;
            for (int k = 0; k < ieCount; k++) {
                if (offset >= ieStart[k] && offset < ieEnd[k]) {
                    isInsideImportExport = true;
                    break;
                }
            }
            if (isInsideImportExport) continue;

            // Check preceding char to see if it's a property access
            int preIndex = offset - 1;
            while (preIndex >= 0 && Character.isWhitespace(text.charAt(preIndex))) preIndex--;
            if (preIndex >= 0 && text.charAt(preIndex) == '.') continue;

            // Check preceding tokens to see if it's a declaration (only if AST is missing)
            if (tree.nodesByOffset == null && isDeclarationSite(text, offset)) continue;

            // Allow object keys in object literals: { key: value }
            int postIndex = idEnd;
            while (postIndex < text.length() && Character.isWhitespace(text.charAt(postIndex))) postIndex++;
            if (postIndex < text.length() && text.charAt(postIndex) == ':') {
                int preTok = offset - 1;
                while (preTok >= 0 && (mask.types[preTok] == TokenStream.TK_WHITESPACE || mask.types[preTok] == TokenStream.TK_COMMENT)) {
                    preTok--;
                }
                if (preTok >= 0 && mask.types[preTok] == TokenStream.TK_PUNCT) {
                    char prevChar = text.charAt(mask.tokenStart[preTok]);
                    if (prevChar == '{' || prevChar == ',') {
                        continue;
                    }
                }
            }

            // Verify this is a real identifier node in the AST
            boolean isAstIdentifier = false;
            boolean isAstDeclaration = false;
            int astOffset = offset;
            if (tree.nodesByOffset != null) {
                int low = 1, high = tree.nodeCount - 1;
                while (low <= high) {
                    int mid = (low + high) >>> 1;
                    int nodeId = tree.nodesByOffset[mid];
                    if (tree.nodeStart[nodeId] < astOffset) {
                        low = mid + 1;
                    } else if (tree.nodeStart[nodeId] > astOffset) {
                        high = mid - 1;
                    } else {
                        int temp = mid;
                        while (temp >= 1 && tree.nodeStart[tree.nodesByOffset[temp]] == astOffset) {
                            int type = tree.nodeType[tree.nodesByOffset[temp]];
                            if (type == JsSyntaxTree.N_IDENTIFIER) {
                                isAstIdentifier = true;
                            } else if (tree.nodeName[tree.nodesByOffset[temp]] != null && (type == JsSyntaxTree.N_PARAM || type == JsSyntaxTree.N_VAR_DECL || type == JsSyntaxTree.N_FUNC_DECL || type == JsSyntaxTree.N_CLASS_DECL || type == JsSyntaxTree.N_METHOD || type == JsSyntaxTree.N_PROPERTY || type == JsSyntaxTree.N_GETTER || type == JsSyntaxTree.N_SETTER || type == JsSyntaxTree.N_IMPORT || type == JsSyntaxTree.N_ENUM || type == JsSyntaxTree.N_INTERFACE || type == JsSyntaxTree.N_TYPE_ALIAS)) {
                                isAstDeclaration = true;
                            }
                            temp--;
                        }
                        temp = mid + 1;
                        while (temp < tree.nodeCount && tree.nodeStart[tree.nodesByOffset[temp]] == astOffset) {
                            int type = tree.nodeType[tree.nodesByOffset[temp]];
                            if (type == JsSyntaxTree.N_IDENTIFIER) {
                                isAstIdentifier = true;
                            } else if (tree.nodeName[tree.nodesByOffset[temp]] != null && (type == JsSyntaxTree.N_PARAM || type == JsSyntaxTree.N_VAR_DECL || type == JsSyntaxTree.N_FUNC_DECL || type == JsSyntaxTree.N_CLASS_DECL || type == JsSyntaxTree.N_METHOD || type == JsSyntaxTree.N_PROPERTY || type == JsSyntaxTree.N_GETTER || type == JsSyntaxTree.N_SETTER || type == JsSyntaxTree.N_IMPORT || type == JsSyntaxTree.N_ENUM || type == JsSyntaxTree.N_INTERFACE || type == JsSyntaxTree.N_TYPE_ALIAS)) {
                                isAstDeclaration = true;
                            }
                            temp++;
                        }
                        break;
                    }
                }
            } else {
                isAstIdentifier = true; // Fallback
            }

            if (isAstDeclaration) continue; // Skip declarations
            if (!isAstIdentifier) continue;

            // Find lexical scope of this identifier and resolve symbol
            int scopeId = scopeTree.findScopeAt(offset, tree);
            int[] resolved = scopeTree.lookupSymbol(id, scopeId, offset, tree);

            if (resolved == null && !KnownElements.JS_GLOBALS.contains(id) && !JS_KEYWORDS.contains(id)) {
                int line = LinterUtils.getLine(text, offset);
                int col = LinterUtils.getColumn(text, offset);
                problems.add(new Problem(file, line, col, id.length(),
                        "'" + id + "' is not defined",
                        Problem.Severity.ERROR));
            }
        }
    }
    
    private static boolean isDeclarationSite(String text, int start) {
        int i = start - 1;
        while (i >= 0 && Character.isWhitespace(text.charAt(i))) i--;
        
        if (i >= 0) {
            char c = text.charAt(i);
            if (c == '{' || c == '[' || c == '(' || c == ',') {
                return true;
            }
            String sub = text.substring(Math.max(0, i - 12), i + 1);
            if (sub.endsWith("function") || sub.endsWith("class") || sub.endsWith("let") || sub.endsWith("const") || sub.endsWith("var") || sub.endsWith("catch") || sub.endsWith("get") || sub.endsWith("set") || sub.endsWith("enum") || sub.endsWith("interface") || sub.endsWith("type") || sub.endsWith("declare") || sub.endsWith("namespace")) {
                int kwLen;
                if (sub.endsWith("function")) kwLen = 8;
                else if (sub.endsWith("interface")) kwLen = 9;
                else if (sub.endsWith("namespace")) kwLen = 9;
                else if (sub.endsWith("declare")) kwLen = 7;
                else if (sub.endsWith("class") || sub.endsWith("catch") || sub.endsWith("const")) kwLen = 5;
                else if (sub.endsWith("enum") || sub.endsWith("type")) kwLen = 4;
                else kwLen = 3; // let, var, get, set
                int wordStart = (i + 1) - kwLen;
                if (wordStart >= 0 && (wordStart == 0 || !Character.isLetterOrDigit(text.charAt(wordStart - 1)))) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private static void checkAdditionalAstRules(File file, String text, TokenStream mask, JsSyntaxTree tree, List<Problem> problems) {
        for (int i = 1; i < tree.nodeCount; i++) {
            int type = tree.nodeType[i];
            
            // checkConsole
            if (type == JsSyntaxTree.N_MEMBER_EXPR || type == JsSyntaxTree.N_CALL_EXPR) {
                if ("console.log".equals(tree.nodeName[i]) || "console.error".equals(tree.nodeName[i]) || 
                    "console.warn".equals(tree.nodeName[i]) || "console.info".equals(tree.nodeName[i]) ||
                    "console".equals(tree.nodeName[i])) {
                    
                    int line = LinterUtils.getLine(text, tree.nodeStart[i]);
                    int col = LinterUtils.getColumn(text, tree.nodeStart[i]);
                    problems.add(new Problem(file, line, col, tree.nodeName[i].length(),
                            "Unexpected console statement",
                            Problem.Severity.WARNING));
                }
            }
        }
        
        // Token stream rules
        int streamLen = Math.min(mask.length, text.length());
        for (int i = 0; i < streamLen; i++) {
            byte type = mask.types[i];
            int start = mask.tokenStart[i];
            int end = start;
            while (end < streamLen && mask.tokenStart[end] == start && mask.types[end] == type) {
                end++;
            }
            
            if (type == TokenStream.TK_OPERATOR) {
                int opEnd = end;
                while (opEnd < streamLen && mask.types[opEnd] == TokenStream.TK_OPERATOR) {
                    opEnd++;
                }
                
                String op = text.substring(start, opEnd).trim();
                
                if ("/".equals(op)) {
                    for (int j = opEnd; j < streamLen; j++) {
                        byte nType = mask.types[j];
                        int nStart = mask.tokenStart[j];
                        int nEnd = nStart;
                        while (nEnd < streamLen && mask.tokenStart[nEnd] == nStart && mask.types[nEnd] == nType) nEnd++;
                        
                        if (nType == TokenStream.TK_WHITESPACE || nType == TokenStream.TK_COMMENT) {
                            j = nEnd - 1;
                            continue;
                        }
                        
                        if (nType == TokenStream.TK_NUMBER) {
                            if ("0".equals(text.substring(nStart, nEnd).trim())) {
                                int line = LinterUtils.getLine(text, start);
                                int col = LinterUtils.getColumn(text, start);
                                problems.add(new Problem(file, line, col, 1, "Division by zero", Problem.Severity.WARNING));
                            }
                        }
                        break;
                    }
                }
                
                if ("===".equals(op) || "==".equals(op) || "!==".equals(op) || "!=".equals(op)) {
                    boolean hasTypeof = false;
                    boolean hasUndefined = false;
                    
                    // Walk backwards
                    for (int j = start - 1; j >= 0; j--) {
                        byte pType = mask.types[j];
                        int pStart = mask.tokenStart[j];
                        
                        if (pType == TokenStream.TK_WHITESPACE || pType == TokenStream.TK_COMMENT) {
                            j = pStart;
                            continue;
                        }
                        
                        if (pType == TokenStream.TK_PUNCT) {
                            int pEnd = pStart;
                            while (pEnd < streamLen && mask.tokenStart[pEnd] == pStart) pEnd++;
                            String punct = text.substring(pStart, pEnd).trim();
                            if (punct.equals(";") || punct.equals("{") || punct.equals("}")) break;
                        }
                        
                        if (pType == TokenStream.TK_KEYWORD || pType == TokenStream.TK_IDENTIFIER) {
                            int pEnd = pStart;
                            while (pEnd < streamLen && mask.tokenStart[pEnd] == pStart) pEnd++;
                            String prev = text.substring(pStart, pEnd).trim();
                            if ("typeof".equals(prev)) hasTypeof = true;
                            if ("undefined".equals(prev)) hasUndefined = true;
                        }
                        j = pStart;
                    }
                    
                    // Walk forwards
                    for (int j = opEnd; j < streamLen; j++) {
                        byte nType = mask.types[j];
                        int nStart = mask.tokenStart[j];
                        int nEnd = nStart;
                        while (nEnd < streamLen && mask.tokenStart[nEnd] == nStart && mask.types[nEnd] == nType) nEnd++;
                        
                        if (nType == TokenStream.TK_WHITESPACE || nType == TokenStream.TK_COMMENT) {
                            j = nEnd - 1;
                            continue;
                        }
                        
                        if (nType == TokenStream.TK_PUNCT) {
                            String punct = text.substring(nStart, nEnd).trim();
                            if (punct.equals(";") || punct.equals("{") || punct.equals("}")) break;
                        }
                        
                        if (nType == TokenStream.TK_KEYWORD || nType == TokenStream.TK_IDENTIFIER) {
                            String next = text.substring(nStart, nEnd).trim();
                            if ("typeof".equals(next)) hasTypeof = true;
                            if ("undefined".equals(next)) hasUndefined = true;
                        }
                        j = nEnd - 1;
                    }
                    
                    if (hasTypeof && hasUndefined) {
                        int line = LinterUtils.getLine(text, start);
                        int col = LinterUtils.getColumn(text, start);
                        problems.add(new Problem(file, line, col, op.length(), "Comparing typeof to undefined directly; typeof always returns a string (e.g. 'undefined')", Problem.Severity.WARNING));
                    }
                }
                end = opEnd;
            }
            
            i = end - 1;
        }
    }
}
