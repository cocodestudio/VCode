package com.cocode.vcode.ide.core.editor.indent;

import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import com.cocode.vcode.ide.core.editor.text.ContentLine;
import com.cocode.vcode.ide.core.language.css.CssSyntaxHighlighter;
import com.cocode.vcode.ide.core.model.FileType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class BracketMatcherCssTest {

    private final int[] rainbowColors = new int[]{0xFFE06C75, 0xFFE5C07B, 0xFF98C379, 0xFF61AFEF, 0xFFC678DD};

    @Test
    public void testCssMultilineBracketsHaveMatchingColors() {
        CssSyntaxHighlighter cssHighlighter = CssSyntaxHighlighter.forTest();

        // Line 1: .card {
        String line1 = ".card {";
        ContentLine cl1 = new ContentLine(line1);
        int state1 = cssHighlighter.computeEndState(cl1, 0);
        int depth1 = BracketMatcher.computeBracketDepth(cl1, 0, 0, FileType.CSS);
        assertEquals("Depth after opening brace should be 1", 1, depth1);

        List<HighlightToken> tokens1 = new ArrayList<>();
        BracketMatcher.applyRainbowBrackets(tokens1, line1, rainbowColors, 0, 0, 0, FileType.CSS);
        assertEquals("Line 1 should have 1 rainbow token for {", 1, tokens1.size());
        int openBraceColor = tokens1.get(0).color;

        // Line 2:   color: red;
        String line2 = "  color: red;";
        ContentLine cl2 = new ContentLine(line2);
        int state2 = cssHighlighter.computeEndState(cl2, state1);
        int depth2 = BracketMatcher.computeBracketDepth(cl2, depth1, state1, FileType.CSS);
        assertEquals("Depth inside body should remain 1", 1, depth2);

        // Line 3: }
        String line3 = "}";
        ContentLine cl3 = new ContentLine(line3);
        int state3 = cssHighlighter.computeEndState(cl3, state2);
        int depth3 = BracketMatcher.computeBracketDepth(cl3, depth2, state2, FileType.CSS);
        assertEquals("Depth after closing brace should return to 0", 0, depth3);

        List<HighlightToken> tokens3 = new ArrayList<>();
        BracketMatcher.applyRainbowBrackets(tokens3, line3, rainbowColors, depth2, state2, 2, FileType.CSS);
        assertEquals("Line 3 should have 1 rainbow token for }", 1, tokens3.size());
        int closeBraceColor = tokens3.get(0).color;

        assertEquals("Opening and closing bracket colors MUST match", openBraceColor, closeBraceColor);
    }

    @Test
    public void testCssNestedRulesAndMediaQueries() {
        CssSyntaxHighlighter cssHighlighter = CssSyntaxHighlighter.forTest();

        // Line 0: @media (min-width: 768px) {
        String line0 = "@media (min-width: 768px) {";
        ContentLine cl0 = new ContentLine(line0);
        int state0 = cssHighlighter.computeEndState(cl0, 0);
        int depth0 = BracketMatcher.computeBracketDepth(cl0, 0, 0, FileType.CSS);
        assertEquals(1, depth0);

        List<HighlightToken> tokens0 = new ArrayList<>();
        BracketMatcher.applyRainbowBrackets(tokens0, line0, rainbowColors, 0, 0, 0, FileType.CSS);
        assertEquals("Line 0 should have 3 tokens: (, ), {", 3, tokens0.size());
        assertEquals("Parens on media query should match", tokens0.get(0).color, tokens0.get(1).color);
        int outerOpenBraceColor = tokens0.get(2).color;

        // Line 1:   .card {
        String line1 = "  .card {";
        ContentLine cl1 = new ContentLine(line1);
        int state1 = cssHighlighter.computeEndState(cl1, state0);
        int depth1 = BracketMatcher.computeBracketDepth(cl1, depth0, state0, FileType.CSS);
        assertEquals(2, depth1);

        List<HighlightToken> tokens1 = new ArrayList<>();
        BracketMatcher.applyRainbowBrackets(tokens1, line1, rainbowColors, depth0, state0, 1, FileType.CSS);
        assertEquals(1, tokens1.size());
        int innerOpenBraceColor = tokens1.get(0).color;
        assertNotEquals("Inner brace should have different color from outer brace", outerOpenBraceColor, innerOpenBraceColor);

        // Line 2:   }
        String line2 = "  }";
        ContentLine cl2 = new ContentLine(line2);
        int state2 = cssHighlighter.computeEndState(cl2, state1);
        int depth2 = BracketMatcher.computeBracketDepth(cl2, depth1, state1, FileType.CSS);
        assertEquals(1, depth2);

        List<HighlightToken> tokens2 = new ArrayList<>();
        BracketMatcher.applyRainbowBrackets(tokens2, line2, rainbowColors, depth1, state1, 2, FileType.CSS);
        assertEquals(1, tokens2.size());
        assertEquals("Inner closing brace must match inner opening brace", innerOpenBraceColor, tokens2.get(0).color);

        // Line 3: }
        String line3 = "}";
        ContentLine cl3 = new ContentLine(line3);
        int depth3 = BracketMatcher.computeBracketDepth(cl3, depth2, state2, FileType.CSS);
        assertEquals(0, depth3);

        List<HighlightToken> tokens3 = new ArrayList<>();
        BracketMatcher.applyRainbowBrackets(tokens3, line3, rainbowColors, depth2, state2, 3, FileType.CSS);
        assertEquals(1, tokens3.size());
        assertEquals("Outer closing brace must match outer opening brace", outerOpenBraceColor, tokens3.get(0).color);
    }

    @Test
    public void testCssBracketsInsideCommentsAndStringsAreMasked() {
        CssSyntaxHighlighter cssHighlighter = CssSyntaxHighlighter.forTest();

        // Line with comments containing brackets
        String commentLine = "/* { ( brackets inside comment ) } */";
        ContentLine cl = new ContentLine(commentLine);
        int depth = BracketMatcher.computeBracketDepth(cl, 0, 0, FileType.CSS);
        assertEquals("Brackets inside CSS comment must not affect depth", 0, depth);

        List<HighlightToken> tokens = new ArrayList<>();
        BracketMatcher.applyRainbowBrackets(tokens, commentLine, rainbowColors, 0, 0, 0, FileType.CSS);
        assertTrue("Brackets inside CSS comment must not get rainbow tokens", tokens.isEmpty());

        // Line with string containing brackets
        String stringLine = "  content: \"{ ( brackets inside string ) }\";";
        ContentLine clString = new ContentLine(stringLine);
        int depthString = BracketMatcher.computeBracketDepth(clString, 0, 0, FileType.CSS);
        assertEquals("Brackets inside CSS string must not affect depth", 0, depthString);

        List<HighlightToken> tokensString = new ArrayList<>();
        BracketMatcher.applyRainbowBrackets(tokensString, stringLine, rainbowColors, 0, 0, 0, FileType.CSS);
        assertTrue("Brackets inside CSS string must not get rainbow tokens", tokensString.isEmpty());
    }

    @Test
    public void testCssFunctionParenthesesInsideRule() {
        CssSyntaxHighlighter cssHighlighter = CssSyntaxHighlighter.forTest();

        // Line 1: .card {
        ContentLine cl1 = new ContentLine(".card {");
        int state1 = cssHighlighter.computeEndState(cl1, 0);
        int depth1 = BracketMatcher.computeBracketDepth(cl1, 0, 0, FileType.CSS);

        // Line 2:   background: rgba(0, 0, 0, 0.5);
        String line2 = "  background: rgba(0, 0, 0, 0.5);";
        ContentLine cl2 = new ContentLine(line2);
        List<HighlightToken> tokens2 = new ArrayList<>();
        BracketMatcher.applyRainbowBrackets(tokens2, line2, rainbowColors, depth1, state1, 1, FileType.CSS);
        assertEquals("Should have 2 tokens for ( and ) of rgba", 2, tokens2.size());
        assertEquals("rgba ( and ) should have matching colors", tokens2.get(0).color, tokens2.get(1).color);

        int depth2 = BracketMatcher.computeBracketDepth(cl2, depth1, state1, FileType.CSS);
        assertEquals("rgba () should open and close, keeping rule depth at 1", 1, depth2);
    }
}
