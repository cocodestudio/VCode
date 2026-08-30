package com.cocode.vcode.ide.core.language.ts;

import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TsLinterTest {

    private final File mockFile = new File("test.ts");

    @Test
    public void testAnalyze() {
        // Just verify it doesn't crash and delegates properly
        String ts = "const x: number = 'hello';";

        List<Problem> problems = TsLinter.analyze(mockFile, ts);
        
        assertFalse("Linter should find problems", problems.isEmpty());
        
        boolean foundMismatch = false;

        for (Problem p : problems) {
            String msg = p.getMessage().toLowerCase();
            if (msg.contains("type mismatch")) {
                foundMismatch = true;
            }
        }

        assertTrue("Should detect type mismatch in TS", foundMismatch);
    }
}
