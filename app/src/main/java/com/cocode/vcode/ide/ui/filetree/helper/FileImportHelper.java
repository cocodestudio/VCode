package com.cocode.vcode.ide.ui.filetree.helper;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.documentfile.provider.DocumentFile;

import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileOperationManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileImportHelper {

    public static void copyUrisToProject(Context context, List<Uri> uris, File root, Runnable onComplete) {
        if (root == null) return;
        FileOperationManager opManager = FileOperationManager.getInstance(context);
        opManager.startOperation("Importing Files");

        ExecutorProvider.getInstance().runOnIo(() -> {
            int total = uris.size();
            int current = 0;
            boolean success = true;

            try {
                for (Uri uri : uris) {
                    if (opManager.getCancelToken().get()) {
                        success = false;
                        break;
                    }
                    current++;
                    String fileName = getFileNameFromUri(context, uri);
                    if (fileName == null) fileName = "imported_file_" + System.currentTimeMillis();

                    opManager.updateProgress("Importing Files", "Importing: " + fileName + " (" + current + "/" + total + ")", total, current);

                    File destFile = new File(root, fileName);
                    copyStreamToFile(context, uri, destFile, opManager.getCancelToken());
                }
            } catch (Exception e) {
                e.printStackTrace();
                success = false;
            } finally {
                final boolean finalSuccess = success;
                ExecutorProvider.getInstance().runOnMain(() -> {
                    opManager.finishOperation("Import Files", finalSuccess ? "Imported successfully" : "Import failed or cancelled", finalSuccess);
                    if (onComplete != null) onComplete.run();
                });
            }
        });
    }

    public static void copyFolderToProject(Context context, Uri treeUri, File root, Runnable onComplete) {
        if (root == null) return;
        FileOperationManager opManager = FileOperationManager.getInstance(context);
        opManager.startOperation("Importing Folder");

        ExecutorProvider.getInstance().runOnIo(() -> {
            DocumentFile documentFile = DocumentFile.fromTreeUri(context, treeUri);
            boolean[] success = new boolean[]{true};
            int[] fileCount = new int[]{0};

            try {
                if (documentFile != null) {
                    String folderName = documentFile.getName() != null ? documentFile.getName() : "Imported_Folder";
                    File destDir = new File(root, folderName);
                    if (!destDir.exists()) destDir.mkdirs();

                    copyDocumentFileTree(context, documentFile, destDir, opManager.getCancelToken(), opManager, fileCount);
                }
            } catch (Exception e) {
                e.printStackTrace();
                success[0] = false;
            } finally {
                if (opManager.getCancelToken().get()) success[0] = false;

                final boolean finalSuccess = success[0];
                ExecutorProvider.getInstance().runOnMain(() -> {
                    opManager.finishOperation("Import Folder", finalSuccess ? "Imported successfully" : "Import failed or cancelled", finalSuccess);
                    if (onComplete != null) onComplete.run();
                });
            }
        });
    }

    private static void copyDocumentFileTree(Context context, DocumentFile sourceDoc, File destDir, AtomicBoolean cancelToken, FileOperationManager opManager, int[] fileCount) {
        if (cancelToken.get()) return;
        for (DocumentFile file : sourceDoc.listFiles()) {
            if (cancelToken.get()) return;
            String name = file.getName();
            if (name == null) name = "unknown_file_" + System.currentTimeMillis();

            if (file.isDirectory()) {
                File newDir = new File(destDir, name);
                if (!newDir.exists()) newDir.mkdirs();
                copyDocumentFileTree(context, file, newDir, cancelToken, opManager, fileCount);
            } else {
                File newFile = new File(destDir, name);
                copyStreamToFile(context, file.getUri(), newFile, cancelToken);
                fileCount[0]++;
                opManager.updateProgress("Importing Folder", "Importing: " + name + " (" + fileCount[0] + ")", 0, fileCount[0]);
            }
        }
    }

    private static void copyStreamToFile(Context context, Uri sourceUri, File destFile, AtomicBoolean cancelToken) {
        try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(destFile)) {
            if (in == null) return;

            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                if (cancelToken != null && cancelToken.get()) return;
                out.write(buffer, 0, length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getFileNameFromUri(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : 0;
            if (cut != -1) {
                result = result != null ? result.substring(cut + 1) : null;
            }
        }
        return result;
    }
}
