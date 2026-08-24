package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.lsp.LspCompletionItem;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class JsLspServerTest {

    private JsLspServer server;

    @Before
    public void setup() {
        server = new JsLspServer(RuntimeEnvironment.getApplication());
        server.initialize(ProjectIndex.getInstance());
    }

    @Test
    public void testDiagnostics() {
        LspDocument doc = new LspDocument("/test.js", "const a = 1 / 0;", "javascript", 1);
        List<Problem> problems = server.diagnostics(doc);
        
        assertNotNull(problems);
        boolean foundDivZero = false;
        for (Problem p : problems) {
            if (p.getMessage().toLowerCase().contains("division by zero") || 
                p.getMessage().toLowerCase().contains("divide by zero")) {
                foundDivZero = true;
            }
        }
        assertTrue("Should detect division by zero from linter", foundDivZero);
    }

    @Test
    public void testMemberCompletionConsole() {
        // "console." should suggest "log", "warn", etc.
        LspDocument doc = new LspDocument("/test.js", "console.", "javascript", 1);
        LspPosition pos = new LspPosition(0, 8); // at the dot
        
        List<LspCompletionItem> items = server.completion(doc, pos);
        
        assertNotNull(items);
        assertFalse(items.isEmpty());
        
        boolean foundLog = false;
        for (LspCompletionItem item : items) {
            if ("log".equals(item.label)) foundLog = true;
        }
        assertTrue("Should suggest console.log", foundLog);
    }

    @Test
    public void testMemberCompletionInferredArray() {
        // "const arr = []; arr." should suggest array methods
        LspDocument doc = new LspDocument("/test.js", "const arr = [];\narr.", "javascript", 1);
        LspPosition pos = new LspPosition(1, 4); // at the dot
        
        List<LspCompletionItem> items = server.completion(doc, pos);
        
        assertNotNull(items);
        assertFalse(items.isEmpty());
        
        boolean foundPush = false;
        for (LspCompletionItem item : items) {
            if ("push".equals(item.label)) foundPush = true;
        }
        assertTrue("Should suggest array.push", foundPush);
    }
}
