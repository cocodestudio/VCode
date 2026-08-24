package com.cocode.vcode.ide.core.language.html;

import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
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
}
