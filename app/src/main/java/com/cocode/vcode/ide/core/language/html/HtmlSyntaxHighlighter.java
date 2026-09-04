package com.cocode.vcode.ide.core.language.html;

import android.content.Context;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import com.cocode.vcode.ide.core.editor.text.ContentLine;
import com.cocode.vcode.ide.core.language.base.SyntaxHighlighter;
import com.cocode.vcode.ide.core.language.css.CssSyntaxHighlighter;
import com.cocode.vcode.ide.core.language.js.JsSyntaxHighlighter;

import java.util.ArrayList;
import java.util.List;

/**
 * Syntax highlighter for HTML documents, including embedded CSS and JavaScript.
 */
public class HtmlSyntaxHighlighter extends SyntaxHighlighter {

    private static final int STATE_NORMAL = 0;
    private static final int STATE_COMMENT = 1;
    private static final int STATE_TAG = 2;
    private static final int STATE_STRING_DOUBLE = 3;
    private static final int STATE_STRING_SINGLE = 4;
    private static final int STATE_STYLE_CONTENT = 5;
    private static final int STATE_SCRIPT_CONTENT = 6;
    private static final int STATE_TAG_PENDING_STYLE = 7;
    private static final int STATE_TAG_PENDING_SCRIPT = 8;
    private static final int STATE_STRING_DOUBLE_PENDING_STYLE = 9;
    private static final int STATE_STRING_SINGLE_PENDING_STYLE = 10;
    private static final int STATE_STRING_DOUBLE_PENDING_SCRIPT = 11;
    private static final int STATE_STRING_SINGLE_PENDING_SCRIPT = 12;
    private final int colorTag;
    private final int colorAttribute;
    private final int colorValue;
    private final int colorBracket;
    private final int colorHtmlComment;
    private final CssSyntaxHighlighter cssHighlighter;
    private final JsSyntaxHighlighter jsHighlighter;

    public HtmlSyntaxHighlighter(Context context) {
        super(context);
        colorTag = getColor(R.color.vcode_color_html_tag);
        colorAttribute = getColor(R.color.vcode_color_html_attribute);
        colorValue = getColor(R.color.vcode_color_html_value);
        colorBracket = getColor(R.color.vcode_color_html_bracket);
        colorHtmlComment = getColor(R.color.vcode_color_comment);
        cssHighlighter = new CssSyntaxHighlighter(context);
        jsHighlighter = new JsSyntaxHighlighter(context);
    }

    public static HtmlSyntaxHighlighter forTest() {
        return new HtmlSyntaxHighlighter((Void) null);
    }

    public static HtmlSyntaxHighlighter forTestWithColors(int tag, int attribute, int value, int bracket, int comment) {
        return new HtmlSyntaxHighlighter(tag, attribute, value, bracket, comment);
    }

    protected HtmlSyntaxHighlighter(Void unusedForTest) {
        super((Void) null);
        this.colorTag = 0;
        this.colorAttribute = 0;
        this.colorValue = 0;
        this.colorBracket = 0;
        this.colorHtmlComment = 0;
        this.cssHighlighter = CssSyntaxHighlighter.forTest();
        this.jsHighlighter = JsSyntaxHighlighter.forTest();
    }

    protected HtmlSyntaxHighlighter(int tag, int attribute, int value, int bracket, int comment) {
        super(comment, value, 0, value, 0);
        this.colorTag = tag;
        this.colorAttribute = attribute;
        this.colorValue = value;
        this.colorBracket = bracket;
        this.colorHtmlComment = comment;
        this.cssHighlighter = CssSyntaxHighlighter.forTestWithColors(comment, tag, attribute, value, tag, bracket);
        this.jsHighlighter = JsSyntaxHighlighter.forTestWithColors(comment, value, tag, value, attribute, tag, bracket);
    }

    private static int outerState(int combined) {
        return combined & 0xF;
    }

    private static int innerState(int combined) {
        return combined >>> 4;
    }

    private static int pack(int outer, int inner) {
        return (inner << 4) | outer;
    }

    private static int stringState(int tagOuter, boolean isDouble) {
        if (tagOuter == STATE_TAG_PENDING_STYLE)
            return isDouble ? STATE_STRING_DOUBLE_PENDING_STYLE : STATE_STRING_SINGLE_PENDING_STYLE;
        if (tagOuter == STATE_TAG_PENDING_SCRIPT)
            return isDouble ? STATE_STRING_DOUBLE_PENDING_SCRIPT : STATE_STRING_SINGLE_PENDING_SCRIPT;
        return isDouble ? STATE_STRING_DOUBLE : STATE_STRING_SINGLE;
    }

    private static int tagReturnState(int stringOuter) {
        switch (stringOuter) {
            case STATE_STRING_DOUBLE_PENDING_STYLE:
            case STATE_STRING_SINGLE_PENDING_STYLE:
                return STATE_TAG_PENDING_STYLE;
            case STATE_STRING_DOUBLE_PENDING_SCRIPT:
            case STATE_STRING_SINGLE_PENDING_SCRIPT:
                return STATE_TAG_PENDING_SCRIPT;
            default:
                return STATE_TAG;
        }
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        int hLen = haystack.length();
        int nLen = needle.length();
        for (int i = Math.max(0, from); i <= hLen - nLen; i++) {
            boolean match = true;
            for (int j = 0; j < nLen; j++) {
                if (Character.toLowerCase(haystack.charAt(i + j)) != needle.charAt(j)) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    private static int indexOfIgnoreCase(ContentLine line, String needle, int from, int len) {
        int nLen = needle.length();
        for (int i = Math.max(0, from); i <= len - nLen; i++) {
            boolean match = true;
            for (int k = 0; k < nLen; k++) {
                if (Character.toLowerCase(line.charAt(i + k)) != needle.charAt(k)) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    private static boolean equalsIgnoreCaseAt(ContentLine line, int start, String word) {
        for (int k = 0; k < word.length(); k++) {
            if (Character.toLowerCase(line.charAt(start + k)) != word.charAt(k)) return false;
        }
        return true;
    }

    @Override
    public List<HighlightToken> tokenizeLine(String lineStr, int lineIndex, int startState) {
        List<HighlightToken> tokens = new ArrayList<>();
        int len = lineStr.length();
        int outer = outerState(startState);
        int inner = innerState(startState);
        int i = 0;
        boolean isFirstWord = false;
        boolean isClosingTag = false;

        while (i < len) {

            if (outer == STATE_STYLE_CONTENT || outer == STATE_SCRIPT_CONTENT) {
                String needle = (outer == STATE_STYLE_CONTENT) ? "</style" : "</script";
                int closeIdx = indexOfIgnoreCase(lineStr, needle, i);
                int contentEnd = (closeIdx == -1) ? len : closeIdx;
                if (contentEnd > i) {
                    SyntaxHighlighter embedded = (outer == STATE_STYLE_CONTENT) ? cssHighlighter : jsHighlighter;
                    List<HighlightToken> embeddedTokens = embedded.tokenizeLine(lineStr.substring(i, contentEnd), lineIndex, inner);
                    for (HighlightToken t : embeddedTokens) {
                        t.startCol += i;
                        t.endCol += i;
                        tokens.add(t);
                    }
                    inner = embedded.getLastLineState();
                }
                if (closeIdx == -1) {
                    i = len;
                } else {
                    i = closeIdx;
                    outer = STATE_NORMAL;
                    inner = 0;
                }
                continue;
            }

            char c = lineStr.charAt(i);

            if (outer == STATE_COMMENT) {
                int commentEnd = lineStr.indexOf("-->", i);
                if (commentEnd != -1) {
                    tokens.add(new HighlightToken(lineIndex, i, commentEnd + 3, colorHtmlComment, false));
                    i = commentEnd + 3;
                    outer = STATE_NORMAL;
                } else {
                    tokens.add(new HighlightToken(lineIndex, i, len, colorHtmlComment, false));
                    i = len;
                }
                continue;
            }

            if (outer == STATE_STRING_DOUBLE || outer == STATE_STRING_SINGLE
                    || outer == STATE_STRING_DOUBLE_PENDING_STYLE || outer == STATE_STRING_SINGLE_PENDING_STYLE
                    || outer == STATE_STRING_DOUBLE_PENDING_SCRIPT || outer == STATE_STRING_SINGLE_PENDING_SCRIPT) {
                boolean isDouble = (outer == STATE_STRING_DOUBLE || outer == STATE_STRING_DOUBLE_PENDING_STYLE || outer == STATE_STRING_DOUBLE_PENDING_SCRIPT);
                char quote = isDouble ? '"' : '\'';
                int j = i;
                while (j < len) {
                    if (lineStr.charAt(j) == quote) {
                        j++;
                        break;
                    }
                    j++;
                }
                tokens.add(new HighlightToken(lineIndex, i, j, colorValue, false));
                if (j > i && lineStr.charAt(j - 1) == quote) {
                    outer = tagReturnState(outer);
                }
                i = j;
                continue;
            }

            if (outer == STATE_NORMAL) {
                if (c == '<') {
                    if (i + 3 < len && lineStr.charAt(i + 1) == '!' && lineStr.charAt(i + 2) == '-' && lineStr.charAt(i + 3) == '-') {
                        outer = STATE_COMMENT;
                        int commentEnd = lineStr.indexOf("-->", i + 4);
                        if (commentEnd != -1) {
                            tokens.add(new HighlightToken(lineIndex, i, commentEnd + 3, colorHtmlComment, false));
                            i = commentEnd + 3;
                            outer = STATE_NORMAL;
                        } else {
                            tokens.add(new HighlightToken(lineIndex, i, len, colorHtmlComment, false));
                            i = len;
                        }
                    } else if (i + 1 < len && lineStr.charAt(i + 1) == '?') {
                        outer = STATE_TAG;
                        isFirstWord = true;
                        isClosingTag = false;
                        tokens.add(new HighlightToken(lineIndex, i, i + 2, colorBracket, false));
                        i += 2;
                    } else {
                        outer = STATE_TAG;
                        isFirstWord = true;
                        if (i + 1 < len && lineStr.charAt(i + 1) == '/') {
                            isClosingTag = true;
                            tokens.add(new HighlightToken(lineIndex, i, i + 2, colorBracket, false));
                            i += 2;
                        } else {
                            isClosingTag = false;
                            tokens.add(new HighlightToken(lineIndex, i, i + 1, colorBracket, false));
                            i++;
                        }
                    }
                } else if (c == '&') {
                    int semi = lineStr.indexOf(';', i + 1);
                    if (semi != -1 && semi - i <= 10 && semi - i >= 2) {
                        boolean validEntity = true;
                        for (int k = i + 1; k < semi; k++) {
                            char ec = lineStr.charAt(k);
                            if (!Character.isLetterOrDigit(ec) && ec != '#') {
                                validEntity = false;
                                break;
                            }
                        }
                        if (validEntity) {
                            tokens.add(new HighlightToken(lineIndex, i, semi + 1, colorValue, false));
                            i = semi + 1;
                            continue;
                        }
                    }
                    i++;
                } else {
                    i++;
                }
                continue;
            }

            boolean inTagBody = (outer == STATE_TAG || outer == STATE_TAG_PENDING_STYLE || outer == STATE_TAG_PENDING_SCRIPT);
            if (inTagBody) {
                if (c == '>') {
                    tokens.add(new HighlightToken(lineIndex, i, i + 1, colorBracket, false));
                    if (isClosingTag) {
                        outer = STATE_NORMAL;
                    } else if (outer == STATE_TAG_PENDING_STYLE) {
                        outer = STATE_STYLE_CONTENT;
                        inner = 0;
                    } else if (outer == STATE_TAG_PENDING_SCRIPT) {
                        outer = STATE_SCRIPT_CONTENT;
                        inner = 0;
                    } else {
                        outer = STATE_NORMAL;
                    }
                    i++;
                    continue;
                }
                if (c == '?' && i + 1 < len && lineStr.charAt(i + 1) == '>') {
                    tokens.add(new HighlightToken(lineIndex, i, i + 2, colorBracket, false));
                    outer = STATE_NORMAL;
                    i += 2;
                    continue;
                }
                if (c == '/' && i + 1 < len && lineStr.charAt(i + 1) == '>') {
                    tokens.add(new HighlightToken(lineIndex, i, i + 2, colorBracket, false));
                    outer = STATE_NORMAL;
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    int j = i + 1;
                    while (j < len && lineStr.charAt(j) != '"') j++;
                    boolean closed = (j < len && lineStr.charAt(j) == '"');
                    if (closed) j++;
                    tokens.add(new HighlightToken(lineIndex, i, j, colorValue, false));
                    if (!closed) outer = stringState(outer, true);
                    i = j;
                    continue;
                }
                if (c == '\'') {
                    int j = i + 1;
                    while (j < len && lineStr.charAt(j) != '\'') j++;
                    boolean closed = (j < len && lineStr.charAt(j) == '\'');
                    if (closed) j++;
                    tokens.add(new HighlightToken(lineIndex, i, j, colorValue, false));
                    if (!closed) outer = stringState(outer, false);
                    i = j;
                    continue;
                }
                if (c == '=') {
                    tokens.add(new HighlightToken(lineIndex, i, i + 1, colorBracket, false));
                    i++;
                    // Check for unquoted attribute value
                    int nextNonWhite = i;
                    while (nextNonWhite < len && Character.isWhitespace(lineStr.charAt(nextNonWhite))) nextNonWhite++;
                    if (nextNonWhite < len) {
                        char nc = lineStr.charAt(nextNonWhite);
                        if (nc != '"' && nc != '\'' && nc != '>' && nc != '/') {
                            int j = nextNonWhite;
                            while (j < len && !Character.isWhitespace(lineStr.charAt(j)) && lineStr.charAt(j) != '>' && lineStr.charAt(j) != '/') {
                                j++;
                            }
                            tokens.add(new HighlightToken(lineIndex, nextNonWhite, j, colorValue, false));
                            i = j;
                            continue;
                        }
                    }
                    continue;
                }
                if (Character.isLetter(c) || c == '!' || c == '-' || c == '_' || c == ':' || Character.isDigit(c)) {
                    int j = i;
                    while (j < len) {
                        char ch = lineStr.charAt(j);
                        if (Character.isLetter(ch) || Character.isDigit(ch) || ch == '-' || ch == '_' || ch == ':' || ch == '!') {
                            j++;
                        } else {
                            break;
                        }
                    }
                    if (isFirstWord) {
                        tokens.add(new HighlightToken(lineIndex, i, j, colorTag, false));
                        isFirstWord = false;
                        if (!isClosingTag) {
                            String word = lineStr.substring(i, j);
                            if (word.equalsIgnoreCase("style")) outer = STATE_TAG_PENDING_STYLE;
                            else if (word.equalsIgnoreCase("script"))
                                outer = STATE_TAG_PENDING_SCRIPT;
                        }
                    } else {
                        tokens.add(new HighlightToken(lineIndex, i, j, colorAttribute, false));
                    }
                    i = j;
                    continue;
                }

                i++;
            }
        }
        lastLineState = pack(outer, inner);
        return tokens;
    }

    @Override
    public int computeEndState(ContentLine line, int startState) {
        int len = line.length();
        int outer = outerState(startState);
        int inner = innerState(startState);
        int i = 0;
        boolean isFirstWord = false;
        boolean isClosingTag = false;

        while (i < len) {

            if (outer == STATE_STYLE_CONTENT || outer == STATE_SCRIPT_CONTENT) {
                String needle = (outer == STATE_STYLE_CONTENT) ? "</style" : "</script";
                int closeIdx = indexOfIgnoreCase(line, needle, i, len);
                int contentEnd = (closeIdx == -1) ? len : closeIdx;
                if (contentEnd > i) {
                    SyntaxHighlighter embedded = (outer == STATE_STYLE_CONTENT) ? cssHighlighter : jsHighlighter;
                    int count = contentEnd - i;
                    char[] buf = new char[count];
                    line.getChars(i, contentEnd, buf, 0);
                    ContentLine sub = new ContentLine(new String(buf));
                    inner = embedded.computeEndState(sub, inner);
                }
                if (closeIdx == -1) {
                    i = len;
                } else {
                    i = closeIdx;
                    outer = STATE_NORMAL;
                    inner = 0;
                }
                continue;
            }

            char c = line.charAt(i);

            if (outer == STATE_COMMENT) {
                int commentEnd = -1;
                for (int k = i; k <= len - 3; k++) {
                    if (line.charAt(k) == '-' && line.charAt(k + 1) == '-' && line.charAt(k + 2) == '>') {
                        commentEnd = k;
                        break;
                    }
                }
                if (commentEnd != -1) {
                    i = commentEnd + 3;
                    outer = STATE_NORMAL;
                } else {
                    i = len;
                }
                continue;
            }

            if (outer == STATE_STRING_DOUBLE || outer == STATE_STRING_SINGLE
                    || outer == STATE_STRING_DOUBLE_PENDING_STYLE || outer == STATE_STRING_SINGLE_PENDING_STYLE
                    || outer == STATE_STRING_DOUBLE_PENDING_SCRIPT || outer == STATE_STRING_SINGLE_PENDING_SCRIPT) {
                boolean isDouble = (outer == STATE_STRING_DOUBLE || outer == STATE_STRING_DOUBLE_PENDING_STYLE || outer == STATE_STRING_DOUBLE_PENDING_SCRIPT);
                char quote = isDouble ? '"' : '\'';
                int j = i;
                while (j < len) {
                    if (line.charAt(j) == quote) {
                        j++;
                        break;
                    }
                    j++;
                }
                if (j > i && line.charAt(j - 1) == quote) {
                    outer = tagReturnState(outer);
                }
                i = j;
                continue;
            }

            if (outer == STATE_NORMAL) {
                if (c == '<') {
                    if (i + 3 < len && line.charAt(i + 1) == '!' && line.charAt(i + 2) == '-' && line.charAt(i + 3) == '-') {
                        outer = STATE_COMMENT;
                        int commentEnd = -1;
                        for (int k = i + 4; k <= len - 3; k++) {
                            if (line.charAt(k) == '-' && line.charAt(k + 1) == '-' && line.charAt(k + 2) == '>') {
                                commentEnd = k;
                                break;
                            }
                        }
                        if (commentEnd != -1) {
                            i = commentEnd + 3;
                            outer = STATE_NORMAL;
                        } else {
                            i = len;
                        }
                        continue;
                    } else if (i + 1 < len && line.charAt(i + 1) == '?') {
                        outer = STATE_TAG;
                        isFirstWord = true;
                        isClosingTag = false;
                        i += 2;
                        continue;
                    } else {
                        outer = STATE_TAG;
                        isFirstWord = true;
                        if (i + 1 < len && line.charAt(i + 1) == '/') {
                            isClosingTag = true;
                            i += 2;
                        } else {
                            isClosingTag = false;
                            i++;
                        }
                        continue;
                    }
                } else {
                    i++;
                    continue;
                }
            }

            boolean inTagBody = (outer == STATE_TAG || outer == STATE_TAG_PENDING_STYLE || outer == STATE_TAG_PENDING_SCRIPT);
            if (inTagBody) {
                if (c == '>') {
                    if (isClosingTag) {
                        outer = STATE_NORMAL;
                    } else if (outer == STATE_TAG_PENDING_STYLE) {
                        outer = STATE_STYLE_CONTENT;
                        inner = 0;
                    } else if (outer == STATE_TAG_PENDING_SCRIPT) {
                        outer = STATE_SCRIPT_CONTENT;
                        inner = 0;
                    } else {
                        outer = STATE_NORMAL;
                    }
                    i++;
                    continue;
                }
                if (c == '?' && i + 1 < len && line.charAt(i + 1) == '>') {
                    outer = STATE_NORMAL;
                    i += 2;
                    continue;
                }
                if (c == '/' && i + 1 < len && line.charAt(i + 1) == '>') {
                    outer = STATE_NORMAL;
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    int j = i + 1;
                    while (j < len && line.charAt(j) != '"') j++;
                    boolean closed = (j < len && line.charAt(j) == '"');
                    if (closed) j++;
                    if (!closed) outer = stringState(outer, true);
                    i = j;
                    continue;
                }
                if (c == '\'') {
                    int j = i + 1;
                    while (j < len && line.charAt(j) != '\'') j++;
                    boolean closed = (j < len && line.charAt(j) == '\'');
                    if (closed) j++;
                    if (!closed) outer = stringState(outer, false);
                    i = j;
                    continue;
                }
                if (Character.isLetter(c) || c == '!' || c == '-' || c == '_' || c == ':' || Character.isDigit(c)) {
                    int j = i;
                    while (j < len) {
                        char ch = line.charAt(j);
                        if (Character.isLetter(ch) || Character.isDigit(ch) || ch == '-' || ch == '_' || ch == ':' || ch == '!') {
                            j++;
                        } else {
                            break;
                        }
                    }
                    if (isFirstWord) {
                        isFirstWord = false;
                        int wordLen = j - i;
                        if (!isClosingTag) {
                            if (wordLen == 5 && equalsIgnoreCaseAt(line, i, "style"))
                                outer = STATE_TAG_PENDING_STYLE;
                            else if (wordLen == 6 && equalsIgnoreCaseAt(line, i, "script"))
                                outer = STATE_TAG_PENDING_SCRIPT;
                        }
                    }
                    i = j;
                    continue;
                }

                i++;
            }
        }
        return pack(outer, inner);
    }

}
