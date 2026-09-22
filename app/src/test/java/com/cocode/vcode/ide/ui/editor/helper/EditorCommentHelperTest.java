package com.cocode.vcode.ide.ui.editor.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.cocode.vcode.ide.core.editor.text.Content;
import com.cocode.vcode.ide.core.editor.text.ContentPosition;
import com.cocode.vcode.ide.core.editor.text.UndoStack;
import com.cocode.vcode.ide.core.model.FileType;

import org.junit.Before;
import org.junit.Test;

public class EditorCommentHelperTest {

    private UndoStack undoStack;

    @Before
    public void setUp() {
        undoStack = new UndoStack();
    }

    @Test
    public void testCommentTokensResolution() {
        EditorCommentHelper.CommentTokens jsTokens = EditorCommentHelper.getCommentTokens(FileType.JAVASCRIPT);
        assertTrue(jsTokens.isLineComment());
        assertEquals("// ", jsTokens.linePrefix);

        EditorCommentHelper.CommentTokens htmlTokens = EditorCommentHelper.getCommentTokens(FileType.HTML);
        assertFalse(htmlTokens.isLineComment());
        assertEquals("<!-- ", htmlTokens.blockStart);
        assertEquals(" -->", htmlTokens.blockEnd);

        EditorCommentHelper.CommentTokens cssTokens = EditorCommentHelper.getCommentTokens(FileType.CSS);
        assertFalse(cssTokens.isLineComment());
        assertEquals("/* ", cssTokens.blockStart);
        assertEquals(" */", cssTokens.blockEnd);

        EditorCommentHelper.CommentTokens envTokens = EditorCommentHelper.getCommentTokens(FileType.ENV);
        assertTrue(envTokens.isLineComment());
        assertEquals("# ", envTokens.linePrefix);
    }

    @Test
    public void testToggleLineComment_JavaScript() {
        Content content = new Content("const a = 10;");
        ContentPosition cursor = new ContentPosition(0, 5);

        // Toggle on: should add "// "
        EditorCommentHelper.CommentResult result = EditorCommentHelper.toggleComment(
                content, undoStack, 0, 0, FileType.JAVASCRIPT, cursor, null);

        assertNotNull(result);
        assertEquals("// const a = 10;", content.getLineCopy(0));

        // Toggle off: should remove "// "
        EditorCommentHelper.CommentResult result2 = EditorCommentHelper.toggleComment(
                content, undoStack, 0, 0, FileType.JAVASCRIPT, result.newCursor, null);

        assertNotNull(result2);
        assertEquals("const a = 10;", content.getLineCopy(0));
    }

    @Test
    public void testToggleLineComment_Indented() {
        Content content = new Content("    let count = 0;");
        ContentPosition cursor = new ContentPosition(0, 8);

        // Comments should be placed at the indentation level
        EditorCommentHelper.toggleComment(
                content, undoStack, 0, 0, FileType.JAVASCRIPT, cursor, null);

        assertEquals("    // let count = 0;", content.getLineCopy(0));

        // Toggling again uncomments cleanly
        EditorCommentHelper.toggleComment(
                content, undoStack, 0, 0, FileType.JAVASCRIPT, cursor, null);

        assertEquals("    let count = 0;", content.getLineCopy(0));
    }

    @Test
    public void testToggleBlockComment_Css() {
        Content content = new Content("    color: #fff;");
        ContentPosition cursor = new ContentPosition(0, 4);

        // Comments should wrap the content
        EditorCommentHelper.toggleComment(
                content, undoStack, 0, 0, FileType.CSS, cursor, null);

        assertEquals("    /* color: #fff; */", content.getLineCopy(0));

        // Toggling again uncomments
        EditorCommentHelper.toggleComment(
                content, undoStack, 0, 0, FileType.CSS, cursor, null);

        assertEquals("    color: #fff;", content.getLineCopy(0));
    }

    @Test
    public void testToggleBlockComment_Html() {
        Content content = new Content("<div>Hello World</div>");
        ContentPosition cursor = new ContentPosition(0, 2);

        EditorCommentHelper.toggleComment(
                content, undoStack, 0, 0, FileType.HTML, cursor, null);

        assertEquals("<!-- <div>Hello World</div> -->", content.getLineCopy(0));

        EditorCommentHelper.toggleComment(
                content, undoStack, 0, 0, FileType.HTML, cursor, null);

        assertEquals("<div>Hello World</div>", content.getLineCopy(0));
    }

    @Test
    public void testToggleComment_UndoStackIntegration() {
        Content content = new Content("console.log('test');");
        ContentPosition cursor = new ContentPosition(0, 0);

        EditorCommentHelper.toggleComment(
                content, undoStack, 0, 0, FileType.JAVASCRIPT, cursor, null);

        assertEquals("// console.log('test');", content.getLineCopy(0));
        assertTrue(undoStack.canUndo());

        undoStack.undo(content);
        assertEquals("console.log('test');", content.getLineCopy(0));

        assertTrue(undoStack.canRedo());
        undoStack.redo(content);
        assertEquals("// console.log('test');", content.getLineCopy(0));
    }
}
