package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.language.json.JsonAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.json.JsonDocumentationProvider;
import com.cocode.vcode.ide.core.language.json.JsonFormatter;
import com.cocode.vcode.ide.core.language.json.JsonLexer;
import com.cocode.vcode.ide.core.language.json.JsonLinter;
import com.cocode.vcode.ide.core.language.json.JsonParser;
import com.cocode.vcode.ide.core.language.json.JsonSchemaManager;
import com.cocode.vcode.ide.core.language.json.JsonSyntaxTree;
import com.cocode.vcode.ide.core.language.json.JsonTokenStream;
import com.cocode.vcode.ide.core.lsp.CancellationToken;
import com.cocode.vcode.ide.core.lsp.HoverInfo;
import com.cocode.vcode.ide.core.lsp.LspCompletionConverter;
import com.cocode.vcode.ide.core.lsp.LspCompletionItem;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspDocumentManager;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspRange;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.lsp.ServerCapabilities;
import com.cocode.vcode.ide.core.lsp.model.CodeAction;
import com.cocode.vcode.ide.core.lsp.model.CodeActionKind;
import com.cocode.vcode.ide.core.lsp.model.CodeActionParams;
import com.cocode.vcode.ide.core.lsp.model.CompletionItemKind;
import com.cocode.vcode.ide.core.lsp.model.CompletionParams;
import com.cocode.vcode.ide.core.lsp.model.DefinitionParams;
import com.cocode.vcode.ide.core.lsp.model.Diagnostic;
import com.cocode.vcode.ide.core.lsp.model.DiagnosticSeverity;
import com.cocode.vcode.ide.core.lsp.model.DocumentDiagnosticParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentFormattingParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentSymbol;
import com.cocode.vcode.ide.core.lsp.model.DocumentSymbolParams;
import com.cocode.vcode.ide.core.lsp.model.Hover;
import com.cocode.vcode.ide.core.lsp.model.HoverParams;
import com.cocode.vcode.ide.core.lsp.model.InitializeParams;
import com.cocode.vcode.ide.core.lsp.model.MarkupContent;
import com.cocode.vcode.ide.core.lsp.model.SymbolKind;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentContentChangeEvent;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentIdentifier;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentItem;
import com.cocode.vcode.ide.core.lsp.model.TextEdit;
import com.cocode.vcode.ide.core.lsp.model.VersionedTextDocumentIdentifier;
import com.cocode.vcode.ide.core.lsp.model.WorkspaceEdit;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process Language Server for JSON and JSONC files implementing full LSP 3.17 capabilities.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Document Synchronization</b>: didOpen, didChange, didSave, didClose with AST index updates.</li>
 *   <li><b>Completions</b>: Schema-driven completions for package.json, tsconfig.json, and general JSON.</li>
 *   <li><b>Diagnostics</b>: RFC 8259 strict vs JSONC linting, single root enforcement, and schema validation.</li>
 *   <li><b>Code Actions</b>: Add missing required schema fields, remove trailing commas, fix quotes, remove duplicate keys.</li>
 *   <li><b>Hover Documentation</b>: Rich schema markdown docs extracted from schemas and documentation providers.</li>
 *   <li><b>Document Symbols</b>: Hierarchical outline for objects, arrays, and properties.</li>
 *   <li><b>Formatting</b>: Full-document atomic TextEdit formatting with configurable indentation.</li>
 *   <li><b>Go to Definition</b>: Resolves file path string values to files in the project.</li>
 * </ul>
 */
public final class JsonLspServer implements LspServer {

    private static final ServerCapabilities CAPABILITIES = new ServerCapabilities.Builder()
            .setHoverProvider(true)
            .setCompletionProvider(true)
            .setDefinitionProvider(true)
            .setReferencesProvider(false)
            .setDocumentHighlightProvider(false)
            .setDocumentSymbolProvider(true)
            .setCodeActionProvider(true)
            .setFormattingProvider(true)
            .setRangeFormattingProvider(false)
            .setFoldingRangeProvider(true)
            .setSignatureHelpProvider(false)
            .setSemanticTokensProvider(false)
            .build();

    private final JsonAutoCompleteEngine completeEngine;
    private final ConcurrentHashMap<String, LspDocument> openDocs = new ConcurrentHashMap<>();
    private volatile boolean ready = false;
    private ProjectIndex projectIndex;

    public JsonLspServer(Context context) {
        this.completeEngine = new JsonAutoCompleteEngine(context);
    }

    public JsonLspServer() {
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
    public void initialize(InitializeParams params) {
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
        return "json";
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
        if (document == null || document.uri == null) return;
        LspDocument doc = new LspDocument(document.uri, document.text, document.languageId != null ? document.languageId : "json", document.version);
        openDocs.put(document.uri, doc);
        LspDocumentManager.getInstance().openDocument(document);
    }

    @Override
    public void didChange(VersionedTextDocumentIdentifier document, List<TextDocumentContentChangeEvent> changes) {
        if (document == null || document.uri == null || changes == null) return;
        LspDocument existing = getDocument(document.uri);
        String currentText = existing != null ? existing.text : "";

        for (TextDocumentContentChangeEvent change : changes) {
            if (change.range != null && existing != null) {
                int startOffset = existing.toOffset(change.range.start);
                int endOffset = existing.toOffset(change.range.end);
                if (startOffset >= 0 && endOffset >= startOffset && endOffset <= currentText.length()) {
                    currentText = currentText.substring(0, startOffset) + (change.text != null ? change.text : "") + currentText.substring(endOffset);
                }
            } else if (change.text != null) {
                currentText = change.text;
            }
        }

        LspDocument updated = new LspDocument(document.uri, currentText, "json", document.version);
        openDocs.put(document.uri, updated);
        LspDocumentManager.getInstance().updateDocument(document.uri, currentText, document.version);
    }

    @Override
    public void didSave(TextDocumentIdentifier document) {
        if (document == null || document.uri == null) return;
        LspDocument doc = getDocument(document.uri);
        if (doc != null) {
            openDocs.put(document.uri, doc);
        }
    }

    @Override
    public void didClose(TextDocumentIdentifier document) {
        if (document == null || document.uri == null) return;
        openDocs.remove(document.uri);
        LspDocumentManager.getInstance().closeDocument(document.uri);
    }

    public LspDocument getDocument(String uri) {
        if (uri == null) return null;
        LspDocument doc = openDocs.get(uri);
        if (doc != null) return doc;
        return LspDocumentManager.getInstance().getDocument(uri);
    }

    // -------------------------------------------------------------------------
    // Completions (LSP 3.17 & Legacy)
    // -------------------------------------------------------------------------

    @Override
    public List<com.cocode.vcode.ide.core.lsp.model.CompletionItem> completion(CompletionParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) {
            return Collections.emptyList();
        }
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        int flatOffset = doc.toOffset(params.position);
        if (flatOffset < 0) flatOffset = doc.text.length();

        List<CompletionItem> combined = new ArrayList<>();
        File docFile = doc.uri != null ? new File(doc.uri) : null;

        // 1. Schema-driven completions (package.json, tsconfig.json)
        boolean isJsonc = JsonLinter.isJsoncMode(docFile);
        JsonTokenStream stream = JsonLexer.tokenize(doc.text);
        JsonSyntaxTree tree = JsonParser.parse(stream, doc.text, isJsonc);
        List<CompletionItem> schemaItems = JsonSchemaManager.getCompletions(docFile, doc.uri, doc.text, flatOffset, tree);
        if (schemaItems != null && !schemaItems.isEmpty()) {
            combined.addAll(schemaItems);
        }

        // 2. Engine suggestions
        if (docFile != null) {
            completeEngine.setCurrentFile(docFile);
        }
        List<CompletionItem> legacy = completeEngine.getSuggestions(doc.text, flatOffset);
        if (legacy != null) {
            Set<String> seen = new HashSet<>();
            for (CompletionItem si : combined) {
                seen.add(si.getLabel());
            }
            for (CompletionItem ci : legacy) {
                if (seen.add(ci.getLabel())) {
                    combined.add(ci);
                }
            }
        }

        List<com.cocode.vcode.ide.core.lsp.model.CompletionItem> result = new ArrayList<>(combined.size());
        for (CompletionItem ci : combined) {
            result.add(convertToModelItem(ci));
        }
        return result;
    }

    private com.cocode.vcode.ide.core.lsp.model.CompletionItem convertToModelItem(CompletionItem ci) {
        String label = ci.getLabel();
        String insertText = ci.getEffectiveInsertText();
        CompletionItemKind kind = mapModelKind(ci.getType());
        String detail = ci.getDetail();
        return new com.cocode.vcode.ide.core.lsp.model.CompletionItem(label, insertText, kind, detail);
    }

    private CompletionItemKind mapModelKind(CompletionItem.Type type) {
        if (type == null) return CompletionItemKind.TEXT;
        switch (type) {
            case JSON_KEY:
                return CompletionItemKind.PROPERTY;
            case VALUE:
                return CompletionItemKind.VALUE;
            case SNIPPET:
                return CompletionItemKind.SNIPPET;
            case KEYWORD:
                return CompletionItemKind.KEYWORD;
            default:
                return CompletionItemKind.TEXT;
        }
    }

    @Override
    public List<LspCompletionItem> completion(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null) return Collections.emptyList();

        int flatOffset = doc.toOffset(pos);
        if (flatOffset < 0) flatOffset = doc.text.length();

        File docFile = doc.uri != null ? new File(doc.uri) : null;
        if (docFile != null) {
            completeEngine.setCurrentFile(docFile);
        }
        List<CompletionItem> legacy = completeEngine.getSuggestions(doc.text, flatOffset);
        return convertCompletions(legacy);
    }

    public static List<LspCompletionItem> convertCompletions(List<CompletionItem> legacy) {
        return LspCompletionConverter.convert(legacy);
    }

    // -------------------------------------------------------------------------
    // Diagnostics (LSP 3.17 & Legacy)
    // -------------------------------------------------------------------------

    @Override
    public List<Diagnostic> diagnostics(DocumentDiagnosticParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        List<Problem> raw = diagnostics(doc);
        if (raw == null || raw.isEmpty()) return Collections.emptyList();

        List<Diagnostic> result = new ArrayList<>(raw.size());
        for (Problem p : raw) {
            int line = Math.max(0, p.getLine() - 1);
            int col = Math.max(0, p.getColumn() - 1);
            int len = Math.max(1, p.getLength());
            com.cocode.vcode.ide.core.lsp.model.LspRange range = new com.cocode.vcode.ide.core.lsp.model.LspRange(
                    new com.cocode.vcode.ide.core.lsp.model.LspPosition(line, col),
                    new com.cocode.vcode.ide.core.lsp.model.LspPosition(line, col + len)
            );
            DiagnosticSeverity sev = mapSeverity(p.getSeverity());
            String diagCode = p.getCode() != null ? p.getCode() : "";
            result.add(new Diagnostic(range, sev, diagCode, "json", p.getMessage()));
        }
        return result;
    }

    @Override
    public List<Problem> diagnostics(LspDocument doc) {
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            File docFile = doc.uri != null ? new File(doc.uri) : null;
            return JsonLinter.analyze(docFile, doc.text);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    private DiagnosticSeverity mapSeverity(Problem.Severity severity) {
        if (severity == null) return DiagnosticSeverity.INFORMATION;
        switch (severity) {
            case ERROR: return DiagnosticSeverity.ERROR;
            case WARNING: return DiagnosticSeverity.WARNING;
            case INFO: return DiagnosticSeverity.INFORMATION;
            default: return DiagnosticSeverity.INFORMATION;
        }
    }

    // -------------------------------------------------------------------------
    // Code Actions & Quick Fixes (LSP 3.17)
    // -------------------------------------------------------------------------

    @Override
    public List<CodeAction> codeAction(CodeActionParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        List<CodeAction> actions = new ArrayList<>();
        if (params.context != null && params.context.diagnostics != null) {
            for (Diagnostic diag : params.context.diagnostics) {
                if (token != null && token.isCancelled()) break;
                String msg = diag.message != null ? diag.message : "";

                // 1. Missing required schema properties in package.json
                if (msg.contains("Missing required property") || msg.contains("missing required property")) {
                    String propName = extractQuotedName(msg);
                    int braceIdx = doc.text.indexOf('{');
                    if (braceIdx >= 0) {
                        String insertText;
                        if ("name".equals(propName)) {
                            insertText = "\n  \"name\": \"my-package\",";
                        } else if ("version".equals(propName)) {
                            insertText = "\n  \"version\": \"1.0.0\",";
                        } else {
                            insertText = "\n  \"" + propName + "\": \"\",";
                        }
                        com.cocode.vcode.ide.core.lsp.model.LspPosition insPos = doc.toModelPosition(braceIdx + 1);
                        TextEdit edit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(insPos, insPos), insertText);
                        WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                        actions.add(new CodeAction("Add missing required property '" + propName + "'",
                                CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
                    }
                }

                // 2. Remove illegal trailing comma
                if (msg.toLowerCase().contains("trailing comma")) {
                    int startOffset = doc.toOffset(diag.range.start);
                    int endOffset = doc.toOffset(diag.range.end);
                    if (startOffset >= 0 && startOffset < doc.text.length()) {
                        int commaIdx = -1;
                        for (int k = startOffset; k <= Math.min(doc.text.length() - 1, endOffset); k++) {
                            if (doc.text.charAt(k) == ',') {
                                commaIdx = k;
                                break;
                            }
                        }
                        if (commaIdx == -1) {
                            for (int k = Math.max(0, startOffset - 2); k <= Math.min(doc.text.length() - 1, startOffset + 2); k++) {
                                if (doc.text.charAt(k) == ',') {
                                    commaIdx = k;
                                    break;
                                }
                            }
                        }
                        if (commaIdx >= 0) {
                            int delEnd = commaIdx + 1;
                            while (delEnd < doc.text.length() && (doc.text.charAt(delEnd) == ' ' || doc.text.charAt(delEnd) == '\t')) {
                                delEnd++;
                            }
                            com.cocode.vcode.ide.core.lsp.model.LspPosition pStart = doc.toModelPosition(commaIdx);
                            com.cocode.vcode.ide.core.lsp.model.LspPosition pEnd = doc.toModelPosition(delEnd);
                            TextEdit edit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(pStart, pEnd), "");
                            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                            actions.add(new CodeAction("Remove trailing comma",
                                    CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
                        }
                    }
                }

                // 3. Convert single quotes or unquoted keys to valid double quotes
                if (msg.contains("double quotes") || msg.contains("Strings must use double quotes")) {
                    int startOffset = doc.toOffset(diag.range.start);
                    int endOffset = doc.toOffset(diag.range.end);
                    if (startOffset >= 0 && endOffset > startOffset && endOffset <= doc.text.length()) {
                        String target = doc.text.substring(startOffset, endOffset);
                        if (target.startsWith("'") && target.endsWith("'") && target.length() >= 2) {
                            String inner = target.substring(1, target.length() - 1);
                            String converted = "\"" + inner.replace("\"", "\\\"") + "\"";
                            TextEdit edit = new TextEdit(diag.range, converted);
                            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                            actions.add(new CodeAction("Convert single quotes to valid double quotes",
                                    CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
                        } else if (!target.startsWith("\"")) {
                            String converted = "\"" + target + "\"";
                            TextEdit edit = new TextEdit(diag.range, converted);
                            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                            actions.add(new CodeAction("Enclose key in double quotes",
                                    CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
                        }
                    }
                }

                // 4. Remove duplicate key
                if (msg.contains("Duplicate object key")) {
                    int startOffset = doc.toOffset(diag.range.start);
                    int lineStart = doc.text.lastIndexOf('\n', startOffset);
                    lineStart = (lineStart == -1) ? 0 : lineStart + 1;
                    int lineEnd = doc.text.indexOf('\n', startOffset);
                    lineEnd = (lineEnd == -1) ? doc.text.length() : lineEnd + 1;

                    com.cocode.vcode.ide.core.lsp.model.LspPosition pStart = doc.toModelPosition(lineStart);
                    com.cocode.vcode.ide.core.lsp.model.LspPosition pEnd = doc.toModelPosition(lineEnd);
                    TextEdit edit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(pStart, pEnd), "");
                    WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                    String keyName = extractQuotedName(msg);
                    actions.add(new CodeAction("Remove duplicate key" + (!keyName.isEmpty() ? " '" + keyName + "'" : ""),
                            CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
                }
            }
        }
        return actions;
    }

    private static String extractQuotedName(String msg) {
        if (msg == null) return "";
        int q1 = msg.indexOf('\'');
        if (q1 >= 0) {
            int q2 = msg.indexOf('\'', q1 + 1);
            if (q2 > q1) return msg.substring(q1 + 1, q2);
        }
        int dq1 = msg.indexOf('"');
        if (dq1 >= 0) {
            int dq2 = msg.indexOf('"', dq1 + 1);
            if (dq2 > dq1) return msg.substring(dq1 + 1, dq2);
        }
        return "";
    }

    // -------------------------------------------------------------------------
    // Hover Documentation (LSP 3.17 & Legacy)
    // -------------------------------------------------------------------------

    @Override
    public Hover hover(HoverParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) return null;
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return null;
        if (token != null && token.isCancelled()) return null;

        int offset = doc.toOffset(params.position);
        if (offset < 0 || offset > doc.text.length()) return null;

        // Walk backward to find opening quote
        int start = offset;
        while (start > 0 && doc.text.charAt(start - 1) != '"' && doc.text.charAt(start - 1) != '\'' && doc.text.charAt(start - 1) != '\n') {
            start--;
        }
        if (start <= 0 || (doc.text.charAt(start - 1) != '"' && doc.text.charAt(start - 1) != '\'')) return null;

        // Walk forward to find closing quote
        char openQuote = doc.text.charAt(start - 1);
        int end = offset;
        while (end < doc.text.length() && doc.text.charAt(end) != openQuote && doc.text.charAt(end) != '\n') {
            end++;
        }
        if (end >= doc.text.length() || doc.text.charAt(end) != openQuote) return null;

        String key = doc.text.substring(start, end);
        File docFile = doc.uri != null ? new File(doc.uri) : null;

        // Check schema documentation first
        String md = JsonSchemaManager.getHoverDocumentation(docFile, doc.uri, key, null);
        if (md == null) {
            md = JsonDocumentationProvider.getPropertyDoc(doc.uri, key);
        }

        if (md != null) {
            com.cocode.vcode.ide.core.lsp.model.LspRange range = new com.cocode.vcode.ide.core.lsp.model.LspRange(
                    doc.toModelPosition(start - 1),
                    doc.toModelPosition(end + 1)
            );
            return new Hover(new MarkupContent("markdown", md), range);
        }
        return null;
    }

    @Override
    public HoverInfo hover(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;
        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return null;

        int start = offset;
        while (start > 0 && doc.text.charAt(start - 1) != '"' && doc.text.charAt(start - 1) != '\n') {
            start--;
        }
        if (start <= 0 || doc.text.charAt(start - 1) != '"') return null;

        int end = offset;
        while (end < doc.text.length() && doc.text.charAt(end) != '"' && doc.text.charAt(end) != '\n') {
            end++;
        }
        if (end >= doc.text.length() || doc.text.charAt(end) != '"') return null;

        String key = doc.text.substring(start, end);
        File docFile = doc.uri != null ? new File(doc.uri) : null;
        String md = JsonSchemaManager.getHoverDocumentation(docFile, doc.uri, key, null);
        if (md == null) {
            md = JsonDocumentationProvider.getPropertyDoc(doc.uri, key);
        }

        if (md != null) {
            LspRange range = new LspRange(doc.toPosition(start - 1), doc.toPosition(end + 1));
            return new HoverInfo(range, md);
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
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        File docFile = doc.uri != null ? new File(doc.uri) : null;
        boolean isJsonc = JsonLinter.isJsoncMode(docFile);
        JsonTokenStream stream = JsonLexer.tokenize(doc.text);
        JsonSyntaxTree tree = JsonParser.parse(stream, doc.text, isJsonc);
        if (tree == null) return Collections.emptyList();

        List<DocumentSymbol> symbols = new ArrayList<>();
        for (int i = 1; i < tree.nodeCount; i++) {
            if (token != null && token.isCancelled()) break;
            if (tree.nodeParent[i] == 0) {
                if (tree.nodeType[i] == JsonSyntaxTree.N_OBJECT) {
                    int ch = tree.nodeChild[i];
                    while (ch > 0 && ch < tree.nodeCount) {
                        DocumentSymbol sym = buildDocumentSymbol(tree, ch, doc);
                        if (sym != null) {
                            symbols.add(sym);
                        }
                        ch = tree.nodeSibling[ch];
                    }
                } else {
                    DocumentSymbol sym = buildDocumentSymbol(tree, i, doc);
                    if (sym != null) {
                        symbols.add(sym);
                    }
                }
            }
        }
        return symbols;
    }

    private DocumentSymbol buildDocumentSymbol(JsonSyntaxTree tree, int node, LspDocument doc) {
        if (node <= 0 || node >= tree.nodeCount) return null;
        byte type = tree.nodeType[node];
        String rawName = tree.nodeName[node];
        String name = JsonSchemaManager.cleanKey(rawName);
        SymbolKind kind = SymbolKind.PROPERTY;
        String detail = null;

        switch (type) {
            case JsonSyntaxTree.N_OBJECT:
                name = name.isEmpty() ? "{}" : name;
                kind = SymbolKind.OBJECT;
                break;
            case JsonSyntaxTree.N_ARRAY:
                name = name.isEmpty() ? "[]" : name;
                kind = SymbolKind.ARRAY;
                break;
            case JsonSyntaxTree.N_KEY:
                kind = SymbolKind.PROPERTY;
                int valNode = tree.nodeChild[node];
                if (valNode > 0 && valNode < tree.nodeCount) {
                    byte valType = tree.nodeType[valNode];
                    if (valType == JsonSyntaxTree.N_OBJECT) {
                        kind = SymbolKind.OBJECT;
                    } else if (valType == JsonSyntaxTree.N_ARRAY) {
                        kind = SymbolKind.ARRAY;
                    } else {
                        detail = tree.nodeName[valNode];
                        if (valType == JsonSyntaxTree.N_VALUE_STRING) kind = SymbolKind.STRING;
                        else if (valType == JsonSyntaxTree.N_VALUE_NUMBER) kind = SymbolKind.NUMBER;
                        else if (valType == JsonSyntaxTree.N_VALUE_BOOL) kind = SymbolKind.BOOLEAN;
                        else if (valType == JsonSyntaxTree.N_VALUE_NULL) kind = SymbolKind.NULL;
                    }
                }
                break;
            case JsonSyntaxTree.N_VALUE_STRING:
                kind = SymbolKind.STRING;
                detail = name;
                break;
            case JsonSyntaxTree.N_VALUE_NUMBER:
                kind = SymbolKind.NUMBER;
                detail = name;
                break;
            case JsonSyntaxTree.N_VALUE_BOOL:
                kind = SymbolKind.BOOLEAN;
                detail = name;
                break;
            case JsonSyntaxTree.N_VALUE_NULL:
                kind = SymbolKind.NULL;
                detail = "null";
                break;
            default:
                return null;
        }

        if (name == null || name.isEmpty()) {
            name = "element";
        }

        int start = Math.max(0, Math.min(tree.nodeStart[node], doc.text.length()));
        int end = Math.max(start, Math.min(tree.nodeEnd[node], doc.text.length()));
        com.cocode.vcode.ide.core.lsp.model.LspRange range = new com.cocode.vcode.ide.core.lsp.model.LspRange(
                doc.toModelPosition(start),
                doc.toModelPosition(end)
        );
        com.cocode.vcode.ide.core.lsp.model.LspRange selRange = new com.cocode.vcode.ide.core.lsp.model.LspRange(
                doc.toModelPosition(start),
                doc.toModelPosition(Math.min(start + name.length(), end))
        );

        List<DocumentSymbol> children = new ArrayList<>();
        if (type == JsonSyntaxTree.N_KEY) {
            int valNode = tree.nodeChild[node];
            if (valNode > 0 && valNode < tree.nodeCount) {
                int ch = tree.nodeChild[valNode];
                while (ch > 0 && ch < tree.nodeCount) {
                    DocumentSymbol childSym = buildDocumentSymbol(tree, ch, doc);
                    if (childSym != null) {
                        children.add(childSym);
                    }
                    ch = tree.nodeSibling[ch];
                }
            }
        } else {
            int ch = tree.nodeChild[node];
            while (ch > 0 && ch < tree.nodeCount) {
                DocumentSymbol childSym = buildDocumentSymbol(tree, ch, doc);
                if (childSym != null) {
                    children.add(childSym);
                }
                ch = tree.nodeSibling[ch];
            }
        }

        return new DocumentSymbol(name, detail, kind, null, range, selRange, children);
    }

    // -------------------------------------------------------------------------
    // Formatting (LSP 3.17)
    // -------------------------------------------------------------------------

    @Override
    public List<TextEdit> formatting(DocumentFormattingParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null || doc.text.isEmpty()) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        String formatted = new JsonFormatter().format(doc.text);
        if (formatted == null || formatted.equals(doc.text)) return Collections.emptyList();

        com.cocode.vcode.ide.core.lsp.model.LspPosition start = new com.cocode.vcode.ide.core.lsp.model.LspPosition(0, 0);
        com.cocode.vcode.ide.core.lsp.model.LspPosition end = doc.toModelPosition(doc.text.length());
        return Collections.singletonList(new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(start, end), formatted));
    }

    // -------------------------------------------------------------------------
    // Go to Definition (LSP 3.17 & Legacy)
    // -------------------------------------------------------------------------

    @Override
    public List<LspLocation> definition(DefinitionParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        LspLocation loc = definition(doc, new LspPosition(params.position.line, params.position.character));
        return loc != null ? Collections.singletonList(loc) : Collections.emptyList();
    }

    @Override
    public LspLocation definition(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;

        String value = extractStringValueAtCursor(doc.text, doc.toOffset(pos));
        if (value == null || value.isEmpty()) return null;

        // Path-based definition
        if (!value.startsWith("./") && !value.startsWith("../") && !value.startsWith("/")) {
            return null;
        }

        String projectRoot = ProjectIndex.getInstance() != null ? ProjectIndex.getInstance().getProjectRoot() : null;
        if (doc.uri == null) return null;

        java.io.File base = new java.io.File(doc.uri).getParentFile();
        if (base == null) return null;
        java.io.File target = new java.io.File(base, value);

        if (target.exists() && target.isFile()) {
            return new LspLocation(target.getAbsolutePath(), new LspRange(0, 0, 0, 0));
        }
        for (String ext : new String[]{".js", ".ts", ".json", ".html", ".css", ".d.ts"}) {
            java.io.File withExt = new java.io.File(target.getAbsolutePath() + ext);
            if (withExt.exists()) {
                return new LspLocation(withExt.getAbsolutePath(), new LspRange(0, 0, 0, 0));
            }
        }
        return null;
    }

    private static String extractStringValueAtCursor(String text, int offset) {
        if (text == null || offset < 0 || offset >= text.length()) return null;
        int start = offset - 1;
        while (start >= 0 && text.charAt(start) != '"' && text.charAt(start) != '\'' && text.charAt(start) != '\n') {
            start--;
        }
        if (start < 0 || (text.charAt(start) != '"' && text.charAt(start) != '\'')) return null;

        char quote = text.charAt(start);
        int end = offset;
        while (end < text.length() && text.charAt(end) != quote && text.charAt(end) != '\n') {
            end++;
        }
        if (end >= text.length() || text.charAt(end) != quote) return null;
        return text.substring(start + 1, end);
    }

    @Override
    public List<LspLocation> references(LspDocument doc, LspPosition pos) {
        return Collections.emptyList();
    }

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos) {
        return null;
    }

    @Override
    public List<LspLocation> rename(LspDocument doc, LspPosition pos) {
        return Collections.emptyList();
    }
}
