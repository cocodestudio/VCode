package com.cocode.vcode.ide.ui.editor.viewer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.cocode.vcode.ide.core.language.base.DomNode;
import com.cocode.vcode.ide.core.language.base.LanguageBackend;
import com.cocode.vcode.ide.core.language.base.LanguageRegistry;
import com.cocode.vcode.ide.core.language.css.CssSyntaxTree;
import com.cocode.vcode.ide.core.language.html.HtmlParser;
import com.cocode.vcode.ide.core.language.html.HtmlSyntaxTree;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.language.js.ParseResult;
import com.cocode.vcode.ide.core.language.json.JsonSyntaxTree;
import com.cocode.vcode.ide.core.language.md.MdLexer;
import com.cocode.vcode.ide.core.language.md.MdLineStream;
import com.cocode.vcode.ide.core.language.md.MdParser;
import com.cocode.vcode.ide.core.language.md.MdSyntaxTree;
import com.cocode.vcode.ide.core.lsp.LspEditorBridge;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.views.BreadcrumbView;
import com.cocode.vcode.ide.views.CodeEditText;
import com.cocode.vcode.ide.views.CodeEditorLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Primary code file viewer tab component wrapping the CodeEditText editor.
 */
public class CodeFileViewer implements IFileViewer {

    private final Handler jsonValidationHandler = new Handler(Looper.getMainLooper());
    private final Handler breadcrumbHandler = new Handler(Looper.getMainLooper());
    private final LspEditorBridge lspBridge = new LspEditorBridge();
    private final Runnable autoSaveValidationRunnable = this::performAutoSaveAndSnapshot;
    private final Runnable breadcrumbRunnable = this::resolveBreadcrumbSymbols;
    private final Runnable cursorChangeListener = this::onCursorMovedDebounced;
    private FrameLayout viewContainer;
    private CodeEditorLayout editorLayout;
    private CodeEditText codeEditText;
    private EditorFile currentFile;
    private EditorViewModel viewModel;
    private IEditorCallback editorCallback;
    private boolean isPositionRestoring = false;

    public boolean isPositionRestoring() {
        return isPositionRestoring;
    }

    public void flushContentToViewModel() {
        if (currentFile != null && codeEditText != null) {
            // Prevent flushing during async text load or position restoration to avoid
            // overwriting the data model with uninitialized (0, 0) coordinates.
            if (codeEditText.isSettingText() || isPositionRestoring) {
                return;
            }
            currentFile.setContent(codeEditText.getTextAsString());
            currentFile.setCursorPosition(codeEditText.getSelectionStart());
            currentFile.setScrollY(codeEditText.getScrollY());
        }
    }

    @Override
    public View getView(Context context, ViewGroup parent) {
        if (editorLayout == null) {
            // Outer FrameLayout — holds the editor + the floating selection toolbar
            FrameLayout container = new FrameLayout(context);
            container.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            editorLayout = new CodeEditorLayout(context);
            editorLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            container.addView(editorLayout);

            this.viewContainer = container;
            codeEditText = editorLayout.getCodeEditText();
            codeEditText.addCursorChangeListener(cursorChangeListener);
            codeEditText.addContentChangeListener(() -> {
                if (currentFile != null && viewModel != null) {
                    if (!currentFile.isDirty()) {
                        currentFile.setDirty(true);
                        viewModel.notifyFileDirtyStatusChanged();
                    }
                    validateCodeIfRequired();
                }
            });
            codeEditText.addTextLoadListener(isLoading -> {
                if (viewModel != null) {
                    viewModel.setEditorLoading(isLoading);
                }
            });
            codeEditText.setOnProblemRemovedListener(p -> {
                if (viewModel != null) {
                    viewModel.removeProblem(p);
                }
            });

            if (context instanceof IEditorCallback) {
                editorCallback = (IEditorCallback) context;
                lspBridge.setEditorCallback(editorCallback);
            }

            // Attach LSP bridge now that the editor view exists.
            // setFile() will be called in bindFile() once a file is known.
            lspBridge.attach(codeEditText);
            if (editorLayout.getLspNavigationToolbar() != null) {
                editorLayout.getLspNavigationToolbar().bindBridge(lspBridge);
                editorLayout.getLspNavigationToolbar().setNavigationListener(new com.cocode.vcode.ide.views.LspNavigationToolbar.NavigationListener() {
                    @Override
                    public void onNavigate(com.cocode.vcode.ide.core.lsp.LspLocation loc) {
                        if (editorCallback != null) editorCallback.navigateToLocation(loc);
                    }

                    @Override
                    public void onShowReferences(java.util.List<com.cocode.vcode.ide.core.lsp.LspLocation> refs) {
                        if (editorCallback != null) editorCallback.showReferences(refs);
                    }

                    @Override
                    public void onRenameSymbol(String newName, java.util.List<com.cocode.vcode.ide.core.lsp.LspLocation> locations) {
                        editorLayout.onRenameSymbol(newName, locations);
                    }
                });
            }
        }
        return viewContainer;
    }


    @Override
    public void bindFile(EditorFile file, EditorViewModel viewModel) {
        if (codeEditText != null && currentFile != null && currentFile != file) {
            // Flush the PREVIOUS file's state before switching to a different file.
            // Guard: only flush when switching to a DIFFERENT file.
            // Flushing when re-binding the same file (e.g., background load notification)
            // would overwrite the model with stale/empty editor content.
            flushContentToViewModel();
        }

        if (file == null) return;
        boolean isDifferentFile = (this.currentFile != file);
        this.currentFile = file;
        this.viewModel = viewModel;

        if (codeEditText == null) return;

        AppSettings settings = viewModel.getSettingsLiveData().getValue();
        if (settings != null) {
            codeEditText.setTextSize(settings.getFontSize());
            codeEditText.setAutoCloseBrackets(settings.isAutoCloseBrackets());
            codeEditText.setAutoCloseQuotes(settings.autoCloseQuotes);
            codeEditText.setAutoCloseHtmlTags(settings.autoCloseHtmlTags);
            codeEditText.setWordWrap(settings.wordWrap);
            codeEditText.setAutoIndent(settings.autoIndent);
            codeEditText.setRainbowBrackets(settings.rainbowBrackets);
            codeEditText.setBracketHighlighting(settings.bracketHighlighting);
            codeEditText.setShowSquigglyLines(settings.enableDiagnostics && settings.showSquigglyLines);
            codeEditText.setDeleteMatchingPairs(settings.deleteMatchingPairs);
            codeEditText.setForceLargeFileHighlighting(settings.forceLargeFileHighlighting);
            editorLayout.setShowLineNumbers(settings.isShowLineNumbers());
            lspBridge.setDiagnosticsEnabled(settings.enableDiagnostics);
        }

        codeEditText.setTag(file.getId());
        codeEditText.setCurrentFile(file.getFile());
        codeEditText.setFileType(file.getFileType());

        // Notify LSP bridge of the file that is now open so it can start indexing
        // and schedule an initial diagnostic pass.
        if (file.getFile() != null && !file.isBinaryAsset()) {
            lspBridge.setFile(file.getFile());
        }

        if (!file.isContentLoaded() && !file.isVirtual() && !file.isBinaryAsset()) {
            // Content was never successfully read (e.g. read failed during session restore).
            // Show an empty editor immediately, then load from disk async and push the
            // content back to the UI once ready. This prevents the editor staying blank.
            codeEditText.setText("");
            final EditorFile capturedFile = file;
            final CodeEditText capturedEditor = codeEditText;
            ExecutorProvider.getInstance().runOnIo(() -> {
                try {
                    String content = com.cocode.vcode.ide.utils.FileUtils.readFile(capturedFile.getFile());
                    capturedFile.setContent(content);
                    capturedFile.markSaved();
                    if (capturedFile.getFile() != null) {
                        capturedFile.setLastDiskModified(capturedFile.getFile().lastModified());
                        capturedFile.setLastDiskSize(capturedFile.getFile().length());
                    }
                    capturedFile.setContentLoaded(true);
                    ExecutorProvider.getInstance().runOnMain(() -> {
                        // Only apply if this viewer is still bound to the same file.
                        if (currentFile == capturedFile && capturedEditor == codeEditText) {
                            capturedEditor.addTextLoadListener(new CodeEditText.OnTextLoadListener() {
                                @Override
                                public void onTextLoadStateChanged(boolean isLoading) {
                                    if (!isLoading) {
                                        capturedEditor.removeTextLoadListener(this);
                                        if (currentFile != capturedFile) return;
                                        capturedEditor.scrollTo(0, capturedFile.getScrollY());
                                        capturedEditor.setSelection(capturedFile.getCursorPosition());
                                        capturedEditor.post(capturedEditor::ensureCursorVisible);
                                        validateCodeIfRequired();
                                        onCursorMovedDebounced();
                                    }
                                }
                            });
                            capturedEditor.setText(content);
                        }
                    });
                } catch (Exception ignored) {
                    // If we still can't read, leave editor empty — user can see the file
                    // is missing and act accordingly.
                }
            });
            return;
        }

        // Only set text if it's different to prevent resetting cursor.
        //
        // EXCEPTION: if a load is still in flight (isSettingText()), we must NOT take the
        // "already matches, skip setText()" shortcut below even when the buffer's current
        // text happens to equal this file's content. That in-flight load belongs to some
        // OTHER file (e.g. from a rapid A -> B -> A switch) and, if left unaddressed, will
        // still apply itself into the buffer once it completes — silently swapping the
        // visible editor content to the wrong file's text even though currentFile is
        // correct. Calling setText() here bumps the load token and invalidates that stale
        // load so only content for the file we're actually bound to can ever land.
        String currentText = codeEditText.getText() != null ? codeEditText.getText().toString() : "";
        if (!currentText.equals(file.getContent()) || codeEditText.isSettingText()) {
            isPositionRestoring = true;
            codeEditText.addTextLoadListener(new CodeEditText.OnTextLoadListener() {
                @Override
                public void onTextLoadStateChanged(boolean isLoading) {
                    if (!isLoading) {
                        codeEditText.removeTextLoadListener(this);
                        if (currentFile != file) {
                            isPositionRestoring = false;
                            return;
                        }
                        codeEditText.scrollTo(0, file.getScrollY());
                        codeEditText.setSelection(file.getCursorPosition());
                        codeEditText.post(() -> {
                            codeEditText.ensureCursorVisible();
                            isPositionRestoring = false;
                            onCursorMovedDebounced();
                        });
                    }
                }
            });
            codeEditText.setText(file.getContent());
        } else {
            // Text is identical, so no async load is triggered.
            // Restore scroll/cursor position if switching from another file OR if viewport/cursor
            // is not yet aligned to the file's saved position (e.g. after container became visible).
            if (isDifferentFile || codeEditText.getScrollY() != file.getScrollY() || codeEditText.getSelectionStart() != file.getCursorPosition()) {
                codeEditText.scrollTo(0, file.getScrollY());
                codeEditText.setSelection(file.getCursorPosition());
                codeEditText.post(codeEditText::ensureCursorVisible);
            }
            isPositionRestoring = false;
            // Since setText wasn't called, the async load event won't fire, so we must
            // clear the LSP bridge's content-sync guard manually to allow diagnostics to run.
            lspBridge.clearContentSyncPending();
        }

        validateCodeIfRequired();
        onCursorMovedDebounced();
    }

    /**
     * Updates the active editor content in-place when a refactoring operation modifies the file,
     * preserving live cursor, scroll position, and undo history.
     */
    public void updateRefactoredContent(String newContent) {
        if (codeEditText == null || newContent == null) return;
        if (currentFile != null) {
            currentFile.setContent(newContent);
            if (!currentFile.isDirty()) {
                currentFile.markSaved();
            }
        }
        codeEditText.updateRefactoredContent(newContent);
        if (currentFile != null) {
            currentFile.setCursorPosition(codeEditText.getSelectionStart());
            currentFile.setScrollY(codeEditText.getScrollY());
        }
    }

    /**
     * Reloads external disk content in-place (e.g. from Git revert, pull, checkout, or external sync),
     * ensuring the file model stays marked clean, resetting the undo baseline, and refreshing diagnostics.
     */
    public void reloadExternalContent(String newContent) {
        if (codeEditText == null || newContent == null) return;
        if (currentFile != null) {
            currentFile.setContent(newContent);
            currentFile.markSaved();
        }
        codeEditText.reloadExternalContent(newContent);
        if (currentFile != null) {
            currentFile.setCursorPosition(codeEditText.getSelectionStart());
            currentFile.setScrollY(codeEditText.getScrollY());
        }
        validateCodeIfRequired();
    }

    @Override
    public void onResume() {
        if (currentFile != null && codeEditText != null) {
            checkExternalDiskModification();
            validateCodeIfRequired();
            onCursorMovedDebounced();
        }
    }

    private void checkExternalDiskModification() {
        if (currentFile == null || currentFile.isVirtual() || currentFile.isBinaryAsset()) return;
        File fileOnDisk = currentFile.getFile();
        if (fileOnDisk == null || !fileOnDisk.exists()) return;
        if (currentFile.isDirty()) return;

        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                long diskMod = fileOnDisk.lastModified();
                long diskSize = fileOnDisk.length();
                String diskContent = com.cocode.vcode.ide.utils.FileUtils.readFile(fileOnDisk);
                currentFile.setLastDiskModified(diskMod);
                currentFile.setLastDiskSize(diskSize);
                if (!diskContent.equals(currentFile.getContent())) {
                    ExecutorProvider.getInstance().runOnMain(() -> {
                        if (currentFile != null && com.cocode.vcode.ide.utils.FileUtils.isSameFile(fileOnDisk, currentFile.getFile())) {
                            reloadExternalContent(diskContent);
                        }
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void onPause() {
        flushContentToViewModel();
        jsonValidationHandler.removeCallbacksAndMessages(null);
        breadcrumbHandler.removeCallbacksAndMessages(null);
        if (editorLayout != null && editorLayout.getSelectionToolbar() != null) {
            editorLayout.getSelectionToolbar().hide();
        }
        if (editorLayout != null && editorLayout.getLspNavigationToolbar() != null) {
            editorLayout.getLspNavigationToolbar().hide();
        }
    }

    @Override
    public void destroy() {
        jsonValidationHandler.removeCallbacksAndMessages(null);
        breadcrumbHandler.removeCallbacksAndMessages(null);
        if (editorLayout != null && editorLayout.getSelectionToolbar() != null) {
            editorLayout.getSelectionToolbar().hide();
        }
        if (editorLayout != null && editorLayout.getLspNavigationToolbar() != null) {
            editorLayout.getLspNavigationToolbar().hide();
            editorLayout.getLspNavigationToolbar().setNavigationListener(null);
        }
        lspBridge.detach();
        if (codeEditText != null) {
            codeEditText.removeCursorChangeListener(cursorChangeListener);
            codeEditText.setOnProblemRemovedListener(null);
        }
        editorLayout = null;
        codeEditText = null;
        currentFile = null;
        viewModel = null;
        editorCallback = null;
    }

    @Override
    public EditorFile getCurrentFile() {
        return currentFile;
    }

    @Override
    public CodeEditText getCodeEditor() {
        return codeEditText;
    }

    /**
     * Returns the LSP bridge so callers can trigger Go to Definition / Find References.
     */
    public com.cocode.vcode.ide.core.lsp.LspEditorBridge getLspBridge() {
        return lspBridge;
    }

    private void validateCodeIfRequired() {
        if (editorCallback == null || currentFile == null || viewModel == null) return;

        AppSettings settings = viewModel.getSettingsLiveData().getValue();
        if (settings == null) return;

        lspBridge.setDiagnosticsEnabled(settings.enableDiagnostics);
        if (!settings.enableDiagnostics) {
            if (codeEditText != null) {
                codeEditText.applyDiagnostics(new java.util.ArrayList<>());
            }
            if (editorCallback != null && currentFile != null) {
                editorCallback.reportProblems(currentFile.getFile(), new java.util.ArrayList<>());
            }
            return;
        }

        jsonValidationHandler.removeCallbacks(autoSaveValidationRunnable);
        jsonValidationHandler.postDelayed(autoSaveValidationRunnable, 300L);
    }

    private void performAutoSaveAndSnapshot() {
        final EditorFile capturedFile = currentFile;
        if (capturedFile == null || capturedFile.getFile() == null) {
            if (codeEditText != null)
                codeEditText.applyDiagnostics(new java.util.ArrayList<>());
            return;
        }

        // If the editor is currently loading text asynchronously (e.g. from a tab switch),
        // do not snapshot the text! The buffer currently holds the OLD file's text.
        //
        // Do NOT just re-poll on a timer here: a blind postDelayed() re-check can fire
        // AFTER some unrelated, still-in-flight load (e.g. from a rapid A -> B -> A tab
        // switch) flips isSettingText() back to false, at which point the buffer holds
        // a DIFFERENT file's text — snapshotting then would corrupt capturedFile with
        // the wrong file's content. Instead, bail out entirely and re-arm via a one-shot
        // text-load listener gated on capturedFile, so this only ever re-fires once
        // loading has genuinely finished for the bind that's actually current.
        if (codeEditText != null && (codeEditText.isSettingText() || isPositionRestoring)) {
            final CodeEditText ed = codeEditText;
            ed.addTextLoadListener(new CodeEditText.OnTextLoadListener() {
                @Override
                public void onTextLoadStateChanged(boolean isLoading) {
                    if (!isLoading) {
                        ed.removeTextLoadListener(this);
                        if (currentFile == capturedFile) {
                            validateCodeIfRequired();
                        }
                    }
                }
            });
            return;
        }

        // Capture UI state on main thread ONLY AFTER text is fully loaded and positions restored.
        final String textSnapshot = codeEditText != null ? codeEditText.getTextAsString() : "";
        final int cursor = (codeEditText != null && !isPositionRestoring) ? codeEditText.getSelectionStart() : capturedFile.getCursorPosition();
        final int scrollY = (codeEditText != null && !isPositionRestoring) ? codeEditText.getScrollY() : capturedFile.getScrollY();

        // 1. Update EditorFile with latest state synchronously on the Main Thread.
        //    This ensures that the model always gets the most recent keystrokes in order.
        capturedFile.setContent(textSnapshot);
        capturedFile.setCursorPosition(cursor);
        capturedFile.setScrollY(scrollY);

        // Notify language parse pipelines with the debounced snapshot (avoids per-keystroke allocations)
        if (capturedFile.getFileType() == com.cocode.vcode.ide.core.model.FileType.JAVASCRIPT ||
                capturedFile.getFileType() == com.cocode.vcode.ide.core.model.FileType.TYPESCRIPT) {
            com.cocode.vcode.ide.core.language.js.JsParsePipeline.getInstance().onTextChanged(
                    capturedFile.getFile(), textSnapshot, cursor);
        } else if (capturedFile.getFileType() == com.cocode.vcode.ide.core.model.FileType.HTML) {
            com.cocode.vcode.ide.core.language.html.HtmlParsePipeline.getInstance().onTextChanged(
                    capturedFile.getFile(), textSnapshot, cursor);
        }

        // 2. Auto-save: dispatch disk write to IO thread (non-blocking for diagnostics).
        AppSettings settings = viewModel != null ? viewModel.getSettingsLiveData().getValue() : null;
        if (settings != null && settings.autoSave && !textSnapshot.isEmpty()) {
            viewModel.triggerAutoSave();
        }
        onCursorMovedDebounced();
    }

    private void onCursorMovedDebounced() {
        if (currentFile == null || codeEditText == null) return;
        if (codeEditText.isSettingText() || isPositionRestoring) return;

        breadcrumbHandler.removeCallbacks(breadcrumbRunnable);
        breadcrumbHandler.postDelayed(breadcrumbRunnable, 120L);
    }

    private void resolveBreadcrumbSymbols() {
        if (currentFile == null || codeEditText == null) return;
        if (codeEditText.isSettingText() || isPositionRestoring) return;

        final File file = currentFile.getFile();
        if (file == null) return;

        final int cursorOffset = Math.max(0, codeEditText.getSelectionStart());
        final FileType fileType = currentFile.getFileType();
        final String text = codeEditText.getTextAsString();

        ExecutorProvider.getInstance().runOnCpu(() -> {
            List<BreadcrumbView.BreadcrumbSymbol> symbolChain = extractBreadcrumbSymbols(file, fileType, text, cursorOffset);
            ExecutorProvider.getInstance().runOnMain(() -> {
                if (currentFile != null && FileUtils.isSameFile(currentFile.getFile(), file)) {
                    if (editorCallback != null) {
                        editorCallback.reportBreadcrumbSymbols(file, symbolChain);
                    }
                }
            });
        });
    }

    private static class HeadingInfo {
        final int id;
        final int level;
        final String title;
        final int startOffset;
        int endOffset;

        HeadingInfo(int id, int level, String title, int startOffset) {
            this.id = id;
            this.level = Math.max(1, level);
            this.title = title != null ? title : "";
            this.startOffset = startOffset;
        }
    }

    private List<BreadcrumbView.BreadcrumbSymbol> extractBreadcrumbSymbols(File file, FileType fileType, String text, int cursorOffset) {
        if (file == null || text == null || fileType == null) {
            return Collections.emptyList();
        }

        if (fileType == FileType.MARKDOWN) {
            return extractMarkdownSymbols(text, cursorOffset);
        }

        ParseResult cached = ProjectIndex.getInstance().getParseResult(file.getAbsolutePath());

        // Check for embedded JS or CSS inside HTML files
        if (fileType == FileType.HTML) {
            ParseResult htmlParseResult = cached;
            if (htmlParseResult == null || htmlParseResult.htmlTree == null || (htmlParseResult.source != null && !htmlParseResult.source.equals(text))) {
                try {
                    htmlParseResult = HtmlParser.parse(text);
                } catch (Exception ignored) {
                }
            }
            if (htmlParseResult != null && htmlParseResult.embeddedResults != null && !htmlParseResult.embeddedResults.isEmpty()) {
                for (ParseResult.EmbeddedResult emb : htmlParseResult.embeddedResults) {
                    if (emb != null && cursorOffset >= emb.startOffset && cursorOffset <= emb.endOffset && emb.result != null) {
                        List<BreadcrumbView.BreadcrumbSymbol> chain = extractEmbeddedSymbols(htmlParseResult, emb, cursorOffset);
                        if (chain != null && !chain.isEmpty()) {
                            return chain;
                        }
                    }
                }
            }
        }

        DomNode root = null;
        if (cached != null && (cached.source == null || cached.source.equals(text))) {
            if (cached.tree != null) {
                root = cached.tree.toDomFile(fileType);
            } else if (cached.htmlTree != null) {
                root = cached.htmlTree.toDomFile();
            } else if (cached.cssTree != null) {
                root = cached.cssTree.toDomFile();
            } else if (cached.jsonTree != null) {
                root = cached.jsonTree.toDomFile();
            }
        }

        if (root == null) {
            LanguageBackend backend = LanguageRegistry.getInstance().getBackend(fileType);
            if (backend != null) {
                try {
                    root = backend.parseToDom(text);
                } catch (Exception ignored) {
                }
            }
        }

        if (root == null) {
            return Collections.emptyList();
        }

        DomNode deepest = root.findDeepestNodeAt(cursorOffset);
        if (deepest == null) {
            return Collections.emptyList();
        }

        List<BreadcrumbView.BreadcrumbSymbol> symbolChain = new ArrayList<>();
        DomNode current = deepest;
        int depthLimit = 0;
        while (current != null && depthLimit < 60) {
            depthLimit++;
            BreadcrumbView.BreadcrumbSymbol symbol = createBreadcrumbSymbol(current, fileType);
            if (symbol != null) {
                if (symbolChain.isEmpty() || !symbolChain.get(0).equals(symbol)) {
                    symbolChain.add(0, symbol);
                }
            }
            current = current.getParent();
        }

        return symbolChain;
    }

    private List<BreadcrumbView.BreadcrumbSymbol> extractEmbeddedSymbols(ParseResult htmlResult, ParseResult.EmbeddedResult emb, int cursorOffset) {
        List<BreadcrumbView.BreadcrumbSymbol> symbolChain = new ArrayList<>();

        // Collect HTML ancestor tags up to <script> or <style>
        if (htmlResult != null && htmlResult.htmlTree != null) {
            DomNode htmlRoot = htmlResult.htmlTree.toDomFile();
            DomNode deepestHtml = htmlRoot.findDeepestNodeAt(emb.startOffset);
            DomNode current = deepestHtml;
            int depth = 0;
            while (current != null && depth < 30) {
                depth++;
                BreadcrumbView.BreadcrumbSymbol sym = createBreadcrumbSymbol(current, FileType.HTML);
                if (sym != null) {
                    if (symbolChain.isEmpty() || !symbolChain.get(0).equals(sym)) {
                        symbolChain.add(0, sym);
                    }
                }
                current = current.getParent();
            }
        }

        if (emb.result == null) {
            return symbolChain;
        }

        int relOffset = cursorOffset - emb.startOffset;

        if (emb.result.tree != null) {
            boolean isZeroBased = emb.result.tree.nodeCount > 1 && emb.result.tree.nodeStart[1] < emb.startOffset;
            int queryOffset = isZeroBased ? relOffset : cursorOffset;
            int offsetDelta = isZeroBased ? emb.startOffset : 0;

            DomNode jsRoot = emb.result.tree.toDomFile(FileType.JAVASCRIPT);
            DomNode deepestJs = jsRoot.findDeepestNodeAt(queryOffset);
            List<BreadcrumbView.BreadcrumbSymbol> jsChain = new ArrayList<>();
            DomNode current = deepestJs;
            int depth = 0;
            while (current != null && depth < 40) {
                depth++;
                BreadcrumbView.BreadcrumbSymbol sym = createBreadcrumbSymbol(current, FileType.JAVASCRIPT);
                if (sym != null) {
                    BreadcrumbView.BreadcrumbSymbol adjustedSym = new BreadcrumbView.BreadcrumbSymbol(
                            sym.getName(), sym.getKind(), sym.getStartOffset() + offsetDelta, sym.getEndOffset() + offsetDelta);
                    if (jsChain.isEmpty() || !jsChain.get(0).equals(adjustedSym)) {
                        jsChain.add(0, adjustedSym);
                    }
                }
                current = current.getParent();
            }
            symbolChain.addAll(jsChain);
        } else if (emb.result.cssTree != null) {
            boolean isZeroBased = emb.result.cssTree.nodeCount > 1 && emb.result.cssTree.nodeStart[1] < emb.startOffset;
            int queryOffset = isZeroBased ? relOffset : cursorOffset;
            int offsetDelta = isZeroBased ? emb.startOffset : 0;

            DomNode cssRoot = emb.result.cssTree.toDomFile();
            DomNode deepestCss = cssRoot.findDeepestNodeAt(queryOffset);
            List<BreadcrumbView.BreadcrumbSymbol> cssChain = new ArrayList<>();
            DomNode current = deepestCss;
            int depth = 0;
            while (current != null && depth < 40) {
                depth++;
                BreadcrumbView.BreadcrumbSymbol sym = createBreadcrumbSymbol(current, FileType.CSS);
                if (sym != null) {
                    BreadcrumbView.BreadcrumbSymbol adjustedSym = new BreadcrumbView.BreadcrumbSymbol(
                            sym.getName(), sym.getKind(), sym.getStartOffset() + offsetDelta, sym.getEndOffset() + offsetDelta);
                    if (cssChain.isEmpty() || !cssChain.get(0).equals(adjustedSym)) {
                        cssChain.add(0, adjustedSym);
                    }
                }
                current = current.getParent();
            }
            symbolChain.addAll(cssChain);
        }

        return symbolChain;
    }

    private List<BreadcrumbView.BreadcrumbSymbol> extractMarkdownSymbols(String text, int cursorOffset) {
        if (text == null) return Collections.emptyList();
        MdLineStream stream = MdLexer.lex(text);
        MdSyntaxTree mdTree = MdParser.parseBlocks(stream, text);
        if (mdTree == null || mdTree.nodeCount <= 1) return Collections.emptyList();

        List<HeadingInfo> headings = new ArrayList<>();
        int codeBlockId = 0;

        for (int i = 1; i < mdTree.nodeCount; i++) {
            int type = mdTree.nodeType[i];
            if (type == MdSyntaxTree.N_HEADER) {
                int level = mdTree.nodeExtra[i];
                String title = mdTree.nodeName[i];
                int start = mdTree.nodeStart[i];
                headings.add(new HeadingInfo(i, level, title, start));
            } else if (type == MdSyntaxTree.N_CODE_BLOCK) {
                int start = mdTree.nodeStart[i];
                int end = mdTree.nodeEnd[i];
                if (cursorOffset >= start && cursorOffset <= end) {
                    codeBlockId = i;
                }
            }
        }

        int textLen = text.length();
        for (int i = 0; i < headings.size(); i++) {
            HeadingInfo curr = headings.get(i);
            int sectionEnd = textLen;
            for (int j = i + 1; j < headings.size(); j++) {
                HeadingInfo next = headings.get(j);
                if (next.level <= curr.level) {
                    sectionEnd = next.startOffset;
                    break;
                }
            }
            curr.endOffset = sectionEnd;
        }

        List<BreadcrumbView.BreadcrumbSymbol> symbolChain = new ArrayList<>();
        for (HeadingInfo h : headings) {
            boolean enclosed = cursorOffset >= h.startOffset
                    && (h.endOffset == textLen ? cursorOffset <= h.endOffset : cursorOffset < h.endOffset);
            if (enclosed) {
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < h.level; k++) {
                    sb.append('#');
                }
                sb.append(' ');
                if (h.title != null) {
                    sb.append(h.title);
                }
                String displayName = sb.toString();
                symbolChain.add(new BreadcrumbView.BreadcrumbSymbol(
                        displayName, MdSyntaxTree.N_HEADER, h.startOffset, h.endOffset));
            }
        }

        if (codeBlockId > 0) {
            String lang = mdTree.nodeName[codeBlockId];
            String blockName = (lang != null && !lang.trim().isEmpty()) ? "```" + lang.trim() : "```";
            symbolChain.add(new BreadcrumbView.BreadcrumbSymbol(
                    blockName, MdSyntaxTree.N_CODE_BLOCK, mdTree.nodeStart[codeBlockId], mdTree.nodeEnd[codeBlockId]));
        }

        return symbolChain;
    }

    private BreadcrumbView.BreadcrumbSymbol createBreadcrumbSymbol(DomNode node, FileType fileType) {
        if (node == null) return null;
        int kind = node.getKind();
        String rawName = node.getName();
        if (rawName != null) {
            rawName = rawName.trim();
        }
        if (rawName == null) {
            rawName = "";
        }

        String displayName = null;

        if (fileType == FileType.JAVASCRIPT || fileType == FileType.TYPESCRIPT) {
            switch (kind) {
                case JsSyntaxTree.N_CLASS_DECL:
                    displayName = rawName.isEmpty() ? "class" : "class " + rawName;
                    break;
                case JsSyntaxTree.N_INTERFACE:
                    displayName = rawName.isEmpty() ? "interface" : "interface " + rawName;
                    break;
                case JsSyntaxTree.N_ENUM:
                    displayName = rawName.isEmpty() ? "enum" : "enum " + rawName;
                    break;
                case JsSyntaxTree.N_TYPE_ALIAS:
                    displayName = rawName.isEmpty() ? "type" : "type " + rawName;
                    break;
                case JsSyntaxTree.N_FUNC_DECL:
                    if (!rawName.isEmpty()) {
                        displayName = rawName + "()";
                    } else {
                        DomNode parent = node.getParent();
                        if (parent != null && (parent.getKind() == JsSyntaxTree.N_VAR_DECL || parent.getKind() == JsSyntaxTree.N_PROPERTY)
                                && !parent.getName().isEmpty()) {
                            displayName = parent.getName() + "()";
                        } else {
                            displayName = "function()";
                        }
                    }
                    break;
                case JsSyntaxTree.N_METHOD:
                    if ("constructor".equals(rawName)) {
                        displayName = "constructor";
                    } else {
                        displayName = rawName.isEmpty() ? "method()" : rawName + "()";
                    }
                    break;
                case JsSyntaxTree.N_GETTER:
                    displayName = "get " + rawName;
                    break;
                case JsSyntaxTree.N_SETTER:
                    displayName = "set " + rawName;
                    break;
                case JsSyntaxTree.N_ARROW_FUNC:
                    if (!rawName.isEmpty()) {
                        displayName = rawName + "()";
                    } else {
                        DomNode parent = node.getParent();
                        if (parent != null) {
                            if ((parent.getKind() == JsSyntaxTree.N_VAR_DECL || parent.getKind() == JsSyntaxTree.N_PROPERTY)
                                    && !parent.getName().isEmpty()) {
                                displayName = parent.getName() + "()";
                            } else if (parent.getKind() == JsSyntaxTree.N_CALL_EXPR && !parent.getName().isEmpty()) {
                                displayName = parent.getName() + "(callback)";
                            } else {
                                displayName = "(anonymous)()";
                            }
                        } else {
                            displayName = "(anonymous)()";
                        }
                    }
                    break;
                case JsSyntaxTree.N_VAR_DECL:
                    if (rawName.isEmpty()) {
                        return null;
                    }
                    // Suppress variable declaration if it directly defines a function/arrow function,
                    // because the function itself already appears in the breadcrumb trail.
                    List<? extends DomNode> varChildren = node.getChildren();
                    if (varChildren != null) {
                        boolean definesFunc = false;
                        for (DomNode child : varChildren) {
                            if (child != null && (child.getKind() == JsSyntaxTree.N_ARROW_FUNC || child.getKind() == JsSyntaxTree.N_FUNC_DECL)) {
                                definesFunc = true;
                                break;
                            }
                        }
                        if (definesFunc) {
                            return null;
                        }
                    }
                    int flag = node.getExtra();
                    String kwPrefix = (flag == JsSyntaxTree.FLAG_CONST) ? "const " :
                                     ((flag == JsSyntaxTree.FLAG_LET) ? "let " :
                                     ((flag == JsSyntaxTree.FLAG_VAR) ? "var " : ""));
                    if ("{destructure}".equals(rawName)) {
                        displayName = kwPrefix.isEmpty() ? "{...}" : kwPrefix + "{...}";
                    } else {
                        displayName = kwPrefix + rawName;
                    }
                    break;
                case JsSyntaxTree.N_PROPERTY:
                    if (rawName.isEmpty()) {
                        return null;
                    }
                    List<? extends DomNode> propChildren = node.getChildren();
                    if (propChildren != null) {
                        boolean definesFunc = false;
                        for (DomNode child : propChildren) {
                            if (child != null && (child.getKind() == JsSyntaxTree.N_ARROW_FUNC || child.getKind() == JsSyntaxTree.N_FUNC_DECL)) {
                                definesFunc = true;
                                break;
                            }
                        }
                        if (definesFunc) {
                            return null;
                        }
                    }
                    displayName = rawName;
                    break;
                case JsSyntaxTree.N_FOR_STMT:
                    displayName = rawName.isEmpty() ? "for" : rawName;
                    break;
                case JsSyntaxTree.N_WHILE_STMT:
                    displayName = rawName.isEmpty() ? "while" : rawName;
                    break;
                case JsSyntaxTree.N_DO_STMT:
                    displayName = rawName.isEmpty() ? "do...while" : rawName;
                    break;
                case JsSyntaxTree.N_IF_STMT:
                    displayName = rawName.isEmpty() ? "if" : rawName;
                    break;
                case JsSyntaxTree.N_SWITCH_STMT:
                    displayName = rawName.isEmpty() ? "switch" : rawName;
                    break;
                case JsSyntaxTree.N_CASE_CLAUSE:
                    displayName = rawName.isEmpty() ? "case" : rawName;
                    break;
                case JsSyntaxTree.N_TRY_STMT:
                    displayName = "try";
                    break;
                case JsSyntaxTree.N_CATCH_CLAUSE:
                    displayName = rawName.isEmpty() ? "catch" : rawName;
                    break;
                case JsSyntaxTree.N_FINALLY_CLAUSE:
                    displayName = "finally";
                    break;
                default:
                    return null;
            }
        } else if (fileType == FileType.HTML || fileType == FileType.SVG) {
            if (kind == HtmlSyntaxTree.N_ELEMENT && !rawName.isEmpty()) {
                String id = null;
                String firstClass = null;
                List<? extends DomNode> children = node.getChildren();
                if (children != null) {
                    for (DomNode child : children) {
                        if (child != null && child.getKind() == HtmlSyntaxTree.N_ATTRIBUTE) {
                            String attrName = child.getName();
                            String attrVal = child.getValue();
                            if (attrVal != null) {
                                attrVal = attrVal.trim();
                                if (attrVal.length() >= 2 && ((attrVal.startsWith("\"") && attrVal.endsWith("\"")) || (attrVal.startsWith("'") && attrVal.endsWith("'")))) {
                                    attrVal = attrVal.substring(1, attrVal.length() - 1).trim();
                                }
                            }
                            if ("id".equalsIgnoreCase(attrName) && attrVal != null && !attrVal.isEmpty()) {
                                id = attrVal;
                            } else if ("class".equalsIgnoreCase(attrName) && attrVal != null && !attrVal.isEmpty() && firstClass == null) {
                                String[] parts = attrVal.split("\\s+");
                                for (String p : parts) {
                                    if (!p.isEmpty()) {
                                        firstClass = p;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                StringBuilder sb = new StringBuilder();
                sb.append('<').append(rawName);
                if (id != null && !id.isEmpty()) {
                    sb.append('#').append(id);
                }
                if (firstClass != null && !firstClass.isEmpty()) {
                    sb.append('.').append(firstClass);
                }
                sb.append('>');
                displayName = sb.toString();
            } else {
                return null;
            }
        } else if (fileType == FileType.CSS || fileType == FileType.SCSS) {
            if ((kind == CssSyntaxTree.N_RULE || kind == CssSyntaxTree.N_AT_RULE || kind == CssSyntaxTree.N_PROPERTY) && !rawName.isEmpty()) {
                displayName = rawName;
            } else {
                return null;
            }
        } else if (fileType == FileType.JSON) {
            if (kind == JsonSyntaxTree.N_KEY && !rawName.isEmpty()) {
                displayName = rawName;
            } else {
                return null;
            }
        } else if (fileType == FileType.MARKDOWN) {
            if (kind == MdSyntaxTree.N_HEADER && !rawName.isEmpty()) {
                displayName = rawName;
            } else {
                return null;
            }
        } else {
            if (!rawName.isEmpty()) {
                displayName = rawName;
            } else {
                return null;
            }
        }

        if (displayName == null || displayName.isEmpty()) {
            return null;
        }

        if (displayName.length() > 40) {
            displayName = displayName.substring(0, 39) + "\u2026";
        }

        int start = node.getStartOffset();
        int end = node.getEndOffset();
        if (start < 0 || end <= start) {
            return null;
        }

        return new BreadcrumbView.BreadcrumbSymbol(displayName, kind, start, end);
    }
}
