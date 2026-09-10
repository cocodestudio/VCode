package com.cocode.vcode.ide.core.language.ts;

import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsLinter;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real-time linter for TypeScript, enforcing type annotations, interface syntax, and TS-specific constructs.
 */
public class TsLinter {

    // Patterns
    private static final Pattern PAT_TYPE_MISMATCH = Pattern.compile(
            "\\b(?:const|let|var)\\s+(\\w+)\\s*:\\s*(string|number|boolean)\\s*=\\s*([^;\\n]+)");
    private static final Pattern PAT_RETURN_ANY = Pattern.compile(
            "\\bfunction\\s+(\\w+)[^)]*\\)\\s*:\\s*any\\b");
    private static final Pattern PAT_AS_CAST = Pattern.compile(
            "\\bas\\s+([A-Z][\\w<>|&\\[\\]]*)");
    private static final Pattern PAT_EXPORT_FN = Pattern.compile(
            "\\bexport\\s+(?:async\\s+)?function\\s+(\\w+)\\s*\\([^)]*\\)\\s*\\{");
    private static final Pattern PAT_NAMESPACE = Pattern.compile("\\bnamespace\\s+\\w+");
    private static final Pattern PAT_FUNCTION_TYPE = Pattern.compile(":\\s*Function\\b");
    private static final Pattern PAT_ENUM = Pattern.compile("\\benum\\s+(\\w+)");
    private static final Pattern PAT_REDUNDANT_TYPE = Pattern.compile(
            "\\b(?:const|let)\\s+(\\w+)\\s*:\\s*(string|number|boolean)\\s*=\\s*(['\"`].*?['\"`]|\\d+\\.?\\d*|true|false)");
    private static final Pattern PAT_NONNULL_COUNT = Pattern.compile(
            "\\b(?!(?:return|throw|case|delete|void|typeof|instanceof|in|await|yield)\\b)(\\w+)[ \\t]*!(?!=)");
    private static final Pattern PAT_UNION_UNDEFINED = Pattern.compile(
            "(\\w+)\\s*:\\s*([\\w<>]+)\\s*\\|\\s*undefined");
    private static final Pattern PAT_READONLY_ARRAY = Pattern.compile(
            "(?:interface|type)[^{]*\\{[^}]*\\b(\\w+)\\s*:\\s*([\\w<>]+)\\[]");
    private static final Pattern PAT_INLINE_OBJ_TYPE = Pattern.compile(
            ":\\s*\\{([^}]+)\\}");
    private static final Pattern PAT_NULLABLE_DECL = Pattern.compile(
            "\\b(\\w+)\\s*:[^=\\n]*(\\|\\s*null|\\|\\s*undefined)");
    private static final Pattern PAT_ANY_PARAM = Pattern.compile(
            "\\b(\\w+)\\s*:\\s*any\\b");
    private static final Pattern PAT_FN_PARAMS = Pattern.compile(
            "(?:function\\s+\\w+|=>|\\()\\s*\\(([^)]+)\\)");

    // Entry point
    public static List<Problem> analyze(File file, String text) {
        return analyze(file, text, null);
    }

    public static List<Problem> analyze(File file, String text, com.cocode.vcode.ide.core.lsp.ProjectIndex index) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        TokenStream mask = JsLexer.tokenize(text);
        List<Problem> problems = new ArrayList<>(JsLinter.analyze(file, text, mask, index));
        String[] lines = LinterUtils.splitLines(text);

        checkTypeMismatch(file, text, mask, problems);
        checkReturnAny(file, text, mask, problems);
        checkNonNullOnNullable(file, text, mask, problems);
        checkAnyType(file, text, mask, problems);
        checkAsAssertion(file, text, mask, problems);
        checkExportedFnReturnType(file, text, mask, problems);
        checkOptionalBeforeRequired(file, text, mask, problems);
        checkNamespace(file, text, mask, problems);
        checkFunctionType(file, text, mask, problems);
        checkEnum(file, text, mask, problems);
        checkRedundantType(file, text, mask, problems);
        checkHighNonNullAssertion(file, text, mask, problems);
        checkUnionUndefined(file, text, mask, problems);
        checkReadonlyArray(file, text, mask, problems);
        checkInlineObjectType(file, text, mask, problems);

        return problems;
    }

    private static boolean isRangeMasked(TokenStream mask, int start, int end) {
        if (mask == null) return false;
        int clampedEnd = Math.min(end, mask.length);
        for (int k = Math.max(0, start); k < clampedEnd; k++) {
            if (mask.isMasked(k)) return true;
        }
        return false;
    }

    // TS-specific rules
    private static void checkTypeMismatch(File file, String text, TokenStream mask, List<Problem> out) {
        Matcher m = PAT_TYPE_MISMATCH.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            String name = m.group(1);
            String declType = m.group(2);
            String value = Objects.requireNonNull(m.group(3)).trim();
            String valueType = inferLiteralType(value);
            if (valueType != null && !valueType.equals(declType)) {
                int nameStart = m.start(1);
                int line = LinterUtils.getLine(text, nameStart);
                int col = LinterUtils.getColumn(text, nameStart);
                out.add(new Problem(file, line, col, Objects.requireNonNull(name).length(),
                        "Type mismatch: cannot assign '" + valueType + "' to '" + declType + "' for '" + name + "'",
                        Problem.Severity.ERROR));
            }
        }
    }

    private static String inferLiteralType(String value) {
        if (value.startsWith("\"") || value.startsWith("'") || value.startsWith("`"))
            return "string";
        if (value.equals("true") || value.equals("false")) return "boolean";
        try {
            Double.parseDouble(value);
            return "number";
        } catch (NumberFormatException e) { /* not a number */ }
        return null;
    }

    private static void checkReturnAny(File file, String text, TokenStream mask, List<Problem> out) {
        Matcher m = PAT_RETURN_ANY.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int nameStart = m.start(1);
            int line = LinterUtils.getLine(text, nameStart);
            int col = LinterUtils.getColumn(text, nameStart);
            out.add(new Problem(file, line, col, Objects.requireNonNull(m.group(1)).length(),
                    "Function '" + m.group(1) + "' returns 'any': specify an explicit return type instead",
                    Problem.Severity.ERROR));
        }
    }

    private static void checkNonNullOnNullable(File file, String text, TokenStream mask, List<Problem> out) {
        // Find variables typed as X | null or X | undefined, then check for ! usage on them
        Matcher declM = PAT_NULLABLE_DECL.matcher(text);
        List<String> nullableVars = new ArrayList<>();
        while (declM.find()) {
            if (!mask.isMasked(declM.start())) nullableVars.add(declM.group(1));
        }
        for (String varName : nullableVars) {
            int vLen = varName.length();
            int idx = text.indexOf(varName);
            while (idx >= 0) {
                if (!mask.isMasked(idx)) {
                    boolean isWordBoundaryBefore = (idx == 0 || !Character.isLetterOrDigit(text.charAt(idx - 1)) && text.charAt(idx - 1) != '_' && text.charAt(idx - 1) != '$');
                    int afterIdx = idx + vLen;
                    boolean isWordBoundaryAfter = (afterIdx >= text.length() || !Character.isLetterOrDigit(text.charAt(afterIdx)) && text.charAt(afterIdx) != '_' && text.charAt(afterIdx) != '$');
                    if (isWordBoundaryBefore && isWordBoundaryAfter) {
                        int p = afterIdx;
                        while (p < text.length() && (text.charAt(p) == ' ' || text.charAt(p) == '\t')) p++;
                        if (p < text.length() && text.charAt(p) == '!') {
                            boolean isNotEquals = (p + 1 < text.length() && text.charAt(p + 1) == '=');
                            if (!isNotEquals && !mask.isMasked(p)) {
                                int line = LinterUtils.getLine(text, idx);
                                int col = LinterUtils.getColumn(text, idx);
                                out.add(new Problem(file, line, col, (p + 1) - idx,
                                        "Non-null assertion '!' used on a possibly-null value: ensure this cannot be null",
                                        Problem.Severity.ERROR));
                            }
                        }
                    }
                }
                idx = text.indexOf(varName, idx + vLen);
            }
        }
    }

    private static void checkAnyType(File file, String text, TokenStream mask, List<Problem> out) {
        // Skip return-type 'any' (already covered by checkReturnAny), flag param/var 'any'
        Matcher m = PAT_ANY_PARAM.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            // skip if preceded by ')' (return type position handled separately)
            int pos = m.start();
            // quick check: is this in a return-type position? skip those
            String before = text.substring(Math.max(0, pos - 5), pos);
            if (before.trim().endsWith(")")) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, Objects.requireNonNull(m.group(1)).length(),
                    "Explicit 'any' type for '" + m.group(1) + "': weakens type safety — use 'unknown' or a specific type",
                    Problem.Severity.WARNING));
        }
    }

    private static void checkAsAssertion(File file, String text, TokenStream mask, List<Problem> out) {
        Matcher m = PAT_AS_CAST.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int nameStart = m.start(1);
            int line = LinterUtils.getLine(text, nameStart);
            int col = LinterUtils.getColumn(text, nameStart);
            out.add(new Problem(file, line, col, Objects.requireNonNull(m.group(1)).length(),
                    "Type assertion 'as " + m.group(1) + "': make sure the cast is safe",
                    Problem.Severity.WARNING));
        }
    }

    private static void checkExportedFnReturnType(File file, String text, TokenStream mask, List<Problem> out) {
        Matcher m = PAT_EXPORT_FN.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            // The pattern already requires the function closes ) then { without ': Type'
            int line = LinterUtils.getLine(text, m.start(1));
            int col = LinterUtils.getColumn(text, m.start(1));
            out.add(new Problem(file, line, col, Objects.requireNonNull(m.group(1)).length(),
                    "Exported function '" + m.group(1) + "' is missing a return type annotation",
                    Problem.Severity.WARNING));
        }
    }

    private static void checkOptionalBeforeRequired(File file, String text, TokenStream mask, List<Problem> out) {
        // Match function parameter lists
        Matcher m = PAT_FN_PARAMS.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            List<String> params = splitParameters(Objects.requireNonNull(m.group(1)));
            String lastOptional = null;
            for (String param : params) {
                String p = param.trim();
                if (p.isEmpty()) continue;
                boolean isOptional = hasDefaultOrOptional(p);
                if (!isOptional && lastOptional != null) {
                    int line = LinterUtils.getLine(text, m.start());
                    int col = LinterUtils.getColumn(text, m.start());
                    out.add(new Problem(file, line, col, p.length(),
                            "Optional parameter '" + lastOptional + "?' before required parameter '" + p.split(":")[0].trim() + "': required params must come first",
                            Problem.Severity.WARNING));
                }
                if (isOptional) lastOptional = p.split("[?=:]")[0].trim();
                else lastOptional = null;
            }
        }
    }

    private static boolean hasDefaultOrOptional(String param) {
        if (param.contains("?")) return true;
        int depth = 0;
        for (int i = 0; i < param.length(); i++) {
            char c = param.charAt(i);
            if (c == '<' || c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == '>' || c == ')' || c == ']' || c == '}') {
                if (depth > 0) depth--;
            } else if (c == '=' && depth == 0) {
                if (i + 1 < param.length() && (param.charAt(i + 1) == '>' || param.charAt(i + 1) == '=')) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private static List<String> splitParameters(String paramList) {
        List<String> list = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < paramList.length(); i++) {
            char c = paramList.charAt(i);
            if (c == '(' || c == '[' || c == '{' || c == '<') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}' || c == '>') {
                if (depth > 0) depth--;
            } else if (c == ',' && depth == 0) {
                list.add(paramList.substring(start, i));
                start = i + 1;
            }
        }
        if (start < paramList.length()) {
            list.add(paramList.substring(start));
        }
        return list;
    }

    private static void checkNamespace(File file, String text, TokenStream mask, List<Problem> out) {
        Matcher m = PAT_NAMESPACE.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, 9,
                    "'namespace' is discouraged in modern TypeScript: use ES modules (import/export) instead",
                    Problem.Severity.WARNING));
        }
    }

    private static void checkFunctionType(File file, String text, TokenStream mask, List<Problem> out) {
        Matcher m = PAT_FUNCTION_TYPE.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, 8,
                    "'Function' type is too broad: specify the exact signature, e.g. '(x: T) => R'",
                    Problem.Severity.WARNING));
        }
    }

    private static void checkEnum(File file, String text, TokenStream mask, List<Problem> out) {
        Matcher m = PAT_ENUM.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int nameStart = m.start(1);
            int line = LinterUtils.getLine(text, nameStart);
            int col = LinterUtils.getColumn(text, nameStart);
            out.add(new Problem(file, line, col, Objects.requireNonNull(m.group(1)).length(),
                    "Enums add runtime overhead: consider 'const' object with 'as const' for better tree-shaking",
                    Problem.Severity.INFO));
        }
    }

    private static void checkRedundantType(File file, String text, TokenStream mask, List<Problem> out) {
        Matcher m = PAT_REDUNDANT_TYPE.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            String name = m.group(1);
            String declType = m.group(2);
            String value = m.group(3);
            String inferred = inferLiteralType(Objects.requireNonNull(value));
            if (Objects.equals(declType, inferred)) {
                int nameStart = m.start(1);
                int line = LinterUtils.getLine(text, nameStart);
                int col = LinterUtils.getColumn(text, nameStart);
                out.add(new Problem(file, line, col, Objects.requireNonNull(name).length(),
                        "Type '" + declType + "' is inferred: remove the explicit annotation ':" + declType + "' for '" + name + "'",
                        Problem.Severity.WARNING));
            }
        }
    }

    private static void checkHighNonNullAssertion(File file, String text, TokenStream mask, List<Problem> out) {
        Matcher m = PAT_NONNULL_COUNT.matcher(text);
        int count = 0;
        while (m.find()) {
            if (!mask.isMasked(m.start()) && !mask.isMasked(m.end() - 1)) count++;
        }
        if (count > 3) {
            out.add(new Problem(file, 1, 1, 1,
                    "High use of '!' non-null assertion (" + count + " occurrences): consider stricter null handling",
                    Problem.Severity.WARNING));
        }
    }

    private static void checkUnionUndefined(File file, String text, TokenStream mask, List<Problem> out) {
        Matcher m = PAT_UNION_UNDEFINED.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, m.group().length(),
                    "'Type | undefined' in parameter can be written as '" + m.group(1) + "?: " + m.group(2) + "'",
                    Problem.Severity.INFO));
        }
    }

    private static void checkReadonlyArray(File file, String text, TokenStream mask, List<Problem> out) {
        Matcher m = PAT_READONLY_ARRAY.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int nameStart = m.start(1);
            int line = LinterUtils.getLine(text, nameStart);
            int col = LinterUtils.getColumn(text, nameStart);
            out.add(new Problem(file, line, col, Objects.requireNonNull(m.group(1)).length(),
                    "Consider 'readonly " + m.group(1) + ": " + m.group(2) + "[]' to prevent accidental mutation",
                    Problem.Severity.INFO));
        }
    }

    private static void checkInlineObjectType(File file, String text, TokenStream mask, List<Problem> out) {
        Matcher m = PAT_INLINE_OBJ_TYPE.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            String body = m.group(1);
            // Verify this is a type signature, not an object literal
            if (body.contains(",") || body.contains(": true") || body.contains(": false") || body.contains(": null")) {
                continue;
            }
            // count properties (split by ;)
            int propCount = 0;
            for (String part : Objects.requireNonNull(body).split(";")) {
                if (!part.trim().isEmpty()) propCount++;
            }
            if (propCount > 2) {
                int line = LinterUtils.getLine(text, m.start());
                int col = LinterUtils.getColumn(text, m.start());
                out.add(new Problem(file, line, col, m.group().length(),
                        "Inline object type with " + propCount + " properties: consider extracting to a named interface",
                        Problem.Severity.INFO));
            }
        }
    }
}
