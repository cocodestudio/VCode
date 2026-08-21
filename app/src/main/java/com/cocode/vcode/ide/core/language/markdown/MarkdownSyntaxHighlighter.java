package com.cocode.vcode.ide.core.language.markdown;

import android.content.Context;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import com.cocode.vcode.ide.core.editor.text.ContentLine;
import com.cocode.vcode.ide.core.language.base.SyntaxHighlighter;
import com.cocode.vcode.ide.core.language.html.HtmlSyntaxHighlighter;

import java.util.ArrayList;
import java.util.List;

/**
 * Syntax highlighter for Markdown documents.
 */
public class MarkdownSyntaxHighlighter extends SyntaxHighlighter {

    // States for multi-line code blocks (``` fences)
    private static final int STATE_NORMAL = 0;
    private static final int STATE_FENCE = 1; // inside ``` block
    private final int colorHeader;
    private final int colorBold;
    private final int colorItalic;
    private final int colorCode;
    private final int colorQuote;
    private final int colorList;
    private final int colorLink;
    private final int colorStrikethrough;
    private final HtmlSyntaxHighlighter htmlHighlighter;

    public MarkdownSyntaxHighlighter(Context context) {
        super(context);
        colorHeader = getColor(R.color.vcode_color_md_header);
        colorBold = getColor(R.color.vcode_color_md_bold);
        colorItalic = getColor(R.color.vcode_color_md_italic);
        colorCode = getColor(R.color.vcode_color_md_code);
        colorQuote = getColor(R.color.vcode_color_md_quote);
        colorList = getColor(R.color.vcode_color_md_list);
        colorLink = getColor(R.color.vcode_color_md_link);
        colorStrikethrough = getColor(R.color.vcode_color_md_strikethrough);
        htmlHighlighter = new HtmlSyntaxHighlighter(context);
    }

    @Override
    public List<HighlightToken> tokenizeLine(String lineStr, int lineIndex, int startState) {
        List<HighlightToken> tokens = new ArrayList<>();
        int len = lineStr.length();

    // Fenced code block
        if (startState == STATE_FENCE) {
            int i = 0;
            while (i < len && lineStr.charAt(i) == '`') i++;
            tokens.add(new HighlightToken(lineIndex, 0, len, colorCode, false));
            lastLineState = (i >= 3) ? STATE_NORMAL : STATE_FENCE;
            return tokens;
        }

    // Opening fence
        if (len >= 3 && lineStr.charAt(0) == '`' && lineStr.charAt(1) == '`' && lineStr.charAt(2) == '`') {
            tokens.add(new HighlightToken(lineIndex, 0, len, colorCode, false));
            lastLineState = STATE_FENCE;
            return tokens;
        }

    // Blank line
        if (len == 0) {
            lastLineState = STATE_NORMAL;
            return tokens;
        }

        int i = 0;

    // ATX Header: # ## ###
        if (lineStr.charAt(0) == '#') {
            tokens.add(new HighlightToken(lineIndex, 0, len, colorHeader, false));
            lastLineState = STATE_NORMAL;
            return tokens;
        }

    // Block quote: >
        if (lineStr.charAt(0) == '>') {
            tokens.add(new HighlightToken(lineIndex, 0, len, colorQuote, false));
            lastLineState = STATE_NORMAL;
            return tokens;
        }

    // HTML block: line starts with < (an HTML tag)
        // Delegate the entire line to the HTML highlighter.
        if (lineStr.charAt(0) == '<') {
            List<HighlightToken> htmlTokens = htmlHighlighter.tokenizeLine(lineStr, lineIndex, 0);
            tokens.addAll(htmlTokens);
            lastLineState = STATE_NORMAL;
            return tokens;
        }

    // Unordered list: - * +
        if ((lineStr.charAt(0) == '-' || lineStr.charAt(0) == '*' || lineStr.charAt(0) == '+')
                && len > 1 && lineStr.charAt(1) == ' ') {
            tokens.add(new HighlightToken(lineIndex, 0, 1, colorList, false));
            i = 1;
        }

    // Ordered list: 1. 2.
        if (i == 0 && Character.isDigit(lineStr.charAt(0))) {
            int j = 0;
            while (j < len && Character.isDigit(lineStr.charAt(j))) j++;
            if (j < len && lineStr.charAt(j) == '.') {
                tokens.add(new HighlightToken(lineIndex, 0, j + 1, colorList, false));
                i = j + 1;
            }
        }

    // Inline scanning
        while (i < len) {
            char delim = lineStr.charAt(i);

            // Inline code: `...`
            if (delim == '`') {
                int j = i + 1;
                while (j < len && lineStr.charAt(j) != '`') j++;
                if (j < len) j++; // include closing `
                tokens.add(new HighlightToken(lineIndex, i, j, colorCode, false));
                i = j;
                continue;
            }

            // Inline HTML: <tag ...> — delegate the tag substring to HtmlSyntaxHighlighter
            if (delim == '<') {
                // Find the closing '>' (handle attributes with quoted '>' inside them)
                int j = i + 1;
                boolean inQuote = false;
                char quoteChar = 0;
                while (j < len) {
                    char ch = lineStr.charAt(j);
                    if (inQuote) {
                        if (ch == quoteChar) inQuote = false;
                    } else {
                        if (ch == '"' || ch == '\'') {
                            inQuote = true;
                            quoteChar = ch;
                        } else if (ch == '>') {
                            j++;
                            break;
                        }
                    }
                    j++;
                }
                // Tokenize the tag substring with the HTML highlighter
                String tagSub = lineStr.substring(i, Math.min(j, len));
                List<HighlightToken> htmlTokens = htmlHighlighter.tokenizeLine(tagSub, lineIndex, 0);
                for (HighlightToken t : htmlTokens) {
                    t.startCol += i;
                    t.endCol += i;
                    tokens.add(t);
                }
                i = Math.min(j, len);
                continue;
            }

            // Bold+italic: ***text*** or ___text___
            if ((delim == '*' || delim == '_') && i + 2 < len && lineStr.charAt(i + 1) == delim && lineStr.charAt(i + 2) == delim) {
                String closing = "" + delim + delim + delim;
                int j = lineStr.indexOf(closing, i + 3);
                if (j != -1) {
                    tokens.add(new HighlightToken(lineIndex, i, j + 3, colorBold, false));
                    i = j + 3;
                    continue;
                }
            }

            // Bold: **text** or __text__
            if ((delim == '*' || delim == '_') && i + 1 < len && lineStr.charAt(i + 1) == delim) {
                String closing = "" + delim + delim;
                int j = lineStr.indexOf(closing, i + 2);
                if (j != -1) {
                    tokens.add(new HighlightToken(lineIndex, i, j + 2, colorBold, false));
                    i = j + 2;
                    continue;
                }
            }

            // Italic: *text* or _text_
            if (delim == '*' || delim == '_') {
                int j = i + 1;
                while (j < len && lineStr.charAt(j) != delim) j++;
                if (j < len) {
                    tokens.add(new HighlightToken(lineIndex, i, j + 1, colorItalic, false));
                    i = j + 1;
                    continue;
                }
            }

            // Strikethrough: ~~text~~
            if (delim == '~' && i + 1 < len && lineStr.charAt(i + 1) == '~') {
                int j = lineStr.indexOf("~~", i + 2);
                if (j != -1) {
                    tokens.add(new HighlightToken(lineIndex, i, j + 2, colorStrikethrough, false));
                    i = j + 2;
                    continue;
                }
            }

            // Link/image: [text](url) or ![text](url)
            if (delim == '[' || (delim == '!' && i + 1 < len && lineStr.charAt(i + 1) == '[')) {
                int start = i;
                if (delim == '!') i++;
                int closeBracket = lineStr.indexOf(']', i + 1);
                if (closeBracket != -1 && closeBracket + 1 < len && lineStr.charAt(closeBracket + 1) == '(') {
                    int closeParen = lineStr.indexOf(')', closeBracket + 2);
                    if (closeParen != -1) {
                        tokens.add(new HighlightToken(lineIndex, start, closeParen + 1, colorLink, false));
                        i = closeParen + 1;
                        continue;
                    }
                }
                i = start + 1;
                continue;
            }

            i++;
        }

        lastLineState = STATE_NORMAL;
        return tokens;
    }

    @Override
    public int computeEndState(ContentLine line, int startState) {
        int len = line.length();

        if (startState == STATE_FENCE) {
            int i = 0;
            while (i < len && line.charAt(i) == '`') i++;
            return (i >= 3) ? STATE_NORMAL : STATE_FENCE;
        }

        if (len >= 3 && line.charAt(0) == '`' && line.charAt(1) == '`' && line.charAt(2) == '`') {
            return STATE_FENCE;
        }

        return STATE_NORMAL;
    }
}
