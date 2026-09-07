package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.js.JsLinter;
import com.cocode.vcode.ide.core.lsp.LspCompletionItem;

import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspRange;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-process Language Server for JavaScript files.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Completions</b>: Delegates to {@link JsAutoCompleteEngine}.</li>
 *   <li><b>Diagnostics</b>: Delegates to {@link JsLinter}.</li>
 *   <li><b>Go to Definition</b>: Resolves {@code import ... from './module'} paths,
 *       then falls back to {@link ProjectIndex} symbol lookup.</li>
 *   <li><b>Find References</b>: Symbol lookup via {@link ProjectIndex}.</li>
 *   <li><b>Signature Help</b>: Returns null (to be enhanced in a future phase).</li>
 * </ul>
 */
public final class JsLspServer implements LspServer {

    private final JsAutoCompleteEngine autoCompleteEngine;
    private volatile boolean ready = false;

    public JsLspServer(Context context) {
        this.autoCompleteEngine = new JsAutoCompleteEngine(context);
    }

    public JsLspServer() {
        this(null);
    }

    // -------------------------------------------------------------------------
    // LspServer contract
    // -------------------------------------------------------------------------

    private static String extractWord(String text, int offset) {
        if (text == null || offset < 0 || offset > text.length()) return "";
        int start = Math.min(offset, text.length() - 1);
        while (start > 0 && isWordChar(text.charAt(start - 1))) start--;
        int end = offset;
        while (end < text.length() && isWordChar(text.charAt(end))) end++;
        return start < end ? text.substring(start, end) : "";
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static int mapKind(CompletionItem.Type type) {
        if (type == null) return LspCompletionItem.KIND_TEXT;
        switch (type) {
            case FUNCTION:
            case BUILTIN:
                return LspCompletionItem.KIND_FUNCTION;
            case KEYWORD:
                return LspCompletionItem.KIND_KEYWORD;
            case SNIPPET:
                return LspCompletionItem.KIND_SNIPPET;
            case VALUE:
                return LspCompletionItem.KIND_VALUE;
            case FILE:
                return LspCompletionItem.KIND_FILE;
            case FOLDER:
                return LspCompletionItem.KIND_FOLDER;
            default:
                return LspCompletionItem.KIND_TEXT;
        }
    }

    // -------------------------------------------------------------------------
    // Completions
    // -------------------------------------------------------------------------



    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------

    /**
     * Returns the text on the current line from the line start up to {@code offset}.
     */
    private static String getLineBeforeCursor(String text, int offset) {
        if (text == null || offset <= 0) return "";
        int lineStart = Math.min(offset, text.length());
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
        return text.substring(lineStart, Math.min(offset, text.length()));
    }

    // -------------------------------------------------------------------------
    // Go to Definition — returns single LspLocation or null
    // -------------------------------------------------------------------------

    /**
     * Extracts the identifier immediately before {@code idx} in {@code line}.
     */
    private static String extractWordBefore(String line, int end) {
        int start = end;
        while (start > 0 && isWordChar(line.charAt(start - 1))) start--;
        return line.substring(start, end);
    }

    // -------------------------------------------------------------------------
    // Find References
    // -------------------------------------------------------------------------


    // -------------------------------------------------------------------------
    // Signature Help
    // -------------------------------------------------------------------------

    @Override
    public void initialize(ProjectIndex index) {
        ready = true;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    @Override
    public void shutdown() {
        ready = false;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String getLanguageId() {
        return "javascript";
    }

    @Override
    public List<LspCompletionItem> completion(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null) return Collections.emptyList();
        int offset = doc.toOffset(pos);
        if (offset < 0) offset = doc.text.length();

        autoCompleteEngine.setCurrentFile(new File(doc.uri));
        List<CompletionItem> suggestions = autoCompleteEngine.getSuggestions(doc.text, offset);
        if (suggestions == null) return Collections.emptyList();

        List<LspCompletionItem> result = new ArrayList<>(suggestions.size());
        for (CompletionItem item : suggestions) {
            String insert = item.getEffectiveInsertText();
            int curOffset = item.getCursorOffset();
            if (curOffset < 0) {
                int pipeIdx = insert.length() + curOffset;
                if (pipeIdx >= 0 && pipeIdx <= insert.length()) {
                    insert = insert.substring(0, pipeIdx) + "|" + insert.substring(pipeIdx);
                }
            }
            result.add(new LspCompletionItem(
                    item.getLabel(),
                    insert,
                    mapKind(item.getType()),
                    item.getDetail(),
                    null,
                    item.getReplaceLength()
            ));
        }
        return result;
    }

    @Override
    public List<Problem> diagnostics(LspDocument doc) {
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            File file = new File(doc.uri);
            List<Problem> problems = new ArrayList<>(com.cocode.vcode.ide.core.diagnostic.BracketLinter.analyze(file, doc.text));
            List<Problem> jsProblems = JsLinter.analyze(file, doc.text, com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance());
            if (jsProblems != null) problems.addAll(jsProblems);
            return com.cocode.vcode.ide.core.diagnostic.DiagnosticEngine.deduplicateAndSort(file, problems);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Member-access detection helpers
    // -------------------------------------------------------------------------

    @Override
    public LspLocation definition(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;

        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return null;

        com.cocode.vcode.ide.core.diagnostic.util.TokenStream tokens = com.cocode.vcode.ide.core.language.js.JsLexer.tokenize(doc.text);
        com.cocode.vcode.ide.core.language.js.JsSyntaxTree tree = com.cocode.vcode.ide.core.language.js.JsParser.parseFull(doc.text, tokens);

        // 1. Resolve import module path if cursor is on an import statement
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_IMPORT) {
                if (offset >= tree.nodeStart[i] && offset <= tree.nodeEnd[i]) {
                    String importPath = tree.nodeName[i];
                    if (importPath != null && !importPath.isEmpty()) {
                        LspLocation resolved = com.cocode.vcode.ide.core.lsp.ModuleResolver.resolveModulePath(doc.uri, importPath);
                        if (resolved != null) return resolved;
                    }
                }
            }
        }

        // 2. Try local file resolution using ScopeTree
        String word = extractWord(doc.text, offset);
        if (word.isEmpty()) return null;

        com.cocode.vcode.ide.core.language.js.ScopeTree scopeTree = com.cocode.vcode.ide.core.language.js.ScopeTree.build(tree);
        
        int scopeId = scopeTree.findScopeAt(offset, tree);
        int[] resolved = scopeTree.lookupSymbol(word, scopeId);
        
        if (resolved != null) {
            int declNodeId = resolved[1];
            int declType = tree.nodeType[declNodeId];
            int declNameOffset = -1;
            
            if (declType == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_PARAM) {
                declNameOffset = tree.nodeStart[declNodeId];
            } else {
                for (int t = 0; t < tokens.types.length; t++) {
                    if (tokens.tokenStart[t] < tree.nodeStart[declNodeId]) continue;
                    if (tokens.tokenStart[t] >= tree.nodeEnd[declNodeId]) break;
                    
                    if (tokens.types[t] == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_IDENTIFIER) {
                        int tStart = tokens.tokenStart[t];
                        int tEnd = tStart + word.length();
                        if (tEnd <= doc.text.length() && doc.text.substring(tStart, tEnd).equals(word)) {
                            declNameOffset = tStart;
                            break;
                        }
                    }
                }
            }
            
            if (declNameOffset != -1) {
                LspPosition start = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(doc.text, declNameOffset);
                LspPosition end = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(doc.text, declNameOffset + word.length());
                return new LspLocation(doc.uri, new LspRange(start, end));
            }
        }

        // Fall back to project-wide symbol lookup
        List<LspLocation> defs = ProjectIndex.getInstance().findDefinitions(word);
        return (defs != null && !defs.isEmpty()) ? defs.get(0) : null;
    }

    @Override
    public List<LspLocation> references(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return Collections.emptyList();
        int offset = doc.toOffset(pos);
        String word = extractWord(doc.text, offset >= 0 ? offset : 0);
        if (word.isEmpty()) return Collections.emptyList();

        return findUsagesInProject(word);
    }

    @Override
    public List<LspLocation> rename(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return Collections.emptyList();
        int offset = doc.toOffset(pos);
        String word = extractWord(doc.text, offset >= 0 ? offset : 0);
        if (word.isEmpty()) return Collections.emptyList();

        com.cocode.vcode.ide.core.diagnostic.util.TokenStream tokens = com.cocode.vcode.ide.core.language.js.JsLexer.tokenize(doc.text);
        com.cocode.vcode.ide.core.language.js.JsSyntaxTree tree = com.cocode.vcode.ide.core.language.js.JsParser.parseFull(doc.text, tokens);
        com.cocode.vcode.ide.core.language.js.ScopeTree scopeTree = com.cocode.vcode.ide.core.language.js.ScopeTree.build(tree);

        int scopeId = scopeTree.findScopeAt(offset, tree);
        if (scopeId == -1) return Collections.emptyList();

        int[] entry = scopeTree.lookupSymbol(word, scopeId);
        if (entry == null) return Collections.emptyList();
        int declarationScopeId = entry[0];

        int[] offsets = scopeTree.findAllReferences(word, declarationScopeId, tree);
        List<LspLocation> result = new ArrayList<>();

        // Always include the declaration site itself (N_VAR_DECL etc. are not N_IDENTIFIER,
        // so findAllReferences never picks them up).
        int declNodeId = entry[1];
        int nameNodeId = -1;
        
        // The declaration node itself (e.g., N_VAR_DECL) points to the keyword (const, let).
        // We scan the token stream forward to find the exact identifier.
        int declType = tree.nodeType[declNodeId];
        
        if (declType == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_PARAM) {
            nameNodeId = tree.nodeStart[declNodeId]; // For N_PARAM, nodeStart is the identifier
        } else {
            for (int t = 0; t < tokens.types.length; t++) {
                if (tokens.tokenStart[t] < tree.nodeStart[declNodeId]) continue;
                if (tokens.tokenStart[t] >= tree.nodeEnd[declNodeId]) break;
                
                if (tokens.types[t] == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_IDENTIFIER) {
                    int tStart = tokens.tokenStart[t];
                    int tEnd = tStart + word.length();
                    if (tEnd <= doc.text.length() && doc.text.substring(tStart, tEnd).equals(word)) {
                        nameNodeId = tStart;
                        break;
                    }
                }
            }
        }
        
        if (nameNodeId != -1) {
            int declCharOffset = nameNodeId;
            LspPosition declP = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(doc.text, declCharOffset);
            LspPosition declEndP = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(doc.text, declCharOffset + word.length());
            result.add(new LspLocation(doc.uri, new LspRange(declP, declEndP)));
        }

        for (int nodeId : offsets) {
            int charOffset = tree.nodeStart[nodeId];
            LspPosition p = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(doc.text, charOffset);
            LspPosition endP = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(doc.text, charOffset + word.length());
            result.add(new LspLocation(doc.uri, new LspRange(p, endP)));
        }
        
        // Deduplicate overlapping offsets to avoid double-replacement (Task 4.3.5 fix)
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<LspLocation> uniqueResult = new ArrayList<>();
        for (LspLocation loc : result) {
            String key = loc.range.start.line + ":" + loc.range.start.character;
            if (seen.add(key)) {
                uniqueResult.add(loc);
            }
        }
        return uniqueResult;
    }

    private List<LspLocation> findUsagesInProject(String word) {
        List<LspLocation> result = new ArrayList<>();
        if (word == null || word.trim().isEmpty()) return result;
        String trimmed = word.trim();
        ProjectIndex projectIndex = ProjectIndex.getInstance();
        List<LspLocation> defs = projectIndex.findDefinitions(trimmed);
        
        final int MAX_REFS = 100;
        for (String uri : projectIndex.getAllUris()) {
            if (result.size() >= MAX_REFS) break;
            LspDocument d = projectIndex.getDocument(uri);
            if (d == null || d.text == null) continue;

            if (uri.endsWith(".js") || uri.endsWith(".ts") || uri.endsWith(".jsx") || uri.endsWith(".tsx")) {
                com.cocode.vcode.ide.core.diagnostic.util.TokenStream ts = com.cocode.vcode.ide.core.language.js.JsLexer.tokenize(d.text);
                int tLen = trimmed.length();
                for (int i = 0; i < ts.length && result.size() < MAX_REFS; ) {
                    if (ts.types[i] == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_IDENTIFIER) {
                        int start = ts.tokenStart[i];
                        int end = start;
                        while (end < ts.length && ts.types[end] == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_IDENTIFIER && ts.tokenStart[end] == start) {
                            end++;
                        }
                        int idLen = end - start;
                        if (idLen == tLen && d.text.regionMatches(start, trimmed, 0, tLen)) {
                            LspPosition posStart = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(d.text, start);
                            LspPosition posEnd = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(d.text, end);
                            LspRange range = new LspRange(posStart, posEnd);
                            LspLocation loc = new LspLocation(uri, range);

                            boolean isDef = false;
                            for (LspLocation def : defs) {
                                if (def.uri.equals(uri) && def.range.start.line == range.start.line && def.range.start.character == range.start.character) {
                                    isDef = true;
                                    break;
                                }
                            }
                            if (!isDef) {
                                result.add(loc);
                            }
                        }
                        i = end;
                    } else {
                        i++;
                    }
                }
            }
        }
        return result;
    }

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos) {
        return JsSignatureParser.parse(doc, pos);
    }
}
