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
}
