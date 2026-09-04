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
}
