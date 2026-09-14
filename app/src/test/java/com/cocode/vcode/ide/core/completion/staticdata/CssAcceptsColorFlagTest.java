package com.cocode.vcode.ide.core.completion.staticdata;

import org.junit.After;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for M.3: the {@code acceptsColor} flag is set on
 * every property in {@code css_properties.json} whose CSS spec value
 * type is {@code <color>}.
 *
 * <p>Per MDN (spot-checked 2024-05), the following CSS properties
 * have a {@code <color>} value type and must therefore be flagged.
 * Properties like {@code text-shadow}, {@code box-shadow},
 * {@code background}, {@code border}, {@code outline}, and
 * {@code column-rule} accept colors as part of a larger value list
 * but their value <em>type</em> is not pure {@code <color>}, so they
 * are not flagged under the M.3 spec.
 */
public class CssAcceptsColorFlagTest {

    /** The eight properties in css_properties.json whose MDN-listed
     *  value type is {@code <color>}. */
    private static final String[] EXPECTED_COLOR_PROPS = {
            "color",
            "background-color",
            "text-decoration-color",
            "border-color",
            "outline-color",
            "caret-color",
            "accent-color",
            "scrollbar-color"
    };

    @After
    public void cleanup() {
        StaticCompletionLoader.resetForTest();
        StaticAssetReader.resetForTest();
    }

    /** Load the real production JSON file from the test
     *  classpath copy. */
    private static String loadRealAsset() {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = CssAcceptsColorFlagTest.class.getResourceAsStream(
                "/css_properties.json")) {
            assertNotNull("test resource /css_properties.json must be present", is);
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

    @Test
    public void realAssetFileHasAcceptsColorOnAllColorTypedProperties() {
        StaticAssetReader.setAssetOverride(
                StaticCompletionLoader.CSS_PROPERTIES_ASSET, loadRealAsset());
        StaticCompletionItem[] items = StaticCompletionLoader.getCssProperties();
        assertNotNull(items);
        Set<String> flagged = new HashSet<>();
        for (StaticCompletionItem item : items) {
            if (item.acceptsColor) flagged.add(item.label);
        }
        for (String expected : EXPECTED_COLOR_PROPS) {
            assertTrue("expected " + expected + " to be flagged acceptsColor=true",
                    flagged.contains(expected));
        }
    }

    @Test
    public void realAssetFileHasNoAcceptsColorOnNonColorProperties() {
        StaticAssetReader.setAssetOverride(
                StaticCompletionLoader.CSS_PROPERTIES_ASSET, loadRealAsset());
        StaticCompletionItem[] items = StaticCompletionLoader.getCssProperties();
        Set<String> flagged = new HashSet<>();
        for (StaticCompletionItem item : items) {
            if (item.acceptsColor) flagged.add(item.label);
        }
        // Sanity check: a few obviously-not-color properties.
        assertFalse("display is not a color-typed property", flagged.contains("display"));
        assertFalse("margin is not a color-typed property", flagged.contains("margin"));
        assertFalse("font-size is not a color-typed property", flagged.contains("font-size"));
        assertFalse("opacity is not a color-typed property", flagged.contains("opacity"));
    }

    @Test
    public void realAssetFileFlaggedCountMatchesExpected() {
        StaticAssetReader.setAssetOverride(
                StaticCompletionLoader.CSS_PROPERTIES_ASSET, loadRealAsset());
        StaticCompletionItem[] items = StaticCompletionLoader.getCssProperties();
        int count = 0;
        for (StaticCompletionItem item : items) if (item.acceptsColor) count++;
        assertEquals(EXPECTED_COLOR_PROPS.length, count);
    }
}
