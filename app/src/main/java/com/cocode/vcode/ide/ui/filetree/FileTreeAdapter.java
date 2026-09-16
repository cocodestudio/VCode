package com.cocode.vcode.ide.ui.filetree;

import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.FileNode;
import com.cocode.vcode.ide.databinding.ItemFileTreeNodeBinding;
import com.cocode.vcode.ide.git.model.FileStatus;
import com.cocode.vcode.ide.utils.FileIconHelper;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.utils.FontManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FileTreeAdapter manages the visual representation of the project's file hierarchy.
 * It transforms a nested {@link FileNode} structure into a flattened list for the RecyclerView,
 * supporting folder expansion/collapse, specialized file icons, and Git status indicators.
 */
public class FileTreeAdapter extends RecyclerView.Adapter<FileTreeAdapter.FileViewHolder> {

    public static final float CUT_OPACITY = 0.5f;
    public static final float NORMAL_OPACITY = 1.0f;
    public static final Object PAYLOAD_CLIPBOARD = new Object();

    private final List<FileNode> flatNodes = new ArrayList<>();
    private final FileTreeListener listener;
    private final int indentWidthPx;
    private Map<String, FileStatus.Type> gitStatusMap;
    private String rootPath;
    private String projectName;

    private File clipboardFile = null;
    private boolean isCutAction = false;

    public FileTreeAdapter(FileTreeListener listener, int indentDp, float screenDensity) {
        this.listener = listener;
        this.indentWidthPx = (int) (indentDp * screenDensity);
    }

    private static String normalizePath(String path) {
        if (path == null) return null;
        String normalized = path.replace('\\', '/');
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    /**
     * Updates the file tree data. Preserves the expansion state of folders across updates.
     *
     * @param rootNodes The new set of root-level nodes.
     */
    public void setTree(List<FileNode> rootNodes) {
        // Capture currently expanded paths before clearing the list
        Set<String> expandedPaths = new HashSet<>();
        for (FileNode node : flatNodes) {
            if (node.isDirectory() && node.isExpanded()) {
                expandedPaths.add(node.getFile().getAbsolutePath());
            }
        }

        // Apply captured state to the new node tree
        restoreExpandedState(rootNodes, expandedPaths);

        List<FileNode> newFlatNodes = new ArrayList<>();
        // Transform the nested tree into a flat list for the adapter
        flatten(rootNodes, newFlatNodes);

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return flatNodes.size();
            }

            @Override
            public int getNewListSize() {
                return newFlatNodes.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                FileNode oldNode = flatNodes.get(oldItemPosition);
                FileNode newNode = newFlatNodes.get(newItemPosition);
                return oldNode.getFile().equals(newNode.getFile());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                FileNode oldNode = flatNodes.get(oldItemPosition);
                FileNode newNode = newFlatNodes.get(newItemPosition);
                return oldNode.isExpanded() == newNode.isExpanded()
                        && oldNode.getDepth() == newNode.getDepth()
                        && oldNode.isDirectory() == newNode.isDirectory()
                        && oldNode.getName().equals(newNode.getName());
            }
        });

        flatNodes.clear();
        flatNodes.addAll(newFlatNodes);
        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * Updates the clipboard state to visualize cut/copy operations.
     */
    public void setClipboardState(File file, boolean isCut) {
        File oldFile = this.clipboardFile;
        this.clipboardFile = file;
        this.isCutAction = isCut;

        String oldPath = normalizePath(oldFile != null ? oldFile.getAbsolutePath() : null);
        String newPath = normalizePath(file != null ? file.getAbsolutePath() : null);

        try {
            if (hasObservers()) {
                // Targeted notify with PAYLOAD_CLIPBOARD so ViewHolder updates in-place without DefaultItemAnimator resetting alpha
                for (int i = 0; i < flatNodes.size(); i++) {
                    File f = flatNodes.get(i).getFile();
                    if (f != null) {
                        String nodePath = normalizePath(f.getAbsolutePath());
                        boolean affectsOld = oldPath != null && (nodePath.equals(oldPath) || nodePath.startsWith(oldPath + "/"));
                        boolean affectsNew = newPath != null && (nodePath.equals(newPath) || nodePath.startsWith(newPath + "/"));
                        if (affectsOld || affectsNew) {
                            notifyItemChanged(i, PAYLOAD_CLIPBOARD);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Resets the clipboard state and restores normal node opacity.
     */
    public void clearClipboardState() {
        if (clipboardFile != null || isCutAction) {
            setClipboardState(null, false);
            try {
                if (hasObservers()) {
                    notifyDataSetChanged();
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Returns true if the given node is currently marked as cut (including descendants of cut folders).
     */
    public boolean isNodeCut(FileNode node) {
        if (!isCutAction || clipboardFile == null || node == null || node.getFile() == null) {
            return false;
        }
        if (node.getFile().equals(clipboardFile)) {
            return true;
        }
        String nodePath = normalizePath(node.getFile().getAbsolutePath());
        String cutPath = normalizePath(clipboardFile.getAbsolutePath());
        if (nodePath == null || cutPath == null) {
            return false;
        }
        return nodePath.equals(cutPath) || nodePath.startsWith(cutPath + "/");
    }

    public File getClipboardFile() {
        return clipboardFile;
    }

    public boolean isCutAction() {
        return isCutAction;
    }

    /**
     * Recursively reapplies the "expanded" flag to folders based on their absolute paths.
     */
    private void restoreExpandedState(List<FileNode> nodes, Set<String> expandedPaths) {
        if (nodes == null) return;
        for (FileNode node : nodes) {
            if (node.isDirectory()) {
                if (expandedPaths.contains(node.getFile().getAbsolutePath())) {
                    node.setExpanded(true);
                }
                restoreExpandedState(node.getChildren(), expandedPaths);
            }
        }
    }

    /**
     * Updates the Git status mapping and refreshes the UI badges.
     */
    public void setGitStatuses(Map<String, FileStatus.Type> statuses) {
        this.gitStatusMap = statuses;
        notifyDataSetChanged();
    }

    /**
     * Converts a nested tree structure into a flat list, including only children
     * of folders that are currently expanded.
     */
    private void flatten(List<FileNode> nodes, List<FileNode> out) {
        if (nodes == null) return;
        for (FileNode node : nodes) {
            out.add(node);
            if (node.isDirectory() && node.isExpanded()) {
                flatten(node.getChildren(), out);
            }
        }
    }

    /**
     * Determines if a file is supported by the internal code editor or asset viewers.
     */
    private boolean isSupportedByEditor(File file) {
        String ext = FileUtils.getExtension(file.getName()).toLowerCase();
        if (ext.isEmpty()) return true; // Generic text files without extensions are allowed

        // Check for supported media and font types
        FileType fileType = FileType.fromExtension(ext);
        if (fileType == FileType.IMAGE || fileType == FileType.GIF ||
                fileType == FileType.ICO || fileType == FileType.BMP ||
                fileType == FileType.FONT) {
            return true;
        }

        // Support all generic text and code files dynamically.
        // If an extension isn't explicitly classified as a restricted binary asset (like Audio/Video/PDF),
        // we safely assume it's a readable text format and allow the editor to parse it.
        return fileType == null || fileType.isTextBased();
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFileTreeNodeBinding binding = ItemFileTreeNodeBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new FileViewHolder(binding, this);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        holder.bind(flatNodes.get(position));
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_CLIPBOARD)) {
            holder.updateCutOpacity(flatNodes.get(position));
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override
    public int getItemCount() {
        return flatNodes.size();
    }

    /**
     * Listener interface for file tree interaction events.
     */
    public interface FileTreeListener {
        void onFileClick(File file);

        void onAddFileClick(File parentDir);

        void onAddFolderClick(File parentDir);

        void onNodeLongClick(View anchor, FileNode node);
    }

    /**
     * ViewHolder for a single node in the file tree.
     * Manages click, long-press, and expansion animations.
     */
    public static class FileViewHolder extends RecyclerView.ViewHolder {

        private final ItemFileTreeNodeBinding binding;
        private final FileTreeAdapter adapter;

        public FileViewHolder(@NonNull ItemFileTreeNodeBinding binding, FileTreeAdapter adapter) {
            super(binding.getRoot());
            this.binding = binding;
            this.adapter = adapter;
            setupClickListeners();
        }

        private void setupClickListeners() {
            // "New File" action button
            binding.btnAddFile.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && adapter.listener != null) {
                    FileNode node = adapter.flatNodes.get(pos);
                    if (node.isDirectory()) {
                        adapter.listener.onAddFileClick(node.getFile());
                    }
                }
            });

            // "New Folder" action button
            binding.btnAddFolder.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && adapter.listener != null) {
                    FileNode node = adapter.flatNodes.get(pos);
                    if (node.isDirectory()) {
                        adapter.listener.onAddFolderClick(node.getFile());
                    }
                }
            });

            // Standard click to open file or toggle folder
            binding.getRoot().setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                FileNode node = adapter.flatNodes.get(pos);

                if (node.isDirectory()) {
                    handleDirectoryClick(node, pos);
                } else {
                    handleFileClick(node.getFile());
                }
            });

            // Long click to reveal rename, delete, copy, cut, paste actions
            binding.getRoot().setOnLongClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && adapter.listener != null) {
                    FileNode node = adapter.flatNodes.get(pos);
                    adapter.listener.onNodeLongClick(binding.getRoot(), node);
                    return true;
                }
                return false;
            });
        }

        /**
         * Expands or collapses a directory and updates the flat list dynamically.
         */
        private void handleDirectoryClick(FileNode node, int pos) {
            node.setExpanded(!node.isExpanded());
            if (node.isExpanded()) {
                // Flatten child nodes and insert them below the parent
                List<FileNode> toAdd = new ArrayList<>();
                adapter.flatten(node.getChildren(), toAdd);
                adapter.flatNodes.addAll(pos + 1, toAdd);
                adapter.notifyItemRangeInserted(pos + 1, toAdd.size());
            } else {
                // Remove all visible descendants of the collapsed folder
                int removeCount = 0;
                int nextPos = pos + 1;
                while (nextPos < adapter.flatNodes.size() && adapter.flatNodes.get(nextPos).getDepth() > node.getDepth()) {
                    removeCount++;
                    nextPos++;
                }
                if (removeCount > 0) {
                    adapter.flatNodes.subList(pos + 1, pos + 1 + removeCount).clear();
                    adapter.notifyItemRangeRemoved(pos + 1, removeCount);
                }
            }
            adapter.notifyItemChanged(pos); // Update chevron and icon
        }

        /**
         * Handles file selection; opens internal editor or delegates to system.
         */
        private void handleFileClick(File clickedFile) {
            if (adapter.isSupportedByEditor(clickedFile)) {
                if (adapter.listener != null) adapter.listener.onFileClick(clickedFile);
            } else {
                // Hand over unsupported formats (e.g., PDF) to external system apps
                FileUtils.openFileExternally(binding.getRoot().getContext(), clickedFile);
            }
        }

        public void updateCutOpacity(FileNode node) {
            float alpha = adapter.isNodeCut(node) ? CUT_OPACITY : NORMAL_OPACITY;
            binding.getRoot().setAlpha(alpha);
        }

        /**
         * Binds the file node data to the view components.
         */
        public void bind(FileNode node) {
            // Apply indentation based on tree depth
            ViewGroup.LayoutParams params = binding.indentSpacer.getLayoutParams();
            params.width = node.getDepth() * adapter.indentWidthPx;
            binding.indentSpacer.setLayoutParams(params);

            // Special case for root node naming
            if (node.getFile().getAbsolutePath().equals(adapter.rootPath) && adapter.projectName != null) {
                binding.tvName.setText(adapter.projectName);
            } else {
                binding.tvName.setText(node.getName());
            }

            binding.ivIcon.setImageTintList(null);

            // Apply opacity if node is cut
            updateCutOpacity(node);

            if (node.isDirectory()) {
                bindDirectory(node);
            } else {
                bindFile(node);
            }
        }

        /**
         * Specialized binding for directory nodes.
         */
        private void bindDirectory(FileNode node) {
            binding.ivChevron.setVisibility(View.VISIBLE);
            binding.ivChevron.setRotation(node.isExpanded() ? 90f : 0f);

            binding.ivIcon.setVisibility(View.VISIBLE);
            binding.ivIcon.setImageResource(node.isExpanded() ? R.drawable.ic_folder_open : R.drawable.ic_folder);

            int folderColor = ContextCompat.getColor(itemView.getContext(), R.color.vcode_accent_primary);
            binding.ivIcon.setColorFilter(folderColor, PorterDuff.Mode.SRC_IN);

            binding.tvLangBadge.setVisibility(View.GONE);
            binding.gitStatusBadge.setVisibility(View.GONE);
            binding.tvName.setTypeface(FontManager.getInstance().getUiSemiBold(itemView.getContext()));

            binding.btnAddFile.setVisibility(View.VISIBLE);
            binding.btnAddFile.setImageResource(R.drawable.ic_file_plus);
            binding.btnAddFile.clearColorFilter();
            binding.btnAddFile.setAlpha(1f);
            binding.btnAddFile.setTranslationX(0f);

            binding.btnAddFolder.setVisibility(View.VISIBLE);
            binding.btnAddFolder.setImageResource(R.drawable.ic_folder_plus);
            binding.btnAddFolder.clearColorFilter();
            binding.btnAddFolder.setAlpha(1f);
            binding.btnAddFolder.setTranslationX(0f);
        }

        /**
         * Specialized binding for file nodes.
         */
        private void bindFile(FileNode node) {
            binding.ivChevron.setVisibility(View.INVISIBLE);
            binding.tvLangBadge.setVisibility(View.GONE);
            binding.ivIcon.setVisibility(View.VISIBLE);
            binding.tvName.setTypeface(FontManager.getInstance().getUiMedium(itemView.getContext()));

            binding.btnAddFile.setVisibility(View.GONE);
            binding.btnAddFolder.setVisibility(View.GONE);

            FileIconHelper.setFileIconAndColor(binding.ivIcon, node.getFile().getName());

            updateGitStatus(node);
        }

        /**
         * Resolves the Git status for the node and displays a colored status dot if applicable.
         */
        private void updateGitStatus(FileNode node) {
            if (adapter.gitStatusMap != null && adapter.rootPath != null) {
                String relativePath = getRelativePath(node.getFile().getAbsolutePath());
                FileStatus.Type status = adapter.gitStatusMap.get(relativePath);
                if (status != null) {
                    binding.gitStatusBadge.setVisibility(View.VISIBLE);
                    binding.gitStatusBadge.setStatus(status);
                } else {
                    binding.gitStatusBadge.setVisibility(View.GONE);
                }
            } else {
                binding.gitStatusBadge.setVisibility(View.GONE);
            }
        }

        /**
         * Converts an absolute path to a project-relative path with normalized separators.
         */
        private String getRelativePath(String absPath) {
            if (absPath.startsWith(adapter.rootPath)) {
                String rel = absPath.substring(adapter.rootPath.length());
                if (rel.startsWith(File.separator)) {
                    rel = rel.substring(1);
                }
                return rel.replace('\\', '/'); // Standardize for Git and internal maps
            }
            return absPath;
        }
    }
}