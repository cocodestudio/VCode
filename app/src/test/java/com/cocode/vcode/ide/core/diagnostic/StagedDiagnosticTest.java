package com.cocode.vcode.ide.core.diagnostic;

import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class StagedDiagnosticTest {

    private final File testFile = new File("test.html");

    @Test
    public void testSyntacticLintFastPath() {
        String jsonWithBraceMismatch = "{ \"a\": [1, 2 }";
        List<Problem> syntacticProblems = DiagnosticEngine.runSyntacticLint(new File("test.json"), jsonWithBraceMismatch, FileType.JSON);
        assertNotNull(syntacticProblems);
        assertFalse(syntacticProblems.isEmpty());
        assertEquals(ProblemCode.BRACKET_MISMATCH, syntacticProblems.get(0).getProblemCode());
    }

    @Test
    public void testSemanticLintHtmlMissingAlt() {
        String html = "<html><body><img src=\"pic.png\"></body></html>";
        List<Problem> problems = DiagnosticEngine.runSemanticLint(testFile, html, FileType.HTML, null);
        assertNotNull(problems);
        boolean foundMissingAlt = false;
        for (Problem p : problems) {
            if (p.getProblemCode() == ProblemCode.HTML_MISSING_ALT) {
                foundMissingAlt = true;
                break;
            }
        }
        assertTrue("Expected HTML_MISSING_ALT problem code", foundMissingAlt);
    }

    @Test
    public void testSyntacticLintJsonDisallowedComment() {
        String json = "{\n  // This is a comment\n  \"key\": \"value\"\n}";
        List<Problem> problems = DiagnosticEngine.runSyntacticLint(new File("data.json"), json, FileType.JSON);
        assertNotNull(problems);
        boolean foundCommentProblem = false;
        for (Problem p : problems) {
            if (p.getProblemCode() == ProblemCode.JSON_COMMENTS_DISALLOWED) {
                foundCommentProblem = true;
                break;
            }
        }
        assertTrue("Expected JSON_COMMENTS_DISALLOWED problem code", foundCommentProblem);
    }
}
