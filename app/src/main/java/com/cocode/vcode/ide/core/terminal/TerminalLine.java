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
        public final int start;
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
        } else {
            text.setCharAt(index, c);
        }
        addStyle(index, index + 1, fgColor, bgColor, bold);
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
