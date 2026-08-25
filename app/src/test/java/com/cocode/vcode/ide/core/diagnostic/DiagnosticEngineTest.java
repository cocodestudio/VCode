package com.cocode.vcode.ide.core.diagnostic;

import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DiagnosticEngineTest {

    private final File mockFile = new File("test.any");

    @Test
    public void testAnalyzeHtml() {
        String html = "<div><p>text</div>";
        List<Problem> problems = DiagnosticEngine.analyze(mockFile, html, FileType.HTML);
        
        assertFalse("Should find HTML problems", problems.isEmpty());
        boolean foundHtmlProblem = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Unclosed tag '<p>'")) {
                foundHtmlProblem = true;
            }
        }
        assertTrue("Should delegate to HtmlLinter", foundHtmlProblem);
    }

    @Test
    public void testAnalyzeJson() {
        String json = "{ \"key: \"value\" }"; // Missing quote
        List<Problem> problems = DiagnosticEngine.analyze(mockFile, json, FileType.JSON);
        
        assertFalse("Should find JSON problems", problems.isEmpty());
    }

    @Test
    public void testDeduplicateAndSort() {
        List<Problem> input = new ArrayList<>();
        input.add(new Problem(mockFile, 2, 5, 1, "Duplicate", Problem.Severity.WARNING));
        input.add(new Problem(mockFile, 1, 1, 1, "First error", Problem.Severity.ERROR));
        input.add(new Problem(mockFile, 2, 5, 1, "Duplicate", Problem.Severity.WARNING)); // Duplicate
        input.add(new Problem(mockFile, 3, 1, 1, "Info msg", Problem.Severity.INFO));

        List<Problem> sorted = DiagnosticEngine.deduplicateAndSort(mockFile, input);

        assertEquals("Should remove duplicates", 3, sorted.size());
        
        // Sorting should be: ERROR, WARNING, INFO (ordinal order)
        assertEquals("ERROR", sorted.get(0).getSeverity().name());
        assertEquals("First error", sorted.get(0).getMessage());
        
        assertEquals("WARNING", sorted.get(1).getSeverity().name());
        assertEquals("Duplicate", sorted.get(1).getMessage());
        
        assertEquals("INFO", sorted.get(2).getSeverity().name());
    }
    
    @Test
    public void testMaxProblemsLimit() {
        List<Problem> input = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            input.add(new Problem(mockFile, i, 1, 1, "Error " + i, Problem.Severity.ERROR));
        }
        
        List<Problem> sorted = DiagnosticEngine.deduplicateAndSort(mockFile, input);
        
        // Max is 60 + 1 info message = 61
        assertEquals(61, sorted.size());
        assertEquals("INFO", sorted.get(60).getSeverity().name());
        assertTrue(sorted.get(60).getMessage().contains("10 more issues not shown"));
    }
}
