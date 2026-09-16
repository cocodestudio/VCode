package com.cocode.vcode.ide.core.language.css;

import android.content.Context;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.editor.highlight.GradientPreview;
import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import com.cocode.vcode.ide.core.editor.text.ContentLine;
import com.cocode.vcode.ide.core.language.base.SyntaxHighlighter;
import com.cocode.vcode.ide.utils.ColorParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Syntax highlighter for CSS and SCSS stylesheets.
 */
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

    CssSyntaxHighlighter(Void unusedForTest) {
        super((Void) null);
        this.colorSelector = 0;
        this.colorProperty = 0;
        this.colorValue = 0;
        this.colorAtRule = 0;
        this.colorBracket = 0;
    }

    CssSyntaxHighlighter(int comment, int selector, int property, int value, int atRule, int bracket) {
        super(comment, value, 0, value, 0);
        this.colorSelector = selector;
        this.colorProperty = property;
        this.colorValue = value;
        this.colorAtRule = atRule;
        this.colorBracket = bracket;
    }

    public static CssSyntaxHighlighter forTest() {
        return new CssSyntaxHighlighter((Void) null);
    }

    public static CssSyntaxHighlighter forTestWithColors(int comment, int selector, int property, int value, int atRule, int bracket) {
        return new CssSyntaxHighlighter(comment, selector, property, value, atRule, bracket);
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

        boolean inComment = (startState & 2) != 0;
        boolean inValue = (startState & 4) != 0;
        int bracketDepth = (startState >>> 5) & 0xFF;
        if (bracketDepth == 0 && (startState & 1) != 0) {
            bracketDepth = 1;
        }
        boolean inStringDouble = (startState & (1 << 13)) != 0;
        boolean inStringSingle = (startState & (1 << 14)) != 0;

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

            if (inStringDouble || inStringSingle) {
                char q = inStringDouble ? '"' : '\'';
                int j = i;
                while (j < len) {
                    if (lineStr.charAt(j) == '\\') {
                        j += 2;
                        continue;
                    }
                    if (lineStr.charAt(j) == q) {
                        j++;
                        break;
                    }
                    j++;
                }
                tokens.add(new HighlightToken(lineIndex, i, Math.min(j, len), colorValue, false));
                if (j < len || (j == len && lineStr.charAt(len - 1) == q && (len < 2 || lineStr.charAt(len - 2) != '\\'))) {
                    inStringDouble = false;
                    inStringSingle = false;
                }
                i = j;
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
                bracketDepth++;
                inValue = false;
                tokens.add(new HighlightToken(lineIndex, i, i + 1, colorBracket, false));
                i++;
                continue;
            }

            if (quote == '}') {
                bracketDepth = Math.max(0, bracketDepth - 1);
                inValue = false;
                tokens.add(new HighlightToken(lineIndex, i, i + 1, colorBracket, false));
                i++;
                continue;
            }

            if (quote == ';') {
                inValue = false;
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
                if (j >= len && (len < 1 || lineStr.charAt(len - 1) != quote || (len >= 2 && lineStr.charAt(len - 2) == '\\'))) {
                    if (quote == '"') inStringDouble = true;
                    else inStringSingle = true;
                }
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
                if (inValue) {
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
                if (inValue) {
                    int j = i + 1;
                    while (j < len && Character.isDigit(lineStr.charAt(j))) j++;
                    tokens.add(new HighlightToken(lineIndex, i, j, colorValue, false));
                    i = j;
                    continue;
                } else {
                    int j = i + 1;
                    while (j < len && isWordPart(lineStr.charAt(j))) j++;
                    tokens.add(new HighlightToken(lineIndex, i, j, colorSelector, false));
                    i = j;
                    continue;
                }
            }

            if (Character.isDigit(quote)) {
                int j = i;
                while (j < len && (Character.isLetterOrDigit(lineStr.charAt(j)) || lineStr.charAt(j) == '.' || lineStr.charAt(j) == '%')) {
                    j++;
                }
                tokens.add(new HighlightToken(lineIndex, i, j, inValue ? colorValue : colorSelector, false));
                i = j;
                continue;
            }

            if (isWordStart(quote)) {
                int j = i;
                while (j < len && isWordPart(lineStr.charAt(j))) {
                    j++;
                }

                if (inValue) {
                    String word = lineStr.substring(i, j);
                    if (j < len && lineStr.charAt(j) == '(') {
                        int closeIdx = findMatchingParen(lineStr, j);
                        String fullExpr = null;
                        if (closeIdx != -1) {
                            fullExpr = lineStr.substring(i, closeIdx + 1);
                        } else if (content != null) {
                            fullExpr = extractMultiLineExpression(lineStr, lineIndex, i, j);
                        }

                        if (fullExpr != null) {
                            if (CssGradientParser.isGradientFunction(word)) {
                                GradientPreview grad = CssGradientParser.parse(fullExpr);
                                if (grad != null) {
                                    tokens.add(new HighlightToken(lineIndex, i, j, colorValue, false, true, grad.colors[0], grad));
                                    i = j;
                                    continue;
                                }
                            } else if (CssGradientParser.isColorFunction(word)) {
                                Integer fnColor = ColorParser.parse(fullExpr);
                                if (fnColor != null) {
                                    tokens.add(new HighlightToken(lineIndex, i, j, colorValue, false, true, fnColor));
                                    i = j;
                                    continue;
                                }
                            }
                        }
                        tokens.add(new HighlightToken(lineIndex, i, j, colorValue, false));
                    } else {
                        Integer colorVal = ColorParser.parse(word);
                        if (colorVal != null) {
                            tokens.add(new HighlightToken(lineIndex, i, j, colorValue, false, true, colorVal));
                        } else {
                            tokens.add(new HighlightToken(lineIndex, i, j, colorValue, false));
                        }
                    }
                } else {
                    int k = j;
                    while (k < len && Character.isWhitespace(lineStr.charAt(k))) k++;
                    if (bracketDepth > 0 && k < len && lineStr.charAt(k) == ':') {
                        int nextBrace = lineStr.indexOf('{', k + 1);
                        int nextSemi = lineStr.indexOf(';', k + 1);
                        if (nextBrace != -1 && (nextSemi == -1 || nextBrace < nextSemi)) {
                            tokens.add(new HighlightToken(lineIndex, i, j, colorSelector, false));
                        } else {
                            tokens.add(new HighlightToken(lineIndex, i, j, colorProperty, false));
                            inValue = true;
                        }
                    } else {
                        tokens.add(new HighlightToken(lineIndex, i, j, colorSelector, false));
                    }
                }

                i = j;
                continue;
            }

            if (quote == ':' && inValue) {
                tokens.add(new HighlightToken(lineIndex, i, i + 1, colorBracket, false));
                i++;
                continue;
            }

            i++;
        }

        int endState = 0;
        if (bracketDepth > 0) endState |= 1;
        if (inComment) endState |= 2;
        if (inValue) endState |= 4;
        endState |= ((bracketDepth & 0xFF) << 5);
        if (inStringDouble) endState |= (1 << 13);
        if (inStringSingle) endState |= (1 << 14);
        lastLineState = endState;
        return tokens;
    }

    @Override
    public int computeEndState(ContentLine line, int startState) {
        int len = line.length();
        int i = 0;

        boolean inComment = (startState & 2) != 0;
        boolean inValue = (startState & 4) != 0;
        int bracketDepth = (startState >>> 5) & 0xFF;
        if (bracketDepth == 0 && (startState & 1) != 0) {
            bracketDepth = 1;
        }
        boolean inStringDouble = (startState & (1 << 13)) != 0;
        boolean inStringSingle = (startState & (1 << 14)) != 0;

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

            if (inStringDouble || inStringSingle) {
                char q = inStringDouble ? '"' : '\'';
                int j = i;
                while (j < len) {
                    if (line.charAt(j) == '\\') {
                        j += 2;
                        continue;
                    }
                    if (line.charAt(j) == q) {
                        j++;
                        break;
                    }
                    j++;
                }
                if (j < len || (j == len && line.charAt(len - 1) == q && (len < 2 || line.charAt(len - 2) != '\\'))) {
                    inStringDouble = false;
                    inStringSingle = false;
                }
                i = j;
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
                bracketDepth++;
                inValue = false;
                i++;
                continue;
            }

            if (quote == '}') {
                bracketDepth = Math.max(0, bracketDepth - 1);
                inValue = false;
                i++;
                continue;
            }

            if (quote == ';') {
                inValue = false;
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
                if (j >= len && (len < 1 || line.charAt(len - 1) != quote || (len >= 2 && line.charAt(len - 2) == '\\'))) {
                    if (quote == '"') inStringDouble = true;
                    else inStringSingle = true;
                }
                i = j;
                continue;
            }

            if (isWordStart(quote)) {
                int j = i;
                while (j < len && isWordPart(line.charAt(j))) j++;
                if (!inValue && bracketDepth > 0) {
                    int k = j;
                    while (k < len && Character.isWhitespace(line.charAt(k))) k++;
                    if (k < len && line.charAt(k) == ':') {
                        inValue = true;
                    }
                }
                i = j;
                continue;
            }

            i++;
        }

        int endState = 0;
        if (bracketDepth > 0) endState |= 1;
        if (inComment) endState |= 2;
        if (inValue) endState |= 4;
        endState |= ((bracketDepth & 0xFF) << 5);
        if (inStringDouble) endState |= (1 << 13);
        if (inStringSingle) endState |= (1 << 14);
        return endState;
    }

    private String extractMultiLineExpression(String lineStr, int lineIndex, int startCol, int openParenCol) {
        if (content == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append(lineStr.substring(startCol));
        int depth = 0;
        for (int k = openParenCol; k < lineStr.length(); k++) {
            char c = lineStr.charAt(k);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return sb.toString();
            }
        }
        int totalLines = content.lineCount();
        for (int l = lineIndex + 1; l < Math.min(totalLines, lineIndex + 20); l++) {
            String nextLine = content.getLine(l).toLineString();
            sb.append("\n");
            for (int k = 0; k < nextLine.length(); k++) {
                char c = nextLine.charAt(k);
                sb.append(c);
                if (c == '(') depth++;
                else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        return sb.toString();
                    }
                }
            }
        }
        return null;
    }
}
