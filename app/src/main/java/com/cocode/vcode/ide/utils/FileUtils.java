package com.cocode.vcode.ide.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import com.cocode.vcode.ide.data.model.FileNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Multi-purpose file manager providing core primitives for the IDE file manager.
 * Performs nested recursive deletions, deep branch duplications, tree construction indexes,
 * and handles localized system file provider interactions.
 */
public class FileUtils {

    private static final String PROJECTS_DIR_NAME = "VCodeProjects";

    private FileUtils() {
    }

    /**
     * Reads string blocks out of targeted disk files using strict UTF-8 normalization mappings.
     */
    public static String readFile(File file) throws IOException {
        if (file == null || !file.exists()) throw new IOException("File not found: " + file);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        // Remove trailing newline added by readLine loop to match the pristine document footprint
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * Persists text character logs into selected disk paths, initializing parent scopes as needed.
     */
    public static void writeFile(File file, String content) throws IOException {
        if (file == null) throw new IOException("File is null");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(content == null ? "" : content);
        }
    }

    /**
     * Extracts lowercase file extensions components from trailing dot delimiters.
     */
    public static String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1).toLowerCase();
    }


    /**
     * Creates new files on the file system, failing early if an object already occupies that name.
     */
    public static File createFile(File dir, String name) throws IOException {
        if (dir == null || name == null || name.isEmpty())
            throw new IOException("Invalid directory or file name");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, name);
        if (file.exists()) throw new IOException("File already exists: " + name);
        if (!file.createNewFile()) throw new IOException("Could not create file: " + name);
        return file;
    }

    /**
     * Generates directory subfolder branches down to target directory coordinates.
     */
    public static void createFolder(File dir, String name) throws IOException {
        if (dir == null || name == null || name.isEmpty())
            throw new IOException("Invalid directory or folder name");
        File folder = new File(dir, name);
        if (folder.exists()) throw new IOException("Folder already exists: " + name);
        if (!folder.mkdirs()) throw new IOException("Could not create folder: " + name);
    }

    /**
     * Recursively steps through internal directory sub-trees to completely wipe files from storage.
     */
    public static boolean deleteRecursive(File file) {
        if (file == null || !file.exists()) return false;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    /**
     * Duplicates data content chunks between files via an intermediate buffer block allocation stream.
     */
    public static boolean copyFile(File src, File dst, java.util.concurrent.atomic.AtomicBoolean isCancelled, ProgressListener listener) {
        if (src == null || !src.exists() || dst == null) return false;
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (isCancelled != null && isCancelled.get()) {
                    return false;
                }
                out.write(buffer, 0, read);
                if (listener != null) listener.onProgress(src, read);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean copyFile(File src, File dst) {
        return copyFile(src, dst, null, null);
    }

    /**
     * Clones complex directory trees by systematically mapping subfolders and files.
     */
    public static boolean copyDirectory(File src, File dst, java.util.concurrent.atomic.AtomicBoolean isCancelled, ProgressListener listener) {
        if (src == null || !src.isDirectory()) return false;
        if (isCancelled != null && isCancelled.get()) return false;
        if (!dst.exists()) dst.mkdirs();
        File[] children = src.listFiles();
        if (children == null) return true;
        for (File child : children) {
            if (isCancelled != null && isCancelled.get()) return false;
            File dstChild = new File(dst, child.getName());
            if (child.isDirectory()) {
                if (!copyDirectory(child, dstChild, isCancelled, listener)) return false;
            } else {
                if (!copyFile(child, dstChild, isCancelled, listener)) return false;
            }
        }
        return true;
    }

    public static boolean copyDirectory(File src, File dst) {
        return copyDirectory(src, dst, null, null);
    }

    /**
     * Modifies local directory entity names within shared parent folders.
     */
    public static void renameFile(File file, String newName) throws IOException {
        if (file == null || !file.exists()) throw new IOException("File not found");
        if (newName == null || newName.isEmpty()) throw new IOException("New name is empty");
        File renamed = new File(file.getParentFile(), newName);
        if (renamed.exists())
            throw new IOException("A file named '" + newName + "' already exists");
        if (!file.renameTo(renamed)) throw new IOException("Could not rename file");
    }

    /**
     * Navigates local shared user memory scopes to configure the app workspace root dir.
     */
    public static File getProjectsDir(Context ctx) {
        File root = Environment.getExternalStorageDirectory();
        File dir = new File(root, PROJECTS_DIR_NAME);

        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Inform external media indexes to ignore internal code configurations scripts text data
        File nomedia = new File(dir, ".nomedia");
        if (!nomedia.exists()) {
            try {
                nomedia.createNewFile();
            } catch (IOException ignored) {
            }
        }

        return dir;
    }

    /**
     * Forwards a file out to external device viewers by computing file provider intent mappings.
     */
    public static void openFileExternally(Context context, java.io.File file) {
        try {
            String authority = context.getPackageName() + ".fileprovider";
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file);

            String mimeType = "*/*";
            String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if (extension != null && !extension.isEmpty()) {
                String type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
                if (type != null) {
                    mimeType = type;
                }
            }

            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);

            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(android.content.Intent.createChooser(intent, "Open " + file.getName() + " with..."));

        } catch (Exception e) {
            android.widget.Toast.makeText(context, "No app found to open this file.", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Generates a fully populated navigation model representable in sidebar layout folders views.
     */
    public static List<FileNode> buildFileTree(File root) {
        FileNode rootNode = new FileNode(root, 0);
        rootNode.setExpanded(true);
        rootNode.setChildren(buildFileTreeRecursive(root, 1));

        List<FileNode> tree = new ArrayList<>();
        tree.add(rootNode);
        return tree;
    }

    /**
     * Sorts folders above plain documents, alphabetically filtering internal versioning
     * metadata elements like .git profiles or tracking sheets from presentation views.
     */
    private static List<FileNode> buildFileTreeRecursive(File dir, int depth) {
        List<FileNode> nodes = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) return nodes;
        File[] entries = dir.listFiles();
        if (entries == null) return nodes;

        Arrays.sort(entries, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File entry : entries) {
            String name = entry.getName();

            // Explicitly exclude .git, and .nomedia. Allow all other dotfiles (like .env)
            if (name.equals(".git") || name.equals(".nomedia")) {
                continue;
            }

            // Hide internal IDE state files from the UI
            if (name.equals("project_meta.json") || name.equals("session.json")) {
                continue;
            }

            FileNode node = new FileNode(entry, depth);
            if (entry.isDirectory()) {
                node.setChildren(buildFileTreeRecursive(entry, depth + 1));
            }
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * Converts a file size metric integer value into a legible text display scale descriptor.
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f);
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f));
    }

    /**
     * Walks recursive data tracks to compute overall file aggregates within a target workspace path.
     */
    public static int countFilesInDir(File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;

        int count = 0;
        for (File f : files) {
            String name = f.getName();

            if (name.equals(".git") || name.equals(".gitignore") || name.equals(".nomedia")) {
                continue;
            }

            if (name.equals("project_meta.json") || name.equals("session.json")) {
                continue;
            }

            if (f.isFile()) {
                count++;
            } else if (f.isDirectory()) {
                count += countFilesInDir(f);
            }
        }
        return count;
    }

    /**
     * Returns the root VCodeProjects directory on external storage.
     */
    public static File getProjectsDirectory() {
        return new File(Environment.getExternalStorageDirectory(), PROJECTS_DIR_NAME);
    }

    /**
     * Given a file, determines the best project root for the VCode editor.
     * - If the file lives inside VCodeProjects/, returns the appropriate top-level project folder.
     * - Otherwise returns the file's parent directory.
     */
    public static File resolveProjectRoot(File file) {
        File projectsDir = getProjectsDirectory();
        String filePath = file.getAbsolutePath();
        String pdPath = projectsDir.getAbsolutePath();

        if (filePath.startsWith(pdPath + File.separator)) {
            // Walk up until the immediate child of VCodeProjects is found
            File candidate = file.getParentFile();
            while (candidate != null) {
                File parent = candidate.getParentFile();
                if (parent != null && parent.getAbsolutePath().equals(pdPath)) {
                    return candidate;
                }
                candidate = parent;
            }
        }

        // Fallback: use the file's parent directory as a standalone project root
        File parent = file.getParentFile();
        return (parent != null && parent.exists()) ? parent : file;
    }

    /**
     * Resolves an Android URI (file:// or content://) to a java.io.File.
     * <p>
     * Strategy for content:// URIs:
     * 1. Try to resolve the real on-disk path (via _data column or DocumentsContract).
     * This handles local file managers (Files by Google, Solid Explorer, etc.) which
     * always send content:// URIs even for plain local files.
     * 2. Only if no real path exists (true cloud providers: Drive, WhatsApp, Gmail, etc.)
     * copy the stream into the app's cache directory.
     * <p>
     * Returns null if the URI cannot be resolved.
     */
    public static File resolveUri(Context context, Uri uri) {
        if (uri == null) return null;
        String scheme = uri.getScheme();

        if ("file".equalsIgnoreCase(scheme)) {
            String path = uri.getPath();
            return path != null ? new File(path) : null;
        }

        if ("content".equalsIgnoreCase(scheme)) {
            // Step 1: Try to resolve to a real file path (local files from file managers)
            File realFile = resolveContentUriToRealFile(context, uri);
            if (realFile != null && realFile.exists()) {
                return realFile;
            }

            // Step 2: Cloud / sandboxed providers — copy stream to the app cache
            return copyUriToCache(context, uri);
        }

        return null;
    }

    /**
     * Attempts to resolve a content:// URI to a real file-system path without copying.
     * Returns null when the URI belongs to a cloud or sandboxed provider.
     */
    private static File resolveContentUriToRealFile(Context context, Uri uri) {
        // Method A: _data column (works for many MediaStore and legacy content providers)
        try (android.database.Cursor cursor = context.getContentResolver().query(
                uri, new String[]{"_data"}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex("_data");
                if (idx >= 0) {
                    String path = cursor.getString(idx);
                    if (path != null && !path.isEmpty()) {
                        File f = new File(path);
                        if (f.exists()) return f;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Method B: DocumentsContract — handles Storage Access Framework URIs
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            try {
                if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
                    String authority = uri.getAuthority();

                    // Primary external storage (e.g. /sdcard/...)
                    if ("com.android.externalstorage.documents".equals(authority)) {
                        String docId = android.provider.DocumentsContract.getDocumentId(uri);
                        String[] parts = docId.split(":", 2);
                        if (parts.length == 2 && "primary".equalsIgnoreCase(parts[0])) {
                            File f = new File(Environment.getExternalStorageDirectory(), parts[1]);
                            if (f.exists()) return f;
                        }
                    }

                    // Downloads provider
                    if ("com.android.providers.downloads.documents".equals(authority)) {
                        String id = android.provider.DocumentsContract.getDocumentId(uri);
                        if (id != null && id.startsWith("raw:")) {
                            File f = new File(id.substring(4));
                            if (f.exists()) return f;
                        }
                        if (id != null) {
                            try {
                                Uri dlUri = android.content.ContentUris.withAppendedId(
                                        Uri.parse("content://downloads/public_downloads"),
                                        Long.parseLong(id));
                                try (android.database.Cursor c = context.getContentResolver().query(
                                        dlUri, new String[]{"_data"}, null, null, null)) {
                                    if (c != null && c.moveToFirst()) {
                                        int idx = c.getColumnIndex("_data");
                                        if (idx >= 0) {
                                            String path = c.getString(idx);
                                            if (path != null && !path.isEmpty()) {
                                                File f = new File(path);
                                                if (f.exists()) return f;
                                            }
                                        }
                                    }
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }

                    // Media (image/video/audio) provider
                    if ("com.android.providers.media.documents".equals(authority)) {
                        String docId = android.provider.DocumentsContract.getDocumentId(uri);
                        String[] parts = docId.split(":", 2);
                        if (parts.length == 2) {
                            Uri mediaUri;
                            switch (parts[0]) {
                                case "image":
                                    mediaUri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                                    break;
                                case "video":
                                    mediaUri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                                    break;
                                case "audio":
                                    mediaUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                                    break;
                                default:
                                    mediaUri = null;
                            }
                            if (mediaUri != null) {
                                try (android.database.Cursor c = context.getContentResolver().query(
                                        mediaUri, new String[]{"_data"}, "_id=?", new String[]{parts[1]}, null)) {
                                    if (c != null && c.moveToFirst()) {
                                        int idx = c.getColumnIndex("_data");
                                        if (idx >= 0) {
                                            String path = c.getString(idx);
                                            if (path != null && !path.isEmpty()) {
                                                File f = new File(path);
                                                if (f.exists()) return f;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return null; // Could not resolve — caller will copy to cache
    }

    /**
     * Copies a content:// stream into the app's cache directory.
     * Used only for true cloud / sandboxed providers where a real path cannot be obtained.
     * Infers the file extension from the MIME type when the display name has none
     * (common for WhatsApp, Gmail attachments, etc.).
     */
    private static File copyUriToCache(Context context, Uri uri) {
        // Get the display name from the provider
        String fileName = null;
        try (android.database.Cursor cursor = context.getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                fileName = cursor.getString(0);
            }
        } catch (Exception ignored) {
        }

        if (fileName == null || fileName.isEmpty()) {
            fileName = "vcode_tmp_" + System.currentTimeMillis();
        }

        // If the display name has no extension, infer it from the MIME type.
        // WhatsApp, Gmail, and many other apps omit extensions from display names.
        if (!fileName.contains(".")) {
            String mimeType = context.getContentResolver().getType(uri);
            if (mimeType != null) {
                String ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                if (ext != null && !ext.isEmpty()) {
                    fileName = fileName + "." + ext;
                }
            }
        }

        // Sanitize: keep letters, digits, dots, hyphens, and underscores
        fileName = fileName.replaceAll("[^a-zA-Z0-9._\\-]", "_");

        File cacheRoot = new File(context.getCacheDir(), "vcode_open");

        // Each external file gets its own subdirectory named after the file itself.
        // resolveProjectRoot() will return that subdirectory, so its getName() produces
        // the actual filename (e.g. "index.html") rather than "vcode_open".
        File cacheDir = new File(cacheRoot, fileName);
        if (!cacheDir.exists()) cacheDir.mkdirs();
        File dest = new File(cacheDir, fileName);

        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(dest)) {
            if (in == null) return null;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return dest;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Writes UTF-8 content back to a content:// URI via ContentResolver.
     * Used to save edits back to files opened from external providers (e.g. Google Drive).
     */
    public static void writeToUri(Context context, Uri uri, String content) throws IOException {
        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Cannot open output stream for URI: " + uri);
            out.write(content != null ? content.getBytes(StandardCharsets.UTF_8) : new byte[0]);
        }
    }

    public interface ProgressListener {
        void onProgress(File file, long bytesRead);
    }
}