package com.cocode.vcode.ide.core.language.json;

import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JsonLinterTest {

    @Test
    public void testValidJsonHasNoProblems() {
        String source = "{ \"a\": 1, \"b\": 2 }";
        List<Problem> problems = JsonLinter.analyze(new File("test.json"), source);
        assertEquals(0, problems.size());
    }

    @Test
    public void testDuplicateKeys() {
        String source = "{ \"a\": 1, \"b\": 2, \"a\": 3 }";
        List<Problem> problems = JsonLinter.analyze(new File("test.json"), source);
        
        assertEquals(1, problems.size());
        Problem p = problems.get(0);
        assertEquals(Problem.Severity.WARNING, p.getSeverity());
        assertTrue(p.getMessage().contains("Duplicate"));
        assertTrue(p.getMessage().contains("\"a\""));
    }

    @Test
    public void testSyntaxErrors() {
        String source = "{ \"a\" 1 }";
        List<Problem> problems = JsonLinter.analyze(new File("test.json"), source);
        
        assertEquals(1, problems.size());
        Problem p = problems.get(0);
        assertEquals(Problem.Severity.ERROR, p.getSeverity());
        assertEquals("Missing colon", p.getMessage());
    }
}
