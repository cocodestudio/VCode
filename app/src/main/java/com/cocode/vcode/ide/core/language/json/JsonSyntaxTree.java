package com.cocode.vcode.ide.core.language.json;

import java.util.Arrays;

/**
 * A flat-array (Struct of Arrays) data container representing a JSON syntax tree.
 * <p>
 * Uses parallel arrays instead of Node objects to maintain zero
 * allocation overhead. Arrays are doubled in size when capacity is reached.
 */
public class JsonSyntaxTree {

    public static final byte N_OBJECT = 1;
    public static final byte N_ARRAY = 2;
    public static final byte N_KEY = 3;
    public static final byte N_VALUE_STRING = 4;
    public static final byte N_VALUE_NUMBER = 5;
    public static final byte N_VALUE_BOOL = 6;
    public static final byte N_VALUE_NULL = 7;
    public static final byte N_ERROR = 8;
    public static final int MAX_NODES = 100_000;
    public byte[] nodeType;
    public int[] nodeStart;
    public int[] nodeEnd;
    public int[] nodeParent;
    public int[] nodeChild;
    public int[] nodeSibling;
    public int[] nodeLastChild;
    public String[] nodeName;
    public int nodeCount;
    // Cache for quick offset lookup
    private int[] nodesByOffset;

    public JsonSyntaxTree(int initialCapacity) {
        nodeType = new byte[initialCapacity];
        nodeStart = new int[initialCapacity];
        nodeEnd = new int[initialCapacity];
        nodeParent = new int[initialCapacity];
        nodeChild = new int[initialCapacity];
        nodeSibling = new int[initialCapacity];
        nodeLastChild = new int[initialCapacity];
        nodeName = new String[initialCapacity];

        Arrays.fill(nodeChild, -1);
        Arrays.fill(nodeSibling, -1);
        Arrays.fill(nodeLastChild, -1);

        nodeCount = 1; // 0 is reserved as root/null
    }

    public int addNode(byte type, int start, int end, int parent, String name) {
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

            int[] newChild = new int[newCap];
            System.arraycopy(nodeChild, 0, newChild, 0, nodeChild.length);
            Arrays.fill(newChild, nodeChild.length, newCap, -1);
            nodeChild = newChild;

            int[] newSibling = new int[newCap];
            System.arraycopy(nodeSibling, 0, newSibling, 0, nodeSibling.length);
            Arrays.fill(newSibling, nodeSibling.length, newCap, -1);
            nodeSibling = newSibling;

            int[] newLastChild = new int[newCap];
            System.arraycopy(nodeLastChild, 0, newLastChild, 0, nodeLastChild.length);
            Arrays.fill(newLastChild, nodeLastChild.length, newCap, -1);
            nodeLastChild = newLastChild;

            nodeName = Arrays.copyOf(nodeName, newCap);
        }

        int index = nodeCount++;
        nodeType[index] = type;
        nodeStart[index] = start;
        nodeEnd[index] = end;
        nodeParent[index] = parent;
        nodeChild[index] = -1;
        nodeSibling[index] = -1;
        nodeLastChild[index] = -1;
        nodeName[index] = name;

        if (parent < 0 || parent >= index) {
            parent = 0;
            nodeParent[index] = 0;
        }

        if (parent != 0) {
            int prevLast = nodeLastChild[parent];
            if (prevLast <= 0 || prevLast >= index) {
                nodeChild[parent] = index;
            } else {
                nodeSibling[prevLast] = index;
            }
            nodeLastChild[parent] = index;
        }

        return index;
    }

    public void buildNodesByOffset() {
        if (nodeCount <= 1) {
            nodesByOffset = new int[0];
            return;
        }
        nodesByOffset = new int[nodeCount - 1];
        for (int i = 1; i < nodeCount; i++) {
            nodesByOffset[i - 1] = i;
        }
        if (nodesByOffset.length > 1) {
            sortNodesByOffsetIterative(0, nodesByOffset.length - 1);
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

    public int getNodeAtOffset(int offset) {
        if (nodesByOffset == null || nodesByOffset.length == 0) return 0;

        int low = 0;
        int high = nodesByOffset.length - 1;
        int bestMatch = 0;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int node = nodesByOffset[mid];

            if (offset >= nodeStart[node] && offset <= nodeEnd[node]) {
                return getDeepestNode(node, offset);
            } else if (nodeStart[node] > offset) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return bestMatch;
    }

    public int findNodeAt(int offset) {
        return getNodeAtOffset(offset);
    }

    public int getEnclosingObject(int offset) {
        int node = findNodeAt(offset);
        while (node > 0) {
            if (nodeType[node] == N_OBJECT) return node;
            node = nodeParent[node];
        }
        return 0;
    }

    public int getEnclosingArray(int offset) {
        int node = findNodeAt(offset);
        while (node > 0) {
            if (nodeType[node] == N_ARRAY) return node;
            node = nodeParent[node];
        }
        return 0;
    }

    public String getKeyName(int nodeId) {
        if (nodeId <= 0 || nodeId >= nodeCount) return null;
        if (nodeType[nodeId] == N_KEY) return nodeName[nodeId];
        return null;
    }

    private int getDeepestNode(int node, int offset) {
        if (node <= 0 || node >= nodeCount) return 0;
        int current = node;
        int depthLimit = 0;
        while (current > 0 && current < nodeCount && ++depthLimit <= nodeCount) {
            int child = nodeChild[current];
            int nextChild = 0;
            int visited = 0;
            while (child > 0 && child < nodeCount && ++visited <= nodeCount) {
                if (offset >= nodeStart[child] && offset <= nodeEnd[child]) {
                    nextChild = child;
                    break;
                }
                child = nodeSibling[child];
            }
            if (nextChild > 0) {
                current = nextChild;
            } else {
                break;
            }
        }
        return current;
    }
}
