package com.cocode.vcode.ide.core.quickfix;

import com.cocode.vcode.ide.core.diagnostic.ProblemCode;
import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuickFixRegistryTest {

    private final File mockFile = new File("test.html");

    @Test
    public void testHasFixesForAllProblemCodes() {
        // Enforce that EVERY single problem code defined in the enum has quick fix support
        for (ProblemCode code : ProblemCode.values()) {
            Problem p = new Problem(mockFile, 1, 1, 1, "Diagnostic message for " + code.name(), Problem.Severity.WARNING, code);
            assertTrue("Every ProblemCode must have registered quick fixes: " + code.name(),
                    QuickFixRegistry.hasFixes(p));
        }
    }

    @Test
    public void testHasFixesForCommonProblemCodes() {
        Problem pAlt = new Problem(mockFile, 1, 1, 1, "Element 'img' is missing 'alt' attribute", Problem.Severity.WARNING, ProblemCode.HTML_MISSING_ALT);
        assertTrue(QuickFixRegistry.hasFixes(pAlt));

        Problem pBtn = new Problem(mockFile, 1, 1, 1, "Element '<button>' is missing 'type' attribute", Problem.Severity.WARNING, ProblemCode.HTML_BUTTON_MISSING_TYPE);
        assertTrue(QuickFixRegistry.hasFixes(pBtn));

        Problem pTitle = new Problem(mockFile, 1, 1, 1, "Empty '<title>' element", Problem.Severity.WARNING, ProblemCode.HTML_EMPTY_TITLE);
        assertTrue(QuickFixRegistry.hasFixes(pTitle));

        Problem pConst = new Problem(mockFile, 1, 1, 1, "Cannot reassign 'const' variable 'x'", Problem.Severity.ERROR, ProblemCode.JS_CONST_REASSIGNMENT);
        assertTrue(QuickFixRegistry.hasFixes(pConst));

        Problem pComment = new Problem(mockFile, 1, 1, 1, "Comments are not permitted in standard JSON", Problem.Severity.ERROR, ProblemCode.JSON_COMMENTS_DISALLOWED);
        assertTrue(QuickFixRegistry.hasFixes(pComment));

        Problem pSemi = new Problem(mockFile, 1, 1, 1, "Missing semicolon", Problem.Severity.WARNING, ProblemCode.JS_MISSING_SEMICOLON);
        assertTrue(QuickFixRegistry.hasFixes(pSemi));

        Problem pUnused = new Problem(mockFile, 1, 1, 1, "Variable 'unusedVar' is defined but never used", Problem.Severity.WARNING, ProblemCode.JS_UNUSED_VARIABLE);
        assertTrue(QuickFixRegistry.hasFixes(pUnused));

        Problem pSyntax = new Problem(mockFile, 1, 1, 1, "Generic syntax error", Problem.Severity.ERROR, ProblemCode.SYNTAX_ERROR);
        assertTrue("Syntax errors now have quick fixes", QuickFixRegistry.hasFixes(pSyntax));

        Problem pBracket = new Problem(mockFile, 1, 1, 1, "Unclosed brace '{'", Problem.Severity.ERROR, ProblemCode.BRACKET_MISMATCH);
        assertTrue("Bracket mismatches have quick fixes", QuickFixRegistry.hasFixes(pBracket));
    }

    @Test
    public void testNullProblemHandled() {
        assertFalse(QuickFixRegistry.hasFixes(null));
    }
}
