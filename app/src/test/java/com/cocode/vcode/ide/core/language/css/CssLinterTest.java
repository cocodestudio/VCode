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

    @Test
    public void testBorderShorthandValues() {
        String css = "div {\n" +
                "  border: 1px solid rgba(255, 255, 255, 0.03);\n" +
                "  border-top: 1px solid rgba(255, 255, 255, 0.05);\n" +
                "  border-bottom: 1px solid rgba(255, 255, 255, 0.05);\n" +
                "  border: 1px solid var(--accent);\n" +
                "  border: solid 2px red;\n" +
                "  border: #fff 1px dashed;\n" +
                "  border: none;\n" +
                "}";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        for (Problem p : problems) {
            assertFalse("Valid border shorthand should not produce invalid color error: " + p.getMessage(),
                    p.getMessage().contains("Invalid color"));
            assertFalse("Valid border shorthand should not produce invalid border error: " + p.getMessage(),
                    p.getMessage().contains("Invalid border"));
        }
    }

    @Test
    public void testRgbaInBorderColor() {
        String css = "div {\n" +
                "  border-color: rgba(212, 175, 55, 0.2);\n" +
                "  border-color: rgba(255, 255, 255, 0.3);\n" +
                "}";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        for (Problem p : problems) {
            assertFalse("rgba in border-color should be valid: " + p.getMessage(),
                    p.getMessage().contains("Invalid color"));
        }
    }

    @Test
    public void testBoxShadowAndBackgroundNotFlaggedAsColor() {
        String css = "div {\n" +
                "  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);\n" +
                "  box-shadow: 0 10px 20px rgba(212, 175, 55, 0.15);\n" +
                "  background: radial-gradient(circle, rgba(212, 175, 55, 0.08) 0%, rgba(0, 0, 0, 0) 70%);\n" +
                "}";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        for (Problem p : problems) {
            assertFalse("box-shadow / complex background should not be flagged as invalid color: " + p.getMessage(),
                    p.getMessage().contains("Invalid color"));
        }
    }

    @Test
    public void testDeclaredCustomPropertyNoFallbackWarning() {
        String css = ":root {\n" +
                "  --accent: #d4af37;\n" +
                "}\n" +
                "div {\n" +
                "  color: var(--accent);\n" +
                "  border: 1px solid var(--accent);\n" +
                "}";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        for (Problem p : problems) {
            assertFalse("Declared custom property should not warn about missing fallback: " + p.getMessage(),
                    p.getMessage().contains("without a fallback"));
        }
    }

    @Test
    public void testZeroPxInsideMathFunctionNotFlagged() {
        String css = "div {\n" +
                "  width: calc(100% - 0px);\n" +
                "  margin-top: max(0px, 10px);\n" +
                "}";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        for (Problem p : problems) {
            assertFalse("0px inside calc/max should not warn about unnecessary units: " + p.getMessage(),
                    p.getMessage().contains("units are unnecessary on zero values"));
        }
    }

    @Test
    public void testSelectorCombinatorsDoNotIncreaseDepth() {
        // 3 compound selectors with 2 combinators -> 5 tokens total if combinators counted,
        // but depth is 3 -> should not be flagged.
        String css = "div > span + p { color: red; }";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        boolean specificWarn = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Overly specific selector")) specificWarn = true;
        }
        assertFalse("Selector with combinators (div > span + p) should not warn depth > 3 when combinators ignored",
                specificWarn);

        // Also test without whitespace around combinator: div>span+p
        String cssNoSpace = "div>span+p { color: red; }";
        List<Problem> problems2 = CssLinter.analyze(mockFile, cssNoSpace);
        boolean specificWarn2 = false;
        for (Problem p : problems2) {
            if (p.getMessage().contains("Overly specific selector")) specificWarn2 = true;
        }
        assertFalse("Selector with tight combinators (div>span+p) should not warn depth > 3",
                specificWarn2);

        // 4 compound selectors -> depth is 4 -> should be flagged
        String cssOver = "div > span + p ~ a { color: red; }";
        List<Problem> problemsOver = CssLinter.analyze(mockFile, cssOver);
        boolean specificWarnOver = false;
        for (Problem p : problemsOver) {
            if (p.getMessage().contains("Overly specific selector")) specificWarnOver = true;
        }
        assertTrue("Selector with 4 compound selectors (div > span + p ~ a) should be flagged",
                specificWarnOver);
    }
}

