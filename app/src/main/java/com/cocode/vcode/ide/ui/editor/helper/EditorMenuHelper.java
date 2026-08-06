package com.cocode.vcode.ide.ui.editor.helper;

import android.content.Intent;
import android.widget.Toast;

import androidx.fragment.app.FragmentManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.lsp.LspCallback;
import com.cocode.vcode.ide.core.lsp.LspEditorBridge;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.ui.editor.EditorActivity;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;
import com.cocode.vcode.ide.ui.git.GitActivity;
import com.cocode.vcode.ide.ui.sheets.editor.EditorOptionsBottomSheet;
import com.cocode.vcode.ide.utils.CodeFormatter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EditorMenuHelper {

    public static void showOverflowMenu(EditorActivity activity, EditorViewModel viewModel, FragmentManager fragmentManager, String projectName, MenuCallbacks callbacks) {
        int activeIndex = viewModel.getActiveTabIndex().getValue() != null ? viewModel.getActiveTabIndex().getValue() : -1;
        List<EditorFile> files = viewModel.getOpenFiles().getValue();
        boolean hasOpenFile = files != null && activeIndex >= 0 && activeIndex < files.size();
        boolean showTextEditingOptions = false;
        EditorFile activeFile = hasOpenFile ? files.get(activeIndex) : null;

        if (hasOpenFile && activeFile != null) {
            FileType type = activeFile.getFileType();
            boolean isBinary = activeFile.isBinaryAsset();

            boolean supportsPreview = type == FileType.CSV || type == FileType.SVG || type == FileType.MARKDOWN;
            String relPath = activeFile.getRelativePath(viewModel.getProjectRoot());
            boolean isPreviewMode = supportsPreview && viewModel.getPreviewState(relPath);

            if (!isBinary && !isPreviewMode) {
                showTextEditingOptions = true;
            }
        }

        List<EditorOptionsBottomSheet.Option> options = new ArrayList<>();

        if (showTextEditingOptions) {
            boolean isVirtual = activeFile.isVirtual();
            if (!isVirtual) {
                options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_magnifying_glass, activity.getString(R.string.vcode_find_hint).replace("...", "/Replace"), callbacks::onShowFindReplace));
                options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_lock, "Read-only", true, callbacks.isReadOnly(), callbacks::onToggleReadOnly));
            }
            if (CodeFormatter.isFormatSupported(files.get(activeIndex).getFileType())) {
                options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_wand_magic, "Format Code", callbacks::onFormatCode));
            }
            if (!isVirtual) {
                options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_arrow_right, activity.getString(R.string.vcode_go_to_line), callbacks::onGoToLine));

                // LSP navigation — only for supported text languages (not binary/virtual)
                LspEditorBridge bridge = callbacks.getActiveLspBridge();
                if (bridge != null) {
                    options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_code, activity.getString(R.string.vcode_lsp_go_to_definition), () ->
                            bridge.requestDefinition(new LspCallback<LspLocation>() {
                                @Override
                                public void onResult(LspLocation result) {
                                    if (result == null) {
                                        Toast.makeText(activity, R.string.vcode_lsp_no_definition_found, Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    File target = new File(result.uri);
                                    int line = result.range != null ? result.range.start.line + 1 : 1;
                                    callbacks.openFileAtLine(target, line);
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    Toast.makeText(activity, R.string.vcode_lsp_no_definition_found, Toast.LENGTH_SHORT).show();
                                }
                            })
                    ));

                    options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_magnifying_glass, activity.getString(R.string.vcode_lsp_find_references), () ->
                            bridge.requestReferences(new LspCallback<List<LspLocation>>() {
                                @Override
                                public void onResult(List<LspLocation> result) {
                                    if (result == null || result.isEmpty()) {
                                        Toast.makeText(activity, R.string.vcode_lsp_no_references_found, Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    if (result.size() == 1) {
                                        // Single result — navigate directly
                                        LspLocation loc = result.get(0);
                                        int line = loc.range != null ? loc.range.start.line + 1 : 1;
                                        callbacks.openFileAtLine(new File(loc.uri), line);
                                    } else {
                                        // Multiple results — show list as chooser options
                                        List<EditorOptionsBottomSheet.Option> refOptions = new ArrayList<>();
                                        for (LspLocation loc : result) {
                                            File f = new File(loc.uri);
                                            int line = loc.range != null ? loc.range.start.line + 1 : 1;
                                            String label = f.getName() + ":" + line;

                                            String ext = com.cocode.vcode.ide.utils.FileUtils.getExtension(f.getName());
                                            int iconResId = FileType.fromExtension(ext).getIconResId();

                                            refOptions.add(new EditorOptionsBottomSheet.Option(
                                                    iconResId, label,
                                                    () -> callbacks.openFileAtLine(f, line)
                                            ));
                                        }
                                        EditorOptionsBottomSheet refsSheet = new EditorOptionsBottomSheet();
                                        refsSheet.setOptions(refOptions);
                                        refsSheet.show(fragmentManager, activity.getString(R.string.vcode_lsp_references_title));
                                    }
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    Toast.makeText(activity, R.string.vcode_lsp_no_references_found, Toast.LENGTH_SHORT).show();
                                }
                            })
                    ));
                }
            }
        }

        options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_star, "Snippet Manager", callbacks::onShowSnippetManager));

        options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_globe, "API Tester", () -> viewModel.openApiTester()));

        options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_git, "Git", () -> callbacks.onNavigateWithUnsavedCheck(() -> {
            Intent navToGit = new Intent(activity, GitActivity.class);
            if (viewModel.getProjectRoot() != null) {
                navToGit.putExtra("project_path", viewModel.getProjectRoot().getAbsolutePath());
                navToGit.putExtra("project_name", projectName);
                AppSettings settings = viewModel.getSettingsLiveData().getValue();
                if (settings != null && settings.gitDefaultBranch != null) {
                    navToGit.putExtra("default_branch", settings.gitDefaultBranch);
                }
                activity.startActivity(navToGit);
            } else {
                Toast.makeText(activity, R.string.vcode_error_project_directory_not_loaded, Toast.LENGTH_SHORT).show();
            }
        })));

        AppSettings settingsForMenu = viewModel.getSettingsLiveData().getValue();
        boolean autoSave = settingsForMenu != null && settingsForMenu.autoSave;
        if (hasOpenFile && !autoSave) {
            options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_floppy_disk, "Save All", () -> {
                viewModel.saveAll();
                Toast.makeText(activity, R.string.vcode_saving_all_files, Toast.LENGTH_SHORT).show();
            }));
        }

        EditorOptionsBottomSheet bottomSheet = new EditorOptionsBottomSheet();
        bottomSheet.setOptions(options);
        bottomSheet.show(fragmentManager, "EditorOptions");
    }

    public interface MenuCallbacks {
        void onShowFindReplace();

        void onToggleReadOnly();

        void onFormatCode();

        void onGoToLine();

        void onShowSnippetManager();

        void onNavigateWithUnsavedCheck(Runnable action);

        boolean isReadOnly();

        /**
         * Returns the LSP bridge for the currently active code viewer, or null if none.
         */
        LspEditorBridge getActiveLspBridge();

        /**
         * Opens a file at a specific line (1-based).
         */
        void openFileAtLine(File file, int line);
    }
}
