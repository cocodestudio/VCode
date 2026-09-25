package com.cocode.vcode.ide.git.core;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

import com.cocode.vcode.ide.git.model.GitFileItem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GitRepositoryTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private GitRepository gitRepository;

    @Before
    public void setUp() {
        gitRepository = new GitRepository();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testInitRepository() throws Exception {
        File repoDir = tempFolder.newFolder("test-repo");
        gitRepository.setConfiguredDefaultBranch("master");
        gitRepository.openRepository(repoDir);

        File gitDir = new File(repoDir, ".git");
        assertTrue(".git directory should exist after init", gitDir.exists());
        assertTrue(".git should be a directory", gitDir.isDirectory());
    }

    @Test
    public void testGetUnstagedFiles() throws Exception {
        File repoDir = tempFolder.newFolder("test-repo-unstaged");
        gitRepository.openRepository(repoDir);

        // Create a new file
        File newFile = new File(repoDir, "test.txt");
        try (FileWriter fw = new FileWriter(newFile)) {
            fw.write("hello");
        }

        List<GitFileItem> unstaged = gitRepository.getUnstagedFiles();
        assertTrue("There should be at least 1 unstaged file", unstaged.size() >= 1);
        
        boolean foundTestTxt = false;
        for(GitFileItem item : unstaged) {
            if(item.getFileName().equals("test.txt")) {
                foundTestTxt = true;
                assertEquals("?", item.getStatus()); // Untracked
                break;
            }
        }
        assertTrue("test.txt should be unstaged", foundTestTxt);
    }

    @Test
    public void testStageAndUnstageFile() throws Exception {
        File repoDir = tempFolder.newFolder("test-repo-staged");
        gitRepository.openRepository(repoDir);
        
        // Initial commit to ensure HEAD exists for reset
        File initFile = new File(repoDir, "init.txt");
        try (FileWriter fw = new FileWriter(initFile)) {
            fw.write("init");
        }
        gitRepository.stageFile("init.txt");
        try {
            org.eclipse.jgit.api.Git.open(repoDir).commit().setMessage("Init").call();
        } catch (Exception e) {}

        File newFile = new File(repoDir, "stage.txt");
        try (FileWriter fw = new FileWriter(newFile)) {
            fw.write("hello");
        }

        gitRepository.stageFile("stage.txt");

        List<GitFileItem> staged = gitRepository.getStagedFiles();
        assertEquals("There should be 1 staged file", 1, staged.size());
        assertEquals("stage.txt", staged.get(0).getFileName());
        
        List<GitFileItem> unstaged = gitRepository.getUnstagedFiles();
        boolean stageIsUnstaged = false;
        for(GitFileItem item : unstaged) {
            if(item.getFileName().equals("stage.txt")) stageIsUnstaged = true;
        }
        assertTrue("stage.txt should not be in unstaged files", !stageIsUnstaged);

        gitRepository.unstageFile("stage.txt");
        
        staged = gitRepository.getStagedFiles();
        boolean stageStaged = false;
        for(GitFileItem item : staged) {
            if(item.getFileName().equals("stage.txt")) stageStaged = true;
        }
        assertTrue("stage.txt should not be staged", !stageStaged);
        
        unstaged = gitRepository.getUnstagedFiles();
        boolean stageUnstaged = false;
        for(GitFileItem item : unstaged) {
            if(item.getFileName().equals("stage.txt")) stageUnstaged = true;
        }
        assertTrue("stage.txt should be unstaged after unstage", stageUnstaged);
    }

    @Test
    public void testBranchComparisonAndDiff() throws Exception {
        File repoDir = tempFolder.newFolder("test-repo-diff");
        gitRepository.setConfiguredDefaultBranch("master");
        gitRepository.openRepository(repoDir);

        File f1 = new File(repoDir, "file1.txt");
        try (FileWriter fw = new FileWriter(f1)) {
            fw.write("hello master\n");
        }
        gitRepository.stageFile("file1.txt");
        org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(repoDir);
        git.commit().setMessage("Initial master commit").call();

        // Create feature branch
        gitRepository.createBranch("feature", "master");
        gitRepository.checkoutBranch("feature");

        File f2 = new File(repoDir, "file2.txt");
        try (FileWriter fw = new FileWriter(f2)) {
            fw.write("hello feature\nline 2\n");
        }
        gitRepository.stageFile("file2.txt");
        git.commit().setMessage("Feature commit 1").call();

        // Switch back to master
        gitRepository.checkoutBranch("master");

        // Test Branch Comparison
        GitRepository.BranchComparison comparison = gitRepository.getBranchComparison("master", "feature");
        assertEquals("Master should be 1 commit behind feature", 1, comparison.getBehindCount());
        assertEquals("Master should be 0 commits ahead of feature", 0, comparison.getAheadCount());

        // Test Incoming Commits
        List<com.cocode.vcode.ide.git.model.CommitItem> incoming = gitRepository.getIncomingCommits("master", "feature");
        assertEquals("Should have 1 incoming commit", 1, incoming.size());
        assertEquals("Feature commit 1", incoming.get(0).getMessage());

        // Test Changed Files between refs
        List<GitFileItem> changedFiles = gitRepository.getChangedFilesBetweenRefs("master", "feature");
        assertEquals("Should have 1 changed file", 1, changedFiles.size());
        assertEquals("file2.txt", changedFiles.get(0).getFileName());

        // Test Diff between refs for file
        String diffText = gitRepository.getDiffBetweenRefsForFile("master", "feature", "file2.txt");
        assertTrue("Diff text should not be null or empty", diffText != null && !diffText.isEmpty());
        assertTrue("Diff should contain additions for file2", diffText.contains("+hello feature"));
    }

    @Test
    public void testCheckoutConflictDetection() throws Exception {
        File repoDir = tempFolder.newFolder("test-repo-checkout-conflict");
        gitRepository.setConfiguredDefaultBranch("master");
        gitRepository.openRepository(repoDir);

        File f1 = new File(repoDir, "conflict.txt");
        try (FileWriter fw = new FileWriter(f1)) {
            fw.write("original version\n");
        }
        gitRepository.stageFile("conflict.txt");
        org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(repoDir);
        git.commit().setMessage("Initial commit").call();

        // Create feature branch and modify conflict.txt
        gitRepository.createBranch("feature", "master");
        gitRepository.checkoutBranch("feature");
        try (FileWriter fw = new FileWriter(f1)) {
            fw.write("feature version\n");
        }
        gitRepository.stageFile("conflict.txt");
        git.commit().setMessage("Feature commit").call();

        // Checkout master
        gitRepository.checkoutBranch("master");

        // Now make an UNCOMMITTED local change to conflict.txt in master
        try (FileWriter fw = new FileWriter(f1)) {
            fw.write("local uncommitted change\n");
        }

        // Attempt to checkout feature -> should throw GitCheckoutConflictException!
        boolean caughtCheckoutConflict = false;
        try {
            gitRepository.checkoutBranch("feature");
        } catch (GitRepository.GitCheckoutConflictException e) {
            caughtCheckoutConflict = true;
            assertTrue("Conflicting files list should contain conflict.txt", e.getConflictingFiles().contains("conflict.txt"));
        }
        assertTrue("Should have caught GitCheckoutConflictException on checkout conflict", caughtCheckoutConflict);
    }
}
