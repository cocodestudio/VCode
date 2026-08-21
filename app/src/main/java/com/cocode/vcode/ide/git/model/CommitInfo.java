package com.cocode.vcode.ide.git.model;

import java.util.Date;
import java.util.List;

/**
 * Detailed metadata model for a Git commit, including hashes, message, author, date, parents, tags, and changed files.
 */
public class CommitInfo {

    private String fullHash;
    private String shortHash;
    private String fullMessage;
    private String shortMessage;
    private String authorName;
    private String authorEmail;
    private Date date;
    private String[] parentHashes;
    private List<FileStatus> changedFiles;
    private List<String> tags;
    private List<String> branchNames;

    public CommitInfo() {
    }

    public CommitInfo(String fullHash, String shortHash, String fullMessage,
                      String shortMessage, String authorName, String authorEmail,
                      Date date, String[] parentHashes) {
        this.fullHash = fullHash;
        this.shortHash = shortHash;
        this.fullMessage = fullMessage;
        this.shortMessage = shortMessage;
        this.authorName = authorName;
        this.authorEmail = authorEmail;
        this.date = date;
        this.parentHashes = parentHashes;
    }

    public boolean isMergeCommit() {
        return parentHashes != null && parentHashes.length > 1;
    }

    /**
     * Generates 2-letter uppercase initials from the author name for avatar badges.
     */
    public String getInitials() {
        if (authorName == null || authorName.isEmpty()) return "?";
        String[] parts = authorName.trim().split("\\s+");

        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }

        return (String.valueOf(parts[0].charAt(0))
                + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    public String getFullHash() {
        return fullHash;
    }

    public void setFullHash(String fullHash) {
        this.fullHash = fullHash;
    }

    public String getShortHash() {
        return shortHash;
    }

    public void setShortHash(String shortHash) {
        this.shortHash = shortHash;
    }

    public String getFullMessage() {
        return fullMessage;
    }

    public void setFullMessage(String fullMessage) {
        this.fullMessage = fullMessage;
    }

    public String getShortMessage() {
        return shortMessage;
    }

    public void setShortMessage(String shortMessage) {
        this.shortMessage = shortMessage;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public void setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String[] getParentHashes() {
        return parentHashes;
    }

    public void setParentHashes(String[] parentHashes) {
        this.parentHashes = parentHashes;
    }

    public List<FileStatus> getChangedFiles() {
        return changedFiles;
    }

    public void setChangedFiles(List<FileStatus> files) {
        this.changedFiles = files;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getBranchNames() {
        return branchNames;
    }

    public void setBranchNames(List<String> branches) {
        this.branchNames = branches;
    }

    public String getMessage() {
        return shortMessage != null ? shortMessage : fullMessage;
    }

    public long getTimestamp() {
        return date != null ? date.getTime() / 1000L : 0;
    }
}