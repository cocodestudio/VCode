package com.cocode.vcode.ide.core.diagnostic;

import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.model.Problem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DiagnosticProviderTest {

    private DiagnosticProvider customProvider;

    @Before
    public void setUp() {
        customProvider = new DiagnosticProvider() {
            @Override
            public String getId() {
                return "test-custom-linter";
            }

            @Override
            public boolean canLint(FileType fileType, String path) {
                return fileType == FileType.JAVASCRIPT;
            }

            @Override
            public List<Problem> lint(File file, String content, FileType fileType, ProjectIndex index) {
                if (content != null && content.contains("disallowedPattern")) {
                    return Collections.singletonList(
                            new Problem(file, 1, 1, 17, "disallowedPattern is forbidden", Problem.Severity.ERROR)
                    );
                }
                return Collections.emptyList();
            }
        };

        DiagnosticEngine.registerProvider(customProvider);
    }

    @After
    public void tearDown() {
        DiagnosticEngine.unregisterProvider(customProvider);
    }

    @Test
    public void testCustomDiagnosticProviderExecution() {
        File file = new File("/test/src/app.js");
        String code = "const disallowedPattern = 42;";

        List<Problem> problems = DiagnosticEngine.analyze(file, code, FileType.JAVASCRIPT);
        assertFalse(problems.isEmpty());

        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("disallowedPattern is forbidden")) {
                found = true;
                break;
            }
        }
        assertTrue("Custom diagnostic provider problem must be included in DiagnosticEngine analysis", found);
    }

    @Test
    public void testCustomDiagnosticProviderDoesNotRunOnDifferentFileType() {
        File file = new File("/test/src/style.css");
        String cssCode = "body { margin: 0; } /* disallowedPattern */";

        List<Problem> problems = DiagnosticEngine.analyze(file, cssCode, FileType.CSS);
        for (Problem p : problems) {
            assertFalse(p.getMessage().contains("disallowedPattern is forbidden"));
        }
    }
}
