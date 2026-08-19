package com.cocode.vcode.ide.ui.editor;

import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.data.model.FileNode;
import com.cocode.vcode.ide.data.model.ProjectState;
import com.cocode.vcode.ide.data.model.Result;
import com.cocode.vcode.ide.data.repository.FileRepository;
import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.cocode.vcode.ide.data.repository.ProjectStateRepository;
import com.cocode.vcode.ide.data.repository.SettingsRepository;
import com.cocode.vcode.ide.git.model.FileStatus;
import com.cocode.vcode.ide.ui.editor.helper.EditorGitHelper;
import com.cocode.vcode.ide.ui.editor.helper.ProjectMetaHelper;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * EditorViewModel serves as the centralized state manager for the EditorActivity.
 * it orchestrates file system operations, project state persistence, settings management,
 * and Git status tracking to ensure a reactive and consistent editing experience.
 */
public class EditorViewModel extends ViewModel {

    private final Context appContext;
    private final FileRepository fileRepo;
    private final ProjectStateRepository stateRepo;
    private final SettingsRepository settingsRepo;
    private final ProjectRepository projectRepo;

    // Reactive streams for UI components to observe
    private final MutableLiveData<List<EditorFile>> openFilesLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> activeTabIndexLiveData = new MutableLiveData<>(-1);
    private final MutableLiveData<Result<Boolean>> fileSaveResult = new MutableLiveData<>();
    private final MutableLiveData<ProjectState> projectStateLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<FileNode>> fileTreeLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<AppSettings> settingsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isEditorLoadingLiveData = new MutableLiveData<>(false);

    /**
     * Maps repository-relative file paths to their current Git status (e.g., Modified, Untracked).
     * This is used to provide visual feedback (colored overlays) in the File Tree.
     */
    private final MutableLiveData<Map<String, FileStatus.Type>> gitStatusesLiveData = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<List<Problem>> problemsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final Map<String, List<Problem>> fileProblemsMap = new HashMap<>();
    private final MutableLiveData<int[]> activeFileDiagnostics = new MutableLiveData<>();

    private File projectRoot;
    private String projectId;
    private String projectName;
    private ProjectState currentState;
    /**
     * Background task for periodic automatic saving of all dirty files.
     */
    private final Runnable autoSaveRunnable = this::saveAll;
    /**
     * When true, the default "open project mainFile" step inside restoreTabsFromState is skipped.
     * Set by EditorActivity when the editor is launched from an external file intent so that the
     * externally-requested file (opened after session restore) stays as the active tab.
     */
    private boolean skipDefaultFileOpen = false;

    public EditorViewModel(Context appContext, FileRepository fileRepo, ProjectStateRepository stateRepo, SettingsRepository settingsRepo, ProjectRepository projectRepo) {
        this.appContext = appContext;
        this.fileRepo = fileRepo;
        this.stateRepo = stateRepo;
        this.settingsRepo = settingsRepo;
        this.projectRepo = projectRepo;
        reloadSettings();
    }

    public void setSkipDefaultFileOpen(boolean skip) {
        this.skipDefaultFileOpen = skip;
    }

    // --- Getters for reactive data streams ---
    public LiveData<List<FileNode>> getFileTree() {
        return fileTreeLiveData;
    }

    public File getProjectRoot() {
        return projectRoot;
    }

    public LiveData<List<EditorFile>> getOpenFiles() {
        return openFilesLiveData;
    }

    public LiveData<Integer> getActiveTabIndex() {
        return activeTabIndexLiveData;
    }

    public LiveData<Result<Boolean>> getFileSaveResult() {
        return fileSaveResult;
    }

    public LiveData<ProjectState> getProjectState() {
        return projectStateLiveData;
    }

    public String getProjectName() {
        return projectName;
    }

    public LiveData<List<Problem>> getProblems() {
        return problemsLiveData;
    }

    public LiveData<int[]> getActiveFileDiagnostics() {
        return activeFileDiagnostics;
    }

    public void reportProblems(File file, List<Problem> problems) {
        if (file == null) return;
        String path = file.getAbsolutePath();
        if (problems == null || problems.isEmpty()) {
            fileProblemsMap.remove(path);
        } else {
            fileProblemsMap.put(path, problems);
        }

        List<Problem> allProblems = new ArrayList<>();
        for (List<Problem> list : fileProblemsMap.values()) {
            allProblems.addAll(list);
        }
        problemsLiveData.postValue(allProblems);

        int activeIndex = getActiveTabIndexValue();
        if (activeIndex >= 0 && activeIndex < getOpenFilesList().size()) {
            EditorFile activeFile = getOpenFilesList().get(activeIndex);
            if (activeFile.getFile().getAbsolutePath().equals(path)) {
                recalculateActiveDiagnostics(path);
            }
        }
    }

    private void recalculateActiveDiagnostics(String path) {
        List<Problem> problems = fileProblemsMap.get(path);
        int[] counts = new int[]{0, 0, 0};
        if (problems != null) {
            for (Problem p : problems) {
                if (p.getSeverity() == Problem.Severity.ERROR) {
                    counts[0]++;
                } else if (p.getSeverity() == Problem.Severity.WARNING) {
                    counts[1]++;
                } else {
                    counts[2]++;
                }
            }
        }
        activeFileDiagnostics.postValue(counts);
    }

    public void setDiagnosticLoading() {
        activeFileDiagnostics.postValue(null);
    }

    public LiveData<AppSettings> getSettingsLiveData() {
        return settingsLiveData;
    }

    public LiveData<Map<String, FileStatus.Type>> getGitStatuses() {
        return gitStatusesLiveData;
    }

    public LiveData<Boolean> getIsEditorLoading() {
        return isEditorLoadingLiveData;
    }

    public void setEditorLoading(boolean loading) {
        if (Boolean.TRUE.equals(isEditorLoadingLiveData.getValue()) == loading) return;
        isEditorLoadingLiveData.setValue(loading);
    }

    /**
     * Loads the latest application settings from the repository and updates the LiveData.
     */
    public void reloadSettings() {
        ExecutorProvider.getInstance().runOnIo(() -> {
            AppSettings freshSettings = projectRoot != null ? settingsRepo.loadMergedSettings(projectRoot) : settingsRepo.loadSettings();
            ExecutorProvider.getInstance().runOnMain(() -> settingsLiveData.setValue(freshSettings));
        });
    }

    /**
     * Initializes the ViewModel with project metadata and restores the previous session's state.
     *
     * @param root  The root directory of the project.
     * @param pId   Unique identifier for the project.
     * @param pName Human-friendly name of the project.
     */
    public void initProject(File root, String pId, String pName) {
        if (this.projectRoot != null) return; // Guard against multiple initializations

        this.projectRoot = root;
        this.projectId = pId;
        this.projectName = pName;

        // Cleanup legacy virtual files that may have been written to disk previously
        File legacyApiFile = new File(projectRoot, "vcode_api_tester.api");
        if (legacyApiFile.exists() && legacyApiFile.isFile()) {
            legacyApiFile.delete();
        }

        refreshFileTree();
        isEditorLoadingLiveData.setValue(true);

        // Load project-specific state (open tabs, scroll positions) from the metadata repository
        ExecutorProvider.getInstance().runOnIo(() -> {
            ProjectState state = stateRepo.loadStateSync(projectRoot, projectId);
            ExecutorProvider.getInstance().runOnMain(() -> {
                currentState = state;
                projectStateLiveData.setValue(currentState);
                restoreTabsFromState(currentState);
            });
        });
    }

    /**
     * Restores file tabs based on the persisted project state.
     */
    private void restoreTabsFromState(ProjectState state) {
        List<String> paths = state.getOpenFilePaths();
        if (paths == null || paths.isEmpty()) {
            isEditorLoadingLiveData.setValue(false);
            if (!skipDefaultFileOpen) {
                ExecutorProvider.getInstance().runOnIo(() -> {
                    try {
                        File metaFile = new File(new File(new File(projectRoot, ProjectRepository.VCODE_DIR), ProjectRepository.META_DIR), ProjectRepository.PROJECT_FILE);
                        if (metaFile.exists()) {
                            String metaContent = FileUtils.readFile(metaFile);
                            org.json.JSONObject metaJson = new org.json.JSONObject(metaContent);
                            String mainFileName = metaJson.optString("mainFile", "index.html");
                            File mainFile = new File(projectRoot, mainFileName);
                            if (mainFile.exists()) {
                                ExecutorProvider.getInstance().runOnMain(() -> openFile(mainFile));
                            }
                        }
                    } catch (Exception ignored) {
                    }
                });
            }
            return;
        }

        ExecutorProvider.getInstance().runOnIo(() -> {
            List<EditorFile> restoredFiles = new ArrayList<>();
            for (String relativePath : paths) {
                File file = new File(projectRoot, relativePath);
                FileType fileType = FileType.fromExtension(com.cocode.vcode.ide.utils.FileUtils.getExtension(file.getName()));
                boolean isVirtual = (state.getVirtualFiles() != null && state.getVirtualFiles().containsKey(relativePath)) || fileType == FileType.API_TESTER;

                if (isVirtual || (file.exists() && file.isFile())) {
                    try {
                        EditorFile ef = new EditorFile(UUID.randomUUID().toString(), file, "", fileType);
                        ef.setCursorPosition(state.getCursorFor(relativePath));
                        ef.setScrollY(state.getScrollFor(relativePath));
                        if (isVirtual) ef.setVirtual(true);
                        ef.setContentLoaded(false);
                        restoredFiles.add(ef);
                    } catch (Exception ignored) {
                    }
                }
            }

            int targetTab = state.getActiveTabIndex();
            if (targetTab < 0 || targetTab >= restoredFiles.size()) {
                targetTab = restoredFiles.isEmpty() ? -1 : 0;
            }

            if (targetTab >= 0) {
                EditorFile active = restoredFiles.get(targetTab);
                if (active.isVirtual()) {
                    String relativePath = active.getRelativePath(projectRoot);
                    String content = state.getVirtualFile(relativePath);
                    active.setContent(content != null ? content : "");
                    active.markSaved();
                    active.setContentLoaded(true);
                } else if (!active.isBinaryAsset()) {
                    try {
                        String content = FileUtils.readFile(active.getFile());
                        active.setContent(content);
                        active.markSaved();
                        // Only mark loaded on success — if this throws, contentLoaded stays
                        // false so CodeFileViewer.bindFile() will retry the read.
                        active.setContentLoaded(true);
                    } catch (Exception e) {
                        // Leave contentLoaded = false intentionally. bindFile will detect
                        // the unloaded state and load content asynchronously as a fallback.
                        active.setContentLoaded(false);
                    }
                } else {
                    // Binary assets don't have text content — mark loaded so bindFile
                    // doesn't try to read them as text.
                    active.setContentLoaded(true);
                }
            }

            final int finalTargetTab = targetTab;
            ExecutorProvider.getInstance().runOnMain(() -> {
                openFilesLiveData.setValue(restoredFiles);
                activeTabIndexLiveData.setValue(finalTargetTab);
                isEditorLoadingLiveData.setValue(false);

                loadRemainingTabsAsync(restoredFiles);
            });
        });
    }

    private void loadRemainingTabsAsync(List<EditorFile> files) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            boolean updated = false;
            Integer activeIdx = activeTabIndexLiveData.getValue();
            for (int i = 0; i < files.size(); i++) {
                EditorFile ef = files.get(i);
                // Skip the active tab — it was already loaded synchronously in restoreTabsFromState.
                if (activeIdx != null && i == activeIdx) continue;
                if (!ef.isContentLoaded()) {
                    if (ef.isVirtual()) {
                        String relativePath = ef.getRelativePath(projectRoot);
                        String content = currentState != null ? currentState.getVirtualFile(relativePath) : null;
                        ef.setContent(content != null ? content : "");
                        ef.markSaved();
                    } else if (!ef.isBinaryAsset()) {
                        try {
                            String content = FileUtils.readFile(ef.getFile());
                            ef.setContent(content);
                            ef.markSaved();
                        } catch (Exception ignored) {
                        }
                    }
                    ef.setContentLoaded(true);
                    updated = true;
                }
            }
            if (updated) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    List<EditorFile> currentDocs = getOpenFilesList();
                    if (!currentDocs.isEmpty()) {
                        openFilesLiveData.setValue(new ArrayList<>(currentDocs));
                    }
                });
            }
        });
    }

    /**
     * Rebuilds the file tree representation based on the current disk state.
     */
    public void refreshFileTree() {
        if (projectRoot == null) return;
        ExecutorProvider.getInstance().runOnIo(() -> {
            List<FileNode> nodes = FileUtils.buildFileTree(projectRoot);
            ExecutorProvider.getInstance().runOnMain(() -> {
                fileTreeLiveData.setValue(nodes);
                // Trigger a refresh of Git statuses to sync with the new tree
                refreshGitStatuses();
            });
        });
    }

    /**
     * Analyzes the project's Git repository to identify modified, untracked, or staged files.
     * Results are mapped by relative path for easy lookup by the UI.
     */
    public void refreshGitStatuses() {
        EditorGitHelper.refreshGitStatuses(projectRoot, gitStatusesLiveData);
    }

    /**
     * Synchronizes open file content with their respective files on disk.
     * This handles scenarios where files were modified or deleted by external processes.
     */
    public void validateOpenFilesWithDisk() {
        if (projectRoot == null) return;

        ExecutorProvider.getInstance().runOnIo(() -> {
            List<EditorFile> currentDocs = getOpenFilesList();
            if (currentDocs.isEmpty()) return;

            java.util.Set<String> missingPaths = new java.util.HashSet<>();
            java.util.Map<String, String> updatedContent = new java.util.HashMap<>();

            for (EditorFile doc : currentDocs) {
                if (doc.isVirtual()) continue;

                File fileOnDisk = doc.getFile();
                if (!fileOnDisk.exists()) {
                    missingPaths.add(fileOnDisk.getAbsolutePath());
                } else if (!doc.isBinaryAsset()) {
                    try {
                        String diskContent = com.cocode.vcode.ide.utils.FileUtils.readFile(fileOnDisk);
                        if (!diskContent.equals(doc.getContent())) {
                            updatedContent.put(fileOnDisk.getAbsolutePath(), diskContent);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            if (!missingPaths.isEmpty() || !updatedContent.isEmpty()) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    List<EditorFile> latestDocs = new java.util.ArrayList<>(getOpenFilesList());
                    boolean actuallyAltered = false;
                    int activeIndex = getActiveTabIndexValue();

                    java.util.Iterator<EditorFile> iterator = latestDocs.iterator();
                    int i = 0;
                    while (iterator.hasNext()) {
                        EditorFile doc = iterator.next();
                        String path = doc.getFile().getAbsolutePath();
                        if (missingPaths.contains(path)) {
                            iterator.remove();
                            actuallyAltered = true;
                            if (i <= activeIndex && activeIndex > 0) activeIndex--;
                        } else {
                            if (updatedContent.containsKey(path)) {
                                doc.setContent(updatedContent.get(path));
                                doc.markSaved();
                                actuallyAltered = true;
                            }
                            i++;
                        }
                    }

                    if (actuallyAltered) {
                        openFilesLiveData.setValue(latestDocs);
                        activeTabIndexLiveData.setValue(latestDocs.isEmpty() ? -1 : Math.min(activeIndex, latestDocs.size() - 1));
                        updateCurrentStateObject();
                        persistStateAsync();
                    }
                });
            }
        });
    }

    /**
     * Creates a new file on disk and refreshes the tree.
     */
    private String getMainFileFromMeta() {
        return ProjectMetaHelper.getMainFileFromMeta(projectRoot);
    }

    private void updateMainFileInMeta(String newMainFile) {
        ProjectMetaHelper.updateMainFileInMeta(projectRoot, newMainFile);
    }

    public void createFile(File parentDir, String name, String content) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                File newFile = FileUtils.createFile(parentDir, name);
                FileUtils.writeFile(newFile, content);

                String relPath = getRelativePath(newFile);
                if (relPath.endsWith(".html")) {
                    String currentMain = getMainFileFromMeta();
                    if (currentMain.isEmpty()) {
                        updateMainFileInMeta(relPath);
                    }
                }

                refreshFileTree();
                projectRepo.touchProjectById(projectId);
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Creates a new directory on disk and refreshes the tree.
     */
    public void createDirectory(File parentDir, String name) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                FileUtils.createFolder(parentDir, name);
                refreshFileTree();
                projectRepo.touchProjectById(projectId);
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Deletes a file or directory recursively and refreshes the tree.
     */
    public void deleteNode(File file) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            String deletedPath = file.getAbsolutePath();
            String relPath = getRelativePath(file);

            FileUtils.deleteRecursive(file);

            ExecutorProvider.getInstance().runOnMain(() -> {
                List<EditorFile> openFiles = getOpenFilesList();
                for (int i = openFiles.size() - 1; i >= 0; i--) {
                    String openFilePath = openFiles.get(i).getFile().getAbsolutePath();
                    if (openFilePath.equals(deletedPath) || openFilePath.startsWith(deletedPath + File.separator)) {
                        closeFile(i);
                    }
                }
            });

            String mainFile = getMainFileFromMeta();
            if (mainFile.equals(relPath) || mainFile.startsWith(relPath + "/")) {
                updateMainFileInMeta("");
            }

            refreshFileTree();
            projectRepo.touchProjectById(projectId);
        });
    }

    /**
     * Renames an existing file or directory and refreshes the tree.
     */
    public void renameNode(File file, String newName) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                FileUtils.renameFile(file, newName);

                File renamedFile = new File(file.getParentFile(), newName);
                List<EditorFile> currentDocs = getOpenFilesList();
                boolean changed = false;

                for (EditorFile doc : currentDocs) {
                    if (doc.getFile().getAbsolutePath().equals(file.getAbsolutePath())) {
                        doc.setFile(renamedFile);
                        // Also update fileType just in case the extension changed
                        doc.setFileType(FileType.fromExtension(FileUtils.getExtension(renamedFile.getName())));
                        changed = true;
                    } else if (doc.getFile().getAbsolutePath().startsWith(file.getAbsolutePath() + "/")) {
                        String relativePath = doc.getFile().getAbsolutePath().substring(file.getAbsolutePath().length());
                        File updatedChildFile = new File(renamedFile.getAbsolutePath() + relativePath);
                        doc.setFile(updatedChildFile);
                        doc.setFileType(FileType.fromExtension(FileUtils.getExtension(updatedChildFile.getName())));
                        changed = true;
                    }
                }

                if (changed) {
                    // Update tabs with a fresh list to trigger RecyclerView/DiffUtil correctly
                    openFilesLiveData.postValue(new java.util.ArrayList<>(currentDocs));
                }

                refreshFileTree();
                projectRepo.touchProjectById(projectId);
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Opens an externally-sourced file. The sourceUriString (content:// URI as a string)
     * is attached to the EditorFile so saves are written back to the original source.
     */
    public void openFile(File file, String sourceUriString) {
        List<EditorFile> currentDocs = getOpenFilesList();
        for (int i = 0; i < currentDocs.size(); i++) {
            if (currentDocs.get(i).getFile().getAbsolutePath().equals(file.getAbsolutePath())) {
                activeTabIndexLiveData.setValue(i);
                return;
            }
        }

        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                FileType fileType = FileType.fromExtension(FileUtils.getExtension(file.getName()));
                String content = "";

                if (fileType == null || fileType.isTextBased()) {
                    if (file.exists()) {
                        content = FileUtils.readFile(file);
                    }
                }

                EditorFile newFile = new EditorFile(UUID.randomUUID().toString(), file, content, fileType);
                newFile.markSaved();
                newFile.setContentLoaded(true);
                if (sourceUriString != null) {
                    newFile.setSourceUriString(sourceUriString);
                }

                ExecutorProvider.getInstance().runOnMain(() -> {
                    List<EditorFile> latestDocs = getOpenFilesList();
                    for (int i = 0; i < latestDocs.size(); i++) {
                        if (latestDocs.get(i).getFile().getAbsolutePath().equals(file.getAbsolutePath())) {
                            activeTabIndexLiveData.setValue(i);
                            return;
                        }
                    }
                    List<EditorFile> updated = new ArrayList<>(latestDocs);
                    updated.add(newFile);
                    openFilesLiveData.setValue(updated);
                    activeTabIndexLiveData.setValue(updated.size() - 1);
                    persistStateAsync();
                });
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Opens a file in the editor, or switches to its tab if it is already open.
     *
     * @param file The file to open.
     */
    public void openFile(File file) {
        List<EditorFile> currentDocs = getOpenFilesList();
        // Check if the file is already loaded in a tab
        for (int i = 0; i < currentDocs.size(); i++) {
            if (currentDocs.get(i).getFile().getAbsolutePath().equals(file.getAbsolutePath())) {
                activeTabIndexLiveData.setValue(i);
                return;
            }
        }

        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                FileType fileType = FileType.fromExtension(FileUtils.getExtension(file.getName()));
                String content = "";

                if (fileType == null || fileType.isTextBased()) {
                    if (file.exists()) {
                        content = FileUtils.readFile(file);
                    } else {
                        content = ""; // Empty content for new/virtual files
                    }
                }

                EditorFile newFile = new EditorFile(UUID.randomUUID().toString(), file, content, fileType);
                newFile.markSaved();
                newFile.setContentLoaded(true);

                // Restore previous cursor/scroll if available in the state object
                if (currentState != null) {
                    String relativePath = getRelativePath(file);
                    newFile.setCursorPosition(currentState.getCursorFor(relativePath));
                    newFile.setScrollY(currentState.getScrollFor(relativePath));
                }

                ExecutorProvider.getInstance().runOnMain(() -> {
                    // Re-check for existing tabs on the main thread to prevent race conditions
                    List<EditorFile> latestDocs = getOpenFilesList();
                    for (int i = 0; i < latestDocs.size(); i++) {
                        if (latestDocs.get(i).getFile().getAbsolutePath().equals(file.getAbsolutePath())) {
                            activeTabIndexLiveData.setValue(i);
                            return;
                        }
                    }

                    List<EditorFile> updated = new ArrayList<>(latestDocs);
                    updated.add(newFile);
                    openFilesLiveData.setValue(updated);
                    activeTabIndexLiveData.setValue(updated.size() - 1);
                    persistStateAsync();
                });
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Opens the integrated API Tester tool as a dedicated virtual file tab.
     */
    public void openApiTester() {
        if (projectRoot == null) return;
        String virtualName = "vcode_api_tester.api";
        File virtualFile = new File(projectRoot, virtualName);

        List<EditorFile> currentDocs = getOpenFilesList();
        for (int i = 0; i < currentDocs.size(); i++) {
            if (currentDocs.get(i).getFile().getAbsolutePath().equals(virtualFile.getAbsolutePath())) {
                activeTabIndexLiveData.setValue(i);
                return;
            }
        }

        EditorFile newFile = new EditorFile(UUID.randomUUID().toString(), virtualFile, "", FileType.API_TESTER);
        newFile.setVirtual(true);
        if (currentState != null) {
            String relativePath = newFile.getRelativePath(projectRoot);
            String content = currentState.getVirtualFile(relativePath);
            newFile.setContent(content != null ? content : "");
            newFile.setCursorPosition(currentState.getCursorFor(relativePath));
            newFile.setScrollY(currentState.getScrollFor(relativePath));
        }
        newFile.markSaved();
        newFile.setContentLoaded(true);

        List<EditorFile> updated = new ArrayList<>(currentDocs);
        updated.add(newFile);
        openFilesLiveData.setValue(updated);
        activeTabIndexLiveData.setValue(updated.size() - 1);
        persistStateAsync();
    }

    /**
     * Closes the file tab at the specified index and manages the active tab transition.
     */
    public void closeFile(int index) {
        List<EditorFile> currentDocs = new ArrayList<>(getOpenFilesList());
        if (index < 0 || index >= currentDocs.size()) return;

        currentDocs.remove(index);
        openFilesLiveData.setValue(currentDocs);

        int currentIndex = activeTabIndexLiveData.getValue() != null ? activeTabIndexLiveData.getValue() : -1;
        if (currentDocs.isEmpty()) {
            activeTabIndexLiveData.setValue(-1);
        } else if (index < currentIndex) {
            activeTabIndexLiveData.setValue(currentIndex - 1);
        } else if (index == currentIndex) {
            activeTabIndexLiveData.setValue(Math.min(currentIndex, currentDocs.size() - 1));
        }

        persistStateAsync();
    }

    /**
     * Closes all currently open file tabs.
     */
    public void closeAll() {
        openFilesLiveData.setValue(new ArrayList<>());
        activeTabIndexLiveData.setValue(-1);
        persistStateAsync();
    }

    /**
     * Sets a specific tab as the active (visible) editor tab.
     */
    public void setActiveTab(int index) {
        List<EditorFile> docs = getOpenFilesList();
        if (index >= 0 && index < docs.size()) {
            EditorFile target = docs.get(index);
            if (!target.isContentLoaded()) {
                isEditorLoadingLiveData.setValue(true);
                ExecutorProvider.getInstance().runOnIo(() -> {
                    if (!target.isContentLoaded()) {
                        if (!target.isBinaryAsset()) {
                            try {
                                String content = FileUtils.readFile(target.getFile());
                                target.setContent(content);
                                target.markSaved();
                            } catch (Exception ignored) {
                            }
                        }
                        target.setContentLoaded(true);
                    }
                    ExecutorProvider.getInstance().runOnMain(() -> {
                        openFilesLiveData.setValue(new ArrayList<>(docs));
                        activeTabIndexLiveData.setValue(index);
                        isEditorLoadingLiveData.setValue(false);
                        persistStateAsync();
                        recalculateActiveDiagnostics(target.getFile().getAbsolutePath());
                    });
                });
            } else {
                activeTabIndexLiveData.setValue(index);
                persistStateAsync();
                recalculateActiveDiagnostics(target.getFile().getAbsolutePath());
            }
        }
    }

    public void notifyFileDirtyStatusChanged() {
        openFilesLiveData.setValue(new ArrayList<>(getOpenFilesList()));
    }

    public void triggerAutoSave() {
        AppSettings settings = settingsLiveData.getValue();

        boolean hasDirtyVirtual = false;
        for (EditorFile ef : getOpenFilesList()) {
            if (ef.isDirty() && ef.getFileType() == FileType.API_TESTER) {
                hasDirtyVirtual = true;
                break;
            }
        }

        if (hasDirtyVirtual || (settings != null && settings.autoSave)) {
            ExecutorProvider.getInstance().getMainHandler().removeCallbacks(autoSaveRunnable);
            long delay = (settings != null) ? settings.autoSaveDelay * 20L : 2000L;
            ExecutorProvider.getInstance().getMainHandler().postDelayed(autoSaveRunnable, delay);
        }
    }

    public void saveFile(int index) {
        saveFile(index, null);
    }

    /**
     * Saves the content of the file at the specified index to disk.
     */
    public void saveFile(int index, Runnable onComplete) {
        List<EditorFile> docs = getOpenFilesList();
        if (index < 0 || index >= docs.size()) return;

        EditorFile ef = docs.get(index);

        // Skip saving if the file isn't modified or is a binary asset
        if (!ef.isDirty() || ef.isBinaryAsset()) {
            fileSaveResult.setValue(Result.success(true));
            if (onComplete != null) onComplete.run();
            return;
        }

        if (ef.isVirtual()) {
            ef.markSaved();
            openFilesLiveData.setValue(new ArrayList<>(docs));
            ProjectState state = projectStateLiveData.getValue();
            if (state != null) {
                state.setVirtualFile(ef.getRelativePath(projectRoot), ef.getContent());
                persistStateAsync();
            }
            if (onComplete != null) onComplete.run();
            return;
        }

        androidx.lifecycle.LiveData<com.cocode.vcode.ide.data.model.Result<Boolean>> liveData = fileRepo.writeFile(ef.getFile(), ef.getContent());
        liveData.observeForever(new androidx.lifecycle.Observer<>() {
            @Override
            public void onChanged(com.cocode.vcode.ide.data.model.Result<Boolean> result) {
                liveData.removeObserver(this);
                if (result != null && result.isSuccess()) {
                    ef.markSaved();
                    openFilesLiveData.setValue(new ArrayList<>(docs));
                    projectRepo.touchProjectById(projectId);

                    // Write back to the original content:// source if applicable
                    if (ef.getSourceUriString() != null) {
                        ExecutorProvider.getInstance().runOnIo(() -> {
                            try {
                                FileUtils.writeToUri(appContext, Uri.parse(ef.getSourceUriString()), ef.getContent());
                            } catch (Exception ignored) {
                            }
                        });
                    }

                    // Update Git status as the file change is now committed to the filesystem
                    refreshGitStatuses();

                    if (onComplete != null) onComplete.run();
                }
                fileSaveResult.setValue(result);
            }
        });
    }

    public void saveActiveFile() {
        saveFile(getActiveTabIndexValue());
    }

    /**
     * Checks if there are any open files with unsaved changes.
     */
    public boolean hasUnsavedFiles() {
        List<EditorFile> docs = getOpenFilesList();
        for (EditorFile ef : docs) {
            if (ef.isDirty() && !ef.isBinaryAsset()) {
                return true;
            }
        }
        return false;
    }

    public void saveAll() {
        saveAll(null);
    }

    /**
     * Bulk saves all open files that have unsaved changes.
     */
    public void saveAll(Runnable onComplete) {
        List<EditorFile> docs = getOpenFilesList();
        ExecutorProvider.getInstance().runOnIo(() -> {
            boolean allSuccess = true;
            boolean anySaved = false;

            for (EditorFile ef : docs) {
                if (ef.isDirty() && !ef.isBinaryAsset()) {
                    if (ef.isVirtual()) {
                        ef.markSaved();
                        if (currentState != null) {
                            currentState.setVirtualFile(ef.getRelativePath(projectRoot), ef.getContent());
                        }
                        anySaved = true;
                    } else {
                        try {
                            fileRepo.writeFileSync(ef.getFile(), ef.getContent());
                            ef.markSaved();
                            anySaved = true;
                            // Write back to the original content:// source if applicable
                            if (ef.getSourceUriString() != null) {
                                try {
                                    FileUtils.writeToUri(appContext, Uri.parse(ef.getSourceUriString()), ef.getContent());
                                } catch (Exception ignored) {
                                }
                            }
                        } catch (Exception e) {
                            allSuccess = false;
                        }
                    }
                }
            }

            boolean finalAllSuccess = allSuccess;
            boolean finalAnySaved = anySaved;

            ExecutorProvider.getInstance().runOnMain(() -> {
                if (finalAnySaved) {
                    openFilesLiveData.setValue(new ArrayList<>(docs));
                    projectRepo.touchProjectById(projectId);
                    refreshGitStatuses();
                }
                if (finalAllSuccess) {
                    fileSaveResult.setValue(Result.success(true));
                } else {
                    fileSaveResult.setValue(Result.error("Failed to save some files"));
                }
                if (onComplete != null) onComplete.run();
            });
        });
    }

    /**
     * Updates the UI-only state (cursor, scroll) for the active file.
     */
    public void updateActiveFileState(int cursor, int scrollY) {
        int index = getActiveTabIndexValue();
        if (index < 0) return;
        List<EditorFile> docs = getOpenFilesList();
        if (index >= docs.size()) return;

        EditorFile file = docs.get(index);
        if (cursor >= 0) file.setCursorPosition(cursor);
        if (scrollY >= 0) file.setScrollY(scrollY);
    }


    /**
     * Triggers an asynchronous save of the project's metadata (tabs, positions).
     */
    public void persistStateAsync() {
        if (currentState == null || projectRoot == null) return;
        updateCurrentStateObject();
        stateRepo.saveState(projectRoot, currentState);
    }

    /**
     * Performs a final synchronous state sync and auto-save before the activity is destroyed.
     */
    public void onStopSync() {
        if (currentState == null || projectRoot == null || projectId == null) return;

        ExecutorProvider.getInstance().getMainHandler().removeCallbacks(autoSaveRunnable);

        AppSettings appSettings = getSettingsLiveData().getValue();
        if (appSettings != null && appSettings.autoSave) {
            saveAllSync();
        }

        updateCurrentStateObject();
        stateRepo.saveStateSync(projectRoot, currentState);
    }

    /**
     * Synchronous version of saveAll for lifecycle-critical cleanup.
     */
    private void saveAllSync() {
        List<EditorFile> docs = getOpenFilesList();
        boolean anySaved = false;

        for (EditorFile ef : docs) {
            if (ef.isDirty() && !ef.isBinaryAsset()) {
                if (ef.isVirtual()) {
                    ef.markSaved();
                    if (currentState != null) {
                        currentState.setVirtualFile(ef.getRelativePath(projectRoot), ef.getContent());
                    }
                    anySaved = true;
                } else {
                    try {
                        fileRepo.writeFileSync(ef.getFile(), ef.getContent());
                        ef.markSaved();
                        anySaved = true;
                        // Write back to the original content:// source if applicable
                        if (ef.getSourceUriString() != null) {
                            try {
                                FileUtils.writeToUri(appContext, Uri.parse(ef.getSourceUriString()), ef.getContent());
                            } catch (Exception ignored2) {
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        if (anySaved) {
            projectRepo.touchProjectById(projectId);
        }
    }

    /**
     * Updates the internal ProjectState model with current UI data (open paths, cursor positions).
     */
    private void updateCurrentStateObject() {
        if (currentState == null) return;
        List<EditorFile> docs = getOpenFilesList();
        int activeIdx = getActiveTabIndexValue();

        currentState.setActiveTabIndex(activeIdx);

        List<String> paths = new ArrayList<>();
        for (EditorFile doc : docs) {
            String rel = getRelativePath(doc.getFile());
            paths.add(rel);
            currentState.setCursorFor(rel, doc.getCursorPosition());
            currentState.setScrollFor(rel, doc.getScrollY());
        }
        currentState.setOpenFilePaths(paths);
    }

    private List<EditorFile> getOpenFilesList() {
        List<EditorFile> list = openFilesLiveData.getValue();
        return list != null ? list : new ArrayList<>();
    }

    private int getActiveTabIndexValue() {
        Integer val = activeTabIndexLiveData.getValue();
        return val != null ? val : -1;
    }

    /**
     * Computes the relative path of a file with respect to the project root.
     */
    public String getRelativePath(File file) {
        if (projectRoot == null) return file.getName();
        String rootPath = projectRoot.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (filePath.startsWith(rootPath)) {
            String rel = filePath.substring(rootPath.length());
            if (rel.startsWith(File.separator)) rel = rel.substring(1);
            return rel;
        }
        return file.getName();
    }

    public void setPreviewState(String relativePath, boolean isPreview) {
        if (currentState != null) {
            currentState.setPreviewStateFor(relativePath, isPreview);
            persistStateAsync();
        }
    }

    public boolean getPreviewState(String relativePath) {
        if (currentState != null) {
            return currentState.getPreviewStateFor(relativePath);
        }
        return true;
    }

    public boolean hasExplicitPreviewState(String relativePath) {
        if (currentState != null) {
            return currentState.hasExplicitPreviewState(relativePath);
        }
        return false;
    }
}