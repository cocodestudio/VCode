package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.completion.staticdata.StaticAssetReader;
import com.cocode.vcode.ide.core.model.CompletionItem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class JsBuiltinTypesTest extends BaseJsAstTest {

    @Before
    public void setUp() {
        JsAutoCompleteEngine.loadBuiltinTypeTable();
    }

    @After
    public void tearDown() {
        StaticAssetReader.setAssetOverride("types/builtin_types.json", null);
        JsAutoCompleteEngine.loadBuiltinTypeTable();
    }

    @Test
    public void testBuiltinTypesLoadedFromAssets() {
        assertFalse("BuiltinTypeTable should be populated from assets", JsAutoCompleteEngine.BuiltinTypeTable.isEmpty());

        // String method mappings
        assertEquals("@ARRAY", JsAutoCompleteEngine.getBuiltinType("@STRING", "split"));
        assertEquals("@ARRAY", JsAutoCompleteEngine.getBuiltinType("@STRING", "match"));
        assertEquals("@STRING", JsAutoCompleteEngine.getBuiltinType("@STRING", "toUpperCase"));
        assertEquals("@NUMBER", JsAutoCompleteEngine.getBuiltinType("@STRING", "indexOf"));
        assertEquals("@NUMBER", JsAutoCompleteEngine.getBuiltinType("@STRING", "length"));
        assertEquals("@BOOLEAN", JsAutoCompleteEngine.getBuiltinType("@STRING", "includes"));

        // Array method mappings
        assertEquals("@ARRAY", JsAutoCompleteEngine.getBuiltinType("@ARRAY", "map"));
        assertEquals("@ARRAY", JsAutoCompleteEngine.getBuiltinType("@ARRAY", "filter"));
        assertEquals("@ARRAY", JsAutoCompleteEngine.getBuiltinType("@ARRAY", "slice"));
        assertEquals("@STRING", JsAutoCompleteEngine.getBuiltinType("@ARRAY", "join"));
        assertEquals("@NUMBER", JsAutoCompleteEngine.getBuiltinType("@ARRAY", "length"));
        assertEquals("@BOOLEAN", JsAutoCompleteEngine.getBuiltinType("@ARRAY", "includes"));
        assertEquals("@UNDEFINED", JsAutoCompleteEngine.getBuiltinType("@ARRAY", "forEach"));

        // Number method mappings
        assertEquals("@STRING", JsAutoCompleteEngine.getBuiltinType("@NUMBER", "toFixed"));
        assertEquals("@STRING", JsAutoCompleteEngine.getBuiltinType("@NUMBER", "toString"));

        // Promise method mappings
        assertEquals("@PROMISE", JsAutoCompleteEngine.getBuiltinType("@PROMISE", "then"));
        assertEquals("@PROMISE", JsAutoCompleteEngine.getBuiltinType("@PROMISE", "catch"));

        // Boolean method mappings
        assertEquals("@STRING", JsAutoCompleteEngine.getBuiltinType("@BOOLEAN", "toString"));
        assertEquals("@BOOLEAN", JsAutoCompleteEngine.getBuiltinType("@BOOLEAN", "valueOf"));
    }

    @Test
    public void testChainedDotCompletionWithStringSplitToArray() {
        String code = "const s = 'hello'; s.split(',').";
        List<CompletionItem> comps = getCompletions(code, code.length());

        boolean hasMap = false;
        boolean hasFilter = false;
        boolean hasJoin = false;

        for (CompletionItem item : comps) {
            if ("map".equals(item.getLabel())) hasMap = true;
            if ("filter".equals(item.getLabel())) hasFilter = true;
            if ("join".equals(item.getLabel())) hasJoin = true;
        }

        assertTrue("String.split() should resolve to array methods, expected 'map'", hasMap);
        assertTrue("String.split() should resolve to array methods, expected 'filter'", hasFilter);
        assertTrue("String.split() should resolve to array methods, expected 'join'", hasJoin);
    }

    @Test
    public void testChainedDotCompletionWithArrayJoinToString() {
        String code = "const arr = ['a', 'b']; arr.join('-').";
        List<CompletionItem> comps = getCompletions(code, code.length());

        boolean hasToUpperCase = false;
        boolean hasTrim = false;
        boolean hasSubstring = false;

        for (CompletionItem item : comps) {
            if ("toUpperCase".equals(item.getLabel())) hasToUpperCase = true;
            if ("trim".equals(item.getLabel())) hasTrim = true;
            if ("substring".equals(item.getLabel())) hasSubstring = true;
        }

        assertTrue("Array.join() should resolve to string methods, expected 'toUpperCase'", hasToUpperCase);
        assertTrue("Array.join() should resolve to string methods, expected 'trim'", hasTrim);
        assertTrue("Array.join() should resolve to string methods, expected 'substring'", hasSubstring);
    }

    @Test
    public void testDynamicAssetOverrideAndReload() {
        String overrideJson = "{\"@CUSTOM\": {\"foo\": \"@BAR\"}}";
        StaticAssetReader.setAssetOverride("types/builtin_types.json", overrideJson);
        JsAutoCompleteEngine.loadBuiltinTypeTable();

        assertEquals("@BAR", JsAutoCompleteEngine.getBuiltinType("@CUSTOM", "foo"));
        assertNull(JsAutoCompleteEngine.getBuiltinType("@STRING", "split"));

        // Reset and verify restoration
        StaticAssetReader.setAssetOverride("types/builtin_types.json", null);
        JsAutoCompleteEngine.loadBuiltinTypeTable();
        assertEquals("@ARRAY", JsAutoCompleteEngine.getBuiltinType("@STRING", "split"));
    }
}
