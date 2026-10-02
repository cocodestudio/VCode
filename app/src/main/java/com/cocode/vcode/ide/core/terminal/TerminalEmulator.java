package com.cocode.vcode.ide.core.terminal;

import androidx.annotation.NonNull;

/**
 * High-performance state-machine parser for terminal data and ANSI escape sequences.
 * Handles text streaming, carriage returns, newlines, and SGR color formatting.
 */
public class TerminalEmulator {

    public static final int COLOR_DEFAULT_FG = 0;
    public static final int COLOR_DEFAULT_BG = 0;

    private static final int[] ANSI_PALETTE_DARK = {
            0xFF1E1E1E, // 0 Black
            0xFFF44747, // 1 Red
            0xFF6A9955, // 2 Green
            0xFFDCDCAA, // 3 Yellow
            0xFF569CD6, // 4 Blue
            0xFFC586C0, // 5 Magenta
            0xFF4EC9B0, // 6 Cyan
            0xFFD4D4D4, // 7 White
            0xFF808080, // 8 Bright Black (Gray)
            0xFFFF6B68, // 9 Bright Red
            0xFFB5CEA8, // 10 Bright Green
            0xFFD7BA7D, // 11 Bright Yellow
            0xFF9CDCFE, // 12 Bright Blue
            0xFFDDBBFF, // 13 Bright Magenta
            0xFF4FC1FF, // 14 Bright Cyan
            0xFFFFFFFF  // 15 Bright White
    };

    private static final int[] ANSI_PALETTE_LIGHT = {
            0xFF000000, // 0 Black
            0xFFCD3131, // 1 Red
            0xFF008000, // 2 Green
            0xFF795E26, // 3 Yellow
            0xFF0451A5, // 4 Blue
            0xFFBC05BC, // 5 Magenta
            0xFF098658, // 6 Cyan
            0xFF6E7681, // 7 White
            0xFF666666, // 8 Bright Black (Gray)
            0xFFCD3131, // 9 Bright Red
            0xFF107C10, // 10 Bright Green
            0xFF8A6D3B, // 11 Bright Yellow
            0xFF005FB8, // 12 Bright Blue
            0xFFAF00DB, // 13 Bright Magenta
            0xFF00758F, // 14 Bright Cyan
            0xFF24292F  // 15 Bright White
    };

    private boolean isDarkTheme = true;

    private static final int STATE_NORMAL = 0;
    private static final int STATE_ESCAPE = 1;
    private static final int STATE_CSI = 2;
    private static final int STATE_CSI_IGNORE = 3;

    private final TerminalBuffer buffer;
    private int state = STATE_NORMAL;
    private final StringBuilder csiBuffer = new StringBuilder();

    private int cursorCol = 0;
    private int currentFgColor = COLOR_DEFAULT_FG;
    private int currentBgColor = COLOR_DEFAULT_BG;
    private boolean isBold = false;

    public static final int DEFAULT_COLUMNS = 80;
    private int columns = DEFAULT_COLUMNS;

    public TerminalEmulator(@NonNull TerminalBuffer buffer) {
        this.buffer = buffer;
    }

    public synchronized void setColumns(int columns) {
        if (columns > 0) {
            this.columns = columns;
        }
    }

    public synchronized int getColumns() {
        return columns;
    }

    public synchronized void setDarkTheme(boolean darkTheme) {
        this.isDarkTheme = darkTheme;
    }

    private int getAnsiColor(int index) {
        int[] palette = isDarkTheme ? ANSI_PALETTE_DARK : ANSI_PALETTE_LIGHT;
        if (index >= 0 && index < palette.length) {
            return palette[index];
        }
        return COLOR_DEFAULT_FG;
    }

    public static int findAnsiFgCode(int color) {
        if (color == COLOR_DEFAULT_FG) return 39;
        for (int i = 0; i < 8; i++) {
            if (ANSI_PALETTE_DARK[i] == color || ANSI_PALETTE_LIGHT[i] == color) {
                return 30 + i;
            }
        }
        for (int i = 8; i < 16; i++) {
            if (ANSI_PALETTE_DARK[i] == color || ANSI_PALETTE_LIGHT[i] == color) {
                return 90 + (i - 8);
            }
        }
        return 39;
    }

    public static int findAnsiBgCode(int color) {
        if (color == COLOR_DEFAULT_BG) return 49;
        for (int i = 0; i < 8; i++) {
            if (ANSI_PALETTE_DARK[i] == color || ANSI_PALETTE_LIGHT[i] == color) {
                return 40 + i;
            }
        }
        for (int i = 8; i < 16; i++) {
            if (ANSI_PALETTE_DARK[i] == color || ANSI_PALETTE_LIGHT[i] == color) {
                return 100 + (i - 8);
            }
        }
        return 49;
    }

    public synchronized void appendOutput(String text) {
        if (text == null || text.isEmpty()) return;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            processChar(c);
        }
    }

    private void processChar(char c) {
        switch (state) {
            case STATE_NORMAL:
                handleNormalChar(c);
                break;
            case STATE_ESCAPE:
                if (c == '[') {
                    state = STATE_CSI;
                    csiBuffer.setLength(0);
                } else {
                    state = STATE_NORMAL;
                }
                break;
            case STATE_CSI:
                handleCsiChar(c);
                break;
            case STATE_CSI_IGNORE:
                if (c >= '@' && c <= '~') {
                    state = STATE_NORMAL;
                } else if (c == '\033') {
                    state = STATE_ESCAPE;
                } else if (c == '\n' || c == '\r') {
                    handleNormalChar(c);
                    state = STATE_NORMAL;
                }
                break;
        }
    }

    private void handleNormalChar(char c) {
        if (c == '\033') {
            state = STATE_ESCAPE;
        } else if (c == '\r') {
            cursorCol = 0;
        } else if (c == '\n') {
            buffer.newLine();
            cursorCol = 0;
        } else if (c == '\b') {
            if (cursorCol > 0) {
                cursorCol--;
                TerminalLine line = buffer.getCurrentLine();
                line.deleteCharAt(cursorCol);
            } else if (buffer.getLineCount() > 1) {
                TerminalLine current = buffer.getCurrentLine();
                if (current.length() == 0) {
                    TerminalLine prev = buffer.getLine(buffer.getLineCount() - 2);
                    if (prev.isWrapped()) {
                        buffer.removeLastLine();
                        prev.setWrapped(false);
                        cursorCol = prev.length();
                        if (cursorCol > 0) {
                            cursorCol--;
                            prev.deleteCharAt(cursorCol);
                        }
                    }
                }
            }
        } else if (c == '\t') {
            int spaces = 4 - (cursorCol % 4);
            for (int i = 0; i < spaces; i++) {
                if (columns > 0 && cursorCol >= columns) {
                    buffer.getCurrentLine().setWrapped(true);
                    buffer.newLine();
                    cursorCol = 0;
                }
                buffer.getCurrentLine().setCharAt(cursorCol++, ' ', currentFgColor, currentBgColor, isBold);
            }
        } else if (c >= 32) {
            if (columns > 0 && cursorCol >= columns) {
                buffer.getCurrentLine().setWrapped(true);
                buffer.newLine();
                cursorCol = 0;
            }
            TerminalLine line = buffer.getCurrentLine();
            line.setCharAt(cursorCol++, c, currentFgColor, currentBgColor, isBold);
        }
    }

    public static final int MAX_CURSOR_COL = 1000;
    public static final int MAX_CSI_BUFFER = 64;

    private void handleCsiChar(char c) {
        if ((c >= '0' && c <= '9') || c == ';' || c == '?' || c == ' ') {
            if (csiBuffer.length() < MAX_CSI_BUFFER) {
                csiBuffer.append(c);
            } else {
                // Prevent buffer exhaustion from malicious/corrupt escape sequence
                state = STATE_CSI_IGNORE;
                csiBuffer.setLength(0);
            }
        } else {
            // Execution character
            executeCsi(c, csiBuffer.toString());
            state = STATE_NORMAL;
            csiBuffer.setLength(0);
        }
    }

    private void executeCsi(char command, String params) {
        if (command == 'm') {
            // SGR color & formatting
            parseSgr(params);
        } else if (command == 'J') {
            // Clear screen
            if ("2".equals(params) || "".equals(params)) {
                buffer.clear();
                cursorCol = 0;
            }
        } else if (command == 'K') {
            // Clear line
            if ("".equals(params) || "0".equals(params)) {
                buffer.getCurrentLine().clearFrom(cursorCol);
            } else if ("2".equals(params)) {
                buffer.getCurrentLine().clear();
                cursorCol = 0;
            }
        } else if (command == 'H' || command == 'f') {
            // Cursor Home
            cursorCol = 0;
        } else if (command == 'C') {
            // Move cursor forward
            int count = 1;
            try {
                if (!params.isEmpty()) count = Integer.parseInt(params);
            } catch (Exception ignored) {
            }
            if (count > 0) {
                cursorCol = Math.min(cursorCol + count, MAX_CURSOR_COL);
            }
        } else if (command == 'D') {
            // Move cursor backward
            int count = 1;
            try {
                if (!params.isEmpty()) count = Integer.parseInt(params);
            } catch (Exception ignored) {
            }
            if (count > 0) {
                cursorCol = Math.max(0, cursorCol - count);
            }
        }
    }

    private void parseSgr(String params) {
        if (params.isEmpty()) {
            resetStyles();
            return;
        }

        String[] tokens = params.split(";");
        for (String token : tokens) {
            int code = 0;
            try {
                code = Integer.parseInt(token.trim());
            } catch (Exception ignored) {
            }

            if (code == 0) {
                resetStyles();
            } else if (code == 1) {
                isBold = true;
            } else if (code == 22) {
                isBold = false;
            } else if (code >= 30 && code <= 37) {
                currentFgColor = getAnsiColor(code - 30);
            } else if (code == 39) {
                currentFgColor = COLOR_DEFAULT_FG;
            } else if (code >= 40 && code <= 47) {
                currentBgColor = getAnsiColor(code - 40);
            } else if (code == 49) {
                currentBgColor = COLOR_DEFAULT_BG;
            } else if (code >= 90 && code <= 97) {
                currentFgColor = getAnsiColor(code - 90 + 8);
            } else if (code >= 100 && code <= 107) {
                currentBgColor = getAnsiColor(code - 100 + 8);
            }
        }
    }

    private void resetStyles() {
        currentFgColor = COLOR_DEFAULT_FG;
        currentBgColor = COLOR_DEFAULT_BG;
        isBold = false;
    }

    public synchronized void resetCursor() {
        cursorCol = 0;
        state = STATE_NORMAL;
        csiBuffer.setLength(0);
        resetStyles();
    }

    public synchronized void clear() {
        buffer.clear();
        resetCursor();
    }

    public synchronized int getCursorCol() {
        return cursorCol;
    }

    public synchronized int getCursorRow() {
        return Math.max(0, buffer.getLineCount() - 1);
    }
}
