package com.cocode.vcode.ide.core.language.md;

public final class MdParser {

    public static MdSyntaxTree parseBlocks(MdLineStream stream, String source) {
        int len = source.length();
        MdSyntaxTree tree = new MdSyntaxTree(Math.max(512, stream.lineCount * 3));
        
        int rootId = tree.addNode(MdSyntaxTree.N_NONE, 0, len, 0, null);
        
        int mode = com.cocode.vcode.ide.core.language.base.ParseModeGate.select(
                com.cocode.vcode.ide.core.language.base.ParseModeGate.SizeMetric.LINES, 
                stream.lineCount, 1000, 5000, 15000);
                
        if (mode == com.cocode.vcode.ide.core.language.js.ParseResult.MODE_TOKENIZE_ONLY) {
            return tree;
        }
        
        // Stack to track open blocks: lists and blockquotes
        int[] stackIds = new int[64];
        int[] stackTypes = new int[64];
        int[] stackLevels = new int[64]; // for lists: indent; for bq: > count
        int stackSize = 0;
        
        stackIds[stackSize] = rootId;
        stackTypes[stackSize] = MdSyntaxTree.N_NONE;
        stackLevels[stackSize] = -1;
        stackSize++;
        
        int currentParagraph = 0;
        
        int i = 0;
        while (i < stream.lineCount) {
            byte lineType = stream.lineTypes[i];
            int start = stream.lineStartOffsets[i];
            int end = stream.lineEndOffsets[i];
            
            if (lineType == MdLineStream.L_BLANK) {
                // Close paragraph, but lists/blockquotes stay open until a mismatch
                currentParagraph = 0;
                i++;
                continue;
            }
            
            // 1. Calculate line's blockquote depth and list indent
            int bqDepth = 0;
            int indent = 0;
            int j = start;
            while (j < end) {
                char c = source.charAt(j);
                if (c == ' ') {
                    indent++;
                    j++;
                } else if (c == '>') {
                    bqDepth++;
                    indent = 0; // Reset indent after >
                    j++;
                } else {
                    break;
                }
            }
            
            // Re-evaluate lineType if it was hidden behind blockquote
            if (lineType == MdLineStream.L_BLOCKQUOTE && j < end) {
                lineType = classifyRemainder(source, j, end);
            }
            
            // 2. Match with current stack
            int matchIdx = 1; // 0 is root
            int currentBqDepth = 0;
            
            while (matchIdx < stackSize) {
                if (stackTypes[matchIdx] == MdSyntaxTree.N_BLOCKQUOTE) {
                    if (currentBqDepth + 1 <= bqDepth) {
                        currentBqDepth++;
                        matchIdx++;
                    } else {
                        break;
                    }
                } else if (stackTypes[matchIdx] == MdSyntaxTree.N_LIST) {
                    if (currentBqDepth < bqDepth) {
                        break;
                    }
                    if (indent >= stackLevels[matchIdx] || lineType == MdLineStream.L_LIST_ITEM) {
                        matchIdx++;
                    } else {
                        break;
                    }
                } else {
                    matchIdx++;
                }
            }
            
            // Close unmatched blocks
            if (stackSize > matchIdx) {
                currentParagraph = 0; // Paragraph always closes if we pop stack
                stackSize = matchIdx;
            }
            
            int currentParent = stackIds[stackSize - 1];
            
            // Open new blockquotes if needed
            while (currentBqDepth < bqDepth) {
                currentParagraph = 0;
                currentBqDepth++;
                int bqId = tree.addNode(MdSyntaxTree.N_BLOCKQUOTE, start, end, currentParent, null);
                if (stackSize < stackIds.length) {
                    stackIds[stackSize] = bqId;
                    stackTypes[stackSize] = MdSyntaxTree.N_BLOCKQUOTE;
                    stackLevels[stackSize] = currentBqDepth;
                    stackSize++;
                }
                currentParent = bqId;
            }
            
            // Process block type
            if (lineType == MdLineStream.L_CODE_FENCE) {
                currentParagraph = 0;
                int tagStart = j;
                while (tagStart < end && (source.charAt(tagStart) == '`' || source.charAt(tagStart) == ' ' || source.charAt(tagStart) == '~')) {
                    tagStart++;
                }
                String lang = source.substring(tagStart, end).trim();
                
                int blockStart = start;
                int originalI = i;
                int startOfInner = (i + 1 < stream.lineCount) ? stream.lineStartOffsets[i + 1] : len;
                
                i++;
                while (i < stream.lineCount) {
                    int innerStart = stream.lineStartOffsets[i];
                    int innerEnd = stream.lineEndOffsets[i];
                    int innerJ = innerStart;
                    while (innerJ < innerEnd && (source.charAt(innerJ) == ' ' || source.charAt(innerJ) == '>')) {
                        innerJ++;
                    }
                    if (classifyRemainder(source, innerJ, innerEnd) == MdLineStream.L_CODE_FENCE) {
                        break;
                    }
                    i++;
                }
                
                int endOfInner = (i < stream.lineCount) ? stream.lineStartOffsets[i] : len;
                if (endOfInner < startOfInner) endOfInner = startOfInner; // safety for empty fences
                
                int blockEnd = (i < stream.lineCount) ? stream.lineEndOffsets[i] : (stream.lineCount > 0 ? stream.lineEndOffsets[stream.lineCount - 1] : len);
                
                int cbId = tree.addNode(MdSyntaxTree.N_CODE_BLOCK, blockStart, blockEnd, currentParent, lang);
                tree.addNode(MdSyntaxTree.N_NONE, startOfInner, endOfInner, cbId, null);
                if (i >= stream.lineCount) {
                    tree.addNode(MdSyntaxTree.N_ERROR, blockStart, Math.min(blockStart + 3, len), cbId, "Unclosed code block");
                }
                
                updateParentBounds(tree, stackIds, stackSize, blockEnd);
                
                i++;
                continue;
            }
            
            if (lineType == MdLineStream.L_HEADER) {
                currentParagraph = 0;
                int hashes = 0;
                int k = j;
                while (k < end && source.charAt(k) == '#') {
                    hashes++;
                    k++;
                }
                int id = tree.addNode(MdSyntaxTree.N_HEADER, start, end, currentParent, null);
                tree.nodeExtra[id] = hashes;
                updateParentBounds(tree, stackIds, stackSize, end);
                i++;
                continue;
            }
            
            if (lineType == MdLineStream.L_THEMATIC_BREAK) {
                currentParagraph = 0;
                tree.addNode(MdSyntaxTree.N_THEMATIC_BREAK, start, end, currentParent, null);
                updateParentBounds(tree, stackIds, stackSize, end);
                i++;
                continue;
            }
            
            if (lineType == MdLineStream.L_LIST_ITEM) {
                currentParagraph = 0;
                if (stackTypes[stackSize - 1] != MdSyntaxTree.N_LIST || indent > stackLevels[stackSize - 1] + 2) {
                    int listId = tree.addNode(MdSyntaxTree.N_LIST, start, end, currentParent, null);
                    if (stackSize < stackIds.length) {
                        stackIds[stackSize] = listId;
                        stackTypes[stackSize] = MdSyntaxTree.N_LIST;
                        stackLevels[stackSize] = indent;
                        stackSize++;
                    }
                    currentParent = listId;
                }
                
                int itemId = tree.addNode(MdSyntaxTree.N_LIST_ITEM, start, end, currentParent, null);
                currentParagraph = tree.addNode(MdSyntaxTree.N_PARAGRAPH, start, end, itemId, null);
                updateParentBounds(tree, stackIds, stackSize, end);
                
                i++;
                continue;
            }
            
            if (currentParagraph == 0) {
                currentParagraph = tree.addNode(MdSyntaxTree.N_PARAGRAPH, start, end, currentParent, null);
            } else {
                tree.nodeEnd[currentParagraph] = end;
                int pParent = tree.nodeParent[currentParagraph];
                if (tree.nodeType[pParent] == MdSyntaxTree.N_LIST_ITEM) {
                    tree.nodeEnd[pParent] = end;
                }
            }
            updateParentBounds(tree, stackIds, stackSize, end);
            
            i++;
        }
        
        embedSubLanguages(tree, source);
        parseInline(tree, source);
        tree.buildNodesByOffset();
        return tree;
    }

    private static void embedSubLanguages(MdSyntaxTree tree, String source) {
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == MdSyntaxTree.N_CODE_BLOCK) {
                String lang = tree.nodeName[i];
                if (lang != null && !lang.isEmpty()) {
                    lang = lang.toLowerCase();
                    int child = tree.nodeChild[i];
                    if (child != 0 && tree.nodeType[child] == MdSyntaxTree.N_NONE) {
                        int start = tree.nodeStart[child];
                        int end = tree.nodeEnd[child];
                        if (start < end) {
                            if (lang.equals("js") || lang.equals("javascript")) {
                                com.cocode.vcode.ide.core.diagnostic.util.TokenStream tokens = com.cocode.vcode.ide.core.language.js.JsLexer.tokenizeRegion(source, start, end);
                                com.cocode.vcode.ide.core.language.js.JsSyntaxTree subTree = com.cocode.vcode.ide.core.language.js.JsParser.parseTopLevel(source, tokens);
                                tree.nodeReference[i] = new com.cocode.vcode.ide.core.language.js.ParseResult(null, source, tokens, subTree, null, com.cocode.vcode.ide.core.language.js.ParseResult.MODE_FULL);
                            } else if (lang.equals("css")) {
                                com.cocode.vcode.ide.core.language.css.CssTokenStream tokens = com.cocode.vcode.ide.core.language.css.CssLexer.tokenizeRegion(source, start, end);
                                com.cocode.vcode.ide.core.language.css.CssSyntaxTree subTree = com.cocode.vcode.ide.core.language.css.CssParser.parse(tokens, source);
                                tree.nodeReference[i] = new com.cocode.vcode.ide.core.language.js.ParseResult(null, source, tokens, subTree);
                            } else if (lang.equals("json")) {
                                com.cocode.vcode.ide.core.language.json.JsonTokenStream tokens = com.cocode.vcode.ide.core.language.json.JsonLexer.tokenizeRegion(source, start, end);
                                com.cocode.vcode.ide.core.language.json.JsonSyntaxTree subTree = com.cocode.vcode.ide.core.language.json.JsonParser.parse(tokens, source);
                                tree.nodeReference[i] = new com.cocode.vcode.ide.core.language.js.ParseResult(null, source, tokens, subTree);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void parseInline(MdSyntaxTree tree, String source) {
        int initialCount = tree.nodeCount; // Only iterate over blocks, not newly added inline nodes
        for (int i = 1; i < initialCount; i++) {
            int type = tree.nodeType[i];
            if (type == MdSyntaxTree.N_PARAGRAPH || type == MdSyntaxTree.N_HEADER) {
                // If it has children, the text is managed by them. 
                // But in our AST, paragraphs and headers are leaf blocks.
                if (tree.nodeChild[i] == 0) {
                    parseInlineContent(tree, i, tree.nodeStart[i], tree.nodeEnd[i], source);
                }
            }
        }
    }

    private static void parseInlineContent(MdSyntaxTree tree, int parentId, int start, int end, String source) {
        int i = start;
        while (i < end) {
            int contentStart = skipBlockMarkers(source, i, end);
            int lineEnd = source.indexOf('\n', contentStart);
            if (lineEnd == -1 || lineEnd > end) {
                lineEnd = end;
            }
            parseInlineRegion(tree, parentId, contentStart, lineEnd, source);
            i = lineEnd + 1;
        }
    }

    private static int skipBlockMarkers(String source, int start, int end) {
        int i = start;
        while (i < end && source.charAt(i) == ' ') i++;
        if (i == end) return end;
        char c = source.charAt(i);
        if (c == '>') {
            while (i < end && (source.charAt(i) == '>' || source.charAt(i) == ' ')) i++;
        }
        if (i < end && source.charAt(i) == '#') {
            while (i < end && source.charAt(i) == '#') i++;
            while (i < end && source.charAt(i) == ' ') i++;
        } else if (i < end && (source.charAt(i) == '-' || source.charAt(i) == '*')) {
            if (i + 1 < end && source.charAt(i + 1) == ' ') {
                i += 2;
                while (i < end && source.charAt(i) == ' ') i++;
            }
        } else if (i < end && c >= '0' && c <= '9') {
            int j = i;
            while (j < end && source.charAt(j) >= '0' && source.charAt(j) <= '9') j++;
            if (j < end && (source.charAt(j) == '.' || source.charAt(j) == ')')) {
                j++;
                if (j < end && source.charAt(j) == ' ') {
                    i = j + 1;
                    while (i < end && source.charAt(i) == ' ') i++;
                }
            }
        }
        return i;
    }

    private static void parseInlineRegion(MdSyntaxTree tree, int parentId, int start, int end, String source) {
        int i = start;
        while (i < end) {
            char c = source.charAt(i);
            
            if (c == '`') {
                int backticks = 0;
                int j = i;
                while (j < end && source.charAt(j) == '`') {
                    backticks++;
                    j++;
                }
                int k = j;
                boolean found = false;
                while (k < end) {
                    if (source.charAt(k) == '`') {
                        int count = 0;
                        int m = k;
                        while (m < end && source.charAt(m) == '`') {
                            count++;
                            m++;
                        }
                        if (count == backticks) {
                            tree.addNode(MdSyntaxTree.N_INLINE_CONTAINER, i, m, parentId, "`");
                            i = m;
                            found = true;
                            break;
                        }
                        k = m;
                    } else {
                        k++;
                    }
                }
                if (!found) i = j;
                continue;
            }
            
            if (c == '*' || c == '_') {
                int runLen = 0;
                int j = i;
                while (j < end && source.charAt(j) == c) {
                    runLen++;
                    j++;
                }
                if (runLen == 1 || runLen == 2) {
                    int k = j;
                    boolean found = false;
                    while (k < end) {
                        if (source.charAt(k) == c) {
                            int count = 0;
                            int m = k;
                            while (m < end && source.charAt(m) == c) {
                                count++;
                                m++;
                            }
                            if (count == runLen) {
                                String type = (runLen == 2) ? "**" : "*";
                                int id = tree.addNode(MdSyntaxTree.N_INLINE_CONTAINER, i, m, parentId, type);
                                parseInlineRegion(tree, id, j, k, source);
                                i = m;
                                found = true;
                                break;
                            }
                            k = m;
                        } else {
                            k++;
                        }
                    }
                    if (!found) i = j;
                    continue;
                } else {
                    i = j;
                    continue;
                }
            }
            
            if (c == '[') {
                int closeBracket = source.indexOf(']', i + 1);
                if (closeBracket != -1 && closeBracket < end) {
                    if (closeBracket + 1 < end && source.charAt(closeBracket + 1) == '(') {
                        int closeParen = source.indexOf(')', closeBracket + 2);
                        if (closeParen != -1 && closeParen < end) {
                            int id = tree.addNode(MdSyntaxTree.N_INLINE_CONTAINER, i, closeParen + 1, parentId, "link");
                            parseInlineRegion(tree, id, i + 1, closeBracket, source);
                            i = closeParen + 1;
                            continue;
                        }
                    }
                }
                i++;
                continue;
            }
            
            i++;
        }
    }
    
    private static void updateParentBounds(MdSyntaxTree tree, int[] stackIds, int stackSize, int end) {
        for (int i = 1; i < stackSize; i++) {
            tree.nodeEnd[stackIds[i]] = end;
        }
    }
    
    private static byte classifyRemainder(String source, int start, int end) {
        int i = start;
        while (i < end && source.charAt(i) == ' ') i++;
        if (i == end) return MdLineStream.L_BLANK;
        
        char c = source.charAt(i);
        
        if (c == '#') {
            int hashes = 0;
            while (i < end && source.charAt(i) == '#') {
                hashes++;
                i++;
            }
            if (hashes <= 6 && (i == end || source.charAt(i) == ' ')) {
                return MdLineStream.L_HEADER;
            }
            return MdLineStream.L_PARAGRAPH;
        }
        
        if (c == '`') {
            int backticks = 0;
            while (i < end && source.charAt(i) == '`') {
                backticks++;
                i++;
            }
            if (backticks >= 3) return MdLineStream.L_CODE_FENCE;
            return MdLineStream.L_PARAGRAPH;
        }
        
        if (c == '-' || c == '*' || c == '_') {
            char marker = c;
            int count = 1;
            int j = i + 1;
            while (j < end) {
                char ch = source.charAt(j);
                if (ch == marker) count++;
                else if (ch != ' ') break;
                j++;
            }
            boolean onlyMarkersAndSpaces = true;
            for (int k = j; k < end; k++) {
                if (source.charAt(k) != ' ') {
                    onlyMarkersAndSpaces = false;
                    break;
                }
            }
            if (count >= 3 && onlyMarkersAndSpaces) return MdLineStream.L_THEMATIC_BREAK;
            
            if ((c == '-' || c == '*') && i + 1 <= end) {
                if (i + 1 == end || source.charAt(i + 1) == ' ') {
                    return MdLineStream.L_LIST_ITEM;
                }
            }
            return MdLineStream.L_PARAGRAPH;
        }
        
        if (c >= '0' && c <= '9') {
            int j = i;
            while (j < end && source.charAt(j) >= '0' && source.charAt(j) <= '9') j++;
            if (j < end && (source.charAt(j) == '.' || source.charAt(j) == ')')) {
                j++;
                if (j == end || source.charAt(j) == ' ') return MdLineStream.L_LIST_ITEM;
            }
        }
        
        return MdLineStream.L_PARAGRAPH;
    }
}
