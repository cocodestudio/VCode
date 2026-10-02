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

    @Test
    public void testVirtualFilesSavedInDedicatedFolder() throws Exception {
        String projectId = "test-virtual-project";
        ProjectState state = new ProjectState(projectId);
        state.setActiveTabIndex(1);
        state.setOpenFilePaths(Arrays.asList("index.html", "vcode_api_tester.api", "vcode_terminal.term"));

        String apiTesterJson = "{\"method\":\"POST\",\"url\":\"https://api.test/users\",\"headers_obj\":{},\"body_obj\":{}}";
        String terminalJson = "{\"cwd\":\"/project\",\"lastCommand\":\"ls\"}";

        state.setVirtualFile("vcode_api_tester.api", apiTesterJson);
        state.setVirtualFile("vcode_terminal.term", terminalJson);

        repository.saveStateSync(projectDir, state);

        File virtualDir = new File(new File(projectDir, ".vcode"), "virtual");
        assertTrue("virtual/ directory should exist in .vcode", virtualDir.exists() && virtualDir.isDirectory());

        File apiTesterFile = new File(virtualDir, "api_tester.json");
        assertTrue("api_tester.json should exist in .vcode/virtual/", apiTesterFile.exists());
        String readApiContent = FileUtils.readFile(apiTesterFile);
        JSONObject parsedApiFile = new JSONObject(readApiContent);
        assertEquals("https://api.test/users", parsedApiFile.getString("url"));
        // Verify formatted JSON
        assertTrue("api_tester.json should be formatted with newlines", readApiContent.contains("\n"));

        File terminalFile = new File(virtualDir, "terminal.json");
        assertTrue("terminal.json should exist in .vcode/virtual/", terminalFile.exists());
        String readTerminalContent = FileUtils.readFile(terminalFile);
        assertTrue("terminal.json should contain lastCommand", readTerminalContent.contains("lastCommand"));

        // Verify session.json does NOT contain inline virtualFiles
        File sessionFile = new File(new File(new File(projectDir, ".vcode"), "state"), "session.json");
        assertTrue("session.json should exist", sessionFile.exists());
        String sessionRaw = FileUtils.readFile(sessionFile);
        JSONObject sessionObj = new JSONObject(sessionRaw);
        org.junit.Assert.assertFalse("session.json should NOT contain virtualFiles key", sessionObj.has("virtualFiles"));

        // Verify state loads back accurately from dedicated files
        ProjectState loaded = repository.loadStateSync(projectDir, projectId);
        assertNotNull(loaded);
        assertEquals(3, loaded.getOpenFilePaths().size());
        assertEquals("vcode_api_tester.api", loaded.getOpenFilePaths().get(1));
        assertEquals("vcode_terminal.term", loaded.getOpenFilePaths().get(2));

        String loadedApiContent = loaded.getVirtualFile("vcode_api_tester.api");
        assertNotNull(loadedApiContent);
        JSONObject parsedLoadedApi = new JSONObject(loadedApiContent);
        assertEquals("POST", parsedLoadedApi.getString("method"));
        assertEquals("https://api.test/users", parsedLoadedApi.getString("url"));

        String loadedTerminalContent = loaded.getVirtualFile("vcode_terminal.term");
        assertNotNull(loadedTerminalContent);
        JSONObject parsedLoadedTerminal = new JSONObject(loadedTerminalContent);
        assertEquals("ls", parsedLoadedTerminal.getString("lastCommand"));
    }

    @Test
    public void testLegacySessionWithVirtualFilesMigratesToDedicatedFolder() throws Exception {
        String projectId = "legacy-virtual-project";
        File sessionDir = new File(new File(projectDir, ".vcode"), "state");
        sessionDir.mkdirs();
        File sessionFile = new File(sessionDir, "session.json");

        JSONObject root = new JSONObject();
        root.put("projectId", projectId);
        root.put("activeTabIndex", 0);
        root.put("openFilePaths", new org.json.JSONArray(Arrays.asList("vcode_api_tester.api")));

        JSONObject legacyVirtuals = new JSONObject();
        legacyVirtuals.put("vcode_api_tester.api", "{\"method\":\"GET\",\"url\":\"https://legacy.api.com\"}");
        root.put("virtualFiles", legacyVirtuals);

        FileUtils.writeFile(sessionFile, root.toString(2));

        // Ensure virtual/ does NOT exist before migration
        File virtualDir = new File(new File(projectDir, ".vcode"), "virtual");
        org.junit.Assert.assertFalse("virtual directory should not exist prior to migration", virtualDir.exists());

        // Load legacy state
        ProjectState loaded = repository.loadStateSync(projectDir, projectId);
        assertNotNull(loaded);
        String legacyContent = loaded.getVirtualFile("vcode_api_tester.api");
        assertNotNull("Legacy virtual file should be loaded into state", legacyContent);
        JSONObject parsedLegacy = new JSONObject(legacyContent);
        assertEquals("https://legacy.api.com", parsedLegacy.getString("url"));

        // Save state -> should write to .vcode/virtual/api_tester.json and remove virtualFiles from session.json
        repository.saveStateSync(projectDir, loaded);

        assertTrue("virtual/ directory should be created after save", virtualDir.exists());
        File apiFile = new File(virtualDir, "api_tester.json");
        assertTrue("api_tester.json should exist in virtual/", apiFile.exists());
        String savedApi = FileUtils.readFile(apiFile);
        JSONObject parsedSavedApi = new JSONObject(savedApi);
        assertEquals("https://legacy.api.com", parsedSavedApi.getString("url"));

        // Verify session.json no longer contains virtualFiles
        String updatedSession = FileUtils.readFile(sessionFile);
        JSONObject updatedSessionObj = new JSONObject(updatedSession);
        org.junit.Assert.assertFalse("session.json should no longer have virtualFiles", updatedSessionObj.has("virtualFiles"));
    }

    @Test
    public void testVirtualFileNameConversionHelpers() {
        assertEquals("api_tester.json", ProjectStateRepository.toVirtualFileName("vcode_api_tester.api"));
        assertEquals("terminal.json", ProjectStateRepository.toVirtualFileName("vcode_terminal.term"));
        assertEquals("custom_tool.json", ProjectStateRepository.toVirtualFileName("vcode_custom_tool.ext"));

        assertEquals("vcode_api_tester.api", ProjectStateRepository.toRelativePath("api_tester.json"));
        assertEquals("vcode_terminal.term", ProjectStateRepository.toRelativePath("terminal.json"));
        assertEquals("vcode_custom_tool.virtual", ProjectStateRepository.toRelativePath("custom_tool.json"));
    }

    @Test
    public void testVirtualFileNameConversionHelpersForMultipleTerminals() {
        assertEquals("terminal.json", ProjectStateRepository.toVirtualFileName("vcode_terminal.term"));
        assertEquals("terminal_2.json", ProjectStateRepository.toVirtualFileName("vcode_terminal_2.term"));
        assertEquals("terminal_5.json", ProjectStateRepository.toVirtualFileName("vcode_terminal_5.term"));

        assertEquals("vcode_terminal.term", ProjectStateRepository.toRelativePath("terminal.json"));
        assertEquals("vcode_terminal_2.term", ProjectStateRepository.toRelativePath("terminal_2.json"));
        assertEquals("vcode_terminal_5.term", ProjectStateRepository.toRelativePath("terminal_5.json"));
    }

    @Test
    public void testMultipleTerminalVirtualFilesPersistence() throws Exception {
        String projectId = "multi-terminal-project";
        ProjectState state = new ProjectState(projectId);
        state.setActiveTabIndex(0);
        state.setOpenFilePaths(Arrays.asList("vcode_terminal.term", "vcode_terminal_2.term", "vcode_terminal_3.term"));

        String term1 = "{\"history\":[\"ls\",\"pwd\"],\"cwd\":\"\",\"buffer\":\"hello terminal 1\"}";
        String term2 = "{\"history\":[\"npm test\"],\"cwd\":\"src\",\"buffer\":\"hello terminal 2\"}";
        String term3 = "{\"history\":[\"git status\"],\"cwd\":\"build\",\"buffer\":\"hello terminal 3\"}";

        state.setVirtualFile("vcode_terminal.term", term1);
        state.setVirtualFile("vcode_terminal_2.term", term2);
        state.setVirtualFile("vcode_terminal_3.term", term3);

        repository.saveStateSync(projectDir, state);

        File virtualDir = new File(new File(projectDir, ".vcode"), "virtual");
        assertTrue(virtualDir.exists() && virtualDir.isDirectory());

        File f1 = new File(virtualDir, "terminal.json");
        File f2 = new File(virtualDir, "terminal_2.json");
        File f3 = new File(virtualDir, "terminal_3.json");

        assertTrue("terminal.json should exist", f1.exists());
        assertTrue("terminal_2.json should exist", f2.exists());
        assertTrue("terminal_3.json should exist", f3.exists());

        // Restore and verify
        ProjectState loaded = repository.loadStateSync(projectDir, projectId);
        assertNotNull(loaded);
        assertEquals(3, loaded.getOpenFilePaths().size());
        assertEquals("vcode_terminal.term", loaded.getOpenFilePaths().get(0));
        assertEquals("vcode_terminal_2.term", loaded.getOpenFilePaths().get(1));
        assertEquals("vcode_terminal_3.term", loaded.getOpenFilePaths().get(2));

        String loaded1 = loaded.getVirtualFile("vcode_terminal.term");
        assertNotNull(loaded1);
        JSONObject json1 = new JSONObject(loaded1);
        assertEquals("hello terminal 1", json1.getString("buffer"));
        assertEquals("ls", json1.getJSONArray("history").getString(0));
        assertEquals("pwd", json1.getJSONArray("history").getString(1));

        String loaded2 = loaded.getVirtualFile("vcode_terminal_2.term");
        assertNotNull(loaded2);
        JSONObject json2 = new JSONObject(loaded2);
        assertEquals("hello terminal 2", json2.getString("buffer"));
        assertEquals("src", json2.getString("cwd"));
        assertEquals("npm test", json2.getJSONArray("history").getString(0));

        String loaded3 = loaded.getVirtualFile("vcode_terminal_3.term");
        assertNotNull(loaded3);
        JSONObject json3 = new JSONObject(loaded3);
        assertEquals("hello terminal 3", json3.getString("buffer"));
        assertEquals("build", json3.getString("cwd"));
        assertEquals("git status", json3.getJSONArray("history").getString(0));
    }

    @Test
    public void testEditorFileFileNameForMultipleTerminals() {
        com.cocode.vcode.ide.data.model.EditorFile ef1 = new com.cocode.vcode.ide.data.model.EditorFile(
                "id1", new File(projectDir, "vcode_terminal.term"), "", com.cocode.vcode.ide.core.model.FileType.TERMINAL);
        com.cocode.vcode.ide.data.model.EditorFile ef2 = new com.cocode.vcode.ide.data.model.EditorFile(
                "id2", new File(projectDir, "vcode_terminal_2.term"), "", com.cocode.vcode.ide.core.model.FileType.TERMINAL);
        com.cocode.vcode.ide.data.model.EditorFile ef3 = new com.cocode.vcode.ide.data.model.EditorFile(
                "id3", new File(projectDir, "vcode_terminal_3.term"), "", com.cocode.vcode.ide.core.model.FileType.TERMINAL);

        assertEquals("Terminal", ef1.getFileName());
        assertEquals("Terminal 2", ef2.getFileName());
        assertEquals("Terminal 3", ef3.getFileName());
    }

    @Test
    public void testRemoveVirtualFile() {
        ProjectState state = new ProjectState("test-proj");
        state.setVirtualFile("vcode_terminal.term", "{\"buffer\":\"abc\"}");
        assertNotNull(state.getVirtualFile("vcode_terminal.term"));

        state.removeVirtualFile("vcode_terminal.term");
        org.junit.Assert.assertNull(state.getVirtualFile("vcode_terminal.term"));
    }

    @Test
    public void testTerminalSessionHistoryCappingAt50() {
        android.content.Context ctx = org.robolectric.RuntimeEnvironment.getApplication();
        com.cocode.vcode.ide.core.terminal.TerminalSession session =
                new com.cocode.vcode.ide.core.terminal.TerminalSession(ctx, projectDir);

        java.util.List<String> sixtyCmds = new java.util.ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            sixtyCmds.add("cmd " + i);
        }
        session.setHistory(sixtyCmds);

        java.util.List<String> restoredHistory = session.getHistory();
        assertEquals(50, restoredHistory.size());
        assertEquals("cmd 11", restoredHistory.get(0));
        assertEquals("cmd 60", restoredHistory.get(49));
    }
}
