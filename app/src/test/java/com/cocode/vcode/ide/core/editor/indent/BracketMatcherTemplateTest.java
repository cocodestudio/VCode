package com.cocode.vcode.ide.core.editor.indent;

import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class BracketMatcherTemplateTest {

    @Test
    public void testTemplateExpressionBracketsAreNotMasked() {
        String codeWithInterpolation = "`hello ${name}`";
        int[] rainbowColors = new int[]{1, 2, 3};
        List<HighlightToken> tokens = new ArrayList<>();
        BracketMatcher.applyRainbowBrackets(tokens, codeWithInterpolation, rainbowColors, 0);

        // ${ has { which should receive a rainbow bracket token, as should }
        assertFalse("Template expression braces should receive rainbow bracket tokens", tokens.isEmpty());

        String plainTemplateString = "`hello {name}`";
        List<HighlightToken> tokensPlain = new ArrayList<>();
        BracketMatcher.applyRainbowBrackets(tokensPlain, plainTemplateString, rainbowColors, 0);

        // { inside a normal string or template string without $ must be masked
        assertTrue("Braces inside plain template string must not receive rainbow tokens", tokensPlain.isEmpty());
    }

    @Test
    public void testComputeBracketDepthInsideTemplate() {
        String code = "`hello ${ (a + b) }`";
        int depth = BracketMatcher.computeBracketDepth(code, 0);
        assertEquals("Depth should return to initial depth after expression closes", 0, depth);

        String unclosedCode = "`hello ${ (a + b";
        int unclosedDepth = BracketMatcher.computeBracketDepth(unclosedCode, 0);
        assertTrue("Unclosed brackets inside ${ should be counted", unclosedDepth >= 1);
    }

    @Test
    public void testMultilineTemplateLiteralRainbowBracketsWithStartState() {
        int[] rainbowColors = new int[]{10, 20, 30};
        // startState with mode = 4 (STATE_TEMPLATE_LITERAL), templateDepth = 1, braceDepth = 0
        int templateLiteralState = 4 | (1 << 3);

        // Line 2 starts inside template literal and contains expression: "  ${foo}  "
        String line2Expr = "  ${foo}  ";
        List<HighlightToken> tokens2 = new ArrayList<>();
        BracketMatcher.applyRainbowBrackets(tokens2, line2Expr, rainbowColors, 0, templateLiteralState);

        assertEquals("Should have 2 rainbow tokens for { and }", 2, tokens2.size());
        assertEquals("Opening brace { should be at index 3", 3, tokens2.get(0).startCol);
        assertEquals("Closing brace } should be at index 7", 7, tokens2.get(1).startCol);

        // Line with plain text in multiline template literal: "  some (text with parens)  "
        String linePlain = "  some (text with parens)  ";
        List<HighlightToken> tokensPlain = new ArrayList<>();
        BracketMatcher.applyRainbowBrackets(tokensPlain, linePlain, rainbowColors, 0, templateLiteralState);
        assertTrue("Parens inside multiline template literal text should be masked", tokensPlain.isEmpty());
    }

    @Test
    public void testMultipleInterpolationsOnSingleLine() {
        String code = "`hello ${a} and ${b}`";
        int[] rainbowColors = new int[]{10, 20, 30};
        List<HighlightToken> tokens = new ArrayList<>();
        BracketMatcher.applyRainbowBrackets(tokens, code, rainbowColors, 0);

        assertEquals("Should have 4 rainbow bracket tokens for 2 sets of braces", 4, tokens.size());
        // ${a} braces
        assertEquals(8, tokens.get(0).startCol); // { of ${a}
        assertEquals(10, tokens.get(1).startCol); // } of ${a}
        // ${b} braces
        assertEquals(17, tokens.get(2).startCol); // { of ${b}
        assertEquals(19, tokens.get(3).startCol); // } of ${b}
    }
}
