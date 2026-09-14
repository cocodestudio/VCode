package com.cocode.vcode.ide.core.language.md;

import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MarkdownLinterTest {

    private final File mockFile = new File("test.md");

    @Test
    public void testHeadingLevelProgression() {
        String md = "# Heading 1\n### Heading 3\n";
        List<Problem> problems = MarkdownLinter.analyze(mockFile, md);
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Heading levels should only increment by one level")) {
                found = true;
                break;
            }
        }
        assertTrue("Skipping heading level from H1 to H3 should trigger warning", found);
    }

    @Test
    public void testCodeBlockSuppressesInlinePatternWarnings() {
        // Raw URL, empty link, useless image alt inside a fenced code block should NOT trigger warnings
        String md = "```markdown\n" +
                "https://example.com\n" +
                "[](empty.com)\n" +
                "![image](pic.png)\n" +
                "\tindented with tab\n" +
                "```\n";
        List<Problem> problems = MarkdownLinter.analyze(mockFile, md);
        assertTrue("Rules inside fenced code block must be suppressed, found: " + problems, problems.isEmpty());
    }

    @Test
    public void testInlineCodeSuppressesRawUrlWarning() {
        String md = "Visit `https://example.com` for details.";
        List<Problem> problems = MarkdownLinter.analyze(mockFile, md);
        boolean foundRawUrl = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Raw URL")) {
                foundRawUrl = true;
                break;
            }
        }
        assertFalse("Raw URL inside inline code backticks should not trigger warning", foundRawUrl);
    }

    @Test
    public void testBareUrlOutsideCodeBlockTriggersWarning() {
        String md = "Visit https://example.com for details.";
        List<Problem> problems = MarkdownLinter.analyze(mockFile, md);
        boolean foundRawUrl = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Raw URL")) {
                foundRawUrl = true;
                break;
            }
        }
        assertTrue("Raw URL outside code block should trigger warning", foundRawUrl);
    }

    @Test
    public void testInconsistentListMarker() {
        String md = "- item 1\n* item 2\n";
        List<Problem> problems = MarkdownLinter.analyze(mockFile, md);
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Inconsistent list marker")) {
                found = true;
                break;
            }
        }
        assertTrue("Inconsistent list markers '-' and '*' should trigger warning", found);
    }
}
