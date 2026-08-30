package com.cocode.vcode.ide.core.language.html;

import java.util.Arrays;

/**
 * A flat-array data container representing an HTML Abstract Syntax Tree.
 * It uses Structure-of-Arrays (SoA) instead of objects to avoid GC overhead.
 */
public final class HtmlSyntaxTree {

    public static final int N_NONE      = 0;
    public static final int N_ELEMENT   = 1;
    public static final int N_ATTRIBUTE = 2;
    public static final int N_TEXT      = 3;
    public static final int N_COMMENT   = 4;
    public static final int N_DOCTYPE   = 5;
    public static final int N_ERROR     = 6;

    public int[] nodesByOffset; // Sorted node IDs

    // Parallel arrays for nodes. The index into these arrays is the "node ID".
    public int[] nodeType;
    public int[] nodeStart;
    public int[] nodeEnd;
    public int[] nodeParent;
    public int[] nodeChild;    // points to the first child ID
    public int[] nodeSibling;  // points to the next sibling ID
    public int[] nodeLastChild;// points to the last child ID (internal use for fast O(1) appends)
    public String[] nodeName;  // e.g., tag name or attribute name
    public String[] nodeValue; // e.g., attribute value or text content
    public int[] nodeExtra;    // extra data
    public Object[] nodeReference; // For embedding sub-trees (e.g. JsSyntaxTree)

    /**
     * Number of active nodes currently in the tree.
     */
    public int nodeCount;

    /**
     * Constructs an HtmlSyntaxTree with a pre-allocated capacity to avoid re-allocations
     * during normal typing.
     *
     * @param initialCapacity typically 4096 or larger depending on file size
     */
    public HtmlSyntaxTree(int initialCapacity) {
        nodeType = new int[initialCapacity];
        nodeStart = new int[initialCapacity];
        nodeEnd = new int[initialCapacity];
        nodeParent = new int[initialCapacity];
        nodeChild = new int[initialCapacity];
        nodeSibling = new int[initialCapacity];
        nodeLastChild = new int[initialCapacity];
        nodeName = new String[initialCapacity];
        nodeValue = new String[initialCapacity];
        nodeExtra = new int[initialCapacity];
        nodeReference = new Object[initialCapacity];
        
        // Node 0 is reserved as "null/root"
        nodeCount = 1; 
    }

    /**
     * Builds nodesByOffset for fast binary search.
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
     * Finds the index of the node that corresponds to the given AST node ID.
     * Note: This is an internal helper.
     */
    private int findIndexById(int id) {
        for (int i = 0; i < nodesByOffset.length; i++) {
            if (nodesByOffset[i] == id) return i;
        }
        return -1;
    }

    /**
     * Creates a shallow copy of this tree, shifting the start/end offsets of all nodes
     * that occur after `offset` by `delta`.
     */
    public HtmlSyntaxTree cloneWithShift(int offset, int delta) {
        HtmlSyntaxTree clone = new HtmlSyntaxTree(this.nodeType.length);
        clone.nodeCount = this.nodeCount;
        
        System.arraycopy(this.nodeType, 0, clone.nodeType, 0, this.nodeCount);
        System.arraycopy(this.nodeParent, 0, clone.nodeParent, 0, this.nodeCount);
        System.arraycopy(this.nodeChild, 0, clone.nodeChild, 0, this.nodeCount);
        System.arraycopy(this.nodeSibling, 0, clone.nodeSibling, 0, this.nodeCount);
        System.arraycopy(this.nodeLastChild, 0, clone.nodeLastChild, 0, this.nodeCount);
        System.arraycopy(this.nodeName, 0, clone.nodeName, 0, this.nodeCount);
        System.arraycopy(this.nodeValue, 0, clone.nodeValue, 0, this.nodeCount);
        System.arraycopy(this.nodeExtra, 0, clone.nodeExtra, 0, this.nodeCount);
        System.arraycopy(this.nodeReference, 0, clone.nodeReference, 0, this.nodeCount);
        
        for (int i = 0; i < this.nodeCount; i++) {
            clone.nodeStart[i] = this.nodeStart[i] >= offset ? this.nodeStart[i] + delta : this.nodeStart[i];
            clone.nodeEnd[i] = this.nodeEnd[i] >= offset ? this.nodeEnd[i] + delta : this.nodeEnd[i];
        }
        
        if (this.nodesByOffset != null) {
            clone.nodesByOffset = Arrays.copyOf(this.nodesByOffset, this.nodesByOffset.length);
        }
        
        return clone;
    }

    /**
     * Resets the tree for re-use without re-allocating the arrays.
     */
    public void reset() {
        reset(0);
    }

    public void reset(int estimatedNodes) {
        if (nodeCount > 1) {
            Arrays.fill(nodeName, 1, nodeCount, null);
            Arrays.fill(nodeValue, 1, nodeCount, null);
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
            nodeValue = Arrays.copyOf(nodeValue, newCap);
            nodeExtra = Arrays.copyOf(nodeExtra, newCap);
            nodeReference = Arrays.copyOf(nodeReference, newCap);
        }
    }

    /**
     * Manually inserts a node and handles array resizing if capacity is exceeded.
     * Returns the assigned node ID.
     */
    public int addNode(int type, int start, int end, int parent, String name, String value) {
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
            nodeValue = Arrays.copyOf(nodeValue, newCap);
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
        nodeValue[id] = value;
        nodeExtra[id] = 0;
        nodeReference[id] = null;

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
