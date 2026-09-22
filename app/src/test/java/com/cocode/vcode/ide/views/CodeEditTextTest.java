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

import java.util.List;

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

    @Test
    public void testInsertCompletionConsumesStrayBraceWithoutExplicitReplaceAfterLength() {
        editor.setAutoCloseBrackets(true);
        editor.setText("p{hello}");
        inputConnection.setSelection(7, 7); // between 'o' and '}'

        CompletionItem emmetItem = new CompletionItem("p{hello}", "<p>hello</p>|", "Emmet", CompletionItem.Type.SNIPPET, 0);
        emmetItem.setReplaceLength(7); // "p{hello"
        emmetItem.setReplaceAfterLength(0); // 0, but editor should detect trailing '}'

        editor.insertCompletion(emmetItem);
        ShadowLooper.runUiThreadTasks();

        assertEquals("<p>hello</p>", editor.getText().toString());
    }

    @Test
    public void testInsertCompletionConsumesStrayBracketWithoutExplicitReplaceAfterLength() {
        editor.setAutoCloseBrackets(true);
        editor.setText("a[href=\"#\"]");
        inputConnection.setSelection(10, 10); // between '"' and ']'

        CompletionItem emmetItem = new CompletionItem("a[href=\"#\"]", "<a href=\"#\"></a>|", "Emmet", CompletionItem.Type.SNIPPET, 0);
        emmetItem.setReplaceLength(10);
        emmetItem.setReplaceAfterLength(0);

        editor.insertCompletion(emmetItem);
        ShadowLooper.runUiThreadTasks();

        assertEquals("<a href=\"#\"></a>", editor.getText().toString());
    }

    @Test
    public void testInsertCompletionConsumesDuplicateBracket() {
        editor.setAutoCloseBrackets(true);
        editor.setText("p{hello}}");
        inputConnection.setSelection(8, 8); // between the first and second '}'

        CompletionItem emmetItem = new CompletionItem("p{hello}", "<p>hello</p>|", "Emmet", CompletionItem.Type.SNIPPET, 0);
        emmetItem.setReplaceLength(8);
        emmetItem.setReplaceAfterLength(0);

        editor.insertCompletion(emmetItem);
        ShadowLooper.runUiThreadTasks();

        assertEquals("<p>hello</p>", editor.getText().toString());
    }

    @Test
    public void testInsertCompletionPreservesOuterBracesWhenAutoCloseDisabled() {
        editor.setAutoCloseBrackets(false);
        editor.setText("p{hello}\n}");
        inputConnection.setSelection(8, 8); // after '}'

        CompletionItem emmetItem = new CompletionItem("p{hello}", "<p>hello</p>|", "Emmet", CompletionItem.Type.SNIPPET, 0);
        emmetItem.setReplaceLength(8);
        emmetItem.setReplaceAfterLength(0);

        editor.insertCompletion(emmetItem);
        ShadowLooper.runUiThreadTasks();

        // Outer brace on next line should NOT be deleted
        assertEquals("<p>hello</p>\n}", editor.getText().toString());
    }

    @Test
    public void testComposingTextSkipOverClosingBracket() {
        editor.setAutoCloseBrackets(true);
        editor.setText("p{}");
        inputConnection.setSelection(2, 2);

        inputConnection.setComposingText("hello", 1);
        inputConnection.commitText("}", 1);
        ShadowLooper.runUiThreadTasks();

        assertEquals("p{hello}", editor.getText().toString());
        assertEquals(8, editor.getSelectionStart());
    }

    @Test
    public void testKeyboardArrowNavigationAndSelection() {
        editor.setText("Hello\nWorld");
        editor.setCursorPosition(0, 0, false);
        assertEquals(0, editor.getSelectionStart());
        assertEquals(0, editor.getSelectionEnd());

        // Right arrow moves 1 char
        editor.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(1, editor.getSelectionStart());

        // Shift + Right arrow expands selection
        KeyEvent shiftRight = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0, KeyEvent.META_SHIFT_ON);
        editor.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, shiftRight);
        assertEquals(1, editor.getSelectionStart());
        assertEquals(2, editor.getSelectionEnd());
        assertTrue(editor.hasSelection());

        // Left arrow without shift collapses selection
        editor.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
        assertFalse(editor.hasSelection());
        assertEquals(1, editor.getSelectionStart());
    }

    @Test
    public void testKeyboardWordNavigationAndDeletion() {
        editor.setText("const greeting = 'hello';");
        editor.setCursorPosition(0, 0, false);

        // Ctrl + Right jumps word
        KeyEvent ctrlRight = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0, KeyEvent.META_CTRL_ON);
        editor.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, ctrlRight);
        assertEquals(5, editor.getSelectionStart()); // after "const"

        // Next word
        editor.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, ctrlRight);
        assertEquals(14, editor.getSelectionStart()); // after "greeting"

        // Ctrl + Backspace deletes word backward
        KeyEvent ctrlDel = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0, KeyEvent.META_CTRL_ON);
        editor.onKeyDown(KeyEvent.KEYCODE_DEL, ctrlDel);
        assertEquals("const  = 'hello';", editor.getText().toString());
    }

    @Test
    public void testKeyboardHomeEndAndDocBoundaries() {
        editor.setText("  line one\n  line two");
        editor.setCursorPosition(0, 0, false);

        // End key
        editor.onKeyDown(KeyEvent.KEYCODE_MOVE_END, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_END));
        assertEquals(10, editor.getSelectionStart()); // at end of "  line one"

        // Home key (smart home: jumps to first non-whitespace at index 2)
        editor.onKeyDown(KeyEvent.KEYCODE_MOVE_HOME, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_HOME));
        assertEquals(2, editor.getSelectionStart());

        // Second Home key jumps to col 0
        editor.onKeyDown(KeyEvent.KEYCODE_MOVE_HOME, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_HOME));
        assertEquals(0, editor.getSelectionStart());

        // Ctrl + End (document boundary end)
        KeyEvent ctrlEnd = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_END, 0, KeyEvent.META_CTRL_ON);
        editor.onKeyDown(KeyEvent.KEYCODE_MOVE_END, ctrlEnd);
        assertEquals(editor.getText().length(), editor.getSelectionStart());

        // Ctrl + Home (document boundary start)
        KeyEvent ctrlHome = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_HOME, 0, KeyEvent.META_CTRL_ON);
        editor.onKeyDown(KeyEvent.KEYCODE_MOVE_HOME, ctrlHome);
        assertEquals(0, editor.getSelectionStart());
    }

    @Test
    public void testForwardDeleteAndCtrlForwardDelete() {
        editor.setText("foo bar baz");
        editor.setCursorPosition(0, 0, false);

        // Forward delete 'f'
        editor.onKeyDown(KeyEvent.KEYCODE_FORWARD_DEL, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL));
        assertEquals("oo bar baz", editor.getText().toString());

        // Ctrl + Forward delete deletes next word "oo"
        KeyEvent ctrlForwardDel = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL, 0, KeyEvent.META_CTRL_ON);
        editor.onKeyDown(KeyEvent.KEYCODE_FORWARD_DEL, ctrlForwardDel);
        assertEquals(" bar baz", editor.getText().toString());
    }

    @Test
    public void testDeleteLineAndUndo() {
        editor.setText("line1\nline2\nline3");
        editor.setCursorPosition(1, 2, false);

        KeyEvent ctrlShiftK = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_K, 0, KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON);
        boolean handled = editor.onKeyDown(KeyEvent.KEYCODE_K, ctrlShiftK);
        assertTrue(handled);
        assertEquals("line1\nline3", editor.getText().toString());

        KeyEvent ctrlZ = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON);
        boolean undoHandled = editor.onKeyDown(KeyEvent.KEYCODE_Z, ctrlZ);
        assertTrue(undoHandled);
        assertEquals("line1\nline2\nline3", editor.getText().toString());

        // Test deleting last line
        editor.setCursorPosition(2, 2, false); // on "line3"
        editor.onKeyDown(KeyEvent.KEYCODE_K, ctrlShiftK);
        assertEquals("line1\nline2", editor.getText().toString());

        editor.onKeyDown(KeyEvent.KEYCODE_Z, ctrlZ);
        assertEquals("line1\nline2\nline3", editor.getText().toString());
    }

    @Test
    public void testCtrlDeleteAndUndo() {
        editor.setText("hello world foo bar");
        editor.setCursorPosition(0, 6, false);

        KeyEvent ctrlDel = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL, 0, KeyEvent.META_CTRL_ON);
        boolean handled = editor.onKeyDown(KeyEvent.KEYCODE_FORWARD_DEL, ctrlDel);
        assertTrue(handled);
        assertEquals("hello  foo bar", editor.getText().toString());

        KeyEvent ctrlZ = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON);
        boolean undoHandled = editor.onKeyDown(KeyEvent.KEYCODE_Z, ctrlZ);
        assertTrue(undoHandled);
        assertEquals("hello world foo bar", editor.getText().toString());
    }

    @Test
    public void testCtrlTabDoesNotIndent() {
        editor.setText("line1\nline2");
        editor.setCursorPosition(0, 0, false);

        KeyEvent ctrlTab = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 0, KeyEvent.META_CTRL_ON);
        boolean handled = editor.onKeyDown(KeyEvent.KEYCODE_TAB, ctrlTab);
        assertFalse(handled);
        assertEquals("line1\nline2", editor.getText().toString());
    }

    @Test
    public void testFormatTextUndoAndPreservesHistory() {
        editor.setText("line 1");
        inputConnection.setSelection(6, 6);
        inputConnection.commitText("\nline 2", 1);
        assertEquals("line 1\nline 2", editor.getText().toString());
        assertTrue(editor.canUndo());

        // Format document
        editor.formatText("line 1\nline 2\n// formatted");
        assertEquals("line 1\nline 2\n// formatted", editor.getText().toString());
        assertTrue("Format should be undoable", editor.canUndo());

        // Undo format
        editor.undo();
        assertEquals("line 1\nline 2", editor.getText().toString());

        // Undo typing before format
        editor.undo();
        assertEquals("line 1", editor.getText().toString());

        // Redo typing
        editor.redo();
        assertEquals("line 1\nline 2", editor.getText().toString());

        // Redo format
        editor.redo();
        assertEquals("line 1\nline 2\n// formatted", editor.getText().toString());
    }

    @Test
    public void testReplaceRangeUndoRedo() {
        editor.setText("hello world foo");
        editor.replaceRange(6, 11, "everyone");
        assertEquals("hello everyone foo", editor.getText().toString());
        assertTrue(editor.canUndo());

        editor.undo();
        assertEquals("hello world foo", editor.getText().toString());

        editor.redo();
        assertEquals("hello everyone foo", editor.getText().toString());
    }

    @Test
    public void testMoveLineUndoRedo() {
        editor.setText("first\nsecond\nthird");
        editor.setCursorPosition(1, 2, false);

        editor.moveLine(true); // move "second" up
        assertEquals("second\nfirst\nthird", editor.getText().toString());
        assertTrue(editor.canUndo());

        editor.undo();
        assertEquals("first\nsecond\nthird", editor.getText().toString());

        editor.redo();
        assertEquals("second\nfirst\nthird", editor.getText().toString());
    }

    @Test
    public void testCopyLineDownUndo() {
        editor.setText("lineA\nlineB");
        editor.setCursorPosition(0, 2, false);

        editor.copyLine(false); // copy down
        assertEquals("lineA\nlineA\nlineB", editor.getText().toString());
        assertTrue(editor.canUndo());

        editor.undo();
        assertEquals("lineA\nlineB", editor.getText().toString());
    }

    @Test
    public void testCtrlShiftZRedo() {
        editor.setText("abc");
        inputConnection.setSelection(3, 3);
        inputConnection.commitText("d", 1);
        assertEquals("abcd", editor.getText().toString());

        // Undo via Ctrl+Z
        KeyEvent ctrlZ = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON);
        boolean undoHandled = editor.onKeyDown(KeyEvent.KEYCODE_Z, ctrlZ);
        assertTrue(undoHandled);
        assertEquals("abc", editor.getText().toString());
        assertTrue(editor.canRedo());

        // Redo via Ctrl+Shift+Z
        KeyEvent ctrlShiftZ = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON);
        boolean redoHandled = editor.onKeyDown(KeyEvent.KEYCODE_Z, ctrlShiftZ);
        assertTrue(redoHandled);
        assertEquals("abcd", editor.getText().toString());
    }

    @Test
    public void testAutomaticReferenceChange_doesNotTriggerAutoComplete() {
        editor.setFileType(FileType.JAVASCRIPT);
        editor.setText("const obj = { prop: 123 };\nobj.prop;");
        editor.setSelection(31);

        editor.updateRefactoredContent("const obj = { renamedProp: 123 };\nobj.renamedProp;");
        ShadowLooper.idleMainLooper();

        assertFalse("Autocomplete popup must not show on automatic reference changes",
                editor.isAutoCompleteVisible());
        assertFalse("Programmatic change flag must be reset", editor.isProgrammaticChange());
    }

    @Test
    public void testFormatText_doesNotTriggerAutoComplete() {
        editor.setFileType(FileType.JAVASCRIPT);
        editor.setText("const obj = { prop: 123 };\nobj.prop;");
        editor.setSelection(31);

        editor.formatText("const obj = {\n  prop: 123\n};\nobj.prop;");
        ShadowLooper.idleMainLooper();

        assertFalse("Autocomplete popup must not show on code formatting",
                editor.isAutoCompleteVisible());
        assertFalse("Programmatic change flag must be reset", editor.isProgrammaticChange());
    }

    @Test
    public void testDeletion_whenAutoCompleteNotShowing_doesNotTriggerAutoComplete() {
        editor.setFileType(FileType.JAVASCRIPT);
        editor.setText("obj.prop");
        editor.setSelection(8);

        assertFalse("Popup must not be showing initially", editor.isAutoCompleteVisible());

        for (int i = 0; i < 4; i++) {
            KeyEvent delEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
            inputConnection.sendKeyEvent(delEvent);
        }
        ShadowLooper.idleMainLooper();

        assertEquals("obj.", editor.getText().toString());
        assertFalse("Deleting characters when autocomplete was closed must NOT open autocomplete popup",
                editor.isAutoCompleteVisible());
    }

    @Test
    public void testDeletion_whenAutoCompleteShowing_retainsVisibilityForFiltering() {
        editor.setFileType(FileType.JAVASCRIPT);
        editor.setText("obj.prop");
        editor.setSelection(8);

        List<CompletionItem> items = new java.util.ArrayList<>();
        items.add(new CompletionItem("prop", "prop", "number", CompletionItem.Type.VALUE, 0));
        editor.showLspCompletions(items);
        assertTrue("Popup must be showing", editor.isAutoCompleteVisible());

        KeyEvent delEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
        inputConnection.sendKeyEvent(delEvent);

        assertEquals("obj.pro", editor.getText().toString());
        assertTrue("wasAutoCompleteVisibleBeforeDelete must be true when popup was visible",
                editor.wasAutoCompleteVisibleBeforeDelete());
    }
}
