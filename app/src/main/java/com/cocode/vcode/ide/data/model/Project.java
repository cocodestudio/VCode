package com.cocode.vcode.ide.data.model;

/**
 * Data model representing a project in the workspace, including metadata like name, creation date,
 * last modification timestamp, main entry file, and file count.
 */
public class Project {

    private String id;
    private String name;
    private long createdAt;
    private long lastModifiedAt;
    private String mainFile; // Path to project entry point (e.g. index.html)
    private int fileCount;

    public Project() {
    }

    public Project(String id, String name, long createdAt, long lastModifiedAt,
                   String mainFile, int fileCount) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.lastModifiedAt = lastModifiedAt;
        this.mainFile = mainFile;
        this.fileCount = fileCount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastModifiedAt() {
        return lastModifiedAt;
    }

    public void setLastModifiedAt(long lastModifiedAt) {
        this.lastModifiedAt = lastModifiedAt;
    }

    public String getMainFile() {
        return mainFile;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }
}