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
    private TerminalBuffer alternateBuffer = null;
    private boolean isAlternateBuffer = false;
    private int savedCol = 0;
    private int savedRow = 0;

    private int state = STATE_NORMAL;
    private final StringBuilder csiBuffer = new StringBuilder();

    private int cursorCol = 0;
    private int cursorRow = 0;
    private int savedCursorCol = 0;
    private int savedCursorRow = 0;
    private int currentFgColor = COLOR_DEFAULT_FG;
    private int currentBgColor = COLOR_DEFAULT_BG;
    private boolean isBold = false;

    public interface CprListener {
        void onCursorPositionReport(String response);
    }

    private CprListener cprListener = null;

    public static final int DEFAULT_COLUMNS = 80;
    public static final int DEFAULT_ROWS = 24;
    private int columns = DEFAULT_COLUMNS;
    private int rows = DEFAULT_ROWS;
    private boolean wrapPending = false;

    public TerminalEmulator(@NonNull TerminalBuffer buffer) {
        this.buffer = buffer;
    }

    public synchronized void setCprListener(CprListener listener) {
        this.cprListener = listener;
    }

    @NonNull
    public synchronized TerminalBuffer getActiveBuffer() {
        return (isAlternateBuffer && alternateBuffer != null) ? alternateBuffer : buffer;
    }

    public synchronized void setColumns(int columns) {
        if (columns > 0) {
            this.columns = columns;
        }
    }

    public synchronized int getColumns() {
        return columns;
    }

    public synchronized void setRows(int rows) {
        if (rows > 0) {
            this.rows = rows;
        }
    }

    public synchronized int getRows() {
        return rows;
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

    private int parse256Color(int colorIndex) {
        if (colorIndex >= 0 && colorIndex < 16) {
            return getAnsiColor(colorIndex);
        }
        if (colorIndex >= 16 && colorIndex <= 231) {
            int r = ((colorIndex - 16) / 36) * 51;
            int g = (((colorIndex - 16) % 36) / 6) * 51;
            int b = ((colorIndex - 16) % 6) * 51;
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        if (colorIndex >= 232 && colorIndex <= 255) {
            int gray = 8 + (colorIndex - 232) * 10;
            return 0xFF000000 | (gray << 16) | (gray << 8) | gray;
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
                } else if (c == '7') {
                    // DECSC: Save cursor
                    savedCursorRow = cursorRow;
                    savedCursorCol = cursorCol;
                    state = STATE_NORMAL;
                } else if (c == '8') {
                    // DECRC: Restore cursor
                    cursorRow = Math.min(savedCursorRow, Math.max(0, getActiveBuffer().getLineCount() - 1));
                    cursorCol = Math.min(savedCursorCol, MAX_CURSOR_COL);
                    getActiveBuffer().ensureLineCapacity(cursorRow);
                    state = STATE_NORMAL;
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

    private TerminalLine getActiveLine() {
        TerminalBuffer buf = getActiveBuffer();
        buf.ensureLineCapacity(cursorRow);
        return buf.getLine(cursorRow);
    }

    private void advanceLine() {
        cursorRow++;
        cursorCol = 0;
        TerminalBuffer buf = getActiveBuffer();
        if (cursorRow >= buf.getMaxLines()) {
            cursorRow = buf.getMaxLines() - 1;
            buf.newLine();
        } else {
            buf.ensureLineCapacity(cursorRow);
        }
        if (cursorRow >= buf.getLineCount()) {
            cursorRow = Math.max(0, buf.getLineCount() - 1);
        }
    }

    public synchronized void rewindWrappedContinuation() {
        if (isAlternateBuffer) return;
        TerminalBuffer buf = getActiveBuffer();
        boolean rewound = false;
        while (cursorRow > 0 && buf.getLine(cursorRow - 1) != null && buf.getLine(cursorRow - 1).isWrapped()) {
            if (cursorRow == buf.getLineCount() - 1) {
                buf.removeLastLine();
            } else {
                buf.getLine(cursorRow).clear();
            }
            cursorRow--;
            buf.getLine(cursorRow).setWrapped(false);
            rewound = true;
        }
        if (rewound) {
            cursorCol = 0;
            wrapPending = false;
        }
    }

    private void handleNormalChar(char c) {
        if (c == '\033') {
            state = STATE_ESCAPE;
        } else if (c == '\r') {
            cursorCol = 0;
            wrapPending = false;
        } else if (c == '\n') {
            advanceLine();
            wrapPending = false;
        } else if (c == '\b') {
            if (wrapPending) {
                wrapPending = false;
            } else if (cursorCol > 0) {
                cursorCol--;
            } else if (cursorRow > 0) {
                TerminalBuffer buf = getActiveBuffer();
                TerminalLine prev = buf.getLine(cursorRow - 1);
                if (prev != null && prev.isWrapped()) {
                    TerminalLine currentLine = buf.getLine(cursorRow);
                    boolean currentEmpty = (currentLine == null || currentLine.getText().trim().isEmpty());

                    cursorRow--;
                    int prevLen = (columns > 0) ? columns : prev.length();
                    cursorCol = Math.max(0, prevLen - 1);

                    if (currentEmpty) {
                        if (cursorRow == buf.getLineCount() - 2) {
                            buf.removeLastLine();
                        }
                        prev.setWrapped(false);
                    }
                }
            }
        } else if (c == '\t') {
            int spaces = 4 - (cursorCol % 4);
            for (int i = 0; i < spaces; i++) {
                if (wrapPending) {
                    getActiveLine().setWrapped(true);
                    advanceLine();
                    wrapPending = false;
                }
                getActiveLine().setCharAt(cursorCol, ' ', currentFgColor, currentBgColor, isBold);
                if (columns > 0 && cursorCol >= columns - 1) {
                    wrapPending = true;
                } else {
                    cursorCol++;
                }
            }
        } else if (c >= 32) {
            if (wrapPending) {
                getActiveLine().setWrapped(true);
                advanceLine();
                wrapPending = false;
            }
            getActiveLine().setCharAt(cursorCol, c, currentFgColor, currentBgColor, isBold);
            if (columns > 0 && cursorCol >= columns - 1) {
                wrapPending = true;
            } else {
                cursorCol++;
            }
        }
    }

    public static final int MAX_CURSOR_COL = 1000;
    public static final int MAX_CSI_BUFFER = 64;

    private void handleCsiChar(char c) {
        if ((c >= '0' && c <= '9') || c == ';' || c == '?' || c == ' ') {
            if (csiBuffer.length() < MAX_CSI_BUFFER) {
                csiBuffer.append(c);
            } else {
                state = STATE_CSI_IGNORE;
                csiBuffer.setLength(0);
            }
        } else {
            executeCsi(c, csiBuffer.toString());
            state = STATE_NORMAL;
            csiBuffer.setLength(0);
        }
    }

    private static int parseCount(String param) {
        if (param == null || param.trim().isEmpty()) return 1;
        try {
            int val = Integer.parseInt(param.trim());
            return Math.max(1, val);
        } catch (Exception ignored) {
            return 1;
        }
    }

    private void executeCsi(char command, String params) {
        if (command == 'm') {
            // SGR color & formatting
            parseSgr(params);
        } else if (command == 'J') {
            // Erase in Display (ED)
            String trimmedParams = params != null ? params.trim() : "";
            int mode = 0;
            if ("1".equals(trimmedParams)) mode = 1;
            else if ("2".equals(trimmedParams) || trimmedParams.startsWith("2")) mode = 2;
            else if ("3".equals(trimmedParams) || trimmedParams.startsWith("3")) mode = 3;

            if (mode == 0) {
                int screenBase = isAlternateBuffer ? 0 : Math.max(0, getActiveBuffer().getLineCount() - rows);
                if (cursorRow <= screenBase && cursorCol == 0) {
                    // Full screen clear from top-left (e.g. \033[H\033[J emitted by /system/bin/clear or busybox)
                    getActiveBuffer().clear();
                    cursorCol = 0;
                    cursorRow = 0;
                } else {
                    // Erase from cursor to end of screen (ECMA-48 ED 0)
                    getActiveLine().clearFrom(cursorCol);
                    if (columns > 0 && getActiveLine().length() < columns) {
                        getActiveLine().setWrapped(false);
                    }
                    TerminalBuffer buf = getActiveBuffer();
                    while (buf.getLineCount() > cursorRow + 1) {
                        if (!buf.removeLastLine()) break;
                    }
                }
            } else if (mode == 1) {
                // Erase from beginning of screen to cursor (ECMA-48 ED 1)
                getActiveLine().clearTo(cursorCol);
            } else if (mode == 2 || mode == 3) {
                // Erase entire screen / scrollback (ECMA-48 ED 2 / xterm ED 3)
                getActiveBuffer().clear();
                cursorCol = 0;
                cursorRow = 0;
            }
            wrapPending = false;
        } else if (command == 'K') {
            // Clear line
            if ("".equals(params) || "0".equals(params)) {
                getActiveLine().clearFrom(cursorCol);
                if (columns > 0 && getActiveLine().length() < columns) {
                    getActiveLine().setWrapped(false);
                }
            } else if ("1".equals(params)) {
                getActiveLine().clearTo(cursorCol);
            } else if ("2".equals(params)) {
                getActiveLine().clear();
                getActiveLine().setWrapped(false);
                cursorCol = 0;
            }
            wrapPending = false;
        } else if (command == 'A') {
            // Cursor Up
            int count = parseCount(params);
            cursorRow = Math.max(0, cursorRow - count);
            wrapPending = false;
        } else if (command == 'B') {
            // Cursor Down
            int count = parseCount(params);
            cursorRow = Math.min(Math.max(0, getActiveBuffer().getLineCount() - 1), cursorRow + count);
            cursorRow = Math.min(cursorRow, getActiveBuffer().getMaxLines() - 1);
            wrapPending = false;
        } else if (command == 'C') {
            // Cursor Forward
            int count = parseCount(params);
            if (columns > 0 && cursorCol + count >= columns && getActiveLine().isWrapped() && cursorRow < getActiveBuffer().getLineCount() - 1) {
                cursorRow++;
                cursorCol = (cursorCol + count) - columns;
            } else {
                cursorCol = Math.min(cursorCol + count, MAX_CURSOR_COL);
            }
            wrapPending = false;
        } else if (command == 'D') {
            // Cursor Backward
            int count = parseCount(params);
            if (wrapPending) {
                wrapPending = false;
                count--;
            }
            if (count > 0) {
                if (cursorCol - count < 0 && cursorRow > 0 && getActiveBuffer().getLine(cursorRow - 1).isWrapped()) {
                    TerminalLine prev = getActiveBuffer().getLine(cursorRow - 1);
                    cursorRow--;
                    int prevLen = (columns > 0) ? columns : prev.length();
                    cursorCol = Math.max(0, prevLen + (cursorCol - count));
                } else {
                    cursorCol = Math.max(0, cursorCol - count);
                }
            }
            wrapPending = false;
        } else if (command == 'E') {
            // Cursor Next Line (CNL: moves cursor to beginning of line n lines down)
            int count = parseCount(params);
            cursorRow = Math.min(Math.max(0, getActiveBuffer().getLineCount() - 1), cursorRow + count);
            cursorRow = Math.min(cursorRow, getActiveBuffer().getMaxLines() - 1);
            getActiveBuffer().ensureLineCapacity(cursorRow);
            cursorCol = 0;
            wrapPending = false;
        } else if (command == 'F') {
            // Cursor Previous Line (CPL: moves cursor to beginning of line n lines up)
            int count = parseCount(params);
            cursorRow = Math.max(0, cursorRow - count);
            cursorCol = 0;
            wrapPending = false;
        } else if (command == 'G') {
            // Cursor Horizontal Absolute (column n, 1-indexed)
            int col = parseCount(params);
            cursorCol = Math.max(0, col - 1);
            wrapPending = false;
        } else if (command == 'H' || command == 'f') {
            // Cursor Position (CUP: row;col, 1-indexed)
            int row = 1;
            int col = 1;
            if (!params.isEmpty()) {
                String[] parts = params.split(";");
                if (parts.length > 0 && !parts[0].isEmpty()) row = parseCount(parts[0]);
                if (parts.length > 1 && !parts[1].isEmpty()) col = parseCount(parts[1]);
            }
            cursorCol = Math.max(0, col - 1);
            if (isAlternateBuffer) {
                cursorRow = Math.min(Math.max(0, row - 1), getActiveBuffer().getMaxLines() - 1);
            } else {
                int screenBase = Math.max(0, getActiveBuffer().getLineCount() - rows);
                cursorRow = Math.min(screenBase + Math.max(0, row - 1), getActiveBuffer().getMaxLines() - 1);
            }
            getActiveBuffer().ensureLineCapacity(cursorRow);
            wrapPending = false;
        } else if (command == 'n' && "6".equals(params)) {
            // CPR: Cursor Position Report (\033[6n)
            int screenBase = isAlternateBuffer ? 0 : Math.max(0, getActiveBuffer().getLineCount() - rows);
            int reportRow = Math.max(1, Math.min(cursorRow - screenBase + 1, rows));
            int reportCol = Math.max(1, cursorCol + 1);
            String response = "\033[" + reportRow + ";" + reportCol + "R";
            if (cprListener != null) {
                cprListener.onCursorPositionReport(response);
            }
        } else if (command == 's') {
            // Save cursor position (ANSI SCP)
            savedCursorRow = cursorRow;
            savedCursorCol = cursorCol;
        } else if (command == 'u') {
            // Restore cursor position (ANSI RCP)
            cursorRow = Math.min(savedCursorRow, Math.max(0, getActiveBuffer().getLineCount() - 1));
            cursorCol = Math.min(savedCursorCol, MAX_CURSOR_COL);
            getActiveBuffer().ensureLineCapacity(cursorRow);
            wrapPending = false;
        } else if (command == 'X') {
            // Erase Character (ECH)
            int count = parseCount(params);
            TerminalLine line = getActiveLine();
            for (int i = 0; i < count && (cursorCol + i) < TerminalLine.MAX_LINE_LENGTH; i++) {
                line.setCharAt(cursorCol + i, ' ', COLOR_DEFAULT_FG, COLOR_DEFAULT_BG, false);
            }
        } else if (command == 'P') {
            // Delete Character (DCH: \033[P or \033[<n>P)
            int count = parseCount(params);
            TerminalLine line = getActiveLine();
            for (int i = 0; i < count && cursorCol < line.length(); i++) {
                line.deleteCharAt(cursorCol);
            }
        } else if (command == 'h' && ("?1049".equals(params) || "?47".equals(params))) {
            // Switch to alternate screen buffer
            if (!isAlternateBuffer) {
                isAlternateBuffer = true;
                savedCol = cursorCol;
                savedRow = cursorRow;
                if (alternateBuffer == null) {
                    alternateBuffer = new TerminalBuffer(200);
                } else {
                    alternateBuffer.clear();
                }
                cursorCol = 0;
                cursorRow = 0;
                wrapPending = false;
            }
        } else if (command == 'l' && ("?1049".equals(params) || "?47".equals(params))) {
            // Switch back to primary screen buffer
            if (isAlternateBuffer) {
                isAlternateBuffer = false;
                cursorCol = savedCol;
                cursorRow = Math.min(savedRow, Math.max(0, buffer.getLineCount() - 1));
                wrapPending = false;
            }
        }
    }

    private void parseSgr(String params) {
        if (params.isEmpty()) {
            resetStyles();
            return;
        }

        String[] tokens = params.split(";");
        for (int i = 0; i < tokens.length; i++) {
            int code = 0;
            try {
                code = Integer.parseInt(tokens[i].trim());
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
            } else if (code == 38) {
                if (i + 2 < tokens.length && "5".equals(tokens[i + 1].trim())) {
                    try {
                        int cIdx = Integer.parseInt(tokens[i + 2].trim());
                        currentFgColor = parse256Color(cIdx);
                    } catch (Exception ignored) {}
                    i += 2;
                } else if (i + 4 < tokens.length && "2".equals(tokens[i + 1].trim())) {
                    try {
                        int r = Integer.parseInt(tokens[i + 2].trim());
                        int g = Integer.parseInt(tokens[i + 3].trim());
                        int b = Integer.parseInt(tokens[i + 4].trim());
                        currentFgColor = 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
                    } catch (Exception ignored) {}
                    i += 4;
                }
            } else if (code == 39) {
                currentFgColor = COLOR_DEFAULT_FG;
            } else if (code >= 40 && code <= 47) {
                currentBgColor = getAnsiColor(code - 40);
            } else if (code == 48) {
                if (i + 2 < tokens.length && "5".equals(tokens[i + 1].trim())) {
                    try {
                        int cIdx = Integer.parseInt(tokens[i + 2].trim());
                        currentBgColor = parse256Color(cIdx);
                    } catch (Exception ignored) {}
                    i += 2;
                } else if (i + 4 < tokens.length && "2".equals(tokens[i + 1].trim())) {
                    try {
                        int r = Integer.parseInt(tokens[i + 2].trim());
                        int g = Integer.parseInt(tokens[i + 3].trim());
                        int b = Integer.parseInt(tokens[i + 4].trim());
                        currentBgColor = 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
                    } catch (Exception ignored) {}
                    i += 4;
                }
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
        cursorRow = Math.max(0, getActiveBuffer().getLineCount() - 1);
        wrapPending = false;
        state = STATE_NORMAL;
        csiBuffer.setLength(0);
        resetStyles();
    }

    public synchronized void clear() {
        getActiveBuffer().clear();
        cursorCol = 0;
        cursorRow = 0;
        wrapPending = false;
        state = STATE_NORMAL;
        csiBuffer.setLength(0);
        resetStyles();
    }

    public synchronized int getCursorCol() {
        return cursorCol;
    }

    public synchronized int getCursorRow() {
        return cursorRow;
    }
}
