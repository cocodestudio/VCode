package com.cocode.vcode.ide.core.model;

import java.io.File;

public class Problem {
    private final File file;
    private final int line;
    private final int column;
    private final int length;
    private final String message;
    private final Severity severity;
    private android.graphics.Path cachedPath;

    public Problem(File file, int line, int column, int length, String message, Severity severity) {
        this.file = file;
        this.line = line;
        this.column = column;
        this.length = length;
        this.message = message;
        this.severity = severity;
    }

    public Problem(File file, int line, int column, String message, Severity severity) {
        this(file, line, column, 1, message, severity);
    }

    public File getFile() {
        return file;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public int getLength() {
        return length;
    }

    public String getMessage() {
        return message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public android.graphics.Path getCachedPath() {
        return cachedPath;
    }

    public void setCachedPath(android.graphics.Path cachedPath) {
        this.cachedPath = cachedPath;
    }

    public enum Severity {
        ERROR, WARNING, INFO
    }
}
