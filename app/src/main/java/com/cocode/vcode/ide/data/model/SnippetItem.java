package com.cocode.vcode.ide.data.model;

import com.cocode.vcode.ide.core.model.FileType;

/**
 * Model representing a user-defined or built-in code snippet.
 */
public class SnippetItem {
    private String id;
    private String title;
    private String content;
    private FileType fileType;

    public SnippetItem(String title, String content, FileType fileType) {
        this.title = title;
        this.content = content;
        this.fileType = fileType;
    }

    public SnippetItem(String id, String title, String content, FileType fileType) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.fileType = fileType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public FileType getFileType() {
        return fileType;
    }

    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }
}