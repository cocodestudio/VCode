package com.cocode.vcode.ide.data.model;

import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a node in the project file tree, holding a {@link File} reference,
 * its depth in the hierarchy, expanded state, and child nodes.
 */
public class FileNode {

    private final int depth;
    private File file;
    private List<FileNode> children;
    private boolean isExpanded;

    public FileNode(File file, int depth) {
        this.file = file;
        this.depth = depth;
        this.isExpanded = false;
        if (file != null && file.isDirectory()) {
            this.children = new ArrayList<>();
        } else {
            this.children = null;
        }
    }

    public boolean isDirectory() {
        return file != null && file.isDirectory();
    }

    public String getName() {
        return file != null ? file.getName() : "";
    }

    public FileType getFileType() {
        if (isDirectory()) return FileType.TEXT;
        return FileType.fromExtension(FileUtils.getExtension(getName()));
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public List<FileNode> getChildren() {
        return children;
    }

    public void setChildren(List<FileNode> c) {
        this.children = c;
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public void setExpanded(boolean expanded) {
        this.isExpanded = expanded;
    }

    public int getDepth() {
        return depth;
    }
}