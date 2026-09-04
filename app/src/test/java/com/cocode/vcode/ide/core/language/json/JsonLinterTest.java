package com.cocode.vcode.ide.core.language.json;

import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JsonLinterTest {

    private final File mockFile = new File("test.json");

    @Test
    public void testValidJsonHasNoProblems() {
        String source = "{ \"a\": 1, \"b\": 2 }";
        List<Problem> problems = JsonLinter.analyze(mockFile, source);
        assertEquals(0, problems.size());
    }

    @Test
    public void testDuplicateKeys() {
        String source = "{ \"a\": 1, \"b\": 2, \"a\": 3 }";
        List<Problem> problems = JsonLinter.analyze(mockFile, source);
        
        assertEquals(1, problems.size());
        Problem p = problems.get(0);
        assertEquals(Problem.Severity.WARNING, p.getSeverity());
        assertTrue(p.getMessage().contains("Duplicate"));
        assertTrue(p.getMessage().contains("\"a\""));
    }

    @Test
    public void testSyntaxErrors() {
        String source = "{ \"a\" 1 }";
        List<Problem> problems = JsonLinter.analyze(mockFile, source);
        
        assertEquals(1, problems.size());
        Problem p = problems.get(0);
        assertEquals(Problem.Severity.ERROR, p.getSeverity());
        assertEquals("Missing colon", p.getMessage());
    }

    @Test
    public void testTrailingComma() {
        String source = "{ \"a\": 1, }";
        List<Problem> problems = JsonLinter.analyze(mockFile, source);
        
        boolean foundTrailing = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Trailing comma")) {
                foundTrailing = true;
                break;
            }
        }
        assertTrue("Should report trailing comma error", foundTrailing);
    }

    @Test
    public void testMissingClosingQuote() {
        String source = "{ \"a: 1 }";
        List<Problem> problems = JsonLinter.analyze(mockFile, source);
        
        boolean foundQuoteError = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("quote") || p.getMessage().contains("colon") || p.getMessage().contains("Syntax")) {
                foundQuoteError = true;
                break;
            }
        }
        assertTrue("Should report missing quote or syntax error", foundQuoteError);
    }

    @Test
    public void testKeywordBoundaryError() {
        String source = "{ \"a\": truth }";
        List<Problem> problems = JsonLinter.analyze(mockFile, source);
        
        boolean foundError = false;
        for (Problem p : problems) {
            if (p.getSeverity() == Problem.Severity.ERROR) {
                foundError = true;
                break;
            }
        }
        assertTrue("Should report error for invalid identifier keyword", foundError);
    }
}
