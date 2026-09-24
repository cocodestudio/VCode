package com.cocode.vcode.ide.data.repository;

import com.cocode.vcode.ide.data.model.ProjectState;
import com.cocode.vcode.ide.utils.FileUtils;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ProjectStateRepositoryTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private ProjectStateRepository repository;
    private File projectDir;

    @Before
    public void setUp() throws Exception {
        repository = new ProjectStateRepository();
        File vcodeProjects = tempFolder.newFolder("VCodeProjects");
        projectDir = new File(vcodeProjects, "TestProject");
        projectDir.mkdirs();
    }

    @Test
    public void testSaveAndLoadStateWithCursorAndScroll() {
        String projectId = "test-project-123";
        ProjectState state = new ProjectState(projectId);
        state.setActiveTabIndex(1);
        state.setOpenFilePaths(Arrays.asList("index.html", "src/main.js", "styles.css"));

        state.setScrollFor("index.html", 250);
        state.setCursorFor("index.html", 145);

        state.setScrollFor("src/main.js", 1020);
        state.setCursorFor("src/main.js", 680);

        state.setScrollFor("styles.css", 0);
        state.setCursorFor("styles.css", 42);

        state.setPreviewStateFor("index.html", false);

        repository.saveStateSync(projectDir, state);

        ProjectState loaded = repository.loadStateSync(projectDir, projectId);
        assertNotNull(loaded);
        assertEquals(projectId, loaded.getProjectId());
        assertEquals(1, loaded.getActiveTabIndex());
        assertEquals(3, loaded.getOpenFilePaths().size());
        assertEquals("index.html", loaded.getOpenFilePaths().get(0));
        assertEquals("src/main.js", loaded.getOpenFilePaths().get(1));

        // Verify scroll positions
        assertEquals(250, loaded.getScrollFor("index.html"));
        assertEquals(1020, loaded.getScrollFor("src/main.js"));
        assertEquals(0, loaded.getScrollFor("styles.css"));
        assertEquals(0, loaded.getScrollFor("nonexistent.txt"));

        // Verify cursor positions
        assertEquals(145, loaded.getCursorFor("index.html"));
        assertEquals(680, loaded.getCursorFor("src/main.js"));
        assertEquals(42, loaded.getCursorFor("styles.css"));
        assertEquals(0, loaded.getCursorFor("nonexistent.txt"));

        assertEquals(false, loaded.getPreviewStateFor("index.html"));
    }

    @Test
    public void testLegacySessionWithoutCursorPositions() throws Exception {
        String projectId = "legacy-project";
        File sessionDir = new File(new File(projectDir, ".vcode"), "state");
        sessionDir.mkdirs();
        File sessionFile = new File(sessionDir, "session.json");

        JSONObject root = new JSONObject();
        root.put("projectId", projectId);
        root.put("activeTabIndex", 0);
        root.put("openFilePaths", new org.json.JSONArray(Arrays.asList("index.html")));

        JSONObject scrolls = new JSONObject();
        scrolls.put("index.html", 320);
        root.put("scrollPositions", scrolls);

        // Intentionally no "cursorPositions" in legacy json
        FileUtils.writeFile(sessionFile, root.toString());

        ProjectState loaded = repository.loadStateSync(projectDir, projectId);
        assertNotNull(loaded);
        assertEquals(320, loaded.getScrollFor("index.html"));
        assertEquals(0, loaded.getCursorFor("index.html"));
    }

    @Test
    public void testIntentSessionDoesNotCreateVCodeDir() {
        File downloadsDir = new File(tempFolder.getRoot(), "Downloads");
        downloadsDir.mkdirs();
        File externalProjectDir = new File(downloadsDir, "subfolder");
        externalProjectDir.mkdirs();

        String projectId = "intent-project-456";
        ProjectState state = new ProjectState(projectId);
        state.setActiveTabIndex(0);
        state.setOpenFilePaths(Arrays.asList("script.js"));
        state.setCursorFor("script.js", 50);
        state.setScrollFor("script.js", 120);

        // Save as intent file
        repository.saveStateSync(externalProjectDir, state, true);

        // Verify .vcode was NOT created in externalProjectDir
        File vcodeDir = new File(externalProjectDir, ".vcode");
        org.junit.Assert.assertFalse(".vcode should NOT exist in external directory for intent files", vcodeDir.exists());

        // Verify state is loaded back properly
        ProjectState loaded = repository.loadStateSync(externalProjectDir, projectId, true);
        assertNotNull(loaded);
        assertEquals(projectId, loaded.getProjectId());
        assertEquals(1, loaded.getOpenFilePaths().size());
        assertEquals("script.js", loaded.getOpenFilePaths().get(0));
        assertEquals(50, loaded.getCursorFor("script.js"));
        assertEquals(120, loaded.getScrollFor("script.js"));
    }

    @Test
    public void testPurgeAccidentalIntentVCodeDir() throws Exception {
        File folder = new File(tempFolder.getRoot(), "ExternalFolder");
        folder.mkdirs();
        File vcodeDir = new File(folder, ".vcode");
        File metaDir = new File(vcodeDir, "meta");
        metaDir.mkdirs();
        File metaFile = new File(metaDir, "project.json");
        FileUtils.writeFile(metaFile, "{\"projectName\": \"VCode Project\", \"createdAt\": 12345}");

        assertTrue(vcodeDir.exists());
        com.cocode.vcode.ide.utils.ProjectFileRecovery.purgeAccidentalIntentVCodeDir(folder);
        org.junit.Assert.assertFalse(".vcode should be purged", vcodeDir.exists());
    }
}
