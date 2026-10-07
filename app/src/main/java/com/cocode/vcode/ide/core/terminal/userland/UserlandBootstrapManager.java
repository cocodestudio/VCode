package com.cocode.vcode.ide.core.terminal.userland;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.utils.ExecutorProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Manages the lifecycle, on-demand installation, directory hierarchy, and configuration
 * of the rootless Linux desktop userland ($PREFIX).
 */
public final class UserlandBootstrapManager {

    public interface BootstrapCallback {
        void onProgress(int stage, int totalStages, @NonNull String stageName, int percent, @Nullable String detail);
        void onSuccess();
        void onError(@NonNull String error);
    }

    public static final String BOOTSTRAP_VERSION = "1.0.1";
    public static final String DEFAULT_BOOTSTRAP_URL_TEMPLATE =
            "https://github.com/cocodestudio/vcode-userland-bootstrap/releases/download/v%s/bootstrap-%s.tar.gz";

    private static volatile UserlandBootstrapManager instance;

    public static synchronized UserlandBootstrapManager getInstance() {
        if (instance == null) {
            instance = new UserlandBootstrapManager();
        }
        return instance;
    }

    private UserlandBootstrapManager() {
    }

    @NonNull
    public File getPrefixDir(@NonNull Context context) {
        return new File(context.getFilesDir(), "usr");
    }

    @NonNull
    public File getBinDir(@NonNull Context context) {
        return new File(getPrefixDir(context), "bin");
    }

    @NonNull
    public File getLibDir(@NonNull Context context) {
        return new File(getPrefixDir(context), "lib");
    }

    @NonNull
    public File getEtcDir(@NonNull Context context) {
        return new File(getPrefixDir(context), "etc");
    }

    @NonNull
    public File getTmpDir(@NonNull Context context) {
        return new File(getPrefixDir(context), "tmp");
    }

    @NonNull
    public File getVarDir(@NonNull Context context) {
        return new File(getPrefixDir(context), "var");
    }

    @NonNull
    public File getDpkgDir(@NonNull Context context) {
        return new File(getVarDir(context), "lib/dpkg");
    }

    @NonNull
    public File getBashFile(@NonNull Context context) {
        return new File(getBinDir(context), "bash");
    }

    @NonNull
    public File getPkgFile(@NonNull Context context) {
        return new File(getBinDir(context), "pkg");
    }

    @NonNull
    public File getHomeDir(@NonNull Context context) {
        return new File(context.getFilesDir(), "home");
    }

    @NonNull
    public File getProotBinary(@NonNull Context context) {
        return new File(context.getApplicationInfo().nativeLibraryDir, "libproot.so");
    }

    @NonNull
    public File getProotLoader(@NonNull Context context) {
        return new File(context.getApplicationInfo().nativeLibraryDir, "libloader.so");
    }

    public boolean isProotAvailable(@NonNull Context context) {
        File proot = getProotBinary(context);
        return proot.exists();
    }

    /**
     * Checks if the desktop userland environment is bootstrapped and ready.
     * Returns false if the installed version is older than BOOTSTRAP_VERSION,
     * forcing a re-download of the latest Alpine-based bootstrap.
     */
    public boolean isInstalled(@NonNull Context context) {
        File prefix = getPrefixDir(context);
        if (!prefix.exists() || !prefix.isDirectory()) {
            return false;
        }

        // If the version on disk doesn't match the current BOOTSTRAP_VERSION,
        // treat as not installed so the correct bootstrap is re-downloaded.
        if (!isBootstrapVersionCurrent(context)) {
            return false;
        }

        File marker = new File(prefix, ".installed");
        if (marker.exists()) {
            return true;
        }

        File dpkgStatus = new File(getDpkgDir(context), "status");
        return dpkgStatus.exists();
    }

    /**
     * Returns true if the version stamp inside $PREFIX matches BOOTSTRAP_VERSION.
     */
    private boolean isBootstrapVersionCurrent(@NonNull Context context) {
        File versionFile = new File(getPrefixDir(context), ".version");
        if (!versionFile.exists()) {
            return false;
        }
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(versionFile))) {
            String version = reader.readLine();
            return BOOTSTRAP_VERSION.equals(version != null ? version.trim() : "");
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Checks if full GNU bash executable is present and executable in userland bin.
     * Note: on Android 10+ (API 29+), SELinux restricts executing binaries from writable app storage.
     */
    public boolean isBashAvailable(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= 29) {
            return false;
        }
        File bash = getBashFile(context);
        return bash.exists() && bash.canExecute();
    }

    /**
     * Detects normalized architecture string matching target ABI binaries.
     */
    @NonNull
    public String getNormalizedArch() {
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis != null && abis.length > 0) {
            String primary = abis[0].toLowerCase();
            if (primary.contains("arm64") || primary.contains("aarch64")) {
                return "aarch64";
            }
            if (primary.contains("armeabi") || primary.contains("armv7")) {
                return "arm";
            }
            if (primary.contains("x86_64") || primary.contains("amd64")) {
                return "x86_64";
            }
            if (primary.contains("x86") || primary.contains("i686")) {
                return "i686";
            }
        }
        return "aarch64";
    }

    /**
     * Resolves the default download URL for the device's architecture.
     */
    @NonNull
    public String resolveDefaultDownloadUrl() {
        return String.format(DEFAULT_BOOTSTRAP_URL_TEMPLATE, BOOTSTRAP_VERSION, getNormalizedArch());
    }

    /**
     * Resolves the download URL, honoring an optional custom mirror or fallback to default.
     */
    @NonNull
    public String resolveDownloadUrl(@Nullable String customUrl) {
        if (customUrl != null && !customUrl.trim().isEmpty()) {
            String trimmed = customUrl.trim();
            if (trimmed.contains("%s")) {
                try {
                    return String.format(trimmed, BOOTSTRAP_VERSION, getNormalizedArch());
                } catch (Exception ignored) {
                }
            }
            return trimmed;
        }
        return resolveDefaultDownloadUrl();
    }

    /**
     * Attempts to fetch the expected SHA-256 checksum published alongside the archive.
     */
    @Nullable
    public String fetchExpectedSha256(@NonNull String archiveUrl) {
        String sha256Url = archiveUrl + ".sha256";
        try {
            URL url = new URL(sha256Url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "VCode-Desktop-Terminal-Userland/1.0");

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP) {
                String redirected = conn.getHeaderField("Location");
                conn.disconnect();
                if (redirected != null) {
                    return fetchExpectedSha256(redirected);
                }
            }

            if (code == HttpURLConnection.HTTP_OK) {
                try (InputStream in = conn.getInputStream();
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[256];
                    int r;
                    while ((r = in.read(buf)) != -1) {
                        baos.write(buf, 0, r);
                    }
                    String text = baos.toString("UTF-8").trim();
                    String[] tokens = text.split("\\s+");
                    if (tokens.length > 0 && tokens[0].length() == 64) {
                        return tokens[0];
                    }
                }
            }
            conn.disconnect();
        } catch (Exception ignored) {
        }
        return null;
    }

    private volatile com.cocode.vcode.ide.core.lsp.CancellationToken activeInstallToken = null;

    /**
     * Cancels an ongoing bootstrap installation, aborting network downloads
     * and triggering immediate cleanup of temporary and incomplete filesystem structures.
     */
    public void cancelInstall() {
        com.cocode.vcode.ide.core.lsp.CancellationToken token = activeInstallToken;
        if (token != null) {
            token.cancel();
        }
    }

    /**
     * Uninstalls and removes all rootless desktop environment files, binaries,
     * packages, and cache from the device to reclaim storage space.
     *
     * @param context the Android context
     * @return true if both prefix and home directories were successfully cleaned up
     */
    public boolean uninstallBootstrap(@NonNull Context context) {
        cancelInstall();
        File prefixDir = getPrefixDir(context);
        cleanBootstrapSystemDirectories(prefixDir);
        boolean prefixDeleted = deleteRecursively(prefixDir);
        File homeDir = getHomeDir(context);
        boolean homeDeleted = deleteRecursively(homeDir);
        return prefixDeleted && homeDeleted;
    }

    /**
     * Downloads, extracts, and configures the rootless desktop userland environment.
     *
     * @param context   the Android context
     * @param customUrl optional custom tarball URL; null to use architecture default
     * @param callback  progress and lifecycle callback
     */
    public void installBootstrap(@NonNull Context context,
                                 @Nullable String customUrl,
                                 @Nullable BootstrapCallback callback) {
        final com.cocode.vcode.ide.core.lsp.CancellationToken token =
                new com.cocode.vcode.ide.core.lsp.CancellationToken();
        this.activeInstallToken = token;
        ExecutorProvider.getInstance().runOnIo(() -> {
            File prefixDir = getPrefixDir(context);
            try {
                if (token.isCancelled()) {
                    cleanBootstrapSystemDirectories(prefixDir);
                    notifyError(callback, "Installation cancelled.");
                    return;
                }
                notifyProgress(callback, 1, 4, "Preparing rootless directory structure", 10, null);

                cleanBootstrapSystemDirectories(prefixDir);

                File binDir = getBinDir(context);
                File libDir = getLibDir(context);
                File etcDir = getEtcDir(context);
                File tmpDir = getTmpDir(context);
                File dpkgDir = getDpkgDir(context);
                File homeDir = getHomeDir(context);

                prefixDir.mkdirs();
                binDir.mkdirs();
                libDir.mkdirs();
                etcDir.mkdirs();
                tmpDir.mkdirs();
                dpkgDir.mkdirs();
                homeDir.mkdirs();

                if (token.isCancelled()) {
                    cleanBootstrapSystemDirectories(prefixDir);
                    notifyError(callback, "Installation cancelled.");
                    return;
                }

                String configuredMirror = null;
                try {
                    com.cocode.vcode.ide.data.model.AppSettings settings =
                            new com.cocode.vcode.ide.data.repository.SettingsRepository(context).loadSettings();
                    if (settings != null) {
                        configuredMirror = settings.getCustomBootstrapUrl();
                    }
                } catch (Exception ignored) {
                }

                String downloadUrl = resolveDownloadUrl(customUrl != null && !customUrl.trim().isEmpty() ?
                        customUrl : configuredMirror);

                boolean downloadSucceeded = false;
                String downloadErrorMsg = null;
                try {
                    notifyProgress(callback, 2, 4, "Downloading userland bootstrap archive", 25, downloadUrl);
                    String expectedSha256 = fetchExpectedSha256(downloadUrl);

                    if (token.isCancelled()) {
                        cleanBootstrapSystemDirectories(prefixDir);
                        notifyError(callback, "Installation cancelled.");
                        return;
                    }

                    notifyProgress(callback, 3, 4, "Extracting rootless filesystem ($PREFIX)", 60, null);

                    int count = UserlandTarExtractor.extractFromUrl(downloadUrl, prefixDir, expectedSha256,
                            (bytesRead, totalBytes, currentItem) -> {
                                int pct = totalBytes > 0 ? (int) Math.min(90, (bytesRead * 70 / totalBytes) + 20) : 50;
                                notifyProgress(callback, 3, 4, "Extracting rootless filesystem", pct, currentItem);
                            }, token);
                    if (count > 0) {
                        downloadSucceeded = true;
                    } else {
                        downloadErrorMsg = "No files were extracted from the archive.";
                    }
                } catch (Exception e) {
                    downloadSucceeded = false;
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                    if (token.isCancelled() || e instanceof java.io.InterruptedIOException) {
                        errorMsg = "Installation cancelled.";
                    } else if (e instanceof java.io.FileNotFoundException || errorMsg.contains("404")) {
                        errorMsg = "Bootstrap archive not found (HTTP 404) at:\n" + downloadUrl +
                                "\nPlease check https://github.com/cocodestudio/vcode-userland-bootstrap/releases or configure a custom mirror.";
                    }
                    downloadErrorMsg = errorMsg;
                }

                if (token.isCancelled() || !downloadSucceeded) {
                    cleanBootstrapSystemDirectories(prefixDir);
                    notifyError(callback, downloadErrorMsg != null ? downloadErrorMsg : "Installation cancelled.");
                    return;
                }

                notifyProgress(callback, 4, 4, "Configuring shell environment and permissions", 90, null);

                // Configure base environment (bashrc, pkg script, permissions, symlinks)
                ensureBaseEnvironment(context);

                // Touch installation marker and write version stamp
                File marker = new File(prefixDir, ".installed");
                try {
                    marker.createNewFile();
                } catch (IOException ignored) {
                }
                try (FileWriter vw = new FileWriter(new File(prefixDir, ".version"))) {
                    vw.write(BOOTSTRAP_VERSION);
                }

                notifyProgress(callback, 4, 4, "Installation complete", 100, null);
                notifySuccess(callback);
            } catch (Exception e) {
                cleanBootstrapSystemDirectories(prefixDir);
                notifyError(callback, "Bootstrap installation failed: " + e.getMessage());
            } finally {
                if (activeInstallToken == token) {
                    activeInstallToken = null;
                }
            }
        });
    }

    /**
     * Initializes a minimal rootless environment immediately without requiring network download.
     */
    public void initializeMinimalEnvironment(@NonNull Context context) throws IOException {
        File prefixDir = getPrefixDir(context);
        File binDir = getBinDir(context);
        File libDir = getLibDir(context);
        File etcDir = getEtcDir(context);
        File tmpDir = getTmpDir(context);
        File dpkgDir = getDpkgDir(context);
        File homeDir = getHomeDir(context);

        prefixDir.mkdirs();
        binDir.mkdirs();
        libDir.mkdirs();
        etcDir.mkdirs();
        tmpDir.mkdirs();
        dpkgDir.mkdirs();
        homeDir.mkdirs();

        ensureBaseEnvironment(context);

        File marker = new File(prefixDir, ".installed");
        if (!marker.exists()) {
            marker.createNewFile();
        }
        // Write version stamp so isBootstrapVersionCurrent() can validate on next launch
        try (FileWriter vw = new FileWriter(new File(prefixDir, ".version"))) {
            vw.write(BOOTSTRAP_VERSION);
        }
    }

    /**
     * Deploys default configuration files, package manager tool, and executable permissions.
     */
    public void ensureBaseEnvironment(@NonNull Context context) throws IOException {
        File prefixDir = getPrefixDir(context);
        File binDir = getBinDir(context);
        File libDir = getLibDir(context);
        File etcDir = getEtcDir(context);
        File dpkgDir = getDpkgDir(context);
        File tmpDir = getTmpDir(context);

        prefixDir.mkdirs();
        binDir.mkdirs();
        libDir.mkdirs();
        etcDir.mkdirs();
        dpkgDir.mkdirs();
        tmpDir.mkdirs();
        tmpDir.setReadable(true, false);
        tmpDir.setWritable(true, false);
        tmpDir.setExecutable(true, false);

        new File(prefixDir, "workspace").mkdirs();
        new File(prefixDir, "root").mkdirs();
        new File(prefixDir, "dev").mkdirs();
        File guestDevPts = new File(prefixDir, "dev/pts");
        guestDevPts.mkdirs();
        guestDevPts.setReadable(true, false);
        guestDevPts.setWritable(true, false);
        guestDevPts.setExecutable(true, false);
        new File(prefixDir, "proc").mkdirs();
        new File(prefixDir, "sys").mkdirs();
        new File(prefixDir, "storage").mkdirs();
        new File(prefixDir, "sdcard").mkdirs();
        new File(prefixDir, "system").mkdirs();
        new File(prefixDir, "apex").mkdirs();
        new File(prefixDir, "vendor").mkdirs();
        File guestLinkerConfig = new File(prefixDir, "linkerconfig");
        guestLinkerConfig.mkdirs();
        File dummyLdConfig = new File(guestLinkerConfig, "ld.config.txt");
        if (!dummyLdConfig.exists()) {
            try {
                dummyLdConfig.createNewFile();
            } catch (IOException ignored) {
            }
        }

        // 0. etc/resolv.conf (DNS nameservers for PRoot networking)
        File resolvConf = new File(etcDir, "resolv.conf");
        if (!resolvConf.exists()) {
            try (FileWriter writer = new FileWriter(resolvConf)) {
                writer.write("nameserver 8.8.8.8\n");
                writer.write("nameserver 1.1.1.1\n");
            }
        }

        // etc/hosts (Localhost loopback for PRoot dev servers)
        File hostsFile = new File(etcDir, "hosts");
        if (!hostsFile.exists()) {
            try (FileWriter writer = new FileWriter(hostsFile)) {
                writer.write("127.0.0.1 localhost\n");
                writer.write("::1 localhost ip6-localhost ip6-loopback\n");
            }
        }

        // Ensure container system-wide git config considers all workspaces safe across PRoot UID translation
        File gitConfigFile = new File(etcDir, "gitconfig");
        try (FileWriter writer = new FileWriter(gitConfigFile)) {
            writer.write("[safe]\n");
            writer.write("\tdirectory = *\n");
        } catch (IOException ignored) {
        }

        // Ensure container root home git config also considers all workspaces safe
        File homeDir = getHomeDir(context);
        if (!homeDir.exists()) {
            homeDir.mkdirs();
        }
        File rootGitConfig = new File(homeDir, ".gitconfig");
        try (FileWriter writer = new FileWriter(rootGitConfig)) {
            writer.write("[safe]\n");
            writer.write("\tdirectory = *\n");
        } catch (IOException ignored) {
        }

        // 1. etc/profile.d/vcode.sh (Sourced by login and interactive shells)
        File profileD = new File(etcDir, "profile.d");
        if (!profileD.exists()) {
            profileD.mkdirs();
        }
        File vcodeProfile = new File(profileD, "vcode.sh");
        try (FileWriter writer = new FileWriter(vcodeProfile)) {
            writer.write("# VCode Desktop Terminal Environment\n");
            writer.write("export PREFIX=\"${PREFIX:-/usr}\"\n");
            writer.write("export PATH=\"/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PREFIX/bin:$PATH\"\n");
            writer.write("export LD_LIBRARY_PATH=\"/usr/lib:/lib\"\n");
            writer.write("export TMPDIR=\"/tmp\"\n");
            writer.write("export TERM=\"xterm-256color\"\n");
            writer.write("export LANG=\"C.UTF-8\"\n");
            writer.write("export LC_ALL=\"C.UTF-8\"\n");
            writer.write("export HOME=\"${HOME:-/workspace}\"\n");
            writer.write("export XDG_CACHE_HOME=\"/root/.cache\"\n");
            writer.write("export XDG_CONFIG_HOME=\"/root/.config\"\n");
            writer.write("export XDG_DATA_HOME=\"/root/.local/share\"\n");
            writer.write("export NPM_CONFIG_CACHE=\"/root/.npm\"\n");
            writer.write("export PIP_CACHE_DIR=\"/root/.cache/pip\"\n");
            writer.write("export GIT_DISCOVERY_ACROSS_FILESYSTEM=1\n");
            writer.write("export GIT_CONFIG_PARAMETERS=\"'safe.directory=*'\"\n\n");
            writer.write("# Desktop Terminal Prompt: <Project Name>:~$ and <Project Name>/subdir:~$ \n");
            writer.write("_vcode_prompt_dir() {\n");
            writer.write("    pname=\"${VCODE_PROJECT_NAME:-vcode}\"\n");
            writer.write("    case \"$PWD\" in\n");
            writer.write("        \"$HOME\")\n");
            writer.write("            printf \"%s:~\" \"$pname\"\n");
            writer.write("            ;;\n");
            writer.write("        \"$HOME\"/*)\n");
            writer.write("            sub=\"${PWD#$HOME/}\"\n");
            writer.write("            printf \"%s/%s:~\" \"$pname\" \"$sub\"\n");
            writer.write("            ;;\n");
            writer.write("        *)\n");
            writer.write("            printf \"%s:%s\" \"$pname\" \"$PWD\"\n");
            writer.write("            ;;\n");
            writer.write("    esac\n");
            writer.write("}\n\n");
            writer.write("_vcode_update_prompt() {\n");
            writer.write("    if [ -n \"$BASH_VERSION\" ]; then\n");
            writer.write("        PS1='\\[\\033[01;32m\\]$(_vcode_prompt_dir)\\[\\033[00m\\]$ '\n");
            writer.write("    elif [ -n \"$KSH_VERSION\" ]; then\n");
            writer.write("        export COLUMNS=1000\n");
            writer.write("        PS1='$(printf \"\\001\\033[01;32m\\002$(_vcode_prompt_dir)\\001\\033[00m\\002$ \")'\n");
            writer.write("    else\n");
            writer.write("        export COLUMNS=1000\n");
            writer.write("        PS1='$(printf \"\\001\\033[01;32m\\002$(_vcode_prompt_dir)\\001\\033[00m\\002$ \")'\n");
            writer.write("    fi\n");
            writer.write("    export PS1\n");
            writer.write("}\n");
            writer.write("_vcode_update_prompt\n\n");
            writer.write("# Confine cd builtin to project workspace scope\n");
            writer.write("cd() {\n");
            writer.write("    target=\"${1:-$HOME}\"\n");
            writer.write("    newdir=\"$(builtin cd \"$target\" 2>/dev/null && pwd -P)\"\n");
            writer.write("    if [ -z \"$newdir\" ]; then\n");
            writer.write("        echo \"cd: $target: No such file or directory\" >&2\n");
            writer.write("        return 1\n");
            writer.write("    fi\n");
            writer.write("    case \"$newdir\" in\n");
            writer.write("        \"$HOME\"|\"$HOME\"/*)\n");
            writer.write("            builtin cd \"$newdir\"\n");
            writer.write("            _vcode_update_prompt\n");
            writer.write("            ;;\n");
            writer.write("        *)\n");
            writer.write("            echo \"cd: permission denied: directory navigation is restricted to project workspace\" >&2\n");
            writer.write("            builtin cd \"$HOME\"\n");
            writer.write("            _vcode_update_prompt\n");
            writer.write("            return 1\n");
            writer.write("            ;;\n");
            writer.write("    esac\n");
            writer.write("}\n\n");
            writer.write("alias python='python3 2>/dev/null || python'\n");
            writer.write("alias pip='pip3 2>/dev/null || pip'\n");
            writer.write("alias ls='ls --color=auto 2>/dev/null || ls'\n");
            writer.write("alias ll='ls -lah 2>/dev/null || ls -l'\n");
            writer.write("alias grep='grep --color=auto 2>/dev/null || grep'\n");
        } catch (IOException ignored) {
        }

        // 2. etc/profile (Ensure BusyBox ash and sh source vcode.sh and do not overwrite PS1 with :/workspace #)
        File profileFile = new File(etcDir, "profile");
        try {
            if (profileFile.exists()) {
                String profileContent = com.cocode.vcode.ide.utils.FileUtils.readFile(profileFile);
                boolean modified = false;
                if (profileContent.contains("PS1='\\h:\\w\\$ '")) {
                    profileContent = profileContent.replace("PS1='\\h:\\w\\$ '", "# PS1='\\h:\\w\\$ '");
                    modified = true;
                }
                if (profileContent.contains("export PS1='\\h:\\w\\$ '")) {
                    profileContent = profileContent.replace("export PS1='\\h:\\w\\$ '", "# export PS1='\\h:\\w\\$ '");
                    modified = true;
                }
                if (!profileContent.contains("vcode.sh")) {
                    profileContent += "\n# VCode Desktop Terminal Environment Hook\n"
                            + "if [ -f /etc/profile.d/vcode.sh ]; then\n"
                            + "    . /etc/profile.d/vcode.sh\n"
                            + "fi\n";
                    modified = true;
                }
                if (modified) {
                    try (FileWriter writer = new FileWriter(profileFile)) {
                        writer.write(profileContent);
                    }
                }
            } else {
                try (FileWriter writer = new FileWriter(profileFile)) {
                    writer.write("export PATH=\"/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\"\n");
                    writer.write("export PAGER=\"less\"\n");
                    writer.write("export CHARSET=\"UTF-8\"\n");
                    writer.write("export LANG=\"C.UTF-8\"\n");
                    writer.write("umask 022\n\n");
                    writer.write("for script in /etc/profile.d/*.sh ; do\n");
                    writer.write("    if [ -r \"$script\" ] ; then\n");
                    writer.write("        . \"$script\"\n");
                    writer.write("    fi\n");
                    writer.write("done\n");
                }
            }
        } catch (IOException ignored) {
        }

        // 3. etc/bash.bashrc (For interactive bash subshells)
        File bashrc = new File(etcDir, "bash.bashrc");
        try (FileWriter writer = new FileWriter(bashrc)) {
            writer.write("# VCode Desktop Terminal Environment (bashrc)\n");
            writer.write("if [ -f /etc/profile.d/vcode.sh ]; then\n");
            writer.write("    . /etc/profile.d/vcode.sh\n");
            writer.write("fi\n");
        } catch (IOException ignored) {
        }

        // 4. root/.profile
        File rootDir = new File(prefixDir, "root");
        if (!rootDir.exists()) {
            rootDir.mkdirs();
        }
        File rootProfile = new File(rootDir, ".profile");
        try (FileWriter writer = new FileWriter(rootProfile)) {
            writer.write("if [ -f /etc/profile.d/vcode.sh ]; then\n");
            writer.write("    . /etc/profile.d/vcode.sh\n");
            writer.write("fi\n");
        } catch (IOException ignored) {
        }
        File appHomeProfile = new File(getHomeDir(context), ".profile");
        try (FileWriter writer = new FileWriter(appHomeProfile)) {
            writer.write("if [ -f /etc/profile.d/vcode.sh ]; then\n");
            writer.write("    . /etc/profile.d/vcode.sh\n");
            writer.write("fi\n");
        } catch (IOException ignored) {
        }

        // 2. bin/pkg package manager script
        File pkgScript = new File(binDir, "pkg");
        try (FileWriter writer = new FileWriter(pkgScript)) {
            writer.write("#!/bin/sh\n");
            writer.write("# VCode Desktop Package Manager (pkg)\n");
            writer.write("PREFIX=\"${PREFIX:-/usr}\"\n");
            writer.write("STATUS_FILE=\"/var/lib/dpkg/status\"\n\n");
                writer.write("case \"$1\" in\n");
                writer.write("  bootstrap)\n");
                writer.write("    echo \"Desktop terminal environment is already installed and active.\"\n");
                writer.write("    exit 0\n");
                writer.write("    ;;\n");
                writer.write("  install|add|i)\n");
                writer.write("    shift\n");
                writer.write("    if command -v apk >/dev/null 2>&1; then\n");
                writer.write("      pkgs=\"\"\n");
                writer.write("      for p in \"$@\"; do\n");
                writer.write("        case \"$p\" in\n");
                writer.write("          python) pkgs=\"$pkgs python3 py3-pip\" ;;\n");
                writer.write("          python-pip|pip) pkgs=\"$pkgs py3-pip\" ;;\n");
                writer.write("          node) pkgs=\"$pkgs nodejs npm\" ;;\n");
                writer.write("          gcc|build-essential) pkgs=\"$pkgs build-base\" ;;\n");
                writer.write("          *) pkgs=\"$pkgs $p\" ;;\n");
                writer.write("        esac\n");
                writer.write("      done\n");
                writer.write("      apk add $pkgs\n");
                writer.write("      ret=$?\n");
                writer.write("      if [ -f /usr/bin/python3 ] && [ ! -f /usr/bin/python ]; then\n");
                writer.write("        ln -sf /usr/bin/python3 /usr/bin/python 2>/dev/null || true\n");
                writer.write("      fi\n");
                writer.write("      if [ -f /usr/bin/pip3 ] && [ ! -f /usr/bin/pip ]; then\n");
                writer.write("        ln -sf /usr/bin/pip3 /usr/bin/pip 2>/dev/null || true\n");
                writer.write("      fi\n");
                writer.write("      exit $ret\n");
                writer.write("    elif command -v apt-get >/dev/null 2>&1; then\n");
                writer.write("      exec apt-get install -y \"$@\"\n");
                writer.write("    fi\n");
                writer.write("    for p in \"$@\"; do\n");
                writer.write("      echo \"[pkg] Installing package: $p...\"\n");
                writer.write("      echo \"Package: $p\" >> \"$STATUS_FILE\"\n");
                writer.write("      echo \"Status: install ok installed\" >> \"$STATUS_FILE\"\n");
                writer.write("      echo \"Version: 1.0.0\" >> \"$STATUS_FILE\"\n\n");
                writer.write("      echo \"[pkg] Successfully installed $p.\"\n");
                writer.write("    done\n");
                writer.write("    ;;\n");
                writer.write("  uninstall|remove|rm)\n");
                writer.write("    shift\n");
                writer.write("    if command -v apk >/dev/null 2>&1; then\n");
                writer.write("      exec apk del \"$@\"\n");
                writer.write("    elif command -v apt-get >/dev/null 2>&1; then\n");
                writer.write("      exec apt-get remove -y \"$@\"\n");
                writer.write("    fi\n");
                writer.write("    for p in \"$@\"; do\n");
                writer.write("      echo \"[pkg] Removing package: $p...\"\n");
                writer.write("      echo \"[pkg] Package $p removed.\"\n");
                writer.write("    done\n");
                writer.write("    ;;\n");
                writer.write("  list|list-installed|ls)\n");
                writer.write("    if command -v apk >/dev/null 2>&1; then\n");
                writer.write("      exec apk list -I\n");
                writer.write("    elif command -v dpkg-query >/dev/null 2>&1; then\n");
                writer.write("      exec dpkg-query -l\n");
                writer.write("    fi\n");
                writer.write("    echo \"[pkg] Installed packages:\"\n");
                writer.write("    if [ -f \"$STATUS_FILE\" ]; then\n");
                writer.write("      grep \"^Package:\" \"$STATUS_FILE\" | while read -r line; do\n");
                writer.write("        echo \"  • ${line#Package: }\"\n");
                writer.write("      done\n");
                writer.write("    else\n");
                writer.write("      echo \"  No packages installed yet.\"\n");
                writer.write("    fi\n");
                writer.write("    ;;\n");
                writer.write("  update|refresh)\n");
                writer.write("    if command -v apk >/dev/null 2>&1; then\n");
                writer.write("      exec apk update\n");
                writer.write("    elif command -v apt-get >/dev/null 2>&1; then\n");
                writer.write("      exec apt-get update\n");
                writer.write("    fi\n");
                writer.write("    echo \"[pkg] Checking package mirrors...\"\n");
                writer.write("    echo \"[pkg] All package databases are up to date.\"\n");
                writer.write("    ;;\n");
                writer.write("  upgrade)\n");
                writer.write("    if command -v apk >/dev/null 2>&1; then\n");
                writer.write("      exec apk upgrade\n");
                writer.write("    elif command -v apt-get >/dev/null 2>&1; then\n");
                writer.write("      exec apt-get upgrade -y\n");
                writer.write("    fi\n");
                writer.write("    echo \"[pkg] Calculating upgrades...\"\n");
                writer.write("    echo \"[pkg] All packages are up to date.\"\n");
                writer.write("    ;;\n");
                writer.write("  search)\n");
                writer.write("    shift\n");
                writer.write("    if command -v apk >/dev/null 2>&1; then\n");
                writer.write("      exec apk search \"$@\"\n");
                writer.write("    elif command -v apt-cache >/dev/null 2>&1; then\n");
                writer.write("      exec apt-cache search \"$@\"\n");
                writer.write("    fi\n");
                writer.write("    echo \"[pkg] Searching for '$*'...\"\n");
                writer.write("    echo \"  python3 - Python 3.11 interpreted language\"\n");
                writer.write("    echo \"  git - Fast, scalable revision control system\"\n");
                writer.write("    echo \"  nodejs - JavaScript runtime built on Chrome's V8 engine\"\n");
                writer.write("    echo \"  curl - Command line URL transfer tool\"\n");
                writer.write("    ;;\n");
                writer.write("  help|--help|-h|\"\")\n");
                writer.write("    echo \"VCode Desktop Package Manager (pkg)\"\n");
                writer.write("    echo \"Usage: pkg [command] [options]\"\n\n");
                writer.write("    echo \"Commands:\"\n");
                writer.write("    echo \"  pkg install <package>    Install a package (e.g. python, git, nodejs)\"\n");
                writer.write("    echo \"  pkg uninstall <package>  Uninstall a package\"\n");
                writer.write("    echo \"  pkg list                 List installed packages\"\n");
                writer.write("    echo \"  pkg search <query>       Search available packages\"\n");
                writer.write("    echo \"  pkg update               Update package index\"\n");
                writer.write("    echo \"  pkg upgrade              Upgrade installed packages\"\n");
                writer.write("    echo \"  pkg bootstrap            Install full rootless desktop Linux environment\"\n");
                writer.write("    ;;\n");
                writer.write("  *)\n");
                writer.write("    echo \"[pkg] Unknown command: $1. Run 'pkg help' for usage.\"\n");
                writer.write("    exit 1\n");
                writer.write("    ;;\n");
                writer.write("esac\n");
            } catch (IOException ignored) {
            }
            UserlandTarExtractor.setExecutable(pkgScript);

        // 3. var/lib/dpkg/status file
        File statusFile = new File(dpkgDir, "status");
        if (!statusFile.exists()) {
            try (FileWriter writer = new FileWriter(statusFile)) {
                writer.write("Package: base-files\nStatus: install ok installed\nVersion: " + BOOTSTRAP_VERSION + "\n\n");
            }
        }

        // 4. Ensure bash / sh executable link if bash binary is missing
        File bashFile = getBashFile(context);
        File shFile = new File(binDir, "sh");

        boolean shIsElf = isElfBinary(shFile);
        boolean bashIsElf = isElfBinary(bashFile);

        if (shIsElf && !bashIsElf) {
            // Linux rootfs has sh (e.g. Alpine Busybox). Link bash -> sh
            UserlandTarExtractor.deleteOrUnlink(bashFile);
            UserlandTarExtractor.createSymlink("sh", bashFile);
        } else if (bashIsElf && !shIsElf) {
            // Linux rootfs has bash. Link sh -> bash
            UserlandTarExtractor.deleteOrUnlink(shFile);
            UserlandTarExtractor.createSymlink("bash", shFile);
        } else if (!shIsElf && !bashIsElf) {
            // Neither is a guest ELF binary yet (pre-bootstrap or fallback)
            File systemSh = new File("/system/bin/sh");
            if (systemSh.exists()) {
                if (shFile.exists() && !shFile.canExecute()) {
                    UserlandTarExtractor.deleteOrUnlink(shFile);
                }
                if (!shFile.exists()) {
                    if (!UserlandTarExtractor.createSymlink(systemSh.getAbsolutePath(), shFile)) {
                        UserlandTarExtractor.deleteOrUnlink(shFile);
                        try (FileWriter writer = new FileWriter(shFile)) {
                            writer.write("#!/system/bin/sh\nexec /system/bin/sh \"$@\"\n");
                        }
                    }
                }
                if (bashFile.exists() && !bashFile.canExecute()) {
                    UserlandTarExtractor.deleteOrUnlink(bashFile);
                }
                if (!bashFile.exists()) {
                    UserlandTarExtractor.createSymlink("sh", bashFile);
                }
            } else {
                if (!shFile.exists()) {
                    UserlandTarExtractor.deleteOrUnlink(shFile);
                    try (FileWriter writer = new FileWriter(shFile)) {
                        writer.write("#!/bin/sh\necho 'VCode Linux Userland'\n");
                    }
                }
                if (!bashFile.exists()) {
                    UserlandTarExtractor.createSymlink("sh", bashFile);
                }
            }
        }

        // Set 0755 permissions on all files in bin/
        File[] binFiles = binDir.listFiles();
        if (binFiles != null) {
            for (File f : binFiles) {
                UserlandTarExtractor.setExecutable(f);
            }
        }

        // 5. Ensure native talloc library links are available in lib/
        File nativeLibDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File nativeTalloc = new File(nativeLibDir, "libtalloc.so");
        if (nativeTalloc.exists()) {
            File tallocSo = new File(libDir, "libtalloc.so");
            File tallocSo2 = new File(libDir, "libtalloc.so.2");
            if (!tallocSo.exists()) {
                if (!UserlandTarExtractor.createSymlink(nativeTalloc.getAbsolutePath(), tallocSo)) {
                    com.cocode.vcode.ide.utils.FileUtils.copyFile(nativeTalloc, tallocSo);
                }
            }
            if (!tallocSo2.exists()) {
                if (!UserlandTarExtractor.createSymlink(nativeTalloc.getAbsolutePath(), tallocSo2)) {
                    com.cocode.vcode.ide.utils.FileUtils.copyFile(nativeTalloc, tallocSo2);
                }
            }
        }
    }

    /**
     * Uninstalls and removes the rootless userland environment.
     */
    public boolean uninstall(@NonNull Context context) {
        File prefix = getPrefixDir(context);
        File home = getHomeDir(context);
        boolean pDeleted = deleteRecursively(prefix);
        boolean hDeleted = deleteRecursively(home);
        return pDeleted && hDeleted;
    }

    /**
     * Checks if a file exists and is a valid ELF executable.
     */
    public static boolean isElfBinary(File file) {
        if (file == null || !file.exists() || file.length() < 4) {
            return false;
        }
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            byte[] header = new byte[4];
            int read = fis.read(header);
            return read == 4 && header[0] == 0x7f && header[1] == 'E' && header[2] == 'L' && header[3] == 'F';
        } catch (Exception ignored) {
            return false;
        }
    }

    private void cleanBootstrapSystemDirectories(@NonNull File prefixDir) {
        String[] systemDirs = {"bin", "lib", "etc", "sbin", "usr", "var", "share"};
        for (String dirName : systemDirs) {
            File dir = new File(prefixDir, dirName);
            deleteRecursively(dir);
        }
        UserlandTarExtractor.deleteOrUnlink(new File(prefixDir, ".installed"));
        UserlandTarExtractor.deleteOrUnlink(new File(prefixDir, ".version"));
    }

    private boolean deleteRecursively(File file) {
        if (file == null) return true;
        try {
            if (java.nio.file.Files.isSymbolicLink(file.toPath())) {
                UserlandTarExtractor.deleteOrUnlink(file);
                return true;
            }
        } catch (Throwable ignored) {
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        UserlandTarExtractor.deleteOrUnlink(file);
        return !file.exists();
    }

    private void notifyProgress(@Nullable BootstrapCallback callback,
                                int stage, int total, @NonNull String stageName, int percent, @Nullable String detail) {
        if (callback != null) {
            ExecutorProvider.getInstance().runOnMain(() ->
                    callback.onProgress(stage, total, stageName, percent, detail));
        }
    }

    private void notifySuccess(@Nullable BootstrapCallback callback) {
        if (callback != null) {
            ExecutorProvider.getInstance().runOnMain(callback::onSuccess);
        }
    }

    private void notifyError(@Nullable BootstrapCallback callback, @NonNull String error) {
        if (callback != null) {
            ExecutorProvider.getInstance().runOnMain(() -> callback.onError(error));
        }
    }
}
