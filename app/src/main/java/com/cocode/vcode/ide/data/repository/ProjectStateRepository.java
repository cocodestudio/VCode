package com.cocode.vcode.ide.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.cocode.vcode.ide.VCodeApplication;
import com.cocode.vcode.ide.data.model.ProjectState;
import com.cocode.vcode.ide.data.model.Result;
import com.cocode.vcode.ide.utils.ExecutorProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Repository for saving and loading project session states (open tabs, cursor offsets, scroll positions, preview modes).
 * Stores session data in .vcode/state/session.json for VCode projects or in app-private storage for external directories.
 */
public class ProjectStateRepository {

    private static final String SESSION_FILE = "session.json";

    public ProjectStateRepository() {
    }

    /**
     * Determines where session data should be persisted.
     * <p>
     * For VCode-owned projects (inside VCodeProjects/) the session file is stored in .vcode/state/.
     * <p>
     * For external directories (Downloads, Documents, etc.) the session is stored in app-private
     * internal storage keyed by a hash of the directory path.
     */
    private static File getSessionStorageDir(File projectDir) {
        android.content.Context ctx = VCodeApplication.getInstance();
        String absPath = projectDir.getAbsolutePath();

        if (absPath.contains("/VCodeProjects/") || absPath.contains("/VCodeProjects")) {
            File stateDir = new File(new File(projectDir, ".vcode"), "state");
            if (!stateDir.exists()) {
                stateDir.mkdirs();
            }
            return stateDir;
        }

        String safeKey = Integer.toHexString(absPath.hashCode());
        File bucket = new File(ctx.getFilesDir(), "external_sessions/" + safeKey);
        if (!bucket.exists()) {
            bucket.mkdirs();
        }
        return bucket;
    }

    /**
     * Asynchronously saves the project session state to disk.
     */
    public void saveState(File projectDir, ProjectState state) {
        MutableLiveData<Result<Boolean>> liveData = new MutableLiveData<>();
        if (projectDir == null || state == null) {
            liveData.setValue(Result.error("Invalid project directory or state"));
            return;
        }
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                writeStateToDisk(projectDir, state);
                ExecutorProvider.getInstance().runOnMain(() -> liveData.setValue(Result.success(true)));
            } catch (Exception e) {
                ExecutorProvider.getInstance()
                        .runOnMain(() -> liveData.setValue(Result.error("Failed to save state: " + e.getMessage())));
            }
        });
    }

    /**
     * Synchronously saves the project session state (intended for background threads or activity lifecycle hooks).
     */
    public void saveStateSync(File projectDir, ProjectState state) {
        if (projectDir == null || state == null)
            return;
        try {
            writeStateToDisk(projectDir, state);
        } catch (Exception ignored) {
        }
    }

    /**
     * Synchronously loads the project session state from disk. Returns a new empty state on failure.
     */
    public ProjectState loadStateSync(File projectDir, String projectId) {
        if (projectDir == null)
            return new ProjectState(projectId);
        try {
            return readStateFromDisk(projectDir, projectId);
        } catch (Exception e) {
            return new ProjectState(projectId);
        }
    }

    private File getSessionFile(File projectDir) {
        return new File(getSessionStorageDir(projectDir), SESSION_FILE);
    }

    /**
     * Packages structural configuration properties into a streamlined string payload for disk updates.
     */
    private void writeStateToDisk(File projectDir, ProjectState state) throws Exception {
        if (!projectDir.exists())
            projectDir.mkdirs();

        JSONObject root = new JSONObject();
        root.put("projectId", state.getProjectId() != null ? state.getProjectId() : "");
        root.put("activeTabIndex", state.getActiveTabIndex());

        // Open file paths
        JSONArray pathsArray = new JSONArray();
        List<String> paths = state.getOpenFilePaths();
        if (paths != null) {
            for (String p : paths) {
                if (p != null)
                    pathsArray.put(p);
            }
        }
        root.put("openFilePaths", pathsArray);

        // Cursor positions
        JSONObject cursors = new JSONObject();
        Map<String, Integer> cursorMap = state.getCursorPositions();
        if (cursorMap != null) {
            for (Map.Entry<String, Integer> entry : cursorMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    cursors.put(entry.getKey(), entry.getValue());
                }
            }
        }
        root.put("cursorPositions", cursors);

        // Scroll positions
        JSONObject scrolls = new JSONObject();
        Map<String, Integer> scrollMap = state.getScrollPositions();
        if (scrollMap != null) {
            for (Map.Entry<String, Integer> entry : scrollMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    scrolls.put(entry.getKey(), entry.getValue());
                }
            }
        }
        root.put("scrollPositions", scrolls);

        // Preview toggle states
        JSONObject previews = new JSONObject();
        Map<String, Boolean> previewMap = state.getPreviewStates();
        if (previewMap != null) {
            for (Map.Entry<String, Boolean> entry : previewMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    previews.put(entry.getKey(), entry.getValue());
                }
            }
        }
        root.put("previewStates", previews);

        // Virtual files
        JSONObject virtuals = new JSONObject();
        Map<String, String> virtualMap = state.getVirtualFiles();
        if (virtualMap != null) {
            for (Map.Entry<String, String> entry : virtualMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    virtuals.put(entry.getKey(), entry.getValue());
                }
            }
        }
        root.put("virtualFiles", virtuals);

        File sessionFile = getSessionFile(projectDir);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(sessionFile), StandardCharsets.UTF_8))) {
            writer.write(root.toString(2));
        }
    }

    private ProjectState readStateFromDisk(File projectDir, String projectId) throws Exception {
        File sessionFile = getSessionFile(projectDir);
        if (!sessionFile.exists()) {
            return new ProjectState(projectId);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(sessionFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        String raw = sb.toString().trim();
        if (raw.isEmpty())
            return new ProjectState(projectId);

        JSONObject root = new JSONObject(raw);

        ProjectState state = new ProjectState();
        state.setProjectId(root.optString("projectId", projectId != null ? projectId : ""));
        state.setActiveTabIndex(root.optInt("activeTabIndex", 0));

        // Restore tab layouts
        JSONArray pathsArray = root.optJSONArray("openFilePaths");
        List<String> paths = new ArrayList<>();
        if (pathsArray != null) {
            for (int i = 0; i < pathsArray.length(); i++) {
                String p = pathsArray.optString(i, null);
                if (p != null && !p.isEmpty())
                    paths.add(p);
            }
        }
        state.setOpenFilePaths(paths);

        // Restore editor carets
        JSONObject cursors = root.optJSONObject("cursorPositions");
        Map<String, Integer> cursorMap = new HashMap<>();
        if (cursors != null) {
            Iterator<String> keys = cursors.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                cursorMap.put(key, cursors.optInt(key, 0));
            }
        }
        state.setCursorPositions(cursorMap);

        // Restore viewport rows
        JSONObject scrolls = root.optJSONObject("scrollPositions");
        Map<String, Integer> scrollMap = new HashMap<>();
        if (scrolls != null) {
            Iterator<String> keys = scrolls.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                scrollMap.put(key, scrolls.optInt(key, 0));
            }
        }
        state.setScrollPositions(scrollMap);

        // Restore preview states
        if (root.has("previewStates")) {
            JSONObject previews = root.getJSONObject("previewStates");
            Iterator<String> keys = previews.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                state.setPreviewStateFor(k, previews.getBoolean(k));
            }
        }

        if (root.has("virtualFiles")) {
            JSONObject virtuals = root.getJSONObject("virtualFiles");
            Iterator<String> keys = virtuals.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                state.setVirtualFile(k, virtuals.getString(k));
            }
        }

        return state;
    }
}