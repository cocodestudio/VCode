package com.cocode.vcode.ide.views;

import android.content.Context;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.KeyEvent;

import org.robolectric.RuntimeEnvironment;

import com.cocode.vcode.ide.core.editor.text.ContentPosition;
import com.cocode.vcode.ide.core.model.FileType;

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
}
