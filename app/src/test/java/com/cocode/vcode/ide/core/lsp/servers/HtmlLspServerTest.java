package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.robolectric.RuntimeEnvironment;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class HtmlLspServerTest {

    private HtmlLspServer server;

    @Before
    public void setup() {
        server = new HtmlLspServer(RuntimeEnvironment.getApplication());
        server.initialize(ProjectIndex.getInstance());
    }

    @Test
    public void testDiagnostics() {
        LspDocument doc = new LspDocument("/test.html", "<div><p>unclosed</div>", "html", 1);
        List<Problem> problems = server.diagnostics(doc);
        
        assertNotNull(problems);
        boolean foundUnclosed = false;
        for (Problem p : problems) {
            if (p.getMessage().toLowerCase().contains("unclosed") && p.getSeverity() == Problem.Severity.ERROR) {
                foundUnclosed = true;
            }
        }
        assertTrue("Should detect unclosed tag from linter", foundUnclosed);
    }
}
