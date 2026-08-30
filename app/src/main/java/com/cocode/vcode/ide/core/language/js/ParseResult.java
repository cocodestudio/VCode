package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;

import com.cocode.vcode.ide.core.language.html.HtmlSyntaxTree;
import com.cocode.vcode.ide.core.language.html.HtmlTokenStream;
import com.cocode.vcode.ide.core.language.css.CssSyntaxTree;
import com.cocode.vcode.ide.core.language.css.CssTokenStream;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * An immutable snapshot of a file's parsed syntax tree.
 * Consumed by the UI thread via LiveData for autocomplete, diagnostics, etc.
 */
public class ParseResult {
    public static final int MODE_FULL = 0;
    public static final int MODE_LAZY_SCOPE = 1;
    public static final int MODE_TOP_LEVEL = 2;
    public static final int MODE_TOKENIZE_ONLY = 3;

    public final File file;
    public final String source;
    public final TokenStream tokens;
    public final JsSyntaxTree tree;
    public final ScopeTree scopeTree;
    public final int mode;

    public final HtmlTokenStream htmlTokens;
    public final HtmlSyntaxTree htmlTree;
    public final CssTokenStream cssTokens;
    public final CssSyntaxTree cssTree;

    public final com.cocode.vcode.ide.core.language.json.JsonTokenStream jsonTokens;
    public final com.cocode.vcode.ide.core.language.json.JsonSyntaxTree jsonTree;

    public final List<EmbeddedResult> embeddedResults;

    public static class EmbeddedResult {
        public final int startOffset;
        public final int endOffset;
        public final ParseResult result;
        
        public EmbeddedResult(int startOffset, int endOffset, ParseResult result) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.result = result;
        }
    }

    public ParseResult(File file, String source, TokenStream tokens, JsSyntaxTree tree, ScopeTree scopeTree, int mode) {
        this.file = file;
        this.source = source;
        this.tokens = tokens;
        this.tree = tree;
        this.scopeTree = scopeTree;
        this.mode = mode;
        this.htmlTokens = null;
        this.htmlTree = null;
        this.cssTokens = null;
        this.cssTree = null;
        this.jsonTokens = null;
        this.jsonTree = null;
        this.embeddedResults = new ArrayList<>();
    }
    
    // Constructor for HTML parsing
    public ParseResult(File file, String source, HtmlTokenStream htmlTokens, HtmlSyntaxTree htmlTree) {
        this(file, source, htmlTokens, htmlTree, new ArrayList<>());
    }

    public ParseResult(File file, String source, HtmlTokenStream htmlTokens, HtmlSyntaxTree htmlTree, List<EmbeddedResult> embeddedResults) {
        this.file = file;
        this.source = source;
        this.htmlTokens = htmlTokens;
        this.htmlTree = htmlTree;
        this.tokens = null;
        this.tree = null;
        this.scopeTree = null;
        this.mode = MODE_FULL;
        this.cssTokens = null;
        this.cssTree = null;
        this.jsonTokens = null;
        this.jsonTree = null;
        this.embeddedResults = embeddedResults;
    }

    // Constructor for CSS parsing
    public ParseResult(File file, String source, CssTokenStream cssTokens, CssSyntaxTree cssTree) {
        this.file = file;
        this.source = source;
        this.cssTokens = cssTokens;
        this.cssTree = cssTree;
        this.tokens = null;
        this.tree = null;
        this.scopeTree = null;
        this.mode = MODE_FULL;
        this.htmlTokens = null;
        this.htmlTree = null;
        this.jsonTokens = null;
        this.jsonTree = null;
        this.embeddedResults = new ArrayList<>();
    }

    // Constructor for JSON parsing
    public ParseResult(File file, String source, com.cocode.vcode.ide.core.language.json.JsonTokenStream jsonTokens, com.cocode.vcode.ide.core.language.json.JsonSyntaxTree jsonTree) {
        this.file = file;
        this.source = source;
        this.jsonTokens = jsonTokens;
        this.jsonTree = jsonTree;
        this.tokens = null;
        this.tree = null;
        this.scopeTree = null;
        this.mode = MODE_FULL;
        this.htmlTokens = null;
        this.htmlTree = null;
        this.cssTokens = null;
        this.cssTree = null;
        this.embeddedResults = new ArrayList<>();
    }
    
    // Legacy constructor for tests or other languages
    public ParseResult(File file, String source, TokenStream tokens, JsSyntaxTree tree) {
        this(file, source, tokens, tree, null, MODE_FULL);
    }
}
