package com.cocode.vcode.ide.core.diagnostic;

import com.cocode.vcode.ide.core.language.css.CssLinter;
import com.cocode.vcode.ide.core.language.html.HtmlLinter;
import com.cocode.vcode.ide.core.language.js.JsLinter;
import com.cocode.vcode.ide.core.language.json.JsonLinter;
import com.cocode.vcode.ide.core.language.ts.TsLinter;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Central orchestrator for code diagnostics and linting across all supported languages.
 */
public class DiagnosticEngine {
    private static final int MAX_PROBLEMS = 60;

    public static List<Problem> analyze(File file, String text, FileType type) {
        if (text == null || text.isEmpty()) return new ArrayList<>();

        List<Problem> problems = new ArrayList<>();

        // BracketLinter handles () [] {} — skip for CSS/SCSS since CssLinter owns {} there
        if (type != null && type.isTextBased()
                && type != FileType.CSS && type != FileType.SCSS) {
            problems.addAll(BracketLinter.analyze(file, text));
        }

        if (type == FileType.JSON) {
            problems.addAll(JsonLinter.analyze(file, text));
        } else if (type == FileType.HTML) {
            problems.addAll(HtmlLinter.analyze(file, text));
        } else if (type == FileType.CSS || type == FileType.SCSS) {
            problems.addAll(CssLinter.analyze(file, text));
        } else if (type == FileType.JAVASCRIPT) {
            problems.addAll(JsLinter.analyze(file, text));
        } else if (type == FileType.TYPESCRIPT) {
            problems.addAll(TsLinter.analyze(file, text));
        } else if (type == FileType.MARKDOWN) {
            problems.addAll(com.cocode.vcode.ide.core.language.md.MarkdownLinter.analyze(file, text));
        }

        problems = deduplicate(problems);

        problems.sort(Comparator
                .comparingInt((Problem p) -> p.getSeverity().ordinal())
                .thenComparingInt(Problem::getLine)
                .thenComparingInt(Problem::getColumn));

        if (problems.size() > MAX_PROBLEMS) {
            int total = problems.size();
            problems = new ArrayList<>(problems.subList(0, MAX_PROBLEMS));
            problems.add(new Problem(file, 0, 0, 1,
                    (total - MAX_PROBLEMS) + " more issues not shown — fix current errors first",
                    Problem.Severity.INFO));
        }

        return problems;
    }

    private static List<Problem> deduplicate(List<Problem> problems) {
        Set<String> seen = new LinkedHashSet<>();
        List<Problem> unique = new ArrayList<>();
        for (Problem p : problems) {
            String key = p.getLine() + ":" + p.getColumn() + ":" + p.getMessage();
            if (seen.add(key)) unique.add(p);
        }
        return unique;
    }
}
