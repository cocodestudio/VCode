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
 * Higher-level structural repository managing project lifecycles.
 * Handles project creation from templates, renaming, cloning, asset unpacking, metadata parsing,
 * and controls underlying version control setup flags.
 */
public class ProjectRepository {

    public static final String META_FILE = "project_meta.json";
    public static final String SESSION_FILE = "session.json";

    public static File findProjectRoot(File file) {
        File current = file;
        while (current != null) {
            if (new File(current, META_FILE).exists()) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }

    // JSON configuration mapping fields for project description schemas
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
     * Collects and monitors all registered workspace directories found within local storage limits.
     * Orders matching outcomes chronologically by their last modification timestamp values.
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
                            File meta = new File(dir, META_FILE);
                            if (meta.exists()) {
                                try {
                                    // Parse individual internal description structures
                                    Project p = readProjectMeta(meta, dir);
                                    p.setFileCount(FileUtils.countFilesInDir(dir));
                                    projects.add(p);
                                } catch (Exception ignored) {
                                    // Soft failure processing skip preserves list load stability
                                }
                            }
                        }
                    }
                }

                // Sort files from newest down to oldest modifications if platform layers support it
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
     * Creates workspace layout instances executing targeted initial default branch criteria configurations.
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

                // Option Variant A: Provision complete web layouts with interlocking styling sheets and actions
                if ("HTML+CSS+JS".equals(templateChoice)) {
                    String htmlContent = readTemplateFromAssets("template_html_css_js.html");
                    String cssContent = readTemplateFromAssets("template_blank.css");
                    String jsContent = readTemplateFromAssets("template_blank.js");

                    FileUtils.writeFile(new File(projectDir, resolvedMainFile), htmlContent);
                    FileUtils.writeFile(new File(projectDir, "style.css"), cssContent);
                    FileUtils.writeFile(new File(projectDir, "app.js"), jsContent);

                    // Option Variant B: Provision standalone standard text markup pages
                } else if ("HTML".equals(templateChoice)) {
                    String htmlContent = readTemplateFromAssets("template_blank.html");
                    FileUtils.writeFile(new File(projectDir, resolvedMainFile), htmlContent);
                }

                // If version control configurations are requested, run full environment initializations next
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
     * Renames user targets inside the project collection metadata records.
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
     * Destroys directory footprints for specified projects inside local partitions.
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
     * Clones workspace environments into separate distinct resource layouts.
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
     * Bumps modification tracking records on targeted directories, updating aggregate totals.
     */
    public void touchProjectById(String projectId) {
        if (projectId == null || projectId.isEmpty()) return;
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                File projectDir = new File(FileUtils.getProjectsDir(appContext), projectId);
                if (!projectDir.exists()) return;

                File meta = new File(projectDir, META_FILE);
                if (!meta.exists()) return;

                Project project = readProjectMeta(meta, projectDir);
                project.setLastModifiedAt(System.currentTimeMillis());
                project.setFileCount(FileUtils.countFilesInDir(projectDir));
                writeProjectMeta(projectDir, project);
            } catch (Exception ignored) {
                // Silently bypass validation discrepancies to preserve background execution stability
            }
        });
    }

    /**
     * Commits active structural configuration parameters to disk using JSON structures.
     */
    private void writeProjectMeta(File projectDir, Project project) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put(KEY_ID, project.getId() != null ? project.getId() : "");
        obj.put(KEY_NAME, project.getName() != null ? project.getName() : "Untitled");
        obj.put(KEY_CREATED_AT, project.getCreatedAt());
        obj.put(KEY_LAST_MODIFIED_AT, project.getLastModifiedAt());
        obj.put(KEY_MAIN_FILE, project.getMainFile() != null ? project.getMainFile() : "index.html");
        obj.put(KEY_FILE_COUNT, project.getFileCount());

        File metaFile = new File(projectDir, META_FILE);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(metaFile), StandardCharsets.UTF_8))) {
            writer.write(obj.toString(2));
        }
    }

    /**
     * Resolves metadata values from storage descriptors during directory scanning loops.
     */
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