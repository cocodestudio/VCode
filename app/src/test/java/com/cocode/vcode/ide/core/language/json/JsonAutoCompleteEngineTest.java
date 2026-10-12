package com.cocode.vcode.ide.core.language.json;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.cocode.vcode.ide.core.autocomplete.VFSManager;
import com.cocode.vcode.ide.core.model.CompletionItem;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class JsonAutoCompleteEngineTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testJsonValueAndArrayPathCompletions() throws IOException {
        File projectRoot = tempFolder.newFolder("json_proj");
        File appDir = new File(projectRoot, "app");
        appDir.mkdir();
        new File(appDir, "index.js").createNewFile();
        File packageJson = new File(projectRoot, "package.json");
        packageJson.createNewFile();
        VFSManager.getInstance().clear();

        JsonAutoCompleteEngine engine = new JsonAutoCompleteEngine(null);
        engine.setCurrentFile(packageJson);

        // 1. Path in value position: "main": "app/|"
        String docValue = "{\n  \"main\": \"app/\"\n}";
        int cursorValue = docValue.indexOf("app/\"") + 4;
        List<CompletionItem> valueItems = engine.getSuggestions(docValue, cursorValue);
        assertNotNull(valueItems);
        boolean hasIndexInValue = false;
        for (CompletionItem item : valueItems) {
            if ("index.js".equals(item.getLabel())) {
                hasIndexInValue = true;
                break;
            }
        }
        assertTrue("JSON value position must suggest files inside app/", hasIndexInValue);

        // 2. Path in array position: "files": ["app/|"]
        String docArray = "{\n  \"files\": [\n    \"app/\"\n  ]\n}";
        int cursorArray = docArray.indexOf("app/\"") + 4;
        List<CompletionItem> arrayItems = engine.getSuggestions(docArray, cursorArray);
        assertNotNull(arrayItems);
        boolean hasIndexInArray = false;
        for (CompletionItem item : arrayItems) {
            if ("index.js".equals(item.getLabel())) {
                hasIndexInArray = true;
                break;
            }
        }
        assertTrue("JSON array position must suggest files inside app/", hasIndexInArray);
    }
}
