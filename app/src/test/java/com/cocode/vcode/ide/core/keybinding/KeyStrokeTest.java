package com.cocode.vcode.ide.core.keybinding;

import android.view.KeyEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class KeyStrokeTest {

    @Test
    public void testParseSimpleAndModifierCombinations() {
        KeyStroke s1 = KeyStroke.fromString("Ctrl+S");
        assertNotNull(s1);
        assertEquals(KeyEvent.KEYCODE_S, s1.keyCode);
        assertTrue(s1.ctrl);
        assertFalse(s1.alt);
        assertFalse(s1.shift);
        assertEquals("Ctrl+S", s1.toSpecString());
        assertEquals("Ctrl + S", s1.toDisplayString());

        KeyStroke s2 = KeyStroke.fromString("Ctrl+Shift+P");
        assertNotNull(s2);
        assertEquals(KeyEvent.KEYCODE_P, s2.keyCode);
        assertTrue(s2.ctrl);
        assertTrue(s2.shift);
        assertFalse(s2.alt);
        assertEquals("Ctrl+Shift+P", s2.toSpecString());

        KeyStroke s3 = KeyStroke.fromString("Shift+Alt+Down");
        assertNotNull(s3);
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, s3.keyCode);
        assertFalse(s3.ctrl);
        assertTrue(s3.shift);
        assertTrue(s3.alt);
        assertEquals("Alt+Shift+Down", s3.toSpecString());

        KeyStroke s4 = KeyStroke.fromString("F5");
        assertNotNull(s4);
        assertEquals(KeyEvent.KEYCODE_F5, s4.keyCode);
        assertFalse(s4.ctrl);
        assertFalse(s4.alt);
        assertFalse(s4.shift);
        assertEquals("F5", s4.toSpecString());

        KeyStroke s5 = KeyStroke.fromString("Ctrl+,");
        assertNotNull(s5);
        assertEquals(KeyEvent.KEYCODE_COMMA, s5.keyCode);
        assertTrue(s5.ctrl);
        assertEquals("Ctrl+,", s5.toSpecString());

        KeyStroke s6 = KeyStroke.fromString("Ctrl++");
        assertNotNull(s6);
        assertEquals(KeyEvent.KEYCODE_EQUALS, s6.keyCode);
        assertTrue(s6.ctrl);

        KeyStroke s7 = KeyStroke.fromString("Cmd+S");
        assertNotNull(s7);
        assertEquals(KeyEvent.KEYCODE_S, s7.keyCode);
        assertTrue(s7.ctrl); // Normalized to ctrl
    }

    @Test
    public void testKeyEventMatching() {
        KeyStroke stroke = KeyStroke.fromString("Ctrl+S");
        assertNotNull(stroke);

        // PC Ctrl+S
        KeyEvent pcCtrlS = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_S, 0, KeyEvent.META_CTRL_ON);
        assertTrue(stroke.matches(pcCtrlS));

        // Mac Cmd+S (META_META_ON)
        KeyEvent macCmdS = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_S, 0, KeyEvent.META_META_ON);
        assertTrue(stroke.matches(macCmdS));

        // Plain 'S' should not match
        KeyEvent plainS = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_S);
        assertFalse(stroke.matches(plainS));

        // Ctrl+Shift+S should not match Ctrl+S
        KeyEvent ctrlShiftS = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_S, 0, KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON);
        assertFalse(stroke.matches(ctrlShiftS));

        // Action UP should not match
        KeyEvent keyUp = new KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_S, 0, KeyEvent.META_CTRL_ON);
        assertFalse(stroke.matches(keyUp));
    }

    @Test
    public void testFromKeyEvent() {
        // Physical keypress: Ctrl + F
        KeyEvent event = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F, 0, KeyEvent.META_CTRL_ON);
        KeyStroke stroke = KeyStroke.fromKeyEvent(event);
        assertNotNull(stroke);
        assertEquals(KeyEvent.KEYCODE_F, stroke.keyCode);
        assertTrue(stroke.ctrl);
        assertFalse(stroke.shift);

        // Modifier-only keypress should return null
        KeyEvent shiftOnly = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT);
        assertNull(KeyStroke.fromKeyEvent(shiftOnly));

        // Back keypress should return null (reserved for navigation)
        KeyEvent backEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK);
        assertNull(KeyStroke.fromKeyEvent(backEvent));
    }

    @Test
    public void testEqualsAndHashCode() {
        KeyStroke s1 = KeyStroke.fromString("Ctrl+Shift+F");
        KeyStroke s2 = KeyStroke.fromString("Shift+Ctrl+F");
        KeyStroke s3 = KeyStroke.fromString("Ctrl+F");

        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
        assertFalse(s1.equals(s3));
    }

    @Test
    public void testToDisplayParts() {
        KeyStroke stroke = KeyStroke.fromString("Ctrl+Shift+P");
        assertNotNull(stroke);
        java.util.List<String> parts = stroke.toDisplayParts();
        assertEquals(3, parts.size());
        assertEquals("Ctrl", parts.get(0));
        assertEquals("Shift", parts.get(1));
        assertEquals("P", parts.get(2));
    }
}
