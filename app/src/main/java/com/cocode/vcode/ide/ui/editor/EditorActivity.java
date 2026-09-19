package com.cocode.vcode.ide.ui.editor;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.databinding.DialogUnsavedChangesBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;

import com.cocode.vcode.ide.core.keybinding.KeyCommand;
import com.cocode.vcode.ide.core.keybinding.KeybindingManager;
import com.cocode.vcode.ide.ui.git.GitActivity;
import com.cocode.vcode.ide.ui.settings.SettingsActivity;
import com.cocode.vcode.ide.ui.sheets.editor.KeyboardShortcutsBottomSheet;

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
import com.cocode.vcode.ide.ui.editor.helper.ServerNotificationHelper;
import com.cocode.vcode.ide.ui.editor.viewer.IEditorCallback;
import com.cocode.vcode.ide.ui.editor.viewer.IFileViewer;
import com.cocode.vcode.ide.ui.editor.viewer.ViewerManager;
import com.cocode.vcode.ide.ui.filetree.FileTreeFragment;
import com.cocode.vcode.ide.ui.sheets.editor.GoToLineBottomSheet;
import com.cocode.vcode.ide.ui.sheets.editor.ProblemsBottomSheet;
import com.cocode.vcode.ide.ui.sheets.editor.SnippetsBottomSheet;
import com.cocode.vcode.ide.ui.sheets.files.NewFileBottomSheet;
import com.cocode.vcode.ide.ui.sheets.files.NewFolderBottomSheet;
import com.cocode.vcode.ide.ui.sheets.files.ProjectSearchBottomSheet;
import com.cocode.vcode.ide.utils.CodeFormatter;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.LocalWebServer;
import com.cocode.vcode.ide.utils.ProjectFileRecovery;
import com.cocode.vcode.ide.utils.UiUtils;
import com.cocode.vcode.ide.views.CodeEditText;

import java.io.File;
import java.util.List;

/**
 * Main IDE editor activity hosting the code editor, file tabs, drawer, and action bars.
 */
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
        binding.drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                Fragment fragment = getSupportFragmentManager().findFragmentById(binding.drawerContainer.getId());
                if (fragment instanceof FileTreeFragment) {
                    ((FileTreeFragment) fragment).clearClipboardState();
                }
            }
        });

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

        binding.tabBar.setOnTabClickListener(this::switchToTab);

        binding.tabBar.setOnTabCloseListener(index -> {
            saveCurrentEditorState();
            handleTabClose(index);
        });
    }

    private void switchToTab(int index) {
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            currentFocus.clearFocus();
        }

        CodeEditText activeEditor = getActiveCodeEditor();
        if (activeEditor != null) activeEditor.dismissSignatureHint();

        if (activeViewer instanceof com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) {
            ((com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) activeViewer).flushContentToViewModel();
            viewModel.triggerAutoSave();
        }
        viewModel.syncAllOpenFilesToIndex();
        viewModel.setActiveTab(index);
        binding.viewerContainer.post(() -> {
            CodeEditText newEditor = getActiveCodeEditor();
            if (newEditor != null) {
                newEditor.requestFocus();
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START);
                    return true;
                }
                if (binding.findReplaceBar.getVisibility() == View.VISIBLE) {
                    binding.findReplaceBar.slideUp();
                    return true;
                }
            }

            KeyCommand cmd = KeybindingManager.getInstance(this).findCommand(event);
            if (cmd != null && handleGlobalCommand(cmd)) {
                return true;
            }

            // Route editor-scoped shortcuts directly to the active editor
            if (activeViewer instanceof com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) {
                boolean findBarFocused = binding.findReplaceBar.getVisibility() == View.VISIBLE
                        && binding.findReplaceBar.hasFocus();
                if (!findBarFocused) {
                    CodeEditText editor = ((com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) activeViewer).getCodeEditor();
                    if (editor != null) {
                        KeyCommand editorCmd = KeybindingManager.getInstance(this).findCommand(event, KeyCommand.Scope.EDITOR);
                        if (editorCmd != null && editorCmd.getScope() == KeyCommand.Scope.EDITOR
                                && editor.executeEditorCommand(editorCmd)) {
                            return true;
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean handleGlobalCommand(@NonNull KeyCommand cmd) {
        switch (cmd) {
            case SAVE_ALL: {
                if (activeViewer instanceof com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) {
                    ((com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) activeViewer).flushContentToViewModel();
                }
                viewModel.saveAll();
                Toast.makeText(this, R.string.vcode_all_files_saved, Toast.LENGTH_SHORT).show();
                return true;
            }
            case CLOSE_TAB: {
                Integer activeIndex = viewModel.getActiveTabIndex().getValue();
                if (activeIndex != null && activeIndex >= 0) {
                    saveCurrentEditorState();
                    handleTabClose(activeIndex);
                }
                return true;
            }
            case NEXT_TAB: {
                List<EditorFile> files = viewModel.getOpenFiles().getValue();
                Integer activeIndex = viewModel.getActiveTabIndex().getValue();
                if (files != null && files.size() > 1 && activeIndex != null) {
                    int next = (activeIndex + 1) % files.size();
                    switchToTab(next);
                }
                return true;
            }
            case PREV_TAB: {
                List<EditorFile> files = viewModel.getOpenFiles().getValue();
                Integer activeIndex = viewModel.getActiveTabIndex().getValue();
                if (files != null && files.size() > 1 && activeIndex != null) {
                    int prev = (activeIndex - 1 + files.size()) % files.size();
                    switchToTab(prev);
                }
                return true;
            }
            case NEW_FILE: {
                openNewFileSheetForRoot();
                return true;
            }
            case NEW_FOLDER: {
                openNewFolderSheetForRoot();
                return true;
            }
            case TOGGLE_SIDEBAR: {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    UiUtils.hideKeyboard(this);
                    CodeEditText codeEditText = getActiveCodeEditor();
                    if (codeEditText != null) codeEditText.clearFocus();
                    binding.drawerLayout.openDrawer(GravityCompat.START);
                }
                return true;
            }
            case FIND: {
                showFindReplaceBar();
                return true;
            }
            case REPLACE: {
                CodeEditText codeEditText = getActiveCodeEditor();
                if (codeEditText != null) binding.findReplaceBar.setEditor(codeEditText);
                binding.findReplaceBar.focusReplace();
                return true;
            }
            case GO_TO_LINE: {
                showGoToLineDialog();
                return true;
            }
            case FORMAT_DOCUMENT: {
                formatCurrentFile();
                return true;
            }
            case EXTRACT_CSS: {
                extractTagsFromCurrentFile(com.cocode.vcode.ide.utils.TagExtractor.Type.STYLE);
                return true;
            }
            case EXTRACT_JS: {
                extractTagsFromCurrentFile(com.cocode.vcode.ide.utils.TagExtractor.Type.SCRIPT);
                return true;
            }
            case SHOW_PROBLEMS: {
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
                return true;
            }
            case SHOW_SNIPPETS: {
                showSnippetManager();
                return true;
            }
            case RUN_PREVIEW: {
                handleRunAction();
                return true;
            }
            case OPEN_SETTINGS: {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            case OPEN_GIT: {
                if (viewModel.getProjectRoot() != null) {
                    Intent navToGit = new Intent(this, GitActivity.class);
                    navToGit.putExtra("project_path", viewModel.getProjectRoot().getAbsolutePath());
                    navToGit.putExtra("project_name", getIntent().getStringExtra(EXTRA_PROJECT_NAME));
                    AppSettings settings = viewModel.getSettingsLiveData().getValue();
                    if (settings != null && settings.gitDefaultBranch != null) {
                        navToGit.putExtra("default_branch", settings.gitDefaultBranch);
                    }
                    startActivity(navToGit);
                } else {
                    Toast.makeText(this, R.string.vcode_error_project_directory_not_loaded, Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            case SHOW_SHORTCUTS: {
                KeyboardShortcutsBottomSheet.show(getSupportFragmentManager());
                return true;
            }
            case TOGGLE_READ_ONLY: {
                toggleReadOnly();
                return true;
            }
            default:
                return false;
        }
    }

    private CodeEditText getActiveCodeEditor() {
        if (activeViewer != null) {
            return activeViewer.getCodeEditor();
        }
        return null;
    }

    private void openNewFileSheetForRoot() {
        File root = viewModel.getProjectRoot();
        if (root != null) {
            NewFileBottomSheet sheet = NewFileBottomSheet.newInstance();
            sheet.setListener((fileName, initialContent) -> viewModel.createFile(root, fileName, initialContent));
            sheet.show(getSupportFragmentManager(), "NewFileBottomSheet");
        }
    }

    private void openNewFolderSheetForRoot() {
        File root = viewModel.getProjectRoot();
        if (root != null) {
            NewFolderBottomSheet sheet = NewFolderBottomSheet.newInstance(root);
            sheet.show(getSupportFragmentManager(), "NewFolderBottomSheet");
        }
    }

    private void handleRunAction() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

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
            if (!settings.enableDiagnostics) {
                binding.diagnosticBar.setVisibility(View.GONE);
            }
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

                if (activeViewer instanceof com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer && activeIndex >= 0 && activeIndex < files.size()) {
                    EditorFile currentActiveFile = files.get(activeIndex);
                    com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer cfv = (com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) activeViewer;
                    if (cfv.getCodeEditor() != null) {
                        File editorFile = cfv.getCodeEditor().getCurrentFile();
                        boolean fileChanged = (editorFile == null && currentActiveFile.getFile() != null)
                                || (editorFile != null && !editorFile.equals(currentActiveFile.getFile()));
                        if (fileChanged) {
                            cfv.getCodeEditor().setCurrentFile(currentActiveFile.getFile());
                            cfv.getCodeEditor().setFileType(currentActiveFile.getFileType());
                            if (cfv.getLspBridge() != null && currentActiveFile.getFile() != null) {
                                cfv.getLspBridge().setFile(currentActiveFile.getFile());
                            }
                        }
                    }
                }
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

        viewModel.getRefactoredFiles().observe(this, modifiedFiles -> {
            if (modifiedFiles == null || modifiedFiles.isEmpty()) return;
            if (activeViewer instanceof com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) {
                com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer cfv = (com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) activeViewer;
                if (cfv.getCodeEditor() != null) {
                    File currentFile = cfv.getCodeEditor().getCurrentFile();
                    if (currentFile != null) {
                        for (File f : modifiedFiles) {
                            if (f != null && f.equals(currentFile)) {
                                Integer activeIndex = viewModel.getActiveTabIndex().getValue();
                                List<EditorFile> files = viewModel.getOpenFiles().getValue();
                                if (activeIndex != null && activeIndex >= 0 && files != null && activeIndex < files.size()) {
                                    EditorFile ef = files.get(activeIndex);
                                    if (ef.getContent() != null) {
                                        cfv.bindFile(ef, viewModel);
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
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
                AppSettings currentSettings = viewModel.getSettingsLiveData().getValue();
                boolean diagnosticsAllowed = currentSettings == null || currentSettings.enableDiagnostics;
                boolean showDiagnostic = diagnosticsAllowed && isFileDiagnosable(activeFile) && !isEmpty;
                binding.diagnosticBar.setVisibility(showDiagnostic ? View.VISIBLE : View.GONE);
            }
            updateToolbarVisibility();
        });

        viewModel.getActiveFileDiagnostics().observe(this, counts -> {
            AppSettings currentSettings = viewModel.getSettingsLiveData().getValue();
            if (currentSettings != null && !currentSettings.enableDiagnostics) {
                binding.diagnosticBar.setVisibility(View.GONE);
                return;
            }
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
            case MARKDOWN:
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
            // Revert LSP index to disk state since the live editor buffer is gone
            if (!file.isBinaryAsset() && file.getFile() != null) {
                com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().revertToDisk(file.getFile());
            }
        };

        if (file.isDirty() && confirm) {
            showSingleFileUnsavedDialog(file, index, doClose);
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
                toggleReadOnly();
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
            public void onExtractTags(com.cocode.vcode.ide.utils.TagExtractor.Type type) {
                extractTagsFromCurrentFile(type);
            }

            @Override
            public void onNavigateWithUnsavedCheck(Runnable action) {
                navigateWithUnsavedCheck(action);
            }

            @Override
            public boolean isReadOnly() {
                return isReadOnly;
            }
        };

        String projectName = getIntent().getStringExtra(EXTRA_PROJECT_NAME);
        EditorMenuHelper.showOverflowMenu(this, viewModel, getSupportFragmentManager(), projectName, callbacks);
    }

    private void extractTagsFromCurrentFile(com.cocode.vcode.ide.utils.TagExtractor.Type type) {
        List<EditorFile> files = viewModel.getOpenFiles().getValue();
        Integer activeIndex = viewModel.getActiveTabIndex().getValue();
        CodeEditText editor = getActiveCodeEditor();

        if (files == null || activeIndex == null || activeIndex < 0 || activeIndex >= files.size() || editor == null) {
            android.widget.Toast.makeText(this, R.string.vcode_no_file_open_to_format, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        EditorFile activeFile = files.get(activeIndex);
        if (activeFile.getFileType() != com.cocode.vcode.ide.core.model.FileType.HTML) {
            android.widget.Toast.makeText(this, R.string.vcode_extract_tags_html_only, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        com.cocode.vcode.ide.ui.sheets.editor.ExtractTagsBottomSheet.show(getSupportFragmentManager(), type, (filename, extractType) -> {
            CodeEditText codeEditor = getActiveCodeEditor();
            if (codeEditor != null) {
                String text = codeEditor.getText().toString();
                com.cocode.vcode.ide.utils.TagExtractor.Result result = com.cocode.vcode.ide.utils.TagExtractor.extract(text, extractType, filename);
                if (result.success) {
                    java.io.File currentFile = activeFile.getFile();
                    java.io.File newFile = new java.io.File(currentFile.getParentFile(), filename);
                    try {
                        com.cocode.vcode.ide.core.model.FileType targetLang = (extractType == com.cocode.vcode.ide.utils.TagExtractor.Type.STYLE) ? com.cocode.vcode.ide.core.model.FileType.CSS : com.cocode.vcode.ide.core.model.FileType.JAVASCRIPT;
                        String formatted = com.cocode.vcode.ide.utils.CodeFormatter.format(result.extractedContent, targetLang);
                        com.cocode.vcode.ide.utils.FileUtils.writeFile(newFile, formatted);
                        codeEditor.replaceRange(0, codeEditor.length(), result.modifiedHtml);
                        viewModel.saveAll();
                        viewModel.refreshFileTree();
                        android.widget.Toast.makeText(EditorActivity.this, getString(R.string.vcode_extracted_to_file, filename), android.widget.Toast.LENGTH_SHORT).show();
                    } catch (java.io.IOException e) {
                        android.widget.Toast.makeText(EditorActivity.this, getString(R.string.vcode_failed_to_write_file, e.getMessage()), android.widget.Toast.LENGTH_SHORT).show();
                    }
                } else {
                    android.widget.Toast.makeText(EditorActivity.this, result.errorMessage, android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void toggleReadOnly() {
        CodeEditText codeEditText = getActiveCodeEditor();
        if (codeEditText == null) {
            return;
        }
        isReadOnly = !isReadOnly;
        applyReadOnlyState();
        int msgRes = isReadOnly ? R.string.vcode_read_only_enabled : R.string.vcode_read_only_disabled;
        Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show();
    }

    private void applyReadOnlyState() {
        CodeEditText codeEditText = getActiveCodeEditor();
        if (codeEditText != null) {
            codeEditText.setFocusable(!isReadOnly);
            codeEditText.setFocusableInTouchMode(!isReadOnly);
            codeEditText.setCursorVisible(!isReadOnly);
            if (!isReadOnly) {
                codeEditText.requestFocus();
            } else {
                codeEditText.clearFocus();
                UiUtils.hideKeyboard(this);
            }
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
        if (activeViewer instanceof com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) {
            ((com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) activeViewer).flushContentToViewModel();
        }
        viewModel.syncAllOpenFilesToIndex();
        saveCurrentEditorState();
        viewModel.openFile(fileNode.getFile());
    }

    @Override
    public void onFindInFile(FileNode fileNode) {
        binding.drawerLayout.closeDrawer(GravityCompat.START);
        if (activeViewer instanceof com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) {
            ((com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) activeViewer).flushContentToViewModel();
        }
        viewModel.syncAllOpenFilesToIndex();
        saveCurrentEditorState();
        viewModel.openFile(fileNode.getFile());

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            showFindReplaceBar();
        }, 300);
    }

    private void showGoToLineDialog() {
        CodeEditText codeEditText = getActiveCodeEditor();
        if (codeEditText == null || codeEditText.getText() == null) return;

        int maxLines = Math.max(1, codeEditText.getLineCount());

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

        String fileName = activeFile.getFile().getName().toLowerCase();
        if (fileName.endsWith(".min.js") || fileName.endsWith(".min.css")) {
            Toast.makeText(this, R.string.vcode_cannot_format_minified, Toast.LENGTH_SHORT).show();
            return;
        }

        String rawCode = java.util.Objects.requireNonNull(codeEditText.getText()).toString();
        FileType lang = activeFile.getFileType();
        int originalCursor = codeEditText.getSelectionStart();

        java.util.List<com.cocode.vcode.ide.core.model.Problem> bracketProblems = com.cocode.vcode.ide.core.editor.indent.BracketMatcher.findMismatches(activeFile.getFile(), rawCode);
        if (bracketProblems != null && !bracketProblems.isEmpty()) {
            Toast.makeText(this, R.string.vcode_cannot_format_unbalanced_brackets, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, R.string.vcode_formatting, Toast.LENGTH_SHORT).show();
        ExecutorProvider.getInstance().runOnIo(() -> {
            String formattedCode = CodeFormatter.format(rawCode, lang);
            ExecutorProvider.getInstance().runOnMain(() -> {
                if (!rawCode.equals(formattedCode)) {
                    activeFile.setContent(formattedCode);
                    activeFile.setDirty(true);
                    viewModel.notifyFileDirtyStatusChanged();
                    viewModel.triggerAutoSave();

                    codeEditText.formatText(formattedCode);
                    Toast.makeText(this, R.string.vcode_formatted_successfully, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.vcode_code_is_already_formatted, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void navigateWithUnsavedCheck(Runnable navigateAction) {
        if (viewModel.hasUnsavedFiles()) {
            showMultipleFilesUnsavedDialog(navigateAction);
        } else {
            navigateAction.run();
        }
    }

    private void showSingleFileUnsavedDialog(EditorFile file, int index, Runnable doClose) {
        DialogUnsavedChangesBinding dialogBinding = DialogUnsavedChangesBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setCancelable(true)
                .create();

        FontManager fm = FontManager.getInstance();
        dialogBinding.tvDialogTitle.setTypeface(fm.getUiSemiBold(this));
        dialogBinding.tvDialogDesc.setTypeface(fm.getUiMedium(this));
        dialogBinding.btnSave.setTypeface(fm.getUiSemiBold(this));
        dialogBinding.btnDiscard.setTypeface(fm.getUiSemiBold(this));
        dialogBinding.btnCancel.setTypeface(fm.getUiSemiBold(this));

        dialogBinding.tvDialogTitle.setText(R.string.vcode_unsaved_changes_2);
        dialogBinding.tvDialogDesc.setText(getString(R.string.vcode_save_changes_to_file_before_closing, file.getFileName()));
        dialogBinding.btnSave.setText(R.string.vcode_save_close);
        dialogBinding.btnDiscard.setText(R.string.vcode_discard_2);

        dialogBinding.btnSave.setOnClickListener(v -> {
            dialog.dismiss();
            viewModel.saveFile(index, doClose);
        });
        dialogBinding.btnDiscard.setOnClickListener(v -> {
            dialog.dismiss();
            doClose.run();
        });
        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int maxWidth = getResources().getDimensionPixelSize(R.dimen.dialog_max_width);
            int targetWidth = Math.min((int) (screenWidth * 0.92f), maxWidth);
            dialog.getWindow().setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showMultipleFilesUnsavedDialog(Runnable navigateAction) {
        DialogUnsavedChangesBinding dialogBinding = DialogUnsavedChangesBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setCancelable(true)
                .create();

        FontManager fm = FontManager.getInstance();
        dialogBinding.tvDialogTitle.setTypeface(fm.getUiSemiBold(this));
        dialogBinding.tvDialogDesc.setTypeface(fm.getUiMedium(this));
        dialogBinding.btnSave.setTypeface(fm.getUiSemiBold(this));
        dialogBinding.btnDiscard.setTypeface(fm.getUiSemiBold(this));
        dialogBinding.btnCancel.setTypeface(fm.getUiSemiBold(this));

        dialogBinding.tvDialogTitle.setText(R.string.vcode_unsaved_changes);
        dialogBinding.tvDialogDesc.setText(R.string.vcode_you_have_unsaved_files_save);
        dialogBinding.btnSave.setText(R.string.vcode_save_all);
        dialogBinding.btnDiscard.setText(R.string.vcode_discard);

        dialogBinding.btnSave.setOnClickListener(v -> {
            dialog.dismiss();
            viewModel.saveAll(navigateAction);
        });
        dialogBinding.btnDiscard.setOnClickListener(v -> {
            dialog.dismiss();
            navigateAction.run();
        });
        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int maxWidth = getResources().getDimensionPixelSize(R.dimen.dialog_max_width);
            int targetWidth = Math.min((int) (screenWidth * 0.92f), maxWidth);
            dialog.getWindow().setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (activeViewer instanceof com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) {
            ((com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) activeViewer).flushContentToViewModel();
        }
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
        ServerNotificationHelper.cancelServerNotification(this);
        if (viewerManager != null) viewerManager.destroyAll();
    }

    @Override
    public void reportDiagnosticLoading(File file) {
        if (viewModel != null) {
            viewModel.setDiagnosticLoading(file);
        }
    }

    @Override
    public void reportProblems(File file, List<Problem> problems) {
        if (viewModel != null) {
            viewModel.reportProblems(file, problems);
        }
    }

    @Override
    public void navigateToLocation(com.cocode.vcode.ide.core.lsp.LspLocation result) {
        if (result == null) {
            Toast.makeText(this, R.string.vcode_lsp_no_definition_found, Toast.LENGTH_SHORT).show();
            return;
        }
        String path = result.uri;
        if (path.startsWith("file://")) {
            try {
                path = new java.net.URI(result.uri).getPath();
            } catch (Exception e) {
                path = path.substring(7);
            }
        }
        File target = new File(path);
        int line = result.range != null ? result.range.start.line + 1 : 1;

        if (!target.exists()) {
            Toast.makeText(this, R.string.vcode_lsp_no_definition_found, Toast.LENGTH_SHORT).show();
            return;
        }
        viewModel.openFile(target);
        binding.viewerContainer.postDelayed(() -> {
            CodeEditText editor = getActiveCodeEditor();
            if (editor != null && line > 1) {
                editor.goToLine(line);
            }
        }, 300);
    }

    @Override
    public void showReferences(List<com.cocode.vcode.ide.core.lsp.LspLocation> result) {
        if (result == null || result.isEmpty()) {
            Toast.makeText(this, R.string.vcode_no_usages_found, Toast.LENGTH_SHORT).show();
            return;
        }

        CodeEditText editor = getActiveCodeEditor();
        String query = "";
        if (editor != null && editor.getText() != null) {
            query = com.cocode.vcode.ide.core.lsp.SymbolExtractor.extractWord(
                    editor.getText().toString(), editor.getSelectionStart());
        }

        ProjectSearchBottomSheet sheet = new ProjectSearchBottomSheet();
        sheet.setUsages(getString(R.string.vcode_find_usages), query, result);
        sheet.setListener((file, line) -> {
            viewModel.openFile(file);
            binding.viewerContainer.postDelayed(() -> {
                CodeEditText targetEditor = getActiveCodeEditor();
                if (targetEditor != null && line > 0) {
                    targetEditor.goToLine(line);
                }
            }, 300);
        });
        sheet.show(getSupportFragmentManager(), "FindUsages");
    }
}