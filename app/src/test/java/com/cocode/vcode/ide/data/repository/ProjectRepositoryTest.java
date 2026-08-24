package com.cocode.vcode.ide.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.cocode.vcode.ide.data.model.Project;
import com.cocode.vcode.ide.data.model.Result;
import com.cocode.vcode.ide.utils.FileUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class ProjectRepositoryTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private ProjectRepository projectRepository;
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        projectRepository = new ProjectRepository(context);
    }

    @Test
    public void testCreateProject() throws Exception {
        LiveData<Result<Project>> createLiveData = projectRepository.createProject("Test Project", "index.html", "Blank", false, "main");

        CountDownLatch latch = new CountDownLatch(1);
        final Result<Project>[] resultHolder = new Result[1];
        
        createLiveData.observeForever(result -> {
            resultHolder[0] = result;
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(resultHolder[0].isSuccess());

        Project project = resultHolder[0].getData();
        assertNotNull(project);
        assertEquals("Test Project", project.getName());
        assertEquals("index.html", project.getMainFile());

        File projectsDir = FileUtils.getProjectsDir(context);
        File projectDir = new File(projectsDir, project.getId());
        assertTrue("Project directory should exist", projectDir.exists());
        
        File mainFile = new File(projectDir, "index.html");
        assertTrue("Main file should exist", mainFile.exists());
    }

    @Test
    public void testFindProjectRoot() throws Exception {
        // Mock a project root with .vcode/meta/project.json
        File root = tempFolder.newFolder("MyProject");
        File metaDir = new File(root, ProjectRepository.VCODE_DIR + "/" + ProjectRepository.META_DIR);
        assertTrue(metaDir.mkdirs());
        new File(metaDir, ProjectRepository.PROJECT_FILE).createNewFile();

        File someSubDir = new File(root, "src/main/java");
        someSubDir.mkdirs();
        File someFile = new File(someSubDir, "Main.java");
        someFile.createNewFile();

        File foundRoot = ProjectRepository.findProjectRoot(someFile);
        assertNotNull("Project root should be found", foundRoot);
        assertEquals(root.getAbsolutePath(), foundRoot.getAbsolutePath());
    }
}
