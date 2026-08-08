package com.cocode.vcode.ide.ui.editor;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.data.model.FileNode;
import com.cocode.vcode.ide.databinding.ActivityEditorBinding;
import com.cocode.vcode.ide.ui.base.BaseActivity;
import com.cocode.vcode.ide.ui.editor.helper.EditorMenuHelper;
import com.cocode.vcode.ide.ui.editor.helper.EditorPreviewHelper;
import com.cocode.vcode.ide.ui.editor.viewer.IEditorCallback;
import com.cocode.vcode.ide.ui.editor.viewer.IFileViewer;
import com.cocode.vcode.ide.ui.editor.viewer.ViewerManager;
import com.cocode.vcode.ide.ui.filetree.FileTreeFragment;
import com.cocode.vcode.ide.ui.sheets.editor.GoToLineBottomSheet;
import com.cocode.vcode.ide.ui.sheets.editor.ProblemsBottomSheet;
import com.cocode.vcode.ide.ui.sheets.editor.SnippetsBottomSheet;
import com.cocode.vcode.ide.utils.CodeFormatter;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.LocalWebServer;
import com.cocode.vcode.ide.utils.ProjectFileRecovery;
import com.cocode.vcode.ide.utils.UiUtils;
import com.cocode.vcode.ide.views.CodeEditText;

import java.io.File;
import java.util.List;

public class EditorActivity extends BaseActivity implements FileTreeFragment.FileSelectionListener, IEditorCallback {

    public static final String EXTRA_PROJECT_PATH = "extra_project_path";
    public static final String EXTRA_PROJECT_ID = "extra_project_id";
    public static final String EXTRA_PROJECT_NAME = "extra_project_name";
    public static final String EXTRA_OPEN_FILE_PATH = "extra_open_file_path";
    public static final String EXTRA_SOURCE_URI = "extra_source_uri";

    private ActivityEditorBinding binding;
    private LocalWebServer localWebServer;
    private EditorViewModel viewModel;
    private ViewerManager viewerManager;

    /**
     * Extras for the external file that should be opened once session restore completes.
     * Stored here to avoid a race where openFile() fires before restoreTabsFromState() runs.
     */
    private String pendingOpenFilePath = null;
    private String pendingOpenSourceUri = null;
    private IFileViewer activeViewer;
    private boolean isReadOnly = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UiUtils.applySystemBarInsets(binding.drawerLayout, binding.mainContent, binding.drawerContainer);

        String projectPath = getIntent().getStringExtra(EXTRA_PROJECT_PATH);
        String projectId = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        String projectName = getIntent().getStringExtra(EXTRA_PROJECT_NAME);

        if (projectPath == null) {
            Toast.makeText(this, R.string.vcode_no_project_path_provided, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (projectId == null) projectId = projectPath.substring(projectPath.lastIndexOf("/") + 1);
        if (projectName == null) projectName = "Project";

        EditorViewModelFactory factory = new EditorViewModelFactory(this);
        viewModel = new ViewModelProvider(this, factory).get(EditorViewModel.class);
        viewerManager = new ViewerManager();

        binding.tvProjectName.setText(projectName);
        binding.tvProjectName.setTypeface(FontManager.getInstance().getUiSemiBold(this));
        binding.tvOpenFileFromTree.setTypeface(FontManager.getInstance().getUiMedium(this));

        File projectDirectory = new File(projectPath);
        ProjectFileRecovery.ensureProjectFilesExist(projectDirectory);
        viewModel.initProject(projectDirectory, projectId, projectName);

        setupFragments();
        setupFloatingPreviewStyles();
        setupListeners();
        setupObservers();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }
                if (binding.findReplaceBar.getVisibility() == View.VISIBLE) {
                    binding.findReplaceBar.slideUp();
                    return;
                }

                CodeEditText codeEditText = getActiveCodeEditor();
                if (codeEditText != null && codeEditText.getSelectionStart() != codeEditText.getSelectionEnd()) {
                    codeEditText.collapseSelection();
                    return;
                }

                navigateWithUnsavedCheck(EditorActivity.this::finish);
            }
        });

        // If launched with an external file, store it as pending.
        // The actual openFile() call is deferred to the isEditorLoading observer (false branch)
        // so it runs AFTER restoreTabsFromState() has fully replaced openFilesLiveData.
        extractPendingOpenIntent(getIntent());
        if (pendingOpenFilePath != null) {
            viewModel.setSkipDefaultFileOpen(true);
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // onNewIntent fires on an already-running Activity; session is already loaded,
        // so it is safe to open the file immediately.
        handleOpenFileIntent(intent);
    }

    private void extractPendingOpenIntent(Intent intent) {
        if (intent != null && intent.hasExtra(EXTRA_OPEN_FILE_PATH)) {
            pendingOpenFilePath = intent.getStringExtra(EXTRA_OPEN_FILE_PATH);
            pendingOpenSourceUri = intent.getStringExtra(EXTRA_SOURCE_URI);
        }
    }

    private void handleOpenFileIntent(Intent intent) {
        if (intent == null) return;

        if (intent.hasExtra(EXTRA_OPEN_FILE_PATH)) {
            String path = intent.getStringExtra(EXTRA_OPEN_FILE_PATH);
            String sourceUri = intent.getStringExtra(EXTRA_SOURCE_URI);
            if (path != null) {
                File file = new File(path);
                if (file.exists() && file.isFile()) {
                    if (sourceUri != null) {
                        viewModel.openFile(file, sourceUri);
                    } else {
                        viewModel.openFile(file);
                    }
                }
            }
        }
    }

    private void setupFragments() {
        if (getSupportFragmentManager().findFragmentById(binding.drawerContainer.getId()) == null) {
            FileTreeFragment fileTreeFragment = new FileTreeFragment();
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(binding.drawerContainer.getId(), fileTreeFragment);
            ft.commit();
        }
    }

    private void setupFloatingPreviewStyles() {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, value, true);
        int baseColor = value.data;
        int glassAccentColor = (baseColor & 0x00FFFFFF) | 0xD9000000;

        GradientDrawable ovalDrawable = new GradientDrawable();
        ovalDrawable.setShape(GradientDrawable.OVAL);
        ovalDrawable.setColor(glassAccentColor);
        binding.ivViewPreview.setBackground(ovalDrawable);
        binding.ivTogglePreview.setBackground(ovalDrawable);
    }

    private void setupListeners() {
        binding.btnMenu.setOnClickListener(v -> {
            UiUtils.hideKeyboard(this);
            CodeEditText codeEditText = getActiveCodeEditor();
            if (codeEditText != null) {
                codeEditText.clearFocus();
            }
            binding.drawerLayout.openDrawer(GravityCompat.START);
        });

        binding.btnUndo.setOnClickListener(v -> {
            CodeEditText codeEditText = getActiveCodeEditor();
            if (codeEditText != null && codeEditText.canUndo()) codeEditText.undo();
        });

        binding.btnRedo.setOnClickListener(v -> {
            CodeEditText codeEditText = getActiveCodeEditor();
            if (codeEditText != null && codeEditText.canRedo()) codeEditText.redo();
        });

        binding.btnRun.setOnClickListener(v -> handleRunAction());

        binding.ivViewPreview.setOnClickListener(v -> executeActiveFilePreviewIntent());

        binding.ivTogglePreview.setOnClickListener(v -> toggleInlinePreview());

        binding.btnSaveCurrent.setOnClickListener(v -> {
            if (activeViewer instanceof com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) {
                ((com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) activeViewer).flushContentToViewModel();
            }
            Integer activeIndex = viewModel.getActiveTabIndex().getValue();
            if (activeIndex != null && activeIndex >= 0) {
                viewModel.saveActiveFile();
            }
        });


        binding.diagnosticBar.setOnClickListener(v -> {
            ProblemsBottomSheet sheet = new ProblemsBottomSheet();
            sheet.setListener(this::jumpToLine);
            Integer activeIndex = viewModel.getActiveTabIndex().getValue();
            if (activeIndex != null && activeIndex >= 0) {
                List<EditorFile> openFiles = viewModel.getOpenFiles().getValue();
                if (openFiles != null && activeIndex < openFiles.size()) {
                    sheet.setFilterFile(openFiles.get(activeIndex).getFile());
                }
            }
            sheet.show(getSupportFragmentManager(), "ProblemsSheet");
        });


        binding.btnOverflow.setOnClickListener(v -> showOverflowMenu());

        binding.tabBar.setOnTabClickListener(index -> {
            saveCurrentEditorState();
            viewModel.setActiveTab(index);
        });

        binding.tabBar.setOnTabCloseListener(index -> {
            saveCurrentEditorState();
            handleTabClose(index);
        });
    }

    private CodeEditText getActiveCodeEditor() {
        if (activeViewer != null) {
            return activeViewer.getCodeEditor();
        }
        return null;
    }

    private void handleRunAction() {
        EditorPreviewHelper.PreviewCallbacks callbacks = new EditorPreviewHelper.PreviewCallbacks() {
            @Override
            public void updateToolbarVisibility() {
                EditorActivity.this.updateToolbarVisibility();
            }

            @Override
            public void executeActiveFilePreviewIntent() {
                EditorActivity.this.executeActiveFilePreviewIntent();
            }

            @Override
            public void updateActiveViewer(EditorFile file, boolean isPreview) {
                EditorActivity.this.updateActiveViewer(file, isPreview);
            }
        };

        Runnable stopUI = () -> {
            binding.btnRun.setImageResource(R.drawable.ic_play);
            binding.ivViewPreview.setVisibility(View.GONE);
        };

        Runnable startUI = () -> {
            binding.btnRun.setImageResource(R.drawable.ic_stop);
            binding.ivViewPreview.setVisibility(View.VISIBLE);
        };

        localWebServer = EditorPreviewHelper.handleRunAction(this, viewModel, localWebServer, callbacks, stopUI, startUI);
    }

    private void toggleInlinePreview() {
        EditorPreviewHelper.PreviewCallbacks callbacks = new EditorPreviewHelper.PreviewCallbacks() {
            @Override
            public void updateToolbarVisibility() {
                EditorActivity.this.updateToolbarVisibility();
            }

            @Override
            public void executeActiveFilePreviewIntent() {
                EditorActivity.this.executeActiveFilePreviewIntent();
            }

            @Override
            public void updateActiveViewer(EditorFile file, boolean isPreview) {
                EditorActivity.this.updateActiveViewer(file, isPreview);
            }
        };
        EditorPreviewHelper.toggleInlinePreview(viewModel, callbacks);
    }

    private void updateToolbarVisibility() {
        boolean isServerRunning = localWebServer != null && localWebServer.isRunning();

        int activeIndex = viewModel.getActiveTabIndex().getValue() != null ? viewModel.getActiveTabIndex().getValue() : -1;
        List<EditorFile> files = viewModel.getOpenFiles().getValue();
        boolean hasOpenFile = files != null && activeIndex >= 0 && activeIndex < files.size();
        boolean isActiveHtml = false;

        if (hasOpenFile) {
            EditorFile activeFile = files.get(activeIndex);
            if (activeFile.getFileType() == FileType.HTML) {
                isActiveHtml = true;
            }
            binding.btnUndo.setVisibility(View.VISIBLE);
            binding.btnRedo.setVisibility(View.VISIBLE);
            AppSettings settings = viewModel.getSettingsLiveData().getValue();
            boolean autoSave = settings != null && settings.autoSave;
            binding.btnSaveCurrent.setVisibility(autoSave ? View.GONE : View.VISIBLE);
        } else {
            binding.btnUndo.setVisibility(View.GONE);
            binding.btnRedo.setVisibility(View.GONE);
            binding.btnSaveCurrent.setVisibility(View.GONE);
        }

        if (isServerRunning || isActiveHtml) {
            binding.btnRun.setVisibility(View.VISIBLE);
        } else {
            binding.btnRun.setVisibility(View.GONE);
        }
    }

    private void executeActiveFilePreviewIntent() {
        EditorPreviewHelper.executeActiveFilePreviewIntent(this, viewModel, localWebServer);
    }

    private void setupObservers() {
        viewModel.getSettingsLiveData().observe(this, settings -> {
            if (settings == null) return;
            int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (settings.theme == AppSettings.Theme.DARK) mode = AppCompatDelegate.MODE_NIGHT_YES;
            else if (settings.theme == AppSettings.Theme.LIGHT)
                mode = AppCompatDelegate.MODE_NIGHT_NO;
            AppCompatDelegate.setDefaultNightMode(mode);

            // Rebind the active viewer so settings take effect
            if (activeViewer != null) {
                int activeIndex = viewModel.getActiveTabIndex().getValue() != null ? viewModel.getActiveTabIndex().getValue() : -1;
                List<EditorFile> files = viewModel.getOpenFiles().getValue();
                if (files != null && activeIndex >= 0 && activeIndex < files.size()) {
                    activeViewer.bindFile(files.get(activeIndex), viewModel);
                }
            }
            binding.tabBar.setAutoSaveOn(settings.autoSave);
            updateToolbarVisibility();
        });

        viewModel.getIsEditorLoading().observe(this, isLoading -> {
            if (isLoading != null && isLoading) {
                binding.progressEditorLoading.setVisibility(View.VISIBLE);
                binding.viewerContainer.setVisibility(View.GONE);
                binding.layoutEmptyEditor.setVisibility(View.GONE);
            } else {
                binding.progressEditorLoading.setVisibility(View.GONE);
                List<EditorFile> files = viewModel.getOpenFiles().getValue();
                if (files != null && !files.isEmpty()) {
                    binding.viewerContainer.setVisibility(View.VISIBLE);
                    binding.layoutEmptyEditor.setVisibility(View.GONE);

                    // setText() in CodeEditText dispatches prepareLoad to a CPU background
                    // thread. The viewerContainer was GONE while that work ran, so when
                    // applyLoaded() called invalidate(), the view was hidden and didn't draw.
                    // Now that the container is VISIBLE, post a re-bind on the next frame so
                    // the active viewer is refreshed with its content correctly rendered.
                    Integer activeIdx = viewModel.getActiveTabIndex().getValue();
                    if (activeIdx != null && activeIdx >= 0 && activeIdx < files.size() && activeViewer != null) {
                        final EditorFile activeFile = files.get(activeIdx);
                        binding.viewerContainer.post(() -> activeViewer.bindFile(activeFile, viewModel));
                    }
                } else {
                    binding.viewerContainer.setVisibility(View.GONE);
                    binding.layoutEmptyEditor.setVisibility(View.VISIBLE);
                }

                // Session restore is complete — now it is safe to open the externally-requested
                // file. Doing this here avoids the race where restoreTabsFromState() would wipe
                // the file from openFilesLiveData if we opened it earlier in onCreate().
                if (pendingOpenFilePath != null) {
                    String path = pendingOpenFilePath;
                    String sourceUri = pendingOpenSourceUri;
                    pendingOpenFilePath = null;
                    pendingOpenSourceUri = null;
                    File file = new File(path);
                    if (file.exists() && file.isFile()) {
                        if (sourceUri != null) {
                            viewModel.openFile(file, sourceUri);
                        } else {
                            viewModel.openFile(file);
                        }
                    }
                }
            }
        });

        viewModel.getOpenFiles().observe(this, files -> {
            int activeIndex = viewModel.getActiveTabIndex().getValue() != null ? viewModel.getActiveTabIndex().getValue() : -1;
            boolean isLoading = viewModel.getIsEditorLoading().getValue() != null && viewModel.getIsEditorLoading().getValue();
            if (files != null && !files.isEmpty()) {
                if (!isLoading) {
                    binding.layoutEmptyEditor.setVisibility(View.GONE);
                    binding.viewerContainer.setVisibility(View.VISIBLE);
                }
                binding.tabBar.setVisibility(View.VISIBLE);
                binding.tabBar.setTabs(files, activeIndex);
                updateBreadcrumbVisibility();
            } else {
                if (!isLoading) {
                    binding.layoutEmptyEditor.setVisibility(View.VISIBLE);
                    binding.viewerContainer.setVisibility(View.GONE);
                }
                binding.tabBar.setVisibility(View.GONE);
                binding.diagnosticBar.setVisibility(View.GONE);
                updateBreadcrumbVisibility();

                // Hide keyboard
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);

                // Hide all toolbar buttons except ivViewPreview (keep visible if server is running)
                binding.ivTogglePreview.setVisibility(View.GONE);
                boolean isServerRunning = localWebServer != null && localWebServer.isRunning();
                binding.ivViewPreview.setVisibility(isServerRunning ? View.VISIBLE : View.GONE);
                updateToolbarVisibility();
            }
        });

        viewModel.getActiveTabIndex().observe(this, index -> {
            List<EditorFile> files = viewModel.getOpenFiles().getValue();
            if (files != null && index >= 0 && index < files.size()) {
                EditorFile activeFile = files.get(index);
                binding.tabBar.setActiveTab(index);

                String relPath = activeFile.getRelativePath(viewModel.getProjectRoot());
                updateBreadcrumbVisibility();

                boolean isPreview = viewModel.getPreviewState(relPath);
                // Don't default to preview mode for empty files (freshly created)
                if (isPreview && !viewModel.hasExplicitPreviewState(relPath)) {
                    String content = activeFile.getContent();
                    if (content == null || content.trim().isEmpty()) {
                        isPreview = false;
                    }
                }
                updateActiveViewer(activeFile, isPreview);
                boolean isEmpty;
                if (activeViewer != null && activeViewer.getCodeEditor() != null) {
                    isEmpty = activeViewer.getCodeEditor().length() == 0;
                } else {
                    String content = activeFile.getContent();
                    isEmpty = (content == null || content.trim().isEmpty());
                }
                boolean showDiagnostic = isFileDiagnosable(activeFile) && !isEmpty;
                binding.diagnosticBar.setVisibility(showDiagnostic ? View.VISIBLE : View.GONE);
            }
            updateToolbarVisibility();
        });

        viewModel.getActiveFileDiagnostics().observe(this, counts -> {
            List<EditorFile> files = viewModel.getOpenFiles().getValue();
            Integer idx = viewModel.getActiveTabIndex().getValue();
            if (files == null || idx == null || idx < 0 || idx >= files.size() || !isFileDiagnosable(files.get(idx))) {
                binding.diagnosticBar.setVisibility(View.GONE);
                return;
            }

            boolean isEmpty;
            if (activeViewer != null && activeViewer.getCodeEditor() != null) {
                isEmpty = activeViewer.getCodeEditor().length() == 0;
            } else {
                String content = files.get(idx).getContent();
                isEmpty = (content == null || content.trim().isEmpty());
            }

            if (isEmpty) {
                binding.diagnosticBar.setVisibility(View.GONE);
                return;
            }

            binding.diagnosticBar.setVisibility(View.VISIBLE);
            if (counts != null) {
                binding.diagnosticBar.update(counts[0], counts[1], counts[2]);
            } else {
                binding.diagnosticBar.setLoading();
            }
        });
    }

    private void updateBreadcrumbVisibility() {
        if (viewModel == null || binding == null) return;
        List<EditorFile> files = viewModel.getOpenFiles().getValue();
        Integer index = viewModel.getActiveTabIndex().getValue();
        if (files != null && !files.isEmpty() && index != null && index >= 0 && index < files.size()) {
            EditorFile activeFile = files.get(index);
            if (activeFile.getFileType() == FileType.API_TESTER) {
                binding.breadcrumb.setVisibility(View.GONE);
            } else {
                binding.breadcrumb.setVisibility(View.VISIBLE);
                String relPath = activeFile.getRelativePath(viewModel.getProjectRoot());
                binding.breadcrumb.setPath(viewModel.getProjectName(), relPath);
            }
        } else {
            binding.breadcrumb.setVisibility(View.GONE);
        }
    }

    private boolean isFileDiagnosable(EditorFile file) {
        if (file == null) return false;
        switch (file.getFileType()) {
            case HTML:
            case CSS:
            case SCSS:
            case JAVASCRIPT:
            case TYPESCRIPT:
            case JSON:
                return true;
            default:
                return false;
        }
    }

    private void updateActiveViewer(EditorFile activeFile, boolean isPreview) {
        if (activeViewer != null) {
            activeViewer.onPause();
        }

        activeViewer = viewerManager.getOrCreateViewer(this, activeFile, isPreview);
        View viewerView = activeViewer.getView(this, binding.viewerContainer);

        // Ensure the view is added to the container
        if (viewerView.getParent() == null) {
            binding.viewerContainer.addView(viewerView);
        }

        // Hide all other views, show this one
        for (int i = 0; i < binding.viewerContainer.getChildCount(); i++) {
            View child = binding.viewerContainer.getChildAt(i);
            child.setVisibility(child == viewerView ? View.VISIBLE : View.GONE);
        }

        activeViewer.bindFile(activeFile, viewModel);
        activeViewer.onResume();

        applyReadOnlyState();

        // Update toggle button UI
        FileType type = activeFile.getFileType();
        if (type == FileType.SVG || type == FileType.CSV || type == FileType.MARKDOWN) {
            binding.ivTogglePreview.setVisibility(View.VISIBLE);
            if (isPreview) {
                binding.ivTogglePreview.setImageResource(R.drawable.ic_code);
            } else {
                int iconRes = R.drawable.ic_image_icon;
                if (type == FileType.CSV) iconRes = R.drawable.ic_csv_icon;
                else if (type == FileType.MARKDOWN) iconRes = R.drawable.ic_md_icon;
                binding.ivTogglePreview.setImageResource(iconRes);
            }
        } else {
            binding.ivTogglePreview.setVisibility(View.GONE);
        }

        if (binding.findReplaceBar.getVisibility() == View.VISIBLE) {
            binding.findReplaceBar.slideUp();
        }
    }

    private void handleTabClose(int index) {
        List<EditorFile> files = viewModel.getOpenFiles().getValue();
        if (files == null || index < 0 || index >= files.size()) return;

        EditorFile file = files.get(index);
        AppSettings settings = viewModel.getSettingsLiveData().getValue();
        boolean confirm = settings == null || settings.confirmOnTabClose;

        Runnable doClose = () -> {
            viewerManager.destroyViewer(file.getId());
            viewModel.closeFile(index);
        };

        if (file.isDirty() && confirm) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.vcode_unsaved_changes_2)
                    .setMessage("Save changes to " + file.getFileName() + " before closing?")
                    .setPositiveButton(R.string.vcode_save_close, (d, w) -> viewModel.saveFile(index, doClose))
                    .setNegativeButton(R.string.vcode_discard_2, (d, w) -> doClose.run())
                    .setNeutralButton(R.string.vcode_action_cancel, null)
                    .show();
        } else {
            doClose.run();
        }
    }

    private void showOverflowMenu() {
        EditorMenuHelper.MenuCallbacks callbacks = new EditorMenuHelper.MenuCallbacks() {
            @Override
            public void onShowFindReplace() {
                showFindReplaceBar();
            }

            @Override
            public void onToggleReadOnly() {
                isReadOnly = !isReadOnly;
                applyReadOnlyState();
            }

            @Override
            public void onFormatCode() {
                formatCurrentFile();
            }

            @Override
            public void onGoToLine() {
                showGoToLineDialog();
            }

            @Override
            public void onShowSnippetManager() {
                showSnippetManager();
            }

            @Override
            public void onNavigateWithUnsavedCheck(Runnable action) {
                navigateWithUnsavedCheck(action);
            }

            @Override
            public boolean isReadOnly() {
                return isReadOnly;
            }

            @Override
            public com.cocode.vcode.ide.core.lsp.LspEditorBridge getActiveLspBridge() {
                if (activeViewer instanceof com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) {
                    return ((com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) activeViewer).getLspBridge();
                }
                return null;
            }

            @Override
            public void openFileAtLine(java.io.File file, int line) {
                if (file == null || !file.exists()) {
                    android.widget.Toast.makeText(EditorActivity.this,
                            R.string.vcode_lsp_no_definition_found, android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                // Open (or switch to) the target file, then jump to the given line
                viewModel.openFile(file);
                // After the tab is active, scroll to the requested line
                binding.viewerContainer.postDelayed(() -> {
                    com.cocode.vcode.ide.views.CodeEditText editor = getActiveCodeEditor();
                    if (editor != null && line > 1) {
                        editor.goToLine(line);
                    }
                }, 300);

            }
        };

        String projectName = getIntent().getStringExtra(EXTRA_PROJECT_NAME);
        EditorMenuHelper.showOverflowMenu(this, viewModel, getSupportFragmentManager(), projectName, callbacks);
    }

    private void applyReadOnlyState() {
        CodeEditText codeEditText = getActiveCodeEditor();
        if (codeEditText != null) {
            codeEditText.setFocusable(!isReadOnly);
            codeEditText.setFocusableInTouchMode(!isReadOnly);
            codeEditText.setCursorVisible(!isReadOnly);
        }
    }

    private void showFindReplaceBar() {
        if (binding.findReplaceBar.getVisibility() == View.VISIBLE) {
            binding.findReplaceBar.slideUp();
        } else {
            CodeEditText codeEditText = getActiveCodeEditor();
            if (codeEditText != null) binding.findReplaceBar.setEditor(codeEditText);
            binding.findReplaceBar.slideDown();
        }
    }

    private void showSnippetManager() {
        SnippetsBottomSheet snippetsSheet = new SnippetsBottomSheet();
        snippetsSheet.setListener(snippet -> {
            CodeEditText codeEditText = getActiveCodeEditor();
            if (codeEditText != null && snippet.getContent() != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() ->
                        codeEditText.insertSnippet(snippet.getContent()), 250);
            }
        });
        snippetsSheet.show(getSupportFragmentManager(), "Snippets");
    }

    private void saveCurrentEditorState() {
        CodeEditText codeEditText = getActiveCodeEditor();
        if (codeEditText != null && codeEditText.getTag() != null) {
            List<EditorFile> files = viewModel.getOpenFiles().getValue();
            Integer activeIndex = viewModel.getActiveTabIndex().getValue();
            if (files != null && activeIndex != null && activeIndex >= 0 && activeIndex < files.size()) {
                EditorFile activeFile = files.get(activeIndex);
                if (!activeFile.isBinaryAsset()) {
                    viewModel.updateActiveFileState(codeEditText.getSelectionStart(), codeEditText.getScrollY());
                }
            }
        }
    }

    @Override
    public void onFileSelected(FileNode fileNode) {
        binding.drawerLayout.closeDrawer(GravityCompat.START);
        saveCurrentEditorState();
        viewModel.openFile(fileNode.getFile());
    }

    private void showGoToLineDialog() {
        CodeEditText codeEditText = getActiveCodeEditor();
        if (codeEditText == null || codeEditText.getText() == null) return;

        int maxLines = codeEditText.getLineCount();
        if (maxLines == 0) maxLines = codeEditText.getText().toString().split("\n", -1).length;

        GoToLineBottomSheet sheet = new GoToLineBottomSheet();
        sheet.setMaxLines(maxLines);
        sheet.setListener(this::jumpToLine);
        sheet.show(getSupportFragmentManager(), "GoToLineSheet");
    }

    public void jumpToLine(int line) {
        CodeEditText codeEditText = getActiveCodeEditor();
        if (codeEditText == null) return;
        // goToLine() handles clamping, cursor update, and scroll — O(log n) via Content.positionAt()
        codeEditText.goToLine(line);
    }

    private void formatCurrentFile() {
        CodeEditText codeEditText = getActiveCodeEditor();
        List<EditorFile> files = viewModel.getOpenFiles().getValue();
        Integer activeIndex = viewModel.getActiveTabIndex().getValue();

        if (files == null || activeIndex == null || activeIndex < 0 || activeIndex >= files.size() || codeEditText == null) {
            Toast.makeText(this, R.string.vcode_no_file_open_to_format, Toast.LENGTH_SHORT).show();
            return;
        }

        EditorFile activeFile = files.get(activeIndex);
        if (activeFile.isBinaryAsset()) {
            Toast.makeText(this, R.string.vcode_cannot_format_a_media_asset, Toast.LENGTH_SHORT).show();
            return;
        }

        String rawCode = java.util.Objects.requireNonNull(codeEditText.getText()).toString();
        FileType lang = activeFile.getFileType();
        int originalCursor = codeEditText.getSelectionStart();

        Toast.makeText(this, R.string.vcode_formatting, Toast.LENGTH_SHORT).show();
        ExecutorProvider.getInstance().runOnIo(() -> {
            String formattedCode = CodeFormatter.format(rawCode, lang);
            ExecutorProvider.getInstance().runOnMain(() -> {
                if (!rawCode.equals(formattedCode)) {
                    activeFile.setContent(formattedCode);
                    if (!activeFile.isDirty()) {
                        activeFile.setDirty(true);
                        viewModel.notifyFileDirtyStatusChanged();
                    }
                    viewModel.triggerAutoSave();
                    
                    codeEditText.setText(formattedCode);
                    // Restore cursor to its pre-format position so Android's cursor-visibility
                    // logic scrolls back to the right place instead of jumping to the top.
                    int safeCursor = Math.min(originalCursor, formattedCode.length());
                    codeEditText.setSelection(safeCursor);
                    Toast.makeText(this, R.string.vcode_formatted_successfully, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.vcode_code_is_already_formatted, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void navigateWithUnsavedCheck(Runnable navigateAction) {
        if (viewModel.hasUnsavedFiles()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.vcode_unsaved_changes)
                    .setMessage(R.string.vcode_you_have_unsaved_files_save)
                    .setPositiveButton(R.string.vcode_save_all, (d, w) -> viewModel.saveAll(navigateAction))
                    .setNegativeButton(R.string.vcode_discard, (d, w) -> navigateAction.run())
                    .setNeutralButton(R.string.vcode_action_cancel, null)
                    .show();
        } else {
            navigateAction.run();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveCurrentEditorState();
        if (viewModel != null) viewModel.onStopSync();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.reloadSettings();
            viewModel.refreshFileTree();
            viewModel.validateOpenFilesWithDisk();
        }
        if (activeViewer != null) {
            activeViewer.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (localWebServer != null) localWebServer.stop();
        if (viewerManager != null) viewerManager.destroyAll();
    }

    @Override
    public void reportProblems(File file, List<Problem> problems) {
        if (viewModel != null) {
            viewModel.reportProblems(file, problems);
        }
    }
}