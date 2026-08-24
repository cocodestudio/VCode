package com.cocode.vcode.ide.core.language.css;

import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CssLinterTest {

    private final File mockFile = new File("test.css");

    @Test
    public void testUnclosedBrace() {
        String css = "body { color: red;";
        List<Problem> problems = CssLinter.analyze(mockFile, css);
        
        // Since there is no closing brace, it should report unclosed brace
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
}
