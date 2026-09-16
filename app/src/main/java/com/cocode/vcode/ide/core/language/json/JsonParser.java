package com.cocode.vcode.ide.core.language.json;

/**
 * Parses a flat JsonTokenStream into a JsonSyntaxTree.
 * Implements a zero-allocation, iterative, fault-tolerant state machine.
 */
public class JsonParser {

    public static JsonSyntaxTree parse(JsonTokenStream stream, String source) {
        JsonSyntaxTree tree = new JsonSyntaxTree(stream.length / 4 + 10);
        int root = 0;

        int p = 0;
        int currentParent = root;

        int[] parentStack = new int[256];
        int depth = 0;

        boolean expectColon = false;
        boolean lastWasComma = false;

        while (p < stream.length) {
            byte type = stream.types[p];

            if (type == JsonTokenStream.TK_WHITESPACE || type == JsonTokenStream.TK_COMMENT) {
                p++;
                continue;
            }

            int start = stream.tokenStart[p];
            int end = getTokenEnd(stream, start);
            p = Math.max(p + 1, end); // advance outer pointer with forward progress guarantee

            if (type == JsonTokenStream.TK_ERROR) {
                tree.addNode(JsonSyntaxTree.N_ERROR, start, end, currentParent, "Unrecognized syntax");
                if (currentParent != root && tree.nodeType[currentParent] == JsonSyntaxTree.N_KEY && !expectColon) {
                    tree.nodeEnd[currentParent] = end;
                    if (depth > 0) currentParent = parentStack[--depth];
                    else currentParent = root;
                }
                lastWasComma = false;
                continue;
            }

            byte parentType = currentParent == root ? 0 : tree.nodeType[currentParent];

            if (parentType == JsonSyntaxTree.N_OBJECT) {
                if (type == JsonTokenStream.TK_BRACE_CLOSE) {
                    if (lastWasComma) {
                        tree.addNode(JsonSyntaxTree.N_ERROR, start, end, currentParent, "Trailing comma");
                    }
                    tree.nodeEnd[currentParent] = end;

                    if (depth > 0) currentParent = parentStack[--depth];
                    else currentParent = root;

                    if (currentParent != root && tree.nodeType[currentParent] == JsonSyntaxTree.N_KEY) {
                        tree.nodeEnd[currentParent] = end;
                        if (depth > 0) currentParent = parentStack[--depth];
                        else currentParent = root;
                    }
                    lastWasComma = false;
                    continue;
                } else if (type == JsonTokenStream.TK_COMMA) {
                    lastWasComma = true;
                    continue;
                } else if (type == JsonTokenStream.TK_STRING) {
                    checkMissingQuote(tree, start, end, currentParent, source);
                    String keyName = source.substring(start, end).trim();
                    int keyNode = tree.addNode(JsonSyntaxTree.N_KEY, start, -1, currentParent, keyName);

                    if (depth >= parentStack.length) {
                        int[] newStack = new int[parentStack.length * 2];
                        System.arraycopy(parentStack, 0, newStack, 0, parentStack.length);
                        parentStack = newStack;
                    }
                    parentStack[depth++] = currentParent;
                    currentParent = keyNode;
                    expectColon = true;
                    lastWasComma = false;
                    continue;
                } else {
                    tree.addNode(JsonSyntaxTree.N_ERROR, start, end, currentParent, "Expected key or '}'");
                    lastWasComma = false;
                    continue;
                }
            } else if (parentType == JsonSyntaxTree.N_KEY) {
                if (expectColon) {
                    if (type == JsonTokenStream.TK_COLON) {
                        expectColon = false;
                        lastWasComma = false;
                        continue;
                    } else if (type == JsonTokenStream.TK_STRING || type == JsonTokenStream.TK_NUMBER ||
                            type == JsonTokenStream.TK_BRACE_OPEN || type == JsonTokenStream.TK_BRACKET_OPEN ||
                            type == JsonTokenStream.TK_KEYWORD) {
                        tree.addNode(JsonSyntaxTree.N_ERROR, start, start, currentParent, "Missing colon");
                        expectColon = false;
                        // DO NOT continue, process this token as value
                    } else {
                        tree.addNode(JsonSyntaxTree.N_ERROR, start, end, currentParent, "Expected colon");
                        lastWasComma = false;
                        continue;
                    }
                }

                if (type == JsonTokenStream.TK_STRING) {
                    checkMissingQuote(tree, start, end, currentParent, source);
                    tree.addNode(JsonSyntaxTree.N_VALUE_STRING, start, end, currentParent, source.substring(start, end).trim());
                    tree.nodeEnd[currentParent] = end;
                    if (depth > 0) currentParent = parentStack[--depth];
                    else currentParent = root;
                } else if (type == JsonTokenStream.TK_NUMBER) {
                    tree.addNode(JsonSyntaxTree.N_VALUE_NUMBER, start, end, currentParent, source.substring(start, end).trim());
                    tree.nodeEnd[currentParent] = end;
                    if (depth > 0) currentParent = parentStack[--depth];
                    else currentParent = root;
                } else if (type == JsonTokenStream.TK_KEYWORD) {
                    String kw = source.substring(start, end).trim();
                    byte kwType = kw.equals("true") || kw.equals("false") ? JsonSyntaxTree.N_VALUE_BOOL : JsonSyntaxTree.N_VALUE_NULL;
                    tree.addNode(kwType, start, end, currentParent, kw);
                    tree.nodeEnd[currentParent] = end;
                    if (depth > 0) currentParent = parentStack[--depth];
                    else currentParent = root;
                } else if (type == JsonTokenStream.TK_BRACE_OPEN) {
                    int obj = tree.addNode(JsonSyntaxTree.N_OBJECT, start, -1, currentParent, null);
                    if (depth >= parentStack.length) {
                        int[] newStack = new int[parentStack.length * 2];
                        System.arraycopy(parentStack, 0, newStack, 0, parentStack.length);
                        parentStack = newStack;
                    }
                    parentStack[depth++] = currentParent;
                    currentParent = obj;
                } else if (type == JsonTokenStream.TK_BRACKET_OPEN) {
                    int arr = tree.addNode(JsonSyntaxTree.N_ARRAY, start, -1, currentParent, null);
                    if (depth >= parentStack.length) {
                        int[] newStack = new int[parentStack.length * 2];
                        System.arraycopy(parentStack, 0, newStack, 0, parentStack.length);
                        parentStack = newStack;
                    }
                    parentStack[depth++] = currentParent;
                    currentParent = arr;
                } else {
                    tree.addNode(JsonSyntaxTree.N_ERROR, start, end, currentParent, "Expected value");
                    tree.nodeEnd[currentParent] = end;
                    if (depth > 0) currentParent = parentStack[--depth];
                    else currentParent = root;
                }
                lastWasComma = false;
            } else { // Array or Root
                if (type == JsonTokenStream.TK_BRACKET_CLOSE) {
                    if (parentType == JsonSyntaxTree.N_ARRAY) {
                        if (lastWasComma) {
                            tree.addNode(JsonSyntaxTree.N_ERROR, start, end, currentParent, "Trailing comma");
                        }
                        tree.nodeEnd[currentParent] = end;
                        if (depth > 0) currentParent = parentStack[--depth];
                        else currentParent = root;

                        if (currentParent != root && tree.nodeType[currentParent] == JsonSyntaxTree.N_KEY) {
                            tree.nodeEnd[currentParent] = end;
                            if (depth > 0) currentParent = parentStack[--depth];
                            else currentParent = root;
                        }
                    } else {
                        tree.addNode(JsonSyntaxTree.N_ERROR, start, end, currentParent, "Stray ']'");
                    }
                    lastWasComma = false;
                    continue;
                } else if (type == JsonTokenStream.TK_COMMA) {
                    lastWasComma = true;
                    continue;
                }

                if (type == JsonTokenStream.TK_STRING) {
                    checkMissingQuote(tree, start, end, currentParent, source);
                    tree.addNode(JsonSyntaxTree.N_VALUE_STRING, start, end, currentParent, source.substring(start, end).trim());
                    if (currentParent != root && tree.nodeType[currentParent] == JsonSyntaxTree.N_KEY) {
                        tree.nodeEnd[currentParent] = end;
                        if (depth > 0) currentParent = parentStack[--depth];
                        else currentParent = root;
                    }
                } else if (type == JsonTokenStream.TK_NUMBER) {
                    tree.addNode(JsonSyntaxTree.N_VALUE_NUMBER, start, end, currentParent, source.substring(start, end).trim());
                    if (currentParent != root && tree.nodeType[currentParent] == JsonSyntaxTree.N_KEY) {
                        tree.nodeEnd[currentParent] = end;
                        if (depth > 0) currentParent = parentStack[--depth];
                        else currentParent = root;
                    }
                } else if (type == JsonTokenStream.TK_KEYWORD) {
                    String kw = source.substring(start, end).trim();
                    byte kwType = kw.equals("true") || kw.equals("false") ? JsonSyntaxTree.N_VALUE_BOOL : JsonSyntaxTree.N_VALUE_NULL;
                    tree.addNode(kwType, start, end, currentParent, kw);
                    if (currentParent != root && tree.nodeType[currentParent] == JsonSyntaxTree.N_KEY) {
                        tree.nodeEnd[currentParent] = end;
                        if (depth > 0) currentParent = parentStack[--depth];
                        else currentParent = root;
                    }
                } else if (type == JsonTokenStream.TK_BRACE_OPEN) {
                    int obj = tree.addNode(JsonSyntaxTree.N_OBJECT, start, -1, currentParent, null);
                    if (depth >= parentStack.length) {
                        int[] newStack = new int[parentStack.length * 2];
                        System.arraycopy(parentStack, 0, newStack, 0, parentStack.length);
                        parentStack = newStack;
                    }
                    parentStack[depth++] = currentParent;
                    currentParent = obj;
                } else if (type == JsonTokenStream.TK_BRACKET_OPEN) {
                    int arr = tree.addNode(JsonSyntaxTree.N_ARRAY, start, -1, currentParent, null);
                    if (depth >= parentStack.length) {
                        int[] newStack = new int[parentStack.length * 2];
                        System.arraycopy(parentStack, 0, newStack, 0, parentStack.length);
                        parentStack = newStack;
                    }
                    parentStack[depth++] = currentParent;
                    currentParent = arr;
                } else {
                    tree.addNode(JsonSyntaxTree.N_ERROR, start, end, currentParent, "Unexpected token");
                }
                lastWasComma = false;
            }
        }

        while (depth > 0) {
            tree.nodeEnd[currentParent] = stream.length;
            currentParent = parentStack[--depth];
        }

        tree.buildNodesByOffset();
        return tree;
    }

    private static int getTokenEnd(JsonTokenStream stream, int start) {
        int i = start;
        while (i < stream.length && stream.tokenStart[i] == start) {
            i++;
        }
        return Math.max(i, start + 1);
    }

    private static void checkMissingQuote(JsonSyntaxTree tree, int start, int end, int parent, String source) {
        if (end - start == 1 || source.charAt(end - 1) != '"') {
            tree.addNode(JsonSyntaxTree.N_ERROR, start, end, parent, "Missing closing quote");
        }
    }
}
