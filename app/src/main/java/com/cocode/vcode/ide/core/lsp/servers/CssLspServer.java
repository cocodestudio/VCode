package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.language.css.CssAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.css.CssContextScanner;
import com.cocode.vcode.ide.core.language.css.CssFormatter;
import com.cocode.vcode.ide.core.language.css.CssLexer;
import com.cocode.vcode.ide.core.language.css.CssLinter;
import com.cocode.vcode.ide.core.language.css.CssParser;
import com.cocode.vcode.ide.core.language.css.CssSyntaxTree;
import com.cocode.vcode.ide.core.language.css.CssTokenStream;
import com.cocode.vcode.ide.core.lsp.CancellationToken;
import com.cocode.vcode.ide.core.lsp.HoverInfo;
import com.cocode.vcode.ide.core.lsp.LspCompletionConverter;
import com.cocode.vcode.ide.core.lsp.LspCompletionItem;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspDocumentManager;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.lsp.ServerCapabilities;
import com.cocode.vcode.ide.core.lsp.SymbolEntry;
import com.cocode.vcode.ide.core.lsp.SymbolExtractor;
import com.cocode.vcode.ide.core.lsp.index.WorkspaceIndexService;
import com.cocode.vcode.ide.core.lsp.model.CodeAction;
import com.cocode.vcode.ide.core.lsp.model.CodeActionKind;
import com.cocode.vcode.ide.core.lsp.model.CodeActionParams;
import com.cocode.vcode.ide.core.lsp.model.Color;
import com.cocode.vcode.ide.core.lsp.model.ColorInformation;
import com.cocode.vcode.ide.core.lsp.model.ColorPresentation;
import com.cocode.vcode.ide.core.lsp.model.ColorPresentationParams;
import com.cocode.vcode.ide.core.lsp.model.CompletionItem;
import com.cocode.vcode.ide.core.lsp.model.CompletionItemKind;
import com.cocode.vcode.ide.core.lsp.model.CompletionParams;
import com.cocode.vcode.ide.core.lsp.model.DefinitionParams;
import com.cocode.vcode.ide.core.lsp.model.Diagnostic;
import com.cocode.vcode.ide.core.lsp.model.DiagnosticSeverity;
import com.cocode.vcode.ide.core.lsp.model.DocumentColorParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentDiagnosticParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentFormattingParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentSymbol;
import com.cocode.vcode.ide.core.lsp.model.DocumentSymbolParams;
import com.cocode.vcode.ide.core.lsp.model.Hover;
import com.cocode.vcode.ide.core.lsp.model.HoverParams;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.model.LspPosition;
import com.cocode.vcode.ide.core.lsp.model.LspRange;
import com.cocode.vcode.ide.core.lsp.model.MarkupContent;
import com.cocode.vcode.ide.core.lsp.model.MarkupKind;
import com.cocode.vcode.ide.core.lsp.model.ReferenceParams;
import com.cocode.vcode.ide.core.lsp.model.SymbolKind;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentContentChangeEvent;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentIdentifier;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentItem;
import com.cocode.vcode.ide.core.lsp.model.TextEdit;
import com.cocode.vcode.ide.core.lsp.model.VersionedTextDocumentIdentifier;
import com.cocode.vcode.ide.core.lsp.model.WorkspaceEdit;
import com.cocode.vcode.ide.core.diagnostic.ProblemCode;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-process Language Server for CSS / SCSS files implementing full LSP 3.17 capabilities.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Document Synchronization</b>: didOpen, didChange, didSave, didClose with AST index updates.</li>
 *   <li><b>Completions</b>: Standard properties/values + workspace CSS variables (var(--*)) across :root.</li>
 *   <li><b>Diagnostics</b>: Linting with strict hexadecimal color validation and problem codes.</li>
 *   <li><b>Code Actions</b>: Margin auto shorthand, media query px-to-rem/em, empty rule deletion.</li>
 *   <li><b>Go to Definition</b>: Jump to @import targets, HTML class/id definitions, and :root CSS variable declarations.</li>
 *   <li><b>Find References</b>: Find all usages of CSS classes, IDs, and custom properties across stylesheets and HTML.</li>
 *   <li><b>Document Symbols</b>: Hierarchical outline for at-rules, rule sets, and declarations.</li>
 *   <li><b>Document Color & Presentation</b>: In-editor color swatch extraction and hex/rgb/hsl conversion.</li>
 *   <li><b>Formatting</b>: Full-document atomic TextEdit formatting.</li>
 * </ul>
 */
public final class CssLspServer implements LspServer {

    private static final ServerCapabilities CAPABILITIES = new ServerCapabilities.Builder()
            .setHoverProvider(true)
            .setCompletionProvider(true)
            .setDefinitionProvider(true)
            .setReferencesProvider(true)
            .setDocumentHighlightProvider(true)
            .setDocumentSymbolProvider(true)
            .setCodeActionProvider(true)
            .setFormattingProvider(true)
            .setRangeFormattingProvider(false)
            .setFoldingRangeProvider(true)
            .setSignatureHelpProvider(false)
            .setSemanticTokensProvider(false)
            .setColorProvider(true)
            .build();

    private static final Pattern HEX_COLOR_PAT = Pattern.compile("#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})\\b");
    private static final Pattern RGB_COLOR_PAT = Pattern.compile("\\brgba?\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*([\\d\\.]+))?\\s*\\)");
    private static final Pattern HSL_COLOR_PAT = Pattern.compile("\\bhsla?\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)%\\s*,\\s*(\\d+)%(?:\\s*,\\s*([\\d\\.]+))?\\s*\\)");

    private final CssAutoCompleteEngine completeEngine;
    private final ConcurrentHashMap<String, LspDocument> openDocs = new ConcurrentHashMap<>();
    private volatile boolean ready = false;
    private ProjectIndex projectIndex;

    public CssLspServer(Context context) {
        this.completeEngine = new CssAutoCompleteEngine(context);
    }

    public CssLspServer() {
        this(null);
    }

    // -------------------------------------------------------------------------
    // Lifecycle & Capabilities
    // -------------------------------------------------------------------------

    @Override
    public void initialize(ProjectIndex index) {
        this.projectIndex = index;
        ready = true;
    }

    @Override
    public void shutdown() {
        ready = false;
        openDocs.clear();
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String getLanguageId() {
        return "css";
    }

    @Override
    public String getTriggerCharacters() {
        return ".#:@-(!&/\"'";
    }

    @Override
    public ServerCapabilities getCapabilities() {
        return CAPABILITIES;
    }

    // -------------------------------------------------------------------------
    // Document Synchronization (LSP 3.17)
    // -------------------------------------------------------------------------

    @Override
    public void didOpen(TextDocumentItem document) {
        if (document == null) return;
        LspDocument doc = new LspDocument(document.uri, document.text, document.languageId, document.version);
        openDocs.put(document.uri, doc);
        WorkspaceIndexService wis = WorkspaceIndexService.getInstance();
        if (wis != null) {
            wis.updateDocument(doc);
        }
    }

    @Override
    public void didChange(VersionedTextDocumentIdentifier document, List<TextDocumentContentChangeEvent> changes) {
        if (document == null) return;
        LspDocument existing = LspDocumentManager.getInstance().getDocument(document.uri);
        if (existing != null) {
            openDocs.put(document.uri, existing);
        }
    }

    @Override
    public void didSave(TextDocumentIdentifier document) {
        if (document == null) return;
        LspDocument doc = getDocument(document.uri);
        if (doc != null) {
            WorkspaceIndexService wis = WorkspaceIndexService.getInstance();
            if (wis != null) {
                wis.updateDocument(doc);
            }
        }
    }

    @Override
    public void didClose(TextDocumentIdentifier document) {
        if (document == null) return;
        openDocs.remove(document.uri);
    }

    // -------------------------------------------------------------------------
    // Completions (LSP 3.17 & Legacy)
    // -------------------------------------------------------------------------

    @Override
    public List<CompletionItem> completion(CompletionParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) {
            return Collections.emptyList();
        }
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();

        int flatOffset = doc.toOffset(params.position);
        if (flatOffset < 0) flatOffset = doc.text.length();

        List<CompletionItem> items = new ArrayList<>();
        Set<String> seenLabels = new HashSet<>();

        // 1. CSS Variable Completion (var(--custom-prop) across :root)
        boolean insideVarFirstArg = CssContextScanner.isInsideVarFirstArg(doc.text, flatOffset);
        boolean isVarPrefix = isTypingCssVariablePrefix(doc.text, flatOffset);

        if (insideVarFirstArg || isVarPrefix) {
            String word = getWordBeforeCursor(doc.text, flatOffset);
            int replaceLen = word.length();
            List<SymbolEntry> allVars = collectAllWorkspaceCssVariables(doc);
            List<SymbolEntry> filteredVars = filterAndRankVariables(allVars, word);

            for (SymbolEntry sym : filteredVars) {
                if (token != null && token.isCancelled()) return Collections.emptyList();
                if (seenLabels.add(sym.name)) {
                    String insert = insideVarFirstArg || isVarPrefix ? sym.name : "var(" + sym.name + ")";
                    String detail = sym.detail != null && !sym.detail.isEmpty() ? sym.detail : "CSS Variable";
                    MarkupContent docContent = new MarkupContent(MarkupKind.MARKDOWN,
                            "Custom property: `" + sym.name + ": " + (sym.detail != null ? sym.detail : "") + "`");
                    CompletionItem.Builder vb = CompletionItem.builder(sym.name)
                            .setInsertText(insert)
                            .setKind(CompletionItemKind.VARIABLE)
                            .setDetail(detail)
                            .setDocumentation(docContent);
                    if (replaceLen > 0) {
                        int startOff = Math.max(0, flatOffset - replaceLen);
                        vb.setTextEdit(new TextEdit(new LspRange(doc.toModelPosition(startOff), doc.toModelPosition(flatOffset)), insert));
                    }
                    items.add(vb.build());
                }
            }

            // Inside the first argument of var(), only custom properties are valid.
            // Return immediately so non-matching queries (e.g. var(--hshsd)) yield an empty list
            // and dismiss the autocomplete popup.
            if (insideVarFirstArg) {
                return items;
            }
        }

        // 2. Standard CSS suggestions from engine
        completeEngine.setCurrentFile(new File(doc.uri));
        List<com.cocode.vcode.ide.core.model.CompletionItem> legacy = completeEngine.getSuggestions(doc.text, flatOffset);
        if (legacy != null) {
            for (com.cocode.vcode.ide.core.model.CompletionItem leg : legacy) {
                if (token != null && token.isCancelled()) return Collections.emptyList();
                if (leg != null && leg.getLabel() != null && seenLabels.add(leg.getLabel())) {
                    CompletionItem.Builder b = CompletionItem.builder(leg.getLabel())
                            .setInsertText(leg.getEffectiveInsertText())
                            .setKind(mapLegacyCompletionKind(leg.getType()))
                            .setDetail(leg.getDetail());
                    if (leg.getReplaceLength() >= 0) {
                        int startOff = Math.max(0, flatOffset - leg.getReplaceLength());
                        b.setTextEdit(new TextEdit(
                                new LspRange(doc.toModelPosition(startOff), doc.toModelPosition(flatOffset)),
                                leg.getEffectiveInsertText()
                        ));
                    }
                    items.add(b.build());
                }
            }
        }

        return items;
    }

    @Override
    public List<LspCompletionItem> completion(LspDocument doc, com.cocode.vcode.ide.core.lsp.LspPosition pos) {
        if (doc == null || doc.text == null) return Collections.emptyList();

        int flatOffset = doc.toOffset(pos);
        if (flatOffset < 0) flatOffset = doc.text.length();

        boolean insideVarFirstArg = CssContextScanner.isInsideVarFirstArg(doc.text, flatOffset);
        boolean isVarPrefix = isTypingCssVariablePrefix(doc.text, flatOffset);

        if (insideVarFirstArg || isVarPrefix) {
            String word = getWordBeforeCursor(doc.text, flatOffset);
            int replaceLen = word.length();
            List<SymbolEntry> allVars = collectAllWorkspaceCssVariables(doc);
            List<SymbolEntry> filteredVars = filterAndRankVariables(allVars, word);
            List<LspCompletionItem> varItems = new ArrayList<>(filteredVars.size());
            Set<String> seen = new HashSet<>();
            for (SymbolEntry sym : filteredVars) {
                if (seen.add(sym.name)) {
                    varItems.add(new LspCompletionItem(
                            sym.name,
                            sym.name,
                            LspCompletionItem.KIND_VARIABLE,
                            sym.detail != null ? sym.detail : "CSS Variable",
                            null,
                            replaceLen
                    ));
                }
            }

            // Inside the first argument of var(), only custom properties are valid.
            // Return immediately so non-matching queries (e.g. var(--hshsd)) yield an empty list
            // and dismiss the autocomplete popup.
            if (insideVarFirstArg) {
                return varItems;
            }

            completeEngine.setCurrentFile(new File(doc.uri));
            List<com.cocode.vcode.ide.core.model.CompletionItem> legacy = completeEngine.getSuggestions(doc.text, flatOffset);
            List<LspCompletionItem> converted = convertCompletions(legacy);
            List<LspCompletionItem> augmented = new ArrayList<>(converted);
            Set<String> existing = new HashSet<>();
            for (LspCompletionItem item : converted) {
                existing.add(item.label);
            }
            for (LspCompletionItem vItem : varItems) {
                if (existing.add(vItem.label)) {
                    augmented.add(vItem);
                }
            }
            return augmented;
        }

        completeEngine.setCurrentFile(new File(doc.uri));
        List<com.cocode.vcode.ide.core.model.CompletionItem> legacy = completeEngine.getSuggestions(doc.text, flatOffset);
        return convertCompletions(legacy);
    }

    private static String getWordBeforeCursor(String text, int offset) {
        if (text == null || offset <= 0) return "";
        int start = offset;
        while (start > 0 && isCssVarChar(text.charAt(start - 1))) {
            start--;
        }
        return text.substring(start, offset);
    }

    public static List<LspCompletionItem> convertCompletions(List<com.cocode.vcode.ide.core.model.CompletionItem> legacy) {
        return LspCompletionConverter.convert(legacy);
    }

    // -------------------------------------------------------------------------
    // Diagnostics (LSP 3.17 & Legacy)
    // -------------------------------------------------------------------------

    @Override
    public List<Diagnostic> diagnostics(DocumentDiagnosticParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Problem> problems = diagnostics(doc);
        if (problems == null || problems.isEmpty()) return Collections.emptyList();

        List<Diagnostic> diags = new ArrayList<>(problems.size());
        for (Problem p : problems) {
            if (token != null && token.isCancelled()) return Collections.emptyList();
            if (p == null) continue;
            int line = Math.max(0, p.getLine() - 1);
            int col = Math.max(0, p.getColumn() - 1);
            int len = Math.max(1, p.getLength());
            LspRange range = new LspRange(new LspPosition(line, col), new LspPosition(line, col + len));

            DiagnosticSeverity sev = DiagnosticSeverity.ERROR;
            if (p.getSeverity() == Problem.Severity.WARNING) {
                sev = DiagnosticSeverity.WARNING;
            } else if (p.getSeverity() == Problem.Severity.INFO) {
                sev = DiagnosticSeverity.INFORMATION;
            }
            String code = p.getCode();
            diags.add(new Diagnostic(range, sev, code, "css", p.getMessage()));
        }
        return diags;
    }

    @Override
    public List<Problem> diagnostics(LspDocument doc) {
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            File file = new File(doc.uri);
            List<Problem> problems = CssLinter.analyze(file, doc.text);

            List<Problem> filtered = new ArrayList<>();
            if (problems != null) {
                for (Problem p : problems) {
                    if (p != null && p.getLine() > 0) {
                        filtered.add(p);
                    }
                }
            }
            return com.cocode.vcode.ide.core.diagnostic.DiagnosticEngine.deduplicateAndSort(file, filtered);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Code Actions (LSP 3.17)
    // -------------------------------------------------------------------------

    @Override
    public List<CodeAction> codeAction(CodeActionParams params, CancellationToken token) {
        return codeAction(params, token, null);
    }

    public List<CodeAction> codeAction(CodeActionParams params, CancellationToken token, LspDocument overrideDoc) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = overrideDoc != null ? overrideDoc : getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null || doc.text.isEmpty()) return Collections.emptyList();

        List<CodeAction> actions = new ArrayList<>();

        // Check if context has specific diagnostics
        boolean hasDiagnostics = params.context != null && params.context.diagnostics != null && !params.context.diagnostics.isEmpty();
        boolean hasEmptyRuleDiag = false;
        if (hasDiagnostics) {
            for (Diagnostic diag : params.context.diagnostics) {
                if ("CSS_EMPTY_RULE".equals(diag.code) || (diag.message != null && diag.message.contains("Empty rule block"))) {
                    hasEmptyRuleDiag = true;
                    break;
                }
            }
        }

        // 1. Margin auto shorthand
        addMarginAutoShorthandActions(doc, params, actions);

        // 2. Media query px to em/rem conversion
        addMediaQueryUnitConversionActions(doc, params, actions);

        // 3. Empty rule block deletion - only query if cursor query without diagnostics or explicit empty rule diagnostic
        if (!hasDiagnostics || hasEmptyRuleDiag) {
            addEmptyRuleDeleteActions(doc, params, actions);
        }

        // 4. Diagnostics-driven quick fixes
        if (hasDiagnostics) {
            for (Diagnostic diag : params.context.diagnostics) {
                if (token != null && token.isCancelled()) break;
                addNativeCssFixes(doc, diag, actions);
            }
        }

        return actions;
    }

    private void addMarginAutoShorthandActions(LspDocument doc, CodeActionParams params, List<CodeAction> actions) {
        String text = doc.text;
        int cursorOffset = doc.toOffset(params.range.start);
        if (cursorOffset < 0 || cursorOffset > text.length()) return;

        int blockStart = text.lastIndexOf('{', cursorOffset);
        if (blockStart == -1) blockStart = text.indexOf('{', cursorOffset);
        if (blockStart == -1) return;
        int blockEnd = text.indexOf('}', blockStart);
        if (blockEnd == -1) return;

        String blockContent = text.substring(blockStart, blockEnd);
        Pattern mlPat = Pattern.compile("(?i)(margin-left\\s*:\\s*auto\\s*;?)");
        Pattern mrPat = Pattern.compile("(?i)(margin-right\\s*:\\s*auto\\s*;?)");
        Matcher mlMatcher = mlPat.matcher(blockContent);
        Matcher mrMatcher = mrPat.matcher(blockContent);

        if (mlMatcher.find() && mrMatcher.find()) {
            int mlStart = blockStart + mlMatcher.start();
            int mlEnd = blockStart + mlMatcher.end();
            int mrStart = blockStart + mrMatcher.start();
            int mrEnd = blockStart + mrMatcher.end();

            int firstStart = Math.min(mlStart, mrStart);
            int firstEnd = (firstStart == mlStart) ? mlEnd : mrEnd;
            int secondStart = Math.max(mlStart, mrStart);
            int secondEnd = (secondStart == mlStart) ? mlEnd : mrEnd;

            int deleteStart = secondStart;
            int deleteEnd = secondEnd;

            int lineStart = secondStart;
            while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
                lineStart--;
            }
            int lineEnd = secondEnd;
            while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') {
                lineEnd++;
            }
            int lineEndWithNewline = (lineEnd < text.length() && text.charAt(lineEnd) == '\n') ? lineEnd + 1 : lineEnd;

            String beforeOnLine = text.substring(lineStart, secondStart);
            String afterOnLine = text.substring(secondEnd, lineEnd);
            if (beforeOnLine.trim().isEmpty() && afterOnLine.trim().isEmpty()) {
                deleteStart = lineStart;
                deleteEnd = lineEndWithNewline;
            } else {
                while (deleteEnd < text.length() && Character.isWhitespace(text.charAt(deleteEnd)) && text.charAt(deleteEnd) != '\n') {
                    deleteEnd++;
                }
            }

            WorkspaceEdit edit = new WorkspaceEdit();
            edit.addEdit(doc.uri, new TextEdit(doc.toModelRange(firstStart, firstEnd), "margin: 0 auto;"));
            edit.addEdit(doc.uri, new TextEdit(doc.toModelRange(deleteStart, deleteEnd), ""));

            actions.add(new CodeAction("Replace with 'margin: 0 auto;' shorthand", CodeActionKind.QUICKFIX, edit));
        }
    }

    private void addMediaQueryUnitConversionActions(LspDocument doc, CodeActionParams params, List<CodeAction> actions) {
        String text = doc.text;
        int cursorOffset = doc.toOffset(params.range.start);
        if (cursorOffset < 0 || cursorOffset > text.length()) return;

        int lineStart = text.lastIndexOf('\n', cursorOffset);
        if (lineStart == -1) lineStart = 0; else lineStart++;
        int lineEnd = text.indexOf('\n', cursorOffset);
        if (lineEnd == -1) lineEnd = text.length();

        String line = text.substring(lineStart, lineEnd);
        if (!line.contains("@media") && !line.contains("px")) return;

        Pattern pxPat = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*px\\b");
        Matcher m = pxPat.matcher(line);
        while (m.find()) {
            int matchStart = lineStart + m.start();
            int matchEnd = lineStart + m.end();
            double pxVal = Double.parseDouble(m.group(1));
            double remVal = pxVal / 16.0;

            String remStr = formatMediaVal(remVal) + "rem";
            String emStr = formatMediaVal(remVal) + "em";

            LspRange range = doc.toModelRange(matchStart, matchEnd);

            WorkspaceEdit editRem = new WorkspaceEdit();
            editRem.addEdit(doc.uri, new TextEdit(range, remStr));
            actions.add(new CodeAction("Convert breakpoint to '" + remStr + "'", CodeActionKind.QUICKFIX, editRem));

            WorkspaceEdit editEm = new WorkspaceEdit();
            editEm.addEdit(doc.uri, new TextEdit(range, emStr));
            actions.add(new CodeAction("Convert breakpoint to '" + emStr + "'", CodeActionKind.QUICKFIX, editEm));
        }
    }

    private static String formatMediaVal(double val) {
        if (val == Math.floor(val)) {
            return String.valueOf((int) val);
        }
        return String.format(Locale.US, "%.3f", val).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void addEmptyRuleDeleteActions(LspDocument doc, CodeActionParams params, List<CodeAction> actions) {
        String text = doc.text;
        int cursorOffset = doc.toOffset(params.range.start);
        if (cursorOffset < 0 || cursorOffset > text.length()) return;

        CssTokenStream stream = CssLexer.tokenize(text);
        CssSyntaxTree tree = CssParser.parse(stream, text);

        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == CssSyntaxTree.N_RULE) {
                int start = tree.nodeStart[i];
                int end = tree.nodeEnd[i];
                if (start >= 0 && end <= text.length() && cursorOffset >= start && cursorOffset <= end) {
                    // Skip leading whitespace to avoid spanning before embedded boundary
                    while (start < end && Character.isWhitespace(text.charAt(start))) {
                        start++;
                    }
                    int braceIdx = text.indexOf('{', start);
                    if (braceIdx == -1 || braceIdx > end) continue;

                    boolean hasDeclarations = false;
                    int child = tree.nodeChild[i];
                    while (child != 0) {
                        if (tree.nodeType[child] == CssSyntaxTree.N_DECLARATION) {
                            hasDeclarations = true;
                            break;
                        }
                        child = tree.nodeSibling[child];
                    }
                    if (!hasDeclarations) {
                        int deleteEnd = end;
                        if (deleteEnd < text.length() && text.charAt(deleteEnd) == '\n') {
                            deleteEnd++;
                        }
                        WorkspaceEdit edit = new WorkspaceEdit();
                        String targetUri = (params.textDocument != null && params.textDocument.uri != null)
                                ? params.textDocument.uri : doc.uri;
                        edit.addEdit(targetUri, new TextEdit(doc.toModelRange(start, deleteEnd), ""));
                        actions.add(new CodeAction("Remove empty rule block", CodeActionKind.QUICKFIX, edit));
                        break;
                    }
                }
            }
        }
    }

    private void addNativeCssFixes(LspDocument doc, Diagnostic diag, List<CodeAction> actions) {
        if (doc == null || doc.text == null || diag == null) return;
        String code = diag.code != null ? diag.code : "";
        String msg = diag.message != null ? diag.message : "";

        // 1. CSS_ZERO_UNIT: 1 fix (remove unit from zero: 0px -> 0)
        if (ProblemCode.CSS_ZERO_UNIT.name().equals(code) || msg.contains("Unit unnecessary on zero") || msg.contains("unnecessary on zero")) {
            int startOff = doc.toOffset(diag.range.start);
            int endOff = doc.toOffset(diag.range.end);
            String slice = safeSubstring(doc.text, startOff, endOff);
            String replacement = slice.replaceAll("(?i)^0[a-z%]+$", "0");
            if (replacement.isEmpty()) replacement = "0";
            TextEdit edit = new TextEdit(diag.range, replacement);
            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
            actions.add(new CodeAction("Remove unit from zero value", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
        }

        // 2. CSS_PT_UNIT: 2 choices (convert to px vs convert to rem)
        if (ProblemCode.CSS_PT_UNIT.name().equals(code) || msg.contains("pt unit")) {
            int startOff = doc.toOffset(diag.range.start);
            int endOff = doc.toOffset(diag.range.end);
            String slice = safeSubstring(doc.text, startOff, endOff).trim();
            float val = parseLeadingNumber(slice, 12f);
            int px = Math.round(val * 1.3333f);
            float rem = Math.round((val / 12f) * 100f) / 100f;

            TextEdit editPx = new TextEdit(diag.range, px + "px");
            WorkspaceEdit wsEditPx = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(editPx)));
            actions.add(new CodeAction("Convert 'pt' to 'px' (" + px + "px)", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEditPx, null, null));

            TextEdit editRem = new TextEdit(diag.range, rem + "rem");
            WorkspaceEdit wsEditRem = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(editRem)));
            actions.add(new CodeAction("Convert 'pt' to 'rem' (" + rem + "rem)", CodeActionKind.QUICKFIX, Collections.singletonList(diag), false, wsEditRem, null, null));
        }

        // 3. CSS_IMPORTANT_USAGE: 1 fix (remove !important)
        if (ProblemCode.CSS_IMPORTANT_USAGE.name().equals(code) || msg.contains("!important")) {
            int startOff = doc.toOffset(diag.range.start);
            int endOff = doc.toOffset(diag.range.end);
            int impIdx = doc.text.indexOf("!important", Math.max(0, startOff - 2));
            if (impIdx == -1) impIdx = startOff;
            int delStart = impIdx;
            // Consume preceding whitespace to prevent orphaned spaces before the semicolon
            while (delStart > 0 && (doc.text.charAt(delStart - 1) == ' ' || doc.text.charAt(delStart - 1) == '\t')) {
                delStart--;
            }
            int delEnd = (impIdx + "!important".length() <= doc.text.length()) ? impIdx + "!important".length() : endOff;
            LspRange targetRange = doc.toModelRange(delStart, delEnd);
            TextEdit edit = new TextEdit(targetRange, "");
            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
            actions.add(new CodeAction("Remove '!important'", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
        }

        // 4. CSS_HEX_SHORTENABLE: 1 fix (shorten #ffffff -> #fff)
        if (ProblemCode.CSS_HEX_SHORTENABLE.name().equals(code) || msg.contains("can be shortened")) {
            int startOff = doc.toOffset(diag.range.start);
            int endOff = doc.toOffset(diag.range.end);
            String hex = safeSubstring(doc.text, startOff, endOff).trim();
            String shortened = shortenHex(hex);
            TextEdit edit = new TextEdit(diag.range, shortened);
            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
            actions.add(new CodeAction("Shorten hex color to '" + shortened + "'", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
        }

        // 5. CSS_DUPLICATE_PROPERTY: 1 fix (remove duplicate declaration)
        if (ProblemCode.CSS_DUPLICATE_PROPERTY.name().equals(code) || msg.contains("Duplicate property")) {
            LspRange delRange = expandToFullLine(doc, diag.range);
            TextEdit edit = new TextEdit(delRange, "");
            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
            actions.add(new CodeAction("Remove duplicate property declaration", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
        }

        // 6. CSS_VENDOR_PREFIX_NEEDED: 1 fix (add standard/vendor-prefixed property)
        if (ProblemCode.CSS_VENDOR_PREFIX_NEEDED.name().equals(code) || msg.contains("vendor prefix") || msg.contains("broader browser support") || msg.contains("Missing standard property")) {
            String targetProp = extractVendorTargetProperty(msg);
            int startOff = doc.toOffset(diag.range.start);
            int semi = doc.text.indexOf(';', startOff);
            String val = "";
            int colon = doc.text.indexOf(':', startOff);
            if (colon != -1 && semi != -1 && colon < semi) {
                val = doc.text.substring(colon + 1, semi).trim();
            }
            int insertOff = (semi != -1) ? semi + 1 : doc.toOffset(diag.range.end);
            LspPosition insPos = doc.toModelPosition(insertOff);
            String insertText = "\n  " + targetProp + ": " + val + ";";
            TextEdit edit = new TextEdit(new LspRange(insPos, insPos), insertText);
            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
            actions.add(new CodeAction("Add standard property declaration", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
        }

        // 7. CSS_VAR_NO_FALLBACK: 2 choices (fallback initial vs inherit)
        if (ProblemCode.CSS_VAR_NO_FALLBACK.name().equals(code) || msg.contains("custom property lacks a fallback") || msg.contains("fallback")) {
            int startOff = doc.toOffset(diag.range.start);
            int endOff = doc.toOffset(diag.range.end);
            String varSlice = safeSubstring(doc.text, startOff, endOff).trim();
            if (varSlice.endsWith(")")) {
                String inner = varSlice.substring(0, varSlice.length() - 1);
                TextEdit editInit = new TextEdit(diag.range, inner + ", initial)");
                WorkspaceEdit wsEditInit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(editInit)));
                actions.add(new CodeAction("Add 'initial' fallback to var()", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEditInit, null, null));

                TextEdit editInherit = new TextEdit(diag.range, inner + ", inherit)");
                WorkspaceEdit wsEditInherit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(editInherit)));
                actions.add(new CodeAction("Add 'inherit' fallback to var()", CodeActionKind.QUICKFIX, Collections.singletonList(diag), false, wsEditInherit, null, null));
            }
        }

        // 8. CSS_VAR_UNUSED: 1 fix (remove unused CSS custom property)
        if (ProblemCode.CSS_VAR_UNUSED.name().equals(code) || msg.contains("Unused CSS custom property")) {
            LspRange delRange = expandToFullLine(doc, diag.range);
            TextEdit edit = new TextEdit(delRange, "");
            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
            actions.add(new CodeAction("Remove unused CSS custom property", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
        }
    }

    private static String extractVendorTargetProperty(String msg) {
        if (msg == null) return "transform";
        int needIdx = msg.indexOf("may need");
        if (needIdx != -1) {
            int q1 = msg.indexOf('\'', needIdx);
            if (q1 != -1) {
                int q2 = msg.indexOf('\'', q1 + 1);
                if (q2 > q1) return msg.substring(q1 + 1, q2);
            }
        }
        int q1 = msg.indexOf('\'');
        if (q1 != -1) {
            int q2 = msg.indexOf('\'', q1 + 1);
            if (q2 > q1) return msg.substring(q1 + 1, q2);
        }
        return "transform";
    }

    private static LspRange expandToFullLine(LspDocument doc, LspRange range) {
        if (doc == null || doc.text == null || range == null || range.start == null) return range;
        int startOff = doc.toOffset(range.start);
        int endOff = doc.toOffset(range.end);
        String text = doc.text;
        int lineStart = text.lastIndexOf('\n', Math.max(0, startOff - 1));
        lineStart = (lineStart == -1) ? 0 : lineStart + 1;
        int lineEnd = text.indexOf('\n', endOff);
        if (lineEnd == -1) lineEnd = text.length();

        // Check if there is other meaningful content on this line before or after
        String before = text.substring(lineStart, Math.min(startOff, text.length())).trim();
        int semi = text.indexOf(';', endOff);
        int declEnd = (semi != -1 && semi <= lineEnd) ? semi + 1 : endOff;
        String after = text.substring(Math.min(declEnd, text.length()), lineEnd).trim();

        // If line contains other properties, braces, or selector, only delete the declaration
        if (!before.isEmpty() || !after.isEmpty()) {
            while (declEnd < text.length() && (text.charAt(declEnd) == ' ' || text.charAt(declEnd) == '\t')) {
                declEnd++;
            }
            return new LspRange(doc.toModelPosition(startOff), doc.toModelPosition(declEnd));
        }

        int deleteEnd = (lineEnd < text.length() && text.charAt(lineEnd) == '\n') ? lineEnd + 1 : lineEnd;
        return new LspRange(doc.toModelPosition(lineStart), doc.toModelPosition(deleteEnd));
    }

    private static String safeSubstring(String text, int start, int end) {
        if (text == null) return "";
        int s = Math.max(0, Math.min(start, text.length()));
        int e = Math.max(s, Math.min(end, text.length()));
        return text.substring(s, e);
    }

    private static String shortenHex(String hex) {
        if (hex == null) return "#fff";
        hex = hex.trim();
        if (hex.length() == 7 && hex.startsWith("#")) {
            char r1 = hex.charAt(1), r2 = hex.charAt(2);
            char g1 = hex.charAt(3), g2 = hex.charAt(4);
            char b1 = hex.charAt(5), b2 = hex.charAt(6);
            if (r1 == r2 && g1 == g2 && b1 == b2) {
                return "#" + r1 + g1 + b1;
            }
        }
        return hex;
    }

    private static float parseLeadingNumber(String s, float fallback) {
        if (s == null) return fallback;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.') {
                sb.append(c);
            } else if (sb.length() > 0) {
                break;
            }
        }
        try {
            return Float.parseFloat(sb.toString());
        } catch (Exception e) {
            return fallback;
        }
    }

    // -------------------------------------------------------------------------
    // Go to Definition (LSP 3.17 & Legacy)
    // -------------------------------------------------------------------------

    @Override
    public List<LspLocation> definition(DefinitionParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) {
            return Collections.emptyList();
        }
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();

        com.cocode.vcode.ide.core.lsp.LspPosition legacyPos =
                new com.cocode.vcode.ide.core.lsp.LspPosition(params.position.line, params.position.character);
        com.cocode.vcode.ide.core.lsp.LspLocation loc = definition(doc, legacyPos);
        return loc != null ? Collections.singletonList(loc) : Collections.emptyList();
    }

    @Override
    public com.cocode.vcode.ide.core.lsp.LspLocation definition(LspDocument doc, com.cocode.vcode.ide.core.lsp.LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;

        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return null;

        // 1. CSS Variable definition: var(--custom-prop) or --custom-prop across :root
        String varName = extractCssVariableAtOffset(doc.text, offset);
        if (varName != null) {
            com.cocode.vcode.ide.core.lsp.LspLocation localDef = findCssVariableDeclarationInDoc(doc, varName);
            if (localDef != null) return localDef;

            if (projectIndex != null) {
                List<com.cocode.vcode.ide.core.lsp.LspLocation> defs = projectIndex.findDefinitions(varName);
                if (!defs.isEmpty()) return defs.get(0);
            }
            WorkspaceIndexService wis = WorkspaceIndexService.getInstance();
            if (wis != null) {
                List<com.cocode.vcode.ide.core.lsp.LspLocation> defs = wis.findDefinitions(varName);
                if (!defs.isEmpty()) return defs.get(0);
            }
            for (String uri : getAllIndexedCssUris()) {
                if (uri.equals(doc.uri)) continue;
                LspDocument otherDoc = getDocument(uri);
                if (otherDoc != null && otherDoc.text != null && otherDoc.text.contains(varName)) {
                    com.cocode.vcode.ide.core.lsp.LspLocation crossDef = findCssVariableDeclarationInDoc(otherDoc, varName);
                    if (crossDef != null) return crossDef;
                }
            }
        }

        // 2. Check for @import via AST
        CssTokenStream stream = CssLexer.tokenize(doc.text);
        CssSyntaxTree tree = CssParser.parse(stream, doc.text);

        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == CssSyntaxTree.N_AT_RULE) {
                if (offset >= tree.nodeStart[i] && offset <= tree.nodeEnd[i]) {
                    int child = tree.nodeChild[i];
                    while (child != 0) {
                        if (tree.nodeType[child] == CssSyntaxTree.N_SELECTOR) {
                            String selText = tree.nodeName[child];
                            if (selText != null && selText.startsWith("@import")) {
                                String importPath = extractQuotedString(selText);
                                if (importPath != null) {
                                    File base = new File(doc.uri).getParentFile();
                                    File target = new File(base, importPath);
                                    if (target.exists()) {
                                        return new com.cocode.vcode.ide.core.lsp.LspLocation(target.getAbsolutePath(), new com.cocode.vcode.ide.core.lsp.LspRange(0, 0, 0, 0));
                                    }
                                }
                            }
                        }
                        child = tree.nodeSibling[child];
                    }
                }
            }
        }

        // 3. Look up class or ID selector definition in HTML documents
        String selector = extractSelectorAtOffset(doc.text, offset);
        if (selector != null) {
            if (projectIndex != null) {
                List<com.cocode.vcode.ide.core.lsp.LspLocation> defs = projectIndex.findDefinitions(selector);
                if (!defs.isEmpty()) return defs.get(0);
            }
            WorkspaceIndexService wis = WorkspaceIndexService.getInstance();
            if (wis != null) {
                List<com.cocode.vcode.ide.core.lsp.LspLocation> defs = wis.findDefinitions(selector);
                if (!defs.isEmpty()) return defs.get(0);
            }
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Find References (LSP 3.17 & Legacy)
    // -------------------------------------------------------------------------

    @Override
    public List<LspLocation> references(ReferenceParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) {
            return Collections.emptyList();
        }
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();

        com.cocode.vcode.ide.core.lsp.LspPosition legacyPos =
                new com.cocode.vcode.ide.core.lsp.LspPosition(params.position.line, params.position.character);
        List<com.cocode.vcode.ide.core.lsp.LspLocation> legacyLocs = references(doc, legacyPos);
        return new ArrayList<>(legacyLocs);
    }

    @Override
    public List<com.cocode.vcode.ide.core.lsp.LspLocation> references(LspDocument doc, com.cocode.vcode.ide.core.lsp.LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return Collections.emptyList();

        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return Collections.emptyList();

        // 1. CSS Variable references: --custom-prop
        String varName = extractCssVariableAtOffset(doc.text, offset);
        if (varName != null) {
            return findCssVariableReferences(varName);
        }

        // 2. Class / ID selector references in HTML/JS/TS
        String selector = extractSelectorAtOffset(doc.text, offset);
        if (selector == null) return Collections.emptyList();

        String plainName = selector.startsWith(".") || selector.startsWith("#")
                ? selector.substring(1) : selector;
        plainName = plainName.trim();
        if (plainName.isEmpty()) return Collections.emptyList();
        boolean isId = selector.startsWith("#");

        final int MAX_REFS = 100;
        List<com.cocode.vcode.ide.core.lsp.LspLocation> result = new ArrayList<>();
        Set<String> allUris = getAllIndexedCssAndHtmlUris();

        for (String uri : allUris) {
            if (result.size() >= MAX_REFS) break;
            LspDocument otherDoc = getDocument(uri);
            if (otherDoc == null || otherDoc.text == null) continue;

            if (uri.endsWith(".html") || uri.endsWith(".htm")) {
                String searchTerm = isId ? "id=\"" + plainName + "\"" : plainName;
                if (searchTerm.isEmpty()) continue;
                int step = Math.max(1, searchTerm.length());
                int idx = otherDoc.text.indexOf(searchTerm);
                while (idx >= 0 && result.size() < MAX_REFS) {
                    com.cocode.vcode.ide.core.lsp.LspPosition refPos = SymbolExtractor.offsetToPosition(otherDoc.text, idx);
                    result.add(new com.cocode.vcode.ide.core.lsp.LspLocation(uri, new com.cocode.vcode.ide.core.lsp.LspRange(refPos, new com.cocode.vcode.ide.core.lsp.LspPosition(refPos.line, refPos.character + searchTerm.length()))));
                    idx = otherDoc.text.indexOf(searchTerm, idx + step);
                }
            } else if (uri.endsWith(".js") || uri.endsWith(".ts")) {
                String searchTerm = isId ? plainName : "." + plainName;
                if (searchTerm.isEmpty()) continue;
                int step = Math.max(1, searchTerm.length());
                int idx = otherDoc.text.indexOf(searchTerm);
                while (idx >= 0 && result.size() < MAX_REFS) {
                    com.cocode.vcode.ide.core.lsp.LspPosition refPos = SymbolExtractor.offsetToPosition(otherDoc.text, idx);
                    result.add(new com.cocode.vcode.ide.core.lsp.LspLocation(uri, new com.cocode.vcode.ide.core.lsp.LspRange(refPos, new com.cocode.vcode.ide.core.lsp.LspPosition(refPos.line, refPos.character + searchTerm.length()))));
                    idx = otherDoc.text.indexOf(searchTerm, idx + step);
                }
            }
        }
        return result;
    }

    private List<com.cocode.vcode.ide.core.lsp.LspLocation> findCssVariableReferences(String varName) {
        List<com.cocode.vcode.ide.core.lsp.LspLocation> results = new ArrayList<>();
        final int MAX_REFS = 150;
        Set<String> allUris = getAllIndexedCssAndHtmlUris();

        for (String uri : allUris) {
            if (results.size() >= MAX_REFS) break;
            LspDocument d = getDocument(uri);
            if (d == null || d.text == null || !d.text.contains(varName)) continue;

            String text = d.text;
            int step = Math.max(1, varName.length());
            int idx = text.indexOf(varName);
            while (idx >= 0 && results.size() < MAX_REFS) {
                boolean validStart = (idx == 0) || !isCssVarChar(text.charAt(idx - 1));
                boolean validEnd = (idx + varName.length() >= text.length()) || !isCssVarChar(text.charAt(idx + varName.length()));
                if (validStart && validEnd) {
                    com.cocode.vcode.ide.core.lsp.LspPosition pos = d.toPosition(idx);
                    results.add(new com.cocode.vcode.ide.core.lsp.LspLocation(uri, new com.cocode.vcode.ide.core.lsp.LspRange(pos, new com.cocode.vcode.ide.core.lsp.LspPosition(pos.line, pos.character + varName.length()))));
                }
                idx = text.indexOf(varName, idx + step);
            }
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // Hover (LSP 3.17 & Legacy)
    // -------------------------------------------------------------------------

    @Override
    public Hover hover(HoverParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) return null;
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return null;

        com.cocode.vcode.ide.core.lsp.LspPosition legacyPos =
                new com.cocode.vcode.ide.core.lsp.LspPosition(params.position.line, params.position.character);
        HoverInfo info = hover(doc, legacyPos);
        if (info == null || info.getMarkdownContent() == null || info.getMarkdownContent().isEmpty()) return null;

        return new Hover(new MarkupContent(MarkupKind.MARKDOWN, info.getMarkdownContent()), toModelRange(info.getRange()));
    }

    @Override
    public HoverInfo hover(LspDocument doc, com.cocode.vcode.ide.core.lsp.LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;
        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return null;

        String text = doc.text;
        int len = text.length();
        if (offset >= len && offset > 0) offset = len - 1;
        if (len == 0) return null;

        // Check CSS Variable hover
        String varName = extractCssVariableAtOffset(text, offset);
        if (varName != null) {
            String val = resolveCssVariableValue(varName, doc);
            String md;
            if (val != null && !val.isEmpty()) {
                md = "```css\n:root {\n  " + varName + ": " + val + ";\n}\n```\n\nCSS Custom Property";
            } else {
                md = "```css\n" + varName + "\n```\n\nCSS Custom Property";
            }
            int varStart = text.lastIndexOf(varName, offset);
            if (varStart == -1) varStart = offset;
            com.cocode.vcode.ide.core.lsp.LspRange range = new com.cocode.vcode.ide.core.lsp.LspRange(doc.toPosition(varStart), doc.toPosition(varStart + varName.length()));
            return new HoverInfo(range, md);
        }

        // Standard symbol boundary extraction
        int s = offset;
        while (s > 0 && isCssNameChar(text.charAt(s - 1))) {
            s--;
        }
        int e = offset;
        while (e < len && isCssNameChar(text.charAt(e))) {
            e++;
        }

        if (s >= 2 && text.charAt(s - 1) == ':' && text.charAt(s - 2) == ':') {
            s -= 2;
        } else if (s >= 1 && text.charAt(s - 1) == ':') {
            s -= 1;
        } else if (s >= 1 && text.charAt(s - 1) == '@') {
            s -= 1;
        } else if (s >= 1 && text.charAt(s - 1) == '.') {
            s -= 1;
        } else if (s >= 1 && text.charAt(s - 1) == '#') {
            s -= 1;
        }

        if (s >= e) return null;
        String word = text.substring(s, e).trim();
        if (word.isEmpty()) return null;

        String md = null;

        if (word.startsWith("@")) {
            md = com.cocode.vcode.ide.core.language.css.CssDocumentationProvider.getAtRuleMarkdown(word);
        } else if (word.startsWith("::")) {
            md = com.cocode.vcode.ide.core.language.css.CssDocumentationProvider.getPseudoElementMarkdown(word);
        } else if (word.startsWith(":")) {
            md = com.cocode.vcode.ide.core.language.css.CssDocumentationProvider.getPseudoClassMarkdown(word);
        } else {
            boolean isFunction = false;
            int next = e;
            while (next < len && Character.isWhitespace(text.charAt(next))) next++;
            if (next < len && text.charAt(next) == '(') {
                isFunction = true;
            }

            if (isFunction) {
                md = com.cocode.vcode.ide.core.language.css.CssDocumentationProvider.getFunctionMarkdown(word);
            }
            if (md == null) {
                md = com.cocode.vcode.ide.core.language.css.CssDocumentationProvider.getDocForToken(word);
            }
            if (md == null) {
                md = com.cocode.vcode.ide.core.language.html.HtmlDocumentationProvider.getElementMarkdown(word);
            }
            if (md == null) {
                com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionItem item =
                        com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader.getCssProperty(word);
                if (item != null) {
                    String detail = item.detail != null ? item.detail : "CSS Property";
                    StringBuilder sb = new StringBuilder("### `").append(item.label).append("`\n\n").append(detail);
                    if (item.values != null && item.values.length > 0) {
                        sb.append("\n\n**Common Values:** `").append(String.join("`, `", item.values)).append("`");
                    }
                    md = sb.toString();
                }
            }
        }

        if (md != null) {
            com.cocode.vcode.ide.core.lsp.LspRange range = new com.cocode.vcode.ide.core.lsp.LspRange(doc.toPosition(s), doc.toPosition(e));
            return new HoverInfo(range, md);
        }

        return null;
    }

    private String resolveCssVariableValue(String varName, LspDocument currentDoc) {
        if (currentDoc != null && currentDoc.text != null) {
            CssTokenStream stream = CssLexer.tokenize(currentDoc.text);
            CssSyntaxTree tree = CssParser.parse(stream, currentDoc.text);
            for (int i = 1; i < tree.nodeCount; i++) {
                if (tree.nodeType[i] == CssSyntaxTree.N_PROPERTY && varName.equals(tree.nodeName[i])) {
                    int sib = tree.nodeSibling[i];
                    if (sib > 0 && sib < tree.nodeCount && tree.nodeType[sib] == CssSyntaxTree.N_VALUE) {
                        return tree.nodeValue[sib];
                    }
                }
            }
        }

        WorkspaceIndexService wis = WorkspaceIndexService.getInstance();
        if (wis != null) {
            List<SymbolEntry> entries = wis.findSymbolsByPrefix(varName);
            for (SymbolEntry s : entries) {
                if (varName.equals(s.name) && s.detail != null && !s.detail.isEmpty()) {
                    return s.detail;
                }
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Document Symbols & Outline (LSP 3.17)
    // -------------------------------------------------------------------------

    @Override
    public List<DocumentSymbol> documentSymbol(DocumentSymbolParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null || doc.text.isEmpty()) return Collections.emptyList();

        CssTokenStream stream = CssLexer.tokenize(doc.text);
        CssSyntaxTree tree = CssParser.parse(stream, doc.text);
        List<DocumentSymbol> result = new ArrayList<>();

        for (int i = 1; i < tree.nodeCount; i++) {
            if (token != null && token.isCancelled()) return Collections.emptyList();
            if (tree.nodeParent[i] != 0) continue;

            if (tree.nodeType[i] == CssSyntaxTree.N_AT_RULE) {
                String atName = getAtRuleHeader(tree, i, doc.text);
                LspRange range = doc.toModelRange(tree.nodeStart[i], tree.nodeEnd[i]);
                List<DocumentSymbol> children = extractAtRuleChildren(tree, i, doc);
                result.add(new DocumentSymbol(atName, "", SymbolKind.NAMESPACE, null, range, range, children));
            } else if (tree.nodeType[i] == CssSyntaxTree.N_RULE) {
                String selector = getRuleSelector(tree, i, doc.text);
                LspRange range = doc.toModelRange(tree.nodeStart[i], tree.nodeEnd[i]);
                List<DocumentSymbol> children = extractRuleDeclarations(tree, i, doc);
                result.add(new DocumentSymbol(selector, "", SymbolKind.CLASS, null, range, range, children));
            }
        }
        return result;
    }

    private static String getAtRuleHeader(CssSyntaxTree tree, int node, String text) {
        int child = tree.nodeChild[node];
        while (child != 0) {
            if (tree.nodeType[child] == CssSyntaxTree.N_SELECTOR) {
                String name = tree.nodeName[child];
                if (name != null && !name.isEmpty()) return name;
            }
            child = tree.nodeSibling[child];
        }
        int start = tree.nodeStart[node];
        int end = Math.min(tree.nodeEnd[node], text.length());
        int brace = text.indexOf('{', start);
        if (brace != -1 && brace < end) {
            return text.substring(start, brace).trim();
        }
        return "@rule";
    }

    private static String getRuleSelector(CssSyntaxTree tree, int node, String text) {
        int child = tree.nodeChild[node];
        while (child != 0) {
            if (tree.nodeType[child] == CssSyntaxTree.N_SELECTOR) {
                String name = tree.nodeName[child];
                if (name != null && !name.isEmpty()) return name;
            }
            child = tree.nodeSibling[child];
        }
        int start = tree.nodeStart[node];
        int end = Math.min(tree.nodeEnd[node], text.length());
        int brace = text.indexOf('{', start);
        if (brace != -1 && brace < end) {
            return text.substring(start, brace).trim();
        }
        return "rule";
    }

    private static List<DocumentSymbol> extractAtRuleChildren(CssSyntaxTree tree, int atNode, LspDocument doc) {
        List<DocumentSymbol> children = new ArrayList<>();
        int child = tree.nodeChild[atNode];
        while (child != 0) {
            if (tree.nodeType[child] == CssSyntaxTree.N_RULE) {
                String selector = getRuleSelector(tree, child, doc.text);
                LspRange range = doc.toModelRange(tree.nodeStart[child], tree.nodeEnd[child]);
                List<DocumentSymbol> decls = extractRuleDeclarations(tree, child, doc);
                children.add(new DocumentSymbol(selector, "", SymbolKind.CLASS, null, range, range, decls));
            }
            child = tree.nodeSibling[child];
        }
        return children;
    }

    private static List<DocumentSymbol> extractRuleDeclarations(CssSyntaxTree tree, int ruleNode, LspDocument doc) {
        List<DocumentSymbol> decls = new ArrayList<>();
        int child = tree.nodeChild[ruleNode];
        while (child != 0) {
            if (tree.nodeType[child] == CssSyntaxTree.N_DECLARATION) {
                int propNode = tree.nodeChild[child];
                String propName = null;
                String propVal = null;
                while (propNode != 0) {
                    if (tree.nodeType[propNode] == CssSyntaxTree.N_PROPERTY) {
                        propName = tree.nodeName[propNode];
                    } else if (tree.nodeType[propNode] == CssSyntaxTree.N_VALUE) {
                        propVal = tree.nodeValue[propNode];
                    }
                    propNode = tree.nodeSibling[propNode];
                }
                if (propName != null) {
                    LspRange range = doc.toModelRange(tree.nodeStart[child], tree.nodeEnd[child]);
                    decls.add(new DocumentSymbol(propName, propVal != null ? propVal : "", SymbolKind.PROPERTY, null, range, range, Collections.emptyList()));
                }
            }
            child = tree.nodeSibling[child];
        }
        return decls;
    }

    // -------------------------------------------------------------------------
    // Document Color & Color Presentation (LSP 3.17)
    // -------------------------------------------------------------------------

    @Override
    public List<ColorInformation> documentColor(DocumentColorParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null || doc.text.isEmpty()) return Collections.emptyList();

        List<ColorInformation> result = new ArrayList<>();
        CssTokenStream stream = CssLexer.tokenize(doc.text);
        CssSyntaxTree tree = CssParser.parse(stream, doc.text);

        for (int i = 1; i < tree.nodeCount; i++) {
            if (token != null && token.isCancelled()) return Collections.emptyList();
            if (tree.nodeType[i] == CssSyntaxTree.N_VALUE) {
                String val = tree.nodeValue[i];
                if (val == null || val.isEmpty()) continue;
                int valStart = tree.nodeStart[i];

                // 1. Hex matches
                Matcher hexMatcher = HEX_COLOR_PAT.matcher(val);
                while (hexMatcher.find()) {
                    String hex = hexMatcher.group();
                    if (CssLinter.isValidHexColor(hex)) {
                        Color c = parseHexColor(hex);
                        if (c != null) {
                            int absStart = valStart + hexMatcher.start();
                            int absEnd = valStart + hexMatcher.end();
                            result.add(new ColorInformation(doc.toModelRange(absStart, absEnd), c));
                        }
                    }
                }

                // 2. RGB/RGBA matches
                Matcher rgbMatcher = RGB_COLOR_PAT.matcher(val);
                while (rgbMatcher.find()) {
                    try {
                        float r = clamp01(Integer.parseInt(rgbMatcher.group(1)) / 255.0f);
                        float g = clamp01(Integer.parseInt(rgbMatcher.group(2)) / 255.0f);
                        float b = clamp01(Integer.parseInt(rgbMatcher.group(3)) / 255.0f);
                        float a = rgbMatcher.group(4) != null ? clamp01(Float.parseFloat(rgbMatcher.group(4))) : 1.0f;
                        int absStart = valStart + rgbMatcher.start();
                        int absEnd = valStart + rgbMatcher.end();
                        result.add(new ColorInformation(doc.toModelRange(absStart, absEnd), new Color(r, g, b, a)));
                    } catch (Throwable ignored) {
                    }
                }

                // 3. HSL/HSLA matches
                Matcher hslMatcher = HSL_COLOR_PAT.matcher(val);
                while (hslMatcher.find()) {
                    try {
                        float h = Float.parseFloat(hslMatcher.group(1));
                        float s = clamp01(Float.parseFloat(hslMatcher.group(2)) / 100.0f);
                        float l = clamp01(Float.parseFloat(hslMatcher.group(3)) / 100.0f);
                        float a = hslMatcher.group(4) != null ? clamp01(Float.parseFloat(hslMatcher.group(4))) : 1.0f;
                        float[] rgb = hslToRgb(h, s, l);
                        int absStart = valStart + hslMatcher.start();
                        int absEnd = valStart + hslMatcher.end();
                        result.add(new ColorInformation(doc.toModelRange(absStart, absEnd), new Color(rgb[0], rgb[1], rgb[2], a)));
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return result;
    }

    private static Color parseHexColor(String hex) {
        try {
            int len = hex.length();
            if (len == 4) {
                float r = Integer.parseInt("" + hex.charAt(1) + hex.charAt(1), 16) / 255.0f;
                float g = Integer.parseInt("" + hex.charAt(2) + hex.charAt(2), 16) / 255.0f;
                float b = Integer.parseInt("" + hex.charAt(3) + hex.charAt(3), 16) / 255.0f;
                return new Color(r, g, b, 1.0f);
            } else if (len == 5) {
                float r = Integer.parseInt("" + hex.charAt(1) + hex.charAt(1), 16) / 255.0f;
                float g = Integer.parseInt("" + hex.charAt(2) + hex.charAt(2), 16) / 255.0f;
                float b = Integer.parseInt("" + hex.charAt(3) + hex.charAt(3), 16) / 255.0f;
                float a = Integer.parseInt("" + hex.charAt(4) + hex.charAt(4), 16) / 255.0f;
                return new Color(r, g, b, a);
            } else if (len == 7) {
                float r = Integer.parseInt(hex.substring(1, 3), 16) / 255.0f;
                float g = Integer.parseInt(hex.substring(3, 5), 16) / 255.0f;
                float b = Integer.parseInt(hex.substring(5, 7), 16) / 255.0f;
                return new Color(r, g, b, 1.0f);
            } else if (len == 9) {
                float r = Integer.parseInt(hex.substring(1, 3), 16) / 255.0f;
                float g = Integer.parseInt(hex.substring(3, 5), 16) / 255.0f;
                float b = Integer.parseInt(hex.substring(5, 7), 16) / 255.0f;
                float a = Integer.parseInt(hex.substring(7, 9), 16) / 255.0f;
                return new Color(r, g, b, a);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public List<ColorPresentation> colorPresentation(ColorPresentationParams params, CancellationToken token) {
        if (params == null || params.color == null || params.range == null) return Collections.emptyList();
        Color c = params.color;
        LspRange range = params.range;

        List<ColorPresentation> presentations = new ArrayList<>(3);

        int r = Math.round(clamp01(c.red) * 255.0f);
        int g = Math.round(clamp01(c.green) * 255.0f);
        int b = Math.round(clamp01(c.blue) * 255.0f);
        int a = Math.round(clamp01(c.alpha) * 255.0f);

        // 1. Hex presentation
        String hex;
        if (a == 255) {
            hex = String.format(Locale.US, "#%02x%02x%02x", r, g, b);
        } else {
            hex = String.format(Locale.US, "#%02x%02x%02x%02x", r, g, b, a);
        }
        presentations.add(new ColorPresentation(hex, new TextEdit(range, hex)));

        // 2. RGB presentation
        String rgb;
        if (c.alpha >= 0.999f) {
            rgb = String.format(Locale.US, "rgb(%d, %d, %d)", r, g, b);
        } else {
            rgb = String.format(Locale.US, "rgba(%d, %d, %d, %.2f)", r, g, b, c.alpha);
        }
        presentations.add(new ColorPresentation(rgb, new TextEdit(range, rgb)));

        // 3. HSL presentation
        float[] hsl = rgbToHsl(c.red, c.green, c.blue);
        int h = Math.round(hsl[0]);
        int s = Math.round(hsl[1] * 100.0f);
        int l = Math.round(hsl[2] * 100.0f);
        String hslStr;
        if (c.alpha >= 0.999f) {
            hslStr = String.format(Locale.US, "hsl(%d, %d%%, %d%%)", h, s, l);
        } else {
            hslStr = String.format(Locale.US, "hsla(%d, %d%%, %d%%, %.2f)", h, s, l, c.alpha);
        }
        presentations.add(new ColorPresentation(hslStr, new TextEdit(range, hslStr)));

        return presentations;
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    private static float[] rgbToHsl(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float h, s, l;
        l = (max + min) / 2.0f;
        if (max == min) {
            h = 0.0f;
            s = 0.0f;
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2.0f - max - min) : d / (max + min);
            if (max == r) {
                h = ((g - b) / d + (g < b ? 6.0f : 0.0f));
            } else if (max == g) {
                h = ((b - r) / d + 2.0f);
            } else {
                h = ((r - g) / d + 4.0f);
            }
            h *= 60.0f;
        }
        return new float[]{h, s, l};
    }

    private static float[] hslToRgb(float h, float s, float l) {
        float r, g, b;
        if (s == 0.0f) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1.0f + s) : l + s - l * s;
            float p = 2.0f * l - q;
            r = hueToRgb(p, q, h / 360.0f + 1.0f / 3.0f);
            g = hueToRgb(p, q, h / 360.0f);
            b = hueToRgb(p, q, h / 360.0f - 1.0f / 3.0f);
        }
        return new float[]{clamp01(r), clamp01(g), clamp01(b)};
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0.0f) t += 1.0f;
        if (t > 1.0f) t -= 1.0f;
        if (t < 1.0f / 6.0f) return p + (q - p) * 6.0f * t;
        if (t < 1.0f / 2.0f) return q;
        if (t < 2.0f / 3.0f) return p + (q - p) * (2.0f / 3.0f - t) * 6.0f;
        return p;
    }

    // -------------------------------------------------------------------------
    // Formatting (LSP 3.17)
    // -------------------------------------------------------------------------

    @Override
    public List<TextEdit> formatting(DocumentFormattingParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null || doc.text.isEmpty()) return Collections.emptyList();

        String formatted = new CssFormatter().format(doc.text);
        if (formatted != null && !doc.text.endsWith("\n") && formatted.endsWith("\n")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted == null || formatted.equals(doc.text)) {
            return Collections.emptyList();
        }

        LspRange fullRange = doc.toModelRange(0, doc.text.length());
        return Collections.singletonList(new TextEdit(fullRange, formatted));
    }

    // -------------------------------------------------------------------------
    // Unused / Stubs
    // -------------------------------------------------------------------------

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, com.cocode.vcode.ide.core.lsp.LspPosition pos) {
        return null;
    }

    @Override
    public List<com.cocode.vcode.ide.core.lsp.LspLocation> rename(LspDocument doc, com.cocode.vcode.ide.core.lsp.LspPosition pos) {
        return Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Document / Index Helpers
    // -------------------------------------------------------------------------

    private LspDocument getDocument(String uri) {
        if (uri == null) return null;
        LspDocument doc = LspDocumentManager.getInstance().getDocument(uri);
        if (doc != null) return doc;
        doc = openDocs.get(uri);
        if (doc != null) return doc;
        if (projectIndex != null) {
            doc = projectIndex.getDocument(uri);
            if (doc != null) return doc;
        }
        WorkspaceIndexService wis = WorkspaceIndexService.getInstance();
        if (wis != null) {
            doc = wis.getDocument(uri);
            if (doc != null) return doc;
        }
        try {
            File f = new File(uri);
            if (f.exists() && f.isFile()) {
                String text = FileUtils.readFile(f);
                return new LspDocument(uri, text, "css", 1);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String extractCssVariableAtOffset(String text, int offset) {
        if (text == null || offset < 0 || offset > text.length()) return null;
        int len = text.length();
        if (offset >= len && offset > 0) offset = len - 1;

        int start = offset;
        while (start > 0 && isCssVarChar(text.charAt(start - 1))) {
            start--;
        }
        int end = offset;
        while (end < len && isCssVarChar(text.charAt(end))) {
            end++;
        }

        if (start < end) {
            String word = text.substring(start, end);
            if (word.startsWith("--")) {
                return word;
            }
        }
        return null;
    }

    private static boolean isCssVarChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }

    private static boolean isCssNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }

    private static final class ScoredVarSymbol {
        final SymbolEntry entry;
        final int score;
        ScoredVarSymbol(SymbolEntry entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }

    private List<SymbolEntry> filterAndRankVariables(List<SymbolEntry> allSymbols, String word) {
        if (allSymbols == null || allSymbols.isEmpty()) return Collections.emptyList();
        String query = word != null ? word.trim().toLowerCase() : "";

        // When cursor is at var(--, var(-, or var(, show all variables
        if (query.isEmpty() || query.equals("-") || query.equals("--")) {
            return allSymbols;
        }

        List<ScoredVarSymbol> scored = new ArrayList<>();
        for (SymbolEntry sym : allSymbols) {
            if (sym == null || sym.name == null) continue;
            int score = AutoCompleteEngine.computeFuzzyScore(sym.name, query);
            if (score > 0) {
                scored.add(new ScoredVarSymbol(sym, score));
            }
        }

        // Sort descending by match score
        Collections.sort(scored, (a, b) -> Integer.compare(b.score, a.score));

        List<SymbolEntry> results = new ArrayList<>(scored.size());
        for (ScoredVarSymbol s : scored) {
            results.add(s.entry);
        }
        return results;
    }

    private static boolean isTypingCssVariablePrefix(String text, int offset) {
        if (text == null || offset < 2) return false;
        int s = offset;
        while (s > 0 && isCssVarChar(text.charAt(s - 1))) {
            s--;
        }
        return text.substring(s, offset).startsWith("--");
    }

    private List<SymbolEntry> collectAllWorkspaceCssVariables(LspDocument currentDoc) {
        List<SymbolEntry> results = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        // 1. Current document scan
        if (currentDoc != null && currentDoc.text != null) {
            CssTokenStream stream = CssLexer.tokenize(currentDoc.text);
            CssSyntaxTree tree = CssParser.parse(stream, currentDoc.text);
            for (int i = 1; i < tree.nodeCount; i++) {
                if (tree.nodeType[i] == CssSyntaxTree.N_PROPERTY) {
                    String name = tree.nodeName[i];
                    if (name != null && name.startsWith("--") && seenNames.add(name)) {
                        String val = null;
                        int sib = tree.nodeSibling[i];
                        if (sib > 0 && sib < tree.nodeCount && tree.nodeType[sib] == CssSyntaxTree.N_VALUE) {
                            val = tree.nodeValue[sib];
                        }
                        com.cocode.vcode.ide.core.lsp.LspRange range =
                                new com.cocode.vcode.ide.core.lsp.LspRange(currentDoc.toPosition(tree.nodeStart[i]), currentDoc.toPosition(tree.nodeEnd[i]));
                        results.add(new SymbolEntry(name, currentDoc.uri, range, SymbolEntry.KIND_CSS_VARIABLE, val));
                    }
                }
            }
        }

        // 2. WorkspaceIndexService
        WorkspaceIndexService wis = WorkspaceIndexService.getInstance();
        if (wis != null) {
            List<SymbolEntry> entries = wis.findSymbolsByPrefix("--");
            for (SymbolEntry s : entries) {
                if (s.name != null && s.name.startsWith("--") && seenNames.add(s.name)) {
                    results.add(s);
                }
            }
        }

        // 3. ProjectIndex
        if (projectIndex != null) {
            for (String uri : projectIndex.getAllUris()) {
                if (uri.endsWith(".css") || uri.endsWith(".scss") || uri.endsWith(".less")) {
                    List<SymbolEntry> syms = projectIndex.getFileSymbols(uri);
                    if (syms != null) {
                        for (SymbolEntry s : syms) {
                            if (s.name != null && s.name.startsWith("--") && seenNames.add(s.name)) {
                                results.add(s);
                            }
                        }
                    }
                }
            }
        }

        return results;
    }

    private static com.cocode.vcode.ide.core.lsp.LspLocation findCssVariableDeclarationInDoc(LspDocument doc, String varName) {
        if (doc == null || doc.text == null) return null;
        CssTokenStream stream = CssLexer.tokenize(doc.text);
        CssSyntaxTree tree = CssParser.parse(stream, doc.text);
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == CssSyntaxTree.N_PROPERTY) {
                String pName = tree.nodeName[i];
                if (varName.equals(pName)) {
                    int start = tree.nodeStart[i];
                    com.cocode.vcode.ide.core.lsp.LspPosition pos = doc.toPosition(start);
                    return new com.cocode.vcode.ide.core.lsp.LspLocation(doc.uri, new com.cocode.vcode.ide.core.lsp.LspRange(pos, new com.cocode.vcode.ide.core.lsp.LspPosition(pos.line, pos.character + varName.length())));
                }
            }
        }
        return null;
    }

    private Set<String> getAllIndexedCssUris() {
        Set<String> set = new HashSet<>(openDocs.keySet());
        if (projectIndex != null) {
            for (String uri : projectIndex.getAllUris()) {
                if (uri.endsWith(".css") || uri.endsWith(".scss") || uri.endsWith(".less")) {
                    set.add(uri);
                }
            }
        }
        WorkspaceIndexService wis = WorkspaceIndexService.getInstance();
        if (wis != null) {
            for (String uri : wis.getAllUris()) {
                if (uri.endsWith(".css") || uri.endsWith(".scss") || uri.endsWith(".less")) {
                    set.add(uri);
                }
            }
        }
        return set;
    }

    private Set<String> getAllIndexedCssAndHtmlUris() {
        Set<String> set = getAllIndexedCssUris();
        if (projectIndex != null) {
            for (String uri : projectIndex.getAllUris()) {
                if (uri.endsWith(".html") || uri.endsWith(".htm") || uri.endsWith(".js") || uri.endsWith(".ts")) {
                    set.add(uri);
                }
            }
        }
        WorkspaceIndexService wis = WorkspaceIndexService.getInstance();
        if (wis != null) {
            for (String uri : wis.getAllUris()) {
                if (uri.endsWith(".html") || uri.endsWith(".htm") || uri.endsWith(".js") || uri.endsWith(".ts")) {
                    set.add(uri);
                }
            }
        }
        return set;
    }

    private static String extractSelectorAtOffset(String text, int offset) {
        if (text == null || offset < 0 || offset > text.length()) return null;
        int start = offset;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (c == '.' || c == '#') {
                start--;
                break;
            } else if (c == '-' || c == '_' || Character.isLetterOrDigit(c)) {
                start--;
            } else {
                break;
            }
        }
        if (start >= text.length() || (text.charAt(start) != '.' && text.charAt(start) != '#')) {
            return null;
        }
        int end = start + 1;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (c == '-' || c == '_' || Character.isLetterOrDigit(c)) {
                end++;
            } else {
                break;
            }
        }
        if (end > start + 1) {
            return text.substring(start, end);
        }
        return null;
    }

    private static String extractQuotedString(String str) {
        if (str == null) return null;
        int firstQuote = -1;
        char quoteChar = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '"' || c == '\'') {
                firstQuote = i;
                quoteChar = c;
                break;
            }
        }
        if (firstQuote != -1) {
            int secondQuote = str.indexOf(quoteChar, firstQuote + 1);
            if (secondQuote != -1) {
                return str.substring(firstQuote + 1, secondQuote);
            }
        }
        return null;
    }

    private static CompletionItemKind mapLegacyCompletionKind(com.cocode.vcode.ide.core.model.CompletionItem.Type type) {
        if (type == null) return CompletionItemKind.TEXT;
        switch (type) {
            case CSS_PROPERTY:
                return CompletionItemKind.PROPERTY;
            case CSS_VALUE:
            case VALUE:
                return CompletionItemKind.VALUE;
            case TAG:
                return CompletionItemKind.CLASS;
            case FUNCTION:
                return CompletionItemKind.FUNCTION;
            case KEYWORD:
                return CompletionItemKind.KEYWORD;
            case SNIPPET:
                return CompletionItemKind.SNIPPET;
            default:
                return CompletionItemKind.TEXT;
        }
    }

    private static LspRange toModelRange(com.cocode.vcode.ide.core.lsp.LspRange r) {
        if (r == null) return null;
        return new LspRange(new LspPosition(r.start.line, r.start.character), new LspPosition(r.end.line, r.end.character));
    }
}
