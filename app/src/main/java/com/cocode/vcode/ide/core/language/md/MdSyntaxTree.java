package com.cocode.vcode.ide.core.language.md;

import java.util.Arrays;

/**
 * A flat-array data container representing a Markdown Syntax Tree.
 * It uses Structure-of-Arrays (SoA) instead of objects to avoid GC overhead.
 */
public final class MdSyntaxTree {

    public static final int N_NONE = 0;
    public static final int MAX_NODES = 100_000;
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
            int t = nodesByOffset[a]; nodesByOffset[a] = nodesByOffset[b]; nodesByOffset[b] = t;
        }
        if (nodeStart[nodesByOffset[b]] > nodeStart[nodesByOffset[c]]) {
            int t = nodesByOffset[b]; nodesByOffset[b] = nodesByOffset[c]; nodesByOffset[c] = t;
            if (nodeStart[nodesByOffset[a]] > nodeStart[nodesByOffset[b]]) {
                int t2 = nodesByOffset[a]; nodesByOffset[a] = nodesByOffset[b]; nodesByOffset[b] = t2;
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

    public void reset() {
        reset(0);
    }

    public void reset(int estimatedNodes) {
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
            Arrays.fill(nodeExtra, 1, Math.min(nodeCount, nodeExtra.length), 0);
            Arrays.fill(nodeReference, 1, Math.min(nodeCount, nodeReference.length), null);
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
            nodeExtra = new int[resetCap];
            nodeReference = new String[resetCap];
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
            nodeExtra = Arrays.copyOf(nodeExtra, newCap);
            nodeReference = Arrays.copyOf(nodeReference, newCap);
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

    public int findNodeAt(int offset) {
        if (nodesByOffset == null || nodeCount <= 1) return 0;
        int low = 1, high = nodeCount - 1;
        int best = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int node = nodesByOffset[mid];
            if (nodeStart[node] <= offset && offset <= nodeEnd[node]) {
                best = node;
                int child = nodeChild[node];
                int childLoop = 0;
                while (child > 0 && child < nodeCount && ++childLoop <= nodeCount) {
                    if (nodeStart[child] <= offset && offset <= nodeEnd[child]) {
                        return getDeepestChild(child, offset, 0);
                    }
                    child = nodeSibling[child];
                }
                return best;
            } else if (nodeStart[node] > offset) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return best;
    }

    public int getEnclosingBlock(int offset, int blockType) {
        int node = findNodeAt(offset);
        int parentLoop = 0;
        while (node > 0 && node < nodeCount && ++parentLoop <= nodeCount) {
            if (nodeType[node] == blockType) return node;
            node = nodeParent[node];
        }
        return 0;
    }

    private int getDeepestChild(int node, int offset) {
        return getDeepestChild(node, offset, 0);
    }

    private int getDeepestChild(int node, int offset, int depth) {
        if (depth > 50 || node <= 0 || node >= nodeCount) return node;
        int best = node;
        int child = nodeChild[node];
        int childLoop = 0;
        while (child > 0 && child < nodeCount && ++childLoop <= nodeCount) {
            if (nodeStart[child] <= offset && offset <= nodeEnd[child]) {
                return getDeepestChild(child, offset, depth + 1);
            }
            child = nodeSibling[child];
        }
        return best;
    }
}
