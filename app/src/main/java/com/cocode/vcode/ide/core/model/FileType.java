package com.cocode.vcode.ide.core.model;

import com.cocode.vcode.ide.R;

import java.util.Arrays;
import java.util.List;

/**
 * Defines the core set of programming languages and binary assets supported by the vcode workspace.
 * Each file type is bound to its canonical file extensions, user-facing display name,
 * and specific UI branding/syntax highlighting color resources.
 */
public enum FileType {
    // Standard Languages
    HTML(R.drawable.ic_html_icon, R.color.vcode_lang_html, "HTML", true, "html", "htm"),
    CSS(R.drawable.ic_css_icon, R.color.vcode_lang_css, "CSS", true, "css"),
    JAVASCRIPT(R.drawable.ic_js_icon, R.color.vcode_lang_js, "JavaScript", true, "js", "mjs", "cjs"),
    TYPESCRIPT(R.drawable.ic_typescript_icon, R.color.vcode_lang_ts, "TypeScript", true, "ts", "tsx"),
    JSON(R.drawable.ic_json_icon, R.color.vcode_lang_json, "JSON", true, "json"),
    MARKDOWN(R.drawable.ic_md_icon, R.color.vcode_lang_md, "Markdown", true, "md"),
    SCSS(R.drawable.ic_css_icon, R.color.vcode_lang_css, "SCSS", true, "scss", "less"),

    // Text-based Assets
    CSV(R.drawable.ic_csv_icon, R.color.vcode_file_csv, "CSV", true, "csv"),
    MANIFEST(R.drawable.ic_gear, R.color.vcode_file_web_manifest, "Manifest", true, "webmanifest"),
    ENV(R.drawable.ic_env_icon, R.color.vcode_file_env, "Env", true, "env", "local"),
    FIREBASE(R.drawable.ic_firebase_icon, R.color.vcode_file_firebase, "Firebase", true, "firebaserc", "rules"),
    LOG(R.drawable.ic_log_icon, R.color.vcode_file_log, "Log", true, "log"),
    BAK(R.drawable.ic_clock_rotate, R.color.vcode_file_bak, "Backup", true, "bak"),
    GITIGNORE(R.drawable.ic_git, R.color.vcode_file_git, "Git Ignore", true, "gitignore"),
    SVG(R.drawable.ic_bezier_curve, R.color.vcode_file_svg, "SVG", true, "svg"),

    // Binary Assets
    IMAGE(R.drawable.ic_image_icon, R.color.vcode_file_img, "Image", false, "png", "jpg", "jpeg", "webp"),
    GIF(R.drawable.ic_image_icon, R.color.vcode_file_gif, "GIF", false, "gif"),
    ICO(R.drawable.ic_image_icon, R.color.vcode_file_ico, "Icon", false, "ico"),
    BMP(R.drawable.ic_image_icon, R.color.vcode_file_bmp, "BMP", false, "bmp"),
    FONT(R.drawable.ic_font_icon, R.color.vcode_file_font, "Font", false, "woff", "woff2", "ttf", "otf", "eot"),
    AUDIO(R.drawable.ic_audio_icon, R.color.vcode_file_music, "Audio", false, "mp3", "wav", "ogg"),
    VIDEO(R.drawable.ic_video_icon, R.color.vcode_file_video, "Video", false, "mp4", "webm", "mov", "avi"),
    PDF(R.drawable.ic_pdf_icon, R.color.vcode_file_pdf, "PDF", false, "pdf"),

    // Fallback
    TEXT(R.drawable.ic_file_lines, R.color.vcode_accent_primary, "Plain Text", true, "txt"),

    // Virtual Tools
    API_TESTER(R.drawable.ic_globe, R.color.vcode_accent_primary, "API Tester", true, "api");

    private final int iconResId;
    private final int colorResId;
    private final String displayName;
    private final boolean isTextBased;
    private final List<String> extensions;

    FileType(int iconResId, int colorResId, String displayName, boolean isTextBased, String... extensions) {
        this.iconResId = iconResId;
        this.colorResId = colorResId;
        this.displayName = displayName;
        this.isTextBased = isTextBased;
        this.extensions = Arrays.asList(extensions);
    }

    /**
     * Inspects a raw file extension suffix string to identify its file type profile.
     *
     * @param ext The raw extension string pulled from the file name.
     * @return The matching FileType configuration enum, defaulting to TEXT if unmatched or empty.
     */
    public static FileType fromExtension(String ext) {
        if (ext == null || ext.isEmpty()) return TEXT;
        ext = ext.toLowerCase();

        for (FileType type : values()) {
            if (type.extensions.contains(ext)) {
                return type;
            }
        }

        return TEXT; // Default catch-all for unknown formats
    }

    public int getIconResId() {
        return iconResId;
    }

    public int getColorResId() {
        return colorResId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTextBased() {
        return isTextBased;
    }

    /**
     * Returns the LSP language identifier for this file type, as required by
     * {@link com.cocode.vcode.ide.core.lsp.LspServer#getLanguageId()}.
     * Returns {@code "plaintext"} for types that have no language server.
     */
    public String getLspLanguageId() {
        switch (this) {
            case HTML:
                return "html";
            case CSS:
                return "css";
            case SCSS:
                return "scss";
            case JAVASCRIPT:
                return "javascript";
            case TYPESCRIPT:
                return "typescript";
            case JSON:
                return "json";
            case MARKDOWN:
                return "markdown";
            case SVG:
                return "svg";
            default:
                return "plaintext";
        }
    }

    /**
     * Returns true if this file type is a binary asset that cannot be opened as text.
     */
    public boolean isBinaryAsset() {
        return !isTextBased;
    }
}

