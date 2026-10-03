package com.cocode.vcode.ide.ui.editor.viewer;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;

/**
 * Interface defining the lifecycle and behavior of file viewers in the IDE.
 * Specialized viewers (Code, Image, WebPreview) must implement this.
 */
public interface IFileViewer {

    /**
     * Initializes and returns the primary View for this viewer.
     *
     * @param context The context for inflation/creation.
     * @param parent  The parent ViewGroup (for layout params, do not attach).
     * @return The constructed View.
     */
    View getView(Context context, ViewGroup parent);

    /**
     * Binds the file data to the viewer. This is called when the tab becomes active.
     *
     * @param file      The file to display.
     * @param viewModel The view model for state updates.
     */
    void bindFile(EditorFile file, EditorViewModel viewModel);

    /**
     * Called when the viewer becomes visible (e.g. tab selected).
     */
    void onResume();

    /**
     * Called when the viewer is hidden (e.g. tab deselected).
     * Ideal for saving state, pausing audio/video, etc.
     */
    void onPause();

    /**
     * Called when the tab is closed and the viewer is no longer needed.
     * Ideal for releasing memory, unregistering listeners, etc.
     */
    void destroy();

    /**
     * Flushes any in-memory UI buffer state back into the active EditorFile and ViewModel.
     * Default implementation is a no-op for non-editable or static viewers.
     */
    default void flushContentToViewModel() {
    }

    /**
     * Returns the underlying CodeEditText if this viewer is a code editor.
     * Returns null for non-code viewers. Used for Find/Replace and formatting.
     */
    com.cocode.vcode.ide.views.CodeEditText getCodeEditor();

    /**
     * Returns the currently bound EditorFile, or null if unassigned.
     */
    default EditorFile getCurrentFile() {
        return null;
    }
}
