package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.diagnostic.util.ViewportHighlighter;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for the J.2 AST second-pass role recoloring.
 */
public class JsRoleRecolorerTest {

    private static final int DECL_COLOR_VAR = 0xA1;
    private static final int DECL_COLOR_CLASS = 0xA2;

    @Test
    public void classDeclarationIsMarkedAsDeclaration() {
        String source = "class Foo {}";
        TokenStream stream = JsLexer.tokenize(source);
        JsSyntaxTree tree = JsParser.parseTopLevel(source, stream);
        int[] ranges = JsRoleRecolorer.buildDeclarationRanges(tree, source, stream);
        assertTrue("Foo at position 6 should be a declaration start",
                JsRoleRecolorer.isDeclarationStart(ranges, 6));
    }

    @Test
    public void variableDeclarationIsMarkedAsDeclaration() {
        String source = "let x = 1;";
        TokenStream stream = JsLexer.tokenize(source);
        JsSyntaxTree tree = JsParser.parseTopLevel(source, stream);
        int[] ranges = JsRoleRecolorer.buildDeclarationRanges(tree, source, stream);
        int xPos = source.indexOf("x");
        assertTrue("variable 'x' at position " + xPos + " should be a declaration",
                JsRoleRecolorer.isDeclarationStart(ranges, xPos));
    }

    @Test
    public void variableUsageIsNotMarkedAsDeclaration() {
        String source = "let x = 1; x = 2;";
        TokenStream stream = JsLexer.tokenize(source);
        JsSyntaxTree tree = JsParser.parseTopLevel(source, stream);
        int[] ranges = JsRoleRecolorer.buildDeclarationRanges(tree, source, stream);
        int secondX = source.indexOf("x", source.indexOf("x") + 1);
        assertEquals("second 'x' at position " + secondX + " must not be a declaration",
                false, JsRoleRecolorer.isDeclarationStart(ranges, secondX));
    }

    @Test
    public void classAndVariableOfSameTextGetDifferentOverlayColours() {
        String source = "class Foo {}\nlet Foo = 1;\n";
        TokenStream stream = JsLexer.tokenize(source);
        JsSyntaxTree tree = JsParser.parseTopLevel(source, stream);
        int[] ranges = JsRoleRecolorer.buildDeclarationRanges(tree, source, stream);

        int classPos = 6;
        List<ViewportHighlighter.ViewportSpan> classOverlay = JsRoleRecolorer.recolor(
                stream.types, stream.tokenStart, stream.length,
                classPos, classPos + 3, ranges,
                DECL_COLOR_CLASS);
        assertEquals(1, classOverlay.size());
        assertEquals(DECL_COLOR_CLASS, classOverlay.get(0).color);

        int varPos = source.indexOf("Foo", "class Foo {}\nlet ".length());
        List<ViewportHighlighter.ViewportSpan> varOverlay = JsRoleRecolorer.recolor(
                stream.types, stream.tokenStart, stream.length,
                varPos, varPos + 3, ranges,
                DECL_COLOR_VAR);
        assertEquals(1, varOverlay.size());
        assertEquals(DECL_COLOR_VAR, varOverlay.get(0).color);

        assertTrue("class and variable of same name must get different colours",
                classOverlay.get(0).color != varOverlay.get(0).color);
    }

    @Test
    public void declarationRangesHandleNullInputs() {
        int[] ranges = JsRoleRecolorer.buildDeclarationRanges(null, null, null);
        assertNotNull(ranges);
        assertEquals(0, ranges.length);
    }

    @Test
    public void mergeOverlaysReplacesBaseSpans() {
        ViewportHighlighter.ViewportSpan base1 =
                new ViewportHighlighter.ViewportSpan(0, 10, 100);
        ViewportHighlighter.ViewportSpan overlay1 =
                new ViewportHighlighter.ViewportSpan(3, 7, 200);
        List<ViewportHighlighter.ViewportSpan> merged = JsRoleRecolorer.mergeOverlays(
                java.util.Arrays.asList(base1),
                java.util.Arrays.asList(overlay1));
        assertEquals(3, merged.size());
        assertEquals(0, merged.get(0).startOffset);
        assertEquals(3, merged.get(0).endOffset);
        assertEquals(100, merged.get(0).color);
        assertEquals(3, merged.get(1).startOffset);
        assertEquals(7, merged.get(1).endOffset);
        assertEquals(200, merged.get(1).color);
        assertEquals(7, merged.get(2).startOffset);
        assertEquals(10, merged.get(2).endOffset);
        assertEquals(100, merged.get(2).color);
    }

    @Test
    public void mergeOverlaysWithEmptyOverlayReturnsBase() {
        ViewportHighlighter.ViewportSpan base1 =
                new ViewportHighlighter.ViewportSpan(0, 5, 100);
        List<ViewportHighlighter.ViewportSpan> merged = JsRoleRecolorer.mergeOverlays(
                java.util.Arrays.asList(base1),
                new java.util.ArrayList<>());
        assertEquals(1, merged.size());
        assertEquals(100, merged.get(0).color);
    }

    @Test
    public void mergeOverlaysWithEmptyBaseReturnsOverlay() {
        ViewportHighlighter.ViewportSpan overlay1 =
                new ViewportHighlighter.ViewportSpan(0, 5, 200);
        List<ViewportHighlighter.ViewportSpan> merged = JsRoleRecolorer.mergeOverlays(
                new java.util.ArrayList<>(),
                java.util.Arrays.asList(overlay1));
        assertEquals(1, merged.size());
        assertEquals(200, merged.get(0).color);
    }

    @Test
    public void declarationOutsideVisibleRangeIsNotEmitted() {
        String source = "class Foo {}\n" + repeat("var x;\n", 100);
        TokenStream stream = JsLexer.tokenize(source);
        JsSyntaxTree tree = JsParser.parseTopLevel(source, stream);
        int[] ranges = JsRoleRecolorer.buildDeclarationRanges(tree, source, stream);
        int start = source.length() - 50;
        int end = source.length();
        List<ViewportHighlighter.ViewportSpan> overlay = JsRoleRecolorer.recolor(
                stream.types, stream.tokenStart, stream.length,
                start, end, ranges, 999);
        for (ViewportHighlighter.ViewportSpan s : overlay) {
            assertTrue("overlay must be inside the visible range",
                    s.startOffset >= start && s.endOffset <= end);
        }
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }
}
