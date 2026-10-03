package com.cocode.vcode.ide.data.model;

import com.cocode.vcode.ide.core.events.IdeEventBus;
import com.cocode.vcode.ide.core.events.ProjectEvent;
import com.cocode.vcode.ide.data.npm.NpmProjectFacet;
import com.cocode.vcode.ide.data.repository.ProjectRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.rules.TemporaryFolder;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FacetedProjectTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setUp() {
        IdeEventBus.getInstance().clearAllSubscriptions();
    }

    @After
    public void tearDown() {
        IdeEventBus.getInstance().clearAllSubscriptions();
    }

    @Test
    public void testFacetLifecycleAndRegistry() {
        Project project = new Project("proj-1", "Test Project", 1000L, 1000L, "index.html", 5);
        assertFalse(project.hasFacet(NpmProjectFacet.class));

        NpmProjectFacet facet = new NpmProjectFacet();
        project.addFacet(facet);

        assertTrue(project.hasFacet(NpmProjectFacet.class));
        assertNotNull(project.getFacet(NpmProjectFacet.class));
        assertTrue(facet.isAttached());
        assertEquals("npm", facet.getFacetId());

        project.removeFacet(NpmProjectFacet.class);
        assertFalse(project.hasFacet(NpmProjectFacet.class));
        assertFalse(facet.isAttached());
    }

    @Test
    public void testNpmProjectFacetWithManifest() throws IOException {
        File rootDir = tempFolder.newFolder("npm_app");
        File pkgJson = new File(rootDir, "package.json");
        try (FileWriter writer = new FileWriter(pkgJson)) {
            writer.write("{\n" +
                    "  \"name\": \"my-app\",\n" +
                    "  \"version\": \"2.1.0\",\n" +
                    "  \"private\": true,\n" +
                    "  \"scripts\": { \"start\": \"vite\" },\n" +
                    "  \"dependencies\": { \"axios\": \"^1.6.0\" },\n" +
                    "  \"devDependencies\": { \"typescript\": \"^5.0.0\" }\n" +
                    "}");
        }

        Project project = new Project("proj-npm", "My App", 1000L, 1000L, "index.html", 1);
        project.setRootDir(rootDir);

        NpmProjectFacet facet = new NpmProjectFacet(rootDir);
        project.addFacet(facet);

        assertTrue(facet.hasPackageJson());
        assertEquals("my-app", facet.getName());
        assertEquals("2.1.0", facet.getVersion());
        assertTrue(facet.isPrivate());
        assertTrue(facet.hasDependency("axios"));
        assertTrue(facet.hasDependency("typescript"));
        assertEquals("vite", facet.getScripts().get("start"));

        // Subscribe to dependency changes on event bus
        AtomicBoolean eventReceived = new AtomicBoolean(false);
        IdeEventBus.getInstance().subscribe(ProjectEvent.DependencyChangedEvent.class, event -> {
            if ("lodash".equals(event.getPackageName())) {
                eventReceived.set(true);
            }
        });

        // Add a new dependency
        boolean added = facet.addDependency("lodash", "^4.17.21", false);
        assertTrue(added);
        assertTrue(facet.hasDependency("lodash"));
        assertTrue(eventReceived.get());

        // Project disposal detaches facet
        project.dispose();
        assertFalse(facet.isAttached());
    }

    @Test
    public void testHeuristicRootDiscoveryWithoutVCodeFolder() throws IOException {
        File workspace = tempFolder.newFolder("standalone_npm_repo");
        File srcDir = new File(workspace, "src/components");
        srcDir.mkdirs();
        File subFile = new File(srcDir, "App.vue");
        subFile.createNewFile();

        File pkgJson = new File(workspace, "package.json");
        try (FileWriter writer = new FileWriter(pkgJson)) {
            writer.write("{\"name\":\"vue-project\"}");
        }

        // Test root discovery from deep sub-file
        File discoveredRoot = ProjectRepository.findProjectRoot(subFile);
        assertNotNull(discoveredRoot);
        assertEquals(workspace.getCanonicalPath(), discoveredRoot.getCanonicalPath());

        // Test synthesizing project for directory
        Project project = ProjectRepository.getProjectForDirectory(workspace);
        assertNotNull(project);
        assertEquals(workspace.getCanonicalPath(), project.getRootDir().getCanonicalPath());
        assertTrue(project.hasFacet(NpmProjectFacet.class));
    }
}
