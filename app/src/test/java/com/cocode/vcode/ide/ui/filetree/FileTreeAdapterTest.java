package com.cocode.vcode.ide.ui.filetree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cocode.vcode.ide.data.model.FileNode;

import org.junit.Before;
import org.junit.Test;

import java.io.File;

public class FileTreeAdapterTest {

    private FileTreeAdapter adapter;

    @Before
    public void setUp() {
        adapter = new FileTreeAdapter(null, 16, 1.0f);
    }

    @Test
    public void testCutFileMarksNodeAsCut() {
        File fileA = new File("/project/src/index.js");
        File fileB = new File("/project/src/other.js");

        adapter.setClipboardState(fileA, true);

        assertTrue("Adapter should indicate cut action", adapter.isCutAction());
        assertEquals("Clipboard file should match fileA", fileA, adapter.getClipboardFile());
        assertTrue("FileNode for fileA should be marked as cut", adapter.isNodeCut(new FileNode(fileA, 1)));
        assertFalse("FileNode for fileB should NOT be marked as cut", adapter.isNodeCut(new FileNode(fileB, 1)));
    }

    @Test
    public void testCutDirectoryMarksFolderAndDescendantsAsCut() {
        File folder = new File("/project/src/components");
        File childFile = new File("/project/src/components/Button.jsx");
        File nestedChild = new File("/project/src/components/sub/Icon.jsx");
        File outsideFile = new File("/project/src/App.jsx");

        adapter.setClipboardState(folder, true);

        assertTrue("Adapter should indicate cut action", adapter.isCutAction());
        assertTrue("Folder should be marked as cut", adapter.isNodeCut(new FileNode(folder, 1)));
        assertTrue("Direct child file should be marked as cut", adapter.isNodeCut(new FileNode(childFile, 2)));
        assertTrue("Nested child file should be marked as cut", adapter.isNodeCut(new FileNode(nestedChild, 3)));
        assertFalse("File outside the cut folder should NOT be marked as cut", adapter.isNodeCut(new FileNode(outsideFile, 1)));
    }

    @Test
    public void testClearClipboardStateRollsBackCutState() {
        File folder = new File("/project/src/components");
        File child = new File("/project/src/components/Button.jsx");

        adapter.setClipboardState(folder, true);
        assertTrue(adapter.isCutAction());
        assertTrue(adapter.isNodeCut(new FileNode(folder, 1)));

        // Roll back the state (e.g. drawer closed)
        adapter.clearClipboardState();

        assertFalse("isCutAction should be false after clearClipboardState", adapter.isCutAction());
        assertNull("clipboardFile should be null after clearClipboardState", adapter.getClipboardFile());
        assertFalse("Folder should NOT be marked as cut after rollback", adapter.isNodeCut(new FileNode(folder, 1)));
        assertFalse("Child should NOT be marked as cut after rollback", adapter.isNodeCut(new FileNode(child, 2)));
    }

    @Test
    public void testCopyDoesNotMarkNodesAsCut() {
        File file = new File("/project/src/index.js");

        adapter.setClipboardState(file, false);

        assertFalse("Copy operation should NOT set isCutAction", adapter.isCutAction());
        assertEquals(file, adapter.getClipboardFile());
        assertFalse("Copy operation should NOT mark node as cut for low opacity", adapter.isNodeCut(new FileNode(file, 1)));
    }

    @Test
    public void testCutOpacityConstants() {
        assertEquals("CUT_OPACITY should be 0.5f for desktop visual feedback", 0.5f, FileTreeAdapter.CUT_OPACITY, 0.001f);
        assertEquals("NORMAL_OPACITY should be 1.0f", 1.0f, FileTreeAdapter.NORMAL_OPACITY, 0.001f);
    }

    @Test
    public void testCutDirectoryWithTrailingSlash() {
        File folder = new File("/project/src/components/");
        File childFile = new File("/project/src/components/Button.jsx");

        adapter.setClipboardState(folder, true);

        assertTrue("Folder with trailing slash should be marked as cut", adapter.isNodeCut(new FileNode(new File("/project/src/components"), 1)));
        assertTrue("Child of folder with trailing slash should be marked as cut", adapter.isNodeCut(new FileNode(childFile, 2)));
    }
}
