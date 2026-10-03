package com.cocode.vcode.ide.core.autocomplete;

import android.content.Context;

import com.cocode.vcode.ide.core.language.css.CssAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.html.HtmlAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.json.JsonAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.md.MarkdownAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.svg.SvgAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.ts.TsAutoCompleteEngine;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.FileType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Composite autocomplete engine aggregating completions across multiple
 * {@link CompletionProvider} instances (built-in language engines, project symbol
 * indexes, library dependencies, and third-party extensions).
 */
public class CompositeCompletionEngine extends AutoCompleteEngine {

    private final List<CompletionProvider> customProviders = new CopyOnWriteArrayList<>();
    private FileType currentFileType = FileType.TEXT;
    private AutoCompleteEngine activeLanguageEngine;
    private final CompletionProvider builtInLanguageProvider;

    public CompositeCompletionEngine(Context context) {
        super(context);
        this.builtInLanguageProvider = new BuiltInLanguageProvider();
    }

    public synchronized void setFileType(FileType fileType) {
        this.currentFileType = fileType != null ? fileType : FileType.TEXT;
        updateActiveLanguageEngine();
    }

    public FileType getFileType() {
        return currentFileType;
    }

    private void updateActiveLanguageEngine() {
        if (currentFileType == null) {
            activeLanguageEngine = null;
            return;
        }

        switch (currentFileType) {
            case HTML:
                activeLanguageEngine = new HtmlAutoCompleteEngine(context);
                break;
            case CSS:
            case SCSS:
                activeLanguageEngine = new CssAutoCompleteEngine(context);
                break;
            case JAVASCRIPT:
                activeLanguageEngine = new JsAutoCompleteEngine(context);
                break;
            case TYPESCRIPT:
                activeLanguageEngine = new TsAutoCompleteEngine(context);
                break;
            case JSON:
                activeLanguageEngine = new JsonAutoCompleteEngine(context);
                break;
            case MARKDOWN:
                activeLanguageEngine = new MarkdownAutoCompleteEngine(context);
                break;
            case SVG:
                activeLanguageEngine = new SvgAutoCompleteEngine(context);
                break;
            case GITIGNORE:
                activeLanguageEngine = new PathAutoCompleteEngine(context);
                break;
            default:
                activeLanguageEngine = null;
                break;
        }

        if (activeLanguageEngine != null && currentFile != null) {
            activeLanguageEngine.setCurrentFile(currentFile);
        }
    }

    @Override
    public void setCurrentFile(File file) {
        super.setCurrentFile(file);
        if (activeLanguageEngine != null) {
            activeLanguageEngine.setCurrentFile(file);
        }
    }

    @Override
    public String getTriggerCharacters() {
        if (activeLanguageEngine != null) {
            return activeLanguageEngine.getTriggerCharacters();
        }
        return AutoCompleteEngine.getTriggerCharacters(currentFileType);
    }

    /**
     * Registers a new custom completion provider (e.g. from an extension or dependency indexer).
     */
    public void registerProvider(CompletionProvider provider) {
        if (provider != null && !customProviders.contains(provider)) {
            customProviders.add(provider);
        }
    }

    /**
     * Unregisters a custom completion provider.
     */
    public void unregisterProvider(CompletionProvider provider) {
        if (provider != null) {
            customProviders.remove(provider);
        }
    }

    public List<CompletionProvider> getCustomProviders() {
        return Collections.unmodifiableList(customProviders);
    }

    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length()) {
            return Collections.emptyList();
        }

        String wordBeforeCursor = getWordBeforeCursor(fullText, cursorPos);
        CompletionContext ctx = new CompletionContext(context, fullText, cursorPos,
                currentFileType, currentFile, wordBeforeCursor);

        List<CompletionProvider> allProviders = new ArrayList<>();
        allProviders.add(builtInLanguageProvider);
        allProviders.addAll(customProviders);

        // Sort providers by priority descending
        allProviders.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        List<CompletionItem> aggregatedItems = new ArrayList<>();
        Set<String> seenLabels = new HashSet<>();

        for (CompletionProvider provider : allProviders) {
            try {
                if (provider.canProvide(ctx)) {
                    List<CompletionItem> items = provider.provideCompletions(ctx);
                    if (items != null) {
                        for (CompletionItem item : items) {
                            if (item != null && item.getLabel() != null) {
                                String key = item.getLabel() + "||" + item.getType();
                                if (seenLabels.add(key)) {
                                    aggregatedItems.add(item);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        if (aggregatedItems.isEmpty()) {
            return Collections.emptyList();
        }

        return fuzzyFilter(aggregatedItems, wordBeforeCursor);
    }

    /**
     * Provider wrapping the built-in language engine.
     */
    private class BuiltInLanguageProvider implements CompletionProvider {

        @Override
        public boolean canProvide(CompletionContext context) {
            return activeLanguageEngine != null;
        }

        @Override
        public List<CompletionItem> provideCompletions(CompletionContext context) {
            if (activeLanguageEngine == null) return Collections.emptyList();
            return activeLanguageEngine.getSuggestions(context.getFullText(), context.getCursorPos());
        }

        @Override
        public int getPriority() {
            return PRIORITY_NORMAL;
        }
    }
}
