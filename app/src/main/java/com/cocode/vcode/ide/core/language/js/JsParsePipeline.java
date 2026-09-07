package com.cocode.vcode.ide.core.language.js;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.atomic.AtomicInteger;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.base.ParseModeGate;
import com.cocode.vcode.ide.utils.ExecutorProvider;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Background pipeline for parsing JavaScript/TypeScript files.
 * Debounces keystrokes by 250ms, parses the file on the diagnostic thread,
 * and commits a ParseResult to the UI thread via LiveData.
 */
public class JsParsePipeline {

    private static final JsParsePipeline instance = new JsParsePipeline();
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger requestId = new AtomicInteger(0);
    private final ConcurrentHashMap<String, FileBufferPool> bufferPools = new ConcurrentHashMap<>();

    private final MutableLiveData<ParseResult> parseResultLiveData = new MutableLiveData<>();

    public static JsParsePipeline getInstance() {
        return instance;
    }

    public LiveData<ParseResult> getParseResultLiveData() {
        return parseResultLiveData;
    }

    /**
     * Debounces and queues a full re-lex and re-parse of the provided source.
     */
    public void onTextChanged(File file, String source, int cursorOffset) {
        debounceHandler.removeCallbacksAndMessages(null);
        final int myRequestId = requestId.incrementAndGet();

        debounceHandler.postDelayed(() -> {
            ExecutorProvider.getInstance().runOnDiagnostic(() -> {
                if (myRequestId != requestId.get()) return;
                // L.1: the per-language tiering decision is now
                // made by the shared ParseModeGate, not by an
                // inline if-else. The JS-specific threshold of
                // 1000/5000/15000 lines is the default; the gate
                // accepts overrides for languages that need
                // different cutoffs.
                int lineCount = ParseModeGate.countLines(source);
                int mode = ParseModeGate.selectByLineCount(source);

                String poolKey = file != null ? file.getAbsolutePath() : "$scratch$";
                FileBufferPool pool = bufferPools.computeIfAbsent(poolKey, k -> new FileBufferPool());
                FileBufferPool.BufferPair buffers = pool.getInactiveBuffer();

                TokenStream tokens = JsLexer.tokenize(source, buffers.tokens);
                JsSyntaxTree tree = null;
                ScopeTree scopeTree;
                
                ParseResult cached = null;
                if (file != null) {
                    cached = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().getParseResult(file.getAbsolutePath());
                }

                int editStart = -1, editEndOld = -1, editEndNew = -1;
                if (cached != null && cached.source != null) {
                    String oldText = cached.source;
                    int minLen = Math.min(oldText.length(), source.length());
                    int pre = 0;
                    while (pre < minLen && oldText.charAt(pre) == source.charAt(pre)) pre++;
                    int oldSuf = oldText.length() - 1;
                    int newSuf = source.length() - 1;
                    while (oldSuf >= pre && newSuf >= pre && oldText.charAt(oldSuf) == source.charAt(newSuf)) {
                        oldSuf--;
                        newSuf--;
                    }
                    editStart = pre;
                    editEndOld = oldSuf + 1;
                    editEndNew = newSuf + 1;
                }

                if (mode == ParseResult.MODE_FULL || mode == ParseResult.MODE_LAZY_SCOPE) {
                    if (cached != null && cached.tree != null && editStart != -1 && editStart <= editEndOld) {
                        // Keep incremental parse unaltered for now (doesn't use buffer yet)
                        tree = JsParser.parseIncremental(source, tokens, cached.source, cached.tree, cached.tokens, editStart, editEndOld, editEndNew);
                    }
                    if (tree == null) {
                        tree = JsParser.parseFull(source, tokens, buffers.tree);
                    }
                } else if (mode == ParseResult.MODE_TOP_LEVEL) {
                    tree = JsParser.parseTopLevel(source, tokens); // Might want to buffer this too later
                } else {
                    tree = new JsSyntaxTree(16); // empty tree for fallback
                }

                scopeTree = ScopeTree.build(tree);
                ParseResult result = new ParseResult(file, source, tokens, tree, scopeTree, mode);
                
                if (file != null) {
                    com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().updateParseResult(file.getAbsolutePath(), result);
                }

                // Swap buffers right before publishing, saving the exact references we used
                pool.commitSwap(tokens, tree);

                debounceHandler.post(() -> {
                    if (myRequestId == requestId.get()) {
                        parseResultLiveData.setValue(result);
                    }
                });
            });
        }, 250);
    }
}
