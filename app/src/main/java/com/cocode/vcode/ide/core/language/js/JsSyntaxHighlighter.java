package com.cocode.vcode.ide.core.language.js;

import android.content.Context;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import com.cocode.vcode.ide.core.language.base.SyntaxHighlighter;
import com.cocode.vcode.ide.utils.ColorParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
}
