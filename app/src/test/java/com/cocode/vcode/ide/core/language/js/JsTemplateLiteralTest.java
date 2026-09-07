package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.model.CompletionItem;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class JsTemplateLiteralTest {

    private JsAutoCompleteEngine engine;

    @Before
    public void setUp() {
        engine = new JsAutoCompleteEngine(null);
    }

    @Test
    public void testLexerTemplateLiteralMultipleInterpolations() {
        String code = "`hello ${first} and ${second}`";
        TokenStream stream = JsLexer.tokenize(code);
        assertNotNull(stream);

        // Check that 'first' is TK_IDENTIFIER
        int firstIdx = code.indexOf("first");
        assertEquals("Token for 'first' must be TK_IDENTIFIER",
                TokenStream.TK_IDENTIFIER, stream.types[firstIdx]);

        // Check that 'second' is TK_IDENTIFIER
        int secondIdx = code.indexOf("second");
        assertEquals("Token for 'second' must be TK_IDENTIFIER",
                TokenStream.TK_IDENTIFIER, stream.types[secondIdx]);

        // Check that string parts are TK_TEMPLATE
        assertEquals(TokenStream.TK_TEMPLATE, stream.types[1]); // 'h'
        int andIdx = code.indexOf("and");
        assertEquals(TokenStream.TK_TEMPLATE, stream.types[andIdx]); // 'a'
    }

    @Test
    public void testLexerUnclosedTemplateInterpolation() {
        String code = "const s = `hello ${user";
        TokenStream stream = JsLexer.tokenize(code);
        assertNotNull(stream);

        int userIdx = code.indexOf("user");
        assertEquals("Unclosed expression while typing must have TK_IDENTIFIER for 'user'",
                TokenStream.TK_IDENTIFIER, stream.types[userIdx]);
    }

    @Test
    public void testAutoCompleteInsideTemplateExpression() {
        String code = "const message = 42;\nconst s = `val: ${m`;";
        int cursorPos = code.indexOf("${m") + 3; // cursor right after 'm'

        List<CompletionItem> items = engine.getSuggestions(code, cursorPos);
        assertNotNull(items);
        assertFalse("Suggestions inside ${m should not be empty", items.isEmpty());

        boolean foundMessage = false;
        for (CompletionItem item : items) {
            if ("message".equals(item.getLabel())) {
                foundMessage = true;
                break;
            }
        }
        assertTrue("Expected 'message' to be suggested inside ${m", foundMessage);
    }

    @Test
    public void testAutoCompleteTriggerAtEmptyInterpolationBrace() {
        String code = "const s = `hello ${}`;";
        int cursorPos = code.indexOf("${}") + 2; // right between { and }

        List<CompletionItem> items = engine.getSuggestions(code, cursorPos);
        assertNotNull(items);
        assertFalse("Suggestions inside empty ${} should be triggered and non-empty", items.isEmpty());
    }

    @Test
    public void testAutoCompleteBlockedInTemplateLiteralString() {
        String code = "const s = `hello world`;";
        int cursorPos = code.indexOf("world") + 2; // inside "world"

        List<CompletionItem> items = engine.getSuggestions(code, cursorPos);
        assertTrue("Suggestions inside string literal part of template should be empty",
                items == null || items.isEmpty());
    }
}
