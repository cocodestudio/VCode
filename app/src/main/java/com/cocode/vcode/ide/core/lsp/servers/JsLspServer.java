package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.diagnostic.DiagnosticEngine;
import com.cocode.vcode.ide.core.diagnostic.ProblemCode;
import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.editor.indent.BracketMatcher;
import com.cocode.vcode.ide.core.language.js.DocCommentParser;
import com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.js.JsDocumentationProvider;
import com.cocode.vcode.ide.core.language.js.JsExportTable;
import com.cocode.vcode.ide.core.language.js.JsFormatter;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsLinter;
import com.cocode.vcode.ide.core.language.js.JsParser;
import com.cocode.vcode.ide.core.language.js.JsStandardLibrary;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.language.js.ParseResult;
import com.cocode.vcode.ide.core.language.js.ScopeTree;
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
import com.cocode.vcode.ide.core.lsp.model.VersionedTextDocumentIdentifier;
import com.cocode.vcode.ide.core.lsp.model.WorkspaceEdit;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process Language Server for JavaScript files implementing LSP 3.17 capabilities.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Document Synchronization</b>: didOpen, didChange, didSave, didClose with AST index updates.</li>
 *   <li><b>Completions & Auto-Imports</b>: Fast autocomplete with cross-file auto-import insertion.</li>
 *   <li><b>Diagnostics</b>: Linting with syntax and semantic diagnostics.</li>
 *   <li><b>Code Actions</b>: Add missing imports, remove unused declarations, arrow function rewrites.</li>
 *   <li><b>Go to Definition</b>: Jump to imports, local declarations, and project-wide exports.</li>
 *   <li><b>Find References</b>: AST-verified identifier token stream verification.</li>
 *   <li><b>Rename</b>: Full symbol renaming across lexical scopes and class members.</li>
 *   <li><b>Document Symbols</b>: Hierarchical outline for classes, functions, and declarations.</li>
 *   <li><b>Semantic Tokens</b>: Delta-encoded semantic highlight tokens.</li>
 *   <li><b>Formatting</b>: Full-document atomic TextEdit formatting.</li>
 *   <li><b>Signature Help & Hover</b>: Parameter hints and JSDoc markdown documentation.</li>
 * </ul>
 */
public final class JsLspServer implements LspServer {

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
            .setSignatureHelpProvider(true)
            .setSemanticTokensProvider(true)
            .setRenameProvider(true)
            .build();

    private final JsAutoCompleteEngine autoCompleteEngine;
    private final ConcurrentHashMap<String, LspDocument> openDocs = new ConcurrentHashMap<>();
    private volatile boolean ready = false;
    private ProjectIndex projectIndex;

    public JsLspServer(Context context) {
        this.autoCompleteEngine = new JsAutoCompleteEngine(context);
    }

    public JsLspServer() {
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
        openDocs.clear();
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
    public String getTriggerCharacters() {
        return ".'\"`/@({*";
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
        LspDocument doc = new LspDocument(document.uri, document.text, document.languageId != null ? document.languageId : "javascript", document.version);
        openDocs.put(document.uri, doc);
        WorkspaceIndexService wis = WorkspaceIndexService.getInstance();
        if (wis != null) {
            wis.updateDocument(doc);
        }
    }

    @Override
    public void didChange(VersionedTextDocumentIdentifier document, List<TextDocumentContentChangeEvent> contentChanges) {
        if (document == null || document.uri == null || contentChanges == null) return;
        LspDocument existing = LspDocumentManager.getInstance().getDocument(document.uri);
        if (existing != null) {
            openDocs.put(document.uri, existing);
            WorkspaceIndexService wis = WorkspaceIndexService.getInstance();
            if (wis != null) {
                wis.updateDocument(existing);
            }
            return;
        }

        LspDocument prev = openDocs.get(document.uri);
        String currentText = prev != null && prev.text != null ? prev.text : "";

        for (TextDocumentContentChangeEvent change : contentChanges) {
            if (change.range == null) {
                currentText = change.text;
            } else if (prev != null) {
                int startOffset = prev.toOffset(change.range.start);
                int endOffset = prev.toOffset(change.range.end);
                if (startOffset >= 0 && endOffset >= startOffset && endOffset <= currentText.length()) {
                    currentText = currentText.substring(0, startOffset) + change.text + currentText.substring(endOffset);
                } else {
                    currentText = change.text;
                }
            } else {
                currentText = change.text;
            }
        }

        LspDocument updated = new LspDocument(document.uri, currentText, "javascript", document.version);
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
                    return new LspDocument(uri, text, "javascript", 1);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Completions & Auto-Imports
    // -------------------------------------------------------------------------

    @Override
    public List<com.cocode.vcode.ide.core.lsp.model.CompletionItem> completion(CompletionParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        int offset = params.position != null ? doc.toOffset(params.position) : doc.text.length();
        if (offset < 0) offset = doc.text.length();

        autoCompleteEngine.setCurrentFile(new File(doc.uri));
        List<CompletionItem> suggestions = autoCompleteEngine.getSuggestions(doc.text, offset);
        if (suggestions == null || suggestions.isEmpty()) return Collections.emptyList();

        List<com.cocode.vcode.ide.core.lsp.model.CompletionItem> out = new ArrayList<>();
        for (CompletionItem item : suggestions) {
            if (token != null && token.isCancelled()) break;
            com.cocode.vcode.ide.core.lsp.model.CompletionItem.Builder b =
                    com.cocode.vcode.ide.core.lsp.model.CompletionItem.builder(item.getLabel())
                            .setInsertText(item.getInsertText() != null ? item.getInsertText() : item.getLabel())
                            .setDetail(item.getDetail())
                            .setKind(mapCompletionKind(item.getType()));

            if (item.getReplaceLength() >= 0) {
                int startOff = Math.max(0, offset - item.getReplaceLength());
                b.setTextEdit(new TextEdit(
                        new com.cocode.vcode.ide.core.lsp.model.LspRange(doc.toModelPosition(startOff), doc.toModelPosition(offset)),
                        item.getEffectiveInsertText()
                ));
            }

            out.add(b.build());
        }
        return out;
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

    public static List<LspCompletionItem> convertCompletions(List<CompletionItem> suggestions) {
        return LspCompletionConverter.convert(suggestions);
    }

    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------

    @Override
    public List<Diagnostic> diagnostics(DocumentDiagnosticParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        List<Problem> problems = diagnostics(doc);
        if (problems == null || problems.isEmpty()) return Collections.emptyList();

        List<Diagnostic> out = new ArrayList<>(problems.size());
        for (Problem p : problems) {
            if (p == null) continue;
            com.cocode.vcode.ide.core.lsp.model.LspPosition start =
                    new com.cocode.vcode.ide.core.lsp.model.LspPosition(p.getLine() - 1, p.getColumn() - 1);
            com.cocode.vcode.ide.core.lsp.model.LspPosition end =
                    new com.cocode.vcode.ide.core.lsp.model.LspPosition(p.getLine() - 1, (p.getColumn() - 1) + Math.max(1, p.getLength()));

            DiagnosticSeverity sev = DiagnosticSeverity.WARNING;
            if (p.getSeverity() == Problem.Severity.ERROR) sev = DiagnosticSeverity.ERROR;
            else if (p.getSeverity() == Problem.Severity.INFO) sev = DiagnosticSeverity.INFORMATION;

            Diagnostic diag = new Diagnostic(
                    new com.cocode.vcode.ide.core.lsp.model.LspRange(start, end),
                    sev,
                    p.getCode() != null ? p.getCode() : "javascript",
                    "javascript",
                    p.getMessage()
            );
            out.add(diag);
        }
        return out;
    }

    @Override
    public List<Problem> diagnostics(LspDocument doc) {
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            File file = new File(doc.uri);
            List<Problem> problems = new ArrayList<>(BracketMatcher.findMismatches(file, doc.text));
            List<Problem> jsProblems = JsLinter.analyze(file, doc.text, ProjectIndex.getInstance());
            if (jsProblems != null) problems.addAll(jsProblems);
            return DiagnosticEngine.deduplicateAndSort(file, problems);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Code Actions
    // -------------------------------------------------------------------------

    @Override
    public List<CodeAction> codeAction(CodeActionParams params, CancellationToken token) {
        return codeAction(params, token, null);
    }

    public List<CodeAction> codeAction(CodeActionParams params, CancellationToken token, LspDocument overrideDoc) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = overrideDoc != null ? overrideDoc : getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        List<CodeAction> actions = new ArrayList<>();

        // 1. Diagnostics-driven quick fixes
        if (params.context != null && params.context.diagnostics != null) {
            for (Diagnostic diag : params.context.diagnostics) {
                if (token != null && token.isCancelled()) break;
                addNativeJsFixes(doc, diag, actions);
            }
        }

        // 2. Refactoring intentions (e.g. convert function declaration to arrow function)
        if (params.range != null) {
            int cursorOffset = doc.toOffset(params.range.start);
            if (cursorOffset >= 0 && cursorOffset < doc.text.length()) {
                TokenStream ts = JsLexer.tokenize(doc.text);
                JsSyntaxTree tree = JsParser.parseFull(doc.text, ts);
                for (int k = 1; k < tree.nodeCount; k++) {
                    if (tree.nodeType[k] == JsSyntaxTree.N_FUNC_DECL
                            && cursorOffset >= tree.nodeStart[k] && cursorOffset <= tree.nodeEnd[k]) {
                        String fnName = tree.nodeName[k];
                        if (fnName != null && !fnName.isEmpty()) {
                            int bodyStart = -1;
                            for (int ch = tree.nodeChild[k]; ch > 0; ch = tree.nodeSibling[ch]) {
                                if (tree.nodeType[ch] == JsSyntaxTree.N_BLOCK) {
                                    bodyStart = tree.nodeStart[ch];
                                    break;
                                }
                            }
                            if (bodyStart > tree.nodeStart[k]) {
                                int parenOpen = doc.text.indexOf('(', tree.nodeStart[k]);
                                if (parenOpen > 0 && parenOpen < bodyStart) {
                                    String paramsText = doc.text.substring(parenOpen, bodyStart).trim();
                                    String bodyText = doc.text.substring(bodyStart, tree.nodeEnd[k]);
                                    String replacement = "const " + fnName + " = " + paramsText + " => " + bodyText;

                                    com.cocode.vcode.ide.core.lsp.model.LspRange fnRange =
                                            doc.toModelRange(tree.nodeStart[k], tree.nodeEnd[k]);
                                    TextEdit edit = new TextEdit(fnRange, replacement);
                                    WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                                    actions.add(new CodeAction(
                                            "Convert function '" + fnName + "' to arrow function",
                                            CodeActionKind.REFACTOR_REWRITE,
                                            wsEdit
                                    ));
                                }
                            }
                        }
                        break;
                    }
                }
            }
        }

        return actions;
    }

    private void addNativeJsFixes(LspDocument doc, Diagnostic diag, List<CodeAction> actions) {
        if (doc == null || doc.text == null || diag == null) return;
        String code = diag.code != null ? diag.code : "";
        String msg = diag.message != null ? diag.message : "";

        // 1. JS_UNUSED_VARIABLE
        if (ProblemCode.JS_UNUSED_VARIABLE.name().equals(code) || msg.contains("is declared but never used") || msg.contains("Unused variable")) {
            TextEdit edit = computeUnusedVariableRemovalEdit(doc, diag.range);
            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
            actions.add(new CodeAction("Remove unused variable", CodeActionKind.QUICKFIX, Collections.singletonList(diag), false, wsEdit, null, null));
        }

        // 2. JS_CONST_REASSIGNMENT
        if (ProblemCode.JS_CONST_REASSIGNMENT.name().equals(code) || msg.contains("Assignment to constant variable")) {
            String varName = extractQuotedName(msg);
            if (!varName.isEmpty()) {
                int declIdx = doc.text.indexOf("const " + varName);
                if (declIdx >= 0) {
                    com.cocode.vcode.ide.core.lsp.model.LspRange range =
                            new com.cocode.vcode.ide.core.lsp.model.LspRange(doc.toModelPosition(declIdx), doc.toModelPosition(declIdx + 5));
                    TextEdit edit = new TextEdit(range, "let");
                    WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                    actions.add(new CodeAction("Change 'const " + varName + "' declaration to 'let'", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
                }
            }
        }

        // 3. JS_MISSING_SEMICOLON
        if (ProblemCode.JS_MISSING_SEMICOLON.name().equals(code) || msg.contains("Missing semicolon")) {
            TextEdit edit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(diag.range.end, diag.range.end), ";");
            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
            actions.add(new CodeAction("Insert missing semicolon ';'", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
        }

        // 4. JS_CONSOLE_STATEMENT (2 choices: delete vs comment out)
        if (ProblemCode.JS_CONSOLE_STATEMENT.name().equals(code) || msg.contains("console statement") || msg.contains("Unexpected console statement")) {
            com.cocode.vcode.ide.core.lsp.model.LspRange delRange = expandToFullLine(doc, diag.range);
            TextEdit editDel = new TextEdit(delRange, "");
            WorkspaceEdit wsEditDel = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(editDel)));
            actions.add(new CodeAction("Remove console statement", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEditDel, null, null));

            com.cocode.vcode.ide.core.lsp.model.LspPosition lineStart = new com.cocode.vcode.ide.core.lsp.model.LspPosition(diag.range.start.line, 0);
            TextEdit editComment = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(lineStart, lineStart), "// ");
            WorkspaceEdit wsEditComment = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(editComment)));
            actions.add(new CodeAction("Comment out console statement", CodeActionKind.QUICKFIX, Collections.singletonList(diag), false, wsEditComment, null, null));
        }

        // 5. JS_TYPEOF_UNDEFINED_COMPARISON
        if (ProblemCode.JS_TYPEOF_UNDEFINED_COMPARISON.name().equals(code)
                || msg.contains("typeof comparison with undefined")
                || (msg.contains("typeof") && msg.contains("undefined"))) {
            TextEdit edit = new TextEdit(diag.range, "\"undefined\"");
            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
            actions.add(new CodeAction("Wrap 'undefined' in quotes ('\"undefined\"')", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
        }

        // 6. JS_DUPLICATE_DECLARATION
        if (ProblemCode.JS_DUPLICATE_DECLARATION.name().equals(code) || msg.contains("Identifier has already been declared") || msg.contains("already been declared")) {
            com.cocode.vcode.ide.core.lsp.model.LspRange delRange = expandToFullLine(doc, diag.range);
            TextEdit edit = new TextEdit(delRange, "");
            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
            actions.add(new CodeAction("Remove duplicate declaration", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
        }

        // 7. JS_TOO_MANY_ARGUMENTS
        if (ProblemCode.JS_TOO_MANY_ARGUMENTS.name().equals(code) || msg.contains("Expected at most") || msg.contains("Too many arguments")) {
            TextEdit edit = new TextEdit(diag.range, "");
            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
            actions.add(new CodeAction("Remove extraneous arguments", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
        }
    }

    private static String extractQuotedName(String msg) {
        if (msg == null) return "";
        int q1 = msg.indexOf('\'');
        if (q1 >= 0) {
            int q2 = msg.indexOf('\'', q1 + 1);
            if (q2 > q1) return msg.substring(q1 + 1, q2);
        }
        return "";
    }

    private static com.cocode.vcode.ide.core.lsp.model.LspRange expandToFullLine(LspDocument doc, com.cocode.vcode.ide.core.lsp.model.LspRange range) {
        if (doc == null || doc.text == null || range == null || range.start == null) return range;
        int startOff = doc.toOffset(range.start);
        int endOff = doc.toOffset(range.end);
        int lineStart = doc.text.lastIndexOf('\n', Math.max(0, startOff - 1));
        lineStart = (lineStart == -1) ? 0 : lineStart + 1;
        int lineEnd = doc.text.indexOf('\n', endOff);
        int deleteEnd = (lineEnd == -1) ? doc.text.length() : lineEnd + 1;
        return new com.cocode.vcode.ide.core.lsp.model.LspRange(doc.toModelPosition(lineStart), doc.toModelPosition(deleteEnd));
    }

    private TextEdit computeUnusedVariableRemovalEdit(LspDocument doc, com.cocode.vcode.ide.core.lsp.model.LspRange range) {
        if (doc == null || doc.text == null || range == null || range.start == null) {
            return new TextEdit(range, "");
        }
        int startOff = doc.toOffset(range.start);
        int endOff = doc.toOffset(range.end);
        if (startOff < 0 || endOff < startOff || startOff >= doc.text.length()) {
            return new TextEdit(range, "");
        }

        int lineStart = startOff;
        while (lineStart > 0 && doc.text.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        int lineEnd = endOff;
        while (lineEnd < doc.text.length() && doc.text.charAt(lineEnd) != '\n') {
            lineEnd++;
        }
        int lineEndWithNewline = (lineEnd < doc.text.length() && doc.text.charAt(lineEnd) == '\n') ? lineEnd + 1 : lineEnd;

        String beforeOnLine = doc.text.substring(lineStart, startOff);
        String trimmedBefore = beforeOnLine.trim();

        // Check if part of a comma-separated declaration on the same line
        // First find the extent of the variable's initializer (if any)
        int initEnd = endOff;
        int p = endOff;
        while (p < lineEnd && Character.isWhitespace(doc.text.charAt(p))) {
            p++;
        }
        if (p < lineEnd && doc.text.charAt(p) == '=') {
            p++;
            int parenDepth = 0;
            int braceDepth = 0;
            int bracketDepth = 0;
            while (p < lineEnd) {
                char c = doc.text.charAt(p);
                if (c == '(') parenDepth++;
                else if (c == ')' && parenDepth > 0) parenDepth--;
                else if (c == '{') braceDepth++;
                else if (c == '}' && braceDepth > 0) braceDepth--;
                else if (c == '[') bracketDepth++;
                else if (c == ']' && bracketDepth > 0) bracketDepth--;
                else if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
                    if (c == ',' || c == ';') break;
                }
                p++;
            }
            initEnd = p;
        }

        // Check if there is a trailing comma on the declaration list: const x = 1, y = 2;
        int afterInit = initEnd;
        while (afterInit < lineEnd && Character.isWhitespace(doc.text.charAt(afterInit))) {
            afterInit++;
        }
        boolean hasTrailingComma = (afterInit < lineEnd && doc.text.charAt(afterInit) == ',');

        // Check if there is a leading comma: const y = 2, x = 1;
        int commaBefore = -1;
        int b = startOff - 1;
        while (b >= lineStart) {
            char c = doc.text.charAt(b);
            if (c == ',') {
                commaBefore = b;
                break;
            } else if (!Character.isWhitespace(c)) {
                break;
            }
            b--;
        }

        if (hasTrailingComma) {
            int removeEnd = afterInit + 1;
            while (removeEnd < lineEnd && Character.isWhitespace(doc.text.charAt(removeEnd))) {
                removeEnd++;
            }
            return new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(
                    doc.toModelPosition(startOff),
                    doc.toModelPosition(removeEnd)
            ), "");
        } else if (commaBefore >= 0) {
            return new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(
                    doc.toModelPosition(commaBefore),
                    doc.toModelPosition(initEnd)
            ), "");
        }

        // If the declaration is the sole statement on this line, delete the entire line cleanly
        if (trimmedBefore.equals("const") || trimmedBefore.equals("let") || trimmedBefore.equals("var") || trimmedBefore.isEmpty()) {
            return new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(
                    doc.toModelPosition(lineStart),
                    doc.toModelPosition(lineEndWithNewline)
            ), "");
        }

        return new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(
                doc.toModelPosition(startOff),
                doc.toModelPosition(initEnd)
        ), "");
    }

    // -------------------------------------------------------------------------
    // Document Symbols
    // -------------------------------------------------------------------------

    @Override
    public List<DocumentSymbol> documentSymbol(DocumentSymbolParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        TokenStream tokens = JsLexer.tokenize(doc.text);
        JsSyntaxTree tree = JsParser.parseFull(doc.text, tokens);
        if (tree == null || tree.nodeCount <= 1) return Collections.emptyList();

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
                break;
            case JsSyntaxTree.N_VAR_DECL:
                kind = SymbolKind.VARIABLE;
                break;
            case JsSyntaxTree.N_INTERFACE:
                kind = SymbolKind.INTERFACE;
                break;
            case JsSyntaxTree.N_ENUM:
                kind = SymbolKind.ENUM;
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
        JsSyntaxTree tree = JsParser.parseFull(doc.text, stream);

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
            if (start != i) {
                i++;
                continue;
            }

            int end = i;
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
            } else if (t == TokenStream.TK_REGEX) {
                typeIdx = 19; // regexp
            } else if (t == TokenStream.TK_OPERATOR) {
                typeIdx = 20; // operator
            } else if (t == TokenStream.TK_IDENTIFIER) {
                typeIdx = 7; // variable (default)
                if (tree != null) {
                    for (int k = 1; k < tree.nodeCount; k++) {
                        if (tree.nodeStart[k] == start) {
                            int nType = tree.nodeType[k];
                            if (nType == JsSyntaxTree.N_CLASS_DECL) {
                                typeIdx = 1; // class
                                modMask = 1; // declaration
                            } else if (nType == JsSyntaxTree.N_FUNC_DECL || nType == JsSyntaxTree.N_ARROW_FUNC) {
                                typeIdx = 11; // function
                                modMask = 1; // declaration
                            } else if (nType == JsSyntaxTree.N_METHOD) {
                                typeIdx = 12; // method
                                modMask = 1;
                            } else if (nType == JsSyntaxTree.N_PARAM) {
                                typeIdx = 6; // parameter
                                modMask = 1;
                            } else if (nType == JsSyntaxTree.N_PROPERTY) {
                                typeIdx = 8; // property
                            } else if (nType == JsSyntaxTree.N_INTERFACE) {
                                typeIdx = 3; // interface
                                modMask = 1;
                            } else if (nType == JsSyntaxTree.N_ENUM) {
                                typeIdx = 2; // enum
                                modMask = 1;
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

        String formatted = new JsFormatter().format(doc.text);
        if (formatted != null && !doc.text.endsWith("\n") && formatted.endsWith("\n")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted == null || formatted.equals(doc.text)) return Collections.emptyList();

        com.cocode.vcode.ide.core.lsp.model.LspPosition start = new com.cocode.vcode.ide.core.lsp.model.LspPosition(0, 0);
        com.cocode.vcode.ide.core.lsp.model.LspPosition end = doc.toModelPosition(doc.text.length());
        return Collections.singletonList(new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(start, end), formatted));
    }

    // -------------------------------------------------------------------------
    // Definition
    // -------------------------------------------------------------------------

    @Override
    public List<LspLocation> definition(DefinitionParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        LspLocation loc = definition(doc, toLegacyPosition(params.position));
        return loc != null ? Collections.singletonList(loc) : Collections.emptyList();
    }

    @Override
    public LspLocation definition(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;

        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return null;

        TokenStream tokens = JsLexer.tokenize(doc.text);
        JsSyntaxTree tree = JsParser.parseFull(doc.text, tokens);

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

        String word = extractWord(doc.text, offset);
        if (word.isEmpty()) return null;

        ScopeTree scopeTree = ScopeTree.build(tree);
        int scopeId = scopeTree.findScopeAt(offset, tree);
        int[] resolved = scopeTree.lookupSymbol(word, scopeId);

        if (resolved != null) {
            int declNodeId = resolved[1];
            int declType = tree.nodeType[declNodeId];
            int declNameOffset = -1;

            if (declType == JsSyntaxTree.N_PARAM) {
                declNameOffset = tree.nodeStart[declNodeId];
            } else {
                for (int t = 0; t < tokens.types.length; t++) {
                    if (tokens.tokenStart[t] < tree.nodeStart[declNodeId]) continue;
                    if (tokens.tokenStart[t] >= tree.nodeEnd[declNodeId]) break;

                    if (tokens.types[t] == TokenStream.TK_IDENTIFIER) {
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
                LspPosition start = SymbolExtractor.offsetToPosition(doc.text, declNameOffset);
                LspPosition end = SymbolExtractor.offsetToPosition(doc.text, declNameOffset + word.length());
                return new LspLocation(doc.uri, new LspRange(start, end));
            }
        }

        ProjectIndex pIndex = projectIndex != null ? projectIndex : ProjectIndex.getInstance();
        List<LspLocation> defs = pIndex != null ? pIndex.findDefinitions(word) : Collections.emptyList();
        return (defs != null && !defs.isEmpty()) ? defs.get(0) : null;
    }

    // -------------------------------------------------------------------------
    // References
    // -------------------------------------------------------------------------

    @Override
    public List<LspLocation> references(ReferenceParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        return references(doc, toLegacyPosition(params.position));
    }

    @Override
    public List<LspLocation> references(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return Collections.emptyList();
        int offset = doc.toOffset(pos);
        String word = extractWord(doc.text, offset >= 0 ? offset : 0);
        if (word.isEmpty()) return Collections.emptyList();

        return findUsagesInProject(word);
    }

    private List<LspLocation> findUsagesInProject(String word) {
        List<LspLocation> result = new ArrayList<>();
        if (word == null || word.trim().isEmpty()) return result;
        String trimmed = word.trim();
        ProjectIndex pIndex = projectIndex != null ? projectIndex : ProjectIndex.getInstance();
        List<LspLocation> defs = pIndex != null ? pIndex.findDefinitions(trimmed) : Collections.emptyList();

        final int MAX_REFS = 100;
        List<String> uris = pIndex != null ? pIndex.getAllUris() : Collections.emptyList();
        for (String uri : uris) {
            if (result.size() >= MAX_REFS) break;
            LspDocument d = getDocument(uri);
            if (d == null || d.text == null) continue;

            if (uri.endsWith(".js") || uri.endsWith(".ts") || uri.endsWith(".jsx") || uri.endsWith(".tsx")) {
                TokenStream ts = JsLexer.tokenize(d.text);
                int tLen = trimmed.length();
                int textLen = d.text.length();

                for (int i = 0; i < ts.length && i < textLen && result.size() < MAX_REFS; ) {
                    if (ts.types[i] == TokenStream.TK_IDENTIFIER && ts.tokenStart[i] == i) {
                        int start = i;
                        int end = i;
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
                            if (defs != null) {
                                for (LspLocation def : defs) {
                                    if (def != null && def.uri != null && def.uri.equals(uri)
                                            && def.range != null
                                            && def.range.start.line == range.start.line
                                            && def.range.start.character == range.start.character) {
                                        isDef = true;
                                        break;
                                    }
                                }
                            }
                            if (!isDef) {
                                result.add(loc);
                            }
                        }
                        i = Math.max(end, i + 1);
                    } else {
                        i++;
                    }
                }
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Rename
    // -------------------------------------------------------------------------

    @Override
    public LspRange prepareRename(PrepareRenameParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) return null;
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return null;

        int offset = doc.toOffset(params.position);
        if (offset < 0 || offset >= doc.text.length()) return null;

        TokenStream tokens = JsLexer.tokenize(doc.text);
        if (offset < tokens.length && tokens.types[offset] == TokenStream.TK_IDENTIFIER) {
            int start = tokens.tokenStart[offset];
            int end = start;
            while (end < tokens.length && tokens.types[end] == TokenStream.TK_IDENTIFIER && tokens.tokenStart[end] == start) {
                end++;
            }
            return new LspRange(doc.toPosition(start), doc.toPosition(end));
        }
        return null;
    }

    @Override
    public WorkspaceEdit rename(RenameParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null || params.newName == null) {
            return null;
        }
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return null;

        LspPosition legacyPos = toLegacyPosition(params.position);
        List<LspLocation> locations = JsSymbolRenamer.rename(doc, legacyPos);
        if (locations == null || locations.isEmpty()) return null;

        Map<String, List<TextEdit>> changes = new HashMap<>();
        for (LspLocation loc : locations) {
            if (loc == null || loc.uri == null || loc.range == null) continue;
            List<TextEdit> edits = changes.computeIfAbsent(loc.uri, k -> new ArrayList<>());
            edits.add(new TextEdit(loc.range, params.newName));
        }
        return new WorkspaceEdit(changes);
    }

    @Override
    public List<LspLocation> rename(LspDocument doc, LspPosition pos) {
        return JsSymbolRenamer.rename(doc, pos);
    }

    // -------------------------------------------------------------------------
    // Signature Help
    // -------------------------------------------------------------------------

    @Override
    public SignatureHelp signatureHelp(SignatureHelpParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) return null;
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return null;
        if (token != null && token.isCancelled()) return null;

        LspSignatureHelp help = signatureHelp(doc, toLegacyPosition(params.position));
        if (help == null || help.signatures == null || help.signatures.isEmpty()) return null;

        List<SignatureInformation> sigInfos = new ArrayList<>();
        for (LspSignatureHelp.LspSignatureInformation sig : help.signatures) {
            List<ParameterInformation> paramInfos = new ArrayList<>();
            if (sig.parameters != null) {
                for (LspSignatureHelp.LspParameterInformation param : sig.parameters) {
                    MarkupContent paramDoc = (param.documentation != null && !param.documentation.isEmpty())
                            ? MarkupContent.markdown(param.documentation) : null;
                    paramInfos.add(new ParameterInformation(param.label, paramDoc));
                }
            }
            MarkupContent sigDoc = (sig.documentation != null && !sig.documentation.isEmpty())
                    ? MarkupContent.markdown(sig.documentation) : null;
            sigInfos.add(new SignatureInformation(sig.label, sigDoc, paramInfos));
        }

        return new SignatureHelp(sigInfos, help.activeSignature, help.activeParameter);
    }

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos) {
        return JsSignatureParser.parse(doc, pos);
    }

    // -------------------------------------------------------------------------
    // Hover
    // -------------------------------------------------------------------------

    @Override
    public Hover hover(HoverParams params, CancellationToken token) {
        if (params == null || params.textDocument == null || params.position == null) return null;
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return null;
        if (token != null && token.isCancelled()) return null;

        HoverInfo info = hover(doc, toLegacyPosition(params.position));
        if (info == null || info.getMarkdownContent() == null || info.getMarkdownContent().isEmpty()) return null;

        MarkupContent content = MarkupContent.markdown(info.getMarkdownContent());
        return new Hover(content, toModelRange(info.getRange()));
    }

    @Override
    public HoverInfo hover(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;
        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return null;

        TokenStream tokens = JsLexer.tokenize(doc.text);
        if (offset < tokens.length) {
            byte type = tokens.types[offset];
            int s = tokens.tokenStart[offset];
            int e = s;
            while (e < tokens.length && tokens.tokenStart[e] == s && tokens.types[e] == type) {
                e++;
            }
            String word = doc.text.substring(s, Math.min(e, doc.text.length())).trim();

            if (type == TokenStream.TK_KEYWORD) {
                String kwDoc = JsDocumentationProvider.resolveIdentifierDoc(word, doc.languageId != null ? doc.languageId : "javascript");
                if (kwDoc != null) {
                    return new HoverInfo(new LspRange(doc.toPosition(s), doc.toPosition(e)), kwDoc);
                }
                for (com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionItem item : com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader.getJsKeywords()) {
                    if (word.equals(item.label)) {
                        String detail = item.detail != null ? item.detail : "JavaScript Keyword";
                        String md = "### `" + item.label + "`\n\n" + detail;
                        return new HoverInfo(new LspRange(doc.toPosition(s), doc.toPosition(e)), md);
                    }
                }
                return new HoverInfo(new LspRange(doc.toPosition(s), doc.toPosition(e)), "### `" + word + "`\n\nJavaScript Keyword");
            }

            JsSyntaxTree tree = JsParser.parseFull(doc.text, tokens);
            if (tree != null) {
                ParseResult localPr = new ParseResult(null, doc.text, tokens, tree, null, ParseResult.MODE_FULL);

                for (int i = 1; i < tree.nodeCount; i++) {
                    if (word.equals(tree.nodeName[i])) {
                        boolean isImport = (tree.nodeParent[i] > 0 && tree.nodeType[tree.nodeParent[i]] == JsSyntaxTree.N_IMPORT)
                                || (tree.nodeTypeAnn != null && tree.nodeTypeAnn[i] != null &&
                                    (tree.nodeTypeAnn[i].startsWith("@IMPORT:") || tree.nodeTypeAnn[i].startsWith("@REQUIRE:") || tree.nodeTypeAnn[i].startsWith("@REQUIRE_PROP:")));

                        if (isImport) {
                            String modPath = null;
                            String expName = word;
                            String typeAnn = tree.nodeTypeAnn != null ? tree.nodeTypeAnn[i] : null;
                            if (typeAnn != null && typeAnn.startsWith("@IMPORT:")) {
                                int colon = typeAnn.indexOf(':', 8);
                                if (colon != -1) {
                                    modPath = typeAnn.substring(8, colon);
                                    expName = typeAnn.substring(colon + 1);
                                }
                            } else if (typeAnn != null && typeAnn.startsWith("@REQUIRE_PROP:")) {
                                int colon = typeAnn.indexOf(':', 14);
                                if (colon != -1) {
                                    modPath = typeAnn.substring(14, colon);
                                    expName = typeAnn.substring(colon + 1);
                                }
                            } else if (typeAnn != null && typeAnn.startsWith("@REQUIRE:")) {
                                modPath = typeAnn.substring(9);
                                expName = "default";
                            } else if (tree.nodeParent[i] > 0 && tree.nodeType[tree.nodeParent[i]] == JsSyntaxTree.N_IMPORT) {
                                modPath = tree.nodeName[tree.nodeParent[i]];
                                expName = word;
                            }
                            if (modPath != null) {
                                HoverInfo cross = resolveCrossFileHover(doc, s, e, modPath, expName, word);
                                if (cross != null) return cross;
                            }
                        } else {
                            HoverInfo local = buildHoverInfoFromNode(doc, s, e, localPr, i, word);
                            if (local != null) return local;
                        }
                    }
                }
            }

            // Extract receiver if part of dot property access
            String receiver = null;
            if (s > 0) {
                int dotPos = s - 1;
                while (dotPos >= 0 && Character.isWhitespace(doc.text.charAt(dotPos))) {
                    dotPos--;
                }
                if (dotPos >= 0 && doc.text.charAt(dotPos) == '.') {
                    int recEnd = dotPos;
                    while (recEnd > 0 && Character.isWhitespace(doc.text.charAt(recEnd - 1))) {
                        recEnd--;
                    }
                    int recStart = recEnd;
                    while (recStart > 0 && (Character.isJavaIdentifierPart(doc.text.charAt(recStart - 1)) || doc.text.charAt(recStart - 1) == '$')) {
                        recStart--;
                    }
                    if (recStart < recEnd) {
                        receiver = doc.text.substring(recStart, recEnd);
                    }
                }
            }

            if (receiver != null && tree != null) {
                for (int r = 1; r < tree.nodeCount; r++) {
                    if (receiver.equals(tree.nodeName[r])) {
                        String rAnn = tree.nodeTypeAnn != null ? tree.nodeTypeAnn[r] : null;
                        String modPath = null;
                        if (rAnn != null && rAnn.startsWith("@IMPORT:")) {
                            int colon = rAnn.indexOf(':', 8);
                            if (colon != -1) modPath = rAnn.substring(8, colon);
                        } else if (rAnn != null && rAnn.startsWith("@REQUIRE:")) {
                            modPath = rAnn.substring(9);
                        } else if (tree.nodeParent[r] > 0 && tree.nodeType[tree.nodeParent[r]] == JsSyntaxTree.N_IMPORT) {
                            modPath = tree.nodeName[tree.nodeParent[r]];
                        }
                        if (modPath != null) {
                            HoverInfo cross = resolveCrossFileHover(doc, s, e, modPath, word, word);
                            if (cross != null) return cross;
                        }
                    }
                }
            }

            // Check built-in documentation provider first
            String richDoc = null;
            if (receiver != null) {
                richDoc = JsDocumentationProvider.resolveMemberDoc(receiver, word, doc.languageId != null ? doc.languageId : "javascript");
            } else {
                richDoc = JsDocumentationProvider.resolveIdentifierDoc(word, doc.languageId != null ? doc.languageId : "javascript");
            }
            if (richDoc != null) {
                return new HoverInfo(new LspRange(doc.toPosition(s), doc.toPosition(e)), richDoc);
            }

            // Built-in standard library function / method fallback
            JsStandardLibrary.SignatureInfo builtin = null;
            if (receiver != null) {
                builtin = JsStandardLibrary.getBuiltinSignature(receiver + "." + word, null);
            }
            if (builtin == null) {
                builtin = JsStandardLibrary.getBuiltinSignature(word, null);
            }
            if (builtin == null) {
                for (Map<String, JsStandardLibrary.SignatureInfo> cMap : JsStandardLibrary.SIGNATURES_BY_CONTAINER.values()) {
                    if (cMap != null && cMap.containsKey(word)) {
                        builtin = cMap.get(word);
                        break;
                    }
                }
            }
            if (builtin != null) {
                String sig = "(method) " + word + "(" + String.join(", ", builtin.parameters) + ")";
                StringBuilder sb = new StringBuilder();
                sb.append("```javascript\n").append(sig).append("\n```\n\n");
                if (builtin.doc != null && !builtin.doc.isEmpty()) {
                    sb.append(builtin.doc).append("\n\n");
                }
                if (builtin.parameters != null && !builtin.parameters.isEmpty()) {
                    sb.append("**Parameters:** `").append(String.join("`, `", builtin.parameters)).append("`\n\n");
                }
                return new HoverInfo(new LspRange(doc.toPosition(s), doc.toPosition(e)), sb.toString().trim());
            }

            // Cross-file symbol definition
            ProjectIndex pIndex = projectIndex != null ? projectIndex : ProjectIndex.getInstance();
            if (pIndex != null) {
                List<LspLocation> defs = pIndex.findDefinitions(word);
                if (defs != null && !defs.isEmpty()) {
                    for (LspLocation loc : defs) {
                        if (loc == null || loc.uri == null) continue;
                        if (doc.uri != null && isSameUri(doc.uri, loc.uri)) {
                            continue;
                        }
                        ParseResult targetPr = getTargetParseResult(pIndex, loc.uri);
                        if (targetPr != null && targetPr.tree != null) {
                            int targetNode = findSymbolNodeInTarget(targetPr.tree, word);
                            if (targetNode > 0) {
                                HoverInfo hInfo = buildHoverInfoFromNode(doc, s, e, targetPr, targetNode, word);
                                if (hInfo != null) return hInfo;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private HoverInfo resolveCrossFileHover(
            LspDocument doc, int s, int e, String modPath, String expName, String displayName) {
        if (doc == null || modPath == null || modPath.isEmpty()) return null;
        if ("*".equals(expName)) {
            String md = "```javascript\nnamespace " + displayName + "\n```\n\nModule `" + modPath + "`";
            return new HoverInfo(new LspRange(doc.toPosition(s), doc.toPosition(e)), md);
        }
        LspLocation targetLoc = ModuleResolver.resolveModulePath(doc.uri, modPath);
        if (targetLoc == null || targetLoc.uri == null) return null;

        ProjectIndex pIndex = projectIndex != null ? projectIndex : ProjectIndex.getInstance();
        ParseResult targetPr = getTargetParseResult(pIndex, targetLoc.uri);
        if (targetPr == null || targetPr.tree == null || targetPr.source == null) return null;

        int targetNode = findSymbolNodeInTarget(targetPr.tree, expName);
        if (targetNode > 0) {
            return buildHoverInfoFromNode(doc, s, e, targetPr, targetNode, displayName);
        }
        return null;
    }

    private int findSymbolNodeInTarget(JsSyntaxTree tree, String expName) {
        if (tree == null || expName == null || expName.isEmpty()) return 0;
        int expNode = JsExportTable.findExportNode(tree, expName);
        if (expNode <= 0) {
            expNode = JsExportTable.findTopLevelDecl(tree, expName);
        }
        if (expNode <= 0) {
            for (int k = 1; k < tree.nodeCount; k++) {
                if (expName.equals(tree.nodeName[k])) {
                    int kType = tree.nodeType[k];
                    if (kType == JsSyntaxTree.N_FUNC_DECL
                            || kType == JsSyntaxTree.N_METHOD
                            || kType == JsSyntaxTree.N_ARROW_FUNC
                            || kType == JsSyntaxTree.N_CLASS_DECL
                            || kType == JsSyntaxTree.N_VAR_DECL
                            || kType == JsSyntaxTree.N_INTERFACE
                            || kType == JsSyntaxTree.N_ENUM
                            || kType == JsSyntaxTree.N_TYPE_ALIAS) {
                        return k;
                    }
                }
            }
        }
        return expNode > 0 ? expNode : 0;
    }

    private ParseResult getTargetParseResult(ProjectIndex pIndex, String uri) {
        if (pIndex == null || uri == null) return null;
        String cleanUri = uri.startsWith("file://") ? uri.substring(7) : uri;
        LspDocument targetDoc = pIndex.getDocument(uri);
        if (targetDoc == null) {
            targetDoc = pIndex.getDocument(cleanUri);
        }
        ParseResult pr = pIndex.getParseResult(uri);
        if (pr == null) {
            pr = pIndex.getParseResult(cleanUri);
        }
        if (pr != null && targetDoc != null && targetDoc.text != null && !targetDoc.text.equals(pr.source)) {
            pr = null;
        }
        if (pr == null && targetDoc != null && targetDoc.text != null) {
            TokenStream tokens = JsLexer.tokenize(targetDoc.text);
            JsSyntaxTree tree = JsParser.parseFull(targetDoc.text, tokens);
            if (tree != null) {
                tree.buildNodesByOffset();
                ScopeTree scopeTree = ScopeTree.build(tree);
                pr = new ParseResult(
                        new File(cleanUri), targetDoc.text, tokens, tree, scopeTree,
                        ParseResult.MODE_FULL);
                pIndex.updateParseResult(uri, pr);
                pIndex.updateParseResult(cleanUri, pr);
            }
        }
        if (pr == null) {
            File f = new File(cleanUri);
            if (f.exists()) {
                pr = pIndex.getOrParseJsFile(f);
            }
        }
        return pr;
    }

    private HoverInfo buildHoverInfoFromNode(
            LspDocument doc, int s, int e, ParseResult targetPr, int node, String displayName) {
        if (targetPr == null || targetPr.tree == null || targetPr.source == null || node <= 0 || node >= targetPr.tree.nodeCount) {
            return null;
        }
        JsSyntaxTree tree = targetPr.tree;
        String source = targetPr.source;
        int type = tree.nodeType[node];

        int fnOrClassNode = node;
        if (type == JsSyntaxTree.N_VAR_DECL) {
            int child = tree.nodeChild[node];
            if (child > 0 && child < tree.nodeCount) {
                int ct = tree.nodeType[child];
                if (ct == JsSyntaxTree.N_FUNC_DECL
                        || ct == JsSyntaxTree.N_ARROW_FUNC
                        || ct == JsSyntaxTree.N_CLASS_DECL) {
                    fnOrClassNode = child;
                    type = ct;
                }
            }
        }

        String sig = null;
        int ctorDocOffset = -1;
        if (type == JsSyntaxTree.N_FUNC_DECL
                || type == JsSyntaxTree.N_METHOD
                || type == JsSyntaxTree.N_ARROW_FUNC) {
            List<String> params = new ArrayList<>();
            int pChild = tree.nodeChild[fnOrClassNode];
            while (pChild > 0 && pChild < tree.nodeCount) {
                if (tree.nodeType[pChild] == JsSyntaxTree.N_PARAM) {
                    params.add(extractParamSignature(tree, pChild, source));
                }
                pChild = tree.nodeSibling[pChild];
            }
            String typeAnn = tree.nodeTypeAnn != null ? tree.nodeTypeAnn[fnOrClassNode] : null;
            sig = "function " + displayName + "(" + String.join(", ", params) + ")";
            if (typeAnn != null && !typeAnn.isEmpty() && !typeAnn.startsWith("@")) {
                sig += ": " + typeAnn;
            }
        } else if (type == JsSyntaxTree.N_CLASS_DECL) {
            List<String> ctorParams = new ArrayList<>();
            int cChild = tree.nodeChild[fnOrClassNode];
            while (cChild > 0 && cChild < tree.nodeCount) {
                if (tree.nodeType[cChild] == JsSyntaxTree.N_METHOD && "constructor".equals(tree.nodeName[cChild])) {
                    int pChild = tree.nodeChild[cChild];
                    while (pChild > 0 && pChild < tree.nodeCount) {
                        if (tree.nodeType[pChild] == JsSyntaxTree.N_PARAM) {
                            ctorParams.add(extractParamSignature(tree, pChild, source));
                        }
                        pChild = tree.nodeSibling[pChild];
                    }
                    ctorDocOffset = tree.nodeStart[cChild];
                    break;
                }
                cChild = tree.nodeSibling[cChild];
            }
            sig = "class " + displayName;
            if (!ctorParams.isEmpty()) {
                sig += "(" + String.join(", ", ctorParams) + ")";
            }
            String superName = tree.nodeTypeAnn != null ? tree.nodeTypeAnn[fnOrClassNode] : null;
            if (superName != null && !superName.isEmpty() && !superName.startsWith("@")) {
                sig += " extends " + superName;
            }
        } else if (type == JsSyntaxTree.N_VAR_DECL) {
            String typeAnn = tree.nodeTypeAnn != null ? tree.nodeTypeAnn[node] : null;
            sig = getVarKindPrefix(tree, node, source) + displayName;
            if (typeAnn != null && !typeAnn.isEmpty()) {
                String displayType = com.cocode.vcode.ide.core.language.js.JsTypeInference.toDisplayType(typeAnn);
                if (displayType != null && !displayType.isEmpty()) {
                    sig += ": " + displayType;
                }
            }
        } else if (type == JsSyntaxTree.N_INTERFACE) {
            sig = "interface " + displayName;
        } else if (type == JsSyntaxTree.N_TYPE_ALIAS) {
            sig = "type " + displayName;
        } else if (type == JsSyntaxTree.N_ENUM) {
            sig = "enum " + displayName;
        }

        if (sig == null) return null;

        int docOffset = (tree.nodeParent[node] > 0 && tree.nodeType[tree.nodeParent[node]] == JsSyntaxTree.N_EXPORT)
                ? tree.nodeStart[tree.nodeParent[node]]
                : tree.nodeStart[node];
        DocCommentParser.DocCommentInfo docInfo = DocCommentParser.extractDoc(source, docOffset);
        if ((docInfo == null || docInfo.isEmpty()) && fnOrClassNode != node) {
            docInfo = DocCommentParser.extractDoc(source, tree.nodeStart[fnOrClassNode]);
        }
        if ((docInfo == null || docInfo.isEmpty()) && ctorDocOffset > 0) {
            docInfo = DocCommentParser.extractDoc(source, ctorDocOffset);
        }
        if ((docInfo == null || docInfo.isEmpty()) && tree.nodeParent[node] > 0) {
            docInfo = DocCommentParser.extractDoc(source, tree.nodeStart[tree.nodeParent[node]]);
        }

        String md = (docInfo != null && !docInfo.isEmpty())
                ? docInfo.toMarkdown(sig, doc.languageId != null ? doc.languageId : "javascript")
                : "```javascript\n" + sig + "\n```";

        return new HoverInfo(new LspRange(doc.toPosition(s), doc.toPosition(e)), md);
    }

    // -------------------------------------------------------------------------
    // Helpers
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

    private static boolean isSameUri(String u1, String u2) {
        if (u1 == null || u2 == null) return false;
        if (u1.equals(u2)) return true;
        String c1 = canonicalize(u1);
        String c2 = canonicalize(u2);
        return c1 != null && c1.equals(c2);
    }

    private static String canonicalize(String uri) {
        if (uri == null) return null;
        String clean = uri.startsWith("file://") ? uri.substring(7) : uri;
        try {
            return new File(clean).getAbsolutePath();
        } catch (Exception e) {
            return clean;
        }
    }

    private static String getVarKindPrefix(JsSyntaxTree tree, int nodeIndex, String source) {
        if (tree != null && tree.nodeExtra != null && nodeIndex >= 0 && nodeIndex < tree.nodeExtra.length) {
            int flag = tree.nodeExtra[nodeIndex] & 3;
            if (flag == JsSyntaxTree.FLAG_CONST) return "const ";
            if (flag == JsSyntaxTree.FLAG_VAR) return "var ";
            if (flag == JsSyntaxTree.FLAG_LET) return "let ";
        }
        if (source != null && tree != null && nodeIndex >= 0 && nodeIndex < tree.nodeCount) {
            int start = tree.nodeStart[nodeIndex];
            if (start >= 0 && start <= source.length()) {
                int lookback = Math.max(0, start - 30);
                String snippet = source.substring(lookback, Math.min(source.length(), start + 20));
                if (snippet.contains("const ")) return "const ";
                if (snippet.contains("var ")) return "var ";
            }
        }
        return "let ";
    }

    private static String extractParamSignature(JsSyntaxTree tree, int pChild, String source) {
        String pName = tree.nodeName[pChild] != null ? tree.nodeName[pChild] : "arg";
        if ((tree.nodeExtra[pChild] & JsSyntaxTree.FLAG_REST) != 0) {
            pName = "..." + pName;
        }
        int pStart = tree.nodeStart[pChild];
        int pEnd = tree.nodeEnd[pChild];
        if (pStart >= 0 && pEnd > pStart && pEnd <= source.length()) {
            String paramText = source.substring(pStart, pEnd).trim();
            if (paramText.startsWith(pName)) {
                pName = paramText;
            }
        }
        String pAnn = tree.nodeTypeAnn != null ? tree.nodeTypeAnn[pChild] : null;
        if (pAnn != null && !pAnn.isEmpty() && !pAnn.startsWith("@") && !pName.contains(":")) {
            pName += ": " + pAnn;
        }
        if ((tree.nodeExtra[pChild] & JsSyntaxTree.FLAG_DEFAULT) != 0 && pEnd < source.length()) {
            int eqPos = -1;
            int depth = 0;
            for (int k = pEnd; k < source.length(); k++) {
                char ch = source.charAt(k);
                if (ch == '(' || ch == '{' || ch == '[') depth++;
                else if (ch == ')' || ch == '}' || ch == ']') {
                    if (depth == 0) break;
                    depth--;
                } else if (ch == ',' && depth == 0) {
                    break;
                } else if (ch == '=' && depth == 0 && (k + 1 < source.length() && source.charAt(k + 1) != '=')) {
                    eqPos = k;
                    break;
                }
            }
            if (eqPos != -1) {
                int valStart = eqPos + 1;
                while (valStart < source.length() && Character.isWhitespace(source.charAt(valStart))) {
                    valStart++;
                }
                int valEnd = valStart;
                depth = 0;
                boolean inStr = false;
                char quoteChar = 0;
                for (int k = valStart; k < source.length(); k++) {
                    char ch = source.charAt(k);
                    if (inStr) {
                        if (ch == '\\') {
                            k++;
                        } else if (ch == quoteChar) {
                            inStr = false;
                        }
                    } else {
                        if (ch == '\'' || ch == '"' || ch == '`') {
                            inStr = true;
                            quoteChar = ch;
                        } else if (ch == '(' || ch == '{' || ch == '[') {
                            depth++;
                        } else if (ch == ')' || ch == '}' || ch == ']') {
                            if (depth == 0) {
                                valEnd = k;
                                break;
                            }
                            depth--;
                        } else if (ch == ',' && depth == 0) {
                            valEnd = k;
                            break;
                        }
                    }
                    valEnd = k + 1;
                }
                if (valEnd > valStart) {
                    String defVal = source.substring(valStart, valEnd).trim();
                    if (!defVal.isEmpty()) {
                        pName = pName + " = " + defVal;
                    }
                }
            }
        }
        return pName;
    }

    private static LspPosition toLegacyPosition(com.cocode.vcode.ide.core.lsp.model.LspPosition pos) {
        if (pos == null) return new LspPosition(0, 0);
        return new LspPosition(pos.line, pos.character);
    }

    private static com.cocode.vcode.ide.core.lsp.model.LspRange toModelRange(LspRange range) {
        if (range == null) return new com.cocode.vcode.ide.core.lsp.model.LspRange(new com.cocode.vcode.ide.core.lsp.model.LspPosition(0, 0), new com.cocode.vcode.ide.core.lsp.model.LspPosition(0, 0));
        return new com.cocode.vcode.ide.core.lsp.model.LspRange(
                new com.cocode.vcode.ide.core.lsp.model.LspPosition(range.start.line, range.start.character),
                new com.cocode.vcode.ide.core.lsp.model.LspPosition(range.end.line, range.end.character)
        );
    }

    private static com.cocode.vcode.ide.core.lsp.model.LspRange toModelRange(com.cocode.vcode.ide.core.lsp.model.LspRange range) {
        return range;
    }

    private static CompletionItemKind mapCompletionKind(CompletionItem.Type type) {
        if (type == null) return CompletionItemKind.VARIABLE;
        switch (type) {
            case FUNCTION: return CompletionItemKind.FUNCTION;
            case KEYWORD: return CompletionItemKind.KEYWORD;
            case BUILTIN: return CompletionItemKind.CLASS;
            case SNIPPET: return CompletionItemKind.SNIPPET;
            case FILE: return CompletionItemKind.FILE;
            case FOLDER: return CompletionItemKind.FOLDER;
            case VALUE: return CompletionItemKind.VALUE;
            case ATTRIBUTE: return CompletionItemKind.PROPERTY;
            case TAG: return CompletionItemKind.KEYWORD;
            default: return CompletionItemKind.VARIABLE;
        }
    }
}
