package com.cocode.vcode.ide.core.lsp;

import android.os.Handler;
import android.os.Looper;

import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.views.CodeEditText;

import com.cocode.vcode.ide.ui.editor.viewer.IEditorCallback;
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
 *   <li>Feeds LSP diagnostic results to {@link CodeEditText#applyDiagnostics(List)}.</li>
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
    private IEditorCallback editorCallback;
    private final Runnable diagnosticRunnable = this::performDiagnostics;
    /**
     * Tracks whether the one-time incremental project index scan has been started
     * for the current project session. Reset to {@code false} by {@link #reset()}
     * when the project is closed. This prevents {@link #setFile(File)} from launching
     * a new full index scan on every tab switch, which would wipe live in-memory data.
     */
    private static final java.util.concurrent.atomic.AtomicBoolean hasIndexedProject = new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * True from the moment {@link #setFile(File)} switches {@code currentFile} until
     * {@link #textLoadListener} reports the load complete. {@link CodeEditText#setText}
     * (which loads the new file's actual text into the editor) runs AFTER
     * {@link #setFile(File)} returns and does so asynchronously — so {@code editor.getText()}
     * still holds the PREVIOUS file's content for a window after the switch. Note this can
     * NOT be detected via {@code OnContentChangeListener}: {@code setText()} is a bulk load
     * that bypasses the insert/delete edit path that listener is wired to, so it never fires
     * for a file open. While this flag is set, {@link #buildSnapshot()}'s (uri, text) pairing
     * cannot be trusted, so diagnostics are withheld. {@link #textLoadListener} clears the
     * flag when the load actually completes and triggers the deferred diagnostic pass.
     */
    private boolean contentSyncPending = false;

    /**
     * Fires when {@link CodeEditText#setText} finishes loading new content into the editor.
     * Used to detect the moment a file switch's real content has landed (see
     * {@link #contentSyncPending}) and to run the diagnostic pass that {@link #setFile(File)}
     * had to defer, now that {@code editor.getText()} is guaranteed to match {@code currentFile}.
     */
    private final CodeEditText.OnTextLoadListener textLoadListener = isLoading -> {
        if (isLoading || !contentSyncPending) return;
        contentSyncPending = false;
        if (!attached || editor == null) return;
        mainHandler.removeCallbacks(diagnosticRunnable);
        if (editorCallback != null && currentFile != null) editorCallback.reportDiagnosticLoading(currentFile);
        mainHandler.post(diagnosticRunnable);
    };

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
    private final Runnable signatureHelpRunnable = this::performSignatureHelp;
    
    private final Runnable cursorChangeListener = () -> {
        if (!attached || editor == null) return;
        mainHandler.removeCallbacks(signatureHelpRunnable);
        mainHandler.postDelayed(signatureHelpRunnable, COMPLETION_DEBOUNCE_MS);
    };

    public void setEditorCallback(IEditorCallback callback) {
        this.editorCallback = callback;
    }

    public boolean isLspActive() {
        return hasLspServer;
    }

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
        if (!editor.isInsertingCompletion()) {
            mainHandler.postDelayed(completionRunnable, COMPLETION_DEBOUNCE_MS);
        } else {
            editor.dismissAutoCompletePopup();
        }
        
        mainHandler.removeCallbacks(signatureHelpRunnable);
        mainHandler.postDelayed(signatureHelpRunnable, COMPLETION_DEBOUNCE_MS);
        
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
        codeEditor.addCursorChangeListener(cursorChangeListener);
        codeEditor.addTextLoadListener(textLoadListener);
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
        File previousFile = this.currentFile;

        // 1. Flush the previous file's LATEST content under its own URI, then
        //    re-derive its symbols. Do this BEFORE overwriting this.currentFile.
        if (previousFile != null) {
            docVersion.incrementAndGet();
            updateProjectIndex();
            if (!previousFile.equals(file)) {
                String prevPath = previousFile.getAbsolutePath();
                com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex.getInstance().invalidateFile(prevPath);
                ProjectIndex.getInstance().reindexFile(previousFile);
                com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex.getInstance().updateFileFromIndex(previousFile);
            }
        }

        // 2. Switch to the new file
        this.currentFile = file;
        this.fileType = editor != null ? editor.getFileType() : fileType;

        // 3. Ensure the new file has an entry in ProjectIndex.
        //    We CANNOT use updateProjectIndex() here because the editor still
        //    contains the previous file's text — the caller's editor.setText(newContent)
        //    call hasn't happened yet.
        //    Use the existing in-memory document if available, otherwise schedule
        //    a disk read from the incremental scanner.
        if (file != null) {
            String uri = file.getAbsolutePath();
            boolean incrementalWillRun = hasIndexedProject.get() == false; // about to be flipped below
            if (!incrementalWillRun && ProjectIndex.getInstance().getDocument(uri) == null) {
                // Only schedule a single-file read when the incremental scan has ALREADY run.
                // If it hasn't run yet, the upcoming indexProjectIncremental() will cover it.
                final File fileRef = file;
                com.cocode.vcode.ide.utils.ExecutorProvider.getInstance().runOnIo(() -> ProjectIndex.getInstance().indexFile(fileRef));
            }
        }

        String languageId = this.fileType != null ? this.fileType.getLspLanguageId() : "plaintext";
        hasLspServer = !"plaintext".equals(languageId);
        if (editor != null) {
            editor.suppressLegacyAutoComplete(hasLspServer);
        }
        // ONE-TIME incremental project scan per session. Unlike indexProject() this does
        // NOT wipe live in-memory data — it only fills gaps for files not yet opened.
        if (file != null && hasIndexedProject.compareAndSet(false, true)) {
            File projectRoot = com.cocode.vcode.ide.data.repository.ProjectRepository.findProjectRoot(file);
            if (projectRoot == null) projectRoot = file.getParentFile();
            ProjectIndex.getInstance().indexProjectIncremental(projectRoot);
        }
        // Do NOT post an immediate diagnostic pass here. The caller's subsequent
        // editor.setText(newContent) call — which actually loads the new file's text into
        // the editor — runs AFTER this method returns and completes asynchronously, so
        // editor.getText() still holds the PREVIOUS file's content right now — see the
        // ProjectIndex comment above. Posting a diagnostic run here would build a snapshot
        // that pairs the NEW file's URI with the OLD file's text. Instead, mark the switch
        // as pending; textLoadListener fires once that setText() call completes and runs
        // diagnostics immediately at that point (see textLoadListener). Note this can't be
        // detected via contentListener/OnContentChangeListener — setText() is a bulk load
        // that never triggers it (see contentSyncPending).
        mainHandler.removeCallbacks(diagnosticRunnable);
        this.contentSyncPending = (file != null);
    }


    public void clearContentSyncPending() {
        if (!attached || !contentSyncPending) return;
        contentSyncPending = false;
        mainHandler.removeCallbacks(diagnosticRunnable);
        if (editorCallback != null && currentFile != null) editorCallback.reportDiagnosticLoading(currentFile);
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
        LspClientManager.getInstance().requestReferences(doc, cursorPosition(), new LspCallback<List<LspLocation>>() {
            @Override
            public void onResult(List<LspLocation> result) {
                if (callback == null) return;
                if (result != null) {
                    callback.onResult(result);
                } else {
                    callback.onResult(Collections.emptyList());
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
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
        contentSyncPending = false;
        mainHandler.removeCallbacks(diagnosticRunnable);
        mainHandler.removeCallbacks(completionRunnable);
        mainHandler.removeCallbacks(signatureHelpRunnable);
        if (editor != null) {
            editor.removeContentChangeListener(contentListener);
            editor.removeCursorChangeListener(cursorChangeListener);
            editor.removeTextLoadListener(textLoadListener);
            editor.dismissSignatureHint();
            editor = null;
        }
        // DO NOT clear currentFile here — it is needed by setFile() to detect the previous file
    }

    /**
     * Completely resets the bridge state. Call this when the project is closed.
     */
    public void reset() {
        detach();
        currentFile = null;
        // Do NOT reset hasIndexedProject here — it is session-level state shared
        // across all tabs. Only resetProjectSession() should reset it.
    }

    /**
     * Called only when the entire project session is closed (different from tab close).
     */
    public static void resetProjectSession() {
        hasIndexedProject.set(false);
    }

    // -------------------------------------------------------------------------
    // Private — LSP → legacy CompletionItem conversion
    // -------------------------------------------------------------------------

    private void performDiagnostics() {
        if (!attached || editor == null) return;
        // Defense in depth: if we're still waiting for the post-file-switch content sync
        // (see contentSyncPending), editor.getText() may not correspond to currentFile yet.
        // Bail rather than risk sending a mismatched (uri, text) pair — textLoadListener will
        // re-trigger this once the real content lands.
        if (contentSyncPending) return;
        LspDocument doc = buildSnapshot();
        if (doc == null) return;
        final int capturedVersion = doc.version;
        
        if (editorCallback != null && currentFile != null) {
            editorCallback.reportDiagnosticLoading(currentFile);
        }

        LspClientManager.getInstance().requestDiagnostics(doc, new LspCallback<List<Problem>>() {
            @Override
            public void onResult(List<Problem> result) {
                // Discard stale result if the document has changed since the request
                if (capturedVersion != docVersion.get()) return;
                if (!attached || editor == null) return;
                editor.applyDiagnostics(result);
                if (editorCallback != null && currentFile != null) {
                    editorCallback.reportProblems(currentFile, result);
                }
            }

            @Override
            public void onError(String errorMessage) {
                // Server not ready yet — clear any stale squiggles and clear the analyzing UI
                if (attached && editor != null && capturedVersion == docVersion.get()) {
                    editor.applyDiagnostics(new ArrayList<>());
                    if (editorCallback != null && currentFile != null) {
                        editorCallback.reportProblems(currentFile, new ArrayList<>());
                    }
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
        
        final boolean isInsideCallArgs = isInsideCallArguments(doc.text, flatCursor);
        final boolean isInsideFuncDef = isInsideFunctionDefinition(doc.text, flatCursor);

        if (isInsideFuncDef) {
            editor.dismissAutoCompletePopup();
            return;
        }

        char lastChar = doc.text.charAt(flatCursor - 1);
        if (Character.isWhitespace(lastChar) && !isInsideCallArgs) {
            editor.dismissAutoCompletePopup();
            return;
        }

        String triggerChars = ".</:'\"@#!";
        boolean isTriggerChar = triggerChars.indexOf(lastChar) >= 0;
        boolean isIdentifier = Character.isLetterOrDigit(lastChar)
                || lastChar == '_' || lastChar == '$' || lastChar == '-';

        if (!isIdentifier && !isTriggerChar && !isInsideCallArgs) {
            editor.dismissAutoCompletePopup();
            return;
        }

        LspPosition pos = cursorPosition();
        final int capturedVersion = docVersion.get();

        LspClientManager.getInstance().requestCompletion(doc, pos, new LspCallback<List<LspCompletionItem>>() {
            @Override
            public void onResult(List<LspCompletionItem> result) {
                if (capturedVersion != docVersion.get() || !attached || editor == null) return;
                
                if (result != null && isInsideCallArgs) {
                    result = filterForArgumentContext(result);
                }
                
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

    private static boolean isInsideCallArguments(String text, int cursor) {
        if (cursor <= 0 || cursor > text.length()) return false;
        int i = cursor - 1;
        int parenDepth = 0;
        int braceDepth = 0;
        boolean inString = false;
        char stringChar = 0;
        int maxDepth = 2000;
        int scanned = 0;
        
        while (i >= 0 && scanned < maxDepth) {
            scanned++;
            char c = text.charAt(i);
            
            if (inString) {
                if (c == stringChar) {
                    int backslashes = 0;
                    int k = i - 1;
                    while (k >= 0 && text.charAt(k) == '\\') {
                        backslashes++;
                        k--;
                    }
                    if (backslashes % 2 == 0) {
                        inString = false;
                    }
                }
            } else {
                if (c == '"' || c == '\'' || c == '`') {
                    inString = true;
                    stringChar = c;
                } else if (c == ')') {
                    parenDepth++;
                } else if (c == '(') {
                    if (parenDepth == 0) {
                        return true;
                    }
                    parenDepth--;
                } else if (c == '}') {
                    braceDepth++;
                } else if (c == '{') {
                    if (braceDepth > 0) {
                        braceDepth--;
                    } else if (parenDepth == 0) {
                        return false;
                    }
                } else if (c == ';') {
                    if (parenDepth == 0) {
                        return false;
                    }
                }
            }
            i--;
        }
        return false;
    }

    private static boolean isInsideFunctionDefinition(String text, int cursor) {
        if (cursor <= 0 || cursor > text.length()) return false;
        
        // Check for single-arg arrow function (no parens) e.g. `const fn = x =>`
        int forward = cursor;
        while (forward < text.length() && forward - cursor < 500) {
            char c = text.charAt(forward);
            if (Character.isWhitespace(c) || Character.isLetterOrDigit(c) || c == '_' || c == '$') {
                forward++;
            } else if (c == '=' && forward + 1 < text.length() && text.charAt(forward + 1) == '>') {
                return true;
            } else {
                break;
            }
        }

        int i = cursor - 1;
        int parenDepth = 0;
        int braceDepth = 0;
        boolean inString = false;
        char stringChar = 0;
        int maxDepth = 2000;
        int scanned = 0;
        
        while (i >= 0 && scanned < maxDepth) {
            scanned++;
            char c = text.charAt(i);
            if (inString) {
                if (c == stringChar) {
                    int backslashes = 0;
                    int k = i - 1;
                    while (k >= 0 && text.charAt(k) == '\\') { backslashes++; k--; }
                    if (backslashes % 2 == 0) inString = false;
                }
            } else {
                if (c == '"' || c == '\'' || c == '`') {
                    inString = true;
                    stringChar = c;
                } else if (c == ')') {
                    parenDepth++;
                } else if (c == '(') {
                    if (parenDepth == 0) {
                        // Found the opening parenthesis. Now check what precedes it.
                        int j = i - 1;
                        while (j >= 0 && Character.isWhitespace(text.charAt(j))) j--;
                        if (j < 0) return false;
                        
                        // Check if it's an arrow function: `(...) =>`
                        int fwd = cursor;
                        while (fwd < text.length() && fwd - cursor < 500) {
                            char fc = text.charAt(fwd);
                            if (Character.isWhitespace(fc) || Character.isLetterOrDigit(fc) || 
                                fc == ',' || fc == ')' || fc == ':' || fc == '<' || fc == '>' || fc == '[' || fc == ']' || fc == '_' || fc == '$') {
                                if (fc == ')') {
                                    int next = fwd + 1;
                                    while (next < text.length() && Character.isWhitespace(text.charAt(next))) next++;
                                    if (next + 1 < text.length() && text.charAt(next) == '=' && text.charAt(next + 1) == '>') {
                                        return true;
                                    }
                                    break;
                                }
                                fwd++;
                            } else {
                                break;
                            }
                        }
                        
                        // Check for standard function: `function foo(` or `class A { constructor(` or `foo(` in a class
                        int endWord = j + 1;
                        while (j >= 0 && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_' || text.charAt(j) == '$')) j--;
                        int startWord = j + 1;
                        if (startWord <= endWord) {
                            String word = text.substring(startWord, endWord);
                            while (j >= 0 && Character.isWhitespace(text.charAt(j))) j--;
                            
                            if (word.equals("function") || word.equals("constructor") || word.equals("catch")) {
                                return true;
                            }
                            
                            // Check if preceded by "function" (e.g. `function foo(`)
                            if (j >= 7) {
                                int k = j;
                                int endKeyword = k + 1;
                                while (k >= 0 && (Character.isLetterOrDigit(text.charAt(k)) || text.charAt(k) == '_' || text.charAt(k) == '$')) k--;
                                int startKeyword = k + 1;
                                if (startKeyword < endKeyword) {
                                    String keyword = text.substring(startKeyword, endKeyword);
                                    if (keyword.equals("function")) {
                                        return true;
                                    }
                                }
                            }
                            
                            // Check for class methods by finding the enclosing '{' and tracing back to 'class'
                            int tempDepth = 0;
                            int classBrace = -1;
                            int scanIndex = startWord - 1;
                            int braceLookDepth = 2000;
                            while (scanIndex >= 0 && braceLookDepth-- > 0) {
                                char sc = text.charAt(scanIndex);
                                if (sc == '}') {
                                    tempDepth++;
                                } else if (sc == '{') {
                                    if (tempDepth == 0) {
                                        classBrace = scanIndex;
                                        break;
                                    }
                                    tempDepth--;
                                }
                                scanIndex--;
                            }

                            if (classBrace != -1) {
                                int k = classBrace - 1;
                                int maxLook = 150;
                                boolean foundClass = false;
                                int braceSkipDepth = 0;
                                while (k >= 0 && maxLook > 0) {
                                    char sc = text.charAt(k);
                                    if (sc == '}') {
                                        braceSkipDepth++;
                                    } else if (sc == '{') {
                                        if (braceSkipDepth > 0) {
                                            braceSkipDepth--;
                                        } else {
                                            break; // Hit an unmatched '{' — went too far
                                        }
                                    } else if (sc == ';' && braceSkipDepth == 0) {
                                        break; // Statement boundary outside any brace pair
                                    } else if (braceSkipDepth == 0 && k >= 4 &&
                                               text.substring(k - 4, k + 1).equals("class") &&
                                               (k == 4 || !Character.isLetterOrDigit(text.charAt(k - 5))) &&
                                               (k == text.length() - 1 || !Character.isLetterOrDigit(text.charAt(k + 1)))) {
                                        foundClass = true;
                                        break;
                                    }
                                    k--;
                                    // Only spend the lookback budget on chars OUTSIDE skipped brace
                                    // pairs (method bodies). Otherwise a class with a couple of
                                    // sibling methods before the current one burns through maxLook
                                    // while skipping their bodies and never reaches the `class`
                                    // keyword, incorrectly reporting foundClass = false.
                                    if (braceSkipDepth == 0) maxLook--;
                                }
                                
                                if (foundClass && j >= 0) {
                                    // Extra guard: ensure the context looks like a class body, not an object literal.
                                    // In a class body, the first non-whitespace after '{' should be a modifier, method name,
                                    // or '#'. In an object literal, a property is always followed by ':' or '('.
                                    // We verify: at no point between classBrace+1 and startWord does a bare ':' appear
                                    // at depth 0 (outside of nested parens/braces/strings). If it does, it's object literal.
                                    boolean looksLikeClassBody = true;
                                    int checkDepth = 0;
                                    boolean inStr = false;
                                    char strCh = 0;
                                    for (int ci = classBrace + 1; ci < startWord && ci < text.length(); ci++) {
                                        char ch = text.charAt(ci);
                                        if (inStr) {
                                            if (ch == strCh) inStr = false;
                                        } else if (ch == '"' || ch == '\'' || ch == '`') {
                                            inStr = true; strCh = ch;
                                        } else if (ch == '(' || ch == '[' || ch == '{') {
                                            checkDepth++;
                                        } else if (ch == ')' || ch == ']' || ch == '}') {
                                            checkDepth--;
                                        } else if (ch == ':' && checkDepth == 0) {
                                            // A bare ':' can be either:
                                            //   - Object literal property separator: { key: value, ... }
                                            //   - TypeScript type annotation: class { prop: Type; method() }
                                            // Distinguish by checking if a ';' appears before the next unbalanced ','
                                            // If yes: it's a TS type annotation — don't disqualify.
                                            // If no: it's an object literal separator — disqualify.
                                            boolean foundSemicolon = false;
                                            int lookahead = ci + 1;
                                            int laDepth = 0;
                                            while (lookahead < startWord && lookahead < text.length()) {
                                                char lc = text.charAt(lookahead);
                                                if (lc == '(' || lc == '[' || lc == '{') laDepth++;
                                                else if (lc == ')' || lc == ']' || lc == '}') laDepth--;
                                                else if (lc == ';' && laDepth == 0) { foundSemicolon = true; break; }
                                                else if (lc == ',' && laDepth == 0) break; // object literal separator
                                                lookahead++;
                                            }
                                            if (!foundSemicolon) {
                                                looksLikeClassBody = false;
                                                break;
                                            }
                                            // else: it's a type annotation — continue scanning
                                        }
                                    }
                                    
                                    if (!looksLikeClassBody) {
                                        return false; // object literal, not a class body
                                    }
                                    
                                    char beforeName = text.charAt(j);
                                    if (beforeName == '{' || beforeName == '}' || beforeName == ';') {
                                        return true;
                                    }
                                    
                                    // Modifiers (static, async, get, set, etc.)
                                    int m = j;
                                    int endMod = m + 1;
                                    while (m >= 0 && (Character.isLetterOrDigit(text.charAt(m)) || text.charAt(m) == '_' || text.charAt(m) == '$')) m--;
                                    int startMod = m + 1;
                                    if (startMod < endMod) {
                                        String mod = text.substring(startMod, endMod);
                                        if (mod.equals("static") || mod.equals("async") || mod.equals("get") || mod.equals("set") || 
                                            mod.equals("public") || mod.equals("private") || mod.equals("protected") || mod.equals("readonly")) {
                                            return true;
                                        }
                                    }
                                    
                                    // Fallback for methods preceded by comments e.g. /* ... */ method()
                                    if (beforeName == '/' || beforeName == '*') {
                                        return true;
                                    }
                                }
                            }
                        }
                        return false; // Found a `(`, but doesn't look like a definition
                    }
                    parenDepth--;
                } else if (c == '}') {
                    braceDepth++;
                } else if (c == '{') {
                    if (braceDepth > 0) braceDepth--;
                    else if (parenDepth == 0) return false;
                } else if (c == ';') {
                    if (parenDepth == 0) return false;
                }
            }
            i--;
        }
        return false;
    }

    private static List<LspCompletionItem> filterForArgumentContext(List<LspCompletionItem> items) {
        List<LspCompletionItem> filtered = new ArrayList<>();
        for (LspCompletionItem item : items) {
            int k = item.kind;
            if (k == LspCompletionItem.KIND_FUNCTION
                    || k == LspCompletionItem.KIND_VARIABLE
                    || k == LspCompletionItem.KIND_VALUE
                    || k == LspCompletionItem.KIND_TEXT
                    || k == LspCompletionItem.KIND_PROPERTY
                    || k == LspCompletionItem.KIND_FILE
                    || k == LspCompletionItem.KIND_FOLDER) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private void performSignatureHelp() {
        if (!attached || editor == null || !hasLspServer) return;
        LspDocument doc = buildSnapshot();
        if (doc == null || doc.text == null) return;

        LspPosition pos = cursorPosition();
        final int capturedVersion = docVersion.get();
        
        LspClientManager.getInstance().requestSignatureHelp(doc, pos, new LspCallback<LspSignatureHelp>() {
            @Override
            public void onResult(LspSignatureHelp result) {
                if (capturedVersion != docVersion.get() || !attached || editor == null) return;
                if (result != null) {
                    editor.showSignatureHint(result);
                } else {
                    editor.dismissSignatureHint();
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (attached && editor != null && capturedVersion == docVersion.get()) {
                    editor.dismissSignatureHint();
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
            ci.setReplaceLength(li.replaceLength);
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
            return SymbolExtractor.offsetToPosition(text.toString(), getCursorFlatOffset());
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


}
