package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseJsAstTest {

    protected String source;
    protected TokenStream tokens;
    protected JsSyntaxTree tree;
    protected ScopeTree scopeTree;
    protected List<Problem> problems;
    protected ProjectIndex mockIndex;
    protected File mockFile;

    @org.junit.Before
    public void initBase() {
        this.mockIndex = ProjectIndex.getInstance();
        this.mockFile = new File("test.js");
        this.problems = new ArrayList<>();
    }

    protected void setupEngine(String code) {
        this.source = code;
        this.tokens = JsLexer.tokenize(code);
        this.tree = JsParser.parseFull(code, this.tokens);
        if (this.tree != null) {
            this.tree.buildNodesByOffset();
        }
        this.scopeTree = ScopeTree.build(this.tree);
        
        this.mockIndex = ProjectIndex.getInstance();
        this.mockFile = new File("test.js");
        this.problems = new ArrayList<>();
        
        // Directly invoke the AST-based semantic linter (bypassing any legacy regex rules)
        JsSemanticLinter.analyze(mockFile, code, tokens, tree, scopeTree, mockIndex, problems);
    }

    protected List<com.cocode.vcode.ide.core.model.CompletionItem> getCompletions(String code, int offset) {
        JsAutoCompleteEngine engine = new JsAutoCompleteEngine(null);
        return engine.getSuggestions(code, offset);
    }

    protected com.cocode.vcode.ide.core.language.js.ParseResult parseFile(String uri, String code) {
        TokenStream toks = JsLexer.tokenize(code);
        JsSyntaxTree t = JsParser.parseFull(code, toks);
        if (t != null) t.buildNodesByOffset();
        ScopeTree s = ScopeTree.build(t);
        return new com.cocode.vcode.ide.core.language.js.ParseResult(new File(uri), code, toks, t, s, com.cocode.vcode.ide.core.language.js.ParseResult.MODE_FULL);
    }

    protected JsExportTable getExportTable(String uri, String code) {
        if (uri == null) return JsExportTable.build(parseFile("test.js", code).tree, null);
        return JsExportTable.build(parseFile(uri, code).tree, uri);
    }
}
