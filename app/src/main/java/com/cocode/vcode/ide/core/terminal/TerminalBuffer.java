package com.cocode.vcode.ide.core.terminal;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Memory-bounded circular buffer for terminal lines.
 * Strictly capped at 1,000 lines to ensure ultra-low memory consumption on low-end Android devices.
 */
public class TerminalBuffer {

    public static final int DEFAULT_MAX_LINES = 1000;

    private final int maxLines;
    private final List<TerminalLine> lines = new ArrayList<>();

    public TerminalBuffer() {
        this(DEFAULT_MAX_LINES);
    }

    public TerminalBuffer(int maxLines) {
        this.maxLines = Math.max(1, maxLines);
        lines.add(new TerminalLine());
    }

    public synchronized int getLineCount() {
        return lines.size();
    }

    @NonNull
    public synchronized TerminalLine getLine(int index) {
        if (index >= 0 && index < lines.size()) {
            return lines.get(index);
        }
        return new TerminalLine();
    }

    @NonNull
    public synchronized TerminalLine getCurrentLine() {
        if (lines.isEmpty()) {
            lines.add(new TerminalLine());
        }
        return lines.get(lines.size() - 1);
    }

    public synchronized int getMaxLines() {
        return maxLines;
    }

    public synchronized void ensureLineCapacity(int requiredIndex) {
        int target = Math.min(requiredIndex, maxLines - 1);
        while (lines.size() <= target) {
            lines.add(new TerminalLine());
        }
    }

    public synchronized void newLine() {
        if (lines.size() >= maxLines) {
            lines.remove(0);
        }
        lines.add(new TerminalLine());
    }

    private void enforceCeiling() {
        while (lines.size() > maxLines) {
            lines.remove(0);
        }
    }

    public synchronized boolean removeLastLine() {
        if (lines.size() > 1) {
            lines.remove(lines.size() - 1);
            return true;
        }
        return false;
    }

    public synchronized void clear() {
        lines.clear();
        lines.add(new TerminalLine());
    }

    @NonNull
    public synchronized String getAllText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            TerminalLine line = lines.get(i);
            sb.append(line.getText());
            if (i < lines.size() - 1 && !line.isWrapped()) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    public synchronized String getAllAnsiText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            TerminalLine line = lines.get(i);
            sb.append(line.toAnsiString());
            if (i < lines.size() - 1 && !line.isWrapped()) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
