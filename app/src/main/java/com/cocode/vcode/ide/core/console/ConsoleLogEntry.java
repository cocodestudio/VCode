package com.cocode.vcode.ide.core.console;

import java.util.Objects;

/**
 * Immutable representation of a single console log record emitted by web previews or JavaScript executions.
 */
public final class ConsoleLogEntry {

    public enum Level {
        LOG, INFO, WARN, ERROR
    }

    private final Level level;
    private final String message;
    private final long timestamp;
    private final String source;

    public ConsoleLogEntry(Level level, String message, long timestamp, String source) {
        this.level = level != null ? level : Level.LOG;
        this.message = message != null ? message : "";
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        this.source = source != null ? source : "";
    }

    public Level getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSource() {
        return source;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsoleLogEntry)) return false;
        ConsoleLogEntry that = (ConsoleLogEntry) o;
        return timestamp == that.timestamp &&
                level == that.level &&
                Objects.equals(message, that.message) &&
                Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, message, timestamp, source);
    }

    @Override
    public String toString() {
        return "[" + level + "] " + message + (source.isEmpty() ? "" : " (" + source + ")");
    }
}
