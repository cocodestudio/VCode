package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex;
import com.cocode.vcode.ide.core.autocomplete.types.TypeDefinitionIndexer;
import com.cocode.vcode.ide.core.diagnostic.DiagnosticEngine;
import com.cocode.vcode.ide.core.diagnostic.ProblemCode;
import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.editor.indent.BracketMatcher;
import com.cocode.vcode.ide.core.language.js.DocCommentParser;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsParser;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.language.ts.TsAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.ts.TsFormatter;
import com.cocode.vcode.ide.core.language.ts.TsLinter;
import com.cocode.vcode.ide.core.language.ts.TsParser;
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
import com.cocode.vcode.ide.core.lsp.ModuleResolver;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.lsp.ServerCapabilities;
import com.cocode.vcode.ide.core.lsp.SymbolExtractor;
import com.cocode.vcode.ide.core.lsp.index.WorkspaceIndexService;
import com.cocode.vcode.ide.core.lsp.model.CodeAction;
import com.cocode.vcode.ide.core.lsp.model.CodeActionKind;
import com.cocode.vcode.ide.core.lsp.model.CodeActionParams;
import com.cocode.vcode.ide.core.lsp.model.CompletionItem;
import com.cocode.vcode.ide.core.lsp.model.CompletionItemKind;
import com.cocode.vcode.ide.core.lsp.model.CompletionParams;
import com.cocode.vcode.ide.core.lsp.model.DefinitionParams;
import com.cocode.vcode.ide.core.lsp.model.Diagnostic;
import com.cocode.vcode.ide.core.lsp.model.DiagnosticSeverity;
import com.cocode.vcode.ide.core.lsp.model.DocumentDiagnosticParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentFormattingParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentHighlight;
import com.cocode.vcode.ide.core.lsp.model.DocumentHighlightParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentSymbol;
import com.cocode.vcode.ide.core.lsp.model.DocumentSymbolParams;
import com.cocode.vcode.ide.core.lsp.model.FoldingRange;
import com.cocode.vcode.ide.core.lsp.model.FoldingRangeParams;
import com.cocode.vcode.ide.core.lsp.model.Hover;
import com.cocode.vcode.ide.core.lsp.model.HoverParams;
import com.cocode.vcode.ide.core.lsp.model.InitializeParams;
import com.cocode.vcode.ide.core.lsp.model.MarkupContent;
import com.cocode.vcode.ide.core.lsp.model.ParameterInformation;
import com.cocode.vcode.ide.core.lsp.model.PrepareRenameParams;
import com.cocode.vcode.ide.core.lsp.model.ReferenceParams;
import com.cocode.vcode.ide.core.lsp.model.RenameParams;
import com.cocode.vcode.ide.core.lsp.model.SemanticTokens;
import com.cocode.vcode.ide.core.lsp.model.SemanticTokensParams;
import com.cocode.vcode.ide.core.lsp.model.SignatureHelp;
import com.cocode.vcode.ide.core.lsp.model.SignatureHelpParams;
import com.cocode.vcode.ide.core.lsp.model.SignatureInformation;
import com.cocode.vcode.ide.core.lsp.model.SymbolKind;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentContentChangeEvent;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentIdentifier;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentItem;
import com.cocode.vcode.ide.core.lsp.model.TextEdit;
import com.cocode.vcode.ide.core.lsp.model.TypeDefinitionParams;
import com.cocode.vcode.ide.core.lsp.model.VersionedTextDocumentIdentifier;
import com.cocode.vcode.ide.core.lsp.model.WorkspaceEdit;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-process Language Server for TypeScript and TSX files implementing LSP 3.17 capabilities.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Document Synchronization</b>: didOpen, didChange, didSave, didClose with AST indexing.</li>
 *   <li><b>Dual Navigation</b>: definition() jumps to value/constructor while typeDefinition() jumps directly to interfaces and type aliases.</li>
 *   <li><b>TypeScript Diagnostics</b>: Eliminates false positives, enforces interface implementation and required properties, supports &#64;ts-ignore suppressions.</li>
 *   <li><b>TypeScript Code Actions</b>: Generates interface method stubs (ts-implement-interface) and adds missing object properties.</li>
 *   <li><b>Declarations & Package Types</b>: Integrated .d.ts resolution from node_modules for rich hover and signature help.</li>
 *   <li><b>Document Symbols & Outline</b>: Hierarchical DOM/symbol tree covering interfaces, enums, classes, and types.</li>
 *   <li><b>Semantic Tokens</b>: Rich token encoding distinguishing types, interfaces, enums, properties, and parameters.</li>
 *   <li><b>Formatting</b>: Full-document formatting with type annotation spacing and union pipe standardization.</li>
 * </ul>
 */
public final class TsLspServer implements LspServer {

    private static final ServerCapabilities CAPABILITIES = new ServerCapabilities.Builder()
            .setHoverProvider(true)
            .setCompletionProvider(true)
            .setDefinitionProvider(true)
            .setTypeDefinitionProvider(true)
            .setReferencesProvider(true)
            .setDocumentHighlightProvider(true)
            .setDocumentSymbolProvider(true)
            .setCodeActionProvider(true)
            .setFormattingProvider(true)
            .setRangeFormattingProvider(false)
            .setFoldingRangeProvider(true)
            .setSignatureHelpProvider(true)
            .setSemanticTokensProvider(true)
            .setRenameProvider(true)
            .build();

    private final TsAutoCompleteEngine autoCompleteEngine;
    private final ConcurrentHashMap<String, LspDocument> openDocs = new ConcurrentHashMap<>();
    private final JsLspServer jsLspServer;
    private volatile boolean ready = false;
    private ProjectIndex projectIndex;

    public TsLspServer(Context context) {
        this.autoCompleteEngine = new TsAutoCompleteEngine(context);
        this.jsLspServer = new JsLspServer(context);
    }

    public TsLspServer() {
        this(null);
    }

    // -------------------------------------------------------------------------
    // Lifecycle & Capabilities
    // -------------------------------------------------------------------------

    @Override
    public void initialize(ProjectIndex index) {
        this.projectIndex = index;
        jsLspServer.initialize(index);
        ready = true;
    }

    @Override
    public void initialize(InitializeParams params) {
        ready = true;
        jsLspServer.initialize(params);
    }

    @Override
    public void shutdown() {
        openDocs.clear();
        jsLspServer.shutdown();
        ready = false;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String getLanguageId() {
        return "typescript";
    }

    @Override
    public String getTriggerCharacters() {
        return ".'\"`/@({*:<";
    }

    @Override
    public ServerCapabilities getCapabilities() {
        return CAPABILITIES;
    }

    // -------------------------------------------------------------------------
    // Document Synchronization
    // -------------------------------------------------------------------------

    @Override
    public void didOpen(TextDocumentItem document) {
        if (document == null || document.uri == null) return;
        LspDocument doc = new LspDocument(document.uri, document.text, "typescript", document.version);
        openDocs.put(document.uri, doc);
        WorkspaceIndexService wis = WorkspaceIndexService.getInstance();
        if (wis != null) {
            wis.updateDocument(doc);
        }
    }

    @Override
    public void didChange(VersionedTextDocumentIdentifier document, List<TextDocumentContentChangeEvent> changes) {
        if (document == null || document.uri == null || changes == null) return;
        LspDocument existing = getDocument(document.uri);
        String currentText = existing != null ? existing.text : "";

        for (TextDocumentContentChangeEvent change : changes) {
            if (change.range != null && existing != null) {
                int startOff = existing.toOffset(change.range.start);
                int endOff = existing.toOffset(change.range.end);
                if (startOff >= 0 && endOff >= startOff && endOff <= currentText.length()) {
                    currentText = currentText.substring(0, startOff) + (change.text != null ? change.text : "") + currentText.substring(endOff);
                } else {
                    currentText = change.text;
                }
            } else {
                currentText = change.text;
            }
        }

        LspDocument updated = new LspDocument(document.uri, currentText, "typescript", document.version);
        openDocs.put(document.uri, updated);
        WorkspaceIndexService wis = WorkspaceIndexService.getInstance();
        if (wis != null) {
            wis.updateDocument(updated);
        }
    }

    @Override
    public void didSave(TextDocumentIdentifier document) {
        if (document == null || document.uri == null) return;
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
        if (document == null || document.uri == null) return;
        openDocs.remove(document.uri);
    }

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
            File f = new File(uri.startsWith("file://") ? uri.substring(7) : uri);
            if (f.exists() && f.isFile()) {
                String text = FileUtils.readFile(f);
                if (text != null) {
                    return new LspDocument(uri, text, "typescript", 1);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Completions
    // -------------------------------------------------------------------------

    @Override
    public List<CompletionItem> completion(CompletionParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        int offset = doc.toOffset(params.position);
        autoCompleteEngine.setCurrentFile(new File(doc.uri));
        List<com.cocode.vcode.ide.core.model.CompletionItem> rawItems = autoCompleteEngine.getSuggestions(doc.text, offset);
        if (rawItems == null || rawItems.isEmpty()) return Collections.emptyList();

        List<CompletionItem> result = new ArrayList<>(rawItems.size());

        for (com.cocode.vcode.ide.core.model.CompletionItem ri : rawItems) {
            if (token != null && token.isCancelled()) break;
            com.cocode.vcode.ide.core.lsp.model.CompletionItem.Builder b =
                    com.cocode.vcode.ide.core.lsp.model.CompletionItem.builder(ri.getLabel())
                            .setInsertText(ri.getInsertText() != null ? ri.getInsertText() : ri.getLabel())
                            .setDetail(ri.getDetail())
                            .setKind(mapCompletionKind(ri.getType()));
            if (ri.getReplaceLength() >= 0) {
                int startOff = Math.max(0, offset - ri.getReplaceLength());
                b.setTextEdit(new TextEdit(
                        new com.cocode.vcode.ide.core.lsp.model.LspRange(doc.toModelPosition(startOff), doc.toModelPosition(offset)),
                        ri.getEffectiveInsertText()
                ));
            }
            result.add(b.build());
        }

        return result;
    }

    @Override
    public List<LspCompletionItem> completion(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null) return Collections.emptyList();
        int offset = doc.toOffset(pos);
        if (offset < 0) offset = doc.text.length();

        autoCompleteEngine.setCurrentFile(new File(doc.uri));
        List<com.cocode.vcode.ide.core.model.CompletionItem> suggestions = autoCompleteEngine.getSuggestions(doc.text, offset);
        return suggestions != null ? LspCompletionConverter.convert(suggestions) : Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Diagnostics
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
            Diagnostic d = new Diagnostic(range, sev, diagCode, "ts", p.getMessage());
            result.add(d);
        }
        return result;
    }

    @Override
    public List<Problem> diagnostics(LspDocument doc) {
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) return Collections.emptyList();
        try {
            File file = new File(doc.uri);
            List<Problem> problems = new ArrayList<>(BracketMatcher.findMismatches(file, doc.text));
            List<Problem> tsProblems = TsLinter.analyze(file, doc.text, ProjectIndex.getInstance());
            if (tsProblems != null) problems.addAll(tsProblems);
            return DiagnosticEngine.deduplicateAndSort(file, problems);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Navigation: Definition (Values) vs TypeDefinition (Interfaces & Types)
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
        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return null;

        TokenStream tokens = JsLexer.tokenize(doc.text);
        JsSyntaxTree tree = TsParser.parseFull(doc.text, tokens);

        if (tree != null) {
            for (int i = 1; i < tree.nodeCount; i++) {
                if (tree.nodeType[i] == JsSyntaxTree.N_IMPORT) {
                    if (offset >= tree.nodeStart[i] && offset <= tree.nodeEnd[i]) {
                        String importPath = tree.nodeName[i];
                        if (importPath != null && !importPath.isEmpty()) {
                            LspLocation resolved = ModuleResolver.resolveModulePath(doc.uri, importPath);
                            if (resolved != null) return resolved;
                        }
                    }
                }
            }
        }

        String word = extractWord(doc.text, offset);
        if (word.isEmpty()) return null;

        // Check local constructor or function first
        if (tree != null) {
            for (int i = 1; i < tree.nodeCount; i++) {
                int t = tree.nodeType[i];
                if ((t == JsSyntaxTree.N_CLASS_DECL || t == JsSyntaxTree.N_FUNC_DECL || t == JsSyntaxTree.N_VAR_DECL) && word.equals(tree.nodeName[i])) {
                    LspPosition p = doc.toPosition(tree.nodeStart[i]);
                    return new LspLocation(doc.uri, new com.cocode.vcode.ide.core.lsp.model.LspRange(
                            new com.cocode.vcode.ide.core.lsp.model.LspPosition(p.line, p.character),
                            new com.cocode.vcode.ide.core.lsp.model.LspPosition(p.line, p.character + word.length())
                    ));
                }
            }
        }

        List<LspLocation> defs = ProjectIndex.getInstance().findDefinitions(word);
        return !defs.isEmpty() ? defs.get(0) : null;
    }

    @Override
    public List<LspLocation> typeDefinition(TypeDefinitionParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        int offset = doc.toOffset(params.position);
        if (offset < 0 || offset > doc.text.length()) return Collections.emptyList();

        JsSyntaxTree tree = TsParser.parseFull(doc.text);
        String targetType = null;

        // If cursor is on a variable, parameter, or property: inspect declared type annotation
        if (tree != null) {
            for (int i = 1; i < tree.nodeCount; i++) {
                if (offset >= tree.nodeStart[i] && offset <= tree.nodeEnd[i]) {
                    int t = tree.nodeType[i];
                    if (t == JsSyntaxTree.N_VAR_DECL || t == JsSyntaxTree.N_PARAM || t == JsSyntaxTree.N_PROPERTY) {
                        String ann = tree.nodeTypeAnn[i];
                        if (ann != null && !ann.isEmpty()) {
                            targetType = extractBaseTypeName(ann);
                            break;
                        }
                    }
                }
            }
        }

        // Fallback: word under cursor
        if (targetType == null || targetType.isEmpty()) {
            targetType = extractWord(doc.text, offset);
        }

        if (targetType == null || targetType.isEmpty()) return Collections.emptyList();

        // 1. Look in active file for interface, type alias, enum, or class
        if (tree != null) {
            int node = TsParser.findInterfaceOrType(tree, targetType);
            if (node > 0) {
                LspPosition p = doc.toPosition(tree.nodeStart[node]);
                com.cocode.vcode.ide.core.lsp.model.LspPosition mp = new com.cocode.vcode.ide.core.lsp.model.LspPosition(p.line, p.character);
                return Collections.singletonList(new LspLocation(doc.uri, new com.cocode.vcode.ide.core.lsp.model.LspRange(mp, new com.cocode.vcode.ide.core.lsp.model.LspPosition(p.line, p.character + targetType.length()))));
            }
        }

        // 2. Search project index prioritizing .ts/.d.ts definitions
        List<LspLocation> defs = ProjectIndex.getInstance().findDefinitions(targetType);
        if (!defs.isEmpty()) {
            for (LspLocation loc : defs) {
                if (loc.uri != null && (loc.uri.endsWith(".d.ts") || loc.uri.endsWith(".ts") || loc.uri.endsWith(".tsx"))) {
                    return Collections.singletonList(loc);
                }
            }
            return Collections.singletonList(defs.get(0));
        }

        // 3. Search package .d.ts declarations from node_modules
        String rootPath = ProjectIndex.getInstance().getProjectRoot();
        File projectRoot = rootPath != null ? new File(rootPath) : null;
        if (projectRoot != null) {
            File dts = TypeDefinitionIndexer.resolveTypeDefinitionFile(projectRoot, targetType);
            if (dts != null && dts.exists()) {
                return Collections.singletonList(new LspLocation("file://" + dts.getAbsolutePath(), 0, 0, 0, 0));
            }
        }

        return Collections.emptyList();
    }

    private static String extractBaseTypeName(String rawAnn) {
        if (rawAnn == null) return "";
        String s = rawAnn.trim();
        int angle = s.indexOf('<');
        if (angle >= 0) s = s.substring(0, angle).trim();
        int arr = s.indexOf('[');
        if (arr >= 0) s = s.substring(0, arr).trim();
        return s;
    }

    // -------------------------------------------------------------------------
    // Code Actions
    // -------------------------------------------------------------------------

    @Override
    public List<CodeAction> codeAction(CodeActionParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        List<CodeAction> actions = new ArrayList<>();
        JsSyntaxTree tree = TsParser.parseFull(doc.text);

        if (params.context != null && params.context.diagnostics != null) {
            for (Diagnostic diag : params.context.diagnostics) {
                if (token != null && token.isCancelled()) break;
                String code = diag.code != null ? diag.code : "";
                String msg = diag.message != null ? diag.message : "";

                // 1. ts-implement-interface: Generate missing interface method stubs
                if (ProblemCode.TS_INTERFACE_NOT_IMPLEMENTED.name().equals(code) || msg.contains("incorrectly implements interface")) {
                    CodeAction ca = createImplementInterfaceAction(doc, tree, diag);
                    if (ca != null) actions.add(ca);
                }

                // 2. ts-add-missing-properties: Add missing interface properties to object literal
                if (ProblemCode.TS_MISSING_PROPERTIES.name().equals(code) || msg.contains("missing required property")) {
                    CodeAction ca = createAddMissingPropertiesAction(doc, tree, diag);
                    if (ca != null) actions.add(ca);
                }

                // 3. ts-replace-any: Replace 'any' / 'as any' with 'unknown' or 'Record<string, unknown>'
                if (ProblemCode.TS_EXPLICIT_ANY.name().equals(code) || msg.contains("Explicit 'any'") || msg.contains("Unexpected any") || msg.contains("'as any'")) {
                    boolean isAsAny = msg.contains("'as any'");
                    String repl1 = isAsAny ? "as unknown" : "unknown";
                    String repl2 = isAsAny ? "as Record<string, unknown>" : "Record<string, unknown>";
                    String title1 = isAsAny ? "Replace 'as any' with 'as unknown'" : "Replace 'any' with 'unknown'";
                    String title2 = isAsAny ? "Replace 'as any' with 'as Record<string, unknown>'" : "Replace 'any' with 'Record<string, unknown>'";

                    TextEdit edit1 = new TextEdit(diag.range, repl1);
                    WorkspaceEdit wsEdit1 = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit1)));
                    actions.add(new CodeAction(title1, CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit1, null, null));

                    TextEdit edit2 = new TextEdit(diag.range, repl2);
                    WorkspaceEdit wsEdit2 = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit2)));
                    actions.add(new CodeAction(title2, CodeActionKind.QUICKFIX, Collections.singletonList(diag), false, wsEdit2, null, null));
                }

                // 4. TS_REDUNDANT_TYPE
                if (ProblemCode.TS_REDUNDANT_TYPE.name().equals(code) || msg.contains("Redundant type annotation")) {
                    TextEdit edit = new TextEdit(diag.range, "");
                    WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                    actions.add(new CodeAction("Remove redundant type annotation", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
                }

                // 5. TS_UNION_UNDEFINED
                if (ProblemCode.TS_UNION_UNDEFINED.name().equals(code) || msg.contains("optional instead of union with undefined") || msg.contains("union with undefined")) {
                    List<TextEdit> edits = new ArrayList<>();
                    // Insert ? immediately after parameter name (e.g. arg?)
                    edits.add(new TextEdit(new LspRange(diag.range.end, diag.range.end), "?"));

                    // Delete "| undefined" from the type annotation
                    int pEndOff = doc.toOffset(diag.range.end);
                    int colonIdx = doc.text.indexOf(':', pEndOff);
                    if (colonIdx >= 0) {
                        int pipeIdx = doc.text.indexOf('|', colonIdx);
                        if (pipeIdx >= 0) {
                            int undefIdx = doc.text.indexOf("undefined", pipeIdx);
                            if (undefIdx >= 0) {
                                int delStart = pipeIdx;
                                while (delStart > colonIdx + 1 && Character.isWhitespace(doc.text.charAt(delStart - 1))) {
                                    delStart--;
                                }
                                int delEnd = undefIdx + "undefined".length();
                                edits.add(new TextEdit(new LspRange(doc.toModelPosition(delStart), doc.toModelPosition(delEnd)), ""));
                            }
                        }
                    }
                    WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, edits));
                    actions.add(new CodeAction("Convert parameter to optional '?:'", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
                }

                // 6. TS_READONLY_ARRAY
                if (ProblemCode.TS_READONLY_ARRAY.name().equals(code) || msg.contains("Array<T> should be readonly") || msg.contains("should be readonly")) {
                    // Insert "readonly " before the property name instead of overwriting it
                    TextEdit edit = new TextEdit(new LspRange(diag.range.start, diag.range.start), "readonly ");
                    WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                    actions.add(new CodeAction("Convert to 'readonly T[]'", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
                }

                // 7. TS_NON_NULL_ASSERTION (2 choices)
                if (ProblemCode.TS_NON_NULL_ASSERTION.name().equals(code) || msg.contains("Forbidden non-null assertion") || msg.contains("non-null assertion")) {
                    TextEdit editOpt = new TextEdit(diag.range, "?.");
                    WorkspaceEdit wsEditOpt = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(editOpt)));
                    actions.add(new CodeAction("Replace '!' with optional chaining '?.'", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEditOpt, null, null));

                    TextEdit editRem = new TextEdit(diag.range, "");
                    WorkspaceEdit wsEditRem = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(editRem)));
                    actions.add(new CodeAction("Remove non-null assertion '!'", CodeActionKind.QUICKFIX, Collections.singletonList(diag), false, wsEditRem, null, null));
                }

                // 8. TS_MISSING_RETURN_TYPE
                if (ProblemCode.TS_MISSING_RETURN_TYPE.name().equals(code) || msg.contains("Missing return type")) {
                    // Insert ": void" right after closing parenthesis of parameter list
                    int fnEndOff = doc.toOffset(diag.range.end);
                    int openParen = doc.text.indexOf('(', fnEndOff);
                    int closeParen = (openParen >= 0) ? doc.text.indexOf(')', openParen) : -1;
                    com.cocode.vcode.ide.core.lsp.model.LspPosition insPos = (closeParen >= 0) ? doc.toModelPosition(closeParen + 1) : diag.range.end;
                    TextEdit edit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(insPos, insPos), ": void");
                    WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                    actions.add(new CodeAction("Add explicit return type annotation (': void')", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
                }
            }
        }

        return actions;
    }

    private CodeAction createImplementInterfaceAction(LspDocument doc, JsSyntaxTree tree, Diagnostic diag) {
        if (doc == null || tree == null || diag == null) return null;
        String msg = diag.message != null ? diag.message : "";

        // Format: "Class 'Foo' incorrectly implements interface 'IFoo'. Missing method: 'run'"
        String className = null;
        String ifaceName = null;
        String missingMethod = null;

        Pattern p = Pattern.compile("Class '([^']+)' incorrectly implements interface '([^']+)'. Missing method: '([^']+)'");
        Matcher m = p.matcher(msg);
        if (m.find()) {
            className = m.group(1);
            ifaceName = m.group(2);
            missingMethod = m.group(3);
        }

        if (className == null || missingMethod == null) return null;

        // Locate class node in AST
        int classNode = -1;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_CLASS_DECL && className.equals(tree.nodeName[i])) {
                classNode = i;
                break;
            }
        }
        if (classNode <= 0) return null;

        // Find closing brace of class
        int classEnd = tree.nodeEnd[classNode];
        int closeBraceIdx = -1;
        for (int idx = classEnd - 1; idx >= tree.nodeStart[classNode]; idx--) {
            if (doc.text.charAt(idx) == '}') {
                closeBraceIdx = idx;
                break;
            }
        }
        if (closeBraceIdx == -1) return null;

        String stub = "\n    " + missingMethod + "(): void {\n        throw new Error(\"Method not implemented.\");\n    }\n";
        com.cocode.vcode.ide.core.lsp.model.LspPosition insertPos = doc.toModelPosition(closeBraceIdx);
        TextEdit edit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(insertPos, insertPos), stub);
        WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));

        return new CodeAction("Implement missing method '" + missingMethod + "()' of interface '" + ifaceName + "'",
                CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null);
    }

    private CodeAction createAddMissingPropertiesAction(LspDocument doc, JsSyntaxTree tree, Diagnostic diag) {
        if (doc == null || diag == null) return null;
        String msg = diag.message != null ? diag.message : "";

        // Format: "Type '{ ... }' is missing required property 'name' from interface 'IUser'"
        Pattern p = Pattern.compile("missing required property '([^']+)'");
        Matcher m = p.matcher(msg);
        if (!m.find()) return null;
        String propName = m.group(1);

        int offset = doc.toOffset(diag.range.start);
        int braceIdx = doc.text.indexOf('{', offset);
        if (braceIdx == -1) return null;

        String stub = "\n    " + propName + ": \"\",";
        com.cocode.vcode.ide.core.lsp.model.LspPosition insertPos = doc.toModelPosition(braceIdx + 1);
        TextEdit edit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(insertPos, insertPos), stub);
        WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));

        return new CodeAction("Add missing required property '" + propName + "'",
                CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null);
    }

    // -------------------------------------------------------------------------
    // Document Symbols & Outline
    // -------------------------------------------------------------------------

    @Override
    public List<DocumentSymbol> documentSymbol(DocumentSymbolParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null || doc.text.isEmpty()) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        JsSyntaxTree tree = TsParser.parseFull(doc.text);
        if (tree == null) return Collections.emptyList();

        List<DocumentSymbol> symbols = new ArrayList<>();
        for (int i = 1; i < tree.nodeCount; i++) {
            if (token != null && token.isCancelled()) break;
            if (tree.nodeParent[i] == 0) {
                DocumentSymbol sym = buildDocumentSymbol(tree, i, doc);
                if (sym != null) {
                    symbols.add(sym);
                }
            }
        }
        return symbols;
    }

    private DocumentSymbol buildDocumentSymbol(JsSyntaxTree tree, int node, LspDocument doc) {
        if (node <= 0 || node >= tree.nodeCount) return null;
        int type = tree.nodeType[node];
        String name = tree.nodeName[node];
        SymbolKind kind = null;
        String detail = null;

        switch (type) {
            case JsSyntaxTree.N_CLASS_DECL:
                kind = SymbolKind.CLASS;
                detail = tree.nodeTypeAnn != null ? tree.nodeTypeAnn[node] : null;
                break;
            case JsSyntaxTree.N_INTERFACE:
                kind = SymbolKind.INTERFACE;
                detail = tree.nodeTypeAnn != null ? tree.nodeTypeAnn[node] : null;
                break;
            case JsSyntaxTree.N_TYPE_ALIAS:
                kind = SymbolKind.TYPE_PARAMETER;
                detail = tree.nodeTypeAnn != null ? tree.nodeTypeAnn[node] : null;
                break;
            case JsSyntaxTree.N_ENUM:
                kind = SymbolKind.ENUM;
                break;
            case JsSyntaxTree.N_FUNC_DECL:
            case JsSyntaxTree.N_ARROW_FUNC:
                kind = SymbolKind.FUNCTION;
                detail = "()";
                break;
            case JsSyntaxTree.N_METHOD:
                kind = "constructor".equals(name) ? SymbolKind.CONSTRUCTOR : SymbolKind.METHOD;
                break;
            case JsSyntaxTree.N_PROPERTY:
                kind = SymbolKind.PROPERTY;
                detail = tree.nodeTypeAnn != null ? tree.nodeTypeAnn[node] : null;
                break;
            case JsSyntaxTree.N_VAR_DECL:
                kind = SymbolKind.VARIABLE;
                detail = tree.nodeTypeAnn != null ? tree.nodeTypeAnn[node] : null;
                break;
            case JsSyntaxTree.N_IMPORT:
                kind = SymbolKind.MODULE;
                break;
            case JsSyntaxTree.N_EXPORT:
                int child = tree.nodeChild[node];
                if (child > 0) return buildDocumentSymbol(tree, child, doc);
                return null;
            default:
                return null;
        }

        if (name == null || name.isEmpty()) {
            if (type == JsSyntaxTree.N_IMPORT) name = "import";
            else if (type == JsSyntaxTree.N_ARROW_FUNC) name = "anonymous";
            else return null;
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
        int ch = tree.nodeChild[node];
        while (ch > 0 && ch < tree.nodeCount) {
            DocumentSymbol childSym = buildDocumentSymbol(tree, ch, doc);
            if (childSym != null) {
                children.add(childSym);
            }
            ch = tree.nodeSibling[ch];
        }

        return new DocumentSymbol(name, detail, kind, null, range, selRange, children);
    }

    // -------------------------------------------------------------------------
    // Semantic Tokens
    // -------------------------------------------------------------------------

    private static class RawToken implements Comparable<RawToken> {
        final int line;
        final int character;
        final int length;
        final int typeIndex;
        final int modifierMask;

        RawToken(int line, int character, int length, int typeIndex, int modifierMask) {
            this.line = line;
            this.character = character;
            this.length = length;
            this.typeIndex = typeIndex;
            this.modifierMask = modifierMask;
        }

        @Override
        public int compareTo(RawToken o) {
            if (this.line != o.line) return Integer.compare(this.line, o.line);
            return Integer.compare(this.character, o.character);
        }
    }

    @Override
    public SemanticTokens semanticTokens(SemanticTokensParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return null;
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null || doc.text.isEmpty()) return new SemanticTokens(new int[0]);
        if (token != null && token.isCancelled()) return null;

        TokenStream stream = JsLexer.tokenize(doc.text);
        JsSyntaxTree tree = TsParser.parseFull(doc.text, stream);

        List<RawToken> rawTokens = new ArrayList<>();
        int len = doc.text.length();

        for (int i = 0; i < stream.length && i < len; ) {
            if (token != null && token.isCancelled()) return null;
            byte t = stream.types[i];

            if (t == TokenStream.TK_WHITESPACE || t == TokenStream.TK_NONE || t == TokenStream.TK_PUNCT) {
                i++;
                continue;
            }

            int start = stream.tokenStart[i];
            int end = start;
            while (end < stream.length && stream.types[end] == t && stream.tokenStart[end] == start) {
                end++;
            }
            int tLen = end - start;
            if (tLen <= 0) {
                i++;
                continue;
            }

            int typeIdx = -1;
            int modMask = 0;

            if (t == TokenStream.TK_KEYWORD) {
                typeIdx = 14; // keyword
            } else if (t == TokenStream.TK_STRING || t == TokenStream.TK_TEMPLATE) {
                typeIdx = 17; // string
            } else if (t == TokenStream.TK_NUMBER) {
                typeIdx = 18; // number
            } else if (t == TokenStream.TK_COMMENT) {
                typeIdx = 16; // comment
            } else if (t == TokenStream.TK_OPERATOR) {
                typeIdx = 20; // operator
            } else if (t == TokenStream.TK_IDENTIFIER) {
                typeIdx = 7; // variable
                if (tree != null) {
                    for (int k = 1; k < tree.nodeCount; k++) {
                        if (tree.nodeStart[k] == start) {
                            int nType = tree.nodeType[k];
                            if (nType == JsSyntaxTree.N_CLASS_DECL) {
                                typeIdx = 1; // class
                                modMask = 1;
                            } else if (nType == JsSyntaxTree.N_INTERFACE) {
                                typeIdx = 3; // interface
                                modMask = 1;
                            } else if (nType == JsSyntaxTree.N_TYPE_ALIAS) {
                                typeIdx = 0; // type
                                modMask = 1;
                            } else if (nType == JsSyntaxTree.N_ENUM) {
                                typeIdx = 2; // enum
                                modMask = 1;
                            } else if (nType == JsSyntaxTree.N_FUNC_DECL || nType == JsSyntaxTree.N_ARROW_FUNC) {
                                typeIdx = 11; // function
                                modMask = 1;
                            } else if (nType == JsSyntaxTree.N_METHOD) {
                                typeIdx = 12; // method
                                modMask = 1;
                            } else if (nType == JsSyntaxTree.N_PARAM) {
                                typeIdx = 6; // parameter
                                modMask = 1;
                            } else if (nType == JsSyntaxTree.N_PROPERTY) {
                                typeIdx = 8; // property
                            } else if (nType == JsSyntaxTree.N_TYPE_REF) {
                                typeIdx = 0; // type reference
                            }
                            break;
                        }
                    }
                }
            }

            if (typeIdx >= 0) {
                LspPosition p = doc.toPosition(start);
                rawTokens.add(new RawToken(p.line, p.character, tLen, typeIdx, modMask));
            }

            i = Math.max(end, i + 1);
        }

        Collections.sort(rawTokens);

        int[] data = new int[rawTokens.size() * 5];
        int prevLine = 0;
        int prevChar = 0;
        int idx = 0;

        for (RawToken rt : rawTokens) {
            int deltaLine = rt.line - prevLine;
            int deltaChar = (deltaLine == 0) ? (rt.character - prevChar) : rt.character;
            data[idx++] = deltaLine;
            data[idx++] = deltaChar;
            data[idx++] = rt.length;
            data[idx++] = rt.typeIndex;
            data[idx++] = rt.modifierMask;
            prevLine = rt.line;
            prevChar = rt.character;
        }

        return new SemanticTokens(data);
    }

    // -------------------------------------------------------------------------
    // Formatting
    // -------------------------------------------------------------------------

    @Override
    public List<TextEdit> formatting(DocumentFormattingParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null || doc.text.isEmpty()) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        String formatted = new TsFormatter().format(doc.text);
        if (formatted != null && !doc.text.endsWith("\n") && formatted.endsWith("\n")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted == null || formatted.equals(doc.text)) return Collections.emptyList();

        com.cocode.vcode.ide.core.lsp.model.LspPosition start = new com.cocode.vcode.ide.core.lsp.model.LspPosition(0, 0);
        com.cocode.vcode.ide.core.lsp.model.LspPosition end = doc.toModelPosition(doc.text.length());
        return Collections.singletonList(new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(start, end), formatted));
    }

    // -------------------------------------------------------------------------
    // Rich Type Hover Documentation & Signature Help
    // -------------------------------------------------------------------------

    @Override
    public Hover hover(HoverParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) return null;
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return null;
        if (token != null && token.isCancelled()) return null;

        int offset = doc.toOffset(params.position);
        if (offset < 0 || offset > doc.text.length()) return null;

        JsSyntaxTree tree = TsParser.parseFull(doc.text);
        if (tree != null) {
            for (int i = 1; i < tree.nodeCount; i++) {
                if (offset >= tree.nodeStart[i] && offset <= tree.nodeEnd[i]) {
                    int t = tree.nodeType[i];
                    String name = tree.nodeName[i];
                    if (t == JsSyntaxTree.N_INTERFACE) {
                        String snippet = TsParser.extractTypeSnippet(doc.text, tree, i);
                        if (snippet != null) {
                            return new Hover(new MarkupContent("markdown", "```typescript\n" + snippet + "\n```"), toModelRange(doc, tree.nodeStart[i], tree.nodeEnd[i]));
                        }
                    } else if (t == JsSyntaxTree.N_TYPE_ALIAS) {
                        String snippet = TsParser.extractTypeSnippet(doc.text, tree, i);
                        if (snippet != null) {
                            return new Hover(new MarkupContent("markdown", "```typescript\n" + snippet + "\n```"), toModelRange(doc, tree.nodeStart[i], tree.nodeEnd[i]));
                        }
                    } else if (t == JsSyntaxTree.N_ENUM) {
                        String snippet = TsParser.extractTypeSnippet(doc.text, tree, i);
                        if (snippet != null) {
                            return new Hover(new MarkupContent("markdown", "```typescript\n" + snippet + "\n```"), toModelRange(doc, tree.nodeStart[i], tree.nodeEnd[i]));
                        }
                    } else if (t == JsSyntaxTree.N_VAR_DECL || t == JsSyntaxTree.N_PARAM) {
                        String ann = tree.nodeTypeAnn[i];
                        if (ann != null && !ann.isEmpty() && name != null) {
                            String displayType = com.cocode.vcode.ide.core.language.js.JsTypeInference.toDisplayType(ann);
                            return new Hover(new MarkupContent("markdown", "```typescript\n(variable) " + name + ": " + displayType + "\n```"), toModelRange(doc, tree.nodeStart[i], tree.nodeEnd[i]));
                        }
                    }
                }
            }
        }

        String word = extractWord(doc.text, offset);
        if (!word.isEmpty()) {
            // Check indexed .d.ts dependency symbols
            ProjectSymbolIndex psi = ProjectIndex.getInstance() != null ? ProjectSymbolIndex.getInstance() : null;
            if (psi != null) {
                Map<String, List<com.cocode.vcode.ide.core.model.CompletionItem>> depExports = psi.getDependencyExports();
                for (Map.Entry<String, List<com.cocode.vcode.ide.core.model.CompletionItem>> entry : depExports.entrySet()) {
                    for (com.cocode.vcode.ide.core.model.CompletionItem ci : entry.getValue()) {
                        if (word.equals(ci.getLabel())) {
                            String sig = ci.getDetail() != null ? ci.getDetail() : word;
                            String md = "```typescript\n(" + entry.getKey() + " declaration) " + word + ": " + sig + "\n```";
                            return new Hover(new MarkupContent("markdown", md), null);
                        }
                    }
                }
            }
        }

        HoverInfo legacy = jsLspServer.hover(doc, new LspPosition(params.position.line, params.position.character));
        if (legacy != null && legacy.getMarkdownContent() != null) {
            return new Hover(new MarkupContent("markdown", legacy.getMarkdownContent()), null);
        }

        return null;
    }

    @Override
    public HoverInfo hover(LspDocument doc, LspPosition pos) {
        if (doc == null || pos == null) return null;
        Hover h = hover(new HoverParams(new TextDocumentIdentifier(doc.uri), new com.cocode.vcode.ide.core.lsp.model.LspPosition(pos.line, pos.character)), null);
        if (h != null && h.contents != null) {
            return new HoverInfo(null, h.contents.value);
        }
        return jsLspServer.hover(doc, pos);
    }

    @Override
    public SignatureHelp signatureHelp(SignatureHelpParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) return null;
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return null;
        if (token != null && token.isCancelled()) return null;

        LspSignatureHelp leg = signatureHelp(doc, new LspPosition(params.position.line, params.position.character));
        if (leg == null || leg.signatures == null || leg.signatures.isEmpty()) return null;

        List<SignatureInformation> sigs = new ArrayList<>();
        for (LspSignatureHelp.LspSignatureInformation si : leg.signatures) {
            List<ParameterInformation> paramsInfo = new ArrayList<>();
            if (si.parameters != null) {
                for (LspSignatureHelp.LspParameterInformation pi : si.parameters) {
                    paramsInfo.add(new ParameterInformation(pi.label, pi.documentation));
                }
            }
            MarkupContent sigDoc = (si.documentation != null && !si.documentation.isEmpty())
                    ? MarkupContent.markdown(si.documentation) : null;
            sigs.add(new SignatureInformation(si.label, sigDoc, paramsInfo));
        }

        return new SignatureHelp(sigs, leg.activeSignature, leg.activeParameter);
    }

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos) {
        return JsSignatureParser.parse(doc, pos);
    }

    // -------------------------------------------------------------------------
    // References & Rename
    // -------------------------------------------------------------------------

    @Override
    public List<LspLocation> references(ReferenceParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        return references(doc, new LspPosition(params.position.line, params.position.character));
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
                TokenStream ts = JsLexer.tokenize(d.text);
                int tLen = trimmed.length();
                for (int i = 0; i < ts.length && result.size() < MAX_REFS; ) {
                    if (ts.types[i] == TokenStream.TK_IDENTIFIER) {
                        int start = ts.tokenStart[i];
                        int end = start;
                        while (end < ts.length && ts.types[end] == TokenStream.TK_IDENTIFIER && ts.tokenStart[end] == start) {
                            end++;
                        }
                        int idLen = end - start;
                        if (idLen == tLen && d.text.regionMatches(start, trimmed, 0, tLen)) {
                            LspPosition posStart = SymbolExtractor.offsetToPosition(d.text, start);
                            LspPosition posEnd = SymbolExtractor.offsetToPosition(d.text, end);
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
    public WorkspaceEdit rename(RenameParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null || params.newName == null) return null;
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return null;
        if (token != null && token.isCancelled()) return null;

        List<LspLocation> locations = rename(doc, new LspPosition(params.position.line, params.position.character));
        if (locations == null || locations.isEmpty()) return null;

        Map<String, List<TextEdit>> changes = new HashMap<>();
        for (LspLocation loc : locations) {
            com.cocode.vcode.ide.core.lsp.model.LspPosition start = new com.cocode.vcode.ide.core.lsp.model.LspPosition(loc.range.start.line, loc.range.start.character);
            com.cocode.vcode.ide.core.lsp.model.LspPosition end = new com.cocode.vcode.ide.core.lsp.model.LspPosition(loc.range.end.line, loc.range.end.character);
            TextEdit edit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(start, end), params.newName);
            changes.computeIfAbsent(loc.uri, k -> new ArrayList<>()).add(edit);
        }
        return new WorkspaceEdit(changes);
    }

    @Override
    public List<LspLocation> rename(LspDocument doc, LspPosition pos) {
        return JsSymbolRenamer.rename(doc, pos);
    }

    @Override
    public LspRange prepareRename(PrepareRenameParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) return null;
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return null;

        int offset = doc.toOffset(params.position);
        String word = extractWord(doc.text, offset);
        if (word.isEmpty()) return null;

        int wordStart = offset;
        while (wordStart > 0 && isWordChar(doc.text.charAt(wordStart - 1))) wordStart--;
        com.cocode.vcode.ide.core.lsp.model.LspPosition s = doc.toModelPosition(wordStart);
        com.cocode.vcode.ide.core.lsp.model.LspPosition e = doc.toModelPosition(wordStart + word.length());
        return new LspRange(s, e);
    }

    @Override
    public List<DocumentHighlight> documentHighlight(DocumentHighlightParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();
        int offset = doc.toOffset(params.position);
        String word = extractWord(doc.text, offset);
        if (word.isEmpty()) return Collections.emptyList();

        List<DocumentHighlight> highlights = new ArrayList<>();
        int idx = doc.text.indexOf(word);
        while (idx >= 0) {
            boolean leftOk = (idx == 0) || !isWordChar(doc.text.charAt(idx - 1));
            boolean rightOk = (idx + word.length() >= doc.text.length()) || !isWordChar(doc.text.charAt(idx + word.length()));
            if (leftOk && rightOk) {
                com.cocode.vcode.ide.core.lsp.model.LspPosition s = doc.toModelPosition(idx);
                com.cocode.vcode.ide.core.lsp.model.LspPosition e = doc.toModelPosition(idx + word.length());
                highlights.add(new DocumentHighlight(new com.cocode.vcode.ide.core.lsp.model.LspRange(s, e)));
            }
            idx = doc.text.indexOf(word, idx + 1);
        }
        return highlights;
    }

    @Override
    public List<FoldingRange> foldingRange(FoldingRangeParams params, CancellationToken token) {
        return jsLspServer.foldingRange(params, token);
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static com.cocode.vcode.ide.core.lsp.model.LspRange toModelRange(LspDocument doc, int startOffset, int endOffset) {
        int s = Math.max(0, Math.min(startOffset, doc.text.length()));
        int e = Math.max(s, Math.min(endOffset, doc.text.length()));
        return new com.cocode.vcode.ide.core.lsp.model.LspRange(doc.toModelPosition(s), doc.toModelPosition(e));
    }

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

    private static CompletionItemKind mapCompletionKind(com.cocode.vcode.ide.core.model.CompletionItem.Type type) {
        if (type == null) return CompletionItemKind.TEXT;
        switch (type) {
            case FUNCTION: return CompletionItemKind.FUNCTION;
            case KEYWORD: return CompletionItemKind.KEYWORD;
            case VALUE: return CompletionItemKind.VARIABLE;
            case SNIPPET: return CompletionItemKind.SNIPPET;
            default: return CompletionItemKind.TEXT;
        }
    }

    private static DiagnosticSeverity mapSeverity(Problem.Severity severity) {
        if (severity == null) return DiagnosticSeverity.INFORMATION;
        switch (severity) {
            case ERROR: return DiagnosticSeverity.ERROR;
            case WARNING: return DiagnosticSeverity.WARNING;
            case INFO: return DiagnosticSeverity.INFORMATION;
            default: return DiagnosticSeverity.INFORMATION;
        }
    }
}
