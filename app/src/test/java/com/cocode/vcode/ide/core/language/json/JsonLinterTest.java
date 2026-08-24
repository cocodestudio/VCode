package com.cocode.vcode.ide.core.language.json;

import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JsonLinterTest {

    private final File mockFile = new File("test.json");

    @Test
    public void testValidJson() {
        String json = "{\n  \"key\": \"value\",\n  \"number\": 123\n}";
        List<Problem> problems = JsonLinter.analyze(mockFile, json);
        assertTrue("Valid JSON should have no problems", problems.isEmpty());
    }

    @Test
    public void testMissingQuote() {
        String json = "{ \"key: \"value\" }"; // missing closing quote on key
        List<Problem> problems = JsonLinter.analyze(mockFile, json);
        assertFalse("Invalid JSON should have problems", problems.isEmpty());
        
        // Exact message depends on JsonValidator implementation, 
        // but it should return an ERROR severity problem.
        boolean hasError = false;
        for (Problem p : problems) {
            if (p.getSeverity() == Problem.Severity.ERROR) {
                hasError = true;
            }
        }
        assertTrue("Should report a syntax error", hasError);
    }
}
