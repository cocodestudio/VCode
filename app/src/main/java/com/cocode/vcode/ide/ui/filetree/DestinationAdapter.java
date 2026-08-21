package com.cocode.vcode.ide.ui.filetree;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.data.model.FileNode;
import com.cocode.vcode.ide.databinding.ItemDestinationFolderBinding;
import com.cocode.vcode.ide.utils.FontManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for listing destination folders in the file import destination picker.
 */
public class DestinationAdapter extends RecyclerView.Adapter<DestinationAdapter.DestinationViewHolder> {

    private final List<FileNode> folders = new ArrayList<>();
    private final int indentWidthPx;
    private final DestinationListener listener;
    private File selectedFile = null;
    private String projectName;

    public DestinationAdapter(DestinationListener listener, int indentDp, float screenDensity) {
        this.listener = listener;
        this.indentWidthPx = (int) (indentDp * screenDensity);
    }

    public void setTree(File projectRoot, String projectName, List<FileNode> tree) {
        this.projectName = projectName;
        folders.clear();

        boolean treeHasRoot = false;
        if (tree != null && !tree.isEmpty()) {
            FileNode first = tree.get(0);
            if (first.getFile().getAbsolutePath().equals(projectRoot.getAbsolutePath())) {
                treeHasRoot = true;
            }
        }

        if (!treeHasRoot) {
            // Add root project folder first
            FileNode rootNode = new FileNode(projectRoot, 0);
            folders.add(rootNode);
        }

        // Flatten directory structure
        flattenDirectories(tree, folders);

        // Auto-select root if nothing is selected
        if (selectedFile == null) {
            selectedFile = projectRoot;
            listener.onDestinationSelected(selectedFile);
        }

        notifyDataSetChanged();
    }

    private void flattenDirectories(List<FileNode> nodes, List<FileNode> out) {
        if (nodes == null) return;
        for (FileNode node : nodes) {
            if (node.isDirectory()) {
                out.add(node);
                // Recursively add all subdirectories (expanded or not)
                flattenDirectories(node.getChildren(), out);
            }
        }
    }

    @NonNull
    @Override
    public DestinationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDestinationFolderBinding binding = ItemDestinationFolderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new DestinationViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DestinationViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            for (Object payload : payloads) {
                if ("SELECTION_CHANGED".equals(payload)) {
                    FileNode node = folders.get(position);
                    boolean isSelected = selectedFile != null && selectedFile.getAbsolutePath().equals(node.getFile().getAbsolutePath());
                    holder.binding.ivCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);
                }
            }
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull DestinationViewHolder holder, int position) {
        FileNode node = folders.get(position);

        // Apply indentation
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.binding.viewIndent.getLayoutParams();
        params.width = node.getDepth() * indentWidthPx;
        holder.binding.viewIndent.setLayoutParams(params);

        // Display name
        if (node.getDepth() == 0 && projectName != null && !projectName.isEmpty()) {
            holder.binding.tvFolderName.setText(projectName);
        } else {
            holder.binding.tvFolderName.setText(node.getName());
        }

        // Apply font
        holder.binding.tvFolderName.setTypeface(FontManager.getInstance().getUiMedium(holder.itemView.getContext()));

        // Check if selected
        boolean isSelected = selectedFile != null && selectedFile.getAbsolutePath().equals(node.getFile().getAbsolutePath());
        holder.binding.ivCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);

        // Chevron logic
        boolean hasDirectoryChildren = false;
        if (node.getChildren() != null) {
            for (FileNode child : node.getChildren()) {
                if (child.isDirectory()) {
                    hasDirectoryChildren = true;
                    break;
                }
            }
        }
        if (hasDirectoryChildren) {
            holder.binding.ivChevron.setVisibility(View.VISIBLE);
            holder.binding.ivChevron.setRotation(90f);
        } else {
            holder.binding.ivChevron.setVisibility(View.INVISIBLE);
        }

        // Click listener
        holder.binding.getRoot().setOnClickListener(v -> {
            int oldSelectedPos = -1;
            if (selectedFile != null) {
                for (int i = 0; i < folders.size(); i++) {
                    if (folders.get(i).getFile().getAbsolutePath().equals(selectedFile.getAbsolutePath())) {
                        oldSelectedPos = i;
                        break;
                    }
                }
            }

            selectedFile = node.getFile();
            listener.onDestinationSelected(selectedFile);

            int newSelectedPos = holder.getAdapterPosition();

            if (oldSelectedPos != -1) {
                notifyItemChanged(oldSelectedPos, "SELECTION_CHANGED");
            }
            if (newSelectedPos != RecyclerView.NO_POSITION && newSelectedPos != oldSelectedPos) {
                notifyItemChanged(newSelectedPos, "SELECTION_CHANGED");
            }
        });
    }

    @Override
    public int getItemCount() {
        return folders.size();
    }

    public interface DestinationListener {
        void onDestinationSelected(File file);
    }

    public static class DestinationViewHolder extends RecyclerView.ViewHolder {
        final ItemDestinationFolderBinding binding;

        public DestinationViewHolder(@NonNull ItemDestinationFolderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
