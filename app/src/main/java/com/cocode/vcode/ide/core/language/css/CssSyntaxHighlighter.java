package com.cocode.vcode.ide.core.language.css;

import android.content.Context;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import com.cocode.vcode.ide.core.editor.text.ContentLine;
import com.cocode.vcode.ide.core.language.base.SyntaxHighlighter;
import com.cocode.vcode.ide.utils.ColorParser;

import java.util.ArrayList;
import java.util.List;

public class CssSyntaxHighlighter extends SyntaxHighlighter {

    protected final int colorSelector;
    protected final int colorProperty;
    protected final int colorValue;
    protected final int colorAtRule;
    protected final int colorBracket;

    public CssSyntaxHighlighter(Context context) {
        super(context);
        colorSelector = getColor(R.color.vcode_color_css_selector);
        colorProperty = getColor(R.color.vcode_color_css_property);
        colorValue = getColor(R.color.vcode_color_css_value);
        colorAtRule = getColor(R.color.vcode_color_css_at_rule);
        colorBracket = getColor(R.color.vcode_color_html_bracket);
    }

    protected boolean isWordStart(char c) {
        return Character.isLetter(c) || c == '-' || c == '_';
    }

    protected boolean isWordPart(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }

    @Override
    public List<HighlightToken> tokenizeLine(String lineStr, int lineIndex, int startState) {
        List<HighlightToken> tokens = new ArrayList<>();
        int len = lineStr.length();
        int i = 0;

        boolean inBracket = (startState & 1) != 0;
        boolean inComment = (startState & 2) != 0;

        while (i < len) {
            char quote = lineStr.charAt(i);

            if (inComment) {
                int commentEnd = lineStr.indexOf("*/", i);
                if (commentEnd != -1) {
                    tokens.add(new HighlightToken(lineIndex, i, commentEnd + 2, colorComment, false));
                    i = commentEnd + 2;
                    inComment = false;
                } else {
                    tokens.add(new HighlightToken(lineIndex, i, len, colorComment, false));
                    i = len;
                }
                continue;
            }

            if (quote == '/' && i + 1 < len && lineStr.charAt(i + 1) == '*') {
                inComment = true;
                int commentEnd = lineStr.indexOf("*/", i + 2);
                if (commentEnd != -1) {
                    tokens.add(new HighlightToken(lineIndex, i, commentEnd + 2, colorComment, false));
                    i = commentEnd + 2;
                    inComment = false;
                } else {
                    tokens.add(new HighlightToken(lineIndex, i, len, colorComment, false));
                    i = len;
                }
                continue;
            }

            if (quote == '{') {
                inBracket = true;
                tokens.add(new HighlightToken(lineIndex, i, i + 1, colorBracket, false));
                i++;
                continue;
            }

            if (quote == '}') {
                inBracket = false;
                tokens.add(new HighlightToken(lineIndex, i, i + 1, colorBracket, false));
                i++;
                continue;
            }

            if (quote == '"' || quote == '\'') {
                int j = i + 1;
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
                tokens.add(new HighlightToken(lineIndex, i, Math.min(j, len), colorValue, false));
                i = j;
                continue;
            }

            if (quote == '@') {
                int j = i + 1;
                while (j < len && (Character.isLetterOrDigit(lineStr.charAt(j)) || lineStr.charAt(j) == '-')) {
                    j++;
                }
                tokens.add(new HighlightToken(lineIndex, i, j, colorAtRule, false));
                i = j;
                continue;
            }

            if (quote == '#') {
                int j = i + 1;
                while (j < len && (Character.isLetterOrDigit(lineStr.charAt(j)) || lineStr.charAt(j) == '-')) {
                    j++;
                }
                if (inBracket) {
                    Integer colorVal = ColorParser.parse(lineStr.substring(i, j));
                    if (colorVal != null) {
                        tokens.add(new HighlightToken(lineIndex, i, j, colorValue, false, true, colorVal));
                    } else {
                        tokens.add(new HighlightToken(lineIndex, i, j, colorValue, false));
                    }
                } else {
                    tokens.add(new HighlightToken(lineIndex, i, j, colorSelector, false));
                }
                i = j;
                continue;
            }

            if (quote == '.') {
                if (i + 1 < len && Character.isDigit(lineStr.charAt(i + 1))) {
                    int j = i + 1;
                    while (j < len && Character.isDigit(lineStr.charAt(j))) j++;
                    tokens.add(new HighlightToken(lineIndex, i, j, inBracket ? colorValue : colorSelector, false));
                    i = j;
                    continue;
                } else {
                    int j = i + 1;
                    while (j < len && isWordPart(lineStr.charAt(j))) {
                        j++;
                    }
                    tokens.add(new HighlightToken(lineIndex, i, j, inBracket ? colorValue : colorSelector, false));
                    i = j;
                    continue;
                }
            }

            if (Character.isDigit(quote)) {
                int j = i;
                while (j < len && (Character.isLetterOrDigit(lineStr.charAt(j)) || lineStr.charAt(j) == '.' || lineStr.charAt(j) == '%')) {
                    j++;
                }
                tokens.add(new HighlightToken(lineIndex, i, j, inBracket ? colorValue : colorSelector, false));
                i = j;
                continue;
            }

            if (isWordStart(quote)) {
                int j = i;
                while (j < len && isWordPart(lineStr.charAt(j))) {
                    j++;
                }

                if (inBracket) {
                    int k = j;
                    while (k < len && Character.isWhitespace(lineStr.charAt(k))) k++;
                    if (k < len && lineStr.charAt(k) == ':') {
                        tokens.add(new HighlightToken(lineIndex, i, j, colorProperty, false));
                    } else {
                        String word = lineStr.substring(i, j);
                        Integer colorVal = ColorParser.parse(word);
                        if (colorVal != null) {
                            tokens.add(new HighlightToken(lineIndex, i, j, colorValue, false, true, colorVal));
                        } else {
                            tokens.add(new HighlightToken(lineIndex, i, j, colorValue, false));
                        }
                    }
                } else {
                    tokens.add(new HighlightToken(lineIndex, i, j, colorSelector, false));
                }

                i = j;
                continue;
            }

            i++;
        }

        int endState = 0;
        if (inBracket) endState |= 1;
        if (inComment) endState |= 2;
        lastLineState = endState;
        return tokens;
    }

    @Override
    public int computeEndState(ContentLine line, int startState) {
        int len = line.length();
        int i = 0;

        boolean inBracket = (startState & 1) != 0;
        boolean inComment = (startState & 2) != 0;

        while (i < len) {
            char quote = line.charAt(i);

            if (inComment) {
                int commentEnd = -1;
                for (int k = i; k < len - 1; k++) {
                    if (line.charAt(k) == '*' && line.charAt(k + 1) == '/') {
                        commentEnd = k;
                        break;
                    }
                }
                if (commentEnd != -1) {
                    i = commentEnd + 2;
                    inComment = false;
                } else {
                    i = len;
                }
                continue;
            }

            if (quote == '/' && i + 1 < len && line.charAt(i + 1) == '*') {
                inComment = true;
                int commentEnd = -1;
                for (int k = i + 2; k < len - 1; k++) {
                    if (line.charAt(k) == '*' && line.charAt(k + 1) == '/') {
                        commentEnd = k;
                        break;
                    }
                }
                if (commentEnd != -1) {
                    i = commentEnd + 2;
                    inComment = false;
                } else {
                    i = len;
                }
                continue;
            }

            if (quote == '{') {
                inBracket = true;
                i++;
                continue;
            }

            if (quote == '}') {
                inBracket = false;
                i++;
                continue;
            }

            if (quote == '"' || quote == '\'') {
                int j = i + 1;
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
                i = j;
                continue;
            }

            i++;
        }

        int endState = 0;
        if (inBracket) endState |= 1;
        if (inComment) endState |= 2;
        return endState;
    }
}
