package com.cocode.vcode.ide.core.autocomplete;

import android.content.Context;
import org.robolectric.RuntimeEnvironment;

import com.cocode.vcode.ide.core.model.CompletionItem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
public class AutoCompleteEngineTest {

    private AutoCompleteEngine engine;

    @Before
    public void setup() {
        Context context = RuntimeEnvironment.getApplication();
        engine = new AutoCompleteEngine(context) {
            @Override
            public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
                return Collections.emptyList();
            }
        };
    }

    @Test
    public void testGetWordBeforeCursor() {
        String text = "function test(abc) { const x = a";
        // cursor is at the end of 'a'
        int cursor = text.length();
        
        String word = engine.getWordBeforeCursor(text, cursor);
        assertEquals("a", word);
    }

    @Test
    public void testGetWordBeforeCursorWithDot() {
        String text = "console.lo";
        int cursor = text.length();
        
        String word = engine.getWordBeforeCursor(text, cursor);
        assertEquals("lo", word);
    }
    
    @Test
    public void testGetWordBeforeCursorEmpty() {
        String text = "function test() { ";
        int cursor = text.length();
        
        String word = engine.getWordBeforeCursor(text, cursor);
        assertEquals("", word);
    }
    
    @Test
    public void testGetNonWhitespaceBeforeCursor() {
        String text = "class MyClass extends BaseClass  ";
        int cursor = text.length();
        
        // It should skip the trailing spaces and return "BaseClass"
        String word = engine.getNonWhitespaceBeforeCursor(text, cursor);
        assertEquals("BaseClass", word);
    }
    
    @Test
    public void testIsInsideStringLiteral() {
        String text1 = "const name = \"Hello\";";
        // cursor before 'H'
        assertTrue(engine.isInsideStringLiteral(text1, 14));
        // cursor after 'o'
        assertTrue(engine.isInsideStringLiteral(text1, 19));
        
        String text2 = "const name = 'Hello';";
        assertTrue(engine.isInsideStringLiteral(text2, 14));
        
        String text3 = "const name = Hello;";
        assertFalse(engine.isInsideStringLiteral(text3, 14));
        
        String text4 = "console.log(\"Hello\\\"World\");";
        // inside the escaped quote
        assertTrue(engine.isInsideStringLiteral(text4, 20));
    }
    
    @Test
    public void testFuzzyFilter() {
        List<CompletionItem> items = new java.util.ArrayList<>();
        items.add(new CompletionItem("document", "document", "var", CompletionItem.Type.KEYWORD, 1));
        items.add(new CompletionItem("documentElement", "documentElement", "var", CompletionItem.Type.CSS_PROPERTY, 2));
        items.add(new CompletionItem("dog", "dog", "var", CompletionItem.Type.VALUE, 3));
        
        // Match exact prefix
        List<CompletionItem> filtered1 = engine.fuzzyFilter(items, "doc");
        assertEquals(2, filtered1.size());
        assertEquals("document", filtered1.get(0).getLabel());
        assertEquals("documentElement", filtered1.get(1).getLabel());
        
        // Match fuzzy
        List<CompletionItem> filtered2 = engine.fuzzyFilter(items, "docEl");
        assertEquals(1, filtered2.size());
        assertEquals("documentElement", filtered2.get(0).getLabel());
        
        // Match empty returns all sorted by type priority
        List<CompletionItem> filtered3 = engine.fuzzyFilter(items, "");
        assertEquals(3, filtered3.size());
    }
}
