package com.cocode.vcode.ide.core.language.css;

import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CssLinterTest {

    private final File mockFile = new File("test.css");

    @Test
    public void testUnclosedBrace() {
        String css = "body { color: red;";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        
        boolean foundUnclosed = false;
        for (Problem p : problems) {
            if (p.getMessage().toLowerCase().contains("unclosed") || p.getMessage().contains("{")) {
                foundUnclosed = true;
                break;
            }
        }
        assertTrue("Should report unclosed brace", foundUnclosed);
    }

    @Test
    public void testDuplicateProperties() {
        String css = "body { color: red; color: blue; }";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        
        boolean foundDuplicate = false;
        for (Problem p : problems) {
            if (p.getMessage().toLowerCase().contains("duplicate")) {
                foundDuplicate = true;
                break;
            }
        }
        assertTrue("Should report duplicate property", foundDuplicate);
    }

    @Test
    public void testMissingColonInDeclaration() {
        String css = "body { color red; }";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        
        boolean foundMissingColon = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Missing ':'") && p.getSeverity() == Problem.Severity.ERROR) {
                foundMissingColon = true;
                break;
            }
        }
        assertTrue("Should report missing colon error", foundMissingColon);
    }

    @Test
    public void testUnknownProperty() {
        String css = "body { fakeprop: 100px; }";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        
        boolean foundUnknown = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Unknown CSS property") && p.getSeverity() == Problem.Severity.WARNING) {
                foundUnknown = true;
                break;
            }
        }
        assertTrue("Should report unknown property warning", foundUnknown);
    }

    @Test
    public void testDeprecatedProperty() {
        String css = "body { zoom: 1.5; }";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        
        boolean foundDeprecated = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("deprecated") && p.getSeverity() == Problem.Severity.WARNING) {
                foundDeprecated = true;
                break;
            }
        }
        assertTrue("Should report deprecated property warning", foundDeprecated);
    }

    @Test
    public void testInvalidColorValue() {
        String css = "body { color: notacolor; }";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        
        boolean foundInvalidColor = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Invalid color value") && p.getSeverity() == Problem.Severity.ERROR) {
                foundInvalidColor = true;
                break;
            }
        }
        assertTrue("Should report invalid color value", foundInvalidColor);
    }

    @Test
    public void testEmptyRule() {
        String css = ".empty-class { }";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        
        boolean foundEmpty = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Empty rule") && p.getSeverity() == Problem.Severity.WARNING) {
                foundEmpty = true;
                break;
            }
        }
        assertTrue("Should report empty rule warning", foundEmpty);
    }

    @Test
    public void testImportAfterFirstRule() {
        String css = ".box { color: red; }\n@import url('other.css');";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        
        boolean foundImportError = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("@import") && p.getSeverity() == Problem.Severity.ERROR) {
                foundImportError = true;
                break;
            }
        }
        assertTrue("Should report @import after first rule error", foundImportError);
    }

    @Test
    public void testShortenableHexColor() {
        String css = "body { background-color: #ffffff; }";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        
        boolean foundShortenable = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("can be shortened to '#fff'") && p.getSeverity() == Problem.Severity.INFO) {
                foundShortenable = true;
                break;
            }
        }
        assertTrue("Should report shortenable hex color info", foundShortenable);
    }

    @Test
    public void testUnusedCssVariable() {
        String css = ":root { --theme-color: blue; }\nbody { color: red; }";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        
        boolean foundUnused = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("is declared but never used") && p.getSeverity() == Problem.Severity.INFO) {
                foundUnused = true;
                break;
            }
        }
        assertTrue("Should report unused CSS variable info", foundUnused);
    }

    @Test
    public void testSpecificityCommaSeparated() {
        // Each group <= 3 tokens -> should not be flagged
        String css = ".a .b, .c .d, .e .f { color: red; }";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        boolean specificWarn = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Overly specific selector")) specificWarn = true;
        }
        assertFalse("Comma-separated groups with <=3 tokens should not be overly specific", specificWarn);

        // One group with 4 tokens -> should be flagged
        String cssOver = ".a .b, .c .d .e .f { color: red; }";
        List<Problem> problemsOver = CssLinter.analyze(mockFile, cssOver);
        boolean specificWarnOver = false;
        for (Problem p : problemsOver) {
            if (p.getMessage().contains("Overly specific selector")) specificWarnOver = true;
        }
        assertTrue("Group with 4 tokens should be flagged as overly specific", specificWarnOver);
    }

    @Test
    public void testNestedVarFallback() {
        String cssWithFallback = "body { color: var(--main, var(--fallback, red)); }";
        List<Problem> problems = CssLinter.analyze(mockFile, cssWithFallback);
        boolean varWarn = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("without a fallback")) varWarn = true;
        }
        assertFalse("Nested var() with fallback should not warn", varWarn);

        String cssNoFallback = "body { color: var(--main); }";
        List<Problem> problems2 = CssLinter.analyze(mockFile, cssNoFallback);
        boolean varWarn2 = false;
        for (Problem p : problems2) {
            if (p.getMessage().contains("without a fallback")) varWarn2 = true;
        }
        assertTrue("var() without fallback should warn", varWarn2);
    }

    @Test
    public void testIdSelectorInsideKeyframesNotWarned() {
        String css = "@keyframes slide { #frame { opacity: 1; } }";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        boolean idWarn = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Avoid using ID selectors")) idWarn = true;
        }
        assertFalse("ID selector inside @keyframes should not warn", idWarn);
    }

    @Test
    public void testLonghandAfterShorthandNotFlagged() {
        String css = "div { border: 1px solid #ccc; border-bottom: 2px solid red; }";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        for (Problem p : problems) {
            assertFalse("Longhand following shorthand should be allowed as specialization: " + p.getMessage(),
                    p.getMessage().contains("overridden by shorthand"));
        }
    }

    @Test
    public void testZeroWithUnitIsInfo() {
        String css = "p { margin: 0px; }";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        boolean foundZero = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("units are unnecessary on zero values")) {
                foundZero = true;
                assertEquals(Problem.Severity.INFO, p.getSeverity());
                break;
            }
        }
        assertTrue("0px should be reported as INFO", foundZero);
    }

    @Test
    public void testMultiColorValues() {
        String css = "div { border-color: red green blue yellow; }";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        for (Problem p : problems) {
            assertFalse("Multi-color space-separated border-color should be valid: " + p.getMessage(),
                    p.getMessage().contains("Invalid color"));
        }
    }
}
