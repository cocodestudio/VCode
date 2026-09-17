package com.cocode.vcode.ide.ui.projects;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.model.Project;
import com.cocode.vcode.ide.data.model.Result;
import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.cocode.vcode.ide.data.repository.SettingsRepository;

import java.util.List;

/**
 * ProjectsViewModel manages the list of projects and workspace-level operations.
 * It interfaces with the {@link ProjectRepository} to handle project creation,
 * renaming, and deletion, and publishes the results to the UI via LiveData.
 */
public class ProjectsViewModel extends ViewModel {

    public static volatile Runnable onCloneCompleteListener;

    private final ProjectRepository projectRepo;
    private final SettingsRepository settingsRepo;

    // Reactive data streams for project list and operation results
    private final MutableLiveData<Result<List<Project>>> projectsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Result<Project>> actionResultLiveData = new MutableLiveData<>();
    private final MutableLiveData<AppSettings> settingsLiveData = new MutableLiveData<>();
    private final androidx.lifecycle.Observer<com.cocode.vcode.ide.data.model.Result<java.util.List<com.cocode.vcode.ide.data.model.Project>>> projectsObserver = projectsLiveData::setValue;
    private androidx.lifecycle.LiveData<com.cocode.vcode.ide.data.model.Result<java.util.List<com.cocode.vcode.ide.data.model.Project>>> currentProjectsLiveData;

    public ProjectsViewModel(ProjectRepository projectRepo, SettingsRepository settingsRepo) {
        this.projectRepo = projectRepo;
        this.settingsRepo = settingsRepo;
        loadProjects();
        loadSettings();
    }

    public LiveData<Result<List<Project>>> getProjectsLiveData() {
        return projectsLiveData;
    }

    /**
     * Refetches the complete list of projects from the repository.
     */
    public void loadProjects() {
        if (currentProjectsLiveData != null) {
            currentProjectsLiveData.removeObserver(projectsObserver);
        }
        currentProjectsLiveData = projectRepo.getAllProjects();
        currentProjectsLiveData.observeForever(projectsObserver);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (currentProjectsLiveData != null) {
            currentProjectsLiveData.removeObserver(projectsObserver);
        }
        onCloneCompleteListener = null;
    }

    /**
     * Loads the current application settings.
     */
    public void loadSettings() {
        AppSettings settings = settingsRepo.loadSettings();
        settingsLiveData.setValue(settings);
    }

    /**
     * Creates a new project with the specified parameters.
     * Automatically resolves the default branch name from application settings.
     */
    public void createProject(String name, String mainFile, String templateContent, boolean initGit) {
        // Resolve target defaults directly from user parameters configuration profiles
        AppSettings currentSettings = settingsRepo.loadSettings();
        String activeDefaultBranch = "main";
        if (currentSettings != null && currentSettings.gitDefaultBranch != null && !currentSettings.gitDefaultBranch.trim().isEmpty()) {
            activeDefaultBranch = currentSettings.gitDefaultBranch.trim();
        }

        androidx.lifecycle.LiveData<com.cocode.vcode.ide.data.model.Result<com.cocode.vcode.ide.data.model.Project>> liveData = projectRepo.createProject(name, mainFile, templateContent, initGit, activeDefaultBranch);
        liveData.observeForever(new androidx.lifecycle.Observer<com.cocode.vcode.ide.data.model.Result<com.cocode.vcode.ide.data.model.Project>>() {
            @Override
            public void onChanged(com.cocode.vcode.ide.data.model.Result<com.cocode.vcode.ide.data.model.Project> result) {
                liveData.removeObserver(this);
                actionResultLiveData.setValue(result);
                if (result != null && result.isSuccess()) {
                    // Refresh the list upon successful project creation
                    loadProjects();
                }
            }
        });
    }

    /**
     * Renames an existing project.
     */
    public void renameProject(Project project, String newName) {
        androidx.lifecycle.LiveData<com.cocode.vcode.ide.data.model.Result<com.cocode.vcode.ide.data.model.Project>> liveData = projectRepo.renameProject(project, newName);
        liveData.observeForever(new androidx.lifecycle.Observer<com.cocode.vcode.ide.data.model.Result<com.cocode.vcode.ide.data.model.Project>>() {
            @Override
            public void onChanged(com.cocode.vcode.ide.data.model.Result<com.cocode.vcode.ide.data.model.Project> result) {
                liveData.removeObserver(this);
                actionResultLiveData.setValue(result);
                if (result != null && result.isSuccess()) {
                    loadProjects();
                }
            }
        });
    }

    /**
     * Deletes a project and its associated files from disk.
     */
    public void deleteProject(Project project) {
        androidx.lifecycle.LiveData<com.cocode.vcode.ide.data.model.Result<Boolean>> liveData = projectRepo.deleteProject(project);
        liveData.observeForever(new androidx.lifecycle.Observer<com.cocode.vcode.ide.data.model.Result<Boolean>>() {
            @Override
            public void onChanged(com.cocode.vcode.ide.data.model.Result<Boolean> deleteResult) {
                liveData.removeObserver(this);
                if (deleteResult != null && deleteResult.isSuccess()) {
                    actionResultLiveData.setValue(com.cocode.vcode.ide.data.model.Result.success(project));
                    loadProjects();
                } else {
                    String err = (deleteResult != null && deleteResult.getError() != null)
                            ? deleteResult.getError()
                            : "Delete failed";
                    actionResultLiveData.setValue(com.cocode.vcode.ide.data.model.Result.error(err));
                }
            }
        });
    }

    /**
     * Persists the ID of the project that was last opened by the user.
     */
    public void saveLastProjectId(String projectId) {
        settingsRepo.saveLastProjectId(projectId);
    }

}