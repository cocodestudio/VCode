package com.cocode.vcode.ide.ui.editor.viewer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.cocode.vcode.ide.core.lsp.LspEditorBridge;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.views.CodeEditText;
import com.cocode.vcode.ide.views.CodeEditorLayout;


/**
 * Primary code file viewer tab component wrapping the CodeEditText editor.
 */
public class CodeFileViewer implements IFileViewer {

    private final Handler jsonValidationHandler = new Handler(Looper.getMainLooper());
    private final LspEditorBridge lspBridge = new LspEditorBridge();
    private FrameLayout viewContainer;
    private CodeEditorLayout editorLayout;
    private CodeEditText codeEditText;
    private EditorFile currentFile;
    private EditorViewModel viewModel;
    private IEditorCallback editorCallback;

    public void flushContentToViewModel() {
        if (currentFile != null && codeEditText != null) {
            // Prevent flushing during async text load to avoid overwriting the data model
            // with the previous tab's content.
            if (codeEditText.isSettingText()) {
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
            editorLayout.setShowLineNumbers(settings.isShowLineNumbers());
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
                    capturedFile.setContentLoaded(true);
                    ExecutorProvider.getInstance().runOnMain(() -> {
                        // Only apply if this viewer is still bound to the same file.
                        if (currentFile == capturedFile && capturedEditor == codeEditText) {
                            capturedEditor.setText(content);
                            capturedEditor.addTextLoadListener(new CodeEditText.OnTextLoadListener() {
                                @Override
                                public void onTextLoadStateChanged(boolean isLoading) {
                                    if (!isLoading) {
                                        capturedEditor.removeTextLoadListener(this);
                                        if (currentFile != capturedFile) return;
                                        capturedEditor.scrollTo(0, capturedFile.getScrollY());
                                    }
                                }
                            });
                            validateCodeIfRequired();
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
            codeEditText.setText(file.getContent());
            codeEditText.addTextLoadListener(new CodeEditText.OnTextLoadListener() {
                @Override
                public void onTextLoadStateChanged(boolean isLoading) {
                    if (!isLoading) {
                        codeEditText.removeTextLoadListener(this);
                        if (currentFile != file) return;
                        codeEditText.scrollTo(0, file.getScrollY());
                    }
                }
            });
        } else {
            // Text is identical, so no async load is triggered. Restore scroll position only.
            codeEditText.scrollTo(0, file.getScrollY());
            // Since setText wasn't called, the async load event won't fire, so we must
            // clear the LSP bridge's content-sync guard manually to allow diagnostics to run.
            lspBridge.clearContentSyncPending();
        }

        validateCodeIfRequired();
    }

    @Override
    public void onResume() {
        if (currentFile != null && codeEditText != null) {
            validateCodeIfRequired();
        }
    }

    @Override
    public void onPause() {
        jsonValidationHandler.removeCallbacksAndMessages(null);
        if (editorLayout != null && editorLayout.getSelectionToolbar() != null) {
            editorLayout.getSelectionToolbar().hide();
        }
        if (editorLayout != null && editorLayout.getLspNavigationToolbar() != null) {
            editorLayout.getLspNavigationToolbar().hide();
        }
    }

    @Override
    public void destroy() {
        onPause();
        lspBridge.detach();
        if (editorLayout != null && editorLayout.getLspNavigationToolbar() != null) {
            editorLayout.getLspNavigationToolbar().setNavigationListener(null);
        }
        if (codeEditText != null) {
            // Nothing to remove for lambdas since we just clear the reference
        }
        editorLayout = null;
        codeEditText = null;
        currentFile = null;
        viewModel = null;
        editorCallback = null;
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

        jsonValidationHandler.removeCallbacksAndMessages(null);

        final EditorFile capturedFile = currentFile;
        final IEditorCallback capturedCallback = editorCallback;

        // Use a self-referencing Runnable array so it can re-post itself when the
        // async setText() hasn't finished applying content yet.
        final Runnable[] runnableHolder = new Runnable[1];
        runnableHolder[0] = () -> {
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
            if (codeEditText != null && codeEditText.isSettingText()) {
                return;
            }

            // Capture UI state on main thread ONLY AFTER text is fully loaded.
            final String textSnapshot = codeEditText != null ? codeEditText.getTextAsString() : "";
            final int cursor = codeEditText != null ? codeEditText.getSelectionStart() : 0;
            final int scrollY = codeEditText != null ? codeEditText.getScrollY() : 0;

            // 1. Update EditorFile with latest state synchronously on the Main Thread.
            //    This ensures that the model always gets the most recent keystrokes in order.
            capturedFile.setContent(textSnapshot);
            capturedFile.setCursorPosition(cursor);
            capturedFile.setScrollY(scrollY);

            // 2. Show "Analyzing..." ONLY when the legacy linter is about to run.
            //    When LSP is active, the bridge fires its own 300ms-debounced diagnostic
            //    and calls reportProblems() when done — so it manages loading state itself.
            //    Calling setDiagnosticLoading() here when LSP is active causes a critical
            //    race: the LSP already completed and cleared the UI at T+300ms, and this
            //    re-sets it at T+800ms with NOTHING left to clear it → infinite hang.
            if (!lspBridge.isLspActive()) {
                viewModel.setDiagnosticLoading(capturedFile.getFile());
            }

            // 3. Auto-save: dispatch disk write to IO thread (non-blocking for diagnostics).
            if (settings.autoSave && !textSnapshot.isEmpty()) {
                viewModel.triggerAutoSave();
            }

            // 4. Run legacy linter only when LSP is NOT active for this file type.
            //    Uses the DIAGNOSTIC executor (not IO), so it never blocks behind auto-saves
            //    or project-indexing tasks. LSP diagnostics are handled independently by
            //    LspEditorBridge via LspClientManager.requestDiagnostics().
            if (!lspBridge.isLspActive()) {
                ExecutorProvider.getInstance().runOnDiagnostic(() -> {
                    java.util.List<Problem> problems = com.cocode.vcode.ide.core.diagnostic.DiagnosticEngine.analyze(
                            capturedFile.getFile(), textSnapshot, capturedFile.getFileType());
                    if (problems != null) {
                        final java.util.List<Problem> finalProblems = problems;
                        ExecutorProvider.getInstance().runOnMain(() -> {
                            if (editorLayout == null || editorLayout.getParent() == null
                                    || ((View) editorLayout.getParent()).getVisibility() != View.VISIBLE) {
                                return;
                            }
                            if (codeEditText != null) {
                                codeEditText.applyDiagnostics(finalProblems);
                            }
                            if (capturedCallback != null) {
                                capturedCallback.reportProblems(capturedFile.getFile(), finalProblems);
                            }
                        });
                    }
                });
            }
            // When LSP IS active: LspEditorBridge.contentListener has already scheduled
            // its own 300ms-debounced performDiagnostics() call — nothing more needed here.
        };

        // Adaptive debounce delay: large files need more time to avoid competing with typing.
        int contentLen = codeEditText != null ? codeEditText.length() : 0;
        long diagDelay;
        if (lspBridge.isLspActive()) {
            diagDelay = 300L;
        } else {
            diagDelay = contentLen > 20000 ? 1500L : 800L;
        }
        jsonValidationHandler.postDelayed(runnableHolder[0], diagDelay);
    }
}
