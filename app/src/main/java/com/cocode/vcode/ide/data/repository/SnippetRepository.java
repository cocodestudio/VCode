package com.cocode.vcode.ide.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.Result;
import com.cocode.vcode.ide.data.model.SnippetItem;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.LanguageDetector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Code boilerplate repository manager.
 * Collects, writes, updates, and deletes custom snippets, executing a merge
 * strategy to prioritize user adaptations above stock application defaults.
 */
public class SnippetRepository {

    private static final String SNIPPETS_FILE_NAME = "snippets.json";
    private final Context appContext;

    public SnippetRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Pulls down a combined view of available boilerplates, sorting modified variants above defaults.
     * Prevents entry duplication by matching and excluding overlapping structural unique IDs.
     *
     * @return A LiveData collection containing the consolidated boilerplate results array.
     */
    public LiveData<Result<List<SnippetItem>>> getSnippets() {
        MutableLiveData<Result<List<SnippetItem>>> liveData = new MutableLiveData<>();
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                // Step 1: Extract developer-defined workspace components from local storage partitions
                List<SnippetItem> userSnippets = readSnippetsFromDisk();

                // Step 2: Assemble hardcoded system baseline shortcuts
                List<SnippetItem> defaults = getDefaultSnippets();

                // Step 3: Populate user-defined models first to prioritize custom items in selection lists
                List<SnippetItem> merged = new ArrayList<>(userSnippets);

                // Step 4: Evaluate and weave default templates into selection matrices if un-overridden
                for (SnippetItem defaultItem : defaults) {
                    boolean isOverridden = false;
                    for (SnippetItem userItem : userSnippets) {
                        if (defaultItem.getId().equals(userItem.getId())) {
                            isOverridden = true;
                            break;
                        }
                    }

                    if (!isOverridden) {
                        merged.add(defaultItem);
                    }
                }

                ExecutorProvider.getInstance().runOnMain(() -> liveData.setValue(Result.success(merged)));
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(() ->
                        liveData.setValue(Result.error("Failed to load snippets: " + e.getMessage())));
            }
        });
        return liveData;
    }

    /**
     * Commits newly authored shortcut macros into configuration storage blocks.
     * Automatically reviews content blocks to determine corresponding code formatting classes.
     */
    public LiveData<Result<SnippetItem>> saveSnippet(SnippetItem item) {
        MutableLiveData<Result<SnippetItem>> liveData = new MutableLiveData<>();
        if (item == null) {
            liveData.setValue(Result.error("Snippet is null"));
            return liveData;
        }
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                if (item.getId() == null || item.getId().isEmpty()) {
                    item.setId(UUID.randomUUID().toString());
                }

                // Analyze template data profiles to apply automated language sorting labels
                // TODO: Review LanguageDetector.detect() for potential performance optimizations
                item.setFileType(LanguageDetector.detect(item.getContent()));

                List<SnippetItem> existing = readSnippetsFromDisk();
                existing.add(item);
                writeSnippetsToDisk(existing);
                ExecutorProvider.getInstance().runOnMain(() -> liveData.setValue(Result.success(item)));
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(() -> liveData.setValue(Result.error("Failed to save snippet: " + e.getMessage())));
            }
        });
        return liveData;
    }

    /**
     * Updates syntax patterns within an existing custom boilerplate definition.
     */
    public LiveData<Result<Boolean>> updateSnippet(SnippetItem updated) {
        MutableLiveData<Result<Boolean>> liveData = new MutableLiveData<>();
        if (updated == null || updated.getId() == null) {
            liveData.setValue(Result.error("Invalid snippet"));
            return liveData;
        }
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                // Re-evaluate grammar rules to align context changes correctly
                // TODO: Review LanguageDetector.detect() for potential performance optimizations
                updated.setFileType(LanguageDetector.detect(updated.getContent()));

                List<SnippetItem> existing = readSnippetsFromDisk();
                boolean found = false;
                for (int i = 0; i < existing.size(); i++) {
                    if (updated.getId().equals(existing.get(i).getId())) {
                        existing.set(i, updated);
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    // If an entry is edited but missing from local records, persist it as a new customization layer
                    existing.add(updated);
                }

                writeSnippetsToDisk(existing);
                ExecutorProvider.getInstance().runOnMain(() -> liveData.setValue(Result.success(true)));
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(() -> liveData.setValue(Result.error("Failed to update snippet: " + e.getMessage())));
            }
        });
        return liveData;
    }

    /**
     * Purges custom configurations from workspace directory paths.
     */
    public LiveData<Result<Boolean>> deleteSnippet(String snippetId) {
        MutableLiveData<Result<Boolean>> liveData = new MutableLiveData<>();
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                List<SnippetItem> existing = readSnippetsFromDisk();
                boolean removed = false;
                java.util.Iterator<SnippetItem> iterator = existing.iterator();
                while (iterator.hasNext()) {
                    if (snippetId.equals(iterator.next().getId())) {
                        iterator.remove();
                        removed = true;
                        break;
                    }
                }

                if (removed) {
                    writeSnippetsToDisk(existing);
                    ExecutorProvider.getInstance().runOnMain(() -> liveData.setValue(Result.success(true)));
                } else {
                    ExecutorProvider.getInstance().runOnMain(() -> liveData.setValue(Result.error("Default snippets cannot be deleted from disk")));
                }
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(() -> liveData.setValue(Result.error("Failed to delete snippet: " + e.getMessage())));
            }
        });
        return liveData;
    }

    /**
     * Generates standard built-in baseline shortcuts for supported text environments.
     */
    private List<SnippetItem> getDefaultSnippets() {
        List<SnippetItem> defaults = new ArrayList<>();

        // --- HTML Environment Snippets ---
        defaults.add(new SnippetItem("def_html5", "html5 (Boilerplate)",
                "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n  <title>|</title>\n</head>\n<body>\n\n</body>\n</html>",
                FileType.HTML));

        defaults.add(new SnippetItem("def_html_link", "link (CSS Link)",
                "<link rel=\"stylesheet\" href=\"|\">",
                FileType.HTML));

        defaults.add(new SnippetItem("def_html_script", "script (JS Source)",
                "<script src=\"|\"></script>",
                FileType.HTML));

        defaults.add(new SnippetItem("def_html_img", "img (Image Tag)",
                "<img src=\"|\" alt=\"\">",
                FileType.HTML));

        // --- CSS Environment Snippets ---
        defaults.add(new SnippetItem("def_css_reset", "reset (CSS Reset)",
                "* {\n  margin: 0;\n  padding: 0;\n  box-sizing: border-box;\n}\n|",
                FileType.CSS));

        defaults.add(new SnippetItem("def_css_flex", "flex-center (Center Content)",
                "display: flex;\njustify-content: center;\nalign-items: center;\n|",
                FileType.CSS));

        defaults.add(new SnippetItem("def_css_mq", "media (Mobile Query)",
                "@media (max-width: 768px) {\n  |\n}",
                FileType.CSS));

        defaults.add(new SnippetItem("def_css_root", "root (Variables)",
                ":root {\n  --primary-color: #|;\n  --secondary-color: #;\n}",
                FileType.CSS));

        // --- JavaScript Environment Snippets ---
        defaults.add(new SnippetItem("def_clg", "clg (Console Log)",
                "console.log(|);",
                FileType.JAVASCRIPT));

        defaults.add(new SnippetItem("def_qs", "qs (Query Selector)",
                "const | = document.querySelector('');",
                FileType.JAVASCRIPT));

        defaults.add(new SnippetItem("def_addEventListener", "event (Click Listener)",
                "|.addEventListener('click', (e) => {\n  \n});",
                FileType.JAVASCRIPT));

        defaults.add(new SnippetItem("def_af", "af (Arrow Function)",
                "const | = () => {\n  \n};",
                FileType.JAVASCRIPT));

        defaults.add(new SnippetItem("def_fetch", "fetch (API GET)",
                "fetch('|')\n  .then(res => res.json())\n  .then(data => {\n    console.log(data);\n  });",
                FileType.JAVASCRIPT));

        return defaults;
    }

    // --- Disk I/O Methods ---
    private File getSnippetsFile() {
        return new File(appContext.getFilesDir(), SNIPPETS_FILE_NAME);
    }

    /**
     * Unpacks user configuration logs stored on disk partitions.
     */
    private List<SnippetItem> readSnippetsFromDisk() throws Exception {
        List<SnippetItem> list = new ArrayList<>();
        File file = getSnippetsFile();
        if (!file.exists()) return list;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        String raw = sb.toString().trim();
        if (raw.isEmpty()) return list;
        JSONArray array = new JSONArray(raw);
        for (int i = 0; i < array.length(); i++) list.add(fromJson(array.getJSONObject(i)));
        return list;
    }

    /**
     * Persists customized snippet listings back into space-optimized data files.
     */
    private void writeSnippetsToDisk(List<SnippetItem> snippets) throws Exception {
        JSONArray array = new JSONArray();
        for (SnippetItem s : snippets) array.put(toJson(s));
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getSnippetsFile()), StandardCharsets.UTF_8))) {
            writer.write(array.toString(2));
        }
    }

    private JSONObject toJson(SnippetItem item) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", item.getId());
        obj.put("title", item.getTitle());
        obj.put("content", item.getContent());
        obj.put("fileType", item.getFileType().name());

        // Custom namespaces for non-code snippets
        if (item.getId() != null && item.getId().startsWith("git_template_")) {
            obj.put("namespace", "git_templates");
        }
        return obj;
    }

    private SnippetItem fromJson(JSONObject obj) {
        return new SnippetItem(
                obj.optString("id", UUID.randomUUID().toString()),
                obj.optString("title", "Untitled"),
                obj.optString("content", ""),
                FileType.valueOf(obj.optString("fileType", obj.optString("language", FileType.TEXT.name())))
        );
    }
}