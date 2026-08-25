package com.cocode.vcode.ide.core.editor.text;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UndoStackTest {

    private UndoStack undoStack;
    private Content content;

    @Before
    public void setUp() {
        undoStack = new UndoStack();
        content = new Content();
    }

    private UndoStack.EditorSnapshot snap(int line, int col) {
        return new UndoStack.EditorSnapshot(new ContentPosition(line, col), null, 0, 0);
    }

    @Test
    public void testBasicInsertUndoRedo() {
        // Record an insert of "hello"
        UndoStack.EditorSnapshot before = snap(0, 0);
        UndoStack.EditorSnapshot after = snap(0, 5);
        
        undoStack.recordInsert(0, 0, "hello", before, after);
        undoStack.commitPending(); // force commit to undo unit

        assertTrue(undoStack.canUndo());
        assertFalse(undoStack.canRedo());

        // Undo
        UndoStack.EditorSnapshot returnedBefore = undoStack.undo(content);
        assertNotNull(returnedBefore);
        assertEquals(0, returnedBefore.cursor.column);
        
        // Verify Content was commanded to delete the inserted text
        assertEquals("", content.getLineCopy(0));

        assertFalse(undoStack.canUndo());
        assertTrue(undoStack.canRedo());

        // Redo
        UndoStack.EditorSnapshot returnedAfter = undoStack.redo(content);
        assertNotNull(returnedAfter);
        assertEquals(5, returnedAfter.cursor.column);
        
        // Verify Content was commanded to insert the text back
        assertEquals("hello", content.getLineCopy(0));
    }

    @Test
    public void testBasicDeleteUndoRedo() {
        content = new Content("world");
        UndoStack.EditorSnapshot before = snap(0, 5);
        UndoStack.EditorSnapshot after = snap(0, 0);

        undoStack.recordDelete(0, 0, 0, 5, "world", before, after);
        undoStack.commitPending();

        assertTrue(undoStack.canUndo());
        
        // Actually perform the deletion on our content so undo has something to restore
        content.delete(0, 0, 0, 5);
        assertEquals("", content.getLineCopy(0));

        // Undo delete -> inserts text back
        undoStack.undo(content);
        assertEquals("world", content.getLineCopy(0));

        // Redo -> deletes text again
        undoStack.redo(content);
        assertEquals("", content.getLineCopy(0));
    }

    @Test
    public void testAtomicGroupBundlesOperations() {
        // Simulating: typing bracket '{' -> triggers auto-indent ('\n  ') and auto-close ('}')
        undoStack.beginAtomicGroup();
        
        // Keystroke '{'
        undoStack.recordInsert(0, 0, "{", snap(0, 0), snap(0, 1));
        // Side-effect: newline and indent
        undoStack.recordInsert(0, 1, "\n  ", snap(0, 1), snap(1, 2));
        // Side-effect: auto-close
        undoStack.recordInsert(1, 2, "}", snap(1, 2), snap(1, 3));
        
        undoStack.endAtomicGroup();
        undoStack.commitPending(); // commit the whole group

        // Calling undo once should reverse all three insertions in reverse order
        // We first need to apply them to content
        content.insert(0, 0, "{");
        content.insert(0, 1, "\n  ");
        content.insert(1, 2, "}");
        
        undoStack.undo(content);

        // It should be empty again
        assertEquals(1, content.lineCount());
        assertEquals("", content.getLineCopy(0));
    }

    @Test
    public void testTypingMergesSameCharacterClass() {
        // Simulate typing "foo" character by character
        undoStack.recordInsert(0, 0, "f", snap(0, 0), snap(0, 1));
        undoStack.recordInsert(0, 1, "o", snap(0, 1), snap(0, 2));
        undoStack.recordInsert(0, 2, "o", snap(0, 2), snap(0, 3));
        
        // Since they are all word characters, they stay in the pending group.
        // We haven't called commitPending. But canUndo() should be true because there's pending group.
        assertTrue(undoStack.canUndo());
        
        // Committing them makes them a single unit.
        undoStack.commitPending();
        
        // Undo should reverse the three insertions. It reverses character by character in reverse order.
        content.insert(0, 0, "f");
        content.insert(0, 1, "o");
        content.insert(0, 2, "o");
        
        undoStack.undo(content);
        assertEquals("", content.getLineCopy(0));
        
        // Ensure there is no second undo unit
        assertNull("Should only be one undo unit because they merged", undoStack.undo(content));
    }

    @Test
    public void testTypingDifferentCharacterClassForcesCommit() {
        // Simulate typing "a" then " "
        undoStack.recordInsert(0, 0, "a", snap(0, 0), snap(0, 1));
        undoStack.recordInsert(0, 1, " ", snap(0, 1), snap(0, 2));
        
        undoStack.commitPending();

        content.insert(0, 0, "a");
        content.insert(0, 1, " ");
        
        undoStack.undo(content);
        assertEquals("a", content.getLineCopy(0)); // space removed
        
        undoStack.undo(content);
        assertEquals("", content.getLineCopy(0)); // 'a' removed
    }
}
