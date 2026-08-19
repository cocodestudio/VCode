package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.lsp.LspCompletionItem;
import com.cocode.vcode.ide.core.lsp.LspDiagnostic;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspRange;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.lsp.SymbolExtractor;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-process Language Server for Markdown files.
 */
public final class MarkdownLspServer implements LspServer {

    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");

    private final com.cocode.vcode.ide.core.autocomplete.PathAutoCompleteEngine pathEngine;
    private volatile boolean ready = false;

    public MarkdownLspServer(Context context) {
        this.pathEngine = new com.cocode.vcode.ide.core.autocomplete.PathAutoCompleteEngine(context);
    }
    
    /**
     * No-arg constructor for backwards compatibility.
     */
    public MarkdownLspServer() {
        this(null);
    }

    @Override
    public void initialize(ProjectIndex index) {
        this.ready = true;
    }

    @Override
    public void shutdown() {
        this.ready = false;
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
    public List<LspCompletionItem> completion(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null) return Collections.emptyList();

        int flatOffset = doc.toOffset(pos);
        if (flatOffset < 0) flatOffset = doc.text.length();

        // PathAutoCompleteEngine requires a File for file-relative src/href resolution.
        File file = new File(doc.uri);
        pathEngine.setCurrentFile(file);

        List<com.cocode.vcode.ide.core.model.CompletionItem> legacy = pathEngine.getSuggestions(doc.text, flatOffset);
        List<LspCompletionItem> result = new ArrayList<>();
        
        // Convert legacy items
        if (legacy != null) {
            for (com.cocode.vcode.ide.core.model.CompletionItem ci : legacy) {
                String insert = ci.getEffectiveInsertText();
                int curOffset = ci.getCursorOffset();
                if (curOffset < 0) {
                    int pipeIdx = insert.length() + curOffset;
                    if (pipeIdx >= 0 && pipeIdx <= insert.length()) {
                        insert = insert.substring(0, pipeIdx) + "|" + insert.substring(pipeIdx);
                    }
                }
                
                int kind;
                if (ci.getType() == com.cocode.vcode.ide.core.model.CompletionItem.Type.FOLDER) {
                    kind = LspCompletionItem.KIND_FOLDER;
                } else if (ci.getType() == com.cocode.vcode.ide.core.model.CompletionItem.Type.FILE) {
                    kind = LspCompletionItem.KIND_FILE;
                } else {
                    kind = LspCompletionItem.KIND_TEXT;
                }
                
                result.add(new LspCompletionItem(
                        ci.getLabel(),
                        insert,
                        kind,
                        ci.getDetail(),
                        null,
                        ci.getReplaceLength()
                ));
            }
        }
        
        // Add Markdown Snippets if triggered by letters
        addMarkdownSnippets(doc.text, flatOffset, result);
        
        return result;
    }

    private void addMarkdownSnippets(String text, int flatOffset, List<LspCompletionItem> result) {
        if (flatOffset <= 0) return;
        
        // Walk back to find the prefix
        int start = flatOffset;
        while (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) {
            start--;
        }
        
        String prefix = text.substring(start, flatOffset).toLowerCase();
        if (prefix.isEmpty()) return;
        
        String[] snippetLabels = {"link", "image", "bold", "italic", "code", "codeblock", "quote", "table"};
        String[] snippetInserts = {
                "[|](url)", 
                "![alt|](url)", 
                "**|**", 
                "*|*", 
                "`|`", 
                "```\n|\n```", 
                "> |", 
                "| Header | Header |\n|--------|--------|\n| |      | |"
        };
        
        for (int i = 0; i < snippetLabels.length; i++) {
            if (snippetLabels[i].startsWith(prefix)) {
                result.add(new LspCompletionItem(
                        snippetLabels[i],
                        snippetInserts[i],
                        LspCompletionItem.KIND_SNIPPET,
                        "Markdown Snippet",
                        null,
                        prefix.length()
                ));
            }
        }
    }

    @Override
    public List<LspDiagnostic> diagnostics(LspDocument doc) {
        if (doc == null || doc.text == null || doc.uri == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        File docFile = new File(doc.uri);
        File parent = docFile.getParentFile();
        if (parent == null) {
            return Collections.emptyList();
        }

        List<LspDiagnostic> diagnostics = new ArrayList<>();
        Matcher matcher = LINK_PATTERN.matcher(doc.text);

        while (matcher.find()) {
            String linkTarget = matcher.group(2).trim();
            if (linkTarget.startsWith("http://") || linkTarget.startsWith("https://") || linkTarget.startsWith("#")) {
                continue;
            }

            String filePath = linkTarget.split("\\s+")[0];
            int anchorIndex = filePath.indexOf('#');
            if (anchorIndex != -1) {
                filePath = filePath.substring(0, anchorIndex);
            }

            if (filePath.isEmpty()) {
                continue;
            }

            File targetFile = new File(parent, filePath);
            if (!targetFile.exists()) {
                LspPosition start = SymbolExtractor.offsetToPosition(doc.text, matcher.start());
                LspPosition end = SymbolExtractor.offsetToPosition(doc.text, matcher.end());
                diagnostics.add(new LspDiagnostic(
                        new LspRange(start, end),
                        LspDiagnostic.SEVERITY_WARNING,
                        "Broken link: " + linkTarget,
                        null,
                        "markdown"
                ));
            }
        }

        return diagnostics;
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

        File parent = new File(doc.uri).getParentFile();
        if (parent == null) {
            return null;
        }

        Matcher matcher = LINK_PATTERN.matcher(doc.text);
        while (matcher.find()) {
            if (offset >= matcher.start() && offset <= matcher.end()) {
                String linkTarget = matcher.group(2).trim();
                if (linkTarget.startsWith("http://") || linkTarget.startsWith("https://") || linkTarget.startsWith("#")) {
                    return null;
                }

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
        return Collections.emptyList();
    }

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos) {
        return null;
    }
}
