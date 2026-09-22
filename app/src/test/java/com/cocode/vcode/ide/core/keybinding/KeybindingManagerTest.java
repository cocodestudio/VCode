package com.cocode.vcode.ide.core.keybinding;

import android.content.Context;
import android.view.KeyEvent;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class KeybindingManagerTest {

    private KeybindingManager manager;
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        manager = KeybindingManager.getInstance(context);
        manager.resetToDefaults();
    }

    @Test
    public void testDefaultBindingsInitialization() {
        KeyStroke saveStroke = manager.getKeyStroke(KeyCommand.SAVE_ALL);
        assertNotNull(saveStroke);
        assertEquals(KeyEvent.KEYCODE_S, saveStroke.keyCode);
        assertTrue(saveStroke.ctrl);

        KeyStroke undoStroke = manager.getKeyStroke(KeyCommand.UNDO);
        assertNotNull(undoStroke);
        assertEquals(KeyEvent.KEYCODE_Z, undoStroke.keyCode);
        assertTrue(undoStroke.ctrl);

        KeyStroke f5Stroke = manager.getKeyStroke(KeyCommand.RUN_PREVIEW);
        assertNotNull(f5Stroke);
        assertEquals(KeyEvent.KEYCODE_F5, f5Stroke.keyCode);

        KeyStroke extractCss = manager.getKeyStroke(KeyCommand.EXTRACT_CSS);
        assertNotNull(extractCss);
        assertEquals(KeyEvent.KEYCODE_C, extractCss.keyCode);
        assertTrue(extractCss.ctrl);
        assertTrue(extractCss.shift);

        KeyStroke extractJs = manager.getKeyStroke(KeyCommand.EXTRACT_JS);
        assertNotNull(extractJs);
        assertEquals(KeyEvent.KEYCODE_J, extractJs.keyCode);
        assertTrue(extractJs.ctrl);
        assertTrue(extractJs.shift);

        KeyStroke readOnlyStroke = manager.getKeyStroke(KeyCommand.TOGGLE_READ_ONLY);
        assertNotNull(readOnlyStroke);
        assertEquals(KeyEvent.KEYCODE_R, readOnlyStroke.keyCode);
        assertTrue(readOnlyStroke.alt);
        assertFalse(readOnlyStroke.ctrl);
    }

    @Test
    public void testFindCommandFromKeyEvent() {
        // Ctrl + S -> SAVE_ALL
        KeyEvent saveEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_S, 0, KeyEvent.META_CTRL_ON);
        KeyCommand cmd = manager.findCommand(saveEvent, KeyCommand.Scope.GLOBAL);
        assertEquals(KeyCommand.SAVE_ALL, cmd);

        // Alt + R -> TOGGLE_READ_ONLY
        KeyEvent roEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_R, 0, KeyEvent.META_ALT_ON);
        KeyCommand roCmd = manager.findCommand(roEvent, KeyCommand.Scope.GLOBAL);
        assertEquals(KeyCommand.TOGGLE_READ_ONLY, roCmd);

        // Ctrl + Shift + C -> EXTRACT_CSS
        KeyEvent cssEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, 0, KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON);
        KeyCommand cssCmd = manager.findCommand(cssEvent, KeyCommand.Scope.GLOBAL);
        assertEquals(KeyCommand.EXTRACT_CSS, cssCmd);

        // Ctrl + Shift + J -> EXTRACT_JS
        KeyEvent jsEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_J, 0, KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON);
        KeyCommand jsCmd = manager.findCommand(jsEvent, KeyCommand.Scope.GLOBAL);
        assertEquals(KeyCommand.EXTRACT_JS, jsCmd);

        // Ctrl + Z -> UNDO (Scope.EDITOR)
        KeyEvent undoEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON);
        KeyCommand undoCmd = manager.findCommand(undoEvent, KeyCommand.Scope.EDITOR);
        assertEquals(KeyCommand.UNDO, undoCmd);

        // Editor scope falls back to Global commands
        KeyCommand saveInEditor = manager.findCommand(saveEvent, KeyCommand.Scope.EDITOR);
        assertEquals(KeyCommand.SAVE_ALL, saveInEditor);
    }

    @Test
    public void testCustomKeybindingAndConflictDetection() {
        KeyStroke altS = KeyStroke.fromString("Alt+S");
        assertNotNull(altS);

        // Change SAVE_ALL to Alt+S
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        KeybindingManager.OnKeybindingsChangedListener listener = () -> listenerCalled.set(true);
        manager.addListener(listener);

        manager.setKeybinding(KeyCommand.SAVE_ALL, altS);
        assertTrue(listenerCalled.get());
        assertEquals(altS, manager.getKeyStroke(KeyCommand.SAVE_ALL));
        assertTrue(manager.isCustomized(KeyCommand.SAVE_ALL));

        // Detect conflict when trying to bind another command to Alt+S
        KeyCommand conflict = manager.findConflictingCommand(KeyCommand.FIND, altS);
        assertEquals(KeyCommand.SAVE_ALL, conflict);

        // Reset SAVE_ALL
        manager.resetKeybinding(KeyCommand.SAVE_ALL);
        assertFalse(manager.isCustomized(KeyCommand.SAVE_ALL));
        assertEquals(KeyStroke.fromString("Ctrl+S"), manager.getKeyStroke(KeyCommand.SAVE_ALL));

        manager.removeListener(listener);
    }

    @Test
    public void testJsonSerializationAndImportExport() throws Exception {
        String json = manager.getSerializedJson();
        assertNotNull(json);
        assertTrue(json.contains("editor.action.save"));
        assertTrue(json.contains("Ctrl+S"));

        File tempFile = new File(context.getCacheDir(), "test_keybindings.json");
        assertTrue(manager.exportToFile(tempFile));
        assertTrue(tempFile.exists());

        // Modify in-memory
        manager.setKeybinding(KeyCommand.SAVE_ALL, KeyStroke.fromString("Ctrl+Shift+S"));
        assertEquals("Ctrl+Shift+S", manager.getKeyStroke(KeyCommand.SAVE_ALL).toSpecString());

        // Import back from original file
        assertTrue(manager.importFromFile(tempFile).isSuccess());
        assertEquals("Ctrl+S", manager.getKeyStroke(KeyCommand.SAVE_ALL).toSpecString());

        tempFile.delete();
    }

    @Test
    public void testImportKeybindingsWithDuplicateShortcuts() throws Exception {
        File tempFile = new File(context.getCacheDir(), "duplicate_keybindings.json");

        // Two commands specified with the exact same shortcut "Ctrl+Alt+D"
        org.json.JSONArray array = new org.json.JSONArray();
        org.json.JSONObject obj1 = new org.json.JSONObject();
        obj1.put("command", KeyCommand.SAVE_ALL.getCommandId());
        obj1.put("key", "Ctrl+Alt+D");
        array.put(obj1);

        org.json.JSONObject obj2 = new org.json.JSONObject();
        obj2.put("command", KeyCommand.FORMAT_DOCUMENT.getCommandId());
        obj2.put("key", "Ctrl+Alt+D");
        array.put(obj2);

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
             java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(fos, java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write(array.toString());
        }

        KeybindingManager.ImportResult result = manager.importFromFile(tempFile);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getConflictCount());

        // FORMAT_DOCUMENT takes Ctrl+Alt+D, SAVE_ALL is unassigned to resolve the collision
        assertEquals("Ctrl+Alt+D", manager.getKeyStroke(KeyCommand.FORMAT_DOCUMENT).toSpecString());
        assertNull(manager.getKeyStroke(KeyCommand.SAVE_ALL));

        tempFile.delete();
    }

    @Test
    public void testSetKeybindingResolvesExistingConflict() {
        manager.resetToDefaults();
        KeyStroke ctrlS = KeyStroke.fromString("Ctrl+S");
        assertEquals(ctrlS, manager.getKeyStroke(KeyCommand.SAVE_ALL));

        // Reassign Ctrl+S to FORMAT_DOCUMENT
        manager.setKeybinding(KeyCommand.FORMAT_DOCUMENT, ctrlS);
        assertEquals(ctrlS, manager.getKeyStroke(KeyCommand.FORMAT_DOCUMENT));
        // SAVE_ALL must now be unassigned so no duplicate shortcut exists
        assertNull(manager.getKeyStroke(KeyCommand.SAVE_ALL));

        manager.resetToDefaults();
    }
}
