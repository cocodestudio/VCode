package com.cocode.vcode.ide.core.language.css;

import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import com.cocode.vcode.ide.core.editor.text.ContentLine;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class CssSyntaxHighlighterTest {

    private static final int C_COMMENT = 201;
    private static final int C_SELECTOR = 202;
    private static final int C_PROPERTY = 203;
    private static final int C_VALUE = 204;
    private static final int C_AT_RULE = 205;
    private static final int C_BRACKET = 206;

    private CssSyntaxHighlighter cssHighlighter;

    @Before
    public void setUp() {
        cssHighlighter = CssSyntaxHighlighter.forTestWithColors(
                C_COMMENT, C_SELECTOR, C_PROPERTY, C_VALUE, C_AT_RULE, C_BRACKET
        );
    }

    @Test
    public void testNestedMediaRuleSelector() {
        String line1Str = "@media (min-width: 768px) {";
        ContentLine line1 = new ContentLine(line1Str);
        int state1 = cssHighlighter.computeEndState(line1, 0);

        String line2Str = "  .card { color: red; }";
        List<HighlightToken> tokens = cssHighlighter.tokenizeLine(line2Str, 1, state1);

        boolean foundCardSelector = false;
        boolean foundColorProperty = false;
        boolean foundRedValue = false;

        for (HighlightToken t : tokens) {
            String text = line2Str.substring(t.startCol, t.endCol);
            if (".card".equals(text) && t.color == C_SELECTOR) {
                foundCardSelector = true;
            } else if ("color".equals(text) && t.color == C_PROPERTY) {
                foundColorProperty = true;
            } else if ("red".equals(text) && t.color == C_VALUE) {
                foundRedValue = true;
            }
        }

        assertTrue("Selector .card inside @media block should be highlighted as selector", foundCardSelector);
        assertTrue("Property 'color' should be highlighted as property", foundColorProperty);
        assertTrue("Value 'red' should be highlighted as value", foundRedValue);
    }

    @Test
    public void testCssVariables() {
        String code = "--primary-color: #3498db;";
        int insideRuleState = 1 << 5; // bracketDepth = 1
        List<HighlightToken> tokens = cssHighlighter.tokenizeLine(code, 0, insideRuleState);

        boolean foundVariableProperty = false;
        boolean foundColorValue = false;

        for (HighlightToken t : tokens) {
            String text = code.substring(t.startCol, t.endCol);
            if ("--primary-color".equals(text) && t.color == C_PROPERTY) {
                foundVariableProperty = true;
            } else if ("#3498db".equals(text) && t.color == C_VALUE) {
                foundColorValue = true;
            }
        }

        assertTrue("CSS variable --primary-color should be highlighted as property", foundVariableProperty);
        assertTrue("Hex color value should be highlighted as value", foundColorValue);
    }

    @Test
    public void testKeyframesPercentageSelectors() {
        String line1Str = "@keyframes fadeIn {";
        ContentLine line1 = new ContentLine(line1Str);
        int state1 = cssHighlighter.computeEndState(line1, 0);

        String line2Str = "  0% { opacity: 0; }";
        List<HighlightToken> tokens = cssHighlighter.tokenizeLine(line2Str, 1, state1);

        boolean foundZeroPercentSelector = false;
        boolean foundOpacityProperty = false;

        for (HighlightToken t : tokens) {
            String text = line2Str.substring(t.startCol, t.endCol);
            if ("0%".equals(text) && t.color == C_SELECTOR) {
                foundZeroPercentSelector = true;
            } else if ("opacity".equals(text) && t.color == C_PROPERTY) {
                foundOpacityProperty = true;
            }
        }

        assertTrue("0% inside @keyframes should be highlighted as selector", foundZeroPercentSelector);
        assertTrue("opacity should be highlighted as property", foundOpacityProperty);
    }
}
