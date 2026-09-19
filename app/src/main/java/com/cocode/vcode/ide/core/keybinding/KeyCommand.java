package com.cocode.vcode.ide.core.keybinding;

import androidx.annotation.StringRes;
import com.cocode.vcode.ide.R;

/**
 * Registry of all bindable keyboard commands in VCode.
 * Uses standard VS Code action identifiers and supports localized display names.
 */
public enum KeyCommand {
    // File
    SAVE_ALL("editor.action.save", R.string.vcode_cmd_save_all, KeyCategory.FILE, "Ctrl+S", Scope.GLOBAL),

    // Workbench / Tabs & Navigation
    CLOSE_TAB("workbench.action.closeActiveEditor", R.string.vcode_cmd_close_tab, KeyCategory.VIEW, "Ctrl+W", Scope.GLOBAL),
    NEXT_TAB("workbench.action.nextEditor", R.string.vcode_cmd_next_tab, KeyCategory.NAVIGATION, "Ctrl+Tab", Scope.GLOBAL),
    PREV_TAB("workbench.action.previousEditor", R.string.vcode_cmd_prev_tab, KeyCategory.NAVIGATION, "Ctrl+Shift+Tab", Scope.GLOBAL),
    QUICK_OPEN("workbench.action.quickOpen", R.string.vcode_cmd_quick_open, KeyCategory.NAVIGATION, "Ctrl+P", Scope.GLOBAL),
    TOGGLE_SIDEBAR("workbench.action.toggleSidebar", R.string.vcode_cmd_toggle_sidebar, KeyCategory.VIEW, "Ctrl+B", Scope.GLOBAL),
    SHOW_PROBLEMS("workbench.action.showProblems", R.string.vcode_cmd_show_problems, KeyCategory.VIEW, "Ctrl+Shift+M", Scope.GLOBAL),
    SHOW_SNIPPETS("workbench.action.showSnippets", R.string.vcode_cmd_show_snippets, KeyCategory.EDITOR, "Ctrl+Shift+P", Scope.GLOBAL),
    RUN_PREVIEW("workbench.action.runPreview", R.string.vcode_cmd_run_preview, KeyCategory.VIEW, "F5", Scope.GLOBAL),
    OPEN_SETTINGS("workbench.action.openSettings", R.string.vcode_cmd_open_settings, KeyCategory.VIEW, "Ctrl+,", Scope.GLOBAL),
    OPEN_GIT("workbench.action.openGit", R.string.vcode_cmd_open_git, KeyCategory.GIT, "Ctrl+Shift+G", Scope.GLOBAL),
    SHOW_SHORTCUTS("workbench.action.showShortcuts", R.string.vcode_cmd_show_shortcuts, KeyCategory.VIEW, "F1", Scope.GLOBAL),
    TOGGLE_READ_ONLY("workbench.action.toggleEditorReadOnly", R.string.vcode_cmd_toggle_read_only, KeyCategory.VIEW, "Alt+R", Scope.GLOBAL),

    // Search & Navigation
    FIND("editor.action.find", R.string.vcode_cmd_find, KeyCategory.SEARCH, "Ctrl+F", Scope.GLOBAL),
    REPLACE("editor.action.replace", R.string.vcode_cmd_replace, KeyCategory.SEARCH, "Ctrl+H", Scope.GLOBAL),
    GO_TO_LINE("editor.action.goToLine", R.string.vcode_cmd_go_to_line, KeyCategory.NAVIGATION, "Ctrl+G", Scope.GLOBAL),

    // Editor Actions
    UNDO("editor.action.undo", R.string.vcode_cmd_undo, KeyCategory.EDITOR, "Ctrl+Z", Scope.EDITOR),
    REDO("editor.action.redo", R.string.vcode_cmd_redo, KeyCategory.EDITOR, "Ctrl+Y", Scope.EDITOR),
    SELECT_ALL("editor.action.selectAll", R.string.vcode_cmd_select_all, KeyCategory.EDITOR, "Ctrl+A", Scope.EDITOR),
    CUT("editor.action.cut", R.string.vcode_cmd_cut, KeyCategory.EDITOR, "Ctrl+X", Scope.EDITOR),
    COPY("editor.action.copy", R.string.vcode_cmd_copy, KeyCategory.EDITOR, "Ctrl+C", Scope.EDITOR),
    PASTE("editor.action.paste", R.string.vcode_cmd_paste, KeyCategory.EDITOR, "Ctrl+V", Scope.EDITOR),
    FORMAT_DOCUMENT("editor.action.formatDocument", R.string.vcode_cmd_format_document, KeyCategory.EDITOR, "Ctrl+Shift+F", Scope.GLOBAL),
    EXTRACT_CSS("editor.action.extractCss", R.string.vcode_cmd_extract_css, KeyCategory.EDITOR, "Ctrl+Shift+C", Scope.GLOBAL),
    EXTRACT_JS("editor.action.extractJs", R.string.vcode_cmd_extract_js, KeyCategory.EDITOR, "Ctrl+Shift+J", Scope.GLOBAL),
    TOGGLE_COMMENT("editor.action.commentLine", R.string.vcode_cmd_toggle_comment, KeyCategory.EDITOR, "Ctrl+/", Scope.EDITOR),
    INDENT("editor.action.indent", R.string.vcode_cmd_indent, KeyCategory.EDITOR, "Tab", Scope.EDITOR),
    OUTDENT("editor.action.outdent", R.string.vcode_cmd_outdent, KeyCategory.EDITOR, "Shift+Tab", Scope.EDITOR),
    TRIGGER_AUTOCOMPLETE("editor.action.triggerSuggest", R.string.vcode_cmd_trigger_autocomplete, KeyCategory.EDITOR, "Ctrl+Space", Scope.EDITOR),

    // Line Operations
    DUPLICATE_LINE("editor.action.duplicateLine", R.string.vcode_cmd_duplicate_line, KeyCategory.LINE, "Ctrl+D", Scope.EDITOR),
    DELETE_LINE("editor.action.deleteLine", R.string.vcode_cmd_delete_line, KeyCategory.LINE, "Ctrl+Shift+K", Scope.EDITOR),
    MOVE_LINE_UP("editor.action.moveLineUp", R.string.vcode_cmd_move_line_up, KeyCategory.LINE, "Alt+Up", Scope.EDITOR),
    MOVE_LINE_DOWN("editor.action.moveLineDown", R.string.vcode_cmd_move_line_down, KeyCategory.LINE, "Alt+Down", Scope.EDITOR),
    COPY_LINE_UP("editor.action.copyLineUp", R.string.vcode_cmd_copy_line_up, KeyCategory.LINE, "Shift+Alt+Up", Scope.EDITOR),
    COPY_LINE_DOWN("editor.action.copyLineDown", R.string.vcode_cmd_copy_line_down, KeyCategory.LINE, "Shift+Alt+Down", Scope.EDITOR),
    INSERT_LINE_BELOW("editor.action.insertLineBelow", R.string.vcode_cmd_insert_line_below, KeyCategory.LINE, "Ctrl+Enter", Scope.EDITOR),
    INSERT_LINE_ABOVE("editor.action.insertLineAbove", R.string.vcode_cmd_insert_line_above, KeyCategory.LINE, "Ctrl+Shift+Enter", Scope.EDITOR);

    public enum KeyCategory {
        FILE(R.string.vcode_key_category_file),
        NAVIGATION(R.string.vcode_key_category_navigation),
        EDITOR(R.string.vcode_key_category_editor),
        LINE(R.string.vcode_key_category_line),
        SEARCH(R.string.vcode_key_category_search),
        VIEW(R.string.vcode_key_category_view),
        GIT(R.string.vcode_key_category_git);

        @StringRes
        public final int titleResId;

        KeyCategory(@StringRes int titleResId) {
            this.titleResId = titleResId;
        }
    }

    public enum Scope {
        GLOBAL,
        EDITOR
    }

    private final String commandId;
    @StringRes
    private final int titleResId;
    private final KeyCategory category;
    private final String defaultKeySpec;
    private final Scope scope;

    KeyCommand(String commandId, @StringRes int titleResId, KeyCategory category, String defaultKeySpec, Scope scope) {
        this.commandId = commandId;
        this.titleResId = titleResId;
        this.category = category;
        this.defaultKeySpec = defaultKeySpec;
        this.scope = scope;
    }

    public String getCommandId() {
        return commandId;
    }

    @StringRes
    public int getTitleResId() {
        return titleResId;
    }

    public KeyCategory getCategory() {
        return category;
    }

    public String getDefaultKeySpec() {
        return defaultKeySpec;
    }

    public Scope getScope() {
        return scope;
    }

    public static KeyCommand fromCommandId(String id) {
        if (id == null) return null;
        String trimmed = id.trim();
        for (KeyCommand cmd : values()) {
            if (cmd.commandId.equalsIgnoreCase(trimmed)) {
                return cmd;
            }
        }
        return null;
    }
}
