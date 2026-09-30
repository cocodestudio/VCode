package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex;
import com.cocode.vcode.ide.core.lsp.LspCompletionItem;

import java.io.File;

import static org.junit.Assert.assertFalse;
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

    @Test
    public void testCompletionScopedCssInClassAttribute() {
        File htmlFile = new File("src/test_project/index.html").getAbsoluteFile();
        File cssFile = new File("src/test_project/styles.css").getAbsoluteFile();

        ProjectIndex.getInstance().updateDocument(new LspDocument(cssFile.getAbsolutePath(), ".container { display: flex; }\n#unlinked-id { color: red; }", "css", 1));
        ProjectSymbolIndex.getInstance().invalidateFile(cssFile.getAbsolutePath());

        // HTML without linked CSS
        String html = "<!DOCTYPE html>\n<html><head></head><body><div class=\"\"></div></body></html>";
        LspDocument doc = new LspDocument(htmlFile.getAbsolutePath(), html, "html", 1);
        int classCursor = html.indexOf("class=\"\"") + 7;
        LspPosition pos = doc.toPosition(classCursor);

        List<LspCompletionItem> items = server.completion(doc, pos);
        for (LspCompletionItem item : items) {
            assertFalse("Unlinked CSS class 'container' must NOT appear in HTML class completion via LSP server",
                    "container".equals(item.label));
        }

        // HTML WITH linked CSS
        String htmlWithLink = "<!DOCTYPE html>\n<html><head><link rel=\"stylesheet\" href=\"styles.css\"></head><body><div class=\"\"></div></body></html>";
        LspDocument docWithLink = new LspDocument(htmlFile.getAbsolutePath(), htmlWithLink, "html", 2);
        int linkedClassCursor = htmlWithLink.indexOf("class=\"\"") + 7;
        LspPosition posWithLink = docWithLink.toPosition(linkedClassCursor);

        List<LspCompletionItem> itemsWithLink = server.completion(docWithLink, posWithLink);
        boolean foundContainer = false;
        for (LspCompletionItem item : itemsWithLink) {
            if ("container".equals(item.label)) {
                foundContainer = true;
                break;
            }
        }
        assertTrue("Linked CSS class 'container' MUST appear in HTML class completion via LSP server", foundContainer);
    }
}
