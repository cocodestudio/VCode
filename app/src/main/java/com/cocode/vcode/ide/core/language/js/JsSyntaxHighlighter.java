package com.cocode.vcode.ide.core.language.js;

import android.content.Context;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.diagnostic.util.ViewportHighlighter;
import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import com.cocode.vcode.ide.core.language.base.SyntaxHighlighter;
import com.cocode.vcode.ide.utils.ColorParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Syntax highlighter for JavaScript source code.
 */
public class JsSyntaxHighlighter extends SyntaxHighlighter {

    private static final Set<String> JS_KEYWORDS = new HashSet<>(Arrays.asList(
            "var", "let", "const", "function", "return",
            "if", "else", "for", "while", "do",
            "switch", "case", "break", "continue", "new",
            "delete", "typeof", "instanceof", "in", "of",
            "class", "extends", "import", "export", "default",
            "async", "await", "try", "catch", "finally",
            "throw", "void", "yield", "this", "super",
            // Built-in objects
            "console", "window", "document", "Math", "JSON", "Promise",
            "Object", "Array", "String", "Number", "Boolean", "RegExp",
            "Date", "Error", "Map", "Set", "Symbol", "globalThis"
    ));
    private static final Set<String> JS_BOOLEANS = new HashSet<>(Arrays.asList(
            "true", "false", "null", "undefined"
    ));
    protected final int colorFunction;
    protected final int colorBoolean;

    public JsSyntaxHighlighter(Context context) {
        super(context);
        colorFunction = getColor(R.color.vcode_color_js_function);
        colorBoolean = getColor(R.color.vcode_color_js_boolean);
    }

    /**
     * Test-only factory: returns a highlighter that skips colour
     * resolution so unit tests can construct one without a real
     * Android {@code Context}. Colours will be {@code 0}; callers
     * that need actual colours must use the resolver-supplied
     * {@code highlightViewport} overload.
     */
    public static JsSyntaxHighlighter forTest() {
        return new JsSyntaxHighlighter((Void) null);
    }

    /**
     * Test-only constructor: skips colour resolution so unit tests
     * can construct a highlighter without a real Android {@code
     * Context}. Colours will be {@code 0} and should be overridden
     * via the resolver-supplied {@code highlightViewport} overload.
     */
    JsSyntaxHighlighter(Void unusedForTest) {
        super((Void) null);
        this.colorFunction = 0;
        this.colorBoolean = 0;
    }

    @Override
    protected boolean isKeyword(String word) {
        return JS_KEYWORDS.contains(word) || JS_BOOLEANS.contains(word);
    }

    protected boolean isBoolean(String word) {
        return JS_BOOLEANS.contains(word);
    }

    @Override
    public List<HighlightToken> tokenizeLine(String lineStr, int lineIndex, int startState) {
        List<HighlightToken> tokens = new ArrayList<>();
        int len = lineStr.length();
        int state = startState;
        int i = 0;

        while (i < len) {
            char c = lineStr.charAt(i);

            if (state == 1) {
                int commentEnd = lineStr.indexOf("*/", i);
                if (commentEnd != -1) {
                    tokens.add(new HighlightToken(lineIndex, i, commentEnd + 2, colorComment, false));
                    i = commentEnd + 2;
                    state = 0;
                } else {
                    tokens.add(new HighlightToken(lineIndex, i, len, colorComment, false));
                    i = len;
                }
                continue;
            }

            if (state == 2 || state == 3 || state == 4) {
                char quote = (state == 2) ? '"' : (state == 3) ? '\'' : '`';
                int j = i;
                while (j < len) {
                    if (lineStr.charAt(j) == '\\') {
                        j += 2;
                        continue;
                    }
                    if (lineStr.charAt(j) == quote) {
                        j++;
                        break;
                    }
                    j++;
                }
                tokens.add(new HighlightToken(lineIndex, i, Math.min(j, len), colorString, false));
                if (j < len || (j == len && lineStr.charAt(len - 1) == quote && (len < 2 || lineStr.charAt(len - 2) != '\\'))) {
                    state = 0;
                }
                i = j;
                continue;
            }

            if (c == '/' && i + 1 < len) {
                if (lineStr.charAt(i + 1) == '*') {
                    state = 1;
                    int commentEnd = lineStr.indexOf("*/", i + 2);
                    if (commentEnd != -1) {
                        tokens.add(new HighlightToken(lineIndex, i, commentEnd + 2, colorComment, false));
                        i = commentEnd + 2;
                        state = 0;
                    } else {
                        tokens.add(new HighlightToken(lineIndex, i, len, colorComment, false));
                        i = len;
                    }
                    continue;
                } else if (lineStr.charAt(i + 1) == '/') {
                    tokens.add(new HighlightToken(lineIndex, i, len, colorComment, false));
                    i = len;
                    continue;
                }
            }

            if (c == '"' || c == '\'' || c == '`') {
                int j = i + 1;
                while (j < len) {
                    if (lineStr.charAt(j) == '\\') {
                        j += 2;
                        continue;
                    }
                    if (lineStr.charAt(j) == (int) c) {
                        j++;
                        break;
                    }
                    j++;
                }
                tokens.add(new HighlightToken(lineIndex, i, Math.min(j, len), colorString, false));
                if (j < len || (j == len && lineStr.charAt(len - 1) == (int) c && (len < 2 || lineStr.charAt(len - 2) != '\\'))) {
                    state = 0;
                } else {
                    state = (c == '"') ? 2 : (c == '\'') ? 3 : 4;
                }
                i = j;
                continue;
            }

            if (c == '#') {
                int j = i + 1;
                while (j < len && isHex(lineStr.charAt(j))) j++;
                if (j - i == 4 || j - i == 7 || j - i == 9) {
                    Integer colorVal = ColorParser.parse(lineStr.substring(i, j));
                    if (colorVal != null) {
                        tokens.add(new HighlightToken(lineIndex, i, j, colorNumber, false, true, colorVal));
                    } else {
                        tokens.add(new HighlightToken(lineIndex, i, j, colorNumber, false));
                    }
                    i = j;
                    continue;
                }
            }

            if (Character.isDigit(c)) {
                int j = i;
                while (j < len && (Character.isLetterOrDigit(lineStr.charAt(j)) || lineStr.charAt(j) == '.')) {
                    j++;
                }
                tokens.add(new HighlightToken(lineIndex, i, j, colorNumber, false));
                i = j;
                continue;
            }

            if (Character.isLetter(c) || c == '_' || c == '$') {
                int j = i;
                while (j < len && (Character.isLetterOrDigit(lineStr.charAt(j)) || lineStr.charAt(j) == '_' || lineStr.charAt(j) == '$')) {
                    j++;
                }
                String word = lineStr.substring(i, j);
                if (isKeyword(word)) {
                    if (isBoolean(word)) {
                        tokens.add(new HighlightToken(lineIndex, i, j, colorBoolean, false));
                    } else {
                        tokens.add(new HighlightToken(lineIndex, i, j, colorKeyword, false));
                    }
                } else {
                    int k = j;
                    while (k < len && Character.isWhitespace(lineStr.charAt(k))) {
                        k++;
                    }
                    if (k < len && lineStr.charAt(k) == '(') {
                        tokens.add(new HighlightToken(lineIndex, i, j, colorFunction, false));
                    } else {
                        Integer cssColor = ColorParser.parse(word);
                        if (cssColor != null) {
                            tokens.add(new HighlightToken(lineIndex, i, j, colorNumber, false, true, cssColor));
                        } else if ((word.equals("rgb") || word.equals("rgba") || word.equals("hsl") || word.equals("hsla")) && j < len && lineStr.charAt(j) == '(') {
                            int closeIdx = lineStr.indexOf(')', j);
                            if (closeIdx != -1) {
                                Integer fnColor = ColorParser.parse(lineStr.substring(i, closeIdx + 1));
                                if (fnColor != null) {
                                    tokens.add(new HighlightToken(lineIndex, i, closeIdx + 1, colorNumber, false, true, fnColor));
                                    j = closeIdx + 1;
                                }
                            }
                        }
                    }
                }
                i = j;
                continue;
            }

            i++;
        }

        lastLineState = state;
        return tokens;
    }

    // ---------------------------------------------------------------
    // Token-stream viewport highlighter
    //
    // Uses the pre-lexed TokenStream from JsLexer / JsParsePipeline
    // and the shared ViewportHighlighter utility to colour only the
    // visible byte range, without re-tokenising and without touching
    // the AST. Designed to be called once per scroll frame from the
    // editor's draw layer.
    // ---------------------------------------------------------------

    /**
     * Compute highlight tokens for the visible byte range
     * {@code [startOffset, endOffset)} in {@code source}, using the
     * pre-lexed {@link TokenStream}.
     *
     * <p>The returned tokens are in {@code (line, startCol, endCol)}
     * form, ready to feed the existing draw pipeline.
     * {@code firstVisibleLine} is the line index of the byte at
     * {@code startOffset}; it lets the conversion skip counting
     * newlines from the top of the file.
     *
     * <p>Bytes outside the visible range are never visited.
     */
    public List<HighlightToken> highlightViewport(
            String source, TokenStream stream,
            int startOffset, int endOffset, int firstVisibleLine) {
        ViewportHighlighter.ColorResolver resolver = new ViewportHighlighter.ColorResolver() {
            @Override
            public int colorFor(byte tokenType) {
                if (tokenType == TokenStream.TK_KEYWORD)  return colorKeyword;
                if (tokenType == TokenStream.TK_STRING)   return colorString;
                if (tokenType == TokenStream.TK_TEMPLATE) return colorString;
                if (tokenType == TokenStream.TK_REGEX)    return colorString;
                if (tokenType == TokenStream.TK_COMMENT)  return colorComment;
                if (tokenType == TokenStream.TK_NUMBER)   return colorNumber;
                return -1;
            }
        };
        return highlightViewport(source, stream, startOffset, endOffset, firstVisibleLine, resolver);
    }

    /**
     * Same as {@link #highlightViewport(String, TokenStream, int, int, int)}
     * but takes a caller-supplied {@link ViewportHighlighter.ColorResolver}.
     * This overload exists so J.1 wiring tests can run with a hand-rolled
     * resolver and avoid needing a real Android {@code Context} for
     * colour resolution.
     */
    public List<HighlightToken> highlightViewport(
            String source, TokenStream stream,
            int startOffset, int endOffset, int firstVisibleLine,
            ViewportHighlighter.ColorResolver resolver) {
        if (source == null || stream == null || resolver == null) return new ArrayList<>();
        int sourceLen = source.length();
        if (sourceLen == 0 || startOffset >= endOffset) return new ArrayList<>();
        if (startOffset < 0) startOffset = 0;
        if (endOffset > sourceLen) endOffset = sourceLen;

        List<ViewportHighlighter.ViewportSpan> spans = ViewportHighlighter.highlight(
                stream.types, stream.tokenStart, stream.length,
                startOffset, endOffset, resolver);

        // Convert byte offsets to (line, col). Walk the visible slice
        // once, tracking current line and column. O(visible length),
        // which is what the editor already spends drawing the slice.
        List<HighlightToken> out = new ArrayList<>(spans.size());
        int line = firstVisibleLine;
        int col  = 0;
        int cursor = startOffset;
        for (ViewportHighlighter.ViewportSpan s : spans) {
            while (cursor < s.startOffset) {
                if (source.charAt(cursor) == '\n') {
                    line++;
                    col = 0;
                } else {
                    col++;
                }
                cursor++;
            }
            int endCol = col;
            int scanEnd = Math.min(s.endOffset, endOffset);
            int scanCursor = cursor;
            while (scanCursor < scanEnd) {
                if (source.charAt(scanCursor) == '\n') {
                    // Clip spans that cross a line boundary to the
                    // first line. J.1 is byte-range based; multi-line
                    // highlight emission can be added later.
                    break;
                }
                endCol++;
                scanCursor++;
            }
            if (endCol > col) {
                out.add(new HighlightToken(line, col, endCol, s.color, false));
            }
            // Advance cursor to scanEnd so the next span starts on
            // the right line.
            while (cursor < scanEnd) {
                if (source.charAt(cursor) == '\n') {
                    line++;
                    col = 0;
                } else {
                    col++;
                }
                cursor++;
            }
        }
        return out;
    }
}
