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
        assertTrue(QuickFixRegistry.getFixesForProblem(null).isEmpty());
    }

    @Test
    public void testUnfixableLintingMessageDoesNotHaveFixes() {
        // Linting messages for which a quick fix cannot be added must NOT show bulb icon (hasFixes == false)
        Problem pUnfixable1 = new Problem(mockFile, 1, 1, 1, "Function 'calculateTotal' has cyclomatic complexity of 15", Problem.Severity.WARNING, (ProblemCode) null);
        assertFalse("Unfixable lint message should not indicate quick fix availability",
                QuickFixRegistry.hasFixes(pUnfixable1));
        assertTrue(QuickFixRegistry.getFixesForProblem(pUnfixable1).isEmpty());

        Problem pUnfixable2 = new Problem(mockFile, 1, 1, 1, "Spelling error: 'functoin'", Problem.Severity.INFO, (ProblemCode) null);
        assertFalse("Unknown info diagnostic should not have quick fixes",
                QuickFixRegistry.hasFixes(pUnfixable2));
        assertTrue(QuickFixRegistry.getFixesForProblem(pUnfixable2).isEmpty());

        Problem pUnfixable3 = new Problem(mockFile, 1, 1, 1, "", Problem.Severity.WARNING, (ProblemCode) null);
        assertFalse("Empty diagnostic message should not have quick fixes",
                QuickFixRegistry.hasFixes(pUnfixable3));
        assertTrue(QuickFixRegistry.getFixesForProblem(pUnfixable3).isEmpty());
    }

    @Test
    public void testFixableMessageWithoutProblemCode() {
        // Messages with recognized pattern but null ProblemCode should still have fixes
        Problem pFixable = new Problem(mockFile, 1, 1, 1, "Element 'img' is missing 'alt' attribute", Problem.Severity.WARNING, (ProblemCode) null);
        assertTrue("Recognized pattern should have quick fix", QuickFixRegistry.hasFixes(pFixable));
        assertFalse(QuickFixRegistry.getFixesForProblem(pFixable).isEmpty());
    }

    @Test
    public void testCustomQuickFixDirectlyOnProblem() {
        Problem p = new Problem(mockFile, 1, 1, 1, "Custom extension lint warning", Problem.Severity.WARNING, (ProblemCode) null);
        assertFalse(QuickFixRegistry.hasFixes(p));

        p.addCustomQuickFix(new QuickFix() {
            @Override
            public String getTitle() {
                return "Apply Custom Extension Fix";
            }

            @Override
            public void apply(CodeEditorTarget editor, Problem problem) {}
        });

        assertTrue(QuickFixRegistry.hasFixes(p));
        java.util.List<QuickFix> fixes = QuickFixRegistry.getFixesForProblem(p);
        org.junit.Assert.assertEquals(1, fixes.size());
        org.junit.Assert.assertEquals("Apply Custom Extension Fix", fixes.get(0).getTitle());
    }
}
