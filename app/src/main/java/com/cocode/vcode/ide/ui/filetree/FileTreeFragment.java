package com.cocode.vcode.ide.ui.filetree;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.model.FileNode;
import com.cocode.vcode.ide.databinding.FragmentFileTreeBinding;
import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.cocode.vcode.ide.databinding.ItemCustomPopupBinding;
import com.cocode.vcode.ide.databinding.LayoutCustomPopupBinding;
import com.cocode.vcode.ide.ui.dialogs.ImportDestinationDialog;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;
import com.cocode.vcode.ide.ui.filetree.helper.FileClipboardHelper;
import com.cocode.vcode.ide.ui.filetree.helper.FileImportHelper;
import com.cocode.vcode.ide.ui.sheets.files.DeleteBottomSheet;
import com.cocode.vcode.ide.ui.sheets.files.NewFileBottomSheet;
import com.cocode.vcode.ide.ui.sheets.files.NewFolderBottomSheet;
import com.cocode.vcode.ide.ui.sheets.files.ProjectSearchBottomSheet;
import com.cocode.vcode.ide.ui.sheets.files.RenameBottomSheet;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;

/**
 * FileTreeFragment displays the project's file explorer.
 * It provides tools for navigating the project structure, creating/deleting files,
 * and importing assets from the device filesystem. It also visualizes Git statuses
 * on a per-file basis.
 */
public class FileTreeFragment extends Fragment implements FileTreeAdapter.FileTreeListener {

    private FragmentFileTreeBinding binding;
    private EditorViewModel viewModel;
    private FileTreeAdapter adapter;
    private FileSelectionListener selectionListener;
    private File selectedImportDestination = null;
    /**
     * Result launcher for importing multiple files from the system picker.
     */
    private final ActivityResultLauncher<String> importFilesLauncher = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris != null && !uris.isEmpty()) {
                    copyUrisToProject(uris);
                }
            }
    );
    /**
     * Result launcher for importing an entire directory tree from the system picker.
     */
    private final ActivityResultLauncher<Uri> importFolderLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri != null) {
                    copyFolderToProject(uri);
                }
            }
    );
    private DrawerLayout.DrawerListener drawerListener = null;
    private DrawerLayout attachedDrawerLayout = null;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Verify that the parent activity implements the selection listener
        if (context instanceof FileSelectionListener) {
            selectionListener = (FileSelectionListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFileTreeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize the tree adapter with a standard 16dp indentation per depth level
        float density = getResources().getDisplayMetrics().density;
        adapter = new FileTreeAdapter(this, 16, density);
        binding.rvFileTree.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvFileTree.setItemAnimator(null);
        binding.rvFileTree.setAdapter(adapter);

        // Apply specialized UI fonts
        binding.tvFileExplorer.setTypeface(FontManager.getInstance().getUiSemiBold(requireContext()));
        binding.btnImportFiles.setTypeface(FontManager.getInstance().getUiMedium(requireContext()));
        binding.btnImportFolder.setTypeface(FontManager.getInstance().getUiMedium(requireContext()));

        // Support right-clicking on empty space in file tree to reveal root actions (Paste, Find in Files)
        binding.rvFileTree.setOnGenericMotionListener((v, event) -> {
            if (event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
                if (event.getAction() == MotionEvent.ACTION_BUTTON_PRESS && event.getActionButton() == MotionEvent.BUTTON_SECONDARY) {
                    View child = binding.rvFileTree.findChildViewUnder(event.getX(), event.getY());
                    if (child == null && viewModel != null && viewModel.getProjectRoot() != null) {
                        onNodeContextMenu(binding.rvFileTree, new FileNode(viewModel.getProjectRoot(), 0), event.getX(), event.getY());
                        return true;
                    }
                }
            }
            return false;
        });

        binding.rvFileTree.setOnContextClickListener(v -> {
            if (viewModel != null && viewModel.getProjectRoot() != null) {
                onNodeContextMenu(binding.rvFileTree, new FileNode(viewModel.getProjectRoot(), 0), v.getWidth() / 2f, v.getHeight() / 2f);
                return true;
            }
            return false;
        });

        // Bind to the Activity-scoped EditorViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(EditorViewModel.class);

        setupObservers();

        // Wire up interaction listeners
        binding.btnRefresh.setOnClickListener(v -> viewModel.refreshFileTree());
        binding.btnImportFiles.setOnClickListener(v -> showImportDestinationDialog(() -> importFilesLauncher.launch("*/*")));
        binding.btnImportFolder.setOnClickListener(v -> showImportDestinationDialog(() -> importFolderLauncher.launch(null)));

        setupDrawerListener(view);
    }

    private void setupDrawerListener(View view) {
        if (getActivity() != null) {
            DrawerLayout dl = getActivity().findViewById(R.id.drawer_layout);
            if (dl != null) {
                attachedDrawerLayout = dl;
                drawerListener = new DrawerLayout.SimpleDrawerListener() {
                    @Override
                    public void onDrawerClosed(@NonNull View drawerView) {
                        clearClipboardState();
                    }
                };
                attachedDrawerLayout.addDrawerListener(drawerListener);
                return;
            }
        }
        view.post(() -> {
            if (!isAdded() || attachedDrawerLayout != null) return;
            ViewParent parent = view.getParent();
            while (parent != null) {
                if (parent instanceof DrawerLayout) {
                    attachedDrawerLayout = (DrawerLayout) parent;
                    drawerListener = new DrawerLayout.SimpleDrawerListener() {
                        @Override
                        public void onDrawerClosed(@NonNull View drawerView) {
                            clearClipboardState();
                        }
                    };
                    attachedDrawerLayout.addDrawerListener(drawerListener);
                    break;
                }
                parent = parent.getParent();
            }
        });
    }

    /**
     * Clears any active clipboard cut/copy state and restores normal node opacity.
     */
    public void clearClipboardState() {
        if (adapter != null) {
            adapter.clearClipboardState();
        }
    }

    /**
     * Connects reactive data streams from the ViewModel to update the file tree.
     */
    private void setupObservers() {
        // Observe the structural file tree data
        viewModel.getFileTree().observe(getViewLifecycleOwner(), nodes -> {
            if (nodes != null && viewModel.getProjectRoot() != null) {
                adapter.setRootPath(viewModel.getProjectRoot().getAbsolutePath());
                adapter.setProjectName(viewModel.getProjectName());
                adapter.setTree(nodes);
            }
        });

        // Observe Git statuses and update the UI dots based on user preferences
        viewModel.getGitStatuses().observe(getViewLifecycleOwner(), statuses -> {
            AppSettings settings = viewModel.getSettingsLiveData().getValue();
            if (settings != null && settings.gitShowFileTreeStatus && statuses != null) {
                adapter.setGitStatuses(statuses);
            } else {
                adapter.setGitStatuses(new HashMap<>());
            }
        });

        // Monitor settings changes to toggle Git status visibility instantly
        viewModel.getSettingsLiveData().observe(getViewLifecycleOwner(), settings -> {
            if (settings != null) {
                if (settings.gitShowFileTreeStatus && viewModel.getGitStatuses().getValue() != null) {
                    adapter.setGitStatuses(viewModel.getGitStatuses().getValue());
                } else {
                    adapter.setGitStatuses(new HashMap<>());
                }
            }
        });

        // FileOperationManager updates notifications independently.
    }

    /**
     * Copies a list of selected system URIs into the project root directory.
     */
    private void copyUrisToProject(List<Uri> uris) {
        File root = selectedImportDestination != null ? selectedImportDestination : viewModel.getProjectRoot();
        FileImportHelper.copyUrisToProject(requireContext(), uris, root, () -> viewModel.refreshFileTree());
    }

    /**
     * Copies an entire directory structure from a system SAF Uri into the project root.
     */
    private void copyFolderToProject(Uri treeUri) {
        File root = selectedImportDestination != null ? selectedImportDestination : viewModel.getProjectRoot();
        FileImportHelper.copyFolderToProject(requireContext(), treeUri, root, () -> viewModel.refreshFileTree());
    }

    private void showImportDestinationDialog(Runnable onConfirmed) {
        if (viewModel.getFileTree().getValue() == null || viewModel.getProjectRoot() == null) {
            Toast.makeText(getContext(), R.string.vcode_project_is_still_loading_please, Toast.LENGTH_SHORT).show();
            return;
        }

        ImportDestinationDialog dialog = ImportDestinationDialog.newInstance(
                viewModel.getProjectRoot(),
                viewModel.getProjectName(),
                viewModel.getFileTree().getValue()
        );
        dialog.setListener(destination -> {
            selectedImportDestination = destination;
            onConfirmed.run();
        });
        dialog.show(getChildFragmentManager(), "ImportDestinationDialog");
    }


    @Override
    public void onFileClick(File file) {
        if (!file.isDirectory()) {
            if (selectionListener != null) {
                // Notify the EditorActivity to load the selected file
                selectionListener.onFileSelected(new FileNode(file, 0));
            }
        }
    }

    @Override
    public void onNodeLongClick(View anchor, FileNode node) {
        onNodeContextMenu(anchor, node, anchor.getWidth() / 2f, anchor.getHeight() / 2f);
    }

    @Override
    public void onNodeContextMenu(View anchor, FileNode node, float x, float y) {
        if (!isAdded() || getContext() == null) return;
        File file = node.getFile();
        boolean isRoot = viewModel.getProjectRoot() != null && file.getAbsolutePath().equals(viewModel.getProjectRoot().getAbsolutePath());

        File clipboardFile = adapter.getClipboardFile();
        boolean canPaste = clipboardFile != null && clipboardFile.exists();

        LayoutCustomPopupBinding popupBinding = LayoutCustomPopupBinding.inflate(getLayoutInflater());
        int screenWidth = requireContext().getResources().getDisplayMetrics().widthPixels;
        int screenHeight = requireContext().getResources().getDisplayMetrics().heightPixels;
        int maxWidth = requireContext().getResources().getDimensionPixelSize(R.dimen.dialog_max_width);
        int preferredWidth = UiUtils.dpToPx(requireContext(), 220);
        int width = Math.min(preferredWidth, Math.min((int) (screenWidth * 0.92f), maxWidth));

        PopupWindow popupWindow = new PopupWindow(
                popupBinding.getRoot(),
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(8f);
        popupWindow.setAnimationStyle(R.style.VCodePopupMenuAnimation);

        if (isRoot) {
            if (canPaste) {
                addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_file_plus, getString(R.string.vcode_paste), () -> {
                    performPaste(file);
                });
                addDivider(popupBinding.popupContainer);
            }
            addFindInFilesPopupItem(popupBinding.popupContainer, popupWindow, file, node);
        } else {
            addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_pen, getString(R.string.vcode_rename), () -> showRenameDialog(file));
            addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_copy, getString(R.string.vcode_copy), () -> {
                adapter.setClipboardState(file, false);
            });
            addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_scissors, getString(R.string.vcode_cut), () -> {
                adapter.setClipboardState(file, true);
            });

            if (canPaste) {
                addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_file_plus, getString(R.string.vcode_paste), () -> {
                    File destDir = file.isDirectory() ? file : file.getParentFile();
                    performPaste(destDir);
                });
            }

            addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_copy, getString(R.string.vcode_copy_path), () -> showCopyPathPopup(anchor, file));

            addDivider(popupBinding.popupContainer);

            addFindInFilesPopupItem(popupBinding.popupContainer, popupWindow, file, node);

            if (!file.isDirectory()) {
                addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_magnifying_glass, getString(R.string.vcode_find_usages), () -> {
                    List<com.cocode.vcode.ide.core.lsp.LspLocation> usages = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().findFileUsages(file.getName());
                    if (usages == null || usages.isEmpty()) {
                        Toast.makeText(getContext(), R.string.vcode_no_usages_found, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ProjectSearchBottomSheet searchSheet = new ProjectSearchBottomSheet();
                    searchSheet.setUsages(getString(R.string.vcode_find_usages), file.getName(), usages);
                    searchSheet.setListener((searchedFile, lineNumber) -> {
                        if (selectionListener != null) {
                            selectionListener.onFileSelected(new FileNode(searchedFile, 0));
                            if (getActivity() instanceof com.cocode.vcode.ide.ui.editor.EditorActivity) {
                                com.cocode.vcode.ide.ui.editor.EditorActivity editorActivity = (com.cocode.vcode.ide.ui.editor.EditorActivity) getActivity();
                                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                    editorActivity.jumpToLine(lineNumber);
                                }, 500);
                            }
                        }
                    });
                    searchSheet.show(getChildFragmentManager(), "FindUsages");
                });
            }

            addDivider(popupBinding.popupContainer);

            View deleteItem = addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_trash, getString(R.string.vcode_delete), () -> showDeleteDialog(file));
            TextView tvTitle = deleteItem.findViewById(R.id.tv_title);
            ImageView ivIcon = deleteItem.findViewById(R.id.iv_icon);
            int errorColor = ContextCompat.getColor(requireContext(), R.color.vcode_accent_error);
            if (tvTitle != null) tvTitle.setTextColor(errorColor);
            if (ivIcon != null) ivIcon.setColorFilter(errorColor);
        }

        popupBinding.getRoot().measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupHeight = popupBinding.getRoot().getMeasuredHeight();

        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);

        int clickYOnScreen = anchorLocation[1] + (int) y;
        int spaceBelow = screenHeight - clickYOnScreen;

        int xOffset = (int) x;
        if (anchorLocation[0] + xOffset + width > screenWidth) {
            xOffset = Math.max(0, screenWidth - anchorLocation[0] - width - UiUtils.dpToPx(requireContext(), 8));
        }

        int yOffset;
        if (spaceBelow >= popupHeight) {
            yOffset = (int) y - anchor.getHeight();
        } else {
            yOffset = (int) y - anchor.getHeight() - popupHeight;
        }

        popupWindow.showAsDropDown(anchor, xOffset, yOffset);
    }

    private void addFindInFilesPopupItem(ViewGroup container, PopupWindow popupWindow, File file, FileNode node) {
        addPopupItem(container, popupWindow, R.drawable.ic_magnifying_glass, getString(R.string.vcode_find_in_files), () -> {
            if (file.isDirectory()) {
                ProjectSearchBottomSheet searchSheet = new ProjectSearchBottomSheet();
                searchSheet.setProjectRoot(file);
                searchSheet.setListener((searchedFile, lineNumber) -> {
                    if (selectionListener != null) {
                        selectionListener.onFileSelected(new FileNode(searchedFile, 0));
                        if (getActivity() instanceof com.cocode.vcode.ide.ui.editor.EditorActivity) {
                            com.cocode.vcode.ide.ui.editor.EditorActivity editorActivity = (com.cocode.vcode.ide.ui.editor.EditorActivity) getActivity();
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                editorActivity.jumpToLine(lineNumber);
                            }, 500);
                        }
                    }
                });
                searchSheet.show(getChildFragmentManager(), "ProjectSearch");
            } else if (selectionListener != null) {
                selectionListener.onFindInFile(node);
            }
        });
    }

    private void showCopyPathPopup(View anchor, File file) {
        LayoutCustomPopupBinding popupBinding = LayoutCustomPopupBinding.inflate(getLayoutInflater());
        int screenWidth = requireContext().getResources().getDisplayMetrics().widthPixels;
        int maxWidth = requireContext().getResources().getDimensionPixelSize(R.dimen.dialog_max_width);
        int preferredWidth = UiUtils.dpToPx(requireContext(), 220);
        int width = Math.min(preferredWidth, Math.min((int) (screenWidth * 0.92f), maxWidth));

        PopupWindow popupWindow = new PopupWindow(
                popupBinding.getRoot(),
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(8f);
        popupWindow.setAnimationStyle(R.style.VCodePopupMenuAnimation);

        addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_copy, getString(R.string.vcode_absolute_path), () -> {
            copyToSystemClipboard("Absolute Path", file.getAbsolutePath());
        });

        addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_copy, getString(R.string.vcode_relative_path), () -> {
            if (viewModel.getProjectRoot() != null) {
                String relPath = file.getAbsolutePath().replace(viewModel.getProjectRoot().getAbsolutePath() + File.separator, "");
                if (relPath.startsWith(File.separator)) relPath = relPath.substring(1);
                copyToSystemClipboard("Relative Path", relPath);
            }
        });

        popupBinding.getRoot().measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupHeight = popupBinding.getRoot().getMeasuredHeight();

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int screenH = requireActivity().getWindow().getDecorView().getHeight();
        int spaceBelow = screenH - loc[1] - anchor.getHeight();

        if (spaceBelow >= popupHeight) {
            popupWindow.showAsDropDown(anchor, anchor.getWidth() / 2, -anchor.getHeight() / 2);
        } else {
            popupWindow.showAsDropDown(anchor, anchor.getWidth() / 2, -(popupHeight + anchor.getHeight() / 2));
        }
    }

    private View addPopupItem(ViewGroup container, PopupWindow popup, int iconRes, String title, Runnable action) {
        ItemCustomPopupBinding itemBinding = ItemCustomPopupBinding.inflate(getLayoutInflater(), container, false);
        itemBinding.ivIcon.setImageResource(iconRes);
        itemBinding.tvTitle.setText(title);
        itemBinding.tvTitle.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall);
        itemBinding.tvTitle.setTypeface(FontManager.getInstance().getUiMedium(requireContext()));
        itemBinding.getRoot().setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        container.addView(itemBinding.getRoot());
        return itemBinding.getRoot();
    }

    private void addDivider(ViewGroup container) {
        View divider = new View(requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiUtils.dpToPx(requireContext(), 1)
        );
        params.setMargins(0, UiUtils.dpToPx(requireContext(), 4), 0, UiUtils.dpToPx(requireContext(), 4));
        divider.setLayoutParams(params);
        divider.setBackgroundColor(getThemeColor(com.google.android.material.R.attr.colorOutlineVariant));
        container.addView(divider);
    }

    private int getThemeColor(int attrRes) {
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(attrRes, typedValue, true);
        return typedValue.data;
    }

    private void copyToSystemClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), R.string.vcode_path_copied_to_clipboard, Toast.LENGTH_SHORT).show();
        }
    }

    private void performPaste(File destinationDir) {
        File source = adapter.getClipboardFile();
        boolean isCut = adapter.isCutAction();
        FileClipboardHelper.performPaste(requireContext(), source, isCut, destinationDir, (success, src, tgt) -> {
            if (success && isCut) {
                adapter.setClipboardState(null, false);
                viewModel.handleNodeMoved(src, tgt);
            } else {
                viewModel.refreshFileTree();
            }
        });
    }

    private void showRenameDialog(File file) {
        RenameBottomSheet.RenameType type = file.isDirectory() ? RenameBottomSheet.RenameType.FOLDER : RenameBottomSheet.RenameType.FILE;
        RenameBottomSheet.show(
                getChildFragmentManager(),
                type,
                file.getName(),
                newName -> viewModel.renameNode(file, newName)
        );
    }

    private void showDeleteDialog(File file) {
        DeleteBottomSheet.DeleteType type = file.isDirectory() ? DeleteBottomSheet.DeleteType.FOLDER : DeleteBottomSheet.DeleteType.FILE;
        DeleteBottomSheet.show(
                getChildFragmentManager(),
                type,
                file.getName(),
                null,
                () -> viewModel.deleteNode(file)
        );
    }

    @Override
    public void onAddFileClick(File parentDir) {
        String projectName = viewModel.getProjectName();
        if (projectName == null || projectName.isEmpty() || (viewModel.getProjectRoot() != null && projectName.equals(viewModel.getProjectRoot().getName()))) {
            projectName = ProjectRepository.getProjectName(parentDir != null ? parentDir : viewModel.getProjectRoot());
        }
        NewFileBottomSheet sheet = NewFileBottomSheet.newInstance(parentDir, projectName);
        sheet.setListener((fileName, initialContent) -> viewModel.createFile(parentDir, fileName, initialContent));
        sheet.show(getChildFragmentManager(), "NewFileBottomSheet");
    }

    @Override
    public void onAddFolderClick(File parentDir) {
        NewFolderBottomSheet sheet = NewFolderBottomSheet.newInstance(parentDir);
        sheet.show(getChildFragmentManager(), "NewFolderBottomSheet");
    }

    @Override
    public void onDestroyView() {
        if (attachedDrawerLayout != null && drawerListener != null) {
            attachedDrawerLayout.removeDrawerListener(drawerListener);
            drawerListener = null;
            attachedDrawerLayout = null;
        }
        clearClipboardState();
        super.onDestroyView();
        binding = null;
    }

    /**
     * Interface for communicating file selections to the hosting Activity.
     */
    public interface FileSelectionListener {
        void onFileSelected(FileNode fileNode);

        void onFindInFile(FileNode fileNode);
    }
}