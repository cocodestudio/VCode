package com.cocode.vcode.ide.ui.editor.viewer;

import android.content.Context;

import androidx.annotation.NonNull;

import com.cocode.vcode.ide.core.events.Disposable;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.EditorFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the lifecycle of dedicated viewer instances for open file tabs.
 * This guarantees zero lag and flicker-free tab switching by keeping
 * views fully rendered in memory.
 * <p>
 * Supports dynamic registration of custom viewers (e.g. contributed by extensions or plugins).
 */
public class ViewerManager {

    @FunctionalInterface
    public interface ViewerPredicate {
        boolean test(@NonNull EditorFile file, boolean isPreviewMode);
    }

    @FunctionalInterface
    public interface ViewerFactory {
        @NonNull
        IFileViewer createViewer(@NonNull Context context, @NonNull EditorFile file, boolean isPreviewMode);
    }

    public static class ViewerRegistration {
        private final ViewerPredicate predicate;
        private final ViewerFactory factory;

        public ViewerRegistration(@NonNull ViewerPredicate predicate, @NonNull ViewerFactory factory) {
            this.predicate = predicate;
            this.factory = factory;
        }

        public boolean matches(@NonNull EditorFile file, boolean isPreviewMode) {
            return predicate != null && predicate.test(file, isPreviewMode);
        }

        @NonNull
        public IFileViewer create(@NonNull Context context, @NonNull EditorFile file, boolean isPreviewMode) {
            return factory.createViewer(context, file, isPreviewMode);
        }
    }

    private static final List<ViewerRegistration> globalViewers = new CopyOnWriteArrayList<>();
    private final List<ViewerRegistration> instanceViewers = new CopyOnWriteArrayList<>();

    // Maps a unique identifier (file ID + mode) to an instantiated viewer.
    private final Map<String, IFileViewer> activeViewers = new HashMap<>();

    /**
     * Registers a global custom viewer factory across all ViewerManager instances.
     *
     * @param predicate Condition under which this custom viewer should be used
     * @param factory   Factory constructing the IFileViewer instance
     * @return Disposable token for unregistering
     */
    @NonNull
    public static Disposable registerGlobalViewer(@NonNull ViewerPredicate predicate, @NonNull ViewerFactory factory) {
        ViewerRegistration reg = new ViewerRegistration(predicate, factory);
        globalViewers.add(0, reg);
        return () -> globalViewers.remove(reg);
    }

    /**
     * Registers an instance-scoped custom viewer factory for this ViewerManager.
     *
     * @param predicate Condition under which this custom viewer should be used
     * @param factory   Factory constructing the IFileViewer instance
     * @return Disposable token for unregistering
     */
    @NonNull
    public Disposable registerViewer(@NonNull ViewerPredicate predicate, @NonNull ViewerFactory factory) {
        ViewerRegistration reg = new ViewerRegistration(predicate, factory);
        instanceViewers.add(0, reg);
        return () -> instanceViewers.remove(reg);
    }

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
            viewer = createViewerFor(context, file, isPreviewMode);
            activeViewers.put(key, viewer);
        }
        return viewer;
    }

    /**
     * Looks up an existing instantiated viewer for the given file ID if cached.
     */
    public IFileViewer getViewer(String fileId) {
        IFileViewer v = activeViewers.get(fileId + "_edit");
        if (v != null) return v;
        return activeViewers.get(fileId + "_preview");
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
     * Evaluates custom instance-level and global registrations before falling back to built-ins.
     */
    private IFileViewer createViewerFor(Context context, EditorFile file, boolean isPreviewMode) {
        // 1. Instance-level custom viewers
        for (ViewerRegistration reg : instanceViewers) {
            if (reg.matches(file, isPreviewMode)) {
                return reg.create(context, file, isPreviewMode);
            }
        }

        // 2. Global custom viewers
        for (ViewerRegistration reg : globalViewers) {
            if (reg.matches(file, isPreviewMode)) {
                return reg.create(context, file, isPreviewMode);
            }
        }

        // 3. Fallback to built-in viewers
        FileType type = file.getFileType();
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
