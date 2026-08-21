package com.cocode.vcode.ide.git.model;

/**
 * Model representing a local or remote Git branch.
 */
public class BranchItem {
    private final String name;
    private final boolean isActive;
    private final boolean isRemote;
    private final String lastCommit;

    public BranchItem(String name, boolean isActive, boolean isRemote, String lastCommit) {
        this.name = name;
        this.isActive = isActive;
        this.isRemote = isRemote;
        this.lastCommit = lastCommit;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean isRemote() {
        return isRemote;
    }

    public String getLastCommit() {
        return lastCommit;
    }
}