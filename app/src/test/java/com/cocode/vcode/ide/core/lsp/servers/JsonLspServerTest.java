package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class JsonLspServerTest {

    private JsonLspServer server;

    @Before
    public void setUp() {
        server = new JsonLspServer();
        server.initialize(null);
    }

    @Test
    public void testLanguageId() {
        assertEquals("json", server.getLanguageId());
        assertTrue(server.isReady());
    }

    @Test
    public void testValidJsonHasNoDiagnostics() {
        LspDocument doc = new LspDocument("/test.json", "{\n  \"name\": \"vcode\",\n  \"version\": 1\n}", "json", 1);
        List<Problem> problems = server.diagnostics(doc);
        assertNotNull(problems);
        assertEquals(0, problems.size());
    }

    @Test
    public void testSyntaxErrorDiagnostics() {
        LspDocument doc = new LspDocument("/test.json", "{ \"a\" 1 }", "json", 1);
        List<Problem> problems = server.diagnostics(doc);
        assertNotNull(problems);
        assertTrue("Should report syntax error for missing colon", problems.size() > 0);
        boolean foundError = false;
        for (Problem p : problems) {
            if (p.getSeverity() == Problem.Severity.ERROR) {
                foundError = true;
                break;
            }
        }
        assertTrue("Should have ERROR severity problem", foundError);
    }

    @Test
    public void testDuplicateKeyDiagnostics() {
        LspDocument doc = new LspDocument("/test.json", "{ \"key\": 1, \"key\": 2 }", "json", 1);
        List<Problem> problems = server.diagnostics(doc);
        assertNotNull(problems);
        boolean foundDuplicate = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Duplicate")) {
                foundDuplicate = true;
                break;
            }
        }
        assertTrue("Should report duplicate key warning", foundDuplicate);
    }
}
