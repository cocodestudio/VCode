package com.cocode.vcode.ide.core.language.json;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JsonContextScannerTest {

    @Test
    public void testFileEmpty() {
        JsonContextScanner.JsonContext ctx = JsonContextScanner.scan("   \n\t  ", 2);
        assertEquals(JsonContextScanner.ContextType.FILE_EMPTY, ctx.type);
    }

    @Test
    public void testCommentsSuppression() {
        String jsonc = "{\n  // A single line comment\n  \"key\": \"value\"\n}";
        int insideComment = jsonc.indexOf("comment");
        JsonContextScanner.JsonContext ctx = JsonContextScanner.scan(jsonc, insideComment);
        assertEquals(JsonContextScanner.ContextType.COMMENT, ctx.type);
        assertTrue(ctx.isInsideComment);

        String jsoncBlock = "{\n  /* A block\n     comment */\n  \"key\": \"value\"\n}";
        int insideBlock = jsoncBlock.indexOf("comment");
        JsonContextScanner.JsonContext ctxBlock = JsonContextScanner.scan(jsoncBlock, insideBlock);
        assertEquals(JsonContextScanner.ContextType.COMMENT, ctxBlock.type);
        assertTrue(ctxBlock.isInsideComment);
    }

    @Test
    public void testObjectKeyContextRoot() {
        String json = "{\n  \"n\"\n}";
        int offset = json.indexOf("\"n\"") + 2; // after 'n', before '"'
        JsonContextScanner.JsonContext ctx = JsonContextScanner.scan(json, offset);
        assertEquals(JsonContextScanner.ContextType.OBJECT_KEY, ctx.type);
        assertTrue(ctx.inQuotes);
        assertEquals("n", ctx.word);
        assertTrue(ctx.propertyPath.isEmpty());
    }

    @Test
    public void testObjectKeyExcludesExistingSiblingKeys() {
        String json = "{\n  \"name\": \"vcode\",\n  \"v\"\n}";
        int offset = json.indexOf("\"v\"") + 2; // inside "v"
        JsonContextScanner.JsonContext ctx = JsonContextScanner.scan(json, offset);
        assertEquals(JsonContextScanner.ContextType.OBJECT_KEY, ctx.type);
        assertTrue(ctx.inQuotes);
        assertEquals("v", ctx.word);
        assertTrue("Existing sibling key 'name' must be tracked", ctx.existingKeys.contains("name"));
        assertFalse("Current key 'v' being edited should not be tracked as existing sibling", ctx.existingKeys.contains("v"));
    }

    @Test
    public void testObjectKeyNestedCompilerOptions() {
        String json = "{\n  \"compilerOptions\": {\n    \"tar\"\n  }\n}";
        int offset = json.indexOf("\"tar\"") + 4; // inside "tar"
        JsonContextScanner.JsonContext ctx = JsonContextScanner.scan(json, offset);
        assertEquals(JsonContextScanner.ContextType.OBJECT_KEY, ctx.type);
        assertTrue(ctx.inQuotes);
        assertEquals("tar", ctx.word);
        assertEquals(1, ctx.propertyPath.size());
        assertEquals("compilerOptions", ctx.propertyPath.get(0));
    }

    @Test
    public void testObjectValueContext() {
        String json = "{\n  \"compilerOptions\": {\n    \"target\": \"ES\"\n  }\n}";
        int offset = json.indexOf("\"ES\"") + 3; // inside "ES"
        JsonContextScanner.JsonContext ctx = JsonContextScanner.scan(json, offset);
        assertEquals(JsonContextScanner.ContextType.OBJECT_VALUE, ctx.type);
        assertTrue(ctx.inQuotes);
        assertEquals("ES", ctx.word);
        assertEquals("target", ctx.activeKey);
        assertEquals(1, ctx.propertyPath.size());
        assertEquals("compilerOptions", ctx.propertyPath.get(0));
    }

    @Test
    public void testObjectValueUnquotedBoolean() {
        String json = "{\n  \"strict\": tr\n}";
        int offset = json.lastIndexOf("tr") + 2;
        JsonContextScanner.JsonContext ctx = JsonContextScanner.scan(json, offset);
        assertEquals(JsonContextScanner.ContextType.OBJECT_VALUE, ctx.type);
        assertFalse(ctx.inQuotes);
        assertEquals("tr", ctx.word);
        assertEquals("strict", ctx.activeKey);
    }

    @Test
    public void testArrayElementContext() {
        String json = "{\n  \"compilerOptions\": {\n    \"lib\": [\n      \"ES\"\n    ]\n  }\n}";
        int offset = json.indexOf("\"ES\"") + 3;
        JsonContextScanner.JsonContext ctx = JsonContextScanner.scan(json, offset);
        assertEquals(JsonContextScanner.ContextType.ARRAY_ELEMENT, ctx.type);
        assertTrue(ctx.inQuotes);
        assertEquals("ES", ctx.word);
        assertEquals("lib", ctx.activeKey);
    }
}
