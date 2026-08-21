package com.cocode.vcode.ide.core.language.js;

import java.util.ArrayList;
import java.util.List;

/**
 * Scope parser for JavaScript, analyzing declared variables, functions, and block scopes.
 */
public class JsScopeParser {

    /**
     * Builds a tree of ScopeBlocks for a JS document by tracking { and } braces.
     * Skips over strings and comments accurately.
     */
    public static List<ScopeBlock> buildScopes(String text) {
        List<ScopeBlock> blocks = new ArrayList<>();
        ScopeBlock root = new ScopeBlock(0, null);
        blocks.add(root);
        ScopeBlock curr = root;

        int len = text.length();
        int state = 0; // 0=normal, 1=line_comment, 2=block_comment, 3=string_double, 4=string_single, 5=string_template
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            char next = (i + 1 < len) ? text.charAt(i + 1) : 0;
            switch (state) {
                case 0:
                    if (c == '/' && next == '/') {
                        state = 1;
                        i++;
                    } else if (c == '/' && next == '*') {
                        state = 2;
                        i++;
                    } else if (c == '"') {
                        state = 3;
                    } else if (c == '\'') {
                        state = 4;
                    } else if (c == '`') {
                        state = 5;
                    } else if (c == '{') {
                        ScopeBlock b = new ScopeBlock(i, curr);
                        blocks.add(b);
                        curr = b;
                    } else if (c == '}') {
                        curr.end = i;
                        if (curr.parent != null) curr = curr.parent;
                    }
                    break;
                case 1:
                    if (c == '\n') state = 0;
                    break;
                case 2:
                    if (c == '*' && next == '/') {
                        state = 0;
                        i++;
                    }
                    break;
                case 3:
                    if (c == '\\') i++;
                    else if (c == '"') state = 0;
                    break;
                case 4:
                    if (c == '\\') i++;
                    else if (c == '\'') state = 0;
                    break;
                case 5:
                    if (c == '\\') i++;
                    else if (c == '`') state = 0;
                    break;
            }
        }
        return blocks;
    }

    /**
     * Finds the most specific scope block that contains the given position.
     */
    public static ScopeBlock findDeepestScope(List<ScopeBlock> scopes, int pos) {
        ScopeBlock deepest = null;
        int minSize = Integer.MAX_VALUE;
        for (ScopeBlock b : scopes) {
            if (b.contains(pos)) {
                int size = b.end - b.start;
                if (size < minSize) {
                    minSize = size;
                    deepest = b;
                }
            }
        }
        return deepest;
    }

    public static class ScopeBlock {
        public int start;
        public int end;
        public ScopeBlock parent;

        public ScopeBlock(int start, ScopeBlock parent) {
            this.start = start;
            this.parent = parent;
            this.end = Integer.MAX_VALUE;
        }

        public boolean contains(int pos) {
            return pos >= start && pos <= end;
        }
    }
}
