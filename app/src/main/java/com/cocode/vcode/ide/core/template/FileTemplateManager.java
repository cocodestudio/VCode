package com.cocode.vcode.ide.core.template;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.cocode.vcode.ide.utils.FileUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages configurable file templates, disk serialization (templates.json),
 * extension matching, placeholder evaluation, and in-editor synchronization.
 */
public class FileTemplateManager {

    public static final String TEMPLATES_FILE_NAME = "templates.json";
    public static final String TEMPLATES_DIR_NAME = "templates";

    /**
     * Set of well-known binary file extensions that cannot be created or edited as text templates.
     */
    private static final Set<String> BINARY_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // Images
            "png", "jpg", "jpeg", "webp", "gif", "ico", "bmp", "tiff", "tif", "heic", "avif", "psd", "raw",
            // Audio & Video
            "mp3", "wav", "ogg", "m4a", "flac", "aac", "wma", "mp4", "webm", "mov", "avi", "mkv", "wmv", "3gp", "flv",
            // Documents & Archives
            "pdf", "zip", "tar", "gz", "7z", "rar", "bz2", "xz", "iso", "dmg",
            // Executables, Binaries & Bytecode
            "exe", "dll", "so", "dylib", "bin", "apk", "aab", "jar", "class", "dex", "o", "obj",
            // Fonts
            "woff", "woff2", "ttf", "otf", "eot"
    )));

    private static volatile FileTemplateManager sInstance;

    private final Context appContext;
    private final File internalConfigFile;
    private final List<FileTemplate> templates = new CopyOnWriteArrayList<>();
    private final Map<String, FileTemplate> extensionMap = new ConcurrentHashMap<>();
    private final List<OnTemplatesChangedListener> listeners = new CopyOnWriteArrayList<>();

    public interface OnTemplatesChangedListener {
        void onTemplatesChanged();
    }

    private FileTemplateManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.internalConfigFile = new File(appContext.getFilesDir(), TEMPLATES_FILE_NAME);
        loadDefaults();
        loadFromFile();
    }

    public static FileTemplateManager getInstance(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (FileTemplateManager.class) {
                if (sInstance == null) {
                    sInstance = new FileTemplateManager(context);
                }
            }
        }
        return sInstance;
    }

    public void addListener(@NonNull OnTemplatesChangedListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(@NonNull OnTemplatesChangedListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (OnTemplatesChangedListener listener : listeners) {
            try {
                listener.onTemplatesChanged();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Initializes default built-in templates for standard web development extensions.
     */
    private void loadDefaults() {
        templates.clear();
        extensionMap.clear();

        addDefault(new FileTemplate("html_starter", "HTML5 Starter", "html",
                "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>{fileName}</title>\n" +
                "    <link rel=\"stylesheet\" href=\"style.css\">\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>{fileName}</h1>\n" +
                "    <script src=\"main.js\"></script>\n" +
                "</body>\n" +
                "</html>\n", true));

        addDefault(new FileTemplate("css_stylesheet", "CSS Stylesheet", "css",
                "/* {fileName} - {projectName} */\n" +
                "* {\n" +
                "    margin: 0;\n" +
                "    padding: 0;\n" +
                "    box-sizing: border-box;\n" +
                "}\n\n" +
                "body {\n" +
                "    font-family: sans-serif;\n" +
                "}\n", true));

        addDefault(new FileTemplate("js_module", "JavaScript Module", "js",
                "/**\n" +
                " * {fileName}\n" +
                " * Project: {projectName}\n" +
                " */\n\n" +
                "console.log('{fileName} initialized');\n", true));

        addDefault(new FileTemplate("ts_module", "TypeScript Module", "ts",
                "/**\n" +
                " * {fileName}\n" +
                " * Project: {projectName}\n" +
                " */\n\n" +
                "export {};\n", true));

        addDefault(new FileTemplate("json_data", "JSON Data", "json",
                "{\n" +
                "  \"name\": \"{fileName}\",\n" +
                "  \"project\": \"{projectName}\"\n" +
                "}\n", true));

        addDefault(new FileTemplate("markdown_doc", "Markdown Document", "md",
                "# {fileName}\n\n" +
                "Welcome to {projectName}.\n", true));

        addDefault(new FileTemplate("text_file", "Plain Text", "txt",
                "{fileName}\n" +
                "Project: {projectName}\n", true));
    }

    private void addDefault(FileTemplate template) {
        templates.add(template);
        extensionMap.put(template.getExtension(), template);
    }

    /**
     * Loads saved templates from internal templates.json if present.
     */
    public synchronized void loadFromFile() {
        if (!internalConfigFile.exists()) {
            return;
        }

        try (FileInputStream fis = new FileInputStream(internalConfigFile);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }

            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.optJSONArray("templates");
            if (arr != null) {
                templates.clear();
                extensionMap.clear();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj != null) {
                        FileTemplate template = FileTemplate.fromJson(obj);
                        if (template != null) {
                            templates.add(template);
                            extensionMap.put(template.getExtension(), template);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Persists active templates to internal templates.json.
     */
    public synchronized boolean saveToFile() {
        try {
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            for (FileTemplate t : templates) {
                arr.put(t.toJson());
            }
            root.put("templates", arr);

            try (FileOutputStream fos = new FileOutputStream(internalConfigFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                writer.write(root.toString(2));
                writer.flush();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @NonNull
    public List<FileTemplate> getAllTemplates() {
        return new ArrayList<>(templates);
    }

    @Nullable
    public FileTemplate getTemplateForExtension(@Nullable String extension) {
        if (extension == null || extension.isEmpty()) {
            return null;
        }
        String normalized = FileTemplate.normalizeExtension(extension);
        return extensionMap.get(normalized);
    }

    @Nullable
    public FileTemplate getTemplateById(@Nullable String id) {
        if (id == null) return null;
        for (FileTemplate t : templates) {
            if (t.getId().equals(id)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Adds or updates a template, binding it to its extension and persisting.
     * Returns false if the template extension is binary.
     */
    public synchronized boolean addOrUpdateTemplate(@NonNull FileTemplate template) {
        if (isBinaryExtension(template.getExtension())) {
            return false;
        }

        int existingIndex = -1;
        for (int i = 0; i < templates.size(); i++) {
            if (templates.get(i).getId().equals(template.getId())) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex >= 0) {
            FileTemplate old = templates.get(existingIndex);
            extensionMap.remove(old.getExtension());
            templates.set(existingIndex, template);
        } else {
            templates.add(template);
        }

        extensionMap.put(template.getExtension(), template);
        saveToFile();
        notifyListeners();
        return true;
    }

    /**
     * Deletes a template by ID.
     */
    public synchronized boolean deleteTemplate(@NonNull String id) {
        FileTemplate target = null;
        int targetIndex = -1;
        for (int i = 0; i < templates.size(); i++) {
            if (templates.get(i).getId().equals(id)) {
                target = templates.get(i);
                targetIndex = i;
                break;
            }
        }

        if (target != null && targetIndex >= 0) {
            templates.remove(targetIndex);
            extensionMap.remove(target.getExtension());
            saveToFile();
            notifyListeners();
            return true;
        }
        return false;
    }

    /**
     * Restores all factory default templates.
     */
    public synchronized void resetToDefaults() {
        loadDefaults();
        saveToFile();
        notifyListeners();
    }

    /**
     * Exports active templates to an external file (e.g. VCodeProjects/templates.json).
     */
    public synchronized boolean exportToFile(@NonNull File targetFile) {
        try {
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            for (FileTemplate t : templates) {
                arr.put(t.toJson());
            }
            root.put("templates", arr);

            try (FileOutputStream fos = new FileOutputStream(targetFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                writer.write(root.toString(2));
                writer.flush();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Imports templates from an external file, merging or replacing existing configurations.
     */
    public synchronized boolean importFromFile(@NonNull File sourceFile) {
        if (!sourceFile.exists()) {
            return false;
        }

        try (FileInputStream fis = new FileInputStream(sourceFile);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }

            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.optJSONArray("templates");
            if (arr != null && arr.length() > 0) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.optJSONObject(i);
                    if (item != null) {
                        FileTemplate parsed = FileTemplate.fromJson(item);
                        if (parsed != null) {
                            addOrUpdateTemplate(parsed);
                        }
                    }
                }
                saveToFile();
                notifyListeners();
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Returns the dedicated disk directory inside VCodeProjects where templates can be edited.
     */
    @NonNull
    public File getTemplatesDirectory() {
        File projectsDir = FileUtils.getProjectsDirectory();
        File templatesDir = new File(projectsDir, TEMPLATES_DIR_NAME);
        if (!templatesDir.exists()) {
            templatesDir.mkdirs();
        }
        File vcodeDir = new File(templatesDir, ProjectRepository.VCODE_DIR);
        if (vcodeDir.exists()) {
            FileUtils.deleteRecursive(vcodeDir);
        }
        return templatesDir;
    }

    /**
     * Synchronizes a specific template to disk as a file so EditorActivity can open it with full IDE support.
     */
    @NonNull
    public File syncTemplateFileToDisk(@NonNull FileTemplate template) {
        File dir = getTemplatesDirectory();
        File targetFile = new File(dir, template.getDiskFileName());
        try {
            FileUtils.writeFile(targetFile, template.getContent());
        } catch (Exception ignored) {
        }
        return targetFile;
    }

    /**
     * Synchronizes modifications made to a template file on disk back into the template model and templates.json.
     */
    public synchronized boolean syncTemplateFromDisk(@NonNull FileTemplate template) {
        File dir = getTemplatesDirectory();
        File diskFile = new File(dir, template.getDiskFileName());
        if (diskFile.exists()) {
            try {
                String diskContent = FileUtils.readFile(diskFile);
                if (!diskContent.equals(template.getContent())) {
                    template.setContent(diskContent);
                    saveToFile();
                    notifyListeners();
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /**
     * Checks all template files on disk and synchronizes any external changes made in EditorActivity.
     */
    public synchronized boolean syncAllTemplatesFromDisk() {
        boolean anyChanged = false;
        File dir = getTemplatesDirectory();
        for (FileTemplate t : templates) {
            File diskFile = new File(dir, t.getDiskFileName());
            if (diskFile.exists()) {
                try {
                    String diskContent = FileUtils.readFile(diskFile);
                    if (!diskContent.equals(t.getContent())) {
                        t.setContent(diskContent);
                        anyChanged = true;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (anyChanged) {
            saveToFile();
            notifyListeners();
        }
        return anyChanged;
    }

    /**
     * Extracts the extension from a file name (without leading dot).
     */
    @NonNull
    public static String getExtension(@Nullable String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            return FileTemplate.normalizeExtension(fileName.substring(dot + 1));
        }
        return "";
    }

    /**
     * Formats a raw file name into a capitalized title, replacing underscores and hyphens with spaces.
     * Example: 'my profile.html' -> 'My Profile', 'user_account_view.js' -> 'User Account View'.
     */
    @NonNull
    public static String formatFileNamePlaceholder(@Nullable String rawFileName) {
        if (rawFileName == null || rawFileName.trim().isEmpty()) {
            return "";
        }

        // Strip directory separators if present
        int slash = Math.max(rawFileName.lastIndexOf('/'), rawFileName.lastIndexOf('\\'));
        String base = slash >= 0 ? rawFileName.substring(slash + 1) : rawFileName;

        // Strip file extension if present
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }

        // Replace underscores and hyphens with spaces
        base = base.replace('_', ' ').replace('-', ' ');

        // Split into words, capitalize the first letter of each word
        String[] words = base.trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }
        return result.toString();
    }

    /**
     * Resolves the actual human-readable project name from .vcode/meta/project.json
     * for a project directory or any file inside it.
     */
    @NonNull
    public static String resolveProjectName(@Nullable File directoryOrFile) {
        return ProjectRepository.getProjectName(directoryOrFile);
    }

    /**
     * Resolves the actual project name, falling back to a provided fallback if empty or equals folder name.
     */
    @NonNull
    public static String resolveProjectName(@Nullable File directoryOrFile, @Nullable String fallback) {
        String name = ProjectRepository.getProjectName(directoryOrFile);
        if (name.isEmpty() || (directoryOrFile != null && name.equals(directoryOrFile.getName()))) {
            if (fallback != null && !fallback.trim().isEmpty()) {
                return fallback.trim();
            }
        }
        return name;
    }

    /**
     * Evaluates a template string by replacing {fileName} and {projectName} placeholders,
     * pulling the actual project name from .vcode/meta/project.json associated with the target directory or file.
     */
    @NonNull
    public static String evaluateTemplate(@Nullable String templateContent, @Nullable String fileName, @Nullable File projectDirOrFile) {
        String projectName = ProjectRepository.getProjectName(projectDirOrFile);
        return evaluateTemplate(templateContent, fileName, projectName);
    }

    /**
     * Evaluates a template string by replacing {fileName} and {projectName} placeholders.
     */
    @NonNull
    public static String evaluateTemplate(@Nullable String templateContent, @Nullable String fileName, @Nullable String projectName) {
        if (templateContent == null || templateContent.isEmpty()) {
            return "";
        }

        String formattedName = formatFileNamePlaceholder(fileName);
        String safeProject = projectName != null ? projectName : "";

        return templateContent
                .replace("{fileName}", formattedName)
                .replace("{projectName}", safeProject);
    }

    /**
     * Checks if a given extension belongs to a binary asset or non-text file type.
     */
    public static boolean isBinaryExtension(@Nullable String extension) {
        if (extension == null || extension.trim().isEmpty()) {
            return false;
        }
        String cleanExt = extension.trim().toLowerCase();
        if (cleanExt.startsWith(".")) {
            cleanExt = cleanExt.substring(1);
        }
        FileType type = FileType.fromExtension(cleanExt);
        return type.isBinaryAsset() || BINARY_EXTENSIONS.contains(cleanExt);
    }
}
