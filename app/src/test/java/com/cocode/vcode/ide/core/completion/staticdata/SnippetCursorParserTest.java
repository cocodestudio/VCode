package com.cocode.vcode.ide.core.completion.staticdata;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Acceptance tests for M.2: shared snippet cursor-marker parser.
 */
public class SnippetCursorParserTest {

    @Test
    public void parsesIfBlockSnippet() {
        SnippetCursorParser.Result r = SnippetCursorParser.parse("if (|) {\n  \n}");
        assertNotNull(r);
        assertEquals("if () {\n  \n}", r.insertText);
        assertEquals(4, r.cursorOffset);
    }

    @Test
    public void parsesSemicolonBetweenTokens() {
        // The M.2 acceptance check: "semi": |true inserts "semi": true
        // with cursor immediately before `true`.
        SnippetCursorParser.Result r = SnippetCursorParser.parse("\"semi\": |true");
        assertNotNull(r);
        assertEquals("\"semi\": true", r.insertText);
        // "\"semi\": " is 8 characters: " s e m i " : <space>.
        assertEquals(8, r.cursorOffset);
    }

    @Test
    public void parsesCursorAtStart() {
        SnippetCursorParser.Result r = SnippetCursorParser.parse("|abc");
        assertNotNull(r);
        assertEquals("abc", r.insertText);
        assertEquals(0, r.cursorOffset);
    }

    @Test
    public void parsesCursorAtEnd() {
        SnippetCursorParser.Result r = SnippetCursorParser.parse("abc|");
        assertNotNull(r);
        assertEquals("abc", r.insertText);
        assertEquals(3, r.cursorOffset);
    }

    @Test
    public void parsesNewlinesInSnippet() {
        SnippetCursorParser.Result r = SnippetCursorParser.parse("{\n  |\n}");
        assertNotNull(r);
        assertEquals("{\n  \n}", r.insertText);
        // Offset counts the prefix character count: "{\n  " = 4 chars.
        assertEquals(4, r.cursorOffset);
    }

    @Test
    public void nullOrEmptyReturnsNull() {
        assertNull(SnippetCursorParser.parse(null));
        assertNull(SnippetCursorParser.parse(""));
    }

    @Test
    public void noMarkerReturnsNull() {
        assertNull(SnippetCursorParser.parse("no pipe here"));
    }

    @Test
    public void stripMarkerConvenience() {
        assertEquals("abc", SnippetCursorParser.stripMarker("a|bc"));
        // No marker → returns input unchanged.
        assertEquals("abc", SnippetCursorParser.stripMarker("abc"));
        assertNull(SnippetCursorParser.stripMarker(null));
    }

    @Test
    public void parsesPrettierrcJsonSnippet() {
        // The M.19 acceptance check: parsing the prettierrc snippet
        // produces valid JSON with the cursor immediately before true.
        String snippet = "{\n  \"semi\": |true,\n  \"singleQuote\": true\n}";
        SnippetCursorParser.Result r = SnippetCursorParser.parse(snippet);
        assertNotNull(r);
        // "{\n  \"semi\": " = 12 chars: { <\n> <sp> <sp> " s e m i " : <sp>.
        assertEquals(12, r.cursorOffset);
        assertEquals("{\n  \"semi\": true,\n  \"singleQuote\": true\n}",
                r.insertText);
    }

    @Test
    public void parsesApiResponseSnippet() {
        // From json_snippets.json "api response":
        // "data": |null — cursor must land before `null`.
        String snippet = "{\n  \"data\": |null\n}";
        SnippetCursorParser.Result r = SnippetCursorParser.parse(snippet);
        assertNotNull(r);
        // "{\n  \"data\": " = 12 chars: { \n <sp> <sp> " d a t a " : <sp>.
        assertEquals(12, r.cursorOffset);
        assertEquals("{\n  \"data\": null\n}", r.insertText);
    }

    @Test
    public void parsesApiErrorSnippet() {
        // From json_snippets.json "api error": "code": |400
        String snippet = "{\n  \"code\": |400\n}";
        SnippetCursorParser.Result r = SnippetCursorParser.parse(snippet);
        assertNotNull(r);
        // "{\n  \"code\": " = 12 chars: { \n <sp> <sp> " c o d e " : <sp>.
        assertEquals(12, r.cursorOffset);
        assertEquals("{\n  \"code\": 400\n}", r.insertText);
    }

    @Test
    public void parsesHtmlTagSnippet() {
        // html_tags.json entry for "div": "<div>|</div>"
        SnippetCursorParser.Result r = SnippetCursorParser.parse("<div>|</div>");
        assertNotNull(r);
        assertEquals("<div></div>", r.insertText);
        assertEquals(5, r.cursorOffset);
    }

    @Test
    public void cursorOffsetIsCharIndexNotByteIndex() {
        // UTF-16 surrogate pair: U+1F600 = "😀" = 2 chars in Java
        // String. The | marker is at char index 5 (after "abc" +
        // the surrogate pair, which counts as 2 chars).
        SnippetCursorParser.Result r = SnippetCursorParser.parse("abc\uD83D\uDE00|x");
        assertNotNull(r);
        assertEquals("abc\uD83D\uDE00x", r.insertText);
        assertEquals(5, r.cursorOffset);
    }

    @Test
    public void multiMarkerUsesFirstMarker() {
        // Data shouldn't have this, but be defensive: if multiple
        // markers exist, the first one's position is used; the rest
        // are stripped.
        SnippetCursorParser.Result r = SnippetCursorParser.parse("a|b|c");
        assertNotNull(r);
        assertEquals("abc", r.insertText);
        assertEquals(1, r.cursorOffset);
    }

    // --- M.19: |-inside-token edge cases --------------------------------

    @Test
    public void m19_prettierrcSnippetParsesWithCursorBeforeTrue() {
        // The M.19 acceptance check verbatim: inserting the
        // `prettierrc.json` snippet produces valid JSON with
        // `"semi": true` and the cursor immediately before `true`.
        String snippet = "{\n  \"semi\": |true,\n  \"singleQuote\": true\n}";
        SnippetCursorParser.Result r = SnippetCursorParser.parse(snippet);
        assertNotNull(r);
        assertEquals("{\n  \"semi\": true,\n  \"singleQuote\": true\n}", r.insertText);
        // The cursor must land at the position where `|` was —
        // immediately before the `t` of `true`.
        int expectedOffset = snippet.indexOf("|true");
        assertEquals(expectedOffset, r.cursorOffset);
        // Sanity: the byte at the cursor position is `t`.
        assertEquals('t', r.insertText.charAt(r.cursorOffset));
    }

    @Test
    public void m19_apiResponseSnippetPreservesNullToken() {
        // From `json_snippets.json` "api response": "data": |null
        String snippet = "{\n  \"data\": |null\n}";
        SnippetCursorParser.Result r = SnippetCursorParser.parse(snippet);
        assertNotNull(r);
        // Cursor lands at the `n` of `null`.
        int expectedOffset = snippet.indexOf("|null");
        assertEquals(expectedOffset, r.cursorOffset);
        assertEquals('n', r.insertText.charAt(r.cursorOffset));
        // Inserted text is valid JSON.
        assertEquals("{\n  \"data\": null\n}", r.insertText);
    }

    @Test
    public void m19_apiErrorSnippetPreservesNumberToken() {
        // From `json_snippets.json` "api error": "code": |400
        String snippet = "{\n  \"code\": |400\n}";
        SnippetCursorParser.Result r = SnippetCursorParser.parse(snippet);
        assertNotNull(r);
        int expectedOffset = snippet.indexOf("|400");
        assertEquals(expectedOffset, r.cursorOffset);
        assertEquals('4', r.insertText.charAt(r.cursorOffset));
        assertEquals("{\n  \"code\": 400\n}", r.insertText);
    }
}
