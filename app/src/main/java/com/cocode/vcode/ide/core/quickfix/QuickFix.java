package com.cocode.vcode.ide.core.quickfix;

import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.views.CodeEditText;

/**
 * Quick-fix transformation associated with a diagnostic problem.
 */
public interface QuickFix {

    /**
     * @return Human-readable title displayed in menus, bottom sheets, or popups (e.g. "Add alt=\"\" attribute").
     */
    String getTitle();

    /**
     * Executes the fix against the given editor target.
     *
     * @param editor  target editor view or test buffer holding content and undo stack
     * @param problem the diagnostic problem being resolved
     */
    void apply(CodeEditorTarget editor, Problem problem);

    /**
     * Convenience / backward-compatibility overload for CodeEditText.
     */
    default void apply(CodeEditText editor, Problem problem) {
        apply((CodeEditorTarget) editor, problem);
    }
}
