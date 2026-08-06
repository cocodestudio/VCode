package com.cocode.vcode.ide.data.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent cache state descriptor capturing a developer's UI layout structure.
 * Records opened documents, highlighted tabs, scroll coordinates, and cursor vectors
 * so the workspace resumes exactly where it was left upon reload.
 */
public class ProjectState {

    private String projectId;
    private List<String> openFilePaths;
    private int activeTabIndex;
    private Map<String, Integer> cursorPositions; // Maps file paths to their last caret offset index
    private Map<String, Integer> scrollPositions; // Maps file paths to their vertical scroll viewport pixel positions
    private Map<String, Boolean> previewStates; // Maps file paths to their toggle preview state (true = preview, false = text)
    private final Map<String, String> virtualFiles; // Maps virtual file paths to their persisted content

    /**
     * Initializes blank tracking parameters for view tracking containers.
     */
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

    /**
     * Records caret placement vectors linked against a relative file path location.
     */
    public void setCursorFor(String relativePath, int cursor) {
        if (relativePath != null) cursorPositions.put(relativePath, cursor);
    }

    /**
     * Resolves the historical caret placement baseline index linked against a relative path target.
     */
    public int getCursorFor(String relativePath) {
        if (relativePath == null) return 0;
        Integer val = cursorPositions.get(relativePath);
        return val != null ? val : 0;
    }

    /**
     * Records the active vertical viewport location offset linked against a relative file path.
     */
    public void setScrollFor(String relativePath, int scrollY) {
        if (relativePath != null) scrollPositions.put(relativePath, scrollY);
    }

    /**
     * Resolves the historical scroll point line pixel height linked against a relative target file.
     */
    public int getScrollFor(String relativePath) {
        if (relativePath == null) return 0;
        Integer val = scrollPositions.get(relativePath);
        return val != null ? val : 0;
    }

    /**
     * Records the toggle preview state linked against a relative file path.
     */
    public void setPreviewStateFor(String relativePath, boolean isPreview) {
        if (relativePath != null) previewStates.put(relativePath, isPreview);
    }

    /**
     * Resolves the toggle preview state linked against a relative target file.
     * Defaults to true so previewable files open in preview mode initially.
     */
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