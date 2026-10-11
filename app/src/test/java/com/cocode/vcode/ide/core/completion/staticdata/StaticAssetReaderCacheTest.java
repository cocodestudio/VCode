package com.cocode.vcode.ide.core.completion.staticdata;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying that StaticAssetReader in-memory ConcurrentHashMap cache
 * retains asset strings and properly invalidates on override and test reset.
 */
public class StaticAssetReaderCacheTest {

    @After
    public void cleanup() {
        StaticAssetReader.resetForTest();
        StaticCompletionLoader.resetForTest();
    }

    @Test
    public void testReadAssetCachesString() {
        String assetPath = "test/fake_asset.json";
        String content = "{\"hello\":\"world\"}";
        StaticAssetReader.setAssetOverride(assetPath, content);

        String read1 = StaticAssetReader.readAsset(assetPath);
        assertEquals(content, read1);

        String read2 = StaticAssetReader.readAsset(assetPath);
        assertEquals(content, read2);
    }

    @Test
    public void testOverrideInvalidatesCache() {
        String assetPath = "test/dynamic.json";
        StaticAssetReader.setAssetOverride(assetPath, "{\"v\":1}");
        assertEquals("{\"v\":1}", StaticAssetReader.readAsset(assetPath));

        StaticAssetReader.setAssetOverride(assetPath, "{\"v\":2}");
        assertEquals("{\"v\":2}", StaticAssetReader.readAsset(assetPath));
    }

    @Test
    public void testResetClearsOverridesAndCache() {
        String assetPath = "test/ephemeral.json";
        StaticAssetReader.setAssetOverride(assetPath, "{\"ephemeral\":true}");
        assertEquals("{\"ephemeral\":true}", StaticAssetReader.readAsset(assetPath));

        StaticAssetReader.resetForTest();
        assertEquals("", StaticAssetReader.readAsset(assetPath));
    }
}
