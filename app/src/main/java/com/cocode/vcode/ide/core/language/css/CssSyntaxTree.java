package com.cocode.vcode.ide.core.language.css;

import java.util.Arrays;

/**
 * A flat-array data container representing a CSS Abstract Syntax Tree.
 * It uses Structure-of-Arrays (SoA) instead of objects to avoid GC overhead.
 */
public final class CssSyntaxTree {

    public static final int N_NONE        = 0;
    public static final int N_RULE        = 1;
    public static final int N_SELECTOR    = 2;
    public static final int N_DECLARATION = 3;
    public static final int N_AT_RULE     = 4;
    public static final int N_PROPERTY    = 5;
    public static final int N_VALUE       = 6;
    public static final int N_ERROR       = 7;

    public int[] nodesByOffset; // Sorted node IDs

    // Parallel arrays for nodes. The index into these arrays is the "node ID".
    public int[] nodeType;
    public int[] nodeStart;
    public int[] nodeEnd;
    public int[] nodeParent;
    public int[] nodeChild;    // points to the first child ID
    public int[] nodeSibling;  // points to the next sibling ID
    public int[] nodeLastChild;// points to the last child ID (internal use for fast O(1) appends)
    public String[] nodeName;  // e.g., selector name, property name
    public String[] nodeValue; // e.g., property value
    public int[] nodeExtra;    // extra data

    /**
     * Number of active nodes currently in the tree.
     */
    public int nodeCount;

    /**
     * Constructs a CssSyntaxTree with a pre-allocated capacity to avoid re-allocations
     * during normal typing.
     *
     * @param initialCapacity typically 4096 or larger depending on file size
     */
    public CssSyntaxTree(int initialCapacity) {
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

        int prevLast = nodeLastChild[parent];
        if (prevLast == 0) {
            nodeChild[parent] = id;
        } else {
            nodeSibling[prevLast] = id;
        }
        nodeLastChild[parent] = id;

        return id;
    }

    /**
     * Finds the most specific (smallest span) AST node containing the offset.
     * Uses binary search over nodesByOffset to find candidates, then returns
     * the tightest matching node.
     */
    public int findNodeAt(int offset) {
        if (nodesByOffset == null || nodeCount <= 1) return 0;
        int bestId = 0;
        int bestSpan = Integer.MAX_VALUE;

        int low = 1;
        int high = nodeCount - 1;

        // Binary search to find initial insertion point
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int id = nodesByOffset[mid];
            int start = nodeStart[id];

            if (start <= offset) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        // Scan backwards and forwards near the binary search boundary to find the tightest enclosing node
        int searchStart = Math.max(1, high - 64);
        int searchEnd = Math.min(nodeCount - 1, high + 64);

        for (int i = searchStart; i <= searchEnd; i++) {
            int id = nodesByOffset[i];
            int start = nodeStart[id];
            int end = nodeEnd[id];
            if (start <= offset && offset <= end) {
                int span = end - start;
                if (span < bestSpan) {
                    bestSpan = span;
                    bestId = id;
                }
            }
        }

        return bestId;
    }

    /**
     * Returns the enclosing rule node (N_RULE or N_AT_RULE) for the given offset, or 0 if outside.
     */
    public int getEnclosingRule(int offset) {
        int nodeId = findNodeAt(offset);
        while (nodeId != 0) {
            int type = nodeType[nodeId];
            if (type == N_RULE || type == N_AT_RULE) {
                return nodeId;
            }
            nodeId = nodeParent[nodeId];
        }
        return 0;
    }

    /**
     * Returns the enclosing declaration node (N_DECLARATION) for the given offset, or 0 if outside.
     */
    public int getEnclosingDeclaration(int offset) {
        int nodeId = findNodeAt(offset);
        while (nodeId != 0) {
            if (nodeType[nodeId] == N_DECLARATION) {
                return nodeId;
            }
            nodeId = nodeParent[nodeId];
        }
        return 0;
    }

    /**
     * Checks if the rule contains a declaration with the given property name.
     */
    public boolean hasProperty(int ruleNodeId, String propName) {
        if (ruleNodeId <= 0 || propName == null) return false;
        int child = nodeChild[ruleNodeId];
        while (child != 0) {
            if (nodeType[child] == N_DECLARATION) {
                int declChild = nodeChild[child];
                while (declChild != 0) {
                    if (nodeType[declChild] == N_PROPERTY && propName.equalsIgnoreCase(nodeName[declChild])) {
                        return true;
                    }
                    declChild = nodeSibling[declChild];
                }
            }
            child = nodeSibling[child];
        }
        return false;
    }

    /**
     * Retrieves the property value for the given property name inside a rule, or null if not found.
     */
    public String getPropertyValue(int ruleNodeId, String propName) {
        if (ruleNodeId <= 0 || propName == null) return null;
        int child = nodeChild[ruleNodeId];
        while (child != 0) {
            if (nodeType[child] == N_DECLARATION) {
                int declChild = nodeChild[child];
                boolean match = false;
                String val = null;
                while (declChild != 0) {
                    if (nodeType[declChild] == N_PROPERTY && propName.equalsIgnoreCase(nodeName[declChild])) {
                        match = true;
                    } else if (nodeType[declChild] == N_VALUE) {
                        val = nodeValue[declChild];
                    }
                    declChild = nodeSibling[declChild];
                }
                if (match) return val;
            }
            child = nodeSibling[child];
        }
        return null;
    }
}
