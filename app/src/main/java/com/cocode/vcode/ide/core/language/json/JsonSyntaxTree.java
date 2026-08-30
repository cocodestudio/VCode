package com.cocode.vcode.ide.core.language.json;

import java.util.Arrays;

/**
 * A flat-array (Struct of Arrays) data container representing a JSON syntax tree.
 * 
 * Uses parallel arrays instead of Node objects to maintain zero 
 * allocation overhead. Arrays are doubled in size when capacity is reached.
 */
public class JsonSyntaxTree {

    public static final byte N_OBJECT        = 1;
    public static final byte N_ARRAY         = 2;
    public static final byte N_KEY           = 3;
    public static final byte N_VALUE_STRING  = 4;
    public static final byte N_VALUE_NUMBER  = 5;
    public static final byte N_VALUE_BOOL    = 6;
    public static final byte N_VALUE_NULL    = 7;
    public static final byte N_ERROR         = 8;

    public byte[] nodeType;
    public int[] nodeStart;
    public int[] nodeEnd;
    public int[] nodeParent;
    public int[] nodeChild;
    public int[] nodeSibling;
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
        nodeName = new String[initialCapacity];
        
        Arrays.fill(nodeChild, -1);
        Arrays.fill(nodeSibling, -1);
        
        nodeCount = 1; // 0 is reserved as root/null
    }

    public int addNode(byte type, int start, int end, int parent, String name) {
        if (nodeCount >= nodeType.length) {
            int newCap = nodeType.length * 2;
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
            
            nodeName = Arrays.copyOf(nodeName, newCap);
        }

        int index = nodeCount++;
        nodeType[index] = type;
        nodeStart[index] = start;
        nodeEnd[index] = end;
        nodeParent[index] = parent;
        nodeName[index] = name;

        if (parent != 0) {
            int child = nodeChild[parent];
            if (child == -1) {
                nodeChild[parent] = index;
            } else {
                while (nodeSibling[child] != -1) {
                    child = nodeSibling[child];
                }
                nodeSibling[child] = index;
            }
        }

        return index;
    }

    public void buildNodesByOffset() {
        nodesByOffset = new int[nodeCount - 1];
        for (int i = 1; i < nodeCount; i++) {
            nodesByOffset[i - 1] = i;
        }
        
        // Simple insertion sort since mostly ordered by start offset
        for (int i = 1; i < nodesByOffset.length; i++) {
            int key = nodesByOffset[i];
            int j = i - 1;
            while (j >= 0 && nodeStart[nodesByOffset[j]] > nodeStart[key]) {
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
                bestMatch = node;
                // Narrow down to deepest child
                int child = nodeChild[node];
                while (child != -1) {
                    if (offset >= nodeStart[child] && offset <= nodeEnd[child]) {
                        return getDeepestNode(child, offset);
                    }
                    child = nodeSibling[child];
                }
                return bestMatch;
            } else if (nodeStart[node] > offset) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return bestMatch;
    }

    private int getDeepestNode(int node, int offset) {
        int best = node;
        int child = nodeChild[node];
        while (child != -1) {
            if (offset >= nodeStart[child] && offset <= nodeEnd[child]) {
                return getDeepestNode(child, offset);
            }
            child = nodeSibling[child];
        }
        return best;
    }
}
