package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.lsp.LspDocument;
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
public class CssLspServerTest {

    private CssLspServer server;

    @Before
    public void setup() {
        server = new CssLspServer(RuntimeEnvironment.getApplication());
        server.initialize(ProjectIndex.getInstance());
    }

    @Test
    public void testDiagnostics() {
        LspDocument doc = new LspDocument("/test.css", ".class { color: red; color: blue; }", "css", 1);
        List<Problem> problems = server.diagnostics(doc);
        
        assertNotNull(problems);
        boolean foundDuplicate = false;
        for (Problem p : problems) {
            if (p.getMessage().toLowerCase().contains("duplicate")) {
                foundDuplicate = true;
            }
        }
        assertTrue("Should detect duplicate property from linter", foundDuplicate);
    }
}
