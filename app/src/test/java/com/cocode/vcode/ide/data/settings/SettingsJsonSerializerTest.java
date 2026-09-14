package com.cocode.vcode.ide.data.settings;

import com.cocode.vcode.ide.data.model.AppSettings;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SettingsJsonSerializerTest {

    @Test
    public void testSerializeStrict14Fields() throws Exception {
        AppSettings s = new AppSettings();
        s.fontSize = 16;
        s.showLineNumbers = true;
        s.autoCloseBrackets = true;
        s.autoCloseQuotes = false;
        s.autoCloseHtmlTags = true;
        s.wordWrap = true;
        s.autoIndent = false;

        s.theme = AppSettings.Theme.DARK;

        s.gitDefaultBranch = "main";
        s.gitConfirmHardReset = true;
        s.gitAuthorName = "Dev User";
        s.gitAuthorEmail = "dev@example.com";

        s.openPreviewInApp = true;
        s.autoSave = false;

        JSONObject root = SettingsJsonSerializer.serialize(s);

        // Assert root has strictly the 4 categories and NO $schema or extra properties
        assertFalse("Must not have $schema", root.has("$schema"));
        assertEquals("Must have exactly 4 category sections", 4, root.length());
        assertTrue(root.has("editor"));
        assertTrue(root.has("appearance"));
        assertTrue(root.has("git"));
        assertTrue(root.has("general"));

        // Editor: 7 fields
        JSONObject editor = root.getJSONObject("editor");
        assertEquals("Editor must have 7 fields", 7, editor.length());
        assertEquals(16, editor.getInt("fontSize"));
        assertTrue(editor.getBoolean("showLineNumbers"));
        assertTrue(editor.getBoolean("autoCloseBrackets"));
        assertFalse(editor.getBoolean("autoCloseQuotes"));
        assertTrue(editor.getBoolean("autoCloseHtmlTags"));
        assertTrue(editor.getBoolean("wordWrap"));
        assertFalse(editor.getBoolean("autoIndent"));

        // Appearance: 1 field
        JSONObject appearance = root.getJSONObject("appearance");
        assertEquals("Appearance must have 1 field", 1, appearance.length());
        assertEquals("DARK", appearance.getString("theme"));

        // Git: 4 fields
        JSONObject git = root.getJSONObject("git");
        assertEquals("Git must have 4 fields", 4, git.length());
        assertEquals("main", git.getString("defaultBranch"));
        assertTrue(git.getBoolean("confirmHardReset"));
        assertEquals("Dev User", git.getString("authorName"));
        assertEquals("dev@example.com", git.getString("authorEmail"));

        // General: 2 fields
        JSONObject general = root.getJSONObject("general");
        assertEquals("General must have 2 fields", 2, general.length());
        assertTrue(general.getBoolean("openPreviewInApp"));
        assertFalse(general.getBoolean("autoSave"));
    }

    @Test
    public void testValidateAndParseHierarchicalJson() {
        String json = "{\n" +
                "  \"editor\": {\n" +
                "    \"fontSize\": 20,\n" +
                "    \"wordWrap\": false\n" +
                "  },\n" +
                "  \"appearance\": {\n" +
                "    \"theme\": \"LIGHT\"\n" +
                "  },\n" +
                "  \"git\": {\n" +
                "    \"defaultBranch\": \"master\"\n" +
                "  },\n" +
                "  \"general\": {\n" +
                "    \"autoSave\": true\n" +
                "  }\n" +
                "}";

        SettingsJsonSerializer.ValidationResult result =
                SettingsJsonSerializer.validateAndParse(json, new AppSettings());

        assertTrue(result.isValid);
        assertNotNull(result.settings);
        assertEquals(20, result.settings.fontSize);
        assertFalse(result.settings.wordWrap);
        assertEquals(AppSettings.Theme.LIGHT, result.settings.theme);
        assertEquals("master", result.settings.gitDefaultBranch);
        assertTrue(result.settings.autoSave);
    }

    @Test
    public void testValidateAndParseFlatDotNotation() {
        String json = "{\n" +
                "  \"editor.fontSize\": 22,\n" +
                "  \"appearance.theme\": \"DARK\",\n" +
                "  \"git.authorName\": \"Test Coder\",\n" +
                "  \"general.openPreviewInApp\": false\n" +
                "}";

        SettingsJsonSerializer.ValidationResult result =
                SettingsJsonSerializer.validateAndParse(json, new AppSettings());

        assertTrue(result.isValid);
        assertNotNull(result.settings);
        assertEquals(22, result.settings.fontSize);
        assertEquals(AppSettings.Theme.DARK, result.settings.theme);
        assertEquals("Test Coder", result.settings.gitAuthorName);
        assertFalse(result.settings.openPreviewInApp);
    }

    @Test
    public void testValidateAndParseInvalidSyntax() {
        String brokenJson = "{\"editor\": { unclosed json";

        SettingsJsonSerializer.ValidationResult result =
                SettingsJsonSerializer.validateAndParse(brokenJson, new AppSettings());

        assertFalse(result.isValid);
        assertNotNull(result.errorMessage);
        assertTrue(result.errorMessage.contains("valid JSON") || result.errorMessage.contains("JSON"));
    }

    @Test
    public void testValidateAndParseUnrelatedJson() {
        String unrelatedJson = "{\n" +
                "  \"name\": \"my-project\",\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"dependencies\": {}\n" +
                "}";

        SettingsJsonSerializer.ValidationResult result =
                SettingsJsonSerializer.validateAndParse(unrelatedJson, new AppSettings());

        assertFalse(result.isValid);
        assertNotNull(result.errorMessage);
        assertTrue(result.errorMessage.contains("recognized VCode settings"));
    }

    @Test
    public void testValidateAndParseEmptyJson() {
        SettingsJsonSerializer.ValidationResult resultEmpty =
                SettingsJsonSerializer.validateAndParse("", new AppSettings());
        assertFalse(resultEmpty.isValid);

        SettingsJsonSerializer.ValidationResult resultEmptyObj =
                SettingsJsonSerializer.validateAndParse("{}", new AppSettings());
        assertFalse(resultEmptyObj.isValid);
    }
}
