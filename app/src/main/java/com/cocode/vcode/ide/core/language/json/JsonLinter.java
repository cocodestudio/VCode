package com.cocode.vcode.ide.core.language.json;

import com.cocode.vcode.ide.core.diagnostic.ProblemCode;
import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Linter for JSON and JSONC using the flat-array AST.
 * Flags syntax errors (N_ERROR), duplicate keys within the same object,
 * comments in strict mode, and enforces single root value.
 * Also delegates to {@link JsonSchemaManager} for manifest validation.
 */
public class JsonLinter {

    public static boolean isJsoncMode(File file) {
        if (file == null) return false;
        return isJsoncMode(file.getName(), file.getPath());
    }

    public static boolean isJsoncMode(String name, String path) {
        if (name == null && path == null) return false;
        String lowerName = (name != null ? name : path).toLowerCase();
        int slash = Math.max(lowerName.lastIndexOf('/'), lowerName.lastIndexOf('\\'));
        String baseName = slash >= 0 ? lowerName.substring(slash + 1) : lowerName;

        if (baseName.endsWith(".jsonc")) return true;
        if (baseName.startsWith("tsconfig") && baseName.endsWith(".json")) return true;
        if (baseName.startsWith("jsconfig") && baseName.endsWith(".json")) return true;
        if (baseName.startsWith(".eslintrc") && baseName.endsWith(".json")) return true;
        if ("settings.json".equals(baseName)) return true;

        if (path != null) {
            String lowerPath = path.toLowerCase();
            if (lowerPath.contains("/.vcode/") || lowerPath.contains("\\.vcode\\")) return true;
            if (lowerPath.contains("/.vscode/") || lowerPath.contains("\\.vscode\\")) return true;
        }
        return false;
    }

    public static List<Problem> analyze(File file, String text) {
        List<Problem> problems = new ArrayList<>();
        if (text == null || text.isEmpty()) return problems;

        JsonTokenStream stream = JsonLexer.tokenize(text);

        int mode = com.cocode.vcode.ide.core.language.base.ParseModeGate.select(
                com.cocode.vcode.ide.core.language.base.ParseModeGate.SizeMetric.NODES,
                stream.length, 1000, 5000, 15000);

        if (mode == com.cocode.vcode.ide.core.language.js.ParseResult.MODE_TOKENIZE_ONLY) {
            return problems;
        }

        boolean isJsonc = isJsoncMode(file);
        if (!isJsonc) {
            int k = 0;
            while (k < stream.length) {
                if (stream.types[k] == JsonTokenStream.TK_COMMENT) {
                    int cStart = stream.tokenStart[k];
                    int cEnd = cStart;
                    while (cEnd < stream.length && stream.tokenStart[cEnd] == cStart) {
                        cEnd++;
                    }
                    int line = LinterUtils.getLine(text, cStart);
                    int col = LinterUtils.getColumn(text, cStart);
                    problems.add(new Problem(file, line, col, Math.max(1, cEnd - cStart),
                            "Comments are not permitted in standard JSON", Problem.Severity.WARNING,
                            ProblemCode.JSON_COMMENTS_DISALLOWED));
                    k = Math.max(k + 1, cEnd);
                } else {
                    k++;
                }
            }
        }

        JsonSyntaxTree tree = JsonParser.parse(stream, text, isJsonc);

        for (int i = 1; i < tree.nodeCount; i++) {
            byte type = tree.nodeType[i];

            if (type == JsonSyntaxTree.N_ERROR) {
                int start = tree.nodeStart[i];
                int end = tree.nodeEnd[i];
                int len = Math.max(1, end - start);

                int line = LinterUtils.getLine(text, start);
                int col = LinterUtils.getColumn(text, start);

                String msg = tree.nodeName[i];
                if (msg == null || msg.isEmpty()) msg = "Syntax error";

                ProblemCode code = ProblemCode.JSON_SYNTAX_ERROR;
                if (msg.contains("Multiple root values")) {
                    code = ProblemCode.JSON_MULTIPLE_ROOTS;
                }

                problems.add(new Problem(file, line, col, len, msg, Problem.Severity.ERROR, code));
            } else if (type == JsonSyntaxTree.N_OBJECT) {
                Set<String> seenKeys = new HashSet<>();
                int child = tree.nodeChild[i];
                int childLoop = 0;
                while (child > 0 && child < tree.nodeCount && ++childLoop <= tree.nodeCount) {
                    if (tree.nodeType[child] == JsonSyntaxTree.N_KEY) {
                        String keyName = tree.nodeName[child];
                        if (keyName != null && !keyName.isEmpty()) {
                            String normalized = normalizeKey(keyName);
                            if (!seenKeys.add(normalized)) {
                                int start = tree.nodeStart[child];
                                int len = keyName != null ? keyName.length() : Math.max(1, tree.nodeEnd[child] - start);
                                int line = LinterUtils.getLine(text, start);
                                int col = LinterUtils.getColumn(text, start);

                                problems.add(new Problem(file, line, col, len, "Duplicate object key: " + keyName, Problem.Severity.WARNING,
                                        ProblemCode.JSON_DUPLICATE_KEY));
                            }
                        }
                    }
                    child = tree.nodeSibling[child];
                }
            }
        }

        // Schema-driven validation (e.g. package.json, tsconfig.json)
        List<Problem> schemaProblems = JsonSchemaManager.validate(file, file != null ? file.getPath() : null, text, tree);
        if (schemaProblems != null && !schemaProblems.isEmpty()) {
            problems.addAll(schemaProblems);
        }

        return problems;
    }

    static String normalizeKey(String key) {
        if (key == null) return "";
        key = key.trim();
        int start = 0;
        int end = key.length();
        if (start < end && (key.charAt(start) == '"' || key.charAt(start) == '\'')) {
            start++;
        }
        if (end > start && (key.charAt(end - 1) == '"' || key.charAt(end - 1) == '\'')) {
            end--;
        }
        return key.substring(start, end).trim();
    }
}
