package com.cocode.vcode.ide.core.autocomplete;

import com.cocode.vcode.ide.core.language.css.EmmetCssDefinitions;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
    }

    @Test
    public void testHtmlAbbreviation() {
        String result = EmmetParser.expandHtml("div>p", null);
        assertNotNull(result);
        assertTrue(result.contains("<div>"));
        assertTrue(result.contains("<p>"));
    }

    @Test
    public void testCssNamedAbbreviation() {
        String result = EmmetParser.expandCss("df");
        assertEquals("display: flex;", result);

        String resultBgc = EmmetParser.expandCss("bgc");
        assertEquals("background-color: |;", resultBgc);
    }

    @Test
    public void testCssNumericAbbreviation() {
        String result = EmmetParser.expandCss("m10");
        assertEquals("margin: 10px;", result);

        String resultP20 = EmmetParser.expandCss("p20");
        assertEquals("padding: 20px;", resultP20);
    }

    @Test
    public void testCssDefinitionsLoaded() {
        assertNotNull(EmmetCssDefinitions.CSS_ABBREVS.get("df"));
        assertNotNull(EmmetCssDefinitions.CSS_PROP_MAP.get("m"));
    }
}
