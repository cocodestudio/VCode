package com.cocode.vcode.ide.core.language.css;

/**
 * Fast, single-pass zero-allocation parser for Cascading Style Sheets (CSS).
 * Converts a {@link CssTokenStream} into a compact {@link CssSyntaxTree} indexing selectors,
 * declaration blocks, property-value pairs, and at-rules (e.g. {@code @media}, {@code @supports},
 * {@code @keyframes}). Incorporates punctuation-based synchronization recovery to remain stable
 * and responsive during incomplete typing states.
 */
public class CssParser {

    /**
     * Parses a CSS Token Stream into a CssSyntaxTree.
     * Implements a highly resilient, single-pass zero-allocation parser.
     * Uses sync-to-punct recovery for malformed statements.
     */
    public static CssSyntaxTree parse(CssTokenStream stream, String source) {
        CssSyntaxTree tree = new CssSyntaxTree(stream.length / 4 + 10);
        int root = 0; // Node 0 is reserved

        int p = 0;
        int currentParent = root;
        int[] parentStack = new int[256];
        int depth = 0;

        while (p < stream.length) {
            byte type = stream.types[p];

            if (type == CssTokenStream.TK_NONE || type == CssTokenStream.TK_COMMENT) {
                p++;
                continue;
            }

            if (type == CssTokenStream.TK_SELECTOR) {
                int startP = p;
                int endP = getEndOffset(stream, p, CssTokenStream.TK_SELECTOR);
                String selectorText = source.substring(startP, endP).trim();
                boolean isAtRule = selectorText.startsWith("@");

                int nextP = skipWhitespaceAndComments(stream, endP);
                if (nextP < stream.length && stream.types[nextP] == CssTokenStream.TK_PUNCT) {
                    char punct = source.charAt(nextP);
                    if (punct == '{') {
                        int nodeType = isAtRule ? CssSyntaxTree.N_AT_RULE : CssSyntaxTree.N_RULE;
                        int ruleNode = tree.addNode(nodeType, startP, nextP + 1, currentParent, null, null);
                        tree.addNode(CssSyntaxTree.N_SELECTOR, startP, endP, ruleNode, selectorText, null);

                        if (depth >= parentStack.length) {
                            int[] newStack = new int[parentStack.length * 2];
                            System.arraycopy(parentStack, 0, newStack, 0, parentStack.length);
                            parentStack = newStack;
                        }
                        parentStack[depth++] = currentParent;
                        currentParent = ruleNode;
                        p = nextP + 1;
                    } else if (punct == ';') {
                        if (isAtRule || currentParent == root) {
                            int nodeType = isAtRule ? CssSyntaxTree.N_AT_RULE : CssSyntaxTree.N_RULE;
                            int ruleNode = tree.addNode(nodeType, startP, nextP + 1, currentParent, null, null);
                            tree.addNode(CssSyntaxTree.N_SELECTOR, startP, endP, ruleNode, selectorText, null);
                        } else {
                            // Malformed declaration inside a rule (e.g. "color red;")
                            tree.addNode(CssSyntaxTree.N_ERROR, startP, nextP + 1, currentParent, "Missing ':' in CSS declaration — property and value must be separated by ':'", null);
                        }
                        p = nextP + 1;
                    } else if (punct == '}') {
                        // Malformed: selector directly followed by }
                        tree.addNode(CssSyntaxTree.N_ERROR, startP, nextP, currentParent, "Unexpected '}' after selector", null);
                        p = nextP; // do not consume }, let loop handle it
                    } else {
                        // unexpected punct (e.g. stray colon from lexer fallback)
                        tree.addNode(CssSyntaxTree.N_ERROR, startP, nextP + 1, currentParent, "Unexpected punctuation", null);
                        p = nextP + 1;
                    }
                } else {
                    // EOF
                    int nodeType = isAtRule ? CssSyntaxTree.N_AT_RULE : CssSyntaxTree.N_RULE;
                    int ruleNode = tree.addNode(nodeType, startP, endP, currentParent, null, null);
                    tree.addNode(CssSyntaxTree.N_SELECTOR, startP, endP, ruleNode, selectorText, null);
                    p = nextP;
                }
            } else if (type == CssTokenStream.TK_PROPERTY) {
                p = parseProperty(tree, stream, source, p, currentParent);

            } else if (type == CssTokenStream.TK_PUNCT) {
                char punct = source.charAt(p);
                if (punct == '}') {
                    if (currentParent != root) {
                        tree.nodeEnd[currentParent] = p + 1;
                        if (depth > 0) {
                            depth--;
                            currentParent = parentStack[depth];
                        } else {
                            currentParent = root;
                        }
                    } else {
                        tree.addNode(CssSyntaxTree.N_ERROR, p, p + 1, currentParent, "Unexpected '}' with no matching '{'", null);
                    }
                    p++;
                } else {
                    tree.addNode(CssSyntaxTree.N_ERROR, p, p + 1, currentParent, "Unexpected punctuation", null);
                    p++;
                }
            } else {
                p++;
            }
        }

        while (currentParent != root) {
            tree.nodeEnd[currentParent] = stream.length;
            tree.addNode(CssSyntaxTree.N_ERROR, tree.nodeStart[currentParent], Math.min(tree.nodeStart[currentParent] + 1, stream.length), currentParent, "Unclosed CSS block '{'", null);
            if (depth > 0) {
                depth--;
                currentParent = parentStack[depth];
            } else {
                break;
            }
        }

        tree.buildNodesByOffset();
        return tree;
    }

    public static CssSyntaxTree parseDeclarationList(CssTokenStream stream, String source) {
        CssSyntaxTree tree = new CssSyntaxTree(stream.length / 4 + 10);
        int root = 0; // Node 0 is reserved

        int p = 0;
        // fast-forward to the first valid token offset if tokenized from a region
        while (p < stream.length && (stream.types[p] == CssTokenStream.TK_NONE || stream.types[p] == CssTokenStream.TK_COMMENT))
            p++;

        while (p < stream.length) {
            byte type = stream.types[p];

            if (type == CssTokenStream.TK_NONE || type == CssTokenStream.TK_COMMENT) {
                p++;
                continue;
            }

            if (type == CssTokenStream.TK_PROPERTY) {
                p = parseProperty(tree, stream, source, p, root);
            } else {
                int startP = p;
                int endP = getEndOffset(stream, p, type);
                tree.addNode(CssSyntaxTree.N_ERROR, startP, endP, root, "Unexpected token in inline style", null);
                p = endP;
            }
        }

        tree.buildNodesByOffset();
        return tree;
    }

    private static int parseProperty(CssSyntaxTree tree, CssTokenStream stream, String source, int p, int currentParent) {
        int startP = p;
        int propEnd = getEndOffset(stream, p, CssTokenStream.TK_PROPERTY);
        String propName = source.substring(startP, propEnd).trim();

        int nextP = skipWhitespaceAndComments(stream, propEnd);
        if (nextP < stream.length && stream.types[nextP] == CssTokenStream.TK_PUNCT && source.charAt(nextP) == ':') {
            int colonP = nextP;
            nextP = skipWhitespaceAndComments(stream, colonP + 1);

            int valStart = nextP;
            int valEnd = valStart;
            if (nextP < stream.length && stream.types[nextP] == CssTokenStream.TK_VALUE) {
                valEnd = getEndOffset(stream, nextP, CssTokenStream.TK_VALUE);
                nextP = skipWhitespaceAndComments(stream, valEnd);
            }

            String valText = valStart < valEnd ? source.substring(valStart, valEnd).trim() : null;

            int declEnd = valEnd;
            if (nextP < stream.length && stream.types[nextP] == CssTokenStream.TK_PUNCT) {
                char punct = source.charAt(nextP);
                if (punct == ';') {
                    declEnd = nextP + 1;
                    p = declEnd;
                } else if (punct == '}') {
                    declEnd = nextP;
                    p = nextP; // do not consume
                } else {
                    declEnd = nextP + 1;
                    p = declEnd;
                }
            } else {
                p = nextP; // EOF
            }

            int declNode = tree.addNode(CssSyntaxTree.N_DECLARATION, startP, declEnd, currentParent, null, null);
            tree.addNode(CssSyntaxTree.N_PROPERTY, startP, propEnd, declNode, propName, null);
            if (valText != null && !valText.isEmpty()) {
                tree.addNode(CssSyntaxTree.N_VALUE, valStart, valEnd, declNode, null, valText);
            }
        } else {
            // Malformed property without colon
            int declEnd = nextP;
            if (nextP < stream.length && stream.types[nextP] == CssTokenStream.TK_PUNCT) {
                char punct = source.charAt(nextP);
                if (punct == ';') {
                    declEnd = nextP + 1;
                    p = declEnd;
                } else if (punct == '}') {
                    p = nextP;
                } else {
                    declEnd = nextP + 1;
                    p = declEnd;
                }
            } else {
                p = nextP;
            }
            tree.addNode(CssSyntaxTree.N_ERROR, startP, declEnd, currentParent, "Malformed declaration", null);
        }
        return p;
    }

    private static int getEndOffset(CssTokenStream stream, int startP, byte matchType) {
        int end = startP;
        for (int i = startP; i < stream.length; i++) {
            byte type = stream.types[i];
            if (type == matchType || type == CssTokenStream.TK_NONE || type == CssTokenStream.TK_COMMENT) {
                if (type == matchType) {
                    end = i + 1;
                }
            } else {
                break;
            }
        }
        return end;
    }

    private static int skipWhitespaceAndComments(CssTokenStream stream, int p) {
        while (p < stream.length) {
            byte type = stream.types[p];
            if (type == CssTokenStream.TK_NONE || type == CssTokenStream.TK_COMMENT) {
                p++;
            } else {
                break;
            }
        }
        return p;
    }
}
