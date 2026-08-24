package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JsLinterTest {

    private final File mockFile = new File("test.js");

    @Test
    public void testAnalyze() {
        // A snippet that triggers a few rules:
        // 1. console.log usage (CoreRules checkConsole)
        // 2. Division by zero (StyleRules checkDivisionByZero)
        // 3. typeof === undefined (StyleRules checkTypeofComparison)
        
        String js = "console.log('test');\n" +
                    "let x = 1 / 0;\n" +
                    "if (typeof y === undefined) {}";

        List<Problem> problems = JsLinter.analyze(mockFile, js);
        
        // Ensure that rules are integrated correctly and returning problems
        assertFalse("Linter should find problems", problems.isEmpty());
        
        boolean foundConsole = false;
        boolean foundDivZero = false;
        boolean foundTypeof = false;

        for (Problem p : problems) {
            String msg = p.getMessage().toLowerCase();
            if (msg.contains("console")) {
                foundConsole = true;
            }
            if (msg.contains("division by zero") || msg.contains("divide by zero")) {
                foundDivZero = true;
            }
            if (msg.contains("typeof") || msg.contains("string")) {
                foundTypeof = true;
            }
        }

        assertTrue("Should detect console statement", foundConsole);
        assertTrue("Should detect division by zero", foundDivZero);
        assertTrue("Should detect typeof undefined", foundTypeof);
    }
}
