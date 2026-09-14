package com.cocode.vcode.ide.views;

import android.content.Context;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.KeyEvent;

import org.robolectric.RuntimeEnvironment;

import com.cocode.vcode.ide.core.editor.text.ContentPosition;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.FileType;
import org.robolectric.shadows.ShadowLooper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class CodeEditTextTest {

    private CodeEditText editor;
    private InputConnection inputConnection;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        // Required theme for CodeEditText instantiation
        context.setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight);
        
        editor = new CodeEditText(context);
        
        EditorInfo editorInfo = new EditorInfo();
        inputConnection = editor.onCreateInputConnection(editorInfo);
        assertNotNull("InputConnection should not be null", inputConnection);
    }

    @Test
    public void testSetTextAndGetText() {
        editor.setText("Hello\nWorld");
        assertEquals("Hello\nWorld", editor.getText().toString());
        
        // Ensure cursor is moved to start after setting text
        // (This behavior is defined in CodeEditText#setText)
        // Testing side-effects of initialization
        assertTrue("Editor should have some total length", editor.getText().length() > 0);
    }

    @Test
    public void testTypingThroughInputConnection() {
        editor.setText("hello");
        // Initial text "hello" has 5 characters. Assuming cursor is at 0 (or we manually set it).
        // Let's set selection to end of text
        inputConnection.setSelection(5, 5);
        
        inputConnection.commitText(" world", 1);
        
        assertEquals("hello world", editor.getText().toString());
    }

    @Test
    public void testBackspaceThroughInputConnection() {
        editor.setText("abc");
        inputConnection.setSelection(3, 3); // Cursor at end
        
        // Simulating backspace
        KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
        inputConnection.sendKeyEvent(downEvent);
        
        assertEquals("ab", editor.getText().toString());
    }

    @Test
    public void testAutoCloseBrackets() {
        editor.setText("");
        inputConnection.setSelection(0, 0);
        
        // Type open parenthesis
        inputConnection.commitText("(", 1);
        
        // The editor should auto-close the parenthesis
        assertEquals("()", editor.getText().toString());
    }

    @Test
    public void testUndoRedoSingleEdit() {
        editor.setText("Line 1");
        inputConnection.setSelection(6, 6);
        
        // Ensure UndoStack is ready
        assertFalse(editor.canUndo());
        
        // Type " appended"
        inputConnection.commitText(" appended", 1);
        assertEquals("Line 1 appended", editor.getText().toString());
        assertTrue("Should be able to undo after typing", editor.canUndo());
        
        // Undo
        editor.undo();
        assertEquals("Line 1", editor.getText().toString());
        
        // Redo
        editor.redo();
        assertEquals("Line 1 appended", editor.getText().toString());
    }

    @Test
    public void testSetFileTypeChangesHighlighting() {
        // Just verify it doesn't crash and properly accepts the file type
        editor.setFileType(FileType.HTML);
        editor.setText("<html></html>");
        
        editor.setFileType(FileType.CSS);
        editor.setText("body { color: red; }");
        
        // Setting text runs tokenization internally.
        // We verify that the text matches and no exceptions were thrown.
        assertEquals("body { color: red; }", editor.getText().toString());
    }

    @Test
    public void testAutoCloseQuotes() {
        editor.setAutoCloseQuotes(true);
        editor.setText("");
        inputConnection.setSelection(0, 0);
        
        // Type double quote
        inputConnection.commitText("\"", 1);
        
        // The editor should auto-close the quote
        assertEquals("\"\"", editor.getText().toString());
    }
    
    @Test
    public void testAutoCloseBracketsCursorPosition() {
        editor.setAutoCloseBrackets(true);
        editor.setText("");
        inputConnection.setSelection(0, 0);
        
        // Type open parenthesis
        inputConnection.commitText("(", 1);
        
        assertEquals("()", editor.getText().toString());
        // Cursor should be between the parentheses
        assertEquals(1, editor.getSelectionStart());
    }
    
    @Test
    public void testAutoIndentationOnEnter() {
        editor.setFileType(FileType.JAVASCRIPT);
        editor.setText("function test() {");
        inputConnection.setSelection(17, 17);
        
        // Simulate pressing Enter
        KeyEvent enterEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);
        inputConnection.sendKeyEvent(enterEvent);
        
        // The next line should be auto-indented with 4 spaces (default)
        String expected = "function test() {\n    ";
        assertEquals(expected, editor.getText().toString());
    }

    @Test
    public void testCursorPaintStrokeWidthUsesDensity() {
        float expectedWidth = 2f * editor.getDensity();
        assertEquals(expectedWidth, editor.getCursorPaint().getStrokeWidth(), 0.001f);
    }

    @Test
    public void testSelectWordAtDoesNotSelectDistantWordsOnPunctuation() {
        editor.setText("body {\n  margin: 0;\n}");
        // "  margin: 0;" -> col 10 is '0', col 11 is ';', col 12 is after ';'
        assertFalse("Holding after ';' should not select '0'",
                editor.selectWordAt(new ContentPosition(1, 12)));
        assertFalse("Holding on ';' should not select '0'",
                editor.selectWordAt(new ContentPosition(1, 11)));
        assertFalse("Holding on '{' should not select word",
                editor.selectWordAt(new ContentPosition(0, 5)));
    }

    @Test
    public void testSelectWordAtBetweenTagsDoesNotSelectTag() {
        editor.setText("<div></div>");
        // "<div></div>" -> '<'(0), 'd'(1), 'i'(2), 'v'(3), '>'(4), '<'(5), '/'(6), 'd'(7), 'i'(8), 'v'(9), '>'(10)
        // Position 5 is between '>' and '<'
        assertFalse("Holding between tags should not select closing div",
                editor.selectWordAt(new ContentPosition(0, 5)));
    }

    @Test
    public void testSelectWordAtSelectsWordDirectly() {
        editor.setText("<div></div>");
        // Opening tag "div" is from col 1 to 4
        assertTrue(editor.selectWordAt(new ContentPosition(0, 2)));
        assertEquals(1, editor.getSelectionStart());
        assertEquals(4, editor.getSelectionEnd());

        // Closing tag "div" is from col 7 to 10
        assertTrue(editor.selectWordAt(new ContentPosition(0, 8)));
        assertEquals(7, editor.getSelectionStart());
        assertEquals(10, editor.getSelectionEnd());
    }

    @Test
    public void testSelectWordAtEndOfWordBoundary() {
        editor.setText("hello world");
        // Col 5 is space right after 'hello'
        assertTrue(editor.selectWordAt(new ContentPosition(0, 5)));
        assertEquals(0, editor.getSelectionStart());
        assertEquals(5, editor.getSelectionEnd());

        // Col 11 is end of line right after 'world'
        assertTrue(editor.selectWordAt(new ContentPosition(0, 11)));
        assertEquals(6, editor.getSelectionStart());
        assertEquals(11, editor.getSelectionEnd());
    }

    @Test
    public void testAutoCloseBracketSkipOver() {
        editor.setAutoCloseBrackets(true);
        editor.setText("");

        // Type 'p' then '{'
        inputConnection.commitText("p", 1);
        inputConnection.commitText("{", 1);
        ShadowLooper.runUiThreadTasks();

        // Should be "p{}" with cursor at index 2 (between { and })
        assertEquals("p{}", editor.getText().toString());
        assertEquals(2, editor.getSelectionStart());

        // Type '}' -> should skip over existing '}' without duplicating to "p{}}"
        inputConnection.commitText("}", 1);
        ShadowLooper.runUiThreadTasks();

        assertEquals("p{}", editor.getText().toString());
        assertEquals(3, editor.getSelectionStart());
    }

    @Test
    public void testAutoCloseQuoteSkipOver() {
        editor.setAutoCloseQuotes(true);
        editor.setText("");

        // Type '"' -> auto-close inserts '"'
        inputConnection.commitText("\"", 1);
        ShadowLooper.runUiThreadTasks();

        assertEquals("\"\"", editor.getText().toString());
        assertEquals(1, editor.getSelectionStart());

        // Type '"' -> should skip over without creating triple quotes
        inputConnection.commitText("\"", 1);
        ShadowLooper.runUiThreadTasks();

        assertEquals("\"\"", editor.getText().toString());
        assertEquals(2, editor.getSelectionStart());
    }

    @Test
    public void testAutoCloseBracketPairBackspace() {
        editor.setAutoCloseBrackets(true);
        editor.setText("");

        // Type '{' -> auto-close inserts '}'
        inputConnection.commitText("{", 1);
        ShadowLooper.runUiThreadTasks();
        assertEquals("{}", editor.getText().toString());
        assertEquals(1, editor.getSelectionStart());

        // Delete between pair -> deletes both '{' and '}'
        inputConnection.deleteSurroundingText(1, 0);
        ShadowLooper.runUiThreadTasks();
        assertEquals("", editor.getText().toString());
        assertEquals(0, editor.getSelectionStart());
    }

    @Test
    public void testInsertCompletionWithReplaceAfterLength() {
        // Document has "p{}" with cursor at 2 (between { and })
        editor.setText("p{}");
        inputConnection.setSelection(2, 2);
        assertEquals(2, editor.getSelectionStart());

        CompletionItem emmetItem = new CompletionItem("p{}", "<p>|</p>", "Emmet", CompletionItem.Type.SNIPPET, 0);
        emmetItem.setReplaceLength(2); // replace "p{" before cursor
        emmetItem.setReplaceAfterLength(1); // replace "}" after cursor

        editor.insertCompletion(emmetItem);
        ShadowLooper.runUiThreadTasks();

        // Trailing '}' must be cleanly consumed, leaving "<p></p>" with cursor at pipe (col 3)
        assertEquals("<p></p>", editor.getText().toString());
        assertEquals(3, editor.getSelectionStart());
    }
}
