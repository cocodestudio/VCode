package com.cocode.vcode.ide.core.language.html;

import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class HtmlSyntaxHighlighterTest {

    private static final int C_TAG = 301;
    private static final int C_ATTR = 302;
    private static final int C_VALUE = 303;
    private static final int C_BRACKET = 304;
    private static final int C_COMMENT = 305;

    private HtmlSyntaxHighlighter htmlHighlighter;

    @Before
    public void setUp() {
        htmlHighlighter = HtmlSyntaxHighlighter.forTestWithColors(
                C_TAG, C_ATTR, C_VALUE, C_BRACKET, C_COMMENT
        );
    }

    @Test
    public void testUnquotedAttributeValue() {
        String code = "<input type=text>";
        List<HighlightToken> tokens = htmlHighlighter.tokenizeLine(code, 0, 0);

        boolean foundTypeAttr = false;
        boolean foundTextValue = false;

        for (HighlightToken t : tokens) {
            String text = code.substring(t.startCol, t.endCol);
            if ("type".equals(text) && t.color == C_ATTR) {
                foundTypeAttr = true;
            } else if ("text".equals(text) && t.color == C_VALUE) {
                foundTextValue = true;
            }
        }

        assertTrue("Attribute 'type' should be highlighted as attribute", foundTypeAttr);
        assertTrue("Unquoted value 'text' should be highlighted as value", foundTextValue);
    }

    @Test
    public void testHtmlCharacterEntities() {
        String code = "<p>&copy; 2026 &amp;</p>";
        List<HighlightToken> tokens = htmlHighlighter.tokenizeLine(code, 0, 0);

        boolean foundCopyEntity = false;
        boolean foundAmpEntity = false;

        for (HighlightToken t : tokens) {
            String text = code.substring(t.startCol, t.endCol);
            if ("&copy;".equals(text) && t.color == C_VALUE) {
                foundCopyEntity = true;
            } else if ("&amp;".equals(text) && t.color == C_VALUE) {
                foundAmpEntity = true;
            }
        }

        assertTrue("&copy; entity should be highlighted as value/entity", foundCopyEntity);
        assertTrue("&amp; entity should be highlighted as value/entity", foundAmpEntity);
    }

    @Test
    public void testXmlProcessingInstruction() {
        String code = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
        List<HighlightToken> tokens = htmlHighlighter.tokenizeLine(code, 0, 0);

        boolean foundXmlTag = false;
        boolean foundVersionAttr = false;
        boolean foundClosePi = false;

        for (HighlightToken t : tokens) {
            String text = code.substring(t.startCol, t.endCol);
            if ("xml".equals(text) && t.color == C_TAG) {
                foundXmlTag = true;
            } else if ("version".equals(text) && t.color == C_ATTR) {
                foundVersionAttr = true;
            } else if ("?>".equals(text) && t.color == C_BRACKET) {
                foundClosePi = true;
            }
        }

        assertTrue("XML processing instruction tag 'xml' should be highlighted as tag", foundXmlTag);
        assertTrue("Processing instruction attribute 'version' should be highlighted as attribute", foundVersionAttr);
        assertTrue("Closing '?>' should be highlighted as bracket", foundClosePi);
    }
}
