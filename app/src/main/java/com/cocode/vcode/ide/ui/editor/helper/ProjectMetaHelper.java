package com.cocode.vcode.ide.ui.editor.helper;

import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.cocode.vcode.ide.utils.FileUtils;

import java.io.File;

/**
 * Helper for loading and updating project metadata and state.
 */
public class ProjectMetaHelper {

    public static String getMainFileFromMeta(File projectRoot) {
        if (projectRoot == null) return "";
        try {
            File metaFile = ProjectRepository.getProjectMetaFile(projectRoot);
            if (metaFile.exists()) {
                String metaContent = FileUtils.readFile(metaFile);
                org.json.JSONObject metaJson = new org.json.JSONObject(metaContent);
                return metaJson.optString("mainFile", "");
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public static void updateMainFileInMeta(File projectRoot, String newMainFile) {
        if (projectRoot == null) return;
        try {
            File metaFile = ProjectRepository.getProjectMetaFile(projectRoot);
            if (metaFile.exists()) {
                String metaContent = FileUtils.readFile(metaFile);
                org.json.JSONObject metaJson = new org.json.JSONObject(metaContent);
                metaJson.put("mainFile", newMainFile);
                FileUtils.writeFile(metaFile, metaJson.toString(2));
            }
        } catch (Exception ignored) {
        }
    }
}
