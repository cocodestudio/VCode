package com.cocode.vcode.ide.data.repository;

import android.content.Context;
import android.os.Build;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cocode.vcode.ide.data.model.Project;
import com.cocode.vcode.ide.data.model.Result;
import com.cocode.vcode.ide.git.core.GitRepository;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repository for managing projects (creation, discovery, metadata, renaming, duplication, deletion).
 */
public class ProjectRepository {

    public static final String VCODE_DIR = ".vcode";
    public static final String META_DIR = "meta";
    public static final String STATE_DIR = "state";
    public static final String PROJECT_FILE = "project.json";
    public static final String LEGACY_META_FILE = "project_meta.json";

    public static File findProjectRoot(File file) {
        File current = file;
        while (current != null) {
            File vcodeMeta = new File(new File(new File(current, VCODE_DIR), META_DIR), PROJECT_FILE);
            if (vcodeMeta.exists() || new File(current, LEGACY_META_FILE).exists()) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }

    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_CREATED_AT = "createdAt";
    private static final String KEY_LAST_MODIFIED_AT = "lastModifiedAt";
    private static final String KEY_MAIN_FILE = "mainFile";
    private static final String KEY_FILE_COUNT = "fileCount";
    private final Context appContext;

    public ProjectRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Loads all projects from the application projects directory, sorted by last modified date descending.
     */
    public LiveData<Result<List<Project>>> getAllProjects() {
        MutableLiveData<Result<List<Project>>> liveData = new MutableLiveData<>();
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                File projectsDir = FileUtils.getProjectsDir(appContext);
                List<Project> projects = new ArrayList<>();

                File[] entries = projectsDir.listFiles();
                if (entries != null) {
                    for (File dir : entries) {
                        if (dir.isDirectory()) {
                            File vcodeDir = new File(dir, VCODE_DIR);
                            File metaDir = new File(vcodeDir, META_DIR);
                            File stateDir = new File(vcodeDir, STATE_DIR);
                            File meta = new File(metaDir, PROJECT_FILE);
                            File legacyMeta = new File(dir, LEGACY_META_FILE);

                            // Migrate legacy metadata if present
                            if (!meta.exists() && legacyMeta.exists()) {
                                try {
                                    String jsonString = FileUtils.readFile(legacyMeta);
                                    org.json.JSONObject json = new org.json.JSONObject(jsonString);
                                    if (dir.getName().equals(json.optString(KEY_ID))) {
                                        metaDir.mkdirs();
                                        stateDir.mkdirs();
                                        FileUtils.writeFile(meta, jsonString);
                                        legacyMeta.delete();

                                        File legacySession = new File(dir, "session.json");
                                        if (legacySession.exists()) {
                                            File newSession = new File(stateDir, "session.json");
                                            FileUtils.writeFile(newSession, FileUtils.readFile(legacySession));
                                            legacySession.delete();
                                        }
                                    }
                                } catch (Exception ignored) {
                                }
                            }

                            if (meta.exists()) {
                                try {
                                    Project p = readProjectMeta(meta, dir);
                                    p.setFileCount(FileUtils.countFilesInDir(dir));
                                    projects.add(p);
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    projects.sort((a, b) -> Long.compare(b.getLastModifiedAt(), a.getLastModifiedAt()));
                }

                ExecutorProvider.getInstance().runOnMain(() -> liveData.setValue(Result.success(projects)));
            } catch (Exception e) {
                ExecutorProvider.getInstance()
                        .runOnMain(() -> liveData.setValue(Result.error("Failed to load projects: " + e.getMessage())));
            }
        });
        return liveData;
    }

    /**
     * Reads base structural boilerplate template sheets packaged inside raw application asset sectors.
     */
    private String readTemplateFromAssets(String fileName) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = appContext.getAssets().open("templates/" + fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            if (sb.length() > 0) {
                sb.deleteCharAt(sb.length() - 1); // Clean up trailing line break adjustments
            }
        } catch (Exception e) {
            return "";
        }
        return sb.toString();
    }

    /**
     * Creates a new project with the given template, optionally initializing a Git repository.
     */
    public LiveData<Result<Project>> createProject(String name, String mainFile, String templateChoice, boolean initGit, String defaultBranch) {
        MutableLiveData<Result<Project>> liveData = new MutableLiveData<>();
        if (name == null || name.trim().isEmpty()) {
            liveData.setValue(Result.error("Project name cannot be empty"));
            return liveData;
        }
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                String id = UUID.randomUUID().toString();
                File projectsDir = FileUtils.getProjectsDir(appContext);

                File projectDir = new File(projectsDir, id);
                if (!projectDir.mkdirs())
                    throw new Exception("Could not create project directory");

                String resolvedMainFile = (mainFile != null && !mainFile.isEmpty()) ? mainFile : "index.html";
                long now = System.currentTimeMillis();

                Project project = new Project(id, name.trim(), now, now, resolvedMainFile, 0);

                if ("HTML+CSS+JS".equals(templateChoice)) {
                    String htmlContent = readTemplateFromAssets("template_html_css_js.html");
                    String cssContent = readTemplateFromAssets("template_blank.css");
                    String jsContent = readTemplateFromAssets("template_blank.js");

                    FileUtils.writeFile(new File(projectDir, resolvedMainFile), htmlContent);
                    FileUtils.writeFile(new File(projectDir, "style.css"), cssContent);
                    FileUtils.writeFile(new File(projectDir, "app.js"), jsContent);
                } else if ("HTML".equals(templateChoice)) {
                    String htmlContent = readTemplateFromAssets("template_blank.html");
                    FileUtils.writeFile(new File(projectDir, resolvedMainFile), htmlContent);
                }

                if (initGit) {
                    GitRepository git = new GitRepository();
                    git.setConfiguredDefaultBranch(defaultBranch);
                    git.openRepository(projectDir);
                }

                project.setFileCount(FileUtils.countFilesInDir(projectDir));
                writeProjectMeta(projectDir, project);

                ExecutorProvider.getInstance().runOnMain(() -> liveData.setValue(Result.success(project)));
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(
                        () -> liveData.setValue(Result.error("Failed to create project: " + e.getMessage())));
            }
        });
        return liveData;
    }

    /**
     * Renames a project and updates its metadata.
     */
    public LiveData<Result<Project>> renameProject(Project project, String newName) {
        MutableLiveData<Result<Project>> liveData = new MutableLiveData<>();
        if (project == null || newName == null || newName.trim().isEmpty()) {
            liveData.setValue(Result.error("Invalid project or name"));
            return liveData;
        }
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                File projectDir = new File(FileUtils.getProjectsDir(appContext), project.getId());
                if (!projectDir.exists())
                    throw new Exception("Project directory not found");

                project.setName(newName.trim());
                project.setLastModifiedAt(System.currentTimeMillis());
                writeProjectMeta(projectDir, project);

                ExecutorProvider.getInstance().runOnMain(() -> liveData.setValue(Result.success(project)));
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(
                        () -> liveData.setValue(Result.error("Failed to rename project: " + e.getMessage())));
            }
        });
        return liveData;
    }

    /**
     * Deletes a project and its directory recursively.
     */
    public LiveData<Result<Boolean>> deleteProject(Project project) {
        MutableLiveData<Result<Boolean>> liveData = new MutableLiveData<>();
        if (project == null || project.getId() == null) {
            liveData.setValue(Result.error("Invalid project"));
            return liveData;
        }
        ExecutorProvider.getInstance().runOnIo(() -> {
            File projectDir = new File(FileUtils.getProjectsDir(appContext), project.getId());
            boolean deleted = FileUtils.deleteRecursive(projectDir);
            if (deleted) {
                ExecutorProvider.getInstance().runOnMain(() -> liveData.setValue(Result.success(true)));
            } else {
                ExecutorProvider.getInstance().runOnMain(
                        () -> liveData.setValue(Result.error("Failed to delete project: " + project.getName())));
            }
        });
        return liveData;
    }

    /**
     * Duplicates an existing project into a new directory with a generated UUID.
     */
    public LiveData<Result<Project>> duplicateProject(Project source) {
        MutableLiveData<Result<Project>> liveData = new MutableLiveData<>();
        if (source == null || source.getId() == null) {
            liveData.setValue(Result.error("Invalid source project"));
            return liveData;
        }
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                File projectsDir = FileUtils.getProjectsDir(appContext);
                File srcDir = new File(projectsDir, source.getId());
                if (!srcDir.exists())
                    throw new Exception("Source project directory not found");

                String newId = UUID.randomUUID().toString();
                File destDir = new File(projectsDir, newId);

                boolean copied = FileUtils.copyDirectory(srcDir, destDir);
                if (!copied)
                    throw new Exception("Directory copy failed");

                long now = System.currentTimeMillis();
                String copyName = "Copy of " + source.getName();
                Project copy = new Project(newId, copyName, now, now, source.getMainFile(), 0);
                copy.setFileCount(FileUtils.countFilesInDir(destDir));

                writeProjectMeta(destDir, copy);

                ExecutorProvider.getInstance().runOnMain(() -> liveData.setValue(Result.success(copy)));
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(
                        () -> liveData.setValue(Result.error("Failed to duplicate project: " + e.getMessage())));
            }
        });
        return liveData;
    }

    /**
     * Updates the last modified timestamp and file count for a project by ID.
     */
    public void touchProjectById(String projectId) {
        if (projectId == null || projectId.isEmpty()) return;
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                File projectDir = new File(FileUtils.getProjectsDir(appContext), projectId);
                if (!projectDir.exists()) return;

                File meta = new File(new File(new File(projectDir, VCODE_DIR), META_DIR), PROJECT_FILE);
                if (!meta.exists()) return;

                Project project = readProjectMeta(meta, projectDir);
                project.setLastModifiedAt(System.currentTimeMillis());
                project.setFileCount(FileUtils.countFilesInDir(projectDir));
                writeProjectMeta(projectDir, project);
            } catch (Exception ignored) {
            }
        });
    }

    private void writeProjectMeta(File projectDir, Project project) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put(KEY_ID, project.getId() != null ? project.getId() : "");
        obj.put(KEY_NAME, project.getName() != null ? project.getName() : "Untitled");
        obj.put(KEY_CREATED_AT, project.getCreatedAt());
        obj.put(KEY_LAST_MODIFIED_AT, project.getLastModifiedAt());
        obj.put(KEY_MAIN_FILE, project.getMainFile() != null ? project.getMainFile() : "index.html");
        obj.put(KEY_FILE_COUNT, project.getFileCount());

        File metaFile = new File(new File(new File(projectDir, VCODE_DIR), META_DIR), PROJECT_FILE);
        metaFile.getParentFile().mkdirs();
        FileUtils.writeFile(metaFile, obj.toString(4));
    }

    private Project readProjectMeta(File metaFile, File projectDir) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(metaFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        JSONObject obj = new JSONObject(sb.toString());

        String id = obj.optString(KEY_ID, projectDir.getName());
        String name = obj.optString(KEY_NAME, "Untitled");
        long createdAt = obj.optLong(KEY_CREATED_AT, 0L);
        long lastModifiedAt = obj.optLong(KEY_LAST_MODIFIED_AT, createdAt);
        String mainFile = obj.optString(KEY_MAIN_FILE, "index.html");
        int fileCount = obj.optInt(KEY_FILE_COUNT, 0);

        return new Project(id, name, createdAt, lastModifiedAt, mainFile, fileCount);
    }
}