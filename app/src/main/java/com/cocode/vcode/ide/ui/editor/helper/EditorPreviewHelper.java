package com.cocode.vcode.ide.ui.editor.helper;

import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.ui.editor.EditorActivity;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;
import com.cocode.vcode.ide.ui.preview.PreviewActivity;
import com.cocode.vcode.ide.utils.LocalWebServer;

import java.util.List;

public class EditorPreviewHelper {

    public static LocalWebServer handleRunAction(EditorActivity activity, EditorViewModel viewModel, LocalWebServer localWebServer, PreviewCallbacks callbacks, Runnable stopServerUI, Runnable startServerUI) {
        if (localWebServer != null && localWebServer.isRunning()) {
            localWebServer.stop();
            stopServerUI.run();
            Toast.makeText(activity, R.string.vcode_server_stopped, Toast.LENGTH_SHORT).show();
            callbacks.updateToolbarVisibility();
            return localWebServer;
        }

        int activeIndex = viewModel.getActiveTabIndex().getValue() != null ? viewModel.getActiveTabIndex().getValue() : -1;
        List<EditorFile> files = viewModel.getOpenFiles().getValue();

        if (files == null || activeIndex < 0 || activeIndex >= files.size()) {
            Toast.makeText(activity, R.string.vcode_open_a_file_first_to, Toast.LENGTH_SHORT).show();
            return localWebServer;
        }

        if (localWebServer == null) {
            localWebServer = new LocalWebServer(viewModel.getProjectRoot());
        }
        localWebServer.start();
        startServerUI.run();
        callbacks.executeActiveFilePreviewIntent();
        callbacks.updateToolbarVisibility();
        return localWebServer;
    }

    public static void toggleInlinePreview(EditorViewModel viewModel, PreviewCallbacks callbacks) {
        int activeIndex = viewModel.getActiveTabIndex().getValue() != null ? viewModel.getActiveTabIndex().getValue() : -1;
        List<EditorFile> files = viewModel.getOpenFiles().getValue();
        if (files == null || activeIndex < 0 || activeIndex >= files.size()) return;

        EditorFile activeFile = files.get(activeIndex);
        FileType type = activeFile.getFileType();
        if (type != FileType.SVG && type != FileType.CSV && type != FileType.MARKDOWN) return;

        String relPath = activeFile.getRelativePath(viewModel.getProjectRoot());
        boolean isPreviewMode = viewModel.getPreviewState(relPath);

        viewModel.setPreviewState(relPath, !isPreviewMode);
        callbacks.updateActiveViewer(activeFile, !isPreviewMode);
    }

    public static void executeActiveFilePreviewIntent(EditorActivity activity, EditorViewModel viewModel, LocalWebServer localWebServer) {
        if (localWebServer == null) return;
        int activeIndex = viewModel.getActiveTabIndex().getValue() != null ? viewModel.getActiveTabIndex().getValue() : -1;
        List<EditorFile> files = viewModel.getOpenFiles().getValue();
        String path = "";
        if (files != null && activeIndex >= 0 && activeIndex < files.size()) {
            path = files.get(activeIndex).getRelativePath(viewModel.getProjectRoot());
        }

        String serverUrl = localWebServer.getUrl(path);
        AppSettings settings = viewModel.getSettingsLiveData().getValue();
        boolean openInApp = settings == null || settings.openPreviewInApp;

        if (openInApp) {
            Intent intent = new Intent(activity, PreviewActivity.class);
            intent.putExtra(PreviewActivity.EXTRA_URL, serverUrl);
            if (viewModel.getProjectRoot() != null) {
                intent.putExtra(PreviewActivity.EXTRA_PROJECT_PATH, viewModel.getProjectRoot().getAbsolutePath());
            }
            activity.startActivity(intent);
        } else {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl));
                activity.startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(activity, R.string.vcode_no_browser_app_found_to, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public interface PreviewCallbacks {
        void updateToolbarVisibility();

        void executeActiveFilePreviewIntent();

        void updateActiveViewer(EditorFile file, boolean isPreview);
    }
}
