package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.language.js.DocCommentParser;
import com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.js.JsLinter;
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
 * In-process Language Server for JavaScript files.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Completions</b>: Delegates to {@link JsAutoCompleteEngine}.</li>
 *   <li><b>Diagnostics</b>: Delegates to {@link JsLinter}.</li>
 *   <li><b>Go to Definition</b>: Resolves {@code import ... from './module'} paths,
 *       then falls back to {@link ProjectIndex} symbol lookup.</li>
 *   <li><b>Find References</b>: Symbol lookup via {@link ProjectIndex}.</li>
 *   <li><b>Signature Help</b>: Context-aware parameter signatures and doc hints for global, prototype, and local functions via {@link JsSignatureParser}.</li>
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

    // -------------------------------------------------------------------------
    // Completions
    // -------------------------------------------------------------------------


    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------


    // -------------------------------------------------------------------------
    // Find References
    // -------------------------------------------------------------------------


    // -------------------------------------------------------------------------
    // Signature Help
    // -------------------------------------------------------------------------

    public static List<LspCompletionItem> convertCompletions(List<CompletionItem> suggestions) {
        return LspCompletionConverter.convert(suggestions);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    @Override
    public void initialize(ProjectIndex index) {
        ready = true;
    }

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

        // Resolve module target path when cursor is on an import statement
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

        // Resolve local symbol definition via ScopeTree
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
        return JsSymbolRenamer.rename(doc, pos);
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
    public com.cocode.vcode.ide.core.lsp.HoverInfo hover(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;
        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return null;

        com.cocode.vcode.ide.core.diagnostic.util.TokenStream tokens = com.cocode.vcode.ide.core.language.js.JsLexer.tokenize(doc.text);
        if (offset < tokens.length) {
            byte type = tokens.types[offset];
            int s = tokens.tokenStart[offset];
            int e = s;
            while (e < tokens.length && tokens.tokenStart[e] == s && tokens.types[e] == type) {
                e++;
            }
            String word = doc.text.substring(s, Math.min(e, doc.text.length())).trim();

            if (type == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_KEYWORD) {
                String kwDoc = com.cocode.vcode.ide.core.language.js.JsDocumentationProvider.resolveIdentifierDoc(word, doc.languageId != null ? doc.languageId : "javascript");
                if (kwDoc != null) {
                    return new com.cocode.vcode.ide.core.lsp.HoverInfo(new LspRange(doc.toPosition(s), doc.toPosition(e)), kwDoc);
                }
                for (com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionItem item : com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader.getJsKeywords()) {
                    if (word.equals(item.label)) {
                        String detail = item.detail != null ? item.detail : "JavaScript Keyword";
                        String md = "### `" + item.label + "`\n\n" + detail;
                        return new com.cocode.vcode.ide.core.lsp.HoverInfo(new LspRange(doc.toPosition(s), doc.toPosition(e)), md);
                    }
                }
                return new com.cocode.vcode.ide.core.lsp.HoverInfo(new LspRange(doc.toPosition(s), doc.toPosition(e)), "### `" + word + "`\n\nJavaScript Keyword");
            }

            com.cocode.vcode.ide.core.language.js.JsSyntaxTree tree = com.cocode.vcode.ide.core.language.js.JsParser.parseFull(doc.text, tokens);
            if (tree != null) {
                com.cocode.vcode.ide.core.language.js.ParseResult localPr =
                        new com.cocode.vcode.ide.core.language.js.ParseResult(null, doc.text, tokens, tree, null, com.cocode.vcode.ide.core.language.js.ParseResult.MODE_FULL);

                for (int i = 1; i < tree.nodeCount; i++) {
                    if (word.equals(tree.nodeName[i])) {
                        boolean isImport = (tree.nodeParent[i] > 0 && tree.nodeType[tree.nodeParent[i]] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_IMPORT)
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
                            } else if (tree.nodeParent[i] > 0 && tree.nodeType[tree.nodeParent[i]] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_IMPORT) {
                                modPath = tree.nodeName[tree.nodeParent[i]];
                                expName = word;
                            }
                            if (modPath != null) {
                                com.cocode.vcode.ide.core.lsp.HoverInfo cross = resolveCrossFileHover(doc, s, e, modPath, expName, word);
                                if (cross != null) return cross;
                            }
                        } else {
                            com.cocode.vcode.ide.core.lsp.HoverInfo local = buildHoverInfoFromNode(doc, s, e, localPr, i, word);
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

            // If part of dot access and receiver is an imported namespace/module
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
                        } else if (tree.nodeParent[r] > 0 && tree.nodeType[tree.nodeParent[r]] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_IMPORT) {
                            modPath = tree.nodeName[tree.nodeParent[r]];
                        }
                        if (modPath != null) {
                            com.cocode.vcode.ide.core.lsp.HoverInfo cross = resolveCrossFileHover(doc, s, e, modPath, word, word);
                            if (cross != null) return cross;
                        }
                    }
                }
            }

            // Check comprehensive JsDocumentationProvider first
            String richDoc = null;
            if (receiver != null) {
                richDoc = com.cocode.vcode.ide.core.language.js.JsDocumentationProvider.resolveMemberDoc(receiver, word, doc.languageId != null ? doc.languageId : "javascript");
            } else {
                richDoc = com.cocode.vcode.ide.core.language.js.JsDocumentationProvider.resolveIdentifierDoc(word, doc.languageId != null ? doc.languageId : "javascript");
            }
            if (richDoc != null) {
                return new com.cocode.vcode.ide.core.lsp.HoverInfo(new LspRange(doc.toPosition(s), doc.toPosition(e)), richDoc);
            }

            // Built-in standard library function / method fallback
            com.cocode.vcode.ide.core.language.js.JsStandardLibrary.SignatureInfo builtin = null;
            if (receiver != null) {
                builtin = com.cocode.vcode.ide.core.language.js.JsStandardLibrary.getBuiltinSignature(receiver + "." + word, null);
            }
            if (builtin == null) {
                builtin = com.cocode.vcode.ide.core.language.js.JsStandardLibrary.getBuiltinSignature(word, null);
            }
            if (builtin == null) {
                for (java.util.Map<String, com.cocode.vcode.ide.core.language.js.JsStandardLibrary.SignatureInfo> cMap : com.cocode.vcode.ide.core.language.js.JsStandardLibrary.SIGNATURES_BY_CONTAINER.values()) {
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
                return new com.cocode.vcode.ide.core.lsp.HoverInfo(new LspRange(doc.toPosition(s), doc.toPosition(e)), sb.toString().trim());
            }

            // Cross-file symbol definition
            com.cocode.vcode.ide.core.lsp.ProjectIndex pIndex = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance();
            if (pIndex != null) {
                java.util.List<com.cocode.vcode.ide.core.lsp.LspLocation> defs = pIndex.findDefinitions(word);
                if (defs != null && !defs.isEmpty()) {
                    for (com.cocode.vcode.ide.core.lsp.LspLocation loc : defs) {
                        if (loc == null || loc.uri == null) continue;
                        if (doc.uri != null && isSameUri(doc.uri, loc.uri)) {
                            continue; // Avoid matching current file
                        }
                        com.cocode.vcode.ide.core.language.js.ParseResult targetPr = getTargetParseResult(pIndex, loc.uri);
                        if (targetPr != null && targetPr.tree != null) {
                            int targetNode = findSymbolNodeInTarget(targetPr.tree, word);
                            if (targetNode > 0) {
                                com.cocode.vcode.ide.core.lsp.HoverInfo info = buildHoverInfoFromNode(doc, s, e, targetPr, targetNode, word);
                                if (info != null) return info;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private com.cocode.vcode.ide.core.lsp.HoverInfo resolveCrossFileHover(
            LspDocument doc, int s, int e, String modPath, String expName, String displayName) {
        if (doc == null || modPath == null || modPath.isEmpty()) return null;
        if ("*".equals(expName)) {
            String md = "```javascript\nnamespace " + displayName + "\n```\n\nModule `" + modPath + "`";
            return new com.cocode.vcode.ide.core.lsp.HoverInfo(new LspRange(doc.toPosition(s), doc.toPosition(e)), md);
        }
        com.cocode.vcode.ide.core.lsp.LspLocation targetLoc =
                com.cocode.vcode.ide.core.lsp.ModuleResolver.resolveModulePath(doc.uri, modPath);
        if (targetLoc == null || targetLoc.uri == null) return null;

        com.cocode.vcode.ide.core.lsp.ProjectIndex pIndex = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance();
        com.cocode.vcode.ide.core.language.js.ParseResult targetPr = getTargetParseResult(pIndex, targetLoc.uri);
        if (targetPr == null || targetPr.tree == null || targetPr.source == null) return null;

        int targetNode = findSymbolNodeInTarget(targetPr.tree, expName);
        if (targetNode > 0) {
            return buildHoverInfoFromNode(doc, s, e, targetPr, targetNode, displayName);
        }
        return null;
    }

    private int findSymbolNodeInTarget(com.cocode.vcode.ide.core.language.js.JsSyntaxTree tree, String expName) {
        if (tree == null || expName == null || expName.isEmpty()) return 0;
        int expNode = com.cocode.vcode.ide.core.language.js.JsExportTable.findExportNode(tree, expName);
        if (expNode <= 0) {
            expNode = com.cocode.vcode.ide.core.language.js.JsExportTable.findTopLevelDecl(tree, expName);
        }
        if (expNode <= 0) {
            for (int k = 1; k < tree.nodeCount; k++) {
                if (expName.equals(tree.nodeName[k])) {
                    int kType = tree.nodeType[k];
                    if (kType == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_FUNC_DECL
                            || kType == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_METHOD
                            || kType == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_ARROW_FUNC
                            || kType == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_CLASS_DECL
                            || kType == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_VAR_DECL
                            || kType == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_INTERFACE
                            || kType == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_ENUM
                            || kType == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_TYPE_ALIAS) {
                        return k;
                    }
                }
            }
        }
        return expNode > 0 ? expNode : 0;
    }

    private com.cocode.vcode.ide.core.language.js.ParseResult getTargetParseResult(
            com.cocode.vcode.ide.core.lsp.ProjectIndex pIndex, String uri) {
        if (pIndex == null || uri == null) return null;
        String cleanUri = uri.startsWith("file://") ? uri.substring(7) : uri;
        LspDocument targetDoc = pIndex.getDocument(uri);
        if (targetDoc == null) {
            targetDoc = pIndex.getDocument(cleanUri);
        }
        com.cocode.vcode.ide.core.language.js.ParseResult pr = pIndex.getParseResult(uri);
        if (pr == null) {
            pr = pIndex.getParseResult(cleanUri);
        }
        if (pr != null && targetDoc != null && targetDoc.text != null && !targetDoc.text.equals(pr.source)) {
            pr = null;
        }
        if (pr == null && targetDoc != null && targetDoc.text != null) {
                com.cocode.vcode.ide.core.diagnostic.util.TokenStream tokens =
                        com.cocode.vcode.ide.core.language.js.JsLexer.tokenize(targetDoc.text);
                com.cocode.vcode.ide.core.language.js.JsSyntaxTree tree =
                        com.cocode.vcode.ide.core.language.js.JsParser.parseFull(targetDoc.text, tokens);
                if (tree != null) {
                    tree.buildNodesByOffset();
                    com.cocode.vcode.ide.core.language.js.ScopeTree scopeTree =
                            com.cocode.vcode.ide.core.language.js.ScopeTree.build(tree);
                    pr = new com.cocode.vcode.ide.core.language.js.ParseResult(
                            new java.io.File(cleanUri), targetDoc.text, tokens, tree, scopeTree,
                            com.cocode.vcode.ide.core.language.js.ParseResult.MODE_FULL);
                    pIndex.updateParseResult(uri, pr);
                    pIndex.updateParseResult(cleanUri, pr);
                }
            }
        if (pr == null) {
            java.io.File f = new java.io.File(cleanUri);
            if (f.exists()) {
                pr = pIndex.getOrParseJsFile(f);
            }
        }
        return pr;
    }

    private com.cocode.vcode.ide.core.lsp.HoverInfo buildHoverInfoFromNode(
            LspDocument doc, int s, int e, com.cocode.vcode.ide.core.language.js.ParseResult targetPr, int node, String displayName) {
        if (targetPr == null || targetPr.tree == null || targetPr.source == null || node <= 0 || node >= targetPr.tree.nodeCount) {
            return null;
        }
        com.cocode.vcode.ide.core.language.js.JsSyntaxTree tree = targetPr.tree;
        String source = targetPr.source;
        int type = tree.nodeType[node];

        int fnOrClassNode = node;
        if (type == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_VAR_DECL) {
            int child = tree.nodeChild[node];
            if (child > 0 && child < tree.nodeCount) {
                int ct = tree.nodeType[child];
                if (ct == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_FUNC_DECL
                        || ct == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_ARROW_FUNC
                        || ct == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_CLASS_DECL) {
                    fnOrClassNode = child;
                    type = ct;
                }
            }
        }

        String sig = null;
        int ctorDocOffset = -1;
        if (type == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_FUNC_DECL
                || type == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_METHOD
                || type == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_ARROW_FUNC) {
            java.util.List<String> params = new java.util.ArrayList<>();
            int pChild = tree.nodeChild[fnOrClassNode];
            while (pChild > 0 && pChild < tree.nodeCount) {
                if (tree.nodeType[pChild] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_PARAM) {
                    params.add(extractParamSignature(tree, pChild, source));
                }
                pChild = tree.nodeSibling[pChild];
            }
            String typeAnn = tree.nodeTypeAnn != null ? tree.nodeTypeAnn[fnOrClassNode] : null;
            sig = "function " + displayName + "(" + String.join(", ", params) + ")";
            if (typeAnn != null && !typeAnn.isEmpty() && !typeAnn.startsWith("@")) {
                sig += ": " + typeAnn;
            }
        } else if (type == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_CLASS_DECL) {
            java.util.List<String> ctorParams = new java.util.ArrayList<>();
            int cChild = tree.nodeChild[fnOrClassNode];
            while (cChild > 0 && cChild < tree.nodeCount) {
                if (tree.nodeType[cChild] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_METHOD && "constructor".equals(tree.nodeName[cChild])) {
                    int pChild = tree.nodeChild[cChild];
                    while (pChild > 0 && pChild < tree.nodeCount) {
                        if (tree.nodeType[pChild] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_PARAM) {
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
        } else if (type == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_VAR_DECL) {
            String typeAnn = tree.nodeTypeAnn != null ? tree.nodeTypeAnn[node] : null;
            sig = getVarKindPrefix(tree, node, source) + displayName;
            if (typeAnn != null && !typeAnn.isEmpty() && !typeAnn.startsWith("@")) {
                sig += ": " + typeAnn;
            }
        } else if (type == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_INTERFACE) {
            sig = "interface " + displayName;
        } else if (type == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_TYPE_ALIAS) {
            sig = "type " + displayName;
        } else if (type == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_ENUM) {
            sig = "enum " + displayName;
        }

        if (sig == null) return null;

        int docOffset = (tree.nodeParent[node] > 0 && tree.nodeType[tree.nodeParent[node]] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_EXPORT)
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

        return new com.cocode.vcode.ide.core.lsp.HoverInfo(new LspRange(doc.toPosition(s), doc.toPosition(e)), md);
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
            return new java.io.File(clean).getAbsolutePath();
        } catch (Exception e) {
            return clean;
        }
    }

    private static String getVarKindPrefix(com.cocode.vcode.ide.core.language.js.JsSyntaxTree tree, int nodeIndex, String source) {
        if (tree != null && tree.nodeExtra != null && nodeIndex >= 0 && nodeIndex < tree.nodeExtra.length) {
            int flag = tree.nodeExtra[nodeIndex] & 3;
            if (flag == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.FLAG_CONST) return "const ";
            if (flag == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.FLAG_VAR) return "var ";
            if (flag == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.FLAG_LET) return "let ";
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

    private static String extractParamSignature(com.cocode.vcode.ide.core.language.js.JsSyntaxTree tree, int pChild, String source) {
        String pName = tree.nodeName[pChild] != null ? tree.nodeName[pChild] : "arg";
        if ((tree.nodeExtra[pChild] & com.cocode.vcode.ide.core.language.js.JsSyntaxTree.FLAG_REST) != 0) {
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
        if ((tree.nodeExtra[pChild] & com.cocode.vcode.ide.core.language.js.JsSyntaxTree.FLAG_DEFAULT) != 0 && pEnd < source.length()) {
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
}
