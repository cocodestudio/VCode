package com.cocode.vcode.ide.core.language.css;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CssDefinitionsTest {

    @Test
    public void testCssDefinitionsLoaded() {
        CssDefinitions.loadCssDefinitions();

        assertFalse("PSEUDO_ITEMS should not be empty", CssDefinitions.PSEUDO_ITEMS.isEmpty());
        assertFalse("AT_RULE_ITEMS should not be empty", CssDefinitions.AT_RULE_ITEMS.isEmpty());
        assertFalse("MEDIA_KEYWORDS should not be empty", CssDefinitions.MEDIA_KEYWORDS.isEmpty());
        assertFalse("MEDIA_FEATURES should not be empty", CssDefinitions.MEDIA_FEATURES.isEmpty());

        // Pseudo-class check
        boolean hasHover = false;
        for (var item : CssDefinitions.PSEUDO_ITEMS) {
            if (":hover".equals(item.getLabel())) {
                hasHover = true;
                break;
            }
        }
        assertTrue("Expected ':hover' in PSEUDO_ITEMS", hasHover);

        // At-rule check
        boolean hasMedia = false;
        for (var item : CssDefinitions.AT_RULE_ITEMS) {
            if ("@media".equals(item.getLabel())) {
                hasMedia = true;
                break;
            }
        }
        assertTrue("Expected '@media' in AT_RULE_ITEMS", hasMedia);

        // Media keywords check
        assertTrue("Expected 'screen' in MEDIA_KEYWORDS", CssDefinitions.MEDIA_KEYWORDS.contains("screen"));

        // Media features check
        boolean hasMaxWidth = false;
        for (var entry : CssDefinitions.MEDIA_FEATURES) {
            if ("max-width".equals(entry.label)) {
                hasMaxWidth = true;
                break;
            }
        }
        assertTrue("Expected 'max-width' in MEDIA_FEATURES", hasMaxWidth);
    }
}
