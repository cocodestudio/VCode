package com.cocode.vcode.ide.core.lsp;

import android.os.Handler;
import android.os.Looper;

import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.views.CodeEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bridges {@link CodeEditText} to the in-process {@link LspClientManager}.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Listens to text changes via {@link CodeEditText.OnContentChangeListener}.</li>
 *   <li>Debounces rapid keystrokes (300 ms) before dispatching diagnostic requests.</li>
 *   <li>Builds an {@link LspDocument} snapshot from the editor's current text on every change.</li>
 *   <li>Converts {@link LspDiagnostic} results back into {@link Problem} objects and feeds
 *       them to {@link CodeEditText#applyDiagnostics(List)}.</li>
 *   <li>Exposes async methods for completion, definition, references, and signature help
 *       that callers can invoke directly (e.g., from a long-press context menu).</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Construct once and call {@link #attach(CodeEditText)} when a file is loaded.</li>
 *   <li>Call {@link #detach()} when the file is closed or the editor is destroyed.</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * All methods must be called on the Android main thread. The bridge posts debounced
 * work via {@link Handler} and receives all LSP callbacks on the main thread via
 * {@link LspCallback}.
 */
public final class LspEditorBridge {

    private static final long DIAGNOSTIC_DEBOUNCE_MS = 300L;
    private static final long COMPLETION_DEBOUNCE_MS = 100L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Monotonically increasing document version counter — invalidates stale responses.
     */
    private final AtomicInteger docVersion = new AtomicInteger(0);

    private CodeEditText editor;
    private File currentFile;
    private FileType fileType;

    /**
     * Whether the bridge is actively connected to an editor instance.
     */
    private boolean attached = false;
    private final Runnable diagnosticRunnable = this::performDiagnostics;

    // -------------------------------------------------------------------------
    // Debounce runnables — cancelled and rescheduled on every keystroke
    // -------------------------------------------------------------------------
    /**
     * True when the current language has a registered LSP server.
     * When true, the legacy autocomplete engine in {@link CodeEditText} is suppressed
     * and all completions flow exclusively through the LSP pipeline.
     */
    private boolean hasLspServer = false;
    private final Runnable completionRunnable = this::performCompletion;

    // -------------------------------------------------------------------------
    // ContentChangeListener wired to the editor
    // -------------------------------------------------------------------------

    private final CodeEditText.OnContentChangeListener contentListener = () -> {
        if (!attached || editor == null) return;
        docVersion.incrementAndGet();
        // Reschedule debounced diagnostics
        mainHandler.removeCallbacks(diagnosticRunnable);
        mainHandler.postDelayed(diagnosticRunnable, DIAGNOSTIC_DEBOUNCE_MS);
        // Reschedule debounced completion
        mainHandler.removeCallbacks(completionRunnable);
        mainHandler.postDelayed(completionRunnable, COMPLETION_DEBOUNCE_MS);
        // Notify ProjectIndex of the in-memory change (no IO, just updates the snapshot)
        updateProjectIndex();
    };

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Maps an LSP completion kind integer to the editor's {@link CompletionItem.Type} enum.
     */
    private static CompletionItem.Type mapKindToLegacy(int kind) {
        switch (kind) {
            case LspCompletionItem.KIND_FUNCTION:
                return CompletionItem.Type.FUNCTION;
            case LspCompletionItem.KIND_CLASS:
                return CompletionItem.Type.TAG;
            case LspCompletionItem.KIND_PROPERTY:
                return CompletionItem.Type.CSS_PROPERTY;
            case LspCompletionItem.KIND_VALUE:
                return CompletionItem.Type.CSS_VALUE;
            case LspCompletionItem.KIND_KEYWORD:
                return CompletionItem.Type.KEYWORD;
            case LspCompletionItem.KIND_SNIPPET:
                return CompletionItem.Type.SNIPPET;
            case LspCompletionItem.KIND_FILE:
                return CompletionItem.Type.FILE;
            case LspCompletionItem.KIND_FOLDER:
                return CompletionItem.Type.FOLDER;
            case LspCompletionItem.KIND_TEXT:
                return CompletionItem.Type.VALUE;
            default:
                return CompletionItem.Type.BUILTIN;
        }
    }

    /**
     * Converts a flat character offset to a zero-based LSP Position.
     */
    static LspPosition offsetToLspPosition(String text, int offset) {
        int line = 0;
        int lastNl = -1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lastNl = i;
            }
        }
        return new LspPosition(line, Math.max(0, offset - lastNl - 1));
    }

    /**
     * Attaches this bridge to the given editor instance and file.
     * Safe to call multiple times — detaches from the previous editor first.
     *
     * @param codeEditor the editor view to observe
     */
    public void attach(CodeEditText codeEditor) {
        detach();
        this.editor = codeEditor;
        this.fileType = codeEditor.getFileType();
        this.attached = true;
        codeEditor.addContentChangeListener(contentListener);
        // Pass the application context so LSP servers can load JSON assets (keywords, etc.)
        LspClientManager.getInstance().setApplicationContext(codeEditor.getContext());
    }

    /**
     * Sets the file currently open in the editor.
     * Must be called after {@link #attach(CodeEditText)} whenever a new file is loaded.
     *
     * @param file the open file (used as the document URI)
     */
    public void setFile(File file) {
        this.currentFile = file;
        this.fileType = editor != null ? editor.getFileType() : fileType;
        // Determine whether this language has a registered LSP server.
        // If so, suppress the legacy autocomplete engine so LSP is the sole source.
        String languageId = fileType != null ? fileType.getLspLanguageId() : "plaintext";
        hasLspServer = !"plaintext".equals(languageId);
        if (editor != null) {
            editor.suppressLegacyAutoComplete(hasLspServer);
        }
        docVersion.incrementAndGet();
        updateProjectIndex();
        // Kick off background project-wide indexing the first time a file is set.
        // This populates ProjectIndex so that Go to Definition / Find References work
        // across all files in the project, not just the currently open one.
        if (file != null && file.getParentFile() != null) {
            File projectRoot = file.getParentFile();
            ProjectIndex.getInstance().indexProject(projectRoot, null);
        }
        // Trigger an immediate diagnostic pass for the newly opened file
        mainHandler.removeCallbacks(diagnosticRunnable);
        mainHandler.post(diagnosticRunnable);
    }

    /**
     * Requests completion items at the current caret position.
     * The result is delivered to the editor's AutoCompletePopup on the main thread.
     *
     * @param callback optional external callback; may be null
     */
    public void requestCompletion(LspCallback<List<LspCompletionItem>> callback) {
        LspDocument doc = buildSnapshot();
        if (doc == null) {
            if (callback != null) callback.onResult(Collections.emptyList());
            return;
        }
        LspPosition pos = cursorPosition();
        LspClientManager.getInstance().requestCompletion(doc, pos, new LspCallback<List<LspCompletionItem>>() {
            @Override
            public void onResult(List<LspCompletionItem> result) {
                if (callback != null) callback.onResult(result);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    /**
     * Requests the definition location for the symbol under the caret.
     *
     * @param callback delivers the result on the main thread
     */
    public void requestDefinition(LspCallback<LspLocation> callback) {
        LspDocument doc = buildSnapshot();
        if (doc == null) {
            if (callback != null) callback.onError("No document");
            return;
        }
        LspClientManager.getInstance().requestDefinition(doc, cursorPosition(), callback);
    }

    /**
     * Requests all references to the symbol under the caret.
     *
     * @param callback delivers the result list on the main thread
     */
    public void requestReferences(LspCallback<List<LspLocation>> callback) {
        LspDocument doc = buildSnapshot();
        if (doc == null) {
            if (callback != null) callback.onError("No document");
            return;
        }
        LspClientManager.getInstance().requestReferences(doc, cursorPosition(), callback);
    }

    // -------------------------------------------------------------------------
    // Private — debounced operations
    // -------------------------------------------------------------------------

    /**
     * Requests signature help at the current caret position.
     * Typically called when the user types {@code (} or {@code ,}.
     *
     * @param callback delivers the result on the main thread
     */
    public void requestSignatureHelp(LspCallback<LspSignatureHelp> callback) {
        LspDocument doc = buildSnapshot();
        if (doc == null) {
            if (callback != null) callback.onResult(null);
            return;
        }
        LspClientManager.getInstance().requestSignatureHelp(doc, cursorPosition(), callback);
    }

    /**
     * Detaches this bridge from the editor, cancelling all pending callbacks.
     * Safe to call even if not attached.
     */
    public void detach() {
        attached = false;
        mainHandler.removeCallbacks(diagnosticRunnable);
        mainHandler.removeCallbacks(completionRunnable);
        if (editor != null) {
            editor.removeContentChangeListener(contentListener);
            editor = null;
        }
        currentFile = null;
    }

    // -------------------------------------------------------------------------
    // Private — LSP → legacy CompletionItem conversion
    // -------------------------------------------------------------------------

    private void performDiagnostics() {
        if (!attached || editor == null) return;
        LspDocument doc = buildSnapshot();
        if (doc == null) return;
        final int capturedVersion = doc.version;

        LspClientManager.getInstance().requestDiagnostics(doc, new LspCallback<List<LspDiagnostic>>() {
            @Override
            public void onResult(List<LspDiagnostic> result) {
                // Discard stale result if the document has changed since the request
                if (capturedVersion != docVersion.get()) return;
                if (!attached || editor == null) return;
                editor.applyDiagnostics(convertDiagnostics(result));
            }

            @Override
            public void onError(String errorMessage) {
                // Server not ready yet — clear any stale squiggles
                if (attached && editor != null && capturedVersion == docVersion.get()) {
                    editor.applyDiagnostics(new ArrayList<>());
                }
            }
        });
    }

    private void performCompletion() {
        if (!attached || editor == null || !hasLspServer) return;
        LspDocument doc = buildSnapshot();
        if (doc == null || doc.text == null) return;

        // Fast-path: prevent autocomplete popup from flashing/triggering on
        // newlines, spaces, backspaces on empty lines, and non-trigger symbols.
        int flatCursor = getCursorFlatOffset();
        if (flatCursor <= 0 || flatCursor > doc.text.length()) {
            editor.dismissAutoCompletePopup();
            return;
        }

        char lastChar = doc.text.charAt(flatCursor - 1);
        if (Character.isWhitespace(lastChar)) {
            editor.dismissAutoCompletePopup();
            return;
        }

        String triggerChars = ".</:'\"@#!";
        boolean isTriggerChar = triggerChars.indexOf(lastChar) >= 0;
        boolean isIdentifier = Character.isLetterOrDigit(lastChar)
                || lastChar == '_' || lastChar == '$' || lastChar == '-';

        if (!isIdentifier && !isTriggerChar) {
            editor.dismissAutoCompletePopup();
            return;
        }

        LspPosition pos = cursorPosition();
        final int capturedVersion = docVersion.get();

        LspClientManager.getInstance().requestCompletion(doc, pos, new LspCallback<List<LspCompletionItem>>() {
            @Override
            public void onResult(List<LspCompletionItem> result) {
                if (capturedVersion != docVersion.get() || !attached || editor == null) return;
                if (result != null && !result.isEmpty()) {
                    editor.showLspCompletions(convertToLegacy(result));
                } else {
                    // No completions for this position — dismiss any stale popup
                    editor.dismissAutoCompletePopup();
                }
            }

            @Override
            public void onError(String errorMessage) {
                // Server not ready — dismiss stale popup silently
                if (attached && editor != null && capturedVersion == docVersion.get()) {
                    editor.dismissAutoCompletePopup();
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Private — helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a list of {@link LspCompletionItem} objects returned by the LSP server
     * into the editor's native {@link CompletionItem} format expected by
     * {@link com.cocode.vcode.ide.views.AutoCompletePopup}.
     */
    private List<CompletionItem> convertToLegacy(List<LspCompletionItem> lspItems) {
        List<CompletionItem> out = new ArrayList<>(lspItems.size());
        for (LspCompletionItem li : lspItems) {
            if (li == null || li.label == null) continue;
            String insert = (li.insertText != null && !li.insertText.isEmpty())
                    ? li.insertText : li.label;
            // Compute the cursor offset inside the insert text if a '|' marker is present.
            // The '|' convention is used by the autocomplete engines to mark cursor position.
            int cursorOffset = 0;
            int pipeIdx = insert.indexOf('|');
            if (pipeIdx >= 0) {
                cursorOffset = -(insert.length() - pipeIdx - 1);
                insert = insert.replace("|", "");
            }
            CompletionItem ci = new CompletionItem(
                    li.label, insert, li.detail, mapKindToLegacy(li.kind), cursorOffset);
            out.add(ci);
        }
        return out;
    }

    /**
     * Builds an immutable {@link LspDocument} snapshot from the editor's current content.
     * Returns null if the editor or file is not available.
     */
    private LspDocument buildSnapshot() {
        if (editor == null || currentFile == null) return null;
        String text = editor.getText() != null ? editor.getText().toString() : "";
        String languageId = fileType != null ? fileType.getLspLanguageId() : "plaintext";
        return new LspDocument(currentFile.getAbsolutePath(), text, languageId, docVersion.get());
    }

    /**
     * Returns the current caret position as an {@link LspPosition}.
     * Falls back to (0, 0) if the position cannot be determined.
     */
    private LspPosition cursorPosition() {
        // CodeEditText exposes the cursor via its content model.
        // We read the full text and compute line/char from the flat offset.
        if (editor == null) return new LspPosition(0, 0);
        try {
            // getText() returns the full content; find newlines to determine line/char
            CharSequence text = editor.getText();
            if (text == null) return new LspPosition(0, 0);
            // Use getCursorOffset if it's available (added in Phase 2 wiring);
            // otherwise fall back to a content scan.
            return offsetToLspPosition(text.toString(), getCursorFlatOffset());
        } catch (Exception e) {
            return new LspPosition(0, 0);
        }
    }

    /**
     * Reads the flat cursor offset from the editor using the public
     * {@link CodeEditText#getSelectionStart()} accessor.
     */
    private int getCursorFlatOffset() {
        if (editor == null) return 0;
        try {
            return editor.getSelectionStart();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Updates the {@link ProjectIndex} with the latest in-memory snapshot of the current file.
     */
    private void updateProjectIndex() {
        LspDocument doc = buildSnapshot();
        if (doc != null) {
            ProjectIndex.getInstance().updateDocument(doc);
        }
    }

    /**
     * Converts a list of {@link LspDiagnostic} objects into the editor's native
     * {@link Problem} list format so they can be rendered as squiggly underlines.
     */
    private List<Problem> convertDiagnostics(List<LspDiagnostic> lspDiagnostics) {
        if (lspDiagnostics == null || lspDiagnostics.isEmpty()) return new ArrayList<>();
        List<Problem> problems = new ArrayList<>(lspDiagnostics.size());
        for (LspDiagnostic d : lspDiagnostics) {
            if (d == null || d.range == null) continue;
            int line = d.range.start.line;
            int col = d.range.start.character;
            int endCol = d.range.end.character;
            int length = Math.max(1, endCol - col);
            Problem.Severity severity = d.severity == LspDiagnostic.SEVERITY_ERROR
                    ? Problem.Severity.ERROR
                    : d.severity == LspDiagnostic.SEVERITY_WARNING
                    ? Problem.Severity.WARNING
                    : Problem.Severity.INFO;
            // Problem constructor is 1-indexed for line; LSP is 0-indexed
            problems.add(new Problem(currentFile, line + 1, col, length, d.message, severity));
        }
        return problems;
    }
}
