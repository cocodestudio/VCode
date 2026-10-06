package com.cocode.vcode.ide.core.terminal;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single line of text and associated ANSI styles within the terminal screen buffer.
 * Optimized for low-end devices by keeping memory overhead minimal when lines use default styling.
 */
public class TerminalLine {

    public static class StyleRun {
        public int start;
        public int end;
        public final int fgColor;
        public final int bgColor;
        public final boolean bold;

        public StyleRun(int start, int end, int fgColor, int bgColor, boolean bold) {
            this.start = start;
            this.end = end;
            this.fgColor = fgColor;
            this.bgColor = bgColor;
            this.bold = bold;
        }

        public boolean matches(int fgColor, int bgColor, boolean bold) {
            return this.fgColor == fgColor && this.bgColor == bgColor && this.bold == bold;
        }
    }

    public static final int MAX_LINE_LENGTH = 4000;

    private final StringBuilder text = new StringBuilder();
    private List<StyleRun> styleRuns;
    private boolean wrapped = false;

    public TerminalLine() {
    }

    public synchronized boolean isWrapped() {
        return wrapped;
    }

    public synchronized void setWrapped(boolean wrapped) {
        this.wrapped = wrapped;
    }

    public synchronized int length() {
        return text.length();
    }

    @NonNull
    public synchronized String getText() {
        return text.toString();
    }

    public synchronized char charAt(int index) {
        if (index >= 0 && index < text.length()) {
            return text.charAt(index);
        }
        return ' ';
    }

    public synchronized void append(char c, int fgColor, int bgColor, boolean bold) {
        if (text.length() >= MAX_LINE_LENGTH) return;
        int pos = text.length();
        text.append(c);
        addStyle(pos, pos + 1, fgColor, bgColor, bold);
    }

    public synchronized void setCharAt(int index, char c, int fgColor, int bgColor, boolean bold) {
        if (index < 0 || index >= MAX_LINE_LENGTH) return;
        while (text.length() < index) {
            text.append(' ');
        }
        if (index == text.length()) {
            text.append(c);
            addStyle(index, index + 1, fgColor, bgColor, bold);
        } else {
            text.setCharAt(index, c);
            setStyleAt(index, fgColor, bgColor, bold);
        }
    }

    private void setStyleAt(int index, int fgColor, int bgColor, boolean bold) {
        if (styleRuns == null) {
            styleRuns = new ArrayList<>(2);
            styleRuns.add(new StyleRun(index, index + 1, fgColor, bgColor, bold));
            return;
        }

        // 1. Remove or adjust any existing run covering index
        for (int i = 0; i < styleRuns.size(); i++) {
            StyleRun run = styleRuns.get(i);
            if (run.start <= index && run.end > index) {
                if (run.matches(fgColor, bgColor, bold)) {
                    return; // Already has identical styling
                }
                if (run.start == index && run.end == index + 1) {
                    styleRuns.remove(i);
                } else if (run.start == index) {
                    run.start = index + 1;
                } else if (run.end == index + 1) {
                    run.end = index;
                } else {
                    int oldEnd = run.end;
                    run.end = index;
                    styleRuns.add(i + 1, new StyleRun(index + 1, oldEnd, run.fgColor, run.bgColor, run.bold));
                }
                break;
            }
        }

        // 2. Insert or merge the new styled segment [index, index + 1)
        int insertIdx = 0;
        while (insertIdx < styleRuns.size() && styleRuns.get(insertIdx).start < index) {
            insertIdx++;
        }

        boolean mergedWithPrev = false;
        if (insertIdx > 0) {
            StyleRun prev = styleRuns.get(insertIdx - 1);
            if (prev.end == index && prev.matches(fgColor, bgColor, bold)) {
                prev.end = index + 1;
                mergedWithPrev = true;
            }
        }

        if (insertIdx < styleRuns.size()) {
            StyleRun next = styleRuns.get(insertIdx);
            if (next.start == index + 1 && next.matches(fgColor, bgColor, bold)) {
                if (mergedWithPrev) {
                    StyleRun prev = styleRuns.get(insertIdx - 1);
                    prev.end = next.end;
                    styleRuns.remove(insertIdx);
                } else {
                    next.start = index;
                }
                return;
            }
        }

        if (!mergedWithPrev) {
            styleRuns.add(insertIdx, new StyleRun(index, index + 1, fgColor, bgColor, bold));
        }
    }

    public synchronized void deleteCharAt(int index) {
        if (index >= 0 && index < text.length()) {
            text.deleteCharAt(index);
            // Adjust styles
            if (styleRuns != null) {
                for (int i = styleRuns.size() - 1; i >= 0; i--) {
                    StyleRun run = styleRuns.get(i);
                    if (run.start >= index) {
                        if (run.end <= index + 1) {
                            styleRuns.remove(i);
                        } else {
                            run.end--;
                        }
                    } else if (run.end > index) {
                        run.end--;
                    }
                }
            }
        }
    }

    public synchronized void clear() {
        text.setLength(0);
        if (styleRuns != null) {
            styleRuns.clear();
        }
    }

    public synchronized void clearFrom(int startCol) {
        if (startCol < text.length()) {
            text.setLength(startCol);
            if (styleRuns != null) {
                for (int i = styleRuns.size() - 1; i >= 0; i--) {
                    StyleRun run = styleRuns.get(i);
                    if (run.start >= startCol) {
                        styleRuns.remove(i);
                    } else if (run.end > startCol) {
                        run.end = startCol;
                    }
                }
            }
        }
    }

    public synchronized void clearTo(int endCol) {
        int limit = Math.min(endCol, text.length());
        for (int i = 0; i < limit; i++) {
            setCharAt(i, ' ', TerminalEmulator.COLOR_DEFAULT_FG, TerminalEmulator.COLOR_DEFAULT_BG, false);
        }
    }

    private void addStyle(int start, int end, int fgColor, int bgColor, boolean bold) {
        if (styleRuns == null) {
            styleRuns = new ArrayList<>(2);
        }
        if (!styleRuns.isEmpty()) {
            StyleRun last = styleRuns.get(styleRuns.size() - 1);
            if (last.end == start && last.matches(fgColor, bgColor, bold)) {
                last.end = end;
                return;
            }
        }
        styleRuns.add(new StyleRun(start, end, fgColor, bgColor, bold));
    }

    public synchronized List<StyleRun> getStyleRuns() {
        if (styleRuns == null || styleRuns.isEmpty()) {
            return null;
        }
        return new ArrayList<>(styleRuns);
    }

    public synchronized String toAnsiString() {
        if (text.length() == 0) return "";
        if (styleRuns == null || styleRuns.isEmpty()) {
            return text.toString();
        }

        StringBuilder sb = new StringBuilder();
        int curPos = 0;
        int activeFg = TerminalEmulator.COLOR_DEFAULT_FG;
        int activeBg = TerminalEmulator.COLOR_DEFAULT_BG;
        boolean activeBold = false;

        for (StyleRun run : styleRuns) {
            int runStart = Math.min(run.start, text.length());
            int runEnd = Math.min(run.end, text.length());
            if (runStart > curPos) {
                if (activeFg != TerminalEmulator.COLOR_DEFAULT_FG || activeBg != TerminalEmulator.COLOR_DEFAULT_BG || activeBold) {
                    sb.append("\033[0m");
                    activeFg = TerminalEmulator.COLOR_DEFAULT_FG;
                    activeBg = TerminalEmulator.COLOR_DEFAULT_BG;
                    activeBold = false;
                }
                sb.append(text.substring(curPos, runStart));
                curPos = runStart;
            }

            if (runStart < runEnd) {
                boolean styleChanged = (run.fgColor != activeFg || run.bgColor != activeBg || run.bold != activeBold);
                if (styleChanged) {
                    if (run.fgColor == TerminalEmulator.COLOR_DEFAULT_FG && run.bgColor == TerminalEmulator.COLOR_DEFAULT_BG && !run.bold) {
                        sb.append("\033[0m");
                    } else {
                        sb.append("\033[0");
                        if (run.bold) {
                            sb.append(";1");
                        }
                        if (run.fgColor != TerminalEmulator.COLOR_DEFAULT_FG) {
                            sb.append(";").append(TerminalEmulator.findAnsiFgCode(run.fgColor));
                        }
                        if (run.bgColor != TerminalEmulator.COLOR_DEFAULT_BG) {
                            sb.append(";").append(TerminalEmulator.findAnsiBgCode(run.bgColor));
                        }
                        sb.append("m");
                    }
                    activeFg = run.fgColor;
                    activeBg = run.bgColor;
                    activeBold = run.bold;
                }
                sb.append(text.substring(runStart, runEnd));
                curPos = runEnd;
            }
        }

        if (curPos < text.length()) {
            if (activeFg != TerminalEmulator.COLOR_DEFAULT_FG || activeBg != TerminalEmulator.COLOR_DEFAULT_BG || activeBold) {
                sb.append("\033[0m");
            }
            sb.append(text.substring(curPos));
        } else if (activeFg != TerminalEmulator.COLOR_DEFAULT_FG || activeBg != TerminalEmulator.COLOR_DEFAULT_BG || activeBold) {
            sb.append("\033[0m");
        }

        return sb.toString();
    }
}
