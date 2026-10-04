package com.cocode.vcode.ide.data.model;

import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.utils.FileUtils;

import java.io.File;

/**
 * Model representing an open file session in the editor.
 * Tracks file path, text content, dirty status, cursor position, scroll position, and virtual file state.
 */
public class EditorFile {

    private String id;
    private File file;
    private volatile String content;
    private String savedContent = "";
    private FileType fileType;
    private int cursorPosition;
    private int scrollY;
    private boolean isContentLoaded = false;
    private boolean readOnly = false;
    private boolean isVirtual = false;
    private boolean manuallyDirty = false;
    private long lastDiskModified = -1;
    private long lastDiskSize = -1;
    private String displayName;
    private String virtualUri;

    /**
     * For files opened via a content:// URI: the original source URI string.
     * When set, saves are written back to this URI in addition to the local cache file.
     * Stored as a String (not Uri) to avoid Android-specific types in the data model.
     */
    private String sourceUriString;

    public EditorFile() {
    }

    public EditorFile(String id, File file, String content, FileType fileType) {
        this.id = id;
        this.file = file;
        this.content = content != null ? content : "";
        this.savedContent = content != null ? content : "";

        if (fileType != null) {
            this.fileType = fileType;
        } else {
            this.fileType = FileType.fromExtension(FileUtils.getExtension(file.getName()));
        }

        if (this.fileType == FileType.API_TESTER || this.fileType == FileType.TERMINAL) {
            this.isVirtual = true;
        }

        if (file != null && file.exists()) {
            this.lastDiskModified = file.lastModified();
            this.lastDiskSize = file.length();
        }
    }

    /**
     * Returns true if this file is a non-text binary asset (e.g. image, font).
     */
    public boolean isBinaryAsset() {
        return fileType != null && !fileType.isTextBased();
    }

    public FileType getFileType() {
        return fileType;
    }

    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    /**
     * Returns true if there are unsaved in-memory edits.
     */
    public boolean isDirty() {
        if (isBinaryAsset())
            return false;
        if (manuallyDirty) return true;
        if (content == null && savedContent == null) return false;
        if (content == null || savedContent == null) return true;
        return !content.equals(savedContent);
    }

    public void setDirty(boolean dirty) {
        this.manuallyDirty = dirty;
    }

    /**
     * Marks the current content as saved, clearing the dirty flag.
     */
    public void markSaved() {
        this.savedContent = this.content;
        this.manuallyDirty = false;
        if (file != null && file.exists()) {
            this.lastDiskModified = file.lastModified();
            this.lastDiskSize = file.length();
        }
    }

    public String getFileName() {
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }
        if (fileType == FileType.API_TESTER) {
            return "API Tester";
        }
        if (fileType == FileType.TERMINAL) {
            if (file != null) {
                String name = file.getName();
                if (name.startsWith("vcode_terminal_")) {
                    String numPart = name.substring("vcode_terminal_".length());
                    int dot = numPart.indexOf('.');
                    if (dot > 0) numPart = numPart.substring(0, dot);
                    return "Terminal " + numPart;
                }
            }
            return "Terminal";
        }
        return file != null ? file.getName() : "Untitled";
    }

    /**
     * Returns the path relative to the given project root, or the file name if outside the root.
     */
    public String getRelativePath(File projectRoot) {
        if (file == null || projectRoot == null) return getFileName();
        return FileUtils.getRelativePath(projectRoot, file);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
        if (file != null && file.exists()) {
            this.lastDiskModified = file.lastModified();
            this.lastDiskSize = file.length();
        } else {
            this.lastDiskModified = -1;
            this.lastDiskSize = -1;
        }
    }

    public String getContent() {
        return content != null ? content : "";
    }

    public void setContent(String content) {
        this.content = content != null ? content : "";
    }

    public int getCursorPosition() {
        return cursorPosition;
    }

    public void setCursorPosition(int cursorPosition) {
        this.cursorPosition = Math.max(0, cursorPosition);
    }

    public int getScrollY() {
        return scrollY;
    }

    public void setScrollY(int scrollY) {
        this.scrollY = Math.max(0, scrollY);
    }

    public boolean isContentLoaded() {
        return isContentLoaded;
    }

    public void setContentLoaded(boolean contentLoaded) {
        isContentLoaded = contentLoaded;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean isVirtual() {
        return isVirtual;
    }

    public void setVirtual(boolean virtual) {
        isVirtual = virtual;
    }

    public String getSourceUriString() {
        return sourceUriString;
    }

    public void setSourceUriString(String sourceUriString) {
        this.sourceUriString = sourceUriString;
    }

    public long getLastDiskModified() {
        return lastDiskModified;
    }

    public void setLastDiskModified(long lastDiskModified) {
        this.lastDiskModified = lastDiskModified;
    }

    public long getLastDiskSize() {
        return lastDiskSize;
    }

    public void setLastDiskSize(long lastDiskSize) {
        this.lastDiskSize = lastDiskSize;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getVirtualUri() {
        return virtualUri;
    }

    public void setVirtualUri(String virtualUri) {
        this.virtualUri = virtualUri;
    }

    /**
     * Creates a virtual document tab without requiring a physical backing file on disk.
     */
    public static EditorFile createVirtual(String id, String displayName, String virtualUri, String content, FileType fileType) {
        EditorFile ef = new EditorFile();
        ef.id = id != null ? id : (virtualUri != null ? virtualUri : java.util.UUID.randomUUID().toString());
        ef.displayName = displayName;
        ef.virtualUri = virtualUri;
        ef.content = content != null ? content : "";
        ef.savedContent = ef.content;
        ef.fileType = fileType != null ? fileType : FileType.TEXT;
        ef.isVirtual = true;
        ef.isContentLoaded = true;
        return ef;
    }
}