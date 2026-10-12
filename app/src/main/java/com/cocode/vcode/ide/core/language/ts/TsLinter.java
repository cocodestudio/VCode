package com.cocode.vcode.ide.core.language.ts;

import com.cocode.vcode.ide.core.diagnostic.ProblemCode;
import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsLinter;
import com.cocode.vcode.ide.core.language.js.JsSemanticLinter;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Real-time AST-driven linter for TypeScript, enforcing type annotations, interface syntax,
 * and TS-specific constructs without regular expressions.
 */
public class TsLinter {

    public static List<Problem> analyze(File file, String text) {
        return analyze(file, text, null);
    }

    public static List<Problem> analyze(File file, String text, com.cocode.vcode.ide.core.lsp.ProjectIndex index) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();
        if (text.contains("@ts-nocheck")) return Collections.emptyList();

        Set<Integer> suppressedLines = new HashSet<>();
        if (text.contains("@ts-ignore") || text.contains("@ts-expect-error")) {
            String[] lines = text.split("\r?\n");
            for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
                String lineStr = lines[lineIdx];
                if (lineStr.contains("@ts-ignore") || lineStr.contains("@ts-expect-error")) {
                    int lineNum = lineIdx + 1;
                    suppressedLines.add(lineNum);
                    suppressedLines.add(lineNum + 1);
                }
            }
        }

        TokenStream mask = JsLexer.tokenize(text);
        JsLinter.AnalysisContext ctx = JsLinter.prepareContext(file, text, mask, index);

        List<Problem> problems = new ArrayList<>();
        JsSemanticLinter.analyze(file, text, ctx.stream, ctx.tree, ctx.scopeTree, index, problems);

        if (ctx.tree != null) {
            checkTypeMismatch(file, text, ctx.stream, ctx.tree, problems);
            checkReturnAny(file, text, ctx.tree, problems);
            checkNonNull(file, text, ctx.stream, ctx.tree, problems);
            checkAnyType(file, text, ctx.tree, problems);
            checkAsAssertion(file, text, ctx.stream, problems);
            checkExportedFnReturnType(file, text, ctx.tree, problems);
            checkOptionalBeforeRequired(file, text, ctx.tree, problems);
            checkNamespace(file, text, ctx.stream, problems);
            checkFunctionType(file, text, ctx.tree, problems);
            checkEnum(file, text, ctx.tree, problems);
            checkRedundantType(file, text, ctx.stream, ctx.tree, problems);
            checkUnionUndefined(file, text, ctx.tree, problems);
            checkReadonlyArray(file, text, ctx.tree, problems);
            checkInlineObjectType(file, text, ctx.tree, problems);
            checkInterfaceImplementation(file, text, ctx.tree, problems);
            checkMissingProperties(file, text, ctx.tree, problems);
        }

        if (!suppressedLines.isEmpty()) {
            problems.removeIf(p -> suppressedLines.contains(p.getLine()));
        }

        return problems;
    }

    // Type mismatch check
    private static void checkTypeMismatch(File file, String text, TokenStream stream, JsSyntaxTree tree, List<Problem> out) {
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_VAR_DECL) {
                String declType = tree.nodeTypeAnn[i];
                if (declType == null) continue;
                declType = declType.trim();
                if (!("string".equals(declType) || "number".equals(declType) || "boolean".equals(declType))) {
                    continue;
                }
                String name = tree.nodeName[i];
                if (name == null || name.isEmpty() || "{destructure}".equals(name)) continue;

                int startOffset = tree.nodeStart[i];
                int endOffset = tree.nodeEnd[i];
                int eqTok = findAssignmentOperator(text, stream, startOffset, endOffset);
                if (eqTok != -1) {
                    int valTok = skipWsAndComments(stream, skipToken(stream, eqTok));
                    if (valTok < stream.length && stream.tokenStart[valTok] < endOffset) {
                        String valueType = inferLiteralTypeFromToken(text, stream, valTok, endOffset);
                        if (valueType != null && !valueType.equals(declType)) {
                            int targetOffset = findIdentifierOffset(text, startOffset, endOffset, name);
                            int line = LinterUtils.getLine(text, targetOffset);
                            int col = LinterUtils.getColumn(text, targetOffset);
                            out.add(new Problem(file, line, col, name.length(),
                                    "Type mismatch: cannot assign '" + valueType + "' to '" + declType + "' for '" + name + "'",
                                    Problem.Severity.ERROR,
                                    ProblemCode.TS_TYPE_MISMATCH));
                        }
                    }
                }
            }
        }
    }

    // Function return type of 'any' check
    private static void checkReturnAny(File file, String text, JsSyntaxTree tree, List<Problem> out) {
        for (int i = 1; i < tree.nodeCount; i++) {
            int type = tree.nodeType[i];
            if (type == JsSyntaxTree.N_FUNC_DECL || type == JsSyntaxTree.N_METHOD) {
                String retType = tree.nodeTypeAnn[i];
                if (retType != null && "any".equalsIgnoreCase(retType.trim())) {
                    String name = tree.nodeName[i];
                    if (name == null || name.isEmpty()) name = "anonymous";
                    int start = tree.nodeStart[i];
                    int end = tree.nodeEnd[i];
                    int targetOffset = "anonymous".equals(name) ? start : findIdentifierOffset(text, start, end, name);
                    int line = LinterUtils.getLine(text, targetOffset);
                    int col = LinterUtils.getColumn(text, targetOffset);
                    out.add(new Problem(file, line, col, name.length(),
                            "Function '" + name + "' returns 'any': specify an explicit return type instead",
                            Problem.Severity.WARNING,
                            ProblemCode.TS_RETURN_ANY));
                }
            }
        }
    }

    // Missing return type on exported function
    private static void checkExportedFnReturnType(File file, String text, JsSyntaxTree tree, List<Problem> out) {
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_EXPORT) {
                int child = tree.nodeChild[i];
                while (child > 0 && child < tree.nodeCount) {
                    if (tree.nodeType[child] == JsSyntaxTree.N_FUNC_DECL) {
                        String retType = tree.nodeTypeAnn[child];
                        if (retType == null || retType.trim().isEmpty()) {
                            String name = tree.nodeName[child];
                            if (name != null && !name.isEmpty()) {
                                int start = tree.nodeStart[child];
                                int end = tree.nodeEnd[child];
                                int targetOffset = findIdentifierOffset(text, start, end, name);
                                int line = LinterUtils.getLine(text, targetOffset);
                                int col = LinterUtils.getColumn(text, targetOffset);
                                out.add(new Problem(file, line, col, name.length(),
                                        "Exported function '" + name + "' is missing a return type annotation",
                                        Problem.Severity.WARNING,
                                        ProblemCode.TS_MISSING_RETURN_TYPE));
                            }
                        }
                    }
                    child = tree.nodeSibling[child];
                }
            }
        }
    }

    // Optional parameter before required parameter
    private static void checkOptionalBeforeRequired(File file, String text, JsSyntaxTree tree, List<Problem> out) {
        for (int i = 1; i < tree.nodeCount; i++) {
            int type = tree.nodeType[i];
            if (type == JsSyntaxTree.N_FUNC_DECL || type == JsSyntaxTree.N_METHOD || type == JsSyntaxTree.N_ARROW_FUNC) {
                String lastOptional = null;
                int child = tree.nodeChild[i];
                while (child > 0 && child < tree.nodeCount) {
                    if (tree.nodeType[child] == JsSyntaxTree.N_PARAM) {
                        String pName = tree.nodeName[child];
                        if (pName != null && !pName.isEmpty() && !"{destructure}".equals(pName)) {
                            boolean isOpt = (tree.nodeExtra[child] & JsSyntaxTree.FLAG_DEFAULT) != 0;
                            boolean isRest = (tree.nodeExtra[child] & JsSyntaxTree.FLAG_REST) != 0;
                            if (!isOpt && !isRest && lastOptional != null) {
                                int start = tree.nodeStart[child];
                                int end = tree.nodeEnd[child];
                                int targetOffset = findIdentifierOffset(text, start, end, pName);
                                int line = LinterUtils.getLine(text, targetOffset);
                                int col = LinterUtils.getColumn(text, targetOffset);
                                out.add(new Problem(file, line, col, pName.length(),
                                        "Optional parameter '" + lastOptional + "?' before required parameter '" + pName + "': required params must come first",
                                        Problem.Severity.WARNING,
                                        ProblemCode.TS_TYPE_MISMATCH));
                            }
                            if (isOpt) {
                                lastOptional = pName;
                            } else {
                                lastOptional = null;
                            }
                        }
                    }
                    child = tree.nodeSibling[child];
                }
            }
        }
    }

    // Explicit 'any' in parameter or variable declaration
    private static void checkAnyType(File file, String text, JsSyntaxTree tree, List<Problem> out) {
        for (int i = 1; i < tree.nodeCount; i++) {
            int type = tree.nodeType[i];
            if (type == JsSyntaxTree.N_PARAM || type == JsSyntaxTree.N_VAR_DECL) {
                String typeAnn = tree.nodeTypeAnn[i];
                if (typeAnn != null && "any".equalsIgnoreCase(typeAnn.trim())) {
                    String name = tree.nodeName[i];
                    if (name != null && !name.isEmpty() && !"{destructure}".equals(name)) {
                        int start = tree.nodeStart[i];
                        int end = tree.nodeEnd[i];
                        int targetOffset = findIdentifierOffset(text, start, end, name);
                        int line = LinterUtils.getLine(text, targetOffset);
                        int col = LinterUtils.getColumn(text, targetOffset);
                        out.add(new Problem(file, line, col, name.length(),
                                "Explicit 'any' type for '" + name + "': weakens type safety — use 'unknown' or a specific type",
                                Problem.Severity.WARNING,
                                ProblemCode.TS_EXPLICIT_ANY));
                    }
                }
            }
        }
    }

    private static final java.util.regex.Pattern PAT_ENUM = java.util.regex.Pattern.compile("\\benum\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    // Enum declaration check
    private static void checkEnum(File file, String text, JsSyntaxTree tree, List<Problem> out) {
        if (text == null) return;
        java.util.regex.Matcher m = PAT_ENUM.matcher(text);
        while (m.find()) {
            int nameStart = m.start(1);
            int line = LinterUtils.getLine(text, nameStart);
            int col = LinterUtils.getColumn(text, nameStart);
            out.add(new Problem(file, line, col, m.group(1).length(),
                    "Enums add runtime overhead: consider 'const' object with 'as const' for better tree-shaking",
                    Problem.Severity.INFO));
        }
    }

    // Redundant type annotation matching inferred literal
    private static void checkRedundantType(File file, String text, TokenStream stream, JsSyntaxTree tree, List<Problem> out) {
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_VAR_DECL) {
                String declType = tree.nodeTypeAnn[i];
                if (declType == null) continue;
                declType = declType.trim();
                if (!("string".equals(declType) || "number".equals(declType) || "boolean".equals(declType))) {
                    continue;
                }
                String name = tree.nodeName[i];
                if (name == null || name.isEmpty() || "{destructure}".equals(name)) continue;

                int startOffset = tree.nodeStart[i];
                int endOffset = tree.nodeEnd[i];
                int eqTok = findAssignmentOperator(text, stream, startOffset, endOffset);
                if (eqTok != -1) {
                    int valTok = skipWsAndComments(stream, skipToken(stream, eqTok));
                    if (valTok < stream.length && stream.tokenStart[valTok] < endOffset) {
                        String inferred = inferLiteralTypeFromToken(text, stream, valTok, endOffset);
                        if (declType.equals(inferred)) {
                            int targetOffset = findIdentifierOffset(text, startOffset, endOffset, name);
                            int line = LinterUtils.getLine(text, targetOffset);
                            int col = LinterUtils.getColumn(text, targetOffset);
                            out.add(new Problem(file, line, col, name.length(),
                                    "Type '" + declType + "' is inferred: remove the explicit annotation ':" + declType + "' for '" + name + "'",
                                    Problem.Severity.INFO,
                                    ProblemCode.TS_REDUNDANT_TYPE));
                        }
                    }
                }
            }
        }
    }

    // Parameter with union undefined without optional marker
    private static void checkUnionUndefined(File file, String text, JsSyntaxTree tree, List<Problem> out) {
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_PARAM) {
                String typeAnn = tree.nodeTypeAnn[i];
                if (typeAnn != null && typeAnn.contains("|") && typeAnn.contains("undefined")) {
                    String pName = tree.nodeName[i];
                    if (pName != null && !pName.isEmpty() && !"{destructure}".equals(pName)) {
                        String baseType = typeAnn.replace("| undefined", "").replace("|undefined", "").trim();
                        int start = tree.nodeStart[i];
                        int end = tree.nodeEnd[i];
                        int targetOffset = findIdentifierOffset(text, start, end, pName);
                        int line = LinterUtils.getLine(text, targetOffset);
                        int col = LinterUtils.getColumn(text, targetOffset);
                        out.add(new Problem(file, line, col, pName.length(),
                                "'Type | undefined' in parameter can be written as '" + pName + "?: " + baseType + "'",
                                Problem.Severity.INFO,
                                ProblemCode.TS_UNION_UNDEFINED));
                    }
                }
            }
        }
    }

    // Suggest readonly array for non-mutated interface properties
    private static void checkReadonlyArray(File file, String text, JsSyntaxTree tree, List<Problem> out) {
        for (int i = 1; i < tree.nodeCount; i++) {
            int type = tree.nodeType[i];
            if (type == JsSyntaxTree.N_INTERFACE || type == JsSyntaxTree.N_TYPE_ALIAS) {
                int child = tree.nodeChild[i];
                while (child > 0 && child < tree.nodeCount) {
                    if (tree.nodeType[child] == JsSyntaxTree.N_PROPERTY) {
                        String typeAnn = tree.nodeTypeAnn[child];
                        if (typeAnn != null && typeAnn.endsWith("[]") && !typeAnn.startsWith("readonly ")) {
                            String propName = tree.nodeName[child];
                            if (propName != null && !propName.isEmpty()) {
                                String elemType = typeAnn.substring(0, typeAnn.length() - 2).trim();
                                int start = tree.nodeStart[child];
                                int end = tree.nodeEnd[child];
                                int targetOffset = findIdentifierOffset(text, start, end, propName);
                                int line = LinterUtils.getLine(text, targetOffset);
                                int col = LinterUtils.getColumn(text, targetOffset);
                                out.add(new Problem(file, line, col, propName.length(),
                                        "Consider 'readonly " + propName + ": " + elemType + "[]' to prevent accidental mutation",
                                        Problem.Severity.INFO,
                                        ProblemCode.TS_READONLY_ARRAY));
                            }
                        }
                    }
                    child = tree.nodeSibling[child];
                }
            }
        }
    }

    // Suggest extracting complex inline object types
    private static void checkInlineObjectType(File file, String text, JsSyntaxTree tree, List<Problem> out) {
        for (int i = 1; i < tree.nodeCount; i++) {
            String typeAnn = tree.nodeTypeAnn[i];
            if (typeAnn != null && typeAnn.startsWith("{") && typeAnn.endsWith("}")) {
                if (typeAnn.contains(",") || typeAnn.contains(": true") || typeAnn.contains(": false") || typeAnn.contains(": null")) {
                    continue;
                }
                int propCount = 0;
                int depth = 0;
                for (int k = 1; k < typeAnn.length() - 1; k++) {
                    char c = typeAnn.charAt(k);
                    if (c == '{' || c == '(' || c == '<' || c == '[') depth++;
                    else if (c == '}' || c == ')' || c == '>' || c == ']') {
                        if (depth > 0) depth--;
                    } else if (c == ';' && depth == 0) propCount++;
                }
                if (propCount > 2) {
                    int start = tree.nodeStart[i];
                    int end = tree.nodeEnd[i];
                    int braceIdx = text.indexOf('{', start);
                    int targetOffset = (braceIdx != -1 && braceIdx < end) ? braceIdx : start;
                    int line = LinterUtils.getLine(text, targetOffset);
                    int col = LinterUtils.getColumn(text, targetOffset);
                    out.add(new Problem(file, line, col, typeAnn.length(),
                            "Inline object type with " + propCount + " properties: consider extracting to a named interface",
                            Problem.Severity.INFO,
                            ProblemCode.TS_REDUNDANT_TYPE));
                }
            }
        }
    }

    // Discourage namespace keyword in modern TypeScript
    private static void checkNamespace(File file, String text, TokenStream stream, List<Problem> out) {
        int t = 0;
        while (t < stream.length) {
            if ((stream.types[t] == TokenStream.TK_KEYWORD || stream.types[t] == TokenStream.TK_IDENTIFIER)
                    && "namespace".equals(getWord(text, stream, t))) {
                int prev = stream.tokenStart[t] - 1;
                while (prev >= 0 && (stream.types[prev] == TokenStream.TK_WHITESPACE || stream.types[prev] == TokenStream.TK_COMMENT)) {
                    prev--;
                }
                boolean isKeywordUsage = true;
                if (prev >= 0) {
                    char pc = text.charAt(prev);
                    if (pc == '.' || pc == ',' || pc == ':' || pc == '=' || pc == '(' || pc == '{') {
                        isKeywordUsage = false;
                    } else {
                        int pStart = stream.tokenStart[prev];
                        int pEnd = skipToken(stream, prev);
                        String prevWord = text.substring(pStart, pEnd);
                        if ("const".equals(prevWord) || "let".equals(prevWord) || "var".equals(prevWord)
                                || "import".equals(prevWord) || "type".equals(prevWord) || "interface".equals(prevWord)
                                || "function".equals(prevWord) || "class".equals(prevWord)) {
                            isKeywordUsage = false;
                        }
                    }
                }
                if (isKeywordUsage) {
                    int next = skipWsAndComments(stream, skipToken(stream, t));
                    if (next < stream.length && (stream.types[next] == TokenStream.TK_IDENTIFIER || stream.types[next] == TokenStream.TK_KEYWORD)) {
                        int start = stream.tokenStart[t];
                        int line = LinterUtils.getLine(text, start);
                        int col = LinterUtils.getColumn(text, start);
                        out.add(new Problem(file, line, col, 9,
                                "'namespace' is discouraged in modern TypeScript: use ES modules (import/export) instead",
                                Problem.Severity.WARNING,
                                ProblemCode.TS_REDUNDANT_TYPE));
                    }
                }
            }
            t = skipToken(stream, t);
        }
    }

    // Suggest specific signature over Function type
    private static void checkFunctionType(File file, String text, JsSyntaxTree tree, List<Problem> out) {
        for (int i = 1; i < tree.nodeCount; i++) {
            String typeAnn = tree.nodeTypeAnn[i];
            if (typeAnn != null && hasFunctionType(typeAnn)) {
                int start = tree.nodeStart[i];
                int end = tree.nodeEnd[i];
                int fnIdx = text.indexOf("Function", start);
                int targetOffset = (fnIdx != -1 && fnIdx < end) ? fnIdx : start;
                int line = LinterUtils.getLine(text, targetOffset);
                int col = LinterUtils.getColumn(text, targetOffset);
                out.add(new Problem(file, line, col, 8,
                        "'Function' type is too broad: specify the exact signature, e.g. '(x: T) => R'",
                        Problem.Severity.WARNING,
                        ProblemCode.TS_BROAD_FUNCTION_TYPE));
            }
        }
    }

    private static boolean hasFunctionType(String typeAnn) {
        int idx = typeAnn.indexOf("Function");
        while (idx >= 0) {
            boolean beforeBound = (idx == 0) || !Character.isLetterOrDigit(typeAnn.charAt(idx - 1));
            boolean afterBound = (idx + 8 >= typeAnn.length()) || !Character.isLetterOrDigit(typeAnn.charAt(idx + 8));
            if (beforeBound && afterBound) return true;
            idx = typeAnn.indexOf("Function", idx + 8);
        }
        return false;
    }

    // Type assertion validation
    private static void checkAsAssertion(File file, String text, TokenStream stream, List<Problem> out) {
        int t = 0;
        while (t < stream.length) {
            if ((stream.types[t] == TokenStream.TK_KEYWORD || stream.types[t] == TokenStream.TK_IDENTIFIER)
                    && "as".equals(getWord(text, stream, t))) {
                int prev = stream.tokenStart[t] - 1;
                boolean insideImportExportBraces = false;
                int scan = prev;
                int braceDepth = 0;
                while (scan >= 0) {
                    char sc = text.charAt(scan);
                    if (sc == ';') break;
                    if (sc == '}') braceDepth++;
                    else if (sc == '{') {
                        if (braceDepth > 0) {
                            braceDepth--;
                        } else {
                            int beforeBrace = scan - 1;
                            while (beforeBrace >= 0 && Character.isWhitespace(text.charAt(beforeBrace))) beforeBrace--;
                            if (beforeBrace >= 0) {
                                int wStart = stream.tokenStart[beforeBrace];
                                int wEnd = skipToken(stream, beforeBrace);
                                String w = text.substring(wStart, wEnd);
                                if ("import".equals(w) || "export".equals(w) || "type".equals(w)) {
                                    insideImportExportBraces = true;
                                }
                            }
                            break;
                        }
                    }
                    scan--;
                }
                if (insideImportExportBraces) {
                    t = skipToken(stream, t);
                    continue;
                }

                while (prev >= 0 && (stream.types[prev] == TokenStream.TK_WHITESPACE || stream.types[prev] == TokenStream.TK_COMMENT)) {
                    prev = stream.tokenStart[prev] - 1;
                }
                if (prev >= 0) {
                    prev = stream.tokenStart[prev];
                    byte pt = stream.types[prev];
                    boolean isExprBefore = (pt == TokenStream.TK_IDENTIFIER || pt == TokenStream.TK_NUMBER || pt == TokenStream.TK_STRING || pt == TokenStream.TK_TEMPLATE);
                    if (pt == TokenStream.TK_PUNCT) {
                        char pc = text.charAt(prev);
                        if (pc == ')' || pc == ']' || pc == '}') isExprBefore = true;
                    }
                    if (isExprBefore) {
                        int next = skipWsAndComments(stream, skipToken(stream, t));
                        if (next < stream.length && stream.types[next] == TokenStream.TK_IDENTIFIER) {
                            String targetName = getWord(text, stream, next);
                            int start = stream.tokenStart[next];
                            int line = LinterUtils.getLine(text, start);
                            int col = LinterUtils.getColumn(text, start);
                            if ("any".equals(targetName)) {
                                out.add(new Problem(file, line, col, targetName.length(),
                                        "Type assertion 'as any': weakens type safety — use 'unknown' or a specific type",
                                        Problem.Severity.WARNING,
                                        ProblemCode.TS_EXPLICIT_ANY));
                            } else if (!targetName.isEmpty() && Character.isUpperCase(targetName.charAt(0))) {
                                out.add(new Problem(file, line, col, targetName.length(),
                                        "Type assertion 'as " + targetName + "': make sure the cast is safe",
                                        Problem.Severity.INFO));
                            }
                        }
                    }
                }
            }
            t = skipToken(stream, t);
        }
    }

    // 14 & 15. Non-null Assertion Checks
    private static void checkNonNull(File file, String text, TokenStream stream, JsSyntaxTree tree, List<Problem> out) {
        Set<String> nullableVars = new HashSet<>();
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_VAR_DECL || tree.nodeType[i] == JsSyntaxTree.N_PARAM) {
                String typeAnn = tree.nodeTypeAnn[i];
                if (typeAnn != null && (typeAnn.contains("| null") || typeAnn.contains("|null")
                        || typeAnn.contains("| undefined") || typeAnn.contains("|undefined"))) {
                    String name = tree.nodeName[i];
                    if (name != null && !name.isEmpty() && !"{destructure}".equals(name)) {
                        nullableVars.add(name);
                    }
                }
            }
        }

        int nonNullCount = 0;
        int t = 0;
        while (t < stream.length) {
            if (stream.types[t] == TokenStream.TK_OPERATOR && text.charAt(t) == '!') {
                // Ignore !=, !==, !!, etc.
                boolean prevIsOp = (t > 0 && stream.types[t - 1] == TokenStream.TK_OPERATOR);
                int next = skipToken(stream, t);
                boolean nextIsOp = (next < stream.length && stream.types[next] == TokenStream.TK_OPERATOR);
                if (prevIsOp || nextIsOp) {
                    t = next;
                    continue;
                }
                int prev = stream.tokenStart[t] - 1;
                boolean newline = false;
                while (prev >= 0 && (stream.types[prev] == TokenStream.TK_WHITESPACE || stream.types[prev] == TokenStream.TK_COMMENT)) {
                    if (text.charAt(prev) == '\n' || text.charAt(prev) == '\r') {
                        newline = true;
                        break;
                    }
                    prev--;
                }
                if (!newline && prev >= 0) {
                    prev = stream.tokenStart[prev];
                    if (stream.types[prev] == TokenStream.TK_IDENTIFIER) {
                        String name = getWord(text, stream, prev);
                        if (!isExcludedKeyword(name)) {
                            nonNullCount++;
                            if (nullableVars.contains(name)) {
                                int start = prev;
                                int line = LinterUtils.getLine(text, start);
                                int col = LinterUtils.getColumn(text, start);
                                int len = skipToken(stream, t) - start;
                                out.add(new Problem(file, line, col, len,
                                        "Non-null assertion '!' used on a possibly-null value: ensure this cannot be null",
                                        Problem.Severity.WARNING,
                                        ProblemCode.TS_NON_NULL_ASSERTION));
                            }
                        }
                    }
                }
            }
            t = skipToken(stream, t);
        }


    }

    private static int findAssignmentOperator(String text, TokenStream stream, int startOffset, int endOffset) {
        if (startOffset < 0 || startOffset >= stream.length) return -1;
        int t = stream.tokenStart[Math.max(0, Math.min(startOffset, stream.length - 1))];
        while (t < endOffset && t < stream.length) {
            if (stream.types[t] == TokenStream.TK_OPERATOR && text.charAt(t) == '=') {
                boolean prevIsOp = (t > 0 && stream.types[t - 1] == TokenStream.TK_OPERATOR);
                int next = skipToken(stream, t);
                boolean nextIsOp = (next < stream.length && stream.types[next] == TokenStream.TK_OPERATOR);
                if (!prevIsOp && !nextIsOp) {
                    return t;
                }
                t = next;
                continue;
            }
            t = skipToken(stream, t);
        }
        return -1;
    }

    private static int skipToken(TokenStream stream, int i) {
        if (i >= stream.length) return i;
        int start = stream.tokenStart[i];
        while (i < stream.length && stream.tokenStart[i] == start) {
            i++;
        }
        return i;
    }

    private static int skipWsAndComments(TokenStream stream, int i) {
        while (i < stream.length) {
            byte t = stream.types[i];
            if (t == TokenStream.TK_WHITESPACE || t == TokenStream.TK_COMMENT) {
                i = skipToken(stream, i);
            } else {
                break;
            }
        }
        return i;
    }

    private static String inferLiteralTypeFromToken(String text, TokenStream stream, int tok, int endOffset) {
        int endTok = Math.min(stream.length, endOffset);
        String inferred = com.cocode.vcode.ide.core.language.js.JsTypeInference.inferFromTokens(text, stream, tok, endTok, null, null);
        if (inferred != null) {
            String display = com.cocode.vcode.ide.core.language.js.JsTypeInference.toDisplayType(inferred);
            if ("string".equals(display) || "number".equals(display) || "boolean".equals(display)) {
                return display;
            }
        }
        byte t = stream.types[tok];
        if (t == TokenStream.TK_STRING || t == TokenStream.TK_TEMPLATE) return "string";
        if (t == TokenStream.TK_NUMBER) return "number";
        if (t == TokenStream.TK_KEYWORD) {
            String kw = getWord(text, stream, tok);
            if ("true".equals(kw) || "false".equals(kw)) return "boolean";
        }
        if (t == TokenStream.TK_OPERATOR && text.charAt(tok) == '-') {
            int next = skipWsAndComments(stream, skipToken(stream, tok));
            if (next < stream.length && stream.types[next] == TokenStream.TK_NUMBER)
                return "number";
        }
        return null;
    }

    private static String getWord(String source, TokenStream stream, int i) {
        if (i >= stream.length) return "";
        int start = stream.tokenStart[i];
        int end = skipToken(stream, i);
        return source.substring(start, end);
    }

    private static boolean isExcludedKeyword(String word) {
        return "return".equals(word) || "throw".equals(word) || "case".equals(word)
                || "delete".equals(word) || "void".equals(word) || "typeof".equals(word)
                || "instanceof".equals(word) || "in".equals(word) || "await".equals(word)
                || "yield".equals(word) || "default".equals(word);
    }

    public static int findIdentifierOffset(String text, int start, int end, String name) {
        if (text == null || name == null || name.isEmpty() || start < 0) return Math.max(0, start);
        int limit = Math.min(text.length(), Math.max(start, end));
        int idx = text.indexOf(name, start);
        while (idx != -1 && idx < limit) {
            boolean leftOk = (idx == 0) || !Character.isJavaIdentifierPart(text.charAt(idx - 1));
            boolean rightOk = (idx + name.length() >= text.length()) || !Character.isJavaIdentifierPart(text.charAt(idx + name.length()));
            if (leftOk && rightOk) {
                return idx;
            }
            idx = text.indexOf(name, idx + 1);
        }
        return start;
    }

    private static void checkInterfaceImplementation(File file, String text, JsSyntaxTree tree, List<Problem> out) {
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_CLASS_DECL) {
                String heritage = tree.nodeTypeAnn[i];
                if (heritage == null || heritage.isEmpty()) continue;
                String className = tree.nodeName[i];
                if (className == null) className = "AnonymousClass";

                Set<String> classMethods = new HashSet<>();
                int child = tree.nodeChild[i];
                while (child > 0 && child < tree.nodeCount) {
                    if (tree.nodeType[child] == JsSyntaxTree.N_METHOD) {
                        String mName = tree.nodeName[child];
                        if (mName != null && !mName.isEmpty()) {
                            classMethods.add(mName);
                        }
                    }
                    child = tree.nodeSibling[child];
                }

                String[] parts = heritage.split(",");
                for (String part : parts) {
                    String ifaceName = part.trim();
                    int angleIdx = ifaceName.indexOf('<');
                    if (angleIdx >= 0) ifaceName = ifaceName.substring(0, angleIdx).trim();
                    if (ifaceName.isEmpty()) continue;

                    int ifaceNode = TsParser.findInterfaceOrType(tree, ifaceName);
                    if (ifaceNode > 0 && tree.nodeType[ifaceNode] == JsSyntaxTree.N_INTERFACE) {
                        int ifChild = tree.nodeChild[ifaceNode];
                        while (ifChild > 0 && ifChild < tree.nodeCount) {
                            if (tree.nodeType[ifChild] == JsSyntaxTree.N_METHOD) {
                                String reqMethod = tree.nodeName[ifChild];
                                if (reqMethod != null && !reqMethod.isEmpty() && !classMethods.contains(reqMethod)) {
                                    int start = tree.nodeStart[i];
                                    int line = LinterUtils.getLine(text, start);
                                    int col = LinterUtils.getColumn(text, start);
                                    out.add(new Problem(file, line, col, className.length(),
                                            "Class '" + className + "' incorrectly implements interface '" + ifaceName + "'. Missing method: '" + reqMethod + "'",
                                            Problem.Severity.ERROR,
                                            ProblemCode.TS_INTERFACE_NOT_IMPLEMENTED));
                                }
                            }
                            ifChild = tree.nodeSibling[ifChild];
                        }
                    }
                }
            }
        }
    }

    private static void checkMissingProperties(File file, String text, JsSyntaxTree tree, List<Problem> out) {
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_VAR_DECL) {
                String typeAnn = tree.nodeTypeAnn[i];
                if (typeAnn == null || typeAnn.isEmpty()) continue;
                String typeName = typeAnn.trim();
                int angleIdx = typeName.indexOf('<');
                if (angleIdx >= 0) typeName = typeName.substring(0, angleIdx).trim();

                int ifaceNode = TsParser.findInterfaceOrType(tree, typeName);
                if (ifaceNode <= 0 || tree.nodeType[ifaceNode] != JsSyntaxTree.N_INTERFACE) continue;

                int objLitNode = -1;
                int child = tree.nodeChild[i];
                while (child > 0 && child < tree.nodeCount) {
                    if (tree.nodeType[child] == JsSyntaxTree.N_OBJECT_LITERAL) {
                        objLitNode = child;
                        break;
                    }
                    child = tree.nodeSibling[child];
                }
                if (objLitNode <= 0) continue;

                Set<String> objProps = new HashSet<>();
                int prop = tree.nodeChild[objLitNode];
                while (prop > 0 && prop < tree.nodeCount) {
                    if (tree.nodeType[prop] == JsSyntaxTree.N_PROPERTY || tree.nodeType[prop] == JsSyntaxTree.N_METHOD) {
                        String pName = tree.nodeName[prop];
                        if (pName != null && !pName.isEmpty()) {
                            objProps.add(pName);
                        }
                    }
                    prop = tree.nodeSibling[prop];
                }

                int ifMember = tree.nodeChild[ifaceNode];
                while (ifMember > 0 && ifMember < tree.nodeCount) {
                    if (tree.nodeType[ifMember] == JsSyntaxTree.N_PROPERTY) {
                        String mName = tree.nodeName[ifMember];
                        if (mName != null && !mName.isEmpty() && !mName.endsWith("?")) {
                            if (!objProps.contains(mName)) {
                                int start = tree.nodeStart[objLitNode];
                                int line = LinterUtils.getLine(text, start);
                                int col = LinterUtils.getColumn(text, start);
                                out.add(new Problem(file, line, col, Math.min(10, text.length() - start),
                                        "Type '{ ... }' is missing required property '" + mName + "' from interface '" + typeName + "'",
                                        Problem.Severity.ERROR,
                                        ProblemCode.TS_MISSING_PROPERTIES));
                            }
                        }
                    }
                    ifMember = tree.nodeSibling[ifMember];
                }
            }
        }
    }
}
