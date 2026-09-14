package com.cocode.vcode.ide.core.language.html;

import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HtmlLinterTest {

    private final File mockFile = new File("test.html");

    @Test
    public void testVoidElementClosingTag() {
        String html = "<br></br>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, html);
        
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("void element") && p.getSeverity() == Problem.Severity.ERROR) {
                found = true;
                break;
            }
        }
        assertTrue("Should detect void element error", found);
    }

    @Test
    public void testUnclosedTag() {
        String html = "<div><p>text</div>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, html);
        
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Unclosed tag") && p.getSeverity() == Problem.Severity.ERROR) {
                found = true;
                break;
            }
        }
        assertTrue("Should detect unclosed tag", found);
    }

    @Test
    public void testStrayClosingTag() {
        String html = "<div>text</div></p>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, html);
        
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Stray closing tag") && p.getSeverity() == Problem.Severity.ERROR) {
                found = true;
                break;
            }
        }
        assertTrue("Should detect stray closing tag", found);
    }

    @Test
    public void testTableWithoutTh() {
        String html = "<table><tr><td>Data</td></tr></table>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, html);
        
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("has no header row")) {
                found = true;
                break;
            }
        }
        assertTrue("Should detect table without th", found);
    }

    @Test
    public void testInvalidElement() {
        String html = "<div><fakeelement>Hello</fakeelement></div>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, html);
        
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("not a valid HTML5 element") && p.getSeverity() == Problem.Severity.WARNING) {
                found = true;
                break;
            }
        }
        assertTrue("Should detect invalid HTML element", found);
    }

    @Test
    public void testDeprecatedElement() {
        String html = "<div><center>Centered</center></div>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, html);
        
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("deprecated") && p.getSeverity() == Problem.Severity.WARNING) {
                found = true;
                break;
            }
        }
        assertTrue("Should detect deprecated element", found);
    }

    @Test
    public void testDuplicateId() {
        String html = "<div id=\"main\"></div><div id=\"main\"></div>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, html);
        
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Duplicate id") && p.getSeverity() == Problem.Severity.ERROR) {
                found = true;
                break;
            }
        }
        assertTrue("Should detect duplicate id", found);
    }

    @Test
    public void testImgMissingAltAndSrc() {
        String html = "<div><img></div>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, html);
        
        boolean missingSrc = false;
        boolean missingAlt = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("missing required attribute 'src'")) missingSrc = true;
            if (p.getMessage().contains("missing 'alt' attribute")) missingAlt = true;
        }
        assertTrue("Should detect missing src on img", missingSrc);
        assertTrue("Should detect missing alt on img", missingAlt);
    }

    @Test
    public void testHtmlFragmentNoDocumentLevelWarnings() {
        String html = "<div><span>Fragment</span></div>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, html);
        for (Problem p : problems) {
            String msg = p.getMessage();
            assertFalse("Fragment should not warn about meta charset", msg.contains("meta charset"));
            assertFalse("Fragment should not warn about viewport", msg.contains("viewport"));
            assertFalse("Fragment should not warn about title", msg.contains("<title>"));
            assertFalse("Fragment should not warn about description", msg.contains("description"));
        }
    }

    @Test
    public void testFullDocumentHasDocumentLevelWarnings() {
        String html = "<!DOCTYPE html><html><body>Hello</body></html>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, html);
        boolean hasTitleWarn = false;
        boolean hasCharsetWarn = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("<title>")) hasTitleWarn = true;
            if (p.getMessage().contains("meta charset")) hasCharsetWarn = true;
        }
        assertTrue("Full document without title should warn", hasTitleWarn);
        assertTrue("Full document without charset should warn", hasCharsetWarn);
    }

    @Test
    public void testTableWithNestedThDoesNotWarn() {
        String htmlWithTrTh = "<table><tr><th>Header</th></tr><tr><td>Data</td></tr></table>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, htmlWithTrTh);
        boolean warned = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("has no header row")) warned = true;
        }
        assertFalse("Table with <tr><th> should not report missing header row", warned);

        String htmlWithTbodyTrTh = "<table><tbody><tr><th>Header</th></tr></tbody></table>";
        List<Problem> problems2 = HtmlLinter.analyze(mockFile, htmlWithTbodyTrTh);
        boolean warned2 = false;
        for (Problem p : problems2) {
            if (p.getMessage().contains("has no header row")) warned2 = true;
        }
        assertFalse("Table with <tbody><tr><th> should not report missing header row", warned2);
    }

    @Test
    public void testDuplicateAttributes() {
        String html = "<div class=\"foo\" id=\"main\" class=\"bar\"></div>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, html);
        boolean foundDuplicate = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Duplicate attribute 'class'")) {
                foundDuplicate = true;
                assertEquals(Problem.Severity.WARNING, p.getSeverity());
                break;
            }
        }
        assertTrue("Should detect duplicate attribute 'class'", foundDuplicate);
    }

    @Test
    public void testEmptyTitleElement() {
        String html = "<!DOCTYPE html><html><head><title>   </title></head><body>Hello</body></html>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, html);
        boolean foundEmptyTitle = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Empty '<title>' element")) {
                foundEmptyTitle = true;
                assertEquals(Problem.Severity.WARNING, p.getSeverity());
                break;
            }
        }
        assertTrue("Should detect whitespace-only <title> element", foundEmptyTitle);
    }

    @Test
    public void testInlineStyleIsInfoSeverity() {
        String html = "<div style=\"color: red;\">Content</div>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, html);
        boolean foundInline = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Avoid inline styles on")) {
                foundInline = true;
                assertEquals(Problem.Severity.INFO, p.getSeverity());
                break;
            }
        }
        assertTrue("Inline styles should be detected as INFO", foundInline);
    }

    @Test
    public void testEmptyTagWithClassOrIdIsNotFlagged() {
        String html = "<div id=\"app\"></div><div class=\"spinner\"></div>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, html);
        for (Problem p : problems) {
            assertFalse("Tags with id or class should not be flagged as empty tags: " + p.getMessage(),
                    p.getMessage().contains("Empty '<div"));
        }
    }
}
