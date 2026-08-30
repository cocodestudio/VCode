package com.cocode.vcode.ide.core.language.js;

import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

/**
 * A flat-array data container representing a JavaScript/TypeScript Abstract Syntax Tree.
 * It uses Structure-of-Arrays (SoA) instead of objects to avoid GC overhead.
 */
public final class JsSyntaxTree {

    public static final int N_NONE         = 0;
    public static final int N_IMPORT       = 1;
    public static final int N_FUNC_DECL    = 2;
    public static final int N_ARROW_FUNC   = 3;
    public static final int N_CLASS_DECL   = 4;
    public static final int N_METHOD       = 5;
    public static final int N_GETTER       = 6;
    public static final int N_SETTER       = 7;
    public static final int N_VAR_DECL     = 8;   // let/const/var
    public static final int N_PARAM        = 9;
    public static final int N_BLOCK        = 10;
    public static final int N_EXPORT       = 11;
    public static final int N_CALL_EXPR    = 12;
    public static final int N_MEMBER_EXPR  = 13;  // a.b
    public static final int N_IDENTIFIER   = 14;
    public static final int N_DESTRUCTURE  = 15;
    public static final int N_STATEMENT    = 16;  // generic statement
    public static final int N_FOR_STMT     = 17;
    public static final int N_CATCH_CLAUSE = 18;
    public static final int N_IF_STMT      = 19;
    public static final int N_WHILE_STMT   = 20;
    public static final int N_DO_STMT      = 21;
    public static final int N_WITH_STMT    = 22;
    public static final int N_SWITCH_STMT  = 23;
    public static final int N_CASE_CLAUSE  = 24;
    public static final int N_TRY_STMT     = 25;
    public static final int N_FINALLY_CLAUSE = 26;
    public static final int N_ERROR        = 27;
    public static final int N_INTERFACE    = 28;
    public static final int N_TYPE_ALIAS   = 29;
    public static final int N_ENUM         = 30;
    public static final int N_OBJECT_LITERAL = 31;
    public static final int N_PROPERTY     = 32;

    public static final int FLAG_NONE      = 0;
    public static final int FLAG_VAR       = 1;
    public static final int FLAG_LET       = 2;
    public static final int FLAG_CONST     = 3;
    public static final int FLAG_REST      = 4;

    public int[] nodesByOffset; // Sorted node IDs

    // Parallel arrays for nodes. The index into these arrays is the "node ID".
    public int[] nodeType;
    public int[] nodeStart;
    public int[] nodeEnd;
    public int[] nodeParent;
    public int[] nodeChild;    // points to the first child ID
    public int[] nodeSibling;  // points to the next sibling ID
    public int[] nodeLastChild;// points to the last child ID (internal use for fast O(1) appends)
    public String[] nodeName;  // e.g., variable/function name
    public String[] nodeTypeAnn; // e.g., type annotation like "User"
    public int[] nodeExtra;    // extra data, e.g., flags (isAsync, isExported)

    public final Map<Integer, String[]> shapeTable = new HashMap<>();

    /**
     * Number of active nodes currently in the tree.
     */
    public int nodeCount;

    /**
     * Constructs a JsSyntaxTree with a pre-allocated capacity to avoid re-allocations
     * during normal typing.
     *
     * @param initialCapacity typically 4096 or larger depending on file size
     */
    public JsSyntaxTree(int initialCapacity) {
        nodeType = new int[initialCapacity];
        nodeStart = new int[initialCapacity];
        nodeEnd = new int[initialCapacity];
        nodeParent = new int[initialCapacity];
        nodeChild = new int[initialCapacity];
        nodeSibling = new int[initialCapacity];
        nodeLastChild = new int[initialCapacity];
        nodeName = new String[initialCapacity];
        nodeTypeAnn = new String[initialCapacity];
        nodeExtra = new int[initialCapacity];
        
        // Node 0 is reserved as "null/root"
        nodeCount = 1; 
    }

    /**
     * Resets the tree for re-use without re-allocating the arrays.
     * Clears the previous String references in nodeName to avoid soft-leaks
     * across rapid re-parses, and zeros nodeExtra so flags do not bleed
     * between generations.
     */
    public void buildNodesByOffset() {
        if (nodesByOffset == null || nodesByOffset.length < nodeCount) {
            nodesByOffset = new int[Math.max(nodeCount, nodeType.length)];
        }
        for (int i = 1; i < nodeCount; i++) {
            nodesByOffset[i] = i;
        }
        sortNodesByOffset(1, nodeCount - 1);
    }

    private void sortNodesByOffset(int low, int high) {
        if (low < high) {
            int pi = partitionNodesByOffset(low, high);
            sortNodesByOffset(low, pi - 1);
            sortNodesByOffset(pi + 1, high);
        }
    }

    private int partitionNodesByOffset(int low, int high) {
        int mid = (low + high) >>> 1;
        int temp = nodesByOffset[mid];
        nodesByOffset[mid] = nodesByOffset[high];
        nodesByOffset[high] = temp;
        
        int pivotId = nodesByOffset[high];
        int pivotStart = nodeStart[pivotId];
        int i = (low - 1);
        for (int j = low; j < high; j++) {
            int currentId = nodesByOffset[j];
            if (nodeStart[currentId] < pivotStart) {
                i++;
                temp = nodesByOffset[i];
                nodesByOffset[i] = nodesByOffset[j];
                nodesByOffset[j] = temp;
            }
        }
        temp = nodesByOffset[i + 1];
        nodesByOffset[i + 1] = nodesByOffset[high];
        nodesByOffset[high] = temp;
        return i + 1;
    }

    /**
     * Resets the tree for re-use without re-allocating the arrays.
     * Clears the previous String references in nodeName to avoid soft-leaks
     * across rapid re-parses, and zeros nodeExtra so flags do not bleed
     * between generations.
     */
    public void reset() {
        reset(0);
    }

    public void reset(int estimatedNodes) {
        if (nodeCount > 1) {
            java.util.Arrays.fill(nodeName, 1, nodeCount, null);
            java.util.Arrays.fill(nodeTypeAnn, 1, nodeCount, null);
            java.util.Arrays.fill(nodeExtra, 1, nodeCount, 0);
        }
        nodeCount = 1;

        if (estimatedNodes > nodeType.length) {
            int newCap = Math.max(nodeType.length * 2, estimatedNodes);
            nodeType = Arrays.copyOf(nodeType, newCap);
            nodeStart = Arrays.copyOf(nodeStart, newCap);
            nodeEnd = Arrays.copyOf(nodeEnd, newCap);
            nodeParent = Arrays.copyOf(nodeParent, newCap);
            nodeChild = Arrays.copyOf(nodeChild, newCap);
            nodeSibling = Arrays.copyOf(nodeSibling, newCap);
            nodeLastChild = Arrays.copyOf(nodeLastChild, newCap);
            nodeName = Arrays.copyOf(nodeName, newCap);
            nodeTypeAnn = Arrays.copyOf(nodeTypeAnn, newCap);
            nodeExtra = Arrays.copyOf(nodeExtra, newCap);
        }
    }

    /**
     * Manually inserts a node and handles array resizing if capacity is exceeded.
     * Returns the assigned node ID.
     */
    public int addNode(int type, int start, int end, int parent, String name) {
        if (nodeCount >= nodeType.length) {
            int newCap = nodeType.length * 2;
            nodeType = Arrays.copyOf(nodeType, newCap);
            nodeStart = Arrays.copyOf(nodeStart, newCap);
            nodeEnd = Arrays.copyOf(nodeEnd, newCap);
            nodeParent = Arrays.copyOf(nodeParent, newCap);
            nodeChild = Arrays.copyOf(nodeChild, newCap);
            nodeSibling = Arrays.copyOf(nodeSibling, newCap);
            nodeLastChild = Arrays.copyOf(nodeLastChild, newCap);
            nodeName = Arrays.copyOf(nodeName, newCap);
            nodeTypeAnn = Arrays.copyOf(nodeTypeAnn, newCap);
            nodeExtra = Arrays.copyOf(nodeExtra, newCap);
        }

        int id = nodeCount++;
        nodeType[id] = type;
        nodeStart[id] = start;
        nodeEnd[id] = end;
        nodeParent[id] = parent;
        nodeChild[id] = 0;   
        nodeSibling[id] = 0; 
        nodeLastChild[id] = 0;
        nodeName[id] = name;
        nodeExtra[id] = 0;

        int prevLast = nodeLastChild[parent];
        if (prevLast == 0) {
            nodeChild[parent] = id;
        } else {
            nodeSibling[prevLast] = id;
        }
        nodeLastChild[parent] = id;

        return id;
    }
}
