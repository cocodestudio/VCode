package com.cocode.vcode.ide.core.language.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * High-performance, zero-allocation lexical state scanner for JSON and JSONC documents.
 * Accurately determines the grammatical context at the cursor: object key, object value,
 * array element, comments, or file-level templates.
 */
public final class JsonContextScanner {

    public enum ContextType {
        OBJECT_KEY,      // Inside an object before ':' or after '{' / ','
        OBJECT_VALUE,    // Inside an object after ':' and before ',' / '}'
        ARRAY_ELEMENT,   // Inside an array '[' ... ']'
        FILE_EMPTY,      // Empty or whitespace-only document
        COMMENT,         // Inside line comment (//) or block comment (/* */)
        NONE             // Outside root, after closing brace, or invalid syntax
    }

    public static final class JsonContext {
        public final ContextType type;
        public final String word;               // typed word/prefix before cursor
        public final boolean inQuotes;          // whether cursor is inside quotes
        public final int quoteStart;            // offset of opening quote (-1 if not in quotes)
        public final int replaceLength;         // explicit characters to replace
        public final String activeKey;          // for OBJECT_VALUE or ARRAY_ELEMENT: the property name
        public final List<String> propertyPath; // hierarchy of enclosing keys (e.g. ["compilerOptions"])
        public final Set<String> existingKeys;  // keys already defined in current object (to exclude)
        public final boolean isInsideComment;

        public JsonContext(ContextType type, String word, boolean inQuotes, int quoteStart,
                           int replaceLength, String activeKey, List<String> propertyPath,
                           Set<String> existingKeys, boolean isInsideComment) {
            this.type = type;
            this.word = word != null ? word : "";
            this.inQuotes = inQuotes;
            this.quoteStart = quoteStart;
            this.replaceLength = replaceLength;
            this.activeKey = activeKey != null ? activeKey : "";
            this.propertyPath = propertyPath != null ? propertyPath : Collections.emptyList();
            this.existingKeys = existingKeys != null ? existingKeys : Collections.emptySet();
            this.isInsideComment = isInsideComment;
        }
    }

    private static final class Scope {
        final boolean isObject;
        final String parentKey;
        final Set<String> keys = new HashSet<>();
        boolean expectValue = false;
        boolean expectColon = false;
        String activeKey = null;

        Scope(boolean isObject, String parentKey) {
            this.isObject = isObject;
            this.parentKey = parentKey;
        }
    }

    private JsonContextScanner() {
    }

    /**
     * Scans the document up to and around {@code cursorPos} to determine the active grammatical context.
     *
     * @param text      the full JSON/JSONC text
     * @param cursorPos 0-based cursor offset
     * @return the resolved {@link JsonContext}
     */
    public static JsonContext scan(String text, int cursorPos) {
        return scan(text, cursorPos, true);
    }

    /**
     * Scans the document up to and around {@code cursorPos} to determine the active grammatical context.
     *
     * @param text      the full JSON/JSONC text
     * @param cursorPos 0-based cursor offset
     * @param isJsonc   whether comments should be parsed and tolerated
     * @return the resolved {@link JsonContext}
     */
    public static JsonContext scan(String text, int cursorPos, boolean isJsonc) {
        if (text == null || cursorPos < 0 || cursorPos > text.length()) {
            return new JsonContext(ContextType.NONE, "", false, -1, 0, "", null, null, false);
        }

        if (text.trim().isEmpty()) {
            return new JsonContext(ContextType.FILE_EMPTY, "", false, -1, 0, "", null, null, false);
        }

        int len = text.length();
        Stack<Scope> stack = new Stack<>();

        ContextType detectedType = null;
        String detectedWord = "";
        boolean detectedInQuotes = false;
        int detectedQuoteStart = -1;
        int detectedReplaceLength = 0;
        String detectedActiveKey = null;
        List<String> detectedPath = null;
        Scope targetScope = null;

        int i = 0;
        while (i < len) {
            // Check if cursor is reached outside any token or string
            if (i == cursorPos && detectedType == null) {
                targetScope = !stack.isEmpty() ? stack.peek() : null;
                detectedPath = buildPath(stack);
                detectedInQuotes = false;
                detectedQuoteStart = -1;
                detectedWord = extractWordBeforeCursor(text, cursorPos);
                detectedReplaceLength = detectedWord.length();

                if (targetScope == null) {
                    detectedType = ContextType.NONE;
                } else if (targetScope.isObject) {
                    if (targetScope.expectValue) {
                        detectedType = ContextType.OBJECT_VALUE;
                        detectedActiveKey = targetScope.activeKey;
                    } else {
                        detectedType = ContextType.OBJECT_KEY;
                    }
                } else {
                    detectedType = ContextType.ARRAY_ELEMENT;
                    detectedActiveKey = targetScope.parentKey;
                }
            }

            char c = text.charAt(i);

            // 1. Whitespace
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // 2. Comments (JSONC)
            if (isJsonc && c == '/' && i + 1 < len) {
                char next = text.charAt(i + 1);
                if (next == '/') {
                    int commentStart = i;
                    i += 2;
                    while (i < len && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
                        i++;
                    }
                    if (cursorPos >= commentStart && cursorPos <= i) {
                        return new JsonContext(ContextType.COMMENT, "", false, -1, 0, "", null, null, true);
                    }
                    continue;
                } else if (next == '*') {
                    int commentStart = i;
                    i += 2;
                    while (i < len) {
                        if (text.charAt(i) == '*' && i + 1 < len && text.charAt(i + 1) == '/') {
                            i += 2;
                            break;
                        }
                        i++;
                    }
                    if (cursorPos >= commentStart && cursorPos <= i) {
                        return new JsonContext(ContextType.COMMENT, "", false, -1, 0, "", null, null, true);
                    }
                    continue;
                }
            }

            // 3. String literals
            if (c == '"' || c == '\'') {
                char quoteChar = c;
                int start = i;
                i++;
                StringBuilder sb = new StringBuilder();
                boolean closed = false;
                while (i < len) {
                    char sc = text.charAt(i);
                    if (sc == '\\' && i + 1 < len) {
                        sb.append(sc).append(text.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (sc == quoteChar) {
                        closed = true;
                        i++; // consume closing quote
                        break;
                    }
                    if (sc == '\n' || sc == '\r') {
                        break;
                    }
                    sb.append(sc);
                    i++;
                }

                String content = sb.toString();
                int end = i;
                boolean cursorInString = (cursorPos > start && cursorPos <= (closed ? end : end));

                Scope currentScope = !stack.isEmpty() ? stack.peek() : null;

                if (cursorInString && detectedType == null) {
                    targetScope = currentScope;
                    detectedPath = buildPath(stack);
                    detectedInQuotes = true;
                    detectedQuoteStart = start;
                    int wordEnd = Math.min(cursorPos, closed ? end - 1 : end);
                    detectedWord = text.substring(start + 1, wordEnd);
                    detectedReplaceLength = cursorPos - start;

                    if (currentScope == null) {
                        detectedType = ContextType.NONE;
                    } else if (currentScope.isObject) {
                        if (currentScope.expectValue) {
                            detectedType = ContextType.OBJECT_VALUE;
                            detectedActiveKey = currentScope.activeKey;
                            // For value string, replaceLength inside quotes defaults to typed word length
                            detectedReplaceLength = detectedWord.length();
                        } else {
                            detectedType = ContextType.OBJECT_KEY;
                        }
                    } else {
                        detectedType = ContextType.ARRAY_ELEMENT;
                        detectedActiveKey = currentScope.parentKey;
                        detectedReplaceLength = detectedWord.length();
                    }
                }

                // Update scope key/value state
                if (currentScope != null && currentScope.isObject) {
                    if (!currentScope.expectValue) {
                        currentScope.activeKey = content;
                        currentScope.expectColon = true;
                        if (!cursorInString) {
                            currentScope.keys.add(content);
                        }
                    } else {
                        currentScope.expectValue = false;
                        currentScope.expectColon = false;
                        currentScope.activeKey = null;
                    }
                }
                continue;
            }

            // 4. Object open
            if (c == '{') {
                String parentKey = null;
                if (!stack.isEmpty()) {
                    Scope parent = stack.peek();
                    if (parent.isObject && parent.expectValue) {
                        parentKey = parent.activeKey;
                        parent.expectValue = false;
                    } else if (!parent.isObject) {
                        parentKey = parent.parentKey;
                    }
                }
                stack.push(new Scope(true, parentKey));
                i++;
                continue;
            }

            // 5. Object close
            if (c == '}') {
                if (!stack.isEmpty() && stack.peek().isObject) {
                    stack.pop();
                }
                i++;
                continue;
            }

            // 6. Array open
            if (c == '[') {
                String parentKey = null;
                if (!stack.isEmpty()) {
                    Scope parent = stack.peek();
                    if (parent.isObject && parent.expectValue) {
                        parentKey = parent.activeKey;
                        parent.expectValue = false;
                    } else if (!parent.isObject) {
                        parentKey = parent.parentKey;
                    }
                }
                stack.push(new Scope(false, parentKey));
                i++;
                continue;
            }

            // 7. Array close
            if (c == ']') {
                if (!stack.isEmpty() && !stack.peek().isObject) {
                    stack.pop();
                }
                i++;
                continue;
            }

            // 8. Colon
            if (c == ':') {
                if (!stack.isEmpty() && stack.peek().isObject) {
                    stack.peek().expectColon = false;
                    stack.peek().expectValue = true;
                }
                i++;
                continue;
            }

            // 9. Comma
            if (c == ',') {
                if (!stack.isEmpty()) {
                    Scope top = stack.peek();
                    if (top.isObject) {
                        top.expectColon = false;
                        top.expectValue = false;
                        top.activeKey = null;
                    }
                }
                i++;
                continue;
            }

            // 10. Unquoted identifiers / literals (true, false, null, numbers, unquoted fragment)
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '-') {
                int start = i;
                while (i < len) {
                    char uc = text.charAt(i);
                    if (Character.isLetterOrDigit(uc) || uc == '_' || uc == '$' || uc == '-' || uc == '.') {
                        i++;
                    } else {
                        break;
                    }
                }
                String token = text.substring(start, i);
                boolean cursorInWord = (cursorPos >= start && cursorPos <= i);

                Scope currentScope = !stack.isEmpty() ? stack.peek() : null;

                if (cursorInWord && detectedType == null) {
                    targetScope = currentScope;
                    detectedPath = buildPath(stack);
                    detectedInQuotes = false;
                    detectedQuoteStart = -1;
                    detectedWord = text.substring(start, cursorPos);
                    detectedReplaceLength = detectedWord.length();

                    if (currentScope == null) {
                        detectedType = ContextType.NONE;
                    } else if (currentScope.isObject) {
                        if (currentScope.expectValue) {
                            detectedType = ContextType.OBJECT_VALUE;
                            detectedActiveKey = currentScope.activeKey;
                        } else {
                            detectedType = ContextType.OBJECT_KEY;
                        }
                    } else {
                        detectedType = ContextType.ARRAY_ELEMENT;
                        detectedActiveKey = currentScope.parentKey;
                    }
                }

                if (currentScope != null && currentScope.isObject) {
                    if (currentScope.expectValue) {
                        currentScope.expectValue = false;
                    } else if (!cursorInWord) {
                        currentScope.keys.add(token);
                    }
                }
                continue;
            }

            i++;
        }

        // Handle cursor at the very end of file
        if (detectedType == null) {
            targetScope = !stack.isEmpty() ? stack.peek() : null;
            detectedPath = buildPath(stack);
            detectedInQuotes = false;
            detectedQuoteStart = -1;
            detectedWord = extractWordBeforeCursor(text, cursorPos);
            detectedReplaceLength = detectedWord.length();

            if (targetScope == null) {
                detectedType = ContextType.NONE;
            } else if (targetScope.isObject) {
                if (targetScope.expectValue) {
                    detectedType = ContextType.OBJECT_VALUE;
                    detectedActiveKey = targetScope.activeKey;
                } else {
                    detectedType = ContextType.OBJECT_KEY;
                }
            } else {
                detectedType = ContextType.ARRAY_ELEMENT;
                detectedActiveKey = targetScope.parentKey;
            }
        }

        Set<String> siblingKeys = (targetScope != null) ? targetScope.keys : Collections.emptySet();
        return new JsonContext(detectedType, detectedWord, detectedInQuotes, detectedQuoteStart,
                detectedReplaceLength, detectedActiveKey, detectedPath, siblingKeys, false);
    }

    private static List<String> buildPath(Stack<Scope> stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        List<String> path = new ArrayList<>();
        for (Scope s : stack) {
            if (s.parentKey != null && !s.parentKey.isEmpty()) {
                path.add(s.parentKey);
            }
        }
        return path;
    }

    private static String extractWordBeforeCursor(String text, int cursor) {
        if (text == null || cursor <= 0) return "";
        int start = cursor - 1;
        while (start >= 0) {
            char c = text.charAt(start);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '-') {
                start--;
            } else {
                break;
            }
        }
        return text.substring(start + 1, cursor);
    }
}
