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
        if (text == null || text.trim().isEmpty()) return java.util.Collections.emptyList();

        List<Problem> problems = new ArrayList<>();
        TokenStream mask = JsLexer.tokenize(text);
        String[] lines = LinterUtils.splitLines(text);

        if (index != null) {
            com.cocode.vcode.ide.core.language.js.ParseResult cached = index.getParseResult(file.getAbsolutePath());
            JsSyntaxTree tree = (cached != null && cached.tree != null) ? cached.tree : JsParser.parseFull(text, mask);
            ScopeTree scopeTree = (cached != null && cached.scopeTree != null) ? cached.scopeTree : ScopeTree.build(tree);
            JsSemanticLinter.analyze(file, text, mask, tree, scopeTree, index, problems);
        }

        return problems;
    }
}
