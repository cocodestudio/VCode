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

        // In standard VT100/POSIX, \b is non-destructive cursor backward movement.
        // It moves the cursor left without erasing the character cells.
        emulator.appendOutput("\b\b\b\b");
        assertEquals(5, emulator.getCursorCol());
        assertEquals("echo test", buffer.getCurrentLine().getText());

        emulator.appendOutput("123");
        assertEquals("echo 123t", buffer.getCurrentLine().getText());
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

        // Backspace twice moves cursor left across line 1
        emulator.appendOutput("\b\b");
        assertEquals(1, emulator.getCursorRow());
        assertEquals(0, emulator.getCursorCol());
        assertEquals("kl", buffer.getLine(1).getText());

        // Next backspace crosses line boundary back to line 0 at column 9 without deleting text
        emulator.appendOutput("\b");
        assertEquals(0, emulator.getCursorRow());
        assertEquals(9, emulator.getCursorCol());
        assertEquals("abcdefghij", buffer.getLine(0).getText());
        assertTrue(buffer.getLine(0).isWrapped());
    }

    @Test
    public void testTerminalEmulatorLeftArrowMovesCursorWithoutDeletingTextAfterCursor() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        // Type git status
        emulator.appendOutput("git status");
        assertEquals("git status", buffer.getCurrentLine().getText());
        assertEquals(10, emulator.getCursorCol());

        // When user presses left arrow in shell, shell line editor moves cursor left via \b or \033[D
        // Move back 3 positions (over 's', 'u', 't')
        emulator.appendOutput("\b\b\b");
        assertEquals(7, emulator.getCursorCol());
        assertEquals("git status", buffer.getCurrentLine().getText());

        // Also test ANSI cursor backward \033[D
        emulator.appendOutput("\033[D");
        assertEquals(6, emulator.getCursorCol());
        assertEquals("git status", buffer.getCurrentLine().getText());

        // Move cursor forward \033[C
        emulator.appendOutput("\033[C\033[C");
        assertEquals(8, emulator.getCursorCol());
        assertEquals("git status", buffer.getCurrentLine().getText());
    }

    @Test
    public void testTerminalEmulatorDeleteCharacterAnsi() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        emulator.appendOutput("git status");
        // Move cursor back to 's' in status
        emulator.appendOutput("\033[6D");
        assertEquals(4, emulator.getCursorCol());

        // ANSI DCH \033[P deletes character at cursor
        emulator.appendOutput("\033[P");
        assertEquals("git tatus", buffer.getCurrentLine().getText());

        // Delete 2 more characters \033[2P
        emulator.appendOutput("\033[2P");
        assertEquals("git tus", buffer.getCurrentLine().getText());
    }

    @Test
    public void testTerminalEmulatorEraseLineAnsi() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        emulator.appendOutput("git status");
        // Move cursor back to column 4
        emulator.appendOutput("\033[6D");
        assertEquals(4, emulator.getCursorCol());

        // \033[K erases from cursor to end of line
        emulator.appendOutput("\033[K");
        assertEquals("git ", buffer.getCurrentLine().getText());
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

    @Test
    public void testTerminalEmulatorProgressUpdateInPlaceWithoutLineBloat() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        // Multiple updates separated by carriage returns
        emulator.appendOutput("Downloading: 10%\rDownloading: 50%\rDownloading: 100%\r\n");

        // Stays on a single line; advances to line 2 only after \r\n
        assertEquals(2, buffer.getLineCount());
        assertEquals("Downloading: 100%", buffer.getLine(0).getText());
    }

    @Test
    public void testTerminalEmulatorMultiLinePromptPreservesLine0PromptOnCarriageReturn() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);
        emulator.setColumns(30);

        // Prompt + command wraps onto line 1
        String prompt = "\033[01;32mMy Awesome Project\033[00m:\033[01;34m~\033[00m$ ";
        emulator.appendOutput(prompt);
        emulator.appendOutput("git commit -m \"very long commit message here\"");

        // Prompt + command is wrapped across line 0, 1, and 2
        assertEquals(3, buffer.getLineCount());
        assertTrue(buffer.getLine(0).isWrapped());
        assertTrue("Line 0 must retain prompt", buffer.getLine(0).getText().contains("My Awesome Project:~$"));

        // Shell line editor sends \r to reposition or redraw
        emulator.appendOutput("\r");

        // Line 0 prompt MUST NOT be erased or cleared
        assertEquals(3, buffer.getLineCount());
        assertTrue("Line 0 must remain intact after \\r", buffer.getLine(0).getText().contains("My Awesome Project:~$"));
    }

    @Test
    public void testTerminalEmulatorNormalWrappedLineWithCrlfPreservesContent() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);
        emulator.setColumns(10);

        // Long line followed by standard CRLF line break
        emulator.appendOutput("1234567890abcdef\r\n");

        assertEquals(3, buffer.getLineCount());
        assertEquals("1234567890", buffer.getLine(0).getText());
        assertEquals("abcdef", buffer.getLine(1).getText());
        assertTrue(buffer.getLine(0).isWrapped());
    }

    @Test
    public void testTerminalEmulatorAnsiCursorSaveRestore() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        // ANSI SCP / RCP: \033[s and \033[u
        emulator.appendOutput("Initial: \033[s10%\033[u50%\033[u100%\n");
        assertEquals("Initial: 100%", buffer.getLine(0).getText());

        // DEC DECSC / DECRC: \0337 and \0338
        emulator.appendOutput("Stage: \03371\03382\03383\n");
        assertEquals("Stage: 3", buffer.getLine(1).getText());
    }

    @Test
    public void testTerminalEmulatorClearLineAnsi() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        // \r\033[K clears from column 0
        emulator.appendOutput("Long text line here\r\033[KShort\n");
        assertEquals("Short", buffer.getLine(0).getText());

        // \033[2K clears entire line
        emulator.appendOutput("Another text line\033[2KCleared\n");
        assertEquals("Cleared", buffer.getLine(1).getText());
    }

    @Test
    public void testTerminalEmulatorCursorPreviousLine() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        emulator.appendOutput("Line 1\nLine 2\033[1FUpdated Line 1\n");
        assertEquals("Updated Line 1", buffer.getLine(0).getText());
    }

    @Test
    public void testTerminalEmulatorEraseCharacters() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        emulator.appendOutput("Hello World\r\033[5X");
        // Erases first 5 characters (Hello) to spaces
        assertEquals("      World", buffer.getLine(0).getText());
    }

    @Test
    public void testTerminalBufferCircularRingRotation() {
        int maxLines = 5;
        TerminalBuffer buffer = new TerminalBuffer(maxLines);

        for (int i = 1; i <= 15; i++) {
            TerminalLine line = buffer.getCurrentLine();
            String s = "Line " + i;
            for (int c = 0; c < s.length(); c++) {
                line.append(s.charAt(c), 0, 0, false);
            }
            if (i < 15) {
                buffer.newLine();
            }
        }

        assertEquals(5, buffer.getLineCount());
        // Old lines 1-10 should have dropped out of circular window. Lines 11-15 remain.
        assertEquals("Line 11", buffer.getLine(0).getText());
        assertEquals("Line 12", buffer.getLine(1).getText());
        assertEquals("Line 13", buffer.getLine(2).getText());
        assertEquals("Line 14", buffer.getLine(3).getText());
        assertEquals("Line 15", buffer.getLine(4).getText());
        assertEquals("Line 15", buffer.getCurrentLine().getText());
    }

    @Test
    public void testTerminalEmulatorEraseDisplayModes() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        // Populate 3 lines
        emulator.appendOutput("Line 1: prompt$ foo\nLine 2: bar\nLine 3: baz");
        assertEquals(3, buffer.getLineCount());

        // Mode 0: \033[J or \033[0J on Line 2, col 8
        emulator.appendOutput("\r\033[A\033[8C\033[J");
        // Line 0 ("Line 1: prompt$ foo") must remain untouched
        assertEquals("Line 1: prompt$ foo", buffer.getLine(0).getText());
        // Line 1 should only retain chars up to col 8 ("Line 2: ")
        assertEquals("Line 2: ", buffer.getLine(1).getText());
        // Line 2 ("Line 3: baz") should be truncated/removed
        assertEquals(2, buffer.getLineCount());

        // Mode 2: \033[2J clears screen
        emulator.appendOutput("\033[2J");
        assertEquals("", buffer.getLine(0).getText());
        assertEquals(0, emulator.getCursorCol());

        // Mode 3: \033[3J clears entire buffer
        emulator.appendOutput("New text\nSecond text\033[3J");
        assertEquals(1, buffer.getLineCount());
        assertEquals("", buffer.getLine(0).getText());
    }

    @Test
    public void testTerminalEmulatorCursorPositionReport() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);
        final String[] receivedResponse = new String[1];

        emulator.setCprListener(response -> receivedResponse[0] = response);

        // Move cursor to row 3 (0-indexed 2), col 7 (0-indexed 6)
        emulator.appendOutput("Hello\nWorld\nFooBar");
        emulator.appendOutput("\033[6n");

        assertNotNull(receivedResponse[0]);
        // CPR response format: \033[<row>;<col>R (1-indexed)
        // cursor is on line 2 (row 3) at col 6 (col 7)
        assertEquals("\033[3;7R", receivedResponse[0]);
    }

    @Test
    public void testTerminalEmulatorDelayedAutoWrap() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);
        emulator.setColumns(10);

        // Type exactly 10 characters: fills line 0 without immediate wrap
        emulator.appendOutput("0123456789");
        assertEquals(1, buffer.getLineCount());
        assertEquals("0123456789", buffer.getLine(0).getText());
        assertEquals(9, emulator.getCursorCol());

        // 11th character triggers the delayed wrap
        emulator.appendOutput("A");
        assertEquals(2, buffer.getLineCount());
        assertTrue(buffer.getLine(0).isWrapped());
        assertEquals("A", buffer.getLine(1).getText());
        assertEquals(1, emulator.getCursorCol());
    }

    @Test
    public void testMultilineWrappedInputAndSeamlessBackspaceUnwrap() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);
        emulator.setColumns(20);

        // Prompt (13 chars) + Command (12 chars) = 25 chars -> Line 0 (20 chars), Line 1 (5 chars)
        String prompt = "<Project>:~$ ";
        String command = "hello world!";
        emulator.appendOutput(prompt + command);

        assertEquals(2, buffer.getLineCount());
        assertTrue(buffer.getLine(0).isWrapped());
        assertEquals(5, emulator.getCursorCol());
        assertEquals(1, emulator.getCursorRow());

        // Backspace characters on line 1 ("world!")
        // Each backspace is \b \b (0x08, 0x20, 0x08)
        for (int i = 0; i < 5; i++) {
            emulator.appendOutput("\b \b");
        }

        // Now cursor is at line 1, col 0, and line 1 is cleared
        assertEquals(0, emulator.getCursorCol());
        assertEquals(1, emulator.getCursorRow());

        // Next backspace must unwrap to line 0, column 19, and prune the empty line 1 from buffer
        emulator.appendOutput("\b \b");
        assertEquals(1, buffer.getLineCount());
        assertEquals(0, emulator.getCursorRow());
        assertEquals(19, emulator.getCursorCol());

        // Further backspace continues naturally deleting characters on line 0
        emulator.appendOutput("\b \b");
        assertEquals(1, buffer.getLineCount());
        assertEquals(18, emulator.getCursorCol());
    }

    @Test
    public void testMultilineWrappedInputWithCsiK() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);
        emulator.setColumns(15);

        // 15 chars on line 0 + 3 chars on line 1
        emulator.appendOutput("0123456789ABCDExyz");
        assertEquals(2, buffer.getLineCount());
        assertTrue(buffer.getLine(0).isWrapped());
        assertEquals(1, emulator.getCursorRow());
        assertEquals(3, emulator.getCursorCol());

        // Delete 'z', 'y', 'x' via \b\033[K
        emulator.appendOutput("\b\033[K");
        emulator.appendOutput("\b\033[K");
        emulator.appendOutput("\b\033[K");

        assertEquals(0, emulator.getCursorCol());
        assertEquals(1, emulator.getCursorRow());

        // Unwrap back to line 0
        emulator.appendOutput("\b\033[K");
        assertEquals(1, buffer.getLineCount());
        assertEquals(0, emulator.getCursorRow());
        assertEquals(14, emulator.getCursorCol());
    }

    @Test
    public void testMultilineHistoryNavigationRewindsContinuationLines() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);
        emulator.setColumns(20);

        // Multi-line wrapped command: Prompt (13 chars) + Long Command (17 chars) = 30 chars
        // Line 0: 20 chars, wrapped=true
        // Line 1: 10 chars, wrapped=false
        String prompt = "<Project>:~$ ";
        String longCommand = "echo 1234567890";
        emulator.appendOutput(prompt + longCommand);

        assertEquals(2, buffer.getLineCount());
        assertTrue(buffer.getLine(0).isWrapped());
        assertEquals(1, emulator.getCursorRow());
        assertEquals(8, emulator.getCursorCol());

        // User clicks Up Arrow to fetch previous history command.
        // rewindWrappedContinuation must prune line 1, unwrap line 0, and move cursor to line 0, col 0.
        emulator.rewindWrappedContinuation();

        assertEquals(1, buffer.getLineCount());
        assertFalse(buffer.getLine(0).isWrapped());
        assertEquals(0, emulator.getCursorRow());
        assertEquals(0, emulator.getCursorCol());

        // Shell redraws line with shorter history command (e.g., "ls")
        emulator.appendOutput("\r" + prompt + "ls\033[K");

        // Verify that exactly ONE line and ONE prompt exists, with the new command displayed cleanly
        assertEquals(1, buffer.getLineCount());
        assertEquals(0, emulator.getCursorRow());
        assertEquals("<Project>:~$ ls", buffer.getLine(0).getText());
        assertEquals(15, emulator.getCursorCol());
    }

    @Test
    public void testSingleLineHistoryNavigationPreservesState() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);
        emulator.setColumns(80);

        String prompt = "<Project>:~$ ";
        emulator.appendOutput(prompt + "ls");

        assertEquals(1, buffer.getLineCount());
        assertEquals(0, emulator.getCursorRow());
        assertEquals(15, emulator.getCursorCol());

        // Single-line command was not wrapped; rewindWrappedContinuation must touch nothing
        emulator.rewindWrappedContinuation();

        assertEquals(1, buffer.getLineCount());
        assertEquals(0, emulator.getCursorRow());
        assertEquals(15, emulator.getCursorCol());
    }

    @Test
    public void testClearScreenSequenceWithScrollbackWipesBufferAndHomesCursor() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);
        emulator.setRows(24);
        emulator.setColumns(80);

        // Fill buffer with 40 lines
        for (int i = 0; i < 40; i++) {
            emulator.appendOutput("Line " + i + "\r\n");
        }
        assertTrue(buffer.getLineCount() > 24);

        // Emulate Toybox / Linux POSIX `clear` binary output: \033[H\033[J
        emulator.appendOutput("\033[H\033[J");

        // Entire buffer must be cleared, cursor homed to (0, 0)
        assertEquals(1, buffer.getLineCount());
        assertEquals(0, emulator.getCursorRow());
        assertEquals(0, emulator.getCursorCol());

        // Subsequent prompt must appear cleanly at line 0
        emulator.appendOutput("vcode:~$ ");
        assertEquals(1, buffer.getLineCount());
        assertEquals("vcode:~$ ", buffer.getLine(0).getText());
        assertEquals(0, emulator.getCursorRow());
        assertEquals(9, emulator.getCursorCol());
    }

    @Test
    public void testEraseInDisplayMode2And3WipesBufferAndHomesCursor() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);
        emulator.setRows(24);
        emulator.setColumns(80);

        // Fill buffer
        for (int i = 0; i < 30; i++) {
            emulator.appendOutput("Line " + i + "\r\n");
        }
        assertEquals(31, buffer.getLineCount());

        // Test mode 2: \033[2J
        emulator.appendOutput("\033[2J");
        assertEquals(1, buffer.getLineCount());
        assertEquals(0, emulator.getCursorRow());
        assertEquals(0, emulator.getCursorCol());

        // Refill and test mode 3: \033[3J
        for (int i = 0; i < 30; i++) {
            emulator.appendOutput("Line " + i + "\r\n");
        }
        assertEquals(31, buffer.getLineCount());

        emulator.appendOutput("\033[3J");
        assertEquals(1, buffer.getLineCount());
        assertEquals(0, emulator.getCursorRow());
        assertEquals(0, emulator.getCursorCol());
    }

    @Test
    public void testPartialEraseInDisplayPreservesPrecedingLines() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);
        emulator.setRows(24);
        emulator.setColumns(80);

        emulator.appendOutput("Line 0\r\nLine 1\r\nLine 2\r\nLine 3");
        assertEquals(4, buffer.getLineCount());

        // Position cursor on row 2, col 3 and emit \033[J (partial clear from cursor)
        emulator.appendOutput("\033[2;3H\033[J");

        // Lines 0 and 1 must be preserved
        assertEquals("Line 0", buffer.getLine(0).getText());
        assertTrue(buffer.getLine(1).getText().startsWith("Li"));
    }

    @Test
    public void testTerminalEmulatorClearMethodDirectlyResetsState() {
        TerminalBuffer buffer = new TerminalBuffer(100);
        TerminalEmulator emulator = new TerminalEmulator(buffer);

        for (int i = 0; i < 20; i++) {
            emulator.appendOutput("Output " + i + "\r\n");
        }
        assertEquals(21, buffer.getLineCount());

        emulator.clear();

        assertEquals(1, buffer.getLineCount());
        assertEquals("", buffer.getLine(0).getText());
        assertEquals(0, emulator.getCursorRow());
        assertEquals(0, emulator.getCursorCol());
    }
}

