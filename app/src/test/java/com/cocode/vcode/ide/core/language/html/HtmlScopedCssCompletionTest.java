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
public class HtmlScopedCssCompletionTest {

    private HtmlAutoCompleteEngine engine;
    private File mockHtmlFile;
    private File mockLinkedCssFile;
    private File mockUnlinkedCssFile;

    @Before
    public void setup() {
        engine = new HtmlAutoCompleteEngine(RuntimeEnvironment.getApplication());
        try {
            mockHtmlFile = new File("src/test_project/index.html").getCanonicalFile();
            mockLinkedCssFile = new File("src/test_project/style.css").getCanonicalFile();
            mockUnlinkedCssFile = new File("src/test_project/unlinked.css").getCanonicalFile();
        } catch (Exception e) {
            mockHtmlFile = new File("src/test_project/index.html").getAbsoluteFile();
            mockLinkedCssFile = new File("src/test_project/style.css").getAbsoluteFile();
            mockUnlinkedCssFile = new File("src/test_project/unlinked.css").getAbsoluteFile();
        }

        engine.setCurrentFile(mockHtmlFile);

        // Populate mock linked CSS
        String linkedCss = ".linked-btn { color: blue; }\n" +
                           ".shared-selector { font-size: 14px; }\n" +
                           "#linked-header { display: flex; }\n" +
                           "#shared-id { position: absolute; }\n";
        ProjectIndex.getInstance().updateDocument(new LspDocument(mockLinkedCssFile.getAbsolutePath(), linkedCss, "css", 1));
        ProjectSymbolIndex.getInstance().invalidateFile(mockLinkedCssFile.getAbsolutePath());

        // Populate mock unlinked CSS
        String unlinkedCss = ".unlinked-card { padding: 10px; }\n" +
                             "#unlinked-modal { display: none; }\n";
        ProjectIndex.getInstance().updateDocument(new LspDocument(mockUnlinkedCssFile.getAbsolutePath(), unlinkedCss, "css", 1));
        ProjectSymbolIndex.getInstance().invalidateFile(mockUnlinkedCssFile.getAbsolutePath());
    }

    @Test
    public void testExtractLinkedCssFilesIgnoresCommentsAndIcons() {
        String html = "<!DOCTYPE html>\n" +
                      "<html>\n" +
                      "<head>\n" +
                      "    <!-- <link rel=\"stylesheet\" href=\"ignored_comment.css\"> -->\n" +
                      "    <link rel=\"icon\" href=\"favicon.ico\">\n" +
                      "    <link rel=\"stylesheet\" href=\"style.css\">\n" +
                      "    <link href=\"./components/nav.css\" rel=\"stylesheet\">\n" +
                      "    <link rel=\"stylesheet\" href=\"https://cdn.example.com/remote.css\">\n" +
                      "</head>\n" +
                      "<body></body>\n" +
                      "</html>";

        List<File> files = HtmlAutoCompleteEngine.extractLinkedCssFiles(html, mockHtmlFile);
        assertNotNull(files);
        assertEquals(2, files.size());

        boolean foundStyle = false;
        boolean foundNav = false;
        for (File f : files) {
            if (f.getName().equals("style.css")) foundStyle = true;
            if (f.getName().equals("nav.css")) foundNav = true;
        }
        assertTrue("Should extract style.css", foundStyle);
        assertTrue("Should extract nav.css", foundNav);
    }

    @Test
    public void testUnlinkedCssFileNotShowingInHtmlCompletions() {
        // HTML has no linked stylesheet and no <style> tag
        String html = "<!DOCTYPE html>\n" +
                      "<html>\n" +
                      "<body>\n" +
                      "    <div class=\"\"></div>\n" +
                      "    <div id=\"\"></div>\n" +
                      "</body>\n" +
                      "</html>";

        int classCursor = html.indexOf("class=\"\"") + 7;
        List<CompletionItem> classSuggestions = engine.getSuggestions(html, classCursor);
        for (CompletionItem item : classSuggestions) {
            assertFalse("Must NOT suggest class from unlinked CSS file", "unlinked-card".equals(item.getLabel()));
            assertFalse("Must NOT suggest class from linked CSS file when not linked", "linked-btn".equals(item.getLabel()));
        }

        int idCursor = html.indexOf("id=\"\"") + 4;
        List<CompletionItem> idSuggestions = engine.getSuggestions(html, idCursor);
        for (CompletionItem item : idSuggestions) {
            assertFalse("Must NOT suggest ID from unlinked CSS file", "unlinked-modal".equals(item.getLabel()));
            assertFalse("Must NOT suggest ID from linked CSS file when not linked", "linked-header".equals(item.getLabel()));
        }
    }

    @Test
    public void testLinkedCssFileShowsSelectors() {
        String html = "<!DOCTYPE html>\n" +
                      "<html>\n" +
                      "<head>\n" +
                      "    <link rel=\"stylesheet\" href=\"style.css\">\n" +
                      "</head>\n" +
                      "<body>\n" +
                      "    <div class=\"l\"></div>\n" +
                      "    <div id=\"l\"></div>\n" +
                      "</body>\n" +
                      "</html>";

        int classCursor = html.indexOf("class=\"l\"") + 8;
        List<CompletionItem> classSuggestions = engine.getSuggestions(html, classCursor);
        boolean foundLinkedBtn = false;
        boolean foundUnlinked = false;
        for (CompletionItem item : classSuggestions) {
            if ("linked-btn".equals(item.getLabel())) foundLinkedBtn = true;
            if ("unlinked-card".equals(item.getLabel())) foundUnlinked = true;
        }
        assertTrue("Should suggest linked-btn from linked style.css", foundLinkedBtn);
        assertFalse("Must NOT suggest unlinked-card from unlinked.css", foundUnlinked);

        int idCursor = html.indexOf("id=\"l\"") + 5;
        List<CompletionItem> idSuggestions = engine.getSuggestions(html, idCursor);
        boolean foundLinkedHeader = false;
        boolean foundUnlinkedId = false;
        for (CompletionItem item : idSuggestions) {
            if ("linked-header".equals(item.getLabel())) foundLinkedHeader = true;
            if ("unlinked-modal".equals(item.getLabel())) foundUnlinkedId = true;
        }
        assertTrue("Should suggest linked-header from linked style.css", foundLinkedHeader);
        assertFalse("Must NOT suggest unlinked-modal from unlinked.css", foundUnlinkedId);
    }

    @Test
    public void testEmbeddedStyleTagShowsSelectors() {
        String html = "<!DOCTYPE html>\n" +
                      "<html>\n" +
                      "<head>\n" +
                      "    <style>\n" +
                      "        .inline-badge { color: green; }\n" +
                      "        #inline-banner { width: 100%; }\n" +
                      "    </style>\n" +
                      "</head>\n" +
                      "<body>\n" +
                      "    <div class=\"in\"></div>\n" +
                      "    <div id=\"in\"></div>\n" +
                      "</body>\n" +
                      "</html>";

        int classCursor = html.indexOf("class=\"in\"") + 9;
        List<CompletionItem> classSuggestions = engine.getSuggestions(html, classCursor);
        boolean foundInlineBadge = false;
        for (CompletionItem item : classSuggestions) {
            if ("inline-badge".equals(item.getLabel())) {
                foundInlineBadge = true;
                assertEquals("<style>", item.getDetail());
            }
        }
        assertTrue("Should suggest inline-badge from <style> tag", foundInlineBadge);

        int idCursor = html.indexOf("id=\"in\"") + 6;
        List<CompletionItem> idSuggestions = engine.getSuggestions(html, idCursor);
        boolean foundInlineBanner = false;
        for (CompletionItem item : idSuggestions) {
            if ("inline-banner".equals(item.getLabel())) {
                foundInlineBanner = true;
                assertEquals("<style>", item.getDetail());
            }
        }
        assertTrue("Should suggest inline-banner from <style> tag", foundInlineBanner);
    }

    @Test
    public void testUniqueDeduplicationWhenBothLinkedCssAndStyleTagPresent() {
        // Both style.css and <style> have .shared-selector and #shared-id
        String html = "<!DOCTYPE html>\n" +
                      "<html>\n" +
                      "<head>\n" +
                      "    <link rel=\"stylesheet\" href=\"style.css\">\n" +
                      "    <style>\n" +
                      "        .shared-selector { font-weight: bold; }\n" +
                      "        .only-style { color: red; }\n" +
                      "        #shared-id { z-index: 10; }\n" +
                      "        #only-style-id { top: 0; }\n" +
                      "    </style>\n" +
                      "</head>\n" +
                      "<body>\n" +
                      "    <div class=\"\"></div>\n" +
                      "    <div id=\"\"></div>\n" +
                      "</body>\n" +
                      "</html>";

        int classCursor = html.indexOf("class=\"\"") + 7;
        List<CompletionItem> classSuggestions = engine.getSuggestions(html, classCursor);

        int sharedSelectorCount = 0;
        boolean foundLinkedBtn = false;
        boolean foundOnlyStyle = false;

        for (CompletionItem item : classSuggestions) {
            if ("shared-selector".equals(item.getLabel())) {
                sharedSelectorCount++;
                assertTrue("Detail should mention both origins: " + item.getDetail(),
                        item.getDetail().contains("<style>") && item.getDetail().contains("style.css"));
            }
            if ("linked-btn".equals(item.getLabel())) foundLinkedBtn = true;
            if ("only-style".equals(item.getLabel())) foundOnlyStyle = true;
        }

        assertEquals("shared-selector must be unique (deduplicated to exactly 1 item)", 1, sharedSelectorCount);
        assertTrue("Should contain class from linked CSS", foundLinkedBtn);
        assertTrue("Should contain class from <style> tag", foundOnlyStyle);

        int idCursor = html.indexOf("id=\"\"") + 4;
        List<CompletionItem> idSuggestions = engine.getSuggestions(html, idCursor);

        int sharedIdCount = 0;
        boolean foundLinkedHeader = false;
        boolean foundOnlyStyleId = false;

        for (CompletionItem item : idSuggestions) {
            if ("shared-id".equals(item.getLabel())) {
                sharedIdCount++;
                assertTrue("Detail should mention both origins: " + item.getDetail(),
                        item.getDetail().contains("<style>") && item.getDetail().contains("style.css"));
            }
            if ("linked-header".equals(item.getLabel())) foundLinkedHeader = true;
            if ("only-style-id".equals(item.getLabel())) foundOnlyStyleId = true;
        }

        assertEquals("shared-id must be unique (deduplicated to exactly 1 item)", 1, sharedIdCount);
        assertTrue("Should contain ID from linked CSS", foundLinkedHeader);
        assertTrue("Should contain ID from <style> tag", foundOnlyStyleId);
    }

    @Test
    public void testEmbeddedStyleTagClassCompletionScoping() {
        String html = "<!DOCTYPE html>\n" +
                      "<html lang=\"en\">\n" +
                      "  <head>\n" +
                      "    <style>\n" +
                      "      .wrapper {\n" +
                      "        display: flex;\n" +
                      "      }\n" +
                      "\n" +
                      "      .bg-wrapper {\n" +
                      "        \n" +
                      "      }\n" +
                      "      #wrap {\n" +
                      "        \n" +
                      "      }\n" +
                      "      .\n" +
                      "    </style>\n" +
                      "  </head>\n" +
                      "  <body>\n" +
                      "    <img src=\"\" class=\"\" loading=\"lazy\" id=\"wrapper-new\" alt=\"\">\n" +
                      "  </body>\n" +
                      "</html>";

        // Cursor right after '.' inside style tag
        int dotCursor = html.lastIndexOf('.') + 1;
        List<CompletionItem> suggestions = engine.getSuggestions(html, dotCursor);

        boolean foundWrapper = false;
        boolean foundBgWrapper = false;
        for (CompletionItem item : suggestions) {
            String label = item.getLabel();
            if (".wrapper".equals(label) || "wrapper".equals(label)) foundWrapper = true;
            if (".bg-wrapper".equals(label) || "bg-wrapper".equals(label)) foundBgWrapper = true;

            // Must NOT suggest unlinked css classes
            assertFalse("Must NOT suggest unlinked CSS class 'unlinked-card'", label.contains("unlinked-card"));
            // Must NOT suggest incomplete/fragmented tokens
            assertFalse("Must NOT suggest trailing hyphen tokens", label.endsWith("-"));
            assertFalse("Must NOT suggest single letter invalid tokens", ".b".equals(label) || ".c".equals(label) || ".w".equals(label));
        }

        assertTrue("Should suggest wrapper in style tag", foundWrapper);
        assertTrue("Should suggest bg-wrapper in style tag", foundBgWrapper);
    }

    @Test
    public void testEmbeddedStyleTagIdCompletionScoping() {
        String html = "<!DOCTYPE html>\n" +
                      "<html lang=\"en\">\n" +
                      "  <head>\n" +
                      "    <style>\n" +
                      "      .wrapper {\n" +
                      "        display: flex;\n" +
                      "      }\n" +
                      "      #wrap {\n" +
                      "        \n" +
                      "      }\n" +
                      "      #\n" +
                      "    </style>\n" +
                      "  </head>\n" +
                      "  <body>\n" +
                      "    <img src=\"\" class=\"\" loading=\"lazy\" id=\"wrapper-new\" alt=\"\">\n" +
                      "  </body>\n" +
                      "</html>";

        // Cursor right after '#' inside style tag
        int hashCursor = html.lastIndexOf('#') + 1;
        List<CompletionItem> suggestions = engine.getSuggestions(html, hashCursor);

        boolean foundWrap = false;
        boolean foundWrapperNew = false;
        for (CompletionItem item : suggestions) {
            String label = item.getLabel();
            if ("#wrap".equals(label) || "wrap".equals(label)) foundWrap = true;
            if ("#wrapper-new".equals(label) || "wrapper-new".equals(label)) foundWrapperNew = true;

            // Must NOT suggest unlinked CSS IDs
            assertFalse("Must NOT suggest unlinked CSS ID 'unlinked-modal'", label.contains("unlinked-modal"));
        }

        assertTrue("Should suggest wrap defined in style tag", foundWrap);
        assertTrue("Should suggest wrapper-new defined in HTML body id attribute", foundWrapperNew);
    }
}
