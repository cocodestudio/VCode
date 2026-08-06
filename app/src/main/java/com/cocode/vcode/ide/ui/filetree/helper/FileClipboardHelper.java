package com.cocode.vcode.ide.ui.filetree.helper;

import android.content.Context;

import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileOperationManager;
import com.cocode.vcode.ide.utils.FileUtils;

import java.io.File;

public class FileClipboardHelper {

    public static void performPaste(Context context, File source, boolean isCut, File destinationDir, PasteCallback callback) {
        if (source == null || !source.exists() || destinationDir == null) return;

        FileOperationManager opManager = FileOperationManager.getInstance(context);
        opManager.startOperation("Pasting...");

        ExecutorProvider.getInstance().runOnIo(() -> {
            File target = new File(destinationDir, source.getName());
            boolean success = false;

            try {
                int counter = 1;
                String baseName = source.getName();
                String extension = "";
                int dotIndex = baseName.lastIndexOf('.');
                if (dotIndex > 0) {
                    extension = baseName.substring(dotIndex);
                    baseName = baseName.substring(0, dotIndex);
                }
                while (target.exists()) {
                    target = new File(destinationDir, baseName + "_" + counter + extension);
                    counter++;
                }

                FileUtils.ProgressListener listener = getProgressListener(opManager);

                if (isCut) {
                    success = source.renameTo(target);
                    if (!success) {
                        if (source.isDirectory()) {
                            success = FileUtils.copyDirectory(source, target, opManager.getCancelToken(), listener) && FileUtils.deleteRecursive(source);
                        } else {
                            success = FileUtils.copyFile(source, target, opManager.getCancelToken(), listener) && source.delete();
                        }
                    }
                } else {
                    if (source.isDirectory()) {
                        success = FileUtils.copyDirectory(source, target, opManager.getCancelToken(), listener);
                    } else {
                        success = FileUtils.copyFile(source, target, opManager.getCancelToken(), listener);
                    }
                }

                if (opManager.getCancelToken().get()) {
                    success = false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                success = false;
            } finally {
                final boolean finalSuccess = success;
                ExecutorProvider.getInstance().runOnMain(() -> {
                    opManager.finishOperation("Paste", finalSuccess ? "Pasted successfully" : "Paste failed or cancelled", finalSuccess);
                    if (callback != null) callback.onPasteComplete(finalSuccess);
                });
            }
        });
    }

    private static FileUtils.ProgressListener getProgressListener(FileOperationManager opManager) {
        long[] bytesCopied = new long[]{0};
        long[] lastUpdate = new long[]{0};
        return (file, read) -> {
            bytesCopied[0] += read;
            long now = System.currentTimeMillis();
            if (now - lastUpdate[0] > 500) {
                opManager.updateProgress("Pasting...", "Pasting: " + file.getName(), 0, 0);
                lastUpdate[0] = now;
            }
        };
    }

    public interface PasteCallback {
        void onPasteComplete(boolean success);
    }
}
