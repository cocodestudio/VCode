package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.language.md.MarkdownAutoCompleteEngine;
import com.cocode.vcode.ide.core.lsp.LspCompletionConverter;
import com.cocode.vcode.ide.core.lsp.LspCompletionItem;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspRange;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.lsp.SymbolExtractor;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.Problem;

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

    private final MarkdownAutoCompleteEngine markdownEngine;
    private volatile boolean ready = false;

    public MarkdownLspServer(Context context) {
        this.markdownEngine = new MarkdownAutoCompleteEngine(context);
    }

    /**
     * No-arg constructor for backwards compatibility.
     */
    public MarkdownLspServer() {
        this(null);
    }

    public static List<LspCompletionItem> convertCompletions(List<CompletionItem> legacy) {
        return LspCompletionConverter.convert(legacy);
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

        // MarkdownAutoCompleteEngine requires a File for file-relative src/href resolution.
        File file = new File(doc.uri);
        markdownEngine.setCurrentFile(file);

        List<com.cocode.vcode.ide.core.model.CompletionItem> legacy = markdownEngine.getSuggestions(doc.text, flatOffset);
        return convertCompletions(legacy);
    }

    @Override
    public List<Problem> diagnostics(LspDocument doc) {
        if (doc == null || doc.text == null || doc.uri == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            File docFile = new File(doc.uri);
            File parent = docFile.getParentFile();
            if (parent == null) {
                return Collections.emptyList();
            }

            List<Problem> diagnostics = new ArrayList<>();
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
                    int newlineIdx = doc.text.indexOf('\n', matcher.start());
                    int length;
                    if (newlineIdx != -1 && newlineIdx < matcher.end()) {
                        length = newlineIdx - matcher.start();
                    } else {
                        length = matcher.end() - matcher.start();
                    }
                    diagnostics.add(new Problem(
                            docFile,
                            start.line + 1,
                            start.character,
                            Math.max(1, length),
                            "Broken link: " + linkTarget,
                            Problem.Severity.WARNING
                    ));
                }
            }

            return diagnostics;
        } catch (Throwable t) {
            return Collections.emptyList();
        }
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

    @Override
    public java.util.List<LspLocation> rename(LspDocument doc, LspPosition pos) {
        return java.util.Collections.emptyList();
    }
}
