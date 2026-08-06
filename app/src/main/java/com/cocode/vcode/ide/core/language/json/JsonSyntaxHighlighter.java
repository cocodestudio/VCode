package com.cocode.vcode.ide.core.language.json;

import android.content.Context;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import com.cocode.vcode.ide.core.editor.text.ContentLine;
import com.cocode.vcode.ide.core.language.base.SyntaxHighlighter;

import java.util.ArrayList;
import java.util.List;

public class JsonSyntaxHighlighter extends SyntaxHighlighter {

    private final int colorKey;
    private final int colorStringValue;
    private final int colorNumberValue;
    private final int colorBoolean;
    private final int colorNull;
    private final int colorBracket;
    private final int colorColon;
    private final int colorComma;

    public JsonSyntaxHighlighter(Context context) {
        super(context);
        colorKey = getColor(R.color.vcode_color_json_key);
        colorStringValue = getColor(R.color.vcode_color_json_string);
        colorNumberValue = getColor(R.color.vcode_color_json_number);
        colorBoolean = getColor(R.color.vcode_color_json_boolean);
        colorNull = getColor(R.color.vcode_color_json_null);
        colorBracket = getColor(R.color.vcode_color_json_bracket);
        colorColon = getColor(R.color.vcode_color_json_colon);
        colorComma = getColor(R.color.vcode_color_json_comma);
    }

    @Override
    public List<HighlightToken> tokenizeLine(String lineStr, int lineIndex, int startState) {
        List<HighlightToken> tokens = new ArrayList<>();
        int len = lineStr.length();
        int state = startState;
        int i = 0;

        while (i < len) {
            char c = lineStr.charAt(i);

            if (state == 1) { // Inside multiline string
                int j = i;
                while (j < len) {
                    if (lineStr.charAt(j) == '\\') {
                        j += 2;
                        continue;
                    }
                    if (lineStr.charAt(j) == '"') {
                        j++;
                        break;
                    }
                    j++;
                }

                int color = colorStringValue;
                if (j < len || (j == len && lineStr.charAt(len - 1) == '"' && (len < 2 || lineStr.charAt(len - 2) != '\\'))) {
                    state = 0;
                    // peek ahead for colon to see if it's a key
                    int k = j;
                    while (k < len && Character.isWhitespace(lineStr.charAt(k))) {
                        k++;
                    }
                    if (k < len && lineStr.charAt(k) == ':') {
                        color = colorKey;
                    }
                }

                tokens.add(new HighlightToken(lineIndex, i, Math.min(j, len), color, false));
                i = j;
                continue;
            }

            if (c == '"') {
                int j = i + 1;
                while (j < len) {
                    if (lineStr.charAt(j) == '\\') {
                        j += 2;
                        continue;
                    }
                    if (lineStr.charAt(j) == '"') {
                        j++;
                        break;
                    }
                    j++;
                }

                int color = colorStringValue;
                if (j < len || (j == len && lineStr.charAt(len - 1) == '"' && (len < 2 || lineStr.charAt(len - 2) != '\\'))) {
                    state = 0;
                    // peek ahead for colon
                    int k = j;
                    while (k < len && Character.isWhitespace(lineStr.charAt(k))) {
                        k++;
                    }
                    if (k < len && lineStr.charAt(k) == ':') {
                        color = colorKey;
                    }
                } else {
                    state = 1;
                }

                tokens.add(new HighlightToken(lineIndex, i, Math.min(j, len), color, false));
                i = j;
                continue;
            }

            if (c == '{' || c == '}' || c == '[' || c == ']') {
                tokens.add(new HighlightToken(lineIndex, i, i + 1, colorBracket, false));
                i++;
                continue;
            }

            if (c == ':') {
                tokens.add(new HighlightToken(lineIndex, i, i + 1, colorColon, false));
                i++;
                continue;
            }

            if (c == ',') {
                tokens.add(new HighlightToken(lineIndex, i, i + 1, colorComma, false));
                i++;
                continue;
            }

            if (c == '-' || Character.isDigit(c)) {
                int j = i + 1;
                while (j < len && (Character.isDigit(lineStr.charAt(j)) || lineStr.charAt(j) == '.' || lineStr.charAt(j) == 'e' || lineStr.charAt(j) == 'E' || lineStr.charAt(j) == '+' || lineStr.charAt(j) == '-')) {
                    j++;
                }
                tokens.add(new HighlightToken(lineIndex, i, j, colorNumberValue, false));
                i = j;
                continue;
            }

            if (Character.isLetter(c)) {
                int j = i;
                while (j < len && Character.isLetter(lineStr.charAt(j))) {
                    j++;
                }
                String word = lineStr.substring(i, j);
                if (word.equals("true") || word.equals("false")) {
                    tokens.add(new HighlightToken(lineIndex, i, j, colorBoolean, false));
                } else if (word.equals("null")) {
                    tokens.add(new HighlightToken(lineIndex, i, j, colorNull, false));
                }
                i = j;
                continue;
            }

            i++;
        }

        lastLineState = state;
        return tokens;
    }

    @Override
    public int computeEndState(ContentLine line, int startState) {
        int len = line.length();
        int state = startState;
        int i = 0;

        while (i < len) {
            char c = line.charAt(i);

            if (state == 1) { // Inside multiline string
                int j = i;
                while (j < len) {
                    if (line.charAt(j) == '\\') {
                        j += 2;
                        continue;
                    }
                    if (line.charAt(j) == '"') {
                        j++;
                        break;
                    }
                    j++;
                }

                if (j < len || (j == len && line.charAt(len - 1) == '"' && (len < 2 || line.charAt(len - 2) != '\\'))) {
                    state = 0;
                }
                i = j;
                continue;
            }

            if (c == '"') {
                int j = i + 1;
                while (j < len) {
                    if (line.charAt(j) == '\\') {
                        j += 2;
                        continue;
                    }
                    if (line.charAt(j) == '"') {
                        j++;
                        break;
                    }
                    j++;
                }

                if (j < len || (j == len && line.charAt(len - 1) == '"' && (len < 2 || line.charAt(len - 2) != '\\'))) {
                    state = 0;
                } else {
                    state = 1;
                }

                i = j;
                continue;
            }

            i++;
        }

        return state;
    }
}
