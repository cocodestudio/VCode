package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsParser;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.language.js.ScopeTree;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspRange;
import com.cocode.vcode.ide.core.lsp.SymbolExtractor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves symbol rename locations across JavaScript and TypeScript documents.
 * Disambiguates lexical variables, parameters, class members (including {@code this} property access),
 * and object properties.
 */
public final class JsSymbolRenamer {

    private JsSymbolRenamer() {
    }

    /**
     * Resolves all locations in the document that should be updated when renaming the symbol at the given position.
     *
     * @param doc current document snapshot
     * @param pos caret position
     * @return deduplicated list of locations targeting the identifier token
     */
    public static List<LspLocation> rename(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return Collections.emptyList();
        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return Collections.emptyList();

        String text = doc.text;
        String word = extractWord(text, offset);
        if (word.isEmpty() || isNonRenamableKeyword(word)) return Collections.emptyList();

        TokenStream tokens = JsLexer.tokenize(text);
        JsSyntaxTree tree = JsParser.parseFull(text, tokens);
        if (tree != null) {
            tree.buildNodesByOffset();
        }
        ScopeTree scopeTree = ScopeTree.build(tree);

        // Find exact start and end of the word at offset
        int wordStart = findWordStart(text, offset);
        if (wordStart < 0 || wordStart + word.length() > text.length()) return Collections.emptyList();
        int wordEnd = wordStart + word.length();
        if (!text.substring(wordStart, wordEnd).equals(word)) return Collections.emptyList();

        // Check for property access (e.g. obj.prop or obj?.prop)
        boolean isMemberAccess = false;
        String receiver = null;
        int receiverStart = -1;
        int prevTokenIdx = findPrevSignificantToken(tokens, text, wordStart);
        if (prevTokenIdx >= 0 && tokens.types[prevTokenIdx] == TokenStream.TK_PUNCT && text.charAt(tokens.tokenStart[prevTokenIdx]) == '.') {
            isMemberAccess = true;
            int dotStart = tokens.tokenStart[prevTokenIdx];
            int beforeDot = dotStart - 1;
            if (beforeDot >= 0 && text.charAt(beforeDot) == '?') {
                beforeDot--;
            }
            int rTokenIdx = findPrevSignificantTokenBefore(tokens, text, beforeDot + 1);
            if (rTokenIdx >= 0) {
                receiverStart = tokens.tokenStart[rTokenIdx];
                int rEnd = receiverStart;
                while (rEnd < text.length() && isWordChar(text.charAt(rEnd))) {
                    rEnd++;
                }
                receiver = text.substring(receiverStart, rEnd);
            }
        }

        // Check for class member declaration
        int classMemberNodeId = -1;
        int declaringClassNodeId = -1;
        if (tree != null && !isMemberAccess) {
            for (int i = 1; i < tree.nodeCount; i++) {
                int type = tree.nodeType[i];
                if ((type == JsSyntaxTree.N_METHOD || type == JsSyntaxTree.N_GETTER ||
                        type == JsSyntaxTree.N_SETTER || type == JsSyntaxTree.N_PROPERTY) &&
                        word.equals(tree.nodeName[i])) {
                    if (isIdentifierSpan(tree, i, text, wordStart, word)) {
                        int parent = tree.nodeParent[i];
                        if (parent > 0 && parent < tree.nodeCount && tree.nodeType[parent] == JsSyntaxTree.N_CLASS_DECL) {
                            classMemberNodeId = i;
                            declaringClassNodeId = parent;
                            break;
                        }
                    }
                }
            }
        }

        List<LspLocation> locations = new ArrayList<>();

        if (isMemberAccess && "this".equals(receiver)) {
            // Renaming this.<prop> inside a class
            renameThisProperty(doc, text, word, wordStart, tree, scopeTree, tokens, locations);
        } else if (declaringClassNodeId > 0 && classMemberNodeId > 0) {
            // Renaming a class method/getter/setter/property from its declaration
            renameClassMember(doc, text, word, declaringClassNodeId, classMemberNodeId, tree, scopeTree, tokens, locations);
        } else if (isMemberAccess && receiver != null) {
            // Renaming a member invocation on an object or class: e.g. myClass.getProfile() or Student.getProfile()
            renameMemberInvocation(doc, text, word, receiver, receiverStart, wordStart, tree, scopeTree, tokens, locations);
        } else {
            // Standard lexical rename: parameter, local variable, function, class, etc.
            renameLexicalSymbol(doc, text, word, wordStart, tree, scopeTree, tokens, locations);
        }

        return deduplicateLocations(locations);
    }

    private static void renameThisProperty(LspDocument doc, String text, String word, int wordStart,
                                           JsSyntaxTree tree, ScopeTree scopeTree, TokenStream tokens,
                                           List<LspLocation> locations) {
        int scopeId = scopeTree != null ? scopeTree.findScopeAt(wordStart, tree) : 0;
        int classNodeId = findEnclosingClass(tree, scopeTree, scopeId);

        if (classNodeId > 0) {
            int classStart = tree.nodeStart[classNodeId];
            int classEnd = tree.nodeEnd[classNodeId];
            if (classEnd <= 0 || classEnd > text.length()) classEnd = text.length();

            // All this.<word> references within the class
            scanThisMemberOccurrences(doc, text, word, classStart, classEnd, tokens, locations);

            // Class field declarations
            int child = tree.nodeChild[classNodeId];
            while (child > 0 && child < tree.nodeCount) {
                int type = tree.nodeType[child];
                if ((type == JsSyntaxTree.N_PROPERTY || type == JsSyntaxTree.N_METHOD ||
                        type == JsSyntaxTree.N_GETTER || type == JsSyntaxTree.N_SETTER) &&
                        word.equals(tree.nodeName[child])) {
                    addDeclarationLocation(doc, text, word, tree, child, locations);
                } else if (type == JsSyntaxTree.N_METHOD && "constructor".equals(tree.nodeName[child])) {
                    // Check parameter properties: constructor(public name: string)
                    int paramChild = tree.nodeChild[child];
                    while (paramChild > 0 && paramChild < tree.nodeCount) {
                        if (tree.nodeType[paramChild] == JsSyntaxTree.N_PARAM &&
                                (tree.nodeExtra[paramChild] & JsSyntaxTree.FLAG_PARAM_PROP) != 0 &&
                                word.equals(tree.nodeName[paramChild])) {
                            addLocation(doc, text, tree.nodeStart[paramChild], word.length(), locations);
                        }
                        paramChild = tree.nodeSibling[paramChild];
                    }
                }
                child = tree.nodeSibling[child];
            }

            // Typed instance property references
            String className = tree.nodeName[classNodeId];
            List<String> instanceVars = findVariablesOfType(tree, className);
            for (String varName : instanceVars) {
                scanMemberOccurrences(doc, text, word, varName, 0, text.length(), tokens, locations);
            }
        } else {
            // Standalone function with this.prop
            int funcNodeId = findEnclosingFunction(tree, scopeTree, scopeId);
            int funcStart = (funcNodeId > 0) ? tree.nodeStart[funcNodeId] : 0;
            int funcEnd = (funcNodeId > 0 && tree.nodeEnd[funcNodeId] > funcStart) ? tree.nodeEnd[funcNodeId] : text.length();
            scanThisMemberOccurrences(doc, text, word, funcStart, funcEnd, tokens, locations);
        }
    }

    private static void renameClassMember(LspDocument doc, String text, String word,
                                          int declaringClassNodeId, int classMemberNodeId,
                                          JsSyntaxTree tree, ScopeTree scopeTree, TokenStream tokens,
                                          List<LspLocation> locations) {
        // Declaration site
        addDeclarationLocation(doc, text, word, tree, classMemberNodeId, locations);

        // Internal this.<word> references
        int classStart = tree.nodeStart[declaringClassNodeId];
        int classEnd = tree.nodeEnd[declaringClassNodeId];
        if (classEnd <= 0 || classEnd > text.length()) classEnd = text.length();
        scanThisMemberOccurrences(doc, text, word, classStart, classEnd, tokens, locations);

        // Static member invocations
        String className = tree.nodeName[declaringClassNodeId];
        if (className != null && !className.isEmpty()) {
            scanMemberOccurrences(doc, text, word, className, 0, text.length(), tokens, locations);
        }

        // Typed instance invocations
        List<String> instanceVars = findVariablesOfType(tree, className);
        for (String varName : instanceVars) {
            scanMemberOccurrences(doc, text, word, varName, 0, text.length(), tokens, locations);
        }

        // If member name is unique across classes, match remaining member calls
        if (isUniqueClassMemberInFile(tree, word)) {
            scanMemberOccurrences(doc, text, word, null, 0, text.length(), tokens, locations);
        }
    }

    private static void renameMemberInvocation(LspDocument doc, String text, String word,
                                               String receiver, int receiverStart, int wordStart,
                                               JsSyntaxTree tree, ScopeTree scopeTree, TokenStream tokens,
                                               List<LspLocation> locations) {
        if (tree == null || scopeTree == null) {
            addLocation(doc, text, wordStart, word.length(), locations);
            return;
        }

        int rScope = scopeTree.findScopeAt(receiverStart, tree);
        int[] resolved = scopeTree.lookupSymbol(receiver, rScope);

        int targetClassNodeId = -1;
        if (resolved != null) {
            int varNode = resolved[1];
            String typeName = tree.nodeTypeAnn[varNode];
            if (typeName != null) {
                targetClassNodeId = findClassByName(tree, typeName);
            }
        }

        if (targetClassNodeId <= 0) {
            // Receiver might be the class name itself for static methods (e.g. Student.getProfile())
            targetClassNodeId = findClassByName(tree, receiver);
        }

        if (targetClassNodeId <= 0) {
            // Find unique class declaring this member
            targetClassNodeId = findSingleClassDeclaringMember(tree, word);
        }

        if (targetClassNodeId > 0) {
            int memberNodeId = findMemberInClass(tree, targetClassNodeId, word);
            if (memberNodeId > 0) {
                renameClassMember(doc, text, word, targetClassNodeId, memberNodeId, tree, scopeTree, tokens, locations);
                return;
            } else if (hasThisPropertyInClass(tree, targetClassNodeId, word)) {
                renameThisProperty(doc, text, word, tree.nodeStart[targetClassNodeId], tree, scopeTree, tokens, locations);
                return;
            }
        }

        // Fallback: match receiver.<word> across the file
        scanMemberOccurrences(doc, text, word, receiver, 0, text.length(), tokens, locations);
    }

    private static void renameLexicalSymbol(LspDocument doc, String text, String word, int wordStart,
                                            JsSyntaxTree tree, ScopeTree scopeTree, TokenStream tokens,
                                            List<LspLocation> locations) {
        if (scopeTree == null || tree == null) return;
        int scopeId = scopeTree.findScopeAt(wordStart, tree);
        if (scopeId == -1) return;

        int[] entry = scopeTree.lookupSymbol(word, scopeId);
        if (entry == null) return;

        int declScopeId = entry[0];
        int declNodeId = entry[1];
        int declType = entry[2];

        // Declaration site
        int wLen = word.length();
        if (declType == JsSyntaxTree.N_PARAM) {
            int paramStart = tree.nodeStart[declNodeId];
            addLocation(doc, text, paramStart, wLen, locations);
        } else {
            int dStart = tree.nodeStart[declNodeId];
            int dEnd = tree.nodeEnd[declNodeId];
            int nameIdx = findExactWord(text, word, dStart, dEnd);
            if (nameIdx != -1) {
                addLocation(doc, text, nameIdx, wLen, locations);
            }
        }

        // References within lexical scope
        int[] offsets = scopeTree.findAllReferences(word, declScopeId, tree);
        for (int nodeId : offsets) {
            int charOffset = tree.nodeStart[nodeId];
            // Skip member property accesses (e.g. this.word or obj.word)
            if (isPrecededByDot(tokens, text, charOffset)) {
                continue;
            }
            if (charOffset + wLen <= text.length() && text.regionMatches(charOffset, word, 0, wLen)) {
                addLocation(doc, text, charOffset, wLen, locations);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Token scanning helpers
    // -------------------------------------------------------------------------

    private static void scanThisMemberOccurrences(LspDocument doc, String text, String word,
                                                  int startOffset, int endOffset,
                                                  TokenStream tokens, List<LspLocation> locations) {
        int len = Math.min(endOffset, text.length());
        int wLen = word.length();

        for (int i = Math.max(0, startOffset); i < len; ) {
            byte t = tokens.types[i];
            int tokenStart = tokens.tokenStart[i];
            int tokenEnd = i + 1;
            while (tokenEnd < len && tokens.tokenStart[tokenEnd] == tokenStart) {
                tokenEnd++;
            }

            if (t == TokenStream.TK_KEYWORD && (tokenEnd - tokenStart == 4) && "this".equals(text.substring(tokenStart, tokenEnd))) {
                int nextTok = tokenEnd;
                while (nextTok < len && (tokens.types[nextTok] == TokenStream.TK_WHITESPACE || tokens.types[nextTok] == TokenStream.TK_COMMENT)) {
                    nextTok++;
                }
                if (nextTok < len && tokens.types[nextTok] == TokenStream.TK_PUNCT && text.charAt(nextTok) == '.') {
                    int afterDot = nextTok + 1;
                    while (afterDot < len && (tokens.types[afterDot] == TokenStream.TK_WHITESPACE || tokens.types[afterDot] == TokenStream.TK_COMMENT)) {
                        afterDot++;
                    }
                    if (afterDot < len && tokens.types[afterDot] == TokenStream.TK_IDENTIFIER) {
                        int propStart = tokens.tokenStart[afterDot];
                        int propEnd = propStart;
                        while (propEnd < len && tokens.tokenStart[propEnd] == propStart) {
                            propEnd++;
                        }
                        if (propEnd - propStart == wLen && text.regionMatches(propStart, word, 0, wLen)) {
                            addLocation(doc, text, propStart, wLen, locations);
                            i = propEnd;
                            continue;
                        }
                    }
                }
            }
            i = tokenEnd;
        }
    }

    private static void scanMemberOccurrences(LspDocument doc, String text, String word,
                                              String targetReceiver,
                                              int startOffset, int endOffset,
                                              TokenStream tokens, List<LspLocation> locations) {
        int len = Math.min(endOffset, text.length());
        int wLen = word.length();
        int rLen = targetReceiver != null ? targetReceiver.length() : 0;

        for (int i = Math.max(0, startOffset); i < len; ) {
            byte t = tokens.types[i];
            int tokenStart = tokens.tokenStart[i];
            int tokenEnd = i + 1;
            while (tokenEnd < len && tokens.tokenStart[tokenEnd] == tokenStart) {
                tokenEnd++;
            }

            if (t == TokenStream.TK_IDENTIFIER || t == TokenStream.TK_KEYWORD) {
                boolean receiverMatches = false;
                if (targetReceiver == null) {
                    // Match any receiver except 'this'
                    receiverMatches = (tokenEnd - tokenStart != 4) || !"this".equals(text.substring(tokenStart, tokenEnd));
                } else if (tokenEnd - tokenStart == rLen && text.regionMatches(tokenStart, targetReceiver, 0, rLen)) {
                    receiverMatches = true;
                }

                if (receiverMatches) {
                    int nextTok = tokenEnd;
                    while (nextTok < len && (tokens.types[nextTok] == TokenStream.TK_WHITESPACE || tokens.types[nextTok] == TokenStream.TK_COMMENT)) {
                        nextTok++;
                    }
                    if (nextTok < len && tokens.types[nextTok] == TokenStream.TK_PUNCT && text.charAt(nextTok) == '.') {
                        int afterDot = nextTok + 1;
                        while (afterDot < len && (tokens.types[afterDot] == TokenStream.TK_WHITESPACE || tokens.types[afterDot] == TokenStream.TK_COMMENT)) {
                            afterDot++;
                        }
                        if (afterDot < len && tokens.types[afterDot] == TokenStream.TK_IDENTIFIER) {
                            int propStart = tokens.tokenStart[afterDot];
                            int propEnd = propStart;
                            while (propEnd < len && tokens.tokenStart[propEnd] == propStart) {
                                propEnd++;
                            }
                            if (propEnd - propStart == wLen && text.regionMatches(propStart, word, 0, wLen)) {
                                addLocation(doc, text, propStart, wLen, locations);
                                i = propEnd;
                                continue;
                            }
                        }
                    }
                }
            }
            i = tokenEnd;
        }
    }

    private static boolean isPrecededByDot(TokenStream tokens, String text, int offset) {
        int prev = findPrevSignificantToken(tokens, text, offset);
        return prev >= 0 && tokens.types[prev] == TokenStream.TK_PUNCT && text.charAt(tokens.tokenStart[prev]) == '.';
    }

    private static int findPrevSignificantToken(TokenStream tokens, String text, int offset) {
        int prev = offset - 1;
        while (prev >= 0) {
            byte t = tokens.types[prev];
            if (t != TokenStream.TK_WHITESPACE && t != TokenStream.TK_COMMENT) {
                return tokens.tokenStart[prev];
            }
            prev = tokens.tokenStart[prev] - 1;
        }
        return -1;
    }

    private static int findPrevSignificantTokenBefore(TokenStream tokens, String text, int offset) {
        int prev = offset - 1;
        while (prev >= 0) {
            byte t = tokens.types[prev];
            if (t != TokenStream.TK_WHITESPACE && t != TokenStream.TK_COMMENT) {
                return tokens.tokenStart[prev];
            }
            prev = tokens.tokenStart[prev] - 1;
        }
        return -1;
    }

    // -------------------------------------------------------------------------
    // AST & Scope Navigation Helpers
    // -------------------------------------------------------------------------

    private static int findEnclosingClass(JsSyntaxTree tree, ScopeTree scopeTree, int scopeId) {
        if (tree == null || scopeTree == null) return -1;
        int currScope = scopeId;
        int guard = 0;
        while (currScope > 0 && currScope < scopeTree.scopeCount && ++guard <= scopeTree.scopeCount) {
            int node = scopeTree.scopeNode[currScope];
            while (node > 0 && node < tree.nodeCount) {
                if (tree.nodeType[node] == JsSyntaxTree.N_CLASS_DECL) {
                    return node;
                }
                node = tree.nodeParent[node];
            }
            currScope = scopeTree.scopeParent[currScope];
        }
        return -1;
    }

    private static int findEnclosingFunction(JsSyntaxTree tree, ScopeTree scopeTree, int scopeId) {
        if (tree == null || scopeTree == null) return -1;
        int currScope = scopeId;
        int guard = 0;
        while (currScope > 0 && currScope < scopeTree.scopeCount && ++guard <= scopeTree.scopeCount) {
            int node = scopeTree.scopeNode[currScope];
            while (node > 0 && node < tree.nodeCount) {
                int type = tree.nodeType[node];
                if (type == JsSyntaxTree.N_FUNC_DECL || type == JsSyntaxTree.N_ARROW_FUNC || type == JsSyntaxTree.N_METHOD) {
                    return node;
                }
                node = tree.nodeParent[node];
            }
            currScope = scopeTree.scopeParent[currScope];
        }
        return -1;
    }

    private static int findClassByName(JsSyntaxTree tree, String className) {
        if (tree == null || className == null) return -1;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_CLASS_DECL && className.equals(tree.nodeName[i])) {
                return i;
            }
        }
        return -1;
    }

    private static int findMemberInClass(JsSyntaxTree tree, int classNodeId, String memberName) {
        if (tree == null || classNodeId <= 0 || memberName == null) return -1;
        int child = tree.nodeChild[classNodeId];
        while (child > 0 && child < tree.nodeCount) {
            int type = tree.nodeType[child];
            if ((type == JsSyntaxTree.N_METHOD || type == JsSyntaxTree.N_GETTER ||
                    type == JsSyntaxTree.N_SETTER || type == JsSyntaxTree.N_PROPERTY) &&
                    memberName.equals(tree.nodeName[child])) {
                return child;
            }
            child = tree.nodeSibling[child];
        }
        return -1;
    }

    private static boolean hasThisPropertyInClass(JsSyntaxTree tree, int classNodeId, String memberName) {
        if (tree == null || classNodeId <= 0 || memberName == null) return false;
        String[] shape = tree.shapeTable.get(classNodeId);
        if (shape != null) {
            for (String prop : shape) {
                if (memberName.equals(prop)) return true;
            }
        }
        return false;
    }

    private static int findSingleClassDeclaringMember(JsSyntaxTree tree, String memberName) {
        if (tree == null || memberName == null) return -1;
        int matchedClass = -1;
        int matchCount = 0;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_CLASS_DECL) {
                if (findMemberInClass(tree, i, memberName) > 0 || hasThisPropertyInClass(tree, i, memberName)) {
                    matchedClass = i;
                    matchCount++;
                }
            }
        }
        return matchCount == 1 ? matchedClass : -1;
    }

    private static boolean isUniqueClassMemberInFile(JsSyntaxTree tree, String memberName) {
        if (tree == null || memberName == null) return false;
        int matchCount = 0;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_CLASS_DECL) {
                if (findMemberInClass(tree, i, memberName) > 0 || hasThisPropertyInClass(tree, i, memberName)) {
                    matchCount++;
                }
            }
        }
        return matchCount == 1;
    }

    private static List<String> findVariablesOfType(JsSyntaxTree tree, String className) {
        List<String> vars = new ArrayList<>();
        if (tree == null || className == null || className.isEmpty()) return vars;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_VAR_DECL) {
                String typeAnn = tree.nodeTypeAnn[i];
                if (className.equals(typeAnn) && tree.nodeName[i] != null && !"{destructure}".equals(tree.nodeName[i])) {
                    vars.add(tree.nodeName[i]);
                }
            }
        }
        return vars;
    }

    private static boolean isIdentifierSpan(JsSyntaxTree tree, int nodeId, String text, int wordStart, String word) {
        int start = tree.nodeStart[nodeId];
        int end = tree.nodeEnd[nodeId];
        int idx = text.indexOf(word, start);
        return idx == wordStart && (end <= 0 || idx < end);
    }

    private static void addDeclarationLocation(LspDocument doc, String text, String word,
                                               JsSyntaxTree tree, int memberNodeId,
                                               List<LspLocation> locations) {
        int start = tree.nodeStart[memberNodeId];
        int end = tree.nodeEnd[memberNodeId];
        int idx = text.indexOf(word, start);
        if (idx != -1 && (end <= 0 || idx < end)) {
            addLocation(doc, text, idx, word.length(), locations);
        }
    }

    private static void addLocation(LspDocument doc, String text, int charOffset, int length, List<LspLocation> locations) {
        if (charOffset < 0 || charOffset + length > text.length()) return;
        LspPosition start = SymbolExtractor.offsetToPosition(text, charOffset);
        LspPosition end = SymbolExtractor.offsetToPosition(text, charOffset + length);
        locations.add(new LspLocation(doc.uri, new LspRange(start, end)));
    }

    private static List<LspLocation> deduplicateLocations(List<LspLocation> locations) {
        if (locations == null || locations.isEmpty()) return Collections.emptyList();
        Set<String> seen = new HashSet<>();
        List<LspLocation> result = new ArrayList<>(locations.size());
        for (LspLocation loc : locations) {
            if (loc == null || loc.range == null || loc.range.start == null || loc.range.end == null) continue;
            String key = loc.uri + ":" + loc.range.start.line + ":" + loc.range.start.character + "-" +
                    loc.range.end.line + ":" + loc.range.end.character;
            if (seen.add(key)) {
                result.add(loc);
            }
        }
        return result;
    }

    private static String extractWord(String text, int offset) {
        if (text == null || offset < 0 || offset > text.length()) return "";
        int start = Math.min(offset, text.length() - 1);
        if (start >= 0 && !isWordChar(text.charAt(start)) && start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }
        int end = start;
        while (end < text.length() && isWordChar(text.charAt(end))) {
            end++;
        }
        return start < end ? text.substring(start, end) : "";
    }

    private static int findWordStart(String text, int offset) {
        if (text == null || offset < 0 || offset > text.length()) return -1;
        int start = Math.min(offset, text.length() - 1);
        if (start >= 0 && !isWordChar(text.charAt(start)) && start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }
        return (start >= 0 && start < text.length() && isWordChar(text.charAt(start))) ? start : -1;
    }

    private static int findExactWord(String text, String word, int start, int end) {
        int wLen = word.length();
        int idx = text.indexOf(word, start);
        while (idx != -1 && (end <= 0 || idx < end)) {
            boolean leftOk = (idx == 0) || !isWordChar(text.charAt(idx - 1));
            boolean rightOk = (idx + wLen >= text.length()) || !isWordChar(text.charAt(idx + wLen));
            if (leftOk && rightOk) {
                return idx;
            }
            idx = text.indexOf(word, idx + 1);
        }
        return -1;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static boolean isNonRenamableKeyword(String word) {
        switch (word) {
            case "this":
            case "super":
            case "constructor":
            case "function":
            case "class":
            case "return":
            case "if":
            case "else":
            case "for":
            case "while":
            case "do":
            case "switch":
            case "case":
            case "default":
            case "break":
            case "continue":
            case "try":
            case "catch":
            case "finally":
            case "throw":
            case "new":
            case "delete":
            case "typeof":
            case "instanceof":
            case "void":
            case "in":
            case "of":
            case "var":
            case "let":
            case "const":
            case "import":
            case "export":
            case "from":
            case "as":
            case "true":
            case "false":
            case "null":
            case "undefined":
            case "string":
            case "number":
            case "boolean":
            case "any":
            case "unknown":
            case "never":
            case "symbol":
            case "bigint":
            case "object":
                return true;
            default:
                return false;
        }
    }
}
