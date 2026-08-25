package com.cocode.vcode.ide.core.language.ts;

import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.diagnostic.util.TokenMask;
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
    private static final Pattern PAT_NONNULL_COUNT = Pattern.compile("\\w+\\s*!");
    private static final Pattern PAT_UNION_UNDEFINED = Pattern.compile(
            "(\\w+)\\s*:\\s*([\\w<>]+)\\s*\\|\\s*undefined");
    private static final Pattern PAT_READONLY_ARRAY = Pattern.compile(
            "(?:interface|type)[^{]*\\{[^}]*\\b(\\w+)\\s*:\\s*([\\w<>]+)\\[]");
    private static final Pattern PAT_INLINE_OBJ_TYPE = Pattern.compile(
            ":\\s*\\{([^}]+)\\}");

    // Entry point
    public static List<Problem> analyze(File file, String text) {
        return analyze(file, text, null);
    }

    public static List<Problem> analyze(File file, String text, com.cocode.vcode.ide.core.lsp.ProjectIndex index) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        List<Problem> problems = new ArrayList<>(JsLinter.analyze(file, text, index));

        TokenMask mask = TokenMask.build(text, "ts");
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

    // TS-specific rules
    private static void checkTypeMismatch(File file, String text, TokenMask mask, List<Problem> out) {
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

    private static void checkReturnAny(File file, String text, TokenMask mask, List<Problem> out) {
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

    private static void checkNonNullOnNullable(File file, String text, TokenMask mask, List<Problem> out) {
        // Find variables typed as X | null or X | undefined, then check for ! usage on them
        Pattern nullableDecl = Pattern.compile("\\b(\\w+)\\s*:[^=\\n]*(\\|\\s*null|\\|\\s*undefined)");
        Matcher declM = nullableDecl.matcher(text);
        List<String> nullableVars = new ArrayList<>();
        while (declM.find()) {
            if (!mask.isMasked(declM.start())) nullableVars.add(declM.group(1));
        }
        for (String varName : nullableVars) {
            Pattern assertPat = Pattern.compile("\\b" + Pattern.quote(varName) + "\\s*!");
            Matcher am = assertPat.matcher(text);
            while (am.find()) {
                if (mask.isMasked(am.start())) continue;
                int line = LinterUtils.getLine(text, am.start());
                int col = LinterUtils.getColumn(text, am.start());
                out.add(new Problem(file, line, col, am.group().length(),
                        "Non-null assertion '!' used on a possibly-null value: ensure this cannot be null",
                        Problem.Severity.ERROR));
            }
        }
    }

    private static void checkAnyType(File file, String text, TokenMask mask, List<Problem> out) {
        // Skip return-type 'any' (already covered by checkReturnAny), flag param/var 'any'
        Pattern anyParam = Pattern.compile("\\b(\\w+)\\s*:\\s*any\\b");
        Matcher m = anyParam.matcher(text);
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

    private static void checkAsAssertion(File file, String text, TokenMask mask, List<Problem> out) {
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

    private static void checkExportedFnReturnType(File file, String text, TokenMask mask, List<Problem> out) {
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

    private static void checkOptionalBeforeRequired(File file, String text, TokenMask mask, List<Problem> out) {
        // Match function parameter lists
        Pattern fnParams = Pattern.compile("(?:function\\s+\\w+|=>|\\()\\s*\\(([^)]+)\\)");
        Matcher m = fnParams.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            String[] params = Objects.requireNonNull(m.group(1)).split(",");
            String lastOptional = null;
            for (String param : params) {
                String p = param.trim();
                boolean isOptional = p.contains("?") || p.contains("= ");
                if (!isOptional && lastOptional != null) {
                    int line = LinterUtils.getLine(text, m.start());
                    int col = LinterUtils.getColumn(text, m.start());
                    out.add(new Problem(file, line, col, p.length(),
                            "Optional parameter '" + lastOptional + "?' before required parameter '" + p.split(":")[0].trim() + "': required params must come first",
                            Problem.Severity.WARNING));
                }
                if (isOptional) lastOptional = p.split("[?:]")[0].trim();
                else lastOptional = null;
            }
        }
    }

    private static void checkNamespace(File file, String text, TokenMask mask, List<Problem> out) {
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

    private static void checkFunctionType(File file, String text, TokenMask mask, List<Problem> out) {
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

    private static void checkEnum(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_ENUM.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int nameStart = m.start(1);
            int line = LinterUtils.getLine(text, nameStart);
            int col = LinterUtils.getColumn(text, nameStart);
            out.add(new Problem(file, line, col, Objects.requireNonNull(m.group(1)).length(),
                    "Enums add runtime overhead: consider 'const' object with 'as const' for better tree-shaking",
                    Problem.Severity.WARNING));
        }
    }

    private static void checkRedundantType(File file, String text, TokenMask mask, List<Problem> out) {
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

    private static void checkHighNonNullAssertion(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_NONNULL_COUNT.matcher(text);
        int count = 0;
        while (m.find()) {
            if (!mask.isMasked(m.start())) count++;
        }
        if (count > 3) {
            out.add(new Problem(file, 1, 1, 1,
                    "High use of '!' non-null assertion (" + count + " occurrences): consider stricter null handling",
                    Problem.Severity.WARNING));
        }
    }

    private static void checkUnionUndefined(File file, String text, TokenMask mask, List<Problem> out) {
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

    private static void checkReadonlyArray(File file, String text, TokenMask mask, List<Problem> out) {
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

    private static void checkInlineObjectType(File file, String text, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_INLINE_OBJ_TYPE.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            String body = m.group(1);
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
