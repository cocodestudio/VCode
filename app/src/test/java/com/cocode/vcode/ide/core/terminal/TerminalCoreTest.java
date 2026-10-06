package com.cocode.vcode.ide.core.terminal;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for terminal core components: TerminalLine, TerminalBuffer, and TerminalEmulator.
 */
public class TerminalCoreTest {

    @Test
    public void testTerminalLineAppendAndStyle() {
        TerminalLine line = new TerminalLine();
        line.append('H', 0xFFF44747, 0, true);
        line.append('i', 0xFFF44747, 0, true);
        line.append('!', 0xFFD4D4D4, 0, false);

        assertEquals("Hi!", line.getText());
        assertEquals(3, line.length());
        assertEquals('H', line.charAt(0));
        assertEquals('i', line.charAt(1));
        assertEquals('!', line.charAt(2));

        List<TerminalLine.StyleRun> runs = line.getStyleRuns();
        assertNotNull(runs);
        assertEquals(2, runs.size());
        assertEquals(0xFFF44747, runs.get(0).fgColor);
        assertTrue(runs.get(0).bold);
        assertEquals(0, runs.get(0).start);
        assertEquals(2, runs.get(0).end);

        assertEquals(0xFFD4D4D4, runs.get(1).fgColor);
        assertFalse(runs.get(1).bold);
        assertEquals(2, runs.get(1).start);
        assertEquals(3, runs.get(1).end);
    }

    @Test
    public void testTerminalLineDeleteCharAt() {
        TerminalLine line = new TerminalLine();
        line.append('a', 0, 0, false);
        line.append('b', 0, 0, false);
        line.append('c', 0, 0, false);

        line.deleteCharAt(1);
        assertEquals("ac", line.getText());
        assertEquals(2, line.length());
    }

    @Test
    public void testTerminalBufferBoundedCeiling() {
        int maxLines = 50;
        TerminalBuffer buffer = new TerminalBuffer(maxLines);

        for (int i = 0; i < 100; i++) {
            buffer.getCurrentLine().append((char) ('0' + (i % 10)), 0, 0, false);
            buffer.newLine();
        }

        // Buffer size must never exceed maxLines
        assertEquals(maxLines, buffer.getLineCount());
    }

    @Test
    public void testTerminalEmulatorPlainTextAndCarriageReturn() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        emulator.appendOutput("Hello\rWorld\n");

        assertEquals(2, buffer.getLineCount());
        assertEquals("World", buffer.getLine(0).getText());
    }

    @Test
    public void testTerminalEmulatorBackspace() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        emulator.appendOutput("abc\bd\n");

        assertEquals("abd", buffer.getLine(0).getText());
    }

    @Test
    public void testTerminalEmulatorAnsiColors() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        // \033[31m = Red, \033[32m = Green, \033[0m = Reset
        emulator.appendOutput("\033[31mRed\033[32mGreen\033[0mPlain\n");

        TerminalLine line = buffer.getLine(0);
        assertEquals("RedGreenPlain", line.getText());

        List<TerminalLine.StyleRun> runs = line.getStyleRuns();
        assertNotNull(runs);
        assertEquals(3, runs.size());
        assertEquals(0xFFF44747, runs.get(0).fgColor); // Red
        assertEquals(0xFF6A9955, runs.get(1).fgColor); // Green
        assertEquals(TerminalEmulator.COLOR_DEFAULT_FG, runs.get(2).fgColor); // Reset to default
    }

    @Test
    public void testTerminalEmulatorClearScreen() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        emulator.appendOutput("Line 1\nLine 2\nLine 3\n");
        assertEquals(4, buffer.getLineCount());

        // \033[2J = Clear entire screen
        emulator.appendOutput("\033[2J");
        assertEquals(1, buffer.getLineCount());
        assertEquals("", buffer.getLine(0).getText());
    }

    @Test
    public void testTerminalEmulatorThemeAwareAnsiColors() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        // Dark theme: Green is 0xFF6A9955
        emulator.setDarkTheme(true);
        emulator.appendOutput("\033[32mDarkGreen\033[0m\n");
        List<TerminalLine.StyleRun> darkRuns = buffer.getLine(0).getStyleRuns();
        assertNotNull(darkRuns);
        assertEquals(0xFF6A9955, darkRuns.get(0).fgColor);

        // Light theme: Green is 0xFF008000 (high contrast for white background)
        emulator.setDarkTheme(false);
        emulator.appendOutput("\033[32mLightGreen\033[0m\n");
        List<TerminalLine.StyleRun> lightRuns = buffer.getLine(1).getStyleRuns();
        assertNotNull(lightRuns);
        assertEquals(0xFF008000, lightRuns.get(0).fgColor);
    }

    @Test
    public void testTerminalEmulatorMultipleBackspaces() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        emulator.appendOutput("echo test");
        assertEquals("echo test", buffer.getCurrentLine().getText());

        emulator.appendOutput("\b\b\b\b");
        assertEquals("echo ", buffer.getCurrentLine().getText());

        emulator.appendOutput("123");
        assertEquals("echo 123", buffer.getCurrentLine().getText());
    }

    @Test
    public void testTerminalEmulatorTabExpansion() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        emulator.appendOutput("a\tb");
        assertEquals("a   b", buffer.getCurrentLine().getText());
    }

    @Test
    public void testTerminalLineMaxLineLengthCeiling() {
        TerminalLine line = new TerminalLine();
        for (int i = 0; i < 5000; i++) {
            line.append('x', 0, 0, false);
        }
        assertEquals(TerminalLine.MAX_LINE_LENGTH, line.length());
    }

    @Test
    public void testTerminalEmulatorCsiBufferOverflowProtection() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        // Send over 100 digits after \033[ without command execution char
        StringBuilder maliciousSeq = new StringBuilder("\033[");
        for (int i = 0; i < 150; i++) {
            maliciousSeq.append('9');
        }
        maliciousSeq.append('m');
        emulator.appendOutput(maliciousSeq.toString());

        // Should safely reset and not crash
        emulator.appendOutput("SafeText\n");
        assertEquals("SafeText", buffer.getLine(0).getText());
    }

    @Test
    public void testTerminalEmulatorCursorClamp() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        // Attempt to move cursor forward by 1,000,000 columns
        emulator.appendOutput("\033[1000000C");
        assertTrue(emulator.getCursorCol() <= TerminalEmulator.MAX_CURSOR_COL);
    }

    @Test
    public void testTerminalEmulatorAutoWrapAtColumns() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);
        emulator.setColumns(40);

        String longError = "cd: permission denied: directory navigation is restricted to project workspace\n";
        emulator.appendOutput(longError);

        // Should wrap across multiple lines instead of one single long line exceeding 40 columns
        assertTrue(buffer.getLineCount() >= 2);
        TerminalLine line0 = buffer.getLine(0);
        TerminalLine line1 = buffer.getLine(1);

        assertTrue(line0.length() <= 40);
        assertTrue(line1.length() <= 40);
        assertTrue(line0.isWrapped());
        assertFalse(line1.isWrapped());

        // First 40 characters on line 0, remainder on line 1
        assertEquals("cd: permission denied: directory navigat", line0.getText());
        assertEquals("ion is restricted to project workspace", line1.getText());
    }

    @Test
    public void testTerminalEmulatorBackspaceAcrossWrappedLines() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);
        emulator.setColumns(10);

        // Output 12 characters: 10 on line 0 (wrapped), 2 on line 1
        emulator.appendOutput("abcdefghijkl");
        assertEquals(2, buffer.getLineCount());
        assertEquals("abcdefghij", buffer.getLine(0).getText());
        assertEquals("kl", buffer.getLine(1).getText());
        assertTrue(buffer.getLine(0).isWrapped());

        // Backspace twice deletes 'l' and 'k' on line 1
        emulator.appendOutput("\b\b");
        assertEquals("", buffer.getLine(1).getText());

        // Next backspace crosses line boundary back to line 0 and deletes 'j'
        emulator.appendOutput("\b");
        assertEquals(1, buffer.getLineCount());
        assertEquals("abcdefghi", buffer.getLine(0).getText());
        assertFalse(buffer.getLine(0).isWrapped());
    }

    @Test
    public void testTerminalBufferGetAllTextPreservesWrappedLines() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);
        emulator.setColumns(10);

        emulator.appendOutput("1234567890abc");
        // Line 0 is wrapped ("1234567890"), line 1 is "abc"
        // getAllText() should preserve the logical unbroken line without an inserted \n
        assertEquals("1234567890abc", buffer.getAllText());
    }

    @Test
    public void testTerminalEmulatorClearResetsCursorCol() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        // Output some text advancing cursorCol
        emulator.appendOutput("Project:~$ long_command_here");
        assertTrue(emulator.getCursorCol() > 0);

        // Repeatedly clear and re-print prompt
        for (int i = 0; i < 5; i++) {
            emulator.clear();
            assertEquals(0, emulator.getCursorCol());
            assertEquals(1, buffer.getLineCount());
            assertEquals("", buffer.getLine(0).getText());

            emulator.appendOutput("Project:~$ ");
            assertEquals("Project:~$ ", buffer.getLine(0).getText());
            // No leading space padding and cursorCol accurately tracks prompt length
            assertEquals(11, emulator.getCursorCol());
        }
    }

    @Test
    public void testTerminalLineToAnsiStringPreservesColors() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        String prompt = "\033[1;32mAST Engine\033[0m:\033[1;34m~\033[0m$ ";
        emulator.appendOutput(prompt);

        TerminalLine line = buffer.getLine(0);
        assertEquals("AST Engine:~$ ", line.getText());

        String ansiSerialized = line.toAnsiString();
        assertTrue("Serialized ANSI should contain green color code", ansiSerialized.contains("32"));
        assertTrue("Serialized ANSI should contain blue color code", ansiSerialized.contains("34"));
        assertTrue("Serialized ANSI should contain bold code", ansiSerialized.contains(";1") || ansiSerialized.contains("1;"));

        // Replay serialized ANSI into a fresh emulator
        TerminalBuffer buffer2 = new TerminalBuffer(100);
        TerminalEmulator emulator2 = new TerminalEmulator(buffer2);
        emulator2.appendOutput(ansiSerialized);

        TerminalLine line2 = buffer2.getLine(0);
        assertEquals("AST Engine:~$ ", line2.getText());
        assertNotNull(line2.getStyleRuns());
        assertEquals(line.getStyleRuns().size(), line2.getStyleRuns().size());
        assertEquals(line.getStyleRuns().get(0).fgColor, line2.getStyleRuns().get(0).fgColor);
        assertEquals(line.getStyleRuns().get(0).bold, line2.getStyleRuns().get(0).bold);
    }

    @Test
    public void testTerminalBufferGetAllAnsiText() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        emulator.appendOutput("\033[31mError line\033[0m\n\033[32mSuccess line\033[0m\n");
        String allAnsi = buffer.getAllAnsiText();

        assertTrue(allAnsi.contains("31"));
        assertTrue(allAnsi.contains("32"));
        assertTrue(allAnsi.contains("Error line"));
        assertTrue(allAnsi.contains("Success line"));
    }

    @Test
    public void testTerminalBufferAdvanceBeyondMaxLinesClampsCursorAndScrolls() {
        int maxLines = 10;
        TerminalBuffer buffer = new TerminalBuffer(maxLines);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        for (int i = 0; i < 25; i++) {
            emulator.appendOutput("Line " + i + "\n");
        }

        assertEquals(maxLines, buffer.getLineCount());
        assertEquals(maxLines - 1, emulator.getCursorRow());
        // Line 24 followed by \n advanced the cursor, so Line 24 is at getLineCount() - 2
        assertTrue("Latest output line must be preserved",
                buffer.getLine(buffer.getLineCount() - 2).getText().contains("Line 24"));
        assertEquals("", buffer.getLine(buffer.getLineCount() - 1).getText());
    }

    @Test
    public void testTerminalEmulatorAlternateBufferSwitching() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        emulator.appendOutput("Primary Screen\n");
        assertEquals("Primary Screen", buffer.getLine(0).getText());

        // Switch to alternate buffer: \033[?1049h
        emulator.appendOutput("\033[?1049h");
        emulator.appendOutput("Alternate Screen\n");
        assertEquals("Alternate Screen", emulator.getActiveBuffer().getLine(0).getText());

        // Switch back to primary buffer: \033[?1049l
        emulator.appendOutput("\033[?1049l");
        assertEquals("Primary Screen", emulator.getActiveBuffer().getLine(0).getText());
    }
}
