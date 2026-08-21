package com.cocode.vcode.ide.git.model;

/**
 * Model tracking the Git status of an individual file (staged, unstaged, untracked, conflicted, ignored).
 */
public class FileStatus {

    private String relativePath;
    private Type type;

    public FileStatus() {
    }

    public FileStatus(String relativePath, Type type) {
        this.relativePath = relativePath;
        this.type = type;
    }

    public boolean isStaged() {
        return type == Type.STAGED_ADDED
                || type == Type.STAGED_MODIFIED
                || type == Type.STAGED_DELETED;
    }

    public boolean isUnstaged() {
        return type == Type.UNSTAGED_MODIFIED
                || type == Type.UNSTAGED_DELETED
                || type == Type.UNTRACKED;
    }

    /**
     * Returns a short 1-letter status code (A, M, D, !) for display badges.
     */
    public String getStatusLabel() {
        if (type == null) return "?";
        switch (type) {
            case STAGED_ADDED:
                return "A";
            case STAGED_MODIFIED:
            case UNSTAGED_MODIFIED:
                return "M";
            case STAGED_DELETED:
            case UNSTAGED_DELETED:
                return "D";
            case CONFLICTED:
                return "!";
            default:
                return "?";
        }
    }

    /**
     * Extracts the simple file name from the relative path.
     */
    public String getFileName() {
        if (relativePath == null) return "";
        int sep = relativePath.lastIndexOf('/');
        if (sep < 0)
            sep = relativePath.lastIndexOf('\\');
        return sep >= 0 ? relativePath.substring(sep + 1) : relativePath;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String path) {
        this.relativePath = path;
    }

    public String getPath() {
        return relativePath;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    /**
     * Types of file status in the repository.
     */
    public enum Type {
        STAGED_ADDED,
        STAGED_MODIFIED,
        STAGED_DELETED,
        UNSTAGED_MODIFIED,
        UNSTAGED_DELETED,
        UNTRACKED,
        CONFLICTED,
        IGNORED
    }
}