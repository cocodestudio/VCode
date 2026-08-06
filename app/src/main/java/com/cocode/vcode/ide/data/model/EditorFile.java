package com.cocode.vcode.ide.data.model;

import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.utils.FileUtils;

import java.io.File;

/**
 * Workspace runtime object representing an active file session inside the editor stage.
 * Tracks character mutations, text dirty states, target languages, scrolling viewport
 * coordinates, and caret cursor locations for state persistence across tabs.
 */
public class EditorFile {

    private String id;
    private File file;
    private String content;
    private String savedContent = "";
    private FileType fileType;
    private int cursorPosition;
    private int scrollY;
    private boolean isContentLoaded = false;
    private boolean readOnly = false;
    private boolean isVirtual = false;
    private boolean manuallyDirty = false;

    /**
     * For files opened via a content:// URI: the original source URI string.
     * When set, saves are written back to this URI in addition to the local cache file.
     * Stored as a String (not Uri) to avoid Android-specific types in the data model.
     */
    private String sourceUriString;

    public EditorFile() {
    }

    /**
     * Initializes an active workspace tracking session for a local target file.
     */
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

        if (this.fileType == FileType.API_TESTER) {
            this.isVirtual = true;
        }
    }

    /**
     * Helper to check if the UI needs to show an Image/Font viewer instead of the text editor.
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
     * Compares active working text lines against disk persistence states to look for unsaved edits.
     * Prevents tracking mutations on external asset models.
     *
     * @return True if there are uncommitted buffer changes waiting for a disk write sequence.
     */
    public boolean isDirty() {
        if (isBinaryAsset())
            return false; // Binary assets edited externally cannot be "dirty" in our text editor
        if (manuallyDirty) return true;
        if (content == null && savedContent == null) return false;
        if (content == null || savedContent == null) return true;
        return !content.equals(savedContent);
    }

    public void setDirty(boolean dirty) {
        this.manuallyDirty = dirty;
    }

    /**
     * Synchronizes storage bookmarks after writing data to disk, clearing dirty markers.
     */
    public void markSaved() {
        this.savedContent = this.content;
        this.manuallyDirty = false;
    }

    public String getFileName() {
        if (fileType == FileType.API_TESTER) {
            return "API Tester";
        }
        return file != null ? file.getName() : "Untitled";
    }

    /**
     * Computes localized file tree routing rules relative to the active root project path.
     * Clean up long file layouts so tab headers display clean paths.
     */
    public String getRelativePath(File projectRoot) {
        if (file == null || projectRoot == null) return getFileName();
        try {
            String root = projectRoot.getCanonicalPath();
            String path = file.getCanonicalPath();
            if (path.startsWith(root)) {
                String relative = path.substring(root.length());
                if (relative.startsWith(File.separator)) {
                    relative = relative.substring(File.separator.length());
                }
                return relative;
            }
        } catch (Exception e) {
            // Fall through to name only if file validation checks encounter an error
        }
        return getFileName();
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
}