package com.cocode.vcode.ide.git.core;

import androidx.annotation.NonNull;

import com.cocode.vcode.ide.VCodeApplication;
import com.cocode.vcode.ide.git.model.BranchItem;
import com.cocode.vcode.ide.git.model.CommitItem;
import com.cocode.vcode.ide.git.model.GitFileItem;
import com.cocode.vcode.ide.utils.DateUtils;
import com.cocode.vcode.ide.git.model.CommitInfo;
import com.cocode.vcode.ide.git.model.FileStatus;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Main local repository controller interacting directly with the JGit engine layer.
 * Implements comprehensive version control mechanics, managing work tree staging matrices,
 * commit building, revision log histories, diff generation, reset workflows, and branch orchestration.
 */
public class GitRepository {
    /**
     * Attaches to an existing repository on disk or sets up a brand new one
     * using the configured fallback branch configurations
     */
    // Default exclusion patterns written to .gitignore on fresh repository initialization
    private static final String DEFAULT_GITIGNORE =
            "node_modules/\n.DS_Store\n*.log\ndist/\nbuild/\n.env\n.env.local\n*.class\n*.jar\n.vcode/";
    private Git git;
    private String configuredDefaultBranch = "main";
    private File repoDir;

    /**
     * Configures the initial tracking branch naming preference used during fresh repository setups.
     */
    public void setConfiguredDefaultBranch(String branchName) {
        if (branchName != null && !branchName.trim().isEmpty()) {
            this.configuredDefaultBranch = branchName.trim();
        }
    }

    public void openRepository(File projectDir) throws Exception {
        this.repoDir = projectDir;
        File gitDir = new File(projectDir, ".git");
        if (gitDir.exists()) {
            git = Git.open(projectDir);
        } else {
            git = Git.init()
                    .setDirectory(projectDir)
                    .setInitialBranch(configuredDefaultBranch)
                    .call();
            File gitignore = new File(projectDir, ".gitignore");
            if (!gitignore.exists()) {
                try (java.io.FileWriter fw = new java.io.FileWriter(gitignore)) {
                    fw.write(DEFAULT_GITIGNORE);
                }
            }
        }
        ensureInternalFilesIgnored(gitDir);
    }

    public File getRepoDir() {
        return repoDir;
    }

    /**
     * Automatically reviews and updates the hidden .git/info/exclude configuration sheet.
     * Prevents internal, non-source application tracking data (such as session maps or
     * metadata descriptors) from polluting user version logs without littering the .gitignore file.
     */
    private void ensureInternalFilesIgnored(File gitDir) {
        try {
            File infoDir = new File(gitDir, "info");
            if (!infoDir.exists()) {
                infoDir.mkdirs();
            }

            File excludeFile = new File(infoDir, "exclude");
            StringBuilder content = new StringBuilder();

            if (excludeFile.exists()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(new java.io.FileInputStream(excludeFile), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                }
            }

            boolean modified = false;
            String[] internalFiles = {".vcode/"};
            for (String file : internalFiles) {
                if (!content.toString().contains(file)) {
                    content.append(file).append("\n");
                    modified = true;
                }
            }

            if (modified) {
                try (java.io.BufferedWriter writer = new java.io.BufferedWriter(
                        new java.io.OutputStreamWriter(new java.io.FileOutputStream(excludeFile), java.nio.charset.StandardCharsets.UTF_8))) {
                    writer.write(content.toString());
                }
            }
        } catch (Exception e) {
            android.util.Log.e("VCode", "Error in ensureInternalFilesIgnored", e);
        }
    }

    /**
     * Inspects index cache trees to gather all components successfully added to the staged index.
     */
    public List<GitFileItem> getStagedFiles() throws Exception {
        Status status = git.status().call();
        List<GitFileItem> items = new ArrayList<>();
        addFiles(items, status.getAdded(), "A", true);
        addFiles(items, status.getChanged(), "M", true);
        addFiles(items, status.getRemoved(), "D", true);
        return items;
    }

    /**
     * Scans the working area to locate un-indexed alterations, modifications, untracked components, or deletions.
     */
    public List<GitFileItem> getUnstagedFiles() throws Exception {
        Status status = git.status().call();
        List<GitFileItem> items = new ArrayList<>();
        addFiles(items, status.getModified(), "M", false);
        addFiles(items, status.getUntracked(), "?", false);
        addFiles(items, status.getMissing(), "D", false);
        return items;
    }

    /**
     * Helper utility populating item arrays, applying filters to keep workspace structure
     * text documents out of change visibility matrices.
     */
    private void addFiles(List<GitFileItem> list, Set<String> paths, String status, boolean staged) {
        for (String path : paths) {
            String fileName = new File(path).getName();
            if (path.startsWith(".vcode/")) {
                continue;
            }
            list.add(new GitFileItem(path, fileName, status, staged));
        }
    }

    /**
     * Stages an individual file target into the index. If the file has been deleted locally,
     * dispatches removal tracking directives instead.
     */
    public void stageFile(String path) throws Exception {
        File file = new File(git.getRepository().getWorkTree(), path);
        if (!file.exists()) {
            git.rm().addFilepattern(path).call();
        } else {
            git.add().addFilepattern(path).call();
        }
    }

    /**
     * Removes an individual target file from the staged index area without altering its contents on disk.
     */
    public void unstageFile(String path) throws Exception {
        git.reset().addPath(path).call();
    }

    /**
     * Aggregates all modified and untracked changes across the current working tree directory
     * directly into the staged index layer.
     */
    public void stageAll() throws Exception {
        git.add().addFilepattern(".").call();
        git.add().setUpdate(true).addFilepattern(".").call();
    }

    /**
     * Wipes structural staging properties globally across the workspace, resetting the index tier entirely.
     */
    public void unstageAll() throws Exception {
        git.reset().call();
    }

    /**
     * Generates a line-by-line unified syntax patch text representation for a target file.
     * Evaluates boundaries depending on whether the asset lives inside staging or working sectors.
     */
    public String getFileDiff(String path, boolean staged) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.setRepository(git.getRepository());

            AbstractTreeIterator oldTree;
            AbstractTreeIterator newTree;

            if (staged) {
                oldTree = getHeadTree();
                newTree = new DirCacheIterator(git.getRepository().readDirCache());
            } else {
                oldTree = new DirCacheIterator(git.getRepository().readDirCache());
                newTree = new FileTreeIterator(git.getRepository());
            }

            List<DiffEntry> entries = formatter.scan(oldTree, newTree);
            for (DiffEntry entry : entries) {
                if (entry.getNewPath().equals(path) || entry.getOldPath().equals(path)) {
                    formatter.format(entry);
                }
            }
        }
        return out.toString();
    }

    /**
     * Walks the tree nodes linked against a specific commit identifier hash to reconstruct its file delta status list.
     */
    public List<GitFileItem> getFilesInCommit(String commitSha) throws Exception {
        List<GitFileItem> items = new ArrayList<>();
        Repository repository = git.getRepository();
        ObjectId commitId = repository.resolve(commitSha);

        try (org.eclipse.jgit.revwalk.RevWalk walk = new org.eclipse.jgit.revwalk.RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(commitId);
            RevCommit parent = commit.getParentCount() > 0 ? walk.parseCommit(commit.getParent(0).getId()) : null;

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 DiffFormatter df = new DiffFormatter(baos)) {
                df.setRepository(repository);
                List<DiffEntry> diffs;

                if (parent != null) {
                    diffs = df.scan(parent.getTree(), commit.getTree());
                } else {
                    EmptyTreeIterator emptyTree = new EmptyTreeIterator();
                    diffs = df.scan(emptyTree, new CanonicalTreeParser(null, walk.getObjectReader(), commit.getTree()));
                }

                for (DiffEntry diff : diffs) {
                    String path = diff.getChangeType() == DiffEntry.ChangeType.DELETE ? diff.getOldPath() : diff.getNewPath();
                    String name = new File(path).getName();

                    String status = "M";
                    if (diff.getChangeType() == DiffEntry.ChangeType.ADD) status = "A";
                    else if (diff.getChangeType() == DiffEntry.ChangeType.DELETE) status = "D";

                    items.add(new GitFileItem(path, name, status, false));
                }
            }
        }
        return items;
    }

    /**
     * Extracts unified string patches representing modifications applied across a single file asset within an archived commit.
     */
    public String getCommitFileDiff(String commitSha, String path) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Repository repository = git.getRepository();
        ObjectId commitId = repository.resolve(commitSha);

        try (org.eclipse.jgit.revwalk.RevWalk walk = new org.eclipse.jgit.revwalk.RevWalk(repository);
             DiffFormatter df = new DiffFormatter(out)) {
            df.setRepository(repository);
            RevCommit commit = walk.parseCommit(commitId);
            RevCommit parent = commit.getParentCount() > 0 ? walk.parseCommit(commit.getParent(0).getId()) : null;

            List<DiffEntry> diffs;
            if (parent != null) {
                diffs = df.scan(parent.getTree(), commit.getTree());
            } else {
                EmptyTreeIterator emptyTree = new EmptyTreeIterator();
                diffs = df.scan(emptyTree, new CanonicalTreeParser(null, walk.getObjectReader(), commit.getTree()));
            }

            for (DiffEntry entry : diffs) {
                if (entry.getNewPath().equals(path) || entry.getOldPath().equals(path)) {
                    df.format(entry);
                }
            }
        }
        return out.toString();
    }

    /**
     * Binds a standard text message row to create a commit transaction using global profile markers.
     */
    public void commit(String message) throws Exception {
        git.commit().setMessage(message).call();
    }

    /**
     * Overloaded commit processor explicitly anchoring custom profile identity strings to authored changes fields.
     */
    public void commit(String message, String authorName, String authorEmail) throws Exception {
        git.commit()
                .setMessage(message)
                .setAuthor(authorName, authorEmail)
                .setCommitter(authorName, authorEmail)
                .call();
    }

    /**
     * Combines currently staged updates directly into the previous commit tip block, modifying its message field.
     */
    public void amendCommit(String message) throws Exception {
        git.commit().setAmend(true).setMessage(message).call();
    }

    /**
     * Traverses head references to compile chronological logs histories list sheets.
     * Gracefully exits with blank results vectors if the repository is completely fresh and uncommitted.
     */
    public List<CommitItem> getCommitHistory() throws Exception {
        List<CommitItem> items = new ArrayList<>();
        try {
            Iterable<RevCommit> commits = git.log().call();
            for (RevCommit commit : commits) {
                items.add(new CommitItem(
                        commit.getName(),
                        commit.abbreviate(7).name(),
                        commit.getFullMessage(),
                        commit.getAuthorIdent().getName(),
                        DateUtils.formatDate(commit.getAuthorIdent().getWhen())
                ));
            }
        } catch (org.eclipse.jgit.api.errors.NoHeadException e) {
            // Freshly initialized repositories have 0 commits.
        }
        return items;
    }

    /**
     * Resolves the string identifier name describing the currently checked out branch head context.
     */
    public String getCurrentBranchName() {
        try {
            if (git != null && git.getRepository() != null) {
                return git.getRepository().getBranch();
            }
        } catch (Exception ignored) {
        }
        return configuredDefaultBranch;
    }

    /**
     * Pulls the string remote access web location parameter linked behind default 'origin' profiles trackers.
     */
    public String getRemoteUrl() {
        try {
            if (git != null && git.getRepository() != null) {
                return git.getRepository().getConfig().getString("remote", "origin", "url");
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * Commits configuration alterations directly to the underlying system config properties.
     * Wipes remote references cleanly if passed a null or blank string input payload.
     */
    public void setRemoteUrl(String url) throws Exception {
        if (git != null && git.getRepository() != null) {
            StoredConfig config = git.getRepository().getConfig();
            if (url == null || url.trim().isEmpty()) {
                config.unset("remote", "origin", "url");
                config.unset("remote", "origin", "fetch");
            } else {
                config.setString("remote", "origin", "url", url.trim());
                config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
            }
            config.save();
        }
    }

    /**
     * Evaluates references maps definitions parameters to fetch lists cataloging working branches nodes.
     *
     * @param remote True if the command layout loop should scan external remote tracking streams exclusively.
     */
    public List<BranchItem> getBranches(boolean remote) throws Exception {
        ListBranchCommand command = git.branchList();
        if (remote) {
            command.setListMode(ListBranchCommand.ListMode.REMOTE);
        }

        List<Ref> refs = command.call();
        List<BranchItem> items = new ArrayList<>();
        String currentBranch = git.getRepository().getFullBranch();

        for (Ref ref : refs) {
            boolean active = ref.getName().equals(currentBranch);
            items.add(new BranchItem(
                    Repository.shortenRefName(ref.getName()),
                    active,
                    ref.getName().startsWith("refs/remotes/"),
                    ref.getObjectId() != null ? ref.getObjectId().abbreviate(7).name() : ""
            ));
        }
        return items;
    }

    /**
     * Shifts active branch context scopes maps to match chosen local destination names markers.
     */
    public void checkoutBranch(String name) throws Exception {
        git.checkout().setName(name).call();
    }

    /**
     * Creates a local tracking branch from a remote tracking ref.
     * e.g. remoteName = "origin/feature-x" creates local "feature-x" tracking origin/feature-x
     */
    public void checkoutRemoteBranchAsLocal(String remoteName) throws Exception {
        String localName = remoteName.contains("/")
                ? remoteName.substring(remoteName.indexOf('/') + 1)
                : remoteName;
        git.checkout()
                .setCreateBranch(true)
                .setName(localName)
                .setStartPoint(remoteName)
                .setUpstreamMode(org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.TRACK)
                .call();
    }

    /**
     * Forks a brand new branch pointer pointing from an explicit historical start reference location.
     */
    public void createBranch(String name, String from) throws Exception {
        git.branchCreate().setName(name).setStartPoint(from).call();
    }

    /**
     * Forces the permanent destruction of chosen branch entries pointers out of local references sets arrays.
     */
    public void deleteBranch(String name) throws Exception {
        git.branchDelete().setBranchNames(name).setForce(true).call();
    }

    /**
     * Generates a structural rollback inversion commit designed to cleanly undo previous commit modifications changes vectors.
     */
    public void revertCommit(String commitSha, String authorName, String authorEmail) throws Exception {
        ObjectId commitId = git.getRepository().resolve(commitSha);
        if (commitId == null) throw new IllegalArgumentException("Commit SHA not found");

        StoredConfig config = git.getRepository().getConfig();
        config.setString("user", null, "name", authorName);
        config.setString("user", null, "email", authorEmail);
        config.save();

        org.eclipse.jgit.api.RevertCommand revert = git.revert().include(commitId);
        RevCommit result = revert.call();
        if (result == null) {
            java.util.List<String> conflicts = new java.util.ArrayList<>(git.status().call().getConflicting());
            if (!conflicts.isEmpty()) {
                throw new GitConflictException("Revert conflicts detected", conflicts);
            }
            throw new Exception("Revert failed. The commit may already have been reverted.");
        }
    }

    /**
     * Merges structural modifications streams running out from separate branches sources into the active workspace focus track.
     */
    public void mergeBranch(String branchName) throws Exception {
        ObjectId branchId = git.getRepository().resolve(branchName);
        if (branchId == null) throw new IllegalArgumentException("Branch reference not found");
        git.merge().include(branchId).call();
    }

    /**
     * Modifies name references records tags assigned across existing local branches targets.
     */
    public void renameBranch(String oldName, String newName) throws Exception {
        git.branchRename().setOldName(oldName).setNewName(newName).call();
    }

    /**
     * Dispatches localized branch modification revisions forward to external hosting platforms channels repositories.
     */
    public String push(String remoteUrl, String pat, String branch) throws Exception {
        org.eclipse.jgit.api.PushCommand pushCommand = git.push()
                .setRemote("origin")
                .add(branch);

        if (remoteUrl != null && remoteUrl.startsWith("http")) {
            pushCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(pat != null ? pat : "token", pat != null ? pat : ""));
        } else {
            pushCommand.setTransportConfigCallback(SshKeyManager.getTransportConfigCallback(VCodeApplication.getInstance()));
        }

        Iterable<org.eclipse.jgit.transport.PushResult> results = pushCommand.call();

        // JGit returns results without throwing even on rejection — inspect each ref update
        for (org.eclipse.jgit.transport.PushResult result : results) {
            for (org.eclipse.jgit.transport.RemoteRefUpdate update : result.getRemoteUpdates()) {
                org.eclipse.jgit.transport.RemoteRefUpdate.Status status = update.getStatus();
                if (status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK
                        || status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE) {
                    // success — continue
                } else if (status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD) {
                    throw new Exception(
                            "Push rejected: remote has changes your local branch doesn't have.\n" +
                                    "Pull first to integrate remote changes, then push again.\n" +
                                    "(Hint: Go to the Remote tab → Pull → then try Push again)");
                } else if (status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED) {
                    throw new Exception(
                            "Push rejected: the remote ref changed while preparing the push.\n" +
                                    "Pull first, then try again.");
                } else if (status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_OTHER_REASON) {
                    String reason = update.getMessage() != null ? update.getMessage() : "unknown reason";
                    throw new Exception("Push rejected by remote: " + reason);
                } else if (status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.NON_EXISTING) {
                    throw new Exception("Push failed: branch '" + branch + "' does not exist on remote.\n" +
                            "Use 'Push' on an existing branch or create it on GitHub first.");
                } else if (status != null && status.name().startsWith("REJECTED")) {
                    throw new Exception("Push rejected (" + status.name().toLowerCase().replace('_', ' ') + "): "
                            + (update.getMessage() != null ? update.getMessage() : "see remote logs"));
                }
            }
        }

        // Set upstream tracking config if not already set
        try {
            StoredConfig config = git.getRepository().getConfig();
            String trackingRemote = config.getString("branch", branch, "remote");
            if (trackingRemote == null || trackingRemote.isEmpty()) {
                config.setString("branch", branch, "remote", "origin");
                config.setString("branch", branch, "merge", "refs/heads/" + branch);
                config.save();
            }
        } catch (Exception ignored) {
        }

        return "Push completed successfully.";
    }

    /**
     * Pulls and returns a human-readable summary. Throws GitConflictException on conflicts.
     */
    public String pull(String remoteUrl, String pat, String branch) throws Exception {
        ensureOriginConfigured(remoteUrl);

        org.eclipse.jgit.api.PullCommand pullCommand = git.pull()
                .setRemote("origin")
                .setRemoteBranchName(branch);

        if (remoteUrl != null && remoteUrl.startsWith("http")) {
            pullCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(pat != null ? pat : "token", pat != null ? pat : ""));
        } else {
            pullCommand.setTransportConfigCallback(SshKeyManager.getTransportConfigCallback(VCodeApplication.getInstance()));
        }

        org.eclipse.jgit.api.PullResult result = pullCommand.call();

        if (!result.isSuccessful()) {
            // Check merge conflicts
            org.eclipse.jgit.api.MergeResult merge = result.getMergeResult();
            if (merge != null) {
                if (merge.getMergeStatus() == org.eclipse.jgit.api.MergeResult.MergeStatus.CONFLICTING) {
                    java.util.Set<String> conflicts = merge.getConflicts() != null
                            ? merge.getConflicts().keySet() : new java.util.HashSet<>();
                    throw new GitConflictException("Merge conflicts detected", new java.util.ArrayList<>(conflicts));
                }
                throw new Exception("Pull failed: " + merge.getMergeStatus().toString());
            }
            // Check rebase conflicts
            org.eclipse.jgit.api.RebaseResult rebase = result.getRebaseResult();
            if (rebase != null) {
                if (rebase.getStatus() == org.eclipse.jgit.api.RebaseResult.Status.STOPPED
                        || rebase.getStatus() == org.eclipse.jgit.api.RebaseResult.Status.CONFLICTS) {
                    // Get conflicting files from repo status
                    Status status = git.status().call();
                    java.util.List<String> conflicts = new java.util.ArrayList<>(status.getConflicting());
                    throw new GitConflictException("Rebase conflicts detected", conflicts);
                }
                throw new Exception("Pull (rebase) failed: " + rebase.getStatus().toString());
            }
            throw new Exception("Pull failed — unknown reason.");
        }

        // Build success summary from fetch result
        org.eclipse.jgit.transport.FetchResult fetchResult = result.getFetchResult();
        if (fetchResult != null && !fetchResult.getTrackingRefUpdates().isEmpty()) {
            return "Pulled successfully. " + fetchResult.getTrackingRefUpdates().size() + " ref(s) updated.";
        }
        return "Already up to date.";
    }

    /**
     * Checkout a specific version of a conflicting file (ours=true → keep local, ours=false → use theirs).
     */
    public void checkoutConflictFile(String path, boolean ours) throws Exception {
        git.checkout()
                .setStage(ours ? org.eclipse.jgit.api.CheckoutCommand.Stage.OURS
                        : org.eclipse.jgit.api.CheckoutCommand.Stage.THEIRS)
                .addPath(path)
                .call();
    }

    /**
     * Returns a human-readable summary of what was fetched.
     */
    public String fetch(String remoteUrl, String pat) throws Exception {
        ensureOriginConfigured(remoteUrl);

        org.eclipse.jgit.api.FetchCommand fetchCommand = git.fetch()
                .setRemote("origin");

        if (remoteUrl != null && remoteUrl.startsWith("http")) {
            fetchCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(pat != null ? pat : "token", pat != null ? pat : ""));
        } else {
            fetchCommand.setTransportConfigCallback(SshKeyManager.getTransportConfigCallback(VCodeApplication.getInstance()));
        }

        org.eclipse.jgit.transport.FetchResult result = fetchCommand.call();

        // Build a summary of updated tracking refs
        java.util.Collection<org.eclipse.jgit.transport.TrackingRefUpdate> updates = result.getTrackingRefUpdates();
        if (updates == null || updates.isEmpty()) {
            return "Already up to date. No new changes on remote.";
        }
        StringBuilder sb = new StringBuilder();
        for (org.eclipse.jgit.transport.TrackingRefUpdate u : updates) {
            String branch = org.eclipse.jgit.lib.Repository.shortenRefName(u.getLocalName());
            String type = getType(u);
            if (sb.length() > 0) sb.append("\n");
            sb.append("  ").append(branch).append(" (").append(type).append(")");
        }
        return "Fetched " + updates.size() + " ref(s):\n" + sb;
    }

    @NonNull
    private String getType(TrackingRefUpdate u) {
        String type;
        org.eclipse.jgit.lib.RefUpdate.Result r = u.getResult();
        if (r == org.eclipse.jgit.lib.RefUpdate.Result.NEW) type = "new branch";
        else if (r == org.eclipse.jgit.lib.RefUpdate.Result.FAST_FORWARD) type = "updated";
        else if (r == org.eclipse.jgit.lib.RefUpdate.Result.FORCED) type = "forced";
        else if (r == org.eclipse.jgit.lib.RefUpdate.Result.NO_CHANGE) type = "up to date";
        else type = r != null ? r.name().toLowerCase().replace('_', ' ') : "updated";
        return type;
    }

    /**
     * Ensures the 'origin' remote in .git/config points to the given URL.
     */
    private void ensureOriginConfigured(String remoteUrl) throws Exception {
        if (remoteUrl == null || remoteUrl.trim().isEmpty()) return;
        StoredConfig config = git.getRepository().getConfig();
        String current = config.getString("remote", "origin", "url");
        if (!remoteUrl.trim().equals(current)) {
            config.setString("remote", "origin", "url", remoteUrl.trim());
            config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
            config.save();
        }
    }

    public void stashCreate() throws Exception {
        git.stashCreate().call();
    }

    public void stashCreate(String message) throws Exception {
        org.eclipse.jgit.api.StashCreateCommand cmd = git.stashCreate();
        if (message != null && !message.trim().isEmpty()) {
            cmd.setWorkingDirectoryMessage(message.trim());
        }
        cmd.call();
    }

    public void stashApply(int stashId) throws Exception {
        git.stashApply().setStashRef("stash@{" + stashId + "}").call();
    }

    public void stashDrop(int stashId) throws Exception {
        git.stashDrop().setStashRef(stashId).call();
    }

    public java.util.Collection<org.eclipse.jgit.revwalk.RevCommit> stashList() throws Exception {
        return git.stashList().call();
    }

    /**
     * Executes soft rollback reset sequences, moving reference tips while keeping staging indexes completely unharmed.
     */
    public void softReset(String commitRef) throws Exception {
        git.reset().setMode(ResetCommand.ResetType.SOFT).setRef(commitRef).call();
    }

    /**
     * Executes mixed rollback reset sequences, moving reference tips, clearing index items, while preserving local disk text fields lines.
     */
    public void mixedReset(String commitRef) throws Exception {
        git.reset().setMode(ResetCommand.ResetType.MIXED).setRef(commitRef).call();
    }

    /**
     * Executes hard destructive reset sequences, rolling branch states back while fully wiping all unstaged local file amendments completely.
     */
    public void hardReset(String commitRef) throws Exception {
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef(commitRef).call();
    }

    /**
     * Compiles a structural canonical parser mapping against the absolute top peak snapshot state node vector.
     */
    private AbstractTreeIterator getHeadTree() throws Exception {
        ObjectId head = git.getRepository().resolve("HEAD^{tree}");
        if (head == null) return new CanonicalTreeParser();
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        try (ObjectReader reader = git.getRepository().newObjectReader()) {
            treeParser.reset(reader, head);
        }
        return treeParser;
    }

    /**
     * Restores a single working-tree file to its index/HEAD state, discarding local edits.
     */
    public void discardFile(String path) throws Exception {
        git.checkout().addPath(path).call();
    }

    /**
     * Discards ALL unstaged changes in the working tree, restoring all files to index state.
     */
    public void discardAll() throws Exception {
        git.checkout().setAllPaths(true).call();
    }

    public void setRepoDir(File repoDir) {
        this.repoDir = repoDir;
    }

    public boolean isGitRepo() {
        return repoDir != null && new File(repoDir, ".git").exists();
    }

    public GitOperationResult init() {
        return init("master");
    }

    public GitOperationResult init(String defaultBranch) {
        try {
            org.eclipse.jgit.api.InitCommand initCommand = Git.init().setDirectory(repoDir);
            if (defaultBranch != null && !defaultBranch.trim().isEmpty()) {
                initCommand.setInitialBranch(defaultBranch.trim());
            }
            git = initCommand.call();
            File gitignore = new File(repoDir, ".gitignore");
            if (!gitignore.exists()) {
                try (java.io.FileWriter fw = new java.io.FileWriter(gitignore)) {
                    fw.write(DEFAULT_GITIGNORE);
                }
            }
            return GitOperationResult.success("Repository initialized");
        } catch (Exception e) {
            return GitOperationResult.error("Init failed: " + e.getMessage());
        }
    }

    public GitOperationResult open() {
        try {
            git = Git.open(repoDir);
            return GitOperationResult.success("Repository opened");
        } catch (java.io.IOException e) {
            return GitOperationResult.error("Not a git repository: " + e.getMessage());
        }
    }

    public void close() {
        if (git != null) {
            git.close();
            git = null;
        }
    }

    public static GitOperationResult cloneRepo(android.content.Context context, String url, File targetDir,
                                               String username, String token,
                                               CloneProgressCallback callback) {
        try {
            org.eclipse.jgit.api.CloneCommand clone = Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(targetDir);

            if (url.startsWith("http")) {
                clone.setCredentialsProvider(
                        new org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider(
                                token != null ? token : "token",
                                token != null ? token : ""));
            } else {
                clone.setTransportConfigCallback(SshKeyManager.getTransportConfigCallback(context));
            }

            clone.setProgressMonitor(new org.eclipse.jgit.lib.ProgressMonitor() {
                private int totalWork;
                private int completedWork;
                private String currentTask;
                private long lastUpdateTime;

                @Override
                public void start(int totalTasks) {
                }

                @Override
                public void beginTask(String title, int total) {
                    this.currentTask = title;
                    this.totalWork = total;
                    this.completedWork = 0;
                    this.lastUpdateTime = System.currentTimeMillis();
                    if (callback != null) callback.onProgress(title, 0, total);
                }

                @Override
                public void update(int completed) {
                    this.completedWork += completed;
                    if (callback != null) {
                        long now = System.currentTimeMillis();
                        if (now - lastUpdateTime > 200 || completedWork == totalWork) {
                            callback.onProgress(currentTask, completedWork, totalWork);
                            callback.onUpdate(completedWork);
                            lastUpdateTime = now;
                        }
                    }
                }

                @Override
                public void endTask() {
                    if (callback != null) callback.onTaskDone();
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public void showDuration(boolean enabled) {
                }
            });
            Git result = clone.call();
            result.close();
            return GitOperationResult.success("Repository cloned successfully");
        } catch (org.eclipse.jgit.api.errors.GitAPIException e) {
            return GitOperationResult.error("Clone failed: " + e.getMessage());
        }
    }

    public interface CloneProgressCallback {
        void onProgress(String task, int done, int total);
        void onUpdate(int completed);
        void onTaskDone();
    }

    public List<CommitInfo> getCommitLog(int maxCount, int skip) {
        if (git == null) return new ArrayList<>();
        List<CommitInfo> logs = new ArrayList<>();
        try {
            Iterable<RevCommit> commits = git.log().setMaxCount(maxCount).setSkip(skip).call();

            try (org.eclipse.jgit.revwalk.RevWalk rw = new org.eclipse.jgit.revwalk.RevWalk(git.getRepository())) {
                for (RevCommit commit : commits) {
                    String[] parents = new String[commit.getParentCount()];
                    for (int i = 0; i < commit.getParentCount(); i++) {
                        parents[i] = commit.getParent(i).getName();
                    }

                    CommitInfo info = new CommitInfo(
                            commit.getName(),
                            commit.getName().substring(0, 7),
                            commit.getFullMessage(),
                            commit.getShortMessage(),
                            commit.getAuthorIdent().getName(),
                            commit.getAuthorIdent().getEmailAddress(),
                            commit.getAuthorIdent().getWhen(),
                            parents
                    );

                    info.setChangedFiles(getChangedFilesInCommit(commit, rw));
                    logs.add(info);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("VCode", "Error getting commit log", e);
        }
        return logs;
    }

    private List<FileStatus> getChangedFilesInCommit(RevCommit commit, org.eclipse.jgit.revwalk.RevWalk rw) {
        List<FileStatus> changedFiles = new ArrayList<>();
        try {
            Repository repo = git.getRepository();

            if (commit.getParentCount() == 0) {
                try (org.eclipse.jgit.treewalk.TreeWalk tw = new org.eclipse.jgit.treewalk.TreeWalk(repo)) {
                    tw.addTree(commit.getTree());
                    tw.setRecursive(true);
                    while (tw.next()) {
                        changedFiles.add(new FileStatus(tw.getPathString(), FileStatus.Type.STAGED_ADDED));
                    }
                }
            } else {
                RevCommit parent = rw.parseCommit(commit.getParent(0).getId());

                try (DiffFormatter df = new DiffFormatter(org.eclipse.jgit.util.io.DisabledOutputStream.INSTANCE)) {
                    df.setRepository(repo);
                    df.setDiffComparator(org.eclipse.jgit.diff.RawTextComparator.DEFAULT);
                    df.setDetectRenames(true);

                    List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
                    for (DiffEntry diff : diffs) {
                        FileStatus.Type type = FileStatus.Type.STAGED_MODIFIED;
                        String path = diff.getNewPath();

                        switch (diff.getChangeType()) {
                            case ADD:
                                type = FileStatus.Type.STAGED_ADDED;
                                break;
                            case DELETE:
                                type = FileStatus.Type.STAGED_DELETED;
                                path = diff.getOldPath();
                                break;
                            case RENAME:
                            case COPY:
                            case MODIFY:
                                break;
                        }
                        changedFiles.add(new FileStatus(path, type));
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("VCode", "Error getting changed files", e);
        }
        return changedFiles;
    }

    /**
     * Thrown when a pull results in unresolved merge conflicts.
     */
    public static class GitConflictException extends Exception {
        private final List<String> conflictingFiles;

        public GitConflictException(String message, List<String> conflictingFiles) {
            super(message);
            this.conflictingFiles = conflictingFiles;
        }

        public List<String> getConflictingFiles() {
            return conflictingFiles;
        }
    }
}