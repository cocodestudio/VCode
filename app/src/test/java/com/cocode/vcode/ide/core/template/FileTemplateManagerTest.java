package com.cocode.vcode.ide.core.template;

import android.content.Context;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.cocode.vcode.ide.utils.FileUtils;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class FileTemplateManagerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Context context;
    private FileTemplateManager manager;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        manager = FileTemplateManager.getInstance(context);
        manager.resetToDefaults();
    }

    @After
    public void tearDown() {
        manager.resetToDefaults();
    }

    @Test
    public void testDefaultTemplatesLoaded() {
        List<FileTemplate> all = manager.getAllTemplates();
        assertNotNull(all);
        assertTrue("Must contain at least 6 default templates", all.size() >= 6);

        assertNotNull("HTML template must exist", manager.getTemplateForExtension("html"));
        assertNotNull("CSS template must exist", manager.getTemplateForExtension("css"));
        assertNotNull("JS template must exist", manager.getTemplateForExtension("js"));
        assertNotNull("TS template must exist", manager.getTemplateForExtension("ts"));
        assertNotNull("JSON template must exist", manager.getTemplateForExtension("json"));
        assertNotNull("MD template must exist", manager.getTemplateForExtension("md"));
    }

    @Test
    public void testExtensionNormalizationAndLookup() {
        assertNotNull(manager.getTemplateForExtension(".html"));
        assertNotNull(manager.getTemplateForExtension("HTML"));
        assertNotNull(manager.getTemplateForExtension(".CSS"));
        assertNotNull(manager.getTemplateForExtension("  js  "));

        assertEquals("html_starter", manager.getTemplateForExtension(".html").getId());
        assertNull(manager.getTemplateForExtension("unknown_ext"));
    }

    @Test
    public void testFormatFileNamePlaceholder() {
        assertEquals("My Profile", FileTemplateManager.formatFileNamePlaceholder("my profile.html"));
        assertEquals("My Profile", FileTemplateManager.formatFileNamePlaceholder("my_profile.html"));
        assertEquals("User Account View", FileTemplateManager.formatFileNamePlaceholder("user_account_view.js"));
        assertEquals("Nav Bar", FileTemplateManager.formatFileNamePlaceholder("nav-bar.css"));
        assertEquals("About Us Page", FileTemplateManager.formatFileNamePlaceholder("about_us_page.html"));
        assertEquals("Index", FileTemplateManager.formatFileNamePlaceholder("index.html"));
        assertEquals("Awesome Component", FileTemplateManager.formatFileNamePlaceholder("awesome_component.tsx"));
        assertEquals("", FileTemplateManager.formatFileNamePlaceholder(""));
        assertEquals("", FileTemplateManager.formatFileNamePlaceholder(null));
    }

    @Test
    public void testEvaluateTemplatePlaceholders() {
        String template = "/* {fileName} in {projectName} */\nconsole.log('{fileName}');";
        String evaluated = FileTemplateManager.evaluateTemplate(template, "my_cool_script.js", "MyAwesomeApp");

        assertTrue(evaluated.contains("/* My Cool Script in MyAwesomeApp */"));
        assertTrue(evaluated.contains("console.log('My Cool Script');"));
        assertFalse(evaluated.contains("{fileName}"));
        assertFalse(evaluated.contains("{projectName}"));
    }

    @Test
    public void testProjectNamePulledFromProjectJson() throws Exception {
        File tempDir = tempFolder.newFolder("uuid-folder-12345");
        File vcodeMetaDir = new File(new File(tempDir, ".vcode"), "meta");
        vcodeMetaDir.mkdirs();
        File projectJson = new File(vcodeMetaDir, "project.json");

        JSONObject meta = new JSONObject();
        meta.put("id", "uuid-folder-12345");
        meta.put("name", "VCode Studio Web");
        FileUtils.writeFile(projectJson, meta.toString());

        File subDir = new File(tempDir, "src");
        subDir.mkdirs();
        File dummyFile = new File(subDir, "app.js");

        assertEquals("VCode Studio Web", ProjectRepository.getProjectName(tempDir));
        assertEquals("VCode Studio Web", ProjectRepository.getProjectName(subDir));
        assertEquals("VCode Studio Web", ProjectRepository.getProjectName(dummyFile));

        String template = "/* Title: {fileName} | Project: {projectName} */";
        String result = FileTemplateManager.evaluateTemplate(template, "app.js", subDir);
        assertEquals("/* Title: App | Project: VCode Studio Web */", result);
    }

    @Test
    public void testTemplateJsonSerialization() {
        FileTemplate template = new FileTemplate("react_jsx", "React Component", "jsx",
                "import React from 'react';\nexport default function {fileName}() {}", false);

        JSONObject json = template.toJson();
        assertNotNull(json);
        assertEquals("react_jsx", json.optString("id"));
        assertEquals("React Component", json.optString("name"));
        assertEquals("jsx", json.optString("extension"));
        assertTrue(json.optString("content").contains("{fileName}"));
        assertFalse(json.optBoolean("isBuiltIn"));

        FileTemplate parsed = FileTemplate.fromJson(json);
        assertNotNull(parsed);
        assertEquals(template.getId(), parsed.getId());
        assertEquals(template.getName(), parsed.getName());
        assertEquals(template.getExtension(), parsed.getExtension());
        assertEquals(template.getContent(), parsed.getContent());
        assertEquals(template.isBuiltIn(), parsed.isBuiltIn());
    }

    @Test
    public void testAddUpdateAndDeleteTemplate() {
        FileTemplate custom = new FileTemplate("python_script", "Python Script", "py",
                "# {fileName} - {projectName}\n\ndef main():\n    pass\n", false);

        manager.addOrUpdateTemplate(custom);
        assertNotNull(manager.getTemplateForExtension("py"));
        assertEquals("Python Script", manager.getTemplateForExtension("py").getName());

        // Update
        custom.setName("Python 3 Script");
        manager.addOrUpdateTemplate(custom);
        assertEquals("Python 3 Script", manager.getTemplateForExtension("py").getName());

        // Delete
        boolean deleted = manager.deleteTemplate("python_script");
        assertTrue(deleted);
        assertNull(manager.getTemplateForExtension("py"));
    }

    @Test
    public void testExportAndImportRoundTrip() throws Exception {
        File exportFile = tempFolder.newFile("exported_templates.json");
        boolean ok = manager.exportToFile(exportFile);
        assertTrue("Export must succeed", ok);
        assertTrue("Exported file must exist and have content", exportFile.length() > 0);

        // Add a new template to export
        FileTemplate custom = new FileTemplate("rust_file", "Rust File", "rs", "// {fileName}\nfn main() {}\n", false);
        manager.addOrUpdateTemplate(custom);
        manager.exportToFile(exportFile);

        // Reset to defaults
        manager.resetToDefaults();
        assertNull("Rust template should be gone after reset", manager.getTemplateForExtension("rs"));

        // Import
        boolean imported = manager.importFromFile(exportFile);
        assertTrue("Import must succeed", imported);
        assertNotNull("Rust template must be restored after import", manager.getTemplateForExtension("rs"));
    }

    @Test
    public void testBinaryFileTemplatePrevention() {
        // Test isBinaryExtension
        assertTrue(FileTemplateManager.isBinaryExtension("png"));
        assertTrue(FileTemplateManager.isBinaryExtension(".jpg"));
        assertTrue(FileTemplateManager.isBinaryExtension("jpeg"));
        assertTrue(FileTemplateManager.isBinaryExtension("webp"));
        assertTrue(FileTemplateManager.isBinaryExtension("gif"));
        assertTrue(FileTemplateManager.isBinaryExtension("mp3"));
        assertTrue(FileTemplateManager.isBinaryExtension("mp4"));
        assertTrue(FileTemplateManager.isBinaryExtension("pdf"));
        assertTrue(FileTemplateManager.isBinaryExtension("zip"));
        assertTrue(FileTemplateManager.isBinaryExtension("exe"));
        assertTrue(FileTemplateManager.isBinaryExtension("class"));
        assertTrue(FileTemplateManager.isBinaryExtension("dex"));
        assertTrue(FileTemplateManager.isBinaryExtension("apk"));

        // Text extensions should return false
        assertFalse(FileTemplateManager.isBinaryExtension("html"));
        assertFalse(FileTemplateManager.isBinaryExtension(".css"));
        assertFalse(FileTemplateManager.isBinaryExtension("js"));
        assertFalse(FileTemplateManager.isBinaryExtension("ts"));
        assertFalse(FileTemplateManager.isBinaryExtension("json"));
        assertFalse(FileTemplateManager.isBinaryExtension("md"));
        assertFalse(FileTemplateManager.isBinaryExtension("txt"));
        assertFalse(FileTemplateManager.isBinaryExtension("py"));
        assertFalse(FileTemplateManager.isBinaryExtension("rs"));

        // addOrUpdateTemplate must reject binary templates
        FileTemplate pngTemplate = new FileTemplate("image_png", "PNG Image", "png", "binarycontent", false);
        boolean added = manager.addOrUpdateTemplate(pngTemplate);
        assertFalse("Binary template must be rejected", added);
        assertNull("Binary template must not be registered", manager.getTemplateForExtension("png"));
    }

    @Test
    public void testTemplatesDirectoryExcludedFromProjects() throws Exception {
        File dummyProjectsDir = tempFolder.newFolder("VCodeProjects");
        File templatesDir = new File(dummyProjectsDir, "templates");
        templatesDir.mkdirs();

        // Simulate an accidental .vcode created inside templates
        File accidentalVcode = new File(templatesDir, ".vcode");
        accidentalVcode.mkdirs();
        File metaDir = new File(accidentalVcode, "meta");
        metaDir.mkdirs();
        File metaFile = new File(metaDir, "project.json");
        FileUtils.writeFile(metaFile, "{\"projectName\": \"VCode Project\"}");

        // ProjectFileRecovery should not create files and should purge .vcode
        com.cocode.vcode.ide.utils.ProjectFileRecovery.ensureProjectFilesExist(templatesDir);
        assertFalse("accidental .vcode must be purged from templates", accidentalVcode.exists());

        // ProjectRepository.getProjectName must return 'templates', never 'Untitled'
        String resolvedName = ProjectRepository.getProjectName(templatesDir);
        assertEquals("templates", resolvedName);
    }
}
