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

    @Override
    public List<CompletionItem> fuzzyFilter(List<CompletionItem> all, String prefix) {
        if (all == null) return new ArrayList<>();

        if (prefix == null || prefix.isEmpty()) {
            return super.fuzzyFilter(all, prefix);
        }

        String lowerPrefix = prefix.toLowerCase();
        List<CompletionItem> matched = new ArrayList<>();

        for (CompletionItem item : all) {
            if (item == null || item.getLabel() == null) continue;
            int score = computeEnhancedFuzzyScore(item, lowerPrefix);
            if (score > 0) {
                item.setSortScore(score + item.getTypePriority() * 10);
                matched.add(item);
            }
        }

        Collections.sort(matched, (a, b) -> b.getSortScore() - a.getSortScore());
        return matched.size() > MAX_SUGGESTIONS ? matched.subList(0, MAX_SUGGESTIONS) : matched;
    }

    /**
     * Enhanced fuzzy scoring that evaluates the label, effective insertText, and acronym/boundary matches
     * (e.g. "clg" matching "console.log", "rfce" matching "reactFunctionalComponentExport").
     */
    public int computeEnhancedFuzzyScore(CompletionItem item, String lowerQuery) {
        if (item == null || lowerQuery == null || lowerQuery.isEmpty()) return 0;
        String label = item.getLabel();
        int baseScore = computeFuzzyScore(label, lowerQuery);

        // Check acronym / boundary match for dotted/camel-cased words (e.g. "clg" -> "console.log")
        int acronymScore = computeAcronymMatchScore(label, lowerQuery);
        int bestScore = Math.max(baseScore, acronymScore);

        // Check effective insertText if distinct from label
        String insertText = item.getEffectiveInsertText();
        if (insertText != null && !insertText.equals(label)) {
            int insertScore = computeFuzzyScore(insertText, lowerQuery);
            int insertAcronym = computeAcronymMatchScore(insertText, lowerQuery);
            bestScore = Math.max(bestScore, Math.max(insertScore, insertAcronym));
        }

        return bestScore;
    }

    /**
     * Checks if lowerQuery matches the initials or boundary characters of words within text.
     * For example, "clg" matches "console.log" (c in console, l and g in log).
     */
    public static int computeAcronymMatchScore(String text, String lowerQuery) {
        if (text == null || lowerQuery == null || lowerQuery.isEmpty()) return 0;
        String lowerText = text.toLowerCase();
        int queryLen = lowerQuery.length();
        int textLen = lowerText.length();

        // Extract word boundary indices
        List<Integer> boundaries = new ArrayList<>();
        for (int i = 0; i < textLen; i++) {
            if (i == 0) {
                boundaries.add(0);
            } else {
                char prev = text.charAt(i - 1);
                char curr = text.charAt(i);
                if (prev == '.' || prev == '_' || prev == '-' || prev == ' ' || prev == '/' ||
                        (Character.isLowerCase(prev) && Character.isUpperCase(curr))) {
                    boundaries.add(i);
                }
            }
        }

        int qi = 0;
        int bi = 0;
        int score = 0;

        while (qi < queryLen && bi < boundaries.size()) {
            int boundaryPos = boundaries.get(bi);
            if (lowerText.charAt(boundaryPos) == lowerQuery.charAt(qi)) {
                score += 100; // Boundary character match bonus
                qi++;

                // Determine whether to match in-word continuation characters within this word segment
                boolean nextBoundaryMatches = (bi + 1 < boundaries.size()) && (qi < queryLen) &&
                        (lowerText.charAt(boundaries.get(bi + 1)) == lowerQuery.charAt(qi));

                if (!nextBoundaryMatches) {
                    int nextBoundary = (bi + 1 < boundaries.size()) ? boundaries.get(bi + 1) : textLen;
                    int curPos = boundaryPos + 1;
                    while (qi < queryLen && curPos < nextBoundary) {
                        if (lowerText.charAt(curPos) == lowerQuery.charAt(qi)) {
                            score += 50; // In-word continuation match bonus
                            qi++;
                            if (bi + 1 < boundaries.size() && qi < queryLen &&
                                    lowerText.charAt(boundaries.get(bi + 1)) == lowerQuery.charAt(qi)) {
                                break; // Preserve next query character for the next boundary
                            }
                        }
                        curPos++;
                    }
                }
            }
            bi++;
        }

        if (qi >= queryLen) {
            return Math.max(score, 550); // Successfully matched all query characters across boundaries
        }

        return 0;
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
