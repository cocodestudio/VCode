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

    public static final int STATE_NORMAL = 0;
    public static final int STATE_BLOCK_COMMENT = 1;
    public static final int STATE_STRING_DOUBLE = 2;
    public static final int STATE_STRING_SINGLE = 3;
    public static final int STATE_TEMPLATE_LITERAL = 4;
    public static final int STATE_TEMPLATE_EXPR = 5;

    private static final Set<String> JS_KEYWORDS = new HashSet<>(Arrays.asList(
            "var", "let", "const", "function", "return",
            "if", "else", "for", "while", "do",
            "switch", "case", "break", "continue", "new",
            "delete", "typeof", "instanceof", "in", "of",
            "class", "extends", "import", "export", "default",
            "async", "await", "try", "catch", "finally",
            "throw", "void", "yield", "this", "super",
            "static", "get", "set", "as", "from", "debugger",
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
     * Test-only factory allowing explicit test colors for JVM testing.
     */
    public static JsSyntaxHighlighter forTestWithColors(int comment, int string, int keyword, int number, int function, int booleanCol, int operator) {
        return new JsSyntaxHighlighter(comment, string, keyword, number, function, booleanCol, operator);
    }

    /**
     * Test-only constructor: skips colour resolution so unit tests
     * can construct a highlighter without a real Android {@code
     * Context}. Colours will be {@code 0} and should be overridden
     * via the resolver-supplied {@code highlightViewport} overload.
     */
    protected JsSyntaxHighlighter(Void unusedForTest) {
        super((Void) null);
        this.colorFunction = 0;
        this.colorBoolean = 0;
    }

    protected JsSyntaxHighlighter(int comment, int string, int keyword, int number, int function, int booleanCol, int operator) {
        super(comment, string, keyword, number, operator);
        this.colorFunction = function;
        this.colorBoolean = booleanCol;
    }

    @Override
    protected boolean isKeyword(String word) {
        return JS_KEYWORDS.contains(word) || JS_BOOLEANS.contains(word);
    }

    protected boolean isBoolean(String word) {
        return JS_BOOLEANS.contains(word);
    }

    public static int getMode(int state) {
        return state & 0x7;
    }

    public static int getTemplateDepth(int state) {
        return (state >>> 3) & 0x7;
    }

    public static int getBraceDepth(int state) {
        return (state >>> 6) & 0x3FF;
    }

    public static int packState(int mode, int templateDepth, int braceDepth) {
        return (mode & 0x7) | ((templateDepth & 0x7) << 3) | ((braceDepth & 0x3FF) << 6);
    }

    private static boolean isRegexAllowedAfterKeyword(String word) {
        switch (word) {
            case "return":
            case "case":
            case "typeof":
            case "yield":
            case "await":
            case "delete":
            case "throw":
            case "void":
            case "instanceof":
            case "in":
            case "of":
            case "new":
                return true;
            default:
                return false;
        }
    }

    private static int matchOperator(String s, int pos, int len) {
        if (pos >= len) return 0;
        char c1 = s.charAt(pos);
        char c2 = (pos + 1 < len) ? s.charAt(pos + 1) : '\0';
        char c3 = (pos + 2 < len) ? s.charAt(pos + 2) : '\0';

        // 3-char operators
        if (c1 == '.' && c2 == '.' && c3 == '.') return 3;
        if (c1 == '=' && c2 == '=' && c3 == '=') return 3;
        if (c1 == '!' && c2 == '=' && c3 == '=') return 3;
        if (c1 == '>' && c2 == '>' && c3 == '>') return 3;
        if (c1 == '<' && c2 == '<' && c3 == '=') return 3;
        if (c1 == '>' && c2 == '>' && c3 == '=') return 3;
        if (c1 == '*' && c2 == '*' && c3 == '=') return 3;
        if (c1 == '&' && c2 == '&' && c3 == '=') return 3;
        if (c1 == '|' && c2 == '|' && c3 == '=') return 3;
        if (c1 == '?' && c2 == '?' && c3 == '=') return 3;

        // 2-char operators
        if (c1 == '=' && c2 == '>') return 2;
        if (c1 == '=' && c2 == '=') return 2;
        if (c1 == '!' && c2 == '=') return 2;
        if (c1 == '<' && c2 == '=') return 2;
        if (c1 == '>' && c2 == '=') return 2;
        if (c1 == '&' && c2 == '&') return 2;
        if (c1 == '|' && c2 == '|') return 2;
        if (c1 == '?' && c2 == '?') return 2;
        if (c1 == '?' && c2 == '.') return 2;
        if (c1 == '+' && c2 == '+') return 2;
        if (c1 == '-' && c2 == '-') return 2;
        if (c1 == '+' && c2 == '=') return 2;
        if (c1 == '-' && c2 == '=') return 2;
        if (c1 == '*' && c2 == '=') return 2;
        if (c1 == '/' && c2 == '=') return 2;
        if (c1 == '%' && c2 == '=') return 2;
        if (c1 == '&' && c2 == '=') return 2;
        if (c1 == '|' && c2 == '=') return 2;
        if (c1 == '^' && c2 == '=') return 2;
        if (c1 == '<' && c2 == '<') return 2;
        if (c1 == '>' && c2 == '>') return 2;
        if (c1 == '*' && c2 == '*') return 2;

        // 1-char operators
        switch (c1) {
            case '+':
            case '-':
            case '*':
            case '%':
            case '&':
            case '|':
            case '^':
            case '~':
            case '!':
            case '<':
            case '>':
            case '=':
            case '?':
            case ':':
            case '.':
                return 1;
            default:
                return 0;
        }
    }

    @Override
    public List<HighlightToken> tokenizeLine(String lineStr, int lineIndex, int startState) {
        List<HighlightToken> tokens = new ArrayList<>();
        int len = lineStr.length();
        int mode = getMode(startState);
        int templateDepth = getTemplateDepth(startState);
        int braceDepth = getBraceDepth(startState);

        if (mode == STATE_TEMPLATE_LITERAL && templateDepth == 0) {
            templateDepth = 1;
        }

        int i = 0;
        int lastTokenType = 0;

        while (i < len) {
            char c = lineStr.charAt(i);

            // Block comment continuation
            if (mode == STATE_BLOCK_COMMENT) {
                int commentEnd = lineStr.indexOf("*/", i);
                if (commentEnd != -1) {
                    tokens.add(new HighlightToken(lineIndex, i, commentEnd + 2, colorComment, false));
                    i = commentEnd + 2;
                    mode = (templateDepth > 0 && braceDepth > 0) ? STATE_TEMPLATE_EXPR : (templateDepth > 0 ? STATE_TEMPLATE_LITERAL : STATE_NORMAL);
                } else {
                    tokens.add(new HighlightToken(lineIndex, i, len, colorComment, false));
                    i = len;
                }
                continue;
            }

            // String continuation
            if (mode == STATE_STRING_DOUBLE || mode == STATE_STRING_SINGLE) {
                char quote = (mode == STATE_STRING_DOUBLE) ? '"' : '\'';
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
                    mode = (templateDepth > 0 && braceDepth > 0) ? STATE_TEMPLATE_EXPR : (templateDepth > 0 ? STATE_TEMPLATE_LITERAL : STATE_NORMAL);
                }
                i = j;
                lastTokenType = 1;
                continue;
            }

            // Template literal string continuation
            if (mode == STATE_TEMPLATE_LITERAL) {
                int j = i;
                int tokenEnd = -1;
                boolean openedExpr = false;
                boolean closedTemplate = false;

                while (j < len) {
                    char ch = lineStr.charAt(j);
                    if (ch == '\\') {
                        j += 2;
                        continue;
                    }
                    if (ch == '$' && j + 1 < len && lineStr.charAt(j + 1) == '{') {
                        tokenEnd = j;
                        openedExpr = true;
                        break;
                    }
                    if (ch == '`') {
                        tokenEnd = j + 1;
                        closedTemplate = true;
                        break;
                    }
                    j++;
                }

                if (openedExpr) {
                    if (tokenEnd > i) {
                        tokens.add(new HighlightToken(lineIndex, i, tokenEnd, colorString, false));
                    }
                    tokens.add(new HighlightToken(lineIndex, tokenEnd, tokenEnd + 2, colorOperator, false));
                    i = tokenEnd + 2;
                    mode = STATE_TEMPLATE_EXPR;
                    braceDepth = 1;
                    lastTokenType = 0;
                    continue;
                } else if (closedTemplate) {
                    tokens.add(new HighlightToken(lineIndex, i, tokenEnd, colorString, false));
                    i = tokenEnd;
                    templateDepth--;
                    if (templateDepth > 0) {
                        mode = STATE_TEMPLATE_EXPR;
                    } else {
                        mode = STATE_NORMAL;
                        templateDepth = 0;
                        braceDepth = 0;
                    }
                    lastTokenType = 1;
                    continue;
                } else {
                    tokens.add(new HighlightToken(lineIndex, i, len, colorString, false));
                    i = len;
                    continue;
                }
            }

            // Comments
            if (c == '/' && i + 1 < len) {
                if (lineStr.charAt(i + 1) == '*') {
                    int commentEnd = lineStr.indexOf("*/", i + 2);
                    if (commentEnd != -1) {
                        tokens.add(new HighlightToken(lineIndex, i, commentEnd + 2, colorComment, false));
                        i = commentEnd + 2;
                    } else {
                        tokens.add(new HighlightToken(lineIndex, i, len, colorComment, false));
                        mode = STATE_BLOCK_COMMENT;
                        i = len;
                    }
                    continue;
                } else if (lineStr.charAt(i + 1) == '/') {
                    tokens.add(new HighlightToken(lineIndex, i, len, colorComment, false));
                    i = len;
                    continue;
                }
            }

            // Opening template literal in code mode
            if (c == '`') {
                templateDepth++;
                mode = STATE_TEMPLATE_LITERAL;
                int j = i + 1;
                int tokenEnd = -1;
                boolean openedExpr = false;
                boolean closedTemplate = false;

                while (j < len) {
                    char ch = lineStr.charAt(j);
                    if (ch == '\\') {
                        j += 2;
                        continue;
                    }
                    if (ch == '$' && j + 1 < len && lineStr.charAt(j + 1) == '{') {
                        tokenEnd = j;
                        openedExpr = true;
                        break;
                    }
                    if (ch == '`') {
                        tokenEnd = j + 1;
                        closedTemplate = true;
                        break;
                    }
                    j++;
                }

                if (openedExpr) {
                    tokens.add(new HighlightToken(lineIndex, i, tokenEnd, colorString, false));
                    tokens.add(new HighlightToken(lineIndex, tokenEnd, tokenEnd + 2, colorOperator, false));
                    i = tokenEnd + 2;
                    mode = STATE_TEMPLATE_EXPR;
                    braceDepth = 1;
                    lastTokenType = 0;
                    continue;
                } else if (closedTemplate) {
                    tokens.add(new HighlightToken(lineIndex, i, tokenEnd, colorString, false));
                    i = tokenEnd;
                    templateDepth--;
                    if (templateDepth > 0) {
                        mode = STATE_TEMPLATE_EXPR;
                    } else {
                        mode = STATE_NORMAL;
                        templateDepth = 0;
                        braceDepth = 0;
                    }
                    lastTokenType = 1;
                    continue;
                } else {
                    tokens.add(new HighlightToken(lineIndex, i, len, colorString, false));
                    i = len;
                    continue;
                }
            }

            // Regular strings
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < len) {
                    if (lineStr.charAt(j) == '\\') {
                        j += 2;
                        continue;
                    }
                    if (lineStr.charAt(j) == c) {
                        j++;
                        break;
                    }
                    j++;
                }
                tokens.add(new HighlightToken(lineIndex, i, Math.min(j, len), colorString, false));
                if (j >= len && (len < 1 || lineStr.charAt(len - 1) != c || (len >= 2 && lineStr.charAt(len - 2) == '\\'))) {
                    mode = (c == '"') ? STATE_STRING_DOUBLE : STATE_STRING_SINGLE;
                }
                i = j;
                lastTokenType = 1;
                continue;
            }

            // Braces inside template expressions
            if (mode == STATE_TEMPLATE_EXPR) {
                if (c == '{') {
                    braceDepth++;
                    tokens.add(new HighlightToken(lineIndex, i, i + 1, colorOperator, false));
                    i++;
                    lastTokenType = 0;
                    continue;
                }
                if (c == '}') {
                    braceDepth--;
                    if (braceDepth == 0) {
                        tokens.add(new HighlightToken(lineIndex, i, i + 1, colorOperator, false));
                        i++;
                        mode = STATE_TEMPLATE_LITERAL;
                        lastTokenType = 0;
                        continue;
                    } else {
                        tokens.add(new HighlightToken(lineIndex, i, i + 1, colorOperator, false));
                        i++;
                        lastTokenType = 1;
                        continue;
                    }
                }
            }

            // Color hex #RGB, #RRGGBB
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
                    lastTokenType = 1;
                    continue;
                }
            }

            // Regex literal vs division
            if (c == '/') {
                if (lastTokenType == 0) {
                    int j = i + 1;
                    boolean inCharClass = false;
                    boolean closed = false;
                    while (j < len) {
                        char rc = lineStr.charAt(j);
                        if (rc == '\\') {
                            j += 2;
                            continue;
                        }
                        if (rc == '[') inCharClass = true;
                        else if (rc == ']') inCharClass = false;
                        else if (rc == '/' && !inCharClass) {
                            closed = true;
                            j++;
                            break;
                        }
                        j++;
                    }
                    if (closed) {
                        while (j < len && Character.isLetter(lineStr.charAt(j))) j++;
                        tokens.add(new HighlightToken(lineIndex, i, j, colorString, false));
                        i = j;
                        lastTokenType = 1;
                        continue;
                    }
                }
                if (i + 1 < len && lineStr.charAt(i + 1) == '=') {
                    tokens.add(new HighlightToken(lineIndex, i, i + 2, colorOperator, false));
                    i += 2;
                } else {
                    tokens.add(new HighlightToken(lineIndex, i, i + 1, colorOperator, false));
                    i++;
                }
                lastTokenType = 0;
                continue;
            }

            // Numbers (with greedy dot fix)
            if (Character.isDigit(c)) {
                int j = i;
                if (c == '0' && j + 1 < len && (lineStr.charAt(j + 1) == 'x' || lineStr.charAt(j + 1) == 'X')) {
                    j += 2;
                    while (j < len && (isHex(lineStr.charAt(j)) || lineStr.charAt(j) == '_')) j++;
                } else if (c == '0' && j + 1 < len && (lineStr.charAt(j + 1) == 'b' || lineStr.charAt(j + 1) == 'B')) {
                    j += 2;
                    while (j < len && (lineStr.charAt(j) == '0' || lineStr.charAt(j) == '1' || lineStr.charAt(j) == '_')) j++;
                } else if (c == '0' && j + 1 < len && (lineStr.charAt(j + 1) == 'o' || lineStr.charAt(j + 1) == 'O')) {
                    j += 2;
                    while (j < len && ((lineStr.charAt(j) >= '0' && lineStr.charAt(j) <= '7') || lineStr.charAt(j) == '_')) j++;
                } else {
                    boolean hasDot = false;
                    while (j < len) {
                        char ch = lineStr.charAt(j);
                        if (Character.isDigit(ch) || ch == '_') {
                            j++;
                        } else if (ch == '.' && !hasDot && j + 1 < len && Character.isDigit(lineStr.charAt(j + 1))) {
                            hasDot = true;
                            j++;
                        } else if ((ch == 'e' || ch == 'E') && j + 1 < len) {
                            j++;
                            if (j < len && (lineStr.charAt(j) == '+' || lineStr.charAt(j) == '-')) j++;
                            while (j < len && Character.isDigit(lineStr.charAt(j))) j++;
                            break;
                        } else {
                            break;
                        }
                    }
                }
                if (j < len && lineStr.charAt(j) == 'n') j++;
                tokens.add(new HighlightToken(lineIndex, i, j, colorNumber, false));
                i = j;
                lastTokenType = 1;
                continue;
            }

            // Keywords and identifiers
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
                    lastTokenType = isRegexAllowedAfterKeyword(word) ? 0 : 1;
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
                    lastTokenType = 1;
                }
                i = j;
                continue;
            }

            // Operators
            int opLen = matchOperator(lineStr, i, len);
            if (opLen > 0) {
                tokens.add(new HighlightToken(lineIndex, i, i + opLen, colorOperator, false));
                i += opLen;
                lastTokenType = 0;
                continue;
            }

            // Single punctuation
            if (c == '(' || c == '[') {
                lastTokenType = 0;
            } else if (c == ')' || c == ']') {
                lastTokenType = 1;
            } else if (c == ',' || c == ';') {
                lastTokenType = 0;
            } else if (c == '{' && mode != STATE_TEMPLATE_EXPR) {
                lastTokenType = 0;
            } else if (c == '}' && mode != STATE_TEMPLATE_EXPR) {
                lastTokenType = 1;
            }

            i++;
        }

        lastLineState = packState(mode, templateDepth, braceDepth);
        return tokens;
    }

    @Override
    public int computeEndState(com.cocode.vcode.ide.core.editor.text.ContentLine line, int startState) {
        int len = line.length();
        int mode = getMode(startState);
        int templateDepth = getTemplateDepth(startState);
        int braceDepth = getBraceDepth(startState);

        if (mode == STATE_TEMPLATE_LITERAL && templateDepth == 0) {
            templateDepth = 1;
        }

        int i = 0;
        while (i < len) {
            char c = line.charAt(i);

            if (mode == STATE_BLOCK_COMMENT) {
                int commentEnd = -1;
                for (int k = i; k < len - 1; k++) {
                    if (line.charAt(k) == '*' && line.charAt(k + 1) == '/') {
                        commentEnd = k;
                        break;
                    }
                }
                if (commentEnd != -1) {
                    i = commentEnd + 2;
                    mode = (templateDepth > 0 && braceDepth > 0) ? STATE_TEMPLATE_EXPR : (templateDepth > 0 ? STATE_TEMPLATE_LITERAL : STATE_NORMAL);
                } else {
                    i = len;
                }
                continue;
            }

            if (mode == STATE_STRING_DOUBLE || mode == STATE_STRING_SINGLE) {
                char quote = (mode == STATE_STRING_DOUBLE) ? '"' : '\'';
                int j = i;
                while (j < len) {
                    if (line.charAt(j) == '\\') { j += 2; continue; }
                    if (line.charAt(j) == quote) { j++; break; }
                    j++;
                }
                if (j < len || (j == len && line.charAt(len - 1) == quote && (len < 2 || line.charAt(len - 2) != '\\'))) {
                    mode = (templateDepth > 0 && braceDepth > 0) ? STATE_TEMPLATE_EXPR : (templateDepth > 0 ? STATE_TEMPLATE_LITERAL : STATE_NORMAL);
                }
                i = j;
                continue;
            }

            if (mode == STATE_TEMPLATE_LITERAL) {
                int j = i;
                int tokenEnd = -1;
                boolean openedExpr = false;
                boolean closedTemplate = false;

                while (j < len) {
                    char ch = line.charAt(j);
                    if (ch == '\\') { j += 2; continue; }
                    if (ch == '$' && j + 1 < len && line.charAt(j + 1) == '{') {
                        tokenEnd = j;
                        openedExpr = true;
                        break;
                    }
                    if (ch == '`') {
                        tokenEnd = j + 1;
                        closedTemplate = true;
                        break;
                    }
                    j++;
                }

                if (openedExpr) {
                    i = tokenEnd + 2;
                    mode = STATE_TEMPLATE_EXPR;
                    braceDepth = 1;
                    continue;
                } else if (closedTemplate) {
                    i = tokenEnd;
                    templateDepth--;
                    if (templateDepth > 0) {
                        mode = STATE_TEMPLATE_EXPR;
                    } else {
                        mode = STATE_NORMAL;
                        templateDepth = 0;
                        braceDepth = 0;
                    }
                    continue;
                } else {
                    i = len;
                    continue;
                }
            }

            if (c == '/' && i + 1 < len) {
                if (line.charAt(i + 1) == '*') {
                    int commentEnd = -1;
                    for (int k = i + 2; k < len - 1; k++) {
                        if (line.charAt(k) == '*' && line.charAt(k + 1) == '/') {
                            commentEnd = k;
                            break;
                        }
                    }
                    if (commentEnd != -1) {
                        i = commentEnd + 2;
                    } else {
                        mode = STATE_BLOCK_COMMENT;
                        i = len;
                    }
                    continue;
                } else if (line.charAt(i + 1) == '/') {
                    i = len;
                    continue;
                }
            }

            if (c == '`') {
                templateDepth++;
                mode = STATE_TEMPLATE_LITERAL;
                int j = i + 1;
                int tokenEnd = -1;
                boolean openedExpr = false;
                boolean closedTemplate = false;

                while (j < len) {
                    char ch = line.charAt(j);
                    if (ch == '\\') { j += 2; continue; }
                    if (ch == '$' && j + 1 < len && line.charAt(j + 1) == '{') {
                        tokenEnd = j;
                        openedExpr = true;
                        break;
                    }
                    if (ch == '`') {
                        tokenEnd = j + 1;
                        closedTemplate = true;
                        break;
                    }
                    j++;
                }

                if (openedExpr) {
                    i = tokenEnd + 2;
                    mode = STATE_TEMPLATE_EXPR;
                    braceDepth = 1;
                    continue;
                } else if (closedTemplate) {
                    i = tokenEnd;
                    templateDepth--;
                    if (templateDepth > 0) {
                        mode = STATE_TEMPLATE_EXPR;
                    } else {
                        mode = STATE_NORMAL;
                        templateDepth = 0;
                        braceDepth = 0;
                    }
                    continue;
                } else {
                    i = len;
                    continue;
                }
            }

            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < len) {
                    if (line.charAt(j) == '\\') { j += 2; continue; }
                    if (line.charAt(j) == c) { j++; break; }
                    j++;
                }
                if (j >= len && (len < 1 || line.charAt(len - 1) != c || (len >= 2 && line.charAt(len - 2) == '\\'))) {
                    mode = (c == '"') ? STATE_STRING_DOUBLE : STATE_STRING_SINGLE;
                }
                i = j;
                continue;
            }

            if (mode == STATE_TEMPLATE_EXPR) {
                if (c == '{') {
                    braceDepth++;
                    i++;
                    continue;
                }
                if (c == '}') {
                    braceDepth--;
                    if (braceDepth == 0) {
                        mode = STATE_TEMPLATE_LITERAL;
                    }
                    i++;
                    continue;
                }
            }

            i++;
        }

        return packState(mode, templateDepth, braceDepth);
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
