package com.cocode.vcode.ide.data.prefs;

/**
 * Constants for SharedPreferences keys used throughout the application.
 */
public final class PreferenceKeys {

    // Editor
    public static final String FONT_SIZE = "vcode_pref_font_size";
    public static final String TAB_SIZE = "vcode_pref_tab_size";
    public static final String SHOW_LINE_NUMBERS = "vcode_pref_show_line_numbers";
    public static final String AUTO_CLOSE_BRACKETS = "vcode_pref_auto_close_brackets";
    public static final String AUTO_CLOSE_HTML_TAGS = "vcode_pref_auto_close_html_tags";
    public static final String AUTO_CLOSE_QUOTES = "vcode_pref_auto_close_quotes";
    public static final String WORD_WRAP = "vcode_pref_word_wrap";
    public static final String HIGHLIGHT_CURRENT_LINE = "vcode_pref_highlight_current_line";
    public static final String AUTO_INDENT = "vcode_pref_auto_indent";
    public static final String MATCH_BRACKETS = "vcode_pref_match_brackets";
    public static final String JSON_FORMAT_ON_SAVE = "vcode_pref_json_format_on_save";

    // Git
    public static final String GIT_AUTHOR_NAME = "vcode_pref_git_author_name";
    public static final String GIT_AUTHOR_EMAIL = "vcode_pref_git_author_email";
    public static final String GIT_DEFAULT_BRANCH = "vcode_pref_git_default_branch";
    public static final String GIT_AUTO_FETCH = "vcode_pref_git_auto_fetch";
    public static final String GIT_CONFIRM_PUSH = "vcode_pref_git_confirm_push";
    public static final String GIT_CONFIRM_HARD_RESET = "vcode_pref_git_confirm_hard_reset";
    public static final String GIT_SHOW_FILE_STATUS = "vcode_pref_git_show_file_status";
    public static final String GIT_DEFAULT_REMOTE = "vcode_pref_git_default_remote";

    // Appearance
    public static final String THEME = "vcode_pref_theme";

    // Preview
    public static final String OPEN_PREVIEW_IN_APP = "vcode_pref_open_preview_in_app";
    public static final String AUTO_REFRESH_PREVIEW = "vcode_pref_auto_refresh_preview";

    // Auto-Save
    public static final String AUTO_SAVE = "vcode_pref_auto_save";
    public static final String AUTO_SAVE_INTERVAL = "vcode_pref_auto_save_interval";

    // Behavior
    public static final String CONFIRM_ON_TAB_CLOSE = "vcode_pref_confirm_on_tab_close";
    public static final String CONFIRM_ON_PROJECT_DEL = "vcode_pref_confirm_on_project_delete";

    // Misc
    public static final String LAST_PROJECT_ID = "vcode_pref_last_project_id";

    private PreferenceKeys() {
    }
}