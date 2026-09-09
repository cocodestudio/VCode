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

    public static final int MAX_SCOPES = 32_768;

    /**
     * Manually inserts a new scope and handles array resizing if capacity is exceeded.
     * Returns the assigned scope ID.
     */
    public int addScope(int parent, int nodeId) {
        if (scopeCount >= MAX_SCOPES) {
            return 0;
        }
        if (scopeCount >= scopeParent.length) {
            int newCap = Math.min(MAX_SCOPES, scopeParent.length * 2);
            if (newCap <= scopeParent.length) {
                return 0;
            }
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
     * Guaranteed O(N) single-pass without recursion or stack exhaustion.
     */
    public static ScopeTree build(JsSyntaxTree tree) {
        if (tree == null || tree.nodeCount <= 1) {
            ScopeTree emptyTree = new ScopeTree(16);
            emptyTree.nodeToScope = new int[Math.max(1, tree != null ? tree.nodeCount : 0)];
            return emptyTree;
        }

        ScopeTree scopeTree = new ScopeTree(Math.max(256, tree.nodeCount / 4));
        scopeTree.nodeToScope = new int[tree.nodeCount];
        scopeTree.nodeToScope[0] = 0; // Root node belongs to global scope 0

        int maxNodes = tree.nodeCount;
        int[] nodeStack = new int[256];
        int[] scopeStack = new int[256];
        boolean[] visited = new boolean[maxNodes];
        int top = 0;

        // Push root's immediate children in reverse sibling order
        // so that the first child is popped and processed first
        int child = tree.nodeChild[0];
        int firstChildCount = 0;
        int temp = child;
        int loopGuard = 0;
        while (temp > 0 && temp < maxNodes && ++loopGuard <= maxNodes) {
            firstChildCount++;
            temp = tree.nodeSibling[temp];
        }

        if (firstChildCount > 0) {
            int[] rootChildren = new int[firstChildCount];
            temp = child;
            for (int k = 0; k < firstChildCount && temp > 0 && temp < maxNodes; k++) {
                rootChildren[k] = temp;
                temp = tree.nodeSibling[temp];
            }
            for (int k = firstChildCount - 1; k >= 0; k--) {
                if (top >= nodeStack.length) {
                    nodeStack = Arrays.copyOf(nodeStack, nodeStack.length * 2);
                    scopeStack = Arrays.copyOf(scopeStack, scopeStack.length * 2);
                }
                nodeStack[top] = rootChildren[k];
                scopeStack[top] = 0;
                top++;
            }
        }

        // Iterative traversal
        while (top > 0) {
            top--;
            int nodeId = nodeStack[top];
            int parentScope = scopeStack[top];

            if (nodeId <= 0 || nodeId >= maxNodes || visited[nodeId]) {
                continue;
            }
            visited[nodeId] = true;

            int type = tree.nodeType[nodeId];
            int myScope = parentScope;

            if (isScopeCreator(type)) {
                boolean isFunctionBody = false;
                if (type == JsSyntaxTree.N_BLOCK) {
                    int parentNode = tree.nodeParent[nodeId];
                    if (parentNode > 0 && parentNode < maxNodes) {
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

            if (nodeId < scopeTree.nodeToScope.length) {
                scopeTree.nodeToScope[nodeId] = myScope;
            }

            String name = tree.nodeName[nodeId];
            if (name != null && !name.isEmpty() && !"{destructure}".equals(name)) {
                if (isDeclaration(type, nodeId, tree)) {
                    int targetScope = isScopeCreator(type) ? parentScope : myScope;

                    if (type == JsSyntaxTree.N_VAR_DECL && tree.nodeExtra[nodeId] == JsSyntaxTree.FLAG_VAR) {
                        int varLoop = 0;
                        while (targetScope > 0 && targetScope < scopeTree.scopeCount && ++varLoop <= scopeTree.scopeCount) {
                            int scopeCreatorNode = scopeTree.scopeNode[targetScope];
                            if (scopeCreatorNode <= 0 || scopeCreatorNode >= maxNodes) {
                                break;
                            }
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

            // Collect children of nodeId and push in reverse order
            int currChild = tree.nodeChild[nodeId];
            if (currChild > 0 && currChild < maxNodes) {
                int childCount = 0;
                int cTemp = currChild;
                int cGuard = 0;
                while (cTemp > 0 && cTemp < maxNodes && ++cGuard <= maxNodes) {
                    childCount++;
                    cTemp = tree.nodeSibling[cTemp];
                }

                if (childCount == 1) {
                    if (top >= nodeStack.length) {
                        nodeStack = Arrays.copyOf(nodeStack, nodeStack.length * 2);
                        scopeStack = Arrays.copyOf(scopeStack, scopeStack.length * 2);
                    }
                    nodeStack[top] = currChild;
                    scopeStack[top] = myScope;
                    top++;
                } else if (childCount > 1) {
                    int[] childList = new int[childCount];
                    cTemp = currChild;
                    for (int k = 0; k < childCount && cTemp > 0 && cTemp < maxNodes; k++) {
                        childList[k] = cTemp;
                        cTemp = tree.nodeSibling[cTemp];
                    }
                    for (int k = childCount - 1; k >= 0; k--) {
                        if (top >= nodeStack.length) {
                            nodeStack = Arrays.copyOf(nodeStack, nodeStack.length * 2);
                            scopeStack = Arrays.copyOf(scopeStack, scopeStack.length * 2);
                        }
                        nodeStack[top] = childList[k];
                        scopeStack[top] = myScope;
                        top++;
                    }
                }
            }
        }

        return scopeTree;
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
               type == JsSyntaxTree.N_CATCH_CLAUSE ||
               type == JsSyntaxTree.N_ENUM ||
               type == JsSyntaxTree.N_INTERFACE;
    }

    private static boolean isDeclaration(int type, int nodeId, JsSyntaxTree tree) {
        if (type == JsSyntaxTree.N_FUNC_DECL ||
            type == JsSyntaxTree.N_ARROW_FUNC ||
            type == JsSyntaxTree.N_CLASS_DECL ||
            type == JsSyntaxTree.N_METHOD ||
            type == JsSyntaxTree.N_GETTER ||
            type == JsSyntaxTree.N_SETTER ||
            type == JsSyntaxTree.N_VAR_DECL ||
            type == JsSyntaxTree.N_PARAM ||
            type == JsSyntaxTree.N_IMPORT ||
            type == JsSyntaxTree.N_ENUM ||
            type == JsSyntaxTree.N_INTERFACE ||
            type == JsSyntaxTree.N_TYPE_ALIAS) {
            return true;
        }
        if (type == JsSyntaxTree.N_PROPERTY && nodeId > 0 && tree != null && nodeId < tree.nodeCount) {
            int pId = tree.nodeParent[nodeId];
            if (pId > 0 && pId < tree.nodeCount && tree.nodeType[pId] == JsSyntaxTree.N_ENUM) {
                return true;
            }
        }
        return false;
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
        int scopeLoop = 0;
        while (currentScope >= 0 && currentScope < scopeCount && ++scopeLoop <= scopeCount) {
            // Scan the tuple array backward to return the latest declaration in the currentScope
            for (int i = tuples.length - 3; i >= 0; i -= 3) {
                if (tuples[i] == currentScope) {
                    int declNodeId = tuples[i+1];
                    if (currentScope == atScopeId && tree != null && declNodeId > 0 && declNodeId < tree.nodeCount && tree.nodeStart[declNodeId] > usageOffset) {
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
        if (nodeToScope == null || tree.nodesByOffset == null || tree.nodeCount <= 1) return 0;
        
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
            if (midNodeId > 0 && midNodeId < tree.nodeCount && tree.nodeStart[midNodeId] <= offset) {
                searchIdx = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        for (int j = searchIdx; j >= 1; j--) {
            int i = tree.nodesByOffset[j];
            if (i > 0 && i < tree.nodeCount && offset >= tree.nodeStart[i] && offset < tree.nodeEnd[i]) {
                int len = tree.nodeEnd[i] - tree.nodeStart[i];
                if (len < minLen) {
                    minLen = len;
                    deepest = i;
                }
            }
        }
        
        return (deepest > 0 && deepest < nodeToScope.length) ? nodeToScope[deepest] : 0;
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
        if (decl == null || nodeToScope == null || tree == null) {
            return new int[0];
        }

        int targetDeclNodeId = decl[1];
        int[] refs = new int[16];
        int count = 0;

        for (int i = 1; i < tree.nodeCount && i < nodeToScope.length; i++) {
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
