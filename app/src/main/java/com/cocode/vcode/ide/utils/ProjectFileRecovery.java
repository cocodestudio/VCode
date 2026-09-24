package com.cocode.vcode.ide.utils;

import com.cocode.vcode.ide.core.template.FileTemplateManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Ensures required project metadata files (such as .vcode/meta/project.json and .vcode/state/session.json)
 * exist for VCode projects, auto-generating default configuration records if missing.
 */
public class ProjectFileRecovery {

    /**
     * Verifies that project metadata and session files exist in the specified project root, creating defaults if missing.
     * Only runs for directories inside VCodeProjects/ to avoid writing metadata in external folders.
     */
    public static void ensureProjectFilesExist(File projectRoot) {
        if (projectRoot == null || !projectRoot.exists() || !projectRoot.isDirectory()) {
            return;
        }

        // Only create metadata files inside VCode-owned project directories
        String absPath = projectRoot.getAbsolutePath();
        if (!absPath.contains("/VCodeProjects/") && !absPath.endsWith("/VCodeProjects")
                && !absPath.contains("\\VCodeProjects\\") && !absPath.endsWith("\\VCodeProjects")) {
            return;
        }

        // Never generate project metadata or session files for the internal templates directory
        String rootName = projectRoot.getName();
        String normalizedAbsPath = absPath.replace('\\', '/').toLowerCase(java.util.Locale.US);
        String templatesLower = FileTemplateManager.TEMPLATES_DIR_NAME.toLowerCase(java.util.Locale.US);
        if (rootName.equalsIgnoreCase(FileTemplateManager.TEMPLATES_DIR_NAME)
                || rootName.equalsIgnoreCase("templates")
                || normalizedAbsPath.contains("/" + templatesLower + "/")
                || normalizedAbsPath.endsWith("/" + templatesLower)) {
            File vcodeDir = new File(projectRoot, ".vcode");
            if (vcodeDir.exists()) {
                com.cocode.vcode.ide.utils.FileUtils.deleteRecursive(vcodeDir);
            }
            return;
        }

        File metaFile = com.cocode.vcode.ide.data.repository.ProjectRepository.getProjectMetaFile(projectRoot);
        if (!metaFile.exists()) {
            if (metaFile.getParentFile() != null) metaFile.getParentFile().mkdirs();
            createDefaultProjectMeta(metaFile);
        }

        File sessionFile = com.cocode.vcode.ide.data.repository.ProjectRepository.getSessionStateFile(projectRoot);
        if (!sessionFile.exists()) {
            if (sessionFile.getParentFile() != null) sessionFile.getParentFile().mkdirs();
            createDefaultSession(sessionFile);
        }
    }

    /**
     * Purges an auto-generated .vcode directory created accidentally during intent file openings.
     * To protect genuine user projects:
     * - Only runs if .vcode exists.
     * - Only purges if meta/project.json contains the default auto-generated projectName "VCode Project"
     *   or if .vcode is completely empty / contains no user content.
     */
    public static void purgeAccidentalIntentVCodeDir(File projectRoot) {
        if (projectRoot == null || !projectRoot.exists() || !projectRoot.isDirectory()) {
            return;
        }
        File vcodeDir = new File(projectRoot, ".vcode");
        if (!vcodeDir.exists() || !vcodeDir.isDirectory()) {
            return;
        }

        try {
            File metaFile = com.cocode.vcode.ide.data.repository.ProjectRepository.getProjectMetaFile(projectRoot);
            if (metaFile.exists()) {
                String content = com.cocode.vcode.ide.utils.FileUtils.readFile(metaFile);
                JSONObject obj = new JSONObject(content);
                String name = obj.optString("projectName", "");
                if ("VCode Project".equals(name)) {
                    com.cocode.vcode.ide.utils.FileUtils.deleteRecursive(vcodeDir);
                }
            } else {
                File[] children = vcodeDir.listFiles();
                if (children == null || children.length == 0) {
                    com.cocode.vcode.ide.utils.FileUtils.deleteRecursive(vcodeDir);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Creates a default project metadata JSON file.
     */
    private static void createDefaultProjectMeta(File metaFile) {
        try {
            JSONObject meta = new JSONObject();
            meta.put("projectName", "VCode Project");
            meta.put("createdAt", System.currentTimeMillis());
            meta.put("version", "1.0.0");

            writeJsonToFile(metaFile, meta);
        } catch (Exception e) {
            android.util.Log.e("VCode", "Failed to create default project meta", e);
        }
    }

    /**
     * Creates a default session state JSON file.
     */
    private static void createDefaultSession(File sessionFile) {
        try {
            JSONObject session = new JSONObject();
            session.put("openFiles", new JSONArray());
            session.put("activeTabIndex", -1);
            session.put("lastOpened", System.currentTimeMillis());

            writeJsonToFile(sessionFile, session);
        } catch (Exception e) {
            android.util.Log.e("VCode", "Failed to create default session", e);
        }
    }

    /**
     * Writes a JSON object to a file with 4-space indentation.
     */
    private static void writeJsonToFile(File file, JSONObject json) throws IOException, JSONException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json.toString(4));
            writer.flush();
        }
    }
}