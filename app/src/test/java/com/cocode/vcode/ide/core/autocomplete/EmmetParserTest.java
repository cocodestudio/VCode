package com.cocode.vcode.ide.core.autocomplete;

import com.cocode.vcode.ide.core.language.css.EmmetCssDefinitions;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EmmetParserTest {

    @Test
    public void testHtmlBoilerplate() {
        String result = EmmetParser.expandHtml("!", null);
        assertNotNull(result);
        assertTrue(result.contains("<!DOCTYPE html>"));
        assertTrue(result.contains("<html lang=\"en\">"));
    }

    @Test
    public void testLoremGeneration() {
        String result = EmmetParser.expandHtml("lorem", null);
        assertNotNull(result);
        assertTrue(result.startsWith("Lorem"));
        assertTrue(result.endsWith("."));

        String result10 = EmmetParser.expandHtml("lorem10", null);
        assertNotNull(result10);
        assertTrue(result10.startsWith("Lorem"));
    }

    @Test
    public void testHtmlAbbreviationBasic() {
        String result = EmmetParser.expandHtml("div>p", null);
        assertNotNull(result);
        assertTrue(result.contains("<div>"));
        assertTrue(result.contains("<p>"));
    }

    @Test
    public void testHtmlAliases() {
        String linkCss = EmmetParser.expandHtml("link:css", null);
        assertNotNull(linkCss);
        assertTrue(linkCss.contains("rel=\"stylesheet\""));
        assertTrue(linkCss.contains("href=\"style.css\""));

        String metaVp = EmmetParser.expandHtml("meta:vp", null);
        assertNotNull(metaVp);
        assertTrue(metaVp.contains("viewport"));
        assertTrue(metaVp.contains("width=device-width"));

        String scriptSrc = EmmetParser.expandHtml("script:src", null);
        assertNotNull(scriptSrc);
        assertTrue(scriptSrc.contains("<script src=\""));

        String inputCheckbox = EmmetParser.expandHtml("input:checkbox", null);
        assertNotNull(inputCheckbox);
        assertTrue(inputCheckbox.contains("type=\"checkbox\""));

        String inputPassword = EmmetParser.expandHtml("input:password", null);
        assertNotNull(inputPassword);
        assertTrue(inputPassword.contains("type=\"password\""));

        String formPost = EmmetParser.expandHtml("form:post", null);
        assertNotNull(formPost);
        assertTrue(formPost.contains("method=\"post\""));

        String btnSubmit = EmmetParser.expandHtml("btn:s", null);
        assertNotNull(btnSubmit);
        assertTrue(btnSubmit.contains("<button type=\"submit\">"));

        String selectPlus = EmmetParser.expandHtml("select+", null);
        assertNotNull(selectPlus);
        assertTrue(selectPlus.contains("<select"));
        assertTrue(selectPlus.contains("<option"));

        String ulPlus = EmmetParser.expandHtml("ul+", null);
        assertNotNull(ulPlus);
        assertTrue(ulPlus.contains("<ul>"));
        assertTrue(ulPlus.contains("<li>"));
    }

    @Test
    public void testMultiplicationAndChildDistribution() {
        // ul>li*3>a must attach <a> to ALL 3 <li> elements
        String result = EmmetParser.expandHtml("ul>li*3>a", null);
        assertNotNull(result);

        int aCount = 0;
        int idx = 0;
        while ((idx = result.indexOf("<a ", idx)) != -1) {
            aCount++;
            idx += 3;
        }
        assertEquals(3, aCount);

        int liCount = 0;
        idx = 0;
        while ((idx = result.indexOf("<li", idx)) != -1) {
            liCount++;
            idx += 3;
        }
        assertEquals(3, liCount);
    }

    @Test
    public void testClimbUpOperator() {
        // div>p>span^a -> <span> climbs up to <p>, so <a> is a sibling of <p> inside <div>
        String result = EmmetParser.expandHtml("div>p>span^a", null);
        assertNotNull(result);

        int pStart = result.indexOf("<p>");
        int pEnd = result.indexOf("</p>");
        int aStart = result.indexOf("<a ");
        int divEnd = result.indexOf("</div>");

        assertTrue(pStart >= 0);
        assertTrue(pEnd > pStart);
        assertTrue(aStart > pEnd); // <a> is outside <p>
        assertTrue(divEnd > aStart); // <a> is inside <div>
    }

    @Test
    public void testClimbUpDoubleOperator() {
        // div>ul>li^^p -> climbs up 2 levels (past <ul>, past <div>) to become sibling of <div>
        String result = EmmetParser.expandHtml("div>ul>li^^p", null);
        assertNotNull(result);

        int divEnd = result.indexOf("</div>");
        int pStart = result.indexOf("<p>");
        assertTrue(divEnd >= 0);
        assertTrue(pStart > divEnd); // <p> is after </div>
    }

    @Test
    public void testMultipleAttributesAndBooleans() {
        String result = EmmetParser.expandHtml("a[href=\"#\"][target=\"_blank\"]", null);
        assertNotNull(result);
        assertTrue(result.contains("href=\"#\""));
        assertTrue(result.contains("target=\"_blank\""));

        String btnDisabled = EmmetParser.expandHtml("button[disabled]", null);
        assertNotNull(btnDisabled);
        assertTrue(btnDisabled.contains("<button disabled>"));

        String inputAttr = EmmetParser.expandHtml("input[type=text name=user]", null);
        assertNotNull(inputAttr);
        assertTrue(inputAttr.contains("type=\"text\""));
        assertTrue(inputAttr.contains("name=\"user\""));
    }

    @Test
    public void testItemNumbering() {
        String result = EmmetParser.expandHtml("ul>li.item-$*3", null);
        assertNotNull(result);
        assertTrue(result.contains("class=\"item-1\""));
        assertTrue(result.contains("class=\"item-2\""));
        assertTrue(result.contains("class=\"item-3\""));

        String resultPadded = EmmetParser.expandHtml("ul>li.item-$$*3", null);
        assertNotNull(resultPadded);
        assertTrue(resultPadded.contains("class=\"item-01\""));
        assertTrue(resultPadded.contains("class=\"item-02\""));
        assertTrue(resultPadded.contains("class=\"item-03\""));

        String resultAttrs = EmmetParser.expandHtml("ul>li[data-id=$]*3", null);
        assertNotNull(resultAttrs);
        assertTrue(resultAttrs.contains("data-id=\"1\""));
        assertTrue(resultAttrs.contains("data-id=\"2\""));
        assertTrue(resultAttrs.contains("data-id=\"3\""));
    }

    @Test
    public void testContextualDefaultTags() {
        String ulItem = EmmetParser.expandHtml("ul>.item", null);
        assertNotNull(ulItem);
        assertTrue(ulItem.contains("<li class=\"item\">"));

        String tableRow = EmmetParser.expandHtml("table>.row", null);
        assertNotNull(tableRow);
        assertTrue(tableRow.contains("<tr class=\"row\">"));

        String trCell = EmmetParser.expandHtml("tr>.cell", null);
        assertNotNull(trCell);
        assertTrue(trCell.contains("<td class=\"cell\">"));

        String selectOpt = EmmetParser.expandHtml("select>.opt", null);
        assertNotNull(selectOpt);
        assertTrue(selectOpt.contains("<option class=\"opt\">"));

        String defaultDiv = EmmetParser.expandHtml(".box", null);
        assertNotNull(defaultDiv);
        assertTrue(defaultDiv.contains("<div class=\"box\">"));
    }

    @Test
    public void testTextNodes() {
        String result = EmmetParser.expandHtml("p{Hello World}", null);
        assertNotNull(result);
        assertTrue(result.contains("<p>Hello World</p>"));

        String combined = EmmetParser.expandHtml("p>{Click }+a{here}+{ to continue}", null);
        assertNotNull(combined);
        assertTrue(combined.contains("Click "));
        assertTrue(combined.contains("here</a>"));
        assertTrue(combined.contains("to continue"));
    }

    @Test
    public void testCssNamedAbbreviation() {
        assertEquals("display: flex;", EmmetParser.expandCss("df"));
        assertEquals("background-color: |;", EmmetParser.expandCss("bgc"));
        assertEquals("position: absolute;", EmmetParser.expandCss("pos:a"));
        assertEquals("position: relative;", EmmetParser.expandCss("pos:r"));
        assertEquals("text-align: center;", EmmetParser.expandCss("ta:c"));
        assertEquals("cursor: pointer;", EmmetParser.expandCss("cur:p"));
        assertEquals("overflow: hidden;", EmmetParser.expandCss("ov:h"));
        assertEquals("box-sizing: border-box;", EmmetParser.expandCss("bs:bb"));
        assertEquals("margin: auto;", EmmetParser.expandCss("m:a"));
    }

    @Test
    public void testCssNumericAbbreviation() {
        assertEquals("margin: 10px;", EmmetParser.expandCss("m10"));
        assertEquals("padding: 20px;", EmmetParser.expandCss("p20"));
        assertEquals("margin: 10px 20px;", EmmetParser.expandCss("m10-20"));
        assertEquals("width: 100%;", EmmetParser.expandCss("w100p"));
        assertEquals("width: 100%;", EmmetParser.expandCss("w100%"));
    }

    @Test
    public void testCssNegativeAndDecimals() {
        assertEquals("margin: -10px;", EmmetParser.expandCss("m-10"));
        assertEquals("margin: -10px -20px;", EmmetParser.expandCss("m-10--20"));
        assertEquals("margin: 10px -20px;", EmmetParser.expandCss("m10--20"));
        assertEquals("margin: -10px 20px;", EmmetParser.expandCss("m-10-20"));
        assertEquals("opacity: 0.5;", EmmetParser.expandCss("op0.5"));
        assertEquals("line-height: 1.5;", EmmetParser.expandCss("lh1.5"));
        assertEquals("margin: 1.5rem;", EmmetParser.expandCss("m1.5rem"));
        assertEquals("top: -5px;", EmmetParser.expandCss("t-5"));
    }

    @Test
    public void testCssHexColorsAndImportant() {
        assertEquals("color: #fff;", EmmetParser.expandCss("c#f"));
        assertEquals("color: #333;", EmmetParser.expandCss("c#333"));
        assertEquals("background: #000;", EmmetParser.expandCss("bg#000"));
        assertEquals("margin: 10px !important;", EmmetParser.expandCss("m10!"));
        assertEquals("display: flex !important;", EmmetParser.expandCss("df!"));
    }

    @Test
    public void testCssDefinitionsLoaded() {
        assertNotNull(EmmetCssDefinitions.CSS_ABBREVS.get("df"));
        assertNotNull(EmmetCssDefinitions.CSS_PROP_MAP.get("m"));
    }
}
