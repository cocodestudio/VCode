package com.cocode.vcode.ide.ui.editor.helper;

import androidx.lifecycle.MutableLiveData;

import com.cocode.vcode.ide.git.model.FileStatus;
import com.cocode.vcode.ide.utils.ExecutorProvider;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class EditorGitHelper {

    public static void refreshGitStatuses(File projectRoot, MutableLiveData<Map<String, FileStatus.Type>> gitStatusesLiveData) {
        if (projectRoot == null) return;

        File gitDir = new File(projectRoot, ".git");
        if (!gitDir.exists()) {
            ExecutorProvider.getInstance().runOnMain(() -> gitStatusesLiveData.setValue(new HashMap<>()));
            return;
        }

        ExecutorProvider.getInstance().runOnIo(() -> {
            try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(projectRoot)) {
                org.eclipse.jgit.api.Status workspaceStatus = git.status().call();
                Map<String, FileStatus.Type> freshMap = new HashMap<>();

                for (String path : workspaceStatus.getAdded())
                    freshMap.put(path, FileStatus.Type.STAGED_ADDED);
                for (String path : workspaceStatus.getChanged())
                    freshMap.put(path, FileStatus.Type.STAGED_MODIFIED);
                for (String path : workspaceStatus.getRemoved())
                    freshMap.put(path, FileStatus.Type.STAGED_DELETED);
                for (String path : workspaceStatus.getModified())
                    freshMap.put(path, FileStatus.Type.UNSTAGED_MODIFIED);
                for (String path : workspaceStatus.getMissing())
                    freshMap.put(path, FileStatus.Type.UNSTAGED_DELETED);
                for (String path : workspaceStatus.getUntracked())
                    freshMap.put(path, FileStatus.Type.UNTRACKED);
                for (String path : workspaceStatus.getConflicting())
                    freshMap.put(path, FileStatus.Type.CONFLICTED);

                ExecutorProvider.getInstance().runOnMain(() -> gitStatusesLiveData.setValue(freshMap));
            } catch (Exception e) {
                android.util.Log.e("VCode", "Error in refreshGitStatuses", e);
            }
        });
    }
}
