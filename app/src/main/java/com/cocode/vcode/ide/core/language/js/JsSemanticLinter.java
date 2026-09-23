package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.KnownElements;
import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.ModuleResolver;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.lsp.SymbolEntry;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
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
        try {
            checkAdditionalAstRules(file, text, mask, tree, problems);
        } catch (Exception ignored) {
        }
    }

    private static void checkDuplicateDeclarations(File file, String text, ScopeTree scopeTree, JsSyntaxTree tree, List<Problem> problems) {
        for (java.util.Map.Entry<String, int[]> entry : scopeTree.symbols.entrySet()) {
            String name = entry.getKey();
            int[] tuples = entry.getValue();
            if (tuples.length <= 3) continue;

            java.util.Map<Integer, java.util.List<Integer>> scopeToNodes = new java.util.HashMap<>();
            for (int i = 0; i < tuples.length; i += 3) {
                int scopeId = tuples[i];
                int nodeId = tuples[i + 1];
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
                // Skip declaration and parameter sites
                int parentId = tree.nodeParent[i];
                if (parentId != 0) {
                    int pType = tree.nodeType[parentId];
                    if (pType == JsSyntaxTree.N_VAR_DECL || pType == JsSyntaxTree.N_PARAM) {
                        continue;
                    }
                    int grandParentId = tree.nodeParent[parentId];
                    if (grandParentId != 0 && tree.nodeType[grandParentId] == JsSyntaxTree.N_VAR_DECL) {
                        continue;
                    }
                }
                if (isDeclarationSite(text, tree.nodeStart[i])) {
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

                boolean isAssign = false;
                int opTokenIdx = -1;

                if (nextTok != -1 && mask.types[nextTok] == TokenStream.TK_OPERATOR) {
                    int off = mask.tokenStart[nextTok];
                    char ch = text.charAt(off);

                    if (ch == '=') {
                        isAssign = true;
                        opTokenIdx = nextTok;
                        // Exclude comparison and arrow operators (==, ===, =>)
                        if (off + 1 < text.length()) {
                            char c1 = text.charAt(off + 1);
                            if (c1 == '=' || c1 == '>') {
                                isAssign = false;
                                opTokenIdx = -1;
                            }
                        }
                    } else if (ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == '%' || ch == '&' || ch == '|' || ch == '^') {
                        if (off + 1 < text.length() && mask.types[off + 1] == TokenStream.TK_OPERATOR) {
                            char c1 = text.charAt(off + 1);
                            if (c1 == '=' || (ch == '+' && c1 == '+') || (ch == '-' && c1 == '-')) {
                                isAssign = true;
                                opTokenIdx = nextTok;
                            } else if (ch == '*' && c1 == '*' && off + 2 < text.length() && mask.types[off + 2] == TokenStream.TK_OPERATOR && text.charAt(off + 2) == '=') {
                                isAssign = true;
                                opTokenIdx = nextTok;
                            } else if (ch == '&' && c1 == '&' && off + 2 < text.length() && mask.types[off + 2] == TokenStream.TK_OPERATOR && text.charAt(off + 2) == '=') {
                                isAssign = true;
                                opTokenIdx = nextTok;
                            } else if (ch == '|' && c1 == '|' && off + 2 < text.length() && mask.types[off + 2] == TokenStream.TK_OPERATOR && text.charAt(off + 2) == '=') {
                                isAssign = true;
                                opTokenIdx = nextTok;
                            }
                        }
                    } else if (ch == '<' || ch == '>') {
                        if (off + 1 < text.length() && mask.types[off + 1] == TokenStream.TK_OPERATOR) {
                            char c1 = text.charAt(off + 1);
                            if (ch == '<' && c1 == '<' && off + 2 < text.length() && mask.types[off + 2] == TokenStream.TK_OPERATOR && text.charAt(off + 2) == '=') {
                                isAssign = true;
                                opTokenIdx = nextTok;
                            } else if (ch == '>' && c1 == '>') {
                                if (off + 2 < text.length() && mask.types[off + 2] == TokenStream.TK_OPERATOR && text.charAt(off + 2) == '=') {
                                    isAssign = true;
                                    opTokenIdx = nextTok;
                                } else if (off + 3 < text.length() && mask.types[off + 2] == TokenStream.TK_OPERATOR && text.charAt(off + 2) == '>'
                                        && mask.types[off + 3] == TokenStream.TK_OPERATOR && text.charAt(off + 3) == '=') {
                                    isAssign = true;
                                    opTokenIdx = nextTok;
                                }
                            }
                        }
                    } else if (ch == '?') {
                        if (off + 2 < text.length() && mask.types[off + 1] == TokenStream.TK_OPERATOR && text.charAt(off + 1) == '?'
                                && mask.types[off + 2] == TokenStream.TK_OPERATOR && text.charAt(off + 2) == '=') {
                            isAssign = true;
                            opTokenIdx = nextTok;
                        }
                    }
                }

                // Check prefix ++ or -- before this identifier (e.g. ++x or --x)
                if (!isAssign) {
                    int startOffset = tree.nodeStart[i];
                    int pLow = 0, pHigh = mask.types.length - 1;
                    int endTokenIdx = -1;
                    while (pLow <= pHigh) {
                        int mid = (pLow + pHigh) >>> 1;
                        if (mask.tokenStart[mid] < startOffset) {
                            endTokenIdx = mid;
                            pLow = mid + 1;
                        } else {
                            pHigh = mid - 1;
                        }
                    }
                    int prevTok = -1;
                    for (int t = endTokenIdx; t >= 0; t--) {
                        if (mask.types[t] != TokenStream.TK_WHITESPACE && mask.types[t] != TokenStream.TK_COMMENT) {
                            prevTok = t;
                            break;
                        }
                    }
                    if (prevTok >= 1 && mask.types[prevTok] == TokenStream.TK_OPERATOR) {
                        int prevPrevTok = prevTok - 1;
                        if (mask.tokenStart[prevTok] == mask.tokenStart[prevPrevTok] + 1 && mask.types[prevPrevTok] == TokenStream.TK_OPERATOR) {
                            char c1 = text.charAt(mask.tokenStart[prevPrevTok]);
                            char c2 = text.charAt(mask.tokenStart[prevTok]);
                            if ((c1 == '+' && c2 == '+') || (c1 == '-' && c2 == '-')) {
                                isAssign = true;
                                opTokenIdx = prevPrevTok;
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

                        if ((tree.nodeExtra[declNodeId] & 3) == JsSyntaxTree.FLAG_CONST || tree.nodeExtra[declNodeId] == JsSyntaxTree.FLAG_CONST) {
                            int targetOffset = (opTokenIdx != -1 && opTokenIdx < mask.tokenStart.length)
                                    ? mask.tokenStart[opTokenIdx]
                                    : tree.nodeStart[i];
                            int line = LinterUtils.getLine(text, targetOffset);
                            int col = LinterUtils.getColumn(text, targetOffset);
                            problems.add(new Problem(file, line, col, baseIdentifier.length(),
                                    "Cannot reassign 'const' variable '" + baseIdentifier + "'",
                                    Problem.Severity.ERROR));
                        }
                    }
                }
            }
        }
    }

    private static ResolvedImport resolveImportedSymbol(File currentFile, String typeAnn, ProjectIndex index) {
        if (typeAnn == null || index == null) return null;
        String modulePath = null;
        String exportName = null;
        if (typeAnn.startsWith("@IMPORT:")) {
            int colon = typeAnn.indexOf(':', 8);
            if (colon != -1) {
                modulePath = typeAnn.substring(8, colon);
                exportName = typeAnn.substring(colon + 1);
            }
        } else if (typeAnn.startsWith("@REQUIRE_PROP:")) {
            int colon = typeAnn.indexOf(':', 14);
            if (colon != -1) {
                modulePath = typeAnn.substring(14, colon);
                exportName = typeAnn.substring(colon + 1);
            }
        } else if (typeAnn.startsWith("@REQUIRE:")) {
            modulePath = typeAnn.substring(9);
            exportName = "default";
        }
        if (modulePath == null || modulePath.isEmpty()) return null;

        String baseUri = (currentFile != null) ? currentFile.getAbsolutePath() : index.getProjectRoot();
        if (baseUri == null) return null;

        LspLocation loc = ModuleResolver.resolveModulePath(baseUri, modulePath);
        if (loc == null || loc.uri == null) return null;

        File targetFile = new File(loc.uri);
        ParseResult targetPr = index.getOrParseJsFile(targetFile);
        if (targetPr == null || targetPr.tree == null) return null;

        int exportNode = JsExportTable.findExportNode(targetPr.tree, exportName);
        if (exportNode <= 0) {
            exportNode = JsExportTable.findTopLevelDecl(targetPr.tree, exportName);
        }
        if (exportNode <= 0) return null;

        return new ResolvedImport(targetPr.tree, exportNode, targetPr.scopeTree);
    }

    private static int findEnclosingClass(JsSyntaxTree tree, int node) {
        if (tree == null || node <= 0 || node >= tree.nodeCount) return 0;
        int curr = tree.nodeParent[node];
        int loop = 0;
        while (curr > 0 && curr < tree.nodeCount && ++loop <= tree.nodeCount) {
            if (tree.nodeType[curr] == JsSyntaxTree.N_CLASS_DECL) {
                return curr;
            }
            curr = tree.nodeParent[curr];
        }
        int offset = tree.nodeStart[node];
        int best = 0;
        int minLen = Integer.MAX_VALUE;
        for (int k = 1; k < tree.nodeCount; k++) {
            if (tree.nodeType[k] == JsSyntaxTree.N_CLASS_DECL) {
                if (offset >= tree.nodeStart[k] && offset <= tree.nodeEnd[k]) {
                    int len = tree.nodeEnd[k] - tree.nodeStart[k];
                    if (len < minLen) {
                        minLen = len;
                        best = k;
                    }
                }
            }
        }
        return best;
    }

    public static int findMethodInClass(JsSyntaxTree tree, int classNodeId, String methodName) {
        if (tree == null || classNodeId <= 0 || methodName == null) return 0;
        int child = tree.nodeChild[classNodeId];
        int guard = 0;
        while (child > 0 && child < tree.nodeCount && ++guard <= tree.nodeCount) {
            int cType = tree.nodeType[child];
            if ((cType == JsSyntaxTree.N_METHOD || cType == JsSyntaxTree.N_GETTER || cType == JsSyntaxTree.N_SETTER)
                    && methodName.equals(tree.nodeName[child])) {
                return child;
            } else if (cType == JsSyntaxTree.N_PROPERTY && methodName.equals(tree.nodeName[child])) {
                int propVal = tree.nodeChild[child];
                if (propVal > 0 && propVal < tree.nodeCount) {
                    int pvType = tree.nodeType[propVal];
                    if (pvType == JsSyntaxTree.N_FUNC_DECL || pvType == JsSyntaxTree.N_ARROW_FUNC) {
                        return propVal;
                    }
                }
            }
            child = tree.nodeSibling[child];
        }
        return 0;
    }

    public static ResolvedMethod findMethodInClassOrSuper(
            JsSyntaxTree t,
            int classNode,
            String methodName,
            File currentFile,
            ScopeTree sTree,
            ProjectIndex index,
            boolean isCrossFile) {
        if (t == null || classNode <= 0 || methodName == null) return null;
        int mNode = findMethodInClass(t, classNode, methodName);
        if (mNode > 0) {
            return new ResolvedMethod(t, mNode, isCrossFile);
        }
        // Check superclass
        String superName = t.nodeTypeAnn[classNode];
        if (superName != null && !superName.isEmpty() && !superName.startsWith("@")) {
            int scopeId = sTree != null ? sTree.findScopeAt(t.nodeStart[classNode], t) : 0;
            int[] superResolved = sTree != null ? sTree.lookupSymbol(superName, scopeId, t.nodeStart[classNode], t) : null;
            if (superResolved != null && superResolved[1] > 0 && superResolved[1] < t.nodeCount) {
                int sNode = superResolved[1];
                if (t.nodeType[sNode] == JsSyntaxTree.N_CLASS_DECL) {
                    return findMethodInClassOrSuper(t, sNode, methodName, currentFile, sTree, index, isCrossFile);
                } else {
                    String sTypeAnn = t.nodeTypeAnn[sNode];
                    if (sTypeAnn != null && (sTypeAnn.startsWith("@IMPORT:") || sTypeAnn.startsWith("@REQUIRE:"))) {
                        ResolvedImport imp = resolveImportedSymbol(currentFile, sTypeAnn, index);
                        if (imp != null && imp.tree != null && imp.nodeId > 0 && imp.tree.nodeType[imp.nodeId] == JsSyntaxTree.N_CLASS_DECL) {
                            return findMethodInClassOrSuper(imp.tree, imp.nodeId, methodName, currentFile, imp.scopeTree, index, true);
                        }
                    }
                }
            } else if (index != null) {
                List<com.cocode.vcode.ide.core.lsp.LspLocation> defs = index.findDefinitions(superName);
                if (defs != null) {
                    for (com.cocode.vcode.ide.core.lsp.LspLocation loc : defs) {
                        if (loc == null || loc.uri == null) continue;
                        File tFile = new File(loc.uri);
                        ParseResult pr = index.getOrParseJsFile(tFile);
                        if (pr != null && pr.tree != null) {
                            for (int k = 1; k < pr.tree.nodeCount; k++) {
                                if (pr.tree.nodeType[k] == JsSyntaxTree.N_CLASS_DECL && superName.equals(pr.tree.nodeName[k])) {
                                    return findMethodInClassOrSuper(pr.tree, k, methodName, tFile, pr.scopeTree, index, true);
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public static ResolvedMethod resolveInstanceMethod(
            String receiver,
            String methodName,
            int callOffset,
            File currentFile,
            String source,
            JsSyntaxTree tree,
            ScopeTree scopeTree,
            ProjectIndex index) {
        if (receiver == null || receiver.isEmpty() || methodName == null || methodName.isEmpty())
            return null;

        String className = null;
        if (receiver.startsWith("new ") || receiver.startsWith("(new ")) {
            String sub = receiver.startsWith("new ") ? receiver.substring(4).trim() : receiver.substring(5).trim();
            int paren = sub.indexOf('(');
            if (paren > 0) sub = sub.substring(0, paren).trim();
            int generic = sub.indexOf('<');
            if (generic > 0) sub = sub.substring(0, generic).trim();
            int dot = sub.lastIndexOf('.');
            if (dot >= 0) sub = sub.substring(dot + 1).trim();
            className = sub.isEmpty() ? null : sub;
        } else {
            int scopeId = scopeTree != null ? scopeTree.findScopeAt(callOffset, tree) : 0;
            int[] recResolved = scopeTree != null ? scopeTree.lookupSymbol(receiver, scopeId, callOffset, tree) : null;
            if (recResolved != null && recResolved[1] > 0 && recResolved[1] < tree.nodeCount) {
                int recNode = recResolved[1];
                int rType = tree.nodeType[recNode];

                // Check if receiver is directly an object literal (e.g. const math = { getName() {} })
                int objNode = (rType == JsSyntaxTree.N_VAR_DECL) ? tree.nodeChild[recNode] : recNode;
                if (objNode > 0 && objNode < tree.nodeCount && tree.nodeType[objNode] == JsSyntaxTree.N_OBJECT_LITERAL) {
                    int mNode = findMethodInClass(tree, objNode, methodName);
                    if (mNode > 0) {
                        return new ResolvedMethod(tree, mNode, false);
                    }
                }

                String typeAnn = tree.nodeTypeAnn[recNode];
                if (typeAnn != null && !typeAnn.isEmpty() && !typeAnn.startsWith("@")) {
                    int generic = typeAnn.indexOf('<');
                    String cleaned = (generic > 0) ? typeAnn.substring(0, generic).trim() : typeAnn.trim();
                    if (cleaned.startsWith(":")) cleaned = cleaned.substring(1).trim();
                    int dot = cleaned.lastIndexOf('.');
                    if (dot >= 0) cleaned = cleaned.substring(dot + 1).trim();
                    className = cleaned;
                } else if (source != null && tree.nodeStart[recNode] >= 0 && tree.nodeEnd[recNode] <= source.length()) {
                    int start = tree.nodeStart[recNode];
                    int end = tree.nodeEnd[recNode];
                    if (end > start) {
                        String declText = source.substring(start, end);
                        int newIdx = declText.indexOf("new ");
                        if (newIdx >= 0) {
                            int idStart = newIdx + 4;
                            while (idStart < declText.length() && Character.isWhitespace(declText.charAt(idStart)))
                                idStart++;
                            int idEnd = idStart;
                            while (idEnd < declText.length() && (Character.isLetterOrDigit(declText.charAt(idEnd)) || declText.charAt(idEnd) == '_' || declText.charAt(idEnd) == '$' || declText.charAt(idEnd) == '.')) {
                                idEnd++;
                            }
                            if (idEnd > idStart) {
                                String rawName = declText.substring(idStart, idEnd);
                                int dot = rawName.lastIndexOf('.');
                                className = (dot >= 0) ? rawName.substring(dot + 1) : rawName;
                            }
                        }
                    }
                }
            }
            if (className == null && source != null && callOffset > 0) {
                // Check if assigned before this offset in source, e.g. "receiver = new ClassName"
                int limit = Math.min(callOffset, source.length());
                String prefixText = source.substring(0, limit);
                int lastAssign = prefixText.lastIndexOf(receiver + " = new ");
                if (lastAssign < 0) {
                    lastAssign = prefixText.lastIndexOf(receiver + " =new ");
                }
                if (lastAssign >= 0) {
                    int newIdx = prefixText.indexOf("new ", lastAssign);
                    if (newIdx >= 0) {
                        int idStart = newIdx + 4;
                        while (idStart < prefixText.length() && Character.isWhitespace(prefixText.charAt(idStart)))
                            idStart++;
                        int idEnd = idStart;
                        while (idEnd < prefixText.length() && (Character.isLetterOrDigit(prefixText.charAt(idEnd)) || prefixText.charAt(idEnd) == '_' || prefixText.charAt(idEnd) == '$' || prefixText.charAt(idEnd) == '.')) {
                            idEnd++;
                        }
                        if (idEnd > idStart) {
                            String rawName = prefixText.substring(idStart, idEnd);
                            int dot = rawName.lastIndexOf('.');
                            className = (dot >= 0) ? rawName.substring(dot + 1) : rawName;
                        }
                    }
                }
            }
        }

        if (className == null || className.isEmpty()) return null;

        // Check declaration in the current file scope
        int scopeId = scopeTree != null ? scopeTree.findScopeAt(callOffset, tree) : 0;
        int[] classResolved = scopeTree != null ? scopeTree.lookupSymbol(className, scopeId, callOffset, tree) : null;
        if (classResolved != null && classResolved[1] > 0 && classResolved[1] < tree.nodeCount) {
            int cNode = classResolved[1];
            if (tree.nodeType[cNode] == JsSyntaxTree.N_CLASS_DECL) {
                ResolvedMethod m = findMethodInClassOrSuper(tree, cNode, methodName, currentFile, scopeTree, index, false);
                if (m != null) return m;
            } else {
                String typeAnn = tree.nodeTypeAnn[cNode];
                if (typeAnn != null && (typeAnn.startsWith("@IMPORT:") || typeAnn.startsWith("@REQUIRE:"))) {
                    ResolvedImport imp = resolveImportedSymbol(currentFile, typeAnn, index);
                    if (imp != null && imp.tree != null && imp.nodeId > 0 && imp.tree.nodeType[imp.nodeId] == JsSyntaxTree.N_CLASS_DECL) {
                        ResolvedMethod m = findMethodInClassOrSuper(imp.tree, imp.nodeId, methodName, currentFile, imp.scopeTree, index, true);
                        if (m != null) return m;
                    }
                }
            }
        }

        // Check cross-file declarations in ProjectIndex
        if (index != null) {
            List<com.cocode.vcode.ide.core.lsp.LspLocation> defs = index.findDefinitions(className);
            if (defs != null) {
                for (com.cocode.vcode.ide.core.lsp.LspLocation loc : defs) {
                    if (loc == null || loc.uri == null) continue;
                    File tFile = new File(loc.uri);
                    ParseResult pr = index.getOrParseJsFile(tFile);
                    if (pr != null && pr.tree != null) {
                        for (int k = 1; k < pr.tree.nodeCount; k++) {
                            if (pr.tree.nodeType[k] == JsSyntaxTree.N_CLASS_DECL && className.equals(pr.tree.nodeName[k])) {
                                boolean isCross = currentFile == null || !currentFile.getAbsolutePath().equals(tFile.getAbsolutePath());
                                ResolvedMethod m = findMethodInClassOrSuper(pr.tree, k, methodName, tFile, pr.scopeTree, index, isCross);
                                if (m != null) return m;
                            }
                        }
                    }
                }
            }
        }

        return null;
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

                int totalParams = 0;
                int minParams = 0;
                boolean isVariadic = false;

                if (dotIdx >= 0) {
                    ResolvedMethod rm = null;
                    if (identifier.startsWith("this.")) {
                        int enclosingClass = findEnclosingClass(tree, i);
                        if (enclosingClass > 0) {
                            rm = findMethodInClassOrSuper(tree, enclosingClass, baseIdentifier, file, scopeTree, index, false);
                        }
                    } else {
                        String receiver = identifier.substring(0, dotIdx);
                        rm = resolveInstanceMethod(receiver, baseIdentifier, tree.nodeStart[i], file, text, tree, scopeTree, index);
                    }

                    if (rm != null) {
                        int child = rm.tree.nodeChild[rm.methodNode];
                        int childLoop = 0;
                        while (child > 0 && child < rm.tree.nodeCount && ++childLoop <= rm.tree.nodeCount) {
                            if (rm.tree.nodeType[child] == JsSyntaxTree.N_PARAM) {
                                if ((rm.tree.nodeExtra[child] & JsSyntaxTree.FLAG_REST) != 0) {
                                    isVariadic = true;
                                } else {
                                    totalParams++;
                                    if ((rm.tree.nodeExtra[child] & JsSyntaxTree.FLAG_DEFAULT) == 0) {
                                        minParams++;
                                    }
                                }
                            }
                            child = rm.tree.nodeSibling[child];
                        }
                    } else {
                        // Other member expression (e.g. Math.max, console.log)
                        JsStandardLibrary.SignatureInfo builtin = JsStandardLibrary.getBuiltinSignature(identifier, null);
                        if (builtin != null && builtin.parameters != null) {
                            for (String p : builtin.parameters) {
                                if (p.contains("...")) isVariadic = true;
                                else {
                                    totalParams++;
                                    if (!JsStandardLibrary.isOptionalParameter(p)) {
                                        minParams++;
                                    }
                                }
                            }
                        } else {
                            // Non-builtin member calls on unknown objects cannot be statically verified without full type inference
                            continue;
                        }
                    }
                } else {
                    int scopeId = scopeTree.findScopeAt(tree.nodeStart[i], tree);
                    int[] resolved = scopeTree.lookupSymbol(baseIdentifier, scopeId, tree.nodeStart[i], tree);

                    if (resolved != null) {
                        int declNodeId = resolved[1];
                        int targetNodeId = declNodeId;
                        String typeAnn = (declNodeId > 0 && declNodeId < tree.nodeCount) ? tree.nodeTypeAnn[declNodeId] : null;

                        if (typeAnn != null && (typeAnn.startsWith("@IMPORT:") || typeAnn.startsWith("@REQUIRE:") || typeAnn.startsWith("@REQUIRE_PROP:"))) {
                            ResolvedImport target = resolveImportedSymbol(file, typeAnn, index);
                            if (target == null || target.tree == null || target.nodeId <= 0) {
                                continue; // External or unresolvable import: bypass arity check to prevent false positives
                            }
                            JsSyntaxTree tTree = target.tree;
                            int tNode = target.nodeId;
                            if (tTree.nodeType[tNode] == JsSyntaxTree.N_CLASS_DECL) {
                                int[] ctorInfo = resolveClassConstructorParams(tTree, tNode, target.scopeTree, 0, index);
                                if (ctorInfo == null) continue;
                                minParams = ctorInfo[0];
                                totalParams = ctorInfo[1];
                                if (ctorInfo[2] == 1) isVariadic = true;
                            } else if (tTree.nodeType[tNode] == JsSyntaxTree.N_FUNC_DECL || tTree.nodeType[tNode] == JsSyntaxTree.N_ARROW_FUNC) {
                                int child = tTree.nodeChild[tNode];
                                int childLoop = 0;
                                while (child > 0 && child < tTree.nodeCount && ++childLoop <= tTree.nodeCount) {
                                    if (tTree.nodeType[child] == JsSyntaxTree.N_PARAM) {
                                        if ((tTree.nodeExtra[child] & JsSyntaxTree.FLAG_REST) != 0) {
                                            isVariadic = true;
                                        } else {
                                            totalParams++;
                                            if ((tTree.nodeExtra[child] & JsSyntaxTree.FLAG_DEFAULT) == 0) {
                                                minParams++;
                                            }
                                        }
                                    }
                                    child = tTree.nodeSibling[child];
                                }
                            } else if (tTree.nodeType[tNode] == JsSyntaxTree.N_VAR_DECL) {
                                int child = tTree.nodeChild[tNode];
                                if (child > 0 && child < tTree.nodeCount && (tTree.nodeType[child] == JsSyntaxTree.N_FUNC_DECL
                                        || tTree.nodeType[child] == JsSyntaxTree.N_ARROW_FUNC)) {
                                    int pChild = tTree.nodeChild[child];
                                    int pLoop = 0;
                                    while (pChild > 0 && pChild < tTree.nodeCount && ++pLoop <= tTree.nodeCount) {
                                        if (tTree.nodeType[pChild] == JsSyntaxTree.N_PARAM) {
                                            if ((tTree.nodeExtra[pChild] & JsSyntaxTree.FLAG_REST) != 0) {
                                                isVariadic = true;
                                            } else {
                                                totalParams++;
                                                if ((tTree.nodeExtra[pChild] & JsSyntaxTree.FLAG_DEFAULT) == 0) {
                                                    minParams++;
                                                }
                                            }
                                        }
                                        pChild = tTree.nodeSibling[pChild];
                                    }
                                } else if (child > 0 && child < tTree.nodeCount && tTree.nodeType[child] == JsSyntaxTree.N_CLASS_DECL) {
                                    int[] ctorInfo = resolveClassConstructorParams(tTree, child, target.scopeTree, 0, index);
                                    if (ctorInfo == null) continue;
                                    minParams = ctorInfo[0];
                                    totalParams = ctorInfo[1];
                                    if (ctorInfo[2] == 1) isVariadic = true;
                                } else {
                                    continue;
                                }
                            } else {
                                continue;
                            }
                        } else {
                            if (declNodeId > 0 && declNodeId < tree.nodeCount && tree.nodeType[declNodeId] == JsSyntaxTree.N_VAR_DECL) {
                                int child = tree.nodeChild[declNodeId];
                                if (child > 0 && child < tree.nodeCount && (tree.nodeType[child] == JsSyntaxTree.N_ARROW_FUNC
                                        || tree.nodeType[child] == JsSyntaxTree.N_FUNC_DECL
                                        || tree.nodeType[child] == JsSyntaxTree.N_CLASS_DECL)) {
                                    targetNodeId = child;
                                }
                            }

                            int targetType = (targetNodeId > 0 && targetNodeId < tree.nodeCount) ? tree.nodeType[targetNodeId] : 0;
                            if (targetType == JsSyntaxTree.N_CLASS_DECL) {
                                int[] ctorInfo = resolveClassConstructorParams(tree, targetNodeId, scopeTree, scopeId, index);
                                if (ctorInfo == null) continue;
                                minParams = ctorInfo[0];
                                totalParams = ctorInfo[1];
                                if (ctorInfo[2] == 1) isVariadic = true;
                            } else if (targetType == JsSyntaxTree.N_FUNC_DECL || targetType == JsSyntaxTree.N_ARROW_FUNC) {
                                int child = tree.nodeChild[targetNodeId];
                                int childLoop = 0;
                                while (child > 0 && child < tree.nodeCount && ++childLoop <= tree.nodeCount) {
                                    if (tree.nodeType[child] == JsSyntaxTree.N_PARAM) {
                                        if ((tree.nodeExtra[child] & JsSyntaxTree.FLAG_REST) != 0) {
                                            isVariadic = true;
                                        } else {
                                            totalParams++;
                                            if ((tree.nodeExtra[child] & JsSyntaxTree.FLAG_DEFAULT) == 0) {
                                                minParams++;
                                            }
                                        }
                                    }
                                    child = tree.nodeSibling[child];
                                }
                            } else {
                                // Dynamic variable or callback parameter: arity is unknown statically
                                continue;
                            }
                        }
                    } else {
                        List<LspLocation> defs = (index != null) ? index.findDefinitions(baseIdentifier) : java.util.Collections.emptyList();
                        SymbolEntry targetEntry = null;
                        LspLocation targetLoc = null;

                        for (LspLocation loc : defs) {
                            List<SymbolEntry> syms = index.getFileSymbols(loc.uri);
                            for (SymbolEntry s : syms) {
                                if (s.name.equals(baseIdentifier) && (s.kind == SymbolEntry.KIND_FUNCTION || s.kind == SymbolEntry.KIND_CLASS || s.kind == SymbolEntry.KIND_METHOD)) {
                                    targetEntry = s;
                                    targetLoc = loc;
                                    break;
                                }
                            }
                            if (targetEntry != null) break;
                        }

                        if (targetEntry != null && targetLoc != null) {
                            ParseResult targetPr = index.getOrParseJsFile(new File(targetLoc.uri));
                            if (targetPr != null && targetPr.tree != null) {
                                int topId = JsExportTable.findTopLevelDecl(targetPr.tree, baseIdentifier);
                                if (topId <= 0) {
                                    topId = JsExportTable.findExportNode(targetPr.tree, baseIdentifier);
                                }
                                if (topId > 0) {
                                    int tType = targetPr.tree.nodeType[topId];
                                    if (tType == JsSyntaxTree.N_CLASS_DECL) {
                                        int[] ctorInfo = resolveClassConstructorParams(targetPr.tree, topId, targetPr.scopeTree, 0, index);
                                        if (ctorInfo == null) continue;
                                        minParams = ctorInfo[0];
                                        totalParams = ctorInfo[1];
                                        if (ctorInfo[2] == 1) isVariadic = true;
                                    } else if (tType == JsSyntaxTree.N_FUNC_DECL || tType == JsSyntaxTree.N_ARROW_FUNC) {
                                        int c = targetPr.tree.nodeChild[topId];
                                        int cLoop = 0;
                                        while (c > 0 && c < targetPr.tree.nodeCount && ++cLoop <= targetPr.tree.nodeCount) {
                                            if (targetPr.tree.nodeType[c] == JsSyntaxTree.N_PARAM) {
                                                if ((targetPr.tree.nodeExtra[c] & JsSyntaxTree.FLAG_REST) != 0) {
                                                    isVariadic = true;
                                                } else {
                                                    totalParams++;
                                                    if ((targetPr.tree.nodeExtra[c] & JsSyntaxTree.FLAG_DEFAULT) == 0) {
                                                        minParams++;
                                                    }
                                                }
                                            }
                                            c = targetPr.tree.nodeSibling[c];
                                        }
                                    } else {
                                        continue;
                                    }
                                } else {
                                    continue;
                                }
                            } else if (targetEntry.detail != null) {
                                String detail = targetEntry.detail;
                                if (detail.contains("...")) isVariadic = true;

                                String[] declaredParams = detail.trim().isEmpty() ? new String[0] : detail.split(",");
                                totalParams = declaredParams.length;
                                minParams = 0;
                                for (String p : declaredParams) {
                                    if (!p.contains("...") && !JsStandardLibrary.isOptionalParameter(p)) {
                                        minParams++;
                                    }
                                }
                            } else {
                                continue;
                            }
                        } else {
                            JsStandardLibrary.SignatureInfo builtin = JsStandardLibrary.getBuiltinSignature(baseIdentifier, null);
                            if (builtin != null && builtin.parameters != null) {
                                for (String p : builtin.parameters) {
                                    if (p.contains("...")) isVariadic = true;
                                    else {
                                        totalParams++;
                                        if (!JsStandardLibrary.isOptionalParameter(p)) {
                                            minParams++;
                                        }
                                    }
                                }
                            } else {
                                continue;
                            }
                        }
                    }
                }

                if ("forEach".equals(baseIdentifier) || "map".equals(baseIdentifier) || "filter".equals(baseIdentifier)
                        || "some".equals(baseIdentifier) || "every".equals(baseIdentifier) || "find".equals(baseIdentifier)
                        || "findIndex".equals(baseIdentifier) || "findLast".equals(baseIdentifier) || "findLastIndex".equals(baseIdentifier)
                        || "flatMap".equals(baseIdentifier) || "reduce".equals(baseIdentifier) || "reduceRight".equals(baseIdentifier)) {
                    minParams = Math.min(minParams, 1);
                    totalParams = Math.max(totalParams, 2);
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

                if (actualArgs < minParams) {
                    int line = LinterUtils.getLine(text, tree.nodeStart[i]);
                    int col = LinterUtils.getColumn(text, tree.nodeStart[i]);
                    String expectedStr = (minParams == totalParams) ? String.valueOf(totalParams) : "at least " + minParams;
                    problems.add(new Problem(file, line, col, identifier.length(),
                            "Too few arguments: '" + identifier + "' expects " + expectedStr + " argument(s), but got " + actualArgs,
                            Problem.Severity.WARNING));
                } else if (actualArgs > totalParams) {
                    int line = LinterUtils.getLine(text, tree.nodeStart[i]);
                    int col = LinterUtils.getColumn(text, tree.nodeStart[i]);
                    problems.add(new Problem(file, line, col, identifier.length(),
                            "Too many arguments: '" + identifier + "' expects " + totalParams + " argument(s), but got " + actualArgs,
                            Problem.Severity.WARNING));
                }
            }
        }
    }

    private static int[] resolveClassConstructorParams(JsSyntaxTree tree, int classNodeId, ScopeTree scopeTree, int scopeId, ProjectIndex index) {
        if (tree == null || classNodeId <= 0 || classNodeId >= tree.nodeCount) {
            return new int[]{0, 0, 0};
        }
        // Look for an explicit constructor method in this class
        int child = tree.nodeChild[classNodeId];
        int guard = 0;
        while (child > 0 && child < tree.nodeCount && ++guard <= tree.nodeCount) {
            if (tree.nodeType[child] == JsSyntaxTree.N_METHOD && "constructor".equals(tree.nodeName[child])) {
                int total = 0;
                int min = 0;
                boolean variadic = false;
                int pChild = tree.nodeChild[child];
                int pGuard = 0;
                while (pChild > 0 && pChild < tree.nodeCount && ++pGuard <= tree.nodeCount) {
                    if (tree.nodeType[pChild] == JsSyntaxTree.N_PARAM) {
                        if ((tree.nodeExtra[pChild] & JsSyntaxTree.FLAG_REST) != 0) {
                            variadic = true;
                        } else {
                            total++;
                            if ((tree.nodeExtra[pChild] & JsSyntaxTree.FLAG_DEFAULT) == 0) {
                                min++;
                            }
                        }
                    }
                    pChild = tree.nodeSibling[pChild];
                }
                return new int[]{min, total, variadic ? 1 : 0};
            }
            child = tree.nodeSibling[child];
        }

        // Check superclass constructor if the class extends another class
        String superName = tree.nodeTypeAnn[classNodeId];
        if (superName != null && !superName.isEmpty()) {
            // Check if superclass is in scope in the same file
            if (scopeTree != null) {
                int[] superResolved = scopeTree.lookupSymbol(superName, scopeId);
                if (superResolved != null && superResolved[1] > 0 && superResolved[1] < tree.nodeCount) {
                    int superDeclId = superResolved[1];
                    String superTypeAnn = tree.nodeTypeAnn[superDeclId];
                    if (superTypeAnn != null && (superTypeAnn.startsWith("@IMPORT:") || superTypeAnn.startsWith("@REQUIRE:"))) {
                        ResolvedImport imp = resolveImportedSymbol(null, superTypeAnn, index);
                        if (imp != null && imp.tree != null && imp.nodeId > 0 && imp.tree.nodeType[imp.nodeId] == JsSyntaxTree.N_CLASS_DECL) {
                            return resolveClassConstructorParams(imp.tree, imp.nodeId, imp.scopeTree, 0, index);
                        }
                    } else if (tree.nodeType[superDeclId] == JsSyntaxTree.N_CLASS_DECL) {
                        return resolveClassConstructorParams(tree, superDeclId, scopeTree, scopeId, index);
                    } else if (tree.nodeType[superDeclId] == JsSyntaxTree.N_VAR_DECL) {
                        int sc = tree.nodeChild[superDeclId];
                        if (sc > 0 && sc < tree.nodeCount && tree.nodeType[sc] == JsSyntaxTree.N_CLASS_DECL) {
                            return resolveClassConstructorParams(tree, sc, scopeTree, scopeId, index);
                        }
                    }
                }
            }

            // Check built-in standard library classes (e.g. Error, Map, Set, Event, etc.)
            JsStandardLibrary.SignatureInfo builtinSuper = JsStandardLibrary.getBuiltinSignature(superName, null);
            if (builtinSuper != null && builtinSuper.parameters != null) {
                boolean variadic = false;
                int total = 0;
                int min = 0;
                for (String p : builtinSuper.parameters) {
                    if (p.contains("...")) variadic = true;
                    else {
                        total++;
                        if (!JsStandardLibrary.isOptionalParameter(p)) {
                            min++;
                        }
                    }
                }
                return new int[]{min, total, variadic ? 1 : 0};
            }

            // Check cross-file project symbols via index
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
                                int total = 0;
                                int min = 0;
                                int open = detail.indexOf('(');
                                int close = detail.lastIndexOf(')');
                                if (open != -1 && close > open) {
                                    String paramStr = detail.substring(open + 1, close).trim();
                                    if (!paramStr.isEmpty()) {
                                        String[] parts = paramStr.split(",");
                                        for (String p : parts) {
                                            String pt = p.trim();
                                            if (pt.contains("...")) {
                                                variadic = true;
                                            } else {
                                                total++;
                                                if (!JsStandardLibrary.isOptionalParameter(pt)) {
                                                    min++;
                                                }
                                            }
                                        }
                                    }
                                }
                                return new int[]{min, total, variadic ? 1 : 0};
                            }
                            return new int[]{0, 0, 0};
                        }
                    }
                }
            }

            // Superclass cannot be resolved (e.g. external node_modules or unindexed library)
            // Return null to bypass arity check and prevent false positives
            return null;
        }

        // Default ES6 constructor takes 0 arguments when no explicit constructor exists
        return new int[]{0, 0, 0};
    }

    private static void checkUndefined(File file, String text, TokenStream mask, ScopeTree scopeTree, JsSyntaxTree tree, List<Problem> problems) {
        int ieCount = 0;
        int[] ieStart = new int[16];
        int[] ieEnd = new int[16];
        for (int i = 1; i < tree.nodeCount; i++) {
            boolean isClause = (tree.nodeType[i] == JsSyntaxTree.N_IMPORT);
            if (tree.nodeType[i] == JsSyntaxTree.N_EXPORT) {
                int child = tree.nodeChild[i];
                if (child == 0 || tree.nodeType[child] == JsSyntaxTree.N_IDENTIFIER) {
                    if (!"default".equals(tree.nodeName[i])) {
                        isClause = true;
                    }
                }
            }
            if (isClause) {
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

            // Skip the rest of this identifier in the character stream
            t = idEnd - 1;

            // Contextual keywords are never undeclared variables
            if ("of".equals(id) || "from".equals(id) || "as".equals(id) || "target".equals(id) || "meta".equals(id)) {
                continue;
            }

            // Check preceding char to see if it's a property access or private identifier (#field)
            int preIndex = offset - 1;
            while (preIndex >= 0 && Character.isWhitespace(text.charAt(preIndex))) preIndex--;
            if (preIndex >= 0 && (text.charAt(preIndex) == '.' || text.charAt(preIndex) == '#')) continue;

            // Check preceding tokens for break/continue labels and typeof operands
            int prevTokenIdx = offset - 1;
            while (prevTokenIdx >= 0 && (mask.types[prevTokenIdx] == TokenStream.TK_WHITESPACE || mask.types[prevTokenIdx] == TokenStream.TK_COMMENT)) {
                prevTokenIdx--;
            }
            if (prevTokenIdx >= 0) {
                int checkTok = prevTokenIdx;
                if (mask.types[checkTok] == TokenStream.TK_PUNCT && text.charAt(mask.tokenStart[checkTok]) == '(') {
                    checkTok--;
                    while (checkTok >= 0 && (mask.types[checkTok] == TokenStream.TK_WHITESPACE || mask.types[checkTok] == TokenStream.TK_COMMENT)) {
                        checkTok--;
                    }
                }
                if (checkTok >= 0 && (mask.types[checkTok] == TokenStream.TK_KEYWORD || mask.types[checkTok] == TokenStream.TK_IDENTIFIER)) {
                    int kwStart = mask.tokenStart[checkTok];
                    int kwEnd = kwStart;
                    while (kwEnd < text.length() && (Character.isLetterOrDigit(text.charAt(kwEnd)) || text.charAt(kwEnd) == '$' || text.charAt(kwEnd) == '_')) kwEnd++;
                    String prevWord = text.substring(kwStart, kwEnd);
                    if ("break".equals(prevWord) || "continue".equals(prevWord)) {
                        continue;
                    }
                    if ("typeof".equals(prevWord)) {
                        continue;
                    }
                }
            }

            // Check preceding tokens to see if it's a declaration (only if AST is missing)
            if (tree.nodesByOffset == null && isDeclarationSite(text, offset)) continue;

            // Allow object keys in object literals: { key: value }
            int postIndex = idEnd;
            while (postIndex < text.length() && Character.isWhitespace(text.charAt(postIndex)))
                postIndex++;
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
                            if (type == JsSyntaxTree.N_IDENTIFIER || type == JsSyntaxTree.N_CALL_EXPR || type == JsSyntaxTree.N_MEMBER_EXPR) {
                                isAstIdentifier = true;
                            } else if (tree.nodeName[tree.nodesByOffset[temp]] != null && (type == JsSyntaxTree.N_PARAM || type == JsSyntaxTree.N_VAR_DECL || type == JsSyntaxTree.N_FUNC_DECL || type == JsSyntaxTree.N_CLASS_DECL || type == JsSyntaxTree.N_METHOD || type == JsSyntaxTree.N_PROPERTY || type == JsSyntaxTree.N_GETTER || type == JsSyntaxTree.N_SETTER || type == JsSyntaxTree.N_IMPORT || type == JsSyntaxTree.N_ENUM || type == JsSyntaxTree.N_INTERFACE || type == JsSyntaxTree.N_TYPE_ALIAS)) {
                                isAstDeclaration = true;
                            }
                            temp--;
                        }
                        temp = mid + 1;
                        while (temp < tree.nodeCount && tree.nodeStart[tree.nodesByOffset[temp]] == astOffset) {
                            int type = tree.nodeType[tree.nodesByOffset[temp]];
                            if (type == JsSyntaxTree.N_IDENTIFIER || type == JsSyntaxTree.N_CALL_EXPR || type == JsSyntaxTree.N_MEMBER_EXPR) {
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
                else if (sub.endsWith("class") || sub.endsWith("catch") || sub.endsWith("const"))
                    kwLen = 5;
                else if (sub.endsWith("enum") || sub.endsWith("type")) kwLen = 4;
                else kwLen = 3; // let, var, get, set
                int wordStart = (i + 1) - kwLen;
                return wordStart >= 0 && (wordStart == 0 || !Character.isLetterOrDigit(text.charAt(wordStart - 1)));
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
                        "console.warn".equals(tree.nodeName[i]) || "console.info".equals(tree.nodeName[i])) {

                    int line = LinterUtils.getLine(text, tree.nodeStart[i]);
                    int col = LinterUtils.getColumn(text, tree.nodeStart[i]);
                    problems.add(new Problem(file, line, col, tree.nodeName[i].length(),
                            "Unexpected console statement",
                            Problem.Severity.INFO));
                }
            }
        }

        // Token stream rules
        int tokenCount = mask.length;
        for (int i = 0; i < tokenCount; i++) {
            byte type = mask.types[i];
            if (type != TokenStream.TK_OPERATOR) continue;

            int opStart = i;
            int opEnd = i;
            while (opEnd < tokenCount && mask.types[opEnd] == TokenStream.TK_OPERATOR) {
                opEnd++;
            }
            String op = text.substring(opStart, opEnd).trim();
            i = opEnd - 1;

            if ("/".equals(op)) {
                int j = opEnd;
                while (j < tokenCount && (mask.types[j] == TokenStream.TK_WHITESPACE || mask.types[j] == TokenStream.TK_COMMENT)) {
                    j++;
                }
                if (j < tokenCount && mask.types[j] == TokenStream.TK_NUMBER) {
                    int nStart = mask.tokenStart[j];
                    int nEnd = nStart;
                    while (nEnd < tokenCount && mask.tokenStart[nEnd] == nStart) {
                        nEnd++;
                    }
                    String numStr = text.substring(nStart, nEnd).trim();
                    if ("0".equals(numStr)) {
                        int line = LinterUtils.getLine(text, opStart);
                        int col = LinterUtils.getColumn(text, opStart);
                        problems.add(new Problem(file, line, col, 1, "Division by zero", Problem.Severity.WARNING));
                    }
                }
            }

            if ("===".equals(op) || "==".equals(op) || "!==".equals(op) || "!=".equals(op)) {
                boolean hasTypeof = false;
                boolean hasUndefined = false;

                // Walk backwards across tokens in the same expression
                int j = opStart - 1;
                while (j >= 0) {
                    byte pType = mask.types[j];
                    if (pType == TokenStream.TK_WHITESPACE || pType == TokenStream.TK_COMMENT) {
                        j--;
                        continue;
                    }
                    if (pType == TokenStream.TK_PUNCT) {
                        char pc = text.charAt(j);
                        if (pc == ';' || pc == '{' || pc == '}') break;
                        j--;
                        continue;
                    }
                    if (pType == TokenStream.TK_KEYWORD || pType == TokenStream.TK_IDENTIFIER) {
                        int wStart = mask.tokenStart[j];
                        int wEnd = wStart;
                        while (wEnd < tokenCount && mask.tokenStart[wEnd] == wStart) {
                            wEnd++;
                        }
                        String word = text.substring(wStart, wEnd);
                        if ("typeof".equals(word)) hasTypeof = true;
                        if ("undefined".equals(word)) hasUndefined = true;
                        j = wStart - 1;
                        continue;
                    }
                    j--;
                }

                // Walk forwards across tokens in the same expression
                j = opEnd;
                while (j < tokenCount) {
                    byte nType = mask.types[j];
                    if (nType == TokenStream.TK_WHITESPACE || nType == TokenStream.TK_COMMENT) {
                        j++;
                        continue;
                    }
                    if (nType == TokenStream.TK_PUNCT) {
                        char nc = text.charAt(j);
                        if (nc == ';' || nc == '{' || nc == '}') break;
                        j++;
                        continue;
                    }
                    if (nType == TokenStream.TK_KEYWORD || nType == TokenStream.TK_IDENTIFIER) {
                        int wStart = mask.tokenStart[j];
                        int wEnd = wStart;
                        while (wEnd < tokenCount && mask.tokenStart[wEnd] == wStart) {
                            wEnd++;
                        }
                        String word = text.substring(wStart, wEnd);
                        if ("typeof".equals(word)) hasTypeof = true;
                        if ("undefined".equals(word)) hasUndefined = true;
                        j = wEnd;
                        continue;
                    }
                    j++;
                }

                if (hasTypeof && hasUndefined) {
                    int line = LinterUtils.getLine(text, opStart);
                    int col = LinterUtils.getColumn(text, opStart);
                    problems.add(new Problem(file, line, col, op.length(),
                            "Comparing typeof to undefined directly; typeof always returns a string (e.g. 'undefined')",
                            Problem.Severity.WARNING));
                }
            }
        }
    }

    private static final class ResolvedImport {
        final JsSyntaxTree tree;
        final int nodeId;
        final ScopeTree scopeTree;

        ResolvedImport(JsSyntaxTree tree, int nodeId, ScopeTree scopeTree) {
            this.tree = tree;
            this.nodeId = nodeId;
            this.scopeTree = scopeTree;
        }
    }

    public static final class ResolvedMethod {
        public final JsSyntaxTree tree;
        public final int methodNode;
        public final boolean isCrossFile;

        public ResolvedMethod(JsSyntaxTree tree, int methodNode, boolean isCrossFile) {
            this.tree = tree;
            this.methodNode = methodNode;
            this.isCrossFile = isCrossFile;
        }
    }
}
