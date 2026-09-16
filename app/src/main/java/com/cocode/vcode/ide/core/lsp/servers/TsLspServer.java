package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.language.ts.TsAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.ts.TsLinter;
import com.cocode.vcode.ide.core.lsp.LspCompletionConverter;
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
 * In-process Language Server for TypeScript files.
 * <p>
 * Extends the JavaScript server's capability set with TS-specific linting
 * (via {@link TsLinter}) and TypeScript-aware completions (via {@link TsAutoCompleteEngine}).
 * Module resolution also tries {@code .ts} and {@code .tsx} extensions.
 */
public final class TsLspServer implements LspServer {

    private final TsAutoCompleteEngine autoCompleteEngine;
    private volatile boolean ready = false;

    public TsLspServer(Context context) {
        this.autoCompleteEngine = new TsAutoCompleteEngine(context);
    }

    public TsLspServer() {
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

    // -------------------------------------------------------------------------
    // Completions
    // -------------------------------------------------------------------------


    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------

    public static List<LspCompletionItem> convertCompletions(List<CompletionItem> suggestions) {
        return LspCompletionConverter.convert(suggestions);
    }

    // -------------------------------------------------------------------------
    // Go to Definition
    // -------------------------------------------------------------------------

    @Override
    public void initialize(ProjectIndex index) {
        ready = true;
    }

    // -------------------------------------------------------------------------
    // Find References
    // -------------------------------------------------------------------------

    @Override
    public void shutdown() {
        ready = false;
    }

    // -------------------------------------------------------------------------
    // Signature Help
    // -------------------------------------------------------------------------

    @Override
    public boolean isReady() {
        return ready;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    @Override
    public String getLanguageId() {
        return "typescript";
    }

    @Override
    public List<LspCompletionItem> completion(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null) return Collections.emptyList();
        int offset = doc.toOffset(pos);
        if (offset < 0) offset = doc.text.length();

        autoCompleteEngine.setCurrentFile(new File(doc.uri));
        List<CompletionItem> suggestions = autoCompleteEngine.getSuggestions(doc.text, offset);
        if (suggestions == null) return Collections.emptyList();

        return convertCompletions(suggestions);
    }

    @Override
    public List<Problem> diagnostics(LspDocument doc) {
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            File file = new File(doc.uri);
            List<Problem> problems = new ArrayList<>(com.cocode.vcode.ide.core.editor.indent.BracketMatcher.findMismatches(file, doc.text));
            List<Problem> tsProblems = TsLinter.analyze(file, doc.text, com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance());
            if (tsProblems != null) problems.addAll(tsProblems);
            return com.cocode.vcode.ide.core.diagnostic.DiagnosticEngine.deduplicateAndSort(file, problems);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    @Override
    public LspLocation definition(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;

        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return null;

        com.cocode.vcode.ide.core.diagnostic.util.TokenStream tokens = com.cocode.vcode.ide.core.language.js.JsLexer.tokenize(doc.text);
        com.cocode.vcode.ide.core.language.js.JsSyntaxTree tree = com.cocode.vcode.ide.core.language.js.JsParser.parseFull(doc.text, tokens);

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

        String word = extractWord(doc.text, offset);
        if (word.isEmpty()) return null;

        List<LspLocation> defs = ProjectIndex.getInstance().findDefinitions(word);
        return !defs.isEmpty() ? defs.get(0) : null;
    }

    @Override
    public List<LspLocation> references(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return Collections.emptyList();
        int offset = doc.toOffset(pos);
        String word = extractWord(doc.text, Math.max(offset, 0));
        if (word.isEmpty()) return Collections.emptyList();

        return findUsagesInProject(word);
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

    @Override
    public java.util.List<LspLocation> rename(LspDocument doc, LspPosition pos) {
        return java.util.Collections.emptyList();
    }
}
