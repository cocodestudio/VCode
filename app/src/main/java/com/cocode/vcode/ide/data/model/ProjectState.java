package com.cocode.vcode.ide.data.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model representing the persisted state of a project session (open files, active tab,
 * cursor and scroll positions, preview states, and virtual file contents).
 */
public class ProjectState {

    private String projectId;
    private List<String> openFilePaths;
    private int activeTabIndex;
    private Map<String, Integer> cursorPositions; // relative path -> cursor offset
    private Map<String, Integer> scrollPositions; // relative path -> scrollY px
    private Map<String, Boolean> previewStates;   // relative path -> preview active
    private final Map<String, String> virtualFiles; // relative path -> content

    public ProjectState() {
        this.openFilePaths = new ArrayList<>();
        this.cursorPositions = new HashMap<>();
        this.scrollPositions = new HashMap<>();
        this.previewStates = new HashMap<>();
        this.virtualFiles = new HashMap<>();
        this.activeTabIndex = 0;
    }

    public ProjectState(String projectId) {
        this();
        this.projectId = projectId;
    }

    public void setCursorFor(String relativePath, int cursor) {
        if (relativePath != null) cursorPositions.put(relativePath, cursor);
    }

    public int getCursorFor(String relativePath) {
        if (relativePath == null) return 0;
        Integer val = cursorPositions.get(relativePath);
        return val != null ? val : 0;
    }

    public void setScrollFor(String relativePath, int scrollY) {
        if (relativePath != null) scrollPositions.put(relativePath, scrollY);
    }

    public int getScrollFor(String relativePath) {
        if (relativePath == null) return 0;
        Integer val = scrollPositions.get(relativePath);
        return val != null ? val : 0;
    }

    public void setPreviewStateFor(String relativePath, boolean isPreview) {
        if (relativePath != null) previewStates.put(relativePath, isPreview);
    }

    public boolean getPreviewStateFor(String relativePath) {
        if (relativePath == null) return false;
        Boolean val = previewStates.get(relativePath);
        return val != null ? val : true;
    }

    public boolean hasExplicitPreviewState(String relativePath) {
        return relativePath != null && previewStates.containsKey(relativePath);
    }

    public Map<String, String> getVirtualFiles() {
        return virtualFiles;
    }

    public void setVirtualFile(String relativePath, String content) {
        if (relativePath != null) virtualFiles.put(relativePath, content);
    }

    public String getVirtualFile(String relativePath) {
        return relativePath != null ? virtualFiles.get(relativePath) : null;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public List<String> getOpenFilePaths() {
        return openFilePaths;
    }

    public void setOpenFilePaths(List<String> paths) {
        this.openFilePaths = paths != null ? paths : new ArrayList<>();
    }

    public int getActiveTabIndex() {
        return activeTabIndex;
    }

    public void setActiveTabIndex(int idx) {
        this.activeTabIndex = Math.max(0, idx);
    }

    public Map<String, Integer> getCursorPositions() {
        return cursorPositions;
    }

    public void setCursorPositions(Map<String, Integer> map) {
        this.cursorPositions = map != null ? map : new HashMap<>();
    }

    public Map<String, Integer> getScrollPositions() {
        return scrollPositions;
    }

    public void setScrollPositions(Map<String, Integer> map) {
        this.scrollPositions = map != null ? map : new HashMap<>();
    }

    public Map<String, Boolean> getPreviewStates() {
        return previewStates;
    }

    public void setPreviewStates(Map<String, Boolean> map) {
        this.previewStates = map != null ? map : new HashMap<>();
    }
}