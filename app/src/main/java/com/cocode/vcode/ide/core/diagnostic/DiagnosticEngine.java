package com.cocode.vcode.ide.core.diagnostic;

import com.cocode.vcode.ide.core.editor.indent.BracketMatcher;
import com.cocode.vcode.ide.core.language.css.CssLinter;
import com.cocode.vcode.ide.core.language.html.HtmlLinter;
import com.cocode.vcode.ide.core.language.js.JsLinter;
import com.cocode.vcode.ide.core.language.json.JsonLinter;
import com.cocode.vcode.ide.core.language.ts.TsLinter;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central orchestrator and SPI registry for code diagnostics and linting across all supported languages.
 */
public class DiagnosticEngine {
    private static final int MAX_PROBLEMS = 60;
    private static final List<DiagnosticProvider> providers = new CopyOnWriteArrayList<>();

    static {
        registerBuiltInProviders();
    }

    private static void registerBuiltInProviders() {
        registerProvider(new HtmlDiagnosticProvider());
        registerProvider(new CssDiagnosticProvider());
        registerProvider(new JsDiagnosticProvider());
        registerProvider(new TsDiagnosticProvider());
        registerProvider(new MarkdownDiagnosticProvider());
    }

    /**
     * Registers a new diagnostic provider (e.g. from an extension, ESLint, or custom linter).
     */
    public static void registerProvider(DiagnosticProvider provider) {
        if (provider != null && !providers.contains(provider)) {
            providers.add(provider);
        }
    }

    /**
     * Unregisters a diagnostic provider.
     */
    public static void unregisterProvider(DiagnosticProvider provider) {
        if (provider != null) {
            providers.remove(provider);
        }
    }

    public static List<DiagnosticProvider> getProviders() {
        return Collections.unmodifiableList(providers);
    }

    /**
     * Tier 1: Fast syntactic checks executed immediately during active typing (<5ms).
     * Validates bracket nesting, JSON structure, and basic lexical errors.
     */
    public static List<Problem> runSyntacticLint(File file, String text, FileType type) {
        if (text == null || text.isEmpty()) return new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        try {
            if (type != null && type.isTextBased()
                    && type != FileType.CSS && type != FileType.SCSS
                    && type != FileType.HTML && type != FileType.MARKDOWN) {
                problems.addAll(BracketMatcher.findMismatches(file, text));
            }
            if (type == FileType.JSON) {
                problems.addAll(JsonLinter.analyze(file, text));
            }
        } catch (Throwable ignored) {
        }
        return problems;
    }

    /**
     * Tier 2: Semantic checks executed after a typing pause (e.g. 400ms idle).
     * Executes all applicable registered {@link DiagnosticProvider} instances.
     */
    public static List<Problem> runSemanticLint(File file, String text, FileType type, ProjectIndex index) {
        if (text == null || text.isEmpty()) return new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        String path = file != null ? file.getAbsolutePath() : null;

        for (DiagnosticProvider provider : providers) {
            try {
                if (provider.canLint(type, path)) {
                    List<Problem> results = provider.lint(file, text, type, index);
                    if (results != null) {
                        problems.addAll(results);
                    }
                }
            } catch (Throwable e) {
                problems.add(new Problem(file, 1, 1, 1,
                        "Internal Linter Error (" + provider.getId() + "): " + e.getMessage(),
                        Problem.Severity.ERROR));
            }
        }

        return problems;
    }

    public static List<Problem> analyze(File file, String text, FileType type) {
        return analyze(file, text, type, null);
    }

    public static List<Problem> analyze(File file, String text, FileType type, ProjectIndex index) {
        if (text == null || text.isEmpty()) return new ArrayList<>();

        List<Problem> problems = new ArrayList<>();
        problems.addAll(runSyntacticLint(file, text, type));
        problems.addAll(runSemanticLint(file, text, type, index));

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

    // Built-in Diagnostic Providers

    private static class HtmlDiagnosticProvider implements DiagnosticProvider {
        @Override
        public String getId() {
            return "builtin-html-linter";
        }

        @Override
        public boolean canLint(FileType fileType, String path) {
            return fileType == FileType.HTML;
        }

        @Override
        public List<Problem> lint(File file, String text, FileType fileType, ProjectIndex index) {
            List<Problem> problems = new ArrayList<>();
            com.cocode.vcode.ide.core.language.js.ParseResult parseResult = (index != null && file != null)
                    ? index.getParseResult(file.getAbsolutePath()) : null;
            if (parseResult != null && parseResult.htmlTree != null && parseResult.htmlTokens != null) {
                problems.addAll(HtmlLinter.analyze(file, text, parseResult.htmlTree, parseResult.htmlTokens));
            } else {
                problems.addAll(HtmlLinter.analyze(file, text));
            }
            if (parseResult == null) {
                try {
                    parseResult = com.cocode.vcode.ide.core.language.html.HtmlParser.parse(text);
                } catch (Throwable ignored) {
                }
            }
            if (parseResult != null && parseResult.embeddedResults != null) {
                for (com.cocode.vcode.ide.core.language.js.ParseResult.EmbeddedResult emb : parseResult.embeddedResults) {
                    String embeddedText = text.substring(emb.startOffset, Math.min(emb.endOffset, text.length()));
                    List<Problem> sub = new ArrayList<>();

                    if (emb.result.cssTree != null) {
                        sub = CssLinter.analyze(file, embeddedText);
                    } else if (emb.result.tree != null) {
                        sub = JsLinter.analyze(file, embeddedText, index);
                    }

                    for (Problem p : sub) {
                        int pOffset = com.cocode.vcode.ide.core.diagnostic.util.LinterUtils.lineStartOffset(embeddedText, p.getLine()) + p.getColumn() - 1;
                        int absoluteOffset = emb.startOffset + pOffset;
                        int absLine = com.cocode.vcode.ide.core.diagnostic.util.LinterUtils.getLine(text, absoluteOffset);
                        int absCol = com.cocode.vcode.ide.core.diagnostic.util.LinterUtils.getColumn(text, absoluteOffset);
                        problems.add(new Problem(p.getFile(), absLine, absCol, p.getLength(), p.getMessage(), p.getSeverity(), p.getCode()));
                    }
                }
            }
            return problems;
        }
    }

    private static class CssDiagnosticProvider implements DiagnosticProvider {
        @Override
        public String getId() {
            return "builtin-css-linter";
        }

        @Override
        public boolean canLint(FileType fileType, String path) {
            return fileType == FileType.CSS || fileType == FileType.SCSS;
        }

        @Override
        public List<Problem> lint(File file, String text, FileType fileType, ProjectIndex index) {
            List<Problem> problems = new ArrayList<>();
            com.cocode.vcode.ide.core.language.js.ParseResult parseResult = (index != null && file != null)
                    ? index.getParseResult(file.getAbsolutePath()) : null;
            if (parseResult != null && parseResult.cssTree != null && parseResult.cssTokens != null) {
                problems.addAll(CssLinter.analyze(file, text, parseResult.cssTree, parseResult.cssTokens));
            } else {
                problems.addAll(CssLinter.analyze(file, text));
            }
            return problems;
        }
    }

    private static class JsDiagnosticProvider implements DiagnosticProvider {
        @Override
        public String getId() {
            return "builtin-js-linter";
        }

        @Override
        public boolean canLint(FileType fileType, String path) {
            return fileType == FileType.JAVASCRIPT;
        }

        @Override
        public List<Problem> lint(File file, String text, FileType fileType, ProjectIndex index) {
            return new ArrayList<>(JsLinter.analyze(file, text, index));
        }
    }

    private static class TsDiagnosticProvider implements DiagnosticProvider {
        @Override
        public String getId() {
            return "builtin-ts-linter";
        }

        @Override
        public boolean canLint(FileType fileType, String path) {
            return fileType == FileType.TYPESCRIPT;
        }

        @Override
        public List<Problem> lint(File file, String text, FileType fileType, ProjectIndex index) {
            return new ArrayList<>(TsLinter.analyze(file, text, index));
        }
    }

    private static class MarkdownDiagnosticProvider implements DiagnosticProvider {
        @Override
        public String getId() {
            return "builtin-markdown-linter";
        }

        @Override
        public boolean canLint(FileType fileType, String path) {
            return fileType == FileType.MARKDOWN;
        }

        @Override
        public List<Problem> lint(File file, String text, FileType fileType, ProjectIndex index) {
            return new ArrayList<>(com.cocode.vcode.ide.core.language.md.MarkdownLinter.analyze(file, text));
        }
    }
}
