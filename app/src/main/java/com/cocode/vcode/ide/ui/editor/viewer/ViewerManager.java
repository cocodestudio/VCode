package com.cocode.vcode.ide.ui.editor.viewer;

import android.content.Context;

import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.EditorFile;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the lifecycle of dedicated viewer instances for open file tabs.
 * This guarantees zero lag and flicker-free tab switching by keeping
 * views fully rendered in memory.
 */
public class ViewerManager {

    // Maps a unique identifier (file ID + mode) to an instantiated viewer.
    private final Map<String, IFileViewer> activeViewers = new HashMap<>();

    /**
     * Retrieves an existing viewer for the file, or creates a new one if it doesn't exist.
     *
     * @param context       The Activity context for creating views.
     * @param file          The file being opened.
     * @param isPreviewMode Whether the file should be opened in preview mode (e.g. Markdown/SVG).
     * @return The dedicated IFileViewer instance.
     */
    public IFileViewer getOrCreateViewer(Context context, EditorFile file, boolean isPreviewMode) {
        // Create a unique key per file and mode so we can have both Code and Preview cached.
        String key = file.getId() + (isPreviewMode ? "_preview" : "_edit");

        IFileViewer viewer = activeViewers.get(key);
        if (viewer == null) {
            viewer = createViewerFor(file.getFileType(), isPreviewMode);
            activeViewers.put(key, viewer);
        }
        return viewer;
    }

    /**
     * Destroys all viewers associated with a closed file to free memory.
     *
     * @param fileId The unique ID of the file being closed.
     */
    public void destroyViewer(String fileId) {
        String editKey = fileId + "_edit";
        String previewKey = fileId + "_preview";

        IFileViewer editViewer = activeViewers.remove(editKey);
        if (editViewer != null) editViewer.destroy();

        IFileViewer previewViewer = activeViewers.remove(previewKey);
        if (previewViewer != null) previewViewer.destroy();
    }

    /**
     * Destroys all viewers.
     */
    public void destroyAll() {
        for (IFileViewer viewer : activeViewers.values()) {
            viewer.destroy();
        }
        activeViewers.clear();
    }

    /**
     * Factory method for creating the appropriate viewer implementation.
     */
    private IFileViewer createViewerFor(FileType type, boolean isPreviewMode) {
        if (type == FileType.IMAGE || type == FileType.GIF || type == FileType.ICO || type == FileType.BMP) {
            return new ImageFileViewer();
        } else if (type == FileType.FONT) {
            return new FontFileViewer();
        } else if (isPreviewMode && (type == FileType.SVG || type == FileType.CSV || type == FileType.MARKDOWN)) {
            return new WebPreviewViewer();
        } else if (type == FileType.API_TESTER) {
            return new ApiTesterViewer();
        } else if (type == FileType.TERMINAL) {
            return new TerminalViewer();
        } else {
            return new CodeFileViewer();
        }
    }
}
