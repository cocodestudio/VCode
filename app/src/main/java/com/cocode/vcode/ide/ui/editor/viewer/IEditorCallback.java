package com.cocode.vcode.ide.ui.editor.viewer;

import com.cocode.vcode.ide.core.model.Problem;

/**
 * Callback interface to allow viewers to communicate with the hosting EditorActivity
 * for global UI updates like JSON status bar or find/replace bar.
 */
public interface IEditorCallback {

    void reportDiagnosticLoading();
    void reportProblems(java.io.File file, java.util.List<Problem> problems);
}
