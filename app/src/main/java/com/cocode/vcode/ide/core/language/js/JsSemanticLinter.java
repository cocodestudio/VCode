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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsSemanticLinter {

    // Regex for import statements: import { foo, bar as b } from './utils' or import * as NS from './utils' or import Default from './utils'
    private static final Pattern PAT_IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:\\*\\s+as\\s+([a-zA-Z_$][\\w$]*)|\\{([^}]+)\\}|([a-zA-Z_$][\\w$]*))\\s+from\\s+['\"]([^'\"]+)['\"]");
    // Simple function call extraction: identifier(args...)
    private static final Pattern PAT_FUNCTION_CALL = Pattern.compile("\\b([a-zA-Z_$][\\w$]*)\\s*\\(");
    // Find bare identifiers
    private static final Pattern PAT_IDENTIFIER = Pattern.compile("\\b([a-zA-Z_$][\\w$]*)\\b");
    // Find JS keywords
    private static final Set<String> JS_KEYWORDS = new HashSet<>(java.util.Arrays.asList(
            "await", "break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "enum", "export", "extends", "false",
            "finally", "for", "function", "if", "import", "in", "instanceof", "new",
            "null", "return", "super", "switch", "this", "throw", "true", "try",
            "typeof", "var", "void", "while", "with", "yield", "let", "static", "async",
            // TS Keywords
            "interface", "type", "namespace", "public", "private", "protected", "readonly", 
            "abstract", "declare", "implements", "keyof", "as", "is", "infer", 
            "any", "unknown", "never", "string", "number", "boolean", "symbol"
    ));
    
    // Pattern to grab declared functions, classes, and variables (simplified from SymbolExtractor)
    private static final Pattern PAT_DECL = Pattern.compile("\\b(?:function|class|let|const|var)\\s+([a-zA-Z_$][\\w$]*)");
    // Pattern to grab function parameters
    private static final Pattern PAT_FUNC_PARAMS = Pattern.compile("function\\s*\\w*\\s*\\(([^)]*)\\)|([a-zA-Z_$][\\w$]*)\\s*=>|\\(([^)]*)\\)\\s*=>");


    public static void analyze(File file, String text, TokenStream mask, JsSyntaxTree tree, ScopeTree scopeTree, ProjectIndex index, List<Problem> problems) {
        if (index == null || text == null || text.trim().isEmpty()) return;

        // Note: For unused-variable checking and arity checking, we still rely on the old regex passes in this phase.
        // Task 3.6 only requested replacing the undefined-variable check logic.
        Set<String> inScope = new HashSet<>(KnownElements.JS_GLOBALS);
        inScope.addAll(JS_KEYWORDS);
        
        // 1. Parse locally declared symbols (kept for old checks)
        Matcher declMatcher = PAT_DECL.matcher(text);
        while (declMatcher.find()) {
            if (!mask.isMasked(declMatcher.start(1))) {
                inScope.add(declMatcher.group(1));
            }
        }
        
        // Add function parameters to scope (kept for old checks)
        Matcher paramMatcher = PAT_FUNC_PARAMS.matcher(text);
        while (paramMatcher.find()) {
            if (!mask.isMasked(paramMatcher.start())) {
                String paramsStr = paramMatcher.group(1);
                if (paramsStr == null) paramsStr = paramMatcher.group(2);
                if (paramsStr == null) paramsStr = paramMatcher.group(3);
                if (paramsStr != null) {
                    for (String p : paramsStr.split(",")) {
                        String clean = p.replaceAll("[={].*|\\.\\.\\.", "").trim();
                        if (!clean.isEmpty() && PAT_IDENTIFIER.matcher(clean).matches()) {
                            inScope.add(clean);
                        }
                    }
                }
            }
        }

        // 2. Resolve imports (kept for old checks)
        Matcher importMatcher = PAT_IMPORT.matcher(text);
        while (importMatcher.find()) {
            if (mask.isMasked(importMatcher.start())) continue;
            
            String namespaceAlias = importMatcher.group(1);
            String namedImports = importMatcher.group(2);
            String defaultImport = importMatcher.group(3);
            
            if (namespaceAlias != null) inScope.add(namespaceAlias);
            if (defaultImport != null) inScope.add(defaultImport);
            if (namedImports != null) {
                for (String named : namedImports.split(",")) {
                    String[] parts = named.split("\\s+as\\s+");
                    String alias = parts.length > 1 ? parts[1].trim() : parts[0].trim();
                    if (!alias.isEmpty()) inScope.add(alias);
                }
            }
        }
        
        // AST-based checks for Phase 3
        checkDuplicateDeclarations(file, text, scopeTree, tree, problems);
        checkConstReassignment(file, text, mask, scopeTree, tree, problems);

        // 3 & 4. Arity checking
        checkArity(file, text, mask, index, scopeTree, tree, problems);
        
        // 5. Undefined symbols checking
        checkUndefined(file, text, mask, scopeTree, tree, problems);
        
        // 6. Additional AST-based rules migrated from regex
        try { checkAdditionalAstRules(file, text, mask, tree, problems); } catch (Exception e) { problems.add(new Problem(file, 0, 0, 0, "EXCEPTION: " + e.toString() + " at " + e.getStackTrace()[0].toString(), Problem.Severity.ERROR)); }
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
                    
                    if (tree.nodeType[declNodeId] == JsSyntaxTree.N_VAR_DECL) {
                        int child = tree.nodeChild[declNodeId];
                        if (child != 0 && (tree.nodeType[child] == JsSyntaxTree.N_ARROW_FUNC || tree.nodeType[child] == JsSyntaxTree.N_FUNC_DECL)) {
                            targetNodeId = child;
                        }
                    }
                    
                    int child = tree.nodeChild[targetNodeId];
                    while (child != 0) {
                        if (tree.nodeType[child] == JsSyntaxTree.N_PARAM) {
                            totalParams++;
                        }
                        child = tree.nodeSibling[child];
                    }
                } else {
                    List<LspLocation> defs = index.findDefinitions(baseIdentifier);
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
                while (child != 0) {
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
        
        Matcher idMatcher = PAT_IDENTIFIER.matcher(text);
        while (idMatcher.find()) {
            if (mask.isMasked(idMatcher.start())) continue;
            
            String id = idMatcher.group(1);
            
            // Skip identifiers that are part of import or export statements (Task 4.3.5 fix)
            int offset = idMatcher.start();
            boolean isInsideImportExport = false;
            for (int k = 0; k < ieCount; k++) {
                if (offset >= ieStart[k] && offset < ieEnd[k]) {
                    isInsideImportExport = true;
                    break;
                }
            }
            if (isInsideImportExport) continue;
            
            // Check preceding char to see if it's a property access
            int preIndex = idMatcher.start() - 1;
            while (preIndex >= 0 && Character.isWhitespace(text.charAt(preIndex))) preIndex--;
            if (preIndex >= 0 && text.charAt(preIndex) == '.') continue;
            
            // Check preceding tokens to see if it's a declaration (only if AST is missing)
            if (tree.nodesByOffset == null && isDeclarationSite(text, idMatcher.start())) continue;
            
            // Allow object keys in object literals: { key: value }
            int postIndex = idMatcher.end();
            while (postIndex < text.length() && Character.isWhitespace(text.charAt(postIndex))) postIndex++;
            if (postIndex < text.length() && text.charAt(postIndex) == ':') {
                int preTok = idMatcher.start() - 1;
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
            int astOffset = idMatcher.start();
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
                            } else if (tree.nodeName[tree.nodesByOffset[temp]] != null && (type == JsSyntaxTree.N_PARAM || type == JsSyntaxTree.N_VAR_DECL || type == JsSyntaxTree.N_FUNC_DECL || type == JsSyntaxTree.N_CLASS_DECL)) {
                                isAstDeclaration = true;
                            }
                            temp--;
                        }
                        temp = mid + 1;
                        while (temp < tree.nodeCount && tree.nodeStart[tree.nodesByOffset[temp]] == astOffset) {
                            int type = tree.nodeType[tree.nodesByOffset[temp]];
                            if (type == JsSyntaxTree.N_IDENTIFIER) {
                                isAstIdentifier = true;
                            } else if (tree.nodeName[tree.nodesByOffset[temp]] != null && (type == JsSyntaxTree.N_PARAM || type == JsSyntaxTree.N_VAR_DECL || type == JsSyntaxTree.N_FUNC_DECL || type == JsSyntaxTree.N_CLASS_DECL)) {
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
            int scopeId = scopeTree.findScopeAt(idMatcher.start(), tree);
            int[] resolved = scopeTree.lookupSymbol(id, scopeId, idMatcher.start(), tree);
            
            if (resolved == null && !KnownElements.JS_GLOBALS.contains(id) && !JS_KEYWORDS.contains(id)) {
                int line = LinterUtils.getLine(text, idMatcher.start());
                int col = LinterUtils.getColumn(text, idMatcher.start());
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
            String sub = text.substring(Math.max(0, i - 8), i + 1);
            if (sub.endsWith("function") || sub.endsWith("class") || sub.endsWith("let") || sub.endsWith("const") || sub.endsWith("var") || sub.endsWith("catch") || sub.endsWith("get") || sub.endsWith("set")) {
                int kwLen = sub.endsWith("function") ? 8 : sub.endsWith("class") ? 5 : sub.endsWith("catch") ? 5 : sub.endsWith("const") ? 5 : sub.endsWith("let") ? 3 : sub.endsWith("var") ? 3 : 3;
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
        for (int i = 0; i < mask.types.length; i++) {
            byte type = mask.types[i];
            int start = mask.tokenStart[i];
            int end = start;
            while (end < mask.types.length && mask.tokenStart[end] == start && mask.types[end] == type) {
                end++;
            }
            
            if (type == TokenStream.TK_OPERATOR) {
                int opEnd = end;
                while (opEnd < mask.types.length && mask.types[opEnd] == TokenStream.TK_OPERATOR) {
                    opEnd++;
                }
                
                String op = text.substring(start, opEnd).trim();
                
                if ("/".equals(op)) {
                    for (int j = opEnd; j < mask.types.length; j++) {
                        byte nType = mask.types[j];
                        int nStart = mask.tokenStart[j];
                        int nEnd = nStart;
                        while (nEnd < mask.types.length && mask.tokenStart[nEnd] == nStart && mask.types[nEnd] == nType) nEnd++;
                        
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
                            while (pEnd < mask.types.length && mask.tokenStart[pEnd] == pStart) pEnd++;
                            String punct = text.substring(pStart, pEnd).trim();
                            if (punct.equals(";") || punct.equals("{") || punct.equals("}")) break;
                        }
                        
                        if (pType == TokenStream.TK_KEYWORD || pType == TokenStream.TK_IDENTIFIER) {
                            int pEnd = pStart;
                            while (pEnd < mask.types.length && mask.tokenStart[pEnd] == pStart) pEnd++;
                            String prev = text.substring(pStart, pEnd).trim();
                            if ("typeof".equals(prev)) hasTypeof = true;
                            if ("undefined".equals(prev)) hasUndefined = true;
                        }
                        j = pStart;
                    }
                    
                    // Walk forwards
                    for (int j = opEnd; j < mask.types.length; j++) {
                        byte nType = mask.types[j];
                        int nStart = mask.tokenStart[j];
                        int nEnd = nStart;
                        while (nEnd < mask.types.length && mask.tokenStart[nEnd] == nStart && mask.types[nEnd] == nType) nEnd++;
                        
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
