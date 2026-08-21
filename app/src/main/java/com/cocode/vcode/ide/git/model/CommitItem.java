package com.cocode.vcode.ide.git.model;

/**
 * Lightweight commit model for RecyclerView adapters in commit history lists.
 */
public class CommitItem {
    private final String sha;
    private final String shortSha;
    private final String message;
    private final String author;
    private final String timestamp;

    public CommitItem(String sha, String shortSha, String message, String author, String timestamp) {
        this.sha = sha;
        this.shortSha = shortSha;
        this.message = message;
        this.author = author;
        this.timestamp = timestamp;
    }

    public String getSha() {
        return sha;
    }

    public String getShortSha() {
        return shortSha;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthor() {
        return author;
    }

    public String getTimestamp() {
        return timestamp;
    }
}