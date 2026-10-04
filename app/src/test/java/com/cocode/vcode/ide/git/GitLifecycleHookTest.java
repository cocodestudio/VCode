package com.cocode.vcode.ide.git;

import androidx.annotation.NonNull;

import com.cocode.vcode.ide.core.events.GitEvent;
import com.cocode.vcode.ide.core.events.IdeEventBus;
import com.cocode.vcode.ide.core.events.ProjectEvent;
import com.cocode.vcode.ide.git.core.GitEventListener;
import com.cocode.vcode.ide.git.core.GitRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GitLifecycleHookTest {

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
    public void testGitLifecycleListenerAndEventBusBroadcast() throws Exception {
        File repoDir = tempFolder.newFolder("git_test_repo");
        File pkgJson = new File(repoDir, "package.json");
        try (FileWriter writer = new FileWriter(pkgJson)) {
            writer.write("{\"name\":\"git-npm-app\"}");
        }

        GitRepository repository = new GitRepository();
        repository.openRepository(repoDir);

        AtomicInteger preCommitCount = new AtomicInteger(0);
        AtomicInteger postCommitCount = new AtomicInteger(0);
        AtomicBoolean preCommitEventBus = new AtomicBoolean(false);
        AtomicBoolean postCommitEventBus = new AtomicBoolean(false);

        // 1. Direct GitEventListener
        GitEventListener listener = new GitEventListener() {
            @Override
            public void onPreCommit(@NonNull File repoRoot, @NonNull String commitMessage) {
                preCommitCount.incrementAndGet();
            }

            @Override
            public void onPostCommit(@NonNull File repoRoot, @NonNull String commitId, @NonNull String commitMessage) {
                postCommitCount.incrementAndGet();
            }
        };

        repository.addEventListener(listener);
        assertEquals(1, repository.getEventListeners().size());

        // 2. Global IdeEventBus listeners
        IdeEventBus.getInstance().subscribe(GitEvent.GitPreCommitEvent.class, event -> {
            preCommitEventBus.set(true);
        });
        IdeEventBus.getInstance().subscribe(GitEvent.GitPostCommitEvent.class, event -> {
            postCommitEventBus.set(true);
        });

        // Create initial file & commit
        File testFile = new File(repoDir, "README.md");
        try (FileWriter writer = new FileWriter(testFile)) {
            writer.write("# Hello Git");
        }
        repository.stageAll();
        repository.commit("feat: initial commit", "Adeel", "adeel@test.com");

        assertEquals(1, preCommitCount.get());
        assertEquals(1, postCommitCount.get());
        assertTrue(preCommitEventBus.get());
        assertTrue(postCommitEventBus.get());

        // Unregister listener
        repository.removeEventListener(listener);
        assertEquals(0, repository.getEventListeners().size());
    }

    @Test
    public void testPostCheckoutEmitsDependencyChangeEvent() throws Exception {
        File repoDir = tempFolder.newFolder("git_branch_repo");
        File pkgJson = new File(repoDir, "package.json");
        try (FileWriter writer = new FileWriter(pkgJson)) {
            writer.write("{\"name\":\"branch-app\"}");
        }

        GitRepository repository = new GitRepository();
        repository.openRepository(repoDir);

        // Initial commit
        repository.stageAll();
        repository.commit("init", "Adeel", "adeel@test.com");

        // Create feature branch
        repository.createBranch("feature-1", "HEAD");

        AtomicBoolean checkoutEventBus = new AtomicBoolean(false);
        AtomicBoolean dependencyEventBus = new AtomicBoolean(false);

        IdeEventBus.getInstance().subscribe(GitEvent.GitPostCheckoutEvent.class, event -> {
            if ("feature-1".equals(event.getBranch())) {
                checkoutEventBus.set(true);
            }
        });
        IdeEventBus.getInstance().subscribe(ProjectEvent.DependencyChangedEvent.class, event -> {
            if ("package.json".equals(event.getPackageName())) {
                dependencyEventBus.set(true);
            }
        });

        repository.checkoutBranch("feature-1");

        assertTrue(checkoutEventBus.get());
        assertTrue(dependencyEventBus.get());
    }

    @Test
    public void testPreCommitVetoAbortsCommit() throws Exception {
        File repoDir = tempFolder.newFolder("git_veto_repo");
        GitRepository repository = new GitRepository();
        repository.openRepository(repoDir);

        File testFile = new File(repoDir, "README.md");
        try (FileWriter writer = new FileWriter(testFile)) {
            writer.write("# Veto Test");
        }
        repository.stageAll();

        GitEventListener vetoListener = new GitEventListener() {
            @Override
            public boolean shouldAllowCommit(@NonNull File repoRoot, @NonNull String commitMessage) {
                return false; // Veto all commits
            }
        };

        repository.addEventListener(vetoListener);

        boolean exceptionThrown = false;
        try {
            repository.commit("feat: should be rejected", "Adeel", "adeel@test.com");
        } catch (GitRepository.GitPreCommitVetoException e) {
            exceptionThrown = true;
            assertTrue(e.getMessage().contains("Commit rejected by pre-commit listener"));
        }

        assertTrue("Expected GitPreCommitVetoException to be thrown", exceptionThrown);
        repository.removeEventListener(vetoListener);
    }
}
