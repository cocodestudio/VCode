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
    private static final int C_JS_IDENTIFIER = 306;

    private HtmlSyntaxHighlighter htmlHighlighter;

    @Before
    public void setUp() {
        htmlHighlighter = HtmlSyntaxHighlighter.forTestWithColors(
                C_TAG, C_ATTR, C_VALUE, C_BRACKET, C_COMMENT, C_JS_IDENTIFIER
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

    @Test
    public void testEventAttributeValuesColoredWithJsIdentifierColor() {
        String code = "<button class=\"btn\" onclick=\"handleClick(event)\" id=\"submit-btn\">";
        List<HighlightToken> tokens = htmlHighlighter.tokenizeLine(code, 0, 0);

        boolean foundBtnClass = false;
        boolean foundOnClickVal = false;
        boolean foundSubmitId = false;

        for (HighlightToken t : tokens) {
            String text = code.substring(t.startCol, t.endCol);
            if ("\"btn\"".equals(text) && t.color == C_VALUE) {
                foundBtnClass = true;
            } else if ("\"handleClick(event)\"".equals(text) && t.color == C_JS_IDENTIFIER) {
                foundOnClickVal = true;
            } else if ("\"submit-btn\"".equals(text) && t.color == C_VALUE) {
                foundSubmitId = true;
            }
        }

        assertTrue("Class attribute value should have standard value color", foundBtnClass);
        assertTrue("onclick attribute value should have JS identifier color", foundOnClickVal);
        assertTrue("id attribute value should have standard value color", foundSubmitId);
    }

    @Test
    public void testVariousEventAttributes() {
        String code = "<input onchange=\"handleChange()\" oninput=\"handleInput()\">";
        List<HighlightToken> tokens = htmlHighlighter.tokenizeLine(code, 0, 0);

        boolean foundChange = false;
        boolean foundInput = false;

        for (HighlightToken t : tokens) {
            String text = code.substring(t.startCol, t.endCol);
            if ("\"handleChange()\"".equals(text) && t.color == C_JS_IDENTIFIER) {
                foundChange = true;
            } else if ("\"handleInput()\"".equals(text) && t.color == C_JS_IDENTIFIER) {
                foundInput = true;
            }
        }

        assertTrue("onchange value should have JS identifier color", foundChange);
        assertTrue("oninput value should have JS identifier color", foundInput);
    }

    @Test
    public void testSingleQuotedAndUnquotedEventAttribute() {
        String code = "<button onfocus='init()' onblur=blurHandler()>";
        List<HighlightToken> tokens = htmlHighlighter.tokenizeLine(code, 0, 0);

        boolean foundFocus = false;
        boolean foundBlur = false;

        for (HighlightToken t : tokens) {
            String text = code.substring(t.startCol, t.endCol);
            if ("'init()'".equals(text) && t.color == C_JS_IDENTIFIER) {
                foundFocus = true;
            } else if ("blurHandler()".equals(text) && t.color == C_JS_IDENTIFIER) {
                foundBlur = true;
            }
        }

        assertTrue("Single-quoted onfocus value should have JS identifier color", foundFocus);
        assertTrue("Unquoted onblur value should have JS identifier color", foundBlur);
    }

    @Test
    public void testMultiLineEventAttribute() {
        String line1 = "<button onclick=\"";
        String line2 = "    handleClick();";
        String line3 = "\">";

        List<HighlightToken> tokens1 = htmlHighlighter.tokenizeLine(line1, 0, 0);
        int state1 = htmlHighlighter.getLastLineState();

        List<HighlightToken> tokens2 = htmlHighlighter.tokenizeLine(line2, 1, state1);
        int state2 = htmlHighlighter.getLastLineState();

        List<HighlightToken> tokens3 = htmlHighlighter.tokenizeLine(line3, 2, state2);

        boolean line1HasJsIdent = false;
        for (HighlightToken t : tokens1) {
            if (line1.substring(t.startCol, t.endCol).equals("\"") && t.color == C_JS_IDENTIFIER) {
                line1HasJsIdent = true;
            }
        }

        boolean line2HasJsIdent = false;
        for (HighlightToken t : tokens2) {
            if (line2.substring(t.startCol, t.endCol).contains("handleClick") && t.color == C_JS_IDENTIFIER) {
                line2HasJsIdent = true;
            }
        }

        boolean line3HasJsIdent = false;
        for (HighlightToken t : tokens3) {
            if (line3.substring(t.startCol, t.endCol).equals("\"") && t.color == C_JS_IDENTIFIER) {
                line3HasJsIdent = true;
            }
        }

        assertTrue("Line 1 unclosed quote of event attr should have JS identifier color", line1HasJsIdent);
        assertTrue("Line 2 content of event attr should have JS identifier color", line2HasJsIdent);
        assertTrue("Line 3 closing quote of event attr should have JS identifier color", line3HasJsIdent);
    }
}
