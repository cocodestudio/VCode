package com.cocode.vcode.ide.core.language.md;

import java.util.Arrays;

/**
 * A flat-array data container representing a Markdown Syntax Tree.
 * It uses Structure-of-Arrays (SoA) instead of objects to avoid GC overhead.
 */
public final class MdSyntaxTree {

    public static final int N_NONE = 0;
    public static final int N_HEADER = 1;
    public static final int N_PARAGRAPH = 2;
    public static final int N_LIST = 3;
    public static final int N_LIST_ITEM = 4;
    public static final int N_CODE_BLOCK = 5;
    public static final int N_BLOCKQUOTE = 6;
    public static final int N_THEMATIC_BREAK = 7;
    public static final int N_INLINE_CONTAINER = 8;
    public static final int N_ERROR = 9;

    public int[] nodesByOffset; // Sorted node IDs

    // Parallel arrays for nodes. The index into these arrays is the "node ID".
    public int[] nodeType;
    public int[] nodeStart;
    public int[] nodeEnd;
    public int[] nodeParent;
    public int[] nodeChild;    // points to the first child ID
    public int[] nodeSibling;  // points to the next sibling ID
    public int[] nodeLastChild;// points to the last child ID (internal use for fast O(1) appends)
    public String[] nodeName;  // e.g., code block language tag
    public int[] nodeExtra;    // extra data, e.g., header depth
    public Object[] nodeReference; // For embedding sub-trees (e.g. ParseResult)

    /**
     * Number of active nodes currently in the tree.
     */
    public int nodeCount;

    /**
     * Constructs a MdSyntaxTree with a pre-allocated capacity to avoid re-allocations.
     *
     * @param initialCapacity typically 512 or larger depending on file size
     */
    public MdSyntaxTree(int initialCapacity) {
        nodeType = new int[initialCapacity];
        nodeStart = new int[initialCapacity];
        nodeEnd = new int[initialCapacity];
        nodeParent = new int[initialCapacity];
        nodeChild = new int[initialCapacity];
        nodeSibling = new int[initialCapacity];
        nodeLastChild = new int[initialCapacity];
        nodeName = new String[initialCapacity];
        nodeExtra = new int[initialCapacity];
        nodeReference = new Object[initialCapacity];
        
        // Node 0 is reserved as "null/root"
        nodeCount = 1; 
    }

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

    public void reset() {
        reset(0);
    }

    public void reset(int estimatedNodes) {
        if (nodeCount > 1) {
            Arrays.fill(nodeName, 1, nodeCount, null);
            Arrays.fill(nodeExtra, 1, nodeCount, 0);
            Arrays.fill(nodeReference, 1, nodeCount, null);
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
            nodeExtra = Arrays.copyOf(nodeExtra, newCap);
            nodeReference = Arrays.copyOf(nodeReference, newCap);
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
            nodeExtra = Arrays.copyOf(nodeExtra, newCap);
            nodeReference = Arrays.copyOf(nodeReference, newCap);
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
