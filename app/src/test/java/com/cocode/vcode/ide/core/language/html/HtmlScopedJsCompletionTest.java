package com.cocode.vcode.ide.core.language.html;

import org.robolectric.RuntimeEnvironment;

import com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.model.CompletionItem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class HtmlScopedJsCompletionTest {

    private HtmlAutoCompleteEngine engine;
    private File mockHtmlFile;
    private File mockLinkedJsFile;
    private File mockUnlinkedJsFile;
    private File mockNestedJsFile;

    @Before
    public void setup() {
        engine = new HtmlAutoCompleteEngine(RuntimeEnvironment.getApplication());
        try {
            mockHtmlFile = new File("src/test_project/index.html").getCanonicalFile();
            mockLinkedJsFile = new File("src/test_project/app.js").getCanonicalFile();
            mockUnlinkedJsFile = new File("src/test_project/unlinked.js").getCanonicalFile();
            mockNestedJsFile = new File("src/test_project/helper.js").getCanonicalFile();
        } catch (Exception e) {
            mockHtmlFile = new File("src/test_project/index.html").getAbsoluteFile();
            mockLinkedJsFile = new File("src/test_project/app.js").getAbsoluteFile();
            mockUnlinkedJsFile = new File("src/test_project/unlinked.js").getAbsoluteFile();
            mockNestedJsFile = new File("src/test_project/helper.js").getAbsoluteFile();
        }

        engine.setCurrentFile(mockHtmlFile);

        // Populate mock linked JS
        String linkedJs = "import { nestedHelper } from './helper.js';\n" +
                          "function calculateTotal(items, tax) { return items * tax; }\n" +
                          "const API_ENDPOINT = 'https://api.example.com';\n" +
                          "class SessionManager {}\n" +
                          "function sharedAction() { console.log('linked'); }\n";
        ProjectIndex.getInstance().updateDocument(new LspDocument(mockLinkedJsFile.getAbsolutePath(), linkedJs, "javascript", 1));
        ProjectSymbolIndex.getInstance().invalidateFile(mockLinkedJsFile.getAbsolutePath());

        // Populate mock nested helper JS
        String nestedJs = "function nestedHelper() { return 42; }\n";
        ProjectIndex.getInstance().updateDocument(new LspDocument(mockNestedJsFile.getAbsolutePath(), nestedJs, "javascript", 1));
        ProjectSymbolIndex.getInstance().invalidateFile(mockNestedJsFile.getAbsolutePath());

        // Populate mock unlinked JS
        String unlinkedJs = "function unlinkedSecretFunction() { return 'secret'; }\n" +
                            "const UNLINKED_TOKEN = 'secret_token_123';\n";
        ProjectIndex.getInstance().updateDocument(new LspDocument(mockUnlinkedJsFile.getAbsolutePath(), unlinkedJs, "javascript", 1));
        ProjectSymbolIndex.getInstance().invalidateFile(mockUnlinkedJsFile.getAbsolutePath());
    }

    @Test
    public void testExtractLinkedJsFilesIgnoresCommentsAndNonJs() {
        String html = "<!DOCTYPE html>\n" +
                      "<html>\n" +
                      "<head>\n" +
                      "    <!-- <script src=\"ignored_comment.js\"></script> -->\n" +
                      "    <script type=\"application/json\" src=\"data.json\"></script>\n" +
                      "    <script src=\"app.js\"></script>\n" +
                      "    <script type=\"module\" src=\"./helper.js\"></script>\n" +
                      "    <script src=\"https://cdn.example.com/remote.js\"></script>\n" +
                      "</head>\n" +
                      "<body></body>\n" +
                      "</html>";

        List<File> files = HtmlAutoCompleteEngine.extractLinkedJsFiles(html, mockHtmlFile);
        assertNotNull(files);
        assertEquals(2, files.size());

        boolean foundApp = false;
        boolean foundHelper = false;
        for (File f : files) {
            if (f.getName().equals("app.js")) foundApp = true;
            if (f.getName().equals("helper.js")) foundHelper = true;
        }
        assertTrue("Should extract app.js", foundApp);
        assertTrue("Should extract helper.js", foundHelper);
    }

    @Test
    public void testUnlinkedJsFileExcludedFromCompletions() {
        // HTML has no linked scripts and no <script> tag
        String html = "<!DOCTYPE html>\n" +
                      "<html>\n" +
                      "<body>\n" +
                      "    <button onclick=\"unl\"></button>\n" +
                      "</body>\n" +
                      "</html>";

        int cursor = html.indexOf("onclick=\"unl\"") + 12;
        List<CompletionItem> suggestions = engine.getSuggestions(html, cursor);
        for (CompletionItem item : suggestions) {
            assertFalse("Must NOT suggest unlinkedSecretFunction from unlinked.js", "unlinkedSecretFunction".equals(item.getLabel()));
            assertFalse("Must NOT suggest UNLINKED_TOKEN from unlinked.js", "UNLINKED_TOKEN".equals(item.getLabel()));
        }
    }

    @Test
    public void testLinkedJsFileShowsSymbolsInOnclick() {
        String html = "<!DOCTYPE html>\n" +
                      "<html>\n" +
                      "<head>\n" +
                      "    <script src=\"app.js\"></script>\n" +
                      "</head>\n" +
                      "<body>\n" +
                      "    <button onclick=\"calc\"></button>\n" +
                      "</body>\n" +
                      "</html>";

        int cursor = html.indexOf("onclick=\"calc\"") + 13;
        List<CompletionItem> suggestions = engine.getSuggestions(html, cursor);
        boolean foundCalculate = false;
        boolean foundUnlinked = false;
        for (CompletionItem item : suggestions) {
            if ("calculateTotal".equals(item.getLabel())) {
                foundCalculate = true;
                assertTrue("Detail should mention app.js", item.getDetail() != null && item.getDetail().contains("app.js"));
            }
            if ("unlinkedSecretFunction".equals(item.getLabel())) {
                foundUnlinked = true;
            }
        }
        assertTrue("Should suggest calculateTotal from linked app.js", foundCalculate);
        assertFalse("Must NOT suggest unlinkedSecretFunction from unlinked.js", foundUnlinked);
    }

    @Test
    public void testLinkedJsFileShowsSymbolsInsideScriptTag() {
        String html = "<!DOCTYPE html>\n" +
                      "<html>\n" +
                      "<head>\n" +
                      "    <script src=\"app.js\"></script>\n" +
                      "    <script>\n" +
                      "        calc\n" +
                      "    </script>\n" +
                      "</head>\n" +
                      "<body></body>\n" +
                      "</html>";

        int cursor = html.indexOf("calc") + 4;
        List<CompletionItem> suggestions = engine.getSuggestions(html, cursor);
        boolean foundCalculate = false;
        boolean foundSessionManager = false;
        boolean foundUnlinked = false;
        for (CompletionItem item : suggestions) {
            if ("calculateTotal".equals(item.getLabel())) {
                foundCalculate = true;
            }
            if ("SessionManager".equals(item.getLabel())) {
                foundSessionManager = true;
            }
            if ("unlinkedSecretFunction".equals(item.getLabel())) {
                foundUnlinked = true;
            }
        }
        assertTrue("Should suggest calculateTotal from linked app.js", foundCalculate);
        assertFalse("Must NOT suggest unlinkedSecretFunction from unlinked.js", foundUnlinked);
    }

    @Test
    public void testEmbeddedScriptSymbolsIndexedAndSuggested() {
        String html = "<!DOCTYPE html>\n" +
                      "<html>\n" +
                      "<head>\n" +
                      "    <script>\n" +
                      "        function myInlineHelper(x) { return x * 2; }\n" +
                      "        const inlineConfig = { debug: true };\n" +
                      "    </script>\n" +
                      "</head>\n" +
                      "<body>\n" +
                      "    <button onclick=\"myIn\"></button>\n" +
                      "    <script>\n" +
                      "        myIn\n" +
                      "    </script>\n" +
                      "</body>\n" +
                      "</html>";

        // In onclick
        int onclickCursor = html.indexOf("onclick=\"myIn\"") + 13;
        List<CompletionItem> onclickSuggestions = engine.getSuggestions(html, onclickCursor);
        boolean foundInOnclick = false;
        for (CompletionItem item : onclickSuggestions) {
            if ("myInlineHelper".equals(item.getLabel())) {
                foundInOnclick = true;
                assertTrue("Detail should mention <script>", item.getDetail() != null && item.getDetail().contains("<script>"));
            }
        }
        assertTrue("Should suggest myInlineHelper in onclick", foundInOnclick);

        // In second script block
        int scriptCursor = html.lastIndexOf("myIn") + 4;
        List<CompletionItem> scriptSuggestions = engine.getSuggestions(html, scriptCursor);
        boolean foundInScript = false;
        for (CompletionItem item : scriptSuggestions) {
            if ("myInlineHelper".equals(item.getLabel())) {
                foundInScript = true;
            }
        }
        assertTrue("Should suggest myInlineHelper across script blocks", foundInScript);
    }

    @Test
    public void testUniqueDeduplicationAcrossScriptTagAndLinkedJs() {
        // Both app.js and inline script define sharedAction
        String html = "<!DOCTYPE html>\n" +
                      "<html>\n" +
                      "<head>\n" +
                      "    <script src=\"app.js\"></script>\n" +
                      "    <script>\n" +
                      "        function sharedAction() { console.log('inline'); }\n" +
                      "    </script>\n" +
                      "</head>\n" +
                      "<body>\n" +
                      "    <button onclick=\"shared\"></button>\n" +
                      "</body>\n" +
                      "</html>";

        int cursor = html.indexOf("onclick=\"shared\"") + 15;
        List<CompletionItem> suggestions = engine.getSuggestions(html, cursor);

        int count = 0;
        String combinedOrigin = null;
        for (CompletionItem item : suggestions) {
            if ("sharedAction".equals(item.getLabel())) {
                count++;
                combinedOrigin = item.getDetail();
            }
        }
        assertEquals("sharedAction must appear exactly once (deduplicated)", 1, count);
        assertNotNull("Origin should not be null", combinedOrigin);
        assertTrue("Origin should mention app.js", combinedOrigin.contains("app.js"));
        assertTrue("Origin should mention <script>", combinedOrigin.contains("<script>"));
    }

    @Test
    public void testRecursiveJsImportsResolution() {
        // app.js has: import { nestedHelper } from './helper.js';
        String html = "<!DOCTYPE html>\n" +
                      "<html>\n" +
                      "<head>\n" +
                      "    <script src=\"app.js\"></script>\n" +
                      "</head>\n" +
                      "<body>\n" +
                      "    <button onclick=\"nest\"></button>\n" +
                      "</body>\n" +
                      "</html>";

        int cursor = html.indexOf("onclick=\"nest\"") + 13;
        List<CompletionItem> suggestions = engine.getSuggestions(html, cursor);
        boolean foundNested = false;
        for (CompletionItem item : suggestions) {
            if ("nestedHelper".equals(item.getLabel())) {
                foundNested = true;
            }
        }
        assertTrue("Should resolve nestedHelper imported by app.js", foundNested);
    }

    @Test
    public void testScopedGetElementByIdInsideScriptTag() {
        File mockOtherHtmlFile = new File("src/test_project/other.html").getAbsoluteFile();
        String otherHtml = "<div id=\"other-unlinked-button\"></div>";
        ProjectIndex.getInstance().updateDocument(new LspDocument(mockOtherHtmlFile.getAbsolutePath(), otherHtml, "html", 1));

        String html = "<!DOCTYPE html>\n" +
                      "<html>\n" +
                      "<body>\n" +
                      "    <div id=\"active-html-button\"></div>\n" +
                      "    <script>\n" +
                      "        document.getElementById('')\n" +
                      "    </script>\n" +
                      "</body>\n" +
                      "</html>";

        int cursor = html.indexOf("document.getElementById('')") + 25;
        List<CompletionItem> suggestions = engine.getSuggestions(html, cursor);

        boolean foundActive = false;
        boolean foundOther = false;
        for (CompletionItem item : suggestions) {
            if ("active-html-button".equals(item.getLabel())) foundActive = true;
            if ("other-unlinked-button".equals(item.getLabel())) foundOther = true;
        }
        assertTrue("Should suggest ID from active HTML document", foundActive);
        assertFalse("Must NOT suggest ID from unrelated HTML document", foundOther);
    }
}
