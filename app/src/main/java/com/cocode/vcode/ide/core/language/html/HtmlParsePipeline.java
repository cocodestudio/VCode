package com.cocode.vcode.ide.core.language.html;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.css.CssParser;
import com.cocode.vcode.ide.core.language.css.CssSyntaxTree;
import com.cocode.vcode.ide.core.language.css.CssTokenStream;
import com.cocode.vcode.ide.core.language.js.JsParser;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.language.js.ParseResult;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.utils.ExecutorProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background pipeline for parsing HTML files incrementally.
 */
public class HtmlParsePipeline {

    private static final HtmlParsePipeline instance = new HtmlParsePipeline();
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger requestId = new AtomicInteger(0);

    private final MutableLiveData<ParseResult> parseResultLiveData = new MutableLiveData<>();

    public static HtmlParsePipeline getInstance() {
        return instance;
    }

    public LiveData<ParseResult> getParseResultLiveData() {
        return parseResultLiveData;
    }

    public void onTextChanged(File file, String source, int cursorOffset) {
        debounceHandler.removeCallbacksAndMessages(null);
        final int myRequestId = requestId.incrementAndGet();

        debounceHandler.postDelayed(() -> {
            ExecutorProvider.getInstance().runOnDiagnostic(() -> {
                if (myRequestId != requestId.get()) return;
                ParseResult cached = null;
                if (file != null) {
                    cached = ProjectIndex.getInstance().getParseResult(file.getAbsolutePath());
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

                int mode = com.cocode.vcode.ide.core.language.base.ParseModeGate.selectByLineCount(source);
                ParseResult result = null;

                if (mode == ParseResult.MODE_TOKENIZE_ONLY) {
                    HtmlTokenStream tokens = HtmlLexer.tokenize(source);
                    result = new ParseResult(file, source, tokens, null);
                } else {
                    if (cached != null && cached.htmlTree != null && editStart != -1 && editStart <= editEndOld) {
                        String insertedText = source.substring(editStart, editEndNew);
                        // Check if edit might break tag structure
                        if (!insertedText.contains("<") && !insertedText.contains(">") && !insertedText.contains("\"") && !insertedText.contains("'")) {
                            // Find if edit is fully inside one EmbeddedResult
                            for (int i = 0; i < cached.embeddedResults.size(); i++) {
                                ParseResult.EmbeddedResult emb = cached.embeddedResults.get(i);
                                if (editStart >= emb.startOffset && editEndOld <= emb.endOffset) {
                                    // We can incrementally parse this embedded sub-tree!
                                    int delta = editEndNew - editEndOld;

                                    HtmlSyntaxTree newHtmlTree = cached.htmlTree.cloneWithShift(editStart, delta);
                                    List<ParseResult.EmbeddedResult> newEmbeddedResults = new ArrayList<>(cached.embeddedResults.size());

                                    for (int j = 0; j < cached.embeddedResults.size(); j++) {
                                        ParseResult.EmbeddedResult oldEmb = cached.embeddedResults.get(j);
                                        if (j < i) {
                                            newEmbeddedResults.add(oldEmb);
                                        } else if (j == i) {
                                            int newEmbStart = oldEmb.startOffset;
                                            int newEmbEnd = oldEmb.endOffset + delta;
                                            String oldEmbSource = cached.source.substring(oldEmb.startOffset, oldEmb.endOffset);
                                            String newEmbSource = source.substring(newEmbStart, newEmbEnd);

                                            int embEditStart = editStart - oldEmb.startOffset;
                                            int embEditEndOld = editEndOld - oldEmb.startOffset;
                                            int embEditEndNew = editEndNew - oldEmb.startOffset;

                                            ParseResult newEmbParseResult;
                                            if (oldEmb.result.tree != null && oldEmb.result.tokens != null) {
                                                // JS
                                                TokenStream newTokens = com.cocode.vcode.ide.core.language.js.JsLexer.tokenize(newEmbSource, null);
                                                JsSyntaxTree newJsTree = JsParser.parseIncremental(newEmbSource, newTokens, oldEmbSource, oldEmb.result.tree, oldEmb.result.tokens, embEditStart, embEditEndOld, embEditEndNew);
                                                newEmbParseResult = new ParseResult(null, newEmbSource, newTokens, newJsTree);
                                            } else if (oldEmb.result.cssTree != null && oldEmb.result.cssTokens != null) {
                                                // CSS
                                                CssTokenStream newTokens = com.cocode.vcode.ide.core.language.css.CssLexer.tokenize(newEmbSource);
                                                CssSyntaxTree newCssTree = CssParser.parse(newTokens, newEmbSource);
                                                newEmbParseResult = new ParseResult(null, newEmbSource, newTokens, newCssTree);
                                            } else {
                                                newEmbParseResult = oldEmb.result;
                                            }

                                            newEmbeddedResults.add(new ParseResult.EmbeddedResult(newEmbStart, newEmbEnd, newEmbParseResult));
                                        } else {
                                            newEmbeddedResults.add(new ParseResult.EmbeddedResult(oldEmb.startOffset + delta, oldEmb.endOffset + delta, oldEmb.result));
                                        }
                                    }

                                    result = new ParseResult(file, source, cached.htmlTokens, newHtmlTree, newEmbeddedResults);
                                    break;
                                }
                            }
                        }
                    }

                    if (result == null) {
                        // Fallback: full parse
                        HtmlTokenStream tokens = HtmlLexer.tokenize(source);
                        result = HtmlParser.parse(source, tokens);
                        // result already contains the embeddedResults via HtmlParser.parse changes in H.5
                    }
                }

                ParseResult finalResult = result;

                if (file != null) {
                    ProjectIndex.getInstance().updateParseResult(file.getAbsolutePath(), finalResult);
                }

                debounceHandler.post(() -> {
                    if (myRequestId == requestId.get()) {
                        parseResultLiveData.setValue(finalResult);
                    }
                });
            });
        }, 250);
    }
}
