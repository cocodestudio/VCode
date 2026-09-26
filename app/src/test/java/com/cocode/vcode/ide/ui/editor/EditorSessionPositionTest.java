package com.cocode.vcode.ide.ui.editor;

import android.content.Context;

import org.robolectric.RuntimeEnvironment;

import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.data.model.ProjectState;
import com.cocode.vcode.ide.data.repository.FileRepository;
import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.cocode.vcode.ide.data.repository.ProjectStateRepository;
import com.cocode.vcode.ide.data.repository.SettingsRepository;
import com.cocode.vcode.ide.utils.FileUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
public class EditorSessionPositionTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Context context;
    private File projectDir;
    private ProjectStateRepository stateRepo;
    private EditorViewModel viewModel;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        File vcodeProjects = tempFolder.newFolder("VCodeProjects");
        projectDir = new File(vcodeProjects, "MyTestApp");
        projectDir.mkdirs();

        // Create test files
        File indexHtml = new File(projectDir, "index.html");
        FileUtils.writeFile(indexHtml, "<html><body><h1>Hello</h1></body></html>");

        File srcDir = new File(projectDir, "src");
        srcDir.mkdirs();
        File appJs = new File(srcDir, "app.js");
        FileUtils.writeFile(appJs, "console.log('App loaded');\nconsole.log('Line 2');");

        // Pre-populate session.json
        stateRepo = new ProjectStateRepository();
        ProjectState initialState = new ProjectState("my-test-app");
        initialState.setActiveTabIndex(0);
        initialState.setOpenFilePaths(Arrays.asList("index.html"));
        initialState.setScrollFor("index.html", 300);
        initialState.setCursorFor("index.html", 45);
        initialState.setScrollFor("src/app.js", 800);
        initialState.setCursorFor("src/app.js", 120);
        stateRepo.saveStateSync(projectDir, initialState);

        FileRepository fileRepo = new FileRepository();
        SettingsRepository settingsRepo = new SettingsRepository(context);
        ProjectRepository projectRepo = new ProjectRepository(context);

        viewModel = new EditorViewModel(context, fileRepo, stateRepo, settingsRepo, projectRepo);
    }

    private void pumpEvents() {
        for (int i = 0; i < 50; i++) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {}
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        }
    }

    @Test
    public void testOpenFileFromTreePreservesScrollAndCursor() {
        viewModel.initProject(projectDir, "my-test-app", "MyTestApp", false);
        pumpEvents();

        // Verify index.html tab was restored with initial scroll & cursor
        List<EditorFile> openFiles = viewModel.getOpenFiles().getValue();
        assertNotNull(openFiles);
        assertEquals(1, openFiles.size());
        assertEquals("index.html", openFiles.get(0).getFileName());
        assertEquals(300, openFiles.get(0).getScrollY());
        assertEquals(45, openFiles.get(0).getCursorPosition());

        // Now open src/app.js from file tree
        File appJs = new File(new File(projectDir, "src"), "app.js");
        viewModel.openFile(appJs);
        pumpEvents();

        // Check if src/app.js tab is open
        openFiles = viewModel.getOpenFiles().getValue();
        assertNotNull(openFiles);
        assertEquals(2, openFiles.size());
        EditorFile appJsFile = openFiles.get(1);
        assertEquals("app.js", appJsFile.getFileName());

        // Check in-memory EditorFile scroll and cursor
        System.out.println("appJs EditorFile scrollY: " + appJsFile.getScrollY());
        System.out.println("appJs EditorFile cursor: " + appJsFile.getCursorPosition());

        // Check persisted session.json
        ProjectState savedState = stateRepo.loadStateSync(projectDir, "my-test-app");
        System.out.println("Saved scroll for src/app.js: " + savedState.getScrollFor("src/app.js"));
        System.out.println("Saved cursor for src/app.js: " + savedState.getCursorFor("src/app.js"));
        System.out.println("All scroll positions: " + savedState.getScrollPositions());
        System.out.println("All cursor positions: " + savedState.getCursorPositions());

        assertEquals("appJsFile in-memory scroll must be 800", 800, appJsFile.getScrollY());
        assertEquals("appJsFile in-memory cursor must be 120", 120, appJsFile.getCursorPosition());

        assertEquals("src/app.js scroll position must be preserved", 800, savedState.getScrollFor("src/app.js"));
        assertEquals("src/app.js cursor position must be preserved", 120, savedState.getCursorFor("src/app.js"));
    }

    @Test
    public void testOpenFileWithSourceUriPreservesScrollAndCursor() {
        viewModel.initProject(projectDir, "my-test-app", "MyTestApp", false);
        pumpEvents();

        File appJs = new File(new File(projectDir, "src"), "app.js");
        viewModel.openFile(appJs, "content://com.cocode.vcode.provider/test");
        pumpEvents();

        List<EditorFile> openFiles = viewModel.getOpenFiles().getValue();
        assertNotNull(openFiles);
        assertEquals(2, openFiles.size());
        EditorFile appJsFile = openFiles.get(1);

        assertEquals(800, appJsFile.getScrollY());
        assertEquals(120, appJsFile.getCursorPosition());

        ProjectState savedState = stateRepo.loadStateSync(projectDir, "my-test-app");
        assertEquals(800, savedState.getScrollFor("src/app.js"));
        assertEquals(120, savedState.getCursorFor("src/app.js"));
    }

    @Test
    public void testSwitchToAlreadyOpenFilePreservesPositions() {
        viewModel.initProject(projectDir, "my-test-app", "MyTestApp", false);
        pumpEvents();

        File appJs = new File(new File(projectDir, "src"), "app.js");
        viewModel.openFile(appJs);
        pumpEvents();

        // Switch back to index.html (already open at tab 0)
        File indexHtml = new File(projectDir, "index.html");
        viewModel.openFile(indexHtml);
        pumpEvents();

        assertEquals(Integer.valueOf(0), viewModel.getActiveTabIndex().getValue());
        List<EditorFile> openFiles = viewModel.getOpenFiles().getValue();
        assertEquals(2, openFiles.size());
        assertEquals(300, openFiles.get(0).getScrollY());
        assertEquals(45, openFiles.get(0).getCursorPosition());
        assertEquals(800, openFiles.get(1).getScrollY());
        assertEquals(120, openFiles.get(1).getCursorPosition());

        ProjectState savedState = stateRepo.loadStateSync(projectDir, "my-test-app");
        assertEquals(300, savedState.getScrollFor("index.html"));
        assertEquals(45, savedState.getCursorFor("index.html"));
        assertEquals(800, savedState.getScrollFor("src/app.js"));
        assertEquals(120, savedState.getCursorFor("src/app.js"));
    }
}
