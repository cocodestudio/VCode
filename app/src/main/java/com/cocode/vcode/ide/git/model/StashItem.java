package com.cocode.vcode.ide.git.model;

/**
 * Model representing an entry in the Git stash list.
 */
public class StashItem {
    private final int id;
    private final String name;
    private final String message;
    private final String timestamp;

    public StashItem(int id, String name, String message, String timestamp) {
        this.id = id;
        this.name = name;
        this.message = message;
        this.timestamp = timestamp;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getMessage() {
        return message;
    }

    public String getTimestamp() {
        return timestamp;
    }
}
