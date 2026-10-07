package com.cocode.vcode.ide.core.terminal;

import androidx.annotation.NonNull;


/**
 * Memory-bounded circular buffer for terminal lines.
 * Strictly capped at 1,000 lines to ensure ultra-low memory consumption on low-end Android devices.
 */
public class TerminalBuffer {

    public static final int DEFAULT_MAX_LINES = 1000;

    private final int maxLines;
    private final TerminalLine[] buffer;
    private int head = 0;
    private int size = 0;

    public TerminalBuffer() {
        this(DEFAULT_MAX_LINES);
    }

    public TerminalBuffer(int maxLines) {
        this.maxLines = Math.max(1, maxLines);
        this.buffer = new TerminalLine[this.maxLines];
        this.buffer[0] = new TerminalLine();
        this.size = 1;
        this.head = 0;
    }

    public synchronized int getLineCount() {
        return size;
    }

    @NonNull
    public synchronized TerminalLine getLine(int index) {
        if (index >= 0 && index < size) {
            int actualIdx = (head + index) % maxLines;
            TerminalLine line = buffer[actualIdx];
            return line != null ? line : new TerminalLine();
        }
        return new TerminalLine();
    }

    @NonNull
    public synchronized TerminalLine getCurrentLine() {
        if (size == 0) {
            buffer[0] = new TerminalLine();
            size = 1;
            head = 0;
            return buffer[0];
        }
        int lastIdx = (head + size - 1) % maxLines;
        return buffer[lastIdx];
    }

    public synchronized int getMaxLines() {
        return maxLines;
    }

    public synchronized void ensureLineCapacity(int requiredIndex) {
        int target = Math.min(requiredIndex, maxLines - 1);
        while (size <= target) {
            int nextIdx = (head + size) % maxLines;
            buffer[nextIdx] = new TerminalLine();
            size++;
        }
    }

    public synchronized void newLine() {
        if (size < maxLines) {
            int nextIdx = (head + size) % maxLines;
            buffer[nextIdx] = new TerminalLine();
            size++;
        } else {
            buffer[head] = new TerminalLine();
            head = (head + 1) % maxLines;
        }
    }

    public synchronized boolean removeLastLine() {
        if (size > 1) {
            int lastIdx = (head + size - 1) % maxLines;
            buffer[lastIdx] = null;
            size--;
            return true;
        }
        return false;
    }

    public synchronized void clear() {
        for (int i = 0; i < maxLines; i++) {
            buffer[i] = null;
        }
        head = 0;
        buffer[0] = new TerminalLine();
        size = 1;
    }

    @NonNull
    public synchronized String getAllText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            TerminalLine line = getLine(i);
            sb.append(line.getText());
            if (i < size - 1 && !line.isWrapped()) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    public synchronized String getAllAnsiText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            TerminalLine line = getLine(i);
            sb.append(line.toAnsiString());
            if (i < size - 1 && !line.isWrapped()) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
