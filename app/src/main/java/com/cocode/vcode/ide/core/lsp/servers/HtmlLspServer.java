package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.diagnostic.DiagnosticEngine;
import com.cocode.vcode.ide.core.diagnostic.util.KnownElements;
import com.cocode.vcode.ide.core.language.html.HtmlAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.html.HtmlDocumentationProvider;
import com.cocode.vcode.ide.core.language.html.HtmlFormatter;
import com.cocode.vcode.ide.core.language.html.HtmlLexer;
import com.cocode.vcode.ide.core.language.html.HtmlLinter;
import com.cocode.vcode.ide.core.language.html.HtmlParser;
import com.cocode.vcode.ide.core.language.html.HtmlSyntaxTree;
import com.cocode.vcode.ide.core.language.html.HtmlTokenStream;
import com.cocode.vcode.ide.core.language.js.ParseResult;
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
import com.cocode.vcode.ide.core.lsp.SymbolEntry;
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
import com.cocode.vcode.ide.core.lsp.model.ReferenceParams;
import com.cocode.vcode.ide.core.lsp.model.SymbolKind;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentContentChangeEvent;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentIdentifier;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentItem;
import com.cocode.vcode.ide.core.lsp.model.TextEdit;
import com.cocode.vcode.ide.core.lsp.model.VersionedTextDocumentIdentifier;
import com.cocode.vcode.ide.core.lsp.model.WorkspaceEdit;
import com.cocode.vcode.ide.core.model.CompletionItem;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process Language Server for HTML files implementing full LSP 3.17 capabilities.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Document Synchronization</b>: didOpen, didChange, didSave, didClose with workspace indexing.</li>
 *   <li><b>Virtual Document Projections</b>: Delegates embedded &lt;style&gt; blocks to {@link CssLspServer}
 *       and &lt;script&gt; blocks to {@link JsLspServer} using 1:1 coordinate masking.</li>
 *   <li><b>Completions</b>: Tags, attributes, scoped class/id completions strictly resolved from linked
 *       stylesheets and embedded style blocks, plus embedded CSS/JS delegation.</li>
 *   <li><b>Diagnostics</b>: HTML structural validation, suppression of raw-HTML bracket mismatch noise,
 *       and seamless merging with projected embedded CSS/JS lint results.</li>
 *   <li><b>Code Actions</b>: Native quick fixes for missing {@code alt=""}, {@code type="button"},
 *       {@code <meta charset="UTF-8">}, and unclosed tags.</li>
 *   <li><b>Document Symbols</b>: Hierarchical DOM outline tree for editor breadcrumbs.</li>
 *   <li><b>Formatting</b>: Full-document atomic TextEdit formatting.</li>
 *   <li><b>Go to Definition & References</b>: Cross-file navigation for src/href, id, and CSS class rules.</li>
 * </ul>
 */
public final class HtmlLspServer implements LspServer {

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
            .setFoldingRangeProvider(false)
            .setSignatureHelpProvider(false)
            .setSemanticTokensProvider(false)
            .setColorProvider(false)
            .build();

    private final HtmlAutoCompleteEngine completeEngine;
    private final CssLspServer cssServer;
    private final JsLspServer jsServer;
    private final ConcurrentHashMap<String, LspDocument> openDocs = new ConcurrentHashMap<>();
    private volatile boolean ready = false;
    private ProjectIndex projectIndex;

    public HtmlLspServer(Context context) {
        this.completeEngine = new HtmlAutoCompleteEngine(context);
        this.cssServer = new CssLspServer(context);
        this.jsServer = new JsLspServer(context);
    }

    public HtmlLspServer() {
        this(null);
    }

    // -------------------------------------------------------------------------
    // Lifecycle & Capabilities
    // -------------------------------------------------------------------------

    @Override
    public void initialize(ProjectIndex index) {
        this.projectIndex = index;
        if (cssServer != null) cssServer.initialize(index);
        if (jsServer != null) jsServer.initialize(index);
        ready = true;
    }

    @Override
    public void shutdown() {
        openDocs.clear();
        if (cssServer != null) cssServer.shutdown();
        if (jsServer != null) jsServer.shutdown();
        ready = false;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String getLanguageId() {
        return "html";
    }

    @Override
    public String getTriggerCharacters() {
        return "<'/\"!=:@-.*+^#&";
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
        LspDocument doc = new LspDocument(document.uri, document.text, document.languageId != null ? document.languageId : "html", document.version);
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

        LspDocument updated = new LspDocument(document.uri, currentText, "html", document.version);
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
            File f = uriToFile(uri);
            if (f != null && f.exists() && f.isFile()) {
                String text = FileUtils.readFile(f);
                if (text != null) {
                    return new LspDocument(uri, text, "html", 1);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Completions (LSP 3.17 & In-process)
    // -------------------------------------------------------------------------

    @Override
    public List<com.cocode.vcode.ide.core.lsp.model.CompletionItem> completion(CompletionParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        int flatOffset = params.position != null ? doc.toOffset(params.position) : doc.text.length();
        if (flatOffset < 0) flatOffset = doc.text.length();

        LspPosition legacyPos = params.position != null ?
                new LspPosition(params.position.line, params.position.character) :
                doc.toPosition(flatOffset);

        List<LspCompletionItem> items = completion(doc, legacyPos);
        if (items == null || items.isEmpty()) return Collections.emptyList();

        List<com.cocode.vcode.ide.core.lsp.model.CompletionItem> out = new ArrayList<>(items.size());
        for (LspCompletionItem item : items) {
            if (token != null && token.isCancelled()) break;
            com.cocode.vcode.ide.core.lsp.model.CompletionItem.Builder b =
                    com.cocode.vcode.ide.core.lsp.model.CompletionItem.builder(item.label)
                            .setInsertText(item.insertText != null ? item.insertText : item.label)
                            .setDetail(item.detail)
                            .setKind(mapCompletionKind(item.kind));
            if (item.replaceLength >= 0) {
                int startOff = Math.max(0, flatOffset - item.replaceLength);
                b.setTextEdit(new TextEdit(
                        new com.cocode.vcode.ide.core.lsp.model.LspRange(doc.toModelPosition(startOff), doc.toModelPosition(flatOffset)),
                        item.insertText != null ? item.insertText : item.label
                ));
            }
            out.add(b.build());
        }
        return out;
    }

    @Override
    public List<LspCompletionItem> completion(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null) return Collections.emptyList();

        int flatOffset = doc.toOffset(pos);
        if (flatOffset < 0) flatOffset = doc.text.length();

        // 1. Delegate embedded completions if cursor is inside <style> or <script>
        List<HtmlEmbeddedProjector.EmbeddedRegion> regions = HtmlEmbeddedProjector.findEmbeddedRegions(doc.text);
        HtmlEmbeddedProjector.EmbeddedRegion reg = HtmlEmbeddedProjector.getRegionAt(regions, flatOffset);
        if (reg != null) {
            if (reg.type == HtmlEmbeddedProjector.EmbeddedRegion.TYPE_CSS && cssServer != null) {
                LspDocument vCss = HtmlEmbeddedProjector.createVirtualCssDocument(doc, regions);
                if (vCss != null) {
                    return cssServer.completion(vCss, pos);
                }
            } else if (reg.type == HtmlEmbeddedProjector.EmbeddedRegion.TYPE_JS && jsServer != null) {
                LspDocument vJs = HtmlEmbeddedProjector.createVirtualJsDocument(doc, regions);
                if (vJs != null) {
                    return jsServer.completion(vJs, pos);
                }
            }
        }

        // 2. HTML tag, attribute, and scoped class/id completions
        File file = uriToFile(doc.uri);
        completeEngine.setCurrentFile(file);

        List<CompletionItem> legacy = completeEngine.getSuggestions(doc.text, flatOffset);
        List<LspCompletionItem> lspItems = new ArrayList<>(convertCompletions(legacy));

        List<LspCompletionItem> uniqueItems = new ArrayList<>();
        Set<String> seenLabels = new HashSet<>();
        for (LspCompletionItem item : lspItems) {
            if (seenLabels.add(item.label)) {
                uniqueItems.add(item);
            }
        }

        return uniqueItems;
    }

    public static List<LspCompletionItem> convertCompletions(List<CompletionItem> legacyItems) {
        return LspCompletionConverter.convert(legacyItems);
    }

    // -------------------------------------------------------------------------
    // Diagnostics (LSP 3.17 & In-process)
    // -------------------------------------------------------------------------

    @Override
    public List<Diagnostic> diagnostics(DocumentDiagnosticParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        if (token != null && token.isCancelled()) return Collections.emptyList();

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
            diags.add(new Diagnostic(range, sev, code, "html", p.getMessage()));
        }
        return diags;
    }

    @Override
    public List<Problem> diagnostics(LspDocument doc) {
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            File file = uriToFile(doc.uri);
            List<Problem> problems = new ArrayList<>();

            // 1. Lint host HTML structure (BracketMatcher is intentionally suppressed on raw HTML
            // to avoid emitting false-positive errors on valid HTML/JS/CSS brackets)
            List<Problem> htmlProblems = HtmlLinter.analyze(file, doc.text);
            if (htmlProblems != null) {
                problems.addAll(htmlProblems);
            }

            // 2. Lint embedded CSS blocks with 1:1 coordinate projection
            List<HtmlEmbeddedProjector.EmbeddedRegion> regions = HtmlEmbeddedProjector.findEmbeddedRegions(doc.text);
            LspDocument virtualCssDoc = HtmlEmbeddedProjector.createVirtualCssDocument(doc, regions);
            if (virtualCssDoc != null && cssServer != null) {
                List<Problem> cssProblems = cssServer.diagnostics(virtualCssDoc);
                if (cssProblems != null) {
                    for (Problem p : cssProblems) {
                        if (p != null && HtmlEmbeddedProjector.isInsideRegion(p, doc, regions, HtmlEmbeddedProjector.EmbeddedRegion.TYPE_CSS)) {
                            problems.add(p);
                        }
                    }
                }
            }

            // 3. Lint embedded JS blocks with 1:1 coordinate projection
            LspDocument virtualJsDoc = HtmlEmbeddedProjector.createVirtualJsDocument(doc, regions);
            if (virtualJsDoc != null && jsServer != null) {
                List<Problem> jsProblems = jsServer.diagnostics(virtualJsDoc);
                if (jsProblems != null) {
                    for (Problem p : jsProblems) {
                        if (p != null && HtmlEmbeddedProjector.isInsideRegion(p, doc, regions, HtmlEmbeddedProjector.EmbeddedRegion.TYPE_JS)) {
                            problems.add(p);
                        }
                    }
                }
            }

            return DiagnosticEngine.deduplicateAndSort(file, problems);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Code Actions (LSP 3.17)
    // -------------------------------------------------------------------------

    @Override
    public List<CodeAction> codeAction(CodeActionParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null || doc.text.isEmpty()) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        List<CodeAction> actions = new ArrayList<>();

        // 1. Delegate code action queries inside embedded regions
        int cursorOffset = doc.toOffset(params.range.start);
        List<HtmlEmbeddedProjector.EmbeddedRegion> regions = HtmlEmbeddedProjector.findEmbeddedRegions(doc.text);
        HtmlEmbeddedProjector.EmbeddedRegion reg = HtmlEmbeddedProjector.getRegionAt(regions, cursorOffset);
        if (reg != null) {
            if (reg.type == HtmlEmbeddedProjector.EmbeddedRegion.TYPE_CSS && cssServer != null) {
                LspDocument vCss = HtmlEmbeddedProjector.createVirtualCssDocument(doc, regions);
                if (vCss != null) {
                    List<CodeAction> cssActions = cssServer.codeAction(params, token, vCss);
                    if (cssActions != null) actions.addAll(cssActions);
                }
            } else if (reg.type == HtmlEmbeddedProjector.EmbeddedRegion.TYPE_JS && jsServer != null) {
                LspDocument vJs = HtmlEmbeddedProjector.createVirtualJsDocument(doc, regions);
                if (vJs != null) {
                    List<CodeAction> jsActions = jsServer.codeAction(params, token, vJs);
                    if (jsActions != null) actions.addAll(jsActions);
                }
            }
        }

        // 2. Native HTML quick fixes derived from diagnostics
        if (params.context != null && params.context.diagnostics != null) {
            for (Diagnostic diag : params.context.diagnostics) {
                if (token != null && token.isCancelled()) break;
                addNativeHtmlFixes(doc, diag, actions);
            }
        }

        return actions;
    }

    private void addNativeHtmlFixes(LspDocument doc, Diagnostic diag, List<CodeAction> actions) {
        if (doc == null || doc.text == null || diag == null) return;
        String code = diag.code != null ? diag.code : "";
        String msg = diag.message != null ? diag.message : "";

        // 1. Missing alt="" on <img>
        if (ProblemCode.HTML_MISSING_ALT.name().equals(code) || msg.contains("missing 'alt'")) {
            int offset = doc.toOffset(diag.range.start);
            int tagStart = findTagStartIgnoreCase(doc.text, offset, "<img");
            if (tagStart == -1) tagStart = offset;
            int gt = doc.text.indexOf('>', tagStart);
            if (gt > tagStart) {
                int insertOffset = (gt > 0 && doc.text.charAt(gt - 1) == '/') ? gt - 1 : gt;
                com.cocode.vcode.ide.core.lsp.model.LspPosition pos = doc.toModelPosition(insertOffset);
                TextEdit edit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(pos, pos), " alt=\"\"");
                WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                actions.add(new CodeAction("Add alt=\"\" attribute", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
            }
        }

        // 2. Missing type="button" on <button>
        if (ProblemCode.HTML_BUTTON_MISSING_TYPE.name().equals(code) || (msg.contains("missing 'type'") && msg.contains("button"))) {
            int offset = doc.toOffset(diag.range.start);
            int tagStart = findTagStartIgnoreCase(doc.text, offset, "<button");
            if (tagStart == -1) tagStart = offset;
            int gt = doc.text.indexOf('>', tagStart);
            if (gt > tagStart) {
                int insertOffset = (gt > 0 && doc.text.charAt(gt - 1) == '/') ? gt - 1 : gt;
                com.cocode.vcode.ide.core.lsp.model.LspPosition pos = doc.toModelPosition(insertOffset);
                TextEdit edit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(pos, pos), " type=\"button\"");
                WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                actions.add(new CodeAction("Add type=\"button\" attribute", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
            }
        }

        // 3. Missing <meta charset="UTF-8">
        if (ProblemCode.HTML_MISSING_CHARSET.name().equals(code) || msg.contains("charset")) {
            insertHeadElementAction(doc, diag, actions, "Add <meta charset=\"UTF-8\">", "<meta charset=\"UTF-8\">");
        }

        // 4. Missing viewport <meta>
        if (ProblemCode.HTML_MISSING_VIEWPORT.name().equals(code) || msg.contains("viewport")) {
            insertHeadElementAction(doc, diag, actions, "Add viewport <meta> tag", "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        }

        // 5. Missing <title>
        if (ProblemCode.HTML_MISSING_TITLE.name().equals(code) || msg.contains("Missing <title>")) {
            insertHeadElementAction(doc, diag, actions, "Add <title> tag", "<title>Document</title>");
        }

        // 6. Missing meta description
        if (ProblemCode.HTML_MISSING_META_DESCRIPTION.name().equals(code) || msg.contains("meta description")) {
            insertHeadElementAction(doc, diag, actions, "Add meta description tag", "<meta name=\"description\" content=\"\">");
        }

        // 7. Missing lang on <html>
        if (ProblemCode.HTML_MISSING_LANG.name().equals(code) || msg.contains("missing 'lang'")) {
            int htmlIdx = indexOfIgnoreCase(doc.text, "<html");
            if (htmlIdx >= 0) {
                int insOff = htmlIdx + 5;
                com.cocode.vcode.ide.core.lsp.model.LspPosition pos = doc.toModelPosition(insOff);
                TextEdit edit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(pos, pos), " lang=\"en\"");
                WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                actions.add(new CodeAction("Add lang=\"en\" to <html>", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
            }
        }

        // 8. Target blank security (rel="noopener noreferrer")
        if (ProblemCode.HTML_TARGET_BLANK_SECURITY.name().equals(code) || msg.contains("rel=\"noopener noreferrer\"") || msg.contains("target=\"_blank\"")) {
            int offset = doc.toOffset(diag.range.start);
            int tagStart = findTagStartIgnoreCase(doc.text, offset, "<a");
            if (tagStart == -1) tagStart = offset;
            int gt = doc.text.indexOf('>', tagStart);
            if (gt > tagStart) {
                int insOff = (gt > 0 && doc.text.charAt(gt - 1) == '/') ? gt - 1 : gt;
                com.cocode.vcode.ide.core.lsp.model.LspPosition pos = doc.toModelPosition(insOff);
                TextEdit edit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(pos, pos), " rel=\"noopener noreferrer\"");
                WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                actions.add(new CodeAction("Add rel=\"noopener noreferrer\"", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
            }
        }

        // 9. Void element closing slash
        if (ProblemCode.HTML_VOID_ELEMENT_CLOSING.name().equals(code) || msg.contains("self-closing slash")) {
            int offset = doc.toOffset(diag.range.start);
            int gt = doc.text.indexOf('>', offset);
            if (gt > 0 && doc.text.charAt(gt - 1) == '/') {
                com.cocode.vcode.ide.core.lsp.model.LspRange slashRange =
                        new com.cocode.vcode.ide.core.lsp.model.LspRange(doc.toModelPosition(gt - 1), doc.toModelPosition(gt));
                TextEdit edit = new TextEdit(slashRange, "");
                WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                actions.add(new CodeAction("Remove self-closing slash", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
            }
        }

        // 10. Deprecated tag replacement
        if (ProblemCode.HTML_DEPRECATED_TAG.name().equals(code) || msg.contains("Deprecated HTML tag")) {
            String tagName = extractTagName(msg, doc.text, diag.range);
            if ("center".equalsIgnoreCase(tagName)) {
                addDeprecatedTagAction(doc, diag, actions, "center", "div", true);
            } else if ("font".equalsIgnoreCase(tagName)) {
                addDeprecatedTagAction(doc, diag, actions, "font", "span", true);
            } else if ("strike".equalsIgnoreCase(tagName)) {
                addDeprecatedTagAction(doc, diag, actions, "strike", "s", true);
                addDeprecatedTagAction(doc, diag, actions, "strike", "del", false);
            } else if ("tt".equalsIgnoreCase(tagName)) {
                addDeprecatedTagAction(doc, diag, actions, "tt", "code", true);
            } else if ("big".equalsIgnoreCase(tagName)) {
                addDeprecatedTagAction(doc, diag, actions, "big", "span", true);
            }
        }

        // 11. Deprecated attribute
        if (ProblemCode.HTML_DEPRECATED_ATTR.name().equals(code) || msg.contains("Deprecated attribute")) {
            TextEdit edit = new TextEdit(diag.range, "");
            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
            actions.add(new CodeAction("Remove deprecated attribute", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
        }

        // 12. Duplicate attribute
        if (ProblemCode.HTML_DUPLICATE_ATTR.name().equals(code) || msg.contains("Duplicate attribute")) {
            TextEdit edit = new TextEdit(diag.range, "");
            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
            actions.add(new CodeAction("Remove duplicate attribute", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
        }

        // 13. Redundant type attribute
        if (ProblemCode.HTML_REDUNDANT_TYPE_ATTR.name().equals(code) || msg.contains("type attribute is unnecessary") || msg.contains("Redundant")) {
            TextEdit edit = new TextEdit(diag.range, "");
            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
            actions.add(new CodeAction("Remove redundant type attribute", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
        }

        // 14. Blocking script without defer or async (2 choices)
        if (ProblemCode.HTML_SCRIPT_MISSING_DEFER.name().equals(code) || msg.contains("missing defer") || msg.contains("blocking script")) {
            int offset = doc.toOffset(diag.range.start);
            int tagStart = findTagStartIgnoreCase(doc.text, offset, "<script");
            if (tagStart == -1) tagStart = offset;
            int gt = doc.text.indexOf('>', tagStart);
            if (gt > tagStart) {
                int insOff = (gt > 0 && doc.text.charAt(gt - 1) == '/') ? gt - 1 : gt;
                com.cocode.vcode.ide.core.lsp.model.LspPosition pos = doc.toModelPosition(insOff);

                TextEdit editDefer = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(pos, pos), " defer");
                WorkspaceEdit wsEditDefer = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(editDefer)));
                actions.add(new CodeAction("Add 'defer' attribute to <script>", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEditDefer, null, null));

                TextEdit editAsync = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(pos, pos), " async");
                WorkspaceEdit wsEditAsync = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(editAsync)));
                actions.add(new CodeAction("Add 'async' attribute to <script>", CodeActionKind.QUICKFIX, Collections.singletonList(diag), false, wsEditAsync, null, null));
            }
        }

        // 15. Anchor tag used as button (2 choices)
        if (ProblemCode.HTML_ANCHOR_AS_BUTTON.name().equals(code) || msg.contains("Anchor tag used as button") || msg.contains("href=\"#\"")) {
            int offset = doc.toOffset(diag.range.start);
            int aTagStart = findTagStartIgnoreCase(doc.text, offset, "<a");
            if (aTagStart == -1) aTagStart = offset;

            // Choice 1: Add role="button" inserted right after "<a"
            int roleInsOff = aTagStart + 2;
            com.cocode.vcode.ide.core.lsp.model.LspPosition rolePos = doc.toModelPosition(roleInsOff);
            TextEdit editRole = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(rolePos, rolePos), " role=\"button\"");
            WorkspaceEdit wsEditRole = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(editRole)));
            actions.add(new CodeAction("Add role=\"button\" to anchor", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEditRole, null, null));

            // Choice 2: Convert <a> to <button type="button">, remove href="#", and convert closing </a>
            List<TextEdit> convEdits = new ArrayList<>();
            com.cocode.vcode.ide.core.lsp.model.LspPosition openStart = doc.toModelPosition(aTagStart);
            com.cocode.vcode.ide.core.lsp.model.LspPosition openEnd = doc.toModelPosition(aTagStart + 2);
            convEdits.add(new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(openStart, openEnd), "<button type=\"button\""));

            int gt = doc.text.indexOf('>', aTagStart);
            if (gt > aTagStart) {
                String tagSlice = doc.text.substring(aTagStart, gt);
                Matcher hrefMatcher = Pattern.compile("(?i)(\\s*href\\s*=\\s*[\"']#[\"'])").matcher(tagSlice);
                if (hrefMatcher.find()) {
                    int hStart = aTagStart + hrefMatcher.start();
                    int hEnd = aTagStart + hrefMatcher.end();
                    convEdits.add(new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(doc.toModelPosition(hStart), doc.toModelPosition(hEnd)), ""));
                }

                int closeA = doc.text.toLowerCase(Locale.ROOT).indexOf("</a>", gt);
                if (closeA >= 0) {
                    com.cocode.vcode.ide.core.lsp.model.LspPosition cAStart = doc.toModelPosition(closeA);
                    com.cocode.vcode.ide.core.lsp.model.LspPosition cAEnd = doc.toModelPosition(closeA + 4);
                    convEdits.add(new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(cAStart, cAEnd), "</button>"));
                }
            }

            WorkspaceEdit wsEditBtn = new WorkspaceEdit(Collections.singletonMap(doc.uri, convEdits));
            actions.add(new CodeAction("Convert <a> to <button type=\"button\">", CodeActionKind.QUICKFIX, Collections.singletonList(diag), false, wsEditBtn, null, null));
        }

        // 16. Empty tag
        if (ProblemCode.HTML_EMPTY_TAG.name().equals(code) || msg.contains("Empty element")) {
            TextEdit edit = new TextEdit(diag.range, "");
            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
            actions.add(new CodeAction("Remove empty tag", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
        }

        // 17. Lazy loading attribute
        if (ProblemCode.HTML_LAZY_LOADING.name().equals(code) || msg.contains("loading=\"lazy\"")) {
            int offset = doc.toOffset(diag.range.start);
            int gt = doc.text.indexOf('>', offset);
            if (gt > offset) {
                int insOff = (gt > 0 && doc.text.charAt(gt - 1) == '/') ? gt - 1 : gt;
                com.cocode.vcode.ide.core.lsp.model.LspPosition pos = doc.toModelPosition(insOff);
                TextEdit edit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(pos, pos), " loading=\"lazy\"");
                WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                actions.add(new CodeAction("Add loading=\"lazy\" attribute", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
            }
        }

        // 18. Close unclosed tag
        if (ProblemCode.HTML_UNCLOSED_TAG.name().equals(code) || msg.contains("Unclosed tag")) {
            String tagName = extractTagNameFromMsg(msg);
            if (!tagName.isEmpty()) {
                int offset = doc.toOffset(diag.range.end);
                if (offset < 0 || offset > doc.text.length()) offset = doc.toOffset(diag.range.start);
                int lineEnd = doc.text.indexOf('\n', Math.max(0, offset));
                if (lineEnd == -1) lineEnd = doc.text.length();

                com.cocode.vcode.ide.core.lsp.model.LspPosition pos = doc.toModelPosition(lineEnd);
                TextEdit edit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(pos, pos), "</" + tagName + ">");
                WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                actions.add(new CodeAction("Close tag </" + tagName + ">", CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
            }
        }
    }

    private static void insertHeadElementAction(LspDocument doc, Diagnostic diag, List<CodeAction> actions, String title, String tag) {
        int headIdx = indexOfIgnoreCase(doc.text, "<head>");
        int insertOffset;
        String insertText;
        if (headIdx >= 0) {
            insertOffset = headIdx + 6;
            insertText = "\n    " + tag;
        } else {
            int htmlIdx = indexOfIgnoreCase(doc.text, "<html>");
            if (htmlIdx >= 0) {
                insertOffset = htmlIdx + 6;
                insertText = "\n<head>\n    " + tag + "\n</head>";
            } else {
                insertOffset = 0;
                insertText = tag + "\n";
            }
        }
        com.cocode.vcode.ide.core.lsp.model.LspPosition pos = doc.toModelPosition(insertOffset);
        TextEdit edit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(pos, pos), insertText);
        WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
        actions.add(new CodeAction(title, CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
    }

    private static String extractTagName(String msg, String text, com.cocode.vcode.ide.core.lsp.model.LspRange range) {
        if (msg != null) {
            int q1 = msg.indexOf('\'');
            if (q1 >= 0) {
                int q2 = msg.indexOf('\'', q1 + 1);
                if (q2 > q1) return msg.substring(q1 + 1, q2).replace("<", "").replace(">", "").trim();
            }
            int l1 = msg.indexOf('<');
            if (l1 >= 0) {
                int l2 = msg.indexOf('>', l1 + 1);
                if (l2 > l1) return msg.substring(l1 + 1, l2).trim();
            }
        }
        return "";
    }

    private static String extractTagNameFromMsg(String msg) {
        if (msg == null) return "";
        int start = msg.indexOf('\'');
        if (start >= 0) {
            int end = msg.indexOf('\'', start + 1);
            if (end > start) {
                return msg.substring(start + 1, end).replace("<", "").replace(">", "").trim();
            }
        }
        return "";
    }

    private static int indexOfIgnoreCase(String text, String needle) {
        if (text == null || needle == null) return -1;
        return text.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private static int findTagStartIgnoreCase(String text, int off, String tagPrefix) {
        if (text == null || tagPrefix == null) return -1;
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerPrefix = tagPrefix.toLowerCase(Locale.ROOT);
        int maxLookahead = Math.min(lowerText.length(), off + 20);
        int tagStart = lowerText.lastIndexOf(lowerPrefix, maxLookahead);
        if (tagStart == -1) {
            int minLookbehind = Math.max(0, off - 20);
            tagStart = lowerText.indexOf(lowerPrefix, minLookbehind);
        }
        return tagStart;
    }

    private void addDeprecatedTagAction(LspDocument doc, Diagnostic diag, List<CodeAction> actions,
                                        String oldTag, String newTag, boolean isPreferred) {
        List<TextEdit> edits = new ArrayList<>();
        edits.add(new TextEdit(diag.range, "<" + newTag + ">"));

        int startOff = doc.toOffset(diag.range.end);
        String lowerText = doc.text.toLowerCase(Locale.ROOT);
        String closeTarget = "</" + oldTag.toLowerCase(Locale.ROOT) + ">";
        int closeIdx = lowerText.indexOf(closeTarget, startOff);
        if (closeIdx >= 0) {
            com.cocode.vcode.ide.core.lsp.model.LspPosition cStart = doc.toModelPosition(closeIdx);
            com.cocode.vcode.ide.core.lsp.model.LspPosition cEnd = doc.toModelPosition(closeIdx + closeTarget.length());
            edits.add(new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(cStart, cEnd), "</" + newTag + ">"));
        }

        WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, edits));
        actions.add(new CodeAction("Replace <" + oldTag + "> with <" + newTag + ">",
                CodeActionKind.QUICKFIX, Collections.singletonList(diag), isPreferred, wsEdit, null, null));
    }

    // -------------------------------------------------------------------------
    // Document Symbols (LSP 3.17)
    // -------------------------------------------------------------------------

    @Override
    public List<DocumentSymbol> documentSymbol(DocumentSymbolParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null || doc.text.isEmpty()) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        HtmlTokenStream stream = HtmlLexer.tokenize(doc.text);
        ParseResult result = HtmlParser.parse(doc.text, stream);
        HtmlSyntaxTree tree = result != null ? result.htmlTree : null;
        if (tree == null) return Collections.emptyList();

        List<DocumentSymbol> roots = new ArrayList<>();
        int count = tree.nodeCount;
        for (int i = 1; i < count; i++) {
            if (token != null && token.isCancelled()) return Collections.emptyList();
            if (tree.nodeParent[i] == 0) {
                DocumentSymbol sym = buildDocumentSymbol(i, tree, doc);
                if (sym != null) roots.add(sym);
            }
        }
        return roots;
    }

    private DocumentSymbol buildDocumentSymbol(int nodeId, HtmlSyntaxTree tree, LspDocument doc) {
        if (nodeId <= 0 || nodeId >= tree.nodeCount) return null;
        int type = tree.nodeType[nodeId];

        if (type == HtmlSyntaxTree.N_DOCTYPE) {
            com.cocode.vcode.ide.core.lsp.model.LspRange range = doc.toModelRange(tree.nodeStart[nodeId], tree.nodeEnd[nodeId]);
            return new DocumentSymbol("<!DOCTYPE html>", "", SymbolKind.PROPERTY, null, range, range, Collections.emptyList());
        }

        if (type == HtmlSyntaxTree.N_ELEMENT) {
            String tagName = tree.nodeName[nodeId];
            if (tagName == null || tagName.isEmpty()) return null;

            String idVal = null;
            String classVal = null;
            int child = tree.nodeChild[nodeId];
            while (child > 0 && child < tree.nodeCount) {
                if (tree.nodeType[child] == HtmlSyntaxTree.N_ATTRIBUTE) {
                    String aName = tree.nodeName[child];
                    if ("id".equalsIgnoreCase(aName)) {
                        idVal = stripQuotes(tree.nodeValue[child]);
                    } else if ("class".equalsIgnoreCase(aName)) {
                        classVal = stripQuotes(tree.nodeValue[child]);
                    }
                }
                child = tree.nodeSibling[child];
            }

            StringBuilder detail = new StringBuilder();
            if (idVal != null && !idVal.isEmpty()) {
                detail.append("#").append(idVal);
            }
            if (classVal != null && !classVal.isEmpty()) {
                String[] classes = classVal.trim().split("\\s+");
                for (String c : classes) {
                    if (!c.isEmpty()) {
                        detail.append(".").append(c);
                    }
                }
            }

            SymbolKind kind = resolveElementSymbolKind(tagName);
            int start = tree.nodeStart[nodeId];
            int end = Math.min(doc.text.length(), Math.max(start, tree.nodeEnd[nodeId]));
            com.cocode.vcode.ide.core.lsp.model.LspRange range = doc.toModelRange(start, end);

            int tagEnd = Math.min(end, start + tagName.length() + 1);
            com.cocode.vcode.ide.core.lsp.model.LspRange selRange = doc.toModelRange(start, tagEnd);

            List<DocumentSymbol> children = new ArrayList<>();
            child = tree.nodeChild[nodeId];
            while (child > 0 && child < tree.nodeCount) {
                if (tree.nodeType[child] == HtmlSyntaxTree.N_ELEMENT) {
                    DocumentSymbol cs = buildDocumentSymbol(child, tree, doc);
                    if (cs != null) children.add(cs);
                }
                child = tree.nodeSibling[child];
            }

            return new DocumentSymbol(tagName, detail.toString(), kind, null, range, selRange, children);
        }

        return null;
    }

    private static SymbolKind resolveElementSymbolKind(String tagName) {
        if (tagName == null) return SymbolKind.FIELD;
        String lower = tagName.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "html":
            case "body":
            case "head":
            case "header":
            case "footer":
            case "main":
            case "nav":
            case "section":
            case "article":
            case "aside":
                return SymbolKind.CLASS;
            case "style":
                return SymbolKind.NAMESPACE;
            case "script":
                return SymbolKind.FUNCTION;
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
            case "title":
                return SymbolKind.STRING;
            case "a":
            case "button":
            case "input":
            case "form":
            case "select":
            case "textarea":
                return SymbolKind.EVENT;
            default:
                return SymbolKind.FIELD;
        }
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

        String formatted = new HtmlFormatter().format(doc.text);
        if (formatted != null && !doc.text.endsWith("\n") && formatted.endsWith("\n")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted == null || formatted.equals(doc.text)) {
            return Collections.emptyList();
        }

        com.cocode.vcode.ide.core.lsp.model.LspRange fullRange = doc.toModelRange(0, doc.text.length());
        return Collections.singletonList(new TextEdit(fullRange, formatted));
    }

    // -------------------------------------------------------------------------
    // Go to Definition (LSP 3.17 & In-process)
    // -------------------------------------------------------------------------

    @Override
    public List<LspLocation> definition(DefinitionParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        LspPosition legacyPos = params.position != null ?
                new LspPosition(params.position.line, params.position.character) :
                new LspPosition(0, 0);

        LspLocation loc = definition(doc, legacyPos);
        return loc != null ? Collections.singletonList(loc) : Collections.emptyList();
    }

    @Override
    public LspLocation definition(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;

        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return null;

        // Check if inside embedded region
        List<HtmlEmbeddedProjector.EmbeddedRegion> regions = HtmlEmbeddedProjector.findEmbeddedRegions(doc.text);
        HtmlEmbeddedProjector.EmbeddedRegion reg = HtmlEmbeddedProjector.getRegionAt(regions, offset);
        if (reg != null) {
            if (reg.type == HtmlEmbeddedProjector.EmbeddedRegion.TYPE_CSS && cssServer != null) {
                LspDocument vCss = HtmlEmbeddedProjector.createVirtualCssDocument(doc, regions);
                if (vCss != null) return cssServer.definition(vCss, pos);
            } else if (reg.type == HtmlEmbeddedProjector.EmbeddedRegion.TYPE_JS && jsServer != null) {
                LspDocument vJs = HtmlEmbeddedProjector.createVirtualJsDocument(doc, regions);
                if (vJs != null) return jsServer.definition(vJs, pos);
            }
        }

        HtmlTokenStream stream = HtmlLexer.tokenize(doc.text);
        ParseResult result = HtmlParser.parse(doc.text, stream);
        HtmlSyntaxTree tree = result != null ? result.htmlTree : null;
        if (tree == null) return null;

        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == HtmlSyntaxTree.N_ATTRIBUTE) {
                if (offset >= tree.nodeStart[i] && offset <= tree.nodeEnd[i]) {
                    String attrName = tree.nodeName[i];
                    String rawVal = tree.nodeValue[i];
                    if (attrName == null || rawVal == null) continue;
                    String val = stripQuotes(rawVal);

                    if ("src".equalsIgnoreCase(attrName) || "href".equalsIgnoreCase(attrName)) {
                        return resolveFileReference(val, doc);
                    }
                    if ("id".equalsIgnoreCase(attrName) && projectIndex != null) {
                        return findIdUsageInJs(val);
                    }
                    if ("class".equalsIgnoreCase(attrName) && projectIndex != null) {
                        String cls = findClassAtOffset(doc.text, tree.nodeStart[i], tree.nodeEnd[i], rawVal, offset);
                        if (cls != null) {
                            return findCssRule(cls);
                        }
                    }
                }
            }
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Find References (LSP 3.17 & In-process)
    // -------------------------------------------------------------------------

    @Override
    public List<LspLocation> references(ReferenceParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return Collections.emptyList();
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return Collections.emptyList();
        if (token != null && token.isCancelled()) return Collections.emptyList();

        LspPosition legacyPos = params.position != null ?
                new LspPosition(params.position.line, params.position.character) :
                new LspPosition(0, 0);

        return references(doc, legacyPos);
    }

    @Override
    public List<LspLocation> references(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return Collections.emptyList();

        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return Collections.emptyList();

        // Check embedded region
        List<HtmlEmbeddedProjector.EmbeddedRegion> regions = HtmlEmbeddedProjector.findEmbeddedRegions(doc.text);
        HtmlEmbeddedProjector.EmbeddedRegion reg = HtmlEmbeddedProjector.getRegionAt(regions, offset);
        if (reg != null) {
            if (reg.type == HtmlEmbeddedProjector.EmbeddedRegion.TYPE_CSS && cssServer != null) {
                LspDocument vCss = HtmlEmbeddedProjector.createVirtualCssDocument(doc, regions);
                if (vCss != null) return cssServer.references(vCss, pos);
            } else if (reg.type == HtmlEmbeddedProjector.EmbeddedRegion.TYPE_JS && jsServer != null) {
                LspDocument vJs = HtmlEmbeddedProjector.createVirtualJsDocument(doc, regions);
                if (vJs != null) return jsServer.references(vJs, pos);
            }
        }

        HtmlTokenStream stream = HtmlLexer.tokenize(doc.text);
        ParseResult result = HtmlParser.parse(doc.text, stream);
        HtmlSyntaxTree tree = result != null ? result.htmlTree : null;
        if (tree == null) return Collections.emptyList();

        List<LspLocation> refs = new ArrayList<>();
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == HtmlSyntaxTree.N_ATTRIBUTE) {
                if (offset >= tree.nodeStart[i] && offset <= tree.nodeEnd[i]) {
                    String attrName = tree.nodeName[i];
                    String rawVal = tree.nodeValue[i];
                    if (attrName == null || rawVal == null) continue;
                    String val = stripQuotes(rawVal);

                    if ("id".equalsIgnoreCase(attrName) && projectIndex != null) {
                        refs.addAll(findUsagesInProject(val, true));
                    } else if ("class".equalsIgnoreCase(attrName) && projectIndex != null) {
                        String cls = findClassAtOffset(doc.text, tree.nodeStart[i], tree.nodeEnd[i], rawVal, offset);
                        if (cls != null) {
                            refs.addAll(findUsagesInProject(cls, false));
                        }
                    }
                }
            }
        }

        return refs;
    }

    // -------------------------------------------------------------------------
    // Hover Documentation (LSP 3.17 & In-process)
    // -------------------------------------------------------------------------

    @Override
    public Hover hover(HoverParams params, CancellationToken token) {
        if (params == null || params.textDocument == null) return null;
        LspDocument doc = getDocument(params.textDocument.uri);
        if (doc == null || doc.text == null) return null;
        if (token != null && token.isCancelled()) return null;

        LspPosition legacyPos = params.position != null ?
                new LspPosition(params.position.line, params.position.character) :
                new LspPosition(0, 0);

        HoverInfo info = hover(doc, legacyPos);
        if (info == null || info.getMarkdownContent() == null || info.getMarkdownContent().isEmpty()) return null;

        MarkupContent content = MarkupContent.markdown(info.getMarkdownContent());
        com.cocode.vcode.ide.core.lsp.LspRange lr = info.getRange();
        com.cocode.vcode.ide.core.lsp.model.LspRange modelRange = null;
        if (lr != null && lr.start != null && lr.end != null) {
            modelRange = new com.cocode.vcode.ide.core.lsp.model.LspRange(
                    new com.cocode.vcode.ide.core.lsp.model.LspPosition(lr.start.line, lr.start.character),
                    new com.cocode.vcode.ide.core.lsp.model.LspPosition(lr.end.line, lr.end.character)
            );
        }
        return new Hover(content, modelRange);
    }

    @Override
    public HoverInfo hover(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;
        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return null;

        // Check if inside embedded region
        List<HtmlEmbeddedProjector.EmbeddedRegion> regions = HtmlEmbeddedProjector.findEmbeddedRegions(doc.text);
        HtmlEmbeddedProjector.EmbeddedRegion reg = HtmlEmbeddedProjector.getRegionAt(regions, offset);
        if (reg != null) {
            if (reg.type == HtmlEmbeddedProjector.EmbeddedRegion.TYPE_CSS && cssServer != null) {
                LspDocument vCss = HtmlEmbeddedProjector.createVirtualCssDocument(doc, regions);
                if (vCss != null) return cssServer.hover(vCss, pos);
            } else if (reg.type == HtmlEmbeddedProjector.EmbeddedRegion.TYPE_JS && jsServer != null) {
                LspDocument vJs = HtmlEmbeddedProjector.createVirtualJsDocument(doc, regions);
                if (vJs != null) return jsServer.hover(vJs, pos);
            }
        }

        HtmlTokenStream stream = HtmlLexer.tokenize(doc.text);
        ParseResult result = HtmlParser.parse(doc.text, stream);
        HtmlSyntaxTree tree = result != null ? result.htmlTree : null;
        if (tree == null) return null;

        for (int i = 1; i < tree.nodeCount; i++) {
            if (offset >= tree.nodeStart[i] && offset <= tree.nodeEnd[i]) {
                if (tree.nodeType[i] == HtmlSyntaxTree.N_ELEMENT) {
                    String tagName = tree.nodeName[i];
                    if (tagName != null) {
                        int tagOpenStart = tree.nodeStart[i];
                        int tagOpenEnd = tagOpenStart + tagName.length() + 1;
                        boolean onOpeningTag = offset >= tagOpenStart && offset <= tagOpenEnd;

                        boolean onClosingTag = false;
                        int closeTagStart = -1;
                        if (tree.nodeEnd[i] > tagOpenEnd + tagName.length() + 2) {
                            int potentialClose = tree.nodeEnd[i] - tagName.length() - 3;
                            if (potentialClose >= 0 && potentialClose + 1 < doc.text.length()
                                    && doc.text.charAt(potentialClose) == '<'
                                    && doc.text.charAt(potentialClose + 1) == '/') {
                                onClosingTag = offset >= potentialClose && offset <= tree.nodeEnd[i];
                                closeTagStart = potentialClose;
                            }
                        }

                        if (onOpeningTag || onClosingTag) {
                            String md = HtmlDocumentationProvider.getElementMarkdown(tagName);
                            if (md != null) {
                                LspRange range = new LspRange(
                                        doc.toPosition(onOpeningTag ? tagOpenStart : closeTagStart),
                                        doc.toPosition(onOpeningTag ? tagOpenEnd : tree.nodeEnd[i]));
                                return new HoverInfo(range, md);
                            }
                        }
                    }
                } else if (tree.nodeType[i] == HtmlSyntaxTree.N_ATTRIBUTE) {
                    String attrName = tree.nodeName[i];
                    if (attrName != null) {
                        String parentTag = null;
                        int parent = tree.nodeParent[i];
                        if (parent > 0 && tree.nodeType[parent] == HtmlSyntaxTree.N_ELEMENT) {
                            parentTag = tree.nodeName[parent];
                        }
                        int valStart = tree.nodeExtra[i];
                        String attrVal = tree.nodeValue[i];

                        if (attrVal != null && valStart > 0 && offset >= valStart) {
                            String valMd = HtmlDocumentationProvider.getAttributeValueMarkdown(parentTag, attrName, attrVal);
                            if (valMd != null) {
                                LspRange range = new LspRange(
                                        doc.toPosition(valStart),
                                        doc.toPosition(tree.nodeEnd[i]));
                                return new HoverInfo(range, valMd);
                            }
                        }

                        String md = HtmlDocumentationProvider.getAttributeMarkdown(parentTag, attrName);
                        if (KnownElements.DEPRECATED_ATTRIBUTES.containsKey(attrName.toLowerCase())) {
                            md += "\n\n⚠️ **Deprecated attribute:** " + KnownElements.DEPRECATED_ATTRIBUTES.get(attrName.toLowerCase());
                        }
                        LspRange range = new LspRange(
                                doc.toPosition(tree.nodeStart[i]),
                                doc.toPosition(tree.nodeStart[i] + attrName.length()));
                        return new HoverInfo(range, md);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos) {
        return null;
    }

    @Override
    public List<LspLocation> rename(LspDocument doc, LspPosition pos) {
        return Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    public static File uriToFile(String uri) {
        if (uri == null || uri.trim().isEmpty()) return null;
        String path = uri.trim();
        if (path.startsWith("file:///")) {
            path = path.substring(7);
            if (path.length() > 2 && path.charAt(0) == '/' && Character.isLetter(path.charAt(1)) && path.charAt(2) == ':') {
                path = path.substring(1);
            }
        } else if (path.startsWith("file://")) {
            path = path.substring(7);
        }
        return new File(path);
    }

    private static String stripQuotes(String str) {
        if (str == null || str.length() < 2) return str != null ? str : "";
        char first = str.charAt(0);
        char last = str.charAt(str.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }

    private static String findClassAtOffset(String text, int attrStart, int attrEnd, String rawVal, int offset) {
        String val = stripQuotes(rawVal);
        int valStart = text.indexOf(val, attrStart);
        if (valStart == -1 || valStart > attrEnd) valStart = attrStart;

        int relOffset = offset - valStart;
        String[] parts = val.split("\\s+");
        int pos = 0;
        for (String p : parts) {
            if (p.isEmpty()) continue;
            int pStart = val.indexOf(p, pos);
            if (pStart == -1) pStart = pos;
            int pEnd = pStart + p.length();
            if (relOffset >= pStart && relOffset <= pEnd) {
                return p;
            }
            pos = pEnd;
        }
        for (String p : parts) {
            if (!p.trim().isEmpty()) return p.trim();
        }
        return null;
    }

    private LspLocation resolveFileReference(String path, LspDocument doc) {
        if (path == null || path.trim().isEmpty()) return null;
        String trimmed = path.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return null;

        File hostFile = uriToFile(doc.uri);
        File base = hostFile != null ? hostFile.getParentFile() : null;
        if (base == null) return null;
        File target = new File(base, trimmed);
        if (target.exists() && target.isFile()) {
            return new LspLocation(target.getAbsolutePath(), new LspRange(0, 0, 0, 0));
        }
        return null;
    }

    private LspLocation findIdUsageInJs(String idValue) {
        if (projectIndex == null || idValue == null) return null;
        String trimmed = idValue.trim();
        if (trimmed.isEmpty()) return null;
        String pattern = "getElementById(\"" + trimmed + "\")";
        for (String uri : projectIndex.getAllUris()) {
            if (!uri.endsWith(".js") && !uri.endsWith(".ts")) continue;

            LspDocument doc = projectIndex.getDocument(uri);
            if (doc == null || doc.text == null) continue;
            int idx = doc.text.indexOf(pattern);
            if (idx >= 0) {
                LspPosition pos = SymbolExtractor.offsetToPosition(doc.text, idx);
                return new LspLocation(uri, new LspRange(pos, new LspPosition(pos.line, pos.character + pattern.length())));
            }
        }
        return null;
    }

    private LspLocation findCssRule(String className) {
        if (projectIndex == null || className == null) return null;
        String trimmed = className.trim();
        if (trimmed.isEmpty()) return null;
        String cssSelector = "." + trimmed;
        for (SymbolEntry sym : projectIndex.findSymbolsByPrefix(cssSelector)) {
            if (sym.kind == SymbolEntry.KIND_CSS_CLASS && sym.name.equals(cssSelector)) {
                return new LspLocation(sym.uri, sym.range);
            }
        }
        return null;
    }

    private List<LspLocation> findUsagesInProject(String name, boolean isId) {
        List<LspLocation> result = new ArrayList<>();
        if (projectIndex == null || name == null) return result;
        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) return result;

        final int MAX_REFS = 100;
        for (String uri : projectIndex.getAllUris()) {
            if (result.size() >= MAX_REFS) break;
            LspDocument d = projectIndex.getDocument(uri);
            if (d == null || d.text == null) continue;

            if (uri.endsWith(".html") || uri.endsWith(".htm")) {
                String searchTerm = isId ? "id=\"" + trimmedName + "\"" : trimmedName;
                if (searchTerm.isEmpty()) continue;
                int step = Math.max(1, searchTerm.length());
                int idx = d.text.indexOf(searchTerm);
                while (idx >= 0 && result.size() < MAX_REFS) {
                    LspPosition refPos = SymbolExtractor.offsetToPosition(d.text, idx);
                    result.add(new LspLocation(uri, new LspRange(refPos, new LspPosition(refPos.line, refPos.character + searchTerm.length()))));
                    idx = d.text.indexOf(searchTerm, idx + step);
                }
            } else if (uri.endsWith(".js") || uri.endsWith(".ts")) {
                String searchTerm = isId ? trimmedName : "." + trimmedName;
                if (searchTerm.isEmpty()) continue;
                int step = Math.max(1, searchTerm.length());
                int idx = d.text.indexOf(searchTerm);
                while (idx >= 0 && result.size() < MAX_REFS) {
                    LspPosition refPos = SymbolExtractor.offsetToPosition(d.text, idx);
                    result.add(new LspLocation(uri, new LspRange(refPos, new LspPosition(refPos.line, refPos.character + searchTerm.length()))));
                    idx = d.text.indexOf(searchTerm, idx + step);
                }
            }
        }
        return result;
    }

    private static CompletionItemKind mapCompletionKind(int kind) {
        return CompletionItemKind.fromValue(kind);
    }
}
