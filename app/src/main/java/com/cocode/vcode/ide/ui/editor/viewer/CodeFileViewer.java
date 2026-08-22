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
            codeEditText.setOnTextLoadListener(isLoading -> {
                if (viewModel != null) {
                    viewModel.setEditorLoading(isLoading);
                }
            });

            // Attach LSP bridge now that the editor view exists.
            // setFile() will be called in bindFile() once a file is known.
            lspBridge.attach(codeEditText);

            if (context instanceof IEditorCallback) {
                editorCallback = (IEditorCallback) context;
                lspBridge.setEditorCallback(editorCallback);
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
                            int cursor = capturedFile.getCursorPosition();
                            if (cursor >= 0 && cursor <= capturedEditor.length()) {
                                capturedEditor.setSelection(cursor);
                            }
                            capturedEditor.scrollTo(0, capturedFile.getScrollY());
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

        // Only set text if it's different to prevent resetting cursor
        String currentText = codeEditText.getText() != null ? codeEditText.getText().toString() : "";
        if (!currentText.equals(file.getContent())) {
            codeEditText.setText(file.getContent());
            int cursor = file.getCursorPosition();
            if (cursor >= 0 && cursor <= codeEditText.length()) {
                codeEditText.setSelection(cursor);
            }
            codeEditText.scrollTo(0, file.getScrollY());
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
    }

    @Override
    public void destroy() {
        onPause();
        lspBridge.detach();
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
        if (settings != null) {
            jsonValidationHandler.removeCallbacksAndMessages(null);

            final EditorFile capturedFile = currentFile;
            final IEditorCallback capturedCallback = editorCallback;

            // Capture UI state on main thread
            final int cursor = codeEditText.getSelectionStart();
            final int scrollY = codeEditText.getScrollY();

            // Use a self-referencing Runnable array so it can re-post itself when the
            // async setText() hasn't finished applying content yet.
            final Runnable[] runnableHolder = new Runnable[1];
            runnableHolder[0] = () -> {
                if (capturedFile == null || capturedFile.getFile() == null) {
                    if (codeEditText != null)
                        codeEditText.applyDiagnostics(new java.util.ArrayList<>());
                    return;
                }

                // CRITICAL: Capture the text on the MAIN THREAD before dispatching to IO.
                // Reading codeEditText from a background thread while the main thread may be
                // calling setText() causes a race condition where the Content object is in a
                // partially-modified state. Per AGENTS.md: always snapshot on main thread.
                final String textSnapshot = codeEditText != null ? codeEditText.getTextAsString() : "";

                // If the snapshot is empty but the model has content, the async setText()
                // CPU task hasn't finished yet (Content.prepareLoad is still running).
                // Retry in 300ms instead of overwriting the model with an empty string.
                final String modelContent = capturedFile.getContent();
                final boolean modelIsEmpty = modelContent == null || modelContent.isEmpty();
                if (textSnapshot.isEmpty() && !modelIsEmpty) {
                    jsonValidationHandler.postDelayed(runnableHolder[0], 300L);
                    return;
                }

                ExecutorProvider.getInstance().runOnIo(() -> {
                    // 2. Update EditorFile with latest state so AutoSave will pick it up.
                    capturedFile.setContent(textSnapshot);
                    capturedFile.setCursorPosition(cursor);
                    capturedFile.setScrollY(scrollY);

                    // 3. Trigger AutoSave on Main Thread
                    if (settings.autoSave && !textSnapshot.isEmpty()) {
                        ExecutorProvider.getInstance().runOnMain(() -> viewModel.triggerAutoSave());
                    }

                    // 4. Run language diagnostics (bypassed if LSP active)
                    java.util.List<Problem> problems = null;
                    if (!lspBridge.isLspActive()) {
                        problems = com.cocode.vcode.ide.core.diagnostic.DiagnosticEngine.analyze(capturedFile.getFile(), textSnapshot, capturedFile.getFileType());
                    }

                    if (problems != null) {
                        final java.util.List<Problem> finalProblems = problems;
                        ExecutorProvider.getInstance().runOnMain(() -> {
                            if (editorLayout == null || editorLayout.getParent() == null || ((View) editorLayout.getParent()).getVisibility() != View.VISIBLE) {
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
            };

            // Set loading state immediately so the UI shows "Analyzing..." while waiting for debounce
            viewModel.setDiagnosticLoading();

            // Perf: adaptive delay — large files get more debounce time so diagnostics don't compete with typing
            int contentLen = codeEditText != null ? codeEditText.length() : 0;
            long diagDelay = contentLen > 20000 ? 1500L : 800L;
            jsonValidationHandler.postDelayed(runnableHolder[0], diagDelay);
        }
    }
}
