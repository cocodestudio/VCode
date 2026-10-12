package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;

import java.util.Map;

/**
 * High-performance expression type inference engine for JavaScript and TypeScript.
 * Evaluates token spans directly without AST allocation overhead, with text-based fallback
 * for autocomplete snippets and LSP hover/completion pipelines.
 */
public final class JsTypeInference {

    private JsTypeInference() {
    }

    public static String inferFromTokens(String source, TokenStream stream, int startTok, int endTok, JsSyntaxTree tree, ScopeTree scopeTree) {
        return inferFromTokens(source, stream, startTok, endTok, tree, scopeTree, null);
    }

    public static String inferFromTokens(String source, TokenStream stream, int startTok, int endTok, JsSyntaxTree tree, ScopeTree scopeTree, Map<String, String> varTypeMap) {
        if (source == null || stream == null || startTok >= endTok || startTok < 0) {
            return null;
        }

        int start = skipWs(stream, startTok, endTok);
        int end = skipTrailingExpressionTerminators(source, stream, start, endTok);
        if (start >= end) return null;

        // 1. TypeScript 'as <type>' cast at top level (e.g. `expr as number`)
        String asCast = checkAsCast(source, stream, start, end);
        if (asCast != null) {
            return asCast;
        }

        // 2. Unwrap enclosing outer parentheses: e.g. `((10 - 6) * 3)` -> `(10 - 6) * 3`
        boolean unwrapped;
        do {
            unwrapped = false;
            if (start < end && stream.types[start] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[start]) == '(') {
                int matchingClose = findMatchingParen(source, stream, start, end);
                if (matchingClose != -1) {
                    int after = skipWs(stream, skipToken(stream, matchingClose), end);
                    if (after >= end) {
                        start = skipWs(stream, skipToken(stream, start), matchingClose);
                        end = skipTrailingExpressionTerminators(source, stream, start, matchingClose);
                        unwrapped = true;
                    }
                }
            }
        } while (unwrapped && start < end);

        if (start >= end) return null;

        // Re-check 'as' cast after parens unwrap in case inner expression had cast
        asCast = checkAsCast(source, stream, start, end);
        if (asCast != null) {
            return asCast;
        }

        // 3. Scan for top-level binary and relational operators (depth 0)
        String opInferred = scanTopLevelOperators(source, stream, start, end, tree, scopeTree, varTypeMap);
        if (opInferred != null) {
            return opInferred;
        }

        // 4. Unary prefix operators at start of expression
        byte firstType = stream.types[start];
        if (firstType == TokenStream.TK_KEYWORD) {
            String kw = getWord(source, stream, start);
            if ("typeof".equals(kw)) return "@STRING";
            if ("void".equals(kw)) return "@UNDEFINED";
        } else if (firstType == TokenStream.TK_OPERATOR) {
            char opChar = source.charAt(stream.tokenStart[start]);
            if (opChar == '!') return "@BOOLEAN";
            if (opChar == '+' || opChar == '-' || opChar == '~') return "@NUMBER";
        }

        // 5. 'new' constructor calls: e.g. `new Date()`, `new RegExp()`, `new Promise(...)`
        if (firstType == TokenStream.TK_KEYWORD && "new".equals(getWord(source, stream, start))) {
            int nextTok = skipWs(stream, skipToken(stream, start), end);
            if (nextTok < end && (stream.types[nextTok] == TokenStream.TK_IDENTIFIER || stream.types[nextTok] == TokenStream.TK_KEYWORD)) {
                String cls = getWord(source, stream, nextTok);
                switch (cls) {
                    case "Date": return "@DATE";
                    case "RegExp": return "@REGEXP";
                    case "Map": return "@MAP";
                    case "Set": return "@SET";
                    case "Promise": return "@PROMISE";
                    case "Error":
                    case "TypeError":
                    case "RangeError":
                    case "SyntaxError":
                    case "ReferenceError":
                        return "@ERROR";
                    case "Number": return "@NUMBER";
                    case "String": return "@STRING";
                    case "Boolean": return "@BOOLEAN";
                    case "Array": return "@ARRAY";
                    default: return cls;
                }
            }
        }

        // 6. Global built-in calls and runtime functions
        String builtinType = checkBuiltinCalls(source, stream, start, end);
        if (builtinType != null) {
            return builtinType;
        }

        // 7. Literals
        if (firstType == TokenStream.TK_STRING) {
            return "@STRING";
        }
        if (firstType == TokenStream.TK_TEMPLATE || source.charAt(stream.tokenStart[start]) == '`') {
            return "@STRING";
        }
        if (firstType == TokenStream.TK_NUMBER) {
            return "@NUMBER";
        }
        if (firstType == TokenStream.TK_REGEX) {
            return "@REGEXP";
        }
        if (firstType == TokenStream.TK_KEYWORD) {
            String kw = getWord(source, stream, start);
            if ("true".equals(kw) || "false".equals(kw)) return "@BOOLEAN";
            if ("async".equals(kw)) return "@PROMISE";
        }

        // Regex literal starting with '/'
        if (source.charAt(stream.tokenStart[start]) == '/') {
            int after = skipToken(stream, start);
            if (after >= end || (firstType == TokenStream.TK_REGEX)) {
                return "@REGEXP";
            }
        }

        // Array literal: `[...]`
        if (firstType == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[start]) == '[') {
            return inferArrayLiteralType(source, stream, start, end);
        }

        // Object literal: `{...}`
        if (firstType == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[start]) == '{') {
            return "@OBJECT";
        }

        // 8. Chained method calls: e.g. `str.toUpperCase()`, `arr.slice()`, `num.toFixed()`
        String chainType = checkMethodCallReturn(source, stream, start, end);
        if (chainType != null) {
            return chainType;
        }

        // 9. CommonJS require: `require('...')`
        if (firstType == TokenStream.TK_IDENTIFIER && "require".equals(getWord(source, stream, start))) {
            int paren = skipWs(stream, skipToken(stream, start), end);
            if (paren < end && source.charAt(stream.tokenStart[paren]) == '(') {
                int arg = skipWs(stream, skipToken(stream, paren), end);
                if (arg < end && stream.types[arg] == TokenStream.TK_STRING) {
                    String raw = getWord(source, stream, arg);
                    if (raw.length() >= 2) {
                        return "@REQUIRE:" + raw.substring(1, raw.length() - 1);
                    }
                }
            }
        }

        // 10. Single identifier variable aliasing / scope resolution
        if (firstType == TokenStream.TK_IDENTIFIER || (firstType == TokenStream.TK_KEYWORD && !"var".equals(getWord(source, stream, start)) && !"let".equals(getWord(source, stream, start)) && !"const".equals(getWord(source, stream, start)))) {
            int nextTok = skipWs(stream, skipToken(stream, start), end);
            if (nextTok >= end) {
                String id = getWord(source, stream, start);
                return resolveIdentifierType(id, tree, scopeTree, varTypeMap);
            }
        }

        return null;
    }

    public static String inferFromText(String exprText, JsSyntaxTree tree, ScopeTree scopeTree, Map<String, String> varTypeMap) {
        if (exprText == null) return null;
        String trimmed = exprText.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) return null;

        TokenStream stream = JsLexer.tokenize(trimmed);
        return inferFromTokens(trimmed, stream, 0, stream.length, tree, scopeTree, varTypeMap);
    }

    public static String toDisplayType(String internal) {
        if (internal == null || internal.isEmpty()) return null;
        switch (internal) {
            case "@NUMBER":
                return "number";
            case "@STRING":
                return "string";
            case "@BOOLEAN":
                return "boolean";
            case "@ARRAY":
                return "any[]";
            case "@REGEXP":
                return "RegExp";
            case "@DATE":
                return "Date";
            case "@PROMISE":
                return "Promise<any>";
            case "@MAP":
                return "Map<any, any>";
            case "@SET":
                return "Set<any>";
            case "@ERROR":
                return "Error";
            case "@OBJECT":
                return "object";
            case "@ARRAY_OF_NUMBER":
                return "number[]";
            case "@ARRAY_OF_STRING":
                return "string[]";
            case "@ARRAY_OF_BOOLEAN":
                return "boolean[]";
            default:
                if (internal.startsWith("@ARRAY_OF_PROMISE_OF:")) {
                    return "Promise<" + toDisplayType(internal.substring(21)) + ">[]";
                }
                if (internal.startsWith("@ARRAY_OF_PROMISE")) {
                    return "Promise<any>[]";
                }
                if (internal.startsWith("@ARRAY_OF_INLINE_SHAPE:")) {
                    return "{ " + internal.substring(23).replace(",", ", ") + " }[]";
                }
                if (internal.startsWith("@ARRAY_OF_")) {
                    return toDisplayType(internal.substring(10)) + "[]";
                }
                if (internal.startsWith("@INLINE_SHAPE:")) {
                    return "{ " + internal.substring(14).replace(",", ", ") + " }";
                }
                if (internal.startsWith("@REQUIRE:")) {
                    return "any";
                }
                if (internal.startsWith("@")) {
                    return internal.substring(1).toLowerCase();
                }
                return internal;
        }
    }

    public static String normalizeToInternalType(String type) {
        if (type == null || type.isEmpty()) return null;
        if (type.startsWith("@")) return type;
        switch (type.toLowerCase()) {
            case "number":
                return "@NUMBER";
            case "string":
                return "@STRING";
            case "boolean":
                return "@BOOLEAN";
            case "array":
                return "@ARRAY";
            case "date":
                return "@DATE";
            case "regexp":
                return "@REGEXP";
            case "map":
                return "@MAP";
            case "set":
                return "@SET";
            case "promise":
                return "@PROMISE";
            case "error":
                return "@ERROR";
            case "object":
                return "@OBJECT";
            default:
                return type;
        }
    }

    private static String checkAsCast(String source, TokenStream stream, int start, int end) {
        int curr = start;
        int depth = 0;
        int lastAsTok = -1;

        while (curr < end) {
            byte t = stream.types[curr];
            if (t == TokenStream.TK_PUNCT) {
                char c = source.charAt(stream.tokenStart[curr]);
                if (c == '(' || c == '[' || c == '{') depth++;
                else if (c == ')' || c == ']' || c == '}') {
                    if (depth > 0) depth--;
                }
            } else if (depth == 0 && (t == TokenStream.TK_KEYWORD || t == TokenStream.TK_IDENTIFIER)) {
                if ("as".equals(getWord(source, stream, curr))) {
                    lastAsTok = curr;
                }
            }
            curr = skipToken(stream, curr);
        }

        if (lastAsTok != -1) {
            int afterAs = skipWs(stream, skipToken(stream, lastAsTok), end);
            if (afterAs < end) {
                String castType = getWord(source, stream, afterAs);
                int afterType = skipWs(stream, skipToken(stream, afterAs), end);
                boolean isArray = false;
                if (afterType < end && source.charAt(stream.tokenStart[afterType]) == '[') {
                    isArray = true;
                }

                switch (castType) {
                    case "number": return isArray ? "@ARRAY_OF_NUMBER" : "@NUMBER";
                    case "string": return isArray ? "@ARRAY_OF_STRING" : "@STRING";
                    case "boolean": return isArray ? "@ARRAY_OF_BOOLEAN" : "@BOOLEAN";
                    case "any":
                    case "Array":
                        return "@ARRAY";
                    case "Date": return "@DATE";
                    case "RegExp": return "@REGEXP";
                    case "Map": return "@MAP";
                    case "Set": return "@SET";
                    case "Promise": return "@PROMISE";
                    case "object": return "@OBJECT";
                    case "Error": return "@ERROR";
                    default:
                        if (isArray) return "@ARRAY";
                        return castType;
                }
            }
        }
        return null;
    }

    private static String scanTopLevelOperators(String source, TokenStream stream, int start, int end, JsSyntaxTree tree, ScopeTree scopeTree, Map<String, String> varTypeMap) {
        int curr = start;
        int depth = 0;
        boolean hasPrecedingOperand = false;

        boolean hasRelational = false;
        boolean hasArithmetic = false;
        boolean hasStringConcat = false;
        boolean hasPlus = false;

        while (curr < end) {
            byte t = stream.types[curr];

            if (t == TokenStream.TK_PUNCT) {
                char c = source.charAt(stream.tokenStart[curr]);
                if (c == '(' || c == '[' || c == '{') {
                    depth++;
                    hasPrecedingOperand = false;
                } else if (c == ')' || c == ']' || c == '}') {
                    if (depth > 0) depth--;
                    hasPrecedingOperand = true;
                } else if (c == ',' || c == ';') {
                    hasPrecedingOperand = false;
                }
            } else if (depth == 0) {
                if (t == TokenStream.TK_OPERATOR) {
                    String op = getWord(source, stream, curr);
                    if ("===".equals(op) || "!==".equals(op) || "==".equals(op) || "!=".equals(op)
                            || "<=".equals(op) || ">=".equals(op)) {
                        hasRelational = true;
                    } else if ("<".equals(op)) {
                        int nextTok = skipToken(stream, curr);
                        if (nextTok >= end || stream.types[nextTok] != TokenStream.TK_OPERATOR || source.charAt(stream.tokenStart[nextTok]) != '<') {
                            hasRelational = true;
                        } else {
                            hasArithmetic = true; // '<<' bitwise shift
                        }
                    } else if (">".equals(op)) {
                        int nextTok = skipToken(stream, curr);
                        if (nextTok >= end || stream.types[nextTok] != TokenStream.TK_OPERATOR || source.charAt(stream.tokenStart[nextTok]) != '>') {
                            hasRelational = true;
                        } else {
                            hasArithmetic = true; // '>>' or '>>>' bitwise shift
                        }
                    } else if ("-".equals(op)) {
                        if (hasPrecedingOperand) {
                            hasArithmetic = true; // binary subtraction
                        }
                    } else if ("*".equals(op) || "**".equals(op) || "/".equals(op) || "%".equals(op)
                            || "^".equals(op) || "<<".equals(op) || ">>".equals(op) || ">>>".equals(op)) {
                        hasArithmetic = true;
                    } else if ("&".equals(op)) {
                        int nextTok = skipToken(stream, curr);
                        if (nextTok >= end || stream.types[nextTok] != TokenStream.TK_OPERATOR || source.charAt(stream.tokenStart[nextTok]) != '&') {
                            hasArithmetic = true; // bitwise AND, not logical &&
                        }
                    } else if ("|".equals(op)) {
                        int nextTok = skipToken(stream, curr);
                        if (nextTok >= end || stream.types[nextTok] != TokenStream.TK_OPERATOR || source.charAt(stream.tokenStart[nextTok]) != '|') {
                            hasArithmetic = true; // bitwise OR, not logical ||
                        }
                    } else if ("+".equals(op)) {
                        if (hasPrecedingOperand) {
                            hasPlus = true;
                            // Check if either side is a string literal / template or infers to string
                            if (isOperandString(source, stream, start, curr, tree, scopeTree, varTypeMap)
                                    || isOperandString(source, stream, skipToken(stream, curr), end, tree, scopeTree, varTypeMap)) {
                                hasStringConcat = true;
                            }
                        }
                    }
                    hasPrecedingOperand = false;
                } else if (t == TokenStream.TK_KEYWORD || t == TokenStream.TK_IDENTIFIER) {
                    String kw = getWord(source, stream, curr);
                    if ("instanceof".equals(kw) || "in".equals(kw)) {
                        hasRelational = true;
                    }
                    hasPrecedingOperand = true;
                } else if (t == TokenStream.TK_NUMBER || t == TokenStream.TK_STRING || t == TokenStream.TK_TEMPLATE || t == TokenStream.TK_REGEX) {
                    hasPrecedingOperand = true;
                }
            } else {
                hasPrecedingOperand = (t != TokenStream.TK_WHITESPACE && t != TokenStream.TK_COMMENT);
            }

            curr = skipToken(stream, curr);
        }

        if (hasRelational) return "@BOOLEAN";
        if (hasStringConcat) return "@STRING";
        if (hasArithmetic || hasPlus) return "@NUMBER";
        return null;
    }

    private static boolean isOperandString(String source, TokenStream stream, int opStart, int opEnd, JsSyntaxTree tree, ScopeTree scopeTree, Map<String, String> varTypeMap) {
        int curr = skipWs(stream, opStart, opEnd);
        if (curr >= opEnd) return false;
        byte t = stream.types[curr];
        if (t == TokenStream.TK_STRING || t == TokenStream.TK_TEMPLATE || source.charAt(stream.tokenStart[curr]) == '`') {
            return true;
        }
        if (t == TokenStream.TK_IDENTIFIER) {
            String id = getWord(source, stream, curr);
            String resolved = resolveIdentifierType(id, tree, scopeTree, varTypeMap);
            return "@STRING".equals(resolved);
        }
        return false;
    }

    private static String checkBuiltinCalls(String source, TokenStream stream, int start, int end) {
        byte t = stream.types[start];
        if (t != TokenStream.TK_IDENTIFIER && t != TokenStream.TK_KEYWORD) return null;

        String id = getWord(source, stream, start);
        int dotOrParen = skipWs(stream, skipToken(stream, start), end);

        if ("Math".equals(id)) {
            if (dotOrParen < end && source.charAt(stream.tokenStart[dotOrParen]) == '.') {
                return "@NUMBER"; // Math.round, Math.floor, Math.abs, Math.PI, etc.
            }
        }

        if ("Date".equals(id)) {
            if (dotOrParen < end && source.charAt(stream.tokenStart[dotOrParen]) == '.') {
                int methodTok = skipWs(stream, skipToken(stream, dotOrParen), end);
                if (methodTok < end) {
                    String m = getWord(source, stream, methodTok);
                    if ("now".equals(m) || "parse".equals(m)) return "@NUMBER";
                }
            }
        }

        if ("Number".equals(id)) {
            if (dotOrParen < end) {
                char c = source.charAt(stream.tokenStart[dotOrParen]);
                if (c == '(') return "@NUMBER";
                if (c == '.') {
                    int methodTok = skipWs(stream, skipToken(stream, dotOrParen), end);
                    if (methodTok < end) {
                        String m = getWord(source, stream, methodTok);
                        if ("isInteger".equals(m) || "isNaN".equals(m) || "isFinite".equals(m)) return "@BOOLEAN";
                        if ("parseInt".equals(m) || "parseFloat".equals(m)) return "@NUMBER";
                    }
                }
            }
        }

        if ("String".equals(id) && dotOrParen < end && source.charAt(stream.tokenStart[dotOrParen]) == '(') {
            return "@STRING";
        }

        if ("Boolean".equals(id) && dotOrParen < end && source.charAt(stream.tokenStart[dotOrParen]) == '(') {
            return "@BOOLEAN";
        }

        if ("parseInt".equals(id) || "parseFloat".equals(id)) {
            if (dotOrParen < end && source.charAt(stream.tokenStart[dotOrParen]) == '(') return "@NUMBER";
        }

        if ("isNaN".equals(id) || "isFinite".equals(id)) {
            if (dotOrParen < end && source.charAt(stream.tokenStart[dotOrParen]) == '(') return "@BOOLEAN";
        }

        if ("JSON".equals(id) && dotOrParen < end && source.charAt(stream.tokenStart[dotOrParen]) == '.') {
            int methodTok = skipWs(stream, skipToken(stream, dotOrParen), end);
            if (methodTok < end) {
                String m = getWord(source, stream, methodTok);
                if ("stringify".equals(m)) return "@STRING";
                if ("parse".equals(m)) return "@OBJECT";
            }
        }

        if ("encodeURI".equals(id) || "encodeURIComponent".equals(id)
                || "decodeURI".equals(id) || "decodeURIComponent".equals(id)) {
            if (dotOrParen < end && source.charAt(stream.tokenStart[dotOrParen]) == '(') return "@STRING";
        }

        if ("Array".equals(id) && dotOrParen < end && source.charAt(stream.tokenStart[dotOrParen]) == '.') {
            int methodTok = skipWs(stream, skipToken(stream, dotOrParen), end);
            if (methodTok < end) {
                String m = getWord(source, stream, methodTok);
                if ("isArray".equals(m)) return "@BOOLEAN";
                if ("from".equals(m) || "of".equals(m)) return "@ARRAY";
            }
        }

        if ("Object".equals(id) && dotOrParen < end && source.charAt(stream.tokenStart[dotOrParen]) == '.') {
            int methodTok = skipWs(stream, skipToken(stream, dotOrParen), end);
            if (methodTok < end) {
                String m = getWord(source, stream, methodTok);
                if ("keys".equals(m) || "values".equals(m) || "entries".equals(m)) return "@ARRAY";
                if ("assign".equals(m) || "create".equals(m)) return "@OBJECT";
            }
        }

        return null;
    }

    private static String checkMethodCallReturn(String source, TokenStream stream, int start, int end) {
        // Look for the last dot-call at the end of expression: `receiver.<method>(...)` or `receiver.<prop>`
        int lastCloseParen = end - 1;
        while (lastCloseParen >= start && (stream.types[lastCloseParen] == TokenStream.TK_WHITESPACE || stream.types[lastCloseParen] == TokenStream.TK_COMMENT)) {
            lastCloseParen--;
        }
        if (lastCloseParen < start) return null;

        int openParen = -1;
        if (source.charAt(stream.tokenStart[lastCloseParen]) == ')') {
            int depth = 0;
            for (int k = lastCloseParen; k >= start; k--) {
                if (stream.types[k] == TokenStream.TK_PUNCT) {
                    char c = source.charAt(stream.tokenStart[k]);
                    if (c == ')') depth++;
                    else if (c == '(') {
                        depth--;
                        if (depth == 0) {
                            openParen = k;
                            break;
                        }
                    }
                }
            }
        }

        int methodEndTok = (openParen != -1) ? openParen : lastCloseParen + 1;
        int methodTok = skipTrailingWs(stream, start, methodEndTok);
        if (methodTok > start) {
            methodTok = stream.tokenStart[methodTok - 1];
        } else {
            return null;
        }

        if (stream.types[methodTok] == TokenStream.TK_IDENTIFIER) {
            int dotTok = methodTok - 1;
            while (dotTok >= start && (stream.types[dotTok] == TokenStream.TK_WHITESPACE || stream.types[dotTok] == TokenStream.TK_COMMENT)) {
                dotTok--;
            }
            if (dotTok >= start && stream.types[dotTok] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[dotTok]) == '.') {
                String mName = getWord(source, stream, methodTok);
                switch (mName) {
                    case "toLowerCase":
                    case "toUpperCase":
                    case "trim":
                    case "trimStart":
                    case "trimEnd":
                    case "slice":
                    case "substring":
                    case "substr":
                    case "charAt":
                    case "repeat":
                    case "replace":
                    case "replaceAll":
                    case "padStart":
                    case "padEnd":
                    case "concat":
                    case "join":
                    case "toFixed":
                    case "toPrecision":
                    case "toExponential":
                    case "toString":
                    case "toLocaleString":
                    case "toISOString":
                    case "toLocaleDateString":
                    case "toLocaleTimeString":
                        return "@STRING";

                    case "indexOf":
                    case "lastIndexOf":
                    case "search":
                    case "charCodeAt":
                    case "codePointAt":
                    case "length":
                    case "size":
                    case "getTime":
                    case "getFullYear":
                    case "getMonth":
                    case "getDate":
                    case "getDay":
                    case "getHours":
                    case "getMinutes":
                    case "getSeconds":
                    case "getMilliseconds":
                    case "findIndex":
                    case "findLastIndex":
                    case "push":
                    case "unshift":
                        return "@NUMBER";

                    case "includes":
                    case "startsWith":
                    case "endsWith":
                    case "test":
                    case "has":
                    case "some":
                    case "every":
                    case "delete":
                        return "@BOOLEAN";

                    case "split":
                    case "match":
                    case "filter":
                    case "map":
                    case "flat":
                    case "flatMap":
                    case "reverse":
                    case "sort":
                    case "splice":
                    case "exec":
                        return "@ARRAY";

                    case "then":
                    case "catch":
                    case "finally":
                        return "@PROMISE";
                }
            }
        }
        return null;
    }

    private static String inferArrayLiteralType(String source, TokenStream stream, int start, int end) {
        int next = skipWs(stream, skipToken(stream, start), end);
        if (next < end && stream.types[next] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[next]) == '{') {
            java.util.List<String> keys = new java.util.ArrayList<>();
            int curr = skipToken(stream, next);
            int depth = 1;
            while (curr < end && depth > 0) {
                curr = skipWs(stream, curr, end);
                if (curr >= end) break;
                byte tt = stream.types[curr];
                if (tt == TokenStream.TK_PUNCT) {
                    char cc = source.charAt(stream.tokenStart[curr]);
                    if (cc == '{') depth++;
                    else if (cc == '}') depth--;
                } else if (tt == TokenStream.TK_IDENTIFIER || tt == TokenStream.TK_STRING) {
                    if (depth == 1) {
                        int colonNext = skipWs(stream, skipToken(stream, curr), end);
                        if (colonNext < end && stream.types[colonNext] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[colonNext]) == ':') {
                            String key = getWord(source, stream, curr);
                            if (tt == TokenStream.TK_STRING && key.length() >= 2) {
                                key = key.substring(1, key.length() - 1);
                            }
                            if (!keys.contains(key)) keys.add(key);
                        }
                    }
                }
                curr = skipToken(stream, curr);
            }
            if (!keys.isEmpty()) {
                return "@ARRAY_OF_INLINE_SHAPE:" + String.join(",", keys);
            }
        } else if (next < end && stream.types[next] == TokenStream.TK_NUMBER) {
            return "@ARRAY_OF_NUMBER";
        } else if (next < end && stream.types[next] == TokenStream.TK_STRING) {
            return "@ARRAY_OF_STRING";
        } else if (next < end && (stream.types[next] == TokenStream.TK_IDENTIFIER || stream.types[next] == TokenStream.TK_KEYWORD)) {
            String firstWord = getWord(source, stream, next);
            if ("Promise".equals(firstWord)) {
                int dotTok = skipWs(stream, skipToken(stream, next), end);
                if (dotTok < end && stream.types[dotTok] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[dotTok]) == '.') {
                    int methodTok = skipWs(stream, skipToken(stream, dotTok), end);
                    if (methodTok < end && "resolve".equals(getWord(source, stream, methodTok))) {
                        int parenTok = skipWs(stream, skipToken(stream, methodTok), end);
                        if (parenTok < end && stream.types[parenTok] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[parenTok]) == '(') {
                            int argTok = skipWs(stream, skipToken(stream, parenTok), end);
                            if (argTok < end) {
                                if (stream.types[argTok] == TokenStream.TK_STRING) {
                                    return "@ARRAY_OF_PROMISE_OF:@STRING";
                                } else if (stream.types[argTok] == TokenStream.TK_NUMBER) {
                                    return "@ARRAY_OF_PROMISE_OF:@NUMBER";
                                } else if (stream.types[argTok] == TokenStream.TK_PUNCT && source.charAt(stream.tokenStart[argTok]) == '{') {
                                    return "@ARRAY_OF_PROMISE_OF:@OBJECT";
                                }
                            }
                        }
                    }
                }
                return "@ARRAY_OF_PROMISE";
            }
        }
        return "@ARRAY";
    }

    private static String resolveIdentifierType(String id, JsSyntaxTree tree, ScopeTree scopeTree, Map<String, String> varTypeMap) {
        if (id == null || id.isEmpty()) return null;

        if (varTypeMap != null && varTypeMap.containsKey(id)) {
            return normalizeToInternalType(varTypeMap.get(id));
        }

        if (scopeTree != null && tree != null) {
            int[] sym = scopeTree.lookupSymbol(id, 0);
            if (sym != null) {
                int declNodeId = sym[1];
                if (declNodeId > 0 && declNodeId < tree.nodeCount) {
                    String ann = tree.nodeTypeAnn != null ? tree.nodeTypeAnn[declNodeId] : null;
                    if (ann != null && !ann.isEmpty()) {
                        return normalizeToInternalType(ann);
                    }
                }
            }
        }

        if (tree != null) {
            for (int i = 1; i < tree.nodeCount; i++) {
                if (id.equals(tree.nodeName[i]) && (tree.nodeType[i] == JsSyntaxTree.N_VAR_DECL || tree.nodeType[i] == JsSyntaxTree.N_PARAM)) {
                    String ann = tree.nodeTypeAnn != null ? tree.nodeTypeAnn[i] : null;
                    if (ann != null && !ann.isEmpty()) {
                        return normalizeToInternalType(ann);
                    }
                }
            }
        }

        return null;
    }

    private static int findMatchingParen(String source, TokenStream stream, int openParenIdx, int limit) {
        int depth = 0;
        int curr = openParenIdx;
        while (curr < limit) {
            byte t = stream.types[curr];
            if (t == TokenStream.TK_PUNCT) {
                char c = source.charAt(stream.tokenStart[curr]);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        return curr;
                    }
                }
            }
            curr = skipToken(stream, curr);
        }
        return -1;
    }

    private static int skipWs(TokenStream stream, int i, int limit) {
        while (i < limit) {
            byte t = stream.types[i];
            if (t == TokenStream.TK_WHITESPACE || t == TokenStream.TK_COMMENT) {
                i = skipToken(stream, i);
            } else {
                break;
            }
        }
        return i;
    }

    private static int skipTrailingWs(TokenStream stream, int start, int limit) {
        int end = limit;
        while (end > start) {
            int prev = end - 1;
            int tokenStart = stream.tokenStart[prev];
            byte t = stream.types[tokenStart];
            if (t == TokenStream.TK_WHITESPACE || t == TokenStream.TK_COMMENT) {
                end = tokenStart;
            } else {
                break;
            }
        }
        return end;
    }

    private static int skipTrailingExpressionTerminators(String source, TokenStream stream, int start, int limit) {
        int end = limit;
        while (end > start) {
            int prev = end - 1;
            int tokenStart = stream.tokenStart[prev];
            byte t = stream.types[tokenStart];
            if (t == TokenStream.TK_WHITESPACE || t == TokenStream.TK_COMMENT) {
                end = tokenStart;
            } else if (t == TokenStream.TK_PUNCT && source != null && tokenStart < source.length()) {
                char c = source.charAt(tokenStart);
                if (c == ';' || c == ',') {
                    end = tokenStart;
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        return end;
    }

    private static int skipToken(TokenStream stream, int i) {
        if (i >= stream.length) return i;
        int start = stream.tokenStart[i];
        while (i < stream.length && stream.tokenStart[i] == start) {
            i++;
        }
        return i;
    }

    private static String getWord(String source, TokenStream stream, int i) {
        if (i >= stream.length) return "";
        int start = stream.tokenStart[i];
        int end = skipToken(stream, i);
        return source.substring(start, end);
    }
}
