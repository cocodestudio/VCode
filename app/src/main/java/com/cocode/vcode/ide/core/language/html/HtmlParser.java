package com.cocode.vcode.ide.core.language.html;

import com.cocode.vcode.ide.core.diagnostic.util.KnownElements;
import com.cocode.vcode.ide.core.language.js.ParseResult;

import java.util.Arrays;

/**
 * Parses an HtmlTokenStream into an HtmlSyntaxTree.
 * Implements a zero-allocation parsing loop (aside from String extraction for node names/values)
 * and robust sync-to-tag recovery for malformed HTML.
 */
public class HtmlParser {

    /**
     * Parses the given token stream into a flat-array syntax tree.
     *
     * @param source The original HTML source string
     * @param stream The token stream produced by HtmlLexer
     * @return A populated ParseResult
     */
    public static ParseResult parse(String source, HtmlTokenStream stream) {
        HtmlSyntaxTree tree = new HtmlSyntaxTree(Math.max(256, stream.length / 4));
        int p = 0;
        int currentParent = 0;
        int[] parentStack = new int[256];
        String[] tagStack = new String[256];
        int depth = 0;

        while (p < stream.length) {
            byte type = stream.types[p];
            if (type == HtmlTokenStream.TK_NONE) {
                p++;
                continue;
            }

            if (type == HtmlTokenStream.TK_TEXT) {
                int start = stream.tokenStart[p];
                int end = advancePastToken(stream, p);
                tree.addNode(HtmlSyntaxTree.N_TEXT, start, end, currentParent, null, null);
                p = end;
                continue;
            }

            if (type == HtmlTokenStream.TK_COMMENT) {
                int start = stream.tokenStart[p];
                int end = advancePastToken(stream, p);
                tree.addNode(HtmlSyntaxTree.N_COMMENT, start, end, currentParent, null, null);
                p = end;
                continue;
            }

            if (type == HtmlTokenStream.TK_DOCTYPE) {
                int start = stream.tokenStart[p];
                int end = advancePastToken(stream, p);
                tree.addNode(HtmlSyntaxTree.N_DOCTYPE, start, end, currentParent, null, null);
                p = end;
                continue;
            }

            if (type == HtmlTokenStream.TK_TAG_OPEN) {
                int start = stream.tokenStart[p];
                int openEnd = advancePastToken(stream, p);
                boolean isClosingTag = (openEnd - start >= 2 && source.charAt(start + 1) == '/');

                p = openEnd;
                // Skip whitespace (TK_NONE)
                while (p < stream.length && stream.types[p] == HtmlTokenStream.TK_NONE) p++;

                String tagName = null;
                if (p < stream.length && stream.types[p] == HtmlTokenStream.TK_TAG_NAME) {
                    int nameEnd = advancePastToken(stream, p);
                    tagName = source.substring(stream.tokenStart[p], nameEnd).toLowerCase();
                    p = nameEnd;
                }

                if (isClosingTag) {
                    // It's </tag>
                    if (tagName != null) {
                        int matchDepth = -1;
                        for (int i = depth - 1; i >= 0; i--) {
                            if (tagName.equals(tagStack[i])) {
                                matchDepth = i;
                                break;
                            }
                        }

                        int closeTagEndOffset = p;
                        while (p < stream.length) {
                            if (stream.types[p] == HtmlTokenStream.TK_TAG_CLOSE) {
                                closeTagEndOffset = advancePastToken(stream, p);
                                p = closeTagEndOffset;
                                break;
                            } else if (stream.types[p] == HtmlTokenStream.TK_TAG_OPEN) {
                                // Missing > on closing tag, found next tag open
                                closeTagEndOffset = p;
                                break;
                            }
                            p++;
                        }

                        if (matchDepth != -1) {
                            for (int i = depth - 1; i > matchDepth; i--) {
                                int unclosedId = parentStack[i];
                                String unclosedName = tagStack[i];
                                tree.nodeEnd[unclosedId] = start;
                                tree.addNode(HtmlSyntaxTree.N_ERROR, tree.nodeStart[unclosedId], tree.nodeStart[unclosedId] + (unclosedName != null ? unclosedName.length() + 2 : 1), unclosedId, unclosedName, "Unclosed");
                            }
                            tree.nodeEnd[parentStack[matchDepth]] = closeTagEndOffset;
                            depth = matchDepth;
                            currentParent = depth > 0 ? parentStack[depth - 1] : 0;
                        } else {
                            // Unmatched closing tag -> Error Node
                            tree.addNode(HtmlSyntaxTree.N_ERROR, start, closeTagEndOffset, currentParent, tagName, null);
                        }
                    } else {
                        // </ but no tag name
                        int closeTagEndOffset = p;
                        while (p < stream.length && stream.types[p] != HtmlTokenStream.TK_TAG_CLOSE && stream.types[p] != HtmlTokenStream.TK_TAG_OPEN)
                            p++;
                        if (p < stream.length && stream.types[p] == HtmlTokenStream.TK_TAG_CLOSE) {
                            closeTagEndOffset = advancePastToken(stream, p);
                            p = closeTagEndOffset;
                        } else {
                            closeTagEndOffset = p;
                        }
                        tree.addNode(HtmlSyntaxTree.N_ERROR, start, closeTagEndOffset, currentParent, null, null);
                    }
                    continue; // Done with closing tag
                }

                // It's an opening tag
                int elemId = tree.addNode(HtmlSyntaxTree.N_ELEMENT, start, start, currentParent, tagName, null);

                boolean selfClosing = false;
                while (p < stream.length) {
                    byte t = stream.types[p];
                    if (t == HtmlTokenStream.TK_TAG_CLOSE) {
                        int closeStart = stream.tokenStart[p];
                        int closeEnd = advancePastToken(stream, p);
                        if (closeEnd - closeStart >= 2 && source.charAt(closeEnd - 2) == '/') {
                            selfClosing = true;
                        }
                        tree.nodeEnd[elemId] = closeEnd;
                        p = closeEnd;
                        break;
                    } else if (t == HtmlTokenStream.TK_ATTR_NAME) {
                        int attrNameStart = stream.tokenStart[p];
                        int attrNameEnd = advancePastToken(stream, p);
                        String attrName = source.substring(attrNameStart, attrNameEnd);
                        p = attrNameEnd;

                        String attrValue = null;
                        int attrEnd = attrNameEnd;
                        int valStart = 0;

                        while (p < stream.length && stream.types[p] == HtmlTokenStream.TK_NONE) p++;

                        if (p < stream.length && stream.types[p] == HtmlTokenStream.TK_ATTR_VALUE) {
                            valStart = stream.tokenStart[p];
                            int valEnd = advancePastToken(stream, p);
                            attrValue = source.substring(valStart, valEnd);
                            attrEnd = valEnd;
                            p = valEnd;
                        }

                        int attrNodeId = tree.addNode(HtmlSyntaxTree.N_ATTRIBUTE, attrNameStart, attrEnd, elemId, attrName, attrValue);
                        if (attrValue != null) {
                            tree.nodeExtra[attrNodeId] = valStart;
                        }
                    } else if (t == HtmlTokenStream.TK_TAG_OPEN) {
                        // Malformed: unclosed tag missing '>'. We saw another tag open!
                        // Sync-to-< recovery
                        tree.nodeEnd[elemId] = p;
                        break;
                    } else {
                        p++;
                    }
                }

                // If it didn't find a close tag and reached EOF
                if (tree.nodeEnd[elemId] == start && p == stream.length) {
                    tree.nodeEnd[elemId] = p;
                }

                if (!selfClosing && isVoidElement(tagName)) {
                    selfClosing = true;
                }

                if (!selfClosing) {
                    if (depth == parentStack.length) {
                        parentStack = Arrays.copyOf(parentStack, parentStack.length * 2);
                        tagStack = Arrays.copyOf(tagStack, tagStack.length * 2);
                    }
                    parentStack[depth] = elemId;
                    tagStack[depth] = tagName;
                    depth++;
                    currentParent = elemId;
                }

                continue;
            }

            p++;
        }

        // Close unclosed tags at EOF
        for (int i = 0; i < depth; i++) {
            int unclosedId = parentStack[i];
            String unclosedName = tagStack[i];
            tree.nodeEnd[unclosedId] = stream.length;
            tree.addNode(HtmlSyntaxTree.N_ERROR, tree.nodeStart[unclosedId], tree.nodeStart[unclosedId] + (unclosedName != null ? unclosedName.length() + 2 : 1), unclosedId, unclosedName, "Unclosed");
        }

        tree.buildNodesByOffset();
        ParseResult result = new ParseResult(null, source, stream, tree);
        embedSubLanguages(tree, source, result.embeddedResults);
        return result;
    }

    private static void embedSubLanguages(HtmlSyntaxTree tree, String source, java.util.List<ParseResult.EmbeddedResult> embeddedResults) {
        int initialCount = tree.nodeCount;
        for (int i = 1; i < initialCount; i++) {
            if (tree.nodeType[i] == HtmlSyntaxTree.N_ELEMENT) {
                String tagName = tree.nodeName[i];
                if ("script".equals(tagName)) {
                    // Check if it has a src= attribute
                    boolean hasSrc = false;
                    int child = tree.nodeChild[i];
                    int childLoop1 = 0;
                    while (child > 0 && child < tree.nodeCount && ++childLoop1 <= tree.nodeCount) {
                        if (tree.nodeType[child] == HtmlSyntaxTree.N_ATTRIBUTE && "src".equals(tree.nodeName[child])) {
                            hasSrc = true;
                            break;
                        }
                        child = tree.nodeSibling[child];
                    }

                    if (!hasSrc) {
                        // Extract inner text-node span
                        child = tree.nodeChild[i];
                        int childLoop2 = 0;
                        while (child > 0 && child < tree.nodeCount && ++childLoop2 <= tree.nodeCount) {
                            if (tree.nodeType[child] == HtmlSyntaxTree.N_TEXT) {
                                int start = tree.nodeStart[child];
                                int end = tree.nodeEnd[child];
                                if (start < end) {
                                    // Parse as JS
                                    com.cocode.vcode.ide.core.diagnostic.util.TokenStream jsStream =
                                            com.cocode.vcode.ide.core.language.js.JsLexer.tokenizeRegion(source, start, end);
                                    com.cocode.vcode.ide.core.language.js.JsSyntaxTree jsTree =
                                            com.cocode.vcode.ide.core.language.js.JsParser.parseTopLevel(source, jsStream);
                                    ParseResult jsResult = new ParseResult(null, source, jsStream, jsTree, null, ParseResult.MODE_FULL);
                                    tree.nodeReference[i] = jsResult; // attach directly to <script> element
                                    embeddedResults.add(new ParseResult.EmbeddedResult(start, end, jsResult));
                                }
                                break; // typically only one text node in <script>
                            }
                            child = tree.nodeSibling[child];
                        }
                    }
                } else if ("style".equals(tagName)) {
                    // Extract inner text-node span
                    int child = tree.nodeChild[i];
                    int childLoop3 = 0;
                    while (child > 0 && child < tree.nodeCount && ++childLoop3 <= tree.nodeCount) {
                        if (tree.nodeType[child] == HtmlSyntaxTree.N_TEXT) {
                            int start = tree.nodeStart[child];
                            int end = tree.nodeEnd[child];
                            if (start < end) {
                                // Parse as CSS
                                com.cocode.vcode.ide.core.language.css.CssTokenStream cssStream =
                                        com.cocode.vcode.ide.core.language.css.CssLexer.tokenizeRegion(source, start, end);
                                com.cocode.vcode.ide.core.language.css.CssSyntaxTree cssTree =
                                        com.cocode.vcode.ide.core.language.css.CssParser.parse(cssStream, source);
                                ParseResult cssResult = new ParseResult(null, source, cssStream, cssTree);
                                tree.nodeReference[i] = cssResult; // attach directly to <style> element
                                embeddedResults.add(new ParseResult.EmbeddedResult(start, end, cssResult));
                            }
                            break; // typically only one text node in <style>
                        }
                        child = tree.nodeSibling[child];
                    }
                }

                // Also scan attributes for inline style="color: red" and on*="doThing()"
                int attr = tree.nodeChild[i];
                int attrLoop = 0;
                while (attr > 0 && attr < tree.nodeCount && ++attrLoop <= tree.nodeCount) {
                    if (tree.nodeType[attr] == HtmlSyntaxTree.N_ATTRIBUTE && tree.nodeValue[attr] != null) {
                        String attrName = tree.nodeName[attr];
                        if ("style".equals(attrName)) {
                            int valStart = tree.nodeExtra[attr];
                            int valEnd = tree.nodeEnd[attr];
                            if (valStart < valEnd) {
                                String val = tree.nodeValue[attr];
                                int innerStart = valStart;
                                int innerEnd = valEnd;
                                if (val.length() >= 2 && (val.charAt(0) == '"' || val.charAt(0) == '\'')) {
                                    innerStart++;
                                    innerEnd--;
                                }
                                if (innerStart < innerEnd) {
                                    com.cocode.vcode.ide.core.language.css.CssTokenStream cssStream =
                                            com.cocode.vcode.ide.core.language.css.CssLexer.tokenizeRegion(source, innerStart, innerEnd);
                                    com.cocode.vcode.ide.core.language.css.CssSyntaxTree cssTree =
                                            com.cocode.vcode.ide.core.language.css.CssParser.parseDeclarationList(cssStream, source);
                                    ParseResult cssResult = new ParseResult(null, source, cssStream, cssTree);
                                    tree.nodeReference[attr] = cssResult;
                                    embeddedResults.add(new ParseResult.EmbeddedResult(innerStart, innerEnd, cssResult));
                                }
                            }
                        } else if (attrName != null && attrName.length() > 2 && attrName.startsWith("on")) {
                            int valStart = tree.nodeExtra[attr];
                            int valEnd = tree.nodeEnd[attr];
                            if (valStart < valEnd) {
                                String val = tree.nodeValue[attr];
                                int innerStart = valStart;
                                int innerEnd = valEnd;
                                if (val.length() >= 2 && (val.charAt(0) == '"' || val.charAt(0) == '\'')) {
                                    innerStart++;
                                    innerEnd--;
                                }
                                if (innerStart < innerEnd) {
                                    com.cocode.vcode.ide.core.diagnostic.util.TokenStream jsStream =
                                            com.cocode.vcode.ide.core.language.js.JsLexer.tokenizeRegion(source, innerStart, innerEnd);
                                    com.cocode.vcode.ide.core.language.js.JsSyntaxTree jsTree =
                                            com.cocode.vcode.ide.core.language.js.JsParser.parseStatementList(source, jsStream);
                                    ParseResult jsResult = new ParseResult(null, source, jsStream, jsTree, null, ParseResult.MODE_FULL);
                                    tree.nodeReference[attr] = jsResult;
                                    embeddedResults.add(new ParseResult.EmbeddedResult(innerStart, innerEnd, jsResult));
                                }
                            }
                        }
                    }
                    attr = tree.nodeSibling[attr];
                }
            }
        }
    }

    private static int advancePastToken(HtmlTokenStream stream, int p) {
        int start = stream.tokenStart[p];
        byte type = stream.types[p];
        int end = p;
        while (end < stream.length && stream.tokenStart[end] == start && stream.types[end] == type) {
            end++;
        }
        return end;
    }

    private static boolean isVoidElement(String tag) {
        return tag != null && KnownElements.VOID_ELEMENTS.contains(tag.toLowerCase());
    }
}
