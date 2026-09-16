package com.cocode.vcode.ide.core.language.js;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A flat-array data container representing a JavaScript/TypeScript Abstract Syntax Tree.
 * It uses Structure-of-Arrays (SoA) instead of objects to avoid GC overhead.
 */
public final class JsSyntaxTree {

    public static final int N_NONE = 0;
    public static final int N_IMPORT = 1;
    public static final int MAX_NODES = 100_000;

    public static final int N_FUNC_DECL = 2;
    public static final int N_ARROW_FUNC = 3;
    public static final int N_CLASS_DECL = 4;
    public static final int N_METHOD = 5;
    public static final int N_GETTER = 6;
    public static final int N_SETTER = 7;
    public static final int N_VAR_DECL = 8;   // let/const/var
    public static final int N_PARAM = 9;
    public static final int N_BLOCK = 10;
    public static final int N_EXPORT = 11;
    public static final int N_CALL_EXPR = 12;
    public static final int N_MEMBER_EXPR = 13;  // a.b
    public static final int N_IDENTIFIER = 14;
    public static final int N_DESTRUCTURE = 15;
    public static final int N_STATEMENT = 16;  // generic statement
    public static final int N_FOR_STMT = 17;
    public static final int N_CATCH_CLAUSE = 18;
    public static final int N_IF_STMT = 19;
    public static final int N_WHILE_STMT = 20;
    public static final int N_DO_STMT = 21;
    public static final int N_WITH_STMT = 22;
    public static final int N_SWITCH_STMT = 23;
    public static final int N_CASE_CLAUSE = 24;
    public static final int N_TRY_STMT = 25;
    public static final int N_FINALLY_CLAUSE = 26;
    public static final int N_ERROR = 27;
    public static final int N_INTERFACE = 28;
    public static final int N_TYPE_ALIAS = 29;
    public static final int N_ENUM = 30;
    public static final int N_OBJECT_LITERAL = 31;
    public static final int N_PROPERTY = 32;

    public static final int FLAG_NONE = 0;
    public static final int FLAG_VAR = 1;
    public static final int FLAG_LET = 2;
    public static final int FLAG_CONST = 3;
    public static final int FLAG_REST = 4;
    public static final int FLAG_PARAM_PROP = 8;
    public static final int FLAG_DEFAULT = 16;
    public final Map<Integer, String[]> shapeTable = new HashMap<>();
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
        if (nodeCount > 2) {
            sortNodesByOffsetIterative(1, nodeCount - 1);
        }
    }

    private void sortNodesByOffsetIterative(int initialLow, int initialHigh) {
        int[] stack = new int[128];
        int top = 0;
        stack[top++] = initialLow;
        stack[top++] = initialHigh;

        while (top > 0) {
            int high = stack[--top];
            int low = stack[--top];

            while (low < high) {
                int len = high - low + 1;
                if (len <= 16) {
                    insertionSortNodesByOffset(low, high);
                    break;
                }

                int mid = (low + high) >>> 1;
                medianOfThree(low, mid, high);

                int pivotId = nodesByOffset[mid];
                int pivotStart = nodeStart[pivotId];

                int lt = low;
                int gt = high;
                int i = low;

                while (i <= gt) {
                    int currId = nodesByOffset[i];
                    int currStart = nodeStart[currId];
                    if (currStart < pivotStart) {
                        int temp = nodesByOffset[lt];
                        nodesByOffset[lt] = nodesByOffset[i];
                        nodesByOffset[i] = temp;
                        lt++;
                        i++;
                    } else if (currStart > pivotStart) {
                        int temp = nodesByOffset[i];
                        nodesByOffset[i] = nodesByOffset[gt];
                        nodesByOffset[gt] = temp;
                        gt--;
                    } else {
                        i++;
                    }
                }

                int leftLen = lt - low;
                int rightLen = high - gt;

                if (top + 2 >= stack.length) {
                    stack = Arrays.copyOf(stack, stack.length * 2);
                }

                if (leftLen > rightLen) {
                    if (leftLen > 1) {
                        stack[top++] = low;
                        stack[top++] = lt - 1;
                    }
                    low = gt + 1;
                } else {
                    if (rightLen > 1) {
                        stack[top++] = gt + 1;
                        stack[top++] = high;
                    }
                    high = lt - 1;
                }
            }
        }
    }

    private void medianOfThree(int a, int b, int c) {
        if (nodeStart[nodesByOffset[a]] > nodeStart[nodesByOffset[b]]) {
            int t = nodesByOffset[a];
            nodesByOffset[a] = nodesByOffset[b];
            nodesByOffset[b] = t;
        }
        if (nodeStart[nodesByOffset[b]] > nodeStart[nodesByOffset[c]]) {
            int t = nodesByOffset[b];
            nodesByOffset[b] = nodesByOffset[c];
            nodesByOffset[c] = t;
            if (nodeStart[nodesByOffset[a]] > nodeStart[nodesByOffset[b]]) {
                int t2 = nodesByOffset[a];
                nodesByOffset[a] = nodesByOffset[b];
                nodesByOffset[b] = t2;
            }
        }
    }

    private void insertionSortNodesByOffset(int low, int high) {
        for (int i = low + 1; i <= high; i++) {
            int key = nodesByOffset[i];
            int keyStart = nodeStart[key];
            int j = i - 1;
            while (j >= low && nodeStart[nodesByOffset[j]] > keyStart) {
                nodesByOffset[j + 1] = nodesByOffset[j];
                j--;
            }
            nodesByOffset[j + 1] = key;
        }
    }

    /**
     * Resets the tree for re-use without re-allocating the arrays.
     * Clears all node state so no stale parent/child/sibling links or names
     * bleed across rapid re-parses.
     */
    public void reset() {
        reset(0);
    }

    public void reset(int estimatedNodes) {
        shapeTable.clear();
        int clearLimit = Math.min(nodeCount, nodeType.length);
        if (clearLimit > 1) {
            Arrays.fill(nodeType, 1, clearLimit, 0);
            Arrays.fill(nodeStart, 1, clearLimit, 0);
            Arrays.fill(nodeEnd, 1, clearLimit, 0);
            Arrays.fill(nodeParent, 1, clearLimit, 0);
            Arrays.fill(nodeChild, 1, clearLimit, 0);
            Arrays.fill(nodeSibling, 1, clearLimit, 0);
            Arrays.fill(nodeLastChild, 1, clearLimit, 0);
            Arrays.fill(nodeName, 1, Math.min(nodeCount, nodeName.length), null);
            Arrays.fill(nodeTypeAnn, 1, Math.min(nodeCount, nodeTypeAnn.length), null);
            Arrays.fill(nodeExtra, 1, Math.min(nodeCount, nodeExtra.length), 0);
        }
        // Always reset root node 0 pointers
        nodeChild[0] = 0;
        nodeLastChild[0] = 0;
        nodeSibling[0] = 0;
        nodeParent[0] = 0;
        nodeStart[0] = 0;
        nodeEnd[0] = 0;
        nodeType[0] = 0;
        nodeCount = 1;

        if (nodeType.length > 32768) {
            int resetCap = Math.max(4096, Math.min(estimatedNodes, 32768));
            nodeType = new int[resetCap];
            nodeStart = new int[resetCap];
            nodeEnd = new int[resetCap];
            nodeParent = new int[resetCap];
            nodeChild = new int[resetCap];
            nodeSibling = new int[resetCap];
            nodeLastChild = new int[resetCap];
            nodeName = new String[resetCap];
            nodeTypeAnn = new String[resetCap];
            nodeExtra = new int[resetCap];
            nodesByOffset = null;
            return;
        }

        if (estimatedNodes > nodeType.length && estimatedNodes <= MAX_NODES) {
            int newCap = Math.min(MAX_NODES, Math.max(nodeType.length * 2, estimatedNodes));
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
        if (nodeCount >= MAX_NODES) {
            return 0;
        }
        if (nodeCount >= nodeType.length) {
            int newCap = Math.min(MAX_NODES, nodeType.length * 2);
            if (newCap <= nodeType.length) {
                return 0;
            }
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

        if (parent < 0 || parent >= id) {
            parent = 0;
        }

        int prevLast = nodeLastChild[parent];
        if (prevLast <= 0 || prevLast >= id) {
            nodeChild[parent] = id;
        } else {
            nodeSibling[prevLast] = id;
        }
        nodeLastChild[parent] = id;

        return id;
    }
}
