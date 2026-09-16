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

/**
 * Base interface for language-specific syntax highlighters.
 */
public class SyntaxHighlighter {

    protected final Context context;
    protected final int colorComment;
    protected final int colorString;
    protected final int colorKeyword;
    protected final int colorNumber;
    protected final int colorOperator;
    protected int lastLineState = 0;
    protected Content content;

    public SyntaxHighlighter(Context context) {
        this.context = context.getApplicationContext();
        colorComment = getColor(R.color.vcode_color_comment);
        colorString = getColor(R.color.vcode_color_js_string);
        colorKeyword = getColor(R.color.vcode_color_js_keyword);
        colorNumber = getColor(R.color.vcode_color_js_number);
        colorOperator = getColor(R.color.vcode_color_js_operator);
    }

    /**
     * Test-only constructor: skips {@code getColor} resolution so
     * unit tests can construct a highlighter without a real Android
     * {@code Context}. The colour fields are left at {@code 0};
     * callers that need actual colours must use the resolver-supplied
     * overload of the method under test (e.g.
     * {@code JsSyntaxHighlighter.highlightViewport(..., resolver)}).
     */
    protected SyntaxHighlighter(Void unusedForTest) {
        this.context = null;
        this.colorComment = 0;
        this.colorString = 0;
        this.colorKeyword = 0;
        this.colorNumber = 0;
        this.colorOperator = 0;
    }

    protected SyntaxHighlighter(int comment, int string, int keyword, int number, int operator) {
        this.context = null;
        this.colorComment = comment;
        this.colorString = string;
        this.colorKeyword = keyword;
        this.colorNumber = number;
        this.colorOperator = operator;
    }

    public static int findMatchingParen(String s, int openParenIdx) {
        if (s == null) return -1;
        int len = s.length();
        int depth = 0;
        for (int k = openParenIdx; k < len; k++) {
            char c = s.charAt(k);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return k;
                }
            }
        }
        return -1;
    }

    public static boolean match(CharSequence cs, int start, int end, String target) {
        int len = end - start;
        if (len != target.length()) return false;
        for (int i = 0; i < len; i++) {
            if (cs.charAt(start + i) != target.charAt(i)) return false;
        }
        return true;
    }

    public void setContent(Content content) {
        this.content = content;
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
                    if (lineStr.charAt(j) == '.' && (j + 1 >= len || !Character.isDigit(lineStr.charAt(j + 1)))) {
                        break;
                    }
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
                if (isKeyword(lineStr, i, j)) {
                    tokens.add(new HighlightToken(lineIndex, i, j, colorKeyword, false));
                } else if (j < len && lineStr.charAt(j) == '(') {
                    int closeIdx = findMatchingParen(lineStr, j);
                    if (closeIdx != -1) {
                        String fnCall = lineStr.substring(i, closeIdx + 1);
                        Integer fnColor = ColorParser.parse(fnCall);
                        if (fnColor != null) {
                            tokens.add(new HighlightToken(lineIndex, i, j, colorNumber, false, true, fnColor));
                        } else {
                            tokens.add(new HighlightToken(lineIndex, i, j, colorKeyword, false));
                        }
                    } else {
                        tokens.add(new HighlightToken(lineIndex, i, j, colorKeyword, false));
                    }
                } else {
                    String word = lineStr.substring(i, j);
                    Integer cssColor = ColorParser.parse(word);
                    if (cssColor != null) {
                        tokens.add(new HighlightToken(lineIndex, i, j, colorNumber, false, true, cssColor));
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

    public boolean isKeyword(CharSequence cs, int start, int end) {
        int len = end - start;
        if (len < 2 || len > 10) return false;
        char c0 = cs.charAt(start);
        switch (len) {
            case 2:
                if (c0 == 'i') return match(cs, start, end, "if") || match(cs, start, end, "in");
                if (c0 == 'd') return match(cs, start, end, "do");
                if (c0 == 'o') return match(cs, start, end, "of");
                if (c0 == 'a') return match(cs, start, end, "as");
                return false;
            case 3:
                if (c0 == 'v') return match(cs, start, end, "var");
                if (c0 == 'l') return match(cs, start, end, "let");
                if (c0 == 'f') return match(cs, start, end, "for");
                if (c0 == 'n') return match(cs, start, end, "new");
                if (c0 == 't') return match(cs, start, end, "try");
                if (c0 == 's') return match(cs, start, end, "set");
                if (c0 == 'g') return match(cs, start, end, "get");
                return false;
            case 4:
                if (c0 == 'e') return match(cs, start, end, "else");
                if (c0 == 'c') return match(cs, start, end, "case");
                if (c0 == 'v') return match(cs, start, end, "void");
                if (c0 == 't')
                    return match(cs, start, end, "this") || match(cs, start, end, "true");
                if (c0 == 'n') return match(cs, start, end, "null");
                if (c0 == 'f') return match(cs, start, end, "from");
                return false;
            case 5:
                if (c0 == 'c')
                    return match(cs, start, end, "const") || match(cs, start, end, "class") || match(cs, start, end, "catch");
                if (c0 == 'w') return match(cs, start, end, "while");
                if (c0 == 'b') return match(cs, start, end, "break");
                if (c0 == 'a')
                    return match(cs, start, end, "async") || match(cs, start, end, "await");
                if (c0 == 't') return match(cs, start, end, "throw");
                if (c0 == 'y') return match(cs, start, end, "yield");
                if (c0 == 's') return match(cs, start, end, "super");
                if (c0 == 'f') return match(cs, start, end, "false");
                return false;
            case 6:
                if (c0 == 'r') return match(cs, start, end, "return");
                if (c0 == 's')
                    return match(cs, start, end, "switch") || match(cs, start, end, "static");
                if (c0 == 'd') return match(cs, start, end, "delete");
                if (c0 == 't') return match(cs, start, end, "typeof");
                if (c0 == 'e') return match(cs, start, end, "export");
                if (c0 == 'i') return match(cs, start, end, "import");
                return false;
            case 7:
                if (c0 == 'e') return match(cs, start, end, "extends");
                if (c0 == 'd') return match(cs, start, end, "default");
                if (c0 == 'f') return match(cs, start, end, "finally");
                return false;
            case 8:
                if (c0 == 'f') return match(cs, start, end, "function");
                if (c0 == 'c') return match(cs, start, end, "continue");
                if (c0 == 'd') return match(cs, start, end, "debugger");
                return false;
            case 9:
                return match(cs, start, end, "undefined");
            case 10:
                return match(cs, start, end, "instanceof");
            default:
                return false;
        }
    }

    protected boolean isKeyword(String w) {
        if (w == null) return false;
        return isKeyword(w, 0, w.length());
    }

    protected int getColor(int resId) {
        return ContextCompat.getColor(context, resId);
    }
}