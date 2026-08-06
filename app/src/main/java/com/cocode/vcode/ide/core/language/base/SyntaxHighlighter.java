package com.cocode.vcode.ide.core.language.base;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import com.cocode.vcode.ide.core.editor.text.Content;
import com.cocode.vcode.ide.utils.ColorParser;
import com.cocode.vcode.ide.views.span.SyntaxHighlightSpan;

import java.util.ArrayList;
import java.util.List;

public class SyntaxHighlighter {

    protected final Context context;
    protected final int colorComment;
    protected final int colorString;
    protected final int colorKeyword;
    protected final int colorNumber;
    protected int lastLineState = 0;

    public SyntaxHighlighter(Context context) {
        this.context = context.getApplicationContext();
        colorComment = getColor(R.color.vcode_color_comment);
        colorString = getColor(R.color.vcode_color_js_string);
        colorKeyword = getColor(R.color.vcode_color_js_keyword);
        colorNumber = getColor(R.color.vcode_color_js_number);
    }

    public android.text.SpannableStringBuilder highlight(String code) {
        android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder(code);
        String[] lines = code.split("\n", -1);
        int state = 0;
        int offset = 0;
        for (String lineStr : lines) {
            List<HighlightToken> tokens = tokenizeLine(lineStr, 0, state);
            state = lastLineState;
            for (HighlightToken t : tokens) {
                ssb.setSpan(new SyntaxHighlightSpan(t.color, t.underline),
                        offset + t.startCol, offset + t.endCol, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            offset += lineStr.length() + 1;
        }
        return ssb;
    }

    public List<HighlightToken> highlightLines(Content content, int startLine, int endLine) {
        if (content == null) return new ArrayList<>();

        int lineCount = content.lineCount();
        int safeStart = Math.max(0, startLine);
        int safeEnd = Math.min(lineCount - 1, endLine);
        if (safeStart > safeEnd) return new ArrayList<>();

        List<HighlightToken> allTokens = new ArrayList<>();

        int state = 0;
        if (safeStart > 0) {
            state = content.getLine(safeStart - 1).getTokenizerEndState();
        }

        for (int i = safeStart; i <= safeEnd; i++) {
            List<HighlightToken> lineTokens = tokenizeLine(content.getLine(i).toLineString(), i, state);
            allTokens.addAll(lineTokens);
            content.getLine(i).setTokenizerEndState(lastLineState);
            state = lastLineState;
        }

        return allTokens;
    }

    public int getLastLineState() {
        return lastLineState;
    }

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
                    tokens.add(new HighlightToken(lineIndex, i, j, colorKeyword, false));
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
                i = j;
                continue;
            }

            i++;
        }

        lastLineState = state;
        return tokens;
    }

    public int computeEndState(com.cocode.vcode.ide.core.editor.text.ContentLine line, int startState) {
        int len = line.length();
        int state = startState;
        int i = 0;
        while (i < len) {
            char c = line.charAt(i);
            if (state == 1) {
                int commentEnd = -1;
                for (int k = i; k < len - 1; k++) {
                    if (line.charAt(k) == '*' && line.charAt(k + 1) == '/') {
                        commentEnd = k;
                        break;
                    }
                }
                if (commentEnd != -1) {
                    i = commentEnd + 2;
                    state = 0;
                } else {
                    i = len;
                }
                continue;
            }
            if (state == 2 || state == 3 || state == 4) {
                char quote = (state == 2) ? '"' : (state == 3) ? '\'' : '`';
                int j = i;
                while (j < len) {
                    if (line.charAt(j) == '\\') {
                        j += 2;
                        continue;
                    }
                    if (line.charAt(j) == quote) {
                        j++;
                        break;
                    }
                    j++;
                }
                if (j < len || (j == len && line.charAt(len - 1) == quote && (len < 2 || line.charAt(len - 2) != '\\'))) {
                    state = 0;
                }
                i = j;
                continue;
            }
            if (c == '/' && i + 1 < len) {
                if (line.charAt(i + 1) == '*') {
                    state = 1;
                    int commentEnd = -1;
                    for (int k = i + 2; k < len - 1; k++) {
                        if (line.charAt(k) == '*' && line.charAt(k + 1) == '/') {
                            commentEnd = k;
                            break;
                        }
                    }
                    if (commentEnd != -1) {
                        i = commentEnd + 2;
                        state = 0;
                    } else {
                        i = len;
                    }
                    continue;
                } else if (line.charAt(i + 1) == '/') {
                    i = len;
                    continue;
                }
            }
            if (c == '"' || c == '\'' || c == '`') {
                int j = i + 1;
                while (j < len) {
                    if (line.charAt(j) == '\\') {
                        j += 2;
                        continue;
                    }
                    if (line.charAt(j) == (int) c) {
                        j++;
                        break;
                    }
                    j++;
                }
                if (j < len || (j == len && line.charAt(len - 1) == (int) c && (len < 2 || line.charAt(len - 2) != '\\'))) {
                    state = 0;
                } else {
                    state = (c == '"') ? 2 : (c == '\'') ? 3 : 4;
                }
                i = j;
                continue;
            }
            i++;
        }
        return state;
    }

    protected boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    protected boolean isKeyword(String w) {
        switch (w) {
            case "var":
            case "let":
            case "const":
            case "function":
            case "return":
            case "if":
            case "else":
            case "for":
            case "while":
            case "do":
            case "switch":
            case "case":
            case "break":
            case "continue":
            case "new":
            case "delete":
            case "typeof":
            case "instanceof":
            case "in":
            case "of":
            case "class":
            case "extends":
            case "import":
            case "export":
            case "default":
            case "async":
            case "await":
            case "try":
            case "catch":
            case "finally":
            case "throw":
            case "void":
            case "yield":
            case "this":
            case "super":
            case "true":
            case "false":
            case "null":
            case "undefined":
                return true;
        }
        return false;
    }

    protected int getColor(int resId) {
        return ContextCompat.getColor(context, resId);
    }
}