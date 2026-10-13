package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.language.md.MarkdownAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.md.MarkdownDocumentationProvider;
import com.cocode.vcode.ide.core.language.md.MarkdownFormatter;
import com.cocode.vcode.ide.core.language.md.MarkdownLinter;
import com.cocode.vcode.ide.core.language.md.MdLexer;
import com.cocode.vcode.ide.core.language.md.MdLineStream;
import com.cocode.vcode.ide.core.language.md.MdParser;
import com.cocode.vcode.ide.core.language.md.MdSyntaxTree;
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
import com.cocode.vcode.ide.core.lsp.model.ReferenceParams;
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
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-process Language Server for Markdown files implementing full LSP 3.17 capabilities.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Document Synchronization</b>: didOpen, didChange, didSave, didClose with LspDocumentManager.</li>
 *   <li><b>Link & Anchor Validation</b>: Real-time diagnostics verifying local files and heading anchor slugs.</li>
 *   <li><b>Heading Hierarchy Outline</b>: Nested H1 → H2 → H3 outline tree for breadcrumbs and symbol navigation.</li>
 *   <li><b>Completions</b>: Fenced code block languages, local heading anchors, file paths, and snippets.</li>
 *   <li><b>Code Actions</b>: Quick fixes to create missing target files, fix broken anchor slugs, and close code blocks.</li>
 *   <li><b>Hover Documentation</b>: Rich Markdown cards for links, images, headings, and code blocks.</li>
 *   <li><b>Formatting</b>: Full-document atomic formatting via MarkdownFormatter.</li>
 *   <li><b>Go to Definition & References</b>: Cross-link jump targets and inbound heading anchor reference discovery.</li>
 * </ul>
 */
public final class MarkdownLspServer implements LspServer {

    private static final Pattern LINK_PATTERN = Pattern.compile("(?<!!)\\[([^\\]]+)\\]\\(([^)]*)\\)");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]*)\\)");

    private static final ServerCapabilities CAPABILITIES = new ServerCapabilities.Builder()
            .setHoverProvider(true)
            .setCompletionProvider(true)
            .setDefinitionProvider(true)
            .setReferencesProvider(true)
            .setDocumentHighlightProvider(false)
            .setDocumentSymbolProvider(true)
            .setCodeActionProvider(true)
            .setFormattingProvider(true)
            .setRangeFormattingProvider(false)
            .setFoldingRangeProvider(true)
            .setSignatureHelpProvider(false)
            .setSemanticTokensProvider(false)
            .build();

    public static final String[][] CODE_BLOCK_LANGUAGES = MarkdownAutoCompleteEngine.CODE_BLOCK_LANGUAGES;

    private final MarkdownAutoCompleteEngine markdownEngine;
    private final ConcurrentHashMap<String, LspDocument> openDocs = new ConcurrentHashMap<>();
    private volatile boolean ready = false;
    private ProjectIndex projectIndex;

    public MarkdownLspServer(Context context) {
        this.markdownEngine = new MarkdownAutoCompleteEngine(context);
    }

    public MarkdownLspServer() {
        this(null);
    }

    // -------------------------------------------------------------------------
    // Lifecycle & Capabilities
    // -------------------------------------------------------------------------

    @Override
    public void initialize(ProjectIndex index) {
        this.projectIndex = index;
        this.ready = true;
    }

    @Override
    public void initialize(InitializeParams params) {
        this.ready = true;
        if (params != null && params.rootUri != null) {
            File root = uriToFile(params.rootUri);
            if (root != null) {
                markdownEngine.setWorkspaceRoot(root);
            }
        }
    }

    @Override
    public void shutdown() {
        this.ready = false;
        openDocs.clear();
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String getLanguageId() {
        return "markdown";
    }

    @Override
    public String getTriggerCharacters() {
        return "[(#*`!|-/.\"<";
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
        LspDocument doc = new LspDocument(document.uri, document.text, document.languageId != null ? document.languageId : "markdown", document.version);
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

        LspDocument updated = new LspDocument(document.uri, currentText, "markdown", document.version);
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

        File file = doc.uri != null ? uriToFile(doc.uri) : null;
        if (file != null) {
            markdownEngine.setCurrentFile(file);
        }

        List<CompletionItem> legacy = markdownEngine.getSuggestions(doc.text, flatOffset);
        List<com.cocode.vcode.ide.core.lsp.model.CompletionItem> results = new ArrayList<>(legacy.size());
        for (CompletionItem ci : legacy) {
            if (token != null && token.isCancelled()) return Collections.emptyList();
            results.add(convertToModelItem(ci, doc, flatOffset));
        }
        return results;
    }

    private com.cocode.vcode.ide.core.lsp.model.CompletionItem convertToModelItem(CompletionItem ci, LspDocument doc, int flatOffset) {
        String label = ci.getLabel();
        String insertText = ci.getEffectiveInsertText();
        CompletionItemKind kind = mapModelKind(ci.getType());
        String detail = ci.getDetail();
        com.cocode.vcode.ide.core.lsp.model.CompletionItem.Builder b =
                com.cocode.vcode.ide.core.lsp.model.CompletionItem.builder(label)
                        .setInsertText(insertText)
                        .setDetail(detail)
                        .setKind(kind);

        // Align filter text so typing after '#' matches the slug query directly without mismatching
        String filterText = (insertText != null && !insertText.isEmpty())
                ? insertText.replaceFirst("^#", "")
                : (label != null ? label.replaceFirst("^#", "") : "");
        b.setFilterText(filterText);

        if (ci.getReplaceLength() >= 0 && doc != null) {
            int startOff = Math.max(0, flatOffset - ci.getReplaceLength());
            String editInsertText = insertText;
            if (editInsertText != null && editInsertText.startsWith("#")
                    && startOff > 0 && doc.text.charAt(startOff - 1) == '#') {
                editInsertText = editInsertText.substring(1);
            }
            b.setTextEdit(new TextEdit(
                    new com.cocode.vcode.ide.core.lsp.model.LspRange(doc.toModelPosition(startOff), doc.toModelPosition(flatOffset)),
                    editInsertText
            ));
        }
        return b.build();
    }

    private CompletionItemKind mapModelKind(CompletionItem.Type type) {
        if (type == null) return CompletionItemKind.TEXT;
        switch (type) {
            case KEYWORD:
                return CompletionItemKind.KEYWORD;
            case VALUE:
            case CSS_VALUE:
                return CompletionItemKind.REFERENCE;
            case SNIPPET:
                return CompletionItemKind.SNIPPET;
            case ATTRIBUTE:
            case CSS_PROPERTY:
            case JSON_KEY:
                return CompletionItemKind.PROPERTY;
            case FUNCTION:
            case BUILTIN:
                return CompletionItemKind.FUNCTION;
            case TAG:
                return CompletionItemKind.CLASS;
            case FILE:
                return CompletionItemKind.FILE;
            case FOLDER:
                return CompletionItemKind.FOLDER;
            default:
                return CompletionItemKind.TEXT;
        }
    }

    @Override
    public List<LspCompletionItem> completion(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null) return Collections.emptyList();

        int flatOffset = doc.toOffset(pos);
        if (flatOffset < 0) flatOffset = doc.text.length();

        File file = doc.uri != null ? uriToFile(doc.uri) : null;
        if (file != null) {
            markdownEngine.setCurrentFile(file);
        }

        List<CompletionItem> legacy = markdownEngine.getSuggestions(doc.text, flatOffset);
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
            result.add(new Diagnostic(range, sev, diagCode, "markdown", p.getMessage()));
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
            return MarkdownLinter.analyze(docFile, doc.text);
        } catch (Throwable t) {
            return Collections.emptyList();
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
            // Collect all heading slugs in document for fuzzy-matching broken anchors
            Set<String> headingSlugs = new HashSet<>();
            MdLineStream stream = MdLexer.lex(doc.text);
            MdSyntaxTree tree = MdParser.parseBlocks(stream, doc.text);
            List<MdParser.HeadingSlug> slugs = MdParser.extractHeadingSlugs(tree, doc.text);
            for (MdParser.HeadingSlug hs : slugs) {
                headingSlugs.add(hs.slug);
            }

            for (Diagnostic diag : params.context.diagnostics) {
                if (token != null && token.isCancelled()) break;
                String msg = diag.message != null ? diag.message : "";

                // 1. Create missing relative file
                if (msg.contains("Broken link: target file '") || msg.contains("Broken image link: target file '")) {
                    String missingPath = extractQuotedPath(msg);
                    if (!missingPath.isEmpty() && doc.uri != null) {
                        File docFile = uriToFile(doc.uri);
                        File parent = docFile != null ? docFile.getParentFile() : null;
                        if (parent != null) {
                            File targetFile = new File(parent, missingPath);
                            String targetUri = targetFile.toURI().toString();
                            TextEdit initEdit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(0, 0, 0, 0),
                                    "# " + targetFile.getName() + "\n\n");
                            WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(targetUri, Collections.singletonList(initEdit)));
                            actions.add(new CodeAction("Create missing file '" + missingPath + "'",
                                    CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
                        }
                    }
                }

                // 2. Fix broken anchor slug
                if (msg.contains("Broken anchor: '")) {
                    String brokenSlug = extractQuotedPath(msg);
                    if (brokenSlug.startsWith("#")) brokenSlug = brokenSlug.substring(1);
                    String bestSlug = findBestMatchingSlug(brokenSlug, headingSlugs);
                    if (bestSlug != null) {
                        int startOffset = doc.toOffset(diag.range.start);
                        int endOffset = doc.toOffset(diag.range.end);
                        if (startOffset >= 0 && endOffset > startOffset && endOffset <= doc.text.length()) {
                            String linkSlice = doc.text.substring(startOffset, endOffset);
                            int hashIdx = linkSlice.indexOf('#');
                            if (hashIdx >= 0) {
                                int anchorStart = startOffset + hashIdx;
                                int parenEnd = linkSlice.indexOf(')', hashIdx);
                                int anchorEnd = (parenEnd >= 0) ? startOffset + parenEnd : endOffset;

                                TextEdit edit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(
                                        doc.toModelPosition(anchorStart),
                                        doc.toModelPosition(anchorEnd)
                                ), "#" + bestSlug);
                                WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                                actions.add(new CodeAction("Fix anchor to '#" + bestSlug + "'",
                                        CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
                            }
                        }
                    }
                }

                // 3. Close unclosed code block
                if (msg.contains("Unclosed code block")) {
                    com.cocode.vcode.ide.core.lsp.model.LspPosition eof = doc.toModelPosition(doc.text.length());
                    TextEdit edit = new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(eof, eof), "\n```\n");
                    WorkspaceEdit wsEdit = new WorkspaceEdit(Collections.singletonMap(doc.uri, Collections.singletonList(edit)));
                    actions.add(new CodeAction("Close code block",
                            CodeActionKind.QUICKFIX, Collections.singletonList(diag), true, wsEdit, null, null));
                }
            }
        }
        return actions;
    }

    private static String extractQuotedPath(String msg) {
        if (msg == null) return "";
        int q1 = msg.indexOf('\'');
        if (q1 >= 0) {
            int q2 = msg.indexOf('\'', q1 + 1);
            if (q2 > q1) return msg.substring(q1 + 1, q2);
        }
        return "";
    }

    private static String findBestMatchingSlug(String targetSlug, Set<String> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String c : candidates) {
            if (c.contains(targetSlug) || targetSlug.contains(c)) {
                return c;
            }
            int dist = levenshtein(targetSlug, c);
            if (dist < bestDist && dist <= 4) {
                bestDist = dist;
                best = c;
            }
        }
        return best;
    }

    private static int levenshtein(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
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

        // 1. Check relative file links [text](path) or anchors [text](#anchor)
        Matcher lm = LINK_PATTERN.matcher(doc.text);
        while (lm.find()) {
            if (offset >= lm.start() && offset <= lm.end()) {
                String label = lm.group(1);
                String target = lm.group(2).trim();

                StringBuilder sb = new StringBuilder();
                if (target.startsWith("#")) {
                    String slug = target.substring(1).trim().toLowerCase();
                    sb.append("### Heading Anchor\n\n");
                    sb.append("**Target:** `").append(target).append("`\n\n");

                    // Extract heading title and section preview if found
                    MdLineStream stream = MdLexer.lex(doc.text);
                    MdSyntaxTree tree = MdParser.parseBlocks(stream, doc.text);
                    List<MdParser.HeadingSlug> slugs = MdParser.extractHeadingSlugs(tree, doc.text);
                    for (MdParser.HeadingSlug hs : slugs) {
                        if (slug.equals(hs.slug)) {
                            sb.append("**Section:** ").append(hs.title).append(" (H").append(hs.level).append(")\n\n");
                            int nextLine = doc.text.indexOf('\n', hs.startOffset);
                            if (nextLine != -1 && nextLine < doc.text.length()) {
                                int lineEnd = doc.text.indexOf('\n', nextLine + 1);
                                if (lineEnd == -1) lineEnd = doc.text.length();
                                String preview = doc.text.substring(nextLine + 1, lineEnd).trim();
                                if (!preview.isEmpty()) {
                                    sb.append("> ").append(preview).append("\n\n");
                                }
                            }
                            break;
                        }
                    }
                } else if (!target.startsWith("http://") && !target.startsWith("https://") && doc.uri != null) {
                    sb.append("### Relative File Link\n\n");
                    sb.append("**Path:** `").append(target).append("`\n\n");
                    File parent = new File(doc.uri).getParentFile();
                    if (parent != null) {
                        File targetFile = new File(parent, target.split("\\s+")[0]);
                        if (targetFile.exists()) {
                            sb.append("**Status:** Exists on disk (").append(targetFile.length()).append(" bytes)\n\n");
                        } else {
                            sb.append("**Status:** Missing on disk\n\n");
                        }
                    }
                } else {
                    sb.append("### Markdown Link\n\n");
                    sb.append("**Target:** `").append(target).append("`\n\n");
                }

                sb.append("**Label:** ").append(label);
                com.cocode.vcode.ide.core.lsp.model.LspRange range = new com.cocode.vcode.ide.core.lsp.model.LspRange(
                        doc.toModelPosition(lm.start()),
                        doc.toModelPosition(lm.end())
                );
                return new Hover(new MarkupContent("markdown", sb.toString()), range);
            }
        }

        // 2. Delegate to MarkdownDocumentationProvider (images, code fences, blockquotes, headings)
        String md = MarkdownDocumentationProvider.getMarkdownDoc(doc.text, offset);
        if (md != null) {
            com.cocode.vcode.ide.core.lsp.model.LspRange range = new com.cocode.vcode.ide.core.lsp.model.LspRange(
                    params.position,
                    params.position
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

        String md = MarkdownDocumentationProvider.getMarkdownDoc(doc.text, offset);
        if (md != null) {
            LspRange range = new LspRange(pos, pos);
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

        MdLineStream stream = MdLexer.lex(doc.text);
        MdSyntaxTree tree = MdParser.parseBlocks(stream, doc.text);
        if (tree == null) return Collections.emptyList();

        // 1. Collect all headings in document order
        List<HeadingEntry> headings = new ArrayList<>();
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == MdSyntaxTree.N_HEADER) {
                String title = tree.nodeName[i];
                if (title == null || title.isEmpty()) {
                    title = "Heading";
                }
                int level = Math.max(1, Math.min(6, tree.nodeExtra[i]));
                int startOffset = tree.nodeStart[i];
                int endOffset = tree.nodeEnd[i];
                headings.add(new HeadingEntry(title, level, startOffset, endOffset));
            }
        }

        if (headings.isEmpty()) return Collections.emptyList();

        // 2. Compute full section range for each heading (until next heading of same or higher level, or EOF)
        for (int i = 0; i < headings.size(); i++) {
            HeadingEntry curr = headings.get(i);
            int sectionEnd = doc.text.length();
            for (int j = i + 1; j < headings.size(); j++) {
                HeadingEntry next = headings.get(j);
                if (next.level <= curr.level) {
                    sectionEnd = next.startOffset;
                    break;
                }
            }
            curr.sectionEnd = sectionEnd;
        }

        // 3. Build strictly nested tree using stack
        List<HeadingNode> roots = new ArrayList<>();
        List<HeadingNode> stack = new ArrayList<>();

        for (HeadingEntry h : headings) {
            if (token != null && token.isCancelled()) break;

            HeadingNode node = new HeadingNode(h);
            while (!stack.isEmpty() && stack.get(stack.size() - 1).entry.level >= h.level) {
                stack.remove(stack.size() - 1);
            }

            if (stack.isEmpty()) {
                roots.add(node);
            } else {
                stack.get(stack.size() - 1).children.add(node);
            }

            stack.add(node);
        }

        List<DocumentSymbol> rootSymbols = new ArrayList<>(roots.size());
        for (HeadingNode root : roots) {
            rootSymbols.add(toDocumentSymbol(doc, root));
        }

        return rootSymbols;
    }

    private static DocumentSymbol toDocumentSymbol(LspDocument doc, HeadingNode node) {
        List<DocumentSymbol> childSymbols = new ArrayList<>(node.children.size());
        for (HeadingNode child : node.children) {
            childSymbols.add(toDocumentSymbol(doc, child));
        }

        com.cocode.vcode.ide.core.lsp.model.LspRange selRange = new com.cocode.vcode.ide.core.lsp.model.LspRange(
                doc.toModelPosition(node.entry.startOffset),
                doc.toModelPosition(node.entry.endOffset)
        );
        com.cocode.vcode.ide.core.lsp.model.LspRange fullRange = new com.cocode.vcode.ide.core.lsp.model.LspRange(
                doc.toModelPosition(node.entry.startOffset),
                doc.toModelPosition(node.entry.sectionEnd)
        );

        return new DocumentSymbol(
                node.entry.title,
                "H" + node.entry.level,
                SymbolKind.STRING,
                null,
                fullRange,
                selRange,
                childSymbols
        );
    }

    private static class HeadingEntry {
        final String title;
        final int level;
        final int startOffset;
        final int endOffset;
        int sectionEnd;

        HeadingEntry(String title, int level, int startOffset, int endOffset) {
            this.title = title;
            this.level = level;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }
    }

    private static class HeadingNode {
        final HeadingEntry entry;
        final List<HeadingNode> children = new ArrayList<>();

        HeadingNode(HeadingEntry entry) {
            this.entry = entry;
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

        String formatted = new MarkdownFormatter().format(doc.text);
        if (formatted != null && !doc.text.endsWith("\n") && formatted.endsWith("\n")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted == null || formatted.equals(doc.text)) return Collections.emptyList();

        com.cocode.vcode.ide.core.lsp.model.LspPosition start = new com.cocode.vcode.ide.core.lsp.model.LspPosition(0, 0);
        com.cocode.vcode.ide.core.lsp.model.LspPosition end = doc.toModelPosition(doc.text.length());
        return Collections.singletonList(new TextEdit(new com.cocode.vcode.ide.core.lsp.model.LspRange(start, end), formatted));
    }

    // -------------------------------------------------------------------------
    // Go to Definition & References (LSP 3.17 & Legacy)
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
        if (doc == null || doc.text == null || doc.uri == null || pos == null) {
            return null;
        }

        int offset = doc.toOffset(pos);
        if (offset < 0) {
            return null;
        }

        File docFile = uriToFile(doc.uri);
        File parent = docFile != null ? docFile.getParentFile() : null;

        Matcher matcher = LINK_PATTERN.matcher(doc.text);
        while (matcher.find()) {
            if (offset >= matcher.start() && offset <= matcher.end()) {
                String linkTarget = matcher.group(2).trim();

                // 1. Heading anchor jump
                if (linkTarget.startsWith("#")) {
                    String slug = linkTarget.substring(1).trim().toLowerCase(Locale.ROOT);
                    MdLineStream stream = MdLexer.lex(doc.text);
                    MdSyntaxTree tree = MdParser.parseBlocks(stream, doc.text);
                    List<MdParser.HeadingSlug> slugs = MdParser.extractHeadingSlugs(tree, doc.text);
                    for (MdParser.HeadingSlug hs : slugs) {
                        if (slug.equals(hs.slug)) {
                            int hLine = LinterUtils.getLine(doc.text, hs.startOffset) - 1;
                            return new LspLocation(doc.uri, new LspRange(hLine, 0, hLine, 0));
                        }
                    }
                    return null;
                }

                if (linkTarget.startsWith("http://") || linkTarget.startsWith("https://")) {
                    return null;
                }

                // 2. Relative file jump
                if (parent == null) return null;
                String filePath = linkTarget.split("\\s+")[0];
                int anchorIndex = filePath.indexOf('#');
                if (anchorIndex != -1) {
                    filePath = filePath.substring(0, anchorIndex);
                }

                if (filePath.isEmpty()) {
                    return null;
                }

                File targetFile = new File(parent, filePath);
                if (targetFile.exists()) {
                    return new LspLocation(targetFile.getAbsolutePath(), new LspRange(0, 0, 0, 0));
                }
                return null;
            }
        }

        return null;
    }

    @Override
    public List<LspLocation> references(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || doc.uri == null || pos == null) {
            return Collections.emptyList();
        }

        int offset = doc.toOffset(pos);
        if (offset < 0) return Collections.emptyList();

        // 1. Check if cursor is on a heading to find all inbound anchor links
        MdLineStream stream = MdLexer.lex(doc.text);
        MdSyntaxTree tree = MdParser.parseBlocks(stream, doc.text);
        List<MdParser.HeadingSlug> slugs = MdParser.extractHeadingSlugs(tree, doc.text);
        String targetSlug = null;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == MdSyntaxTree.N_HEADER && offset >= tree.nodeStart[i] && offset <= tree.nodeEnd[i]) {
                for (MdParser.HeadingSlug hs : slugs) {
                    if (hs.startOffset == tree.nodeStart[i]) {
                        targetSlug = hs.slug;
                        break;
                    }
                }
                break;
            }
        }

        if (targetSlug == null) {
            // Check if cursor is on an anchor link directly
            Matcher matcher = LINK_PATTERN.matcher(doc.text);
            while (matcher.find()) {
                if (offset >= matcher.start() && offset <= matcher.end()) {
                    String target = matcher.group(2).trim();
                    if (target.startsWith("#")) {
                        targetSlug = target.substring(1).trim().toLowerCase();
                    }
                    break;
                }
            }
        }

        if (targetSlug == null || targetSlug.isEmpty()) {
            return Collections.emptyList();
        }

        List<LspLocation> locations = new ArrayList<>();
        Matcher matcher = LINK_PATTERN.matcher(doc.text);
        while (matcher.find()) {
            String target = matcher.group(2).trim();
            if (target.startsWith("#")) {
                String slug = target.substring(1).trim().toLowerCase();
                if (targetSlug.equals(slug)) {
                    int line = LinterUtils.getLine(doc.text, matcher.start()) - 1;
                    int col = LinterUtils.getColumn(doc.text, matcher.start()) - 1;
                    int len = matcher.end() - matcher.start();
                    locations.add(new LspLocation(doc.uri, new LspRange(line, col, line, col + len)));
                }
            }
        }
        return locations;
    }

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos) {
        return null;
    }

    @Override
    public List<LspLocation> rename(LspDocument doc, LspPosition pos) {
        return Collections.emptyList();
    }

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
}
