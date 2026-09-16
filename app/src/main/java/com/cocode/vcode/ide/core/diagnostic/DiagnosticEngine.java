package com.cocode.vcode.ide.core.diagnostic;

import com.cocode.vcode.ide.core.editor.indent.BracketMatcher;
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
        return analyze(file, text, type, null);
    }

    public static List<Problem> analyze(File file, String text, FileType type, com.cocode.vcode.ide.core.lsp.ProjectIndex index) {
        if (text == null || text.isEmpty()) return new ArrayList<>();

        List<Problem> problems = new ArrayList<>();

        try {
            // BracketMatcher handles () [] {} for programming languages — skip for CSS/SCSS, HTML, and Markdown
            if (type != null && type.isTextBased()
                    && type != FileType.CSS && type != FileType.SCSS
                    && type != FileType.HTML && type != FileType.MARKDOWN) {
                problems.addAll(BracketMatcher.findMismatches(file, text));
            }

            if (type == FileType.JSON) {
                problems.addAll(JsonLinter.analyze(file, text));
            } else if (type == FileType.HTML) {
                com.cocode.vcode.ide.core.language.js.ParseResult parseResult = (index != null && file != null)
                        ? index.getParseResult(file.getAbsolutePath()) : null;
                if (parseResult != null && parseResult.htmlTree != null && parseResult.htmlTokens != null) {
                    problems.addAll(HtmlLinter.analyze(file, text, parseResult.htmlTree, parseResult.htmlTokens));
                } else {
                    problems.addAll(HtmlLinter.analyze(file, text));
                }
                if (parseResult != null && parseResult.embeddedResults != null) {
                    for (com.cocode.vcode.ide.core.language.js.ParseResult.EmbeddedResult emb : parseResult.embeddedResults) {
                        String embeddedText = text.substring(emb.startOffset, Math.min(emb.endOffset, text.length()));
                        List<Problem> sub = new ArrayList<>();

                        if (emb.result.cssTree != null) {
                            sub = CssLinter.analyze(file, embeddedText);
                        }

                        for (Problem p : sub) {
                            int pOffset = com.cocode.vcode.ide.core.diagnostic.util.LinterUtils.lineStartOffset(embeddedText, p.getLine()) + p.getColumn() - 1;
                            int absoluteOffset = emb.startOffset + pOffset;
                            int absLine = com.cocode.vcode.ide.core.diagnostic.util.LinterUtils.getLine(text, absoluteOffset);
                            int absCol = com.cocode.vcode.ide.core.diagnostic.util.LinterUtils.getColumn(text, absoluteOffset);
                            problems.add(new Problem(p.getFile(), absLine, absCol, p.getLength(), p.getMessage(), p.getSeverity()));
                        }
                    }
                }
            } else if (type == FileType.CSS || type == FileType.SCSS) {
                com.cocode.vcode.ide.core.language.js.ParseResult parseResult = (index != null && file != null)
                        ? index.getParseResult(file.getAbsolutePath()) : null;
                if (parseResult != null && parseResult.cssTree != null && parseResult.cssTokens != null) {
                    problems.addAll(CssLinter.analyze(file, text, parseResult.cssTree, parseResult.cssTokens));
                } else {
                    problems.addAll(CssLinter.analyze(file, text));
                }
            } else if (type == FileType.JAVASCRIPT) {
                problems.addAll(JsLinter.analyze(file, text, index));
            } else if (type == FileType.TYPESCRIPT) {
                problems.addAll(TsLinter.analyze(file, text, index));
            } else if (type == FileType.MARKDOWN) {
                problems.addAll(com.cocode.vcode.ide.core.language.md.MarkdownLinter.analyze(file, text));
            }
        } catch (Throwable e) {
            problems.add(new Problem(file, 1, 1, 1, "Internal Linter Error: " + e.getMessage() + " (" + e.getClass().getSimpleName() + ")", Problem.Severity.ERROR));
        }

        return deduplicateAndSort(file, problems);
    }

    public static List<Problem> deduplicateAndSort(File file, List<Problem> problems) {
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
