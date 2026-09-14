package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import com.cocode.vcode.ide.core.editor.text.ContentLine;
import com.cocode.vcode.ide.core.language.ts.TsSyntaxHighlighter;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class JsSyntaxHighlighterTest {

    private static final int C_COMMENT = 101;
    private static final int C_STRING = 102;
    private static final int C_KEYWORD = 103;
    private static final int C_NUMBER = 104;
    private static final int C_FUNCTION = 105;
    private static final int C_BOOLEAN = 106;
    private static final int C_OPERATOR = 107;

    private JsSyntaxHighlighter jsHighlighter;
    private TsSyntaxHighlighter tsHighlighter;

    @Before
    public void setUp() {
        jsHighlighter = JsSyntaxHighlighter.forTestWithColors(
                C_COMMENT, C_STRING, C_KEYWORD, C_NUMBER, C_FUNCTION, C_BOOLEAN, C_OPERATOR
        );
        tsHighlighter = TsSyntaxHighlighter.forTestWithColors(
                C_COMMENT, C_STRING, C_KEYWORD, C_NUMBER, C_FUNCTION, C_BOOLEAN, C_OPERATOR
        );
    }

    @Test
    public void testTemplateLiteralBasicInterpolation() {
        String code = "`hello ${name}`";
        List<HighlightToken> tokens = jsHighlighter.tokenizeLine(code, 0, 0);

        assertFalse("Tokens should not be empty", tokens.isEmpty());

        // Token 0: `hello  (0..7) as string
        assertEquals(0, tokens.get(0).startCol);
        assertEquals(7, tokens.get(0).endCol);
        assertEquals(C_STRING, tokens.get(0).color);

        // Token 1: ${ (7..9) as operator
        assertEquals(7, tokens.get(1).startCol);
        assertEquals(9, tokens.get(1).endCol);
        assertEquals(C_OPERATOR, tokens.get(1).color);

        // Token 2: } (13..14) as operator
        assertEquals(13, tokens.get(2).startCol);
        assertEquals(14, tokens.get(2).endCol);
        assertEquals(C_OPERATOR, tokens.get(2).color);

        // Token 3: ` (14..15) as string
        assertEquals(14, tokens.get(3).startCol);
        assertEquals(15, tokens.get(3).endCol);
        assertEquals(C_STRING, tokens.get(3).color);
    }

    @Test
    public void testTemplateLiteralWithComplexExpression() {
        String code = "`hello ${user.getName() + 42}`";
        List<HighlightToken> tokens = jsHighlighter.tokenizeLine(code, 0, 0);

        // Find function getName and number 42
        boolean foundFunction = false;
        boolean foundNumber = false;
        boolean foundPlus = false;

        for (HighlightToken t : tokens) {
            String text = code.substring(t.startCol, t.endCol);
            if ("getName".equals(text) && t.color == C_FUNCTION) {
                foundFunction = true;
            } else if ("42".equals(text) && t.color == C_NUMBER) {
                foundNumber = true;
            } else if ("+".equals(text) && t.color == C_OPERATOR) {
                foundPlus = true;
            }
        }

        assertTrue("Function getName() should be highlighted inside ${...}", foundFunction);
        assertTrue("Number 42 should be highlighted inside ${...}", foundNumber);
        assertTrue("Operator + should be highlighted inside ${...}", foundPlus);
    }

    @Test
    public void testTemplateLiteralWithObjectLiteral() {
        String code = "`data: ${{ a: 1 }}`";
        List<HighlightToken> tokens = jsHighlighter.tokenizeLine(code, 0, 0);

        boolean foundNumber = false;
        boolean foundFinalString = false;

        for (HighlightToken t : tokens) {
            String text = code.substring(t.startCol, t.endCol);
            if ("1".equals(text) && t.color == C_NUMBER) {
                foundNumber = true;
            } else if ("`".equals(text) && t.startCol == 18 && t.color == C_STRING) {
                foundFinalString = true;
            }
        }

        assertTrue("Number inside object literal in ${} should be highlighted", foundNumber);
        assertTrue("Closing backtick of template literal should be string", foundFinalString);
    }

    @Test
    public void testMultilineTemplateLiteralExpression() {
        String line1Str = "const s = `hello ${";
        String line2Str = "  format(name)";
        String line3Str = "}!`;";

        ContentLine line1 = new ContentLine(line1Str);
        ContentLine line2 = new ContentLine(line2Str);
        ContentLine line3 = new ContentLine(line3Str);

        int state1 = jsHighlighter.computeEndState(line1, 0);
        assertTrue("State1 should preserve template expr mode", (state1 & 0x7) == 5); // STATE_TEMPLATE_EXPR

        List<HighlightToken> tokens2 = jsHighlighter.tokenizeLine(line2Str, 1, state1);
        boolean foundFormat = false;
        for (HighlightToken t : tokens2) {
            String text = line2Str.substring(t.startCol, t.endCol);
            if ("format".equals(text) && t.color == C_FUNCTION) {
                foundFormat = true;
            }
        }
        assertTrue("format() on line 2 should be highlighted as function", foundFormat);

        int state2 = jsHighlighter.computeEndState(line2, state1);
        int state3 = jsHighlighter.computeEndState(line3, state2);
        assertEquals("State after closing template literal should be normal (0)", 0, state3 & 0x7);
    }

    @Test
    public void testGreedyDotNumberParsing() {
        String code = "123.toString()";
        List<HighlightToken> tokens = jsHighlighter.tokenizeLine(code, 0, 0);

        boolean foundNumber = false;
        boolean foundDot = false;
        boolean foundToString = false;

        for (HighlightToken t : tokens) {
            String text = code.substring(t.startCol, t.endCol);
            if ("123".equals(text) && t.color == C_NUMBER) {
                foundNumber = true;
            } else if (".".equals(text) && t.color == C_OPERATOR) {
                foundDot = true;
            } else if ("toString".equals(text) && t.color == C_FUNCTION) {
                foundToString = true;
            }
        }

        assertTrue("Number 123 should be parsed without greedy dot", foundNumber);
        assertTrue("Dot should be parsed as operator", foundDot);
        assertTrue("toString() should be parsed as function", foundToString);
    }

    @Test
    public void testRegexLiteralVsDivision() {
        String regexCode = "let r = /^[a-z]+$/gi;";
        List<HighlightToken> regexTokens = jsHighlighter.tokenizeLine(regexCode, 0, 0);

        boolean foundRegex = false;
        for (HighlightToken t : regexTokens) {
            String text = regexCode.substring(t.startCol, t.endCol);
            if ("/^[a-z]+$/gi".equals(text) && t.color == C_STRING) {
                foundRegex = true;
            }
        }
        assertTrue("Regex literal should be highlighted as string", foundRegex);

        String divCode = "let d = a / b;";
        List<HighlightToken> divTokens = jsHighlighter.tokenizeLine(divCode, 0, 0);

        boolean foundDiv = false;
        for (HighlightToken t : divTokens) {
            String text = divCode.substring(t.startCol, t.endCol);
            if ("/".equals(text) && t.color == C_OPERATOR) {
                foundDiv = true;
            }
        }
        assertTrue("Division operator should be highlighted as operator", foundDiv);
    }

    @Test
    public void testTypeScriptKeywords() {
        String code = "override satisfies asserts bigint";
        List<HighlightToken> tokens = tsHighlighter.tokenizeLine(code, 0, 0);

        assertEquals(4, tokens.size());
        for (HighlightToken t : tokens) {
            assertEquals(C_KEYWORD, t.color);
        }
    }
}
