package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.language.ts.TsAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.ts.TsLinter;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-process Language Server for TypeScript files.
 * <p>
 * Extends the JavaScript server's capability set with TS-specific linting
 * (via {@link TsLinter}) and TypeScript-aware completions (via {@link TsAutoCompleteEngine}).
 * Module resolution also tries {@code .ts} and {@code .tsx} extensions.
 */
public final class TsLspServer implements LspServer {

    private static final Pattern IMPORT_FROM =
            Pattern.compile("import\\s+.*?from\\s+['\"]([^'\"]+)['\"]");
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

    @Override
    public void initialize(ProjectIndex index) {
        ready = true;
    }

    // -------------------------------------------------------------------------
    // Go to Definition
    // -------------------------------------------------------------------------

    @Override
    public void shutdown() {
        ready = false;
    }

    // -------------------------------------------------------------------------
    // Find References
    // -------------------------------------------------------------------------

    @Override
    public boolean isReady() {
        return ready;
    }

    // -------------------------------------------------------------------------
    // Signature Help
    // -------------------------------------------------------------------------

    @Override
    public String getLanguageId() {
        return "typescript";
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

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
                if (pipeIdx >= 0) {
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
        File file = new File(doc.uri);
        List<Problem> problems = new ArrayList<>(com.cocode.vcode.ide.core.diagnostic.BracketLinter.analyze(file, doc.text));
        List<Problem> tsProblems = TsLinter.analyze(file, doc.text, com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance());
        if (tsProblems != null) problems.addAll(tsProblems);
        return com.cocode.vcode.ide.core.diagnostic.DiagnosticEngine.deduplicateAndSort(file, problems);
    }

    @Override
    public LspLocation definition(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;

        String lineText = doc.getLine(pos.line);
        Matcher m = IMPORT_FROM.matcher(lineText);
        while (m.find()) {
            if (pos.character >= m.start() && pos.character <= m.end()) {
                String importPath = m.group(1);
                if (importPath != null && !importPath.isEmpty()) {
                    LspLocation resolved = com.cocode.vcode.ide.core.lsp.ModuleResolver.resolveModulePath(doc.uri, importPath);
                    if (resolved != null) return resolved;
                }
            }
        }

        int offset = doc.toOffset(pos);
        String word = extractWord(doc.text, Math.max(offset, 0));
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
        ProjectIndex projectIndex = ProjectIndex.getInstance();
        List<LspLocation> defs = projectIndex.findDefinitions(word);
        
        for (String uri : projectIndex.getAllUris()) {
            LspDocument d = projectIndex.getDocument(uri);
            if (d == null || d.text == null) continue;

            if (uri.endsWith(".js") || uri.endsWith(".ts") || uri.endsWith(".jsx") || uri.endsWith(".tsx")) {
                Pattern p = Pattern.compile("\\b" + Pattern.quote(word) + "\\b");
                Matcher m = p.matcher(d.text);
                while (m.find()) {
                    LspPosition start = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(d.text, m.start());
                    LspPosition end = com.cocode.vcode.ide.core.lsp.SymbolExtractor.offsetToPosition(d.text, m.end());
                    LspRange range = new LspRange(start, end);
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
            }
        }
        return result;
    }

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos) {
        return JsSignatureParser.parse(doc, pos);
    }
}
