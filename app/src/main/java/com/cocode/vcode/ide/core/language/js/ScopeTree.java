package com.cocode.vcode.ide.core.language.js;

import java.util.Arrays;
import java.util.HashMap;

/**
 * A flat-array data container representing the lexical scopes of a file.
 * Used for ultra-fast symbol resolution, autocomplete filtering, and go-to-definition.
 */
public final class ScopeTree {

    // Parallel arrays for scopes. The index is the "scope ID".
    public int[] scopeParent;
    public int[] scopeNode; // Points back to the JsSyntaxTree node ID that defines this scope

    /**
     * Maps a JsSyntaxTree node ID to its enclosing Scope ID.
     * Populated during build() to enable O(1) scope lookups for any AST node.
     */
    public int[] nodeToScope;

    /**
     * Number of active scopes currently in the tree.
     */
    public int scopeCount;

    /**
     * Maps a variable/symbol name to a flat array of tuples [scopeId, nodeId, kind].
     * During resolution, the engine iterates this array to find the deepest matching scope.
     */
    public final HashMap<String, int[]> symbols;

    /**
     * Constructs a ScopeTree with a pre-allocated capacity to avoid GC pauses.
     *
     * @param initialCapacity typically 256 or 512 depending on file complexity
     */
    public ScopeTree(int initialCapacity) {
        scopeParent = new int[initialCapacity];
        scopeNode = new int[initialCapacity];
        symbols = new HashMap<>(initialCapacity);
        
        // Scope 0 is reserved as the global/file root scope
        scopeCount = 1;
    }

    /**
     * Resets the tree for re-use without re-allocating the arrays or map entirely.
     */
    public void reset() {
        scopeCount = 1;
        symbols.clear();
        nodeToScope = null;
    }

    /**
     * Manually inserts a new scope and handles array resizing if capacity is exceeded.
     * Returns the assigned scope ID.
     */
    public int addScope(int parent, int nodeId) {
        if (scopeCount >= scopeParent.length) {
            int newCap = scopeParent.length * 2;
            scopeParent = Arrays.copyOf(scopeParent, newCap);
            scopeNode = Arrays.copyOf(scopeNode, newCap);
        }

        int id = scopeCount++;
        scopeParent[id] = parent;
        scopeNode[id] = nodeId;
        return id;
    }

    /**
     * Registers a symbol declaration in a specific scope, pointing to its AST node and type.
     */
    public void addSymbol(String name, int scopeId, int nodeId, int kind) {
        int[] tuples = symbols.get(name);
        if (tuples == null) {
            tuples = new int[] { scopeId, nodeId, kind };
            symbols.put(name, tuples);
        } else {
            int len = tuples.length;
            int[] newTuples = Arrays.copyOf(tuples, len + 3);
            newTuples[len] = scopeId;
            newTuples[len + 1] = nodeId;
            newTuples[len + 2] = kind;
            symbols.put(name, newTuples);
        }
    }

    /**
     * Iteratively builds a ScopeTree from a populated JsSyntaxTree.
     * Guaranteed O(N) single-pass.
     */
    public static ScopeTree build(JsSyntaxTree tree) {
        ScopeTree scopeTree = new ScopeTree(Math.max(256, tree.nodeCount / 4));
        scopeTree.nodeToScope = new int[tree.nodeCount];
        scopeTree.nodeToScope[0] = 0; // Root node belongs to global scope 0
        
        int child = tree.nodeChild[0];
        while (child != 0) {
            traverseDFS(tree, scopeTree, child, 0);
            child = tree.nodeSibling[child];
        }
        
        return scopeTree;
    }

    private static void traverseDFS(JsSyntaxTree tree, ScopeTree scopeTree, int nodeId, int parentScope) {
        int type = tree.nodeType[nodeId];
        
        int myScope = parentScope;
        if (isScopeCreator(type)) {
            boolean isFunctionBody = false;
            if (type == JsSyntaxTree.N_BLOCK) {
                int parentNode = tree.nodeParent[nodeId];
                if (parentNode != 0) {
                    int pType = tree.nodeType[parentNode];
                    if (pType == JsSyntaxTree.N_FUNC_DECL || pType == JsSyntaxTree.N_ARROW_FUNC || pType == JsSyntaxTree.N_METHOD || pType == JsSyntaxTree.N_GETTER || pType == JsSyntaxTree.N_SETTER) {
                        isFunctionBody = true;
                    }
                }
            }
            if (!isFunctionBody) {
                myScope = scopeTree.addScope(parentScope, nodeId);
            }
        }
        scopeTree.nodeToScope[nodeId] = myScope;
        
        String name = tree.nodeName[nodeId];
        if (name != null && !name.isEmpty() && !"{destructure}".equals(name)) {
            if (isDeclaration(type)) {
                int targetScope = isScopeCreator(type) ? parentScope : myScope;
                
                if (type == JsSyntaxTree.N_VAR_DECL && tree.nodeExtra[nodeId] == JsSyntaxTree.FLAG_VAR) {
                    while (targetScope > 0) {
                        int scopeCreatorNode = scopeTree.scopeNode[targetScope];
                        int creatorType = tree.nodeType[scopeCreatorNode];
                        if (creatorType == JsSyntaxTree.N_FUNC_DECL || 
                            creatorType == JsSyntaxTree.N_ARROW_FUNC || 
                            creatorType == JsSyntaxTree.N_METHOD || 
                            creatorType == JsSyntaxTree.N_GETTER || 
                            creatorType == JsSyntaxTree.N_SETTER ||
                            creatorType == JsSyntaxTree.N_CLASS_DECL) {
                            break;
                        } else {
                            targetScope = scopeTree.scopeParent[targetScope];
                        }
                    }
                }
                
                scopeTree.addSymbol(name, targetScope, nodeId, type);
            }
        }
        
        int child = tree.nodeChild[nodeId];
        while (child != 0) {
            traverseDFS(tree, scopeTree, child, myScope);
            child = tree.nodeSibling[child];
        }
    }

    private static boolean isScopeCreator(int type) {
        return type == JsSyntaxTree.N_FUNC_DECL || 
               type == JsSyntaxTree.N_ARROW_FUNC || 
               type == JsSyntaxTree.N_CLASS_DECL || 
               type == JsSyntaxTree.N_METHOD ||
               type == JsSyntaxTree.N_GETTER ||
               type == JsSyntaxTree.N_SETTER ||
               type == JsSyntaxTree.N_BLOCK ||
               type == JsSyntaxTree.N_FOR_STMT ||
               type == JsSyntaxTree.N_CATCH_CLAUSE;
    }

    private static boolean isDeclaration(int type) {
        return type == JsSyntaxTree.N_FUNC_DECL ||
               type == JsSyntaxTree.N_ARROW_FUNC ||
               type == JsSyntaxTree.N_CLASS_DECL ||
               type == JsSyntaxTree.N_METHOD ||
               type == JsSyntaxTree.N_GETTER ||
               type == JsSyntaxTree.N_SETTER ||
               type == JsSyntaxTree.N_VAR_DECL ||
               type == JsSyntaxTree.N_PARAM ||
               type == JsSyntaxTree.N_IMPORT;
    }

    /**
     * Resolves a symbol name starting from a specific scope, traversing up to the global scope.
     * Uses a single hash lookup and a fast array scan to model lexical shadowing perfectly.
     *
     * @param name The symbol name to resolve.
     * @param atScopeId The scope ID where the lookup originates.
     * @return An array [scopeId, nodeId, kind] if found, or null if unresolved.
     */
    public int[] lookupSymbol(String name, int atScopeId) {
        return lookupSymbol(name, atScopeId, Integer.MAX_VALUE, null);
    }

    public int[] lookupSymbol(String name, int atScopeId, int usageOffset, JsSyntaxTree tree) {
        int[] tuples = symbols.get(name);
        if (tuples == null) return null;

        int currentScope = atScopeId;
        while (currentScope >= 0) {
            // Scan the tuple array backward to return the latest declaration in the currentScope
            for (int i = tuples.length - 3; i >= 0; i -= 3) {
                if (tuples[i] == currentScope) {
                    int declNodeId = tuples[i+1];
                    if (currentScope == atScopeId && tree != null && tree.nodeStart[declNodeId] > usageOffset) {
                        int kind = tuples[i+2];
                        boolean isHoisted = kind == JsSyntaxTree.N_FUNC_DECL || kind == JsSyntaxTree.N_IMPORT ||
                                          (kind == JsSyntaxTree.N_VAR_DECL && (tree.nodeExtra[declNodeId] & 3) == JsSyntaxTree.FLAG_VAR);
                        if (!isHoisted) continue;
                    }
                    return new int[] { tuples[i], tuples[i+1], tuples[i+2] };
                }
            }
            
            if (currentScope == 0) break;
            currentScope = scopeParent[currentScope];
        }
        return null;
    }

    /**
     * Finds the deepest scope containing the given source offset.
     */
    public int findScopeAt(int offset, JsSyntaxTree tree) {
        if (nodeToScope == null || tree.nodesByOffset == null) return 0;
        
        int deepest = 0;
        int minLen = Integer.MAX_VALUE;
        
        int n = tree.nodeCount - 1;
        if (n <= 0) return 0;

        int low = 1;
        int high = n;
        int searchIdx = n;
        
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midNodeId = tree.nodesByOffset[mid];
            if (tree.nodeStart[midNodeId] <= offset) {
                searchIdx = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        for (int j = searchIdx; j >= 1; j--) {
            int i = tree.nodesByOffset[j];
            if (offset >= tree.nodeStart[i] && offset < tree.nodeEnd[i]) {
                int len = tree.nodeEnd[i] - tree.nodeStart[i];
                if (len < minLen) {
                    minLen = len;
                    deepest = i;
                }
            }
        }
        
        return deepest == 0 ? 0 : nodeToScope[deepest];
    }

    /**
     * Finds all N_IDENTIFIER nodes that resolve to the same declaration as the name at the given scope.
     * 
     * @param name The symbol name to search for.
     * @param atScopeId The scope from which to resolve the target declaration.
     * @param tree The syntax tree containing the nodes.
     * @return An array of JsSyntaxTree node IDs representing all references to the declaration.
     */
    public int[] findAllReferences(String name, int atScopeId, JsSyntaxTree tree) {
        int[] decl = lookupSymbol(name, atScopeId, Integer.MAX_VALUE, tree);
        if (decl == null || nodeToScope == null) {
            return new int[0];
        }

        int targetDeclNodeId = decl[1];
        int[] refs = new int[16];
        int count = 0;

        for (int i = 1; i < tree.nodeCount; i++) {
            boolean matches = false;
            if (tree.nodeType[i] == JsSyntaxTree.N_IDENTIFIER || tree.nodeType[i] == JsSyntaxTree.N_CALL_EXPR) {
                matches = name.equals(tree.nodeName[i]);
            } else if (tree.nodeType[i] == JsSyntaxTree.N_MEMBER_EXPR) {
                matches = name.equals(tree.nodeName[i]) || (tree.nodeName[i] != null && tree.nodeName[i].startsWith(name + "."));
            }
            
            if (matches) {
                int scope = nodeToScope[i];
                int[] resolved = lookupSymbol(name, scope, Integer.MAX_VALUE, tree);
                if (resolved != null && resolved[1] == targetDeclNodeId) {
                    if (count >= refs.length) {
                        refs = Arrays.copyOf(refs, refs.length * 2);
                    }
                    refs[count++] = i;
                }
            }
        }

        return Arrays.copyOf(refs, count);
    }
}
