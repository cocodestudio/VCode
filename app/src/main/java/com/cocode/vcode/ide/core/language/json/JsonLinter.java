package com.cocode.vcode.ide.core.language.json;

import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Real-time syntax and schema linter for JSON documents.
 */
public class JsonLinter {
    public static List<Problem> analyze(File file, String text) {
        List<Problem> problems = new ArrayList<>();
        JsonValidator validator = new JsonValidator();
        ValidationReport report = validator.validate(text);

        for (JsonError err : report.getErrors()) {
            Problem.Severity severity = "WARNING".equals(err.severity) ? Problem.Severity.WARNING : Problem.Severity.ERROR;
            int length = getTokenLength(text, err.line, err.column);
            problems.add(new Problem(file, err.line, err.column, length, err.message, severity));
        }
        return problems;
    }

    private static int getTokenLength(String text, int line, int column) {
        int l = 1;
        int idx = 0;
        for (int i = 0; i < text.length(); i++) {
            if (l == line) {
                idx = i + column - 1;
                break;
            }
            if (text.charAt(i) == '\n') l++;
        }
        if (idx < 0 || idx >= text.length()) return 1;
        int end = idx;
        char c = text.charAt(idx);
        if (c == '"' || c == '\'') {
            do {
                end++;
            } while (end < text.length() && text.charAt(end) != c && text.charAt(end) != '\n');
            if (end < text.length()) end++;
        } else if (Character.isLetterOrDigit(c)) {
            while (end < text.length() && Character.isLetterOrDigit(text.charAt(end))) {
                end++;
            }
        } else {
            end = idx + 1; // Just underline the single character
        }
        return Math.max(1, end - idx);
    }
}
