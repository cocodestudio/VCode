package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Real-time linter for JavaScript, checking syntax errors, undefined variables, missing semicolons, and code quality.
 */
public class JsLinter {

    // Patterns compiled once
    // Entry point
    public static List<Problem> analyze(File file, String text) {
        return analyze(file, text, null);
    }

    public static List<Problem> analyze(File file, String text, com.cocode.vcode.ide.core.lsp.ProjectIndex index) {
        return analyze(file, text, null, index);
    }

    public static class AnalysisContext {
        public final JsSyntaxTree tree;
        public final ScopeTree scopeTree;
        public final TokenStream stream;

        public AnalysisContext(JsSyntaxTree tree, ScopeTree scopeTree, TokenStream stream) {
            this.tree = tree;
            this.scopeTree = scopeTree;
            this.stream = stream;
        }
    }

    public static AnalysisContext prepareContext(File file, String text, TokenStream mask, com.cocode.vcode.ide.core.lsp.ProjectIndex index) {
        if (mask == null) {
            mask = JsLexer.tokenize(text);
        }
        JsSyntaxTree tree;
        ScopeTree scopeTree;
        if (index != null) {
            String filePath = file != null ? file.getAbsolutePath() : "";
            com.cocode.vcode.ide.core.language.js.ParseResult cached = !filePath.isEmpty() ? index.getParseResult(filePath) : null;
            if (cached != null && cached.tree != null && text.equals(cached.source)) {
                tree = cached.tree;
                if (tree.nodesByOffset == null) {
                    tree.buildNodesByOffset();
                }
                scopeTree = (cached.scopeTree != null) ? cached.scopeTree : ScopeTree.build(tree);
            } else {
                tree = JsParser.parseFull(text, mask);
                if (tree != null) {
                    tree.buildNodesByOffset();
                }
                scopeTree = ScopeTree.build(tree);
                if (!filePath.isEmpty()) {
                    ParseResult freshPr = new ParseResult(file, text, mask, tree, scopeTree, ParseResult.MODE_FULL);
                    index.updateParseResult(filePath, freshPr);
                    String langId = (filePath.endsWith(".ts") || filePath.endsWith(".tsx")) ? "typescript" : "javascript";
                    index.updateDocumentSnapshot(filePath, text, langId);
                    java.util.List<com.cocode.vcode.ide.core.lsp.SymbolEntry> syms =
                            com.cocode.vcode.ide.core.lsp.SymbolExtractor.extractJsSymbolsFromTree(filePath, text, tree);
                    index.updateFileSymbols(filePath, syms);
                }
            }
        } else {
            tree = JsParser.parseFull(text, mask);
            if (tree != null) {
                tree.buildNodesByOffset();
            }
            scopeTree = ScopeTree.build(tree);
        }
        return new AnalysisContext(tree, scopeTree, mask);
    }

    public static List<Problem> analyze(File file, String text, TokenStream mask, com.cocode.vcode.ide.core.lsp.ProjectIndex index) {
        if (text == null || text.trim().isEmpty()) return java.util.Collections.emptyList();

        List<Problem> problems = new ArrayList<>();
        AnalysisContext ctx = prepareContext(file, text, mask, index);
        JsSemanticLinter.analyze(file, text, ctx.stream, ctx.tree, ctx.scopeTree, index, problems);

        return problems;
    }
}
