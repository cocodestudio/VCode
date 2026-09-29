package com.cocode.vcode.ide.core.model;

import java.io.File;

/**
 * Model representing a diagnostic problem (error, warning, or info) in a source file.
 */
public class Problem {
    private final File file;
    private final int line;
    private final int column;
    private final int length;
    private final String message;
    private final Severity severity;
    private final String code;
    private android.graphics.Path cachedPath;

    public Problem(File file, int line, int column, int length, String message, Severity severity, String code) {
        this.file = file;
        this.line = line;
        this.column = column;
        this.length = length;
        this.message = message;
        this.severity = severity;
        this.code = code;
    }

    public Problem(File file, int line, int column, int length, String message, Severity severity, com.cocode.vcode.ide.core.diagnostic.ProblemCode problemCode) {
        this(file, line, column, length, message, severity, problemCode != null ? problemCode.name() : null);
    }

    public Problem(File file, int line, int column, int length, String message, Severity severity) {
        this(file, line, column, length, message, severity, (String) null);
    }

    public Problem(File file, int line, int column, String message, Severity severity) {
        this(file, line, column, 1, message, severity, (String) null);
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

    public String getCode() {
        return code;
    }

    public com.cocode.vcode.ide.core.diagnostic.ProblemCode getProblemCode() {
        if (code == null) return null;
        try {
            return com.cocode.vcode.ide.core.diagnostic.ProblemCode.valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public android.graphics.Path getCachedPath() {
        return cachedPath;
    }

    public void setCachedPath(android.graphics.Path cachedPath) {
        this.cachedPath = cachedPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Problem problem = (Problem) o;
        if (line != problem.line) return false;
        if (column != problem.column) return false;
        if (length != problem.length) return false;
        if (severity != problem.severity) return false;
        if (file != null ? !file.equals(problem.file) : problem.file != null) return false;
        if (message != null ? !message.equals(problem.message) : problem.message != null) return false;
        return code != null ? code.equals(problem.code) : problem.code == null;
    }

    @Override
    public int hashCode() {
        int result = file != null ? file.hashCode() : 0;
        result = 31 * result + line;
        result = 31 * result + column;
        result = 31 * result + length;
        result = 31 * result + (message != null ? message.hashCode() : 0);
        result = 31 * result + (severity != null ? severity.hashCode() : 0);
        result = 31 * result + (code != null ? code.hashCode() : 0);
        return result;
    }

    public enum Severity {
        ERROR, WARNING, INFO
    }
}
