package com.cocode.vcode.ide.ui.editor;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;

import java.io.File;

/**
 * Transparent trampoline Activity that handles ACTION_VIEW intents for supported file types
 * dispatched by external apps (file managers, email clients, browsers).
 * <p>
 * It resolves the incoming URI to a local File, determines the correct project root,
 * then immediately launches EditorActivity before finishing itself — no UI is ever shown.
 */
public class FileOpenActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            finish();
            return;
        }

        Uri uri = intent.getData();
        if (uri == null) {
            finish();
            return;
        }

        final boolean isContentUri = "content".equalsIgnoreCase(uri.getScheme());

        // URI resolution may involve IO (content:// copy) — dispatch to background thread
        ExecutorProvider.getInstance().runOnIo(() -> {
            File file = FileUtils.resolveUri(FileOpenActivity.this, uri);

            if (file == null || !file.exists() || !file.isFile()) {
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.vcode_could_not_open_file, Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }

            // Only attach the source URI for write-back if the file was actually copied into the
            // app's cache (true cloud/sandboxed providers: Drive, WhatsApp, Gmail...).
            // For local files that were resolved to their real path, no write-back is needed.
            boolean isInCache = file.getAbsolutePath().startsWith(
                    getCacheDir().getAbsolutePath());
            final String sourceUriString = (isContentUri && isInCache) ? uri.toString() : null;

            // Check that the file extension is supported — reject gracefully if not
            String ext = FileUtils.getExtension(file.getName());
            FileType fileType = FileType.fromExtension(ext);
            if (fileType != null && !fileType.isTextBased()) {
                // Binary files can't be edited as text
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.vcode_vcode_cannot_edit_binary_files, Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }

            File projectRoot = FileUtils.resolveProjectRoot(file);
            String projectName = projectRoot.getName();
            // Build a stable project ID from the path
            String projectId = projectRoot.getAbsolutePath()
                    .replace(File.separator, "_")
                    .replaceAll("[^a-zA-Z0-9_]", "");

            runOnUiThread(() -> {
                Intent editorIntent = new Intent(this, EditorActivity.class);
                editorIntent.putExtra(EditorActivity.EXTRA_PROJECT_PATH, projectRoot.getAbsolutePath());
                editorIntent.putExtra(EditorActivity.EXTRA_PROJECT_ID, projectId);
                editorIntent.putExtra(EditorActivity.EXTRA_PROJECT_NAME, projectName);
                editorIntent.putExtra(EditorActivity.EXTRA_OPEN_FILE_PATH, file.getAbsolutePath());
                if (sourceUriString != null) {
                    editorIntent.putExtra(EditorActivity.EXTRA_SOURCE_URI, sourceUriString);
                }
                startActivity(editorIntent);
                finish();
            });
        });
    }
}
