package com.cocode.vcode.ide.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cocode.vcode.ide.data.model.Result;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;

import java.io.File;

/**
 * Repository coordinating file I/O operations (asynchronous and synchronous file writes, recursive deletion).
 */
public class FileRepository {

    private static final MutableLiveData<File> fileSavedEvent = new MutableLiveData<>();

    public FileRepository() {
    }

    public static LiveData<File> getFileSavedEvent() {
        return fileSavedEvent;
    }

    /**
     * Asynchronously writes content to a file on the I/O thread, posting the result to {@link LiveData}.
     *
     * @param file    the destination file
     * @param content the content string to write
     * @return a {@link LiveData} containing the {@link Result} of the operation
     */
    public LiveData<Result<Boolean>> writeFile(File file, String content) {
        MutableLiveData<Result<Boolean>> liveData = new MutableLiveData<>();
        if (file == null) {
            liveData.setValue(Result.error("File is null"));
            return liveData;
        }

        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                FileUtils.writeFile(file, content != null ? content : "");
                ExecutorProvider.getInstance().runOnMain(() -> {
                    liveData.setValue(Result.success(true));
                    fileSavedEvent.setValue(file);
                });
            } catch (Exception e) {
                ExecutorProvider.getInstance()
                        .runOnMain(() -> liveData.setValue(Result.error("Failed to write file: " + e.getMessage())));
            }
        });
        return liveData;
    }

    /**
     * Synchronously writes content to a file (intended for background threads).
     */
    public void writeFileSync(File file, String content) {
        if (file == null)
            return;
        try {
            FileUtils.writeFile(file, content != null ? content : "");
            ExecutorProvider.getInstance().runOnMain(() -> fileSavedEvent.setValue(file));
        } catch (Exception ignored) {
        }
    }

    /**
     * Asynchronously deletes a file or directory recursively on the I/O thread.
     */
    public LiveData<Result<Boolean>> delete(File file) {
        MutableLiveData<Result<Boolean>> liveData = new MutableLiveData<>();
        if (file == null) {
            liveData.setValue(Result.error("File is null"));
            return liveData;
        }

        ExecutorProvider.getInstance().runOnIo(() -> {
            boolean deleted = FileUtils.deleteRecursive(file);
            if (deleted) {
                ExecutorProvider.getInstance().runOnMain(() -> liveData.setValue(Result.success(true)));
            } else {
                ExecutorProvider.getInstance()
                        .runOnMain(() -> liveData.setValue(Result.error("Failed to delete: " + file.getName())));
            }
        });
        return liveData;
    }
}