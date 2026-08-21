package com.cocode.vcode.ide.git.model;

/**
 * Model representing a modified file in the Git staging/unstaged file list.
 */
public class GitFileItem {
    private final String path;
    private final String fileName;
    private final String status;
    private final boolean staged;

    public GitFileItem(String path, String fileName, String status, boolean staged) {
        this.path = path;
        this.fileName = fileName;
        this.status = status;
        this.staged = staged;
    }

    public String getPath() {
        return path;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStatus() {
        return status;
    }

    public boolean isStaged() {
        return staged;
    }
}