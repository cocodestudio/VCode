package com.cocode.vcode.ide.core.language.css;

import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.List;

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
            if (p.getMessage().contains("Unknown CSS property") && p.getSeverity() == Problem.Severity.ERROR) {
                foundUnknown = true;
                break;
            }
        }
        assertTrue("Should report unknown property error", foundUnknown);
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
}
