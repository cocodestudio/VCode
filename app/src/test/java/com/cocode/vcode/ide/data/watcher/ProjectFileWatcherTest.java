package com.cocode.vcode.ide.data.watcher;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ProjectFileWatcherTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File projectRoot;

    @Before
    public void setUp() throws Exception {
        projectRoot = tempFolder.newFolder("TestWorkspace");
        new File(projectRoot, "src").mkdirs();
        new File(projectRoot, ".git").mkdirs();
        new File(projectRoot, ".vcode").mkdirs();
    }

    @Test
    public void testWatcherStartsAndStops() {
        AtomicInteger eventCount = new AtomicInteger(0);
        ProjectFileWatcher watcher = new ProjectFileWatcher(projectRoot, eventCount::incrementAndGet);

        assertFalse(watcher.isWatching());
        watcher.startWatching();
        assertTrue(watcher.isWatching());

        watcher.stopWatching();
        assertFalse(watcher.isWatching());
    }

    @Test
    public void testWatcherDebounceExecution() {
        AtomicInteger eventCount = new AtomicInteger(0);
        ProjectFileWatcher watcher = new ProjectFileWatcher(projectRoot, eventCount::incrementAndGet);

        watcher.startWatching();

        // Simulate looper idle
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(0, eventCount.get());

        watcher.stopWatching();
    }
}
