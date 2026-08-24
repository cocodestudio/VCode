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
}
