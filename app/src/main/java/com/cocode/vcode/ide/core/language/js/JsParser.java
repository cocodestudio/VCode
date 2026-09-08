package com.cocode.vcode.ide.core.language.js;



import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;



/**
 * High-performance, iterative top-down parser that consumes a {@link TokenStream} and builds a {@link JsSyntaxTree}.
 * Traverses module scopes, class declarations, functions, and nested statement blocks, constructing flat AST node
 * arrays with parent/child offset mapping without deep recursive call stacks to ensure sub-millisecond AST construction.
 */
public class JsParser {



    public static JsSyntaxTree parseFull(String source, TokenStream stream) {

        return parseTopLevel(source, stream, null); // backward compatibility alias

    }



    public static JsSyntaxTree parseFull(String source, TokenStream stream, JsSyntaxTree buffer) {

        return parseTopLevel(source, stream, buffer);

    }



    public static JsSyntaxTree parseIncremental(String newSource, TokenStream newTokens, String oldSource, JsSyntaxTree oldTree, TokenStream oldTokens, int editStart, int editEndOld, int editEndNew) {

        int r = findDeepestBlock(oldTree, 0, editStart, editEndOld, 0);

        if (r == 0) return null;



        int oldStartTok = binarySearchToken(oldTokens, oldTree.nodeStart[r]);

        if (oldStartTok < 0 || oldTokens.types[oldStartTok] != TokenStream.TK_PUNCT || oldSource.charAt(oldTokens.tokenStart[oldStartTok]) != '{') return null;



        int oldEndTok = oldStartTok;

        int depth = 0;

        for (int i = oldStartTok; i < oldTokens.length; i++) {

            if (oldTokens.types[i] == TokenStream.TK_PUNCT) {

                char c = oldSource.charAt(oldTokens.tokenStart[i]);

                if (c == '{') depth++;

                else if (c == '}') {

                    depth--;

                    if (depth == 0) { oldEndTok = i; break; }

                }

            }

        }

        if (depth != 0) return null;



        int newStartTok = binarySearchToken(newTokens, oldTree.nodeStart[r]);

        if (newStartTok < 0 || newTokens.types[newStartTok] != TokenStream.TK_PUNCT || newSource.charAt(newTokens.tokenStart[newStartTok]) != '{') return null;



        int newEndTok = newStartTok;

        depth = 0;

        for (int i = newStartTok; i < newTokens.length; i++) {

            if (newTokens.types[i] == TokenStream.TK_PUNCT) {

                char c = newSource.charAt(newTokens.tokenStart[i]);

                if (c == '{') depth++;

                else if (c == '}') {

                    depth--;

                    if (depth == 0) { newEndTok = i; break; }

                }

            }

        }

        if (depth != 0) return null;



        int oldAfter = oldTokens.length - oldEndTok;

        int newAfter = newTokens.length - newEndTok;

        if (oldAfter != newAfter) return null;



        JsSyntaxTree newTree = new JsSyntaxTree(oldTree.nodeType.length + 1024);

        System.arraycopy(oldTree.nodeType, 0, newTree.nodeType, 0, oldTree.nodeCount);

        System.arraycopy(oldTree.nodeStart, 0, newTree.nodeStart, 0, oldTree.nodeCount);

        System.arraycopy(oldTree.nodeEnd, 0, newTree.nodeEnd, 0, oldTree.nodeCount);

        System.arraycopy(oldTree.nodeParent, 0, newTree.nodeParent, 0, oldTree.nodeCount);

        System.arraycopy(oldTree.nodeChild, 0, newTree.nodeChild, 0, oldTree.nodeCount);

        System.arraycopy(oldTree.nodeSibling, 0, newTree.nodeSibling, 0, oldTree.nodeCount);

        System.arraycopy(oldTree.nodeLastChild, 0, newTree.nodeLastChild, 0, oldTree.nodeCount);

        System.arraycopy(oldTree.nodeName, 0, newTree.nodeName, 0, oldTree.nodeCount);

        System.arraycopy(oldTree.nodeExtra, 0, newTree.nodeExtra, 0, oldTree.nodeCount);

        newTree.nodeCount = oldTree.nodeCount;



        int delta = (editEndNew - editStart) - (editEndOld - editStart);

        for (int i = 1; i < newTree.nodeCount; i++) {

            if (newTree.nodeStart[i] >= editEndOld) newTree.nodeStart[i] += delta;

            if (newTree.nodeEnd[i] >= editEndOld) newTree.nodeEnd[i] += delta;

        }



        newTree.nodeChild[r] = 0;

        newTree.nodeLastChild[r] = 0;

        newTree.nodeEnd[r] = getOffset(newTokens, newSource, newEndTok + 1);



        int nextTok = skipToken(newTokens, newStartTok);

        while (nextTok < newEndTok) {

            nextTok = skipWhitespaceAndComments(newTokens, nextTok);

            if (nextTok >= newEndTok) break;

            nextTok = parseNext(newSource, newTokens, newTree, nextTok, r);

        }



        return newTree;

    }



    private static int findDeepestBlock(JsSyntaxTree tree, int nodeId, int editStart, int editEnd, int best) {
        if (tree == null || tree.nodeCount <= 1) return 0;
        if (nodeId < 0 || nodeId >= tree.nodeCount) return best;

        if (nodeId == 0 && best != 0) nodeId = tree.nodeChild[0]; 

        if (nodeId == 0 && best == 0) {
            int child = tree.nodeChild[0];
            int loop = 0;
            while (child > 0 && child < tree.nodeCount && ++loop <= tree.nodeCount) {
                best = findDeepestBlock(tree, child, editStart, editEnd, best);
                child = tree.nodeSibling[child];
            }
            return best;
        }

        if (tree.nodeStart[nodeId] <= editStart && tree.nodeEnd[nodeId] >= editEnd) {
            if (tree.nodeType[nodeId] == JsSyntaxTree.N_BLOCK) {
                best = nodeId;
            }
            int child = tree.nodeChild[nodeId];
            int loop = 0;
            while (child > 0 && child < tree.nodeCount && ++loop <= tree.nodeCount) {
                best = findDeepestBlock(tree, child, editStart, editEnd, best);
                child = tree.nodeSibling[child];
            }
        }

        return best;
    }



    private static int binarySearchToken(TokenStream stream, int offset) {

        int low = 0;

        int high = stream.length - 1;

        while (low <= high) {

            int mid = (low + high) >>> 1;

            int midVal = stream.tokenStart[mid];

            if (midVal < offset) low = mid + 1;

            else if (midVal > offset) high = mid - 1;

            else return mid;

        }

        return -1;

    }



    public static JsSyntaxTree parseTopLevel(String source, TokenStream stream) {
        return parseTopLevel(source, stream, null);
    }

    public static JsSyntaxTree parseStatementList(String source, TokenStream stream) {
        JsSyntaxTree tree = new JsSyntaxTree(stream.length / 2 + 10);
        int len = stream.length;
        int i = 0;
        
        while (i < len && (stream.types[i] == TokenStream.TK_NONE || stream.types[i] == TokenStream.TK_WHITESPACE || stream.types[i] == TokenStream.TK_COMMENT)) {
            i = skipToken(stream, i);
        }
        
        while (i < len) {
            if (stream.types[i] == TokenStream.TK_NONE) break; // End of tokenized region
            i = skipWhitespaceAndComments(stream, i);
            if (i >= len || stream.types[i] == TokenStream.TK_NONE) break;
            int nextI = parseNext(source, stream, tree, i, 0);
            i = (nextI <= i) ? skipToken(stream, i) : nextI;
        }
        
        // We do not resolve class inheritance for inline statements typically,
        // but it's safe to call if a class is somehow defined inline.
        resolveClassInheritance(tree);
        tree.buildNodesByOffset();
        return tree;
    }



    public static JsSyntaxTree parseTopLevel(String source, TokenStream stream, JsSyntaxTree buffer) {

        JsSyntaxTree tree;

        if (buffer == null) {

            tree = new JsSyntaxTree(4096);

        } else {

            tree = buffer;

            tree.reset(4096);

        }

        int len = stream.length;

        int i = 0;



        while (i < len && (stream.types[i] == TokenStream.TK_NONE || stream.types[i] == TokenStream.TK_WHITESPACE || stream.types[i] == TokenStream.TK_COMMENT)) {
            i = skipToken(stream, i);
        }

        while (i < len) {
            if (stream.types[i] == TokenStream.TK_NONE) break; // End of tokenized region
            i = skipWhitespaceAndComments(stream, i);
            if (i >= len || stream.types[i] == TokenStream.TK_NONE) break;
            int nextI = parseNext(source, stream, tree, i, 0);
            i = (nextI <= i) ? skipToken(stream, i) : nextI;
        }

        resolveClassInheritance(tree);

        tree.buildNodesByOffset();

        return tree;

    }



    private static int parseNext(String source, TokenStream stream, JsSyntaxTree tree, int i, int parent) {
        if (parent > 0) {
            int depth = 0;
            int p = parent;
            while (p > 0 && p < tree.nodeCount) {
                depth++;
                if (depth > 400) {
                    return skipToken(stream, i);
                }
                p = tree.nodeParent[p];
            }
        }

        byte type = stream.types[i];
        int statementStart = i;

        

        if (type == TokenStream.TK_KEYWORD) {

            String kw = getWord(source, stream, i);

            if ("import".equals(kw)) return parseImport(source, stream, tree, i, parent);

            if ("export".equals(kw)) return parseExport(source, stream, tree, i, parent);

            if ("function".equals(kw) || "async".equals(kw)) return parseFunction(source, stream, tree, i, parent);

            if ("class".equals(kw)) return parseClass(source, stream, tree, i, parent);

            if ("const".equals(kw) || "let".equals(kw) || "var".equals(kw)) return parseVarDecl(source, stream, tree, i, parent);

            if ("for".equals(kw)) return parseFor(source, stream, tree, i, parent);

            if ("catch".equals(kw)) return parseCatch(source, stream, tree, i, parent);

            if ("interface".equals(kw) || "enum".equals(kw)) return parseClass(source, stream, tree, i, parent);

            if ("namespace".equals(kw) || "module".equals(kw)) return parseNamespace(source, stream, tree, i, parent);

            if ("type".equals(kw)) return parseTypeAlias(source, stream, tree, i, parent);

            if ("if".equals(kw)) return parseIf(source, stream, tree, i, parent);

            if ("while".equals(kw)) return parseWhile(source, stream, tree, i, parent);

            if ("do".equals(kw)) return parseDo(source, stream, tree, i, parent);

            if ("with".equals(kw)) return parseWith(source, stream, tree, i, parent);

            if ("switch".equals(kw)) return parseSwitch(source, stream, tree, i, parent);

            if ("case".equals(kw) || "default".equals(kw)) return parseCase(source, stream, tree, i, parent);

            if ("try".equals(kw)) return parseTry(source, stream, tree, i, parent);

            if ("finally".equals(kw)) return parseFinally(source, stream, tree, i, parent);

        }



        if (type == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '{') {

            return parseBlock(source, stream, tree, i, parent);

        }



        if (type == TokenStream.TK_IDENTIFIER) {

            int nextTok = skipWhitespaceAndComments(stream, skipToken(stream, i));

            if (nextTok < stream.length && stream.types[nextTok] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[nextTok]) == ':') {

                int labelNode = tree.addNode(JsSyntaxTree.N_STATEMENT, stream.tokenStart[i], 0, parent, getWord(source, stream, i));

                tree.nodeEnd[labelNode] = getOffset(stream, source, skipToken(stream, nextTok));

                int afterColon = skipWhitespaceAndComments(stream, skipToken(stream, nextTok));

                if (afterColon < stream.length) {

                    return parseNext(source, stream, tree, afterColon, parent);

                }

                return afterColon;

            }

        }



        // Generic statement
        int stmtNode = tree.addNode(JsSyntaxTree.N_STATEMENT, stream.tokenStart[i], 0, parent, null);
        int endIdx = skipToNextStatement(source, stream, tree, i, stmtNode);
        if (endIdx <= i) {
            endIdx = skipToken(stream, i);
        }
        tree.nodeEnd[stmtNode] = getOffset(stream, source, endIdx);
        return endIdx;

    }



    static void resolveClassInheritance(JsSyntaxTree tree) {
        if (tree == null || tree.nodeCount <= 1) return;
        java.util.Map<String, Integer> classByName = new java.util.HashMap<>();
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_CLASS_DECL && tree.nodeName[i] != null) {
                classByName.put(tree.nodeName[i], i);
            }
        }
        
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_CLASS_DECL && tree.nodeTypeAnn[i] != null) {
                Integer baseNodeId = classByName.get(tree.nodeTypeAnn[i]);
                if (baseNodeId != null && baseNodeId > 0 && baseNodeId < tree.nodeCount) {
                    String[] baseShape = tree.shapeTable.get(baseNodeId);
                    String[] myShape = tree.shapeTable.get(i);
                    
                    if (baseShape != null) {
                        java.util.Set<String> merged = new java.util.LinkedHashSet<>();
                        if (myShape != null) {
                            for (String s : myShape) merged.add(s);
                        }
                        for (String s : baseShape) merged.add(s);
                        
                        tree.shapeTable.put(i, merged.toArray(new String[0]));
                    }
                }
            }
        }
    }

    private static int getOffset(TokenStream stream, String source, int tokIdx) {
        if (tokIdx >= stream.length) return source.length();
        return stream.tokenStart[tokIdx];
    }



    private static int parseFor(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        int nodeStart = stream.tokenStart[startIdx];

        int forNode = tree.addNode(JsSyntaxTree.N_FOR_STMT, nodeStart, 0, parent, null);

        

        int i = skipToken(stream, startIdx); // skip 'for'

        i = skipWhitespaceAndComments(stream, i);

        

        if (i < stream.length && stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '(') {

            int headerStart = i;

            int headerEnd = skipBlockFast(source, stream, i, '(', ')');

            

            int inner = skipToken(stream, headerStart);

            while (inner < headerEnd) {

                inner = skipWhitespaceAndComments(stream, inner);

                if (inner >= headerEnd) break;

                byte t = stream.types[inner];

                if (t == TokenStream.TK_KEYWORD) {

                    String kw = getWord(source, stream, inner);

                    if ("let".equals(kw) || "const".equals(kw) || "var".equals(kw)) {

                        inner = parseVarDecl(source, stream, tree, inner, forNode);

                        continue;

                    }

                }

                inner = skipToken(stream, inner);

            }

            i = headerEnd;

        }

        

        i = skipWhitespaceAndComments(stream, i);

        if (i < stream.length) {

            if (stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '{') {

                i = parseBlock(source, stream, tree, i, forNode);

            } else {

                i = parseNext(source, stream, tree, i, forNode);

            }

        }

        

        tree.nodeEnd[forNode] = getOffset(stream, source, i);

        return i;

    }



    private static int parseCatch(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        int nodeStart = stream.tokenStart[startIdx];

        int catchNode = tree.addNode(JsSyntaxTree.N_CATCH_CLAUSE, nodeStart, 0, parent, null);

        

        int i = skipToken(stream, startIdx); // skip 'catch'

        i = skipWhitespaceAndComments(stream, i);

        

        if (i < stream.length && stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '(') {

            i = skipToken(stream, i);

            i = skipWhitespaceAndComments(stream, i);

            

            if (i < stream.length && stream.types[i] == TokenStream.TK_IDENTIFIER) {

                String paramName = getWord(source, stream, i);

                int paramStart = stream.tokenStart[i];

                i = skipToken(stream, i);

                int endOffset = getOffset(stream, source, i);

                tree.addNode(JsSyntaxTree.N_PARAM, paramStart, endOffset, catchNode, paramName);

            }

            

            while (i < stream.length) {

                if (stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == ')') {

                    i = skipToken(stream, i);

                    break;

                }

                i = skipToken(stream, i);

            }

        }

        

        i = skipWhitespaceAndComments(stream, i);

        if (i < stream.length && stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '{') {

            i = parseBlock(source, stream, tree, i, catchNode);

        } else {

            // If no block, just skip to next statement

            i = skipToNextStatement(source, stream, tree, i, catchNode);

        }

        

        tree.nodeEnd[catchNode] = getOffset(stream, source, i);

        return i;

    }



    private static int parseTry(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        int nodeStart = stream.tokenStart[startIdx];

        int node = tree.addNode(JsSyntaxTree.N_TRY_STMT, nodeStart, 0, parent, null);

        

        int i = skipToken(stream, startIdx); // skip 'try'

        i = skipWhitespaceAndComments(stream, i);

        

        if (i < stream.length && stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '{') {

            i = parseBlock(source, stream, tree, i, node);

        }

        

        tree.nodeEnd[node] = getOffset(stream, source, i);

        return i;

    }



    private static int parseFinally(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        int nodeStart = stream.tokenStart[startIdx];

        int node = tree.addNode(JsSyntaxTree.N_FINALLY_CLAUSE, nodeStart, 0, parent, null);

        

        int i = skipToken(stream, startIdx); // skip 'finally'

        i = skipWhitespaceAndComments(stream, i);

        

        if (i < stream.length && stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '{') {

            i = parseBlock(source, stream, tree, i, node);

        }

        

        tree.nodeEnd[node] = getOffset(stream, source, i);

        return i;

    }



    private static int parseConditionAndBody(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent, int nodeType) {

        int nodeStart = stream.tokenStart[startIdx];

        int node = tree.addNode(nodeType, nodeStart, 0, parent, null);

        

        int i = skipToken(stream, startIdx); 

        i = skipWhitespaceAndComments(stream, i);

        

        if (i < stream.length && stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '(') {

            i = skipBlockFast(source, stream, i, '(', ')');

        }

        

        i = skipWhitespaceAndComments(stream, i);

        if (i < stream.length) {

            if (stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '{') {

                i = parseBlock(source, stream, tree, i, node);

            } else {

                i = parseNext(source, stream, tree, i, node);

            }

        }

        

        if (nodeType == JsSyntaxTree.N_IF_STMT) {

            int nextTok = skipWhitespaceAndComments(stream, i);

            if (nextTok < stream.length && stream.types[nextTok] == TokenStream.TK_KEYWORD && "else".equals(getWord(source, stream, nextTok))) {

                i = skipToken(stream, nextTok); 

                i = skipWhitespaceAndComments(stream, i);

                if (i < stream.length) {

                    if (stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '{') {

                        i = parseBlock(source, stream, tree, i, node);

                    } else {

                        i = parseNext(source, stream, tree, i, node);

                    }

                }

            }

        }

        

        tree.nodeEnd[node] = getOffset(stream, source, i);

        return i;

    }



    private static int parseIf(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        return parseConditionAndBody(source, stream, tree, startIdx, parent, JsSyntaxTree.N_IF_STMT);

    }



    private static int parseWhile(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        return parseConditionAndBody(source, stream, tree, startIdx, parent, JsSyntaxTree.N_WHILE_STMT);

    }



    private static int parseWith(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        return parseConditionAndBody(source, stream, tree, startIdx, parent, JsSyntaxTree.N_WITH_STMT);

    }



    private static int parseDo(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        int nodeStart = stream.tokenStart[startIdx];

        int node = tree.addNode(JsSyntaxTree.N_DO_STMT, nodeStart, 0, parent, null);

        

        int i = skipToken(stream, startIdx); 

        i = skipWhitespaceAndComments(stream, i);

        

        if (i < stream.length) {

            if (stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '{') {

                i = parseBlock(source, stream, tree, i, node);

            } else {

                i = parseNext(source, stream, tree, i, node);

            }

        }

        

        int nextTok = skipWhitespaceAndComments(stream, i);

        if (nextTok < stream.length && stream.types[nextTok] == TokenStream.TK_KEYWORD && "while".equals(getWord(source, stream, nextTok))) {

            i = skipToken(stream, nextTok); 

            i = skipWhitespaceAndComments(stream, i);

            if (i < stream.length && stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '(') {

                i = skipBlockFast(source, stream, i, '(', ')');

            }

        }

        

        int semiCheck = skipWhitespaceAndComments(stream, i);

        if (semiCheck < stream.length && stream.types[semiCheck] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[semiCheck]) == ';') {

            i = skipToken(stream, semiCheck);

        }

        

        tree.nodeEnd[node] = getOffset(stream, source, i);

        return i;

    }



    private static int parseSwitch(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        int nodeStart = stream.tokenStart[startIdx];

        int node = tree.addNode(JsSyntaxTree.N_SWITCH_STMT, nodeStart, 0, parent, null);

        

        int i = skipToken(stream, startIdx); 

        i = skipWhitespaceAndComments(stream, i);

        

        if (i < stream.length && stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '(') {

            i = skipBlockFast(source, stream, i, '(', ')');

        }

        

        i = skipWhitespaceAndComments(stream, i);

        if (i < stream.length && stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '{') {

            i = parseBlock(source, stream, tree, i, node);

        }

        

        tree.nodeEnd[node] = getOffset(stream, source, i);

        return i;

    }



    private static int parseCase(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        int nodeStart = stream.tokenStart[startIdx];

        String kw = getWord(source, stream, startIdx);

        int node = tree.addNode(JsSyntaxTree.N_CASE_CLAUSE, nodeStart, 0, parent, kw);

        

        int i = skipToken(stream, startIdx); 

        

        while (i < stream.length) {

            byte t = stream.types[i];

            if (t == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == ':') {

                i = skipToken(stream, i);

                break;

            }

            i = skipToken(stream, i);

        }

        

        tree.nodeEnd[node] = getOffset(stream, source, i);

        return i;

    }



    private static int parseBlock(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        int nodeStart = stream.tokenStart[startIdx];

        int blockNode = tree.addNode(JsSyntaxTree.N_BLOCK, nodeStart, 0, parent, null);

        

        int i = skipToken(stream, startIdx);

        while (i < stream.length) {

            i = skipWhitespaceAndComments(stream, i);

            if (i >= stream.length) break;

            

            byte t = stream.types[i];

            if (t == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '}') {

                i = skipToken(stream, i);

                break;

            }

            

            int nextI = parseNext(source, stream, tree, i, blockNode);
            i = (nextI <= i) ? skipToken(stream, i) : nextI;
        }

        tree.nodeEnd[blockNode] = getOffset(stream, source, i);

        return i;

    }



    private static int parseObjectLiteral(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        int nodeStart = stream.tokenStart[startIdx];

        int objNode = tree.addNode(JsSyntaxTree.N_OBJECT_LITERAL, nodeStart, 0, parent, null); // Object literals don't create scopes

        

        int i = skipToken(stream, startIdx);

        while (i < stream.length) {

            i = skipWhitespaceAndComments(stream, i);

            if (i >= stream.length) break;

            

            byte t = stream.types[i];

            if (t == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '}') {

                i = skipToken(stream, i);

                break;

            }

            

            i = parseObjectPropertyOrMethod(source, stream, tree, i, objNode);

            

            i = skipWhitespaceAndComments(stream, i);

            if (i < stream.length && stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == ',') {

                i = skipToken(stream, i);

            }

        }

        tree.nodeEnd[objNode] = getOffset(stream, source, i);

        java.util.List<String> keys = new java.util.ArrayList<>();
        int child = tree.nodeChild[objNode];
        while (child > 0) {
            String name = tree.nodeName[child];
            if (name != null && !name.isEmpty()) {
                keys.add(name);
            }
            child = tree.nodeSibling[child];
        }
        if (!keys.isEmpty()) {
            String[] keyArray = keys.toArray(new String[0]);
            tree.shapeTable.put(parent, keyArray);
            tree.shapeTable.put(objNode, keyArray);
        }

        

        return i;

    }

    private static void scanConstructorForThisAssignments(JsSyntaxTree tree, int node, java.util.Set<String> keys) {
        int child = tree.nodeChild[node];
        while (child > 0) {
            if (tree.nodeType[child] == JsSyntaxTree.N_MEMBER_EXPR) {
                String name = tree.nodeName[child];
                if (name != null && name.startsWith("this.")) {
                    keys.add(name.substring(5));
                }
            } else {
                scanConstructorForThisAssignments(tree, child, keys);
            }
            child = tree.nodeSibling[child];
        }
    }



    private static int parseObjectPropertyOrMethod(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        int i = startIdx;

        int nodeType = JsSyntaxTree.N_METHOD;

        String name = null;

        int nodeStart = stream.tokenStart[i];



        while (i < stream.length) {

            i = skipWhitespaceAndComments(stream, i);

            if (i >= stream.length) break;

            

            byte t = stream.types[i];

            if (t == TokenStream.TK_IDENTIFIER || t == TokenStream.TK_KEYWORD || t == TokenStream.TK_STRING) {

                String word = (t == TokenStream.TK_STRING) ? "{string}" : getWord(source, stream, i);

                if ("get".equals(word) && name == null) {

                    int next = skipWhitespaceAndComments(stream, skipToken(stream, i));

                    if (next < stream.length && stream.types[next] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[next]) == '(') {

                        name = "get"; 

                    } else {

                        nodeType = JsSyntaxTree.N_GETTER;

                    }

                } else if ("set".equals(word) && name == null) {

                    int next = skipWhitespaceAndComments(stream, skipToken(stream, i));

                    if (next < stream.length && stream.types[next] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[next]) == '(') {

                        name = "set";

                    } else {

                        nodeType = JsSyntaxTree.N_SETTER;

                    }

                } else if (name == null && ("async".equals(word) || "function".equals(word))) {
                    int next = skipWhitespaceAndComments(stream, skipToken(stream, i));
                    if (next < stream.length && (stream.types[next] == TokenStream.TK_IDENTIFIER 
                            || stream.types[next] == TokenStream.TK_KEYWORD
                            || (stream.types[next] == TokenStream.TK_OPERATOR && (source.charAt(stream.tokenStart[next]) == '*' || source.charAt(stream.tokenStart[next]) == '#')))) {
                        // skip modifier
                    } else {
                        name = word;
                    }
                } else if (name == null) {
                    name = word;
                }

                i = skipToken(stream, i);

            } else if (t == TokenStream.TK_OPERATOR && source.charAt(stream.tokenStart[i]) == '*') {

                i = skipToken(stream, i);

            } else if (t == TokenStream.TK_PUNCT) {

                char c = source.charAt(stream.tokenStart[i]);

                if (c == '(') {

                    int methodNode = tree.addNode(nodeType, nodeStart, 0, parent, name);

                    i = parseParams(source, stream, tree, i, methodNode);

                    

                    while (i < stream.length) {

                        i = skipWhitespaceAndComments(stream, i);

                        if (i >= stream.length) break;

                        if (stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '{') {

                            i = parseBlock(source, stream, tree, i, methodNode);

                            break;

                        }

                        i = skipToken(stream, i);

                    }

                    tree.nodeEnd[methodNode] = getOffset(stream, source, i);

                    return i;

                } else if (c == ':') {
                    int propNode = tree.addNode(JsSyntaxTree.N_PROPERTY, nodeStart, 0, parent, name);
                    String inferredPropType = inferBaseType(source, stream, i);
                    if (inferredPropType != null) {
                        tree.nodeTypeAnn[propNode] = inferredPropType;
                    }

                    i = skipToken(stream, i);
                    i = parseExpressionTokens(source, stream, tree, i, propNode, STOP_OBJ_PROP);
                    tree.nodeEnd[propNode] = getOffset(stream, source, i);
                    return i;

                } else if (c == '[') {

                    i = skipBlockFast(source, stream, i, '[', ']');

                    name = "{computed}";

                    continue;

                } else {

                    if (name != null) {

                        tree.addNode(JsSyntaxTree.N_STATEMENT, nodeStart, getOffset(stream, source, i), parent, name);

                        return i;

                    }

                    return skipToken(stream, i);

                }

            } else {

                return skipToken(stream, i);

            }

        }

        return i;

    }



    private static int parseImport(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        int nodeStart = stream.tokenStart[startIdx];

        int importNode = tree.addNode(JsSyntaxTree.N_IMPORT, nodeStart, 0, parent, null);

        int i = skipToken(stream, startIdx); // skip 'import'

        java.util.List<Integer> declNodes = new java.util.ArrayList<>();
        java.util.List<String> importedSymbols = new java.util.ArrayList<>();
        boolean insideBraces = false;
        String pendingImportedName = null;

        while (i < stream.length) {
            i = skipWhitespaceAndComments(stream, i);
            if (i >= stream.length) break;

            byte t = stream.types[i];
            String word = (t == TokenStream.TK_KEYWORD || t == TokenStream.TK_IDENTIFIER) ? getWord(source, stream, i) : null;
            if ("from".equals(word)) {
                break;
            }
            if (t == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == ';') {
                break;
            }
            if (t == TokenStream.TK_STRING) {
                break; // e.g. import "module";
            }

            if (t == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '{') {
                insideBraces = true;
                i = skipToken(stream, i);
                continue;
            } else if (t == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '}') {
                insideBraces = false;
                i = skipToken(stream, i);
                continue;
            } else if (t == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == ',') {
                i = skipToken(stream, i);
                continue;
            }

            if (t == TokenStream.TK_OPERATOR && source.charAt(stream.tokenStart[i]) == '*') {
                pendingImportedName = "*";
                i = skipToken(stream, i);
                continue;
            }

            if ("as".equals(word)) {
                i = skipToken(stream, i); // skip 'as'
                i = skipWhitespaceAndComments(stream, i);
                if (i < stream.length && (stream.types[i] == TokenStream.TK_IDENTIFIER || stream.types[i] == TokenStream.TK_KEYWORD)) {
                    String localName = getWord(source, stream, i);
                    if (!declNodes.isEmpty() && pendingImportedName == null) {
                        int lastIdx = declNodes.size() - 1;
                        int lastDeclNode = declNodes.get(lastIdx);
                        String origExport = tree.nodeName[lastDeclNode];
                        tree.nodeName[lastDeclNode] = localName;
                        tree.nodeEnd[lastDeclNode] = getOffset(stream, source, skipToken(stream, i));
                        importedSymbols.set(lastIdx, origExport);
                    } else {
                        int declNode = tree.addNode(JsSyntaxTree.N_VAR_DECL, stream.tokenStart[i], getOffset(stream, source, skipToken(stream, i)), importNode, localName);
                        tree.nodeExtra[declNode] = JsSyntaxTree.FLAG_CONST;
                        declNodes.add(declNode);
                        importedSymbols.add(pendingImportedName != null ? pendingImportedName : "*");
                        pendingImportedName = null;
                    }
                    i = skipToken(stream, i);
                }
                continue;
            }

            if (t == TokenStream.TK_IDENTIFIER || (t == TokenStream.TK_KEYWORD && "default".equals(word))) {
                String name = word;
                int declNode = tree.addNode(JsSyntaxTree.N_VAR_DECL, stream.tokenStart[i], getOffset(stream, source, skipToken(stream, i)), importNode, name);
                tree.nodeExtra[declNode] = JsSyntaxTree.FLAG_CONST;
                declNodes.add(declNode);
                if (!insideBraces) {
                    importedSymbols.add("default");
                } else {
                    importedSymbols.add(name);
                }
                i = skipToken(stream, i);
                continue;
            }

            i = skipToken(stream, i);
        }

        i = skipWhitespaceAndComments(stream, i);
        String modulePath = null;
        if (i < stream.length) {
            byte ft = stream.types[i];
            String fword = (ft == TokenStream.TK_KEYWORD || ft == TokenStream.TK_IDENTIFIER) ? getWord(source, stream, i) : null;
            if ("from".equals(fword)) {
                i = skipToken(stream, i); // skip 'from'
                i = skipWhitespaceAndComments(stream, i);
                if (i < stream.length && stream.types[i] == TokenStream.TK_STRING) {
                    String raw = getWord(source, stream, i);
                    if (raw != null && raw.length() >= 2) {
                        modulePath = raw.substring(1, raw.length() - 1);
                    }
                    i = skipToken(stream, i);
                }
            } else if (ft == TokenStream.TK_STRING) {
                String raw = getWord(source, stream, i);
                if (raw != null && raw.length() >= 2) {
                    modulePath = raw.substring(1, raw.length() - 1);
                }
                i = skipToken(stream, i);
            }
        }

        if (modulePath != null) {
            tree.nodeName[importNode] = modulePath;
            for (int k = 0; k < declNodes.size(); k++) {
                int dNode = declNodes.get(k);
                String sym = importedSymbols.get(k);
                tree.nodeTypeAnn[dNode] = "@IMPORT:" + modulePath + ":" + sym;
            }
        }

        int endIdx = skipToNextStatement(source, stream, tree, i, importNode);
        tree.nodeEnd[importNode] = getOffset(stream, source, endIdx);
        return endIdx;

    }



    private static int parseExport(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        int nodeStart = stream.tokenStart[startIdx];

        int exportNode = tree.addNode(JsSyntaxTree.N_EXPORT, nodeStart, 0, parent, null);

        

        int i = skipToken(stream, startIdx);

        i = skipWhitespaceAndComments(stream, i);

        

        if (i < stream.length && stream.types[i] == TokenStream.TK_KEYWORD) {

            String kw = getWord(source, stream, i);

            if ("function".equals(kw) || "async".equals(kw)) {

                i = parseFunction(source, stream, tree, i, exportNode);

                tree.nodeEnd[exportNode] = getOffset(stream, source, i);

                return i;

            } else if ("class".equals(kw)) {

                i = parseClass(source, stream, tree, i, exportNode);

                tree.nodeEnd[exportNode] = getOffset(stream, source, i);

                return i;

            } else if ("const".equals(kw) || "let".equals(kw) || "var".equals(kw)) {

                i = parseVarDecl(source, stream, tree, i, exportNode);

                tree.nodeEnd[exportNode] = getOffset(stream, source, i);

                return i;

            } else if ("default".equals(kw)) {
                tree.nodeName[exportNode] = "default";
                i = skipToken(stream, i); // skip 'default'
                i = skipWhitespaceAndComments(stream, i);
                if (i < stream.length) {
                    byte dt = stream.types[i];
                    if (dt == TokenStream.TK_KEYWORD) {
                        String dkw = getWord(source, stream, i);
                        if ("function".equals(dkw) || "async".equals(dkw)) {
                            i = parseFunction(source, stream, tree, i, exportNode);
                            tree.nodeEnd[exportNode] = getOffset(stream, source, i);
                            return i;
                        } else if ("class".equals(dkw)) {
                            i = parseClass(source, stream, tree, i, exportNode);
                            tree.nodeEnd[exportNode] = getOffset(stream, source, i);
                            return i;
                        }
                    } else if (dt == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '{') {
                        i = parseObjectLiteral(source, stream, tree, i, exportNode);
                        tree.nodeEnd[exportNode] = getOffset(stream, source, i);
                        return i;
                    } else if (dt == TokenStream.TK_IDENTIFIER) {
                        String idName = getWord(source, stream, i);
                        tree.addNode(JsSyntaxTree.N_IDENTIFIER, stream.tokenStart[i], getOffset(stream, source, skipToken(stream, i)), exportNode, idName);
                        i = skipToken(stream, i);
                    }
                }
                i = skipToNextStatement(source, stream, tree, i, exportNode);
                tree.nodeEnd[exportNode] = getOffset(stream, source, i);
                return i;
            }

        } else if (i < stream.length && stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '{') {

            i = skipToken(stream, i);

            String lastName = null;

            int lastNameTok = -1;

            String aliasName = null;

            int aliasTokIdx = -1;

            while (i < stream.length) {

                int tok = skipWhitespaceAndComments(stream, i);

                if (tok >= stream.length) {

                    i = tok;

                    break;

                }

                byte t = stream.types[tok];

                String word = (t == TokenStream.TK_IDENTIFIER || t == TokenStream.TK_KEYWORD) ? getWord(source, stream, tok) : null;

                

                if (t == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[tok]) == '}') {
                    if (lastName != null) {
                        int nameNodeStart = stream.tokenStart[lastNameTok];
                        String nameToExport = aliasName != null ? aliasName : lastName;
                        int id = tree.addNode(JsSyntaxTree.N_IDENTIFIER, nameNodeStart, nameNodeStart + nameToExport.length(), exportNode, nameToExport);
                        if (aliasName != null) {
                            tree.nodeTypeAnn[id] = lastName;
                        }
                    }
                    i = skipToken(stream, tok);
                    break;
                } else if (t == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[tok]) == ',') {
                    if (lastName != null) {
                        int nameNodeStart = stream.tokenStart[lastNameTok];
                        String nameToExport = aliasName != null ? aliasName : lastName;
                        int id = tree.addNode(JsSyntaxTree.N_IDENTIFIER, nameNodeStart, nameNodeStart + nameToExport.length(), exportNode, nameToExport);
                        if (aliasName != null) {
                            tree.nodeTypeAnn[id] = lastName;
                        }
                        lastName = null;
                        lastNameTok = -1;
                        aliasName = null;
                        aliasTokIdx = -1;
                    }
                    i = skipToken(stream, tok);

                } else if ("as".equals(word)) {

                    i = skipToken(stream, tok);

                    int aliasTok = skipWhitespaceAndComments(stream, i);

                    if (aliasTok < stream.length) {

                        byte at = stream.types[aliasTok];

                        if (at == TokenStream.TK_IDENTIFIER || at == TokenStream.TK_KEYWORD) {

                            aliasName = getWord(source, stream, aliasTok);

                            aliasTokIdx = aliasTok;

                            i = skipToken(stream, aliasTok);

                        } else {

                            i = aliasTok;

                        }

                    } else {

                        i = aliasTok;

                    }

                } else if (word != null) {

                    lastName = word;

                    lastNameTok = tok;

                    i = skipToken(stream, tok);

                } else {

                    i = skipToken(stream, tok);

                }

            }

            int endIdx = skipToNextStatement(source, stream, tree, i, exportNode);

            tree.nodeEnd[exportNode] = getOffset(stream, source, endIdx);

            return endIdx;

        }

        

        int endIdx = skipToNextStatement(source, stream, tree, startIdx, exportNode);

        tree.nodeEnd[exportNode] = getOffset(stream, source, endIdx);

        return endIdx;

    }



    private static int parseFunction(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        int i = startIdx;

        int nodeStart = stream.tokenStart[i];

        String name = null;

        

        while (i < stream.length) {

            int nextTok = skipWhitespaceAndComments(stream, i);

            if (nextTok >= stream.length) {

                i = nextTok;

                break;

            }

            int t = stream.types[nextTok];
            if (t == TokenStream.TK_KEYWORD && ("function".equals(getWord(source, stream, nextTok)) || "async".equals(getWord(source, stream, nextTok)))) {
                i = skipToken(stream, nextTok);
            } else if (t == TokenStream.TK_IDENTIFIER || t == TokenStream.TK_KEYWORD) {
                if (name == null) name = getWord(source, stream, nextTok);
                i = skipToken(stream, nextTok);
            } else if (t == TokenStream.TK_OPERATOR && source.charAt(stream.tokenStart[nextTok]) == '*') {
                i = skipToken(stream, nextTok);

            } else if (t == TokenStream.TK_OPERATOR && source.charAt(stream.tokenStart[nextTok]) == '<') {

                i = skipGenericArguments(source, stream, nextTok);

            } else if (t == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[nextTok]) == '(') {

                break;

            } else {

                if (isTopLevelKeyword(source, stream, nextTok) && hasNewlineBetween(source, stream, i, nextTok)) {

                    int funcNode = tree.addNode(JsSyntaxTree.N_FUNC_DECL, nodeStart, stream.tokenStart[nextTok], parent, name);

                    return nextTok; 

                }

                i = skipToken(stream, nextTok);

            }

        }

        

        int funcNode = tree.addNode(JsSyntaxTree.N_FUNC_DECL, nodeStart, 0, parent, name);

        

        if (i < stream.length && stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '(') {

            i = parseParams(source, stream, tree, i, funcNode);

        }

        

        while (i < stream.length) {

            i = skipWhitespaceAndComments(stream, i);

            if (i >= stream.length) break;

            byte t = stream.types[i];

            if (t == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '{') {

                i = parseBlock(source, stream, tree, i, funcNode);

                break;

            } else if (t == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == ';') {

                i = skipToken(stream, i);

                break;

            }

            i = skipToken(stream, i);

        }

        

        tree.nodeEnd[funcNode] = getOffset(stream, source, i);

        return i;

    }



    private static int parseParams(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {
        int i = skipToken(stream, startIdx);
        boolean isRest = false;
        
        while (i < stream.length) {
            i = skipWhitespaceAndComments(stream, i);
            if (i >= stream.length) break;
            
            byte t = stream.types[i];
            if (t == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == ')') {
                return skipToken(stream, i);
            }
            
            if (t == TokenStream.TK_OPERATOR && source.charAt(stream.tokenStart[i]) == '.' && i + 2 < stream.length && source.charAt(stream.tokenStart[i+1]) == '.' && source.charAt(stream.tokenStart[i+2]) == '.') {
                i = skipToken(stream, skipToken(stream, skipToken(stream, i)));
                isRest = true;
                continue; // skip spread operator
            }
            if (t == TokenStream.TK_IDENTIFIER) {
                String paramName = getWord(source, stream, i);
                int pStart = stream.tokenStart[i];
                i = skipToken(stream, i);
                int paramNodeId = tree.addNode(JsSyntaxTree.N_PARAM, pStart, pStart + paramName.length(), parent, paramName);
                if (isRest) {
                    tree.nodeExtra[paramNodeId] = JsSyntaxTree.FLAG_REST;
                    isRest = false;
                }
                
                int next = skipWhitespaceAndComments(stream, i);
                if (next < stream.length && stream.types[next] == TokenStream.TK_PUNCT && (source.charAt(stream.tokenStart[next]) == ':' || source.charAt(stream.tokenStart[next]) == '?')) {
                    int typeStart = next + 1;
                    if (source.charAt(stream.tokenStart[next]) == '?') {
                        int skipNext = skipWhitespaceAndComments(stream, next + 1);
                        if (skipNext < stream.length && stream.types[skipNext] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[skipNext]) == ':') {
                            typeStart = skipToken(stream, skipNext);
                        }
                    }
                    i = skipTypeAnnotation(source, stream, typeStart);
                    int pNodeId = tree.nodeCount - 1; // It was just added
                    if (i > typeStart) {
                        tree.nodeTypeAnn[pNodeId] = source.substring(stream.tokenStart[typeStart], stream.tokenStart[i]).trim();
                    }
                }
            } else if (t == TokenStream.TK_PUNCT && (source.charAt(stream.tokenStart[i]) == '{' || source.charAt(stream.tokenStart[i]) == '[')) {
                char open = source.charAt(stream.tokenStart[i]);
                char close = open == '{' ? '}' : ']';
                int bNodeStart = stream.tokenStart[i];
                int declNode = tree.addNode(JsSyntaxTree.N_PARAM, bNodeStart, 0, parent, "{destructure}");
                if (isRest) {
                    tree.nodeExtra[declNode] = JsSyntaxTree.FLAG_REST;
                    isRest = false;
                }
                i = parseDestructuredVars(source, stream, tree, i, declNode, 0, open, close, JsSyntaxTree.N_PARAM);
                tree.nodeEnd[declNode] = getOffset(stream, source, i);
                
                int next = skipWhitespaceAndComments(stream, i);
                if (next < stream.length && stream.types[next] == TokenStream.TK_PUNCT && (source.charAt(stream.tokenStart[next]) == ':' || source.charAt(stream.tokenStart[next]) == '?')) {
                    int typeStart = next + 1;
                    if (source.charAt(stream.tokenStart[next]) == '?') {
                        int skipNext = skipWhitespaceAndComments(stream, next + 1);
                        if (skipNext < stream.length && stream.types[skipNext] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[skipNext]) == ':') {
                            typeStart = skipToken(stream, skipNext);
                        }
                    }
                    i = skipTypeAnnotation(source, stream, typeStart);
                    int pNodeId = tree.nodeCount - 1; // It was just added
                    if (i > typeStart) {
                        tree.nodeTypeAnn[pNodeId] = source.substring(stream.tokenStart[typeStart], stream.tokenStart[i]).trim();
                    }
                }
                continue;
            } else if (t == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == ',') {

                i = skipToken(stream, i);

            } else if (t == TokenStream.TK_OPERATOR && source.charAt(stream.tokenStart[i]) == '=') {

                i = skipToken(stream, i);

                int depth = 0;

                while (i < stream.length) {

                    byte tt = stream.types[i];

                    if (tt == TokenStream.TK_PUNCT) {

                        char c = source.charAt(stream.tokenStart[i]);

                        if (c == '(' || c == '[' || c == '{') depth++;

                        else if (c == ')' || c == ']' || c == '}') { if (depth == 0) break; depth--; }

                        else if (c == ',' && depth == 0) break;

                    }

                    i = skipToken(stream, i);

                }

            } else {

                i = emitErrorAndSync(source, stream, tree, i, parent); // defensive: always advance, never stall the loop

            }

        }

        return i;

    }



    private static int parseClass(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {
        int i = startIdx;
        int nodeStart = stream.tokenStart[i];
        String kw = getWord(source, stream, startIdx);
        int nodeType = "interface".equals(kw) ? JsSyntaxTree.N_INTERFACE : ("enum".equals(kw) ? JsSyntaxTree.N_ENUM : JsSyntaxTree.N_CLASS_DECL);

        String name = null;
        String extendsName = null;

        while (i < stream.length) {
            int nextTok = skipWhitespaceAndComments(stream, i);
            if (nextTok >= stream.length) {
                i = nextTok;
                break;
            }

            byte t = stream.types[nextTok];
            if (t == TokenStream.TK_IDENTIFIER) {
                if (name == null) name = getWord(source, stream, nextTok);
                i = skipToken(stream, nextTok);
            } else if (t == TokenStream.TK_KEYWORD && "extends".equals(getWord(source, stream, nextTok))) {
                int extTok = skipWhitespaceAndComments(stream, skipToken(stream, nextTok));
                if (extTok < stream.length && stream.types[extTok] == TokenStream.TK_IDENTIFIER) {
                    extendsName = getWord(source, stream, extTok);
                    i = skipToken(stream, extTok);
                } else {
                    i = skipToken(stream, nextTok);
                }
            } else if (t == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[nextTok]) == '{') {
                int classNode = tree.addNode(nodeType, nodeStart, 0, parent, name);
                tree.nodeTypeAnn[classNode] = extendsName;

                i = parseClassBody(source, stream, tree, nextTok, classNode);
                
                // C.1 pre-populate shape for class/interface
                java.util.Set<String> keys = new java.util.LinkedHashSet<>();
                int child = tree.nodeChild[classNode];
                while (child != 0) {
                    if (tree.nodeType[child] == JsSyntaxTree.N_METHOD || tree.nodeType[child] == JsSyntaxTree.N_PROPERTY) {
                        String childName = tree.nodeName[child];
                        if (childName != null && !childName.isEmpty()) {
                            if ("constructor".equals(childName)) {
                                scanConstructorForThisAssignments(tree, child, keys);
                            } else if (!"prototype".equals(childName) && !isClassMemberModifierOrDecl(childName)) {
                                keys.add(childName);
                            }
                        }
                    }
                    child = tree.nodeSibling[child];
                }
                if (!keys.isEmpty()) {
                    tree.shapeTable.put(classNode, keys.toArray(new String[0]));
                }

                tree.nodeEnd[classNode] = i;
                return i;
            } else {
                if (isTopLevelKeyword(source, stream, nextTok) && hasNewlineBetween(source, stream, i, nextTok)) {
                    int cn = tree.addNode(nodeType, nodeStart, stream.tokenStart[nextTok], parent, name);
                    tree.nodeTypeAnn[cn] = extendsName;

                    return nextTok;

                }

                i = skipToken(stream, nextTok);

            }

        }

        int cn2 = tree.addNode(JsSyntaxTree.N_CLASS_DECL, nodeStart, i, parent, name);
        tree.nodeTypeAnn[cn2] = extendsName;

        return i;

    }



    private static int parseNamespace(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {
        int i = startIdx;
        int nodeStart = stream.tokenStart[i];
        String name = null;
        i = skipToken(stream, i);
        while (i < stream.length) {
            int nextTok = skipWhitespaceAndComments(stream, i);
            if (nextTok >= stream.length) return nextTok;
            byte t = stream.types[nextTok];
            if (t == TokenStream.TK_IDENTIFIER) {
                if (name == null) name = getWord(source, stream, nextTok);
                i = skipToken(stream, nextTok);
            } else if (t == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[nextTok]) == '{') {
                int nsNode = tree.addNode(JsSyntaxTree.N_CLASS_DECL, nodeStart, 0, parent, name);
                int endIdx = parseBlock(source, stream, tree, nextTok, nsNode);
                tree.nodeEnd[nsNode] = getOffset(stream, source, endIdx);
                return endIdx;
            } else {
                if (isTopLevelKeyword(source, stream, nextTok) && hasNewlineBetween(source, stream, i, nextTok)) {
                    tree.addNode(JsSyntaxTree.N_CLASS_DECL, nodeStart, stream.tokenStart[nextTok], parent, name);
                    return nextTok;
                }
                i = skipToken(stream, nextTok);
            }
        }
        tree.addNode(JsSyntaxTree.N_CLASS_DECL, nodeStart, i, parent, name);
        return i;
    }

    private static int parseTypeAlias(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        int i = startIdx;

        int nodeStart = stream.tokenStart[i];

        String name = null;

        i = skipToken(stream, i); 

        

        while (i < stream.length) {

            i = skipWhitespaceAndComments(stream, i);

            if (i >= stream.length) break;

            

            byte t = stream.types[i];

            if (t == TokenStream.TK_IDENTIFIER && name == null) {

                name = getWord(source, stream, i);

                i = skipToken(stream, i);

            } else if (t == TokenStream.TK_OPERATOR && source.charAt(stream.tokenStart[i]) == '=') {

                i = skipToken(stream, i);

                int declNode = tree.addNode(JsSyntaxTree.N_VAR_DECL, nodeStart, 0, parent, name);

                tree.nodeExtra[declNode] = JsSyntaxTree.FLAG_CONST; 

                int depth = 0;

                while (i < stream.length) {

                    byte dt = stream.types[i];

                    if (dt == TokenStream.TK_PUNCT) {

                        char dc = source.charAt(stream.tokenStart[i]);

                        if (dc == '{' || dc == '[' || dc == '(' || dc == '<') depth++;

                        else if (dc == '}' || dc == ']' || dc == ')' || dc == '>') { if (depth > 0) depth--; }

                        else if (depth == 0 && dc == ';') {

                            i = skipToken(stream, i);

                            break;

                        }

                    } else if (depth == 0 && isTopLevelKeyword(source, stream, i)) {

                        break;

                    }

                    i = skipToken(stream, i);

                }

                tree.nodeEnd[declNode] = getOffset(stream, source, i);

                return i;

            } else {

                i = skipToken(stream, i);

            }

        }

        tree.addNode(JsSyntaxTree.N_VAR_DECL, nodeStart, i, parent, name);

        return i;

    }



    private static int parseClassBody(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        int i = skipToken(stream, startIdx); // skip '{'

        while (i < stream.length) {

            i = skipWhitespaceAndComments(stream, i);

            if (i >= stream.length) break;

            

            byte t = stream.types[i];

            if (t == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '}') {

                return skipToken(stream, i);

            }

            

            i = parseMethodOrProperty(source, stream, tree, i, parent);

        }

        return i;

    }



    private static int parseMethodOrProperty(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        int i = startIdx;

        int nodeType = JsSyntaxTree.N_METHOD;

        String name = null;

        int nodeStart = stream.tokenStart[i];



        while (i < stream.length) {

            i = skipWhitespaceAndComments(stream, i);

            if (i >= stream.length) break;

            

            byte t = stream.types[i];

            if (t == TokenStream.TK_IDENTIFIER || t == TokenStream.TK_KEYWORD) {
                if (name != null) {
                    if (!isClassMemberModifierOrDecl(name)) {
                        int propNode = tree.addNode(JsSyntaxTree.N_PROPERTY, nodeStart, 0, parent, name);
                        tree.nodeEnd[propNode] = stream.tokenStart[i];
                    }
                    return i;
                }

                String word = getWord(source, stream, i);

                if ("get".equals(word) && name == null) {
                    int next = skipWhitespaceAndComments(stream, skipToken(stream, i));
                    if (next < stream.length && stream.types[next] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[next]) == '(') {
                        name = "get"; 
                    } else {
                        nodeType = JsSyntaxTree.N_GETTER;
                    }
                } else if ("set".equals(word) && name == null) {
                    int next = skipWhitespaceAndComments(stream, skipToken(stream, i));
                    if (next < stream.length && stream.types[next] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[next]) == '(') {
                        name = "set";
                    } else {
                        nodeType = JsSyntaxTree.N_SETTER;
                    }
                } else if (name == null && isClassMemberModifierOrDecl(word)) {
                    int next = skipWhitespaceAndComments(stream, skipToken(stream, i));
                    if (next < stream.length && (stream.types[next] == TokenStream.TK_IDENTIFIER 
                            || stream.types[next] == TokenStream.TK_KEYWORD
                            || (stream.types[next] == TokenStream.TK_OPERATOR && (source.charAt(stream.tokenStart[next]) == '*' || source.charAt(stream.tokenStart[next]) == '#')))) {
                        // skip modifier / declaration keyword
                    } else {
                        name = word;
                    }
                } else if (name == null) {
                    name = word;
                }
                i = skipToken(stream, i);
            } else if (t == TokenStream.TK_OPERATOR && source.charAt(stream.tokenStart[i]) == '*') {
                i = skipToken(stream, i);
            } else if (t == TokenStream.TK_OPERATOR && source.charAt(stream.tokenStart[i]) == '<') {
                i = skipGenericArguments(source, stream, i);
            } else if (t == TokenStream.TK_OPERATOR && source.charAt(stream.tokenStart[i]) == '=') {
                int propNode = tree.addNode(JsSyntaxTree.N_PROPERTY, nodeStart, 0, parent, name);
                i = skipToNextStatement(source, stream, tree, i, propNode);
                tree.nodeEnd[propNode] = getOffset(stream, source, i);
                return i;
            } else if (t == TokenStream.TK_PUNCT) {
                char c = source.charAt(stream.tokenStart[i]);
                if (c == '}') {
                    if (name != null && !isClassMemberModifierOrDecl(name)) {
                        int propNode = tree.addNode(JsSyntaxTree.N_PROPERTY, nodeStart, 0, parent, name);
                        tree.nodeEnd[propNode] = getOffset(stream, source, i);
                    }
                    return i;
                }
                if (c == '(') {
                    int methodNode = tree.addNode(nodeType, nodeStart, 0, parent, name);
                    i = parseParams(source, stream, tree, i, methodNode);
                    while (i < stream.length) {
                        i = skipWhitespaceAndComments(stream, i);
                        if (i >= stream.length) break;
                        if (stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '{') {
                            i = parseBlock(source, stream, tree, i, methodNode);
                            break;
                        }
                        i = skipToken(stream, i);
                    }
                    tree.nodeEnd[methodNode] = getOffset(stream, source, i);
                    return i;
                } else if (c == '=' || c == ';' || c == ':') {
                    int propNode = tree.addNode(JsSyntaxTree.N_PROPERTY, nodeStart, 0, parent, name);
                    if (c == ':') {
                        int typeStart = i + 1;
                        i = skipTypeAnnotation(source, stream, typeStart);
                        if (i > typeStart) {
                            tree.nodeTypeAnn[propNode] = source.substring(stream.tokenStart[typeStart], stream.tokenStart[i]).trim();
                        }
                    }
                    i = skipToNextStatement(source, stream, tree, i, propNode);
                    tree.nodeEnd[propNode] = getOffset(stream, source, i);
                    return i;
                } else {
                    i = skipToken(stream, i);
                }
            } else {
                i = skipToken(stream, i);
            }
        }
        return i;
    }

    private static boolean isClassMemberModifierOrDecl(String word) {
        return "static".equals(word) || "async".equals(word)
                || "public".equals(word) || "private".equals(word) || "protected".equals(word)
                || "readonly".equals(word) || "override".equals(word) || "declare".equals(word)
                || "abstract".equals(word) || "accessor".equals(word)
                || "const".equals(word) || "let".equals(word) || "var".equals(word)
                || "function".equals(word);
    }



    private static int parseVarDecl(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {

        int nodeStart = stream.tokenStart[startIdx];

        String kw = getWord(source, stream, startIdx);

        int flag = "var".equals(kw) ? JsSyntaxTree.FLAG_VAR : ("let".equals(kw) ? JsSyntaxTree.FLAG_LET : JsSyntaxTree.FLAG_CONST);

        

        int i = skipToken(stream, startIdx);

        

        while (i < stream.length) {

            i = skipWhitespaceAndComments(stream, i);

            if (i >= stream.length) break;

            

            if (stream.types[i] == TokenStream.TK_PUNCT && (source.charAt(stream.tokenStart[i]) == '{' || source.charAt(stream.tokenStart[i]) == '[')) {

                char open = source.charAt(stream.tokenStart[i]);

                char close = open == '{' ? '}' : ']';

                int bNodeStart = stream.tokenStart[i];

                int declNode = tree.addNode(JsSyntaxTree.N_VAR_DECL, bNodeStart, 0, parent, "{destructure}");

                tree.nodeExtra[declNode] = flag;

                i = parseDestructuredVars(source, stream, tree, i, declNode, flag, open, close, JsSyntaxTree.N_VAR_DECL);

                int nextTok = skipWhitespaceAndComments(stream, i);

                if (nextTok < stream.length && stream.types[nextTok] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[nextTok]) == ':') {

                    i = skipTypeAnnotation(source, stream, skipToken(stream, nextTok));

                    nextTok = skipWhitespaceAndComments(stream, i);

                }

                if (nextTok < stream.length && stream.types[nextTok] == TokenStream.TK_OPERATOR && source.charAt(stream.tokenStart[nextTok]) == '=') {
                    
                    String inferredType = inferBaseType(source, stream, nextTok);
                    if (inferredType != null && tree.nodeTypeAnn[declNode] == null) {
                        tree.nodeTypeAnn[declNode] = inferredType;
                        if (inferredType.startsWith("@REQUIRE:")) {
                            String reqMod = inferredType.substring(9);
                            int cChild = tree.nodeChild[declNode];
                            while (cChild > 0) {
                                if (tree.nodeType[cChild] == JsSyntaxTree.N_VAR_DECL && tree.nodeName[cChild] != null) {
                                    tree.nodeTypeAnn[cChild] = "@REQUIRE_PROP:" + reqMod + ":" + tree.nodeName[cChild];
                                }
                                cChild = tree.nodeSibling[cChild];
                            }
                        }
                    }

                    i = parseExpressionTokens(source, stream, tree, nextTok, declNode, STOP_VAR_DECL);

                } else {

                    i = nextTok;

                }

                tree.nodeEnd[declNode] = getOffset(stream, source, i);

            } else if (stream.types[i] == TokenStream.TK_IDENTIFIER) {

                String name = getWord(source, stream, i);

                int declNodeStart = stream.tokenStart[i];

                i = skipToken(stream, i);

                int declNode = tree.addNode(JsSyntaxTree.N_VAR_DECL, declNodeStart, 0, parent, name);

                tree.nodeExtra[declNode] = flag;

                int nextTok = skipWhitespaceAndComments(stream, i);

                if (nextTok < stream.length && stream.types[nextTok] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[nextTok]) == ':') {

                    i = skipTypeAnnotation(source, stream, skipToken(stream, nextTok));

                    nextTok = skipWhitespaceAndComments(stream, i);

                }

                if (nextTok < stream.length && stream.types[nextTok] == TokenStream.TK_OPERATOR && source.charAt(stream.tokenStart[nextTok]) == '=') {
                    
                    String inferredType = inferBaseType(source, stream, nextTok);
                    if (inferredType != null && tree.nodeTypeAnn[declNode] == null) {
                        tree.nodeTypeAnn[declNode] = inferredType;
                    }

                    i = parseExpressionTokens(source, stream, tree, nextTok, declNode, STOP_VAR_DECL);

                } else {

                    i = nextTok;

                }

                tree.nodeEnd[declNode] = getOffset(stream, source, i);

            } else {

                i = skipToken(stream, i);

                continue;

            }

            

            if (i < stream.length && stream.types[i] == TokenStream.TK_PUNCT) {

                char c = source.charAt(stream.tokenStart[i]);

                if (c == ',') {

                    i = skipToken(stream, i);

                    continue;

                } else if (c == ';') {

                    i = skipToken(stream, i);

                    break;

                }

            }

            break;

        }

        return i;

    }



    private static int parseDestructuredVars(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent, int flag, char openChar, char closeChar, int emitType) {

        int i = skipToken(stream, startIdx); // skip '{' or '['

        String pendingName = null;

        int pendingNodeStart = -1;

        boolean inAlias = false;

        

        while (i < stream.length) {

            i = skipWhitespaceAndComments(stream, i);

            if (i >= stream.length) break;

            

            byte t = stream.types[i];

            if (t == TokenStream.TK_PUNCT) {

                char c = source.charAt(stream.tokenStart[i]);

                if (c == closeChar) {

                    if (pendingName != null) {

                        int dNode = tree.addNode(emitType, pendingNodeStart, getOffset(stream, source, skipToken(stream, i)), parent, pendingName);

                        if (emitType == JsSyntaxTree.N_VAR_DECL) tree.nodeExtra[dNode] = flag;

                    }

                    return skipToken(stream, i);

                } else if (c == '{' || c == '[') {

                    if (c == '[' && openChar == '{' && !inAlias) {

                        i = skipBlockFast(source, stream, i, '[', ']');

                        continue;

                    }

                    pendingName = null;

                    char nestedClose = c == '{' ? '}' : ']';

                    i = parseDestructuredVars(source, stream, tree, i, parent, flag, c, nestedClose, emitType);

                    inAlias = false;

                    continue;

                } else if (c == ':') {

                    inAlias = true;

                } else if (c == ',') {

                    if (pendingName != null) {

                        int dNode = tree.addNode(emitType, pendingNodeStart, getOffset(stream, source, skipToken(stream, i)), parent, pendingName);

                        if (emitType == JsSyntaxTree.N_VAR_DECL) tree.nodeExtra[dNode] = flag;

                        pendingName = null;

                    }

                    inAlias = false;

                }

            } else if (t == TokenStream.TK_OPERATOR && source.charAt(stream.tokenStart[i]) == '=') {

                if (pendingName != null) {

                    int dNode = tree.addNode(emitType, pendingNodeStart, getOffset(stream, source, skipToken(stream, i)), parent, pendingName);

                    if (emitType == JsSyntaxTree.N_VAR_DECL) tree.nodeExtra[dNode] = flag;

                    pendingName = null;

                }

                i = skipToken(stream, i);

                int depth = 0;

                while (i < stream.length) {

                    byte dt = stream.types[i];

                    if (dt == TokenStream.TK_PUNCT) {

                        char dc = source.charAt(stream.tokenStart[i]);

                        if (dc == '{' || dc == '[' || dc == '(') depth++;

                        else if (dc == '}' || dc == ']' || dc == ')') {

                            if (depth == 0) break;

                            depth--;

                        } else if (depth == 0 && dc == ',') {

                            break;

                        }

                    }

                    i = skipToken(stream, i);

                }

                continue; 

            } else if (t == TokenStream.TK_IDENTIFIER) {

                if (pendingName == null || inAlias) {

                    pendingName = getWord(source, stream, i);

                    pendingNodeStart = stream.tokenStart[i];

                    inAlias = false;

                }

            }

            i = skipToken(stream, i);

        }

        return i;

    }



    private static final int STOP_STMT = 0;

    private static final int STOP_PARAM = 1;

    private static final int STOP_VAR_DECL = 2;

    private static final int STOP_OBJ_PROP = 3;



        private static int emitErrorAndSync(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int parent) {
        int errStart = stream.tokenStart[startIdx];
        int i = skipToken(stream, startIdx);
        tree.addNode(JsSyntaxTree.N_ERROR, errStart, getOffset(stream, source, i), parent, null);
        
        while (i < stream.length) {
            byte t = stream.types[i];
            if (t == TokenStream.TK_PUNCT) {
                char c = source.charAt(stream.tokenStart[i]);
                if (c == ';' || c == '}') {
                    break;
                }
            } else if (t == TokenStream.TK_WHITESPACE || t == TokenStream.TK_COMMENT) {
                int wsStart = stream.tokenStart[i];
                int nextI = skipToken(stream, i);
                int wsEnd = getOffset(stream, source, nextI);
                boolean hasNewline = false;
                for (int k = wsStart; k < wsEnd; k++) {
                    if (source.charAt(k) == '\n') {
                        hasNewline = true;
                        break;
                    }
                }
                if (hasNewline) {
                    break;
                }
            }
            i = skipToken(stream, i);
        }
        return i;
    }

    private static int skipToNextStatement(String source, TokenStream stream, JsSyntaxTree tree, int i, int parent) {

        return parseExpressionTokens(source, stream, tree, i, parent, STOP_STMT);

    }



    private static String inferBaseType(String source, TokenStream stream, int tokIdx) {
        int i = skipToken(stream, tokIdx); 
        i = skipWhitespaceAndComments(stream, i);
        if (i >= stream.length) return null;
        
        byte t = stream.types[i];
        if (t == TokenStream.TK_PUNCT) {
            char c = source.charAt(stream.tokenStart[i]);
            if (c == '[') {
                int next = skipWhitespaceAndComments(stream, skipToken(stream, i));
                if (next < stream.length && stream.types[next] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[next]) == '{') {
                    java.util.List<String> keys = new java.util.ArrayList<>();
                    int curr = skipToken(stream, next);
                    int depth = 1;
                    while (curr < stream.length && depth > 0) {
                        curr = skipWhitespaceAndComments(stream, curr);
                        if (curr >= stream.length) break;
                        byte tt = stream.types[curr];
                        if (tt == TokenStream.TK_PUNCT) {
                            char cc = source.charAt(stream.tokenStart[curr]);
                            if (cc == '{') depth++;
                            else if (cc == '}') depth--;
                        } else if (tt == TokenStream.TK_IDENTIFIER || tt == TokenStream.TK_STRING) {
                            if (depth == 1) {
                                int colonNext = skipWhitespaceAndComments(stream, skipToken(stream, curr));
                                if (colonNext < stream.length && stream.types[colonNext] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[colonNext]) == ':') {
                                    String key = getWord(source, stream, curr);
                                    if (tt == TokenStream.TK_STRING) key = key.substring(1, key.length() - 1);
                                    if (!keys.contains(key)) keys.add(key);
                                }
                            }
                        }
                        curr = skipToken(stream, curr);
                    }
                    if (!keys.isEmpty()) {
                        return "@ARRAY_OF_INLINE_SHAPE:" + String.join(",", keys);
                    }
                } else if (next < stream.length && stream.types[next] == TokenStream.TK_NUMBER) {
                    return "@ARRAY_OF_NUMBER";
                } else if (next < stream.length && stream.types[next] == TokenStream.TK_STRING) {
                    return "@ARRAY_OF_STRING";
                }
                return "@ARRAY";
            }
            if (c == '{') return "@OBJECT";
        } else if (t == TokenStream.TK_STRING) {
            return "@STRING";
        } else if (t == TokenStream.TK_NUMBER) {
            return "@NUMBER";
        } else if (t == TokenStream.TK_KEYWORD) {
            String kw = getWord(source, stream, i);
            if ("new".equals(kw)) {
                int next = skipWhitespaceAndComments(stream, skipToken(stream, i));
                if (next < stream.length && stream.types[next] == TokenStream.TK_IDENTIFIER) {
                    String className = getWord(source, stream, next);
                    if ("Map".equals(className)) return "@MAP";
                    if ("Set".equals(className)) return "@SET";
                    if ("Promise".equals(className)) return "@PROMISE";
                    return className;
                }
            } else if ("async".equals(kw)) {
                return "@PROMISE";
            } else if ("true".equals(kw) || "false".equals(kw)) {
                return "@BOOLEAN";
            }
        } else if (t == TokenStream.TK_IDENTIFIER) {
            String id = getWord(source, stream, i);
            if ("require".equals(id)) {
                int next = skipWhitespaceAndComments(stream, skipToken(stream, i));
                if (next < stream.length && stream.types[next] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[next]) == '(') {
                    int arg = skipWhitespaceAndComments(stream, skipToken(stream, next));
                    if (arg < stream.length && stream.types[arg] == TokenStream.TK_STRING) {
                        String raw = getWord(source, stream, arg);
                        if (raw != null && raw.length() >= 2) {
                            return "@REQUIRE:" + raw.substring(1, raw.length() - 1);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void convertObjectLiteralToParams(JsSyntaxTree tree, int nodeId) {

        if (tree.nodeType[nodeId] == JsSyntaxTree.N_STATEMENT && tree.nodeName[nodeId] != null) {

            int child = tree.nodeChild[nodeId];

            if (child != 0 && tree.nodeType[child] == JsSyntaxTree.N_IDENTIFIER) {

                tree.nodeType[child] = JsSyntaxTree.N_PARAM;

            } else {

                tree.nodeType[nodeId] = JsSyntaxTree.N_PARAM;

            }

        }

        int child = tree.nodeChild[nodeId];

        while (child != 0) {

            convertObjectLiteralToParams(tree, child);

            child = tree.nodeSibling[child];

        }

    }



    private static int parseExpressionTokens(String source, TokenStream stream, JsSyntaxTree tree, int i, int parent, int stopCond) {

        int depth = 0; 

        int initialLastChild = tree.nodeLastChild[parent];

        

        while (i < stream.length) {
            byte t = stream.types[i];
            if (t == TokenStream.TK_NONE) break; // End of tokenized region

            if (t == TokenStream.TK_OPERATOR && source.charAt(stream.tokenStart[i]) == '=' && stream.tokenStart[i] + 1 < source.length() && source.charAt(stream.tokenStart[i] + 1) == '>') {

                int arrowNode = tree.addNode(JsSyntaxTree.N_ARROW_FUNC, stream.tokenStart[i], 0, parent, null);

                

                int firstExprChild = (initialLastChild == 0) ? tree.nodeChild[parent] : tree.nodeSibling[initialLastChild];

                if (firstExprChild > 0 && firstExprChild < tree.nodeCount && firstExprChild != arrowNode) {
                    int prev = firstExprChild;
                    int loopPrev = 0;
                    while (prev > 0 && prev < tree.nodeCount && tree.nodeSibling[prev] > 0 && tree.nodeSibling[prev] < tree.nodeCount && tree.nodeSibling[prev] != arrowNode && ++loopPrev <= tree.nodeCount) {
                        prev = tree.nodeSibling[prev];
                    }

                    if (initialLastChild == 0) {
                        tree.nodeChild[parent] = arrowNode;
                    } else if (initialLastChild < tree.nodeCount) {
                        tree.nodeSibling[initialLastChild] = arrowNode;
                    }

                    tree.nodeChild[arrowNode] = firstExprChild;
                    tree.nodeLastChild[arrowNode] = prev;
                    if (prev > 0 && prev < tree.nodeCount) {
                        tree.nodeSibling[prev] = 0;
                    }

                    int curr = firstExprChild;
                    int lastParam = 0;
                    int loopCurr = 0;
                    while (curr > 0 && curr < tree.nodeCount && ++loopCurr <= tree.nodeCount) {

                        int next = tree.nodeSibling[curr];

                        tree.nodeParent[curr] = arrowNode;



                        boolean isDefaultValue = false;

                        if (lastParam != 0) {

                            for (int p = tree.nodeEnd[lastParam]; p < tree.nodeStart[curr]; p++) {

                                char c = source.charAt(p);

                                if (c == ',') break;

                                if (c == '=') { isDefaultValue = true; break; }

                            }

                        }



                        if (isDefaultValue) {

                            tree.nodeSibling[lastParam] = next;

                            tree.nodeParent[curr] = lastParam;

                            tree.nodeChild[lastParam] = curr;

                            tree.nodeSibling[curr] = 0;

                        } else {

                            if (tree.nodeType[curr] == JsSyntaxTree.N_IDENTIFIER) {

                                tree.nodeType[curr] = JsSyntaxTree.N_PARAM;

                            } else if (tree.nodeType[curr] == JsSyntaxTree.N_STATEMENT) {

                                convertObjectLiteralToParams(tree, curr);

                            }

                            lastParam = curr;

                        }

                        curr = next;

                    }

                }

                

                i = skipToken(stream, i); // skip '=>' (both chars share same tokenStart)

                i = skipWhitespaceAndComments(stream, i);

                if (i < stream.length && stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == '{') {

                    i = parseBlock(source, stream, tree, i, arrowNode);

                } else {

                    i = parseExpressionTokens(source, stream, tree, i, arrowNode, stopCond);

                }

                tree.nodeEnd[arrowNode] = getOffset(stream, source, i);

                continue;

            }

            

            if (t == TokenStream.TK_IDENTIFIER || (t == TokenStream.TK_KEYWORD && ("this".equals(getWord(source, stream, i)) || "super".equals(getWord(source, stream, i))))) {

                int startId = i;

                int curr = skipToken(stream, i);

                String name = getWord(source, stream, i);

                boolean isMember = false;

                

                while (curr < stream.length) {

                    int next = skipWhitespaceAndComments(stream, curr);

                    if (next < stream.length && stream.types[next] == TokenStream.TK_PUNCT && 
                        (source.charAt(stream.tokenStart[next]) == '.' || 
                         (source.charAt(stream.tokenStart[next]) == '?' && stream.tokenStart[next] + 1 < source.length() && source.charAt(stream.tokenStart[next] + 1) == '.'))) {

                        int afterDot = skipWhitespaceAndComments(stream, skipToken(stream, next));

                        if (afterDot < stream.length && stream.types[afterDot] == TokenStream.TK_IDENTIFIER) {

                            isMember = true;

                            name += "." + getWord(source, stream, afterDot);

                            curr = skipToken(stream, afterDot);

                        } else {

                            break;

                        }

                    } else {

                        break;

                    }

                }

                

                int nextAfterChain = skipWhitespaceAndComments(stream, curr);

                if (nextAfterChain < stream.length && stream.types[nextAfterChain] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[nextAfterChain]) == '(') {

                    int callNode = tree.addNode(JsSyntaxTree.N_CALL_EXPR, stream.tokenStart[startId], 0, parent, name);

                    curr = parseArguments(source, stream, tree, nextAfterChain, callNode);

                    tree.nodeEnd[callNode] = getOffset(stream, source, curr);

                    i = curr;

                    continue;

                } else {

                    if (isMember) {

                        tree.addNode(JsSyntaxTree.N_MEMBER_EXPR, stream.tokenStart[startId], getOffset(stream, source, curr), parent, name);

                    } else {

                        tree.addNode(JsSyntaxTree.N_IDENTIFIER, stream.tokenStart[startId], getOffset(stream, source, curr), parent, name);

                    }

                    i = curr;

                    continue; 

                }

            } else if (t == TokenStream.TK_KEYWORD && ("let".equals(getWord(source, stream, i)) || "const".equals(getWord(source, stream, i)) || "var".equals(getWord(source, stream, i)))) {

                i = parseVarDecl(source, stream, tree, i, parent);

                continue;

            } else if (t == TokenStream.TK_KEYWORD && "function".equals(getWord(source, stream, i))) {

                i = parseFunction(source, stream, tree, i, parent);

                continue;

            } else if (t == TokenStream.TK_KEYWORD && "class".equals(getWord(source, stream, i))) {

                i = parseClass(source, stream, tree, i, parent);

                continue;

            }

            

            if (t == TokenStream.TK_PUNCT) {

                char c = source.charAt(stream.tokenStart[i]);

                if (c == '{') {

                    if (isObjectLiteralStart(source, stream, i)) {

                        i = parseObjectLiteral(source, stream, tree, i, parent);

                    } else {

                        i = parseBlock(source, stream, tree, i, parent);

                    }

                    continue; 

                } else if (c == '}') {

                    if (depth <= 0 && (stopCond == STOP_STMT || stopCond == STOP_VAR_DECL || stopCond == STOP_OBJ_PROP)) return i; 

                } else if (c == '(' || c == '[') {

                    depth++;

                } else if (c == ')' || c == ']') {

                    depth--;

                    if (c == ')' && depth < 0 && stopCond == STOP_PARAM) {

                        return i;

                    }

                } else if (c == ',' && depth <= 0 && (stopCond == STOP_PARAM || stopCond == STOP_VAR_DECL || stopCond == STOP_OBJ_PROP)) {

                    return i;

                } else if (c == ';' && depth <= 0 && (stopCond == STOP_STMT || stopCond == STOP_VAR_DECL || stopCond == STOP_OBJ_PROP)) {

                    return skipToken(stream, i);

                }

            }

            

            int nextTok = skipToken(stream, i);

            

            if (depth <= 0 && (stopCond == STOP_STMT || stopCond == STOP_VAR_DECL)) {

                int next = skipWhitespaceAndComments(stream, nextTok);

                if (next < stream.length && isTopLevelKeyword(source, stream, next)) {

                    if (hasNewlineBetween(source, stream, i, next)) {

                        return next;

                    }

                }

            }

            i = nextTok;

        }

        return i;

    }



    private static int parseArguments(String source, TokenStream stream, JsSyntaxTree tree, int startIdx, int callNode) {

        int i = skipToken(stream, startIdx); // skip '('

        while (i < stream.length) {

            i = skipWhitespaceAndComments(stream, i);

            if (i >= stream.length) break;

            

            if (stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == ')') {

                i = skipToken(stream, i);

                break;

            }

            

            int paramNode = tree.addNode(JsSyntaxTree.N_PARAM, stream.tokenStart[i], 0, callNode, null);

            i = parseExpressionTokens(source, stream, tree, i, paramNode, STOP_PARAM);

            tree.nodeEnd[paramNode] = getOffset(stream, source, i);

            

            if (i < stream.length && stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == ',') {

                i = skipToken(stream, i);

            } else if (i < stream.length && stream.types[i] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[i]) == ')') {

                i = skipToken(stream, i);

                break;

            }

        }

        return i;

    }



    private static int skipBlockFast(String source, TokenStream stream, int i, char openChar, char closeChar) {

        int depth = 0;

        while (i < stream.length) {

            byte t = stream.types[i];

            if (t == TokenStream.TK_PUNCT) {

                char c = source.charAt(stream.tokenStart[i]);

                if (c == openChar) depth++;

                else if (c == closeChar) {

                    depth--;

                    if (depth <= 0) {

                        return skipToken(stream, i);

                    }

                }

            }

            i = skipToken(stream, i);

        }

        return i;

    }



    private static boolean isTopLevelKeyword(String source, TokenStream stream, int idx) {

        if (stream.types[idx] != TokenStream.TK_KEYWORD) return false;

        String kw = getWord(source, stream, idx);

        return "import".equals(kw) || "export".equals(kw) || "class".equals(kw) || 

               "function".equals(kw) || "const".equals(kw) || "let".equals(kw) || "var".equals(kw) ||

               "return".equals(kw) || "if".equals(kw) || "for".equals(kw) || "while".equals(kw) ||

               "interface".equals(kw) || "type".equals(kw) || "enum".equals(kw) || "namespace".equals(kw) || "module".equals(kw) ||

               "do".equals(kw) || "with".equals(kw) || "switch".equals(kw) || "case".equals(kw) || "default".equals(kw) ||

               "try".equals(kw) || "finally".equals(kw);

    }



    private static boolean hasNewlineBetween(String source, TokenStream stream, int from, int to) {

        for (int k = from; k < to && k < source.length(); k++) {

            if (source.charAt(k) == '\n') return true;

        }

        return false;

    }



    private static boolean isObjectLiteralStart(String source, TokenStream stream, int index) {

        int prev = index - 1;

        while (prev >= 0) {

            byte t = stream.types[prev];

            if (t != TokenStream.TK_WHITESPACE && t != TokenStream.TK_COMMENT) {

                break;

            }

            prev--;

        }

        if (prev < 0) return false;

        

        byte pt = stream.types[prev];

        if (pt == TokenStream.TK_OPERATOR) return true; // =, ==, =>, +, etc.

        

        if (pt == TokenStream.TK_PUNCT) {

            char c = source.charAt(stream.tokenStart[prev]);

            if (c == '(' || c == '[' || c == ',' || c == ':') return true;

            if (c == ')' || c == ']' || c == '}' || c == ';') return false;

        }

        

        if (pt == TokenStream.TK_KEYWORD) {

            String kw = getWord(source, stream, prev);

            if ("return".equals(kw) || "yield".equals(kw) || "throw".equals(kw) || "await".equals(kw) || "default".equals(kw)) return true;

            return false;

        }

        

        return false;

    }



    private static int skipTypeAnnotation(String source, TokenStream stream, int i) {

        int depth = 0;

        while (i < stream.length) {

            byte t = stream.types[i];

            if (t == TokenStream.TK_PUNCT || t == TokenStream.TK_OPERATOR) {

                char c = source.charAt(stream.tokenStart[i]);

                if (depth == 0 && (c == '=' || c == ',' || c == ';' || c == ')' || c == '}')) {
                    return i;
                }
                if (c == '{' || c == '[' || c == '(' || c == '<') depth++;
                else if (c == '}' || c == ']' || c == ')' || c == '>') { if (depth > 0) depth--; }

            }

            i = skipToken(stream, i);

        }

        return i;

    }



    private static int skipGenericArguments(String source, TokenStream stream, int i) {

        int depth = 0;

        while (i < stream.length) {

            byte t = stream.types[i];

            if (t == TokenStream.TK_OPERATOR) {

                char c = source.charAt(stream.tokenStart[i]);

                if (c == '<') depth++;

                else if (c == '>') {

                    depth--;

                    if (depth <= 0) {

                        return skipToken(stream, i);

                    }

                }

            } else if (t == TokenStream.TK_PUNCT) {

                char c = source.charAt(stream.tokenStart[i]);

                if (c == '{' || c == '(' || c == ';') break; // safety breakout

            }

            i = skipToken(stream, i);

        }

        return i;

    }



    private static int skipToken(TokenStream stream, int i) {

        if (i >= stream.length) return i;

        int start = stream.tokenStart[i];

        while (i < stream.length && stream.tokenStart[i] == start) {

            i++;

        }

        return i;

    }



    private static int skipWhitespaceAndComments(TokenStream stream, int i) {
        while (i < stream.length) {
            byte t = stream.types[i];
            if (t == TokenStream.TK_WHITESPACE || t == TokenStream.TK_COMMENT) {
                i = skipToken(stream, i);

            } else {

                break;

            }

        }

        return i;

    }



    private static String getWord(String source, TokenStream stream, int i) {

        int start = stream.tokenStart[i];

        int end = skipToken(stream, i);

        return source.substring(start, end);

    }

}


