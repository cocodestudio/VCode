package com.cocode.vcode.ide.core.language.html;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HtmlDefinitionsTest {

    @Test
    public void testHtmlDefinitionsLoaded() {
        HtmlDefinitions.loadHtmlDefinitions();

        assertFalse("GLOBAL_ATTRS should not be empty", HtmlDefinitions.GLOBAL_ATTRS.isEmpty());
        assertFalse("EVENT_ATTRS should not be empty", HtmlDefinitions.EVENT_ATTRS.isEmpty());
        assertFalse("DOCTYPE_ITEMS should not be empty", HtmlDefinitions.DOCTYPE_ITEMS.isEmpty());
        assertFalse("ENTITY_ITEMS should not be empty", HtmlDefinitions.ENTITY_ITEMS.isEmpty());
        assertFalse("ATTR_VALUES should not be empty", HtmlDefinitions.ATTR_VALUES.isEmpty());

        // Global attribute check
        boolean hasClass = false;
        for (var item : HtmlDefinitions.GLOBAL_ATTRS) {
            if ("class".equals(item.getLabel())) {
                hasClass = true;
                break;
            }
        }
        assertTrue("Expected 'class' in GLOBAL_ATTRS", hasClass);

        // Event attribute check
        boolean hasOnClick = false;
        for (var item : HtmlDefinitions.EVENT_ATTRS) {
            if ("onclick".equals(item.getLabel())) {
                hasOnClick = true;
                break;
            }
        }
        assertTrue("Expected 'onclick' in EVENT_ATTRS", hasOnClick);

        // Doctype check
        boolean hasDoctype = false;
        for (var item : HtmlDefinitions.DOCTYPE_ITEMS) {
            if ("<!DOCTYPE html>".equals(item.getLabel())) {
                hasDoctype = true;
                break;
            }
        }
        assertTrue("Expected '<!DOCTYPE html>' in DOCTYPE_ITEMS", hasDoctype);

        // Entity check
        boolean hasAmp = false;
        for (var item : HtmlDefinitions.ENTITY_ITEMS) {
            if ("&amp;".equals(item.getLabel())) {
                hasAmp = true;
                break;
            }
        }
        assertTrue("Expected '&amp;' in ENTITY_ITEMS", hasAmp);

        // Attribute values check
        String[] types = HtmlDefinitions.ATTR_VALUES.get("type");
        assertNotNull("Expected values for 'type' attribute", types);
        assertTrue(types.length > 0);
    }
}
