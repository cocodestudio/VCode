package com.cocode.vcode.ide.core.language.base;

import com.cocode.vcode.ide.core.language.css.CssSyntaxTree;
import com.cocode.vcode.ide.core.language.html.HtmlSyntaxTree;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.language.json.JsonSyntaxTree;
import com.cocode.vcode.ide.core.language.md.MdSyntaxTree;
import com.cocode.vcode.ide.core.model.FileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapter mapping flat syntax trees into {@link DomNode} and {@link DomFile} interfaces.
 */
public class SyntaxTreeDomAdapter implements DomNode {

    public interface FlatTreeAccessor {
        int getNodeCount();
        int getNodeType(int id);
        int getNodeStart(int id);
        int getNodeEnd(int id);
        int getNodeParent(int id);
        int getNodeChild(int id);
        int getNodeSibling(int id);
        String getNodeName(int id);
        int findNodeAt(int offset);
    }

    protected final FlatTreeAccessor accessor;
    protected final int nodeId;

    public SyntaxTreeDomAdapter(FlatTreeAccessor accessor, int nodeId) {
        this.accessor = accessor;
        this.nodeId = nodeId;
    }

    public int getNodeId() {
        return nodeId;
    }

    @Override
    public int getKind() {
        if (nodeId <= 0 || nodeId >= accessor.getNodeCount()) return 0;
        return accessor.getNodeType(nodeId);
    }

    @Override
    public String getName() {
        if (nodeId <= 0 || nodeId >= accessor.getNodeCount()) return "";
        String name = accessor.getNodeName(nodeId);
        return name != null ? name : "";
    }

    @Override
    public int getStartOffset() {
        if (nodeId <= 0 || nodeId >= accessor.getNodeCount()) return 0;
        return accessor.getNodeStart(nodeId);
    }

    @Override
    public int getEndOffset() {
        if (nodeId <= 0 || nodeId >= accessor.getNodeCount()) return 0;
        return accessor.getNodeEnd(nodeId);
    }

    @Override
    public DomNode getParent() {
        if (nodeId <= 0 || nodeId >= accessor.getNodeCount()) return null;
        int parentId = accessor.getNodeParent(nodeId);
        if (parentId <= 0 || parentId == nodeId || parentId >= accessor.getNodeCount()) {
            return null;
        }
        return new SyntaxTreeDomAdapter(accessor, parentId);
    }

    @Override
    public List<DomNode> getChildren() {
        if (nodeId < 0 || nodeId >= accessor.getNodeCount()) return Collections.emptyList();
        int childId = accessor.getNodeChild(nodeId);
        if (childId <= 0 || childId >= accessor.getNodeCount()) return Collections.emptyList();

        List<DomNode> children = new ArrayList<>();
        int curr = childId;
        int count = 0;
        int max = accessor.getNodeCount();
        while (curr > 0 && curr < max && count < max) {
            children.add(new SyntaxTreeDomAdapter(accessor, curr));
            curr = accessor.getNodeSibling(curr);
            count++;
            if (curr == childId) break; // cycle guard
        }
        return children;
    }

    @Override
    public DomNode findDeepestNodeAt(int offset) {
        int id = accessor.findNodeAt(offset);
        if (id <= 0 || id >= accessor.getNodeCount()) return this;
        return new SyntaxTreeDomAdapter(accessor, id);
    }

    // -------------------------------------------------------------------------
    // Factory Adapters for Standard Language Trees
    // -------------------------------------------------------------------------

    public static DomFile fromHtml(HtmlSyntaxTree tree) {
        if (tree == null) return null;
        FlatTreeAccessor accessor = new FlatTreeAccessor() {
            @Override public int getNodeCount() { return tree.nodeCount; }
            @Override public int getNodeType(int id) { return tree.nodeType[id]; }
            @Override public int getNodeStart(int id) { return tree.nodeStart[id]; }
            @Override public int getNodeEnd(int id) { return tree.nodeEnd[id]; }
            @Override public int getNodeParent(int id) { return tree.nodeParent[id]; }
            @Override public int getNodeChild(int id) { return tree.nodeChild[id]; }
            @Override public int getNodeSibling(int id) { return tree.nodeSibling[id]; }
            @Override public String getNodeName(int id) { return tree.nodeName[id]; }
            @Override public int findNodeAt(int offset) { return tree.findNodeAt(offset); }
        };
        return new DomFileImpl(FileType.HTML, accessor, 0);
    }

    public static DomFile fromJs(JsSyntaxTree tree, FileType type) {
        if (tree == null) return null;
        FlatTreeAccessor accessor = new FlatTreeAccessor() {
            @Override public int getNodeCount() { return tree.nodeCount; }
            @Override public int getNodeType(int id) { return tree.nodeType[id]; }
            @Override public int getNodeStart(int id) { return tree.nodeStart[id]; }
            @Override public int getNodeEnd(int id) { return tree.nodeEnd[id]; }
            @Override public int getNodeParent(int id) { return tree.nodeParent[id]; }
            @Override public int getNodeChild(int id) { return tree.nodeChild[id]; }
            @Override public int getNodeSibling(int id) { return tree.nodeSibling[id]; }
            @Override public String getNodeName(int id) { return tree.nodeName[id]; }
            @Override public int findNodeAt(int offset) { return tree.findNodeAt(offset); }
        };
        return new DomFileImpl(type != null ? type : FileType.JAVASCRIPT, accessor, 0);
    }

    public static DomFile fromCss(CssSyntaxTree tree) {
        if (tree == null) return null;
        FlatTreeAccessor accessor = new FlatTreeAccessor() {
            @Override public int getNodeCount() { return tree.nodeCount; }
            @Override public int getNodeType(int id) { return tree.nodeType[id]; }
            @Override public int getNodeStart(int id) { return tree.nodeStart[id]; }
            @Override public int getNodeEnd(int id) { return tree.nodeEnd[id]; }
            @Override public int getNodeParent(int id) { return tree.nodeParent[id]; }
            @Override public int getNodeChild(int id) { return tree.nodeChild[id]; }
            @Override public int getNodeSibling(int id) { return tree.nodeSibling[id]; }
            @Override public String getNodeName(int id) { return tree.nodeName[id]; }
            @Override public int findNodeAt(int offset) { return tree.findNodeAt(offset); }
        };
        return new DomFileImpl(FileType.CSS, accessor, 0);
    }

    public static DomFile fromJson(JsonSyntaxTree tree) {
        if (tree == null) return null;
        FlatTreeAccessor accessor = new FlatTreeAccessor() {
            @Override public int getNodeCount() { return tree.nodeCount; }
            @Override public int getNodeType(int id) { return tree.nodeType[id]; }
            @Override public int getNodeStart(int id) { return tree.nodeStart[id]; }
            @Override public int getNodeEnd(int id) { return tree.nodeEnd[id]; }
            @Override public int getNodeParent(int id) { return tree.nodeParent[id]; }
            @Override public int getNodeChild(int id) { return tree.nodeChild[id]; }
            @Override public int getNodeSibling(int id) { return tree.nodeSibling[id]; }
            @Override public String getNodeName(int id) { return tree.nodeName[id]; }
            @Override public int findNodeAt(int offset) { return tree.getNodeAtOffset(offset); }
        };
        return new DomFileImpl(FileType.JSON, accessor, 0);
    }

    public static DomFile fromMd(MdSyntaxTree tree) {
        if (tree == null) return null;
        FlatTreeAccessor accessor = new FlatTreeAccessor() {
            @Override public int getNodeCount() { return tree.nodeCount; }
            @Override public int getNodeType(int id) { return tree.nodeType[id]; }
            @Override public int getNodeStart(int id) { return tree.nodeStart[id]; }
            @Override public int getNodeEnd(int id) { return tree.nodeEnd[id]; }
            @Override public int getNodeParent(int id) { return tree.nodeParent[id]; }
            @Override public int getNodeChild(int id) { return tree.nodeChild[id]; }
            @Override public int getNodeSibling(int id) { return tree.nodeSibling[id]; }
            @Override public String getNodeName(int id) { return tree.nodeName[id]; }
            @Override public int findNodeAt(int offset) { return tree.findNodeAt(offset); }
        };
        return new DomFileImpl(FileType.MARKDOWN, accessor, 0);
    }

    private static class DomFileImpl extends SyntaxTreeDomAdapter implements DomFile {
        private final FileType fileType;

        DomFileImpl(FileType fileType, FlatTreeAccessor accessor, int rootNodeId) {
            super(accessor, rootNodeId);
            this.fileType = fileType;
        }

        @Override
        public FileType getFileType() {
            return fileType;
        }

        @Override
        public int getNodeCount() {
            return accessor.getNodeCount();
        }
    }
}
